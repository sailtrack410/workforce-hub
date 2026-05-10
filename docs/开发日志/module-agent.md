# module-agent 开发日志

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
- 增加人工确认继续执行/驳回接口。
- 接入 `WorkflowAgentPreReviewTrigger` 和 `WorkflowAgentResultCallback`，把 workflow 侧预审 SPI 接到真实 Agent 任务。
- 接入 `ImAgentTriggerService` 和 `ImAgentResultCallback`，把会话 Agent 回复写回 IM。
- 增加任务查询、运行监控、重试、超时扫描和定时/事件触发。
- 增加工具参数 Schema 校验、敏感字段脱敏和更细的风险策略。
