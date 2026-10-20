# 第 61 审 module-agent 人工确认超时扫描生产化审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/controller/AdminAgentTaskController.java`、`server/src/main/java/com/workforcehub/agent/dto/AgentTaskTimeoutScanResponse.java`、`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java`、`server/src/main/java/com/workforcehub/agent/scheduler/AgentTaskTimeoutJobService.java`、`server/src/main/resources/application.yml`、`server/src/main/resources/db/migration/V12__add_agent_timeout_job_records.sql`、`server/src/test/java/com/workforcehub/agent/ModuleAgentTaskTimeoutIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`server/src/test/java/com/workforcehub/support/WorkflowFlowableIntegrationTestSupport.java`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`、`docs/开发日志/module-agent.md`
- 审查目标：复核 Agent 人工确认超时扫描从手动服务升级为生产化任务后的正确性、并发互斥、批次记录、测试稳定性和文档一致性；本轮不审查会话 Agent、workflow 真实触发和后台批次查询页面。

---

## 第一部分：提交内容概览

本轮把 Agent 人工确认超时扫描从“后台手动触发”推进到“可生产运行”：

- 新增 `V12__add_agent_timeout_job_records.sql`。
- 新增 `sys_job_lock`，用于后台任务数据库互斥。
- 新增 `agt_task_timeout_job_run`，用于记录每次超时扫描批次。
- `AgentTaskTimeoutJobService` 新增 `runTimeoutScan(triggerType, batchSize)`，统一服务调用、手动触发和定时触发。
- `AgentTaskTimeoutJobService` 新增 `scheduledTimeoutScan()` 定时入口。
- 后台 `POST /api/admin/agent-tasks/timeout-scan` 从单一 `cancelledCount` 响应升级为运行摘要响应。
- `application.yml` 显式增加 `workforce.agent.timeout-scan.*` 配置。
- 集成测试基线关闭定时扫描副作用，避免长回归中后台任务抢跑。

本轮有数据库 schema 变更：新增 1 个 Flyway 迁移和 2 张表。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 原问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 超时扫描触发方式 | 上一轮只有服务调用和后台手动触发，生产环境无法周期自动收口过期等待任务。 | P1 | 新增 `@Scheduled` 定时入口，默认启用，支持环境变量关闭。 |
| 1-2 | 批次可追溯性 | 手动扫描只返回取消数，没有运行编号、触发方式、扫描数、跳过数和错误摘要。 | P1 | 新增 `agt_task_timeout_job_run`，接口返回完整运行摘要。 |
| 1-3 | 多实例并发 | 多实例同时扫描时可能重复抢同一批任务，虽有任务条件更新兜底，但缺少任务级互斥和跳过记录。 | P1 | 新增 `sys_job_lock`，锁未过期时记录 `SKIPPED` 批次并不处理任务。 |
| 1-4 | 测试副作用 | 注册 `@Scheduled` 后，长回归中定时扫描可能自动执行，影响人工构造的等待任务断言。 | P1 | 测试基线设置 `workforce.agent.timeout-scan.enabled=false`，并补充禁用后无副作用用例。 |
| 1-5 | 响应模型 | 旧 `AgentTaskTimeoutScanResponse.of(long)` 只表达取消数，和生产化运行摘要不一致。 | P2 | 已移除旧工厂方法，统一通过服务构建运行摘要响应。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 当前处理 |
| --- | --- | --- | --- | --- |
| 2-1 | `tryAcquireJobLock` 并发语义 | 数据库锁依赖 `ON DUPLICATE KEY UPDATE` 和 `locked_until <= now` 判断。该方案对单库 MySQL 有效，但多节点时钟偏差过大可能导致锁提前或延后释放。 | P2 | 第一版接受数据库时间窗口方案；后续如部署多节点，应统一服务器时间或改为数据库 `NOW()` 参与锁判断。 |
| 2-2 | 扫描失败状态 | 扫描过程中如果部分任务已取消后发生异常，当前批次会记录 `FAILED`，但已取消任务不会回滚到等待态。 | P2 | 扫描事务内异常会整体回滚；当前逐任务取消没有外部副作用，暂未发现半成功问题。后续接通知或回写时必须改为可重试明细。 |
| 2-3 | 锁释放失败 | 如果数据库短暂异常导致释放锁失败，后续扫描会等待 `locked_until` 过期。 | P2 | 当前锁 TTL 为 5 分钟，释放失败会记录 warn；后续可增加后台锁观测和告警。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 隐患描述 | 严重程度 | 优化建议 |
| --- | --- | --- | --- | --- |
| 3-1 | `AgentRepository` | Agent 仓储继续承载定义、任务、工具、指标和任务锁 SQL，类职责正在变宽。 | P2 | 后续进入批次查询或更多调度能力时，建议拆出 `AgentTaskTimeoutRepository` 或通用 `JobLockRepository`。 |
| 3-2 | 任务锁表 | `sys_job_lock` 是通用表，但当前仓储方法落在 Agent 仓储里，后续 file/workflow 清理任务复用时容易复制 SQL。 | P2 | 在第二个模块需要复用锁时，抽出 common infra 仓储，避免每个模块各写一套锁实现。 |
| 3-3 | 批次查询缺口 | 已有批次记录表，但后台暂时没有查询接口。管理员仍需查数据库才能看到历史扫描结果。 | P2 | 下一步可补 `GET /api/admin/agent-tasks/timeout-scan-runs` 或统一后台任务运行记录查询。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 生产化扫描红灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_red mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldCreateJobRunRecordAndReturnSummary test` | 按预期失败 | `runTimeoutScan(String,int)` 未实现时失败。 |
| 生产化扫描绿灯 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_green mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldCreateJobRunRecordAndReturnSummary test` | 通过 | 覆盖运行摘要和批次记录。 |
| 锁跳过与定时入口 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_more mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest#timeoutScanShouldSkipWhenAnotherInstanceHoldsLock,ModuleAgentTaskTimeoutIntegrationTest#timeoutScanServiceShouldExposeScheduledEntry test` | 通过 | 覆盖锁占用跳过和 `@Scheduled` 注解。 |
| 超时扫描专项 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_full mvn -Dtest=ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 8 个用例通过，覆盖服务、手动、批次、锁、定时入口和禁用保护。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17.0.18 下后端编译通过。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_agent mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 24 个用例通过，空库执行到 Flyway V12。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_timeout_prod_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 65 个认证、组织、IM、Agent 用例通过，空库执行到 Flyway V12。 |
| 差异格式检查 | `git diff --check` | 通过 | 未发现空白错误。 |

未验证项：

- 尚未做真实多 JVM 并发压测；当前只用预置未过期锁验证跳过语义。
- 尚未验证后台批次查询，因为该接口本轮未实现。

---

## 第六部分：处理优先级建议

| 优先级 | 建议 |
| --- | --- |
| P1 | 本轮已补跑 `mvn -DskipTests compile`、Agent 模块回归、跨模块回归和 `git diff --check`，提交前无需追加新的阻断验证。 |
| P2 | 后续拆出通用任务锁仓储，避免 `sys_job_lock` 被各模块重复实现。 |
| P2 | 后续补后台批次记录查询和失败告警，让 `agt_task_timeout_job_run` 不只停留在数据库排查层。 |
| P3 | 后续根据任务量决定是否为 `agt_task(status, waiting_deadline_at, id)` 补组合索引。 |

---

## 第七部分：阶段性结论

本轮将 Agent 人工确认超时扫描补齐到可生产运行的基础形态：有定时入口、有手动补偿入口、有数据库互斥、有批次记录、有测试环境禁用保护。当前没有发现阻断继续推进 `module-agent` 下一节点的问题。

最终编译、Agent 模块回归、跨模块回归和差异格式检查均已通过，可以进入下一步：补后台超时扫描批次查询，或转向 workflow/IM 的真实 Agent 触发链路。
