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

## 2026-05-07 请假审批通过后对外提供考勤覆盖查询

### 背景

考勤侧进入请假联动节点后，需要在日结果计算时判断某个员工某一天是否存在已批准请假。为了避免 `module-attendance` 直接依赖 workflow 内部 Repository，本轮把“已批准请假覆盖日期查询”放到 `LeaveQueryService` 中，由 `module-leave` 统一对外提供。

### 本次处理

- `LeaveQueryService` 新增 `hasApprovedLeaveOnDate(Long userId, LocalDate workDate)`。
- `LeaveService` 实现该接口，并委托 `LeaveRepository` 查询 `wf_request` 与 `wf_req_leave`。
- `LeaveRepository.hasApprovedLeaveOnDate()` 只统计 `biz_type=LEAVE`、`status=APPROVED`、未删除且日期范围覆盖 `workDate` 的申请。
- `module-attendance` 通过该 SPI 判断请假覆盖，不直接读取 workflow 明细服务。

### 红绿记录

- 红灯：`ModuleLeaveWorkflowIntegrationTest.approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult` 首次运行时，审批通过后考勤日结果没有生成 `LEAVE`。
- 绿灯：补齐查询 SPI 和考勤重算接入后，审批通过请假可以把覆盖日期日结果重算为 `LEAVE`。

### 当前完成范围增量

- 假期模块不仅负责余额，也成为“已批准请假事实”的对外查询边界。
- 考勤模块能用 `LeaveQueryService` 判断请假覆盖日期。

### 暂未覆盖

- 半天假、小时假和分钟级覆盖比例。
- 多天请假跨休息日、节假日时的扣减和考勤过滤。
- 提交冻结余额、审批通过转扣减、驳回解冻。

### 意义

- 请假事实查询从 workflow 内部表结构中抽象出来，降低考勤、Agent 后续接入时对流程内部实现的耦合。
- 后续如果新增销假、请假变更、请假撤回后返还等能力，可以继续在 `LeaveQueryService` 上扩展统一查询语义。

## 2026-05-08 已批准请假区间查询升级

### 背景

第 42 审 `3-2` 指出，考勤侧使用 `hasApprovedLeaveOnDate(userId, workDate)` 只能知道当天是否存在已批准请假，无法区分整日请假、半天假和小时假。由于 workflow 请假明细已经保存 `start_at/end_at` 和分钟级时长，leave 对外 SPI 继续返回布尔值会把考勤计算锁死在日期级豁免模型上。

### 本次处理

- `LeaveQueryService` 将 `hasApprovedLeaveOnDate()` 替换为 `listApprovedLeavePeriodsOnDate()`。
- 新增 `ApprovedLeavePeriod(startAt, endAt)` 作为跨模块查询结果，明确对外提供已批准请假事实区间。
- `LeaveRepository` 查询 `wf_request` 与 `wf_req_leave`，只返回 `biz_type=LEAVE`、`status=APPROVED`、未删除且与目标日期有交集的请假区间。
- 查询条件从 `DATE(start_at) <= workDate <= DATE(end_at)` 改为 `start_at < nextDayStart AND end_at > dayStart`，避免对列套函数，也能表达跨日区间与目标日期的交集。
- `LeaveService` 继续作为 SPI 实现方，考勤侧不直接读取 workflow 或 leave 内部表。

### 红绿记录

- 红灯：`approvedHourlyLeaveShouldOnlyOffsetCoveredAbsenceMinutes` 证明布尔覆盖会把 `09:00-10:00` 小时假误算为整日 `LEAVE`。
- 绿灯：区间查询接入考勤计算后，小时假只抵扣实际覆盖分钟。
- 红灯：`overlappingApprovedHourlyLeavesShouldNotDoubleOffsetAbsenceMinutes` 证明两段重叠小时假不能按简单分钟求和。
- 绿灯：考勤侧合并区间后，重叠部分只抵扣一次。

### 当前完成范围增量

- `module-leave` 对外提供已批准请假区间，不再只提供日期级布尔判断。
- `module-attendance` 可以基于 leave SPI 完成整段 `LEAVE` 和部分请假分钟抵扣两类计算。
- 跨模块边界仍保持在 SPI 层，未引入 attendance 对 workflow Repository 的直接依赖。

### 暂未覆盖

- 请假变更、销假、撤回后返还对已批准区间查询的影响。
- 节假日、休息日和工作日历对请假扣减分钟数的精细过滤。
- 提交冻结余额、审批通过转扣减、驳回或撤回解冻。

### 意义

- leave 模块从“能告诉考勤当天有没有请假”升级为“能告诉考勤当天哪些时间段已经请假”，支撑小时假和半天假的真实计算。
- 该 SPI 以后也可以给 Agent、月报和异常申诉复用，避免各模块重复拼 workflow 明细 SQL。

## 2026-05-08 余额调整幂等键绑定操作指纹

### 背景

第 42 审 `3-3` 指出，管理员余额调整只按 `sourceTxnKey` 查已有流水，命中后直接回放旧账户结果，没有校验本次请求是否仍是同一个员工、同一个假种、同一个调整分钟数。调用方一旦复用同一个幂等键发起不同余额调整，接口会返回成功但不调整目标账户，形成静默错账。

本轮不新增数据库字段，先用现有流水和账户数据组成最小操作指纹。余额调整指纹为 `source_type + txn_type + userId + leaveTypeId + changeMinutes`；审批扣减指纹额外绑定 `sourceId=requestId`。

### 本次处理

- `LeaveService.adjustAccount()` 在命中已有 `sourceTxnKey` 后，不再无条件回放旧账户结果。
- 新增 `ensureAdjustmentIdempotencyFingerprint()`，校验既有流水必须是 `ADMIN/ADJUST`，且既有账户的 `userId`、`leaveTypeId` 与本次请求一致，既有流水的 `changeMinutes` 也与本次请求一致。
- 指纹一致时继续回放已有账户结果，保证真正重复请求不会重复写流水或重复增加余额。
- 指纹不一致时返回 `code=409` 和 `幂等键已被不同余额调整操作使用`，不创建新账户、不写新流水、不调整余额。
- `deductForWorkflowApproval()` 命中 `WORKFLOW_REQUEST:{requestId}` 时同样校验既有流水必须是 `WORKFLOW_REQUEST/DEDUCT`，并绑定申请人、假种、扣减分钟数和 `requestId`。
- 审批扣减幂等键被管理员调整或其他扣减操作占用时返回 `code=409` 和 `幂等键已被不同假期扣减操作使用`，审批事务回滚，流程保持待审批。
- `ModuleLeaveApiIntegrationTest` 新增 `adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint`，覆盖同键不同分钟数、不同员工、不同假种三类冲突。
- `ModuleLeaveWorkflowIntegrationTest` 新增 `workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint`，覆盖审批扣减幂等键被非扣减流水占用时不能静默跳过扣减。
- 测试 helper 的 `availableMinutes()` 改为无账户返回 0，便于明确断言错误目标账户没有被创建或调整。

### 红绿记录

- 红灯：`mvn -Dtest=ModuleLeaveApiIntegrationTest#adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint test` 首次运行失败，第二次用同一 `sourceTxnKey` 调整不同分钟数时实际返回 `code=0`。
- 绿灯：补齐指纹校验后，同一测试通过，三类冲突均返回 `409`。
- 红灯：`mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint test` 首次运行失败，`WORKFLOW_REQUEST:{requestId}` 被管理员调整流水占用后审批仍返回 `code=0`。
- 绿灯：补齐审批扣减指纹校验后，同一测试通过，冲突时流程保持待审批，既有管理员调整后的余额保持不变。
- 回归：`mvn -Dtest=ModuleLeaveApiIntegrationTest test` 通过，原重复同 payload 幂等回放仍生效。
- 组合回归：`mvn -Dtest=ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest test` 通过，审批扣减和请假考勤联动未受影响。

### 当前完成范围增量

- 管理员余额调整幂等键不再只是“字符串去重”，而是绑定到具体余额调整操作。
- 同一幂等键复用到不同员工、不同假种或不同分钟数时会被显式拒绝，避免静默错账。
- workflow 审批扣减幂等键不再被非扣减流水静默吞掉，避免流程通过但余额未扣。
- `lv_account_txn` 仍保持第一版结构，现有字段已能支撑当前校验口径。

### 暂未覆盖

- 独立的 `operation_fingerprint` 字段和历史账本指纹补偿脚本。
- 余额冻结、解冻、发放、结转、过期清零等更多流水类型的统一幂等模型。
- 管理员余额调整操作审计详情和后台查询页。

### 意义

- leave 账本从“重复请求不重复写”升级为“重复请求必须语义相同才回放”，更符合账本类接口的幂等要求。
- 该规则后续可以沉淀到 `LeaveAccountLedgerService`，统一约束调整、发放、扣减、冻结和解冻等余额流水。
