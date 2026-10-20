# 第 82 审 module-im outbox 操作审计查询接口审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 操作审计查询接口）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/AdminImOutboxController.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxAuditQuery.java`、`server/src/main/java/com/workforcehub/im/dto/AdminImOutboxAuditResponse.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 操作审计从“只写入”升级到“后台可查询”后的接口契约、权限边界、路径匹配、过滤条件和测试覆盖；本轮不审查通用审计中心、不新增批量重试或 outbox 批次表。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 操作审计查询闭环收口。

- `AdminImOutboxController` 新增 `GET /api/admin/im/outbox-events/audits`，且路由放在 `/{eventId}` 前，避免 `audits` 被解析成事件 ID。
- `ImOutboxAdminService.pageOutboxAudits(...)` 复用后台管理员访问边界，限制审计动作只允许 `IM_OUTBOX_RETRY_REQUESTED` 和 `IM_OUTBOX_DISPATCH_TRIGGERED`。
- `ImOutboxAdminRepository` 新增 `sys_audit_log` 分页查询 SQL，按 `operator_type='ADMIN'` 和 outbox action 白名单约束范围。
- `eventId` 查询同时匹配 `target_id`、`attemptedEventIds`、`dispatchedEventIds`、`failedEventIds`，覆盖单条重试和主动派发两类审计。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加按 `eventId` 查询审计和非法 `actionType` 断言。
- module-im API 文档、测试用例、开发日志和后续开发计划同步补充审计查询接口。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 81 审 6-3，admin-web 操作历史 | 第 81 审指出 admin-web 操作历史需要后端可展示的数据入口。当前已新增 `GET /api/admin/im/outbox-events/audits`，可以按 outbox 事件 ID 查询重试和派发审计，前端不需要直连通用审计表。 | 已处理 | admin-web 第一批页面可直接接入该接口展示 outbox 操作历史。 |
| 2-2 | 第 80 审 3-2、第 81 审 2-1，派发审计明细 | 前轮已经让派发审计包含 eventId 明细，本轮把这些明细纳入查询条件，按单个事件可以查到主动派发审计。 | 已处理 | 如果后续引入批次表，再把查询从 JSON detail 升级到批次明细表。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AdminImOutboxController.listAudits(...)` | 审计查询路由必须位于 `/{eventId}` 前。当前代码顺序正确；如果后续重排 Controller 方法或新增更宽泛的路径，`/audits` 可能被路径变量吞掉，表现为事件详情查询失败或返回 `400/404`。 | P2 | 保持固定路由优先于路径变量，并在集成测试中持续覆盖 `/api/admin/im/outbox-events/audits`。 |
| 3-2 | `ImOutboxAdminRepository.outboxAuditWhereSql(...)` | 按 `eventId` 查询派发审计依赖 `detail_json` 中的 `attemptedEventIds`、`dispatchedEventIds`、`failedEventIds`。触发条件是未来调整审计 detail 字段名或压缩 eventId 摘要；失败路径是重试审计仍可查到，但派发审计会静默漏查，admin-web 误判事件没有被派发过。 | P2 | 批量治理或 detail schema 改动前新增持久批次明细表，或把 detail 字段名集中成常量并补回归测试。 |
| 3-3 | `ImOutboxAdminService.toAuditCriteria(...)` | 当前 action 白名单只允许两类 outbox 操作审计。触发条件是后续新增忽略、批量重试、强制标记 DEAD 等动作但未同步白名单；失败路径是审计已写入但接口返回 `outbox审计动作不支持`，admin-web 无法筛选新动作。 | P2 | 新增 outbox 操作时必须同步 action 白名单、API 文档和测试用例。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminRepository` | 仓储现在承载 outbox 事件查询、重试状态重置、审计写入和审计查询四类 SQL。虽然已经从 dispatcher 写模型中拆出，但后台读模型继续扩展批量重试、忽略、导出后仍会膨胀。 | P2 | 下一次新增 outbox 批量治理前拆出 `ImOutboxAuditRepository` 或 `ImOutboxOperationRepository`。 |
| 4-2 | `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` | 单个测试方法同时验证查询、详情、非法状态、员工越权、重试、派发、审计写入和审计查询。触发条件是继续补 outbox DEAD、批量重试或筛选项；失败路径是测试方法过长，失败定位和 fixture 维护成本上升。 | P2 | 下一个 outbox 功能扩展前拆 `ModuleImOutboxIntegrationTest`，按查询、重试、派发、审计分组。 |
| 4-3 | `sys_audit_log.detail_json` JSON 查询 | 审计查询通过 JSON path 检索数组，短期避免新增表结构，但长期不适合作为高频运营列表的主索引。 | P2 | 如果 admin-web 需要高频按事件反查操作历史，应新增 outbox 操作批次或明细表并建立普通索引。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增 DTO、Repository 行对象和 Controller 路由可编译。 |
| outbox 操作审计查询目标用例 | `cd server && env TEST_DB_NAME=wfh_im_outbox_audit_api_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test` | 通过 | 覆盖按 eventId 查询重试与派发审计、非法 actionType、员工越权、重试和派发主链路。 |

未验证项：

- 没有跑全量基础回归；本轮改动集中在 IM outbox 后台治理接口和文档。
- 没有压测 `JSON_CONTAINS` 按 eventId 反查审计的性能。
- 没有新增 admin-web 页面，当前只提供后端接口。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | outbox 审计查询接口 | 后端接口、过滤、权限和目标测试已补齐。 | P0 | 目标测试通过后可作为 admin-web outbox 操作历史接口接入。 |
| 6-2 | outbox 批次模型 | 当前按 JSON detail 支撑单事件追踪，足够演示和轻量排障。 | P2 | 批量重试或高频运营前新增批次表，避免把 JSON 审计当长期查询模型。 |
| 6-3 | outbox 测试类拆分 | `ModuleImApiIntegrationTest` 已继续膨胀。 | P2 | 下一次 outbox 后端功能前先拆测试，降低维护风险。 |

## 第七部分：阶段性结论

本轮把 IM outbox 操作审计从“可写入、可落库”推进到“后台可查询、可按事件追踪”。接口权限仍走后台管理员边界，查询范围限定在 outbox 两类动作，不暴露通用审计日志；路径顺序和目标测试覆盖了 `/audits` 与 `/{eventId}` 的冲突风险。

当前没有发现新的 P0/P1 阻断项。剩余风险主要是 JSON detail 查询不适合长期高频运营、outbox 批量治理缺少批次模型，以及 IM outbox 测试继续堆在主集成测试类里。
