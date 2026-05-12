# API 文档：module-im 会话接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-im` 会话接口 API 文档 |
| 文档版本 | `v1.1` |
| 适用范围 | 员工端会话列表、私聊、群聊、Agent 会话、消息发送、消息分页、编辑撤回、已读、偏好、附件下载、WebSocket 推送、后台 outbox 治理 |
| 对应模块 | `server/src/main/java/com/workforcehub/im` |
| 发布日期 | `2026-05-12` |
| 当前状态 | 后端会话闭环、IM x Agent 异步真实模型回写、失败提示、WebSocket outbox 可靠投递和后台 outbox 查询/重试/状态总览/失败聚合已实现，可供 employee-web 与后续 admin-web 接入 |

---

## 二、前端最小接入顺序

前端演示链路优先按下面顺序接，不要先做后台运营入口：

1. 员工登录：`POST /api/employee/auth/login`，保存浏览器自动维护的 `SESSION` Cookie。
2. 拉会话列表：`GET /api/employee/im/conversations`。
3. 选中会话后拉消息：`GET /api/employee/im/conversations/{conversationId}/messages`。
4. 发送普通消息：`POST /api/employee/im/conversations/{conversationId}/messages`。
5. 建立 WebSocket：`ws://localhost:8080/ws/employee/im`，复用同一个员工 `SESSION` Cookie。
6. 收到 `IM_MESSAGE_CREATED`、`IM_AGENT_MESSAGE_CREATED`、`IM_AGENT_REPLY_FAILED` 后刷新对应会话和消息流。
7. Agent 对话：先 `GET /api/employee/agents` 取可用 Agent，再 `POST /api/employee/im/agent-conversations` 创建会话，随后向该会话发送 `TEXT` 消息。

### employee-web 当前联调前置

如果前端登录后消息页仍然是空的，优先检查两类前置：

1. 当前员工是否已有会话和消息
2. 后台是否已初始化可用 Agent

后端本身不会自动生成演示聊天数据；本地需要至少创建一条私聊或群聊后，列表才会出现内容。

---

## 三、调用约定

### 3.1 基础地址

```text
http://localhost:8080
```

WebSocket 地址：

```text
ws://localhost:8080/ws/employee/im
```

### 3.2 鉴权方式

当前项目使用 Spring Session，不使用 Bearer Token。

- HTTP 接口必须使用员工会话 Cookie：`SESSION=...`。
- WebSocket 握手复用同一个员工会话 Cookie。
- 未登录握手返回 HTTP `401`，后台管理员会话或不可登录员工握手返回 HTTP `403`。
- 员工状态不可登录时，HTTP IM 接口返回 `code=403`，消息为 `当前员工状态不允许使用会话`。

如果要在本地演示 IM 内 Agent 回复，但暂时没接真实模型，可以先启动兼容桩：

```bash
python3 server/scripts/dev/start_agent_stub_server.py
```

默认模型桩地址：

```text
http://127.0.0.1:18080/v1/chat/completions
```

### 3.3 请求头

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | `POST` 时是 | `application/json` | 请求体类型 |
| `Cookie` | 是 | `SESSION=...` | 员工登录会话 |
| `X-Request-Id` | 否 | `im202605120001` | 请求链路 ID；响应会回写 |

### 3.4 响应体格式

除附件下载执行接口外，接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "im202605120001"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `number` | 业务码，`0` 表示成功 |
| `message` | `string` | 业务提示 |
| `data` | `object/array/null` | 业务数据 |
| `requestId` | `string` | 请求链路 ID |

---

## 四、核心数据模型

### 4.1 ImConversationSummaryResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `conversationId` | `number` | 会话 ID |
| `conversationType` | `string` | `PRIVATE`、`GROUP`、`AGENT` |
| `conversationKey` | `string/null` | 服务端唯一键；前端只读 |
| `title` | `string/null` | 会话标题 |
| `avatarFileId` | `number/null` | 头像文件 ID，当前可为空 |
| `boundAgentId` | `number/null` | Agent 会话绑定的 Agent ID |
| `lastMessageSeq` | `number` | 会话最后消息序号 |
| `lastReadSeq` | `number` | 当前员工已读序号 |
| `unreadCount` | `number` | 当前员工未读数 |
| `lastMessage` | `ImMessageResponse/null` | 最后一条消息 |
| `lastMessageAt` | `string/null` | 最后一条消息时间，ISO LocalDateTime |
| `status` | `string` | 会话状态，通常为 `ACTIVE` |
| `pin` | `boolean` | 当前员工是否置顶 |
| `mute` | `boolean` | 当前员工是否免打扰 |
| `memberRole` | `string` | 当前员工角色，常见 `OWNER`、`MEMBER` |

示例：

```json
{
  "conversationId": 7001,
  "conversationType": "PRIVATE",
  "conversationKey": "PRIVATE:2002:2003",
  "title": "张三与李四",
  "avatarFileId": null,
  "boundAgentId": null,
  "lastMessageSeq": 2,
  "lastReadSeq": 1,
  "unreadCount": 1,
  "lastMessage": {
    "messageId": 7102,
    "conversationId": 7001,
    "seqNo": 2,
    "senderType": "USER",
    "senderId": 2003,
    "messageType": "TEXT",
    "contentJson": "{\"text\":\"下午同步一下排班。\",\"attachmentCount\":0}",
    "contentText": "下午同步一下排班。",
    "attachments": [],
    "quotedMessageId": null,
    "status": "NORMAL",
    "sentAt": "2026-05-12T09:30:00"
  },
  "lastMessageAt": "2026-05-12T09:30:00",
  "status": "ACTIVE",
  "pin": false,
  "mute": false,
  "memberRole": "MEMBER"
}
```

### 4.2 ImMessageResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `messageId` | `number` | 消息 ID |
| `conversationId` | `number` | 会话 ID |
| `seqNo` | `number` | 会话内递增序号 |
| `senderType` | `string` | `USER`、`AGENT`、`SYSTEM` |
| `senderId` | `number` | 发送方 ID；系统消息为 `0` |
| `messageType` | `string` | `TEXT`、`FILE`、`SYSTEM`、Agent 可返回 `CARD` |
| `contentJson` | `string` | 消息原始 JSON 字符串 |
| `contentText` | `string` | 服务端从 `contentJson.text` 提取的展示文本 |
| `attachments` | `ImMessageAttachmentResponse[]` | 消息附件，撤回消息返回空数组 |
| `quotedMessageId` | `number/null` | 引用消息，当前第一版一般为空 |
| `status` | `string` | `NORMAL`、`EDITED`、`RECALLED` |
| `sentAt` | `string/null` | 发送时间，ISO LocalDateTime |

### 4.3 ImMessageAttachmentResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attachmentRefId` | `number` | 附件引用 ID |
| `displayName` | `string` | 文件展示名 |
| `mimeType` | `string` | MIME 类型 |
| `fileSize` | `number` | 文件大小，字节 |
| `contentSha256` | `string` | 文件 SHA-256 |
| `scanStatus` | `string` | 安全扫描状态，`PASSED`、`PENDING`、`FAILED` 等 |

### 4.4 ImConversationDetailResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `conversation` | `ImConversationSummaryResponse` | 会话摘要 |
| `members` | `ImConversationMemberResponse[]` | 活跃成员；详情接口会包含 Agent 成员 |
| `groupProfile` | `ImGroupProfileResponse/null` | 群资料；仅群聊返回 |

### 4.5 ImConversationMemberResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `memberId` | `number` | 成员 ID，用户成员为 `org_user.id`，Agent 成员为 Agent ID |
| `memberType` | `string` | `USER`、`AGENT` |
| `displayName` | `string` | 展示名；关联用户或 Agent 已软删除时降级为 `USER:{memberId}` 或 `AGENT:{memberId}` |
| `memberRole` | `string` | `OWNER`、`MEMBER`、`AGENT` 等 |
| `status` | `string` | 成员状态 |
| `joinedAt` | `string/null` | 加入时间 |

### 4.6 ImGroupProfileResponse

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `conversationId` | `number` | 群聊会话 ID |
| `title` | `string` | 群名称 |
| `avatarFileId` | `number/null` | 群头像文件 ID |
| `groupType` | `string` | 当前自定义群为 `CUSTOM` |
| `ownerUserId` | `number` | 群主用户 ID |
| `noticeText` | `string/null` | 群公告 |
| `status` | `string` | 群状态 |
| `currentMemberRole` | `string/null` | 当前员工角色 |

---

## 五、会话接口

### 5.1 查询当前员工会话列表

`GET /api/employee/im/conversations`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `beforeLastMessageAt` | 否 | 游标时间，ISO LocalDateTime |
| `beforeConversationId` | 否 | 游标会话 ID |
| `size` | 否 | 默认 `30`，最大 `100` |

规则：

- `beforeLastMessageAt` 和 `beforeConversationId` 必须同时传或同时不传。
- `size <= 0` 返回 `code=400`、`message=分页大小必须大于0`。
- 列表只返回当前员工活跃参与的会话。
- 排序规则为当前员工私有置顶优先，然后按 `lastMessageAt` 倒序、`conversationId` 倒序。
- 游标翻页会带入游标会话的置顶状态，前端继续只需要传 `beforeLastMessageAt + beforeConversationId`。

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "conversationId": 7001,
      "conversationType": "PRIVATE",
      "title": "张三与李四",
      "lastMessageSeq": 2,
      "lastReadSeq": 1,
      "unreadCount": 1,
      "lastMessageAt": "2026-05-12T09:30:00",
      "status": "ACTIVE",
      "pin": false,
      "mute": false,
      "memberRole": "MEMBER"
    }
  ],
  "requestId": "..."
}
```

### 5.2 查询会话详情

`GET /api/employee/im/conversations/{conversationId}`

响应 `data` 为 `ImConversationDetailResponse`。

规则：

- 非成员访问已有会话返回 `code=403`、`message=无权访问该会话`。
- 会话不存在返回 `code=404`、`message=会话不存在`。
- 群聊返回 `groupProfile`，私聊和 Agent 会话该字段为 `null`。
- 成员展示名由后端直接返回；如果历史成员关联用户或 Agent 被软删除，`displayName` 会降级为 `USER:{memberId}` 或 `AGENT:{memberId}`，前端不需要再兜底展示空字符串。

### 5.3 发起私聊

`POST /api/employee/im/private-conversations`

请求：

```json
{
  "targetUserId": 2003
}
```

响应 `data` 为 `ImConversationSummaryResponse`。

规则：

- 不能和自己发起私聊。
- 仅允许直属上级、直属下级或同群成员之间发起私聊。
- 两人私聊会话具备幂等效果；重复发起返回同一个活跃会话。

### 5.4 创建自定义群聊

`POST /api/employee/im/group-conversations`

请求：

```json
{
  "title": "项目推进群",
  "memberUserIds": [2003, 2004]
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 群名称，最大 `64` 字符 |
| `memberUserIds` | 是 | 初始成员用户 ID，最大 `200` 人；当前员工自己会自动成为群主 |

规则：

- `memberUserIds` 中的当前员工和 `null` 会被忽略。
- 忽略后没有其他成员返回 `code=400`、`message=群聊至少需要选择1名其他成员`。
- 目标成员不存在或不可加入返回 `code=404`。

### 5.5 发起 Agent 会话

`POST /api/employee/im/agent-conversations`

请求：

```json
{
  "agentId": 2053519959787827201
}
```

响应 `data` 为 `ImConversationSummaryResponse`，其中：

- `conversationType=AGENT`
- `boundAgentId` 为请求的 `agentId`
- `title` 为 Agent 名称

规则：

- Agent 必须已发布、启用且当前员工具备 `use` 权限。
- 同一员工对同一 Agent 重复发起会返回同一个活跃 Agent 会话。
- 创建会话本身不会触发 Agent 回复；只有向 Agent 会话发送 `TEXT` 消息才会触发。

---

## 六、消息接口

### 6.1 查询会话消息

`GET /api/employee/im/conversations/{conversationId}/messages`

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `beforeSeq` | 否 | 查询早于该序号的消息 |
| `size` | 否 | 默认 `50`，最大 `100` |

响应 `data` 为 `ImMessageResponse[]`。

规则：

- 仅会话活跃成员可查。
- `size <= 0` 返回 `code=400`、`message=分页大小必须大于0`。
- 前端可用 `beforeSeq` 做向上翻页。

### 6.2 发送消息

`POST /api/employee/im/conversations/{conversationId}/messages`

请求：

```json
{
  "messageType": "TEXT",
  "text": "我下午两点同步。",
  "attachmentRefIds": [],
  "clientMessageId": "client-msg-20260512-0001"
}
```

字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `messageType` | 否 | `TEXT` 或 `FILE`；不传时有附件且无文本为 `FILE`，否则为 `TEXT` |
| `text` | 否 | 文本内容，最大 `4000` 字符；服务端会 trim |
| `attachmentRefIds` | 否 | 附件引用 ID，最大 `10` 个 |
| `clientMessageId` | 建议必传 | 客户端幂等 ID，最大 `128` 字符 |

规则：

- 文本和附件不能同时为空。
- `clientMessageId` 在同一会话、同一发送人下幂等；重复提交返回已存在消息，不重复写库。
- 附件第一版必须来自当前员工个人文件 `PERSONAL_FILE`，发送后会转成 `IM_MESSAGE` 归属。
- 向 Agent 会话发送 `USER + TEXT` 消息后，后端会在事务提交后异步触发 Agent 回复。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "messageId": 2055521000000000001,
    "conversationId": 7001,
    "seqNo": 3,
    "senderType": "USER",
    "senderId": 2002,
    "messageType": "TEXT",
    "contentJson": "{\"text\":\"我下午两点同步。\",\"attachmentCount\":0}",
    "contentText": "我下午两点同步。",
    "attachments": [],
    "quotedMessageId": null,
    "status": "NORMAL",
    "sentAt": "2026-05-12T10:12:30"
  },
  "requestId": "..."
}
```

### 6.3 编辑文本消息

`POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/edit`

请求：

```json
{
  "text": "我下午三点同步。"
}
```

规则：

- 只能编辑自己发送的消息。
- 只能编辑 `TEXT` 类型且状态为 `NORMAL` 或 `EDITED` 的消息。
- 成功后消息 `status=EDITED`，并推送 `IM_MESSAGE_EDITED`。

### 6.4 撤回消息

`POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/recall`

规则：

- 只能撤回自己发送的消息。
- 只能撤回 `NORMAL` 或 `EDITED` 状态消息。
- 成功后消息 `status=RECALLED`，`contentText=消息已撤回`，附件列表为空，并推送 `IM_MESSAGE_RECALLED`。

### 6.5 更新已读位点

`POST /api/employee/im/conversations/{conversationId}/read-position`

请求：

```json
{
  "lastReadSeq": 3
}
```

规则：

- `lastReadSeq` 必填且不能小于 `0`。
- 如果传入值大于会话最后消息序号，服务端会压到 `lastMessageSeq`。
- 成功后返回更新后的 `ImConversationSummaryResponse`，并推送 `IM_READ_POSITION_UPDATED`。

### 6.6 更新会话偏好

`POST /api/employee/im/conversations/{conversationId}/preference`

请求：

```json
{
  "pin": true,
  "mute": false
}
```

规则：

- `pin` 和 `mute` 至少传一个。
- 成功后返回更新后的 `ImConversationSummaryResponse`，并只向当前操作者推送 `IM_CONVERSATION_PREFERENCE_UPDATED`。
- `pin=true` 会影响当前员工自己的会话列表排序，不影响其他成员的列表顺序。

---

## 七、群治理接口

### 7.1 查询群成员

`GET /api/employee/im/group-conversations/{conversationId}/members`

响应 `data` 为 `ImConversationMemberResponse[]`，只返回活跃用户成员。

### 7.2 查询群资料

`GET /api/employee/im/group-conversations/{conversationId}/profile`

响应 `data` 为 `ImGroupProfileResponse`。

### 7.3 更新群资料

`POST /api/employee/im/group-conversations/{conversationId}/profile`

请求：

```json
{
  "title": "项目推进群-后端",
  "noticeText": "今天先完成会话闭环"
}
```

规则：

- 仅自定义群群主可操作。
- `title` 最大 `64` 字符，`noticeText` 最大 `1024` 字符。
- 两个字段不能同时为空。
- 成功推送 `IM_GROUP_PROFILE_UPDATED`。

### 7.4 添加群成员

`POST /api/employee/im/group-conversations/{conversationId}/members`

请求：

```json
{
  "memberUserIds": [2004, 2005]
}
```

规则：

- 仅自定义群群主可操作。
- 当前员工自己和 `null` 会被忽略。
- 成功返回最新成员列表，并推送 `IM_GROUP_MEMBERS_ADDED`。

### 7.5 移除群成员

`POST /api/employee/im/group-conversations/{conversationId}/members/remove`

请求：

```json
{
  "memberUserIds": [2004]
}
```

规则：

- 仅自定义群群主可操作。
- 群主不能把自己移出群聊。
- 成功返回最新成员列表，并向变更前成员推送 `IM_GROUP_MEMBERS_REMOVED`。

### 7.6 主动退群

`POST /api/employee/im/group-conversations/{conversationId}/leave`

规则：

- 仅群成员可操作。
- 群主不能直接退群。
- 成功返回最新成员列表，并向变更前成员推送 `IM_GROUP_MEMBER_LEFT`。

### 7.7 解散群聊

`POST /api/employee/im/group-conversations/{conversationId}/dismiss`

规则：

- 仅自定义群群主可操作。
- 成功返回 `data=null`，并向变更前成员推送 `IM_GROUP_DISMISSED`。

---

## 八、附件接口

### 8.1 创建消息附件下载票据

`POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets`

请求：

```json
{
  "downloadType": "SINGLE",
  "reason": "查看会话附件"
}
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "downloadTicketId": 2055522000000000001,
    "ticketNo": "IMDL2055522000000000001",
    "downloadUrl": "/api/employee/im/attachments/download/IMDL2055522000000000001",
    "expireAt": "2026-05-12T10:22:30",
    "fileName": "会话资料.txt",
    "fileSize": 1024
  },
  "requestId": "..."
}
```

规则：

- 第一版只支持 `downloadType=SINGLE`。
- 当前员工必须有权访问会话。
- 消息状态必须为 `NORMAL` 或 `EDITED`。
- 附件必须属于该消息且安全扫描未失败。
- 下载票据有效期为 `10` 分钟，只能由申请人使用。

### 8.2 执行附件下载

`GET /api/employee/im/attachments/download/{ticketNo}`

该接口返回文件流，不返回统一 `R<T>`。

规则：

- 票据不存在返回 `404`。
- 使用他人票据返回 `403`。
- 票据已使用或失效返回 `409`。
- 票据过期返回 `410`。

---

## 九、WebSocket 契约

### 9.1 连接

```text
ws://localhost:8080/ws/employee/im
```

浏览器端使用同源或代理时，`SESSION` Cookie 会自动带上。跨端口联调时确保后端 `workforce.im.websocket.allowed-origin-patterns` 包含前端 Origin，例如：

```yaml
workforce:
  im:
    websocket:
      allowed-origin-patterns: http://localhost:5173,http://127.0.0.1:5173
```

连接成功后服务端立即发送：

```json
{
  "eventType": "CONNECTED",
  "occurredAt": "2026-05-12T10:00:00"
}
```

前端可发送纯文本 `PING` 或包含 `"PING"` 的消息，服务端返回：

```json
{
  "eventType": "PONG",
  "occurredAt": "2026-05-12T10:00:10"
}
```

### 9.2 事件结构

业务事件统一结构：

```json
{
  "eventType": "IM_MESSAGE_CREATED",
  "conversationId": 7001,
  "messageId": 2055521000000000001,
  "seqNo": 3,
  "actorUserId": 2002,
  "payload": {},
  "occurredAt": "2026-05-12T10:12:30"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `eventType` | `string` | 事件类型 |
| `conversationId` | `number/null` | 相关会话 ID |
| `messageId` | `number/null` | 相关消息 ID |
| `seqNo` | `number/null` | 消息序号或已读位点 |
| `actorUserId` | `number/null` | 触发人；Agent 事件为 Agent ID，系统失败提示事件为 Agent ID |
| `payload` | `object/array` | 事件载荷 |
| `occurredAt` | `string` | 事件产生时间 |

### 9.3 事件类型与前端处理

| eventType | payload | 前端建议处理 |
| --- | --- | --- |
| `IM_MESSAGE_CREATED` | `ImMessageResponse` | 如果当前打开该会话，追加或按 `messageId` 去重更新消息流；刷新会话列表摘要 |
| `IM_AGENT_MESSAGE_CREATED` | `ImMessageResponse` | Agent 成功回复；同上，按 `messageId` 去重 |
| `IM_AGENT_REPLY_FAILED` | `ImMessageResponse` | Agent 失败系统提示；展示 `payload.contentText`，不要展示模型供应商原始错误 |
| `IM_MESSAGE_EDITED` | `ImMessageResponse` | 替换本地同 `messageId` 消息 |
| `IM_MESSAGE_RECALLED` | `ImMessageResponse` | 替换为撤回态，隐藏附件 |
| `IM_READ_POSITION_UPDATED` | `{ userId, lastReadSeq }` | 更新已读位点；当前第一版可只刷新会话摘要 |
| `IM_CONVERSATION_PREFERENCE_UPDATED` | `{ userId, pin, mute }` | 只推送给操作者本人；更新当前员工会话置顶/免打扰状态 |
| `IM_GROUP_PROFILE_UPDATED` | `ImGroupProfileResponse` | 刷新群资料和会话标题 |
| `IM_GROUP_MEMBERS_ADDED` | `ImConversationMemberResponse[]` | 刷新成员列表 |
| `IM_GROUP_MEMBERS_REMOVED` | `ImConversationMemberResponse[]` | 刷新成员列表；若当前用户被移除，应退出该会话视图 |
| `IM_GROUP_MEMBER_LEFT` | `ImConversationMemberResponse[]` | 刷新成员列表 |
| `IM_GROUP_DISMISSED` | `{ conversationId }` | 将会话从列表移除或标记不可用 |

### 9.4 outbox 可靠投递说明

后端已将 IM WebSocket 业务事件写入 `im_event_outbox`，事务提交后由 dispatcher 投递：

- 前端 payload 结构不因 outbox 改变。
- 初次入账事件会被标记为立即可投递，数据库事务提交后会马上尝试推送；失败后按 `next_retry_at` 重试。
- WebSocket 只保证投递到当前在线连接；离线期间仍应通过会话列表和消息分页补齐。
- 前端收到事件应按 `messageId`、`conversationId + seqNo` 或事件语义做幂等处理，避免重连或重试导致 UI 重复追加。

---

## 十、后台 outbox 治理接口

后台 outbox 接口用于 admin-web 或演示排障查看实时推送账本。所有接口必须使用后台管理员 `SESSION`，员工会话访问返回 HTTP `403`。

### 10.1 outbox 状态总览

```text
GET /api/admin/im/outbox-events/summary
```

用途：

- 给 admin-web 首页、红点和排障面板展示 outbox 积压概况。
- 只读接口，不触发派发，不修改事件状态，不写审计。
- `dueDispatchCount` 统计当前可被 dispatcher 抢占的事件：到期的 `PENDING/FAILED`，以及超过 `sending-stale-ms` 的 `SENDING`。
- `staleSendingCount` 只统计超过 `sending-stale-ms` 的 `SENDING`，用于识别卡住的投递线程。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "totalCount": 6,
    "dueDispatchCount": 3,
    "staleSendingCount": 1,
    "earliestCreatedAt": "2026-05-12T08:00:00",
    "earliestNextRetryAt": "2000-01-01T00:00:00",
    "generatedAt": "2026-05-12T10:30:00",
    "sendingStaleBefore": "2026-05-12T10:29:00",
    "statusCounts": [
      {
        "status": "PENDING",
        "count": 2
      },
      {
        "status": "SENDING",
        "count": 1
      },
      {
        "status": "SENT",
        "count": 1
      },
      {
        "status": "FAILED",
        "count": 1
      },
      {
        "status": "DEAD",
        "count": 1
      }
    ]
  },
  "requestId": "..."
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `totalCount` | outbox 总事件数 |
| `dueDispatchCount` | 当前 dispatcher 可立即抢占的事件数 |
| `staleSendingCount` | 已超过 `sending-stale-ms` 的 `SENDING` 事件数 |
| `earliestCreatedAt` | 最早创建的 outbox 事件时间；无事件时为 `null` |
| `earliestNextRetryAt` | `PENDING/FAILED` 中最早的下次投递时间；无待投递事件时为 `null` |
| `generatedAt` | 后端生成统计的时间 |
| `sendingStaleBefore` | `claimed_at` 早于该时间的 `SENDING` 会被视为 stale |
| `statusCounts` | 固定按 `PENDING/SENDING/SENT/FAILED/DEAD` 返回，缺失状态 count 为 `0` |

常见错误：

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| 员工会话访问 | `403` | `403` | `当前账号不是管理员身份，无法访问后台端` |

### 10.2 outbox 失败原因聚合

```text
GET /api/admin/im/outbox-events/failure-summary
```

用途：

- 给 admin-web 告警面板展示当前主要失败原因。
- 只聚合 `FAILED`、`DEAD` 事件，不返回 `PENDING/SENDING/SENT`。
- 按 `eventCount DESC`、`lastOccurredAt DESC`、`lastError ASC` 排序，最多返回 20 条。
- `last_error` 为空或空字符串时归一为 `UNKNOWN`。
- 只读接口，不触发重试或派发，不写审计。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "status": "FAILED",
      "lastError": "simulated websocket failure",
      "eventCount": 2,
      "maxRetryCount": 3,
      "firstOccurredAt": "2026-05-12T08:00:00",
      "lastOccurredAt": "2026-05-12T08:05:00"
    },
    {
      "status": "DEAD",
      "lastError": "max attempts",
      "eventCount": 1,
      "maxRetryCount": 5,
      "firstOccurredAt": "2026-05-12T08:10:00",
      "lastOccurredAt": "2026-05-12T08:10:00"
    }
  ],
  "requestId": "..."
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `status` | `FAILED` 或 `DEAD` |
| `lastError` | 失败原因；空值统一为 `UNKNOWN` |
| `eventCount` | 该状态和失败原因下的事件数量 |
| `maxRetryCount` | 当前聚合组内最大的重试次数 |
| `firstOccurredAt` | 该失败组最早更新时间 |
| `lastOccurredAt` | 该失败组最近更新时间 |

常见错误：

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| 员工会话访问 | `403` | `403` | `当前账号不是管理员身份，无法访问后台端` |

### 10.3 分页查询 outbox 事件

```text
GET /api/admin/im/outbox-events?pageNo=1&pageSize=20&status=FAILED&eventType=IM_MESSAGE_CREATED&conversationId=7001
```

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `pageNo` | 否 | 默认 `1`，最小 `1` |
| `pageSize` | 否 | 默认 `20`，最大 `100` |
| `status` | 否 | `PENDING`、`SENDING`、`SENT`、`FAILED`、`DEAD` |
| `eventType` | 否 | WebSocket 事件类型，如 `IM_MESSAGE_CREATED` |
| `conversationId` | 否 | 会话 ID |
| `messageId` | 否 | 消息 ID |
| `actorUserId` | 否 | 触发用户 ID；Agent 事件为 Agent ID |
| `createdFrom` | 否 | ISO 时间，事件创建起始 |
| `createdTo` | 否 | ISO 时间，事件创建结束 |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 1,
    "list": [
      {
        "eventId": 2055523000000000001,
        "eventType": "IM_MESSAGE_CREATED",
        "conversationId": 7001,
        "messageId": 2055522999000000001,
        "seqNo": 3,
        "actorUserId": 2003,
        "recipientUserIdsJson": "[2003,2002]",
        "payloadJson": "{\"eventType\":\"IM_MESSAGE_CREATED\",\"conversationId\":7001}",
        "status": "FAILED",
        "retryCount": 1,
        "nextRetryAt": "2026-05-12T10:15:03",
        "lastError": "simulated websocket failure",
        "claimedAt": "2026-05-12T10:15:00",
        "sentAt": null,
        "createdAt": "2026-05-12T10:15:00",
        "updatedAt": "2026-05-12T10:15:00"
      }
    ]
  },
  "requestId": "..."
}
```

### 10.4 查看单个 outbox 事件

```text
GET /api/admin/im/outbox-events/{eventId}
```

用途：

- 查看单条事件的 `payloadJson`、目标用户、最近失败原因和重试次数。
- 不触发投递，不修改事件状态。

事件不存在返回 `code=404`、`message=IM outbox事件不存在`。

### 10.5 手动重试 outbox 事件

```text
POST /api/admin/im/outbox-events/{eventId}/retry
```

行为：

- 仅允许 `FAILED`、`DEAD` 事件进入手动重试。
- 后端会校验 `payloadJson.eventType` 和 `payloadJson.conversationId` 与索引字段一致；不一致或 JSON 损坏时返回 `409`，避免重放错误 payload。
- 重试接口只把事件重置为可立即投递的 `FAILED` 状态，不同步保证已经推送成功；随后由 dispatcher 或 `dispatch` 接口执行派发。
- `SENDING` 卡住事件不允许手动重试，由 dispatcher 按 `sending-stale-ms` 自动重新抢占。
- 手动重试成功会写入 `sys_audit_log`，`action_type=IM_OUTBOX_RETRY_REQUESTED`，`target_type=IM_EVENT_OUTBOX`，`target_id` 为事件 ID。

常见错误：

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| 事件不存在 | `200` | `404` | `IM outbox事件不存在` |
| 当前状态是 `SENT`、`PENDING` 或 `SENDING` | `200` | `409` | `当前outbox事件状态不允许手动重试` |
| payload 与索引不一致 | `200` | `409` | `outbox事件payload与索引字段不一致，需人工核查后再重试` |
| payload 非合法 JSON | `200` | `409` | `outbox事件payload不是合法JSON，需人工核查后再重试` |

### 10.6 手动派发到期 outbox 事件

```text
POST /api/admin/im/outbox-events/dispatch
Content-Type: application/json

{
  "batchSize": 50
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `batchSize` | 否 | 默认 `50`，最小 `1`，最大 `500` |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestedBatchSize": 50,
    "dispatchedCount": 1
  },
  "requestId": "..."
}
```

说明：

- `dispatchedCount` 表示本次成功发送并标记 `SENT` 的事件数，不等于扫描到的事件数。
- 如果目标用户离线，当前实现仍视为发送完成并标记 `SENT`；前端靠重新拉取会话和消息补齐离线内容。
- `dispatch` 可用于演示前或 admin-web 排障时主动补偿，不替代后台定时 dispatcher。
- 每次主动派发都会写入 `sys_audit_log`，`action_type=IM_OUTBOX_DISPATCH_TRIGGERED`，`target_type=IM_EVENT_OUTBOX_DISPATCH`，详情包含 `requestedBatchSize`、`attemptedCount`、`dispatchedCount`、`attemptedEventIds`、`dispatchedEventIds` 和 `failedEventIds`。

### 10.7 批量重试 outbox 事件

```text
POST /api/admin/im/outbox-events/batch-retry
Content-Type: application/json

{
  "eventIds": [2055523000000000001, 2055523000000000002]
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `eventIds` | 是 | outbox 事件 ID 列表，不能为空，单次最多 `100` 个；重复 ID 会按首次出现顺序去重 |

行为：

- 批量重试采用“部分成功”语义，不因为某一条事件不存在、状态不允许或 payload 异常而回滚整批。
- 单条成功规则与 `POST /api/admin/im/outbox-events/{eventId}/retry` 一致：只允许 `FAILED`、`DEAD`，并校验 payload 与索引字段一致。
- 成功项会被重置为可立即投递的 `FAILED` 状态；接口不保证同步推送成功，后续仍由 dispatcher 或 `dispatch` 接口派发。
- 跳过项会返回 `skipReason`，前端应逐条展示。
- 每次批量重试都会写入 `sys_audit_log`，`action_type=IM_OUTBOX_BATCH_RETRY_REQUESTED`，`target_type=IM_EVENT_OUTBOX_BATCH_RETRY`，`result` 为 `SUCCESS` 或 `PARTIAL_SUCCESS`，详情包含 `requestedEventIds`、`succeededEventIds`、`skippedEventIds`。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestedCount": 3,
    "succeededCount": 1,
    "skippedCount": 2,
    "items": [
      {
        "eventId": 2055523000000000001,
        "succeeded": true,
        "beforeStatus": "FAILED",
        "afterStatus": "FAILED",
        "skipReason": null
      },
      {
        "eventId": 2055523000000000002,
        "succeeded": false,
        "beforeStatus": "SENT",
        "afterStatus": null,
        "skipReason": "当前outbox事件状态不允许手动重试"
      },
      {
        "eventId": 999999999999,
        "succeeded": false,
        "beforeStatus": null,
        "afterStatus": null,
        "skipReason": "IM outbox事件不存在"
      }
    ]
  },
  "requestId": "..."
}
```

常见错误：

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| `eventIds` 为空 | `400` | `400` | `outbox事件ID不能为空` |
| 单次超过 100 个事件 | `400` | `400` | `单次批量重试最多支持100个事件` |
| `eventIds` 包含小于等于 0 的值 | `200` | `400` | `outbox事件ID必须大于0` |
| 员工会话访问 | `403` | `403` | `当前账号不是管理员身份，无法访问后台端` |

### 10.8 查询 outbox 操作审计

```text
GET /api/admin/im/outbox-events/audits?pageNo=1&pageSize=20&eventId=2055523000000000001
```

用途：

- 给 admin-web 展示 outbox 手动重试和主动派发的操作历史。
- 只查询 IM outbox 相关审计，不作为通用 `sys_audit_log` 浏览接口。
- 按 `eventId` 查询时，会匹配单条重试审计的 `target_id`，也会匹配主动派发审计 detail 中的 `attemptedEventIds`、`dispatchedEventIds`、`failedEventIds`。

查询参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `pageNo` | 否 | 默认 `1`，最小 `1` |
| `pageSize` | 否 | 默认 `20`，最大 `100` |
| `actionType` | 否 | 仅支持 `IM_OUTBOX_RETRY_REQUESTED`、`IM_OUTBOX_BATCH_RETRY_REQUESTED`、`IM_OUTBOX_DISPATCH_TRIGGERED` |
| `eventId` | 否 | outbox 事件 ID，用于追踪单个事件被哪些管理员操作影响 |
| `operatorId` | 否 | 管理员用户 ID |
| `occurredFrom` | 否 | ISO 时间，审计发生起始 |
| `occurredTo` | 否 | ISO 时间，审计发生结束 |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 2,
    "list": [
      {
        "auditId": 2055524000000000001,
        "operatorType": "ADMIN",
        "operatorId": 8001,
        "actionType": "IM_OUTBOX_DISPATCH_TRIGGERED",
        "targetType": "IM_EVENT_OUTBOX_DISPATCH",
        "targetId": null,
        "result": "SUCCESS",
        "detailJson": "{\"requestedBatchSize\":10,\"attemptedCount\":1,\"dispatchedCount\":1,\"attemptedEventIds\":[2055523000000000001],\"dispatchedEventIds\":[2055523000000000001],\"failedEventIds\":[]}",
        "occurredAt": "2026-05-12T10:18:00"
      },
      {
        "auditId": 2055523999000000001,
        "operatorType": "ADMIN",
        "operatorId": 8001,
        "actionType": "IM_OUTBOX_RETRY_REQUESTED",
        "targetType": "IM_EVENT_OUTBOX",
        "targetId": 2055523000000000001,
        "result": "SUCCESS",
        "detailJson": "{\"eventId\":2055523000000000001,\"eventType\":\"IM_MESSAGE_CREATED\",\"conversationId\":7001,\"beforeStatus\":\"FAILED\",\"afterStatus\":\"FAILED\"}",
        "occurredAt": "2026-05-12T10:17:30"
      }
    ]
  },
  "requestId": "..."
}
```

常见错误：

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| `actionType` 不支持 | `200` | `400` | `outbox审计动作不支持` |
| `occurredFrom` 晚于 `occurredTo` | `200` | `400` | `发生时间起始不能晚于结束` |
| 员工会话访问 | `403` | `403` | `当前账号不是管理员身份，无法访问后台端` |

---

## 十一、IM x Agent 前端接入说明

### 11.1 获取可用 Agent

使用 `module-agent` 员工端接口：

```text
GET /api/employee/agents
```

前端拿到 `agentId` 后调用：

```text
POST /api/employee/im/agent-conversations
```

### 11.2 触发 Agent 回复

向 Agent 会话发送文本消息：

```json
{
  "messageType": "TEXT",
  "text": "帮我总结一下今天有哪些待办",
  "clientMessageId": "agent-msg-20260512-0001"
}
```

后端行为：

1. 立即写入用户消息并返回 `ImMessageResponse`。
2. WebSocket 推送 `IM_MESSAGE_CREATED`。
3. 事务提交后异步创建 Agent 任务，调用真实模型。
4. 模型成功后写入 `senderType=AGENT` 的消息，并推送 `IM_AGENT_MESSAGE_CREATED`。
5. 模型失败、配置缺失、超时、限流或响应不可解析时写入 `senderType=SYSTEM` 的失败提示，并推送 `IM_AGENT_REPLY_FAILED`。

### 11.3 Agent 成功消息

成功回复示例：

```json
{
  "eventType": "IM_AGENT_MESSAGE_CREATED",
  "conversationId": 9001,
  "messageId": 2055523000000000001,
  "seqNo": 2,
  "actorUserId": 2053519959787827201,
  "payload": {
    "messageId": 2055523000000000001,
    "conversationId": 9001,
    "seqNo": 2,
    "senderType": "AGENT",
    "senderId": 2053519959787827201,
    "messageType": "TEXT",
    "contentText": "今天你有 2 条待审批任务，建议先处理请假申请。",
    "contentJson": "{\"text\":\"今天你有 2 条待审批任务，建议先处理请假申请。\",\"agentTaskId\":2055522999000000001,\"agentCode\":\"HR_HELPER\"}",
    "attachments": [],
    "status": "NORMAL",
    "sentAt": "2026-05-12T10:15:00"
  },
  "occurredAt": "2026-05-12T10:15:00"
}
```

### 11.4 Agent 失败消息

失败提示示例：

```json
{
  "eventType": "IM_AGENT_REPLY_FAILED",
  "conversationId": 9001,
  "messageId": 2055523000000000002,
  "seqNo": 2,
  "actorUserId": 2053519959787827201,
  "payload": {
    "messageId": 2055523000000000002,
    "conversationId": 9001,
    "seqNo": 2,
    "senderType": "SYSTEM",
    "senderId": 0,
    "messageType": "SYSTEM",
    "contentText": "Agent 暂时无法回复，请稍后再试",
    "contentJson": "{\"text\":\"Agent 暂时无法回复，请稍后再试\"}",
    "attachments": [],
    "status": "NORMAL",
    "sentAt": "2026-05-12T10:15:00"
  },
  "occurredAt": "2026-05-12T10:15:00"
}
```

前端规则：

- 失败提示按普通系统消息展示。
- 不要在前端自行拼模型错误文案。
- 不要认为发送用户消息后一定马上有 Agent 回复；必须按异步事件或重新拉消息处理。
- 如果 WebSocket 未在线，重新进入会话时通过消息分页补齐 Agent 回复或失败提示。

---

## 十二、常见错误

| 场景 | HTTP 状态 | 业务码 | message |
| --- | --- | --- | --- |
| 未登录访问 HTTP 接口 | `401` | `401` | `未登录或会话已过期` |
| 非员工会话访问员工端 IM | `403` | `403` | `当前账号不是员工身份，无法访问员工端` |
| 员工状态不可登录 | `200` | `403` | `当前员工状态不允许使用会话` |
| 会话不存在 | `200` | `404` | `会话不存在` |
| 不是会话成员 | `200` | `403` | `无权访问该会话` |
| 私聊目标不存在 | `200` | `404` | `目标用户不存在` |
| 私聊关系不允许 | `200` | `403` | `只能和直属上级、直属下级或同群成员发起私聊` |
| 消息内容为空 | `200` | `400` | `消息内容不能为空` |
| 不支持的消息类型 | `200` | `400` | `当前仅支持发送文本或附件消息` |
| 附件消息无附件 | `200` | `400` | `附件消息必须包含附件` |
| 使用他人附件 | `200` | `403` | `无权使用该附件引用` |
| 附件安全扫描失败 | `200` | `409` | `附件安全扫描未通过` |
| 编辑他人消息 | `200` | `403` | `只能操作自己发送的消息` |
| 编辑非文本消息 | `200` | `400` | `当前仅支持编辑文本消息` |
| 撤回已撤回消息 | `200` | `409` | `当前消息状态不允许撤回` |
| 群成员非群主治理 | `200` | `403` | `只有群主可以管理群成员` |
| 群主直接退群 | `200` | `400` | `群主不能直接退群，请先解散群聊或转让群主` |
| Agent 不可用 | `200` | `409` | `Agent未发布或未启用` |
| 员工无权使用 Agent | `200` | `403` | `无权使用该Agent` |
| 后台 outbox 查询状态不支持 | `200` | `400` | `outbox事件状态不支持` |
| 后台 outbox 查询事件类型不支持 | `200` | `400` | `outbox事件类型不支持` |
| 后台 outbox 审计动作不支持 | `200` | `400` | `outbox审计动作不支持` |
| 后台 outbox 批量重试事件列表为空 | `400` | `400` | `outbox事件ID不能为空` |
| 后台 outbox 员工会话访问 | `403` | `403` | `当前账号不是管理员身份，无法访问后台端` |

---

## 十三、前端落地建议

- `employee-web/src/api/employee.ts` 应作为唯一后端路径出口，避免组件内硬编码路径。
- 发送消息必须生成稳定 `clientMessageId`，推荐格式：`web-{conversationId}-{timestamp}-{random}`。
- 消息流按 `seqNo` 排序，按 `messageId` 去重。
- WebSocket 事件到达时，如果当前会话打开则增量更新；如果不在当前会话，则刷新会话列表摘要。
- Agent 会话发送后显示“等待 Agent 回复”的本地状态即可，不要阻塞输入框。
- `IM_AGENT_REPLY_FAILED` 是业务可见失败，不是前端异常；按系统消息展示。
- WebSocket 断线重连后，应重新拉会话列表和当前会话第一页消息，补齐离线期间 outbox 未能投递到当前连接的消息。
