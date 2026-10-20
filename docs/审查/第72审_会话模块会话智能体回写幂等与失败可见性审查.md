# 第 72 审 module-im 会话 Agent 回写幂等与失败可见性审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（第 71 审后续修复：Agent 结果回写幂等、Agent 触发失败系统消息与失败推送）
- 审查范围：`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/审查/README.md`
- 审查目标：复核第 71 审指出的“Agent 回写重复投递”和“Agent 触发失败无会话可见反馈”是否收口；本轮不审查真实大模型执行、RAG、outbox 可靠投递和前端页面交互。

---

## 第一部分：提交内容概览

本轮是第 71 审会话 Agent 后端闭环的风险收口，不扩大新的产品能力。

- `ImService.sendAgentMessage(...)` 为 Agent 回写统一生成 `client_message_id='agent-task-{agentTaskId}'`。
- Agent callback 写入前先按 `conversationId + senderType + agentId + clientMessageId` 查询既有消息；重复投递直接返回，不再插入重复消息。
- Agent callback 插入时捕获 `DuplicateKeyException`；并发重复投递命中唯一约束时按幂等成功处理。
- `triggerAgentReplyAfterCommitIfNeeded(...)` 捕获 afterCommit 阶段 Agent 触发异常，避免员工消息已发送但后端异常向上破坏请求语义。
- `writeAgentFailureSystemMessage(...)` 在独立事务中写入 `sender_type='SYSTEM'`、`message_type='SYSTEM'` 的失败提示消息：`Agent 暂时无法回复，请稍后再试`。
- 失败提示写入后直接向会话活跃成员推送 `IM_AGENT_REPLY_FAILED`，前端可在演示时实时看到失败反馈。
- `ModuleImApiIntegrationTest.repeatedAgentResultCallbackShouldReuseExistingMessage` 覆盖重复 Agent callback 不产生重复消息。
- `ModuleImApiIntegrationTest.agentTriggerFailureShouldWriteSystemMessage` 覆盖 Agent 并发失效后，员工消息成功、系统失败提示落库、失败事件推送。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImService.sendAgentMessage(...)`、`im_message.uk_im_message_client_message` | 第 71 审 3-3 指出 Agent 结果 callback 重复投递时会撞唯一键，可能让后续重试或补偿被误判失败。当前写入前查询既有 `client_message_id`，写入时再捕获唯一键冲突，重复回调不会增加消息，也不会推进 `last_message_seq`。 | 已处理 | 后续接 outbox 或异步模型任务时继续复用 `agent-task-{taskId}` 作为业务幂等键，不另造弱幂等字段。 |
| 2-2 | `ImService.triggerAgentReplyAfterCommitIfNeeded(...)`、`writeAgentFailureSystemMessage(...)` | 第 71 审 3-2 指出 Agent 触发失败只记录日志，用户会看到自己消息成功但会话没有任何反馈。当前 afterCommit 触发异常会写系统消息并推送 `IM_AGENT_REPLY_FAILED`，前端可直接展示失败提示。 | 已处理 | 后续如果改为 outbox，应把失败提示也纳入可靠事件账本，避免直接推送和数据库提交之间再次出现不可观测缺口。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `writeAgentFailureSystemMessage(...)` | 失败提示消息没有携带源用户消息 ID 或 Agent 任务 ID。触发条件是同一会话连续多条消息触发 Agent，其中多次失败；失败路径是前端只能知道会话发生过失败，但不能精确把失败提示绑定到哪条用户消息。当前测试只验证单条消息失败场景。 | P2 | 在系统消息 `content_json` 中补充 `sourceMessageId`、`agentId`、`failureType`，并在 WebSocket payload 中保持一致，方便前端定位失败气泡。 |
| 3-2 | `writeAgentFailureSystemMessage(...)` | 失败系统消息没有幂等键。触发条件是 afterCommit 触发逻辑未来改为可重试，或同一条用户消息因异常恢复被重复触发；失败路径是会话中可能出现多条相同失败提示，虽然不破坏数据账本，但会影响演示和用户体验。现有测试不覆盖同一源消息重复失败。 | P2 | 为失败提示引入 `client_message_id='agent-failed-{sourceMessageId}'`，并按该幂等键查询或捕获唯一键冲突。 |
| 3-3 | `webSocketPushService.pushToUsers(...)` 直接调用 | 为了让 afterCommit 失败路径能立即推送，本轮在独立事务内直接推送失败事件。触发条件是系统消息已插入但推送失败，或推送成功后事务提交异常；失败路径是数据库状态和实时事件仍可能短暂不一致。当前测试只覆盖内存 WebSocket session 成功路径。 | P2 | 在进入生产化前补 `im_event_outbox` 或统一消息推送账本，所有 `IM_MESSAGE_CREATED`、`IM_AGENT_MESSAGE_CREATED`、`IM_AGENT_REPLY_FAILED` 都从已提交事件可靠投递。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | `ImService` 已继续扩张到普通消息、附件、编辑撤回、群治理、实时推送、Agent 会话、Agent callback 和失败补偿。触发条件是继续补组织群同步、outbox、会话详情、已读位点扩展；失败路径是所有逻辑挤在单类中，后续修复容易互相影响。 | P2 | 在下一个大功能前拆出 `ImAgentConversationService` 和 `ImMessageWriteService`，先把 Agent 触发、失败提示、幂等回写从普通发送路径里拆开。 |
| 4-2 | `ModuleImApiIntegrationTest` | `ModuleImApiIntegrationTest` 已覆盖大量 IM 能力，新增 Agent 幂等和失败路径后单类继续变长。触发条件是后续继续加入 outbox、会话详情、组织群同步测试；失败路径是测试定位成本上升，失败时难以快速判断属于哪条子链路。 | P2 | 拆分为 `ModuleImConversationIntegrationTest`、`ModuleImMessageIntegrationTest`、`ModuleImAgentConversationIntegrationTest`、`ModuleImAttachmentIntegrationTest`。 |
| 4-3 | `ImAgentResultCallback` | callback 方法仍是散参数签名，幂等键、源消息、失败原因等上下文靠实现方拼接。触发条件是接真实异步模型或补偿任务后，结果回写字段继续增加；失败路径是 SPI 调用方和接收方对字段语义产生隐式约定。 | P2 | 引入 `ImAgentReplyCommand` 或 `ImAgentReplyResult` 对象，显式携带 `agentTaskId`、`sourceMessageId`、`messageType`、`contentJson`、`idempotencyKey`。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只追加第 72 审文档和 README 条目，并复核 IM/Agent 相关增量。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 71 审，本轮新增第 72 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM + Agent 目标测试 | `env TEST_DB_NAME=workforce_hub_node72_im_agent_resilience2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleAgentApiIntegrationTest test` | 通过 | 覆盖 Agent 回写幂等、触发失败系统消息、失败事件推送和 Agent 原有任务用例。 |
| 失败事件回归 | `env TEST_DB_NAME=workforce_hub_node72_im_agent_resilience mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleAgentApiIntegrationTest test` | 曾失败 | 首次实现只注册 afterCommit 推送，失败系统消息独立事务提交后没有触发事件；随后改为直接 `pushToUsers(...)` 并通过目标测试。 |

未验证项：

- 没有跑全仓 Maven 测试，本轮以 IM/Agent 目标测试为主。
- 没有跑真实浏览器和真实 WebSocket 握手测试，当前实时推送验证仍使用测试内存 session。
- 没有验证 outbox、断线补偿、消息事件重放和真实网络抖动。
- 没有验证真实模型、RAG、工具编排的异步 Agent 执行。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `writeAgentFailureSystemMessage(...)` | 失败消息缺少源消息 ID，连续失败时前端不能精确定位。 | P2 | 下一轮补 `sourceMessageId` 并同步调整测试断言。 |
| 6-2 | IM 事件推送链路 | 当前实时事件仍是直接推送，没有可重试账本。 | P2 | 在演示后优先做 `im_event_outbox`，统一消息、已读、Agent 回复、Agent 失败事件。 |
| 6-3 | `ImService` | 服务类职责继续膨胀。 | P2 | 先拆 Agent 会话服务，再补组织群同步或真实 Agent 执行，避免继续在单类上叠补丁。 |

## 第七部分：阶段性结论

第 71 审指出的两个演示可见风险已经收口：Agent 结果重复回写不会产生重复消息；Agent 触发失败时，会话里会出现系统提示并推送失败事件。当前没有新增 P0/P1 阻断项，后端演示链路比第 71 节点更稳。

需要明确的是，本轮仍然不是生产级可靠消息投递闭环。失败提示没有源消息幂等键，WebSocket 推送也没有 outbox 兜底；这些不阻断当前前端演示，但应作为进入真实试用前的优先治理项。
