# API 文档：module-agent 智能体接口

## 1. 文档范围

| 项目 | 说明 |
| --- | --- |
| 模块 | `module-agent` |
| 当前状态 | 第一批后端闭环已实现：后台 Agent 治理、版本、工具、ACL、发布，员工端 Agent 列表与任务创建 |
| 认证方式 | 复用 Spring Session，后台接口必须使用管理员会话，员工接口必须使用员工会话 |
| 响应格式 | 统一 `R<T>`，`code=0` 表示接口调用成功，业务失败使用非 0 code |
| 数据库变更 | 本轮无新增迁移，使用 `V1__init_schema.sql` 已存在的 `agt_*` 表 |

当前第一批不是完整 Agent 平台，暂不包含任务列表、人工确认继续执行、会话 Agent 结果回写、workflow 真实触发器、定时任务和后台运行监控。

---

## 2. 关键规则

### 2.1 Agent 发布规则

- Agent 创建后初始状态为 `DRAFT`。
- Agent 版本创建后初始状态为 `DRAFT`。
- 只有 `DRAFT` 版本允许绑定工具。
- 发布版本后：
  - 当前版本进入 `PUBLISHED`。
  - 同 Agent 旧发布版本归档为 `ARCHIVED`。
  - Agent 主表写入 `published_version_id` 并进入 `ACTIVE`。

### 2.2 ACL 规则

| subjectType | subjectId 语义 | 当前是否生效 |
| --- | --- | --- |
| `USER` | `org_user.id` | 是 |
| `DEPARTMENT` | `org_department.id`，按员工 `primary_dept_id` 判断 | 是 |
| `GROUP` | `im_conversation.id`，仅活跃群聊和活跃成员生效 | 是 |
| `ADMIN_USER` | `admin_user.id` | 可写入，后台委托管理暂未接入 |

| permCode | 当前语义 |
| --- | --- |
| `visible` | 员工可在 Agent 列表看到 |
| `use` | 员工可看到并创建任务 |
| `manage` | 预留给后续委托管理；当前员工端按可用权限处理，后台接口仍以管理员会话为准 |

### 2.3 工具调用规则

- 未绑定到当前发布版本的工具不能调用，任务创建前返回 `403`，不写 `agt_task`。
- `approvalMode=AUTO` 的 `SPI` 工具会同步执行。
- `approvalMode=REQUIRE_CONFIRM` 的工具会让任务进入 `WAITING_HUMAN_CONFIRM`，不执行工具。
- `CONNECTOR` 工具第一批只允许定义和绑定；如果配置为 `AUTO` 执行，会生成 `FAILED` 任务并返回 `连接器工具执行器尚未接入`。
- 业务型工具执行失败时，接口仍返回 `code=0`，任务状态为 `FAILED`，并写入 `agt_tool_call_log` 和 `agt_audit_log`。

---

## 3. 后台接口

### 3.1 创建 Agent

`POST /api/admin/agents`

请求：

```json
{
  "agentCode": "ORG_HELPER",
  "agentName": "组织助手",
  "agentType": "QA",
  "identityMode": "SYSTEM_IDENTITY"
}
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "agentId": 2053519959787827201,
    "agentCode": "ORG_HELPER",
    "agentName": "组织助手",
    "agentType": "QA",
    "identityMode": "SYSTEM_IDENTITY",
    "publishedVersionId": null,
    "status": "DRAFT"
  },
  "requestId": "..."
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `agentCode` | 是 | 最大 64 字符，服务端转大写，唯一 |
| `agentName` | 是 | 最大 128 字符 |
| `agentType` | 是 | `QA`、`PRE_REVIEW`、`REMINDER`、`AUTOMATION` |
| `identityMode` | 否 | 默认 `SYSTEM_IDENTITY`，也支持 `USER_DELEGATED` |

### 3.2 创建 Agent 版本

`POST /api/admin/agents/{agentId}/versions`

请求：

```json
{
  "versionNo": 1,
  "modelConfigJson": "{\"provider\":\"mock\",\"model\":\"agent-test\"}",
  "systemPrompt": "你是企业协同助手。",
  "workflowConfigJson": "{\"mode\":\"tool-first\"}"
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `versionNo` | 是 | 最小为 1，同一 Agent 下唯一 |
| `modelConfigJson` | 否 | 必须是合法 JSON 字符串 |
| `systemPrompt` | 否 | 最大 8192 字符 |
| `workflowConfigJson` | 否 | 必须是合法 JSON 字符串 |

### 3.3 创建工具

`POST /api/admin/agents/tools`

请求：

```json
{
  "toolCode": "ORG_GET_USER_SUMMARY",
  "toolName": "组织用户摘要",
  "toolType": "SPI",
  "riskLevel": "READONLY",
  "readOnlyFlag": true,
  "status": "ACTIVE"
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `toolCode` | 是 | 最大 64 字符，服务端转大写，唯一 |
| `toolName` | 是 | 最大 128 字符 |
| `toolType` | 是 | `SPI`、`CONNECTOR` |
| `connectorId` | 否 | 连接器 ID，第一批暂不执行连接器 |
| `riskLevel` | 是 | `READONLY`、`LOW`、`MEDIUM`、`HIGH` |
| `readOnlyFlag` | 否 | 是否只读 |
| `status` | 否 | 默认 `ACTIVE` |

### 3.4 绑定版本工具

`POST /api/admin/agents/versions/{versionId}/tools`

请求：

```json
{
  "toolId": 2053519959000000001,
  "approvalMode": "AUTO",
  "argSchemaJson": "{\"type\":\"object\"}",
  "riskControlJson": "{\"maxCalls\":3}",
  "status": "ACTIVE"
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `toolId` | 是 | 已存在且启用的工具 ID |
| `approvalMode` | 否 | 默认 `AUTO`，支持 `AUTO`、`REQUIRE_CONFIRM` |
| `argSchemaJson` | 否 | 合法 JSON 字符串 |
| `riskControlJson` | 否 | 合法 JSON 字符串 |
| `status` | 否 | 默认 `ACTIVE` |

### 3.5 授予 Agent ACL

`POST /api/admin/agents/{agentId}/acl`

请求：

```json
{
  "subjectType": "GROUP",
  "subjectId": 7301,
  "permCode": "use",
  "status": "ACTIVE"
}
```

说明：

- `GROUP` 的 `subjectId` 使用群聊 `im_conversation.id`。
- `GROUP` ACL 只对 `im_conversation.status=ACTIVE`、`im_conversation.conversation_type=GROUP`、`im_group_profile.status=ACTIVE` 且 `im_conversation_member.status=ACTIVE` 的成员生效。

### 3.6 发布 Agent 版本

`POST /api/admin/agents/versions/{versionId}/publish`

响应中的 `data.publishedVersionId` 应等于本次发布版本 ID，`data.status` 应为 `ACTIVE`。

---

## 4. 员工端接口

### 4.1 查询当前员工可用 Agent

`GET /api/employee/agents`

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "agentId": 2053519959787827201,
      "agentCode": "ORG_HELPER",
      "agentName": "组织助手",
      "agentType": "QA",
      "identityMode": "SYSTEM_IDENTITY",
      "publishedVersionId": 2053519959880101889,
      "status": "ACTIVE"
    }
  ],
  "requestId": "..."
}
```

### 4.2 创建 Agent 任务

`POST /api/employee/agents/{agentId}/tasks`

请求：

```json
{
  "triggerType": "MANUAL",
  "contextType": "USER_QUERY",
  "contextId": 2003,
  "inputPayloadJson": "{\"query\":\"查询张三组织信息\"}",
  "toolCalls": [
    {
      "toolCode": "ORG_GET_USER_SUMMARY",
      "requestPayloadJson": "{\"userId\":2003}"
    }
  ]
}
```

响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "taskId": 2053520418988605442,
    "agentId": 2053519959787827201,
    "agentVersionId": 2053519959880101889,
    "triggerType": "MANUAL",
    "contextType": "USER_QUERY",
    "contextId": 2003,
    "status": "SUCCEEDED",
    "toolCallCount": 1,
    "waitingDeadlineAt": null,
    "startedAt": "2026-05-11T01:01:23",
    "finishedAt": "2026-05-11T01:01:23",
    "errorMessage": null
  },
  "requestId": "..."
}
```

字段约束：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `triggerType` | 是 | `MANUAL`、`CONVERSATION`、`WORKFLOW`、`TIMER`、`EVENT` |
| `contextType` | 否 | 最大 64 字符 |
| `contextId` | 否 | 业务上下文 ID |
| `inputPayloadJson` | 否 | 合法 JSON 字符串 |
| `toolCalls` | 否 | 最大 10 个；省略或传 `null` 均按空列表处理 |
| `toolCalls[].toolCode` | 是 | 工具编码 |
| `toolCalls[].requestPayloadJson` | 否 | 合法 JSON 字符串 |

---

## 5. 当前内置 SPI 工具编码

| toolCode | 调用 SPI | 请求参数 |
| --- | --- | --- |
| `ORG_GET_USER_SUMMARY` | `OrgQueryService#getUserSummary` | `userId` |
| `ORG_GET_DEPT_SUMMARY` | `OrgQueryService#getDeptSummary` | `deptId` |
| `ORG_GET_MANAGER_USER_ID` | `OrgQueryService#getManagerUserId` | `userId` |
| `LEAVE_GET_BALANCES` | `LeaveQueryService#getLeaveBalances` | `userId` |
| `LEAVE_GET_AVAILABLE_MINUTES` | `LeaveQueryService#getAvailableMinutes` | `userId`、`leaveTypeId` |
| `ATTENDANCE_GET_DAILY_RESULT` | `AttendanceQueryService#getDailyResult` | `userId`、`workDate` |
| `ATTENDANCE_GET_MONTHLY_EXCEPTIONS` | `AttendanceQueryService#getMonthlyExceptions` | `userId`、`yearMonth` |
| `KNOWLEDGE_SEARCH_CHUNKS` | `KnowledgeSearchService#searchChunks` | `spaceId`、`query`、`topK` |

---

## 6. 错误码与任务状态

| 场景 | HTTP | `code` | 说明 |
| --- | --- | --- | --- |
| 未登录 | 200/401 | 401 | 取决于安全链路拦截点 |
| 员工无权使用 Agent | 200 | 403 | 不创建任务 |
| Agent 未发布或未启用 | 200 | 409 | 不创建任务 |
| 工具未绑定当前版本 | 200 | 403 | 不创建任务 |
| 输入 JSON 不合法 | 200 | 400 | 不创建任务 |
| 业务型工具执行失败 | 200 | 0 | 创建任务，任务状态为 `FAILED` |
| 高风险/需确认工具 | 200 | 0 | 创建任务，任务状态为 `WAITING_HUMAN_CONFIRM` |

---

## 7. 后续扩展点

- 增加后台 Agent 列表、详情、停用、回滚和任务查询接口。
- 增加人工确认继续执行/驳回接口。
- 接入 workflow 预审触发器与回调。
- 接入 IM 会话 Agent 触发与回复回写。
- 接入任务异步执行、重试、超时扫描、运行指标和告警。
- 接入工具参数 Schema 校验、敏感数据脱敏和更细的风险控制策略。
