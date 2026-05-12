# 第 78 审 module-im outbox 后台治理接口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 后台查询、详情、手动重试和主动派发接口）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/websocket/ImEventOutboxRepository.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxEventQuery.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxEventResponse.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxDispatchRequest.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxDispatchResponse.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 IM WebSocket outbox 从“可靠投递账本”补到“后台可观测与手动补偿”后，是否存在权限越界、错误重放、状态机竞争或 API 文档失真；本轮不审查 employee-web 改动，也不启动 admin-web 页面。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 运营可见性补齐后端闭环。

- 新增 `GET /api/admin/im/outbox-events`，支持后台按 `status/eventType/conversationId/messageId/actorUserId/createdAt` 查询 outbox 事件。
- 新增 `GET /api/admin/im/outbox-events/{eventId}`，用于查看单条事件 payload、目标用户、失败原因、重试次数和时间字段。
- 新增 `POST /api/admin/im/outbox-events/{eventId}/retry`，允许管理员把 `FAILED/DEAD` 事件重新置为可立即投递。
- 新增 `POST /api/admin/im/outbox-events/dispatch`，允许管理员主动触发 dispatcher 派发到期事件。
- 手动重试前校验 `payloadJson.eventType` 与 `payloadJson.conversationId` 是否匹配索引字段，避免破损 payload 被后台误重放。
- 手动重试不接管 `SENDING` 状态，卡住的发送中事件继续由 dispatcher stale reclaim 机制处理。
- `ModuleImApiIntegrationTest` 增加后台查询、详情、非法状态、员工越权、手动重试、主动派发和 `SENT` 不可重试断言。
- module-im API 文档、测试用例、开发日志和后续计划同步更新。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 77 审 3-2，`im_event_outbox` | 第 77 审指出 `next_retry_at` 只是投递控制字段，不能作为事件发生时间展示或统计。当前后台响应同时返回 `createdAt`、`updatedAt`、`nextRetryAt`、`sentAt`，API 文档明确区分这些字段，避免 admin-web 把 retry 时间误当事件发生时间。 | 已处理 | admin-web 展示 outbox 时继续以 `createdAt` 表示入账时间，`nextRetryAt` 只表示下次可投递时间。 |
| 2-2 | `docs/开发规划/后续开发计划与测试节点.md` | 旧计划写着 outbox 暂无后台处置页面，容易把“无后台 API”和“无前端页面”混在一起。当前已改为 outbox 已有后台 API、暂无 admin-web 页面，后续任务边界更清楚。 | 已处理 | 下一阶段只需把这些 API 接入 admin-web，不需要前端直接查库或新增临时接口。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminService.retryOutboxEvent(...)` | 手动重试会重放历史 WebSocket payload。触发条件是事件已经进入 `DEAD` 很久后，原会话、消息或成员关系已经发生变化；失败路径是管理员重试后，在线用户收到一条历史事件，再由前端刷新会话和消息。当前只允许 `FAILED/DEAD` 重试，并校验 payload 与索引字段一致，不会重放破损 JSON；但它不能判断业务语义是否仍值得推送。 | P2 | admin-web 展示重试按钮时必须同时展示 `createdAt`、`eventType`、`conversationId`、`messageId`、`lastError` 和 payload 摘要，并对较旧事件做二次确认。 |
| 3-2 | `POST /api/admin/im/outbox-events/dispatch` | `dispatch` 返回的是成功标记 `SENT` 的事件数，不是扫描数，也不是所有目标用户实际在线接收数。触发条件是目标用户离线或没有活跃 WebSocket 连接；失败路径是事件标记 `SENT`，但用户只能通过重新拉消息补齐。该行为与当前 outbox “投递到当前在线连接”语义一致，不影响消息数据本身。 | P2 | admin-web 文案不要写成“用户已收到”，应写成“事件已派发/已完成”；前端继续通过会话列表和消息分页做离线补齐。 |
| 3-3 | `ImOutboxAdminService.assertPayloadEventMatches(...)` | 当前重试前只校验 payload 的 `eventType` 和 `conversationId`，没有校验 `messageId/seqNo/recipientUserIdsJson` 与 payload 深度一致。触发条件是数据库被人工修改或未来 payload 结构扩展后出现局部不一致；失败路径是重试仍可能发送目标用户或消息字段不完全一致的事件。当前系统内写入路径统一由 `ImWebSocketOutboxService` 生成，风险主要来自人工改库或未来扩展。 | P2 | 后续如 admin-web 增加直接编辑或批量重试，应扩展校验 `messageId`、`seqNo` 和目标用户摘要，或禁止对人工修改过的 payload 重试。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImEventOutboxRepository` | 本轮把后台查询和重试 SQL 放进既有 outbox 仓储。触发条件是后续继续增加失败聚合、批量重试、租户隔离或审计导出；失败路径是投递写模型、后台读模型和运营动作混在一个类里，修改 dispatcher 时容易误伤后台查询。 | P2 | admin-web 扩展 outbox 页面前拆 `ImOutboxAdminRepository` 或 `ImOutboxQueryRepository`，把后台读模型和投递状态机分离。 |
| 4-2 | `ModuleImApiIntegrationTest` | 新增后台 outbox 治理用例继续放在 IM 大集成测试类中。触发条件是继续补 outbox DEAD、stale SENDING、批量重试和 admin-web 契约；失败路径是单类测试继续膨胀，局部回归定位成本变高。 | P2 | 下一次 outbox 扩展前拆 `ModuleImOutboxIntegrationTest`，保留当前类只覆盖员工端会话和 WebSocket 主流程。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 新增 Controller、DTO、Service、Repository 和测试源码编译通过。 |
| outbox 后台治理目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_admin_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test` | 通过 | 覆盖管理员列表、详情、非法状态、员工越权、重试、主动派发、`SENT` 不可重试。 |
| outbox 后台治理与失败重试组合 | `cd server && env TEST_DB_NAME=wfh_im_outbox_admin2_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 验证新增后台重试没有破坏既有 outbox 失败重试路径。 |
| IM 员工端全类 | `cd server && env TEST_DB_NAME=wfh_im_outbox_full_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 48 个用例通过，覆盖员工端 IM 主链路、IM x Agent 和新增 outbox 管理接口。 |
| IM/Agent 组合回归 | `cd server && env TEST_DB_NAME=wfh_im_outbox_combo_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test` | 通过 | 95 个用例通过，验证 IM/Agent 组合链路未受 outbox 后台治理接口影响。 |

未验证项：

- 没有跑全量基础回归；本轮没有改 auth/org/file/workflow/leave/attendance 业务代码。
- 没有做真实浏览器 WebSocket 联调；当前仍通过 Mock WebSocketSession 验证推送和 outbox 状态。
- 没有接 admin-web 页面；本轮只交付后端 API 与文档。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | admin-web outbox 页面 | 后端已能查询、详情、重试和主动派发，但没有运营页面。 | P1 | admin-web 启动后把 `FAILED/DEAD` 列表、事件详情、重试二次确认和 dispatch 操作接入第一批运营入口。 |
| 6-2 | outbox 批量治理 | 当前只支持单条重试，适合演示和小规模排障。 | P2 | 后续如要批量处理 `DEAD`，先补批量重试审计和更严格 payload 校验，不要直接循环调用单条接口。 |
| 6-3 | outbox 读模型拆分 | 当前仓储继续增大但未引入跨模块越权。 | P2 | admin-web outbox 页面扩展前拆后台读模型仓储，避免投递状态机和运营查询耦合。 |

## 第七部分：阶段性结论

本轮把 IM WebSocket outbox 从“有可靠账本但只能查库排障”推进到“后台 API 可观测、可重试、可主动派发”。权限边界走 `/api/admin/**` 管理员路径，员工会话访问已覆盖 `403`；手动重试只允许 `FAILED/DEAD`，并且先校验 payload 关键索引，避免最直接的错误重放。

当前没有发现阻断前端演示或后续 admin-web 接入的 P0/P1。剩余风险集中在后台 UI 操作确认、批量重试审计、以及 outbox 查询仓储后续拆分。
