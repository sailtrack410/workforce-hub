# 第 85 审 module-im outbox 失败原因聚合接口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 失败原因聚合接口）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxFailureSummaryResponse.java`、`server/src/main/java/com/workforcehub/im/repository/AdminImOutboxFailureSummaryRow.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 失败原因聚合接口是否能给 admin-web 提供可直接消费的失败分布，同时不改变投递、重试和审计状态机；重点检查路由顺序、权限、聚合口径、排序语义和测试文档一致性。本轮不审查 admin-web 页面，不新增数据库迁移。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 后台失败排障能力补齐轻量聚合接口。

- `AdminImOutboxController` 新增 `GET /api/admin/im/outbox-events/failure-summary`，固定路由放在 `/{eventId}` 前，避免被路径变量误吞。
- 新增 `AdminImOutboxFailureSummaryResponse` 和 `AdminImOutboxFailureSummaryRow`。
- `ImOutboxAdminRepository.listOutboxFailureSummary(...)` 只聚合 `FAILED/DEAD`，按 `status + lastError` 分组，空错误归一为 `UNKNOWN`，最多返回 20 条。
- 聚合响应返回 `status`、`lastError`、`eventCount`、`maxRetryCount`、`firstOccurredAt`、`lastOccurredAt`。
- `ModuleImApiIntegrationTest.adminShouldGetImOutboxFailureSummary` 覆盖失败原因聚合、`SENT` 排除、空错误归一和员工访问 403。
- module-im API 文档、测试用例、开发日志和后续开发计划同步补齐失败聚合接口说明。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 84 审 6-1，admin-web outbox 总览语义 | 第 84 审补齐状态总览后，admin-web 可以知道有多少积压，但还不知道主要失败原因。当前新增 `failure-summary`，前端可以直接展示 `FAILED/DEAD` 的原因分布和影响数量。 | 已处理 | 前端告警面板优先组合 `summary` 与 `failure-summary`，再进入分页列表处理明细。 |
| 2-2 | 第 78-83 审，outbox 后台固定路由顺序 | 前轮多次强调 `/audits`、`/batch-retry`、`/dispatch` 等固定路由必须放在 `/{eventId}` 前。当前 `/failure-summary` 同样位于路径变量前，避免字符串被当成 Long 解析。 | 已处理 | 后续所有固定管理路由继续放在 `/{eventId}` 前。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminRepository.listOutboxFailureSummary(...)` | 聚合按 `last_error` 原文分组。触发条件是同一类底层错误包含动态 ID、地址或堆栈片段；失败路径是同一根因被拆成多条，admin-web 看到的失败分布被稀释。 | P2 | 后续如果错误文本变长或动态化，dispatcher 写入 `failure_type` 或规范化错误码，聚合按错误码而不是原文。 |
| 3-2 | `failure-summary` 响应排序 | 当前按 `eventCount DESC`、`lastOccurredAt DESC`、`lastError ASC` 排序。触发条件是一个 `DEAD` 高风险失败只有 1 条，而普通 `FAILED` 有多条；失败路径是 DEAD 不一定排在前面。 | P2 | admin-web 如果要突出不可自动恢复事件，应单独按 `status=DEAD` 做视觉标记，不依赖列表排序表达严重程度。 |
| 3-3 | `last_error` 为空归一为 `UNKNOWN` | 触发条件是后台手动重试清空 `last_error` 后事件又处于 `FAILED`，或历史数据缺失错误原因；失败路径是运营只能看到 UNKNOWN，无法定位根因。 | P2 | dispatcher 失败写入必须继续保证 `last_error` 有值；手动重试后再次失败会覆盖为真实错误。必要时新增操作审计反查上一条错误。 |
| 3-4 | `GET /api/admin/im/outbox-events/failure-summary` | 接口只读，不写查询审计。触发条件是管理员查看失败分布；失败路径不是数据状态改变，而是无法追踪谁查看过聚合结果。当前接口不暴露 payload 和收件人，只展示错误摘要，风险较低。 | P3 | 如果后续扩展为导出失败明细、收件人分布或 payload 摘要，再补查询审计。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminService` | outbox 后台服务继续承担 summary、failure-summary、分页、详情、重试、派发和审计。触发条件是 admin-web 继续加入告警确认、忽略错误、导出等功能；失败路径是运营逻辑聚合在一个类里，审查难度继续上升。 | P2 | 下一轮 outbox 后台功能前拆 `ImOutboxQueryService`、`ImOutboxMetricsService`、`ImOutboxOperationService`。 |
| 4-2 | `ImOutboxAdminRepository` | 仓储继续聚合分页、审计、summary 和 failure-summary SQL。触发条件是聚合指标继续增加；失败路径是 SQL 变更互相影响，容易误伤已有查询。 | P2 | 将 metrics 类 SQL 拆到 `ImOutboxMetricsRepository`，审计 SQL 拆到 `ImOutboxAuditRepository`。 |
| 4-3 | `ModuleImApiIntegrationTest` | outbox 相关测试继续集中在 IM API 大测试类里，虽然每个用例已经拆方法，但类体积仍然偏大。 | P2 | 后续拆 `ModuleImOutboxIntegrationTest`，把 summary、failure-summary、重试、派发、审计统一迁出。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增 DTO、Row、Controller、Service、Repository 和测试可编译。 |
| outbox 失败原因聚合目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_failure_summary_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldGetImOutboxFailureSummary test` | 通过 | 覆盖失败聚合、空错误归一、`SENT` 排除和员工 403。 |

未验证项：

- 没有跑全量基础回归；本轮改动集中在 IM outbox 后台只读接口。
- 没有验证大量不同 `last_error` 文本下的响应可读性；当前接口限制最多 20 条。
- 没有引入 `failure_type` 错误码，当前聚合仍依赖 `last_error` 原文。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 错误码归一 | 当前按 `last_error` 原文聚合，足够演示和早期排障，但生产告警会受动态错误文本影响。 | P2 | dispatcher 后续增加 `failure_type` 或错误码字段，聚合按稳定码展示。 |
| 6-2 | DEAD 视觉优先级 | 排序按数量优先，不保证 `DEAD` 排第一。 | P2 | admin-web 对 `DEAD` 单独标红或独立区块展示。 |
| 6-3 | outbox 后台服务拆分 | summary 和 failure-summary 继续加重 `ImOutboxAdminService`。 | P2 | 下一轮功能前拆 metrics/query/operation/audit。 |

## 第七部分：阶段性结论

本轮新增的 outbox 失败原因聚合接口补齐了 admin-web 排障面板的关键后端能力：前端可以直接展示 `FAILED/DEAD` 的主要失败原因、影响数量、最大重试次数和时间范围，不需要扫描分页明细自行聚合。接口保持只读，不改变 dispatcher、手动重试、批量重试、主动派发或操作审计语义。

当前没有发现新的 P0/P1 阻断项。后续需要治理的重点是错误原因稳定化、`DEAD` 事件展示优先级和 outbox 后台服务拆分。
