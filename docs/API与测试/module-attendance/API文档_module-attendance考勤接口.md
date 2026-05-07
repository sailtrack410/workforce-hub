# API 文档 module-attendance 考勤接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-attendance` 考勤接口 API 文档 |
| 文档版本 | `v0.1` |
| 适用范围 | 工作日历、班次、考勤组、考勤组成员、排班、员工打卡、日结果、异常查询 |
| 对应模块 | `server/module-attendance` |
| 发布日期 | `2026-05-07` |
| 当前状态 | 第一版考勤基础配置、排班覆盖、排班变更重算、打卡、日结果计算、异常识别、请假审批通过联动日结果重算已实现；补卡审批联动、半天假精细合并待后续迭代 |

---

## 二、接口范围

当前已实现 `6` 个后台接口、`4` 个员工端接口。

后台接口：

- `POST /api/admin/attendance/calendars`
- `POST /api/admin/attendance/shifts`
- `POST /api/admin/attendance/groups`
- `POST /api/admin/attendance/groups/{groupId}/members`
- `POST /api/admin/attendance/schedules`
- `PUT /api/admin/attendance/schedules/{scheduleId}`

员工端接口：

- `POST /api/employee/attendance/punch`
- `GET /api/employee/attendance/daily-results`
- `GET /api/employee/attendance/exceptions`
- `GET /api/employee/attendance/team/daily-results`

内部能力：

- `AttendanceService` 实现 `AttendanceQueryService`，后续 Agent 可通过 SPI 查询日结果与月度异常。
- `AttendanceService` 实现 `AttendanceRecalculationService`，workflow/leave 等外部业务事件可通过 SPI 触发指定员工指定日期的日结果重算。

---

## 三、调用约定

### 3.1 基础地址

```text
http://localhost:8080
```

### 3.2 鉴权方式

当前项目采用服务端 Session 鉴权。

- 后台考勤治理接口必须使用管理员会话。
- 员工端打卡、我的日结果、我的异常接口必须使用员工会话。
- 团队日结果第一版只允许直属上级查询直属下级。
- 跨端身份由 `UserTypePathFilter` 拦截。

### 3.3 响应体格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

业务冲突仍以 HTTP `200` 返回统一响应体，`code` 表示业务错误，例如区间重叠返回 `code=409`。

---

## 四、关键枚举

### 4.1 status

| 值 | 含义 |
| --- | --- |
| `ACTIVE` | 启用 |
| `INACTIVE` | 停用 |

### 4.2 punchType

| 值 | 含义 |
| --- | --- |
| `ON_DUTY` | 上班打卡 |
| `OFF_DUTY` | 下班打卡 |

### 4.3 resultStatus

| 值 | 含义 |
| --- | --- |
| `NORMAL` | 正常 |
| `ABNORMAL` | 异常 |
| `NO_RULE` | 无有效考勤规则 |
| `LEAVE` | 已批准请假覆盖 |

### 4.4 exceptionType

| 值 | 含义 |
| --- | --- |
| `LATE` | 迟到 |
| `EARLY_LEAVE` | 早退 |
| `MISS_PUNCH` | 缺卡 |

---

## 五、接口详情

## 5.1 创建工作日历

```http
POST /api/admin/attendance/calendars
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `calendarCode` | `string` | 是 | 日历编码，最多 64 字符 |
| `calendarName` | `string` | 是 | 日历名称，最多 128 字符 |
| `calendarType` | `string` | 是 | 第一版仅支持 `STANDARD` |
| `status` | `string` | 是 | `ACTIVE` 或 `INACTIVE` |

示例：

```json
{
  "calendarCode": "CAL_STANDARD",
  "calendarName": "标准工作日历",
  "calendarType": "STANDARD",
  "status": "ACTIVE"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "calendarId": 2052160000000000001,
    "calendarCode": "CAL_STANDARD",
    "calendarName": "标准工作日历",
    "calendarType": "STANDARD",
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- `calendarCode` 会转为大写后保存。
- 同一未删除日历编码不能重复。
- 第一版暂不维护 `att_work_calendar_day` 明细。

---

## 5.2 创建班次

```http
POST /api/admin/attendance/shifts
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `shiftCode` | `string` | 是 | 班次编码，最多 64 字符 |
| `shiftName` | `string` | 是 | 班次名称，最多 128 字符 |
| `startTime` | `time` | 是 | 上班时间，如 `09:00:00` |
| `endTime` | `time` | 是 | 下班时间，如 `18:00:00` |
| `onDutyPunchStart` | `time` | 否 | 上班打卡开始，不传默认上班前 1 小时 |
| `onDutyPunchEnd` | `time` | 否 | 上班打卡截止，不传默认上班后 1 小时 |
| `offDutyPunchStart` | `time` | 否 | 下班打卡开始，不传默认下班前 1 小时 |
| `offDutyPunchEnd` | `time` | 否 | 下班打卡截止，不传默认下班后 1 小时 |
| `status` | `string` | 是 | `ACTIVE` 或 `INACTIVE` |

示例：

```json
{
  "shiftCode": "SHIFT_DAY",
  "shiftName": "标准白班",
  "startTime": "09:00:00",
  "endTime": "18:00:00",
  "onDutyPunchStart": "08:00:00",
  "onDutyPunchEnd": "10:00:00",
  "offDutyPunchStart": "17:00:00",
  "offDutyPunchEnd": "19:00:00",
  "status": "ACTIVE"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "shiftId": 2052160000000000002,
    "shiftCode": "SHIFT_DAY",
    "shiftName": "标准白班",
    "startTime": "09:00:00",
    "endTime": "18:00:00",
    "onDutyPunchStart": "08:00:00",
    "onDutyPunchEnd": "10:00:00",
    "offDutyPunchStart": "17:00:00",
    "offDutyPunchEnd": "19:00:00",
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- 第一版不支持跨天班次，`startTime` 必须早于 `endTime`。
- 创建班次会同时写入 `att_shift` 与第一段 `att_shift_segment`。

---

## 5.3 创建考勤组

```http
POST /api/admin/attendance/groups
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `groupCode` | `string` | 是 | 考勤组编码，最多 64 字符 |
| `groupName` | `string` | 是 | 考勤组名称，最多 128 字符 |
| `calendarId` | `long` | 是 | 启用状态工作日历 |
| `defaultShiftId` | `long` | 是 | 启用状态默认班次 |
| `lateGraceMinutes` | `int` | 是 | 迟到宽限分钟，不能小于 0 |
| `earlyLeaveGraceMinutes` | `int` | 是 | 早退宽限分钟，不能小于 0 |
| `status` | `string` | 是 | `ACTIVE` 或 `INACTIVE` |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "groupId": 2052160000000000003,
    "groupCode": "GROUP_RD",
    "groupName": "研发考勤组",
    "calendarId": 2052160000000000001,
    "defaultShiftId": 2052160000000000002,
    "lateGraceMinutes": 0,
    "earlyLeaveGraceMinutes": 0,
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- 工作日历和默认班次必须存在且启用。
- 同一未删除考勤组编码不能重复。
- 第一版暂不做地点、IP、人脸和外勤规则校验。

---

## 5.4 添加考勤组成员

```http
POST /api/admin/attendance/groups/{groupId}/members
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | `long` | 是 | 员工 ID，必须存在且未离职 |
| `effectiveFrom` | `date` | 是 | 生效开始日 |
| `effectiveTo` | `date` | 否 | 生效结束日，不传表示长期 |
| `sourceType` | `string` | 是 | 来源，如 `MANUAL` |

示例：

```json
{
  "userId": 2003,
  "effectiveFrom": "2026-05-01",
  "effectiveTo": "2026-05-31",
  "sourceType": "MANUAL"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "memberId": 2052160000000000004,
    "groupId": 2052160000000000003,
    "userId": 2003,
    "effectiveFrom": "2026-05-01",
    "effectiveTo": "2026-05-31",
    "sourceType": "MANUAL",
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- 同一员工的 `ACTIVE` 考勤组生效区间不能重叠。
- `effectiveTo` 不能早于 `effectiveFrom`。
- 第一版在单实例内按员工维度加应用锁，并在事务内复查重叠区间，避免同一员工并发写入重叠归属。
- 如果后续改成多实例部署，需要升级为数据库锁表或 Redis 分布式锁，不能继续只依赖 JVM 内存锁。

## 5.5 创建员工排班

```http
POST /api/admin/attendance/schedules
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | `long` | 是 | 员工 ID，必须存在且未离职 |
| `workDate` | `date` | 是 | 排班日期 |
| `shiftId` | `long` | 是 | 启用状态班次 |
| `calendarId` | `long` | 否 | 工作日历 ID，传入时必须启用 |
| `scheduleSource` | `string` | 是 | `MANUAL`、`AUTO` 或 `TEMPLATE` |

示例：

```json
{
  "userId": 2003,
  "workDate": "2026-05-10",
  "shiftId": 2052160000000000002,
  "calendarId": 2052160000000000001,
  "scheduleSource": "MANUAL"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "scheduleId": 2052160000000000005,
    "userId": 2003,
    "workDate": "2026-05-10",
    "shiftId": 2052160000000000002,
    "calendarId": 2052160000000000001,
    "scheduleSource": "MANUAL",
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- 同一员工同一天只能有一条 `ACTIVE` 排班。
- 排班班次必须存在且启用。
- 传入工作日历时，工作日历必须存在且启用。
- 日结果计算时优先使用当天有效排班班次；未命中排班时才使用考勤组默认班次。

---

## 5.6 修改员工排班

```http
PUT /api/admin/attendance/schedules/{scheduleId}
Content-Type: application/json
Cookie: SESSION=...
```

### 路径参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `scheduleId` | `long` | 是 | 排班 ID |

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `shiftId` | `long` | 是 | 启用状态班次 |
| `calendarId` | `long` | 否 | 工作日历 ID，传入时必须启用 |
| `scheduleSource` | `string` | 是 | `MANUAL`、`AUTO` 或 `TEMPLATE` |
| `status` | `string` | 是 | `ACTIVE` 或 `INACTIVE` |

示例：

```json
{
  "shiftId": 2052160000000000008,
  "calendarId": 2052160000000000001,
  "scheduleSource": "MANUAL",
  "status": "ACTIVE"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "scheduleId": 2052160000000000005,
    "userId": 2003,
    "workDate": "2026-05-10",
    "shiftId": 2052160000000000008,
    "calendarId": 2052160000000000001,
    "scheduleSource": "MANUAL",
    "status": "ACTIVE"
  },
  "requestId": "..."
}
```

### 业务规则

- 排班不存在时返回 `code=404`。
- 修改排班时会锁定原排班记录和员工主档，避免同一员工排班变更与打卡计算交叉写入。
- 新班次必须存在且启用。
- 传入工作日历时，工作日历必须存在且启用。
- 如果该员工该日期已有 `att_daily_result`，修改排班后会在同一事务内重算日结果和异常。
- 当排班改为 `INACTIVE` 后，日结果重算会回退到考勤组默认班次。
- 第一版为原地修改模型，不保留排班变更历史；如果后续需要审计历史，应补版本表或变更流水。

---

## 5.7 员工打卡

```http
POST /api/employee/attendance/punch
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `punchAt` | `datetime` | 是 | 打卡时间 |
| `punchType` | `string` | 是 | `ON_DUTY` 或 `OFF_DUTY` |
| `sourceType` | `string` | 是 | 来源，如 `PC`、`APP` |
| `locationText` | `string` | 否 | 位置描述，最多 256 字符 |
| `ipAddress` | `string` | 否 | IP 地址，最多 64 字符 |
| `deviceId` | `string` | 否 | 设备 ID，最多 128 字符 |

示例：

```json
{
  "punchAt": "2026-05-07T09:00:00",
  "punchType": "ON_DUTY",
  "sourceType": "PC",
  "locationText": "杭州总部",
  "ipAddress": "127.0.0.1",
  "deviceId": "web-001"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "punchRecordId": 2052160000000000005,
    "userId": 2003,
    "punchAt": "2026-05-07T09:00:00",
    "punchType": "ON_DUTY",
    "sourceType": "PC",
    "status": "NORMAL",
    "dailyResult": {
      "dailyResultId": 2052160000000000006,
      "userId": 2003,
      "workDate": "2026-05-07",
      "scheduleId": null,
      "groupId": 2052160000000000003,
      "shiftId": 2052160000000000002,
      "resultStatus": "ABNORMAL",
      "lateMinutes": 0,
      "earlyLeaveMinutes": 0,
      "absenceMinutes": 270,
      "workMinutes": 0,
      "exceptionFlag": true
    }
  },
  "requestId": "..."
}
```

### 业务规则

- 打卡用户固定取当前员工会话，不允许客户端传 `userId`。
- 打卡前会重新校验数据库中的员工主档状态和账号状态；员工离职、账号停用或主档停用后，即使旧 Session 未过期也不能继续打卡。
- 打卡后会重算当天 `att_daily_result`。
- 第一版按当天最早 `ON_DUTY` 和最晚 `OFF_DUTY` 计算。
- 如果当天存在有效排班，日结果使用排班班次；否则使用考勤组默认班次。
- 如果当天存在已审批通过的请假申请覆盖，日结果重算时写入 `resultStatus=LEAVE`，迟到、早退、缺勤和工时均归零，并清理当天已有异常。
- 同一日重复打卡会重算日结果，并先清理旧异常再重建异常，避免异常重复累计。
- 第一版请假覆盖按日期判断，只要已批准请假与考勤日期重叠即按整日请假处理；暂不做半天假、小时假和打卡记录的分钟级合并。
- 当前计算不扣午休，不处理节假日、补卡、外勤定位和跨天班次。

---

## 5.8 员工查询自己的日结果

```http
GET /api/employee/attendance/daily-results?startDate=2026-05-07&endDate=2026-05-07
Cookie: SESSION=...
```

### 查询参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `startDate` | `date` | 是 | 开始日期 |
| `endDate` | `date` | 是 | 结束日期 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "dailyResultId": 2052160000000000006,
      "userId": 2003,
      "workDate": "2026-05-07",
      "resultStatus": "NORMAL",
      "lateMinutes": 0,
      "earlyLeaveMinutes": 0,
      "absenceMinutes": 0,
      "workMinutes": 540,
      "exceptionFlag": false
    }
  ],
  "requestId": "..."
}
```

### 业务规则

- 只能查询当前登录员工自己的日结果。
- 查询跨度不能超过 63 天。
- 请假审批通过后会由 workflow 侧触发重算；员工查询该日期日结果时可看到 `resultStatus=LEAVE`。

---

## 5.9 员工查询自己的异常

```http
GET /api/employee/attendance/exceptions?startDate=2026-05-08&endDate=2026-05-08
Cookie: SESSION=...
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "exceptionId": 2052160000000000007,
      "dailyResultId": 2052160000000000006,
      "userId": 2003,
      "workDate": "2026-05-08",
      "exceptionType": "LATE",
      "exceptionDesc": "迟到20分钟",
      "status": "PENDING"
    }
  ],
  "requestId": "..."
}
```

### 业务规则

- 只能查询当前登录员工自己的异常。
- 第一版异常状态只写入 `PENDING`，忽略、补卡修正、管理员处理待后续节点。

---

## 5.10 直属上级查询团队日结果

```http
GET /api/employee/attendance/team/daily-results?userId=2003&startDate=2026-05-07&endDate=2026-05-07
Cookie: SESSION=...
```

### 业务规则

- 第一版仅允许 `org_user.manager_user_id = 当前登录员工 ID` 的直属上级查询。
- 非直属上级返回 `code=403`、`message=只能查询直属下级考勤`。

---

## 六、审批联动规则

### 6.1 请假审批通过联动日结果

直属上级审批通过请假申请后：

- `WorkflowService` 在审批事务内调用 `AttendanceRecalculationService.recalculateDailyResultForExternalEvent(userId, workDate)`。
- 重算日期范围为请假开始日期到结束日期，包含首尾日期。
- `AttendanceService` 在日结果计算时通过 `LeaveQueryService.hasApprovedLeaveOnDate(userId, workDate)` 判断是否存在已审批通过请假。
- 命中已批准请假时，日结果写入：
  - `resultStatus=LEAVE`
  - `lateMinutes=0`
  - `earlyLeaveMinutes=0`
  - `absenceMinutes=0`
  - `workMinutes=0`
  - `exceptionFlag=false`
- 已存在的 `att_exception` 会被清理，避免请假通过后仍显示缺卡或迟到早退。

### 6.2 当前边界

- 第一版只按日期覆盖判断请假，不按分钟计算请假覆盖比例。
- 第一版不区分上午半天、下午半天和小时假；这些场景会暂时按整日 `LEAVE` 展示。
- 如果员工该日期没有有效考勤组，日结果仍可能是 `NO_RULE`，不会强行写成 `LEAVE`。
- 补卡审批通过后的打卡修正和二次重算待后续节点实现。

---

## 七、当前未覆盖

- 特殊班次模板、批量排班和排班变更历史。
- `att_work_calendar_day` 工作日、休息日、节假日明细。
- 半天假、小时假与打卡结果的分钟级合并。
- 补卡申请审批通过后修正打卡记录和异常。
- 外勤、定位、IP、人脸设备校验。
- 管理员异常治理列表与忽略异常。
- 多实例部署下考勤组成员区间重叠的分布式锁或数据库锁表治理。
