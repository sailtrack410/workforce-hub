# hub-web 新增接口文档

> 对应《hub-web接口需求表-1.0.0.md》2026-10-15 实现的 14/15 项（#13 日程事件未做）。
> 前端可直接对照本文档联调；同步可在后端 `doc.html`（Knife4j）按各接口 `@Operation` 查看在线版。
> 迁移脚本：`server/src/main/resources/db/migration/V66__hub_web_api_requirements.sql`

## 通用约定（与存量接口一致）

- 响应信封：`{ "code": 0, "message": "ok", "data": T, "requestId": "..." }`；非 0 为业务错误
- 雪花 ID：JSON 中一律为**字符串**（19 位数字，防 JS 精度丢失）
- 时间：ISO8601 `2026-10-15T09:30:00`；日期：`YYYY-MM-DD`
- 写操作（POST/PUT/DELETE）需先 `GET /api/csrf` 引导 `XSRF-TOKEN` Cookie 并回传同名 Header
- 分页：`PageResult<T> = { "pageNo", "pageSize", "total", "list": T[] }`
- 员工端接口默认需要登录态（Cookie 会话）；标注「公开」的除外

---

## 一、IM 会话（#1 #2 #3 #4）

### 1.1 会话列表字段扩展 ✅#1 ✅#2

`GET /api/employee/im/conversations`（存量端点，响应扩展）

`ImConversationSummaryResponse` 新增字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| memberCount | string/number \| null | 群成员数，**仅 GROUP 类型返回**，私聊/AI 为 null |
| saveToContacts | boolean | 是否保存到通讯录 |
| showMemberNickname | boolean | 是否显示成员群昵称 |

### 1.2 会话偏好扩展 ✅#2

`POST /api/employee/im/conversations/{conversationId}/preference`（存量端点，请求扩展）

请求体（4 个字段均可选，只传要改的）：

```json
{
  "pin": true,
  "mute": false,
  "saveToContacts": true,
  "showMemberNickname": false
}
```

响应：更新后的 `ImConversationSummaryResponse`。

### 1.3 设置本群昵称 ✅#3

`POST /api/employee/im/conversations/{conversationId}/my-nickname`

请求体：

```json
{ "nickname": "前端组-小王" }
```

- 空串 `""` = 清除群昵称
- 响应：更新后的 `ImConversationMemberResponse`（其中 `groupNickname` 字段为最新值）

### 1.4 清空聊天记录 ✅#4

`POST /api/employee/im/conversations/{conversationId}/messages/clear`

- 无请求体
- 语义：仅清空**当前用户视角**的消息（`cleared_seq` 游标推进），不影响其他成员
- 响应：`{ "cleared": true }`

---

## 二、群公告（#5）

### 2.1 公告列表 ✅#5

`GET /api/employee/im/group-conversations/{conversationId}/notices`

响应 `data`：`Array<ImGroupNoticeResponse>`，按发布时间倒序：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| noticeId | string | 公告 ID |
| title | string \| null | 标题（可空） |
| content | string | 正文 |
| publisherName | string | 发布人姓名 |
| publishedAt | string | ISO8601 |

### 2.2 发布公告 ✅#5

`POST /api/employee/im/group-conversations/{conversationId}/notices`

- 权限：群主（OWNER）/ 管理员（ADMIN），否则业务错误
- 请求体：

```json
{ "title": "周会调整", "content": "本周周会改到周四 14:00" }
```

- 响应：新发布的 `ImGroupNoticeResponse`

---

## 三、审批工作流（#6 #7 #8）

### 3.1 AI 预审统计 ✅#6A

`GET /api/employee/workflow/ai-review/summary`

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| weekReviewed | number | 本周已预审单数 |
| passRate | number | 通过率 0~1 |
| blockedCount | number | 硬规则拦截数 |
| blockedRules | Array | `{ "ruleName": string, "count": number }[]` |

### 3.2 待办列表字段扩展 ✅#6B

`GET /api/employee/workflow/todos`（存量端点，响应扩展）

`WorkflowTodoResponse` 新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| aiVerdict | "PASS" \| "REVIEWING" \| "BLOCKED" \| null | AI 预审结论 |
| aiVerdictName | string \| null | 通过/预审中/已拦截 |

映射规则：Agent 决策 `APPROVED→PASS`、`REJECTED→BLOCKED`、`TO_HUMAN→REVIEWING`。

### 3.3 抄送我列表 ✅#7

`GET /api/employee/workflow/cc-list?pageNo=1&pageSize=10`

响应 `data`：`PageResult<WorkflowCcResponse>`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| requestId | string | 申请单 ID |
| requestNo | string | 单号 |
| bizTypeName | string | 业务类型中文名 |
| title | string | 标题 |
| applicantName | string | 发起人 |
| ccAt | string | 抄送时间 ISO8601 |
| status | string | 申请单状态码 |
| statusName | string | 状态中文名 |
| read | boolean | 是否已读 |

### 3.4 标记抄送已读 ✅#7

`POST /api/employee/workflow/cc-list/{requestId}/read`

- 无请求体；响应 `{ "code": 0, "message": "ok" }`

### 3.5 我发起的·统计 ✅#8

`GET /api/employee/workflow/requests/summary`

响应 `data`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| inFlight | number | 进行中单数 |
| finishedThisMonth | number | 本月已完成单数 |
| avgHandleHours | number \| null | 平均处理时长（小时），无已完成单时为 null |

---

## 四、考勤（#9 #11）

### 4.1 排班查询 ✅#9

`GET /api/employee/attendance/schedules?startDate=2026-10-13&endDate=2026-10-19`

响应 `data`：`Array<AttendanceScheduleResponse>`（新增字段标 ★）：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| scheduleId | string | 排班 ID |
| userId | string | 员工 ID |
| employeeNo | string | 工号 |
| realName | string | 姓名 |
| workDate | string | 排班日期 |
| shiftId | string \| null | 班次 ID |
| shiftName | string \| null | 班次名 |
| calendarId | string \| null | 日历 ID |
| calendarName | string \| null | 日历名 |
| scheduleSource | string | 排班来源 |
| scheduleSourceName | string | 来源中文名 |
| status | string | 状态码 |
| statusName | string | 状态中文 |
| startTime ★ | string \| null | 班次开始 `HH:mm`（当日班段聚合 MIN） |
| endTime ★ | string \| null | 班次结束 `HH:mm`（当日班段聚合 MAX） |
| isWorkday ★ | boolean | 是否工作日 |

无排班日返回空数组或 `isWorkday: false` 且时间为 null。

### 4.2 打卡首末签 ✅#11

`GET /api/employee/attendance/daily-results`（存量端点，响应扩展）

`AttendanceDailyResultResponse` 新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| firstPunchAt | string \| null | 当日首次打卡 ISO8601 |
| lastPunchAt | string \| null | 当日末次打卡 ISO8601 |

---

## 五、通知中心（#10）

### 5.1 通知列表 ✅#10

`GET /api/employee/notifications?pageNo=1&pageSize=10`

响应 `data`：`PageResult<SysNotificationResponse>`：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| notificationId | string | 通知 ID |
| type | string | APPROVAL / SYSTEM / ATTENDANCE / ANNOUNCEMENT |
| title | string | 标题 |
| content | string \| null | 正文 |
| linkType | string \| null | 跳转类型（预留） |
| linkId | string \| null | 跳转目标（预留） |
| read | boolean | 是否已读 |
| createdAt | string | ISO8601 |

### 5.2 未读数 ✅#10

`GET /api/employee/notifications/unread-count` → `data: number`

### 5.3 标记单条已读 ✅#10

`POST /api/employee/notifications/{notificationId}/read` → 无 data

### 5.4 全部已读 ✅#10

`POST /api/employee/notifications/read-all` → 无 data

---

## 六、员工自助注册（#12）

### 6.1 发送注册验证码 ✅#12（公开接口，无需登录）

`POST /api/public/auth/register/send-sms-code`

请求体：

```json
{ "phone": "13800138000" }
```

- 固定 REGISTER 场景；验证码 SHA-256 哈希入库，10 分钟有效，60 秒内不可重发
- **演示模式**：不接真实短信通道，验证码打印在后端日志（`sms-code` 关键字）
- 响应：`data: { "maskedPhone": "138****8000" }`

### 6.2 注册 ✅#12（公开接口，无需登录）

`POST /api/public/auth/register`

请求体：

```json
{
  "realName": "王小明",
  "phone": "13800138000",
  "smsCode": "123456",
  "password": "Abc12345",
  "inviteCode": "WELCOME2026"
}
```

- 密码：≥8 位且同时包含字母和数字；BCrypt 加密存储
- 邀请码：必须为管理端预置的有效邀请码（对应 `auth_invite_code` 表），注册即消耗
- 注册成功：创建员工档案（PROBATION 试用期）+ 登录账号（登录名=手机号）
- 响应：`data: { "userId": "...", "loginName": "13800138000" }`（前端拿到后直接走登录流程）

错误码（信封 code 非 0，message 随附）：

| code | message | 场景 |
| --- | --- | --- |
| 400 | 手机号格式不正确 | phone 校验失败 |
| 400 | 验证码错误或已过期 | 短信码无效/过期/已用 |
| 400 | 邀请码无效或已使用 | inviteCode 校验失败 |
| 400 | 密码需8位以上且包含字母和数字 | 密码强度不足 |
| 400 | 姓名不能为空 | realName 空 |
| 409 | 该手机号已注册 | 手机号已存在 |

---

## 七、假期账户（#14）

### 7.1 总额度字段 ✅#14

`GET /api/employee/leave/accounts`（存量端点，响应扩展）

`LeaveAccountResponse` 新增：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| entitlementMinutes | number \| null | 当年总额度（分钟）；仅按「年度授信」模式入账的假种返回（= 当年 GRANT 流水合计），无授信流水/不扣余额假种为 null |

进度条计算：`progress = availableMinutes / entitlementMinutes`（entitlementMinutes 为 null 时前端退回相对比例）。

---

## 八、IM 附件下载票证（#15）

### 8.1 票证放开 ✅#15

`POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{refId}/download-tickets`

- 存量端点行为变更：原「第一版仅支持单文件下载」限制移除，现按 `downloadType` 白名单放行：

```json
{ "downloadType": "INLINE" }
```

| downloadType | 用途 |
| --- | --- |
| INLINE | 图片/视频预览（lightbox、封面） |
| ATTACHMENT | 文件附件下载 |
| SINGLE | 单文件下载（兼容旧语义） |

- 响应：`DownloadTicketResponse { "ticketNo": "...", "downloadUrl": "..." }`
- 下载走存量 `GET /api/employee/im/attachments/download/{ticketNo}`

---

## 未实现项

| 项 | 说明 |
| --- | --- |
| #13 日程事件 | `GET /api/employee/schedule/events` 未做，建议与 #9 排班合并设计（班次作为 eventType=SHIFT） |
| 邀请码生成（#12 管理侧） | `auth_invite_code` 表已建，消费侧已通；管理端生成/管理接口待排期，测试期可直插表或 V66 数据 |

---

*生成：2026-10-15 ｜ 依据：server 源码 + mvn test 961 用例全绿（0 failures / 0 errors）*
