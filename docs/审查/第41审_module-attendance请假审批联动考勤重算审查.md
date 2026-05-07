# 第 41 审 module-attendance 请假审批联动考勤重算审查

- 审查日期：2026-05-07
- 审查提交：
  - `648582f..未提交工作区`（module-attendance 第四批：请假审批通过后联动考勤日结果重算、leave 查询 SPI、测试基线隔离、API 与开发日志同步）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java`、`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java`、`server/src/main/java/com/workforcehub/leave/`、`server/src/main/java/com/workforcehub/spi/`、`server/src/test/java/com/workforcehub/leave/ModuleLeaveWorkflowIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/`、`docs/API与测试/module-attendance/`、`docs/API与测试/module-leave/`、`docs/开发日志/`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 40 审提出的“请假通过后影响考勤日结果”P1 风险是否收口，并检查 workflow、leave、attendance 三个模块之间的事务边界、查询边界、测试隔离和后续扩展风险；不把半天假、小时假、节假日过滤、销假、补卡审批闭环纳入本轮已完成范围。

---

## 第一部分：提交内容概览

本轮新增 `AttendanceRecalculationService` SPI，由 `AttendanceService` 实现。`WorkflowService` 在直属上级审批通过请假申请后，按 `wf_req_leave.start_at` 到 `wf_req_leave.end_at` 的日期范围逐日调用考勤重算。`AttendanceService` 在日结果计算中通过 `LeaveQueryService.hasApprovedLeaveOnDate(userId, workDate)` 判断指定日期是否存在已批准请假，命中后将日结果写为 `LEAVE`，并把迟到、早退、缺勤、工时归零，同时清理该日已有异常。

`LeaveQueryService` 新增已批准请假覆盖查询，由 `LeaveService` 和 `LeaveRepository` 实现。查询条件限定 `wf_request.biz_type='LEAVE'`、`wf_request.status='APPROVED'`、未删除申请，并用请假开始和结束日期覆盖 `workDate`。

测试侧新增 `ModuleLeaveWorkflowIntegrationTest.approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult()`，覆盖“张三提交 2026-05-07 请假，直属上级审批通过后，张三查询当天日结果应为 `LEAVE`，缺勤为 0，异常标记为 false，异常表无记录”的路径。测试基类同步补齐 `att_` 与 workflow 表清理，避免跨模块读取已批准请假时发生测试污染。

文档侧同步更新 `module-attendance`、`module-leave` 的 API 文档、测试用例、开发日志和后续开发计划。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.recalculateDailyResult()`、`WorkflowService.completeManagerTask()`、`ModuleLeaveWorkflowIntegrationTest.approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult()` | 第 40 审 6-1 指出，请假审批已能扣减假期账户，但考勤日结果暂不识别已批准请假，员工获批请假后可能仍显示缺卡或异常。当前审批通过后会触发考勤重算，重算时识别已批准请假并写入 `LEAVE`，测试验证日结果和异常表均符合预期。 | 已处理 | 后续补销假、请假变更和补卡时，继续复用统一重算入口，不能绕过 `AttendanceRecalculationService` 直接修表。 |
| 2-2 | `LeaveQueryService.hasApprovedLeaveOnDate()`、`LeaveRepository.hasApprovedLeaveOnDate()` | 本轮没有让考勤模块直接读取 workflow 内部服务，而是把“已批准请假事实查询”收口到 `LeaveQueryService`。这样考勤只依赖 leave 对外语义，降低跨模块耦合。 | 已处理 | 后续如果新增销假、半天假和小时假，应优先扩展 leave 对外查询模型，而不是把 workflow 表细节扩散到 attendance。 |
| 2-3 | `WorkflowFlowableIntegrationTestSupport.resetBusinessTables()`、`LocalIntegrationTestSupport.resetBusinessTables()` | 考勤计算开始读取已批准请假状态后，如果普通测试或 workflow 专用测试不清理对方表，会出现顺序依赖和数据污染。当前 workflow 测试基线补充清理 `att_` 表，普通测试基线补充清理 workflow 表。 | 已处理 | 后续任何跨模块读模型都要同步检查测试基线清理范围，尤其是 im、agent、doc、kb 接入后。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `LeaveRepository.hasApprovedLeaveOnDate()`、`AttendanceService.recalculateDailyResult()` | 当前请假影响考勤采用日期级覆盖：只要已批准请假的开始日期和结束日期覆盖某个 `workDate`，该日就被写为整日 `LEAVE`。当员工只请上午半天、下午半天或 1 小时时，系统也会清空当天缺勤和异常，这会高估请假覆盖范围。当前测试只覆盖整日请假，因此挡不住半天假误算。 | P1 | 下一批请假深化时增加分钟级覆盖模型：leave 查询返回覆盖时间段或覆盖分钟数，考勤计算按班段、打卡和请假区间合并生成 `LEAVE`、`ABNORMAL` 或混合结果；在此之前，前端和文档必须明确第一版只支持整日请假语义。 |
| 3-2 | `WorkflowService.completeManagerTask()`、`AttendanceRecalculationService.recalculateDailyResultForExternalEvent()` | 审批通过、余额扣减、Flowable 完成、申请终态更新、待办关闭和考勤重算当前在同一事务链路内完成。好处是数据库内状态一致，但如果未来考勤重算加入外部副作用、通知或 Agent 触发，审批事务会被外部耗时和失败放大影响。 | P2 | 当前可以接受，因为重算只写本地 MySQL。后续如果重算触发消息、通知或 Agent，应改为审批提交后写入可重试业务事件或 outbox，由异步任务重算并记录失败明细。 |
| 3-3 | `AttendanceService.recalculateDailyResultForExternalEvent()`、`AttendanceService.punch()`、`AttendanceService.updateSchedule()` | 打卡、排班修改、请假审批现在都会触发同一员工同一天日结果重算，但还没有统一的 `userId + workDate` 串行化边界。并发下最后一次重算会覆盖前一次结果，短期因为都会重新读取当前库表状态风险可控，但补卡、销假、排班批量调整加入后，竞态路径会变多。 | P2 | 在进入补卡审批闭环前设计统一重算门面和锁策略，所有影响日结果的动作统一进入 `AttendanceCalculationService.recalculate(userId, workDate)`，并用数据库锁表或 Redis 锁保证同一天同员工串行。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | `AttendanceService` 已承担日历、班次、考勤组成员、排班、打卡、日结果计算、异常重建、外部事件重算等职责。本轮新增请假识别后，计算规则继续膨胀。后续补卡和日历明细如果继续追加，会让一个服务同时处理配置写入、员工行为和规则计算。 | P2 | 在补卡或半天假前拆出 `AttendanceCalculationService`，把“读取当前事实并计算日结果”从配置写入和打卡写入中抽离；排班写入可进一步拆到 `AttendanceScheduleService`。 |
| 4-2 | `LeaveRepository.hasApprovedLeaveOnDate()` | 已批准请假查询写在 `LeaveRepository` 中，但底层依赖 `wf_request` 与 `wf_req_leave` 表。它目前服务于 leave 对外语义，边界可以接受；但如果后续更多模块都需要审批事实，单个 leave repository 会开始承载 workflow 读模型。 | P2 | 后续根据使用面决定是否抽出 `LeaveFactQueryRepository` 或 workflow read model；至少要保持 SPI 层语义稳定，避免 attendance、agent 直接依赖 workflow 表。 |
| 4-3 | `LocalIntegrationTestSupport.resetBusinessTables()` | 普通测试基线已经开始清理 workflow 表，这是因为 attendance 会读取 workflow 产生的已批准请假事实。这个调整能解决污染，但也说明模块间读依赖越来越多，后续测试基类继续手工堆删除语句会变得脆弱。 | P2 | 中期应把业务表清理按模块分组封装，或生成统一清理清单；新增模块时只扩展清单，不在多个测试基类里重复维护大段 SQL。 |

## 第五部分：验证与未验证项

本轮已执行验证：

- `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult test`：红灯阶段先暴露审批通过后日结果为空，说明测试能捕获缺失的联动重算；实现后通过。
- `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`：已通过，覆盖请假 workflow 联动和考勤既有接口回归。
- `git diff --check`：通过，无空白错误。
- `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`：通过，`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`。
- `mvn -DskipTests compile`：通过，`BUILD SUCCESS`。

当前仍未验证：

- 半天假、小时假与打卡结果的分钟级合并。
- 多天请假中包含休息日、节假日时是否应生成或跳过考勤日结果。
- 销假、请假变更、审批撤销后对 `LEAVE` 日结果的回滚。
- 请假审批通过与员工同日打卡、管理员同日改排班并发触发重算的串行化。
- 补卡申请审批通过后对缺卡异常的修正。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-attendance` 补卡闭环 | 缺卡异常已经能识别，请假也能豁免整日缺勤，但员工仍不能通过补卡申请修正真实漏打卡。没有补卡闭环时，考勤异常治理仍不完整。 | P1 | 下一节点优先做补卡申请审批闭环：员工提交补卡申请，直属上级审批通过后写入补卡记录或修正打卡事实，并触发同一日结果重算。 |
| 6-2 | `module-attendance` 计算边界 | 请假、排班、打卡都已开始影响日结果，进入补卡前需要减少计算入口分散带来的竞态和维护成本。 | P2 | 设计 `AttendanceCalculationService` 和 `userId + workDate` 重算锁；先在补卡节点复用该入口，后续再迁移打卡和排班。 |
| 6-3 | `module-leave` 请假精细化 | 当前整日 `LEAVE` 能解决主流程冲突，但半天假和小时假会被高估为整日请假。 | P1 | 在补卡闭环后优先深化 leave/attendance 交叉计算，增加分钟级请假覆盖测试。 |

## 第七部分：阶段性结论

本轮没有发现阻断当前单实例单企业后端继续推进的 P0 问题。第 40 审提出的“请假通过后考勤日结果识别为请假而非缺勤”P1 风险已经通过 workflow 审批后触发考勤重算、leave 查询 SPI、`LEAVE` 日结果和集成测试阶段性收口。

阶段性结论是：可以进入下一节点。下一节点建议优先做“补卡申请审批闭环”，但进入补卡前应同步开始收敛日结果重算入口，避免补卡、请假、排班、打卡四条路径各自重算导致维护成本继续上升。
