# module-agent 开发日志

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

- 增加 `WAITING_HUMAN_CONFIRM` 超时扫描，把过期任务自动转为 `CANCELLED` 或升级给新的处理人。
- 增加后台 Agent 列表、详情、停用和任务查询接口。
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
