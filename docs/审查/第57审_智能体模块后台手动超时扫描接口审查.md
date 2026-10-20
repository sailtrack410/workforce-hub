# 第 57 审 module-agent 后台手动超时扫描接口审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AgentTaskTimeoutScanRequest.java`、`server/src/main/java/com/workforcehub/agent/dto/AgentTaskTimeoutScanResponse.java`、`server/src/test/java/com/workforcehub/agent/ModuleAgentTaskTimeoutIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台是否能手动触发人工确认超时扫描，并确认批次参数、任务终态、工具日志和审计语义是否自洽。

---

## 第一部分：本轮内容概览

本轮在已有 `AgentTaskTimeoutJobService` 的基础上补后台手动触发入口：

- 新增 `POST /api/admin/agent-tasks/timeout-scan`。
- 新增 `AgentTaskTimeoutScanRequest`，约束 `batchSize` 为 1 到 500，默认 100。
- 新增 `AgentTaskTimeoutScanResponse`，返回本次 `cancelledCount`。
- 接口复用已有超时扫描服务，命中过期等待任务后将任务收口为 `CANCELLED`。
- 显式传 `batchSize:null` 时按默认 100 处理，避免 Controller 拆箱触发 500。

本轮没有新增 Flyway 迁移。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台手动扫描红灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_red2 mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldTriggerTimeoutScanManually test` | 按预期失败 | `POST /api/admin/agent-tasks/timeout-scan` 未实现，返回 404。 |
| 后台手动扫描绿灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldTriggerTimeoutScanManually test` | 通过 | 手动触发后过期等待任务进入 `CANCELLED`，工具日志和审计同步更新。 |
| 空批次红灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_null_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanShouldUseDefaultBatchSizeWhenBatchSizeIsNull test` | 按预期失败 | `batchSize:null` 触发空指针并返回 500。 |
| 空批次绿灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_null_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanShouldUseDefaultBatchSizeWhenBatchSizeIsNull test` | 通过 | `batchSize:null` 按默认批次处理。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_full2 mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 16 个 Agent 集成用例全部通过。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | 后端编译通过。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_admin_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 57 个集成用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 后台接口 | 超时扫描只有服务能力，没有后台 HTTP 入口，管理员无法手动收口过期等待任务。 | 重要 | 新增 `AdminAgentTaskController#triggerTimeoutScan`。 |
| 1-2 | 接口路径 | 初始测试路径使用 `/api/admin/agents/tasks/timeout-scan`，与任务资源命名不一致。 | 建议 | 改为 `/api/admin/agent-tasks/timeout-scan`，对齐员工端 `/api/employee/agent-tasks`。 |
| 1-3 | `AgentTaskTimeoutScanRequest` | 显式传 `batchSize:null` 时会在 Controller 里拆箱触发空指针，接口返回 500。 | 重要 | 增加 `safeBatchSize()`，空值按默认 100 处理。 |
| 1-4 | Controller 边界 | 如果继续把任务治理接口堆进 `AdminAgentController`，后续任务查询、批次记录和调度入口会让文件职责膨胀。 | 建议 | 新增独立 `AdminAgentTaskController`。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 认证边界 | 接口挂在 `/api/admin/agent-tasks`，复用后台管理员会话链路。 |
| 批次参数 | 请求层限制 1 到 500，空值按默认批次处理。 |
| 任务终态 | 过期等待任务进入 `CANCELLED`。 |
| 工具日志 | 待确认工具日志进入 `TIMEOUT/CANCELLED`。 |
| 审计记录 | 每个实际取消成功的任务写入 `TASK_TIMEOUT_CANCELLED`。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 定时触发 | 当前只有后台手动触发，还没有周期调度。 | 重要 | 后续接入统一调度器或 Spring `@Scheduled`，并提供开关配置。 |
| 2-2 | 多实例并发 | 当前依赖条件更新避免重复审计，但没有跨实例 job 锁或批次级运行记录。 | 重要 | 增加 job run 表或分布式锁，记录每次扫描批次。 |
| 2-3 | 后台任务查询 | 管理员可以触发扫描，但仍不能通过后台 API 查询等待、超时、取消任务。 | 重要 | 下一批补 `GET /api/admin/agent-tasks` 和详情接口。 |
| 2-4 | 操作审计 | 手动触发扫描本身没有独立后台操作审计，只能看到任务级 `TASK_TIMEOUT_CANCELLED`。 | 建议 | 等后台审计模块落地后记录操作人、批次大小、命中数和耗时。 |
| 2-5 | 参数策略 | 请求层限制最大 500，与服务层最大 500 一致；后续如果需要配置化，需要避免两处常量漂移。 | 建议 | 后续抽成配置项或集中常量。 |

---

## 第六部分：阶段性结论

`module-agent` 人工确认超时能力已从服务级能力推进到后台可操作入口。现在管理员可以在定时调度未接入前手动收口过期等待任务。

下一步建议优先补后台 Agent 任务查询接口，因为创建、确认、取消、停用和超时扫描都已有状态变化，后台需要可观测入口来排查任务状态。
