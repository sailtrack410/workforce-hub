# 第 54 审 module-agent 人工确认动作闭环审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/**`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `module-agent` 人工确认继续执行、人工拒绝、人工取消和待确认工具恢复执行边界是否自洽，是否能作为 workflow/IM Agent 后续接入的安全节点。

---

## 第一部分：本轮内容概览

本轮在 `module-agent` 第一批治理和任务骨架基础上，补齐 `WAITING_HUMAN_CONFIRM` 的员工端动作入口：

- 新增 `POST /api/employee/agent-tasks/{taskId}/confirm`。
- 新增 `POST /api/employee/agent-tasks/{taskId}/cancel`。
- 新增 `ConfirmAgentTaskRequest` 和 `CancelAgentTaskRequest`。
- 新增 `EmployeeAgentTaskController`，把 Agent 任务动作和 Agent 创建任务接口分开。
- 拆出 `AgentToolExecutionService`，让工具执行逻辑从 `AgentService` 中移出。
- 人工同意后恢复执行待确认工具，成功后任务进入 `SUCCEEDED`。
- 人工拒绝后任务进入 `REJECTED`，工具日志同步写入拒绝态。
- 人工取消后任务进入 `CANCELLED`，工具日志同步写入取消态。
- 通过 `SELECT ... FOR UPDATE` 锁定任务行，降低重复确认、取消的并发竞争风险。

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已有 `agt_*` 表。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 人工确认接口红灯 | `env TEST_DB_NAME=workforce_hub_agent_tdd_red mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldContinueAfterHumanConfirmation test` | 按预期失败 | 接口未实现时返回 404。 |
| 人工确认接口绿灯 | `env TEST_DB_NAME=workforce_hub_agent_tdd_green mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldContinueAfterHumanConfirmation test` | 通过 | 同意继续后任务执行待确认工具并进入 `SUCCEEDED`。 |
| 绑定失效红灯 | `env TEST_DB_NAME=workforce_hub_agent_inactive_binding_red mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldNotSucceedWhenPendingToolBindingIsInactive test` | 按预期失败 | 待确认工具绑定失效后仍会把任务误置为 `SUCCEEDED`。 |
| 绑定失效绿灯 | `env TEST_DB_NAME=workforce_hub_agent_inactive_binding_green mvn -Dtest=ModuleAgentApiIntegrationTest#waitingTaskShouldNotSucceedWhenPendingToolBindingIsInactive test` | 通过 | 无有效待确认工具时返回 `409`，任务保持 `WAITING_HUMAN_CONFIRM`。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_confirm_full2 mvn -Dtest=ModuleAgentApiIntegrationTest test` | 通过 | 11 个 Agent 集成用例全部通过。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | Maven 编译成功。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_confirm_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest test` | 通过 | 52 个用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `EmployeeAgentTaskController` 缺失 | `WAITING_HUMAN_CONFIRM` 已能生成，但没有员工端确认继续、拒绝和取消接口，任务会停留在等待态，后续 workflow/IM 也无法复用该状态。 | 重要 | 新增任务动作 Controller 和两个请求 DTO，接入确认与取消动作。 |
| 1-2 | `AgentService#confirmCurrentEmployeeTask` | 恢复执行前只校验任务状态，若待确认工具绑定失效或待确认日志异常缺失，可能不执行任何工具却把任务置为 `SUCCEEDED`。 | 重大 | 确认继续前必须查询到有效待确认工具；为空则返回 `409`，任务保持等待态。 |
| 1-3 | `AgentRepository#listPendingToolCalls` | 查询待确认工具时最初只看绑定状态，未继续限定 `permission_mode='ALLOWED'`。 | 重要 | 增加 `aat.permission_mode = 'ALLOWED'`，避免后续工具绑定语义扩展后误执行不允许工具。 |
| 1-4 | `AgentService` | Agent 工具执行逻辑继续留在主服务中，后续 workflow/IM 触发和异步任务会重复依赖单个大服务。 | 建议 | 拆出 `AgentToolExecutionService`，让同步任务和人工确认恢复执行共用同一工具执行边界。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 状态机流转 | `WAITING_HUMAN_CONFIRM -> RUNNING -> SUCCEEDED`、`WAITING_HUMAN_CONFIRM -> REJECTED`、`WAITING_HUMAN_CONFIRM -> CANCELLED` 均已有自动化覆盖。 |
| 操作权限 | 当前只允许任务触发员工确认或取消自己的 Agent 任务，非触发人返回 `403`。 |
| 工具执行边界 | 人工确认后只执行仍有效、仍允许、仍处于待确认状态的工具调用。 |
| 异常绑定保护 | 待确认工具绑定失效时不会把任务误置为成功。 |
| 审计留痕 | 确认、拒绝、取消、恢复运行和成功均写入对应 `agt_audit_log`。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 人工确认超时 | 当前确认接口会拒绝已超过 `waiting_deadline_at` 的任务，但还没有后台扫描器把超时任务自动转为 `CANCELLED` 或升级给新的处理人。 | 重要 | 下一批补 `AgentTaskTimeoutJobService` 或统一调度入口，扫描超时等待任务，写 `TASK_TIMEOUT_CANCELLED` 审计。 |
| 2-2 | 重复确认幂等语义 | 当前通过行级锁和状态校验避免并发误执行；第二次确认终态任务会返回 `409`，不是幂等回放。 | 建议 | 如果前端需要弱网重试体验，后续可增加幂等键或终态回放策略。 |
| 2-3 | 工具调用日志敏感信息 | 人工确认恢复执行仍会保存完整请求/响应载荷，真实工具接入后可能出现敏感字段长期留存。 | 重要 | 按工具配置增加日志脱敏、字段白名单和保留期限。 |
| 2-4 | 同步执行模型 | 人工确认后仍在 HTTP 请求内同步执行工具，适合当前短链路测试，不适合长耗时 RAG、连接器或复杂 Agent 工作流。 | 重要 | 后续引入任务 Worker、队列化执行、重试、超时和运行监控。 |
| 2-5 | 后台停用 Agent | 开发规划里仍有后台停用 Agent 待覆盖；如果 Agent 或版本被停用，已等待确认任务的处理策略还需要产品化定义。 | 建议 | 补后台停用接口时同步定义等待中任务是保持、取消还是升级。 |

---

## 第六部分：阶段性结论

`module-agent` 人工确认动作已经从“只能进入等待态”推进到“可继续、可拒绝、可取消、可审计”的状态，基本补齐高风险 Agent 工具调用的第一版人工安全阀。

下一步不建议继续在同步 HTTP 链路上堆更多 Agent 能力，优先补人工确认超时扫描和后台停用策略，再接 workflow/IM 真实触发回调。
