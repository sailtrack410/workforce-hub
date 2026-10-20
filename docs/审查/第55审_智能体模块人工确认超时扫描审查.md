# 第 55 审 module-agent 人工确认超时扫描审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/**`、`server/src/test/java/com/workforcehub/agent/ModuleAgentTaskTimeoutIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `WAITING_HUMAN_CONFIRM` 人工确认超时扫描是否能把过期任务收口为终态，是否避免误取消未过期任务，并确认当前边界是否适合后续接定时调度或后台治理入口。

---

## 第一部分：本轮内容概览

本轮在 Agent 人工确认动作闭环之后，补齐服务级超时扫描：

- 新增 `AgentTaskTimeoutJobService#cancelExpiredWaitingTasks(int batchSize)`。
- 新增 `AgentExpiredTaskRow`，作为超时任务候选行。
- `AgentRepository` 新增超时等待任务查询和条件取消方法。
- 超过 `waiting_deadline_at` 的 `WAITING_HUMAN_CONFIRM` 任务会进入 `CANCELLED`。
- 待确认工具日志会从 `PENDING/WAITING_CONFIRM` 更新为 `TIMEOUT/CANCELLED`。
- 每个实际取消成功的任务写入 `TASK_TIMEOUT_CANCELLED` 审计。
- 暂不直接接 `@Scheduled` 或后台 API，先保留为可测试、可复用的内部服务。

本轮没有新增 Flyway 迁移，继续复用 `V1__init_schema.sql` 已存在的 `agt_task.waiting_deadline_at`、`agt_tool_call_log` 和 `agt_audit_log`。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 超时扫描红灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#waitingHumanConfirmTaskShouldBeCancelledWhenDeadlineExpired test` | 按预期失败 | `AgentTaskTimeoutJobService` 尚不存在。 |
| 超时扫描绿灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_green2 mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 2 个用例覆盖超时取消和未超时保护。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 13 个 Agent 集成用例全部通过。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | Maven 编译成功。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 54 个用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 人工确认任务状态机 | 已有确认、拒绝和取消入口，但没有服务把超过 `waiting_deadline_at` 的任务自动收口，等待任务可能长期挂起。 | 重要 | 新增 `AgentTaskTimeoutJobService`，扫描已过期等待任务并转为 `CANCELLED`。 |
| 1-2 | `AgentRepository#cancelExpiredWaitingTask` | 超时扫描如果只按任务 ID 更新，可能误取消刚被员工确认或取消的任务。 | 重大 | 取消更新时同时校验 `status='WAITING_HUMAN_CONFIRM'` 和 `waiting_deadline_at <= now`，更新成功后才写审计。 |
| 1-3 | 超时工具日志 | 只更新任务状态会导致工具调用日志仍停留在 `PENDING/WAITING_CONFIRM`，后续排查会看到状态不一致。 | 重要 | 同步把待确认工具日志更新为 `TIMEOUT/CANCELLED`，并写入响应摘要。 |
| 1-4 | 扫描时间边界 | 候选查询和取消更新如果分别取当前时间，边界任务可能出现列表命中但更新不命中或反向差异。 | 建议 | 服务层生成一次 `now`，查询和条件更新共用同一时间参数。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 过期任务收口 | 已覆盖 `WAITING_HUMAN_CONFIRM + waiting_deadline_at <= now -> CANCELLED`。 |
| 未过期保护 | 已覆盖未超过截止时间时任务和工具日志不被扫描误改。 |
| 工具日志一致性 | 超时取消会同步更新待确认工具日志为 `TIMEOUT/CANCELLED`。 |
| 审计留痕 | 实际取消成功才写 `TASK_TIMEOUT_CANCELLED`，避免并发重复审计。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 调度入口 | 当前只有服务方法，没有定时触发、后台手动触发或运维界面；生产环境不会自动扫描。 | 重要 | 后续接统一调度器或后台治理 API，并明确执行频率、批次大小和权限。 |
| 2-2 | 多实例并发 | 当前依靠条件更新避免重复取消和重复审计，但没有分布式锁或 job run 表记录扫描批次。 | 建议 | 多实例部署前补 job run 表、批次游标、失败明细和可选分布式锁。 |
| 2-3 | 业务回调 | 任务被超时取消后，还没有通知 workflow/IM 等业务上下文。 | 重要 | 接入 workflow/IM 真实触发后，补超时结果回写和通知策略。 |
| 2-4 | 错误可观测性 | 当前服务返回取消数量，但没有指标、日志摘要或后台可查询记录。 | 建议 | 后续补运行指标、审计查询和后台任务运行记录。 |
| 2-5 | 升级处理 | 当前统一取消，未支持超时后升级给上级、管理员或 Agent 负责人。 | 建议 | 产品规则明确后补升级策略，保留 `CANCELLED` 作为第一版默认收口。 |

---

## 第六部分：阶段性结论

`module-agent` 人工确认超时扫描已完成核心服务闭环，能够把超时等待任务转为可审计的取消终态，并且不会误取消未到期任务。

下一步如果继续推进 Agent，优先级应是后台 Agent 列表、详情、停用和任务查询；如果要先接生产运行，则需要先把本服务挂到统一调度或后台手动触发入口。
