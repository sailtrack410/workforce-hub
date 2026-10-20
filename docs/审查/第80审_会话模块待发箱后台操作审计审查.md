# 第 80 审 module-im outbox 后台操作审计审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 后台手动重试和主动派发操作审计）
- 审查范围：`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API文档_module-im会话接口.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台 outbox 手动补偿操作是否具备最小审计追踪，确认审计不会破坏既有重试/派发状态机；本轮不新增审计查询接口、不做批量重试、不启动 admin-web 页面。

---

## 第一部分：提交内容概览

本轮围绕 IM outbox 后台高风险操作补齐审计。

- `retryOutboxEvent(...)` 成功重置 `FAILED/DEAD` 事件后写入 `sys_audit_log`，动作类型为 `IM_OUTBOX_RETRY_REQUESTED`。
- `dispatchDueEvents(...)` 每次管理员主动派发后写入 `sys_audit_log`，动作类型为 `IM_OUTBOX_DISPATCH_TRIGGERED`。
- 审计详情 JSON 记录事件类型、会话 ID、消息 ID、重试前后状态、重试次数、原失败原因、请求批量大小和实际派发成功数量。
- `ModuleImApiIntegrationTest.adminShouldManageImOutboxEvents` 增加 `sys_audit_log` 断言，覆盖重试审计和派发审计。
- module-im API 文档、测试用例、开发日志和后续计划同步说明 outbox 后台操作会写审计。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 79 审 6-2，outbox 批量治理审计 | 第 79 审指出批量重试前需要先补操作审计，否则后台补偿不可追溯。当前单条重试和主动派发已经写入 `sys_audit_log`，记录管理员 ID、动作类型、目标和操作摘要。 | 已处理 | 后续批量重试仍需补批量请求级审计摘要，不应只依赖多条单事件审计。 |
| 2-2 | `docs/API与测试/module-im/API文档_module-im会话接口.md` | 旧文档只说明重试和派发行为，没有告诉 admin-web 这些操作会写审计。当前已补 `IM_OUTBOX_RETRY_REQUESTED` 和 `IM_OUTBOX_DISPATCH_TRIGGERED` 审计动作说明。 | 已处理 | 后续如新增审计查询接口，再把 outbox 操作审计纳入后台治理页面文档。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminService.retryOutboxEvent(...)` | 重试审计写在状态重置之后、同一个事务内。触发条件是审计插入失败，例如未来 detail_json 字段扩展生成非法 JSON；失败路径是重试状态更新会随事务回滚，管理员看到重试失败，事件仍保持原状态。当前 detail 使用 Jackson 生成合法 JSON，且目标测试覆盖成功写审计。 | P2 | 如后续希望“业务操作成功但审计失败也保留操作”，需要引入独立审计事务；当前更倾向于审计失败即整体失败，避免不可追溯补偿。 |
| 3-2 | `dispatchDueEvents(...)` | 主动派发审计记录的是 `requestedBatchSize` 和 `dispatchedCount`，没有列出具体 eventId 集合。触发条件是一次派发多个事件后需要逐条还原；失败路径是只能通过 outbox `sent_at/updated_at` 时间窗口辅助排查。当前接口仍用于演示和小规模排障。 | P2 | 后续批量治理前让 dispatcher 返回已处理 eventId 摘要，或新增批次号，把派发审计和具体事件关联起来。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImOutboxAdminService` | 本轮在服务中继续追加审计详情 JSON 拼装。触发条件是后续增加批量重试、失败聚合导出、忽略 DEAD 等操作；失败路径是服务类同时承担参数校验、状态机、审计详情和 DTO 转换。 | P2 | 后续扩展 outbox 后台操作前，拆 `ImOutboxAuditService` 或专用 detail builder。 |
| 4-2 | `ImOutboxAdminRepository.insertAudit(...)` | 当前新增了 outbox 后台专用审计写入方法，项目里其他模块也存在类似 `INSERT INTO sys_audit_log` 私有方法。重复写法暂不影响本轮行为，但长期会造成审计字段和 action 约定分散。 | P2 | 后续统一抽 `SystemAuditRepository` 或 `AuditLogService`，避免各模块复制审计 SQL。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增审计写入、Jackson detail 和测试断言可编译。 |
| outbox 后台治理、操作审计与失败重试组合 | `cd server && env TEST_DB_NAME=wfh_im_outbox_admin_audit_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 覆盖管理员查询、详情、重试、派发、员工越权、非法状态、审计落库和 dispatcher 失败重试。 |

未验证项：

- 没有跑全量基础回归；本轮只改 IM outbox 后台操作审计。
- 没有验证审计查询页面，因为 admin-web 尚未启动，当前只验证数据库审计记录。
- 没有覆盖审计插入失败路径；当前基于 Jackson 生成合法 JSON，风险较低。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | outbox 派发批次关联 | 当前审计只有派发数量，没有 eventId 明细。 | P2 | 批量治理或 admin-web 操作历史上线前，增加派发批次号或 eventId 摘要。 |
| 6-2 | 通用审计写入组件 | 多模块仍重复手写 `sys_audit_log` 插入 SQL。 | P2 | 后续统一抽通用审计仓储，避免 action 类型、target 类型和 detail JSON 分散。 |
| 6-3 | admin-web 操作历史 | 后端已有审计记录，但没有页面查看。 | P1 | admin-web 第一批治理页应展示 outbox 操作审计，至少能按 eventId 或 actionType 查最近操作。 |

## 第七部分：阶段性结论

本轮把 IM outbox 后台“可操作”推进到“可追溯”：管理员手动重试和主动派发都会写入 `sys_audit_log`，测试已验证审计记录随目标链路落库。外部 outbox API 契约没有破坏，失败重试和派发状态机仍通过目标回归。

当前没有发现新的 P0/P1 阻断项。剩余风险主要是派发审计缺少具体 eventId 批次关联，以及项目级审计写入 SQL 仍未统一抽象。
