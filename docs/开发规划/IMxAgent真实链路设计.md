# IM x Agent 真实链路设计

## 一、设计目标

当前 IM x Agent 已经能支撑演示：员工在 Agent 会话里发文本消息，`module-im` 触发 `module-agent` 创建任务，Agent 同步生成 mock 回复，再回写 IM 消息并推送 WebSocket。

下一阶段目标是把这条演示链路升级为真实链路：

- Agent 回复由 mock 规则回复切换为真实大模型调用。
- Agent 任务从同步执行改为异步状态机。
- IM 与 Agent 的跨模块字段显式化，避免散参数和隐式约定。
- WebSocket 推送从事务内直接推送改为 outbox 可靠投递。

本设计只覆盖基础对话链路，不覆盖工具调用、RAG、知识库引用和多轮上下文压缩。

---

## 二、链路分层

### 1. module-im 负责

- 校验会话成员和员工状态。
- 保存用户消息。
- 生成 Agent 触发命令。
- 接收 Agent 回复结果或失败结果。
- 写入 Agent 回复消息或失败提示消息。
- 写入 IM 推送 outbox。

### 2. module-agent 负责

- 校验 Agent 是否发布、启用、对当前用户可用。
- 创建会话 Agent 任务。
- 管理任务状态：`PENDING`、`RUNNING`、`SUCCEEDED`、`FAILED`。
- 调用真实模型客户端。
- 归档模型调用失败信息。
- 通过 SPI 回写 IM。

### 3. SPI 负责

SPI 只表达跨模块语义，不暴露内部 Repository、Row、Entity 或 Service 实现。

---

## 三、SPI 命令对象

### 1. 触发命令

`ImAgentTriggerCommand` 必须显式携带：

- `conversationId`：IM 会话 ID。
- `sourceMessageId`：触发 Agent 的用户消息 ID。
- `agentId`：绑定 Agent ID。
- `triggerUserId`：触发员工 ID。
- `triggerSenderType`：触发发送方类型，第一版只接受 `USER`。
- `messageContentJson`：用户消息内容 JSON。
- `clientRequestId`：前端请求 ID，可为空。
- `idempotencyKey`：跨 IM/Agent 的幂等键，建议第一版使用 `im-message-{sourceMessageId}`。
- `metadataJson`：扩展元数据，第一版可为空 JSON。

触发接口返回 `ImAgentTriggerResult`：

- `agentTaskId`
- `status`
- `idempotencyKey`

### 2. 回复结果

`ImAgentReplyResult` 必须显式携带：

- `conversationId`
- `sourceMessageId`
- `agentId`
- `agentTaskId`
- `messageType`
- `contentJson`
- `status`
- `failureType`
- `failureMessage`
- `idempotencyKey`

其中：

- 成功回复：`status=SUCCEEDED`，`messageType/contentJson` 必填。
- 失败回复：`status=FAILED`，`failureType/failureMessage` 必填。
- IM 侧用 `agentTaskId` 和 `idempotencyKey` 生成或校验 Agent 消息幂等键，避免重复回写。

---

## 四、Agent 异步状态机

第一阶段先保持进程内异步执行，不引入独立队列。

状态流转：

1. `module-im` 提交用户消息事务成功后触发 Agent。
2. `module-agent` 创建 `agt_task`，初始状态写入 `PENDING`。
3. Agent 执行器抢占任务，状态改为 `RUNNING`。
4. 模型调用成功，任务改为 `SUCCEEDED`，通过 `ImAgentResultCallback` 回写成功回复。
5. 模型调用失败或超时，任务改为 `FAILED`，通过 `ImAgentResultCallback` 回写失败结果。

失败分类第一版固定为：

- `CONFIG_MISSING`：模型配置缺失。
- `AUTH_FAILED`：模型服务认证失败。
- `TIMEOUT`：模型调用超时。
- `RATE_LIMITED`：模型服务限流。
- `PROVIDER_ERROR`：模型服务 5xx 或错误响应。
- `INVALID_RESPONSE`：模型响应不可解析。
- `UNKNOWN`：其他未分类异常。

---

## 五、模型配置

第一版使用 OpenAI 兼容 Chat Completions 协议，不绑定具体厂商 SDK。

配置项：

- `workforce.agent.model.enabled`
- `workforce.agent.model.base-url`
- `workforce.agent.model.api-key`
- `workforce.agent.model.model`
- `workforce.agent.model.timeout`
- `workforce.agent.model.max-tokens`
- `workforce.agent.model.temperature`

硬规则：

- 代码里不允许 fallback 到 mock 回复。
- 配置缺失时任务必须失败并可观测，不能静默成功。
- API key 不写入普通日志、响应、审计详情。
- 第一版只发送当前用户消息，不做工具调用和 RAG。

---

## 六、IM outbox

`im_event_outbox` 是 IM 推送可靠投递账本，所有 WebSocket 推送先落库。

第一版事件类型：

- `IM_MESSAGE_CREATED`
- `IM_AGENT_MESSAGE_CREATED`
- `IM_AGENT_REPLY_FAILED`

核心字段：

- `id`
- `event_type`
- `conversation_id`
- `message_id`
- `seq_no`
- `recipient_user_ids_json`
- `payload_json`
- `status`
- `retry_count`
- `next_retry_at`
- `last_error`
- `created_at`
- `updated_at`

状态：

- `PENDING`
- `SENDING`
- `SENT`
- `FAILED`
- `DEAD`

业务事务内只写消息和 outbox，不直接依赖 WebSocket 推送成功。dispatcher 负责异步推送、失败重试和最终失败可见。

---

## 七、落地顺序

1. SPI 命令对象化：先改 `ImAgentTriggerService` / `ImAgentResultCallback`，保持现有 mock 行为不变。
2. 服务拆分：拆出 `ImAgentConversationService` 和 `AgentTaskExecutionService`，不新增行为。
3. 真实模型调用：接 OpenAI 兼容 HTTP 客户端，把 mock 回复替换为模型结果。
4. outbox：新增 Flyway 表、写入 outbox、dispatcher 推送、失败重试。
5. 审查与文档：每个节点单独审查，P0/P1 当下修，P2 写入计划。

---

## 八、验证口径

每步提交前至少执行：

```bash
cd server && mvn -DskipTests compile
cd server && env TEST_DB_NAME=<unique_name> mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test
cd server && env TEST_DB_NAME=<unique_name> mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test
```

真实模型节点额外增加模型客户端单元测试或 WireMock 风格 HTTP stub 测试，不依赖真实外部 API key。
