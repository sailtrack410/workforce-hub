# 开发日志 - module-file

记录 `module-file` 文件中心相关开发改动，按时间倒序，最新在最上方。

---

## 2026-05-03 module-file 第一批后端骨架落地与自审修订

### 改动背景

上一轮已经把文件中心第一版范围、API 契约和测试用例收口。本轮开始进入代码落地，优先选择员工端主链路和后台存储摘要，而不是一次性把 17 个接口全部铺开。

这样做的原因是：

- 文件中心会被消息、文档、审批、知识库、Agent 共同依赖，第一步必须先把共享底座跑通
- 上传、确认、去重、配额、回收站这几条链路一旦语义不稳，后续业务模块都会被污染
- 后台配额策略、管理员回收站和清理任务可以在主链路稳定后继续做第二批

### 本次目标

- 新增文件中心 DDL 增量迁移
- 落地员工端上传票据、确认上传、秒传、下载票据、回收站、配额查询接口
- 落地后台端存储摘要查询接口
- 增加文件中心集成测试，按“到一个测试点就测试”的方式推进
- 自审并修复容量账本、幂等和上传归属一致性问题

### 执行过程

#### 1. 新增 V2 文件中心增强迁移

新增：

- `server/src/main/resources/db/migration/V2__enhance_file_center_schema.sql`

本迁移增强了：

- `sys_file_object`
- `sys_attachment_ref`
- `sys_upload_ticket`

并新增：

- `sys_storage_quota_policy`
- `sys_storage_usage`
- `sys_download_ticket`

**意义**：

- 让 V1 已有的文件对象、附件引用、上传票据具备第一版文件中心所需字段
- 用 `content_sha256 + file_size + is_deleted` 支撑物理去重
- 用独立的配额策略表和用量表承载个人空间、企业总池和临时缓存池

#### 2. 新增 V3 上传票据与附件引用幂等关联

新增：

- `server/src/main/resources/db/migration/V3__link_attachment_to_upload_ticket.sql`

本迁移给 `sys_attachment_ref` 增加 `source_upload_ticket_id`。

**意义**：

- 已确认上传票据重复确认时，可以准确返回原附件引用
- 避免前端重试确认时重复创建业务引用

#### 3. 新增文件中心控制器、DTO 和服务

新增：

- `server/src/main/java/com/workforcehub/file/controller/EmployeeFileController.java`
- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/dto/*.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`

当前已实现接口：

- `POST /api/employee/files/upload-tickets`
- `POST /api/employee/files/confirm-upload`
- `GET /api/employee/files/quota`
- `POST /api/employee/files/{attachmentRefId}/download-tickets`
- `POST /api/employee/files/{attachmentRefId}/trash`
- `POST /api/employee/files/{attachmentRefId}/restore`
- `DELETE /api/employee/files/{attachmentRefId}`
- `GET /api/admin/files/storage-summary`

**意义**：

- 员工端文件中心主链路已经能通过 JSON API 调用
- 后台端可以先看到企业总池、缓存池、文件对象数、引用数、回收站数和待清理对象数
- 文件中心先以 `LOCAL` Provider 跑通自动化，`MINIO` 保留依赖和枚举口径，后续补真实实现

#### 4. 新增文件中心集成测试

新增：

- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`

测试覆盖：

- LOCAL 上传票据、确认上传、秒传、下载票据
- 回收站删除、恢复、彻底删除
- 个人配额不足拦截
- 恢复时企业总池不足拦截
- 彻底删除释放个人和企业用量
- 重复彻底删除不重复释放空间
- `clientRequestId` 重复申请上传票据幂等返回同一票据
- 确认上传不能篡改上传票据的业务归属
- 员工云空间和后台存储摘要查询

同步修改：

- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`

**意义**：

- 集成测试前置清理文件中心表，保证每个测试独立
- 通过真实 MySQL、Redis、Session、MockMvc 验证接口链路，不只测 Service 层

#### 5. 自审修订

本轮自审发现并修复了以下问题：

| 问题 | 修订结果 |
| --- | --- |
| 彻底删除业务引用后没有释放 `sys_storage_usage` | 新增 `releaseUsage`，彻底删除时释放个人与企业总池用量 |
| 回收站恢复只校验个人空间，没有校验企业总池 | 恢复时同时调用 `ensureGlobalQuota(0L)` |
| 重复彻底删除可能造成重复释放空间 | 对 `is_deleted=1` 的附件引用返回 `404`，避免二次扣减 |
| 重复 `clientRequestId` 申请上传票据会触发唯一键异常 | 申请前回查已有票据并直接返回 |
| 确认上传阶段可以篡改业务归属 | 确认时校验请求归属必须和上传票据归属一致 |

### 当前结果

- `module-file` 第一批后端骨架已经落到可运行代码
- 员工端文件中心主链路已具备自动化测试保护
- 后台端先具备存储摘要查询入口
- DDL 已从 V1 增量演进到 V3

### 当前未完成边界

- `MINIO` 真实 Provider 还未实现，当前自动化链路只跑 `LOCAL`
- 下载票据目前只生成短期入口，还没有实现真实下载执行接口
- 后台上传、后台确认上传、配额策略 CRUD、后台回收站、清理任务尚未实现
- 文件类型、大小场景限制还未配置化
- `FileCenterService` 当前用 `JdbcTemplate` 快速落地，后续建议拆 Repository 和 Provider 层
- `clientRequestId` 幂等目前覆盖顺序重试，并发重复请求还需要捕获唯一键冲突后回查

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldBeIdempotentByClientRequestId test`
- `mvn -Dtest=ModuleFileApiIntegrationTest#confirmUploadShouldRejectWhenOwnerDoesNotMatchTicket test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 下一步建议

下一步建议继续做 `module-file` 第二批后端能力：

1. 抽象 `StorageProvider` 接口，补 `LocalStorageProvider` 和 `MinioStorageProvider`
2. 实现真实下载执行入口
3. 实现后台配额策略 CRUD
4. 实现后台回收站治理和清理任务
5. 将文件类型、大小限制配置化

---

## 2026-05-03 module-file 第一版开发范围与接口契约收口

### 改动背景

前一轮已经把文件存储与云空间设计补成了平台级架构文档，明确了：

- 第一版支持 `LOCAL` 和 `MINIO`
- 物理去重使用 `content_sha256 + file_size`
- 文件名不影响物理去重
- 个人空间、群空间、知识空间和企业总池要分层控制
- 回收站先删除业务引用，不立即删除物理对象
- Agent 读取文件必须通过业务授权上下文

但这些内容还停留在架构说明层。进入开发前，需要把它进一步拆成 `module-file` 第一版可执行范围、接口契约和测试点。

### 本次目标

- 明确 `module-file` 第一版必须做什么、不做什么
- 把文件上传、确认、秒传、配额、下载、回收站、清理任务拆成可开发流程
- 补员工端和后台端文件中心 API 文档
- 补企业级测试用例清单
- 为下一步 `V2` 数据库迁移和后端实现建立稳定依据

### 执行过程

#### 1. 新增第一版开发范围文档

新增：

- `docs/开发规划/module-file第一版开发范围.md`

文档中明确了第一版必须做：

- `LOCAL` / `MINIO` Provider
- 两段式上传
- 物理文件去重
- 业务引用创建
- 配额校验
- 下载票据
- 回收站
- 延迟清理
- 访问审计

同时明确第一版暂不做：

- 在线预览与转码
- 防病毒真实扫描
- 水印
- WebDAV
- OSS/COS/S3
- Agent 流程执行
- 公网匿名永久分享

**意义**：

- 让第一版文件中心保持克制，不一开始就变成完整网盘工程
- 先把后续模块都会依赖的共享底座做稳

#### 2. 新增文件中心 API 文档

新增：

- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`

本次覆盖 `17` 个接口：

- 员工端上传票据、确认上传、配额查询、下载票据、回收站、恢复、彻底删除
- 后台端上传票据、确认上传、存储摘要、配额策略、回收站治理、清理任务

**意义**：

- 后续后端实现不需要临时猜请求体和响应体
- 前端和测试可以提前按接口契约准备联调

#### 3. 新增文件中心测试用例

新增：

- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

测试用例覆盖：

- 上传票据
- 上传确认与去重
- 配额查询
- 下载票据
- 回收站
- 后台配额治理
- 后台回收站与清理
- 鉴权与契约

**意义**：

- 后续进入代码实现时，可以做到“到一个测试点就开始测试”
- 文件中心最容易出问题的并发去重、配额复核、回收站恢复都有明确用例

#### 4. 同步文档索引

同步更新：

- `docs/README.md`
- `docs/API与测试/README.md`
- `docs/开发日志/README.md`
- `docs/审查/README.md`
- `docs/架构与设计/接口与事件清单.md`

**意义**：

- 新增文档可以通过总索引和模块索引被快速找到
- 系统级接口清单能正确指向详细 API 文档和测试用例

### 当前结果

- `module-file` 第一版从“架构设计”推进到了“可开发契约”
- 接口与测试点已经具备后续实现依据
- 下一步适合进入数据库迁移设计和后端代码落地

### 下一步建议

下一步建议新建 `V2__enhance_file_center_schema.sql`，不要直接改已经存在的 `V1__init_schema.sql`。

优先落地顺序：

1. 增强 `sys_file_object`
2. 增强 `sys_attachment_ref`
3. 增强 `sys_upload_ticket`
4. 新增 `sys_storage_quota_policy`
5. 新增 `sys_storage_usage`
6. 新增 `sys_download_ticket`
7. 补文件中心集成测试骨架

### 已知待补项

- 当前仍是开发范围和接口契约，尚未修改 DDL 与后端代码
- MinIO 本地联调配置还未写入工程配置
- 并发去重、配额扣减一致性需要在实现阶段用数据库约束和事务测试验证
