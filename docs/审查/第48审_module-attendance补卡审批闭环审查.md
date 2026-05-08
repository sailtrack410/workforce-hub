# 第 48 审 module-attendance补卡审批闭环审查

- 审查日期：2026-05-08
- 审查提交：
  - 工作区待提交变更（实现 `CLOCK_CORRECTION` 补卡申请审批通过后写入人工补卡事实并重算日结果）
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/main/java/com/workforcehub/attendance/`、`server/src/main/java/com/workforcehub/spi/AttendanceClockCorrectionService.java`、`server/src/main/resources/processes/clock-correction-manager-approval.bpmn20.xml`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowFlowableIntegrationTest.java`、`docs/API与测试/module-workflow/`、`docs/API与测试/module-attendance/`、`docs/开发日志/`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核补卡是否作为独立审批业务类型接入 workflow，而不是伪装成请假；重点检查提交、明细、审批通过副作用、考勤人工补卡写入、同日重算、文档和测试是否自洽。

---

## 第一部分：提交内容概览

本轮围绕“缺卡异常通过正式审批修正”做最小闭环。

- 新增员工端接口 `POST /api/employee/workflow/clock-correction-requests`。
- 新增补卡流程 BPMN：`clock_correction_manager_approval`。
- 新增 `ClockCorrectionRequestCreateRequest`、`WorkflowClockCorrectionDetailResponse`、`WorkflowClockCorrectionDetailRow`。
- `WorkflowRepository.insertWorkflowRequest(...)` 改为显式传入 `bizType` 和 `bizId`，不再固定写死 `LEAVE`。
- 补卡申请写入 `wf_request.biz_type=CLOCK_CORRECTION` 与 `wf_req_clock_correction`。
- 申请详情新增 `clockCorrection` 明细。
- 审批通过时按 `wf_request.biz_type` 分支：请假走假期扣减和请假重算，补卡走 `AttendanceClockCorrectionService`。
- 新增 `AttendanceClockCorrectionService`，由 `AttendanceService` 实现补卡打卡事实落地。
- 补卡通过后写入 `source_type=MANUAL` 的 `att_punch_record`，并触发同日 `att_daily_result` 重算。
- `ModuleWorkflowFlowableIntegrationTest` 新增补卡审批闭环集成测试。
- API 文档、测试用例、开发日志、后续开发计划和审查索引已同步更新。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `EmployeeWorkflowController.submitClockCorrectionRequest()` | 员工端此前没有补卡申请入口，历史漏打卡只能停留为缺卡异常。当前新增独立补卡提交接口，使用 `ClockCorrectionRequestCreateRequest` 表达补卡日期、缺卡类型、补卡时间、原因和附件。 | 已处理 | 后续前端应把缺卡异常详情中的“发起补卡”直接指向该接口，而不是让员工重新发起普通请假或联系管理员改库。 |
| 2-2 | `WorkflowRepository.insertWorkflowRequest(...)` | 原申请头写入固定 `biz_type=LEAVE`，会阻塞多业务审批扩展。当前改为显式传入 `bizType`，补卡写入 `CLOCK_CORRECTION`，并让 `biz_id` 指向 `wf_req_clock_correction.id`。 | 已处理 | 后续新增加班、外出、调休、HR 变更时继续沿用 `bizType + bizId`，不能再写死某个业务类型。 |
| 2-3 | `WorkflowService.completeManagerTask(...)` | 审批通过原逻辑默认读取请假明细并扣减假期余额。如果补卡复用该路径，会误触发请假余额扣减或因为缺少请假明细失败。当前按 `bizType` 分支处理，请假和补卡副作用分离。 | 已处理 | 后续应把不同业务类型的审批通过副作用进一步下沉为策略或 SPI，避免 `WorkflowService` 随业务类型增加继续膨胀。 |
| 2-4 | `AttendanceService.applyApprovedClockCorrection(...)` | 历史打卡修正必须保留服务端可信边界，不能重新开放员工端传历史 `punchAt`。当前只有 workflow 审批通过后才能通过 SPI 写入 `MANUAL` 补卡事实。 | 已处理 | 后续管理员直接补卡、异常申诉通过等入口也应复用同一考勤补卡落地边界，不能绕过幂等和重算。 |
| 2-5 | `ModuleWorkflowFlowableIntegrationTest.approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult()` | 新测试覆盖了缺下班卡、提交补卡、直属上级审批、补卡详情、人工打卡写入、日结果变正常和 `MISS_PUNCH` 清理。 | 已处理 | 后续补卡规则治理时，应继续补负例：超期补卡、次数超限、无直属上级、补卡日期不一致、补卡时间不在班次窗口。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `WorkflowService.completeManagerTask(...)` | 审批通过后的业务副作用现在开始按 `bizType` 分支。当前只有 `LEAVE` 和 `CLOCK_CORRECTION` 两类，仍可读；但继续加入加班、外出、调休后，`WorkflowService` 会承担过多业务编排，容易出现某个类型忘记处理或错误复用其他类型副作用。 | P2 | 在第三个以上业务类型接入前，抽出 `WorkflowBizApprovalHandler` 策略接口，按 `bizType` 注册处理器。 |
| 3-2 | `AttendanceService.applyApprovedClockCorrection(...)` | 当前补卡幂等判断基于同一员工、同一时间、同一打卡类型已有 `NORMAL` 记录就不再写入。该规则能防止重复回调，但如果真实业务允许同一时间多来源打卡证据并存，当前会合并为一条事实。 | P3 | 第一版可接受；后续若要保留多证据，应新增补卡申请 ID 到打卡记录或补充补卡流水表，幂等键改为 `requestId`。 |
| 3-3 | 补卡规则治理 | 当前只校验补卡类型和补卡时间日期一致，尚未限制补卡次数、申请窗口、是否必须针对已有缺卡异常、是否必须附件。真实企业规则缺失时，员工可以申请任意历史日期补卡，只要直属上级审批通过就会落地。 | P2 | 下一批补后台补卡规则表或模板规则快照，并让 workflow/Agent 在提交或预审阶段校验。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 打卡、排班、请假抵扣、日结果重算和补卡落地仍集中在一个服务类。补卡闭环复用了现有重算逻辑，短期保持一致；但服务边界已经明显变宽。 | P2 | 下一节点优先拆 `AttendanceCalculationService`，把日结果重算、原子 upsert、异常重建、请假抵扣和补卡触发集中到计算服务。 |
| 4-2 | `ModuleWorkflowFlowableIntegrationTest` | workflow 集成测试类已经覆盖请假、审批、撤回、列表、附件、下载、补卡等多条链路。继续增加业务类型会降低定位效率。 | P3 | 后续按 `ModuleWorkflowLeaveFlowableIntegrationTest`、`ModuleWorkflowClockCorrectionIntegrationTest`、`ModuleWorkflowAttachmentIntegrationTest` 拆分。 |
| 4-3 | `clock_correction_manager_approval.bpmn20.xml` | 补卡 BPMN 与请假 BPMN 当前结构完全相同，仅流程 ID 和名称不同。短期可读；长期如果所有单节点审批都复制 BPMN，会产生大量重复流程定义。 | P3 | 等业务类型稳定后评估是否引入通用直属上级审批定义，或保留独立 BPMN 但用模板治理统一发布规则。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证：补卡接口缺失 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult test` | 失败符合预期 | 实现前 `POST /api/employee/workflow/clock-correction-requests` 返回 `404`。 |
| 针对性绿灯：补卡审批闭环 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult test` | 通过 | 输出为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。 |
| module-workflow 回归 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` | 通过 | 输出为 `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`。 |
| workflow 后台治理与 Agent 预审回归 | `mvn -Dtest=ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test` | 通过 | 输出为 `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`。 |
| leave + attendance 回归 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 22, Failures: 0, Errors: 0, Skipped: 0`。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | Maven 编译成功，JDK 17 enforcer 校验通过。 |
| 空白检查 | `git diff --check` | 通过 | 未发现行尾空白或补丁格式问题。 |

未验证项：

- 尚未执行全仓全部集成测试组合。
- 尚未覆盖补卡审批驳回后异常展示仍保持待处理。
- 尚未覆盖补卡规则治理、次数限制、超期限制和 Agent 自动驳回。
- 尚未覆盖多实例并发审批同一补卡任务的重复回调场景。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `AttendanceService` 服务边界 | 补卡闭环已落地，但考勤计算主服务继续变宽。继续加月报、节假日、异常申诉前应先拆计算边界。 | P2 | 下一节点拆 `AttendanceCalculationService`，保证打卡、请假、补卡、排班变更都调用同一重算服务。 |
| 6-2 | 补卡规则治理 | 当前补卡只靠直属上级人工审批，没有后台规则和 Agent 预审。真实企业场景容易出现频繁补卡、超期补卡和无证明补卡。 | P2 | 增加补卡规则配置，并接入 Agent 预审：超期、次数超限、缺附件可直接驳回或转人工复核。 |
| 6-3 | 工作日历与节假日 | 补卡和请假都依赖日结果计算，但当前仍缺工作日历日明细、节假日和休息日。 | P2 | 在月报前补 `att_work_calendar_day` 读取、休息日跳过和节假日特殊规则。 |

---

## 第七部分：阶段性结论

补卡审批闭环已具备开发基底价值：员工可以对缺卡发起正式审批，直属上级通过后系统写入 `MANUAL` 打卡事实，并复用同一日结果重算路径把缺卡异常修正为正常结果。workflow 也从请假专用审批推进到 `bizType` 驱动的多业务审批底座。

当前不建议继续往 `AttendanceService` 和 `WorkflowService` 内直接堆更多业务类型。下一阶段更适合做两个收口：一是拆考勤重算服务边界，二是补补卡规则治理和 Agent 预审，让“能补卡”进一步升级为“按企业规则可控地补卡”。
