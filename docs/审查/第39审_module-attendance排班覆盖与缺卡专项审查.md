# 第 39 审 module-attendance 排班覆盖与缺卡专项审查

- 审查日期：2026-05-07
- 审查提交：
  - `60a0eec..未提交工作区`（module-attendance 第二批：排班覆盖默认班次、重复排班冲突、缺卡专项保护）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/`、`server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 38 审提出的排班覆盖和缺卡测试缺口是否收口，并检查新增排班接口的数据一致性、API 契约、测试保护和后续扩展风险；不把批量排班、排班修改、节假日、请假影响考勤和补卡联动纳入本轮已完成范围。

---

## 第一部分：提交内容概览

本轮新增后台排班接口 `POST /api/admin/attendance/schedules`，新增 `CreateAttendanceScheduleRequest`、`AttendanceScheduleResponse`、`AttendanceScheduleRow`，并在 `AttendanceRepository` 中补充排班写入、排班查询和日结果计算所需的读取方法。

`AttendanceService.recalculateDailyResult()` 已从“只记录 `scheduleId` 但仍使用考勤组默认班次”调整为“当天存在有效排班时使用排班 `shift_id`，否则回退考勤组默认班次”。测试侧新增排班覆盖默认班次、重复排班冲突、缺卡异常专项三个用例，`ModuleAttendanceApiIntegrationTest` 从 5 个用例扩展到 8 个用例。

文档侧同步更新 `module-attendance` API 文档、API 测试用例、开发日志和后续开发计划，把排班覆盖和缺卡专项从待办调整为已覆盖，同时保留补卡、请假影响考勤、节假日、异常治理等未完成项。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.recalculateDailyResult()`、`AttendanceRepository.getActiveSchedule()`、`ModuleAttendanceApiIntegrationTest.scheduleShouldOverrideDefaultGroupShiftWhenCalculatingDailyResult()` | 第 38 审指出日结果只读取 `att_schedule.id`，但没有用排班班次覆盖考勤组默认班次，员工临时班次会被默认班次误判迟到或早退。当前已改为先查当天有效排班，命中时使用排班 `shift_id` 计算；测试覆盖默认班次 `09:00-18:00`、排班班次 `10:00-19:00` 的场景，员工按排班班次打卡后结果为 `NORMAL`。 | 已处理 | 后续扩展排班修改或停用时，需要同步触发对应日期日结果重算，不能只改排班表。 |
| 2-2 | `AttendanceService.rebuildExceptions()`、`ModuleAttendanceApiIntegrationTest.missingOffDutyPunchShouldCreateMissPunchException()` | 第 38 审指出缺卡逻辑已有实现但没有专项测试保护，后续调整日结果计算时可能漏算 `MISS_PUNCH`。当前补充只有上班卡的缺卡用例，验证日结果 `ABNORMAL`、`absenceMinutes=270`，异常列表返回 1 条 `MISS_PUNCH`。 | 已处理 | 后续仍需补“只有下班卡”和“无任何打卡但由日结任务触发”的缺卡路径。 |
| 2-3 | `AttendanceService.createSchedule()`、`ModuleAttendanceApiIntegrationTest.duplicateActiveScheduleShouldBeRejected()` | 新增排班接口如果不固定重复排班语义，调用方可能在重复点击或重试时拿到不稳定错误。当前创建排班前会锁员工主档并检查同日 `ACTIVE` 排班，重复创建返回 `code=409`、`message=员工当天已存在有效排班`，并有集成测试覆盖。 | 已处理 | 如果后续支持修改排班，应使用明确的 `PUT` 接口或版本号，避免用重复 `POST` 承担修改语义。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceService.createSchedule()`、`att_schedule`、`att_daily_result` | 当前只支持创建排班，不支持修改、停用或删除排班，也没有“排班变更后重算已有日结果”的入口。若管理员排错班次且员工已经打卡，当前只能新增失败，不能修正原排班；如果后续直接手工改库或新增停用接口但不重算日结果，会导致 `att_daily_result.shift_id`、异常和排班表不一致。 | P1 | 下一步做排班修改/停用时，必须把“变更排班”和“重算该员工该日期日结果”放在同一业务流程内，并补充已有打卡后改排班的集成测试。 |
| 3-2 | `AttendanceService.calculateAbsenceMinutes()`、`ModuleAttendanceApiIntegrationTest.missingOffDutyPunchShouldCreateMissPunchException()` | 缺卡缺勤分钟当前按班次总时长的一半粗算。这个规则可用于第一版保护 `MISS_PUNCH`，但对多班段、午休、半天班、跨天班和弹性班不准确，后续一旦接入复杂班段会制造错误缺勤分钟。 | P2 | 后续应把缺勤分钟计算从固定二分之一改为基于班段和应打卡次数计算，并把只有上班卡、只有下班卡、多班段缺卡拆成独立计算测试。 |
| 3-3 | `AttendanceRepository.getActiveSchedule()`、`att_schedule.uk_user_date` | 数据库已有 `uk_user_date(user_id, work_date)`，因此当前软停用后也不能再为同一员工同一天新增另一条排班记录。若后续要支持排班变更历史或恢复历史记录，当前唯一键会阻断“保留旧记录再新增新记录”的模型。 | P2 | 如果要保留排班变更历史，应新增版本号或把唯一约束调整为能表达“同一天只有一个当前有效版本”的模型；如果只允许原地修改，也要在文档中明确不保留排班历史。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 本轮继续向同一个 Service 增加排班创建和计算分支，使该类同时承担配置、排班、归属、打卡、计算、异常、查询和 SPI。短期可控，但继续加入补卡、请假影响考勤和异常治理后会变成难以审查的核心大类。 | P2 | 进入补卡或请假影响考勤前，建议先抽出 `AttendanceScheduleService` 和 `AttendanceCalculationService`，至少把排班写入和日结果计算职责拆开。 |
| 4-2 | `AttendanceRepository` | Repository 已集中承载多个聚合 SQL，排班读写继续加在同一个类中会增加变更冲突，也会让权限、状态、软删除过滤条件分散在大文件内。 | P2 | 后续按配置、排班、打卡、结果/异常四类 Repository 拆分，避免补卡和异常治理继续堆叠到单文件。 |

## 第五部分：验证与未验证项

本轮已执行验证：

- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#scheduleShouldOverrideDefaultGroupShiftWhenCalculatingDailyResult test`：初始红灯为 `POST /api/admin/attendance/schedules` 返回 `404`。
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#scheduleShouldOverrideDefaultGroupShiftWhenCalculatingDailyResult test`：实现排班接口和排班优先计算后通过。
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#missingOffDutyPunchShouldCreateMissPunchException test`：通过。
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#duplicateActiveScheduleShouldBeRejected test`：通过。
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest test`：8 个用例，0 失败，0 错误。
- `mvn -DskipTests compile`：通过。
- `git diff --check`：通过。

当前仍未验证：

- 只有下班卡的缺卡路径。
- 无任何打卡记录时由日结任务生成缺勤。
- 排班创建后已有打卡和日结果的重算路径。
- 排班修改、停用、批量排班、排班变更历史。
- 请假通过后考勤日结果识别为请假而非缺勤。
- 补卡申请审批通过后修正打卡记录和异常。
- 多实例部署下考勤组成员归属并发写入。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-attendance` 排班能力 | 排班创建已完成，但缺少修改、停用和重算入口，真实后台操作中排错班是高频场景，不能长期只允许创建。 | P1 | 下一批优先实现排班修改/停用，并在已有打卡后触发日结果重算。 |
| 6-2 | `module-attendance` 与 `module-leave` | 请假通过后考勤仍会按缺卡或缺勤处理，直接影响员工考勤结果可信度。 | P1 | 接入已通过请假记录查询或审批结果表，在日结果计算时优先识别请假覆盖时段。 |
| 6-3 | `module-attendance` 补卡闭环 | 缺卡异常已能识别，但员工还不能通过补卡申请修正异常，异常治理链路仍不闭环。 | P1 | 通过 `module-workflow` 新增补卡申请类型，审批通过后写入修正打卡或异常解决记录，并重算日结果。 |

## 第七部分：阶段性结论

本轮没有发现阻断当前单实例单企业后端继续推进的 P0 问题。第 38 审中的排班覆盖默认班次和缺卡专项测试已经实质收口，排班接口第一版具备创建、重复冲突保护和日结果计算优先使用排班班次的能力。

阶段性结论是：可以进入下一节点，但下一节点不应继续只补查询接口，应优先做“排班变更触发重算”或“请假影响考勤/补卡审批联动”这类会影响考勤结果可信度的闭环能力。
