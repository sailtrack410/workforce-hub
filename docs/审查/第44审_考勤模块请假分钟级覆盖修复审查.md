# 第 44 审 module-attendance请假分钟级覆盖修复审查

- 审查日期：2026-05-08
- 审查提交：
  - 工作区待提交变更（修复第 42 审 `3-2` 请假按日期整日豁免问题）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java`、`server/src/main/java/com/workforcehub/spi/LeaveQueryService.java`、`server/src/main/java/com/workforcehub/leave/`、`server/src/test/java/com/workforcehub/leave/ModuleLeaveWorkflowIntegrationTest.java`、`docs/API与测试/module-attendance/`、`docs/API与测试/module-leave/`、`docs/开发日志/`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 42 审 `3-2` 是否从日期级整日豁免修正为区间级分钟覆盖，重点检查小时假、重叠请假、迟到早退抵扣、测试保护和文档契约；本审不处理第 42 审其他 P1。

---

## 第一部分：提交内容概览

本轮围绕“已批准请假影响考勤的计算粒度”做修复。

- `LeaveQueryService` 从 `hasApprovedLeaveOnDate()` 升级为 `listApprovedLeavePeriodsOnDate()`，对外返回已批准请假区间。
- `LeaveRepository` 查询与目标日期有交集的 `APPROVED` 请假明细，返回 `start_at/end_at`，不再只做日期级布尔判断。
- `AttendanceService` 在日结果重算时按班次窗口计算请假覆盖分钟；整段班次被覆盖时仍写 `LEAVE`。
- 部分时段请假不再把整天改成 `LEAVE`，而是按分钟抵扣迟到、早退和缺勤窗口。
- 多段或重叠请假区间会先合并再计算覆盖分钟，避免重复抵扣。
- `ModuleLeaveWorkflowIntegrationTest` 新增 3 个回归测试：小时假只抵扣实际缺勤、重叠小时假不重复抵扣、小时假抵扣迟到和早退窗口。
- 文档同步更新 module-attendance、module-leave API 契约、测试用例、开发日志和后续开发计划。
- 测试执行前发现 `BizException` 类名被误改导致编译失败，本轮恢复为 `BizException`，作为构建基线修复纳入验证。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `LeaveQueryService.listApprovedLeavePeriodsOnDate()`、`LeaveRepository.listApprovedLeavePeriodsOnDate()` | 第 42 审 `3-2` 指出原 `hasApprovedLeaveOnDate()` 只能返回某天是否有请假，导致考勤无法区分整日请假、半天假和小时假。本轮 SPI 已改为返回已批准请假时间段，查询条件也改为与目标日期窗口求交集。 | 已处理 | 后续销假、请假变更、撤回后返还等能力继续通过该 SPI 统一表达有效请假区间，不要回退为布尔判断。 |
| 2-2 | `AttendanceService.recalculateDailyResult()`、`ModuleLeaveWorkflowIntegrationTest.approvedHourlyLeaveShouldOnlyOffsetCoveredAbsenceMinutes()` | 原逻辑只要当天存在已批准请假就写整日 `LEAVE`。本轮改为整段班次被覆盖才写 `LEAVE`；`09:00-10:00` 小时假且全天无打卡时，结果为 `ABNORMAL`，`absenceMinutes=480`。 | 已处理 | 后续新增半天假 UI 或审批类型时，仍应落到 `startAt/endAt` 区间模型，不应在考勤侧新增特殊枚举分支。 |
| 2-3 | `AttendanceService.calculateApprovedLeaveCoveredMinutes()`、`ModuleLeaveWorkflowIntegrationTest.overlappingApprovedHourlyLeavesShouldNotDoubleOffsetAbsenceMinutes()` | 多段小时假如果直接求和，会把 `09:00-10:00` 和 `09:30-10:30` 重叠部分重复抵扣。本轮先排序合并区间再计算覆盖分钟，测试验证合并覆盖 90 分钟，缺勤为 450 分钟。 | 已处理 | 后续如果支持销假或部分撤销，需要在 leave SPI 层返回净有效区间，考勤侧继续只负责合并和求交。 |
| 2-4 | `AttendanceService.calculateLateMinutes()`、`AttendanceService.calculateEarlyLeaveMinutes()`、`ModuleLeaveWorkflowIntegrationTest.approvedHourlyLeaveShouldOffsetLateAndEarlyWindows()` | 初版修复只抵扣缺卡缺勤，未抵扣迟到/早退窗口。当前已把迟到和早退计算也接入同一套请假区间覆盖逻辑，测试验证 `09:00-10:00`、`17:00-18:00` 请假且 `10:00/17:00` 打卡时结果为 `NORMAL`。 | 已处理 | 后续迟到宽限、弹性班次和分段班次加入后，应继续以“异常窗口与请假区间求交”为核心模型。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceService.calculateAbsenceMinutes()` | 当前缺勤模型仍是第一版简化：无上下班卡按整段班次缺勤，缺一张卡按半段班次缺勤。请假分钟级覆盖已经接入这些窗口，但如果未来支持多班段、午休扣除、弹性上下班或外出时段，这个“半段缺勤”模型会不够精确。 | P2 | 在补卡和月报前拆出 `AttendanceCalculationService`，把班段、午休、打卡、请假、补卡统一建模为时间窗口集合，再统一求差集和异常。 |
| 3-2 | `AttendanceService`、跨天班次 | 当前文档已明确第一版不支持跨天班次，请假覆盖计算也基于同一 `workDate` 的 `LocalTime start/end`。如果后续开放夜班或跨天排班，而继续复用当前逻辑，会把跨日请假与打卡窗口截断在单日内。 | P2 | 开放跨天班次前必须把班次窗口升级为 `LocalDateTime` 范围，并补跨天请假、跨天打卡、跨天日结果归属测试。 |
| 3-3 | `LeaveRepository.listApprovedLeavePeriodsOnDate()`、`WorkflowService.validateLeaveRequest()` | leave 查询能返回分钟级区间，但请假时长本身仍按固定午休 `12:00-13:00` 扣除，尚未结合工作日历、节假日、休息日和实际班次。真实年假或调休场景下，余额扣减分钟和考勤覆盖分钟可能出现口径差异。 | P2 | 后续假期策略应引入日历和排班口径：余额扣减、考勤覆盖、展示时长使用同一套服务端计算规则。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 本轮为修正请假分钟级覆盖继续在 `AttendanceService` 内增加计算逻辑。该类已经同时处理配置、排班、打卡、日结果、异常、请假联动和时间窗口计算，后续再加入补卡、月报和日历会继续膨胀。 | P2 | 下一个考勤功能节点前优先拆 `AttendanceCalculationService`，让重算只编排数据读取和落库，时间窗口计算集中在可单测的纯业务服务中。 |
| 4-2 | `ModuleLeaveWorkflowIntegrationTest` | 请假、workflow、attendance 的跨模块测试集中在同一个集成测试类中，覆盖价值高，但测试类职责开始变宽。随着冻结余额、补卡、销假加入，单类会变成跨域大杂烩。 | P3 | 后续可拆出 `ModuleLeaveAttendanceIntegrationTest` 专门覆盖请假与考勤联动，`ModuleLeaveWorkflowIntegrationTest` 保持余额和流程状态关注点。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证：小时假误算整日 `LEAVE` | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedHourlyLeaveShouldOnlyOffsetCoveredAbsenceMinutes test` | 失败符合预期 | 修改前 `09:00-10:00` 小时假被整日豁免，实际返回 `LEAVE`。 |
| 红灯验证：重叠小时假重复抵扣 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#overlappingApprovedHourlyLeavesShouldNotDoubleOffsetAbsenceMinutes test` | 失败符合预期 | 合并前重叠小时假被按 120 分钟抵扣，缺勤为 420。 |
| 红灯验证：小时假未抵扣迟到早退窗口 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedHourlyLeaveShouldOffsetLateAndEarlyWindows test` | 失败符合预期 | 初版只抵扣缺勤，最终仍返回 `lateMinutes=60`、`earlyLeaveMinutes=60`。 |
| 针对性绿灯 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedHourlyLeaveShouldOffsetLateAndEarlyWindows test` | 通过 | 迟到、早退、缺勤窗口均接入请假区间抵扣后通过。 |
| leave workflow 回归 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest test` | 通过 | 输出为 `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`。 |
| leave + attendance 组合回归 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 19, Failures: 0, Errors: 0, Skipped: 0`。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 环境下编译成功。 |
| 空白检查 | `git diff --check` | 通过 | 当前工作区差异无空白错误。 |

未验证项：

- 尚未验证跨天班次、节假日、休息日和多班段场景。
- 尚未执行全仓全部集成测试组合。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 第 42 审 `3-3` 假期余额调整幂等键 | 当前 `3-2` 已收口，剩余最高优先级是 leave 账本幂等键未绑定操作指纹。该问题会导致复用同一幂等键时静默回放错误账户结果，属于账本错账风险。 | P1 | 下一节点优先补 `sourceTxnKey` 操作指纹校验，不一致请求返回 `409`，并补数据库或流水字段支撑。 |
| 6-2 | 第 42 审 `3-4` 排班状态与唯一键语义 | 排班 `INACTIVE` 与 `uk_user_date` 唯一键语义仍冲突，停用后创建同日新排班会撞库。 | P1 | 明确排班模型是原地修改还是停用后重建，再修服务语义和迁移。 |
| 6-3 | 考勤计算服务拆分 | 请假区间、打卡、排班、异常计算继续集中在主服务内，后续补卡会进一步扩大复杂度。 | P2 | 在补卡闭环前拆计算服务，并用单元测试覆盖时间窗口组合。 |

---

## 第七部分：阶段性结论

第 42 审 `3-2` 已实质修复：请假影响考勤不再按日期布尔整日豁免，而是基于已批准请假区间与班次、迟到、早退、缺勤窗口求交集；整段覆盖才写 `LEAVE`，部分覆盖只按分钟抵扣。新增测试覆盖了原问题、重叠区间和迟到早退窗口三个关键路径。

当前可以进入第 42 审下一个 P1 修复节点，但不建议直接扩展月度考勤、薪资扣款或复杂补卡统计，因为 `3-3` 账本幂等指纹、`3-4` 排班唯一模型、以及考勤计算服务拆分仍是后续稳定性的关键前置。
