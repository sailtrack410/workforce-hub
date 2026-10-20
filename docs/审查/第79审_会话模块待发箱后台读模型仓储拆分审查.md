# 第 79 审 module-im outbox 后台读模型仓储拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM outbox 后台读模型仓储从投递仓储拆出）
- 审查范围：`server/src/main/java/com/workforcehub/im/websocket/ImEventOutboxRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImOutboxAdminRepository.java`、`server/src/main/java/com/workforcehub/im/repository/AdminImOutboxEventQueryCriteria.java`、`server/src/main/java/com/workforcehub/im/repository/AdminImOutboxEventRow.java`、`server/src/main/java/com/workforcehub/im/service/ImOutboxAdminService.java`、`docs/开发日志/module-im.md`、`docs/API与测试/module-im/API测试用例_module-im会话接口.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 78 审提出的 outbox 投递仓储与后台运营查询耦合问题是否收口，确认拆分没有改变 dispatcher 状态机和后台 API 契约；本轮不新增 outbox API、不拆 employee-web、不启动 admin-web 页面。

---

## 第一部分：提交内容概览

本轮只做 IM outbox 仓储边界治理。

- 新增 `ImOutboxAdminRepository`，承载后台 outbox 分页查询、详情查询和人工重试状态重置。
- 新增 `AdminImOutboxEventQueryCriteria` 和 `AdminImOutboxEventRow` 到 `im.repository` 包，作为后台读模型专用对象。
- `ImEventOutboxRepository` 移除后台查询、详情和人工重试 SQL，只保留 outbox 入账、claim、标记发送中、标记成功和标记失败等 dispatcher 投递状态机能力。
- `ImOutboxAdminService` 改为依赖 `ImOutboxAdminRepository`；`AdminImOutboxController`、DTO、URL、请求参数和响应结构不变。
- API 测试文档、开发日志、后续开发计划和审查 README 同步记录本轮拆分与验证结果。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 78 审 4-1，`ImEventOutboxRepository` | 第 78 审指出后台查询、人工重试 SQL 与 dispatcher 投递写模型混在同一仓储。触发条件是后续继续增加失败聚合、批量重试、租户隔离或审计导出；失败路径是后台读模型改动误伤 claim、markSending、markFailed 等状态机 SQL。当前已拆出 `ImOutboxAdminRepository`，`ImEventOutboxRepository` 只保留投递状态机。 | 已处理 | 后续 admin-web 扩展 outbox 页面时继续只改后台仓储，dispatcher 状态机改动必须单独审查。 |
| 2-2 | `docs/开发规划/后续开发计划与测试节点.md` | 计划中此前只写“部分 Repository 仍需拆分治理”，没有记录 outbox 后台读模型已经拆出。当前已补充 IM outbox 后台读模型拆分状态，避免后续重复把这块列为未做。 | 已处理 | 后续仍保留 `ImRepository`、`WorkflowRepository`、`AttendanceRepository` 等大仓储治理任务。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImOutboxAdminRepository.resetForAdminRetry(...)` | 人工重试状态更新从 `ImEventOutboxRepository` 移到后台仓储，但仍直接写 `im_event_outbox.status/next_retry_at/claimed_at/sent_at/last_error`。触发条件是后续 dispatcher 状态枚举或 stale reclaim 规则变化后，只改投递仓储忘记同步后台重试更新；失败路径是后台把事件重置成 dispatcher 不再接受的状态。当前状态枚举未变化，目标测试覆盖 `FAILED -> SENT` 重试链路。 | P2 | 后续如扩展 outbox 状态机，应把可人工重试状态、重置目标状态和 dispatcher 可 claim 状态抽成共享常量或状态机组件。 |
| 3-2 | `ImOutboxAdminRepository.pageOutboxEvents(...)` | 后台分页仍按 `created_at DESC, id DESC` 排序，不提供稳定游标。触发条件是 admin-web 操作大量 outbox 事件时一边翻页一边有新事件写入；失败路径是后台列表页可能出现翻页重复或遗漏。当前运营页面尚未启动，演示排障和低频查询可接受。 | P2 | admin-web 若需要长列表连续排障，应新增游标分页或固定时间窗口查询。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ModuleImApiIntegrationTest` | 本轮没有拆第 78 审 4-2 提出的 IM 大测试类问题。触发条件是继续补 outbox DEAD、stale SENDING、批量重试和 admin-web 契约；失败路径是单类测试继续膨胀，局部回归定位成本变高。 | P2 | 下一次 outbox 功能扩展前拆 `ModuleImOutboxIntegrationTest`，把后台 outbox 和 dispatcher 失败重试用例迁出主 IM 员工端测试类。 |
| 4-2 | `ImOutboxAdminService`、`ImOutboxAdminRepository` | 后台服务仍同时负责参数标准化、状态校验、payload 关键字段校验和 DTO 转换。当前逻辑量不大，但批量重试、失败聚合和审计导出接入后会继续增长。 | P2 | admin-web 第二批 outbox 功能前，拆出 payload 校验器和状态策略，避免 service 继续承载所有运营规则。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新增仓储、Row、Criteria 和服务依赖调整可编译。 |
| outbox 后台治理与失败重试组合 | `cd server && env TEST_DB_NAME=wfh_im_outbox_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest#adminShouldManageImOutboxEvents,ModuleImApiIntegrationTest#websocketOutboxShouldRetryFailedDelivery test` | 通过 | 覆盖管理员列表、详情、非法状态、员工越权、重试、主动派发和 dispatcher 失败后重试到 `SENT`。 |

未验证项：

- 没有跑全量基础回归；本轮只移动 IM outbox 后台仓储边界，没有改 auth/org/file/workflow/leave/attendance 代码。
- 没有重跑 IM 全类和 IM/Agent 组合全套；第 78 审已记录 48 个 IM 用例和 95 个 IM/Agent 组合用例通过，本轮补了直接受影响目标用例。
- 没有做真实浏览器 WebSocket 联调；当前仍通过 Mock WebSocketSession 验证 outbox 投递路径。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | `ModuleImOutboxIntegrationTest` 拆分 | 仓储边界已拆，但测试仍集中在 `ModuleImApiIntegrationTest`。 | P2 | 下一次 outbox 扩展先拆测试类，再加批量治理或 DEAD 场景。 |
| 6-2 | outbox 批量治理审计 | 当前后台只支持单条重试，且没有单独记录管理员操作审计。 | P2 | 批量重试前先设计操作审计表或复用后台审计机制，避免批量补偿不可追溯。 |
| 6-3 | admin-web outbox 页面 | 后端 API 和仓储边界已具备接入条件。 | P1 | admin-web 第一批页面应接 `FAILED/DEAD` 列表、详情、重试二次确认和 dispatch 操作。 |

## 第七部分：阶段性结论

本轮把 IM outbox 后台读模型从 dispatcher 投递仓储中拆出，收口了第 78 审指出的 Repository 职责耦合问题。外部 API 契约没有变化，目标测试验证后台查询、重试、主动派发和 dispatcher 失败重试链路仍然可用。

当前没有发现新的 P0/P1 阻断项。剩余后端治理重点是 outbox 测试类拆分、批量治理审计、以及 admin-web 页面接入前的操作确认语义。
