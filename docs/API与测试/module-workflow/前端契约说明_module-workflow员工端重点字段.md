# 前端契约说明 module-workflow 员工端重点字段

## 一、目的

这份说明只补 employee-web 当前最容易打错的字段，不重复完整 API 文档。

---

## 二、补卡提交字段

接口：

```text
POST /api/employee/workflow/clock-correction-requests
```

当前后端真实请求体：

```json
{
  "targetDate": "2026-05-16",
  "missingPunchType": "OFF_DUTY",
  "expectedPunchAt": "2026-05-16 18:00:00",
  "reason": "下班时网络异常，补下班卡",
  "attachmentRefIds": []
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `targetDate` | `string` | 补卡日期，格式 `yyyy-MM-dd` |
| `missingPunchType` | `string` | 缺卡类型，仅支持 `ON_DUTY`、`OFF_DUTY` |
| `expectedPunchAt` | `string` | 申请补写的打卡时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `reason` | `string` | 补卡原因 |
| `attachmentRefIds` | `number[]` | 可选附件 |

不要再使用旧字段：

- `workDate`
- `correctionType`
- `correctedAt`

---

## 三、审批动作字段

### 1. 通过

```text
POST /api/employee/workflow/tasks/{taskId}/approve
```

请求体：

```json
{
  "comment": "同意"
}
```

### 2. 驳回

```text
POST /api/employee/workflow/tasks/{taskId}/reject
```

请求体：

```json
{
  "comment": "理由不充分"
}
```

### 3. 撤回

```text
POST /api/employee/workflow/requests/{requestId}/withdraw
```

请求体：

```json
{
  "comment": "暂时撤回补充材料"
}
```

注意：

- 撤回接口不是 `reason`
- 后端当前 DTO 字段是 `comment`

---

## 四、提交结果字段

员工提交请假或补卡后的返回结构：

```json
{
  "requestId": 2050000000000000001,
  "requestNo": "WF-LEAVE-20260512093000-2050000000000000000",
  "status": "IN_APPROVAL",
  "flowableProcessInstanceId": "xxx",
  "agentTaskId": null
}
```

说明：

- `status=IN_APPROVAL`：直接进入人工审批
- `status=PENDING_REVIEW`：先进入 Agent 预审
- `agentTaskId` 只有走 Agent 预审时才有值
