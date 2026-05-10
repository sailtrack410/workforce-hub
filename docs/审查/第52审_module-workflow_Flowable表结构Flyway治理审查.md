# 第 52 审 module-workflow Flowable 表结构 Flyway 治理审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/resources/db/migration/V11__init_flowable_process_schema.sql`、`server/src/main/resources/application.yml`、`server/src/test/java/com/workforcehub/support/WorkflowFlowableIntegrationTestSupport.java`、`docs/开发日志/module-workflow.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 Flowable `ACT_*` / `FLW_*` 表结构是否真正从自动建表收口到 Flyway 治理，并确认旧开发库、空库、异常半成品库的行为自洽。

---

## 第一部分：本轮内容概览

本轮把此前多次标记为待治理的 Flowable 引擎表正式纳入 Flyway：

- 新增 `V11__init_flowable_process_schema.sql`，初始化 Flowable 7.0.1 Process Engine 所需 `ACT_*` / `FLW_*` 表。
- `application.yml` 将 `flowable.database-schema-update` 默认值改为 `false`。
- workflow 专用集成测试基线关闭 Flowable 自动改库，验证流程引擎依赖 Flyway 初始化后的表结构运行。
- V11 对数据库状态做条件治理：空库创建、完整旧库跳过、半成品或版本不匹配库直接失败。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 空库初始化 | `env TEST_DB_NAME=workforce_hub_flowable_flyway_final mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test` | 通过 | Flyway 从 V1 到 V11 初始化全新库，Flowable 自动改库关闭时仍能跑通请假审批闭环。 |
| 半成品库拦截 | `env TEST_DB_NAME=workforce_hub_flowable_partial mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test` | 按预期失败 | 只预置 `ACT_GE_PROPERTY` 的半成品库在 V11 阶段报 `Partial Flowable schema detected before Flyway V11 migration`。 |
| workflow 回归 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test` | 通过 | 重建默认测试库后，25 个 workflow 集成用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `application.yml` | 默认 `flowable.database-schema-update=true` 会让 Flowable 绕过 Flyway 自动改库，和主业务库迁移治理原则冲突。 | 重要 | 默认改为 `false`，仅允许临时诊断旧库时显式打开。 |
| 1-2 | `WorkflowFlowableIntegrationTestSupport` | workflow 集成测试仍打开 Flowable 自动改库，会掩盖 V11 是否真正初始化了引擎表。 | 重要 | 测试基线改为 `flowable.database-schema-update=false`，流程闭环必须依赖 Flyway 表结构。 |
| 1-3 | `V11__init_flowable_process_schema.sql` | 如果只按 `ACT_GE_PROPERTY` 是否存在判断旧库，半成品 Flowable schema 会被误判为已存在并跳过建表，应用启动后才失败。 | 重要 | 改为检查 32 张必需 Flowable 表：0 张创建，32 张齐全继续校验版本，介于中间直接失败。 |
| 1-4 | `V11__init_flowable_process_schema.sql` | 已有 Flowable 表但版本不是 `7.0.1.1` 时，如果直接跳过会把不兼容结构纳入当前应用。 | 重要 | 完整旧库必须满足 `ACT_GE_PROPERTY.schema.version=7.0.1.1`，否则 V11 直接失败。 |
| 1-5 | `V11__init_flowable_process_schema.sql` | 迁移失败后临时存储过程可能残留，修复数据库后重试会被 `CREATE PROCEDURE` 卡住。 | 建议 | V11 开头和结尾都使用 `DROP PROCEDURE IF EXISTS` 清理临时过程。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 空库可交付性 | 全新测试库能一次性执行 V1 到 V11，并启动真实 Flowable 流程引擎。 |
| 自动改库边界 | 默认配置和 workflow 测试基线均关闭 Flowable 自动改库。 |
| 旧开发库兼容 | 完整且版本匹配的旧 Flowable 7.0.1.1 表结构不会重复建表。 |
| 异常库拦截 | 半成品 Flowable schema 会在 Flyway 迁移阶段失败，不会静默进入运行期。 |
| 文档状态 | 开发日志和后续计划已把 Flowable 表治理从待补改为已完成，并保留后续升级约束。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 未提交迁移的本地打磨流程 | 在本轮打磨 V11 期间，默认 `workforce_hub_test` 曾执行过旧版未提交迁移。最终版 V11 内容变化后，该测试库会出现 Flyway checksum mismatch。 | 建议 | 本轮已重建默认测试库并完成回归；以后未提交迁移反复修改时，也应优先用临时库验证，最终提交前重建测试库或执行 Flyway repair。 |
| 2-2 | Flowable 后续升级 | V11 固定 Flowable 7.0.1.1 初始结构。未来升级 Flowable 时如果直接修改 V11，会破坏已部署环境 checksum。 | 重要 | 后续升级必须新增 V12+ 增量迁移，不能修改已提交的 V11。 |
| 2-3 | Flowable 官方 SQL 来源维护 | V11 是从本地 Flowable 7.0.1 MySQL 初始化脚本整理而来，后续排查时需要能追溯来源。 | 建议 | 保留 `-- Source:` 注释；升级时重新从对应版本依赖中导出 SQL 并写入新迁移。 |

---

## 第六部分：阶段性结论

本轮后，`module-workflow` 的数据库治理边界已从“Flowable 开发环境自动建表”收口为“Flyway 统一管理”。这使后续 Docker Compose、生产初始化、测试库重建和版本升级都有明确结构来源。

下一步后端主线可以继续推进 `module-agent`：任务调度、工具调用、Agent 后台治理、运行身份和审批页展示。
