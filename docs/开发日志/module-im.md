# module-im 开发日志

## 2026-05-12 IM 消息仓储拆分

### 背景

群治理 SQL 拆入 `ImGroupRepository` 后，`ImRepository` 仍约 891 行，继续承载消息分页、幂等查找、消息编辑撤回、发送抢序、会话最后消息更新和已读位点更新。消息主链路是 IM 与 Agent 回写、outbox 事件生产的共同基础，继续堆在主仓储里会影响后续拆 `ImMessageService` 和附件治理。

### 本轮完成内容

- 新增 `ImMessageRepository`，承载消息分页、按消息 ID 查询、按 `clientMessageId` 查询、消息插入、编辑、撤回、会话 `last_message` 更新、已读位点更新和发消息抢序。
- `ImService` 的员工发消息、消息列表、编辑、撤回、已读和附件下载中的消息查询切换到 `ImMessageRepository`。
- `ImAgentConversationService` 的 Agent 成功回写和失败系统提示写入切换到 `ImMessageRepository`。
- `ImRepository` 删除迁出的消息 SQL 和消息 RowMapper，主仓储从约 891 行降到约 643 行。
- 群创建初始成员写入同步切到 `ImGroupRepository.insertUserMember(...)`，避免群成员写 SQL 回流到主仓储。

### 本轮边界

- 本轮只做消息仓储职责拆分，不新增 API。
- 本轮不新增 Flyway 迁移。
- 附件引用、下载票据、审计写入、会话列表、私聊/Agent 会话创建和会话偏好仍保留在 `ImRepository`，后续继续拆。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- IM 员工端主测试：`cd server && env TEST_DB_NAME=wfh_im_message_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过。
- 群治理专项：`cd server && env TEST_DB_NAME=wfh_im_message_repo_group_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test`
  - 结果：通过。
- outbox 专项：`cd server && env TEST_DB_NAME=wfh_im_message_repo_outbox_$(date +%s) mvn -q -Dtest=ModuleImOutboxIntegrationTest test`
  - 结果：通过。

## 2026-05-12 IM 群治理仓储拆分

### 背景

第 91 审把群聊资料与成员治理从 `ImService` 拆入 `ImGroupService` 后，群治理 SQL 仍留在 `ImRepository` 主仓储内。继续维护群成员、群资料或会话详情时，仍会触碰消息、附件、私聊和 Agent 会话同一个大仓储文件，后续接 admin-web 或继续拆消息服务时改动面过大。

### 本轮完成内容

- 新增 `ImGroupRepository`，承载群成员校验、群聊存在性、群资料读取/更新、成员列表、成员退群/移除、群解散、群会话与群资料创建。
- `ImGroupService` 切换为依赖 `ImGroupRepository` 执行群治理 SQL，继续保留 `ImRepository` 用于当前员工准入、会话摘要读取、会话存在性、会话成员关系和推送收件人查询。
- `ImRepository` 删除群治理专属 SQL 和迁出后无调用的成员 Row mapper，主仓储从约 1220 行降到约 891 行。
- `ImGroupRepository` 约 356 行，群治理 SQL 形成独立仓储边界，接口路径、响应结构、WebSocket/outbox 事件和错误语义均不变。

### 本轮边界

- 本轮只做仓储职责拆分，不新增 API。
- 本轮不新增 Flyway 迁移。
- `ImRepository` 仍超过 600 行阈值，剩余会话列表、消息、附件、私聊和 Agent 会话共享 SQL 需要继续按聚合拆分。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- 群治理专项：`cd server && env TEST_DB_NAME=wfh_im_group_repo_split_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test`
  - 结果：通过。
- IM 员工端主测试：`cd server && env TEST_DB_NAME=wfh_im_api_group_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过。

## 2026-05-12 IM 群治理服务拆分

### 背景

`ImService` 已同时承担会话列表、私聊、群治理、消息发送、附件下载、编辑撤回、已读、偏好和 Agent 触发。outbox 和 Agent 会话服务拆分后，群聊资料与成员治理仍留在主服务内，继续扩展会让员工端主消息链路和群治理边界互相污染。

### 本轮完成内容

- 新增 `ImGroupService`，承载群聊创建、群成员列表、群资料读取/更新、成员添加/移除、退群和解散。
- `EmployeeImController` 的群聊接口改为直接调用 `ImGroupService`。
- `ImService` 删除群治理公开方法和对应私有校验/转换辅助，主类行数从约 1033 行降到约 793 行。
- `ImService.getCurrentEmployeeConversationDetail(...)` 继续通过同包方法复用 `ImGroupService` 的群成员和群资料读模型，保持会话详情响应不变。
- 群治理 WebSocket/outbox 事件类型、payload、接口路径和错误语义均保持不变。

### 本轮边界

- 本轮只做服务职责拆分，不新增 API。
- 本轮不新增 Flyway 迁移。
- 本节点完成时 `ImRepository` 尚未拆分；后续已于 2026-05-12 的群治理仓储拆分节点迁出群治理 SQL。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- 群治理专项：`cd server && env TEST_DB_NAME=wfh_im_group_service_split_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test`
  - 结果：通过。
- IM 员工端主测试：`cd server && env TEST_DB_NAME=wfh_im_api_group_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过。

## 2026-05-12 IM outbox 审计动作常量收敛

### 背景

outbox 后台操作审计动作同时用于写入审计、查询白名单和审计 SQL 过滤。此前这些字符串在操作服务、查询服务和审计仓储中分别维护，后续新增后台治理动作时容易出现“审计已写入但查询接口不支持”的漏同步。

### 本轮完成内容

- 新增 `ImOutboxAdminAuditActions`，集中定义 outbox 后台操作审计动作。
- `ImOutboxAdminService` 写入单条重试、批量重试、主动派发审计时改为引用共享常量。
- `ImOutboxAdminQueryService` 的 `actionType` 查询白名单改为复用 `SUPPORTED_ACTION_TYPES`。
- `ImOutboxAdminAuditRepository` 审计 SQL 过滤改为参数化引用共享常量，不再在 SQL 字符串中硬编码动作值。
- 常量类落在 `im.support` 包，避免 repository 反向依赖 service 层。

### 本轮边界

- 本轮只收敛审计动作常量，不新增审计动作。
- 测试中的审计动作字符串断言保留，用于保护后台 API 契约不被误改。
- 审计 detail JSON 的 eventId 反查仍依赖 `JSON_CONTAINS`，后续高频查询时再考虑明细索引表。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 审计动作目标用例：`cd server && env TEST_DB_NAME=wfh_im_audit_actions_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test`
  - 结果：通过。

## 2026-05-12 IM outbox 事件类型常量收敛

### 背景

outbox 事件已经覆盖消息、群治理、偏好、已读和 Agent 回写。后台查询服务如果继续独立维护事件类型白名单，后续新增 WebSocket 事件时容易出现“事件已入库但后台筛选不支持”的漏同步问题。

### 本轮完成内容

- 新增 `ImWebSocketEventTypes`，集中定义当前 IM WebSocket/outbox 事件类型。
- `ImService` 和 `ImAgentConversationService` 发送 WebSocket/outbox 事件时改为引用常量，payload 中的 `eventType` 字符串保持不变。
- `ImOutboxAdminQueryService` 的 outbox eventType 查询白名单改为复用 `ImWebSocketEventTypes.OUTBOX_EVENT_TYPES`。
- 编译验证通过，接口契约和数据库结构不变。

### 本轮边界

- 本轮只收敛事件类型常量，不新增事件类型。
- 操作审计 action type 已在后续“IM outbox 审计动作常量收敛”节点单独收敛。
- 测试中的字符串断言保留，用于保护前端可见事件名不被误改。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox eventType 筛选目标用例：`cd server && env TEST_DB_NAME=wfh_im_event_types_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test`
  - 结果：通过。

## 2026-05-12 IM outbox 后台仓储细分

### 背景

第 86 审完成服务拆分后，`ImOutboxAdminRepository` 仍然同时承载事件分页、详情、指标聚合、状态重置、审计查询和审计写入 SQL。继续增加 admin-web 排障能力时，仓储层会重新变成混杂大类。

### 本轮完成内容

- 删除聚合式 `ImOutboxAdminRepository`。
- 新增 `ImOutboxAdminEventRepository`，负责 outbox 事件分页、详情和手动重试状态重置。
- 新增 `ImOutboxAdminMetricsRepository`，负责状态总览、状态分布和失败原因聚合。
- 新增 `ImOutboxAdminAuditRepository`，负责 outbox 操作审计写入和审计分页查询。
- 新增 `ImOutboxAdminResponseMapper`，统一 outbox 事件 Row 到后台响应 DTO 的转换，避免操作服务和查询服务重复维护字段映射。
- `ImOutboxAdminService`、`ImOutboxAdminQueryService`、`ImOutboxAdminMetricsService` 分别依赖对应仓储，接口路径和响应结构不变。

### 本轮边界

- 本轮只做仓储职责拆分，不新增 API、不新增 Flyway 迁移。
- 本轮没有迁移 outbox 集成测试类，`ModuleImApiIntegrationTest` 仍承载 outbox 后台目标用例。
- 事件类型白名单已在后续“IM outbox 事件类型常量收敛”节点收敛成共享常量。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台仓储细分目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_repo_split2_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds+adminShouldSummarizeImOutboxEvents+adminShouldGetImOutboxFailureSummary test`
  - 结果：通过。

## 2026-05-12 IM outbox 后台服务拆分

### 背景

outbox 后台已经连续补齐分页、详情、手动重试、批量重试、主动派发、操作审计、审计查询、状态总览和失败原因聚合。原 `ImOutboxAdminService` 同时承担查询、指标、操作和审计 detail 组装，继续叠 admin-web 后台能力会让单类职责失控。

### 本轮完成内容

- 新增 `ImOutboxAdminQueryService`，承载 outbox 事件分页、详情、审计分页、查询条件归一和 DTO 转换。
- 新增 `ImOutboxAdminMetricsService`，承载状态总览和失败原因聚合。
- `ImOutboxAdminService` 收口为操作服务，只保留单条重试、批量重试、主动派发和操作审计 detail 构造。
- `AdminImOutboxController` 改为分别依赖操作、查询、指标三个服务，接口路径、请求参数和响应结构保持不变。
- 拆分后 `ImOutboxAdminService` 降到 256 行，`ImOutboxAdminQueryService` 为 166 行，`ImOutboxAdminMetricsService` 为 79 行。

### 本轮边界

- 本轮只做服务职责拆分，不新增 API、不新增 Flyway 迁移。
- outbox 后台仓储已在后续“IM outbox 后台仓储细分”节点继续拆分。
- `ModuleImApiIntegrationTest` 在本节点仍是 IM/outbox 集成测试承载类；后续已于 2026-05-12 的 outbox 专项测试拆分节点迁出 `ModuleImOutboxIntegrationTest`。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台服务拆分目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_split_admin_verify_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds+adminShouldSummarizeImOutboxEvents+adminShouldGetImOutboxFailureSummary test`
  - 结果：通过。

## 2026-05-12 IM outbox 失败原因聚合接口

### 背景

状态总览能告诉 admin-web 当前 outbox 有多少积压和 stale `SENDING`，但还不能说明“为什么失败”。如果前端只能从分页列表里人工扫 `lastError`，演示排障和后续运营告警都不够直接。

### 本轮完成内容

- 新增 `GET /api/admin/im/outbox-events/failure-summary`。
- 新增 `AdminImOutboxFailureSummaryResponse` 和 `AdminImOutboxFailureSummaryRow`。
- 聚合规则：
  - 只统计 `FAILED`、`DEAD`。
  - 按 `status + lastError` 分组。
  - 空 `last_error` 归一为 `UNKNOWN`。
  - 返回 `eventCount`、`maxRetryCount`、`firstOccurredAt`、`lastOccurredAt`。
  - 最多返回 20 条，排序为 `eventCount DESC`、`lastOccurredAt DESC`、`lastError ASC`。
- 固定路由 `/failure-summary` 位于 `/{eventId}` 前，避免被路径变量误吞。
- `ModuleImApiIntegrationTest.adminShouldGetImOutboxFailureSummary` 覆盖失败原因聚合、`SENT` 排除、空错误归一和员工访问 403。

### 本轮边界

- 失败聚合接口只读，不触发重试、派发或审计写入。
- 聚合口径面向运营排障，不替代分页明细和操作审计。
- 本轮不新增索引；如果失败事件量上来，再补 `status/updated_at` 或错误摘要索引。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 失败原因聚合目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_failure_summary_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldGetImOutboxFailureSummary test`
  - 结果：通过。

## 2026-05-12 IM outbox 状态总览接口

### 背景

outbox 后台已经具备分页、详情、单条/批量重试、主动派发和操作审计查询，但 admin-web 首页或排障面板仍需要一个轻量总览接口。如果前端每次都扫分页列表再自行聚合，既慢，也容易遗漏 `SENDING` stale 判定规则。

### 本轮完成内容

- 新增 `GET /api/admin/im/outbox-events/summary`。
- 新增 `AdminImOutboxSummaryResponse`：
  - `totalCount`
  - `dueDispatchCount`
  - `staleSendingCount`
  - `earliestCreatedAt`
  - `earliestNextRetryAt`
  - `generatedAt`
  - `sendingStaleBefore`
  - 固定顺序 `statusCounts`
- 新增 outbox 聚合 Row：
  - `AdminImOutboxStatusSummaryRow`
  - `AdminImOutboxSummaryAggregateRow`
- `ImOutboxAdminRepository` 增加状态分组和总览聚合 SQL。
- `ImOutboxAdminService` 复用 `workforce.im.outbox.dispatcher.sending-stale-ms` 计算 stale 截止时间，保证总览口径和 dispatcher 抢占口径一致。
- `ModuleImApiIntegrationTest.adminShouldSummarizeImOutboxEvents` 覆盖五类状态计数、待派发数、stale `SENDING` 数、最早时间和员工访问 403。

### 本轮边界

- 总览接口只读，不触发派发，不写操作审计。
- `dueDispatchCount` 是当前时刻可抢占事件数，不等于下一次派发一定成功数。
- `statusCounts` 固定返回 `PENDING/SENDING/SENT/FAILED/DEAD`，缺失状态返回 `0`，方便前端直接渲染。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 状态总览目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_summary_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldSummarizeImOutboxEvents test`
  - 结果：通过。

## 2026-05-12 IM outbox 批量重试接口

### 背景

outbox 后台治理已经支持单条查询、单条重试、主动派发和操作审计查询，但 admin-web 排障时常见场景是一次选择多个 `FAILED/DEAD` 事件批量恢复。如果只提供单条重试，前端需要循环调用，失败明细和审计批次都不清楚。

### 本轮完成内容

- 新增 `POST /api/admin/im/outbox-events/batch-retry`。
- 新增 `AdminImOutboxBatchRetryRequest` 和 `AdminImOutboxBatchRetryResponse`：
  - 请求 `eventIds` 不能为空，单次最多 `100` 个。
  - 响应返回 `requestedCount`、`succeededCount`、`skippedCount` 和逐条 `items`。
- 批量重试采用部分成功语义：
  - `FAILED/DEAD` 且 payload 校验通过的事件会重置为可立即投递。
  - `SENT/PENDING/SENDING`、不存在事件、payload 异常事件会进入 skipped，不阻断整批。
- 批量重试写入 `sys_audit_log`：
  - `action_type=IM_OUTBOX_BATCH_RETRY_REQUESTED`
  - `target_type=IM_EVENT_OUTBOX_BATCH_RETRY`
  - `result=SUCCESS` 或 `PARTIAL_SUCCESS`
  - detail 包含 `requestedEventIds`、`succeededEventIds`、`skippedEventIds`
- 审计查询接口白名单同步支持 `IM_OUTBOX_BATCH_RETRY_REQUESTED`，并支持按批量审计 detail 中的 eventId 反查。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 覆盖批量重试成功、非法状态跳过、不存在事件跳过、批量审计写入和按 eventId 查询批量审计。

### 本轮边界

- 批量重试不直接派发，只把成功事件重置为可立即投递；派发仍由 dispatcher 或 `POST /dispatch` 负责。
- 本轮不新增 outbox 批次表，批次摘要先进入 `sys_audit_log.detail_json`。
- 本轮不改变单条重试接口语义。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 批量重试目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_validation_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds test`
  - 结果：通过。
- dispatcher 失败重试兼容性：`cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_dispatcher_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test`
  - 结果：通过。
- IM/Agent 组合回归：`cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_combo_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过。

## 2026-05-12 IM outbox 操作审计查询接口

### 背景

第 81 审后，outbox 手动重试和主动派发已经写入 `sys_audit_log`，派发审计也包含 eventId 明细。但 admin-web 仍缺一个受控查询入口；如果前端直接依赖通用审计表，会把 outbox 治理和全局审计浏览混在一起，权限和筛选语义都不清楚。

### 本轮完成内容

- 新增后台接口 `GET /api/admin/im/outbox-events/audits`，只返回 IM outbox 相关操作审计。
- 支持按 `actionType`、`eventId`、`operatorId`、`occurredFrom`、`occurredTo` 分页查询。
- `eventId` 查询同时覆盖：
  - 单条重试审计的 `target_id`
  - 主动派发审计 `detail_json` 里的 `attemptedEventIds`
  - 主动派发审计 `detail_json` 里的 `dispatchedEventIds`
  - 主动派发审计 `detail_json` 里的 `failedEventIds`
- 新增 `AdminImOutboxAuditQuery`、`AdminImOutboxAuditResponse`、`AdminImOutboxAuditQueryCriteria`、`AdminImOutboxAuditRow`。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加审计查询断言，覆盖按 eventId 查出重试和派发两类审计，以及非法 actionType 返回 `outbox审计动作不支持`。

### 本轮边界

- 该接口不是通用审计日志浏览器，只允许查询 `IM_OUTBOX_RETRY_REQUESTED` 和 `IM_OUTBOX_DISPATCH_TRIGGERED`。
- 本轮不新增 Flyway 迁移，复用既有 `sys_audit_log`。
- 本轮不做批量重试，也不新增 outbox 批次表。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 操作审计查询目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_audit_api_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test`
  - 结果：通过。

## 2026-05-12 IM outbox 派发审计明细

### 背景

第 80 审补齐 outbox 后台操作审计后，主动派发审计仍只记录 `requestedBatchSize` 和 `dispatchedCount`。如果一次派发多个事件，后台只能通过时间窗口反查具体 outbox 事件，不利于 admin-web 操作历史和排障。

### 本轮完成内容

- 新增 `ImWebSocketOutboxDispatchResult`，记录本次 dispatcher 抢占、成功派发和失败派发的 eventId 列表。
- `ImWebSocketOutboxDispatcher.dispatchDueEvents(int)` 保持原返回数量语义，新增 `dispatchDueEventsWithResult(int)` 供后台治理服务写审计明细。
- `ImOutboxAdminService` 主动派发审计详情补充：
  - `attemptedCount`
  - `attemptedEventIds`
  - `dispatchedEventIds`
  - `failedEventIds`
- 重试审计详情补充 `eventId`，便于按事件还原管理员操作。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加审计 detail JSON 断言。

### 本轮边界

- 后台派发 API 响应保持 `requestedBatchSize/dispatchedCount` 不变，不把 eventId 明细暴露给前端接口。
- 本轮不新增审计查询接口，仍只写 `sys_audit_log`。
- after-commit 即时派发和定时 dispatcher 仍使用原 `dispatchDueEvents(...)` 数量语义。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台派发审计明细与失败重试组合：`cd server && env TEST_DB_NAME=wfh_im_outbox_dispatch_detail_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test`
  - 结果：通过。

## 2026-05-12 IM outbox 后台操作审计

### 背景

后台 outbox API 已支持查询、详情、手动重试和主动派发，但第 79 审指出批量治理前需要先补可追溯操作审计，否则 admin-web 上线后管理员点击重试或派发按钮无法还原操作人和操作结果。

### 本轮完成内容

- `POST /api/admin/im/outbox-events/{eventId}/retry` 成功后写入 `sys_audit_log`：
  - `operator_type=ADMIN`
  - `action_type=IM_OUTBOX_RETRY_REQUESTED`
  - `target_type=IM_EVENT_OUTBOX`
  - `target_id=eventId`
- `POST /api/admin/im/outbox-events/dispatch` 每次主动派发后写入 `sys_audit_log`：
  - `operator_type=ADMIN`
  - `action_type=IM_OUTBOX_DISPATCH_TRIGGERED`
  - `target_type=IM_EVENT_OUTBOX_DISPATCH`
- 审计详情 JSON 记录 eventType、conversationId、messageId、前后状态、retryCount、lastError、requestedBatchSize 和 dispatchedCount 等排障字段。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加重试审计和派发审计断言。

### 本轮边界

- 本轮不新增审计查询接口，复用既有 `sys_audit_log` 表。
- 失败的非法状态重试仍返回业务错误，不写成功审计。
- 本轮不实现批量重试；批量操作前仍需补批量请求语义和审计摘要。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台治理、操作审计与失败重试组合：`cd server && env TEST_DB_NAME=wfh_im_outbox_admin_audit_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test`
  - 结果：通过。

## 2026-05-12 IM outbox 后台读模型仓储拆分

### 背景

第 78 审补齐后台 outbox 查询、详情、重试和主动派发接口后，`ImEventOutboxRepository` 同时承载 dispatcher 投递状态机和后台运营查询。继续扩展 admin-web、失败聚合或批量治理时，这两类 SQL 混在一起会提高误伤投递状态机的风险。

### 本轮完成内容

- 新增 `ImOutboxAdminRepository`，专门承载后台 outbox 分页查询、详情查询和人工重试状态重置。
- 新增 `AdminImOutboxEventQueryCriteria` 与 `AdminImOutboxEventRow` 到 `im.repository` 包，避免后台读模型继续挂在 `websocket` 包下。
- `ImEventOutboxRepository` 只保留 outbox 入账、claim、`SENDING/SENT/FAILED/DEAD` 状态推进等投递写模型职责。
- `ImOutboxAdminService` 改为依赖 `ImOutboxAdminRepository` 和 `ImWebSocketOutboxDispatcher`，后台 API 路径、请求参数和响应字段保持不变。

### 本轮边界

- 本轮不新增 Flyway 迁移，不改 `im_event_outbox` 表结构。
- 本轮不新增后台 outbox API，也不改变前端/admin-web 契约。
- `ModuleImApiIntegrationTest` 在本节点暂未拆分；后续已于 2026-05-12 的 outbox 专项测试拆分节点迁出 `ModuleImOutboxIntegrationTest`。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台治理与失败重试组合：`cd server && env TEST_DB_NAME=wfh_im_outbox_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test`
  - 结果：通过。

## 2026-05-12 IM outbox 后台治理接口

### 背景

第 77 审后，IM WebSocket 推送已经通过 `im_event_outbox` 实现可靠账本、失败重试和 `DEAD` 状态，但管理员仍只能通过数据库查看失败事件。后续 admin-web 需要可直接接入的只读排障和手动补偿入口。

### 本轮完成内容

- 新增后台 outbox 治理接口：
  - `GET /api/admin/im/outbox-events`
  - `GET /api/admin/im/outbox-events/{eventId}`
  - `POST /api/admin/im/outbox-events/{eventId}/retry`
  - `POST /api/admin/im/outbox-events/dispatch`
- 新增 `ImOutboxAdminService`，负责后台查询条件校验、事件详情转换、失败事件重试和主动触发 dispatcher。
- 新增后台查询/响应 DTO：
  - `AdminImOutboxEventQuery`
  - `AdminImOutboxEventResponse`
  - `AdminImOutboxDispatchRequest`
  - `AdminImOutboxDispatchResponse`
- `ImEventOutboxRepository` 增加分页查询、详情查询和手动重试状态重置方法。
- 手动重试只允许 `FAILED/DEAD` 事件；`SENDING` 事件仍由 dispatcher 的 stale reclaim 处理，避免管理员重试和正在发送的线程竞争。
- 重试前校验 `payloadJson.eventType` 与 `payloadJson.conversationId` 是否和索引字段一致；不一致或 JSON 损坏时返回 `409`，要求人工核查。
- `ModuleImApiIntegrationTest` 增加管理员查询、详情、状态过滤、员工越权、手动重试、主动派发和 `SENT` 不可重试覆盖。

### 本轮边界

- 不新增 Flyway 迁移，复用 V14 `im_event_outbox`。
- 不改变员工端 IM API 和 WebSocket payload。
- 本轮只补后台 API，不启动 admin-web 页面。
- 无在线用户时 dispatcher 仍按当前 outbox 语义标记 `SENT`，离线补齐继续依赖会话列表和消息分页。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 后台治理目标用例：`cd server && env TEST_DB_NAME=wfh_im_outbox_admin_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test`
  - 结果：通过。
- outbox 后台治理与失败重试组合：`cd server && env TEST_DB_NAME=wfh_im_outbox_admin2_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test`
  - 结果：通过。
- IM 员工端全类：`cd server && env TEST_DB_NAME=wfh_im_outbox_full_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过，48 个用例全部通过。
- IM/Agent 组合回归：`cd server && env TEST_DB_NAME=wfh_im_outbox_combo_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，95 个用例全部通过。

## 2026-05-11 IM WebSocket outbox 可靠投递

### 背景

IM 消息和 Agent 回写已经能完成演示闭环，但此前 WebSocket 推送发生在业务事务提交后直接调用内存连接。数据库提交成功后如果推送失败或进程崩溃，前端可能收不到实时事件，且后端没有可追踪的失败账本。

### 本轮完成内容

- 新增 Flyway 迁移 `V14__add_im_event_outbox.sql`，创建 `im_event_outbox`。
- 新增 outbox 写入与投递组件：
  - `ImEventOutboxRepository`
  - `ImEventOutboxRow`
  - `ImWebSocketOutboxService`
  - `ImWebSocketOutboxDispatcher`
  - `ImWebSocketOutboxProperties`
- `ImWebSocketPushService.pushToUsersAfterCommit(...)` 改为在业务事务内写 outbox，并在事务提交后唤醒 dispatcher。
- dispatcher 支持 `PENDING/FAILED` 到期批量抢占、`SENDING` 投递、成功标记 `SENT`、失败标记 `FAILED`，超过最大次数标记 `DEAD`。
- 支持 `SENDING` 超时重扫，避免投递进程中断后事件永久卡住。
- dispatcher 抢占、成功和失败状态更新使用独立事务，避免事务提交回调阶段状态推进不确定。
- `IM_MESSAGE_CREATED`、`IM_AGENT_MESSAGE_CREATED`、`IM_AGENT_REPLY_FAILED` 等现有推送事件保持前端 payload 结构不变，只改变后端投递方式。
- `ModuleImApiIntegrationTest` 增加 outbox 失败重试覆盖：首次 WebSocket 抛出 IOException 后 outbox 记录 `FAILED`，重试后进入 `SENT`。

### 本轮边界

- 本轮不新增管理员 outbox 查询页面，失败运营入口留到后续 admin-web/后台治理阶段。
- 第一版 outbox 只覆盖 IM WebSocket 推送，不抽象成全局事件总线。
- 当前 dispatcher 仍在应用进程内执行，不引入 MQ；表结构已为多实例 `FOR UPDATE SKIP LOCKED` 抢占预留。
- 无在线用户时投递视为成功，WebSocket 只保证“事件被尝试投递到当前连接”，离线补偿仍依赖消息列表拉取。

### 验证记录

- 编译：`cd server && mvn -DskipTests compile test-compile`
  - 结果：通过。
- IM 专项：`cd server && env TEST_DB_NAME=workforce_hub_im_outbox_retry mvn -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过，45 个用例全部通过。
- IM/Agent 组合专项：`cd server && env TEST_DB_NAME=workforce_hub_outbox_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，92 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_outbox_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。
- 格式检查：`git diff --check`
  - 结果：通过。

## 2026-05-11 IM Agent 真实模型回写与失败幂等

### 背景

Agent 会话任务切换为真实模型异步执行后，IM 侧不能再假设回写会同步完成。成功回复、失败提示和 WebSocket 推送都必须能承受模型执行延迟、失败和重复回调。

### 本轮完成内容

- `ModuleImApiIntegrationTest` 增加本地 JDK HTTP stub，覆盖 OpenAI 兼容 `/v1/chat/completions` 成功回复。
- 员工在 Agent 会话发消息后，IM 列表等待异步模型任务回写真实模型文本，不再断言旧 mock 文案。
- 增加模型配置缺失用例，验证 Agent 任务进入 `FAILED`，IM 会话写入系统提示并推送 `IM_AGENT_REPLY_FAILED`。
- 成功 Agent 回复继续使用 `agent-task-{taskId}` 作为 `client_message_id`，重复成功回写不重复写消息。
- 失败 Agent 回写也使用同一幂等键写入系统提示，重复失败回写不重复生成系统消息。
- Agent 触发阶段失败使用 `agent-trigger-failure-{sourceMessageId}` 作为系统提示幂等键。

### 本轮边界

- 本轮仍保留当前 WebSocket 事务后直接推送方式，可靠 outbox 在下一节点单独实现。
- 失败提示文案仍为统一用户可见文案，不把模型供应商错误原文暴露给员工端。
- 本轮不新增 IM 表结构，复用 V13 的 `im_message.client_message_id` 唯一约束。

### 验证记录

- 编译与测试编译：`cd server && mvn -DskipTests compile test-compile`
  - 结果：通过，302 个主源码和 25 个测试源码编译成功。
- IM/Agent 回归：`cd server && env TEST_DB_NAME=workforce_hub_real_model_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，91 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_real_model_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。

## 2026-05-11 IM Agent 会话服务拆分

### 背景

真实模型异步执行和 WebSocket outbox 进入前，`ImService` 已经同时承担会话列表、消息发送、附件下载、编辑撤回、群治理、Agent 触发、Agent 回写和失败提示。继续直接叠加 outbox 会让事务边界和失败补偿混在主服务里，后续风险会放大。

### 本轮完成内容

- 新增 `ImAgentConversationService`，专门负责 Agent 会话创建、员工消息 afterCommit 触发 Agent、Agent 成功回写、Agent 失败系统提示。
- `ImService.startCurrentEmployeeAgentConversation(...)` 改为委托新服务，员工端接口路径不变。
- `ImService.sendCurrentEmployeeMessage(...)` 发送成功后改为委托新服务触发 Agent 回复，原先只处理 `USER + TEXT + AGENT会话` 的触发规则不变。
- `ImAgentResultCallback` 实现从 `ImService` 移到 `ImAgentConversationService`，跨模块回写入口更贴近 Agent 会话子域。
- 保持 `IM_AGENT_MESSAGE_CREATED` 与 `IM_AGENT_REPLY_FAILED` 现有推送行为不变，outbox 仍留在后续节点。

### 本轮边界

- 本轮只做服务拆分，不新增 Flyway 迁移。
- 本轮不接真实模型，Agent 回复仍由 `module-agent` 当前同步 mock 任务产出。
- 本轮不改员工端 IM API 响应结构，不改 WebSocket 事件结构。

### 验证记录

- 编译：`cd server && mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 296 个源文件编译成功。
- IM/Agent 回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，89 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。

## 2026-05-11 IM x Agent SPI 命令对象化

### 背景

第 75 审后，IM 会话与 Agent 已经能完成演示闭环，但跨模块 SPI 仍使用散参数：IM 触发 Agent 时只传会话、Agent、发送人和消息 JSON，Agent 回写 IM 时只传会话、Agent、任务和消息内容。真实模型异步执行后，`sourceMessageId`、`agentTaskId`、`clientRequestId`、`idempotencyKey`、失败类型等字段必须显式存在，否则后续 outbox、重试和失败可见性会继续靠隐式约定。

### 本轮完成内容

- 新增 `ImAgentTriggerCommand`，作为 IM 触发 Agent 的命令对象。
- 新增 `ImAgentTriggerResult`，作为 Agent 任务触发结果。
- 新增 `ImAgentReplyResult`，作为 Agent 回写 IM 的结果对象。
- `ImAgentTriggerService.triggerConversationReply` 从散参数改为接收命令对象。
- `ImAgentResultCallback` 从 `sendAgentMessage(...)` 改为 `onAgentReply(ImAgentReplyResult result)`。
- `ImService` 触发 Agent 时显式传入 `sourceMessageId` 和 `idempotencyKey=im-message-{sourceMessageId}`。
- `ImService` 接收 Agent 成功回写时继续复用 `agent-task-{taskId}` 消息幂等键，保持重复回写不重复生成消息。
- `ImService` 预留 `FAILED` 回写分支，失败结果会写入 Agent 失败系统提示消息。

### 本轮边界

- 本轮不接真实模型，仍保持现有同步 mock 回复行为。
- 本轮不新增 outbox 表，WebSocket 推送可靠投递在后续节点单独做。
- 本轮不拆 `ImService`，只调整跨模块契约，服务拆分在下一节点做。
- 本轮不新增 Flyway 迁移。

### 验证记录

- 编译：`mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 294 个源文件编译成功。

### 后续待补

- 拆出 `ImAgentConversationService`，把 Agent 触发、回写、失败提示从 `ImService` 主类中剥离。
- 接入真实大模型后，`FAILED` 回写应携带 `failureType` 和 `failureMessage`。
- outbox 节点把 `IM_AGENT_MESSAGE_CREATED` 与 `IM_AGENT_REPLY_FAILED` 改为先写账本再异步推送。

## 2026-05-12 IM outbox 专项集成测试拆分

### 背景

第 89 审后，outbox 后台治理、审计动作、状态总览和失败聚合都已经完成，但相关用例仍堆在 `ModuleImApiIntegrationTest` 中。主 IM 测试类同时覆盖员工端会话、消息、附件、偏好、Agent、WebSocket 和后台 outbox，后续继续扩展会增加测试定位成本。

### 本轮完成内容

- 新增 `ModuleImOutboxIntegrationTest`，承接 outbox 失败重试、后台查询/详情、手动重试、主动派发、批量重试、状态总览、失败原因聚合和后台权限校验。
- `ModuleImApiIntegrationTest` 移除 outbox 后台治理专项用例，保留员工端 IM 主链路和普通 WebSocket 推送用例。
- outbox 专项 seed 数据、批量重试参数构造、失败一次 WebSocket 会话等辅助方法迁入新测试类。
- 主测试类保留通用 WebSocket 事件断言和 outbox `SENT` 等待能力，因为普通消息、编辑、撤回、偏好推送仍需要验证实时事件落地。
- API 路径、响应结构、事件类型、审计 action type 和数据库结构均未改变。

### 本轮边界

- 本轮只调整测试组织，不新增业务代码。
- 本轮不新增 Flyway 迁移。
- 后台 outbox API 契约不变，前端无需调整调用参数或响应解析。

### 验证记录

- 编译与测试编译：`cd server && mvn -q -DskipTests compile test-compile`
  - 结果：通过。
- outbox 专项：`cd server && env TEST_DB_NAME=wfh_im_outbox_split_tests_$(date +%s) mvn -q -Dtest=ModuleImOutboxIntegrationTest test`
  - 结果：通过。
- IM 员工端主测试：`cd server && env TEST_DB_NAME=wfh_im_api_after_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test`
  - 结果：通过。
