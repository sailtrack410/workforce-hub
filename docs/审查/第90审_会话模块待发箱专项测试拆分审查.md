# 第 90 审 module-im outbox 专项测试拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 专项集成测试拆分）
- 审查范围：`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/im/ModuleImOutboxIntegrationTest.java`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 outbox 失败重试和后台治理集成测试是否从员工端 IM 主测试类中拆出，确认测试覆盖没有丢失、API 契约没有变化。本轮不新增业务接口、不新增数据库迁移、不调整 WebSocket 事件结构。

---

## 第一部分：提交内容概览

本轮处理第 89 审遗留的 outbox 专项测试类迁移问题。

- 新增 `ModuleImOutboxIntegrationTest`，集中覆盖 WebSocket 推送失败重试、后台 outbox 查询/详情、手动重试、主动派发、批量重试、操作审计查询、状态总览、失败原因聚合和后台权限校验。
- `ModuleImApiIntegrationTest` 移除 outbox 后台治理专项用例，继续保留员工端会话、消息、附件、编辑撤回、已读、偏好、Agent 会话和普通 WebSocket 推送用例。
- outbox 专用 seed 数据、批量重试参数构造、失败一次 WebSocket 会话迁入新测试类。
- `ModuleImApiIntegrationTest` 保留通用 WebSocket 断言和 outbox `SENT` 等待方法，因为普通 IM 推送测试仍需要验证事件最终派发。
- 同步更新 `module-im` 测试文档、开发日志和后续开发计划中的测试类清单。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 89 审 4-1，`ModuleImApiIntegrationTest` | 第 89 审指出 outbox 后台审计、重试和聚合测试仍堆在 IM 大类中。当前 `adminShouldManageImOutboxEvents`、`adminBatchRetryOutboxEventsShouldValidateEventIds`、`adminShouldSummarizeImOutboxEvents`、`adminShouldGetImOutboxFailureSummary` 以及失败重试用例已经迁入 `ModuleImOutboxIntegrationTest`，主测试类不再承担 outbox 后台治理专项。 | 已处理 | 后续 outbox 新能力优先补在 `ModuleImOutboxIntegrationTest`，不要回塞到 `ModuleImApiIntegrationTest`。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ModuleImOutboxIntegrationTest` 与 `ModuleImApiIntegrationTest` 的 WebSocket 测试辅助类 | 拆分后两个测试类各自维护 `CapturingWebSocketSession`、注册/注销和事件断言逻辑。触发条件是未来 WebSocket payload 或 session attribute 规则变化；失败路径是只改其中一个测试类，导致另一个类继续按旧协议断言，测试保护出现偏差。当前行为未受影响，因为两个类都通过编译和目标测试。 | P2 | 后续新增第三个 WebSocket 相关测试类前，抽出 `ImWebSocketIntegrationTestSupport` 或 package-private 测试 fixture，统一 session 构造和事件断言。 |
| 3-2 | `ModuleImOutboxIntegrationTest.adminShouldManageImOutboxEvents` | 该用例仍是一个较长的端到端组合测试，串联查询、详情、非法状态、权限、单条重试、派发、审计、批量重试和审计反查。触发条件是其中任一子能力失败；失败路径是单个用例失败后定位成本仍高。拆出测试类降低了类级别膨胀，但方法级职责还可以继续细化。 | P2 | 下一次改 outbox 后台接口时，把单条重试/派发审计、批量重试、审计查询拆成独立用例，保留一个轻量 happy path 组合用例。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ModuleImOutboxIntegrationTest.seedOutboxSummaryEvents(...)`、`seedOutboxFailureSummaryEvents(...)` | outbox 汇总类测试仍通过固定 ID 和较长 SQL 手工造账本。触发条件是 `im_event_outbox` 字段新增或默认值调整；失败路径是每个 seed SQL 都需要手工同步。 | P2 | outbox 表继续扩展后，补测试数据 builder 或 repository-level fixture，减少长 SQL 复制。 |
| 4-2 | `docs/开发规划/后续开发计划与测试节点.md` | 固定 IM/Agent 回归命令已新增 `ModuleImOutboxIntegrationTest` 单独行，但部分历史验证记录仍保留旧的 `ModuleImApiIntegrationTest#outbox...` 命令。历史记录不应改写，但新开发者可能误以为 outbox 目标用例仍在主类中。 | P3 | 后续只在“最近一次验证”和固定命令中使用新测试类；历史记录保持不可变。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增测试类、主测试类删除 outbox 专项辅助方法后仍可编译。 |
| outbox 专项测试类 | `cd server && env TEST_DB_NAME=wfh_im_outbox_split_tests_$(date +%s) mvn -q -Dtest=ModuleImOutboxIntegrationTest test` | 通过 | 覆盖 outbox 失败重试、后台查询/重试/派发/审计/批量重试/状态总览/失败聚合。日志中 `simulated websocket failure` 为用例故意触发的失败路径。 |
| IM 员工端主测试类 | `cd server && env TEST_DB_NAME=wfh_im_api_after_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 验证迁出 outbox 专项后，员工端 IM、Agent、普通 WebSocket 和附件等主链路测试仍通过。 |

未验证项：

- 没有跑全量基础回归；本轮只拆分测试组织，不改业务代码。
- 没有跑 `ModuleImGroupIntegrationTest` 和 Agent 组合回归；这两类接口契约未改。
- 没有抽出共享 WebSocket 测试 fixture。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | WebSocket 测试 fixture 抽取 | 当前只有两个测试类复制，暂不阻断；继续增加 WebSocket 专项测试时会放大维护成本。 | P2 | 在新增 `ModuleImWebSocketDispatchIntegrationTest` 前先抽测试 support。 |
| 6-2 | outbox 后台组合用例拆小 | 当前目标测试能保护完整闭环，但方法过长。 | P2 | 下一轮改后台 outbox 接口时顺手拆方法，不单独为拆测试方法改业务。 |
| 6-3 | 全量 IM/Agent 组合回归 | 当前已跑主类和 outbox 专项，未跑组合回归。 | P3 | 收口提交前再跑固定 IM/Agent 组合命令。 |

## 第七部分：阶段性结论

本轮完成 outbox 专项集成测试拆分，`ModuleImApiIntegrationTest` 不再继续承载后台 outbox 治理用例，测试边界比第 89 审更清晰。当前没有发现 P0/P1 阻断项，API、WebSocket 事件、审计动作和数据库结构均未改变。

剩余风险集中在 P2：WebSocket 测试辅助类仍有复制，`adminShouldManageImOutboxEvents` 方法内部仍偏长。建议进入下一后端治理节点前保留这两个事项，但不阻塞当前演示闭环。
