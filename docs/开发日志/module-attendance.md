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
- 请假通过后考勤日结果识别为请假而非缺勤。
- 补卡申请审批通过后修正打卡记录和异常。
- 管理员异常治理列表、忽略异常和处理流水。
- 外勤、定位、IP、人脸设备校验。
- 多实例部署下考勤组成员区间重叠写入的分布式锁或数据库锁表治理。
