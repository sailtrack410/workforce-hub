# module-agent 开发日志

## 2026-05-11 Agent 会话任务执行服务拆分

### 背景

真实大模型接入前，`AgentService` 同时承担后台治理、员工任务、工具调用确认、IM 会话任务创建、mock 回复生成和 IM 回写。模型执行改成异步后，任务状态流、模型调用、失败回写和重试会继续膨胀，因此需要先把会话任务执行边界拆出来。

### 本轮完成内容

- 新增 `AgentTaskExecutionService`，承接 IM 会话触发后的 Agent 任务创建、`PENDING -> RUNNING -> SUCCEEDED/FAILED` 状态推进、审计写入、mock 回复生成和 IM 成功回写。
- `AgentService.triggerConversationReply(ImAgentTriggerCommand command)` 保留 SPI 门面职责，只校验命令和触发人类型，然后委托执行服务。
- 继续保留 `agent-task-{taskId}` 作为 Agent 回复消息幂等键。
- 继续保留当前同步 mock 回复文案和审计动作，不在本轮引入外部模型、异步线程或任务队列。

### 本轮边界

- 本轮只拆执行服务，不新增数据库表或 Flyway 迁移。
- 本轮不改变后台 Agent 任务、工具调用、人工确认、超时扫描接口行为。
- 本轮不引入真实模型配置，模型接入在下一节点单独完成。

### 验证记录

- 编译：`cd server && mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 296 个源文件编译成功。
- IM/Agent 回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，89 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。

## 2026-05-11 IM x Agent SPI 命令对象化

### 背景

IM 会话 Agent 第一版用散参数 SPI 完成演示闭环，但真实模型异步执行需要把来源消息、任务、请求、幂等键和失败类型显式串起来。否则 Agent 后续切换到异步模型调用时，任务状态、IM 消息幂等和失败可见性会继续依赖隐式 JSON 字段。

### 本轮完成内容

- `AgentService` 实现新的 `ImAgentTriggerService.triggerConversationReply(ImAgentTriggerCommand command)`。
- `createConversationReplyTask` 改为从命令对象读取 `conversationId`、`agentId`、`triggerUserId` 和 `messageContentJson`。
- 触发结果改为返回 `ImAgentTriggerResult`，显式包含 `agentTaskId`、`status` 和 `idempotencyKey`。
- Agent 回复 IM 时改为构造 `ImAgentReplyResult` 并调用 `ImAgentResultCallback.onAgentReply(...)`。
- 当前仍保留同步 mock 回复，不在本轮引入真实模型客户端。

### 本轮边界

- 不改变 `agt_task` 表结构。
- 不改变任务状态流转，当前会话 Agent 任务仍同步从 `PENDING` 进入 `RUNNING` 并完成为 `SUCCEEDED`。
- 不新增模型配置和外部 HTTP 调用。
- 不新增 outbox。

### 验证记录

- 编译：`mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 294 个源文件编译成功。

### 后续待补

- 下一节点拆出 `AgentTaskExecutionService` 或 `AgentConversationService`，把会话 Agent 执行从 `AgentService` 主门面中剥离。
- 真实模型接入后，任务应先落 `RUNNING`，模型成功后回写 `SUCCEEDED`，模型失败后回写 `FAILED` 并通知 IM。
- 失败分类应对齐 `CONFIG_MISSING`、`AUTH_FAILED`、`TIMEOUT`、`RATE_LIMITED`、`PROVIDER_ERROR`、`INVALID_RESPONSE`、`UNKNOWN`。

## 2026-05-11 Agent 后台超时扫描批次查询

### 背景

上一轮已经把人工确认超时扫描升级为生产化任务，并把每次运行写入 `agt_task_timeout_job_run`。但管理员如果想确认最近是否扫描成功、是否被锁跳过、扫描了多少任务，仍然需要直接查数据库。这个缺口会影响后台可观测性，也会让超时扫描失败排查依赖开发人员介入。

### 本轮完成内容

- 新增后台超时扫描批次查询接口：`GET /api/admin/agent-tasks/timeout-scan-runs`。
- 新增查询 DTO：`AgentTaskTimeoutRunQuery`。
- 新增响应 DTO：`AdminAgentTaskTimeoutRunResponse`。
- 新增仓储查询条件和行对象：
  - `AgentTaskTimeoutRunQueryCriteria`
  - `AdminAgentTaskTimeoutRunRow`
- 支持按 `triggerType`、`status`、`startedFrom`、`startedTo` 筛选。
- 批次列表返回 `jobRunId`、触发方式、批次状态、批次大小、扫描数、取消数、跳过数、锁持有者、错误摘要和起止时间。
- 路由放在 `/api/admin/agent-tasks/timeout-scan-runs`，并显式置于 `/{taskId}` 前面，避免静态路径被任务详情动态路由误吞。

### 本轮修复的自审问题

- 上一轮第 61 审指出 `agt_task_timeout_job_run` 只有表和写入，没有后台查询入口。本轮补齐只读分页查询，管理员不再需要直接查库才能看扫描批次。
- `triggerType` 和 `status` 在服务层做白名单校验，避免任意字符串进入查询语义。
- 查询 SQL 使用参数化参数，动态 SQL 只拼接固定条件片段。
- 本轮不新增 Flyway 迁移，继续复用 V12 已有 `agt_task_timeout_job_run`。
- 本轮不做失败告警、长期等待升级策略和统一任务运行中心，避免查询节点膨胀。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldListTimeoutScanRuns test`
  - 结果：失败，`/api/admin/agent-tasks/timeout-scan-runs` 被 `/{taskId}` 误吞并返回 500。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldListTimeoutScanRuns test`
  - 结果：通过，覆盖手动扫描后按 `MANUAL/SUCCESS` 查询批次记录。
- 非法状态：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_invalid mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanRunListShouldRejectUnsupportedStatus test`
  - 结果：通过，非法批次状态返回 `code=400`、`message=Agent超时扫描批次状态不支持`。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下编译成功。
- 超时扫描专项：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_full mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，10 个用例，0 失败。
- Agent 模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_agent mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，26 个用例，0 失败。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_runs_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，67 个用例，0 失败。
- 差异格式检查：`git diff --check`
  - 结果：通过，未发现空白错误。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V12__add_agent_timeout_job_records.sql` 已创建的 `agt_task_timeout_job_run`。

### 后续待补

- 增加超时扫描失败告警和后台通知。
- 增加长期等待任务升级策略，例如提醒触发人、直属上级或管理员介入。
- 等 `file/workflow/agent` 都有后台任务运行记录后，再评估是否抽出统一 `sys_job_run` 和通用任务运行中心。

## 2026-05-11 Agent 人工确认超时扫描生产化

### 背景

上一轮后台已能手动触发人工确认超时扫描，但生产环境不能只依赖人工按钮。过期等待任务需要周期自动收口，并且每次扫描要能被追踪；如果未来出现多实例部署，也不能让多个实例同时取消同一批任务。

### 本轮完成内容

- 新增 Flyway 迁移 `V12__add_agent_timeout_job_records.sql`。
- 新增 `sys_job_lock`，作为后台任务数据库互斥锁。
- 新增 `agt_task_timeout_job_run`，记录每次 Agent 人工确认超时扫描批次。
- `AgentTaskTimeoutJobService` 新增生产化入口 `runTimeoutScan(triggerType, batchSize)`。
- `AgentTaskTimeoutJobService` 新增 `@Scheduled` 定时入口 `scheduledTimeoutScan()`。
- 后台手动扫描接口返回完整运行摘要：`jobRunId`、`triggerType`、`jobStatus`、`batchSize`、`scannedCount`、`cancelledCount`、`skippedCount`、`errorMessage`、`startedAt`、`finishedAt`。
- `application.yml` 新增 `workforce.agent.timeout-scan.*` 配置，支持环境变量控制启停、批次大小和定时间隔。
- 集成测试基线关闭定时副作用，避免长回归中后台定时任务抢跑影响断言。

### 本轮修复的自审问题

- 原手动扫描只返回 `cancelledCount`，无法区分“扫描到多少、取消多少、是否被锁跳过”。本轮改为返回批次运行摘要。
- 原服务没有生产化批次记录，排查时只能看任务终态。本轮新增 `agt_task_timeout_job_run`。
- 原服务没有任务级互斥，多实例下可能同时扫描同一批等待任务。现在使用 `sys_job_lock` 抢占，拿不到锁时记录 `SKIPPED` 批次。
- 自审发现把抢锁、扫描和释放放在同一个大事务里会让其他实例等待事务提交，不利于快速跳过。最终拆成“创建批次并抢锁”、“扫描取消”、“完成批次并释放锁”三个事务边界。
- 自审发现测试环境注册 `@Scheduled` 后可能在长回归中产生隐性副作用。现在测试基线设置 `workforce.agent.timeout-scan.enabled=false`，并补充禁用保护用例。
- 移除过时的 `AgentTaskTimeoutScanResponse.of(long)`，避免后续调用方误以为响应只有取消数量。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_prod_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldCreateJobRunRecordAndReturnSummary test`
  - 结果：失败，`AgentTaskTimeoutJobService#runTimeoutScan(String,int)` 尚不存在。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_prod_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldCreateJobRunRecordAndReturnSummary test`
  - 结果：通过，覆盖批次记录和运行摘要。
- 锁跳过与定时入口：`env TEST_DB_NAME=workforce_hub_agent_timeout_prod_more mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldSkipWhenAnotherInstanceHoldsLock,ModuleAgentTaskTimeoutIntegrationTest#timeoutScanServiceShouldExposeScheduledEntry test`
  - 结果：通过，覆盖锁占用时跳过和 `@Scheduled` 入口。
- 超时扫描专项：`env TEST_DB_NAME=workforce_hub_agent_timeout_prod_full mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，8 个用例，0 失败。

### 数据库说明

本轮新增 `V12__add_agent_timeout_job_records.sql`：

- `sys_job_lock`：后台任务互斥锁，当前用于 `AGENT_TASK_TIMEOUT_SCAN`。
- `agt_task_timeout_job_run`：Agent 人工确认超时扫描批次记录，记录触发方式、状态、扫描数、取消数、跳过数、错误摘要和起止时间。

### 后续待补

- 后台批次记录查询入口已在后续节点补齐。
- 增加扫描失败告警和长期等待任务升级策略。
- 等任务量增长后评估是否需要对 `agt_task.waiting_deadline_at/status` 增加更贴合超时扫描的组合索引。

## 2026-05-11 Agent 后台任务运行指标与失败聚合

### 背景

后台已经能查看任务列表、详情、工具调用明细和审计轨迹，但管理员仍需要一个更轻的总览入口判断当前 Agent 任务整体健康度。否则排查失败任务时只能逐页翻列表，无法快速判断失败集中在哪类错误、工具调用失败数量是否异常。

### 本轮完成内容

- 新增后台 Agent 任务运行指标接口：`GET /api/admin/agent-tasks/metrics`。
- 新增后台 Agent 任务失败聚合接口：`GET /api/admin/agent-tasks/failure-summary`。
- 新增响应 DTO：
  - `AdminAgentTaskMetricsResponse`
  - `AdminAgentTaskFailureSummaryResponse`
- 新增仓储查询行对象：
  - `AdminAgentTaskMetricsRow`
  - `AdminAgentTaskFailureSummaryRow`
- 运行指标返回任务总量、各状态数量、工具调用总数、失败工具调用数、已完成任务平均耗时和最近任务创建时间。
- 失败聚合按 `errorMessage` 汇总失败任务数量、失败工具调用数量和首次/最近发生时间。

### 本轮修复的自审问题

- 红灯阶段发现 `/api/admin/agent-tasks/metrics` 会被 `/{taskId}` 路由误吞，导致 `metrics` 转 Long 失败并返回 500。已新增明确静态路由 `/metrics` 和 `/failure-summary`。
- 运行指标不直接 `LEFT JOIN agt_tool_call_log` 后再 `COUNT(task.id)`，避免一个任务多个工具调用时放大任务数量。
- 失败聚合只统计 `task.status=FAILED`，并且只把 `result_status=FAILED` 的工具日志计入失败工具调用数。
- 本轮不新增筛选参数和索引迁移，先做全局只读总览，后续按使用情况补时间范围、Agent 维度和失败分类码。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_metrics_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskMetricsAndFailureSummary test`
  - 结果：失败，`GET /api/admin/agent-tasks/metrics` 被 `/{taskId}` 路由误解析，返回 500。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_metrics_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskMetricsAndFailureSummary test`
  - 结果：通过，覆盖成功、失败、等待人工确认三类任务的指标统计和失败原因聚合。
- 编译：`mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下编译成功。
- Agent 模块回归：`env TEST_DB_NAME=workforce_hub_agent_metrics_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，20 个用例，0 失败。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_metrics_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，61 个用例，0 失败。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task`、`agt_agent`、`agt_tool_call_log` 表。

### 后续待补

- 为运行指标增加时间范围、Agent、触发来源等筛选条件。
- 为失败聚合增加稳定失败分类码，避免长期依赖自然语言错误信息聚合。
- 接入后台告警入口；人工确认超时扫描批次记录查询已在后续节点补齐。

## 2026-05-11 Agent 后台任务工具调用明细与审计轨迹

### 背景

后台任务列表和详情已经能定位任务状态、触发主体、上下文和工具调用数量，但管理员排查 `FAILED`、`WAITING_HUMAN_CONFIRM`、`REJECTED`、`CANCELLED` 等任务时仍缺少两类关键证据：具体调用了哪些工具，以及任务状态为什么发生变化。为了让后续 workflow、IM 和运行监控接入时有可追溯基础，本轮补齐任务明细子资源。

### 本轮完成内容

- 新增后台 Agent 任务工具调用明细接口：`GET /api/admin/agent-tasks/{taskId}/tool-calls`。
- 新增后台 Agent 任务审计轨迹接口：`GET /api/admin/agent-tasks/{taskId}/audits`。
- 新增响应 DTO：
  - `AdminAgentTaskToolCallResponse`
  - `AdminAgentTaskAuditResponse`
- 新增仓储查询行对象：
  - `AdminAgentTaskToolCallRow`
  - `AdminAgentTaskAuditRow`
- 工具调用明细返回工具编码、工具名称、工具类型、风险等级、只读标记、请求载荷、响应载荷、审批状态、执行结果和调用时间。
- 审计轨迹返回动作类型、目标对象、决策摘要、风险标记和发生时间。
- 两个明细接口都会先校验任务存在，不存在时保持 `code=404`、`message=Agent任务不存在`。

### 本轮修复的自审问题

- 明细接口继续放在 `AdminAgentTaskController`，作为任务详情子资源，不回塞到 Agent 定义治理 Controller。
- 工具调用查询使用 `LEFT JOIN agt_tool`，避免历史工具被软删除后调用日志从排查结果中消失。
- 明细列表按 `called_at ASC, id ASC` 排序，审计轨迹按 `occurred_at ASC, id ASC` 排序，保证后台能按时间还原任务流转。
- 测试对 JSON 载荷只断言关键内容，不依赖 MySQL JSON 字符串空格格式。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_task_trace_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskToolCallsAndAudits test`
  - 结果：失败，`GET /api/admin/agent-tasks/{taskId}/tool-calls` 未实现，返回 404。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_task_trace_green2 mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskToolCallsAndAudits test`
  - 结果：通过，覆盖工具调用明细和 `TASK_CREATED -> TASK_WAITING_HUMAN_CONFIRM -> TASK_HUMAN_CONFIRMED -> TASK_RUNNING -> TASK_SUCCEEDED` 审计轨迹。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_task_trace_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，19 个 Agent 用例全部通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_task_trace_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，60 个认证、组织、IM、Agent 相关用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task`、`agt_tool`、`agt_tool_call_log`、`agt_audit_log` 表。

### 后续待补

- 增加任务运行指标、失败原因聚合和后台告警入口。
- 真实工具接入前补请求/响应载荷脱敏策略，避免后台明细接口暴露敏感业务数据。
- 人工确认超时扫描的定时触发、批次记录和多实例数据库锁控制已在后续生产化节点补齐。

## 2026-05-11 Agent 后台任务查询与详情

### 背景

后台已经能创建、发布、停用 Agent，也能手动触发人工确认超时扫描，但管理员仍缺少任务可观测入口。任务一旦进入 `FAILED`、`WAITING_HUMAN_CONFIRM`、`CANCELLED` 等状态，只能通过数据库排查，不利于后续接入 workflow、IM 和运行监控。

### 本轮完成内容

- 新增后台 Agent 任务列表接口：`GET /api/admin/agent-tasks`。
- 新增后台 Agent 任务详情接口：`GET /api/admin/agent-tasks/{taskId}`。
- 新增查询 DTO：`AgentTaskListQuery`。
- 新增后台任务响应 DTO：`AdminAgentTaskResponse`。
- 新增仓储查询行对象和条件对象：`AdminAgentTaskRow`、`AgentTaskQueryCriteria`。
- 支持筛选条件：
  - `status`
  - `agentId`
  - `triggeredByType`
  - `triggeredById`
  - `contextType`
  - `createdFrom`
  - `createdTo`
- 列表项返回 Agent 编码、Agent 名称、任务状态、触发主体、上下文、时间字段和 `toolCallCount`。
- 本轮不新增任务工具调用明细、审计轨迹、运行指标接口，避免任务查询节点膨胀。

### 本轮修复的自审问题

- 任务查询接口放在 `AdminAgentTaskController`，没有继续塞进 `AdminAgentController`。
- `status`、`triggeredByType` 使用服务层枚举校验，不把任意字符串直接作为查询语义。
- 查询 SQL 使用参数化参数；动态 SQL 只拼接固定条件片段。
- `toolCallCount` 使用 `LEFT JOIN agt_tool_call_log` 聚合统计，并显式 `GROUP BY` 任务与 Agent 字段，避免 MySQL 严格模式下聚合不确定。
- 本轮没有新增索引迁移；先复用已有 `agt_task` 索引，后续如果任务量上来再按慢查询补 `created_at`、`triggered_by` 等组合索引。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_task_query_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListAndGetAgentTasks test`
  - 结果：失败，`GET /api/admin/agent-tasks` 未实现，返回 404。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_task_query_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListAndGetAgentTasks test`
  - 结果：通过，覆盖任务分页筛选和详情查询。
- 边界测试：`env TEST_DB_NAME=workforce_hub_agent_task_query_invalid_status mvn -Dtest=ModuleAgentApiIntegrationTest#adminTaskListShouldRejectUnsupportedStatus test`
  - 结果：通过，非法任务状态返回 `code=400`。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_task_query_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，18 个 Agent 用例全部通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_task_query_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，59 个用例全部通过。
- 测试执行注意：本地集成测试会在每个用例前清理 `workforce:test:session*`、`auth:login:fail:*`、`auth:email:*` 等 Redis key；模块回归和跨模块回归不要用多个 Maven 进程并行跑，否则会出现会话或登录失败计数被其他测试进程清理的假失败。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task`、`agt_agent`、`agt_tool_call_log` 表。

### 后续待补

- 任务工具调用明细和审计轨迹查询接口已在后续节点补齐。
- 增加任务运行指标、失败原因聚合和后台告警入口。
- 任务量增长后再根据慢查询补充组合索引。

## 2026-05-11 Agent 后台手动触发人工确认超时扫描

### 背景

上一批已经把 `AgentTaskTimeoutJobService#cancelExpiredWaitingTasks` 做成服务能力，但生产使用时不能只靠代码内部调用。管理员需要在定时调度未接入前，能通过后台 API 手动收口已经超过 `waiting_deadline_at` 的等待人工确认任务。

### 本轮完成内容

- 新增后台 Agent 任务治理接口：`POST /api/admin/agent-tasks/timeout-scan`。
- 新增请求 DTO：`AgentTaskTimeoutScanRequest`。
- 新增响应 DTO：`AgentTaskTimeoutScanResponse`。
- 接口调用已有 `AgentTaskTimeoutJobService#cancelExpiredWaitingTasks(batchSize)`，返回 `cancelledCount`。
- `batchSize` 规则：
  - 默认 100。
  - 最小 1。
  - 最大 500。
  - 显式传 `null` 时按默认 100 处理，避免空指针 500。
- 本轮不引入 `@Scheduled`、分布式锁或 job run 表，先补后台可操作入口。

### 本轮修复的自审问题

- 最初测试路径写成 `/api/admin/agents/tasks/timeout-scan`，与员工端任务资源 `/api/employee/agent-tasks` 不一致。已校准为 `/api/admin/agent-tasks/timeout-scan`。
- 自审发现 `batchSize:null` 会在 Controller 拆箱时触发 `NullPointerException`，导致接口返回 500。已把默认值兜底收进 `AgentTaskTimeoutScanRequest#safeBatchSize()`。
- 后台任务治理接口独立为 `AdminAgentTaskController`，避免继续把任务治理能力塞进 `AdminAgentController`。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_red2 mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldTriggerTimeoutScanManually test`
  - 结果：失败，`POST /api/admin/agent-tasks/timeout-scan` 未实现，返回 404。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldTriggerTimeoutScanManually test`
  - 结果：通过，后台手动触发后过期等待任务进入 `CANCELLED`。
- 边界红灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_null_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanShouldUseDefaultBatchSizeWhenBatchSizeIsNull test`
  - 结果：失败，`batchSize:null` 触发空指针并返回 500。
- 边界绿灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_null_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanShouldUseDefaultBatchSizeWhenBatchSizeIsNull test`
  - 结果：通过，显式传空批次时使用默认批次并取消过期任务。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_full2 mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，16 个 Agent 用例全部通过。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_admin_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，57 个用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task.waiting_deadline_at`、`agt_tool_call_log`、`agt_audit_log` 等字段和表。

### 后续待补

- 接入统一定时调度，让超时扫描可周期自动执行。
- 增加 job run 表或后台操作审计，记录每次扫描批次、耗时、命中数和异常明细。
- 多实例部署时补分布式锁或基于数据库的抢占策略。
- 后台 Agent 任务查询、工具调用明细和审计轨迹已在后续节点补齐。

## 2026-05-11 Agent 后台列表、详情与停用基础治理

### 背景

`module-agent` 已经具备后台创建、版本、工具、ACL、发布和员工端使用能力，但后台还缺少最基本的查询与停用入口。没有这些入口，管理员只能通过数据库确认 Agent 状态，也无法在发现配置错误、工具风险或业务终止时快速下线 Agent。

### 本轮完成内容

- 新增后台 Agent 分页列表接口：`GET /api/admin/agents`。
- 新增后台 Agent 详情接口：`GET /api/admin/agents/{agentId}`。
- 新增后台 Agent 停用接口：`POST /api/admin/agents/{agentId}/disable`。
- 停用后：
  - `agt_agent.status` 更新为 `INACTIVE`。
  - 后台详情继续可查，便于管理员确认状态。
  - 员工端 Agent 列表不再展示该 Agent。
  - 员工端创建任务返回 `code=409`、`message=Agent未发布或未启用`。
- 员工创建任务的校验顺序调整为：先判断 Agent 发布和启用状态，再判断员工 ACL。这样已停用 Agent 的错误语义稳定为“未启用”，而不是被 ACL 查询过滤后误报“无权使用”。

### 本轮修复的自审问题

- 原后台 Controller 只有 `POST /api/admin/agents`，`GET /api/admin/agents` 会落到不支持方法异常，且被全局异常包装为 500。现在已有明确列表接口。
- 停用不应清空 `published_version_id`，否则后台无法区分“未发布草稿”和“曾发布但被下线”；本轮只更新 `status=INACTIVE`。
- 本轮没有把任务查询、版本回滚和停用时等待任务处理塞进同一批，避免后台治理节点过大。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_admin_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListGetAndDisablePublishedAgent test`
  - 结果：失败，`GET /api/admin/agents` 未实现，返回方法不支持并被包装为 500。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_admin_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListGetAndDisablePublishedAgent test`
  - 结果：通过，覆盖后台列表、详情、停用和员工侧停用效果。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_admin_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，14 个 Agent 用例全部通过。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_admin_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，55 个用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_agent.status`、`updated_by` 等字段。

### 后续待补

- 后台 Agent 任务查询、工具调用明细和审计轨迹已在后续节点补齐。
- 增加版本回滚、重新启用、软删除和工具/ACL 查询接口。
- 定义停用 Agent 时已等待确认任务的处理策略：保持等待、自动取消或升级给管理员。
- 接入 `AgentTaskTimeoutJobService` 的定时触发或后台治理入口。

## 2026-05-11 Agent 人工确认超时扫描服务

### 背景

人工确认动作闭环已经允许员工继续、拒绝或取消 `WAITING_HUMAN_CONFIRM` 任务，但如果员工一直不处理，任务会长期停留在等待态。这个状态会影响后续 workflow/IM Agent 的业务闭环，因此需要先补服务级超时扫描能力，把超过 `waiting_deadline_at` 的等待任务收口为终态。

### 本轮完成内容

- 新增 `AgentTaskTimeoutJobService`，提供 `cancelExpiredWaitingTasks(int batchSize)`。
- 新增 `AgentExpiredTaskRow`，作为超时扫描候选任务的轻量查询结果。
- `AgentRepository` 新增：
  - `listExpiredWaitingTasks(now, limit)`：按 `waiting_deadline_at` 和 `id` 顺序读取已超时等待任务。
  - `cancelExpiredWaitingTask(taskId, now, responsePayloadJson)`：用状态与截止时间条件原子取消任务。
- 超时扫描命中后：
  - `agt_task.status` 更新为 `CANCELLED`。
  - `agt_task.finished_at` 写入完成时间。
  - `agt_task.error_message` 写入 `人工确认超时，任务已取消`。
  - 待确认工具日志从 `PENDING/WAITING_CONFIRM` 更新为 `TIMEOUT/CANCELLED`。
  - 写入 `TASK_TIMEOUT_CANCELLED` 审计。
- 本轮没有直接添加 `@Scheduled`，也没有增加后台手动触发 API；先把可测试、可复用的核心扫描服务落地，后续再接统一调度、后台治理入口或分布式锁。

### 本轮修复的自审问题

- 扫描列表和取消更新必须使用同一个 `now` 参数，避免列表判断和更新判断分别取数据库当前时间导致边界不一致。
- 取消任务使用条件更新，只有仍处于 `WAITING_HUMAN_CONFIRM` 且仍已过期的任务才会写入超时审计，避免并发扫描重复写审计。
- `batchSize <= 0` 直接返回业务错误，避免调度配置错误时静默不执行。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#waitingHumanConfirmTaskShouldBeCancelledWhenDeadlineExpired test`
  - 结果：失败，`AgentTaskTimeoutJobService` 尚不存在。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_timeout_green2 mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，2 个用例覆盖“已超时自动取消”和“未超时保持等待”。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，13 个 Agent 用例全部通过。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_timeout_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test`
  - 结果：通过，54 个用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task.waiting_deadline_at`、`agt_tool_call_log`、`agt_audit_log` 等字段和表。

### 后续待补

- 接入统一调度或后台治理入口，让 `AgentTaskTimeoutJobService` 可被生产环境周期触发或管理员手动触发。
- 如进入多实例部署，需要补分布式锁或 job run 表，记录扫描批次、耗时、命中数和失败明细。
- 后续接入 workflow/IM 后，需要定义超时取消是否同步回写业务上下文或通知触发人。
- 后台 Agent 任务查询、工具调用明细和审计轨迹已在后续节点补齐。

## 2026-05-11 Agent 人工确认继续、拒绝与取消闭环

### 背景

上一批 `module-agent` 已能在高风险或 `REQUIRE_CONFIRM` 工具场景下生成 `WAITING_HUMAN_CONFIRM` 任务，但员工还不能对该任务做继续执行、拒绝或取消动作。这个状态如果没有动作入口，只能停留在数据库中，后续 workflow/IM 接入时也无法形成完整安全闭环。

### 本轮完成内容

- 新增员工端 Agent 任务动作接口：
  - `POST /api/employee/agent-tasks/{taskId}/confirm`
  - `POST /api/employee/agent-tasks/{taskId}/cancel`
- 新增请求 DTO：
  - `ConfirmAgentTaskRequest`
  - `CancelAgentTaskRequest`
- 新增 `EmployeeAgentTaskController`，把任务动作接口从“创建 Agent 任务”接口中拆出。
- 拆出 `AgentToolExecutionService`，把 SPI/Connector 工具执行从 `AgentService` 中移出，后续 workflow/IM 触发和异步执行可以复用同一执行边界。
- 人工确认同意后：
  - 校验当前员工必须是任务触发人。
  - 校验任务必须仍处于 `WAITING_HUMAN_CONFIRM`。
  - 通过行级锁读取任务，避免重复确认竞争。
  - 查询仍有效、仍允许的待确认工具。
  - 任务进入 `RUNNING`，执行待确认工具，成功后进入 `SUCCEEDED`。
  - 写入 `TASK_HUMAN_CONFIRMED`、`TASK_RUNNING`、`TASK_SUCCEEDED` 审计。
- 人工拒绝后：
  - 任务进入 `REJECTED`。
  - 待确认工具日志写入 `approval_status=REJECTED`、`result_status=REJECTED`。
  - 写入 `TASK_HUMAN_REJECTED` 审计。
- 人工取消后：
  - 任务进入 `CANCELLED`。
  - 待确认工具日志写入 `approval_status=CANCELLED`、`result_status=CANCELLED`。
  - 写入 `TASK_CANCELLED` 审计。

### 本轮修复的自审问题

- 人工确认接口最初只检查任务状态，没有检查恢复执行时是否还能查询到有效待确认工具。若工具绑定被停用，可能把没有执行任何工具的任务误置为 `SUCCEEDED`。
- 修复后，恢复执行前必须存在至少一个有效待确认工具；否则返回 `409`，任务仍停留在 `WAITING_HUMAN_CONFIRM`。
- 恢复执行待确认工具时继续限定 `agt_agent_tool.permission_mode='ALLOWED'`，避免后续工具绑定扩展后误取不允许调用的绑定。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_tdd_red mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldContinueAfterHumanConfirmation test`
  - 结果：失败，`POST /api/employee/agent-tasks/{taskId}/confirm` 返回 404。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_tdd_green mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldContinueAfterHumanConfirmation test`
  - 结果：通过，确认后任务进入 `SUCCEEDED`，待确认工具日志变为 `APPROVED/SUCCESS`。
- 红灯：`env TEST_DB_NAME=workforce_hub_agent_inactive_binding_red mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldNotSucceedWhenPendingToolBindingIsInactive test`
  - 结果：失败，绑定失效后确认仍返回 `code=0` 并把任务置为 `SUCCEEDED`。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_inactive_binding_green mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldNotSucceedWhenPendingToolBindingIsInactive test`
  - 结果：通过，绑定失效后返回 `code=409`，任务保持 `WAITING_HUMAN_CONFIRM`。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_confirm_full2 mvn -Dtest=ModuleAgentApiIntegrationTest test`
  - 结果：通过，11 个用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task`、`agt_tool_call_log`、`agt_audit_log` 等表。

### 后续待补

- 后台 Agent 任务查询、工具调用明细和审计轨迹已在后续节点补齐。
- 接入 `AgentTaskTimeoutJobService` 的定时触发或后台治理入口。
- 接入 `WorkflowAgentPreReviewTrigger` 和 `WorkflowAgentResultCallback`，把 workflow 侧预审 SPI 接到真实 Agent 任务。
- 接入 `ImAgentTriggerService` 和 `ImAgentResultCallback`，把会话 Agent 回复写回 IM。
- 增加任务异步执行、重试、运行监控和工具日志脱敏。

## 2026-05-11 Agent 治理、授权、任务与工具调用第一批闭环

### 背景

`module-workflow` 已经完成 Agent 预审的 SPI 入口，但真正的 Agent 后台治理、工具绑定、员工可用 Agent 列表和任务运行记录还没有落地。本轮进入 `module-agent` 第一批后端节点，目标不是一次性完成所有 Agent 能力，而是先建立可运行、可审计、可继续扩展的最小闭环。

### 本轮完成内容

- 新增后台 Agent 治理接口：
  - 创建 Agent 主定义。
  - 创建 Agent 版本。
  - 创建 Agent 工具。
  - 绑定 Agent 版本可调用工具。
  - 授予 Agent ACL。
  - 发布 Agent 版本。
- 新增员工端 Agent 使用接口：
  - 查询当前员工可见/可用 Agent。
  - 创建 Agent 任务。
- 完成第一批权限闭环：
  - `USER` ACL 按员工 ID 生效。
  - `DEPARTMENT` ACL 按员工主部门生效。
  - `GROUP` ACL 按 `im_conversation.id` 和活跃群成员关系生效。
- 完成第一批工具闭环：
  - `SPI` 工具可同步调用组织、假期、考勤、知识库查询 SPI。
  - 未绑定工具在任务创建前拦截，不落失败任务。
  - `REQUIRE_CONFIRM` 工具进入 `WAITING_HUMAN_CONFIRM`，不直接执行。
  - 业务型工具执行失败时保留 `FAILED` 任务、失败工具日志和审计记录。
- 完成测试库复位补充：
  - `LocalIntegrationTestSupport` 增加 `agt_*` 表清理，避免 Agent 测试污染后续用例。

### 本轮修复的自审问题

- `GROUP` ACL 原本只允许写入，但员工端可见和可用查询没有消费群成员关系，导致群组授权配置无效。
- 自动工具执行失败时，服务先写 `FAILED` 再抛异常，最终被事务回滚，失败任务和审计无法保留。
- 员工端创建任务时如果显式传入 `toolCalls: null`，会触发空指针并返回 500。

### 验证记录

- 红灯：`env TEST_DB_NAME=workforce_hub_agent_group_red mvn -Dtest=ModuleAgentApiIntegrationTest#groupAclShouldAuthorizeOnlyActiveGroupMembers test`
  - 结果：失败，群成员拿不到 `GROUP` ACL 授权的 Agent。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_group_green mvn -Dtest=ModuleAgentApiIntegrationTest#groupAclShouldAuthorizeOnlyActiveGroupMembers test`
  - 结果：通过。
- 红灯：`env TEST_DB_NAME=workforce_hub_agent_failed_task_red mvn -Dtest=ModuleAgentApiIntegrationTest#toolExecutionFailureShouldKeepFailedTaskAndLog test`
  - 结果：失败，接口返回业务错误且任务被回滚。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_failed_task_green mvn -Dtest=ModuleAgentApiIntegrationTest#toolExecutionFailureShouldKeepFailedTaskAndLog test`
  - 结果：通过。
- 红灯：`env TEST_DB_NAME=workforce_hub_agent_null_tool_red mvn -Dtest=ModuleAgentApiIntegrationTest#nullToolCallsShouldBeTreatedAsEmptyList test`
  - 结果：失败，显式 `null` 导致 NPE。
- 绿灯：`env TEST_DB_NAME=workforce_hub_agent_null_tool_green mvn -Dtest=ModuleAgentApiIntegrationTest#nullToolCallsShouldBeTreatedAsEmptyList test`
  - 结果：通过。
- 模块回归：`env TEST_DB_NAME=workforce_hub_agent_full_green2 mvn -Dtest=ModuleAgentApiIntegrationTest test`
  - 结果：通过，7 个用例全部通过。
- 编译验证：`mvn -DskipTests compile`
  - 结果：通过。
- 跨模块回归：`env TEST_DB_NAME=workforce_hub_agent_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest test`
  - 结果：通过，48 个用例全部通过。

### 数据库说明

本轮没有新增 Flyway 迁移。`agt_agent`、`agt_agent_version`、`agt_tool`、`agt_agent_tool`、`agt_agent_acl`、`agt_task`、`agt_tool_call_log`、`agt_audit_log` 等表已经存在于 `V1__init_schema.sql`，本轮只是把第一批后端代码接到这些表。

### 后续待补

- 拆分 `AgentService` 与 `AgentRepository` 中继续增长的编排、执行、授权 SQL，避免第二批继续膨胀。
- 接入 `WorkflowAgentPreReviewTrigger` 和 `WorkflowAgentResultCallback`，把 workflow 侧预审 SPI 接到真实 Agent 任务。
- 接入 `ImAgentTriggerService` 和 `ImAgentResultCallback`，把会话 Agent 回复写回 IM。
- 增加任务查询、运行监控、重试、超时扫描和定时/事件触发。
- 增加工具参数 Schema 校验、敏感字段脱敏和更细的风险策略。
