# hub-web 接口需求表（前端 → 后端，字段级规格）

> 用途：前端按画布实现时发现的后端数据/接口缺口。每项含：现状（现有端点+字段）、期望（端点+请求/响应字段定义+示例）、前端消费点。
> 提出日期：2026-10-14 ｜ 字段规格补充：2026-10-14 ｜ 状态：**已实现（2026-10-15，14/15 项，#13 日程事件留待排期）**
>
> **实现记录（2026-10-15）**：V66 迁移 `V66__hub_web_api_requirements.sql` 已应用（im_conversation_member 偏好/群昵称/清空标记 + 新表 im_group_notice、sys_notification、auth_sms_code、auth_invite_code）；mvn test 全量 961 用例 0 失败 0 错误（首轮 10 个 Redis 网络超时错误重跑后全绿）。各端点 @Operation/@Schema 注释齐备，doc.html 可查。逐项实现端点：
> - #1/#2 会话摘要补 `memberCount`（仅 GROUP）、`saveToContacts`、`showMemberNickname`；`POST /api/employee/im/conversations/{id}/preference` 支持 4 字段偏好
> - #3 `POST /api/employee/im/conversations/{id}/my-nickname` `{nickname}`（空串清除）→ ImConversationMemberResponse
> - #4 `POST /api/employee/im/conversations/{id}/messages/clear` → `{cleared:true}`（cleared_seq 用户视角清空）
> - #5 `GET/POST /api/employee/im/group-conversations/{id}/notices`（发布需群主/管理员）
> - #6 `GET /api/employee/workflow/ai-review/summary` + WorkflowTodoResponse 补 `aiVerdict/aiVerdictName`（APPROVED→PASS/REJECTED→BLOCKED/其余 REVIEWING）
> - #7 `GET /api/employee/workflow/cc-list?pageNo=&pageSize=` + `POST /api/employee/workflow/cc-list/{requestId}/read`
> - #8 `GET /api/employee/workflow/requests/summary` → `{inFlight, finishedThisMonth, avgHandleHours}`
> - #9 `GET /api/employee/attendance/schedules?startDate=&endDate=`（att_shift_segment 聚合班次时间）
> - #10 `GET /api/employee/notifications?pageNo=&pageSize=`、`GET .../unread-count`、`POST .../{id}/read`、`POST .../read-all`
> - #11 AttendanceDailyResultResponse 补 `firstPunchAt/lastPunchAt`
> - #12 `POST /api/public/auth/register/send-sms-code` `{phone}`（REGISTER 场景，SHA-256 哈希 10 分钟有效，演示模式验证码打日志）+ `POST /api/public/auth/register`（邀请码→部门、密码 8 位含字母数字、BCrypt、PROBATION 入职）→ 直接返回登录态；SecurityConfig 已放行两个匿名端点
> - #14 LeaveAccountResponse 补 `entitlementMinutes`（当年 GRANT 汇总，实测年假=1440）
> - #15 IM 附件下载票证放开：INLINE/ATTACHMENT 类型白名单取代「仅单文件」限制
> - 遗留：#13 日程事件未做（建议与 #9 合并设计）；管理端邀请码生成接口未建（#12 目前只有消费侧）
>
> **通用约定**（与现有接口一致）：
> - 响应信封：`{ code: 0, message: "ok", data: T, requestId: string }`，错误码 6 位（如 100400）
> - 雪花 ID：19 位数字，**JSON 中必须序列化为字符串**（JS Number 精度丢失）
> - 时间：ISO8601 字符串（`2026-10-14T09:30:00`），日期：`YYYY-MM-DD`
> - 写操作需先经 `GET /api/csrf` 引导 XSRF-TOKEN Cookie 并回传同名 Header
> - 分页结构沿用 `PageResult<T> = { pageNo, pageSize, total, list: T[] }`

---

## P1

### #1 ✅ 会话列表群人数（memberCount）

| 项 | 内容 |
| --- | --- |
| 现状 | `GET /api/employee/im/conversations` → `ImConversationSummaryResponse` 无成员数字段，群会话列表/标题无法显示「N人」 |
| 期望 | 会话摘要响应补字段：`memberCount?: number`（仅 GROUP 类型返回；PRIVATE/AGENT/SYSTEM 可省略或 null） |
| 示例 | `{ "conversationId": "2054016024106426370", "conversationType": "GROUP", "title": "研发一部项目同步", "memberCount": 12, ... }` |
| 前端消费 | 会话列表标题「N人」、聊天头标题 `群名（N）`（当前前端用 detail.members.length 兜底，列表页无 detail 时无法显示） |

### #15 ✅ IM 消息附件下载/预览票证（最痛，阻塞图片预览）

| 项 | 内容 |
| --- | --- |
| 现状 | `POST /api/employee/im/conversations/{cid}/messages/{mid}/attachments/{refId}/download-tickets` 对所有附件均返回 `100400 第一版仅支持单文件下载`；`POST /api/employee/files/{refId}/download-tickets` 返回 `100403 仅允许下载个人文件或头像`。云空间转发的附件与直接上传的 IM 附件**都拿不到票证** |
| 期望 | 两选一：① 放开 IM 票证接口支持所有消息附件（推荐，语义最清晰）；② 若坚持单文件限制，请在错误信息/文档中明确"单文件"的判定条件（单条消息仅 1 个附件？附件 ownerType 必须为本消息上传？），并保证云空间转发消息的附件可走某通道下载 |
| 请求 | `{ "downloadType": "INLINE" \| "ATTACHMENT" }`（INLINE=img/video 预览，ATTACHMENT=文件下载） |
| 响应 | `DownloadTicketResponse { ticketNo: string, downloadUrl?: string }`；下载地址沿用 `GET /api/employee/im/attachments/download/{ticketNo}` |
| 前端消费 | 图片消息缩略图+放大预览、视频封面、文件附件下载（批次⑨，代码链路已就绪，拿到 ticketNo 即可显示） |

### #9 ✅ 排班/班次数据

| 项 | 内容 |
| --- | --- |
| 现状 | 无排班接口。工作台「今日班次/今日日程」卡无数据源 |
| 期望 | `GET /api/employee/attendance/schedules?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` |
| 响应 | `Array<ScheduleDayResponse>`：`{ workDate: string, shiftId: string, shiftName: string, startTime: "HH:mm", endTime: "HH:mm", restMinutes?: number, isWorkday: boolean }`；休息/无排班日 `isWorkday: false` 且时间字段 null |
| 前端消费 | 工作台「今日日程/班次」卡（行C）、考勤页排班视图 |

### #11 ✅ 打卡记录明细（首签/末签时间）

| 项 | 内容 |
| --- | --- |
| 现状 | `GET /api/employee/attendance/daily-results` 响应仅有 `workMinutes/resultStatus` 等汇总字段，无当日打卡时间 |
| 期望 | `AttendanceDailyResultResponse` 补字段：`firstPunchAt?: string | null`（当日首签 ISO8601）、`lastPunchAt?: string | null`（当日末签） |
| 示例 | `{ "workDate": "2026-10-14", "workMinutes": 465, "firstPunchAt": "2026-10-14T08:57:12", "lastPunchAt": "2026-10-14T18:02:41", ... }` |
| 前端消费 | 工作台「今日考勤」卡上班/下班打卡时间（当前显示 `--:--`） |

### #6 ✅ AI 预审统计（审批中心右栏）

| 项 | 内容 |
| --- | --- |
| 现状 | 无统计接口；审批中心「AI 预审概览」卡无数据；待办单上无 AI 预审结论 |
| 期望 A | `GET /api/employee/workflow/ai-review/summary` → `{ weekReviewed: number, passRate: number, blockedCount: number, blockedRules: Array<{ ruleName: string, count: number }> }` |
| 期望 B | `WorkflowTodoResponse` 补字段：`aiVerdict?: "PASS" \| "REVIEWING" \| "BLOCKED" | null`、`aiVerdictName?: string | null` |
| 前端消费 | 审批中心右栏「AI 预审概览」卡（本周预审单数/通过率/硬规则拦截）、审批单「AI预审通过/预审中」标签（画布 8:34） |

---

## P2

### #2 ✅ 会话偏好扩展字段

| 项 | 内容 |
| --- | --- |
| 现状 | `POST /api/employee/im/conversations/{id}/preference` 仅支持 `{ pin?: boolean, mute?: boolean }` |
| 期望 | 请求与 `ImConversationSummaryResponse` 响应同步扩展：`saveToContacts?: boolean`、`showMemberNickname?: boolean` |
| 前端消费 | 详情栏开关「保存到通讯录」「显示成员昵称」（前端已置灰占位，接口放开即启用） |

### #3 ✅ 本群昵称设置

| 项 | 内容 |
| --- | --- |
| 现状 | 无接口 |
| 期望 | `POST /api/employee/im/conversations/{id}/my-nickname`，请求 `{ nickname: string }`（空串=清除）；响应返回更新后的 `ImConversationMemberResponse`（补 `nickname?: string | null` 字段）；群成员列表中他人可见 |
| 前端消费 | 详情栏行项「本群昵称」（置灰占位中）、群消息发送者显示名优先级：群昵称 > 备注 > 姓名 |

### #4 ✅ 清空聊天记录

| 项 | 内容 |
| --- | --- |
| 现状 | 无接口 |
| 期望 | `POST /api/employee/im/conversations/{id}/messages/clear`，无请求体；语义=仅清空**当前用户视角**的消息（不影响其他成员），响应 `{ cleared: true }` |
| 前端消费 | 详情栏危险区「清空聊天记录」（置灰占位中，附二次确认弹窗） |

### #5 ✅ 群公告多条记录

| 项 | 内容 |
| --- | --- |
| 现状 | `ImGroupProfileResponse.noticeText` 单条文本，无发布人/时间 |
| 期望 | `GET /api/employee/im/group-conversations/{id}/notices` → `Array<{ noticeId: string, title?: string, content: string, publisherName: string, publishedAt: string }>`（按发布时间倒序）；发布沿用 `updateGroupProfile.noticeText` 或新 `POST .../notices` |
| 前端消费 | 群公告详情弹窗（画布 8:5671：多公告列表=标题/内容/发布信息） |

### #7 ✅ 抄送我列表

| 项 | 内容 |
| --- | --- |
| 现状 | 无接口 |
| 期望 | `GET /api/employee/workflow/cc-list?pageNo=&pageSize=` → `PageResult<CcItemResponse>`；`CcItemResponse { requestId: string, requestNo: string, bizTypeName: string, title: string, applicantName: string, ccAt: string, status: string, statusName: string, read: boolean }`；另需 `POST .../cc-list/{requestId}/read` 标记已读 |
| 前端消费 | 审批中心「抄送我」页签（画布 8:34 三页签之一，当前占位） |

### #8 ✅ 我发起的·统计口径

| 项 | 内容 |
| --- | --- |
| 现状 | `GET /api/employee/workflow/requests` 仅分页列表，指标卡「进行中/本月已审批/平均耗时」前端只能按当前页估算 |
| 期望 | 响应补 `summary: { inFlight: number, finishedThisMonth: number, avgHandleHours: number | null }`（与筛选条件同口径） |
| 前端消费 | 审批中心指标卡 ×4（画布 8:34） |

### #10 ✅ 通知中心

| 项 | 内容 |
| --- | --- |
| 现状 | 无接口，工作台通知卡拿 workflow todos 冒充 |
| 期望 | `GET /api/employee/notifications?pageNo=&pageSize=` → `PageResult<NotificationResponse>`；`{ notificationId: string, type: "APPROVAL" \| "SYSTEM" \| "ATTENDANCE" \| "ANNOUNCEMENT", title: string, content?: string, createdAt: string, read: boolean, linkType?: string, linkId?: string }`；未读数 `GET .../notifications/unread-count`；`POST .../notifications/{id}/read` |
| 前端消费 | 工作台通知卡、顶导铃铛红点与通知面板 |

### #12 ✅ 员工自助注册

| 项 | 内容 |
| --- | --- |
| 现状 | 无注册接口 |
| 期望 | ① `POST /api/employee/auth/send-sms-code { phone: string, scene: "REGISTER" }`；② `POST /api/employee/auth/register { realName: string, phone: string, smsCode: string, password: string, inviteCode: string }` → 注册成功返回 `CurrentUserResponse`（直接登录）；错误码约定：验证码错误/过期、邀请码无效、手机号已注册 |
| 前端消费 | 注册页（画布 10:10，表单已按画布实现，提交为占位） |

### #13 日程事件

| 项 | 内容 |
| --- | --- |
| 现状 | 无接口，工作台「今日日程」卡空态 |
| 期望 | `GET /api/employee/schedule/events?date=YYYY-MM-DD` → `Array<{ eventId: string, title: string, startTime: "HH:mm", endTime?: "HH:mm", eventType: "MEETING" \| "SHIFT" \| "PERSONAL", location?: string }>`；可与 #9 排班合并设计（班次作为一种 eventType） |
| 前端消费 | 工作台「今日日程」卡（行C，画布事件：产品评审会/面试/部门周会） |

---

## P3

### #14 ✅ 假期总额度

| 项 | 内容 |
| --- | --- |
| 现状 | `GET /api/employee/leave/accounts` 仅 `availableMinutes/frozenMinutes`，假期余额进度条只能用相对比例 |
| 期望 | `LeaveAccountResponse` 补 `entitlementMinutes: number`（年度总额度）；进度 = available/entitlement |
| 前端消费 | 工作台「我的假期」进度条（画布 3:1420） |

---

## 附注

- 添加群成员弹窗的「标签筛选」（前端/后端/设计/主管）：依赖岗位序列字段，`OrgUserSummaryResponse` 补 `jobFamily?: string | null` 或 `tags?: string[]`（P3，前端已用 department 覆盖部门筛选）。
- 群头像上传：会话资料更新接口补 `avatarFileId` 写入（P3）。
- 登录态持久化：刷新页面后 `auth/me` 偶发 401 跳登录（纯体验问题，建议 token/cookie 有效期 ≥ 12h，P2）。

## 联调发现的后端 bug（2026-10-15，批次⑫⑬）

| # | 问题 | 定位 | 优先级 |
| --- | --- | --- | --- |
| B1 | **#3 本群昵称写读不一致**：`POST /group-conversations/{id}/my-nickname` 返回成功且响应含 `groupNickname`，但 `GET /conversations/{id}` 与 `GET /group-conversations/{id}/members` 的 `groupNickname` 恒为 null | `server/.../im/repository/ImRepository.java`：`updateGroupNickname` 的 UPDATE SQL 正常（V66 已建列），但**成员查询的 SELECT/RowMapper 未映射 `group_nickname` 列** | P1 |
| B2 | 接口文档 1.3 路径写错：文档写 `/conversations/{id}/my-nickname`，实际后端是 `/group-conversations/{id}/my-nickname`（前端已按实际路径联调通过） | `EmployeeImController.java:306` | 文档修正 |
| B3 | 发布公告响应 `publisherName` 首次为 null（后续查询正常显示「演示员工一」） | notices 写入后组装响应时未回填发布人姓名 | P3 |
