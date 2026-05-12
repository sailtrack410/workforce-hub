# 第 81 审 module-im outbox 派发审计明细审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 主动派发审计补充 eventId 明细）
- 审查范围：`server/src/main/java/com/workforcehub/im/websocket/ImWebSocketOutboxDispatcher.java`、`server/src/main/java/com/workforcehub/im/websocket/ImWebSocketOutboxDispatchResult.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 80 审提出的“主动派发审计缺少具体 eventId 明细”是否收口，并确认新增 dispatcher 详细结果不改变现有 API 响应和 after-commit 即时派发语义；本轮不新增审计查询接口、不做批量重试。

---

## 第一部分：提交内容概览

本轮围绕 outbox 主动派发审计可追溯性收口。

- 新增 `ImWebSocketOutboxDispatchResult`，记录 `attemptedEventIds`、`dispatchedEventIds`、`failedEventIds`。
- `ImWebSocketOutboxDispatcher` 保留原 `dispatchDueEvents(int)` 的 `int` 返回语义，新增 `dispatchDueEventsWithResult(int)` 给后台治理服务使用。
- `ImOutboxAdminService.dispatchDueEvents(...)` 改为使用详细派发结果写审计，API 响应仍只返回 `requestedBatchSize` 和 `dispatchedCount`。
- 重试审计详情补充 `eventId`。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加审计 detail JSON 断言，验证派发审计包含实际成功派发的 eventId。
- module-im API 文档、测试用例、开发日志和开发计划同步更新审计明细口径。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 80 审 3-2，`dispatchDueEvents(...)` | 第 80 审指出主动派发审计只有 `requestedBatchSize` 和 `dispatchedCount`，无法直接还原具体处理了哪些 outbox 事件。当前 dispatcher 返回详细结果，审计 detail 包含本次抢占、成功和失败 eventId 列表。 | 已处理 | 后续 admin-web 可按审计 detail 直接展示本次派发涉及的事件摘要。 |
| 2-2 | 第 80 审 6-1，outbox 派发批次关联 | 第 80 审建议增加派发批次号或 eventId 摘要。当前先采用 eventId 摘要，不新增表结构和批次号，满足小规模排障和 admin-web 操作历史追溯。 | 已处理 | 如果后续需要批量处理上百条事件，再考虑新增批次表或批次号，避免审计 JSON 过大。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImWebSocketOutboxDispatcher.dispatchDueEventsWithResult(...)` | 详细结果只记录本次 claim 到的事件和逐条派发结果。触发条件是多个 dispatcher 并发运行；失败路径是某个管理员主动派发审计只包含自己这次抢占到的 eventId，无法代表整个系统同时刻所有 outbox 事件处理情况。该行为符合 `FOR UPDATE SKIP LOCKED` 多实例抢占模型。 | P2 | admin-web 文案应写“本次派发处理事件”，不要写“系统所有待派发事件”。 |
| 3-2 | `sys_audit_log.detail_json` | eventId 明细写入 JSON 数组。触发条件是未来允许超大 batchSize 或批量治理一次处理大量事件；失败路径是审计 detail 过大，影响查询和展示。当前后台 dispatch 限制最大 `500`，且本轮仍是小规模排障入口。 | P2 | 批量治理前新增批次表或只保留前 N 个 eventId 摘要，并把完整明细放独立表。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminService.dispatchAuditDetail(...)` | 服务层继续手写审计 detail 拼装，字段已经从数量扩展到 eventId 数组。触发条件是继续增加 outbox 忽略、批量重试、导出等后台操作；失败路径是 audit detail 构造继续膨胀。 | P2 | 后续扩展前拆 `ImOutboxAuditService` 或 detail builder。 |
| 4-2 | `ImWebSocketOutboxDispatchResult` | 当前只是 dispatcher 内部结果对象，没有持久化批次 ID。对当前后台 API 足够，但不能作为长期运营批次模型。 | P2 | 真正批量治理阶段再引入 outbox 操作批次表，不要把 record 当作持久批次语义。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增 dispatcher 结果对象、审计 detail 和测试断言可编译。 |
| outbox 后台派发审计明细与失败重试组合 | `cd server && env TEST_DB_NAME=wfh_im_outbox_dispatch_detail_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 覆盖后台重试、派发、审计 detail eventId、员工越权和 WebSocket 失败重试。 |

未验证项：

- 没有跑全量基础回归；本轮只改 IM outbox dispatcher 内部返回和后台审计 detail。
- 没有验证大批量 500 条事件的审计 JSON 展示性能。
- 没有新增 admin-web 操作历史页面，当前只验证数据库审计内容。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | outbox 批量重试 | 查询、单条重试、主动派发、操作审计和 eventId 明细都已具备。 | P2 | 真实需要批量重试时，先设计批次语义和审计摘要，再新增接口。 |
| 6-2 | `ModuleImOutboxIntegrationTest` 拆分 | outbox 相关用例继续放在 `ModuleImApiIntegrationTest`。 | P2 | 下一次 outbox 功能扩展前拆测试类，降低主 IM 测试类膨胀。 |
| 6-3 | admin-web 操作历史 | 后端审计 detail 已可支撑页面展示。 | P1 | admin-web 第一批 outbox 页面可展示最近重试和派发审计。 |

## 第七部分：阶段性结论

本轮把 outbox 主动派发从“有操作审计”推进到“审计能定位具体事件”：后台派发审计现在包含本次抢占、成功和失败 eventId 明细，且没有改变外部 API 响应结构。目标测试验证了 eventId 明细随重试/派发链路落库。

当前没有发现新的 P0/P1 阻断项。剩余风险集中在批量治理时的批次模型、测试类拆分和 admin-web 页面呈现。
