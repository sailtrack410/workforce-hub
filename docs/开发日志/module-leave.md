# 开发日志 module-leave

## 2026-05-07 假种、假期账户与请假审批扣减最小闭环

### 背景

`module-workflow` 已经完成请假提交、直属上级审批、附件、模板治理和 Agent 预审入口，但请假还没有和真实假期余额发生关系。按照当前路线，本轮进入 `module-leave`，先做最小闭环：管理员能维护假种和员工余额，员工能查自己的余额，请假提交前能拦截余额不足，审批通过后能扣减余额并写流水。

本轮暂不做复杂年假规则、自动发放、结转、过期清零、节假日和排班日历扣减。

### 本次处理

- 新增 `module-leave` 后端包，包含 Controller、Service、Repository、DTO 和 Row。
- 新增后台接口：
  - `POST /api/admin/leave/types`
  - `POST /api/admin/leave/accounts/adjustments`
- 新增员工端接口：
  - `GET /api/employee/leave/accounts`
- `LeaveService` 实现 `LeaveQueryService`，为后续 Agent 查询假期余额预留 SPI。
- 管理员创建假种时校验 `leaveCode` 唯一、`balanceMode` 和 `status` 合法。
- 管理员调整余额时自动创建账户、更新 `available_minutes`、写入 `lv_account_txn`。
- 余额调整支持 `sourceTxnKey` 幂等，重复调用不会重复加余额或重复写流水。
- `LocalIntegrationTestSupport` 补充 `lv_` 表清理，避免普通接口测试污染假期数据。
- `WorkflowService` 接入 `LeaveService`：
  - 提交请假前，`DEDUCT` 假种校验当前员工余额。
  - 审批通过前，再次锁定账户并扣减余额。
  - 审批驳回、Agent 驳回、申请撤回不扣减。
- 旧 `ModuleWorkflowFlowableIntegrationTest` 和 `ModuleWorkflowAgentPreReviewIntegrationTest` 的测试基线补充足够假期余额，避免新余额规则干扰原有 workflow 关注点。
- 新增 `docs/API与测试/module-leave/`，记录 API 文档和测试用例。

### 红绿记录

- 红灯：新增 `ModuleLeaveApiIntegrationTest` 后，`POST /api/admin/leave/types` 返回 `404`。
- 绿灯：补齐后台假种创建、余额调整、员工余额查询接口后，`mvn -Dtest=ModuleLeaveApiIntegrationTest test` 通过。
- 红灯：新增 `ModuleLeaveWorkflowIntegrationTest` 后，余额不足提交请假仍返回 `code=0`，审批通过后余额仍为 `960`。
- 绿灯：`WorkflowService` 提交前调用余额校验，审批通过时调用扣减后，`mvn -Dtest=ModuleLeaveWorkflowIntegrationTest test` 通过。
- 回归红灯：`ModuleWorkflowFlowableIntegrationTest` 原有用例因测试数据未准备余额而被新规则拦截为 `假期余额不足`。
- 绿灯：原 workflow 测试基线补充足够余额后，`mvn -Dtest=ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleWorkflowFlowableIntegrationTest test` 通过。

### 当前完成范围

- 管理员可以创建假种。
- 管理员可以初始化或调整员工假期账户余额。
- 员工可以查询自己的假期账户。
- 管理员余额调整写入 `ADJUST` 流水。
- 余额调整幂等键生效。
- 请假提交前余额不足会被拦截。
- 请假审批通过后扣减余额并写 `DEDUCT` 流水。
- 审批驳回不扣减余额。
- `LeaveQueryService` 已具备 Agent 查询余额入口。

### 暂未覆盖

- 年假规则按工龄自动发放。
- 自动发放、结转、过期清零。
- 节假日、排班和考勤日历对扣减分钟数的影响。
- 提交时冻结余额，审批通过转扣减，驳回或撤回解冻。
- 管理员查询全部员工假期账户列表。
- 员工余额流水明细查询。

### 意义

- 请假审批从“流程可跑通”进入“会影响真实业务余额”的阶段。
- `lv_account_txn` 已经成为余额变化的审计来源，后续做年假规则、Agent 合规判断和报表统计时有可追溯基础。
- workflow 与 leave 的边界清晰：workflow 负责流程状态，leave 负责余额规则和流水。
