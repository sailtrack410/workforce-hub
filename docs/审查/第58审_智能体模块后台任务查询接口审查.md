# 第 58 审 module-agent 后台任务查询接口审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskResponse.java`、`server/src/main/java/com/workforcehub/agent/dto/AgentTaskListQuery.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentTaskQueryCriteria.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台 Agent 任务查询是否满足管理员排查需要，并确认分页、过滤、参数校验、SQL 聚合和文档记录是否自洽。

---

## 第一部分：本轮内容概览

本轮补齐后台 Agent 任务可观测入口：

- 新增 `GET /api/admin/agent-tasks`。
- 新增 `GET /api/admin/agent-tasks/{taskId}`。
- 新增后台任务查询参数 `AgentTaskListQuery`。
- 新增后台任务响应 `AdminAgentTaskResponse`。
- 新增仓储行对象 `AdminAgentTaskRow` 和查询条件 `AgentTaskQueryCriteria`。
- 支持按 `status`、`agentId`、`triggeredByType`、`triggeredById`、`contextType`、`createdFrom`、`createdTo` 过滤。
- 列表和详情返回 `toolCallCount`，便于后台判断任务是否已产生工具调用。

本轮没有新增 Flyway 迁移。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台任务查询红灯 | `env TEST_DB_NAME=workforce_hub_agent_task_query_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListAndGetAgentTasks test` | 按预期失败 | `GET /api/admin/agent-tasks` 未实现，返回 404。 |
| 后台任务查询绿灯 | `env TEST_DB_NAME=workforce_hub_agent_task_query_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListAndGetAgentTasks test` | 通过 | 覆盖分页、状态/Agent/触发人/上下文过滤和详情查询。 |
| 非法状态边界 | `env TEST_DB_NAME=workforce_hub_agent_task_query_invalid_status mvn -Dtest=ModuleAgentApiIntegrationTest#adminTaskListShouldRejectUnsupportedStatus test` | 通过 | `status=UNKNOWN_STATUS` 返回 `code=400`。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | 后端编译通过。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_task_query_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 18 个 Agent 用例通过。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_task_query_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 59 个认证、组织、IM、Agent 相关用例通过。 |

补充说明：本地集成测试基类会清理共享 Redis 测试 key，模块回归和跨模块回归应串行执行；多个 Maven 进程并行跑会互相清理会话与登录失败计数，导致非业务原因的 `401` 或计数断言假失败。

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 后台可观测性 | Agent 任务创建、确认、取消、停用和超时扫描已有状态变化，但后台没有任务查询入口。 | 重要 | 新增任务列表和详情接口。 |
| 1-2 | Controller 职责 | 如果继续把任务查询放进 `AdminAgentController`，Agent 定义治理和任务治理会混在一个文件里。 | 建议 | 继续使用 `AdminAgentTaskController` 承载任务治理接口。 |
| 1-3 | 查询参数 | 任务状态如果不校验，任意字符串都会被当成查询条件，接口语义不清。 | 重要 | `AgentService#normalizeOptionalIn` 校验 `status` 和 `triggeredByType`。 |
| 1-4 | SQL 聚合 | 列表需要返回工具调用数量，直接 `COUNT(log.id)` 如果没有 `GROUP BY` 会在严格 SQL 模式下存在风险。 | 重要 | 查询列表和详情均显式按任务与 Agent 字段 `GROUP BY`。 |
| 1-5 | SQL 注入边界 | 动态筛选容易误拼用户输入。 | 重要 | 动态 SQL 只拼固定条件片段，用户值全部走参数化绑定。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 认证边界 | 接口挂在 `/api/admin/agent-tasks`，复用后台管理员会话链路。 |
| 分页边界 | `pageNo` 最小 1，`pageSize` 最大 100。 |
| 状态过滤 | 支持已存在任务终态和运行态，非法状态返回业务错误。 |
| 触发主体过滤 | 支持 `USER`、`ADMIN`、`SYSTEM`、`WORKFLOW`、`TIMER`、`EVENT`。 |
| 工具调用数量 | 使用 `LEFT JOIN` 聚合，0 工具任务也能返回。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 查询性能 | 当前没有为 `created_at`、`triggered_by_type + triggered_by_id`、`context_type` 增加组合索引。小数据量可接受，任务量上来后可能慢。 | 重要 | 后续结合慢查询补专门索引迁移。 |
| 2-2 | 详情深度 | 任务详情当前只返回任务基本信息和工具调用数量，没有工具调用明细与审计轨迹。 | 重要 | 后续补 `GET /api/admin/agent-tasks/{taskId}/tool-calls` 和审计轨迹接口。 |
| 2-3 | 运行监控 | 当前列表不能直接返回耗时、失败类型聚合、重试次数或运行指标。 | 建议 | 等异步执行、重试和监控节点统一设计。 |
| 2-4 | 时间范围语义 | `createdFrom/createdTo` 使用应用层传入的 ISO 时间，没有时区字段。 | 建议 | 后续统一 API 时间规范，必要时明确服务端时区。 |

---

## 第六部分：阶段性结论

`module-agent` 后台已经具备任务列表和详情查询能力，能支持管理员按状态、Agent、触发人和上下文排查任务。当前实现没有引入 schema 变更，适合作为第一版可观测入口。

下一步建议继续补任务工具调用明细和审计轨迹，或者转入 workflow/IM 真实触发接入前的 Agent 任务可观测补强。
