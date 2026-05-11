# 第 69 审 module-im 群治理后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 群治理闭环：群资料读取/维护、普通成员退群、群主解散、自定义群治理事件推送）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核群聊从“创建、拉人、移人”补齐到“资料、公告、退群、解散”的前端可接入闭环，重点看群主权限、普通成员边界、解散后的可见性、实时事件和测试保护。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 自定义群治理补齐后端闭环。

- 新增 `ImGroupProfileResponse`，返回群名称、头像文件 ID、群类型、群主、公告、状态和当前成员角色。
- 新增 `UpdateGroupConversationRequest`，支持群主维护群名称和群公告。
- `EmployeeImController` 新增群资料读取、群资料维护、主动退群、解散群聊接口。
- `ImRepository.findActiveGroupProfile(...)` 查询活跃群资料，并继续绑定活跃会话与活跃群资料状态。
- `ImRepository.updateGroupProfile(...)` 更新 `im_conversation.title` 和 `im_group_profile.notice_text`。
- `ImRepository.markUserMemberLeft(...)` 支持普通成员主动退群，且不允许群主通过该路径离开。
- `ImRepository.dismissGroupConversation(...)` 同步把会话、群资料和所有活跃成员置为解散/离开状态。
- `ImService` 保持第一版权限规则：群成员可读取资料，只有群主可维护资料、拉人、移人和解散，普通成员可主动退群。
- 群资料更新、拉人、移人、退群、解散都会通过已补的 WebSocket 推送轻量事件，便于前端刷新群资料和成员列表。
- `ModuleImGroupIntegrationTest` 新增群资料读取、群主维护资料、普通成员拒绝维护、普通成员退群、群主拒绝直接退群、群主解散、普通成员拒绝解散等覆盖。

当前后端已经能支撑前端演示中的自定义群主链路：创建群、查看群成员、查看群资料、维护群公告、拉人、移人、普通成员退群、群主解散。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `EmployeeImController.groupProfile(...)`、`ImService.getCurrentEmployeeGroupProfile(...)` | 群资料此前没有员工端读取入口，前端只能拿会话摘要，无法展示公告、群主和当前成员角色。当前新增资料接口，且仅群成员可读取。 | 已处理 | 前端群详情页优先调用 profile 接口，不要从会话标题推断群资料。 |
| 2-2 | `EmployeeImController.updateGroupProfile(...)`、`ImService.updateCurrentEmployeeGroupProfile(...)` | 群公告和群名称此前无法维护。当前仅群主可更新，普通成员返回 403，避免成员越权修改群资料。 | 已处理 | 后续如增加管理员或群管理员权限，应显式扩展角色判断，不要放宽为所有成员可改。 |
| 2-3 | `EmployeeImController.leaveGroup(...)`、`ImRepository.markUserMemberLeft(...)` | 普通成员此前没有主动退群入口。当前普通成员可把自己的成员状态置为 `LEFT`，群主不能走退群路径，避免自定义群没有 OWNER。 | 已处理 | 下一步如果要支持群主退群，应先补群主转让能力。 |
| 2-4 | `EmployeeImController.dismissGroup(...)`、`ImRepository.dismissGroupConversation(...)` | 开发规划中的“解散群”此前没有后端入口。当前群主可解散自定义群，解散后会话状态为 `DISMISSED`、群资料状态为 `DISMISSED`，成员全部离开。 | 已处理 | 前端收到解散事件后应退出群详情页并刷新会话列表。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImService.leaveCurrentEmployeeGroupConversation(...)` | 群主不能直接退群，只能解散群聊。演示上可接受，但真实协作里群主可能只想退出并转让群主，如果当前强制解散，会造成群成员和历史协作空间被一起关闭。 | P2 | 补“转让群主”接口后允许原群主退群；退群前强制校验存在新 OWNER。 |
| 3-2 | `ImRepository.dismissGroupConversation(...)` | 解散群时直接更新会话、群资料和成员状态，但没有写系统消息或审计日志。用户刷新后知道群不可用，却无法在消息流里看到“谁解散了群”。 | P2 | 写入一条 `SYSTEM` 消息或审计日志，并通过 WebSocket 推送系统事件。 |
| 3-3 | `ImService.updateCurrentEmployeeGroupProfile(...)` | 群资料更新只支持名称和公告，头像文件 ID 暂未接入文件授权。前端如果要演示群头像，目前没有后端入口。 | P3 | 后续复用 `module-file` 的头像文件引用校验，单独接 `avatarFileId` 更新。 |
| 3-4 | `ImRepository.markUserMembersLeft(...)` | 群主移人时对请求中的非成员 ID 不报错，只会无影响更新。批量治理演示没问题，但真实管理台可能需要知道哪些人未在群内。 | P3 | 如果前端需要精确提示，返回移除成功/未在群内的明细结果。 |
| 3-5 | 群治理 WebSocket 事件 | 群治理事件 payload 直接复用成员列表或资料 DTO，没有事件版本，也没有系统消息序列。前端可以刷新，但断线后无法按事件序列补偿。 | P2 | 接 outbox 或事件序列后，为群治理事件补 `eventVersion` 和可补拉的事件 ID。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 群资料、群成员、消息、附件、实时推送继续集中在一个服务类中。虽然当前演示推进快，但后续组织群、群主转让和禁言会继续推高复杂度。 | P2 | 下一阶段拆 `ImGroupService`，把群资料、成员治理、退群、解散和系统消息集中到群服务。 |
| 4-2 | `ImRepository` | 新增群资料和解散 SQL 后，单一仓储同时覆盖会话列表、消息、附件、群资料、成员和下载票据。SQL 风险定位成本继续上升。 | P2 | 拆 `ImGroupRepository` 和 `ImConversationRepository`，降低后续改群 SQL 时误伤消息 SQL 的概率。 |
| 4-3 | 权限错误文案 | `ensureCanManageCustomGroup(...)` 复用“只有群主可以管理群成员”文案，资料维护和解散也会返回同一句。行为正确，但前端展示不够精确。 | P3 | 拆成更明确的错误文案，例如“只有群主可以维护群资料”“只有群主可以解散群聊”。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只处理 IM 群治理、测试和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 68 审，本轮新增第 69 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node69_im_group_governance mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖会话、分页、实时推送、附件、编辑撤回、群资料、退群、解散和群成员治理。 |
| 差异格式检查 | `git diff --check -- docs/审查/第67审_module-im会话列表分页后端闭环审查.md docs/审查/第68审_module-im最小实时推送后端闭环审查.md docs/审查/README.md server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im` | 通过 | 当前 IM 与审查文档差异没有空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有跑 employee-web Vitest 或浏览器联调，本轮目标是后端群治理接口闭环。
- 没有验证群治理 WebSocket 事件的真实浏览器接收，只验证了 HTTP 行为和前一节点的推送基础链路。
- 没有补群主转让、群管理员、禁言、组织群自动同步。
- 没有补群治理审计或系统消息。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 群主转让 | 当前群主不能退群，只能解散群聊。 | P2 | 补群主转让后再允许原群主退群。 |
| 6-2 | 解散系统消息/审计 | 解散和资料维护没有可追溯记录。 | P2 | 增加系统消息和审计日志，前端消息流能展示治理动作。 |
| 6-3 | 服务拆分 | 群治理加入后 `ImService` 已明显过大。 | P2 | 优先拆 `ImGroupService` 与 `ImMessageService`，再继续补组织群自动同步。 |
| 6-4 | 组织群同步 | 当前仅自定义群闭环，组织群创建与组织事件同步仍未完成。 | P2 | 接 `module-org` 事件消费者，处理员工状态、直属上级和部门结构变化。 |

## 第七部分：阶段性结论

本轮 `module-im` 群治理后端闭环已经达到演示可接入状态：自定义群可以查看资料、维护公告、拉人、移人、退群和解散，权限边界由后端兜底，目标集成测试已经覆盖主路径和越权路径。

当前没有 P0/P1 阻断项，可以进入前端群详情与群管理接入。但这仍不是完整生产级群系统：群主转让、治理审计、系统消息、组织群自动同步和服务拆分仍应作为下一批 P2 工作继续收口。
