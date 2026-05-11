# 第 50 审 employee-web前端收口与module-im接口接入审查

- 审查日期：2026-05-10
- 审查提交：
  - `84b9da2`（收口员工端前端工程边界）
  - `0554519`（新增员工端接口基础层）
  - `1042401`（补充员工端联调配置说明）
  - `89ec677`（接入员工端会话状态）
  - `f73ce9b`（新增员工端待办接口适配）
  - `67a6da7`（接入员工端会话读取接口）
  - `da61dab`（接入员工端会话发送接口）
  - `2728f8b`（接入员工端私聊创建接口）
  - `3daf4b3`（接入员工端会话已读位点接口）
  - `c9e5e70`（接入员工端会话消息分页）
  - `644a28d`（接入员工端自定义群聊创建接口）
- 审查范围：`employee-web/`、`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`server/src/main/resources/db/migration/V1__init_schema.sql`、`.env.example`、`prototypes/employee-web/README.md`
- 审查目标：复核第 49 审之后员工端工程边界是否收口，并审查 module-im 员工端会话、消息、私聊、已读位点、分页、群聊创建与前端 API 接入是否存在正确性、权限、幂等和维护性风险。

---

## 第一部分：提交内容概览

本轮提交从员工端前端工程边界治理推进到真实接口接入。

- `84b9da2` 把 `employee-web/` 纳入正式 Vue 工程主线，补根 `.gitignore`，明确 `prototypes/employee-web/` 只保留为历史设计参考。
- `0554519` 增加 `employee-web/src/api/` 基础层，统一封装 `fetch`、R 包装响应、Session Cookie、查询参数和错误对象。
- `1042401` 补充 `.env.example` 和 `employee-web/README.md` 的联调说明。
- `89ec677` 到 `c9e5e70` 持续接入员工端 IM 会话列表、消息读取、消息发送、私聊创建、已读位点和消息分页。
- `644a28d` 新增员工端自定义群聊创建接口。
- 后端新增 `EmployeeImController`、`ImService`、`ImRepository`、IM DTO/Row，以及 `ModuleImApiIntegrationTest`、`ModuleImGroupIntegrationTest`。
- 前端新增 `session`、`workflow`、`im` store 和 API 单测，页面开始从真实 IM 接口加载会话与消息。

当前提交已经具备员工端 IM 第一版雏形，但仍有几个关键边界没有收口：消息发送重试幂等、私聊并发创建语义、前端对后端新增 IM 能力的覆盖度，以及群聊成员可见范围。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `.gitignore`、`employee-web/.gitignore` | 第 49 审指出 `employee-web/` 未跟踪目录混有 `node_modules`、`dist`、`file-dist` 和截图产物，容易污染主仓库。当前根 `.gitignore` 已明确排除 `employee-web/node_modules/`、`employee-web/dist/`、`employee-web/file-dist/`、`employee-web/*.tsbuildinfo` 和本地截图，`employee-web/.gitignore` 也同步排除本地依赖与构建产物。 | 已处理 | 后续提交仍应用 `git status --short --ignored employee-web` 复核，避免手工强制添加构建产物。 |
| 2-2 | `employee-web/README.md`、`prototypes/employee-web/README.md` | 第 49 审指出旧静态原型和 Vue 工程双轨演进会造成源头漂移。当前文档已声明 `employee-web/` 是正式员工端工程，`prototypes/employee-web/` 只作为历史设计稿和交互参考，不再作为正式开发入口或后端契约来源。 | 已处理 | 后续只允许正式联调逻辑进入 `employee-web/src/`，静态原型不能再新增真实接口文案。 |
| 2-3 | `employee-web/src/stores/` | 第 49 审指出 `stores/inbox.ts` 与 `stores/shell.ts` 重复。当前 HEAD 中 `employee-web/src/stores/inbox.ts` 已不存在，IM 数据改为 `stores/im.ts` 通过后端响应映射到 shell 会话模型。 | 已处理 | 继续保持 store 分层：`im.ts` 管真实 IM 数据，`shell.ts` 管页面组合和展示状态。 |
| 2-4 | `employee-web/src/views/MessagesView.vue`、`employee-web/src/stores/shell.ts` | 第 49 审指出搜索、筛选、`window.alert` 只是占位交互。当前页面已改为非阻塞 toast，列表也通过 `filteredConversations`、`filteredTodos`、`filteredFiles`、`filteredWorkbench`、`filteredProfile` 等 getter 响应搜索与筛选。 | 已处理 | 后续应继续补真实空状态、错误状态和加载状态，而不是回退到静态占位。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `SendImMessageRequest.clientMessageId`、`ImService.sendCurrentEmployeeMessage(...)`、`im_message` | 发送消息请求 DTO 已提供 `clientMessageId`，但服务端发送逻辑没有读取、落库或建立唯一约束。客户端网络超时后重试、用户双击发送、前端失败重放时，同一条客户端消息会被插入多次，并各自推进 `seq_no` 和会话最后消息。当前测试只覆盖单次发送和未加入会话禁止发送，挡不住重复请求。 | P1 | 在 `im_message` 或独立去重表中保存 `clientMessageId + conversationId + senderId`，建立唯一约束；发送接口遇到重复 key 时返回既有消息，保证客户端重试幂等。 |
| 3-2 | `ImService.startCurrentEmployeePrivateConversation(...)`、`ImRepository.findActiveConversationIdByKey(...)`、`ImRepository.insertPrivateConversation(...)` | 私聊创建使用“先查 `conversation_key` 再插入”的幂等逻辑，表上虽然有 `uk_conversation_key` 唯一约束，但并发首次发起同一对用户私聊时，两个事务都可能查不到记录，随后同时插入。一个事务成功，另一个会因唯一键冲突抛出数据库异常，接口语义会变成 500/异常，而不是复用已创建会话。 | P2 | 捕获唯一键冲突后重新查询并返回已有会话，或使用数据库原子 upsert/insert-ignore 模式；补并发测试验证重复点击和双端同时发起都返回同一个会话。 |
| 3-3 | `EmployeeImController`、`employee-web/src/api/employee.ts`、`employee-web/src/stores/im.ts`、`employee-web/src/stores/shell.ts` | 后端已经提交 `POST /api/employee/im/private-conversations`、`POST /api/employee/im/group-conversations`、`POST /api/employee/im/conversations/{conversationId}/read-position`，消息分页也支持 `beforeSeq` 和 `size`；但前端 API 只暴露 `conversations()`、`messages(conversationId)`、`sendMessage(...)`。页面选择会话时只在本地把 `unread` 置 0，没有调用已读位点接口；刷新后未读数会按后端旧 `last_read_seq` 回来。前端也无法使用已提交的私聊创建、群聊创建和历史分页能力。 | P2 | 前端 API 层补齐 `startPrivateConversation`、`createGroupConversation`、`updateReadPosition`、`messages(conversationId, { beforeSeq, size })`；页面选择会话或滚动到最新消息后写回已读位点，历史消息用分页追加而不是只取默认页。 |
| 3-4 | `ImService.createCurrentEmployeeGroupConversation(...)`、`ImRepository.countActiveOrgUsers(...)` | 自定义群聊创建只校验当前用户和目标成员是 active org user，不校验成员是否在当前员工可见通讯录范围、是否同部门/上下级/业务协作范围，也没有邀请确认或群创建频率限制。若员工知道任意 active 用户 ID，就能把对方拉入自定义群聊。第一版可以先做内部群，但权限边界必须明确，否则容易形成跨组织骚扰或误拉敏感成员。 | P2 | 定义员工通讯录可见范围和群聊创建策略：至少按同组织/直属关系/业务协作范围做成员可加入校验；高权限或敏感岗位可增加审批、邀请确认或管理员开关。 |
| 3-5 | `ImRepository.listActiveConversationsForUser(...)` | 会话列表接口不分页、不限制返回数量，直接返回当前用户所有 active 会话并联查最后消息。员工加入大量组织群、项目群、Agent 会话后，该接口会成为登录首屏的无界查询，影响员工端首页稳定性。 | P2 | 增加游标或分页参数，例如 `beforeLastMessageAt`、`beforeConversationId`、`size`；默认只取首屏，置顶和未读可单独排序，历史会话按滚动加载。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 第一版仓储已经接近 500 行，集中维护会话摘要、成员校验、消息分页、消息写入、私聊创建、群聊创建和 ResultSet 映射。后续再加群成员治理、公告、免打扰、置顶、撤回、附件消息，会快速变成大 SQL 仓储。 | P2 | 进入下一批 IM 能力前拆分为 `ImConversationRepository`、`ImMessageRepository`、`ImMemberRepository`，摘要查询可以保留专用 Query Repository。 |
| 4-2 | `ImService` | `ImService` 同时承担权限校验、关系规则、会话创建、消息发送、已读位点、响应 DTO 映射和 JSON 内容解析。当前还能阅读，但继续堆群治理和 Agent 会话后，业务规则会混在一个服务中。 | P2 | 拆出 `ImConversationService`、`ImMessageService`、`ImReadStateService`；DTO 映射可下沉到 mapper，避免核心服务继续承载展示转换。 |
| 4-3 | `employee-web/src/stores/shell.ts#mergeBackendConversations` | 前端成功加载后端会话后，仍把静态 mock 会话追加到真实会话后面。这个策略保留了演示完整度，但也会让真实联调页面同时出现生产数据和原型数据；如果测试人员按页面操作，容易把 mock 会话误认为真实接口能力。 | P2 | 增加运行模式开关：开发演示可保留 mock fallback，真实联调和生产模式必须只显示后端数据；mock 会话应有明显标识或从正式构建剔除。 |
| 4-4 | `employee-web/src/stores/workflow.ts`、`employee-web/src/views/MessagesView.vue` | 提交中新增了 workflow 待办 store 和测试，但 HEAD 页面尚未加载 `useWorkflowStore`，待办页仍主要依赖 shell 静态数据。当前工作区已有未提交改动开始接入 `workflow.loadTodos()`，但尚不属于本轮已提交内容。 | P3 | 后续提交时把 workflow store 与页面接入、加载状态、错误状态、测试一起提交，避免“API 适配已写但页面未用”的半完成状态长期停留。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 提交范围确认 | `git log --oneline --reverse 9570bee..HEAD` | 已执行 | 本轮关注 `84b9da2` 到 `644a28d` 共 11 个提交。 |
| 影响面确认 | `git diff --stat 9570bee..HEAD`、`git diff --name-status 9570bee..HEAD` | 已执行 | 主要影响 `employee-web/`、`.env.example`、`server/src/main/java/com/workforcehub/im/` 和 `server/src/test/java/com/workforcehub/im/`。 |
| 关键文件静态审查 | `git show HEAD:<path>`、`rg`、`nl -ba` | 已执行 | 使用 HEAD 版本复核后端 IM 代码和前端 API/store，避免混入当前未提交改动。 |
| 工作区状态 | `git status --short --branch` | 已执行 | 当前工作区仍有未提交改动，包括审查文档、employee-web、prototypes/employee-web、module-im 代码和测试等。本轮审查结论以已提交的 HEAD 为准，未把这些后续工作区改动混入提交结论。 |

未验证项：

- 本轮没有运行 Maven 测试或 Vitest。原因是当前工作区已有未提交代码改动，直接跑测试会把提交范围和未提交范围混在一起。
- 尚未做 IM 并发私聊创建测试。
- 尚未做消息发送重复 `clientMessageId` 幂等测试。
- 尚未做员工端真实浏览器联调和 Session 过期场景验证。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 消息发送幂等 | `clientMessageId` 已在请求里出现但没有落库去重，这是最容易在真实网络环境中产生重复消息的点。 | P1 | 优先补唯一键和重复返回语义，再补前端重试。 |
| 6-2 | 前端 IM API 覆盖 | 后端已开放私聊、群聊、已读位点和分页，但前端 API/store 还没有覆盖，真实联调会出现“后端已实现但页面不可用”。 | P2 | 下一批前端先补 API 方法和 store action，再接页面按钮/滚动加载。 |
| 6-3 | 群聊成员边界 | 自定义群聊创建的成员选择策略还没有产品化边界。 | P2 | 明确通讯录可见范围、邀请策略和敏感成员限制，再扩群成员治理接口。 |
| 6-4 | IM 仓储拆分 | `ImRepository` 已经接近需要拆分的临界点。 | P2 | 在新增群成员治理、公告、撤回、附件消息前拆 Repository 和 Service 边界。 |

---

## 第七部分：阶段性结论

本轮提交对第 49 审的工程化问题有实质收口：`employee-web/` 已作为正式工程纳入版本管理，依赖和构建产物被排除，静态原型被降级为历史参考，前端也开始通过统一 API client 接入真实员工端接口。

module-im 第一版也具备可继续迭代的基础：列表、消息读取、发送、私聊、已读位点、分页和自定义群聊创建都有后端接口与集成测试覆盖。当前不建议直接把 IM 当作稳定闭环继续上层扩展，优先处理消息发送幂等、私聊并发创建语义、前端 API 覆盖缺口和群聊成员授权边界。处理完这些后，再进入群成员治理、公告、撤回、附件消息和 Agent 会话会更稳。
