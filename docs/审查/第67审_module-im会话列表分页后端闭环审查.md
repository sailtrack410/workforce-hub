# 第 67 审 module-im 会话列表分页后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 会话列表分页：时间与会话 ID 组合游标、分页大小校验、目标集成测试补齐）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核员工端会话列表是否从一次性全量读取收口为可供前端接入的分页接口，重点看分页稳定性、权限过滤、参数错误语义、测试保护和后续置顶/实时推送衔接。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 会话列表补齐后端分页能力。

- `EmployeeImController.conversations(...)` 支持 `beforeLastMessageAt`、`beforeConversationId`、`size` 查询参数。
- `ImService.listCurrentEmployeeConversations(...)` 统一校验当前员工状态，解析 ISO `LocalDateTime` 游标，并要求时间和会话 ID 游标成对出现。
- `ImService.normalizeConversationPageSize(...)` 将默认会话页大小固定为 30，最大限制为 100，避免前端一次性拉取过多会话。
- `ImRepository.listActiveConversationsForUser(...)` 按当前用户活跃成员关系、活跃会话和未删除会话过滤，使用 `COALESCE(c.last_message_at, c.created_at) DESC, c.id DESC` 作为稳定排序。
- 游标条件与排序字段保持一致：先按最新消息时间向后翻页，同一时间再按 `conversation_id` 向后翻页，避免同一时间戳下漏读或重复读。
- `ModuleImApiIntegrationTest.employeeShouldPageConversationsByLastMessageCursor` 覆盖第一页、第二页和游标翻页顺序。
- `ModuleImApiIntegrationTest.employeeConversationPageParamsShouldBeValidated` 覆盖 `size=0`、游标缺半、时间格式非法等参数错误路径。

当前后端已经能支撑前端演示中的会话列表分页：前端首屏调用 `GET /api/employee/im/conversations?size=30`，下一页使用上一页最后一条的 `lastMessageAt` 和 `conversationId` 作为游标继续拉取。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `EmployeeImController.conversations(...)`、`ImService.listCurrentEmployeeConversations(...)` | 会话列表此前是无分页的全量读取。真实员工会话数量增长后，前端首页会被一次性响应大小、数据库排序扫描和渲染成本拖慢。当前新增 `size` 和组合游标，首屏和后续翻页都能限制返回数量。 | 已处理 | 前端接入时保留默认 `size=30` 或显式传小页大小，不要再假设一次返回全部会话。 |
| 2-2 | `ImRepository.listActiveConversationsForUser(...)` | 分页查询仍保留当前用户成员过滤、成员状态过滤、会话状态过滤和软删除过滤，没有因为分页改造放松权限边界。非当前成员、归档会话和删除会话不会出现在列表。 | 已处理 | 后续增加搜索、置顶或群类型筛选时，必须继续以当前用户成员关系作为查询主约束。 |
| 2-3 | `ModuleImApiIntegrationTest.seedPagedConversation(...)` | 分页用例最初使用 `conversationId + 1000/1001` 生成成员表主键，相邻会话会产生重复主键导致集成测试失败。当前改为 `conversationId * 10 + 1/2`，测试数据主键不再互相碰撞。 | 已处理 | 后续新增测试种子时，优先使用明确不重叠的 ID 段，避免测试数据自身制造假失败。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImRepository.listActiveConversationsForUser(...)` | 当前分页排序为了游标稳定改为纯最新消息时间排序，没有继续把 `m.pin_flag` 放在排序首位。触发条件是用户存在置顶会话但该会话最新消息较旧；此时它会被排到普通较新会话之后。功能不会越权或丢数据，但会改变“置顶优先”的产品语义。 | P2 | 如果演示或产品要求置顶优先，应将 `pin_flag` 纳入排序和游标，例如增加 `beforePin`，排序改为 `pin_flag DESC, lastMessageAt DESC, id DESC`，避免只改 ORDER BY 造成翻页重复或漏页。 |
| 3-2 | `ImConversationSummaryResponse` | 响应只返回 `lastMessageAt` 和 `conversationId`，没有显式 `nextCursor` 或 `hasMore`。前端可以用最后一条数据自行构造游标，但空页、刚好满页和下一页是否还有数据都需要额外约定。 | P2 | 为列表响应引入统一分页 DTO，返回 `items`、`nextCursor`、`hasMore`。如果当前为了兼容先保持数组响应，应在接口文档里明确“少于 size 即无下一页”。 |
| 3-3 | `ImRepository.listActiveConversationsForUser(...)` | 当前排序依赖 `COALESCE(c.last_message_at, c.created_at)`，但 `im_conversation` 只有 `conversation_key` 唯一键，没有针对该排序表达式和成员过滤的组合索引。会话量上来后，列表页可能出现排序扫描和回表成本。 | P2 | 增加面向会话列表的索引设计，例如在 `im_conversation_member(member_type, member_id, status, conversation_id)` 与 `im_conversation(status, is_deleted, last_message_at, id)` 上补查询路径；如果继续使用表达式排序，需要评估 MySQL 8 函数索引或改为保证 `last_message_at` 非空。 |
| 3-4 | `GET /api/employee/im/conversations` | 分页游标使用 `LocalDateTime.parse`，只接受 `2026-05-09T11:30:00` 这种无时区 ISO 格式。后端数据库和测试固定在 `Asia/Shanghai` 语义下没问题，但跨端如果传 `Z` 或带偏移时间会返回格式错误。 | P3 | 在前端 API 契约里固定无时区本地时间格式，或后续改为毫秒时间戳/`OffsetDateTime`，避免移动端和浏览器序列化格式不一致。 |
| 3-5 | 会话列表与消息发送并发 | 当前翻页是常见的弱一致游标分页。用户翻页期间如果有新消息写入，旧会话可能被推到第一页，后续页不会保证强快照一致。演示可接受，但真实 IM 列表需要配合实时事件刷新首屏。 | P2 | WebSocket/outbox 接入后，前端对新消息事件更新首屏列表；历史翻页继续用游标读取，不承诺跨页强快照。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | `ImService` 已同时承载会话列表分页、消息分页、私聊准入、群治理、附件下载、编辑撤回和 DTO 映射。继续把 WebSocket 推送也直接塞入同一类，会让变更影响面过大。 | P2 | 在接实时推送前拆出 `ImConversationService`、`ImMessageService` 和 `ImAttachmentService`，推送只订阅领域事件或服务回调。 |
| 4-2 | `ImRepository` | 会话列表 SQL、消息写入 SQL、附件 SQL、群成员 SQL 和编辑撤回 SQL 继续堆在单一仓储。分页排序和游标语义以后会频繁调整，放在大仓储里容易误改其他 SQL。 | P2 | 至少拆出 `ImConversationRepository` 和 `ImMessageRepository`，让会话列表索引、排序和游标逻辑独立演进。 |
| 4-3 | 分页契约常量 | 会话页大小默认值和上限目前在 `ImService` 私有常量里，前端、接口文档和测试只能通过代码或用例推断。 | P3 | 后续将 IM 分页契约写入 API 文档或配置说明，避免前后端对默认页大小理解不一致。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只处理 IM 会话列表分页、测试和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 66 审，本轮新增第 67 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node67_im_conversation_page2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖会话列表分页、参数校验、文本消息、附件消息、群聊、编辑撤回等 IM 现有集成路径。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有跑 employee-web Vitest 或浏览器联调，本轮目标是后端分页接口闭环。
- 没有压测大量会话下的分页 SQL 执行计划。
- 没有验证置顶会话的产品排序，因为当前分页实现选择了不含置顶字段的稳定游标。
- 没有接 WebSocket/outbox，因此会话列表仍需要前端主动刷新才能看到新消息、编辑或撤回变化。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 置顶排序与分页游标 | 当前分页会改变置顶优先语义，是否影响演示取决于前端是否展示置顶功能。 | P2 | 若前端本轮展示置顶，立即把 `pin_flag` 纳入游标；若不展示，可推迟到会话偏好设置节点。 |
| 6-2 | WebSocket/outbox | 会话列表分页解决了“能拉取”，但新消息、编辑、撤回仍不能主动推到前端。 | P2 | 下一节点优先补最小 WebSocket 连接、鉴权和新消息推送，保证演示时不用频繁手动刷新。 |
| 6-3 | 列表分页响应结构 | 当前数组响应不带 `hasMore/nextCursor`，前端需要自行推导。 | P2 | 引入统一游标分页响应，或先在前端 API 层封装游标生成规则。 |
| 6-4 | 查询索引 | 会话量小的时候问题不明显，但列表页是 IM 高频入口。 | P2 | 在补实时推送前后补索引迁移和执行计划验证。 |

## 第七部分：阶段性结论

本轮 `module-im` 会话列表分页后端闭环已经达到演示可接入状态：员工端能按当前用户活跃会话分页拉取列表，分页参数有边界校验，游标排序与查询条件一致，目标集成测试已经通过。

当前没有发现 P0/P1 阻断项，可以进入前端会话列表分页接入或继续补实时推送。但需要明确：这不是最终生产级 IM 列表模型，置顶排序、分页响应结构、索引和 WebSocket/outbox 仍应作为下一批 P2 工作继续收口。
