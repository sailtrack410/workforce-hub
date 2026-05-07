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
