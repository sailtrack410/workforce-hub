# 第 92 审 module-im 群治理仓储拆分审查

- 审查日期：2026-05-12
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（IM 群治理仓储拆分）
- 审查范围：`server/src/main/java/com/workforcehub/im/repository/ImGroupRepository.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/main/java/com/workforcehub/im/service/ImGroupService.java`、`docs/开发日志/module-im.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核群聊资料、群成员治理和群会话创建 SQL 是否从 `ImRepository` 拆入独立仓储，确认员工端 IM API、WebSocket/outbox 事件、错误语义和数据库迁移均未改变。本轮不新增业务能力、不新增接口、不新增 Flyway 迁移。

---

## 第一部分：提交内容概览

本轮承接第 91 审 4-1 的 P2 仓储拆分建议，处理 `ImRepository` 继续承载群治理 SQL 的维护风险。

- 新增 `ImGroupRepository`，集中承载群成员校验、群聊存在性、群资料读写、成员列表、成员退群/移除、群解散、群会话和群资料创建。
- `ImGroupService` 的群治理 SQL 调用切换到 `ImGroupRepository`。
- `ImRepository` 删除群治理专属 SQL 和迁出后无调用的成员 Row mapper，主仓储从约 1220 行降到约 891 行。
- `ImRepository` 继续保留当前员工准入、会话摘要读取、会话存在性、会话成员关系和推送收件人查询等共享能力。
- 员工端路由、请求/响应 DTO、WebSocket/outbox 事件类型、payload 和数据库结构均未改变。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 91 审 4-1，`ImRepository` 群治理 SQL | 第 91 审指出群治理服务已拆出，但群成员、群资料和群会话创建 SQL 仍堆在 `ImRepository`，后续改群治理仍会触碰消息、附件、私聊和 Agent 会话同一个大仓储。本轮已新增 `ImGroupRepository` 并迁移群治理 SQL，主仓储行数明显下降。 | 已处理 | 继续按聚合拆剩余会话、消息、附件 SQL，不把新的群治理 SQL 回填到 `ImRepository`。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImGroupRepository.countActiveOrgUsers(...)` | 群成员添加仍按 `org_user` 的员工状态判断可加入，未同步检查 `auth_user_account.account_status`。触发条件是某员工仍为在职状态但登录账号被禁用；失败路径是该用户可被加入群聊，但不能登录且不会进入 `listPushableActiveUserMemberIds(...)` 的推送收件人结果。该行为是拆分前既有语义，本轮没有放大为 P1，但仓储拆开后容易被误认为“群成员可加入”和“可推送/可登录”语义一致。 | P2 | 后续明确成员可加入规则：如果群成员必须可登录，则把账号状态校验纳入 `ImGroupRepository.countActiveOrgUsers(...)`；如果只要求在职员工，则在命名和文档中区分“可加入成员”和“可推送成员”。 |
| 3-2 | `ImGroupService.ensureCanAccessActiveGroup(...)` 与 `ImRepository.existsActiveUserMembership(...)` | 群服务已独立，但访问校验中的“用户是否为活跃会话成员”仍调用主 `ImRepository`。触发条件是后续群成员状态字段、软删除字段或成员可见性规则调整；失败路径是群治理写路径和访问判断读路径分散在两个仓储，可能出现一个仓储已更新条件、另一个遗漏的语义漂移。当前 `ModuleImGroupIntegrationTest` 覆盖群访问、退群和非群主权限边界，未发现行为回归。 | P2 | 下一步拆 `ImConversationRepository` 或 `ImMemberRepository`，把会话成员通用读写规则收敛到单一仓储，群服务通过明确的成员读接口复用。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 群治理 SQL 已迁出，但 `ImRepository` 仍约 891 行，继续同时承载会话列表、私聊/Agent 会话创建、消息、附件、下载票据、已读、偏好和 outbox 事件写入等职责。触发条件是继续接消息编辑、附件治理或会话列表排序需求；失败路径是不同聚合 SQL 混在一个文件里，字段演进时容易误改无关链路。 | P2 | 继续拆 `ImConversationRepository`、`ImMessageRepository`、`ImAttachmentRepository`，每轮只迁一类 SQL 并跑对应 IM 主测试。 |
| 4-2 | `ImGroupService.getGroupProfileForUser(...)` | 群资料读取通过 `findActiveGroupProfile(...)` 后再单独查询当前成员角色，两次读取之间没有锁。触发条件是并发退群或群主变更类能力进入后，资料和当前角色可能读到不同瞬间。当前尚未实现群主转让，且接口主要用于展示，不构成 P1。 | P2 | 后续如加入群主转让或成员角色管理，应把群资料和当前成员角色合并为一个读模型 SQL，保证展示语义来自同一快照。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 编译与测试编译 | `cd server && mvn -q -DskipTests compile test-compile` | 通过 | 验证新仓储 Bean、构造器注入、迁移后调用点和测试源码可编译。 |
| 群治理专项 | `cd server && env TEST_DB_NAME=wfh_im_group_repo_split_$(date +%s) mvn -q -Dtest=ModuleImGroupIntegrationTest test` | 通过 | 覆盖群创建、成员添加/移除、退群、解散、群资料和权限边界。 |
| IM 员工端主测试 | `cd server && env TEST_DB_NAME=wfh_im_api_group_repo_split_$(date +%s) mvn -q -Dtest=ModuleImApiIntegrationTest test` | 通过 | 覆盖会话、消息、附件、会话详情、Agent、WebSocket/outbox 和偏好主链路。 |

未验证项：

- 没有跑全量基础回归；本轮只拆 IM 群治理仓储。
- 没有新增迁移，未涉及空库、旧库、半成品库的额外数据库兼容验证。
- 没有更新 API 文档，因为接口路径、请求字段、响应字段和事件契约未变化。

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 继续拆 `ImRepository` | 主仓储仍约 891 行，超过仓储拆分阈值。 | P2 | 下一轮优先迁移会话列表/会话偏好或消息/附件 SQL，避免继续向主仓储加新能力。 |
| 6-2 | 成员可加入与可推送语义 | 目前群成员加入和推送收件人使用不同准入条件，行为未变但语义需要固化。 | P2 | 在后续成员仓储拆分时明确命名和测试，防止前端误解“加入群聊”等同于“可登录接收推送”。 |
| 6-3 | 会话成员通用读写边界 | 群服务和主仓储仍共同访问 `im_conversation_member`。 | P2 | 抽出成员读模型仓储或会话仓储，减少同表规则分散。 |

## 第七部分：阶段性结论

本轮完成第 91 审提出的群治理仓储拆分：`ImGroupRepository` 已承接群资料、群成员和群会话创建 SQL，员工端群接口和 IM 主链路测试均通过。当前没有发现 P0/P1 阻断项。

剩余风险集中在 P2：`ImRepository` 仍是大仓储，且 `im_conversation_member` 规则还分散在群仓储和主仓储之间。下一步应继续按会话、消息、附件或成员读模型拆分，而不是新增 IM 业务代码。
