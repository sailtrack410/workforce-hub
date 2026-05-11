# API 测试用例：module-agent 智能体接口

## 1. 测试范围

| 项目 | 说明 |
| --- | --- |
| 测试类 | `ModuleAgentApiIntegrationTest`、`ModuleAgentTaskTimeoutIntegrationTest` |
| 当前覆盖 | 后台创建/列表/详情/停用/版本/工具/绑定/ACL/发布/任务查询/任务工具调用明细/任务审计轨迹/任务运行指标/失败聚合/人工确认超时手动扫描，员工 Agent 列表，任务创建，工具调用，人工确认等待、继续、拒绝、取消，失败任务保留，人工确认超时扫描 |
| 运行前提 | 本地 MySQL、Redis 可用；测试使用 `LocalIntegrationTestSupport` 自动建库、迁移和装载组织种子数据 |
| 数据库变更 | 本轮无新增迁移，测试依赖 V1 已存在 `agt_*` 表 |

---

## 2. 推荐测试命令

### 2.1 单模块测试

```bash
cd server
env TEST_DB_NAME=workforce_hub_agent_metrics_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test
```

预期结果：

- 20 个用例全部通过。
- 空库可执行 V1 到 V11 的 Flyway 迁移。
- `LocalIntegrationTestSupport` 每个用例前清理 `agt_*` 与相关业务表。

### 2.2 编译验证

```bash
cd server
mvn -DskipTests compile
```

### 2.3 跨模块回归建议

```bash
cd server
env TEST_DB_NAME=workforce_hub_agent_metrics_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test
```

该命令用于验证 Agent 对认证、组织和 IM 群成员关系的依赖没有破坏既有模块。

---

## 3. 自动化用例清单

| 编号 | 优先级 | 用例 | 期望 |
| --- | --- | --- | --- |
| AGT-001 | P0 | 后台发布 Agent 并绑定只读组织工具后，授权员工创建任务 | 返回 `SUCCEEDED`，写入 1 条成功工具调用日志和任务审计 |
| AGT-002 | P0 | 员工访问未授权 Agent | 列表为空；创建任务返回 `code=403` |
| AGT-003 | P1 | 员工创建无工具任务时显式传 `toolCalls:null` | 按空工具列表处理，任务 `SUCCEEDED`，工具调用数为 0 |
| AGT-004 | P0 | 调用未绑定工具 | 返回 `code=403`，不创建 `agt_task` |
| AGT-005 | P0 | 业务型工具执行失败 | 返回 `code=0`，任务 `FAILED`，写入失败工具日志和 `TASK_FAILED` 审计 |
| AGT-006 | P0 | 高风险工具绑定为人工确认 | 任务进入 `WAITING_HUMAN_CONFIRM`，工具不执行，日志为等待确认 |
| AGT-007 | P0 | `GROUP` ACL 授权 | 活跃群成员可见并可创建任务；非群成员不可见且不能使用 |
| AGT-008 | P0 | 人工确认继续执行 | 任务进入 `RUNNING` 后执行待确认工具，最终 `SUCCEEDED` |
| AGT-009 | P0 | 人工拒绝继续执行 | 任务进入 `REJECTED`，待确认工具日志为 `REJECTED`，工具不执行 |
| AGT-010 | P0 | 人工取消任务 | 非触发人返回 `code=403`；触发人取消后任务进入 `CANCELLED` |
| AGT-011 | P0 | 待确认工具绑定失效后确认继续 | 返回 `code=409`，任务保持 `WAITING_HUMAN_CONFIRM`，避免误成功 |
| AGT-012 | P0 | 已超过确认截止时间的等待任务被扫描 | 服务返回取消数量 1，任务进入 `CANCELLED`，工具日志为 `TIMEOUT/CANCELLED`，写入 `TASK_TIMEOUT_CANCELLED` |
| AGT-013 | P0 | 未超过确认截止时间的等待任务被扫描 | 服务返回取消数量 0，任务保持 `WAITING_HUMAN_CONFIRM`，工具日志保持 `PENDING/WAITING_CONFIRM` |
| AGT-014 | P0 | 后台查询 Agent 列表、详情并停用已发布 Agent | 列表和详情返回 Agent；停用后状态为 `INACTIVE`；员工不可见且创建任务返回 `code=409` |
| AGT-015 | P0 | 后台手动触发人工确认超时扫描 | `POST /api/admin/agent-tasks/timeout-scan` 返回取消数量 1，过期等待任务进入 `CANCELLED` |
| AGT-016 | P1 | 后台手动触发超时扫描显式传空批次 | `batchSize:null` 按默认批次处理，不返回 500 |
| AGT-017 | P0 | 后台分页筛选并查看 Agent 任务详情 | `GET /api/admin/agent-tasks` 支持按状态、Agent、触发人和上下文过滤，详情接口返回同一任务 |
| AGT-018 | P1 | 后台任务查询非法状态 | `status=UNKNOWN_STATUS` 返回 `code=400`、`message=Agent任务状态不支持` |
| AGT-019 | P0 | 后台查看任务工具调用明细和审计轨迹 | `GET /api/admin/agent-tasks/{taskId}/tool-calls` 返回工具调用明细，`GET /api/admin/agent-tasks/{taskId}/audits` 返回状态审计 |
| AGT-020 | P0 | 后台查看任务运行指标和失败聚合 | `GET /api/admin/agent-tasks/metrics` 返回任务状态和工具调用统计，`GET /api/admin/agent-tasks/failure-summary` 返回失败原因聚合 |

---

## 4. 关键断言

### AGT-001 成功工具调用

- `GET /api/employee/agents` 返回已授权 Agent。
- `POST /api/employee/agents/{agentId}/tasks` 返回：
  - `data.status=SUCCEEDED`
  - `data.toolCallCount=1`
- 数据库断言：
  - `agt_task.status=SUCCEEDED`
  - `agt_tool_call_log.result_status=SUCCESS`
  - `agt_audit_log` 包含 `TASK_CREATED`、`TASK_RUNNING`、`TASK_SUCCEEDED`

### AGT-004 未绑定工具

- 接口返回 `code=403`。
- `agt_task` 数量仍为 0。
- 该用例确认“授权校验发生在任务落库前”，避免产生无意义失败任务。

### AGT-005 失败任务保留

- 接口返回 `code=0`。
- `data.status=FAILED`。
- `data.errorMessage=暂不支持该SPI工具执行`。
- `agt_task.status=FAILED`。
- `agt_tool_call_log.result_status=FAILED`。
- `agt_audit_log` 包含 `TASK_FAILED`。

### AGT-006 人工确认等待

- `approvalMode=REQUIRE_CONFIRM`。
- `agt_task.status=WAITING_HUMAN_CONFIRM`。
- `agt_task.waiting_deadline_at` 应有值。
- `agt_tool_call_log.approval_status=PENDING`。
- `agt_tool_call_log.result_status=WAITING_CONFIRM`。

### AGT-007 群组 ACL

- `agt_agent_acl.subject_type=GROUP`。
- `agt_agent_acl.subject_id` 使用 `im_conversation.id`。
- 员工必须存在活跃 `im_conversation_member` 记录才能看到和使用 Agent。
- 非群成员访问同一 Agent 返回 `code=403`。

### AGT-008 人工确认继续执行

- `POST /api/employee/agent-tasks/{taskId}/confirm` 请求 `approved=true`。
- 任务触发人才能操作该任务。
- `agt_task.status=SUCCEEDED`。
- `agt_tool_call_log.approval_status=APPROVED`。
- `agt_tool_call_log.result_status=SUCCESS`。
- `agt_audit_log` 包含 `TASK_HUMAN_CONFIRMED`、`TASK_RUNNING`、`TASK_SUCCEEDED`。

### AGT-009 人工拒绝继续执行

- `POST /api/employee/agent-tasks/{taskId}/confirm` 请求 `approved=false`。
- `agt_task.status=REJECTED`。
- `agt_tool_call_log.approval_status=REJECTED`。
- `agt_tool_call_log.result_status=REJECTED`。
- 不写入 `TASK_RUNNING` 审计。

### AGT-010 人工取消任务

- 非任务触发人调用取消接口返回 `code=403`。
- 任务触发人调用取消接口后，`agt_task.status=CANCELLED`。
- `agt_tool_call_log.approval_status=CANCELLED`。
- `agt_tool_call_log.result_status=CANCELLED`。
- `agt_audit_log` 包含 `TASK_CANCELLED`。

### AGT-011 待确认工具绑定失效保护

- 人工确认继续执行前，如果待确认工具绑定被停用，接口返回 `code=409`。
- `agt_task.status` 保持 `WAITING_HUMAN_CONFIRM`。
- 待确认工具日志保持 `PENDING/WAITING_CONFIRM`。
- 不写入 `TASK_SUCCEEDED` 审计。

### AGT-012 人工确认超时扫描取消

- 测试类：`ModuleAgentTaskTimeoutIntegrationTest`。
- 构造 `WAITING_HUMAN_CONFIRM` 任务后，把 `agt_task.waiting_deadline_at` 调整为当前时间之前。
- 调用 `AgentTaskTimeoutJobService#cancelExpiredWaitingTasks(10)`。
- 断言返回值为 `1`。
- `agt_task.status=CANCELLED`。
- `agt_task.error_message=人工确认超时，任务已取消`。
- `agt_tool_call_log.approval_status=TIMEOUT`。
- `agt_tool_call_log.result_status=CANCELLED`。
- `agt_tool_call_log.response_payload_json` 包含 `人工确认超时`。
- `agt_audit_log` 包含 `TASK_TIMEOUT_CANCELLED`。

### AGT-013 未超时等待任务保护

- 构造 `WAITING_HUMAN_CONFIRM` 任务后不调整截止时间。
- 调用 `AgentTaskTimeoutJobService#cancelExpiredWaitingTasks(10)`。
- 断言返回值为 `0`。
- `agt_task.status` 保持 `WAITING_HUMAN_CONFIRM`。
- `agt_tool_call_log.approval_status=PENDING`。
- `agt_tool_call_log.result_status=WAITING_CONFIRM`。
- 不写入 `TASK_TIMEOUT_CANCELLED` 审计。

### AGT-014 后台基础治理

- `GET /api/admin/agents?pageNo=1&pageSize=20` 返回分页结构。
- `data.total=1`，`data.list[0].agentCode` 为本次创建的 Agent 编码。
- `GET /api/admin/agents/{agentId}` 返回 Agent 详情。
- `POST /api/admin/agents/{agentId}/disable` 返回 `data.status=INACTIVE`。
- 停用后后台详情仍可查询到 `status=INACTIVE`。
- 停用后员工端 `GET /api/employee/agents` 不再返回该 Agent。
- 停用后员工端 `POST /api/employee/agents/{agentId}/tasks` 返回 `code=409`、`message=Agent未发布或未启用`。

### AGT-015 后台手动超时扫描

- 构造 `WAITING_HUMAN_CONFIRM` 任务后，把 `agt_task.waiting_deadline_at` 调整为当前时间之前。
- 后台调用 `POST /api/admin/agent-tasks/timeout-scan`，请求 `batchSize=10`。
- 接口返回 `data.cancelledCount=1`。
- `agt_task.status=CANCELLED`。
- `agt_task.error_message=人工确认超时，任务已取消`。
- `agt_tool_call_log.approval_status=TIMEOUT`。
- `agt_tool_call_log.result_status=CANCELLED`。
- `agt_audit_log` 包含 `TASK_TIMEOUT_CANCELLED`。

### AGT-016 后台手动超时扫描默认批次

- 后台调用 `POST /api/admin/agent-tasks/timeout-scan`，请求 `batchSize=null`。
- 接口返回 `code=0`，不出现空指针 500。
- 过期等待任务仍可被取消。

### AGT-017 后台任务查询与详情

- 员工创建一个 `SUCCEEDED` Agent 任务。
- 后台调用 `GET /api/admin/agent-tasks`，传入：
  - `status=SUCCEEDED`
  - `agentId`
  - `triggeredById`
  - `contextType=USER_QUERY`
- 列表返回分页结构，`data.total=1`。
- 列表项包含 `taskId`、`agentId`、`agentCode`、`agentName`、`triggeredByType`、`triggeredById`、`toolCallCount`。
- 后台调用 `GET /api/admin/agent-tasks/{taskId}` 返回同一任务详情。
- 使用不匹配状态筛选时返回空列表。

### AGT-018 后台任务查询非法状态

- 后台调用 `GET /api/admin/agent-tasks?status=UNKNOWN_STATUS`。
- 接口返回 `code=400`。
- 错误信息为 `Agent任务状态不支持`。

### AGT-019 后台任务工具调用明细和审计轨迹

- 构造一个 `WAITING_HUMAN_CONFIRM` 任务，再由触发人确认继续执行。
- 后台调用 `GET /api/admin/agent-tasks/{taskId}/tool-calls`。
- 返回 1 条工具调用明细，包含 `toolCode`、`toolName`、`toolType`、`riskLevel`、`approvalStatus`、`resultStatus`、`requestPayloadJson`、`responsePayloadJson` 和 `calledAt`。
- `requestPayloadJson`、`responsePayloadJson` 按 JSON 内容断言，不依赖 MySQL JSON 字符串空格格式。
- 后台调用 `GET /api/admin/agent-tasks/{taskId}/audits`。
- 返回 `TASK_CREATED`、`TASK_WAITING_HUMAN_CONFIRM`、`TASK_HUMAN_CONFIRMED`、`TASK_RUNNING`、`TASK_SUCCEEDED` 五条审计轨迹。
- `TASK_WAITING_HUMAN_CONFIRM` 的 `riskFlag=true`。

### AGT-020 后台任务运行指标和失败聚合

- 构造 1 个成功任务、1 个失败任务和 1 个等待人工确认任务。
- 后台调用 `GET /api/admin/agent-tasks/metrics`。
- 返回 `totalTaskCount=3`、`succeededTaskCount=1`、`failedTaskCount=1`、`waitingHumanConfirmTaskCount=1`。
- 返回 `totalToolCallCount=2`、`failedToolCallCount=1`。
- 后台调用 `GET /api/admin/agent-tasks/failure-summary`。
- 返回 1 条失败原因，`errorMessage=暂不支持该SPI工具执行`、`taskCount=1`、`toolCallCount=1`。
- 该用例同时覆盖 `/metrics`、`/failure-summary` 两个静态路由不会被 `/{taskId}` 误吞。

---

## 5. 手工验证示例

### 5.1 创建并发布组织助手

1. 后台登录：`POST /api/admin/auth/login`
2. 创建工具：`POST /api/admin/agents/tools`
3. 创建 Agent：`POST /api/admin/agents`
4. 后台查询列表：`GET /api/admin/agents`
5. 后台查询详情：`GET /api/admin/agents/{agentId}`
6. 创建版本：`POST /api/admin/agents/{agentId}/versions`
7. 绑定工具：`POST /api/admin/agents/versions/{versionId}/tools`
8. 授权员工或群组：`POST /api/admin/agents/{agentId}/acl`
9. 发布版本：`POST /api/admin/agents/versions/{versionId}/publish`
10. 员工登录后查询：`GET /api/employee/agents`
11. 员工创建任务：`POST /api/employee/agents/{agentId}/tasks`
12. 如任务进入等待确认，员工确认继续或拒绝：`POST /api/employee/agent-tasks/{taskId}/confirm`
13. 如业务上下文终止，员工取消任务：`POST /api/employee/agent-tasks/{taskId}/cancel`
14. 如需要下线 Agent，后台调用：`POST /api/admin/agents/{agentId}/disable`
15. 如任务超过 `waiting_deadline_at` 仍无人处理，后台调用：`POST /api/admin/agent-tasks/timeout-scan`
16. 后台排查任务状态：`GET /api/admin/agent-tasks?status=SUCCEEDED&pageNo=1&pageSize=20`
17. 后台查看任务详情：`GET /api/admin/agent-tasks/{taskId}`
18. 后台查看任务工具调用明细：`GET /api/admin/agent-tasks/{taskId}/tool-calls`
19. 后台查看任务审计轨迹：`GET /api/admin/agent-tasks/{taskId}/audits`
20. 后台查看运行指标：`GET /api/admin/agent-tasks/metrics`
21. 后台查看失败聚合：`GET /api/admin/agent-tasks/failure-summary`

### 5.2 排查 SQL

```sql
SELECT id, agent_code, status, published_version_id
FROM agt_agent;

SELECT task.id, task.status, task.error_message, COUNT(log.id) AS tool_call_count
FROM agt_task task
LEFT JOIN agt_tool_call_log log ON log.task_id = task.id
GROUP BY task.id, task.status, task.error_message;

SELECT *
FROM agt_audit_log
WHERE task_id = ?;
```

---

## 6. 当前未覆盖但后续必须补的测试

- 人工确认超时扫描的定时触发、批次记录和多实例并发。
- 后台 Agent 版本回滚、重新启用、工具查询、ACL 查询和告警入口。
- workflow 预审真实触发和回调。
- IM 会话 Agent 触发和回复回写。
- 知识库绑定空间 ACL。
- 工具参数 Schema 校验和敏感字段脱敏。
