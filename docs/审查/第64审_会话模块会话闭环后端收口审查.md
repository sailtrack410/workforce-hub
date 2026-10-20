# 第 64 审 module-im 会话闭环后端收口审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为本轮工作区增量基线）
  - 工作区增量（module-im 会话后端收口：群成员查询、同群私聊、旧 Session 员工状态复核、消息发送幂等）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`server/src/main/resources/db/migration/V13__add_im_message_client_idempotency.sql`、`docs/审查/README.md`
- 审查目标：复核前端接入会话功能所需的后端闭环是否足够演示使用，重点看权限、状态、幂等、成员可见性和测试保护；本轮不评估 employee-web 未提交页面改动，也不把 WebSocket、附件消息、撤回编辑视为已完成能力。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 员工端会话闭环做后端收口。

- `EmployeeImController` 新增 `GET /api/employee/im/group-conversations/{conversationId}/members`，让前端群聊详情可以读取成员列表。
- `ImService` 将员工端 IM 入口统一改为 `requireActiveEmployeeUserId()`，旧 Session 在员工离职或账号停用后不能继续读取、发送或治理会话。
- `ImService.startCurrentEmployeePrivateConversation(...)` 把私聊准入从“仅直属上下级”扩展为“直属上下级或同一活跃群成员”，补齐开发计划中“同群成员可发起私聊”的要求。
- `ImRepository` 新增 `existsLoginAllowedOrgUser(...)`、`existsActiveGroupConversation(...)`、`existsSharedActiveGroupMembership(...)`，并继续保留消息 `clientMessageId` 幂等查询、私聊创建唯一键冲突回查。
- `ModuleImApiIntegrationTest` 新增离职旧 Session 不能继续发消息、重复 `clientMessageId` 复用既有消息的覆盖。
- `ModuleImGroupIntegrationTest` 新增群成员列表读取、非成员禁止读取、同群成员发起私聊的覆盖。
- `V13__add_im_message_client_idempotency.sql` 为 `im_message` 增加 `client_message_id` 与唯一键，支撑前端重试幂等。

当前后端已经能支持前端演示的主路径：会话列表、消息分页、文本消息发送、发送幂等、已读位点、私聊创建、群聊创建、群成员读取、群主拉人/移人、同群私聊和离职状态拦截。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImService.sendCurrentEmployeeMessage(...)`、`V13__add_im_message_client_idempotency.sql`、`ModuleImApiIntegrationTest.employeeShouldReuseExistingMessageWhenClientMessageIdRepeated` | 第 50 审指出发送请求已有 `clientMessageId` 但服务端不落库去重，网络重试会重复插入消息并推进 `seq_no`。当前新增 `im_message.client_message_id` 和 `uk_im_message_client_message`，发送前先查既有消息，唯一键冲突后也回查并返回既有消息。 | 已处理 | 后续若支持附件/卡片消息，应继续复用同一幂等键模型，不要为每种消息类型另起一套去重语义。 |
| 2-2 | `ImService.createPrivateConversation(...)`、`ImRepository.tryInsertPrivateConversation(...)` | 第 50 审指出私聊创建“先查后插”在并发首次创建时可能因 `conversation_key` 唯一键冲突抛异常。当前插入私聊会话时捕获唯一键冲突并回查已有会话，重复发起可以返回同一会话。 | 已处理 | 后续应补真正并发测试，目前只覆盖重复请求复用，不覆盖双线程竞争。 |
| 2-3 | `EmployeeImController.groupMembers(...)`、`ImService.listCurrentEmployeeGroupMembers(...)`、`ModuleImGroupIntegrationTest.groupMemberShouldListMembersOfJoinedGroupConversation` | 接口清单要求群成员列表“已入群成员可见”，但原 Controller 只有添加/移除成员，没有成员查询入口。当前补齐 GET 入口，群成员可见、非成员 403。 | 已处理 | 前端群聊详情面板可直接接该接口，避免继续用本地 mock 成员列表。 |
| 2-4 | `ImService.startCurrentEmployeePrivateConversation(...)`、`ImRepository.existsSharedActiveGroupMembership(...)`、`ModuleImGroupIntegrationTest.groupMembersShouldStartPrivateConversationWithEachOther` | 开发计划要求“同群成员可发起私聊”，原实现只允许直属上下级，导致群聊里普通同事无法进入一对一会话。当前准入扩展为直属上下级或同一活跃群成员。 | 已处理 | 后续如要限制跨部门私聊，应在通讯录策略层统一配置，不应回到硬编码分支。 |
| 2-5 | `ImService.requireActiveEmployeeUserId()`、`ImRepository.existsLoginAllowedOrgUser(...)`、`ModuleImApiIntegrationTest.offboardedEmployeeShouldNotSendMessageWithOldSession` | 员工登录时会校验 `employment_status`，但旧 Session 在员工状态变更后如果只靠会话成员关系，仍可能继续发消息。当前所有员工端 IM 入口先复核当前员工档案和账号仍允许登录，离职后旧 Session 发送消息返回 403 且不写入消息。 | 已处理 | 如果后续统一做全局员工端状态拦截器，可把该逻辑上移到公共过滤器，IM 保留业务成员校验。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImService.createCurrentEmployeeGroupConversation(...)`、`ImRepository.countActiveOrgUsers(...)` | 自定义群聊创建和拉人仍只校验目标用户是可用员工，不校验通讯录可见范围、同部门/上下级/协作关系，也没有邀请确认。只要员工知道用户 ID，就能把任意 active 员工拉入群聊。演示环境可接受，但真实企业场景会造成跨组织误拉和骚扰。 | P2 | 在 `module-org` 或 `module-im` 增加通讯录可见策略，例如同部门、上下级、同群、业务协作范围；敏感角色可要求邀请确认或管理员策略开关。 |
| 3-2 | `ImService.addCurrentEmployeeGroupMembers(...)`、`ImRepository.upsertUserMember(...)` | 群成员被移出后，群主再次拉人会把原成员行 `LEFT` 改回 `ACTIVE`，但当前没有成员变更审计或系统消息。真实协作中成员被移除、再次加入、历史可见范围都需要可追溯；否则问题排查时只能看最终成员状态。 | P2 | 补 `im_group_member_event` 或系统消息写入：记录 add/remove/rejoin、操作者、原因、时间，并明确重新加入是否可见历史消息。 |
| 3-3 | `ImRepository.listActiveConversationsForUser(...)` | 会话列表仍不分页，直接返回当前用户全部 active 会话并联查最后消息。演示数据少时可用，但员工进入多个组织群、项目群、Agent 会话后，该接口会成为首屏无界查询。 | P2 | 增加游标分页参数，如 `beforeLastMessageAt`、`beforeConversationId`、`size`；默认只取首屏，历史会话滚动加载。 |
| 3-4 | `ImRepository.existsLoginAllowedOrgUser(...)` 与 `EmployeeUserDetailsService.LOGIN_ALLOWED_STATUSES` | IM 当前把登录准入状态白名单重复写了一份 SQL：`PROBATION/ACTIVE/PENDING_OFFBOARD`。如果后续认证模块调整允许登录状态，IM 可能忘记同步，形成“能登录但不能会话”或“不能登录但旧会话可用”的不一致。 | P2 | 抽公共 `EmployeeAccessPolicy` 或 `OrgUserAccessRepository`，认证和业务模块共用同一登录准入口径。 |
| 3-5 | `module-im` 当前接口集合 | 当前演示闭环已覆盖文本会话，但开发计划中的附件消息、消息编辑/撤回、WebSocket 推送、组织事件消费还没有落地。若演示口径说“会话功能全部完成”，会和实际能力不一致。 | P2 | 演示口径应限定为“文本会话闭环”；下一节点按附件消息、撤回编辑、实时推送、组织事件同步拆成独立小闭环。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 仓储已同时承载会话摘要、成员校验、组织用户状态、群成员关系、消息分页、发送幂等、私聊和群聊写入。继续加入附件、撤回、系统消息和 WebSocket outbox 后，单类会快速膨胀。 | P2 | 下一批 IM 能力前拆成 `ImConversationRepository`、`ImMemberRepository`、`ImMessageRepository` 和查询专用 repository。 |
| 4-2 | `ImService` | 服务层同时做员工状态准入、权限校验、私聊准入规则、群治理、消息发送、已读位点、DTO 映射和 JSON 解析。当前还能读，但后续 Agent 会话和附件消息进入后会难以维护。 | P2 | 拆 `ImConversationService`、`ImMessageService`、`ImGroupService`；DTO 映射和内容 JSON 解析下沉到 mapper/helper。 |
| 4-3 | `ImService.requireActiveEmployeeUserId()` | 当前为尽快收口旧 Session 风险，在 IM 内部补了统一复核。这个边界正确，但如果每个员工端模块都复制一份，会造成策略分散。 | P2 | 中期应上移到员工端公共权限拦截器或共享策略服务；各业务模块只保留业务授权。 |
| 4-4 | `ModuleImApiIntegrationTest`、`ModuleImGroupIntegrationTest` | 当前覆盖了重复 `clientMessageId`、群成员查询、同群私聊和离职旧 Session，但没有多线程并发私聊创建、并发消息发送、并发已读位点更新测试。 | P2 | 增加并发级集成测试，至少覆盖同一对用户双端同时创建私聊、同一会话多用户同时发消息、重复读位点不会倒退。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动，本轮只修改 IM 后端、IM 测试和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/` 文件列表 | 已执行 | 当前索引最新为两个第 63 审，本轮使用第 64 审。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node64_im_fix2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖会话列表、消息分页、消息发送幂等、私聊、群聊、群成员治理、群成员列表、同群私聊、离职旧 Session 禁止发送。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| 代码差异空白检查 | `git diff --check -- server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im server/src/main/resources/db/migration/V13__add_im_message_client_idempotency.sql` | 通过 | 本轮 IM 代码和迁移无尾随空白。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有 employee-web、agent、leave、workflow 等多模块未提交改动，全量回归成本较高且会混入非本轮变量。
- 没有跑 employee-web Vitest 或浏览器联调，本轮目标是后端闭环。
- 没有验证并发私聊创建、并发发消息和 WebSocket 实时推送。
- 没有验证附件消息上传/下载链路，因为附件消息尚未实现。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-im` 演示口径 | 当前已能支撑文本会话演示，但不应对外称“完整 IM 全功能完成”。 | P2 | 演示时明确说“文本会话闭环已完成，附件/撤回/实时推送是下一节点”。 |
| 6-2 | 群成员加入策略 | 任意 active 员工可被拉群是最接近真实权限风险的剩余问题。 | P2 | 下一轮优先补通讯录可见范围或邀请确认。 |
| 6-3 | 会话列表分页 | 首屏无界查询会影响后续真实数据规模。 | P2 | 前端接入前后端共同定义分页契约。 |
| 6-4 | IM 拆分 | `ImService` 和 `ImRepository` 已进入继续扩展前的拆分窗口。 | P2 | 在附件消息和 WebSocket outbox 前完成服务/仓储拆分。 |

## 第七部分：阶段性结论

本轮后端会话闭环已满足演示主路径：员工登录后可以加载会话、分页读消息、发送文本消息并幂等重试、更新已读位点、创建私聊、创建群聊、读取群成员、群主拉人/移人，并且同群成员可以继续发起私聊。旧 Session 在员工离职后不能继续发送消息，避免绕过登录准入。

当前不存在 P0/P1 阻断项，可以让前端按这些后端接口接入文本会话演示。但剩余 P2 必须如实纳入下一节点：群成员拉人范围还没有通讯录策略，会话列表没有分页，附件消息/撤回编辑/WebSocket/组织事件同步未完成，IM 服务和仓储也需要在继续扩展前拆分。
