# 第 87 审 module-im outbox 后台仓储细分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 后台仓储细分）
- 审查范围：`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminEventRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminMetricsRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminAuditRepository.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminQueryService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminMetricsService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminResponseMapper.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 后台仓储是否已从混合 SQL 大类拆成事件、指标、审计三个边界，并确认服务层调用、审计写入、查询分页、指标聚合和 DTO 映射行为不变。本轮不新增 API、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮继续偿还第 86 审留下的 P2 维护债：`ImOutboxAdminRepository` 仍混合事件、指标、操作和审计 SQL。

- 删除聚合式 `ImOutboxAdminRepository`。
- 新增 `ImOutboxAdminEventRepository`，承载 outbox 事件计数、分页、详情和手动重试状态重置。
- 新增 `ImOutboxAdminMetricsRepository`，承载状态分布、总览聚合和失败原因聚合。
- 新增 `ImOutboxAdminAuditRepository`，承载操作审计写入和审计分页查询。
- 新增 `ImOutboxAdminResponseMapper`，统一 outbox 事件 Row 到后台响应 DTO 的字段映射。
- `ImOutboxAdminService`、`ImOutboxAdminQueryService`、`ImOutboxAdminMetricsService` 改为依赖对应仓储，接口契约不变。
- 拆分后仓储行数为：event 165 行、metrics 101 行、audit 152 行；操作服务降到 238 行，查询服务降到 148 行。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 86 审 4-1，`ImOutboxAdminRepository` | 第 86 审指出仓储同时承载事件分页、状态总览、失败聚合、状态重置、审计查询和审计写入 SQL。当前已拆成 event、metrics、audit 三个仓储，SQL 按用途分组，后续修改指标不再直接碰审计和操作 SQL。 | 已处理 | 后续新增导出或告警确认时继续按读模型、指标、审计、操作选择落点。 |
| 2-2 | 第 86 审 4-2，事件响应 DTO 映射重复 | 第 86 审指出操作服务和查询服务各自维护 outbox event response 转换。当前新增 `ImOutboxAdminResponseMapper`，单条重试响应和查询响应复用同一映射。 | 已处理 | 后续新增 outbox event 响应字段只改 mapper，并同步 API 文档和测试断言。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminEventRepository.resetForAdminRetry(...)` | 仓储拆分后手动重试状态更新仍在 event 仓储。触发条件是未来 batch retry 或单条 retry 需要同时写审计和重置状态；失败路径是调用方如果绕过 `ImOutboxAdminService` 直接调用仓储，会产生无审计的状态变更。当前只有 service 调用，未发现越界调用。 | P2 | 保持仓储为包内受控依赖，所有后台人工操作必须经 `ImOutboxAdminService` 写审计；不要在 Controller 或其他服务直接调用 reset。 |
| 3-2 | `ImOutboxAdminMetricsRepository.summarizeOutboxEvents(...)` | 指标 SQL 从原仓储迁出后仍独立维护 due/stale 判断。触发条件是 dispatcher 抢占条件变化；失败路径是 metrics 仓储和 dispatcher 查询口径漂移。此次拆分未新增该风险，但仍需后续治理。 | P2 | 把 due/stale 判定提炼为共享 SQL 片段或视图；dispatcher 规则变更时强制同步 metrics 测试。 |
| 3-3 | `ImOutboxAdminAuditRepository.outboxAuditWhereSql(...)` | 审计按 eventId 查询依赖 `detail_json` 内数组字段名。触发条件是未来审计 detail 改名或新增动作但没有同步 JSON_CONTAINS 条件；失败路径是审计记录存在但按 eventId 反查不到。当前 batch retry、dispatch、single retry 字段名保持原样，目标用例已覆盖。 | P2 | 后续新增审计动作时同步更新白名单、JSON 查询条件和 `adminShouldManageImOutboxEvents` 或独立审计测试。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminQueryService.OUTBOX_EVENT_TYPES` | outbox 事件类型白名单仍在 query service 内硬编码。触发条件是新增 WebSocket 事件类型；失败路径是事件已入库但后台筛选不支持。 | P2 | 抽 `ImOutboxEventTypes` 常量或枚举，dispatcher、query service、API 文档共用同一来源。 |
| 4-2 | `ModuleImApiIntegrationTest` | outbox 后台测试仍在 IM 大测试类里。触发条件是继续补 repository 拆分、告警确认或导出测试；失败路径是单类继续增大，测试定位和并行拆分困难。 | P2 | 新建 `ModuleImOutboxIntegrationTest`，迁移 outbox 后台治理、summary、failure-summary、dispatch、audit 用例。 |
| 4-3 | `ImOutboxAdminAuditRepository` | 审计查询 JSON 条件较长，虽然已经独立仓储，但 SQL 仍难读。触发条件是继续扩展审计 detail 数组字段；失败路径是 JSON 查询条件遗漏或性能下降。 | P2 | 后续如果审计反查成为高频能力，建立 outbox 操作审计明细表或冗余 eventId 索引表，不再依赖 JSON_CONTAINS。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证仓储拆分、服务依赖注入和 mapper 编译通过。 |
| outbox 后台仓储细分目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_repo_split2_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents+adminBatchRetryOutboxEventsShouldValidateEventIds+adminShouldSummarizeImOutboxEvents+adminShouldGetImOutboxFailureSummary test` | 通过 | 覆盖事件分页/详情、单条/批量重试、主动派发、审计查询、状态总览和失败聚合。 |

未验证项：

- 没有跑全量基础回归；本轮仅拆分 IM outbox 后台仓储，未改跨模块业务链路。
- 没有做性能压测；审计 JSON 查询和 outbox 聚合 SQL 的大表性能仍需后续生产化评估。
- 没有拆出独立 `ModuleImOutboxIntegrationTest`。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | outbox 事件类型共享常量 | 当前硬编码白名单是后续新增事件时最容易漏改的点。 | P2 | 下一轮小治理抽常量，并补一个新事件类型查询白名单单测或集成断言。 |
| 6-2 | outbox 测试类迁移 | 测试类过大已经影响维护，但不阻断当前演示闭环。 | P2 | 在继续新增 outbox 后台功能前迁移到独立测试类。 |
| 6-3 | 审计 eventId 反查模型 | JSON_CONTAINS 可用但不适合长期高频查询。 | P2 | admin-web 审计查询变高频后补审计明细索引表。 |

## 第七部分：阶段性结论

本轮仓储细分把 outbox 后台 SQL 从一个混合 Repository 拆到事件、指标、审计三个边界，并统一了事件响应 DTO 映射。当前没有发现新的 P0/P1 阻断项，outbox 后台接口契约、状态机和审计语义保持不变。

剩余维护风险集中在事件类型白名单硬编码、outbox 测试类过大和审计 JSON 反查模型。建议下一轮后端收口优先处理事件类型共享常量或测试类拆分，再进入新的 admin-web 后台能力扩展。
