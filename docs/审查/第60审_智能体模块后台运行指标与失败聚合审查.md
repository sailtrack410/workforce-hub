# 第 60 审 module-agent 后台运行指标与失败聚合审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskMetricsResponse.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskFailureSummaryResponse.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskMetricsRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskFailureSummaryRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台 Agent 运行指标与失败聚合接口是否满足第一版排障总览需要，并确认静态路由、聚合 SQL、任务计数、失败统计口径和文档记录是否自洽。

---

## 第一部分：本轮内容概览

本轮补齐后台 Agent 任务运行治理的只读总览入口：

- 新增 `GET /api/admin/agent-tasks/metrics`。
- 新增 `GET /api/admin/agent-tasks/failure-summary`。
- 新增 `AdminAgentTaskMetricsResponse` 和 `AdminAgentTaskFailureSummaryResponse`。
- 新增 `AdminAgentTaskMetricsRow` 和 `AdminAgentTaskFailureSummaryRow`。
- 运行指标返回任务总量、各状态数量、工具调用总数、失败工具调用数、已完成任务平均耗时和最近任务创建时间。
- 失败聚合按 `errorMessage` 汇总失败任务数量、失败工具调用数量和首次/最近发生时间。

本轮没有新增 Flyway 迁移。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台运行指标红灯 | `env TEST_DB_NAME=workforce_hub_agent_metrics_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskMetricsAndFailureSummary test` | 按预期失败 | `/metrics` 未实现时被 `/{taskId}` 路由误解析，返回 500。 |
| 后台运行指标绿灯 | `env TEST_DB_NAME=workforce_hub_agent_metrics_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldGetAgentTaskMetricsAndFailureSummary test` | 通过 | 覆盖成功、失败、等待人工确认三类任务的运行指标和失败聚合。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17.0.18 下编译通过。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_metrics_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 20 个用例通过，覆盖 Agent API 与人工确认超时扫描回归。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_metrics_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 61 个用例通过，覆盖认证、组织、IM、Agent 跨模块回归。 |

补充说明：本地集成测试基类会清理共享 Redis 测试 key，模块回归和跨模块回归应串行执行；多个 Maven 进程并行跑会互相清理会话与登录失败计数，导致非业务原因的 `401` 或计数断言假失败。

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 后台运行治理 | 管理员只能逐条查任务和明细，缺少任务整体健康度和失败原因排行。 | 重要 | 新增运行指标和失败聚合接口。 |
| 1-2 | 静态路由 | 红灯阶段发现 `/api/admin/agent-tasks/metrics` 会被 `/{taskId}` 捕获，`metrics` 转 Long 失败后返回 500。 | 重要 | 新增明确的 `/metrics` 和 `/failure-summary` 静态路由。 |
| 1-3 | 任务计数 | 如果直接 `LEFT JOIN agt_tool_call_log` 后 `COUNT(task.id)`，一个任务多次工具调用会放大任务数量。 | 重要 | 任务状态指标直接统计 `agt_task`，工具调用数量使用独立子查询。 |
| 1-4 | 失败统计口径 | 拒绝、取消、超时取消不应混入业务工具执行失败。 | 重要 | 失败聚合限定 `task.status=FAILED`，失败工具调用限定 `result_status=FAILED`。 |
| 1-5 | 文档状态 | 上一轮文档仍把运行指标列为后续待补。 | 建议 | API 文档、测试用例、开发规划和开发日志均已更新。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 认证边界 | 接口挂在 `/api/admin/agent-tasks` 下，复用后台管理员会话链路。 |
| 路由边界 | `/metrics` 和 `/failure-summary` 已作为静态子资源，不再被 `/{taskId}` 误吞。 |
| 数据口径 | 任务状态统计基于 `agt_task.status`；失败聚合只统计 `FAILED` 任务。 |
| 写入副作用 | 两个接口均为只读查询，不修改任务、工具日志或审计日志。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 指标筛选 | 第一版指标是全局统计，不能按时间范围、Agent、触发来源筛选。 | 重要 | 后续在后台看板接入前补筛选参数，并复用任务列表的 `createdFrom/createdTo/agentId` 语义。 |
| 2-2 | 失败分类 | 当前按自然语言 `errorMessage` 聚合，长期看不够稳定，文案变化会导致同类失败被拆开。 | 重要 | 后续增加稳定失败分类码或错误枚举字段，再保留 `errorMessage` 作为展示文案。 |
| 2-3 | 查询性能 | 当前依赖 `agt_task.status`、`agt_tool_call_log.task_id` 等已有索引，全局统计数据量大后可能变慢。 | 建议 | 等任务量上来后基于慢查询补 `created_at/status/agent_id` 组合索引或预聚合表。 |
| 2-4 | 告警闭环 | 指标和失败聚合目前只提供查询，不会触发后台告警。 | 建议 | 后续接入告警规则、阈值配置和通知渠道。 |

---

## 第六部分：阶段性结论

`module-agent` 后台已经具备任务排障总览能力，可以快速看到任务状态分布、工具调用失败数量和主要失败原因。当前实现没有引入 schema 变更，适合作为第一版只读运行治理入口。

下一步建议继续推进人工确认超时扫描的定时触发、批次记录和多实例并发控制，或开始接入 workflow/IM 的真实 Agent 触发链路。
