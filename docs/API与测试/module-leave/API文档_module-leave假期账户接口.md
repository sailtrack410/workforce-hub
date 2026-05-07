# API 文档 module-leave 假期账户接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-leave` 假期账户接口 API 文档 |
| 文档版本 | `v0.1` |
| 适用范围 | 假种创建、假期账户余额调整、员工余额查询、请假审批余额联动、请假审批通过联动考勤重算 |
| 对应模块 | `server/module-leave` |
| 发布日期 | `2026-05-07` |
| 当前状态 | 第一版假种、账户、余额流水、workflow 审批通过扣减、审批通过联动考勤日结果重算、已批准请假区间查询已实现；复杂年假规则、自动发放、结转、过期清零待后续迭代 |

---

## 二、接口范围

当前已实现 `2` 个后台接口、`1` 个员工端接口，以及 workflow 内部余额联动能力。

- `POST /api/admin/leave/types`
- `POST /api/admin/leave/accounts/adjustments`
- `GET /api/employee/leave/accounts`

内部联动：

- 员工提交请假申请前校验 `DEDUCT` 假种余额。
- 审批通过后扣减余额并写入 `lv_account_txn`。
- 审批通过后触发考勤侧指定日期范围重算，由考勤按已批准请假区间计算整段 `LEAVE` 或部分分钟抵扣。
- 审批驳回、Agent 驳回、申请撤回不扣减余额。
- `LeaveQueryService` 已由 `LeaveService` 实现，后续 Agent 可通过 SPI 查询假期余额；考勤侧也通过该 SPI 查询指定日期内已批准请假区间。

---

## 三、调用约定

### 3.1 基础地址

```text
http://localhost:8080
```

### 3.2 鉴权方式

当前项目采用服务端 Session 鉴权。

- 后台假期治理接口必须使用管理员会话。
- 员工端余额查询接口必须使用员工会话，只返回当前登录员工自己的假期账户。
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

---

## 四、关键枚举

### 4.1 balanceMode

| 值 | 含义 | 第一版行为 |
| --- | --- | --- |
| `DEDUCT` | 余额扣减假种 | 提交请假前校验余额，审批通过后扣减 |
| `NO_DEDUCT` | 不扣减余额假种 | 提交和审批通过均不扣减余额 |

### 4.2 leaveTypeStatus

| 值 | 含义 |
| --- | --- |
| `ACTIVE` | 启用 |
| `INACTIVE` | 停用 |

### 4.3 accountTxnType

| 值 | 含义 | 当前是否产生 |
| --- | --- | --- |
| `ADJUST` | 管理员调整 | 是 |
| `DEDUCT` | 审批扣减 | 是 |
| `GRANT` | 系统发放 | 否，待后续 |
| `REFUND` | 余额退回 | 否，待后续 |

### 4.4 sourceType

| 值 | 含义 |
| --- | --- |
| `ADMIN` | 后台人工调整 |
| `WORKFLOW_REQUEST` | 审批申请扣减 |

---

## 五、接口详情

## 5.1 创建假种

```http
POST /api/admin/leave/types
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `leaveCode` | `string` | 是 | 假种编码，最多 64 字符，只允许大写字母、数字和下划线 |
| `leaveName` | `string` | 是 | 假种名称，最多 128 字符 |
| `balanceMode` | `string` | 是 | `DEDUCT` 或 `NO_DEDUCT` |
| `status` | `string` | 是 | `ACTIVE` 或 `INACTIVE` |

示例：

```json
{
  "leaveCode": "ANNUAL_LEAVE",
  "leaveName": "年假",
  "balanceMode": "DEDUCT",
  "status": "ACTIVE"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "leaveTypeId": 2052160000000000001,
    "leaveCode": "ANNUAL_LEAVE",
    "leaveName": "年假",
    "balanceMode": "DEDUCT",
    "status": "ACTIVE",
    "createdAt": "2026-05-07T07:00:00",
    "updatedAt": "2026-05-07T07:00:00"
  },
  "requestId": "..."
}
```

### 业务规则

- `leaveCode` 会转为大写后保存。
- 同一未删除假种编码不能重复。
- 第一版只维护假种主数据，不在本接口创建复杂规则。

---

## 5.2 管理员调整员工假期余额

```http
POST /api/admin/leave/accounts/adjustments
Content-Type: application/json
Cookie: SESSION=...
```

### 请求体

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `userId` | `long` | 是 | 员工 ID，必须存在且未离职 |
| `leaveTypeId` | `long` | 是 | 假种 ID，必须启用 |
| `changeMinutes` | `int` | 是 | 调整分钟数，正数增加，负数扣减，不能为 0 |
| `reason` | `string` | 否 | 调整原因，最长 512 字符 |
| `sourceTxnKey` | `string` | 否 | 幂等键，最长 128 字符；相同键重复调用不重复写流水 |

示例：

```json
{
  "userId": 2003,
  "leaveTypeId": 2052160000000000001,
  "changeMinutes": 960,
  "reason": "初始化两天年假",
  "sourceTxnKey": "init-annual-2003-2026"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "accountId": 2052160000000000002,
    "userId": 2003,
    "leaveTypeId": 2052160000000000001,
    "leaveCode": "ANNUAL_LEAVE",
    "leaveName": "年假",
    "balanceMode": "DEDUCT",
    "availableMinutes": 960,
    "frozenMinutes": 0,
    "status": "ACTIVE",
    "lastTxnType": "ADJUST",
    "lastChangeMinutes": 960
  },
  "requestId": "..."
}
```

### 落库语义

- 若账户不存在，自动创建 `lv_account`。
- 更新 `lv_account.available_minutes`。
- 写入 `lv_account_txn`，`txn_type=ADJUST`、`source_type=ADMIN`。
- 若 `sourceTxnKey` 已存在，直接回放已有流水对应账户结果，不重复增加余额。
- 调整后余额不能小于 0。

---

## 5.3 员工查询自己的假期账户

```http
GET /api/employee/leave/accounts
Cookie: SESSION=...
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "accountId": 2052160000000000002,
      "userId": 2003,
      "leaveTypeId": 2052160000000000001,
      "leaveCode": "ANNUAL_LEAVE",
      "leaveName": "年假",
      "balanceMode": "DEDUCT",
      "availableMinutes": 960,
      "frozenMinutes": 0,
      "status": "ACTIVE",
      "lastTxnType": null,
      "lastChangeMinutes": null
    }
  ],
  "requestId": "..."
}
```

### 业务规则

- 仅返回当前登录员工自己的 `ACTIVE` 账户。
- 后台不能通过员工端路径查询他人余额。

---

## 六、workflow 联动规则

### 6.1 提交前余额校验

员工提交 `POST /api/employee/workflow/leave-requests` 时：

- 若假种为 `NO_DEDUCT`，不检查余额。
- 若假种为 `DEDUCT`，读取当前员工该假种 `available_minutes`。
- 当 `available_minutes < durationMinutes` 时返回：

```json
{
  "code": 400,
  "message": "假期余额不足",
  "data": null,
  "requestId": "..."
}
```

### 6.2 审批通过扣减

直属上级审批通过时：

- 读取 `wf_req_leave` 中的 `leave_type_id` 与 `duration_minutes`。
- 再次锁定 `lv_account` 校验余额，防止提交后余额被其他操作消耗。
- 扣减 `lv_account.available_minutes`。
- 写入 `lv_account_txn`：
  - `txn_type=DEDUCT`
  - `change_minutes=-durationMinutes`
  - `source_type=WORKFLOW_REQUEST`
  - `source_id=wf_request.id`
  - `source_txn_key=WORKFLOW_REQUEST:{requestId}`
- 扣减失败时审批不会进入错误终态。
- 扣减成功、审批状态更新为 `APPROVED` 后，同一事务内触发考勤日结果重算。

### 6.3 审批通过联动考勤

直属上级审批通过请假申请后：

- `WorkflowService` 会按 `wf_req_leave.start_at` 到 `wf_req_leave.end_at` 的日期范围逐日触发考勤重算。
- `AttendanceService` 会通过 `LeaveQueryService.listApprovedLeavePeriodsOnDate(userId, workDate)` 查询该员工该日内已批准请假区间。
- 查询只返回与当天 `[00:00, 次日00:00)` 有交集的 `APPROVED` 请假区间，考勤侧再按班次窗口求交集。
- 当已批准请假区间覆盖整段班次时，考勤日结果写为 `LEAVE`，迟到、早退、缺勤、工时归零，并清理该日异常。
- 当已批准请假只覆盖部分时段时，考勤侧按分钟抵扣迟到、早退和缺勤窗口，不会把整天误算为 `LEAVE`。
- 重叠请假区间由考勤侧合并后再计算，避免重复抵扣同一时间段。

### 6.4 不扣减场景

- 直属上级驳回不扣减。
- Agent 预审驳回不扣减。
- 申请人撤回不扣减。

---

## 七、错误码

| HTTP 状态 | `code` | `message` | 场景 |
| --- | --- | --- | --- |
| `200` | `400` | `余额模式仅支持 DEDUCT/NO_DEDUCT` | 创建假种余额模式非法 |
| `200` | `400` | `假种状态仅支持 ACTIVE/INACTIVE` | 创建假种状态非法 |
| `200` | `400` | `调整分钟数不能为0` | 余额调整为 0 |
| `200` | `400` | `假期余额不能调整为负数` | 管理员调整后余额小于 0 |
| `200` | `400` | `假期余额不足` | 提交请假或审批通过扣减时余额不足 |
| `200` | `404` | `员工不存在或不可用` | 调整余额时员工不存在、停用或已离职 |
| `200` | `404` | `假种不存在` | 假种不存在 |
| `200` | `409` | `假种编码已存在` | 创建假种编码重复 |
| `200` | `409` | `假种已停用` | 调整或扣减停用假种 |

---

## 八、当前边界

- 第一版不做复杂年假规则。
- 第一版不做按工龄自动发放。
- 第一版不做结转、过期清零。
- 第一版不做节假日、休息日和工作日历对请假扣减分钟数的精细过滤。
- 第一版不支持跨天班次下的请假分钟级覆盖。
- 第一版不冻结提交中的余额，只在提交前检查，审批通过时再次检查并扣减。
- 第一版保留 `frozen_minutes` 字段，后续可扩展为“提交冻结、撤回/驳回解冻、通过转扣减”。
