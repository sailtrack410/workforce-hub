# 第 62 审 module-agent 后台超时扫描批次查询审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AgentTaskTimeoutRunQuery.java`、`server/src/main/java/com/workforcehub/agent/dto/AdminAgentTaskTimeoutRunResponse.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentTaskTimeoutRunQueryCriteria.java`、`server/src/main/java/com/workforcehub/agent/repository/AdminAgentTaskTimeoutRunRow.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/test/java/com/workforcehub/agent/ModuleAgentTaskTimeoutIntegrationTest.java`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`、`docs/开发日志/module-agent.md`
- 审查目标：复核后台人工确认超时扫描批次查询是否补齐第 61 审遗留的可观测缺口，并确认路由、筛选、分页、SQL 参数化、测试和文档是否自洽。

---

## 第一部分：本轮内容概览

本轮在已有 `agt_task_timeout_job_run` 批次记录表之上补齐后台查询入口：

- 新增 `GET /api/admin/agent-tasks/timeout-scan-runs`。
- 新增 `AgentTaskTimeoutRunQuery`，支持 `pageNo`、`pageSize`、`triggerType`、`status`、`startedFrom`、`startedTo`。
- 新增 `AdminAgentTaskTimeoutRunResponse`，返回运行编号、触发方式、批次状态、批次大小、扫描数、取消数、跳过数、锁持有者、错误摘要和起止时间。
- `AgentRepository` 新增批次总数查询和分页查询。
- `AgentService` 新增枚举校验与响应映射。
- `ModuleAgentTaskTimeoutIntegrationTest` 新增批次查询成功路径和非法状态路径。
- API 文档、测试用例、开发日志和后续开发计划同步更新。

本轮没有新增数据库 schema 变更，继续复用 V12 已创建的 `agt_task_timeout_job_run`。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 原问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 第 61 审 3-3 | 已有批次记录表，但后台没有查询接口，管理员仍需查数据库才能看到历史扫描结果。 | P2 | 新增 `GET /api/admin/agent-tasks/timeout-scan-runs`，支持分页和筛选。 |
| 1-2 | 静态路由 | `timeout-scan-runs` 属于静态资源路径，若放在 `/{taskId}` 后面会被误解析为任务 ID。 | P1 | Controller 中 `/timeout-scan-runs` 位于 `/{taskId}` 前，并用红灯验证过误吞风险。 |
| 1-3 | 筛选输入 | `triggerType`、`status` 如果不做白名单校验，会让接口语义漂移。 | P2 | 服务层限定 `SERVICE/MANUAL/SCHEDULED` 和 `RUNNING/SUCCESS/SKIPPED/FAILED`。 |
| 1-4 | 文档状态 | 旧文档仍把批次查询列为待补。 | P3 | API 文档、测试用例、开发计划和开发日志均已校准。 |

---

## 第三部分：正确性与安全性复核

| 检查点 | 结论 |
| --- | --- |
| 认证边界 | 接口挂在 `/api/admin/agent-tasks` 下，复用后台管理员会话链路。 |
| 写入副作用 | 接口只读查询 `agt_task_timeout_job_run`，不触发扫描、不修改任务、不释放锁。 |
| SQL 注入 | 动态 SQL 只拼接固定条件片段，用户输入均通过参数绑定。 |
| 分页边界 | DTO 使用 `@Min/@Max`，服务层继续对 `pageNo/pageSize` 做安全兜底和最大 100 限制。 |
| 排序语义 | 按 `started_at DESC, id DESC` 返回最近批次，符合后台排障优先看最新记录的习惯。 |
| 错误语义 | 非法批次状态返回业务错误 `code=400`、`message=Agent超时扫描批次状态不支持`。 |

---

## 第四部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化建议 |
| --- | --- | --- | --- | --- |
| 2-1 | 批次查询性能 | 当前查询依赖 V12 中 `idx_agt_timeout_job_run_started_at` 和 `idx_agt_timeout_job_run_status`，但组合筛选 `triggerType + status + startedAt` 数据量大后仍可能需要更贴合后台查询的组合索引。 | P3 | 等真实数据量上来后按慢查询补 `trigger_type/status/started_at` 组合索引。 |
| 2-2 | 失败排障深度 | 接口只返回 `errorMessage` 摘要，不包含异常堆栈或失败任务明细。 | P2 | 后续接后台告警时补失败详情页或日志关联 ID。 |
| 2-3 | 运行中心复用 | `file`、`workflow`、`agent` 后续都会有后台任务运行记录，继续各自实现查询接口会产生重复模式。 | P3 | 当第二个模块需要类似能力时，评估抽出通用 `sys_job_run` 或后台任务运行中心。 |
| 2-4 | Agent 仓储膨胀 | `AgentRepository` 继续承载 Agent 定义、任务、指标、锁和批次查询 SQL。 | P2 | 下一轮如果继续扩展 Agent 调度能力，优先拆 `AgentTaskTimeoutRepository`。 |

---

## 第五部分：验证记录

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 批次查询红灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldListTimeoutScanRuns test` | 按预期失败 | 接口未实现时 `/timeout-scan-runs` 被 `/{taskId}` 误吞并返回 500。 |
| 批次查询绿灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminShouldListTimeoutScanRuns test` | 通过 | 覆盖手动扫描后按 `MANUAL/SUCCESS` 查询批次记录。 |
| 非法状态 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_invalid mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#adminTimeoutScanRunListShouldRejectUnsupportedStatus test` | 通过 | 覆盖非法批次状态业务错误。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17.0.18 下后端编译通过。 |
| 超时扫描专项 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_full mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 10 个用例通过，覆盖超时扫描服务、后台手动扫描、生产化批次、锁、定时禁用和批次查询。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_agent mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 26 个 Agent 用例通过。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_runs_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 67 个认证、组织、IM、Agent 用例通过。 |
| 差异格式检查 | `git diff --check` | 通过 | 未发现空白错误。 |

补充说明：本轮没有新增 Flyway 迁移，数据库 schema 未变化。

---

## 第六部分：阶段性结论

后台人工确认超时扫描批次查询已经补齐第 61 审提出的主要可观测缺口。当前实现是只读接口，没有引入新的 schema 变更，也没有扩大任务扫描副作用边界。主要剩余问题是后续调度能力增多后需要拆仓储、补告警和评估统一后台任务运行中心。

提交前编译、Agent 模块回归、跨模块回归和差异格式检查均已通过，本节点可提交。
