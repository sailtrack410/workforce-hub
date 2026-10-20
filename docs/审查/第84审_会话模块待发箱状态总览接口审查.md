# 第 84 审 module-im outbox 状态总览接口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 状态总览接口）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxSummaryResponse.java`、`server/src/main/java/com/workforcehub/im/repository/AdminImOutboxStatusSummaryRow.java`、`server/src/main/java/com/workforcehub/im/repository/AdminImOutboxSummaryAggregateRow.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 状态总览接口是否能给 admin-web 提供稳定、只读、可解释的积压口径；重点检查权限、stale `SENDING` 判定、统计字段语义和文档测试一致性。本轮不审查 admin-web 页面，不新增数据库索引或迁移。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 后台运营可观测性补齐轻量总览。

- `AdminImOutboxController` 新增 `GET /api/admin/im/outbox-events/summary`，固定路由位于 `/{eventId}` 前，避免路径被 eventId 捕获。
- 新增 `AdminImOutboxSummaryResponse`，返回总量、待派发量、stale `SENDING` 数、最早创建时间、最早重试时间、统计生成时间、stale 截止时间和固定顺序状态计数。
- 新增 `AdminImOutboxStatusSummaryRow` 与 `AdminImOutboxSummaryAggregateRow`，把后台统计读模型从 DTO 和 SQL 映射中拆开。
- `ImOutboxAdminRepository` 新增状态分组 SQL 与总览聚合 SQL。
- `ImOutboxAdminService.summarizeOutboxEvents()` 复用 `workforce.im.outbox.dispatcher.sending-stale-ms` 计算 `sendingStaleBefore`，保证后台总览和 dispatcher stale 抢占口径一致。
- `ModuleImApiIntegrationTest.adminShouldSummarizeImOutboxEvents` 覆盖五类状态计数、待派发数、stale `SENDING` 数、最早时间和员工访问 403。
- module-im API 文档、测试用例、开发日志和后续开发计划同步补齐状态总览接口说明。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 78-83 审，outbox 后台治理链路 | 前轮已经补齐 outbox 分页、详情、单条重试、主动派发、操作审计、审计查询和批量重试，但 admin-web 如果要展示红点和积压概况仍只能扫分页列表自行聚合。当前新增只读 summary，前端可以直接拿 `dueDispatchCount/staleSendingCount/statusCounts` 渲染排障入口。 | 已处理 | 后续 admin-web 接入时以 summary 作为入口概览，以分页列表作为明细来源。 |
| 2-2 | 第 83 审 4-3，outbox 测试继续膨胀 | 本轮仍在 `ModuleImApiIntegrationTest` 中新增测试方法，没有拆出独立 outbox 测试类。新增用例是独立方法，未继续扩张 `adminShouldManageImOutboxEvents`。 | 部分收口 | 下一轮 outbox 功能前仍建议拆 `ModuleImOutboxIntegrationTest`，避免单个测试类继续承担全部 IM 场景。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminRepository.summarizeOutboxEvents(...)` | `dueDispatchCount` 统计的是当前可被抢占的事件数，不是实际派发成功数。触发条件是事件状态为到期 `PENDING/FAILED` 或 stale `SENDING`，但目标用户离线、payload 异常、WebSocket 发送失败时，后续 dispatcher 仍可能失败或进入 `DEAD`。如果 admin-web 把该字段展示成“可成功推送数”，会误导运营。 | P1 | 前端文案必须使用“待派发/可抢占”，不要使用“可成功推送”；真正结果以 `POST /dispatch` 返回和审计明细为准。 |
| 3-2 | `ImOutboxAdminService.summarizeOutboxEvents()` | 总览使用当前时间和 `sending-stale-ms` 计算 `sendingStaleBefore`，与 dispatcher 当前代码一致。触发条件是未来 dispatcher stale 规则变更但 summary 未同步；失败路径是后台显示 stale 数与实际抢占数不一致。 | P2 | 如果 dispatcher 未来加入租约 owner、分片或更复杂抢占条件，应把 due 判定抽成共享策略或数据库视图，避免两处 SQL 口径漂移。 |
| 3-3 | `ImOutboxAdminRepository.countOutboxEventsByStatus()` | `statusCounts` 当前固定只展示 `PENDING/SENDING/SENT/FAILED/DEAD`，未知状态会被总数包含但不会进入明细。触发条件是后续新增状态但未更新 `OUTBOX_STATUS_ORDER`；失败路径是前端看到 total 与状态明细合计不一致。 | P2 | 新增 outbox 状态时必须同步 `OUTBOX_STATUS_ORDER`、API 文档和测试；后续可增加 unknown 状态兜底计数。 |
| 3-4 | `GET /api/admin/im/outbox-events/summary` | 该接口只读，不写操作审计。触发条件是管理员频繁查看 summary；失败路径不是数据损坏，而是无法追踪谁查看过 outbox 总览。由于接口不暴露单条 payload，不改变状态，也不是敏感导出，当前不写审计符合成本边界。 | P3 | 如果后续 summary 增加失败原因聚合、收件人分布或 payload 摘要，再评估是否需要查询审计。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminService` | 类继续承担分页查询、详情、summary、单条重试、批量重试、主动派发、审计 detail 构造和 DTO 转换。触发条件是继续加入忽略 DEAD、失败聚合、导出或批次详情；失败路径是运营规则越堆越难审查。 | P2 | 下一次 outbox 后台扩展前拆 `ImOutboxQueryService`、`ImOutboxOperationService`、`ImOutboxAuditService`。 |
| 4-2 | `ImOutboxAdminRepository` | 后台仓储虽然已和 dispatcher 仓储拆开，但现在同时包含分页、详情、summary 和审计查询 SQL。触发条件是 admin-web 后续增加更多聚合图表；失败路径是单仓储 SQL 过长，影响审查和变更定位。 | P2 | 按读模型拆 `ImOutboxEventAdminRepository` 与 `ImOutboxAuditAdminRepository`，summary 可以单独进入 `ImOutboxMetricsRepository`。 |
| 4-3 | `ModuleImApiIntegrationTest.adminShouldSummarizeImOutboxEvents` | 测试通过手写 6 条 outbox 数据覆盖统计，但没有复用 outbox 写入服务。这样能精确覆盖状态组合，但和真实 payload 生成路径存在一定距离。 | P2 | 保留当前精确统计用例，同时在后续拆分测试类时增加一条通过真实消息发送产生 outbox 后再查 summary 的轻量用例。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增 DTO、Row、Controller、Service、Repository 和测试可编译。 |
| outbox 状态总览目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_summary_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldSummarizeImOutboxEvents test` | 通过 | 覆盖 summary 响应字段、状态计数、due/stale 统计和员工 403。 |

未验证项：

- 没有跑全量基础回归；本轮改动集中在 IM outbox 后台只读接口。
- 没有验证百万级 outbox 表的聚合查询耗时；当前表已有 `status,next_retry_at,id` 索引，但 `MIN(created_at)` 没有专用索引。
- 没有验证真实 admin-web 渲染；文档已给出字段语义，前端仍需避免把 `dueDispatchCount` 解释为派发成功数。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | admin-web 字段展示语义 | `dueDispatchCount` 容易被误读为“成功数”。 | P1 | 前端显示为“待派发”或“可抢占”，派发结果以 dispatch 响应和审计为准。 |
| 6-2 | 聚合查询性能 | 当前足够演示和小规模运营，但 `MIN(created_at)` 在大表上可能扫描更多数据。 | P2 | outbox 数据量上来后补 `created_at` 索引或维护汇总表。 |
| 6-3 | 服务拆分 | outbox 后台服务已经继续膨胀。 | P2 | 下一轮后台 outbox 功能前先拆 query/operation/audit/metrics 服务。 |

## 第七部分：阶段性结论

本轮新增的 outbox 状态总览接口是 admin-web 接入前的必要补口：前端可以不用扫描分页列表，就能展示 outbox 总量、待派发数、stale `SENDING` 和状态分布。接口保持只读，不改变 dispatcher、重试或审计状态机，也没有新增数据库迁移。

当前没有发现新的 P0 阻断项。需要重点跟进的 P1 是前端展示语义：`dueDispatchCount` 只能表示“当前可抢占/待派发”，不能承诺推送成功。其余风险集中在后续数据量增长后的聚合性能、状态枚举扩展同步和 outbox 后台服务继续膨胀。
