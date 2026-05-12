# 第 89 审 module-im outbox 审计动作常量收敛审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 审计动作常量收敛）
- 审查范围：`server/src/main/java/com/workforcehub/im/support/ImOutboxAdminAuditActions.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminQueryService.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminAuditRepository.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 后台审计 action type 是否从写入、查询白名单和 SQL 过滤中的分散字符串收敛为共享常量，并确认审计写入、按动作筛选、按 eventId 反查语义不变。本轮不新增 API、不新增审计动作、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮处理第 88 审剩余的审计 action type 常量化问题。

- 新增 `ImOutboxAdminAuditActions`，集中定义 `IM_OUTBOX_RETRY_REQUESTED`、`IM_OUTBOX_BATCH_RETRY_REQUESTED`、`IM_OUTBOX_DISPATCH_TRIGGERED`。
- `ImOutboxAdminService` 写审计时引用共享常量。
- `ImOutboxAdminQueryService` 的 `actionType` 白名单复用 `SUPPORTED_ACTION_TYPES`。
- `ImOutboxAdminAuditRepository` 的 `action_type IN (...)` 改为参数化占位并绑定共享常量，避免 SQL 字符串中继续硬编码动作值。
- 常量类放在 `im.support` 包，避免 repository 依赖 service 层。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 88 审 4-1，`ImOutboxAdminQueryService` 审计 action type | 第 88 审指出操作审计 action type 仍在查询服务内硬编码。当前写审计、查审计白名单和审计 SQL 过滤都复用 `ImOutboxAdminAuditActions`，新增动作时不再需要三处同步字符串。 | 已处理 | 后续新增 outbox 操作审计动作时扩展同一常量类，并补查询和审计反查测试。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminAuditRepository.outboxAuditWhereSql(...)` | SQL 从字面量 `IN ('...')` 改为 `IN (?, ?, ?)` 参数绑定。触发条件是参数顺序与 SQL 占位顺序不一致；失败路径是审计列表漏出或漏查动作。当前参数顺序与常量顺序一致，目标用例覆盖重试、派发和批量重试审计查询。 | P2 | 后续如果动作数量继续增加，改为动态生成占位符，避免固定三个占位与集合数量不一致。 |
| 3-2 | `ImOutboxAdminAuditActions.SUPPORTED_ACTION_TYPES` | 常量集合同时被查询服务和审计 SQL 使用。触发条件是未来新增动作常量但忘记加入集合；失败路径是写审计可引用新常量，但查询仍不支持。 | P2 | 新增动作必须同时加入 `SUPPORTED_ACTION_TYPES`，并在测试里按 `actionType` 查询新动作。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ModuleImApiIntegrationTest` | 测试仍在 IM 大类中覆盖 outbox 后台审计。常量收敛后行为保护存在，但测试组织仍不理想。 | P2 | 继续推进 `ModuleImOutboxIntegrationTest` 拆分，把 outbox 后台治理用例迁出。 |
| 4-2 | `ImOutboxAdminAuditRepository` | eventId 反查仍通过 JSON_CONTAINS 扫 detail_json。常量化不解决高频查询性能问题。 | P2 | admin-web 审计查询高频后，补 outbox 操作审计明细索引表。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证常量移动到 support 包后，service、query service、repository 引用关系可编译。 |
| outbox 审计动作目标用例 | `cd server && env TEST_DB_NAME=wfh_im_audit_actions_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents test` | 通过 | 覆盖单条重试审计、主动派发审计、批量重试审计、按 actionType 查询和按 eventId 反查。 |

未验证项：

- 没有跑全量基础回归；本轮只调整 outbox 后台审计动作常量。
- 没有调整审计 detail JSON 存储模型。
- 没有拆出独立 outbox 集成测试类。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 动态审计动作 SQL | 当前固定三个占位足够现有动作，但新增动作时还要改 SQL。 | P2 | 动作继续增加时用集合动态生成占位符，或将支持动作下推到 criteria。 |
| 6-2 | outbox 测试类迁移 | 当前最大剩余维护债。 | P2 | 下一轮优先拆 `ModuleImOutboxIntegrationTest`。 |

## 第七部分：阶段性结论

本轮把 outbox 后台操作审计动作从分散字符串收敛为共享常量，并修正为 support 层常量，避免 repository 反向依赖 service。当前没有发现新的 P0/P1 阻断项，审计写入、按动作筛选和按 eventId 反查语义保持不变。

剩余风险集中在 P2：审计 SQL 仍是固定三个占位，outbox 专项测试仍未迁出大测试类，审计 detail JSON 反查模型仍不适合高频生产查询。
