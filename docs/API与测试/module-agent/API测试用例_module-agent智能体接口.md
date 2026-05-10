# API 测试用例：module-agent 智能体接口

## 1. 测试范围

| 项目 | 说明 |
| --- | --- |
| 测试类 | `ModuleAgentApiIntegrationTest` |
| 当前覆盖 | 后台创建/版本/工具/绑定/ACL/发布，员工 Agent 列表，任务创建，工具调用，人工确认等待，失败任务保留 |
| 运行前提 | 本地 MySQL、Redis 可用；测试使用 `LocalIntegrationTestSupport` 自动建库、迁移和装载组织种子数据 |
| 数据库变更 | 本轮无新增迁移，测试依赖 V1 已存在 `agt_*` 表 |

---

## 2. 推荐测试命令

### 2.1 单模块测试

```bash
cd server
env TEST_DB_NAME=workforce_hub_agent_full_green2 mvn -Dtest=ModuleAgentApiIntegrationTest test
```

预期结果：

- 7 个用例全部通过。
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
env TEST_DB_NAME=workforce_hub_agent_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest test
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

---

## 5. 手工验证示例

### 5.1 创建并发布组织助手

1. 后台登录：`POST /api/admin/auth/login`
2. 创建工具：`POST /api/admin/agents/tools`
3. 创建 Agent：`POST /api/admin/agents`
4. 创建版本：`POST /api/admin/agents/{agentId}/versions`
5. 绑定工具：`POST /api/admin/agents/versions/{versionId}/tools`
6. 授权员工或群组：`POST /api/admin/agents/{agentId}/acl`
7. 发布版本：`POST /api/admin/agents/versions/{versionId}/publish`
8. 员工登录后查询：`GET /api/employee/agents`
9. 员工创建任务：`POST /api/employee/agents/{agentId}/tasks`

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

- 人工确认继续执行、人工驳回、超时取消。
- 后台 Agent 列表、详情、停用、版本回滚。
- workflow 预审真实触发和回调。
- IM 会话 Agent 触发和回复回写。
- 知识库绑定空间 ACL。
- 工具参数 Schema 校验和敏感字段脱敏。
