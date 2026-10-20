# 第 36 审 module-workflow Agent 预审入口审查

- 审查日期：2026-05-07
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/main/java/com/workforcehub/spi/`、`server/src/main/resources/db/migration/V10__add_workflow_agent_pre_review_task_id.sql`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowAgentPreReviewIntegrationTest.java`、`docs/API与测试/module-workflow/`、`docs/开发日志/module-workflow.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 Agent 预审入口接入后，workflow 与 agent 的依赖边界、申请状态机、Flowable 启动时机、Agent 回调幂等边界和文档记录是否自洽。

---

## 第一部分：本轮内容概览

本轮完成 workflow 侧 Agent 预审最小闭环：

- `WorkflowService` 实现 `WorkflowAgentResultCallback`，提供 Agent 结果回写入口。
- 请假提交后可选触发 `WorkflowAgentPreReviewTrigger`。
- Agent 返回任务 ID 时，申请进入 `PENDING_REVIEW`，不启动 Flowable。
- Agent 返回 `null` 时，保持原直属上级 Flowable 审批行为。
- Agent 通过或转人工后，workflow 写预审动作并启动直属上级审批。
- Agent 驳回后，workflow 写预审动作并直接终止申请。
- `wf_task_action.agent_decision_json` 进入审批轨迹响应。
- 新增 `wf_request.agent_pre_review_task_id`，回调必须匹配当前申请上的 Agent 任务 ID。

本轮没有实现完整 `module-agent` 平台。Agent 定义、版本、工具绑定、任务调度、实际规则执行和后台创建仍在后续 `module-agent` 阶段完成。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| Agent 预审任务 ID 红灯 | `mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest#agentCallbackShouldMatchPendingAgentTaskId test` | 先失败后通过 | 先确认错误任务 ID 可串写，再用 `agent_pre_review_task_id` 修复 |
| Agent 预审测试 | `mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest test` | 通过 | 6 个用例通过 |
| workflow 组合回归 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test` | 通过 | 20 个用例通过 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `WorkflowAgentResultCallback` | 初始红灯测试没有任何 workflow 侧回调 Bean，`module-agent` 后续无法把结果写回 workflow。 | 阻断 | `WorkflowService` 实现回调接口，并用事务收口状态流转。 |
| 1-2 | `WorkflowService.submitLeaveRequest` | 原提交逻辑直接启动 Flowable，Agent 无法在人工审批前介入。 | 阻断 | 拆出 `startHumanApproval`，提交后先可选触发 Agent，只有无需预审或预审通过后才启动 Flowable。 |
| 1-3 | 测试触发器 | 测试触发器是单例，关闭后会污染后续用例。 | 一般 | 增加 `reset`，每个测试用例前恢复默认启用。 |
| 1-4 | `agent_decision_json` 响应 | MySQL JSON 输出对象键顺序不稳定，接口测试和前端展示容易出现字符串差异。 | 一般 | 响应层将 Agent 决策 JSON 压缩并稳定输出 `decision`、`reason` 优先顺序。 |
| 1-5 | Agent 回调匹配 | 只校验申请处于 `PENDING_REVIEW` 不够，错误 Agent 任务 ID 也能回写申请结果。 | 重要 | 新增 `wf_request.agent_pre_review_task_id`，提交时记录，回调时强制匹配。 |
| 1-6 | Flyway 迁移治理 | 曾尝试直接修改已执行的 `V1__init_schema.sql`，会触发历史 checksum mismatch。 | 重要 | 已还原 V1，只保留 V10 增量迁移。 |
| 1-7 | `WorkflowService.ensurePendingAgentPreReview` | 空 `agentTaskId` 回调会触发空指针异常，不利于后续内部调用或 HTTP 封装返回稳定业务错误。 | 一般 | 增加空值防御，并补空任务 ID 回调拒绝测试。 |
| 1-8 | `wf_task_action.after_status` | Agent 通过或转人工动作曾把决策枚举写入状态字段，容易和申请状态机混用。 | 一般 | `after_status` 统一记录申请状态 `IN_APPROVAL`，Agent 决策枚举保存在 `agent_decision_json.decision`。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| workflow 与 agent 边界 | workflow 只依赖 SPI，没有引入 `module-agent` 具体类。 |
| Flowable 启动时机 | Agent 预审中不启动 Flowable；通过或转人工后才启动直属上级审批。 |
| 自动驳回闭环 | Agent 驳回直接 `REJECTED`，不生成直属上级待办。 |
| 审批轨迹 | Agent 动作以 `actor_type=AGENT`、`action_type=AGENT_PRE_REVIEW` 记录，并返回 `agentDecisionJson`。 |
| 回调串写防护 | 回调 `agentTaskId` 必须匹配 `wf_request.agent_pre_review_task_id`。 |
| 旧流程兼容 | 触发器返回 `null` 时仍直接进入原 Flowable 人工审批链路。 |
| 测试覆盖 | 预审等待、通过、驳回、任务 ID 不匹配、任务 ID 为空、无 Agent 任务共 6 条路径已自动化。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `WorkflowAgentPreReviewTrigger` | 当前触发器只返回任务 ID，没有表达任务创建失败、降级原因或预审规则快照版本。 | 建议 | `module-agent` 阶段把触发结果扩展为对象，包含任务 ID、规则版本、降级策略和错误码。 |
| 2-2 | `WorkflowAgentResultCallback` | 当前回调不是 HTTP API，后续 agent 模块落地时还需要明确内部调用方式、鉴权和重放策略。 | 重要 | 在 `module-agent` 阶段设计内部服务调用或事件总线，并补幂等回放测试。 |
| 2-3 | 幂等回调 | 同一 Agent 任务重复回调时当前会因状态已变更而拒绝，但没有返回“已处理”的幂等语义。 | 建议 | 后续根据 agent 任务重试机制决定是否提供幂等查询或重复回调成功返回。 |
| 2-4 | Agent 规则执行 | workflow 目前不读取 `ruleSnapshotJson`，只是提供预审入口和回调闭环。 | 重要 | `module-agent` 阶段实现规则读取、工具调用和决策生成。 |
| 2-5 | 前端展示 | 后端已经返回 `agentDecisionJson`，但人工审批页尚未展示 Agent 结论。 | 建议 | 前端 workflow 详情页增加 Agent 预审卡片和风险说明。 |

---

## 第六部分：处理优先级建议

**第一优先级：进入 `module-leave` 假期账户闭环。**

- workflow 已经能产生通过、驳回、撤回和 Agent 驳回结果。
- 下一步应让请假审批通过后扣减年假余额。
- 驳回、撤回、Agent 驳回不扣减。

**第二优先级：后续开发完整 `module-agent`。**

- 后台创建 Agent。
- Agent 版本、工具绑定、ACL。
- Agent 任务状态和重试。
- 读取 `ruleSnapshotJson` 并调用工具形成预审结论。

**第三优先级：Flowable 生产治理。**

- 导出 Flowable MySQL 初始化 SQL。
- 纳入 Flyway。
- 生产关闭 `flowable.database-schema-update`。

---

## 第七部分：阶段性结论

本轮没有发现继续进入下一节点前必须阻断的问题。`module-workflow` 已具备“提交申请 -> Agent 预审 -> 自动驳回或转人工审批 -> 轨迹可查”的后端最小闭环。

下一步建议按规划进入 `module-leave`，把请假审批结果和假期账户余额扣减打通。
