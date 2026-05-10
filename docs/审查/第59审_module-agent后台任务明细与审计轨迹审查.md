# 第 59 审 module-agent 后台任务明细与审计轨迹审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskToolCallResponse.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskAuditResponse.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskToolCallRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskAuditRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台 Agent 任务工具调用明细和审计轨迹查询是否满足排障可追溯要求，并确认接口边界、排序、任务存在性校验、软删除工具追溯和文档记录是否自洽。

---

## 第一部分：本轮内容概览

本轮补齐后台 Agent 任务明细排查入口：

- 新增 `GET /api/admin/agent-tasks/{taskId}/tool-calls`。
- 新增 `GET /api/admin/agent-tasks/{taskId}/audits`。
- 新增 `AdminAgentTaskToolCallResponse` 和 `AdminAgentTaskAuditResponse`。
- 新增 `AdminAgentTaskToolCallRow` 和 `AdminAgentTaskAuditRow`。
- 工具调用明细返回工具元数据、请求载荷、响应载荷、审批状态、结果状态和调用时间。
- 审计轨迹返回动作类型、目标对象、决策摘要、风险标记和发生时间。
- 两个接口都先校验任务存在，避免不存在任务返回空列表造成排查误判。

本轮没有新增 Flyway 迁移。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台任务明细红灯 | `env TEST_DB_NAME=workforce_hub_agent_task_trace_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskToolCallsAndAudits test` | 按预期失败 | `GET /api/admin/agent-tasks/{taskId}/tool-calls` 未实现，返回 404。 |
| 后台任务明细绿灯 | `env TEST_DB_NAME=workforce_hub_agent_task_trace_green2 mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskToolCallsAndAudits test` | 通过 | 覆盖工具调用明细和审计轨迹查询。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | 后端编译通过。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_task_trace_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 19 个 Agent 用例通过。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_task_trace_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 60 个认证、组织、IM、Agent 相关用例通过。 |

补充说明：本地集成测试基类会清理共享 Redis 测试 key，模块回归和跨模块回归应串行执行；多个 Maven 进程并行跑会互相清理会话与登录失败计数，导致非业务原因的 `401` 或计数断言假失败。

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 后台可观测性 | 任务列表只返回工具调用数量，管理员无法查看具体工具调用和状态流转证据。 | 重要 | 新增工具调用明细和审计轨迹接口。 |
| 1-2 | 任务存在性 | 如果直接查询明细表，不存在任务会返回空数组，容易和“任务存在但没有明细”混淆。 | 重要 | 服务层先调用 `getAdminAgentTaskOrThrow` 校验任务存在。 |
| 1-3 | 历史工具追溯 | 工具调用日志需要在工具软删除后仍可排查。 | 重要 | 工具明细查询使用 `LEFT JOIN agt_tool`。 |
| 1-4 | 排序语义 | 明细和审计如果无稳定排序，后台无法可靠还原执行顺序。 | 重要 | 工具调用按 `called_at ASC, id ASC`，审计按 `occurred_at ASC, id ASC`。 |
| 1-5 | JSON 断言 | MySQL JSON 字段输出可能规范化空格，如果测试依赖完整字符串会过脆。 | 建议 | 测试改为断言关键 JSON 内容。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 认证边界 | 接口挂在 `/api/admin/agent-tasks/{taskId}/...`，复用后台管理员会话链路。 |
| 任务边界 | 不存在任务先返回 `Agent任务不存在`，不会静默返回空列表。 |
| 工具追溯 | 使用 `LEFT JOIN` 保留历史调用日志可见性。 |
| 审计顺序 | 审计轨迹按发生时间和主键升序返回，可还原状态流转。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 明细载荷脱敏 | 当前后台明细直接返回 `requestPayloadJson` 和 `responsePayloadJson`。真实工具接入后，这里可能包含邮箱、手机号、审批内容、知识库片段或第三方响应。 | 重要 | 在真实工具接入前设计字段级脱敏策略，至少支持按工具类型、字段名和响应路径脱敏。 |
| 2-2 | 操作者审计 | 当前任务审计只返回任务动作，没有后台查看人、IP、User-Agent 和导出记录。 | 建议 | 后续后台审计节点增加敏感排障接口访问审计。 |
| 2-3 | 运行指标 | 明细接口不提供耗时、失败分类、重试次数和聚合统计。 | 建议 | 后续独立设计任务运行指标和失败聚合接口，避免明细接口继续膨胀。 |
| 2-4 | 历史工具硬删除 | `LEFT JOIN` 可以兼容软删除，但如果未来允许硬删除工具定义，`toolCode/toolName/readOnlyFlag` 可能为空或失真。 | 建议 | 后续工具调用日志落库时冗余工具快照字段，或禁止硬删除工具定义。 |

---

## 第六部分：阶段性结论

`module-agent` 后台已经具备任务列表、详情、工具调用明细和审计轨迹查询能力，能支撑第一版管理员排障闭环。当前实现没有引入 schema 变更，接口边界和测试覆盖基本自洽。

下一步建议转向 Agent 运行治理：补任务运行指标、失败原因聚合、人工确认超时扫描定时调度和批次记录。
