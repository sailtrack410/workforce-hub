# 开发日志 module-attendance

## 2026-05-07 考勤基础配置、员工打卡与日结果最小闭环

### 背景

`module-leave` 已完成假期账户与审批扣减闭环，后续年假规则、请假扣减和异常处理都依赖考勤侧的日历、班次、考勤组和打卡结果。按照当前路线，本轮进入 `module-attendance`，先实现第一版可测试闭环：后台能配置基础规则，员工能打卡，系统能生成日结果和异常，直属上级能查询团队日结果。

本轮暂不做排班接口、补卡审批联动、请假影响考勤、外勤定位、人脸和 IP 规则校验。

### 本次处理

- 新增 `module-attendance` 后端包，包含 Controller、Service、Repository、DTO 和 Row。
- 新增后台接口：
  - `POST /api/admin/attendance/calendars`
  - `POST /api/admin/attendance/shifts`
  - `POST /api/admin/attendance/groups`
  - `POST /api/admin/attendance/groups/{groupId}/members`
- 新增员工端接口：
  - `POST /api/employee/attendance/punch`
  - `GET /api/employee/attendance/daily-results`
  - `GET /api/employee/attendance/exceptions`
  - `GET /api/employee/attendance/team/daily-results`
- `AttendanceService` 实现 `AttendanceQueryService`，为后续 Agent 查询考勤结果和异常预留 SPI。
- 管理员创建工作日历时校验编码唯一、类型和状态合法。
- 管理员创建班次时写入 `att_shift` 和默认第一段 `att_shift_segment`，第一版不支持跨天班次。
- 管理员创建考勤组时校验工作日历和默认班次存在且启用。
- 添加考勤组成员时校验员工可用，并拦截同一员工 `ACTIVE` 生效区间重叠。
- 添加考勤组成员时按员工维度使用单实例应用锁，锁内开启事务并复查重叠区间，避免并发请求同时写入重叠归属。
- 员工打卡时固定取当前员工会话，不接受客户端传入 `userId`。
- 员工打卡前重新校验数据库中的员工主档状态和账号状态，防止离职后旧 Session 继续写入考勤数据。
- 打卡后按当天最早上班打卡和最晚下班打卡重算 `att_daily_result`。
- 日结果重算时先删除旧 `att_exception`，再按迟到、早退、缺卡重新生成异常，避免重复打卡导致异常重复累计。
- 员工日结果和异常查询固定查询当前登录员工。
- 团队日结果查询第一版只允许直属上级查询直属下级。
- `LocalIntegrationTestSupport` 补充 `att_` 表清理，避免考勤数据污染其他集成测试。
- 新增 `docs/API与测试/module-attendance/`，记录 API 文档和测试用例。

### 红绿记录

- 红灯：新增 `ModuleAttendanceApiIntegrationTest` 后，`POST /api/admin/attendance/calendars` 返回 `404`。
- 绿灯：补齐后台日历、班次、考勤组、成员接口和员工打卡、日结果、异常查询后，`mvn -Dtest=ModuleAttendanceApiIntegrationTest test` 通过。
- 红灯：新增离职旧会话用例后，员工被流转到 `OFFBOARDED` 后仍能用旧 Session 打下班卡，`$.code` 实际返回 `0`。
- 绿灯：在打卡写入前增加实时员工状态和账号状态校验后，`mvn -Dtest=ModuleAttendanceApiIntegrationTest#offboardedEmployeeShouldNotPunchWithExistingSession test` 通过。

### 当前完成范围

- 管理员可以创建工作日历。
- 管理员可以创建班次与第一段班段。
- 管理员可以创建考勤组。
- 管理员可以维护考勤组成员。
- 同一员工考勤组生效区间重叠会被拦截，并已覆盖同一单实例内并发写入场景。
- 员工可以打上班卡、下班卡。
- 员工离职、账号停用或主档停用后不能继续打卡。
- 正常上下班会生成正常日结果。
- 迟到、早退会生成异常日结果和待处理异常。
- 员工可以查询自己的日结果和异常。
- 直属上级可以查询直属下级日结果。
- `AttendanceQueryService` 已具备 Agent 查询考勤数据入口。

### 暂未覆盖

- 排班接口和特殊班次覆盖。
- 工作日历日明细、节假日和休息日。
- 缺卡异常专门测试和独立计算测试类。
- 请假通过后考勤日结果识别为请假而非缺勤。
- 补卡申请审批通过后修正打卡记录和异常。
- 管理员异常治理列表、忽略异常和处理流水。
- 外勤、定位、IP、人脸设备校验。
- 多实例部署下考勤组成员区间重叠写入的分布式锁或数据库锁表治理。

### 意义

- 考勤模块从空目录进入可运行闭环，为后续补卡、请假影响考勤、考勤统计和 Agent 异常提醒提供数据基础。
- 考勤归属通过生效区间建模，能承接后续调岗、调部门和分公司组织变化。
- `AttendanceQueryService` 让 Agent 查询考勤不需要直接依赖考勤内部 Repository，模块边界更清晰。

## 2026-05-07 排班覆盖默认班次与缺卡专项保护

### 背景

第 38 审指出，第一版日结果虽然读取了 `att_schedule.id`，但还没有用排班班次覆盖考勤组默认班次；同时缺卡逻辑已有实现但缺少专项测试保护。这两个点会直接影响考勤结果可信度，因此作为 `module-attendance` 第二批的最小可测节点优先处理。

### 本次处理

- 新增后台排班接口：`POST /api/admin/attendance/schedules`。
- 新增 `CreateAttendanceScheduleRequest`、`AttendanceScheduleResponse` 和 `AttendanceScheduleRow`。
- 创建排班时校验员工可用、班次启用、可选工作日历启用。
- 同一员工同一天已有 `ACTIVE` 排班时返回 `code=409`，避免同日多个有效排班。
- 补充重复创建有效排班测试，固定同一员工同一天重复排班的 `409` 语义。
- 日结果计算改为优先读取当天有效排班；命中排班时使用排班 `shift_id`，未命中时回退考勤组默认班次。
- 补充排班覆盖默认班次集成测试，验证 `10:00-19:00` 排班不会被 `09:00-18:00` 默认班次误判为迟到和早退。
- 补充缺卡专项测试，验证只有上班卡时生成 `MISS_PUNCH` 异常。
- 更新 `module-attendance` API 文档和测试用例。

### 红绿记录

- 红灯：新增 `scheduleShouldOverrideDefaultGroupShiftWhenCalculatingDailyResult` 后，`POST /api/admin/attendance/schedules` 返回 `404`。
- 绿灯：补齐排班接口、排班写入和日结果排班优先逻辑后，该用例通过。
- 绿灯：新增 `duplicateActiveScheduleShouldBeRejected` 后，事务内员工行锁和重复排班检查已满足预期，用例通过。
- 绿灯：新增 `missingOffDutyPunchShouldCreateMissPunchException` 后，现有缺卡逻辑已满足预期，用例通过并形成回归保护。

### 当前完成范围增量

- 管理员可以为员工创建单日排班。
- 日结果计算已支持“排班班次优先，考勤组默认班次兜底”。
- 只有上班卡会生成缺卡异常和 `MISS_PUNCH` 异常记录。

### 暂未覆盖

- 排班修改、停用、批量排班和排班变更历史。
- 工作日历日明细、节假日和休息日。
- 请假通过后考勤日结果识别为请假而非缺勤。
- 补卡申请审批通过后修正打卡记录和异常。
- 管理员异常治理列表、忽略异常和处理流水。
- 外勤、定位、IP、人脸设备校验。
- 多实例部署下考勤组成员区间重叠写入的分布式锁或数据库锁表治理。

## 2026-05-07 排班变更触发日结果重算

### 背景

第 39 审指出，排班第一版已经支持创建和排班班次覆盖默认班次，但缺少排班修改入口。真实后台操作中“排错班次后员工已经打卡”是高频场景，如果只改 `att_schedule` 而不重算 `att_daily_result`，会造成排班表、日结果和异常记录互相不一致。因此本轮优先补齐单日排班修改和已有日结果重算。

### 本次处理

- 新增后台排班修改接口：`PUT /api/admin/attendance/schedules/{scheduleId}`。
- 新增 `UpdateAttendanceScheduleRequest`。
- 排班修改时先 `FOR UPDATE` 锁定原排班记录，再锁定员工主档，避免排班变更和打卡计算交叉写入。
- 排班修改时校验新班次存在且启用，可选工作日历存在且启用。
- 排班来源继续限制为 `MANUAL`、`AUTO`、`TEMPLATE`。
- 排班状态继续限制为 `ACTIVE`、`INACTIVE`。
- 修改排班后，如果该员工该日期已有日结果，则在同一事务内调用日结果重算。
- 日结果重算会按最新有效排班计算；如果排班被改为 `INACTIVE`，则回退到考勤组默认班次。
- 补充 `updatingScheduleShouldRecalculateExistingDailyResult` 集成测试，验证员工先按 `10:00-19:00` 排班正常打卡，再把排班改成 `09:00-18:00` 后，日结果变为迟到异常。
- 更新 `module-attendance` API 文档和测试用例。

### 红绿记录

- 红灯：新增 `updatingScheduleShouldRecalculateExistingDailyResult` 后，`PUT /api/admin/attendance/schedules/{scheduleId}` 返回 `404`。
- 绿灯：补齐排班修改接口、排班行锁、更新 SQL 和已有日结果重算后，该用例通过。

### 当前完成范围增量

- 管理员可以修改已有单日排班的班次、工作日历、来源和状态。
- 已有打卡和日结果的日期，在排班修改后会同步重算日结果和异常。
- 第 39 审中“排班变更后不重算已有日结果”的 P1 风险已阶段性收口。

### 暂未覆盖

- 排班变更历史、版本号和审计流水。
- 批量排班、排班复制和模板排班。
- 排班停用后的产品语义还需要在前端和业务说明中明确：当前实现会使日结果回退到考勤组默认班次。
- 工作日历日明细、节假日和休息日。
- 补卡申请审批通过后修正打卡记录和异常。
- 管理员异常治理列表、忽略异常和处理流水。
- 外勤、定位、IP、人脸设备校验。
- 多实例部署下考勤组成员区间重叠写入的分布式锁或数据库锁表治理。

## 2026-05-07 请假审批通过后联动考勤日结果重算

### 背景

第 40 审把“请假通过后影响考勤日结果”列为下一批 P1。当前 `module-leave` 已经能在 workflow 审批通过后扣减假期余额，但考勤侧如果没有联动，员工请假获批后仍可能因为没有打卡而显示缺卡或异常。这会让假期、审批和考勤三套数据互相冲突，因此本轮优先补齐审批通过后的日结果重算。

### 本次处理

- 新增 `AttendanceRecalculationService` SPI，由 `AttendanceService` 实现。
- `WorkflowService` 在直属上级审批通过请假后，按请假开始日期到结束日期逐日调用考勤重算。
- `LeaveQueryService` 新增 `hasApprovedLeaveOnDate(userId, workDate)`，由 `LeaveService` 通过 `wf_request` 与 `wf_req_leave` 查询已批准请假覆盖。
- `AttendanceService` 日结果计算时优先识别已批准请假，命中后写入 `resultStatus=LEAVE`。
- 命中请假覆盖时，迟到、早退、缺勤、工时均归零，`exceptionFlag=false`，并清理该日已有 `att_exception`。
- `WorkflowFlowableIntegrationTestSupport` 补充 `att_` 表清理，避免 workflow 专用测试污染考勤数据。
- `LocalIntegrationTestSupport` 补充 workflow 表清理，因为普通考勤测试已经会读取已批准 workflow 请假状态。
- 在 `ModuleLeaveWorkflowIntegrationTest` 中新增审批通过联动考勤重算集成测试。
- 更新 `module-attendance` 与 `module-leave` API 文档、测试用例、开发日志和后续开发计划。

### 红绿记录

- 红灯：新增 `approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult` 后，请假审批通过后查询 `GET /api/employee/attendance/daily-results` 返回空结果，说明审批通过没有触发考勤重算。
- 绿灯：补齐 `AttendanceRecalculationService`、`LeaveQueryService.hasApprovedLeaveOnDate` 和 workflow 审批通过后逐日重算后，该用例通过。
- 回归绿灯：`mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test` 通过，确认请假联动没有破坏考勤既有接口测试。

### 当前完成范围增量

- 请假审批通过后会触发覆盖日期的考勤日结果重算。
- 已批准请假覆盖的日期会生成或更新为 `LEAVE` 日结果。
- 已批准请假覆盖日期不再保留缺卡、迟到、早退异常。
- workflow 与 attendance 通过 `AttendanceRecalculationService` 解耦，workflow 不直接依赖考勤内部 Repository。
- attendance 与 leave 通过 `LeaveQueryService` 查询已批准请假，考勤计算不直接读取 workflow 内部服务。

### 暂未覆盖

- 半天假、小时假与实际打卡结果的分钟级合并。
- 多天请假中的节假日、休息日过滤。
- 补卡申请审批通过后修正打卡记录和异常。
- 同一员工同一天来自打卡、排班、请假、补卡的统一重算锁。
- 日结果重算服务、排班服务、打卡服务的进一步拆分。

### 意义

- 请假、假期余额、审批和考勤从“各自闭环”进入第一版跨模块一致性闭环。
- `LEAVE` 日结果让员工端、领导端和后续 Agent 可以直接识别已批准请假日，不再把获批请假误判为缺卡异常。
- 外部事件触发考勤重算的 SPI 已建立，后续补卡审批、排班批量调整、日历变更可以复用同一扩展方向。

## 2026-05-07 第42审3-1打卡事实可信边界修复

### 背景

第 42 审指出，员工实时打卡接口信任客户端 `punchAt` 和 `sourceType`。这会让员工通过接口伪造历史或未来打卡事实，也可能把员工端来源伪造成 `MANUAL`。由于考勤结果后续会进入月报、异常处理、审批和可能的薪资扣款，这个边界必须先收口。

### 本次处理

- 新增 `TimeConfig`，统一提供生产环境 `Clock` Bean。
- `AttendanceService.punch()` 改为使用 `LocalDateTime.now(clock)` 作为实时打卡落库时间，不再使用客户端 `punchAt` 作为可信事实。
- `CreatePunchRecordRequest.punchAt` 改为兼容字段，不再必填；员工端可以只提交打卡类型和来源。
- 员工实时打卡来源收口为 `APP`、`PC`，拒绝 `MANUAL` 等后台、审批或系统任务专用来源。
- 补充 `MutableTestClock` 和 `FixedClockTestConfiguration`，让考勤集成测试可以稳定模拟服务端接收时间。
- 补充三个回归测试：客户端历史 `punchAt` 不生效、缺省客户端 `punchAt` 仍可打卡、员工端伪造 `MANUAL` 被拒绝。
- 更新 `module-attendance` API 文档和测试用例，明确实时打卡以服务端时间为准。

### 红绿记录

- 红灯：新增 `employeePunchShouldAllowMissingClientPunchAt` 后，请求不传 `punchAt` 返回 `400`，错误为 `打卡时间不能为空`。
- 绿灯：移除 `punchAt` 必填校验后，`employeePunchShouldAllowMissingClientPunchAt`、`employeePunchShouldUseServerReceivedTimeInsteadOfClientPunchAt`、`employeePunchShouldRejectControlledManualSourceType` 通过。
- 回归绿灯：`mvn -Dtest=ModuleAttendanceApiIntegrationTest test` 通过，确认打卡可信边界改动没有破坏考勤既有接口测试。

### 当前完成范围增量

- 第 42 审 `3-1` 员工打卡事实可信边界已收口。
- 员工实时打卡事实时间由服务端产生，客户端时间不能直接改写考勤日。
- 员工实时打卡来源具备员工端白名单，受控来源不再由员工端直接写入。

### 暂未覆盖

- 补卡审批入口尚未实现，历史打卡修正仍需后续通过审批闭环落地。
- 外勤定位、IP 白名单、设备签名、人脸核验尚未实现，当前只收口时间和来源可信边界。
- 服务端时钟依赖运行环境时间，生产部署需要 NTP 或容器宿主机时间同步保障。

### 意义

- 考勤打卡从“客户端自报事实”调整为“服务端生成事实”，后续月度统计、异常处理和 Agent 风险判断才有可信数据基础。
- `Clock` 抽象让时间相关测试可控，后续补卡、排班、跨日和日历计算可以继续用稳定测试方式覆盖边界。
