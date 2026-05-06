# 第 35 审 module-workflow 流程模板后台治理审查

- 审查日期：2026-05-06
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowAdminApiIntegrationTest.java`、`docs/API与测试/module-workflow/`、`docs/开发日志/module-workflow.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `module-workflow` 新增后台流程类型、流程模板、模板版本草稿与发布治理后，版本状态、Flowable 定义绑定、员工端取版本语义和后续 Agent 规则入口是否自洽。

---

## 第一部分：本轮内容概览

本轮补齐第 34 审建议的“流程模板和模板版本后台治理”缺口：

- 新增后台流程类型创建接口。
- 新增后台流程模板创建接口。
- 新增后台模板列表查询接口，返回当前有效发布版本。
- 新增后台模板版本草稿创建接口。
- 新增后台模板版本列表查询接口。
- 新增后台模板版本发布接口。
- 发布时校验 Flowable 最新流程定义存在。
- 发布时写入 `flowable_definition_key` 和 `flowable_definition_id`。
- 同一模板发布新版本时自动归档旧的当前有效发布版本。
- `formSchemaJson` 与 `ruleSnapshotJson` 校验并稳定输出为紧凑 JSON。

本轮没有实现在线 BPMN 设计器。BPMN 文件仍由工程侧部署，后台只治理业务模板、版本、规则快照和 Flowable 定义绑定。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台模板治理测试 | `mvn -Dtest=ModuleWorkflowAdminApiIntegrationTest test` | 通过 | 3 个用例通过，覆盖创建、发布、Flowable 定义负例和新版本归档 |
| workflow 组合测试 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest test` | 通过 | 14 个用例通过，确认后台治理未破坏员工端审批闭环 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 下编译通过 |
| 已完成模块核心回归 | `mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest test` | 通过 | 72 个用例通过 |
| 空白与换行检查 | `git diff --check` | 通过 | 未发现空白错误 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `AdminWorkflowController` | 初始红灯测试访问 `POST /api/admin/workflow/process-types` 返回 `404`，后台模板治理入口不存在。 | 阻断 | 已新增后台 workflow Controller、Service、Repository 和 DTO。 |
| 1-2 | `WorkflowAdminService.publishTemplateVersion` | 如果发布时不校验 Flowable 定义，员工端提交申请会在启动流程实例时才失败，错误滞后且难排查。 | 重要 | 发布阶段已通过 `RepositoryService` 校验最新流程定义存在，不存在返回 `Flowable流程定义不存在`。 |
| 1-3 | `wf_template_version` 发布状态 | 如果新版本发布后旧版本仍保持当前有效 `PUBLISHED`，员工端查询最新发布版本只能靠排序兜底，治理语义不清。 | 重要 | 发布新版本前归档同模板旧的当前有效发布版本，并写入 `effective_to`。 |
| 1-4 | JSON 响应格式 | MySQL JSON 字段查询返回会带格式化空格，前端或测试如果依赖字符串会不稳定。 | 一般 | 服务响应层统一把 `formSchemaJson`、`ruleSnapshotJson` 压成紧凑 JSON。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 后台身份边界 | 接口挂在 `/api/admin/workflow`，受已有后台管理员会话和路径身份过滤保护。 |
| 版本发布入口 | 只允许 `DRAFT` 发布，避免重复发布或改写历史版本。 |
| Flowable 定义绑定 | 发布时绑定最新流程定义 ID，员工提交时按模板版本启动对应定义 Key。 |
| 当前有效版本 | 同一模板当前有效 `PUBLISHED` 版本保持唯一。 |
| 员工端兼容 | 员工端仍使用 `getPublishedTemplateVersion("LEAVE")` 查询最新有效发布版本，和本轮发布语义一致。 |
| Agent 入口 | `ruleSnapshotJson` 已有合法 JSON 维护入口，后续 Agent 预审可以按模板版本读取规则。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 模板治理能力 | 当前只支持创建和发布，不支持编辑模板状态、撤回版本、软删除或复制版本。 | 建议 | 后续进入多流程类型前补模板编辑、停用、版本复制和发布前检查清单。 |
| 2-2 | BPMN 生命周期 | 后台绑定已部署 Flowable 定义，但不负责部署 BPMN，流程定义文件仍由工程侧管理。 | 重要 | 生产化时明确 BPMN 发布流程，并把 Flowable 初始化 SQL 纳入 Flyway。 |
| 2-3 | Agent 规则语义 | `ruleSnapshotJson` 当前只校验合法 JSON，不校验业务 Schema。 | 建议 | 接入 Agent 预审前定义规则 Schema，并补校验和测试。 |
| 2-4 | 并发发布 | 当前事务能保证单次发布自洽，但没有显式加模板级锁，高并发同时发布不同版本时仍可能需要更严格保护。 | 建议 | 后续可在发布前对模板行 `SELECT ... FOR UPDATE` 或引入模板版本发布锁。 |

---

## 第六部分：处理优先级建议

**第一优先级：继续做 Agent 预审入口。**

- 当前模板版本已有规则快照入口。
- 下一步可以让提交申请后先进入 Agent 预审。
- Agent 判定不合规时自动驳回，写入 `wf_task_action` 和申请详情展示字段。

**第二优先级：开发 `module-leave` 假期账户闭环。**

- 请假审批通过后应扣减年假余额。
- 驳回和撤回不扣减。
- 后续还要按员工工龄生成年假额度。

**第三优先级：Flowable 生产治理。**

- 导出 Flowable MySQL 初始化 SQL。
- 纳入 Flyway。
- 生产环境关闭 `flowable.database-schema-update`。

---

## 第七部分：阶段性结论

本轮没有发现继续开发前必须阻断的问题。`module-workflow` 已从“固定请假审批闭环”推进到“流程模板可后台治理、版本可发布、规则快照可维护”的状态。

下一步如果继续 workflow，建议接 Agent 预审；如果转业务价值闭环，建议进入 `module-leave` 假期账户与余额扣减。
