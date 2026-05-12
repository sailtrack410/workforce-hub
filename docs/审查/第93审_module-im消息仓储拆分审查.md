# 第 93 审 module-im 消息仓储拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM 消息仓储拆分）
- 审查范围：`server/src/main/java/com/workforcehub/im/repository/ImMessageRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImGroupRepository.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/service/ImAgentConversationService.java`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核消息分页、发送抢序、幂等查找、编辑撤回、已读和 Agent 回写消息写入是否从主仓储拆入 `ImMessageRepository`，确认员工端 API、WebSocket/outbox 事件、消息幂等和数据库迁移均未改变。本轮不新增业务能力、不新增接口、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮继续处理 `ImRepository` 超阈值问题，优先迁移 IM 主链路和 Agent 回写共同依赖的消息 SQL。

- 新增 `ImMessageRepository`，集中承载消息分页、消息 ID 查询、`clientMessageId` 幂等查询、消息插入、编辑、撤回、会话 `last_message` 更新、已读位点更新和发消息抢序。
- `ImService` 的消息列表、员工发消息、编辑、撤回、已读和附件下载中的消息查询切到 `ImMessageRepository`。
- `ImAgentConversationService` 的 Agent 成功回复和失败系统提示消息写入切到 `ImMessageRepository`。
- `ImRepository` 删除迁出的消息 SQL 和消息 RowMapper，主仓储从约 891 行降到约 643 行。
- 群创建初始成员写入同步切到 `ImGroupRepository.insertUserMember(...)`，避免群成员写 SQL 继续留在主仓储。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 92 审 4-1，`ImRepository` 剩余消息 SQL | 第 92 审指出群治理 SQL 已迁出，但 `ImRepository` 仍承载消息、附件、会话列表、私聊/Agent 会话创建等多类 SQL。本轮已把消息分页、写入、幂等、编辑撤回、已读和 Agent 回写消息写入迁入 `ImMessageRepository`，主仓储行数继续下降。 | 已处理 | 后续继续拆附件/下载票据和会话读写，不把新消息 SQL 回填到 `ImRepository`。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImMessageRepository.lockActiveConversationAndGetLastSeq(...)` 与 `updateConversationLastMessage(...)` | 消息仓储同时管理 `im_message` 和 `im_conversation.last_message_*`，这是现有发送状态机的一部分。触发条件是后续会话仓储继续拆分时，只迁走会话表更新而没有保持同一事务内的抢序和 last message 更新；失败路径是消息写入成功但会话摘要不更新，导致前端列表不显示最新消息。当前 `ModuleImApiIntegrationTest` 覆盖员工发消息、会话摘要和 WebSocket/outbox，未发现行为回归。 | P2 | 后续拆 `ImConversationRepository` 时保留清晰的消息发送事务边界：抢序、写消息、更新会话摘要必须仍在同一事务和同一状态机里完成。 |
| 3-2 | `ImService.toMessageResponse(...)` 和 `ImAgentConversationService.toMessageResponse(...)` 仍从 `ImRepository.listMessageAttachments(...)` 读取附件 | 本轮只迁移消息 SQL，附件 Row 仍留在主仓储。触发条件是后续迁移附件 SQL 时只改员工消息响应，遗漏 Agent 消息响应；失败路径是普通消息和 Agent 回写消息的附件响应不一致。当前 Agent 文本消息无附件，普通附件消息仍由 IM 主测试覆盖。 | P2 | 下一轮拆 `ImAttachmentRepository` 时同时更新 `ImService` 和 `ImAgentConversationService` 的附件转换调用，避免双路径遗漏。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 主仓储已降到约 643 行，但仍超过 600 行阈值，且还承载会话列表、私聊/Agent 会话创建、附件引用、下载票据、审计写入和偏好更新。继续扩展会让会话、附件和审计边界重新混杂。 | P2 | 下一轮优先拆 `ImAttachmentRepository` 或 `ImConversationRepository`，把主仓储压到阈值以下。 |
| 4-2 | `ImService` | 本轮拆的是仓储，`ImService` 仍约 797 行，继续承担会话、消息、附件下载、偏好和私聊编排。触发条件是继续加前端联调补丁或运营能力时，主服务会重新膨胀。 | P2 | 后续拆 `ImMessageService` / `ImConversationService`，不要只停留在仓储层拆分。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新仓储 Bean、构造器注入、迁移后调用点和测试源码可编译。 |
| IM 员工端主测试 | `cd server && env TEST_DB_NAME=wfh_im_message_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 覆盖会话、消息分页、发送、编辑撤回、附件下载、Agent 回写、WebSocket/outbox 和偏好主链路。 |
| 群治理专项 | `cd server && env TEST_DB_NAME=wfh_im_message_repo_group_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test` | 通过 | 覆盖群创建初始成员写入迁移后仍正常。 |
| outbox 专项 | `cd server && env TEST_DB_NAME=wfh_im_message_repo_outbox_$(date +%s) mvn -q -Dtest=ModuleImOutboxIntegrationTest test` | 通过 | 覆盖消息事件 outbox 失败重试、后台治理和派发链路。 |

未验证项：

- 没有跑全量基础回归；本轮只拆 IM 消息仓储。
- 没有新增迁移，未涉及空库、旧库、半成品库的额外数据库兼容验证。
- 没有更新 API 文档，因为接口路径、请求字段、响应字段和事件契约未变化。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 继续拆 `ImRepository` 剩余 SQL | 主仓储仍约 643 行，距离阈值只差一步，但仍包含附件、会话、偏好、下载票据和审计写入。 | P2 | 优先拆附件/下载票据或会话读写，下一轮把主仓储压到 600 行以下。 |
| 6-2 | 拆 `ImMessageService` | 消息 SQL 已独立，但消息业务编排仍在 `ImService`。 | P2 | 把发送、编辑、撤回、已读和消息列表迁入 `ImMessageService`，让 `ImService` 退化为门面或继续拆会话服务。 |
| 6-3 | 附件响应双路径 | `ImService` 和 `ImAgentConversationService` 都各自转换消息响应和附件响应。 | P2 | 拆统一 message response mapper 或附件仓储时同步收敛转换逻辑。 |

## 第七部分：阶段性结论

本轮完成消息仓储拆分，`ImMessageRepository` 已承接 IM 员工消息和 Agent 回写共同依赖的核心消息 SQL，IM 主测试、群治理测试和 outbox 专项测试均通过。当前没有发现 P0/P1 阻断项。

剩余风险集中在 P2：`ImRepository` 仍需继续拆到阈值以下，`ImService` 仍是 IM 员工端主服务大类。下一步建议继续拆附件/会话仓储或直接推进 `ImMessageService`，不要新增 IM 业务能力。
