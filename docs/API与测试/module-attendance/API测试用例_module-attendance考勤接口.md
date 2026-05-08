# API 测试用例 module-attendance 考勤接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-attendance` 考勤接口测试用例 |
| 文档版本 | `v0.1` |
| 适用范围 | 考勤基础配置、排班覆盖、排班唯一模型、排班变更重算、日结果原子 upsert、打卡、异常识别、请假审批通过联动重算、补卡审批通过联动重算、直属上级团队查询 |
| 发布日期 | `2026-05-07` |
| 当前状态 | 第一版自动化测试已落地，已补排班覆盖默认班次、排班唯一模型、排班变更重算、日结果原子 upsert、缺卡专项保护、请假审批通过联动考勤重算、请假分钟级覆盖抵扣和补卡审批通过联动考勤重算 |

---

## 二、测试目标

本轮测试目标：

- 验证管理员可以创建工作日历、班次和考勤组。
- 验证管理员可以添加考勤组成员。
- 验证同一员工考勤组生效区间重叠会被拒绝。
- 验证员工打卡写入 `att_punch_record`。
- 验证正常上下班生成正常日结果。
- 验证迟到、早退生成异常日结果和 `att_exception`。
- 验证员工只能通过员工端查询自己的考勤数据。
- 验证直属上级可以查询直属下级日结果。
- 验证同一员工并发添加重叠考勤组成员时只能成功一条。
- 验证员工登录后被离职，旧 Session 也不能继续打卡。
- 验证管理员排班后，日结果计算优先使用排班班次而不是考勤组默认班次。
- 验证同一员工同一天重复创建排班会被拒绝，停用排班也不能通过新增绕过。
- 验证管理员修改已有排班后，会触发已有日结果重算。
- 验证只有上班卡时会生成 `MISS_PUNCH` 缺卡异常。
- 验证请假审批通过后，整段班次覆盖会把考勤日结果重算为 `LEAVE`，并清理缺卡异常。
- 验证小时假审批通过后，只抵扣实际覆盖的迟到、早退或缺勤分钟，不把整天误算为 `LEAVE`。
- 验证重叠小时假会先合并覆盖区间，不重复抵扣同一段缺勤。
- 验证无考勤组 `NO_RULE` 日结果并发重算时，只生成一条 `att_daily_result`。
- 验证员工实时打卡使用服务端接收时间，不信任客户端历史 `punchAt`。
- 验证员工实时打卡不要求客户端传入 `punchAt`。
- 验证员工实时打卡拒绝 `MANUAL` 等受控来源伪造。
- 验证补卡审批通过后写入人工补卡打卡事实并重算同日日结果。
- 验证测试基线会清理 `att_` 表，避免跨测试污染。

当前已落地自动化测试类：

- `ModuleAttendanceApiIntegrationTest`
- `ModuleLeaveWorkflowIntegrationTest`

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080` |
| JDK | `17` |
| MySQL | `workforce_hub_test` |
| Redis | 已启动，用于会话 |
| 会话模式 | Spring Session + Redis |

---

## 四、测试数据

| 类型 | 登录名 | 密码 | 用途 |
| --- | --- | --- | --- |
| 管理员 | `admin.demo` | `Admin@123456` | 创建考勤基础数据 |
| 员工账号 | `zhangsan.employee` | `Employee@123456` | 打卡、查询自己的考勤 |
| 直属上级 | `lisi.manager` | `Employee@123456` | 查询直属下级考勤 |

组织关系：

- 张三 `userId=2003`。
- 李四 `userId=2002`。
- 张三的 `managerUserId=2002`。

---

## 五、测试用例明细

## 5.1 考勤基础配置与正常打卡

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ATT-API-001` | P0 | `POST /api/admin/attendance/calendars` | 管理员创建工作日历 | 管理员已登录 | `calendarType=STANDARD`、`status=ACTIVE` | HTTP `200`；`code=0`；返回 `calendarId` |
| `ATT-API-002` | P0 | `POST /api/admin/attendance/shifts` | 管理员创建班次 | 管理员已登录 | `09:00:00-18:00:00` | HTTP `200`；`code=0`；返回 `shiftId` 和班段时间 |
| `ATT-API-003` | P0 | `POST /api/admin/attendance/groups` | 管理员创建考勤组 | 日历、班次已创建 | 绑定日历和默认班次 | HTTP `200`；`code=0`；返回 `groupId` |
| `ATT-API-004` | P0 | `POST /api/admin/attendance/groups/{groupId}/members` | 添加考勤组成员 | 考勤组已创建 | `userId=2003`、`effectiveFrom=2026-05-01` | HTTP `200`；`code=0`；返回成员记录 |
| `ATT-API-005` | P0 | `POST /api/employee/attendance/punch` | 员工上班打卡 | 张三已登录且命中考勤组 | `ON_DUTY`、`sourceType=PC` | HTTP `200`；`code=0`；按服务端接收时间写入 `att_punch_record` |
| `ATT-API-006` | P0 | `POST /api/employee/attendance/punch` | 员工下班打卡后生成正常日结果 | `ATT-API-005` 成功 | `OFF_DUTY`、`sourceType=PC` | HTTP `200`；`dailyResult.resultStatus=NORMAL`；`workMinutes=540`；`exceptionFlag=false` |
| `ATT-API-007` | P0 | `GET /api/employee/attendance/daily-results` | 员工查询自己的日结果 | 正常上下班已打卡 | `startDate=endDate=2026-05-07` | 返回 1 条本人日结果；状态为 `NORMAL` |
| `ATT-API-008` | P0 | `GET /api/employee/attendance/team/daily-results` | 直属上级查询直属下级日结果 | 李四已登录；张三有日结果 | `userId=2003` | 返回张三日结果 |

## 5.2 考勤归属与异常识别

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ATT-API-009` | P0 | `POST /api/admin/attendance/groups/{groupId}/members` | 考勤组成员生效区间重叠拒绝 | 张三已有 `2026-05-01` 到 `2026-05-31` 成员关系 | 再添加 `2026-05-15` 起生效关系 | HTTP `200`；`code=409`；`message=员工考勤组生效区间重叠`；数据库只保留 1 条 ACTIVE 关系 |
| `ATT-API-010` | P0 | `POST /api/employee/attendance/punch` | 迟到打卡 | 张三已命中考勤组 | `2026-05-08T09:20:00`、`ON_DUTY` | 写入打卡记录；日结果可被重算 |
| `ATT-API-011` | P0 | `POST /api/employee/attendance/punch` | 早退打卡后生成异常 | `ATT-API-010` 成功 | `2026-05-08T17:40:00`、`OFF_DUTY` | `resultStatus=ABNORMAL`；`lateMinutes=20`；`earlyLeaveMinutes=20`；`exceptionFlag=true` |
| `ATT-API-012` | P0 | `GET /api/employee/attendance/exceptions` | 员工查询自己的异常 | `ATT-API-011` 成功 | `startDate=endDate=2026-05-08` | 返回 2 条异常，类型分别为 `LATE` 和 `EARLY_LEAVE` |
| `ATT-API-013` | P0 | `POST /api/admin/attendance/groups/{groupId}/members` | 并发添加重叠考勤组成员只成功一条 | 8 个线程同时为张三添加 `2026-06-01` 起长期生效归属 | 相同员工、不同考勤组、重叠区间 | 最终只有 1 条 `code=0`；数据库只保留 1 条 ACTIVE 关系 |
| `ATT-API-014` | P0 | `POST /api/employee/attendance/punch` | 离职后旧会话不能继续打卡 | 张三先登录并打上班卡，后台随后把张三流转到 `OFFBOARDED` | 使用旧员工 Session 打下班卡 | HTTP `200`；`code=403`；`message=员工当前状态不允许打卡`；当天只保留离职前 1 条打卡记录 |
| `ATT-API-015` | P0 | `POST /api/admin/attendance/schedules` | 管理员创建排班 | 张三已加入考勤组；存在默认班次和另一个排班班次 | `workDate=2026-05-10`、`shiftId=排班班次` | HTTP `200`；`code=0`；返回 `scheduleId` |
| `ATT-API-016` | P0 | `POST /api/employee/attendance/punch` | 排班班次覆盖考勤组默认班次 | `ATT-API-015` 成功 | 张三按排班班次 `10:00-19:00` 打上下班卡 | 日结果 `scheduleId` 为排班 ID，`shiftId` 为排班班次，`resultStatus=NORMAL` |
| `ATT-API-017` | P0 | `POST /api/admin/attendance/schedules` | 重复排班拒绝 | 张三当天已有 `ACTIVE` 排班 | 再次创建同一员工同一天排班 | HTTP `200`；`code=409`；`message=员工当天已存在排班，请通过修改原排班恢复或改班` |
| `ATT-API-018` | P0 | `POST /api/admin/attendance/schedules` | 已停用排班仍占用单日唯一模型 | 张三当天已有排班且已通过 `PUT` 改为 `INACTIVE` | 再次创建同一员工同一天排班 | HTTP `200`；`code=409`；`message=员工当天已存在排班，请通过修改原排班恢复或改班`；数据库仍只有 1 条排班且状态保持 `INACTIVE` |
| `ATT-API-019` | P0 | `PUT /api/admin/attendance/schedules/{scheduleId}` | 修改排班触发已有日结果重算 | 张三按 `10:00-19:00` 排班打卡且日结果为正常 | 把同一天排班改为 `09:00-18:00` | HTTP `200`；`code=0`；日结果 `shiftId` 变为新班次；`resultStatus=ABNORMAL`；`lateMinutes=60`；`exceptionFlag=true` |
| `ATT-API-020` | P0 | `POST /api/employee/attendance/punch` | 只有上班卡生成缺卡异常 | 张三已命中考勤组 | 只提交 `ON_DUTY` 打卡 | 日结果 `resultStatus=ABNORMAL`；`absenceMinutes=270`；异常列表返回 1 条 `MISS_PUNCH` |
| `ATT-API-021` | P0 | `POST /api/employee/attendance/punch` | 员工实时打卡不信任客户端历史时间 | 服务端当前时间为 `2026-05-14T10:30:00`；请求体传 `punchAt=2026-05-07T09:00:00` | `ON_DUTY`、`sourceType=PC` | HTTP `200`；响应 `data.punchAt=2026-05-14T10:30:00`；`2026-05-07` 没有打卡记录；`2026-05-14` 有 1 条打卡记录 |
| `ATT-API-022` | P0 | `POST /api/employee/attendance/punch` | 员工实时打卡不要求客户端时间 | 服务端当前时间为 `2026-05-15T09:05:00` | 请求体不传 `punchAt`，只传 `ON_DUTY`、`sourceType=PC` | HTTP `200`；响应 `data.punchAt=2026-05-15T09:05:00`；写入当天打卡记录 |
| `ATT-API-023` | P0 | `POST /api/employee/attendance/punch` | 员工实时打卡拒绝受控来源伪造 | 张三已登录 | `ON_DUTY`、`sourceType=MANUAL` | HTTP `200`；`code=400`；`message=员工实时打卡来源仅支持 APP/PC`；不写入打卡记录 |
| `ATT-API-024` | P0 | `AttendanceRecalculationService.recalculateDailyResultForExternalEvent` | 无考勤组日结果并发重算原子 upsert | 张三当天没有有效考勤组 | 8 个线程同时重算 `2026-07-01` | 不抛死锁或重复键异常；数据库只有 1 条日结果；`resultStatus=NO_RULE` |

## 5.3 请假审批联动考勤重算

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ATT-WF-001` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` | 请假审批通过后重算当天考勤为请假结果 | 张三有有效考勤组；张三提交 `2026-05-07` 请假；李四为直属审批人 | 李四审批通过请假任务 | 请假申请进入 `APPROVED`；张三 `2026-05-07` 日结果为 `LEAVE`；`absenceMinutes=0`；`exceptionFlag=false`；当天异常数为 0 |
| `ATT-WF-002` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` + `GET /api/employee/attendance/daily-results` | 小时假只抵扣实际覆盖缺勤 | 张三有有效考勤组；张三提交 `09:00-10:00` 小时假且当天无打卡 | 李四审批通过后，张三查询 `2026-05-07` 日结果 | 日结果 `resultStatus=ABNORMAL`；`absenceMinutes=480`；`exceptionFlag=true`；不会整天写成 `LEAVE` |
| `ATT-WF-003` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` + `GET /api/employee/attendance/daily-results` | 重叠小时假不重复抵扣缺勤 | 张三提交 `09:00-10:00` 和 `09:30-10:30` 两段小时假且当天无打卡 | 两段请假均审批通过 | 合并覆盖 90 分钟；日结果 `absenceMinutes=450`，不是重复抵扣后的 420 |
| `ATT-WF-004` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` + `POST /api/employee/attendance/punch` | 小时假抵扣迟到和早退窗口 | 张三提交 `09:00-10:00`、`17:00-18:00` 两段小时假；实际 `10:00` 上班、`17:00` 下班 | 请假审批通过后员工打上下班卡 | 日结果 `resultStatus=NORMAL`；`lateMinutes=0`；`earlyLeaveMinutes=0`；`absenceMinutes=0`；当天无异常 |

## 5.4 补卡审批联动考勤重算

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ATT-WF-005` | P0 | `POST /api/employee/workflow/clock-correction-requests` + `POST /api/employee/workflow/tasks/{taskId}/approve` + `GET /api/employee/attendance/daily-results` | 补卡审批通过后修正缺卡异常 | 张三有有效考勤组；张三当天只有上班卡，日结果为 `ABNORMAL` 且存在 `MISS_PUNCH`；补卡流程模板已发布 | 张三提交 `OFF_DUTY` 补卡，李四审批通过 | 写入 1 条 `source_type=MANUAL` 下班卡；日结果重算为 `NORMAL`；`exceptionFlag=false`；`workMinutes=540`；当天 `MISS_PUNCH` 异常数为 0 |

---

## 六、验证命令

当前已通过命令：

```bash
cd server
mvn -Dtest=ModuleAttendanceApiIntegrationTest test
mvn -Dtest=ModuleAttendanceApiIntegrationTest#concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow test
mvn -Dtest=ModuleAttendanceApiIntegrationTest#inactiveScheduleShouldStillRejectCreateForSameUserDate,ModuleAttendanceApiIntegrationTest#duplicateActiveScheduleShouldBeRejected test
mvn -Dtest=ModuleAttendanceApiIntegrationTest#employeePunchShouldAllowMissingClientPunchAt,ModuleAttendanceApiIntegrationTest#employeePunchShouldUseServerReceivedTimeInsteadOfClientPunchAt,ModuleAttendanceApiIntegrationTest#employeePunchShouldRejectControlledManualSourceType test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedHourlyLeaveShouldOnlyOffsetCoveredAbsenceMinutes,ModuleLeaveWorkflowIntegrationTest#overlappingApprovedHourlyLeavesShouldNotDoubleOffsetAbsenceMinutes,ModuleLeaveWorkflowIntegrationTest#approvedHourlyLeaveShouldOffsetLateAndEarlyWindows test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test
```

建议进入下一节点前执行：

```bash
cd server
mvn -DskipTests compile
mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test
```

---

## 七、当前未覆盖

- 独立 `ModuleAttendanceCalculationIntegrationTest`。
- 管理员补卡规则治理、补卡次数限制和异常申诉处理流水。
- 跨天班次下的请假分钟级覆盖。
- 工作日历、节假日和休息日对请假覆盖与考勤日结果的精细影响。
- 多实例部署下高并发考勤组成员重叠写入的分布式锁或数据库锁表治理。
