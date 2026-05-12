# 第 77 审 module-im 会话偏好排序与 outbox 即时投递收口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
- 审查范围：`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/websocket/ImWebSocketOutboxService.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`
- 审查目标：复核第 74/75 审留下的展示名降级、置顶排序、偏好事件范围，并审查本轮发现的 WebSocket outbox 初次投递时序问题；不处理 employee-web 现有工作区改动。

---

## 第一部分：提交内容概览

- 会话列表排序从单纯按 `lastMessageAt/id` 倒序，调整为 `pin_flag DESC, lastMessageAt DESC, conversationId DESC`。
- 游标分页补充游标会话的 `pin_flag` 判断，避免第一页置顶后翻页丢失未置顶会话。
- 会话成员展示名查询增加 `COALESCE` 降级，用户软删除后返回 `USER:{memberId}`，Agent 软删除后返回 `AGENT:{memberId}`。
- 会话偏好更新事件从广播给会话所有活跃成员改为只推给操作者本人，避免私有置顶/免打扰状态泄露给其他成员。
- outbox 初次入账的 `next_retry_at` 改为当前时间减一秒，避免 MySQL `DATETIME` 秒级精度导致 after-commit 即时投递 claim 不到刚插入事件。
- `ModuleImApiIntegrationTest` 新增置顶排序、偏好私有推送、展示名降级测试，并恢复 outbox 失败重试用例稳定性。
- module-im API 文档和测试用例文档同步更新前端契约。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 75 审 3-1，`ImRepository.listActiveConversationsForUser(...)` | 旧实现只返回 `pin` 字段，但排序仍按最后消息时间倒序。触发条件是用户把一个较旧会话置顶；失败路径是前端能看到 `pin=true`，但置顶会话仍排在较新的未置顶会话后，演示会表现为“设置成功但列表无变化”。本轮已按 `pin_flag DESC` 排序，并补游标跨置顶/未置顶边界测试。 | 已处理 | 保持当前游标契约不变，前端继续传 `beforeLastMessageAt + beforeConversationId`；如后续支持多排序维度，再显式版本化列表接口。 |
| 2-2 | 第 75 审 3-2，`IM_CONVERSATION_PREFERENCE_UPDATED` | 旧实现把偏好事件广播给会话所有活跃成员。触发条件是 A 设置置顶或免打扰；失败路径是 B 收到不属于自己的偏好状态，如果前端未过滤 `userId` 可能误刷新本地 UI。当前事件只推给操作者本人，测试验证 manager socket 不再收到该事件。 | 已处理 | 保持偏好事件为用户私有事件，不再复用会话广播入口。 |
| 2-3 | 第 74 审 3-1，`ImRepository.listActiveUserMembers(...)`、`listActiveMembers(...)` | 旧实现对用户和 Agent 使用 `LEFT JOIN` 后直接返回名称。触发条件是历史会话成员关联对象被软删除；失败路径是 `displayName=null`，前端只能展示空值或自行猜 ID。当前 SQL 增加 `COALESCE(..., CONCAT(member_type, ':', member_id))`，测试覆盖用户软删除降级。 | 已处理 | 头像、部门、在线状态仍不在本轮范围；后续如果扩展成员卡片，应拆专用读模型。 |
| 2-4 | `ImWebSocketOutboxService.enqueue(...)`、`ImWebSocketOutboxDispatcher.claimDueEvents(...)` | 测试暴露出初次事件写入 `next_retry_at=LocalDateTime.now()` 后，MySQL `DATETIME` 秒级精度可能让落库时间晚于 dispatcher 传入的 `now`。触发条件是事务提交后立刻执行 after-commit dispatcher；失败路径是事件保持 `PENDING`，即时推送没有发生，只能等后续定时扫描。当前初次入账将 `next_retry_at` 设为当前时间减一秒，保证提交后可立即 claim。 | 已处理 | 后续如果迁移到更高精度字段或独立消息中间件，可重新审视该兼容处理；现阶段比修改历史迁移更安全。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImRepository.listActiveConversationsForUser(...)` | 当前游标仍只暴露 `beforeLastMessageAt + beforeConversationId`，服务端内部再查询游标会话的 `pin_flag`。触发条件是用户在翻页过程中修改游标会话的置顶状态；失败路径是第二页边界可能相对第一页发生漂移，出现重复或漏一条会话。当前演示和常规列表刷新可接受，因为置顶变更后前端通常重新拉第一页。 | P2 | 如果后续做强一致列表分页，应把排序游标升级为显式对象，例如 `beforePin + beforeLastMessageAt + beforeConversationId`。 |
| 3-2 | `ImWebSocketOutboxService.enqueue(...)` | 初次投递用 `now - 1s` 是为了兼容 `DATETIME` 精度，不是业务语义字段。触发条件是后续有人把 `next_retry_at` 当事件发生时间展示或统计；失败路径是监控上看到初次可投递时间早于创建时间。当前表已有 `created_at` 和事件 payload 的 `occurredAt`，不应使用 `next_retry_at` 做发生时间。 | P2 | 管理后台展示 outbox 时区分 `created_at`、`next_retry_at`、`sent_at`，不要把 retry 字段作为事件时间。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 本轮继续在大仓储里追加会话排序、成员展示和偏好相关 SQL。触发条件是继续做组织群同步、在线状态、成员卡片和搜索；失败路径是 IM 仓储会混合会话、成员、消息、附件、outbox 以外的通讯录读模型职责，后续修改容易破坏过滤条件。 | P2 | 近期拆 `ImConversationRepository`、`ImMemberQueryRepository` 和 `ImMessageRepository`，成员展示字段集中在成员读模型中维护。 |
| 4-2 | `ModuleImApiIntegrationTest` | 本轮新增测试继续进入同一个大测试类。触发条件是继续补会话列表、详情、偏好、outbox、Agent 场景；失败路径是单类用例数量继续上升，失败定位和局部回归成本变高。 | P2 | 下一轮可拆 `ModuleImConversationPreferenceIntegrationTest` 和 `ModuleImOutboxIntegrationTest`，保留主类只覆盖核心会话收发。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| outbox 失败重试目标用例 | `cd server && env TEST_DB_NAME=wfh_im_retry_fix_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 验证初次 outbox 事件可立即 claim，首次推送失败后进入 `FAILED`，重试后可到 `SENT`。 |
| IM 员工端专项 | `cd server && env TEST_DB_NAME=wfh_im_full_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 覆盖 47 个 IM 员工端用例，包括新增置顶排序、展示名降级、偏好私有事件。 |
| IM 群治理专项 | `cd server && env TEST_DB_NAME=wfh_im_group_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test` | 通过 | 验证成员展示名降级 SQL 未破坏群治理查询。 |
| IM/Agent 组合回归 | `cd server && env TEST_DB_NAME=wfh_im_agent_combo_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test` | 通过 | 验证 outbox 即时投递改动没有破坏 Agent 任务、超时仓储和 IM 回写。 |

未验证项：

- 本轮没有重跑全部基础回归；上轮第 76 审已记录完整回归通过，本轮覆盖直接受影响 IM 类和 IM/Agent 组合回归。
- 没有使用真实浏览器 WebSocket 客户端联调，只通过 Mock WebSocketSession 和 outbox dispatcher 验证。
- 没有验证置顶变更和翻页请求并发交错下的强一致分页，只按当前前端“偏好变更后刷新第一页”语义收口。

---

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 前端 IM 接入 | 后端会话列表、详情、消息、Agent、偏好和 WebSocket 事件契约已具备演示接入条件。 | P1 | employee-web 可以按 API 文档继续完成真实消息发送、WebSocket 订阅和 Agent 会话入口。 |
| 6-2 | IM 仓储和测试拆分 | 大仓储和大测试类继续膨胀，但本轮没有新增跨模块越权或状态机 P1。 | P2 | 下一次继续扩组织群或在线状态前先拆读模型仓储和偏好/outbox 测试类。 |
| 6-3 | 会话分页游标对象化 | 当前游标兼容置顶排序，但不覆盖偏好变更并发下的强一致快照。 | P2 | 如前端需要无限滚动和实时置顶混用，应新增显式游标对象。 |

---

## 第七部分：阶段性结论

本轮收口了第 74/75 审中最直接影响 employee-web 消息页体验的 P2 项：置顶排序、偏好事件范围、成员展示名降级。同时修复了测试暴露出的 outbox 初次投递时序缺陷，避免数据库已提交但 after-commit 即时推送因 `next_retry_at` 精度错过首轮 claim。

当前没有发现新的 P0/P1 阻断项。后端 IM 会话偏好和实时推送契约可以继续交给前端接入；下一后端治理重点应转向 IM 仓储/测试拆分、前端端到端 WebSocket 联调，以及 admin-web 运营入口。
