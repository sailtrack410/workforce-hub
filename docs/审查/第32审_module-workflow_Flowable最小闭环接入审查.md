# 第 32 审 module-workflow Flowable最小闭环接入审查

- 审查日期：2026-05-06
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/main/resources/processes/`、`server/src/test/java/com/workforcehub/workflow/`、`server/src/test/java/com/workforcehub/support/WorkflowFlowableIntegrationTestSupport.java`、`server/src/test/java/com/workforcehub/support/WorkflowFlowableTestConfiguration.java`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`docs/API与测试/module-workflow/`、`docs/开发日志/module-workflow.md`
- 审查目标：复核 `module-workflow` 第一版直接接入 Flowable 后，员工请假申请、直属上级待办、审批通过、业务状态回写和测试隔离是否形成可继续开发的基线。

---

## 第一部分：本轮内容概览

本轮选择直接接入 Flowable，不再先做自研轻量审批路由。当前已形成第一条真实审批闭环：

- 员工端提交请假申请。
- 写入业务申请头 `wf_request`。
- 写入请假明细 `wf_req_leave`。
- 启动 Flowable 流程 `leave_manager_approval`。
- 直属上级收到 Flowable 用户任务 `manager_approval`。
- 同步生成统一待办 `sys_todo_item`。
- 直属上级审批通过。
- Flowable 流程实例结束。
- 审批动作写入 `wf_task_action`。
- 申请状态更新为 `APPROVED`。
- 统一待办状态更新为 `DONE`。

本轮新增了 `ModuleWorkflowFlowableIntegrationTest`，使用真实 Flowable Process Engine 跑通闭环，而不是只用 Mock 或静态代码检查。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| Flowable 请假审批闭环 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test` | 通过 | 覆盖提交、Flowable 任务、待办、审批通过、状态回写 |
| 普通组织模块回归 | `mvn -Dtest=ModuleOrgApiIntegrationTest test` | 通过 | 确认普通测试基线未被 Flowable 引擎反向耦合 |
| 差异格式检查 | `git diff --check` | 通过 | 未发现空白字符或补丁格式问题 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `EmployeeWorkflowController`、`WorkflowService` | 初版使用 `@ConditionalOnBean(RuntimeService.class)` 控制 workflow Bean 装配。复跑集成测试时 `/api/employee/workflow/leave-requests` 返回 `404`，说明 controller 在 Spring MVC 阶段没有注册。根因是业务 Bean 扫描条件和 Flowable RuntimeService 注册存在时序差异。 | 阻断 | 已改为 `workforce.workflow.enabled` 模块开关；生产默认启用，普通集成测试显式关闭，Flowable 集成测试显式开启。 |
| 1-2 | `LocalIntegrationTestSupport` | 普通集成测试默认排除 Flowable 自动配置，如果 workflow controller/service 无条件启用，会因缺少 Flowable Bean 导致上下文启动失败。 | 阻断 | 普通测试基线新增 `workforce.workflow.enabled=false`，保持非 workflow 模块测试隔离。 |
| 1-3 | `WorkflowFlowableIntegrationTestSupport` | workflow 测试需要真实 Flowable 引擎，不能继承普通测试的 Flowable 排除策略。 | 已处理 | workflow 专用测试基线保留 Process Engine，并设置 `workforce.workflow.enabled=true`。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| Flowable BPMN 部署 | 已自动部署 `leave-manager-approval.bpmn20.xml`，测试可启动流程实例。 |
| 直属上级任务分配 | `managerUserId` 作为 Flowable assignee，测试断言李四收到任务。 |
| 业务申请与明细关系 | `wf_request.biz_id = wf_req_leave.id`，`wf_req_leave.request_id = wf_request.id`，测试已锁定。 |
| 非 workflow 模块隔离 | `ModuleOrgApiIntegrationTest` 通过，普通测试没有被 Flowable 业务 Bean 拖坏。 |
| 审批通过事务边界 | 审批动作、Flowable complete、申请状态、待办关闭位于同一服务层事务中。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `application.yml`、Flowable `ACT_*` 表 | 当前 Flowable 引擎表仍依赖 `flowable.database-schema-update=true` 在开发环境自动维护。作为第一版开发闭环可以接受，但生产治理终态仍应把 Flowable 初始化 SQL 纳入 Flyway，并在生产环境设置 `FLOWABLE_DB_SCHEMA_UPDATE=false`。 | 重要 | 后续单独做 `module-workflow` 数据库治理批次：导出 Flowable 7.0.1 MySQL 初始化 SQL，纳入 Flyway 迁移，并补启动验证。 |
| 2-2 | `WorkflowService.approveTask` | 当前只实现审批通过，没有实现驳回、撤回、退回、转交、加签等动作。第一版闭环可用，但还不是完整审批域。 | 重要 | 下一批优先补驳回与撤回，并为每个动作补独立集成测试。 |
| 2-3 | `WorkflowRepository.listPendingTodos` | 待办查询直接 JOIN `ACT_RU_TASK`，当前能保证待办与运行任务一致，但业务仓储直接依赖 Flowable 运行表，后续流程复杂后 SQL 会变重。 | 建议 | 后续可以引入 `WorkflowTaskQueryService`，统一封装 Flowable 任务查询，业务仓储只处理业务表。 |
| 2-4 | `wf_template_version.flowable_definition_key` | 测试通过手工插入模板版本数据，暂未实现后台模板发布、BPMN 版本治理和定义 ID 回写。 | 建议 | 后续做后台流程模板治理接口，发布时写入 `flowable_definition_id` 和版本快照。 |
| 2-5 | Agent 预审 | 当前流程从提交直接进入直属上级人工审批，还没有 Agent 预审、自动驳回、人工可解释结论展示。 | 建议 | 在驳回/撤回稳定后接入 Agent 预审节点，优先通过 workflow SPI 或领域事件解耦 agent 具体实现。 |

---

## 第六部分：处理优先级建议

**第一优先级：补齐审批动作最小完整性。**

- 增加非审批人审批被拒绝测试。
- 增加审批驳回接口与测试。
- 增加申请人撤回接口与测试。
- 增加申请详情和审批轨迹查询接口。

**第二优先级：收口 Flowable 生产治理。**

- 明确 Flowable `ACT_*` 表由 Flyway 管理。
- 生产环境禁用 Flowable 自动改库。
- 文档同步启动、迁移、回滚规则。

**第三优先级：接入 Agent 与附件。**

- 请假提交支持附件引用。
- Agent 预审写入 `wf_task_action.agent_decision_json`。
- 不合规申请自动驳回，并保留可解释原因和审计记录。

---

## 第七部分：阶段性结论

当前 `module-workflow` 已经可以作为 Flowable 接入的第一版开发基线：它不是只写了接口壳，而是用真实 Process Engine 跑通了员工提交、直属上级审批、业务状态回写和统一待办关闭。

本阶段没有发现继续开发前必须阻断的问题。剩余风险主要是“审批域还不完整”和“Flowable 表结构还没有进入 Flyway 生产治理终态”。建议下一步继续沿着测试节点推进，优先补非审批人拦截、驳回、撤回、详情和轨迹，再进入 Agent 预审。
