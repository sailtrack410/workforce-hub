# 第 86 审 module-im outbox 后台服务拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 后台服务拆分）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminQueryService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminMetricsService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 后台服务拆分是否在不改变 API 契约、投递状态机和审计语义的前提下降低单类职责；重点检查 Controller 路由分发、查询/指标/操作边界、验证覆盖和剩余维护债。本轮不新增接口、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮围绕第 84、85 审提出的 outbox 后台服务膨胀问题做职责拆分。

- 新增 `ImOutboxAdminQueryService`，负责 outbox 事件分页、详情、审计分页、查询条件归一和 DTO 转换。
- 新增 `ImOutboxAdminMetricsService`，负责状态总览和失败原因聚合。
- `ImOutboxAdminService` 收口为操作服务，保留单条重试、批量重试、主动派发和审计 detail 构造。
- `AdminImOutboxController` 改为按接口语义分别调用操作、查询、指标服务；`/summary`、`/failure-summary`、`/audits`、`/batch-retry`、`/dispatch` 固定路由仍位于 `/{eventId}` 前。
- 拆分后服务行数为：`ImOutboxAdminService` 256 行、`ImOutboxAdminQueryService` 166 行、`ImOutboxAdminMetricsService` 79 行。
- API 文档无需变更；本轮同步了 module-im 开发日志和后续开发计划。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 85 审 4-1，`ImOutboxAdminService` | 第 85 审指出 outbox 后台服务继续承担 summary、failure-summary、分页、详情、重试、派发和审计 detail。当前已拆出 query 与 metrics 服务，原服务只保留操作类能力，避免 admin-web 后续扩展继续堆到单类。 | 已处理 | 后续新增只读查询或指标时分别进入 query/metrics；状态变更和审计仍进入 operation 服务。 |
| 2-2 | 第 84 审 6-3，outbox 后台服务拆分 | 第 84 审要求下一轮后台 outbox 功能前先拆服务。本轮没有继续新增功能，而是先做职责收口，符合“先拆再扩”的治理顺序。 | 已处理 | 如果继续增加告警确认、忽略 DEAD、导出等能力，再按写操作、只读查询、指标聚合拆分落点。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AdminImOutboxController` | Controller 现在依赖三个服务。触发条件是后续新增固定路由时没有注意顺序；失败路径是 `/summary`、`/failure-summary`、`/audits` 等字符串路由被 `/{eventId}` 捕获，导致前端拿到参数类型错误而不是业务响应。当前代码仍把固定路由放在 `/{eventId}` 前，未发现回归。 | P2 | 后续新增固定路由继续放在 `/{eventId}` 前；必要时把详情路径改成 `/events/{eventId}` 降低路由歧义。 |
| 3-2 | `ImOutboxAdminQueryService.OUTBOX_EVENT_TYPES` | 查询服务复制维护 outbox 事件类型白名单。触发条件是 dispatcher 新增事件类型但未同步后台查询白名单；失败路径是新事件已经入库并可投递，但 admin-web 用事件类型筛选时报 `outbox事件类型不支持`。 | P2 | 后续把 outbox 事件类型收敛成共享枚举或常量集合，dispatcher、查询服务、API 文档和测试同时引用。 |
| 3-3 | `ImOutboxAdminMetricsService.summarizeOutboxEvents()` | 指标服务独立复用 `sending-stale-ms`，但 due/stale 判定仍依赖仓储 SQL。触发条件是 dispatcher 抢占规则未来加入租约 owner、分片或新状态；失败路径是 summary 展示的待派发数与实际派发抢占数不一致。 | P2 | dispatcher 抢占条件继续变化时，将 due 判定抽成共享 SQL 片段、数据库视图或专门策略类，避免指标口径漂移。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminRepository` | 服务层已经拆分，但仓储仍同时承载事件分页、详情、状态总览、失败聚合、状态重置、审计查询和审计写入 SQL。触发条件是 admin-web 继续补更多 outbox 图表或导出；失败路径是一个仓储内 SQL 类型混杂，后续改指标可能误伤审计或操作 SQL。 | P2 | 下一轮继续治理时拆成 `ImOutboxAdminEventRepository`、`ImOutboxAdminMetricsRepository`、`ImOutboxAdminAuditRepository`。 |
| 4-2 | `ImOutboxAdminService.toResponse(...)` 与 `ImOutboxAdminQueryService.toEventResponse(...)` | 拆分后事件 Row 到 Response 的转换在操作服务和查询服务中各保留一份。触发条件是响应字段后续新增或重命名；失败路径是一处转换漏改，导致单条重试响应和查询响应字段不一致。 | P2 | 抽出 package-private mapper 或让操作服务重试后委托 query service 转换，避免重复 DTO 映射。 |
| 4-3 | `ModuleImApiIntegrationTest` | outbox 后台用例仍集中在 2699 行的大集成测试类里。触发条件是继续补 outbox repository 拆分、导出、告警确认等测试；失败路径是单测试类初始化和定位成本继续上升，审查难以快速判断 outbox 专项覆盖。 | P2 | 拆 `ModuleImOutboxIntegrationTest`，把 outbox 派发、后台治理、审计、summary、failure-summary 迁出。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证服务拆分后的依赖注入、DTO、Repository 和测试编译。 |
| outbox 后台服务拆分目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_split_admin_verify_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds+adminShouldSummarizeImOutboxEvents+adminShouldGetImOutboxFailureSummary test` | 通过 | 覆盖单条重试、批量重试、主动派发、审计查询、状态总览和失败聚合。 |

未验证项：

- 没有跑全量基础回归；本轮是 outbox 后台服务拆分，接口契约和数据库结构未改变。
- 没有拆 repository，也没有为 repository 拆分增加单独边界测试。
- 没有拆 `ModuleImApiIntegrationTest`，测试组织层面的维护债仍在。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | outbox 后台仓储拆分 | 当前仓储 361 行，职责已经混合查询、指标、操作和审计。 | P2 | 下一轮维护治理优先拆 repository，避免后续 admin-web 接入时 SQL 继续膨胀。 |
| 6-2 | DTO 映射复用 | 操作服务和查询服务各有一份 event response 转换。 | P2 | 抽 mapper 或委托 query service，降低字段演进漏改风险。 |
| 6-3 | outbox 测试类迁移 | `ModuleImApiIntegrationTest` 已明显过大。 | P2 | 新建 `ModuleImOutboxIntegrationTest`，迁出 outbox 专项测试。 |

## 第七部分：阶段性结论

本轮服务拆分没有改变 outbox 后台 API、投递状态机、手动重试、批量重试、主动派发或审计语义，但把原先持续膨胀的 `ImOutboxAdminService` 拆成操作、查询、指标三个服务。当前没有发现新的 P0/P1 阻断项，后端 outbox 后台接口仍可供 admin-web 接入。

剩余风险集中在 P2 维护债：`ImOutboxAdminRepository` 仍需继续拆分，事件 DTO 映射存在重复，outbox 测试仍应从 IM 大测试类迁出。建议下一轮后端治理优先处理 repository 拆分，再进入新的后台能力扩展。
