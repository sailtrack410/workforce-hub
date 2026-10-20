# 第 22 审 module-file 第一批后端骨架风险与冗余复核

- 审查日期：2026-05-04
- 审查范围：`server/src/main/java/com/workforcehub/file/service/FileCenterService.java`、`server/src/main/java/com/workforcehub/file/controller/EmployeeFileController.java`、`server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`、`server/src/main/resources/db/migration/V2__enhance_file_center_schema.sql`、`server/src/main/resources/db/migration/V3__link_attachment_to_upload_ticket.sql`、`server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`
- 审查目标：在不修改业务代码的前提下，复核最近一次 `module-file` 后端骨架提交中的正确性风险、冗余实现和可维护性隐患，避免后续通过零散补丁叠加导致逻辑变形。

---

## 第一部分：风险审查

### 一、权限与业务归属边界

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `FileCenterService.validateOwnerType` | 当前员工上传入口允许 `WF_REQUEST`、`IM_MESSAGE`、`KB_DOCUMENT` 等业务归属类型，但服务层只做白名单校验，没有校验 `ownerId` 是否存在、是否属于当前用户、是否允许当前用户挂附件。后续业务模块如果按 `owner_type + owner_id` 查询附件，可能被写入污染引用或形成越权可见性问题。 | 重大 | 第一批如只落个人云文件，应先把员工端上传限制为 `PERSONAL_FILE`；如要开放业务附件，应在文件中心确认前调用对应业务模块的归属与权限校验。 |
| 1-2 | `createAttachmentRef` | 业务引用创建统一写入 `visibility_scope = 'OWNER'`，但当前没有把 `OWNER` 具体解释为个人、审批单、消息、知识库空间等业务上下文。该字段先占位是合理的，但如果继续扩展而不收口权限语义，会让文件中心承担不清晰的授权责任。 | 建议 | 下一批接入跨模块附件前，先定义 `ownerType` 到授权策略的映射，不建议在 Controller 或调用方各自临时判断。 |

### 二、幂等与并发一致性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `confirmEmployeeUpload` + `V3__link_attachment_to_upload_ticket.sql` | 重复确认上传的幂等依赖先读到 `status = CONFIRMED`，再通过 `source_upload_ticket_id` 回查附件引用。但两个请求并发进入时都可能读到 `PENDING`，随后各自创建附件引用、增加用量并更新票据状态。V3 只给 `source_upload_ticket_id` 建普通索引，没有唯一约束，无法阻止同一票据产生多条附件引用。 | 重大 | 需要作为下一批优先设计点处理：对上传票据确认加行级锁、条件更新或唯一约束，确保同一上传票据只会创建一条有效附件引用。 |
| 2-2 | `createEmployeeUploadTicket` | `clientRequestId` 的顺序重试已经能回查同一票据，但并发重复请求仍可能同时穿过前置查询并触发唯一键冲突。当前没有捕获该唯一键后回查，可能把客户端正常重试变成 500。 | 重大 | 第二批应把幂等逻辑统一成“先查、插入冲突后再查”的模式，并补并发重复申请测试。 |
| 2-3 | `increaseUsage` | 用量行不存在时先查再插入，存在时累加。该流程在串行测试下成立，但并发首次上传时会出现重复插入或唯一键冲突；如果唯一约束没有正确覆盖空作用域，还会形成多条用量行。 | 重大 | 用量账本建议统一封装为仓储能力，明确行锁、原子更新和唯一键策略，避免每条业务链路各自处理。 |

### 三、数据模型与 MySQL NULL 语义

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `uk_quota_scope_active` | MySQL 唯一索引允许多条 `NULL`，所以 `scope_type, scope_id, is_deleted` 对 `GLOBAL`、`USER_DEFAULT` 等 `scope_id = NULL` 的配额策略不能形成真正唯一约束。后续如果写入多条启用策略，`resolveQuota` 会按 `updated_at` 取一条，结果受数据写入顺序影响。 | 重大 | 使用规范化 `scope_key`、哨兵 `scope_id` 或生成列表达空作用域，确保全局和默认策略也被数据库唯一性保护。 |
| 3-2 | `uk_usage_scope` | `sys_storage_usage(scope_type, scope_id)` 同样无法约束 `scope_id = NULL` 的全局用量行。并发或手工修复数据时可能出现多条 `GLOBAL` 用量，服务层 `resolveUsedBytes` 只读第一条，会导致容量判断失真。 | 重大 | 与配额策略采用同一套空作用域唯一性方案，并补数据一致性检查脚本或测试。 |
| 3-3 | `uk_file_sha256_size_active` | `content_sha256 + file_size + is_deleted` 能支持物理去重，但当前唯一键包含 `is_deleted`。如果未来软删除后重新上传同一文件，是否允许复用旧物理对象、是否恢复旧对象或新建对象，需要提前定义，否则清理任务和秒传语义容易分叉。 | 建议 | 在实现物理清理任务前明确文件对象生命周期，避免“逻辑删除对象”和“可秒传对象”混用。 |

### 四、下载与接口完整性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `createEmployeeDownloadTicket` | 当前只创建下载票据并返回 `/api/employee/files/download/{ticketNo}`，但 Controller 没有对应 `GET` 下载执行入口。客户端拿到票据后无法实际下载文件，票据过期、使用状态、二进制响应和执行审计也都未落地。 | 重大 | 如果第一批刻意只发票据，需要在文档和响应语义中明确“执行入口待实现”；下一批应一次性设计下载执行链路，不建议临时补一个只读本地文件的窄接口。 |
| 4-2 | `ModuleFileApiIntegrationTest` | 下载测试只断言 `downloadUrl` 包含固定路径，无法发现下载入口不存在的问题。测试保护的是字符串格式，不是用户可完成下载的业务结果。 | 建议 | 等下载执行入口进入开发范围后，用真实二进制响应、过期票据、重复使用和越权访问覆盖测试。 |

### 五、测试可靠性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 5-1 | `LocalIntegrationTestSupport.resetBusinessTables` | 测试基类会清理文件中心核心表，但没有清理 `sys_audit_log`。`ModuleFileApiIntegrationTest` 又通过 `COUNT(action_type) >= N` 断言审计写入，历史数据可能让当前路径没有写审计也通过。 | 重要 | 测试数据隔离应覆盖 `sys_audit_log`，或把审计断言限定到本次生成的 target、operator、时间窗口或 request 上。 |
| 5-2 | `ModuleFileApiIntegrationTest` | 当前集成测试覆盖串行主链路较完整，但没有覆盖并发上传、并发确认、并发配额扣减和并发幂等。这些正是文件中心最容易出错的状态写入点。 | 重要 | 第二批应增加并发专项测试，先用测试暴露语义，再统一设计锁、唯一约束和冲突回查策略。 |

---

## 第二部分：冗余与可维护性审查

### 一、服务类职责过重

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `FileCenterService` | 单个服务类同时承担 Controller 编排、SQL 读写、文件 Provider、本地文件校验、配额账本、审计写入、DTO 组装和部分权限判断。第一批快速落地可以接受，但继续追加 MinIO、真实下载、后台治理后会变成难以理解和回归的中心类。 | 重要 | 下一批不要在该类继续直接加大量方法，建议先拆出 `StorageProvider`、`FileObjectRepository`、`AttachmentRefRepository`、`StorageUsageRepository`、`FileAuditService`。 |
| 6-2 | `FileCenterService.audit` | 审计详情通过字符串拼 JSON，并在 SQL 中 `CAST(? AS JSON)`。当前输入很少，风险暂时可控，但后续字段增多后容易出现转义遗漏、字段格式不一致和审计结构不可演进。 | 建议 | 后续改为结构化对象序列化，统一由审计服务负责写入，不建议各业务方法手写 JSON 字符串。 |
| 6-3 | `FileCenterService` 中 SQL 字符串 | 所有 SQL 直接散落在服务方法和私有方法内，短期直观，但领域规则、表结构和查询口径耦合很深。未来表结构调整时，风险会分散在多个方法里。 | 建议 | 用仓储层收口表访问，先按 `file_object`、`attachment_ref`、`upload_ticket`、`storage_usage` 拆分即可，不需要过度抽象。 |

### 二、接口语义与阶段性占位

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 7-1 | `UploadTicketResponse.uploadUrl` | LOCAL 模式返回的是本地文件路径，语义上更像服务端落盘位置，不是真正可由前端上传的 HTTP 预签名地址。这个占位有利于集成测试，但容易让接口使用方误解。 | 建议 | 在真实 Provider 抽象前，文档中应标注 LOCAL 为测试/开发链路；接入 MinIO 后再统一 `uploadUrl` 与 `headers` 的外部语义。 |
| 7-2 | `AdminFileController` | 后台端目前只有 `storage-summary`，而 API 文档中已有更多后台契约。代码中保留轻量 Controller 没有明显冗余，但要避免后续每个后台治理动作都直接堆到同一服务类。 | 建议 | 后台治理进入开发时单独划分管理服务，不要把员工端主链路和后台治理规则混在同一批补丁里。 |

---

## 第三部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 7 | 1-1、2-1、2-2、2-3、3-1、3-2、4-1 |
| 重要 | 3 | 5-1、5-2、6-1 |
| 建议 | 7 | 1-2、3-3、4-2、6-2、6-3、7-1、7-2 |
| 合计 | 17 |  |

---

## 第四部分：处理优先级建议

**第一优先级（下一批设计前先收口）：**

1. 明确员工端第一批支持的 `ownerType` 范围，避免业务归属越权写入。
2. 统一上传票据确认幂等策略，保证同一票据不会并发创建多条附件引用。
3. 修正配额与用量表对 `scope_id = NULL` 的唯一性建模。
4. 决定 `downloadUrl` 是阶段性占位还是下一批必须落地的可执行接口。

**第二优先级（避免补丁叠加）：**

- 拆分 `FileCenterService` 职责，再继续接 MinIO、下载执行和后台治理。
- 为用量账本建立统一仓储与并发测试，而不是在单个业务方法里继续补锁。
- 清理测试隔离口径，避免审计断言被历史数据误报。

---

## 第五部分：阶段性结论

本次复核结论是：

`module-file` 第一批后端骨架具备串行主链路验证价值，但还不宜在现有结构上直接继续堆补丁。下一批应先收口权限边界、幂等并发、NULL 作用域唯一性和服务拆分，再进入 MinIO、真实下载和后台治理开发。

---

## 第六部分：处理记录

处理日期：2026-05-04

### 已修订

| 序号 | 处理状态 | 处理说明 |
| --- | --- | --- |
| 1-1 | 已修订 | 员工端上传与确认上传第一版收口为 `PERSONAL_FILE + ownerId=null`，拒绝 `WF_REQUEST`、`IM_MESSAGE` 等跨业务归属。 |
| 2-1 | 已修订 | 新增 `uk_attachment_upload_ticket_active`，服务层在确认前先按 `source_upload_ticket_id` 回查已有引用，唯一冲突后也回查原引用。 |
| 2-2 | 已修订 | 上传票据创建捕获 `clientRequestId` 唯一冲突并回查已有票据，避免并发重试冒成 `500`。 |
| 2-3 | 已修订 | 用量账本写入改为 `INSERT ... ON DUPLICATE KEY UPDATE`，收口首次写入并发窗口。 |
| 3-1 | 已修订 | `sys_storage_quota_policy` 新增 `scope_key` 生成列，空作用域统一为 `__NONE__`，重建唯一索引。 |
| 3-2 | 已修订 | `sys_storage_usage` 新增 `scope_key` 生成列，空作用域统一为 `__NONE__`，重建唯一索引。 |
| 5-1 | 已修订 | 集成测试重置补充清理 `sys_audit_log`，避免审计断言被历史数据污染。 |

### 已由第二批覆盖

| 序号 | 处理状态 | 处理说明 |
| --- | --- | --- |
| 4-1 | 已覆盖 | 第二批已落地 `GET /api/employee/files/download/{ticketNo}` 真实下载入口。 |
| 4-2 | 已覆盖 | 第二批测试已覆盖有效下载、过期下载和下载执行审计。 |

### 保留后续设计

| 序号 | 处理状态 | 后续处理 |
| --- | --- | --- |
| 1-2 | 后续处理 | 当前只开放个人文件，`visibility_scope='OWNER'` 暂不承担跨业务授权；跨模块附件接入前再定义 ownerType 到授权策略映射。 |
| 3-3 | 后续处理 | 物理对象软删除后是否复用旧对象，留到清理任务与物理生命周期设计阶段统一收口。 |
| 5-2 | 部分处理 | 本轮补了异常状态下重复确认幂等测试；真实多线程并发专项测试后续跟 Repository 拆分一起做。 |
| 6-1 | 后续处理 | `FileCenterService` 仍偏大，下一批后台治理前优先拆 Repository 与 AuditService。 |
| 6-2 | 后续处理 | 审计 JSON 结构化序列化留到 `FileAuditService` 拆分时统一处理。 |
| 6-3 | 后续处理 | SQL 仓储层收口留到 Repository 拆分批次。 |
| 7-1 | 部分处理 | API 文档已明确 LOCAL 为开发/测试链路，员工端第一版仅开放个人文件；MinIO 外部语义待容器联调校准。 |
| 7-2 | 后续处理 | 后台治理进入下一批时单独拆管理服务，避免继续堆到员工端主链路服务。 |
