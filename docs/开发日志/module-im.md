# module-im 开发日志

## 2026-05-11 IM Agent 真实模型回写与失败幂等

### 背景

Agent 会话任务切换为真实模型异步执行后，IM 侧不能再假设回写会同步完成。成功回复、失败提示和 WebSocket 推送都必须能承受模型执行延迟、失败和重复回调。

### 本轮完成内容

- `ModuleImApiIntegrationTest` 增加本地 JDK HTTP stub，覆盖 OpenAI 兼容 `/v1/chat/completions` 成功回复。
- 员工在 Agent 会话发消息后，IM 列表等待异步模型任务回写真实模型文本，不再断言旧 mock 文案。
- 增加模型配置缺失用例，验证 Agent 任务进入 `FAILED`，IM 会话写入系统提示并推送 `IM_AGENT_REPLY_FAILED`。
- 成功 Agent 回复继续使用 `agent-task-{taskId}` 作为 `client_message_id`，重复成功回写不重复写消息。
- 失败 Agent 回写也使用同一幂等键写入系统提示，重复失败回写不重复生成系统消息。
- Agent 触发阶段失败使用 `agent-trigger-failure-{sourceMessageId}` 作为系统提示幂等键。

### 本轮边界

- 本轮仍保留当前 WebSocket 事务后直接推送方式，可靠 outbox 在下一节点单独实现。
- 失败提示文案仍为统一用户可见文案，不把模型供应商错误原文暴露给员工端。
- 本轮不新增 IM 表结构，复用 V13 的 `im_message.client_message_id` 唯一约束。

### 验证记录

- 编译与测试编译：`cd server && mvn -DskipTests compile test-compile`
  - 结果：通过，302 个主源码和 25 个测试源码编译成功。
- IM/Agent 回归：`cd server && env TEST_DB_NAME=workforce_hub_real_model_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，91 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_real_model_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。

## 2026-05-11 IM Agent 会话服务拆分

### 背景

真实模型异步执行和 WebSocket outbox 进入前，`ImService` 已经同时承担会话列表、消息发送、附件下载、编辑撤回、群治理、Agent 触发、Agent 回写和失败提示。继续直接叠加 outbox 会让事务边界和失败补偿混在主服务里，后续风险会放大。

### 本轮完成内容

- 新增 `ImAgentConversationService`，专门负责 Agent 会话创建、员工消息 afterCommit 触发 Agent、Agent 成功回写、Agent 失败系统提示。
- `ImService.startCurrentEmployeeAgentConversation(...)` 改为委托新服务，员工端接口路径不变。
- `ImService.sendCurrentEmployeeMessage(...)` 发送成功后改为委托新服务触发 Agent 回复，原先只处理 `USER + TEXT + AGENT会话` 的触发规则不变。
- `ImAgentResultCallback` 实现从 `ImService` 移到 `ImAgentConversationService`，跨模块回写入口更贴近 Agent 会话子域。
- 保持 `IM_AGENT_MESSAGE_CREATED` 与 `IM_AGENT_REPLY_FAILED` 现有推送行为不变，outbox 仍留在后续节点。

### 本轮边界

- 本轮只做服务拆分，不新增 Flyway 迁移。
- 本轮不接真实模型，Agent 回复仍由 `module-agent` 当前同步 mock 任务产出。
- 本轮不改员工端 IM API 响应结构，不改 WebSocket 事件结构。

### 验证记录

- 编译：`cd server && mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 296 个源文件编译成功。
- IM/Agent 回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_im_agent mvn -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test`
  - 结果：通过，89 个用例全部通过。
- 基础回归：`cd server && env TEST_DB_NAME=workforce_hub_service_split_base mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`
  - 结果：通过，112 个用例全部通过。

## 2026-05-11 IM x Agent SPI 命令对象化

### 背景

第 75 审后，IM 会话与 Agent 已经能完成演示闭环，但跨模块 SPI 仍使用散参数：IM 触发 Agent 时只传会话、Agent、发送人和消息 JSON，Agent 回写 IM 时只传会话、Agent、任务和消息内容。真实模型异步执行后，`sourceMessageId`、`agentTaskId`、`clientRequestId`、`idempotencyKey`、失败类型等字段必须显式存在，否则后续 outbox、重试和失败可见性会继续靠隐式约定。

### 本轮完成内容

- 新增 `ImAgentTriggerCommand`，作为 IM 触发 Agent 的命令对象。
- 新增 `ImAgentTriggerResult`，作为 Agent 任务触发结果。
- 新增 `ImAgentReplyResult`，作为 Agent 回写 IM 的结果对象。
- `ImAgentTriggerService.triggerConversationReply` 从散参数改为接收命令对象。
- `ImAgentResultCallback` 从 `sendAgentMessage(...)` 改为 `onAgentReply(ImAgentReplyResult result)`。
- `ImService` 触发 Agent 时显式传入 `sourceMessageId` 和 `idempotencyKey=im-message-{sourceMessageId}`。
- `ImService` 接收 Agent 成功回写时继续复用 `agent-task-{taskId}` 消息幂等键，保持重复回写不重复生成消息。
- `ImService` 预留 `FAILED` 回写分支，失败结果会写入 Agent 失败系统提示消息。

### 本轮边界

- 本轮不接真实模型，仍保持现有同步 mock 回复行为。
- 本轮不新增 outbox 表，WebSocket 推送可靠投递在后续节点单独做。
- 本轮不拆 `ImService`，只调整跨模块契约，服务拆分在下一节点做。
- 本轮不新增 Flyway 迁移。

### 验证记录

- 编译：`mvn -DskipTests compile`
  - 结果：通过，JDK 17.0.18 下 294 个源文件编译成功。

### 后续待补

- 拆出 `ImAgentConversationService`，把 Agent 触发、回写、失败提示从 `ImService` 主类中剥离。
- 接入真实大模型后，`FAILED` 回写应携带 `failureType` 和 `failureMessage`。
- outbox 节点把 `IM_AGENT_MESSAGE_CREATED` 与 `IM_AGENT_REPLY_FAILED` 改为先写账本再异步推送。
