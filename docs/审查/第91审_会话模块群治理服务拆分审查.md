# 第 91 审 module-im 群治理服务拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM 群治理服务拆分）
- 审查范围：`server/src/main/java/com/workforcehub/im/service/ImGroupService.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/controller/EmployeeImController.java`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核群聊创建、群资料、成员治理、退群和解散是否从 `ImService` 拆入独立服务，确认员工端 API、WebSocket/outbox 事件和错误语义不变。本轮不新增接口、不新增数据库迁移、不调整前端契约。

---

## 第一部分：提交内容概览

本轮处理 `ImService` 超阈值后的职责拆分问题。

- 新增 `ImGroupService`，承载群聊创建、成员列表、群资料读取/更新、成员添加/移除、退群和解散。
- `EmployeeImController` 的 `/api/employee/im/group-conversations/**` 路由改为调用 `ImGroupService`，路径和请求/响应 DTO 不变。
- `ImService` 删除群治理公开方法和私有辅助，主类从约 1033 行降到约 793 行。
- `ImService.getCurrentEmployeeConversationDetail(...)` 仍需展示群资料和成员列表，因此通过同包方法复用 `ImGroupService` 的读模型转换。
- 群治理 `IM_GROUP_PROFILE_UPDATED`、`IM_GROUP_MEMBERS_ADDED`、`IM_GROUP_MEMBERS_REMOVED`、`IM_GROUP_MEMBER_LEFT`、`IM_GROUP_DISMISSED` 事件仍走现有 WebSocket outbox。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImService` 群治理职责 | 之前 `ImService` 同时承担消息主链路、附件、偏好、Agent 和群治理，已经超过 1000 行。当前群治理写路径已迁入 `ImGroupService`，员工端群接口不再直接扩张主消息服务。 | 已处理 | 后续继续拆 `ImMessageService` 或 `ImConversationService`，避免新的职责回流到 `ImService`。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImService.getCurrentEmployeeConversationDetail(...)` 调用 `ImGroupService` 同包读方法 | 会话详情仍需要群成员和群资料，因此主服务和群服务之间保留一个同包读模型依赖。触发条件是未来群资料读取权限或成员展示名降级规则变化；失败路径是群接口和会话详情读到的成员/资料语义不一致。当前 `ModuleImApiIntegrationTest` 和 `ModuleImGroupIntegrationTest` 已覆盖会话详情和群资料读取。 | P2 | 下一步拆 `ImConversationService` 时，把会话详情组装迁出 `ImService`，并把成员/群资料读模型收敛成明确的只读协作接口。 |
| 3-2 | `ImGroupService.toConversationSummaryResponse(...)` | 群创建后只需要返回新会话摘要，当前复制了 `ImService` 的摘要转换逻辑，但没有填充 `lastMessage`。触发条件是未来群创建响应需要展示最后消息；失败路径是两个转换器字段演进不一致。当前新群创建本身没有最后消息，行为未变化。 | P2 | 后续抽 `ImConversationResponseMapper`，让会话摘要转换在一个地方维护。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 虽然群治理服务已拆出，但 SQL 仍集中在 `ImRepository`，该仓储约 1220 行，继续扩展会让群成员、消息、附件、会话列表和 Agent 会话 SQL 混在一起。 | P2 | 下一轮按聚合拆出 `ImGroupRepository` 或 `ImConversationRepository`，先迁移群成员与群资料 SQL。 |
| 4-2 | `EmployeeImController` | Controller 现在同时依赖 `ImService` 和 `ImGroupService`，路由层开始暴露服务拆分边界。当前接口清晰，但继续拆消息/会话服务后构造函数会继续膨胀。 | P3 | 后续可按员工端子域拆 controller，或引入薄门面服务统一员工 IM 路由编排。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新服务注入、同包读方法和 controller 依赖可编译。 |
| 群治理专项 | `cd server && env TEST_DB_NAME=wfh_im_group_service_split_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test` | 通过 | 覆盖群成员、群资料、创建、添加/移除、退群、解散和权限边界。 |
| IM 员工端主测试 | `cd server && env TEST_DB_NAME=wfh_im_api_group_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 覆盖会话详情、消息、附件、Agent、普通 WebSocket 和偏好主链路。 |

未验证项：

- 没有跑全量基础回归；本轮只拆 IM 群治理服务。
- 没有拆 `ImRepository`，SQL 职责边界仍待治理。
- 没有新增 API 文档，因为接口路径、请求和响应契约未改变。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | `ImRepository` 拆分 | 群服务已独立，但仓储仍是大类，后续改群成员 SQL 仍会触碰消息/附件仓储文件。 | P2 | 下一轮优先拆 `ImGroupRepository`，只迁移群资料和成员治理 SQL。 |
| 6-2 | 会话响应 mapper | `ImService` 和 `ImGroupService` 现在各自维护会话摘要转换。 | P2 | 拆 `ImConversationService` 时统一 mapper。 |
| 6-3 | Controller 继续膨胀 | 当前只新增一个服务依赖，风险低。 | P3 | 后续再评估是否拆 controller。 |

## 第七部分：阶段性结论

本轮把群治理从 `ImService` 拆入 `ImGroupService`，员工端群接口路径和响应保持不变，群治理专项和 IM 主测试均通过。当前没有发现 P0/P1 阻断项。

剩余风险集中在 P2：`ImRepository` 仍是大仓储，群服务和主服务之间仍共享部分会话/群资料响应转换。下一步后端治理应继续按仓储和会话/消息服务边界拆分，而不是继续向 `ImService` 回填新职责。
