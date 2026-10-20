# 第 83 审 module-im outbox 批量重试接口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 批量重试接口）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxBatchRetryRequest.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxBatchRetryResponse.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 后台从单条重试扩展到批量重试后的状态机、部分成功语义、审计追踪、路径边界和测试覆盖；本轮不审查 admin-web 页面，不引入 outbox 批次表。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 后台批量恢复能力收口。

- `AdminImOutboxController` 新增 `POST /api/admin/im/outbox-events/batch-retry`，固定路由位于 `/{eventId}` 前。
- 新增 `AdminImOutboxBatchRetryRequest`，限制 `eventIds` 不能为空且单次最多 100 个。
- 新增 `AdminImOutboxBatchRetryResponse`，返回 `requestedCount`、`succeededCount`、`skippedCount` 和逐条结果。
- `ImOutboxAdminService.batchRetryOutboxEvents(...)` 复用单条重试的状态和 payload 校验，只对 `FAILED/DEAD` 事件重置为可立即投递。
- 批量重试采用部分成功语义：不存在事件、非法状态和 payload 异常事件进入 skipped，不回滚整批。
- 批量重试写入 `sys_audit_log`，动作 `IM_OUTBOX_BATCH_RETRY_REQUESTED`，审计 detail 包含请求、成功和跳过 eventId 列表。
- 审计查询接口白名单和 eventId 反查同步支持批量重试审计。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 覆盖批量成功、非法状态跳过、不存在跳过、审计写入和按 eventId 查询批量审计。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 79 审 6-2、第 81 审 6-1，outbox 批量治理 | 前轮指出后台只有单条重试，批量治理前必须补操作审计。当前新增批量重试接口并写批量审计，admin-web 不需要循环调用单条接口来恢复多个失败事件。 | 已处理 | 后续新增忽略、强制 DEAD 等批量动作时继续沿用逐条结果和批次审计。 |
| 2-2 | 第 82 审 3-3，审计动作白名单 | 第 82 审指出新增 outbox 操作时必须同步审计查询白名单。当前 `IM_OUTBOX_BATCH_RETRY_REQUESTED` 已加入白名单，且 eventId 反查覆盖批量审计 detail。 | 已处理 | 后续新增动作继续同步 Service、Repository、API 文档和测试用例。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminService.batchRetryOutboxEvents(...)` | 批量重试采用部分成功语义。触发条件是请求中同时包含可重试、已发送和不存在事件；失败路径不是事务失败，而是返回 `skipped` 明细。如果 admin-web 只看 HTTP 200 或 `code=0`，可能误提示“全部重试成功”。 | P1 | 前端必须以 `succeededCount/skippedCount/items` 为准展示结果；API 文档已明确逐条展示，admin-web 接入时需按 skipped 明细提示。 |
| 3-2 | `retryOneForBatch(...)` | 批量重试先读事件再条件更新。触发条件是 dispatcher 或其他管理员在读后抢先改状态；失败路径是 `resetForAdminRetry` 返回 0，当前会把该事件记为 skipped。该行为避免覆盖并发状态，但用户看到的是部分失败。 | P2 | 后续如果需要更强一致的批量锁定，应引入批次抢占或 `SELECT ... FOR UPDATE`，当前阶段先保守跳过。 |
| 3-3 | `AdminImOutboxBatchRetryRequest.eventIds` | 请求会按首次出现顺序去重，因此 `requestedCount` 表示去重后的事件数，不是原始数组长度。触发条件是前端重复提交同一个 ID；失败路径是前端如果按原数组长度对账会发现数量不一致。 | P2 | admin-web 选中列表应先去重；文档已说明重复 ID 按首次出现顺序去重。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminService` | 服务继续承担查询、单条重试、批量重试、派发、审计 detail 构造和 DTO 转换。触发条件是再加入忽略 DEAD、批量导出、失败聚合；失败路径是后台运营规则继续膨胀在一个类中。 | P2 | 下一次 outbox 后台操作扩展前拆 `ImOutboxOperationService` 和 `ImOutboxAuditService`。 |
| 4-2 | `sys_audit_log.detail_json` | 批量重试的批次语义仍存在 JSON 审计详情中，没有独立批次表。触发条件是批量操作量变大、需要按批次分页、导出或重放；失败路径是查询性能和数据结构演进受限。 | P2 | admin-web 进入高频运营后新增 `im_outbox_operation_batch` / `im_outbox_operation_item` 表。 |
| 4-3 | `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` | outbox 后台治理、审计查询和批量重试继续集中在同一个测试方法，已经偏长。 | P2 | 下一轮 outbox 功能前拆 `ModuleImOutboxIntegrationTest`，至少按后台查询、重试、审计、dispatcher 重试拆分。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增 DTO、Controller、Service 和测试可编译。 |
| outbox 批量重试目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_validation_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds test` | 通过 | 覆盖批量重试部分成功、参数校验、审计写入和 eventId 审计查询。 |
| dispatcher 失败重试兼容性 | `cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_dispatcher_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 验证新增后台批量重试不影响原 WebSocket outbox 失败后自动重试路径。 |
| IM/Agent 组合回归 | `cd server && env TEST_DB_NAME=wfh_im_outbox_batch_retry_combo_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest,ModuleAgentTaskTimeoutRepositoryBoundaryTest test` | 通过 | 验证员工端 IM、群治理、Agent API、超时仓储和 outbox 后台治理组合仍可通过。 |

未验证项：

- 没有跑全量基础回归；本轮改动集中在 IM outbox 后台治理接口。
- 没有验证 100 条批量请求的响应体大小和审计 JSON 展示体验。
- 没有验证两个管理员并发批量重试同一批事件；当前依赖条件更新避免覆盖状态。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | admin-web 批量重试展示 | 后端已经返回逐条结果，但前端如果只提示成功会误导运营。 | P1 | admin-web 接入时必须展示 `skippedCount` 和 `skipReason`。 |
| 6-2 | outbox 批次表 | 当前 JSON 审计足够演示和轻量排障。 | P2 | 高频运营或需要导出批次历史前新增批次表。 |
| 6-3 | outbox 测试拆分 | 目标测试已覆盖，但测试方法继续膨胀。 | P2 | 下一轮先拆测试类再继续扩展。 |

## 第七部分：阶段性结论

本轮把 IM outbox 后台治理从单条重试推进到批量重试：管理员可以一次提交多个失败事件，后端逐条校验并返回成功/跳过明细，同时写入批量审计并支持按事件反查。接口没有改变 dispatcher 派发语义，也没有新增数据库迁移。

当前没有发现新的 P0 阻断项。需要明确的 P1 是 admin-web 接入时不能把 `code=0` 当作“全部成功”，必须展示部分成功结果；其余风险集中在批次模型仍基于审计 JSON、测试类继续膨胀和并发批量操作缺少显式批次锁。
