# 第 40 审 module-attendance 排班变更重算审查

- 审查日期：2026-05-07
- 审查提交：
  - `66063b9..未提交工作区`（module-attendance 第三批：排班修改接口、已有日结果重算、API 与测试文档同步）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/`、`server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 39 审提出的“排班变更后不重算已有日结果”风险是否收口，并检查排班修改接口的数据一致性、事务边界、API 契约、测试保护和后续扩展风险；不把批量排班、排班历史、补卡审批联动、请假影响考勤、节假日明细纳入本轮已完成范围。

---

## 第一部分：提交内容概览

本轮新增后台排班修改接口 `PUT /api/admin/attendance/schedules/{scheduleId}`，新增 `UpdateAttendanceScheduleRequest`，并在 `AdminAttendanceController`、`AttendanceService`、`AttendanceRepository` 中补齐排班行锁读取、排班更新和已有日结果重算入口。

`AttendanceService.updateSchedule()` 会先锁定原排班记录，再锁定员工主档，校验新班次和可选工作日历的启用状态，更新排班后如果该员工该日期已有 `att_daily_result`，则在同一事务内调用 `recalculateDailyResult()`。由于 `recalculateDailyResult()` 已按最新有效排班计算，排班变更会同步刷新 `shift_id`、迟到早退缺卡分钟、结果状态和异常记录。

测试侧新增 `ModuleAttendanceApiIntegrationTest.updatingScheduleShouldRecalculateExistingDailyResult()`，覆盖员工先按 `10:00-19:00` 排班正常打卡，再把同一天排班改为 `09:00-18:00` 后，已有日结果变成迟到异常的场景。文档侧同步更新 API 文档、API 测试用例、开发日志和后续开发计划。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.updateSchedule()`、`AttendanceRepository.getScheduleForUpdateOrThrow()`、`AttendanceRepository.updateSchedule()`、`ModuleAttendanceApiIntegrationTest.updatingScheduleShouldRecalculateExistingDailyResult()` | 第 39 审 3-1 指出，排班创建完成后缺少修改入口，管理员排错班次且员工已经打卡时，后续如果只改排班表不重算日结果，会造成 `att_schedule`、`att_daily_result` 和 `att_exception` 不一致。当前已新增 `PUT /api/admin/attendance/schedules/{scheduleId}`，排班修改后检测已有日结果并在同一事务内重算；测试验证排班从 `10:00-19:00` 改为 `09:00-18:00` 后，日结果从正常变为 `ABNORMAL`，`lateMinutes=60`。 | 已处理 | 后续如果加入批量排班或停用排班，也必须复用同一重算边界，不能绕过 `AttendanceService.updateSchedule()` 直接写表。 |
| 2-2 | `docs/API与测试/module-attendance/API文档_module-attendance考勤接口.md`、`docs/API与测试/module-attendance/API测试用例_module-attendance考勤接口.md`、`docs/开发日志/module-attendance.md` | 排班修改是新 API 契约，如果只加代码不更新文档，前端和测试会不知道排班变更触发重算的语义。当前 API 文档已新增 `PUT` 接口、请求体、响应体和重算规则；测试用例新增 `ATT-API-018`；开发日志记录红绿过程和阶段边界。 | 已处理 | 后续排班历史、批量排班、模板排班应继续保持代码、测试、API 文档同步更新。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceService.updateSchedule()`、`att_schedule.status`、`AttendanceRepository.getActiveSchedule()` | 当前允许把排班状态改为 `INACTIVE`，并且重算时只读取有效排班。这样会使已有日结果回退到考勤组默认班次。这个行为在代码上自洽，但产品语义需要明确：停用排班到底表示“撤销临时排班，按默认班次算”，还是“该日不应出勤”。如果前端或运营把停用理解为“休息日/不考勤”，当前结果会误算为默认班次异常。 | P1 | 在进入前端和管理员治理前明确排班状态语义。若 `INACTIVE` 表示撤销排班，则文档保持当前说明；若表示该日不考勤，需要新增工作日历日明细或日结果类型，不能复用当前停用语义。 |
| 3-2 | `att_schedule.uk_user_date`、`AttendanceRepository.updateSchedule()` | 本轮采用原地修改排班模型，没有保留排班变更历史。短期能避免唯一键冲突并收口重算问题，但真实 HR 场景中排班调整通常需要追溯谁在什么时候改了什么。当前只更新 `operator_id`、`updated_by`，不能还原历史班次。 | P2 | 后续增加 `att_schedule_change_log` 或版本化排班模型，记录旧班次、新班次、旧状态、新状态、操作人和原因；如果继续原地修改，需要在后台审计中明确“不保留历史版本”。 |
| 3-3 | `AttendanceService.updateSchedule()`、`AttendanceService.punch()` | 排班修改和打卡都在各自事务内重算日结果，当前排班修改先锁排班再锁员工，打卡先写打卡记录后在重算过程中锁考勤组和日结果。单实例低并发下可运行，但在真实并发下，管理员改班和员工同时打卡仍可能出现最后一次重算覆盖前一次重算的时序差异。 | P2 | 后续可在重算入口按 `userId + workDate` 建立统一数据库锁或轻量锁表，使打卡、排班修改、补卡、请假变更都串行化同一天同一员工的日结果计算。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 排班创建、排班修改、打卡、日结果计算、异常重建、考勤组成员并发保护仍集中在一个 Service。当前第三批继续增加写侧和计算侧职责，后续再加入补卡、请假影响考勤、异常治理后会明显超过可审查边界。 | P2 | 在进入补卡或请假影响考勤前，优先抽出 `AttendanceScheduleService` 和 `AttendanceCalculationService`，让排班写入、打卡写入、日结果计算分别有清晰入口。 |
| 4-2 | `AttendanceRepository` | Repository 已承载配置、成员、排班、打卡、结果、异常多类 SQL。本轮又新增排班锁定和更新 SQL，继续扩张会导致过滤条件和锁策略分散，后续维护者难以判断哪些查询需要 `FOR UPDATE`。 | P2 | 按 `AttendanceScheduleRepository`、`AttendancePunchRepository`、`AttendanceResultRepository` 拆分 SQL，并把需要锁的写侧方法集中到对应聚合内。 |

## 第五部分：验证与未验证项

本轮已执行验证：

- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#updatingScheduleShouldRecalculateExistingDailyResult test`：初始红灯为 `PUT /api/admin/attendance/schedules/{scheduleId}` 返回 `404`。
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#updatingScheduleShouldRecalculateExistingDailyResult test`：实现排班修改、行锁和重算后通过。

提交前仍需执行：

- `git diff --check`
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest test`
- `mvn -DskipTests compile`

当前仍未验证：

- 把排班改为 `INACTIVE` 后，已有日结果回退默认班次的业务语义是否符合最终产品预期。
- 只有下班卡的缺卡路径。
- 无任何打卡记录时由日结任务生成缺勤。
- 排班批量修改、排班复制、模板排班和排班历史。
- 请假通过后考勤日结果识别为请假而非缺勤。
- 补卡申请审批通过后修正打卡记录和异常。
- 多实例部署下同一员工同一天重算入口的串行化。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-attendance` 与 `module-leave` | 请假审批已经能扣减假期账户，但考勤日结果暂不识别已批准请假。员工请假通过后，如果没有打卡，考勤仍可能显示缺卡或异常，影响可信度。 | P1 | 下一批优先做“已批准请假影响考勤日结果”，在日结果计算中查询请假覆盖并生成明确的请假态或豁免异常。 |
| 6-2 | `module-attendance` 补卡闭环 | 缺卡异常已经能识别，但员工不能提交补卡申请修正异常，审批通过后也没有重算入口。 | P1 | 接入 `module-workflow` 新增补卡申请类型，审批通过后写入补卡记录或修正打卡记录，并统一触发日结果重算。 |
| 6-3 | `module-attendance` 计算边界 | 打卡、排班修改、未来补卡和请假都会触发日结果计算，如果没有统一串行化边界，后续并发和重试下会出现难以复现的结果覆盖。 | P2 | 在计算服务拆分时设计 `userId + workDate` 级别重算锁，所有会影响日结果的业务动作统一进入该入口。 |

## 第七部分：阶段性结论

本轮没有发现阻断当前单实例单企业后端继续推进的 P0 问题。第 39 审中的“排班变更后不重算已有日结果”P1 风险已经通过排班修改接口、事务内重算和集成测试阶段性收口。

阶段性结论是：可以进入下一节点。下一节点建议优先做“请假通过后影响考勤日结果”或“补卡申请审批闭环”，因为这两个能力会直接决定考勤结果是否可信；同时在进入更复杂的考勤联动前，应准备拆分 `AttendanceService` 和统一日结果重算入口。
