# 第 20 审 module-file 第一批后端骨架审查

- 审查日期：2026-05-03
- 审查范围：`server/src/main/resources/db/migration/V2__enhance_file_center_schema.sql`、`server/src/main/resources/db/migration/V3__link_attachment_to_upload_ticket.sql`、`server/src/main/java/com/workforcehub/file/`、`server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`docs/开发日志/module-file.md`、`docs/API与测试/module-file/API文档_module-file文件中心接口.md`、`docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`
- 审查目标：确认 `module-file` 第一批后端实现是否与文件中心第一版契约自洽，是否具备可继续开发的测试保护，并识别下一批必须补齐的风险点

---

## 第一部分：逻辑审查

### 一、DDL 与数据模型

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `V2__enhance_file_center_schema.sql` | 本轮通过 V2 增量增强 `sys_file_object`、`sys_attachment_ref`、`sys_upload_ticket`，并新增配额策略、用量统计和下载票据表，没有直接改 V1 初始化脚本。这个做法符合 Flyway 增量治理原则。 | 建议 | 后续继续新增 V4、V5 迁移，不再回改已执行的 V1/V2/V3。 |
| 1-2 | `V2__enhance_file_center_schema.sql` | `sys_file_object` 已增加 `content_sha256 + file_size + is_deleted` 唯一键，能支撑物理去重；服务层也在并发唯一键冲突时回查已有物理对象。 | 建议 | 进入并发压测前，建议补确认上传并发测试，验证同一文件多请求同时确认时仍只生成一条物理对象。 |
| 1-3 | `V3__link_attachment_to_upload_ticket.sql` | `sys_attachment_ref.source_upload_ticket_id` 能让确认上传重复提交返回原附件引用，解决确认接口幂等识别问题。 | 建议 | 后续如果支持后台上传，也要沿用同一字段，不要另开一套后台幂等口径。 |

### 二、员工端文件主链路

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `EmployeeFileController` + `FileCenterService` | 员工端已实现上传票据、确认上传、配额查询、下载票据、回收站、恢复和彻底删除，覆盖第一批主链路。 | 建议 | 下一批应补真实下载执行入口，否则当前 `downloadUrl` 只是短期票据地址，还不能实际返回文件二进制。 |
| 2-2 | `FileCenterService.confirmEmployeeUpload` | 确认上传会重新读取本地对象并计算 SHA-256、校验文件大小，避免只信客户端 hash。 | 建议 | 后续抽象 Provider 后，`LOCAL` 和 `MINIO` 都必须走服务端校验，不要让 MinIO 分支退化成只信客户端。 |
| 2-3 | `FileCenterService.confirmEmployeeUpload` | 本轮自审补上了上传票据业务归属校验，确认阶段不能把 `PERSONAL_FILE` 篡改成 `WF_REQUEST` 等其他业务归属。 | 建议 | 后续消息、群文件、审批、知识库接入时，业务归属有效性仍需要由对应模块校验。 |

### 三、配额与容量账本

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `FileCenterService.purgeEmployeeFile` | 本轮自审发现彻底删除引用后没有释放 `sys_storage_usage`，已修复为释放个人空间与企业总池用量，并增加自动化测试覆盖。 | 已修复 | 后续引入群空间、知识空间后，需要把释放逻辑从固定 `USER/GLOBAL` 扩展为按 `ownerType` 计算目标空间。 |
| 3-2 | `FileCenterService.restoreEmployeeFile` | 本轮自审发现恢复回收站文件只校验个人空间，没有校验企业总池，已补 `ensureGlobalQuota(0L)` 并增加测试。 | 已修复 | 后续恢复群文件、知识库文件时，还要补目标空间配额复核。 |
| 3-3 | `sys_storage_usage` 更新逻辑 | 当前用量更新通过 `JdbcTemplate` 直接 `used_bytes = used_bytes + ?` 或 `GREATEST(used_bytes - ?, 0)`，能满足第一批串行集成测试，但还没有显式行锁、乐观锁或并发测试。 | 重大 | 第二批或进入多人并发上传前，应补并发配额测试，并考虑对用量行做事务内锁定或原子更新约束。 |

### 四、幂等与重试

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `createEmployeeUploadTicket` | 本轮自审发现重复 `clientRequestId` 会触发数据库唯一键异常并返回 500，已修复为顺序重试时回查并返回同一上传票据。 | 已修复 | 并发重复请求仍建议捕获 `DuplicateKeyException` 后再次回查，避免两个请求同时穿过前置查询。 |
| 4-2 | `confirmEmployeeUpload` | 已确认票据重复确认时会通过 `source_upload_ticket_id` 返回原附件引用，不会重复创建业务引用。 | 建议 | 后续补充确认接口并发测试，覆盖同一票据多次快速确认。 |
| 4-3 | `purgeEmployeeFile` | 本轮自审补上 `is_deleted=1` 防御，重复彻底删除返回附件不存在，避免重复释放空间。 | 建议 | 如果后续希望删除接口做到严格幂等，可改为返回同一个删除结果，但必须保持不重复扣减。 |

### 五、存储 Provider 与下载

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 5-1 | `FileCenterService` | 当前代码固定使用 `LOCAL`，虽然 POM 已有 MinIO 依赖、文档也保留 `MINIO` 口径，但真实 MinIO Provider 还没有实现。 | 重大 | 下一批优先抽象 `StorageProvider` 接口，并实现 `LocalStorageProvider`、`MinioStorageProvider`，再补 MinIO 冒烟测试。 |
| 5-2 | `createEmployeeDownloadTicket` | 当前只创建下载票据并返回短期入口，没有实现 `GET /api/employee/files/download/{ticketNo}`。 | 重大 | 下一批需要实现下载执行入口，校验票据状态、过期时间、权限，并在执行下载时写审计。 |

### 六、代码结构与可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `FileCenterService` | 当前服务类集中承载 Controller 背后的所有 SQL、Provider、配额、审计和 DTO 组装逻辑，第一批落地速度快，但继续扩展会变重。 | 建议 | 第二批建议拆为 `FileObjectRepository`、`AttachmentRefRepository`、`StorageUsageRepository`、`StorageProvider`、`FileAuditService`。 |
| 6-2 | `FileCenterService.audit` | 审计写入使用字符串拼 JSON 并 `CAST(? AS JSON)`，当前输入已做简单转义，能覆盖测试场景。 | 建议 | 后续建议改为 `ObjectMapper` 序列化 detail 对象，避免人工拼 JSON。 |
| 6-3 | `GlobalExceptionHandler` | 当前未专门处理 `HttpRequestMethodNotSupportedException`，不存在的方法或方法不匹配可能落到 500。 | 建议 | 可在后续统一异常治理中补 405 响应，避免接口联调时误判成系统异常。 |

### 七、测试与文档

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 7-1 | `ModuleFileApiIntegrationTest` | 当前已有 8 个集成测试覆盖上传、确认、秒传、下载票据、配额、回收站、幂等、防篡改和后台存储摘要。 | 建议 | 下一批继续按接口落测试，优先补 MinIO、下载执行、后台配额策略和清理任务。 |
| 7-2 | `docs/API与测试/module-file/` | 本轮已把 API 文档和测试用例从“开发前契约”校准为“第一批已实现状态 + 后续契约”，避免误以为 17 个接口全部完成。 | 建议 | 每做一批接口都同步更新已实现清单和自动化覆盖清单。 |
| 7-3 | `LocalIntegrationTestSupport` | 集成测试清理逻辑已加入文件中心表，保证测试数据隔离。 | 建议 | 表越来越多后可考虑按模块拆测试清理器，避免基础类持续膨胀。 |

### 八、审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 3 | 3-3、5-1、5-2 |
| 已修复 | 4 | 3-1、3-2、4-1、4-3 |
| 建议 | 13 | 1-1、1-2、1-3、2-1、2-2、2-3、4-2、6-1、6-2、6-3、7-1、7-2、7-3 |
| 合计 | 20 |  |

### 九、优先处理建议

**第一优先级（下一批直接处理）：**

1. **5-1** 抽象并实现 `LOCAL/MINIO` Provider
2. **5-2** 实现真实下载执行入口
3. **3-3** 补并发配额与并发去重测试，完善用量更新一致性

**第二优先级（文件中心继续扩展时处理）：**

- **6-1** 拆分 `FileCenterService`
- **4-1** 捕获并发重复 `clientRequestId` 的唯一键冲突后回查
- **6-3** 补 405 方法不支持异常处理
- **7-1** 补后台配额策略、回收站治理、清理任务测试

---

## 第二部分：阶段性成熟度评估

### 一、当前正向结论

- 第一批文件中心后端已经具备可运行的员工端主链路
- Flyway 增量迁移、控制器、DTO、服务和集成测试已经形成闭环
- 上传校验、物理去重、业务引用、配额用量、回收站和审计都有第一版实现
- 自审中发现的容量释放、恢复总池校验、重复删除、上传票据幂等、确认归属防篡改都已通过测试修复

### 二、当前核心风险判断

- 当前实现还不是完整文件中心第一版，只是第一批后端主链路。
- 尚未实现真实 MinIO Provider 和真实下载执行入口，因此还不能用于私有化文件服务完整联调。
- 后台治理接口仍停留在文档契约，尚不能通过后台配置配额、治理回收站或执行清理任务。
- 并发上传、并发配额扣减和并发幂等还需要专项测试。

### 三、阶段性结论

可以把这一轮视为：

`module-file 已完成第一批后端骨架落地，员工端主链路可测；下一步应进入 StorageProvider 抽象、MinIO 接入、真实下载和后台治理接口。`
