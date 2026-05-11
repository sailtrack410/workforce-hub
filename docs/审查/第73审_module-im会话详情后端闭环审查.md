# 第 73 审 module-im 会话详情后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 会话详情接口、详情成员聚合、私聊/群聊/Agent 会话详情测试）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/EmployeeImController.java`、`server/src/main/java/com/workforcehub/im/dto/ImConversationDetailResponse.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/im/ModuleImGroupIntegrationTest.java`、`docs/审查/README.md`
- 审查目标：复核开发流程中“会话详情”是否补齐为前端可直接接入的后端接口，重点检查权限边界、私聊/群聊/Agent 三类会话返回语义、成员暴露范围和测试覆盖；本轮不审查成员展示名、头像聚合、组织群自动同步和前端页面渲染。

---

## 第一部分：提交内容概览

本轮新增员工端会话详情聚合接口：

- 新增 `GET /api/employee/im/conversations/{conversationId}`。
- 新增 `ImConversationDetailResponse`，返回 `conversation`、`members`、`groupProfile` 三块数据。
- `conversation` 复用 `ImConversationSummaryResponse`，包含会话类型、标题、绑定 Agent、最近消息、已读位点、未读数、置顶和免打扰状态。
- `members` 在详情接口返回活跃成员全量视图，Agent 会话会包含 `USER` 和 `AGENT` 两类成员。
- 群聊详情额外返回 `groupProfile`，包含群类型、群主、公告和当前用户群角色。
- 独立保留原群成员列表接口语义：`GET /group-conversations/{conversationId}/members` 仍只返回 `USER` 成员，不把 Agent 成员混入群成员管理视图。
- `ModuleImApiIntegrationTest.employeeShouldReadJoinedConversationDetail` 覆盖私聊详情。
- `ModuleImApiIntegrationTest.employeeShouldNotReadUnjoinedConversationDetail` 覆盖非成员禁止读取详情。
- `ModuleImApiIntegrationTest.authorizedEmployeeShouldStartAgentConversationAndReceiveAgentReply` 追加 Agent 会话详情断言，覆盖 `boundAgentId` 和 `AGENT` 成员。
- `ModuleImGroupIntegrationTest.groupMemberShouldReadGroupConversationDetail` 覆盖群聊详情聚合群资料和成员。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `docs/开发规划/开发流程总览.md`、`EmployeeImController.conversationDetail(...)` | 开发流程 4.2.1 明确包含“会话详情”，此前后端只有会话列表、消息列表、群 profile 和群成员分散接口。前端详情面板需要拼多个接口，Agent 会话还缺少统一详情入口。当前新增 `/api/employee/im/conversations/{conversationId}`，前端可按会话 ID 一次拿到摘要、成员和群资料。 | 已处理 | 前端详情面板优先接入该聚合接口，列表接口继续用于会话首页分页。 |
| 2-2 | `ImRepository.listActiveMembers(...)`、`ImService.getCurrentEmployeeConversationDetail(...)` | Agent 会话创建后会写入 `USER` 和 `AGENT` 两类成员，但原 `listActiveUserMembers(...)` 只返回用户成员。如果详情直接复用旧方法，前端无法从详情识别 Agent 成员。当前新增详情专用全量成员查询，Agent 会话详情会返回 `AGENT` 成员。 | 已处理 | 群治理成员列表继续使用用户成员查询，避免 Agent 成员进入拉人、移人、退群等用户治理语义。 |
| 2-3 | `ImService.getCurrentEmployeeConversationDetail(...)` | 会话详情权限以 `findConversationSummaryForUser(conversationId, userId)` 为入口，只允许当前活跃员工读取自己参与且会话活跃的数据。非成员读取已存在会话返回 403，不存在会话返回 404。 | 已处理 | 后续新增详情字段时继续从当前用户成员关系出发，不允许直接按会话 ID 查询再过滤。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImConversationMemberResponse` | 会话详情成员只返回 `memberId`、`memberType`、`memberRole`、`status` 和 `joinedAt`，没有成员展示名、头像和部门信息。触发条件是前端详情面板直接展示成员列表；失败路径是前端只能显示 ID 或继续调用其他组织接口补数据，演示体验不完整。现有测试只验证成员 ID 和类型。 | P2 | 下一轮补 `displayName`、`avatarFileId`、`deptName` 等轻量展示字段，或提供批量用户卡片接口，避免前端逐个查用户。 |
| 3-2 | `ImRepository.listActiveMembers(...)` | 详情成员查询当前不分页。自定义群创建接口限制成员数 200，演示阶段可接受；但后续组织群和部门群可能自动同步出更大成员规模。触发条件是大组织群打开详情；失败路径是详情接口响应变慢或返回体过大。 | P2 | 组织群上线前把详情成员改为分页或只返回前 N 个成员和总数，完整成员列表走专用分页接口。 |
| 3-3 | `ImConversationDetailResponse.groupProfile` | 非群聊详情的 `groupProfile` 为空，私聊和 Agent 会话没有各自的 profile 对象。触发条件是前端写死读取 `groupProfile` 或需要 Agent 名称、描述、状态等元数据；失败路径是前端需要按 `conversationType` 分支处理。 | P3 | 保持第一版清晰分支即可；后续可扩展 `agentProfile` 和 `privateProfile`，但不要把不同会话类型字段混进同一个对象。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 会话详情本身实现较小，但继续把详情聚合、消息写入、Agent 回写、群治理、附件下载放在同一个服务类。触发条件是继续补成员展示名、偏好设置、组织群同步和 outbox；失败路径是一个服务内混合查询聚合与写侧状态机，后续容易补丁堆叠。 | P2 | 近期拆出 `ImConversationQueryService` 和 `ImConversationMemberQueryService`，把详情、列表、成员读模型从写侧服务中分离。 |
| 4-2 | `ImRepository` | 新增 `listActiveMembers(...)` 后，仓储同时存在用户成员查询、全量成员查询、推送成员查询、群 profile 查询和写侧成员治理 SQL。触发条件是后续每类成员视图都继续加字段；失败路径是相似 SQL 分叉后难以保证过滤条件一致。 | P2 | 拆 `ImMemberRepository`，并用方法名明确 `listActiveUserMembersForGroupManagement`、`listActiveMembersForConversationDetail`、`listPushableUserIds` 的边界。 |
| 4-3 | `ModuleImApiIntegrationTest`、`ModuleImGroupIntegrationTest` | 会话详情测试继续追加在已有大测试类中，覆盖有效但文件膨胀明显。触发条件是继续加入成员展示名、偏好设置、组织群详情测试；失败路径是测试失败定位成本上升。 | P2 | 拆出 `ModuleImConversationDetailIntegrationTest`，把私聊、群聊、Agent 详情和越权读取集中维护。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区已有多模块增量，本轮只追加会话详情接口、相关测试和审查文档。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 72 审，本轮新增第 73 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 详情目标测试 | `env TEST_DB_NAME=workforce_hub_node73_im_conversation_detail mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖私聊详情、群聊详情、Agent 会话详情、非成员禁止读取，以及 IM/群治理既有主路径。 |
| 本轮差异格式检查 | `git diff --check -- server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java server/src/test/java/com/workforcehub/im/ModuleImGroupIntegrationTest.java` | 通过 | 未发现本轮代码差异空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，本轮以 IM 详情和群治理目标测试为准。
- 没有跑 employee-web 测试，前端尚未接入详情接口。
- 没有验证真实浏览器详情面板渲染。
- 没有验证大群成员规模下的响应体和性能。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 成员展示字段 | 详情成员没有姓名和头像，前端演示会继续依赖 mock 或二次查询。 | P2 | 下一节点优先补成员展示名，保证详情面板可直接展示人名。 |
| 6-2 | 大群详情 | 详情成员不分页，组织群上线后会有性能风险。 | P2 | 组织群同步前补分页或成员摘要。 |
| 6-3 | 服务拆分 | `ImService` 和 `ImRepository` 已持续膨胀。 | P2 | 在继续补偏好设置和组织群同步前，至少拆读模型服务和成员仓储。 |

## 第七部分：阶段性结论

本轮补齐了会话主链路中“会话详情”后端节点。前端现在可以通过一个接口读取当前会话摘要、活跃成员，以及群聊资料；Agent 会话详情也能返回绑定 Agent 和 `AGENT` 成员，不再只能依赖会话列表推断。

当前没有新增 P0/P1 阻断项，可以进入前端接入或继续补详情体验字段。最接近演示体验的剩余问题是成员没有展示名和头像；如果前端详情面板要展示成员列表，下一节点应优先补成员轻量展示字段。
