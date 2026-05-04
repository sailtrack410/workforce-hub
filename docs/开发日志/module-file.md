# 开发日志 - module-file

记录 `module-file` 文件中心相关开发改动，按时间倒序，最新在最上方。

---

## 2026-05-05 module-file 上传限制策略配置化

### 改动背景

前面文件中心已经跑通员工端个人文件上传、确认、秒传、下载、回收站和配额主链路，但“文件类型与大小限制”仍停留在契约层。如果不在申请上传票据阶段拦截，前端可以对 `.exe` 等高风险文件或超大文件拿到上传地址，后续会浪费存储资源，也会让不同业务场景共用一套模糊规则。

### 红灯验证

新增自动化用例：

- `uploadTicketShouldRejectUnsupportedFileType`
- `uploadTicketShouldRejectWhenFileExceedsSceneLimit`

红灯结果：

- `.exe + application/x-msdownload` 实际返回 `code=0`
- 超过 `100MB` 的 `.zip` 实际返回 `code=0`

这说明当前上传票据创建阶段没有读取任何文件类型或大小策略。

### 修订内容

新增：

- `server/src/main/resources/db/migration/V5__add_file_upload_policy.sql`

修改：

- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 新增 `sys_file_upload_policy`，按 `owner_type` 管理单文件大小、允许扩展名、允许 MIME 类型
- 内置 `PERSONAL_FILE_DEFAULT_100M` 策略，个人文件默认单文件上限 `100MB`
- 上传票据创建前先校验 `ownerType`，并优先按 `clientRequestId` 返回已签发票据；新票据才读取启用上传策略并校验大小、扩展名和 MIME
- 文件过大返回业务码 `413`，文件类型不允许返回业务码 `415`
- 唯一约束只限制同一 `ownerType` 同时存在一条启用策略，为后续后台配置页保留禁用和历史策略空间

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldRejectUnsupportedFileType,ModuleFileApiIntegrationTest#uploadTicketShouldRejectWhenFileExceedsSceneLimit test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 后续边界

- 当前只内置 `PERSONAL_FILE` 第一版策略，后台上传、群文件、审批附件、知识库、Agent 临时文件需要在各自授权模型落地后再补对应 `ownerType` 策略
- 还没有后台上传策略 CRUD；下一步可先做管理员查询和调整上传策略，再接前端配置页
- 上传限制策略只是元数据前置拦截，不能替代服务端内容识别、安全扫描或杀毒流程；后续需要在真实上传确认或异步扫描阶段补文件嗅探和扫描状态流转
- `FileCenterService` 继续增大，后续应拆出上传策略 Repository / Service，避免文件服务变成“万能抽屉”

### 第 23 审修订补充

第 23 审指出上传策略校验先于 `clientRequestId` 幂等回查，会导致已签发票据在策略禁用、调整或误删后重试失败。本轮补充回归测试并调整顺序：

- 已签发票据重复申请时，先按同一用户和同一 `clientRequestId` 回查原票据
- 只有新上传票据才执行当前上传策略校验
- 新增 `V6__align_file_upload_policy_owner_type_comment.sql`，将 `sys_file_upload_policy.owner_type` 列注释中的群文件 ownerType 示例统一为 `IM_GROUP_FILE`

补充验证：

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldReturnExistingTicketWhenPolicyChangesAfterFirstRequest test`

---

## 2026-05-04 module-file 第22审风险复核修订

### 改动背景

第 22 审复核的是第一批后端骨架风险。由于当前代码已经推进到第二批，部分问题已经被第二批覆盖，例如真实下载入口已经落地；但仍有几类问题在当前 `HEAD` 下属实：

- 员工端上传仍允许 `WF_REQUEST`、`IM_MESSAGE` 等跨业务归属类型
- 同一上传票据在异常状态或并发确认下可能重复生成附件引用并重复加账
- MySQL 唯一索引对 `scope_id = NULL` 无法约束全局配额和全局用量
- 集成测试重置没有清理 `sys_audit_log`，审计断言存在历史数据误报风险

### 红灯验证

新增测试覆盖：

- 员工端第一版拒绝非 `PERSONAL_FILE` 归属
- 已有关联附件引用时，即使上传票据状态异常回到待确认，再次确认也必须返回原附件引用
- 数据库层拒绝重复的 `GLOBAL` 空作用域配额策略
- 数据库层拒绝重复的 `GLOBAL` 空作用域用量账本
- 集成测试重置必须清理审计日志

红灯结果：

- `employeeUploadTicketShouldRejectBusinessOwnerType` 失败，实际返回 `code=0`
- `confirmUploadShouldBeIdempotentWhenTicketStatusIsStale` 失败，实际创建了第二条附件引用
- `databaseShouldRejectDuplicateNullScopeQuotaPolicy` 失败，重复 `GLOBAL` 配额策略未被数据库拒绝
- `databaseShouldRejectDuplicateNullScopeUsage` 失败，重复 `GLOBAL` 用量账本未被数据库拒绝
- `integrationTestResetShouldClearAuditLog` 失败，历史审计仍保留

### 修订内容

新增：

- `server/src/main/resources/db/migration/V4__harden_file_center_idempotency.sql`

修改：

- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`
- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 员工端上传和确认上传第一版只允许 `ownerType=PERSONAL_FILE` 且 `ownerId=null`
- 上传确认在校验前先按 `source_upload_ticket_id` 回查已有附件引用，避免异常状态下重复加账
- `sys_attachment_ref` 增加 `uk_attachment_upload_ticket_active`，数据库层保证同一上传票据只生成一条未删除附件引用
- `sys_storage_quota_policy` 和 `sys_storage_usage` 增加 `scope_key` 生成列，用 `__NONE__` 表达空作用域，并重建唯一索引
- `increaseUsage` 改为 `INSERT ... ON DUPLICATE KEY UPDATE`，避免用量行首次写入的先查再插并发窗口
- 上传票据创建捕获 `clientRequestId` 唯一冲突后回查已有票据，避免并发重试冒成 `500`
- 集成测试重置清理 `sys_audit_log`，审计断言不再被历史数据污染

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#employeeUploadTicketShouldRejectBusinessOwnerType,ModuleFileApiIntegrationTest#confirmUploadShouldBeIdempotentWhenTicketStatusIsStale,ModuleFileApiIntegrationTest#databaseShouldRejectDuplicateNullScopeQuotaPolicy,ModuleFileApiIntegrationTest#databaseShouldRejectDuplicateNullScopeUsage,ModuleFileApiIntegrationTest#integrationTestResetShouldClearAuditLog test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 后续边界

- `FileCenterService` 仍然偏大，下一批后台治理前建议继续拆 Repository 与 AuditService
- `visibility_scope='OWNER'` 暂时只服务个人文件；跨业务附件开放前必须先定义 ownerType 到授权策略的映射
- `uk_file_sha256_size_active` 的物理对象软删除语义留到清理任务阶段统一设计
- MinIO 仍需真实容器端到端联调

---

## 2026-05-04 module-file 第二批文件中心能力落地

### 改动背景

第一批已经跑通员工端上传、确认、秒传、下载票据、配额查询和回收站主链路，但还存在三个明显缺口：

- 下载票据只能生成短期地址，不能真正返回文件二进制
- `LOCAL` 存储逻辑直接写在 `FileCenterService` 中，后续接 `MINIO` 会继续堆大 Service
- 后台端只有存储摘要，管理员还不能通过接口维护用户默认配额或单人覆盖配额

本轮目标是补第二批后端能力，并保持“先测试、再实现、再审查”的节奏。

### 执行过程

#### 1. 先补红灯测试

修改：

- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`

新增测试覆盖：

- 有效下载票据可以下载原始文件字节
- 下载成功后票据状态更新为 `USED`
- 下载执行写入 `FILE_DOWNLOAD_EXECUTED` 审计
- 过期下载票据返回业务码 `410`
- 后台创建、查询、更新配额策略
- 配额策略阈值非法时拦截
- 配额低于当前已用量时拦截
- 同一作用域不能重复创建配额策略
- 相同 `policyCode` 不能重复创建配额策略，避免数据库唯一键异常冒成 `500`

红灯结果：

- `mvn -Dtest=ModuleFileApiIntegrationTest test`
- 新增 4 个用例按预期失败在 `404`
- 失败原因是 `GET /api/employee/files/download/{ticketNo}` 与后台配额策略接口尚未实现

**意义**：

- 先用测试把本轮要补的业务行为钉住，避免只做“看起来有代码”的空接口

#### 2. 抽象存储 Provider

新增：

- `server/src/main/java/com/workforcehub/file/storage/StorageProvider.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageProviderRegistry.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageUploadTarget.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageObjectResource.java`
- `server/src/main/java/com/workforcehub/file/storage/LocalStorageProvider.java`
- `server/src/main/java/com/workforcehub/file/storage/MinioStorageProvider.java`

本轮 Provider 边界：

- `LOCAL` 负责本地上传目标、服务端 hash 校验、下载资源读取和后端剩余容量读取
- `MINIO` 提供预签名上传地址、对象校验和对象读取结构
- 普通自动化测试仍只依赖 `LOCAL`，不会要求 MinIO 服务在线

**意义**：

- 文件中心的业务 Service 不再直接依赖本地磁盘路径
- 后续切换 MinIO、OSS、WebDAV 时，可以沿 Provider 接口扩展

#### 3. 实现真实下载入口

修改：

- `server/src/main/java/com/workforcehub/file/controller/EmployeeFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/main/java/com/workforcehub/file/dto/DownloadFileResult.java`

新增接口：

- `GET /api/employee/files/download/{ticketNo}`

接口行为：

- 校验当前用户必须是下载票据申请人
- 校验票据状态必须为 `ACTIVE`
- 校验票据未过期，过期时更新为 `EXPIRED` 并返回业务码 `410`
- 校验附件引用和物理文件对象未删除
- 通过 `StorageProvider` 读取文件资源
- 下载成功后把票据状态更新为 `USED`
- 写入 `FILE_DOWNLOAD_EXECUTED` 审计

**意义**：

- 文件下载从“只创建票据”推进到“短期票据真正可用”
- 后续消息附件、群文件、文档、审批附件都可以复用同一下载底座

#### 4. 实现后台配额策略 CRUD

新增：

- `server/src/main/java/com/workforcehub/file/dto/CreateQuotaPolicyRequest.java`
- `server/src/main/java/com/workforcehub/file/dto/UpdateQuotaPolicyRequest.java`
- `server/src/main/java/com/workforcehub/file/dto/QuotaPolicyResponse.java`

修改：

- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`

新增接口：

- `GET /api/admin/files/quota-policies`
- `POST /api/admin/files/quota-policies`
- `PUT /api/admin/files/quota-policies/{policyId}`

关键校验：

- `warningThreshold` 必须小于 `blockThreshold`
- `blockThreshold` 不得大于 `1.0`
- `quotaBytes` 不能低于当前作用域已用量
- `GLOBAL`、`USER_DEFAULT`、`TEMP_CACHE` 不允许传 `scopeId`
- `USER`、`DEPARTMENT`、`ROLE`、`GROUP`、`KNOWLEDGE_SPACE` 必须传 `scopeId`
- `status` 仅支持 `ENABLED`、`DISABLED`
- `policyCode` 必须唯一，重复时返回业务码 `409`
- 同一 `scopeType + scopeId` 只能保留一条未删除策略，显式规避 MySQL 唯一键遇到 `NULL` 时不能约束重复默认策略的问题

**意义**：

- 管理员可以通过后台接口维护默认配额和单人覆盖配额
- 配额治理开始从“写死种子数据”进入“后台可配置”阶段

#### 5. 同步 API 文档与测试用例

修改：

- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

同步内容：

- 已实现接口数量从第一批 `8` 个校准为第二批 `12` 个
- 补 `GET /api/employee/files/download/{ticketNo}` 文档
- 校准配额策略列表响应为数组结构
- 自动化覆盖清单加入下载执行、过期下载拦截和配额策略 CRUD
- 未覆盖清单移除“真实下载执行入口”和“配额策略 CRUD”

### 自审结果

| 角度 | 结论 | 后续建议 |
| --- | --- | --- |
| 功能完整性 | 第二批目标已覆盖：Provider 抽象、真实下载、配额策略 CRUD | 后台回收站治理、清理任务、后台上传仍需后续批次 |
| 数据一致性 | 下载票据执行后会更新为 `USED`，过期会更新为 `EXPIRED` | 后续可补重复使用下载票据的自动化测试 |
| 存储扩展性 | `FileCenterService` 已从直接碰磁盘改为通过 Provider 访问存储 | 后续建议继续拆 Repository，避免 Service 继续膨胀 |
| MinIO 边界 | 已有 Provider 结构和 SDK 编译校验 | 仍未接真实 MinIO 容器做端到端上传/下载联调 |
| 配额治理 | 已校验阈值、作用域、当前已用量、重复编码和同作用域重复创建 | 后续要把群空间、知识空间、部门策略的实际选择优先级补成测试 |
| 安全与审计 | 下载执行、配额策略创建和更新都有审计入口 | 后台配额策略接口后续需要细化角色权限，不应长期只依赖管理员会话 |

### 验证记录

- `mvn -DskipTests compile`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 当前未完成边界

- `MINIO` Provider 已编译接入，但还没有真实 MinIO 服务端到端联调
- 后台上传与确认上传未实现
- 后台回收站治理未实现
- 清理任务 dry-run 与执行未实现
- 文件类型、大小场景限制仍未配置化
- 并发去重、并发配额扣减、并发幂等还需要专项测试

### 下一步建议

下一批建议继续做：

1. 后台回收站治理接口
2. 清理任务 dry-run 与执行
3. 文件类型与大小限制配置化
4. MinIO 本地容器联调和可选测试入口
5. `FileCenterService` 仓储层拆分

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
