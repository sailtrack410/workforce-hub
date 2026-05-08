# API 文档 module-workflow 审批接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-workflow` 审批接口 API 文档 |
| 文档版本 | `v0.1` |
| 适用范围 | 请假申请提交、补卡申请提交、我的申请列表、审批待办、审批通过、审批驳回、申请撤回、详情、附件、下载授权、轨迹与后台流程模板治理 |
| 对应模块 | `server/module-workflow` |
| 发布日期 | `2026-05-06` |
| 当前状态 | 第一版 Flowable 审批闭环、我的申请列表、审批附件业务引用、审批附件下载授权、后台流程模板治理、Agent 预审入口、假期余额联动和补卡审批联动考勤重算已实现；完整 Agent 平台待后续迭代 |

---

## 二、接口范围

当前已实现 `11` 个员工端接口、`6` 个后台流程模板治理接口，以及 workflow 侧 Agent 预审 SPI 回调入口。

- `POST /api/employee/workflow/leave-requests`
- `POST /api/employee/workflow/clock-correction-requests`
- `GET /api/employee/workflow/todos`
- `GET /api/employee/workflow/requests`
- `POST /api/employee/workflow/tasks/{taskId}/approve`
- `POST /api/employee/workflow/tasks/{taskId}/reject`
- `POST /api/employee/workflow/requests/{requestId}/withdraw`
- `GET /api/employee/workflow/requests/{requestId}`
- `GET /api/employee/workflow/requests/{requestId}/actions`
- `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets`
- `GET /api/employee/workflow/attachments/download/{ticketNo}`

后台流程模板治理接口：

- `POST /api/admin/workflow/process-types`
- `POST /api/admin/workflow/templates`
- `GET /api/admin/workflow/templates`
- `POST /api/admin/workflow/templates/{templateId}/versions`
- `GET /api/admin/workflow/templates/{templateId}/versions`
- `POST /api/admin/workflow/template-versions/{versionId}/publish`

当前暂未实现但已纳入后续规划：

- 完整 `module-agent` 任务调度、工具调用、后台创建和运行监控

---

## 三、调用约定

### 3.1 基础地址

```text
http://localhost:8080
```

### 3.2 鉴权方式

当前项目采用服务端 Session 鉴权。

- 员工端 workflow 接口必须使用员工会话。
- `/api/employee/workflow/todos` 返回当前登录员工自己的待办。
- `/api/employee/workflow/requests` 返回当前登录员工自己发起的申请。
- `/api/employee/workflow/tasks/{taskId}/approve` 只允许该 Flowable 任务的 assignee 审批。
- `/api/employee/workflow/tasks/{taskId}/reject` 只允许该 Flowable 任务的 assignee 驳回。
- `/api/employee/workflow/requests/{requestId}/withdraw` 只允许申请人撤回审批中的申请。
- 申请详情和审批轨迹只允许申请人、待办处理人或已处理人查看。
- 审批附件下载票据只允许审批参与人为该申请下的 `WF_REQUEST` 附件创建。
- 审批附件下载执行只允许票据申请人本人使用，且票据只能使用一次。
- 后台流程模板治理接口必须使用管理员会话。
- 后台发布模板版本时只绑定已部署的 Flowable 流程定义 Key，不提供在线 BPMN 设计器。

### 3.3 请求头约定

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | `POST` 时是 | `application/json` | 请求体类型 |
| `Cookie` | 是 | `SESSION=...` | 会话维持 |
| `X-Request-Id` | 否 | `wf202605060001` | 请求链路 ID |

### 3.4 响应体格式

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

---

## 四、关键枚举

### 4.1 requestStatus

| 值 | 含义 | 当前是否会产生 |
| --- | --- | --- |
| `DRAFT` | 草稿 | 否 |
| `PENDING_REVIEW` | 待预审 | 是，存在 Agent 预审任务时产生 |
| `IN_APPROVAL` | 审批中 | 是 |
| `APPROVED` | 已通过 | 是 |
| `REJECTED` | 已驳回 | 是 |
| `WITHDRAWN` | 已撤回 | 是 |

### 4.2 workflowTaskAction

| 值 | 含义 | 当前是否会产生 |
| --- | --- | --- |
| `APPROVE` | 审批通过 | 是 |
| `REJECT` | 审批驳回 | 是 |
| `WITHDRAW` | 申请撤回 | 是 |
| `AGENT_PRE_REVIEW` | Agent 预审 | 是 |

### 4.3 todoStatus

| 值 | 含义 |
| --- | --- |
| `PENDING` | 待处理 |
| `DONE` | 已完成 |
| `CLOSED` | 已关闭 |

---

### 4.4 downloadTicketStatus

| 值 | 含义 |
| --- | --- |
| `ACTIVE` | 可使用 |
| `USED` | 已使用 |
| `EXPIRED` | 已过期 |
| `REVOKED` | 已撤销 |

---

### 4.5 templateVersionStatus

| 值 | 含义 |
| --- | --- |
| `DRAFT` | 草稿，未发布，员工端不会使用 |
| `PUBLISHED` | 当前有效发布版本，员工端提交审批时可选用 |
| `ARCHIVED` | 已被新版本替代，保留历史追溯 |

---

## 五、接口详情

## 5.1 提交请假申请

```http
POST /api/employee/workflow/leave-requests
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `leaveTypeId` | `long` | 是 | 假种 ID，必须存在且启用 |
| `startAt` | `string` | 是 | 开始时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `endAt` | `string` | 是 | 结束时间，必须晚于开始时间 |
| `durationMinutes` | `int` | 是 | 请假时长，单位分钟，必须大于 0；服务端会按开始、结束时间计算工作分钟数并校验一致，第一版默认扣除每天 `12:00-13:00` 午休 |
| `reason` | `string` | 是 | 请假理由，最长 512 字符 |
| `attachmentRefIds` | `array<long>` | 否 | 申请附件引用 ID，最多 10 个；必须是当前申请人拥有且未删除的个人附件 |

示例：

```json
{
  "leaveTypeId": 9401,
  "startAt": "2026-05-07 09:00:00",
  "endAt": "2026-05-07 18:00:00",
  "durationMinutes": 480,
  "reason": "家中有事请假一天",
  "attachmentRefIds": [9601]
}
```

### 成功响应

无 Agent 预审任务时，保持原直属上级审批响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2051896265423310851,
    "requestNo": "WF-LEAVE-20260506132606-2051896265419116545",
    "status": "IN_APPROVAL",
    "flowableProcessInstanceId": "151a826b-490c-11f1-8afb-e6db54dfb665"
  },
  "requestId": "f02351bbb11640468dc6f758f8afde95"
}
```

存在 Agent 预审任务时，申请先停留在 `PENDING_REVIEW`，不会立即启动 Flowable：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2052112388202749954,
    "requestNo": "WF-LEAVE-20260507034454-2052112388202749953",
    "status": "PENDING_REVIEW",
    "agentTaskId": 88001
  },
  "requestId": "f02351bbb11640468dc6f758f8afde95"
}
```

### 落库语义

- 写入 `wf_request`，`biz_type=LEAVE`。
- 写入 `wf_req_leave`。
- `wf_request.biz_id` 指向 `wf_req_leave.id`。
- `wf_req_leave.request_id` 回指 `wf_request.id`。
- 服务端校验 `durationMinutes` 必须等于 `startAt` 到 `endAt` 的工作分钟数；第一版仅扣除每天 `12:00-13:00` 午休，暂不处理节假日和排班日历。
- 如果传入 `attachmentRefIds`，先校验原附件必须是当前申请人上传的 `PERSONAL_FILE` 引用，且附件引用、文件对象未删除，安全扫描未失败。
- 对每个有效原附件创建新的 `sys_attachment_ref` 业务引用，`owner_type=WF_REQUEST`、`owner_id=wf_request.id`，并写入 `wf_request_attachment`。
- 审批申请后续展示和归档使用 `WF_REQUEST` 业务附件引用，避免申请人删除个人文件后破坏审批归档；物理文件对象仍复用同一 `sys_file_object`。
- 如果没有可用 Agent 预审触发器，或触发器返回 `null`，直接进入人工审批：`wf_request.status=IN_APPROVAL`，启动 Flowable 流程 `leave_manager_approval`，为直属上级写入 `sys_todo_item`。
- 如果触发器返回 `agentTaskId`，申请进入 `PENDING_REVIEW`，`current_step_key=agent_pre_review`，`agent_pre_review_task_id=agentTaskId`，不启动 Flowable，也不生成直属上级待办。
- 后续 Agent 回调通过或转人工时，workflow 校验 `agentTaskId` 匹配后写入 `AGENT_PRE_REVIEW` 动作，再启动直属上级 Flowable 审批。
- 后续 Agent 回调驳回时，workflow 校验 `agentTaskId` 匹配后写入 `AGENT_PRE_REVIEW` 动作，直接把申请置为 `REJECTED`，不启动 Flowable。
- Agent 回调的 `decisionJson` 会写入 `wf_task_action.agent_decision_json`，员工端轨迹接口返回 `agentDecisionJson`。

---

## 5.2 提交补卡申请

```http
POST /api/employee/workflow/clock-correction-requests
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetDate` | `string` | 是 | 补卡考勤日期，格式 `yyyy-MM-dd` |
| `missingPunchType` | `string` | 是 | 缺卡类型，仅支持 `ON_DUTY`、`OFF_DUTY` |
| `expectedPunchAt` | `string` | 是 | 申请补写的打卡时间，格式 `yyyy-MM-dd HH:mm:ss`，日期必须等于 `targetDate` |
| `reason` | `string` | 是 | 补卡原因，最长 512 字符 |
| `attachmentRefIds` | `array<long>` | 否 | 补卡证明附件引用 ID，最多 10 个；必须是当前申请人拥有且未删除的个人附件 |

示例：

```json
{
  "targetDate": "2026-05-16",
  "missingPunchType": "OFF_DUTY",
  "expectedPunchAt": "2026-05-16 18:00:00",
  "reason": "下班时网络异常，补下班卡",
  "attachmentRefIds": [9601]
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2052500000000000001,
    "requestNo": "WF-CLOCK_CORRECTION-20260508235011-2052500000000000000",
    "status": "IN_APPROVAL",
    "flowableProcessInstanceId": "151a826b-4c1f-11f1-8afb-e6db54dfb665"
  },
  "requestId": "f02351bbb11640468dc6f758f8afde95"
}
```

### 落库与审批语义

- 写入 `wf_request`，`biz_type=CLOCK_CORRECTION`。
- 写入 `wf_req_clock_correction`。
- `wf_request.biz_id` 指向 `wf_req_clock_correction.id`。
- `wf_req_clock_correction.request_id` 回指 `wf_request.id`。
- 员工必须存在直属上级，补卡第一版进入直属上级审批流程 `clock_correction_manager_approval`。
- 附件绑定语义与请假申请一致：提交时把本人 `PERSONAL_FILE` 附件克隆为 `WF_REQUEST` 业务附件引用。
- 审批通过前不写入 `att_punch_record`，避免未批准补卡提前影响考勤结果。
- 直属上级审批通过后，workflow 通过 `AttendanceClockCorrectionService` 通知考勤模块写入 `source_type=MANUAL` 的补卡打卡事实，并触发同日 `att_daily_result` 重算。
- 补卡写入以 `userId + expectedPunchAt + missingPunchType + status=NORMAL` 做幂等判断，重复审批回调不会重复生成同一条人工补卡事实。
- 审批驳回或撤回时不写入人工补卡打卡，也不触发补卡重算。

---

## 5.3 查询当前员工审批待办

```http
GET /api/employee/workflow/todos
Cookie: SESSION=...
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "todoId": 2051896265633026049,
      "requestId": 2051896265423310851,
      "taskId": "151ca553-490c-11f1-8afb-e6db54dfb665",
      "title": "张三的请假申请",
      "summary": "家中有事请假一天",
      "status": "PENDING",
      "priority": "NORMAL",
      "createdAt": "2026-05-06T13:26:06"
    }
  ],
  "requestId": "589a5f6f1648427485f2bc6c964c5294"
}
```

### 查询语义

- 只返回当前登录员工自己的 `PENDING` 待办。
- 待办必须能匹配仍存在的 Flowable 运行中任务。
- 第一版返回审批待办，不返回文件、IM、Agent 等其他来源待办。

---

## 5.4 查询我的申请列表

```http
GET /api/employee/workflow/requests?pageNo=1&pageSize=20&status=IN_APPROVAL&bizType=LEAVE
Cookie: SESSION=...
```

### 查询参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageNo` | `long` | 否 | 页码，默认 `1`，最小 `1` |
| `pageSize` | `long` | 否 | 每页数量，默认 `20`，最大 `100` |
| `status` | `string` | 否 | 申请状态过滤 |
| `bizType` | `string` | 否 | 业务类型过滤，当前支持 `LEAVE`、`CLOCK_CORRECTION` |

### 成功响应

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
        "requestId": 2051912325509763074,
        "requestNo": "WF-LEAVE-20260506142955-2051912325509763073",
        "bizType": "LEAVE",
        "bizId": 2051912325509763075,
        "title": "张三的请假申请",
        "status": "IN_APPROVAL",
        "currentStepKey": "manager_approval",
        "currentStepName": "直属上级审批",
        "submittedAt": "2026-05-06T14:29:55",
        "finishedAt": null
      }
    ]
  },
  "requestId": "589a5f6f1648427485f2bc6c964c5294"
}
```

### 查询语义

- 只返回当前登录员工自己发起的申请。
- 不返回当前员工作为审批人的待办；审批人待办仍走 `/api/employee/workflow/todos`。
- 第一版按 `submitted_at DESC, id DESC` 排序。

---

## 5.5 审批通过

```http
POST /api/employee/workflow/tasks/{taskId}/approve
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `comment` | `string/null` | 否 | 审批意见，最长 512 字符 |

示例：

```json
{
  "comment": "同意，请安排好交接"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2051896265423310851,
    "status": "APPROVED"
  },
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

### 落库语义

- 校验当前登录员工必须是 Flowable 任务 assignee。
- 写入 `wf_task_action`，`action_type=APPROVE`。
- 调用 Flowable `taskService.complete(taskId)`。
- 更新 `wf_request.status=APPROVED`。
- 将对应 `sys_todo_item.status` 更新为 `DONE`。

---

## 5.6 审批驳回

```http
POST /api/employee/workflow/tasks/{taskId}/reject
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `comment` | `string` | 是 | 驳回原因，最长 512 字符 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2051903029824782338,
    "status": "REJECTED"
  },
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

### 落库语义

- 校验当前登录员工必须是 Flowable 任务 assignee。
- 写入 `wf_task_action`，`action_type=REJECT`。
- 完成当前 Flowable 任务并结束流程。
- 更新 `wf_request.status=REJECTED`。
- 将对应 `sys_todo_item.status` 更新为 `DONE`。

---

## 5.7 申请撤回

```http
POST /api/employee/workflow/requests/{requestId}/withdraw
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `comment` | `string` | 是 | 撤回原因，最长 512 字符 |

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "requestId": 2051903029824782338,
    "status": "WITHDRAWN"
  },
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

### 落库语义

- 只允许申请人撤回 `IN_APPROVAL` 状态的申请。
- 删除运行中的 Flowable 流程实例，删除原因固定为 `申请人撤回`。
- 写入 `wf_task_action`，`action_type=WITHDRAW`。
- 更新 `wf_request.status=WITHDRAWN`。
- 将该申请所有未处理待办更新为 `CLOSED`。

---

## 5.8 查询申请详情

```http
GET /api/employee/workflow/requests/{requestId}
Cookie: SESSION=...
```

### 成功响应字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `requestId` | `long` | 申请 ID |
| `requestNo` | `string` | 申请编号 |
| `bizType` | `string` | 业务类型，当前支持 `LEAVE`、`CLOCK_CORRECTION` |
| `bizId` | `long` | 业务明细 ID |
| `applicantUserId` | `long` | 申请人用户 ID |
| `title` | `string` | 申请标题 |
| `status` | `string` | 申请状态 |
| `currentStepKey` | `string/null` | 当前节点 Key |
| `currentStepName` | `string/null` | 当前节点名称 |
| `flowableProcessInstanceId` | `string` | Flowable 流程实例 ID |
| `submittedAt` | `string` | 提交时间 |
| `finishedAt` | `string/null` | 完成时间 |
| `leave` | `object/null` | 请假明细 |
| `clockCorrection` | `object/null` | 补卡明细 |
| `attachments` | `array` | 审批申请附件列表，返回 `WF_REQUEST` 业务附件引用 |

### 补卡明细字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `targetDate` | `string` | 补卡考勤日期 |
| `missingPunchType` | `string` | 缺卡类型，`ON_DUTY` 或 `OFF_DUTY` |
| `expectedPunchAt` | `string` | 审批通过后要写入的人工补卡时间 |
| `reason` | `string` | 补卡原因 |

### 附件字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attachmentRefId` | `long` | 审批申请业务附件引用 ID |
| `displayName` | `string` | 展示文件名 |
| `mimeType` | `string` | MIME 类型 |
| `fileSize` | `long` | 文件大小 |
| `contentSha256` | `string` | 文件 SHA-256 |
| `scanStatus` | `string` | 安全扫描状态 |

### 查询权限

- 申请人可查。
- 当前待办处理人可查。
- 已产生审批动作的处理人可查。

---

## 5.9 查询审批轨迹

```http
GET /api/employee/workflow/requests/{requestId}/actions
Cookie: SESSION=...
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "actionId": 2051903029824782339,
      "requestId": 2051903029824782338,
      "taskId": "d65b70ee-490f-11f1-910f-e6db54dfb665",
      "stepKey": "manager_approval",
      "stepName": "直属上级审批",
      "actorType": "USER",
      "actorId": 2002,
      "actionType": "APPROVE",
      "comment": "同意",
      "agentDecisionJson": null,
      "beforeStatus": "IN_APPROVAL",
      "afterStatus": "APPROVED",
      "actedAt": "2026-05-06T13:52:59"
    }
  ],
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

Agent 预审轨迹示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "actionId": 2052110803351396354,
      "requestId": 2052110803330424834,
      "taskId": null,
      "stepKey": "agent_pre_review",
      "stepName": "Agent预审",
      "actorType": "AGENT",
      "actorId": 88001,
      "actionType": "AGENT_PRE_REVIEW",
      "comment": null,
      "agentDecisionJson": "{\"decision\":\"REJECTED\",\"reason\":\"缺少证明材料\"}",
      "beforeStatus": "PENDING_REVIEW",
      "afterStatus": "REJECTED",
      "actedAt": "2026-05-07T03:38:36"
    }
  ],
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

---

## 5.10 创建审批附件下载票据

```http
POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets
Content-Type: application/json
Cookie: SESSION=...
```

### 路径参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `requestId` | `long` | 是 | 审批申请 ID |
| `attachmentRefId` | `long` | 是 | 审批申请业务附件引用 ID，必须属于该申请的 `WF_REQUEST` 附件 |

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `downloadType` | `string` | 是 | 下载类型，第一版仅支持 `SINGLE` |
| `reason` | `string/null` | 否 | 下载原因，最长 256 字符 |

示例：

```json
{
  "downloadType": "SINGLE",
  "reason": "审批查看证明材料"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "downloadTicketId": 2051964970056302595,
    "ticketNo": "WFDL2051964970056302595",
    "downloadUrl": "/api/employee/workflow/attachments/download/WFDL2051964970056302595",
    "expireAt": "2026-05-06T18:09:07",
    "fileName": "请假证明.pdf",
    "fileSize": 36
  },
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

### 授权与落库语义

- 当前登录员工必须是该审批申请参与人。
- `attachmentRefId` 必须满足 `wf_request_attachment.request_id = requestId`。
- 附件引用必须满足 `sys_attachment_ref.owner_type = WF_REQUEST` 且 `owner_id = requestId`。
- 附件引用、物理文件对象不能被删除。
- 安全扫描失败的附件不能创建下载票据。
- 写入 `sys_download_ticket`，`requester_type=USER`、`requester_id=当前登录员工`、`status=ACTIVE`。
- 票据有效期第一版固定为 10 分钟。
- 写入 `sys_audit_log`，`action_type=WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED`。

---

## 5.11 使用审批附件下载票据下载文件

```http
GET /api/employee/workflow/attachments/download/{ticketNo}
Cookie: SESSION=...
```

### 路径参数

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ticketNo` | `string` | 是 | 审批附件下载票据编号 |

### 成功响应

成功时返回文件流，不包裹统一 `R` 响应。

典型响应头：

```http
HTTP/1.1 200
Content-Type: application/pdf
Content-Disposition: attachment; filename*=UTF-8''%E8%AF%B7%E5%81%87%E8%AF%81%E6%98%8E.pdf
```

### 授权与状态机语义

- 票据必须存在且未删除。
- 票据申请人必须是当前登录员工。
- 票据状态必须为 `ACTIVE`。
- 票据过期后会被标记为 `EXPIRED`，并返回 `410` 业务码。
- 下载执行会重新读取文件对象最新扫描状态；如果最新状态为 `FAILED`，返回 `409`，票据保持 `ACTIVE`。
- 下载前通过条件更新把 `ACTIVE` 抢占为 `USED`，避免同一票据并发重复使用。
- 下载执行时会再次校验当前用户仍是审批申请参与人。
- 写入 `sys_audit_log`，`action_type=WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED`。

---

## 5.12 创建流程类型

```http
POST /api/admin/workflow/process-types
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `processCode` | `string` | 是 | 流程编码，唯一，例如 `LEAVE` |
| `processName` | `string` | 是 | 流程名称 |
| `bizType` | `string/null` | 否 | 关联业务类型，例如 `LEAVE` |
| `status` | `string/null` | 否 | `ACTIVE` 或 `INACTIVE`，默认 `ACTIVE` |

示例：

```json
{
  "processCode": "LEAVE",
  "processName": "请假流程",
  "bizType": "LEAVE",
  "status": "ACTIVE"
}
```

### 落库语义

- 写入 `wf_process_type`。
- `process_code` 必须唯一。
- `created_by`、`updated_by` 写当前管理员 ID。

---

## 5.13 创建流程模板

```http
POST /api/admin/workflow/templates
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `processTypeId` | `long` | 是 | 启用状态的流程类型 ID |
| `templateCode` | `string` | 是 | 模板编码，唯一 |
| `templateName` | `string` | 是 | 模板名称 |
| `status` | `string/null` | 否 | `ACTIVE` 或 `INACTIVE`，默认 `ACTIVE` |

### 落库语义

- 写入 `wf_template`。
- `template_code` 必须唯一。
- `process_type_id` 必须指向启用且未删除的流程类型。

---

## 5.14 创建流程模板版本草稿

```http
POST /api/admin/workflow/templates/{templateId}/versions
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `versionNo` | `int` | 是 | 版本号，同模板下唯一，最小为 `1` |
| `formSchemaJson` | `string/null` | 否 | 表单结构 JSON 字符串，第一版只保存快照 |
| `ruleSnapshotJson` | `string/null` | 否 | 规则快照 JSON 字符串，后续 Agent 预审读取该快照 |

示例：

```json
{
  "versionNo": 1,
  "formSchemaJson": "{\"formKey\":\"leave-request\"}",
  "ruleSnapshotJson": "{\"leaveCode\":\"ANNUAL_LEAVE\",\"maxDurationMinutes\":480}"
}
```

### 落库语义

- 写入 `wf_template_version`。
- 新版本初始状态固定为 `DRAFT`。
- `form_schema_json` 和 `rule_snapshot_json` 必须是合法 JSON；为空时保存 `NULL`。
- 本接口不部署 BPMN，也不发布版本。

---

## 5.15 发布流程模板版本

```http
POST /api/admin/workflow/template-versions/{versionId}/publish
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `flowableDefinitionKey` | `string` | 是 | 已部署 Flowable 流程定义 Key，例如 `leave_manager_approval` |

示例：

```json
{
  "flowableDefinitionKey": "leave_manager_approval"
}
```

### 发布规则

- 只能发布 `DRAFT` 版本。
- `flowableDefinitionKey` 必须能在 Flowable 中查询到最新流程定义。
- 发布后当前版本状态变为 `PUBLISHED`，写入 `flowable_definition_key`、`flowable_definition_id`、`published_by`、`published_at`、`effective_from`。
- 同一模板下旧的当前有效 `PUBLISHED` 版本会自动变为 `ARCHIVED`，并写入 `effective_to`。
- 员工端提交请假申请时，仍按 `wf_process_type.process_code=LEAVE` 查询最新有效发布版本。

---

## 5.16 查询流程模板与版本

```http
GET /api/admin/workflow/templates?processCode=LEAVE
Cookie: SESSION=...
```

查询模板响应语义：

- 返回模板基础信息。
- `currentVersion` 只返回当前有效发布版本。
- 如果模板尚未发布任何版本，`currentVersion=null`。

```http
GET /api/admin/workflow/templates/{templateId}/versions
Cookie: SESSION=...
```

查询版本响应语义：

- 返回指定模板下全部版本。
- 按 `versionNo DESC, id DESC` 排序。
- 包含 `DRAFT`、`PUBLISHED`、`ARCHIVED`。

---

## 六、错误码

| HTTP 状态 | 业务码 | message 示例 | 场景 |
| --- | --- | --- | --- |
| `200` | `0` | `ok` | 成功 |
| `400` | `400` | `结束时间必须晚于开始时间` | 参数或状态不合法 |
| `200` | `400` | `当前申请状态不允许处理Agent预审结果` | Agent 回调时申请不在 `PENDING_REVIEW` |
| `401` | `401` | `未登录或会话已过期` | 未登录 |
| `200` | `403` | `当前用户不是该审批任务处理人` | 已登录但不是该 Flowable 任务处理人 |
| `200` | `403` | `只有申请人可以撤回该申请` | 非申请人撤回 |
| `200` | `403` | `无权查看该审批申请` | 非参与人查看申请详情或轨迹 |
| `200` | `403` | `无权使用该附件引用` | 提交申请时引用了他人附件或非个人附件 |
| `200` | `403` | `无权使用该下载票据` | 当前用户不是票据申请人 |
| `200` | `404` | `补卡申请明细不存在` | 补卡审批通过时申请头缺少补卡明细 |
| `200` | `404` | `附件引用不存在` | 附件引用不存在、已删除或物理文件已删除 |
| `200` | `404` | `审批附件不存在` | 下载票据申请的附件不属于该审批申请 |
| `200` | `404` | `下载票据不存在` | 下载票据编号不存在 |
| `200` | `404` | `流程类型不存在或未启用` | 创建模板时流程类型不存在、停用或已删除 |
| `200` | `404` | `流程模板不存在或未启用` | 创建模板版本或发布时模板不存在、停用或已删除 |
| `200` | `404` | `Flowable流程定义不存在` | 发布模板版本时绑定了不存在的 Flowable 定义 Key |
| `200` | `409` | `附件安全扫描未通过` | 附件安全扫描失败 |
| `200` | `409` | `Agent预审任务不匹配` | Agent 回调任务 ID 与申请当前预审任务不一致 |
| `200` | `409` | `Agent预审完成前不应存在Flowable流程实例` | Agent 回调时申请已异常启动 Flowable |
| `200` | `409` | `下载票据已使用或已失效` | 下载票据不是 `ACTIVE`，或被并发请求抢占 |
| `200` | `409` | `流程编码已存在` | 创建流程类型时编码重复 |
| `200` | `409` | `流程模板编码已存在` | 创建流程模板时编码重复 |
| `200` | `409` | `流程模板版本号已存在` | 同一模板下重复创建版本号 |
| `200` | `409` | `只有草稿版本可以发布` | 重复发布或发布非草稿版本 |
| `200` | `410` | `下载链接已过期，请重新申请` | 下载票据超过有效期 |
| `400` | `400` | `补卡类型仅支持 ON_DUTY/OFF_DUTY` | 补卡申请传入不支持的缺卡类型 |
| `400` | `400` | `补卡时间必须落在补卡日期内` | 补卡时间与补卡日期不一致 |
| `404` | `404` | `假种不存在或已停用` | 业务对象不存在 |
| `500` | `500` | `Flowable 未生成直属上级审批任务` | 引擎或流程定义异常 |

---

## 七、当前边界

- 第一版只支持直属上级单节点审批。
- 第一版支持提交时绑定已有个人附件，并克隆为 `WF_REQUEST` 业务引用。
- 第一版支持审批参与人为审批附件创建短期下载票据，并按一次性票据状态机下载原文件。
- 第一版详情返回请假明细和审批申请业务附件。
- 第一版详情返回补卡明细；补卡审批通过后通过 `AttendanceClockCorrectionService` 写入考勤人工补卡事实并重算同日结果。
- 第一版支持后台维护流程类型、流程模板和模板版本草稿，并发布绑定已部署 Flowable 定义。
- 第一版支持 workflow 侧 Agent 预审入口：提交后可进入 `PENDING_REVIEW`，Agent 回调通过或转人工后进入 Flowable，Agent 回调驳回后直接终止。
- Agent 回调必须匹配 `wf_request.agent_pre_review_task_id`，避免错误任务串写申请结果。
- 第一版不支持可视化 BPMN 在线设计；BPMN 文件仍由工程侧部署。
- 第一版已接入 `module-leave`：提交前校验 `DEDUCT` 假种余额，审批通过后扣减余额并写 `lv_account_txn`；驳回、撤回和 Agent 驳回不扣减。
- 第一版不包含完整 `module-agent` 后台创建、任务调度、工具调用和运行监控；当前只完成 workflow 侧 SPI 接入口和回调收口。
- 第一版 Flowable 表仍由开发环境自动创建；生产化时应把 Flowable 初始化 SQL 纳入迁移治理。
