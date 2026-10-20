# 第 88 审 module-im outbox 事件类型常量收敛审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 事件类型常量收敛）
- 审查范围：`server/src/main/java/com/workforcehub/im/websocket/ImWebSocketEventTypes.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/service/ImAgentConversationService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminQueryService.java`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 WebSocket/outbox 事件类型是否从分散字符串收敛为共享常量，并确认前端可见事件名、outbox 入库 eventType 和后台筛选白名单不发生契约变化。本轮不新增事件类型、不新增 API、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮处理第 87 审 4-1 提出的事件类型白名单硬编码问题。

- 新增 `ImWebSocketEventTypes`，集中定义消息、群治理、已读、偏好和 Agent 相关的 IM WebSocket/outbox 事件类型。
- `ImService` 发送消息、编辑、撤回、群治理、已读、偏好事件时改为引用常量。
- `ImAgentConversationService` 发送 Agent 成功回复和失败提示事件时改为引用常量。
- `ImOutboxAdminQueryService` 的 outbox eventType 查询白名单改为复用 `ImWebSocketEventTypes.OUTBOX_EVENT_TYPES`。
- 测试中的字符串断言保留，继续保护前端可见事件名不被误改。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 87 审 4-1，`ImOutboxAdminQueryService.OUTBOX_EVENT_TYPES` | 第 87 审指出 outbox 事件类型白名单在 query service 中硬编码，新增推送事件时容易漏同步后台筛选。当前已改为复用 `ImWebSocketEventTypes.OUTBOX_EVENT_TYPES`，发送方和后台查询白名单来自同一常量类。 | 已处理 | 后续新增 WebSocket/outbox 事件时只扩展 `ImWebSocketEventTypes`，并同步 API 文档和前端事件处理。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImWebSocketEventTypes.OUTBOX_EVENT_TYPES` | 常量集合被后台查询服务复用。触发条件是未来定义了只走 WebSocket 但不入 outbox 的事件，或只走 outbox 但不暴露给前端的内部事件；失败路径是后台筛选集合和真实存储事件范围不匹配。当前所有列入集合的事件都是 outbox 可存储事件，未发现不匹配。 | P2 | 如果后续出现内部 outbox 事件，拆分 `WEBSOCKET_EVENT_TYPES` 和 `OUTBOX_EVENT_TYPES`，不要默认所有事件都进后台筛选。 |
| 3-2 | `ImService`、`ImAgentConversationService` | 发送方从字符串字面量改为常量。触发条件是常量值误改；失败路径是前端 WebSocket eventType 和 outbox 查询 eventType 同时变化，测试若也改常量可能挡不住。当前测试仍保留字符串断言，可发现误改。 | P2 | 前端可见事件名继续在集成测试和 API 文档中以字符串断言保留，不要把测试断言也全部替换为同一常量。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminQueryService` | 操作审计 action type 仍在查询服务内硬编码。触发条件是后续新增 outbox 操作审计动作；失败路径是审计已写入但查询白名单不支持。 | P2 | 后续抽 `ImOutboxAdminAuditActions`，让写审计和查审计复用同一动作常量集合。 |
| 4-2 | `ModuleImApiIntegrationTest` | 本轮没有拆测试类。事件类型常量收敛后，outbox 后台目标用例仍在大测试类中。 | P2 | 后续迁移到 `ModuleImOutboxIntegrationTest`，保留字符串 eventType 契约断言。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证常量引用、发送方和后台查询服务编译通过。 |
| outbox eventType 筛选目标用例 | `cd server && env TEST_DB_NAME=wfh_im_event_types_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test` | 通过 | 覆盖后台按 `eventType=IM_MESSAGE_CREATED` 查询、详情、重试、派发和审计链路。 |

未验证项：

- 没有跑前端 WebSocket 事件解析；本轮未改变事件名字符串。
- 没有抽审计 action 常量。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 审计 action 常量 | 和事件类型同类问题，但只影响后台审计查询，不影响前端实时事件。 | P2 | 下轮小治理抽出写审计和查审计共享动作常量。 |
| 6-2 | outbox 测试类迁移 | 仍是主要测试维护债。 | P2 | 迁移 outbox 专项测试，降低 `ModuleImApiIntegrationTest` 体积。 |

## 第七部分：阶段性结论

本轮把 IM WebSocket/outbox 事件类型从发送方和后台查询服务中的分散字符串收敛到共享常量，减少后续新增事件时后台筛选漏同步的风险。当前没有发现新的 P0/P1 阻断项，前端可见事件名和 outbox eventType 契约保持不变。

剩余风险是 P2：审计 action type 仍可继续常量化，outbox 专项测试仍应迁出大测试类。本轮最终校验需要补跑 outbox 后台目标用例，确认 eventType 查询筛选仍正常。
