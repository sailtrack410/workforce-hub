# 第 71 审 module-im 会话 Agent 入口后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im Agent 会话入口、会话消息触发 Agent 任务、Agent 结果回写 IM）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/main/java/com/workforcehub/agent/service/AgentService.java`、`server/src/main/java/com/workforcehub/spi/`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/审查/README.md`
- 审查目标：复核规划中“私聊、群聊、Agent 会话三类入口闭环”和“会话 Agent 结果能回写 module-im”是否形成演示可用后端闭环；本轮不审查真实大模型调用、RAG 检索和前端页面接入。

---

## 第一部分：提交内容概览

本轮把 `module-im` 与 `module-agent` 的会话主链路接通。

- 新增员工端 Agent 会话入口：`POST /api/employee/im/agent-conversations`。
- 新增请求 DTO：`StartAgentConversationRequest`，要求传入 `agentId`。
- `ImAgentTriggerService` 增加 `requireUsableAgent(...)`，供 IM 创建会话前复用 Agent 发布态和 ACL 校验。
- 新增 `ImAgentDescriptor`，只暴露 IM 创建会话所需的 Agent ID、编码和名称。
- `ImRepository` 支持创建 `conversation_type='AGENT'` 会话、写入 `USER` + `AGENT` 两类成员、按会话查询 `bound_agent_id`。
- `ImService.startCurrentEmployeeAgentConversation(...)` 按 `AGENT:{userId}:{agentId}` 创建或复用单人 Agent 会话。
- 员工在 Agent 会话发送文本消息后，`ImService` 在用户消息事务提交后触发 `ImAgentTriggerService.triggerConversationReply(...)`。
- `AgentService` 实现 `ImAgentTriggerService`，创建 `trigger_type='CONVERSATION'`、`context_type='IM_CONVERSATION'` 的 Agent 任务，并在成功后调用 `ImAgentResultCallback`。
- `ImService` 实现 `ImAgentResultCallback`，以 `sender_type='AGENT'` 写回同一会话并推送 `IM_AGENT_MESSAGE_CREATED` WebSocket 事件。
- `ModuleImApiIntegrationTest.authorizedEmployeeShouldStartAgentConversationAndReceiveAgentReply` 覆盖授权员工创建 Agent 会话、重复创建复用、发送消息、生成 Agent 任务、写回 Agent 回复和 WebSocket 推送。
- `ModuleImApiIntegrationTest.unauthorizedEmployeeShouldNotStartAgentConversation` 覆盖未授权员工不能创建 Agent 会话。

当前后端已经能支撑前端演示最小链路：员工选择可用 Agent，创建会话，发一条文本消息，随后在同一会话看到 Agent 回复。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `docs/开发规划/开发流程总览.md`、`EmployeeImController`、`ImService` | 规划第 4 节要求“私聊、群聊、Agent 会话三类入口闭环”，此前后端只有私聊和群聊入口。当前新增 `/api/employee/im/agent-conversations`，会话表写入 `AGENT` 类型并绑定 `bound_agent_id`。 | 已处理 | 前端可直接按该接口接入 Agent 会话创建，不需要绕过 IM 去调用 Agent 任务接口。 |
| 2-2 | `server/src/main/java/com/workforcehub/spi/ImAgentTriggerService.java`、`AgentService` | 规划第 5 节要求“会话触发”，此前 SPI 存在但没有实现类。当前 `AgentService` 实现触发接口，创建 `CONVERSATION` 任务并写审计。 | 已处理 | 后续真实模型或工具编排应继续复用这一任务入口，不另建旁路任务模型。 |
| 2-3 | `server/src/main/java/com/workforcehub/spi/ImAgentResultCallback.java`、`ImService.sendAgentMessage(...)` | 规划大测试要求“会话 Agent 结果能回写 module-im”，此前 callback 接口存在但没有回写实现。当前 IM 实现 callback，按会话序号写入 `sender_type='AGENT'` 消息并推送实时事件。 | 已处理 | 后续补 outbox 时，把该回写消息也纳入可靠投递账本。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AgentService.createConversationReplyTask(...)` | 当前会话 Agent 的回复文本是后端同步生成的演示级回复，不是真实大模型推理结果，也没有执行已绑定工具。触发条件是前端在 Agent 会话中发送任意文本；失败路径是业务方误以为 `SUCCEEDED` 代表真实 AI 能力已完成，影响范围是演示后进入真实试用时的能力预期。现有测试只验证闭环，不验证语义质量。 | P2 | 在 API 文档或开发日志里明确“当前是 mock/规则回复闭环”；接真实模型前把 Agent 运行器拆成独立执行服务，并让 `CONVERSATION` 任务走真实模型或工具编排。 |
| 3-2 | `ImService.triggerAgentReplyAfterCommitIfNeeded(...)` | 用户消息提交后，Agent 触发发生在 afterCommit 回调里，触发异常只记录日志，不会回写一条系统失败消息。触发条件是 Agent 服务异常、ACL 被并发撤销、IM callback 缺失或数据库短暂失败；结果是用户消息发送成功但会话里没有可见失败原因。现有测试覆盖成功路径和未授权创建，不覆盖发送后触发失败的可观测性。 | P2 | 增加失败系统消息或任务失败状态查询入口；至少在会话里写入“Agent 暂不可用”系统消息，避免前端只看到沉默。 |
| 3-3 | `ImService.sendAgentMessage(...)`、`im_message.uk_im_message_client_message` | Agent 回写使用 `client_message_id='agent-task-{taskId}'` 具备数据库唯一约束，但当前 callback 重复投递时没有查询既有消息并幂等返回。触发条件是后续引入重试、outbox 或手动补偿时重复回调同一 `agentTaskId`；失败路径是第二次写入触发唯一键冲突，任务补偿可能被误判失败。 | P2 | 参考员工消息 `clientMessageId` 逻辑，先按 `conversationId + AGENT + agentId + clientMessageId` 查询既有消息；重复回调直接返回或刷新会话最后消息，不抛异常。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | `ImService` 已同时承担会话列表、私聊、群聊治理、附件下载、消息编辑撤回、WebSocket 推送、Agent 会话创建和 Agent 回写。继续扩展组织事件同步、outbox、群主转让和真实 Agent 运行后，该类会成为新的补丁堆积点。 | P2 | 下一轮功能前拆出 `ImConversationService`、`ImMessageService`、`ImGroupService`、`ImAgentConversationService`，把 callback 回写和普通员工发送分开。 |
| 4-2 | `AgentService` | `AgentService` 在已有后台治理、任务创建、人工确认、任务查询、指标聚合基础上又接入 IM 会话触发。虽然本轮复用状态机有利于闭环，但服务职责继续变宽。 | P2 | 抽 `AgentConversationService` 或 `AgentTaskExecutionService`，让 `AgentService` 只保留编排门面，避免 workflow/IM/timer/event 触发逻辑全部压入一个类。 |
| 4-3 | `ImAgentTriggerService` 与 `ImAgentResultCallback` | 两个 SPI 当前只表达同步触发和同步回写，缺少事件 ID、幂等键、失败原因、重试次数等生产化字段。演示闭环足够，但后续异步模型调用、outbox 和补偿任务会被迫在方法签名外补上下文。 | P2 | 在接真实模型前扩展为命令对象和结果对象，例如 `ImAgentTriggerCommand`、`ImAgentReplyResult`，显式携带 `sourceMessageId`、`clientRequestId`、`agentTaskId` 和失败语义。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区已有多模块未提交改动；本轮只在 IM/Agent/SPI/审查文档范围内继续，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 70 审，本轮新增第 71 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM + Agent 目标测试 | `env TEST_DB_NAME=workforce_hub_node71_im_agent_conversation2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleAgentApiIntegrationTest test` | 通过 | 覆盖 Agent 会话创建、发送后触发、任务记录、结果回写以及原 Agent 任务用例。 |
| IM + 群治理 + Agent 回归 | `env TEST_DB_NAME=workforce_hub_node71_im_full mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest test` | 通过 | 覆盖 IM 主链路、群治理、实时推送、Agent 会话闭环和 Agent 治理任务用例。 |
| 本轮差异格式检查 | `git diff --check -- server/src/main/java/com/workforcehub/im server/src/main/java/com/workforcehub/agent/service/AgentService.java server/src/main/java/com/workforcehub/spi server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java` | 通过 | 未发现本轮代码差异空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区存在多模块并行改动，本轮以 IM/Agent 目标测试和回归测试为准。
- 没有跑 employee-web 测试，前端尚未接入 `/api/employee/im/agent-conversations`。
- 没有跑真实浏览器或真实 WebSocket 握手测试，本轮 WebSocket 验证使用模拟 session registry。
- 没有验证真实模型、RAG、工具编排和异步 Agent 执行。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 前端接入 | 后端已提供 Agent 会话入口，但 employee-web API 层尚未封装 `startAgentConversation`。 | P2 | 前端新增 API 类型和 store action，把 Agent 列表中的可用 Agent 接到 IM 会话入口。 |
| 6-2 | 触发失败可见性 | Agent 触发失败当前只记录日志。 | P2 | 写系统消息或暴露任务状态，避免用户只看到无响应。 |
| 6-3 | 回复幂等 | Agent 回写尚未处理重复 callback。 | P2 | 按 `agentTaskId` 幂等查询既有消息后返回。 |
| 6-4 | 真实 Agent 能力 | 当前闭环是演示级规则回复。 | P2 | 接真实模型执行器、工具编排或 RAG 后再宣称“会话问答 Agent”语义完成。 |

## 第七部分：阶段性结论

本轮已经补上前端演示最关键的会话闭环缺口：后端现在同时支持私聊、群聊和 Agent 会话入口；员工发送 Agent 会话消息后，会生成可审计的 Agent 任务，并把 Agent 回复作为 IM 消息写回同一会话，同时走现有 WebSocket 推送链路。

当前没有新的 P0/P1 阻断项，可以让前端按 `/api/employee/im/agent-conversations` 开始接入演示链路。需要明确的是，这一版完成的是“会话 Agent 后端闭环”，不是“真实 AI 推理闭环”；进入真实试用前，应优先补触发失败可见性、回写幂等、outbox 可靠投递和真实 Agent 执行器。
