# 第 66 审 module-im 消息编辑撤回后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 消息编辑/撤回后端闭环：文本消息编辑、本人消息撤回、撤回后附件下载阻断）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核开发规划中“消息编辑、消息撤回”是否具备可演示后端闭环，重点看操作者归属、消息状态迁移、撤回后原文与附件暴露、测试保护和后续实时推送衔接。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 消息编辑和撤回做后端收口。

- 新增 `EditImMessageRequest`，约束编辑文本不能为空且不超过 4000 字。
- `EmployeeImController` 新增 `POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/edit` 和 `POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/recall`。
- `ImService.editCurrentEmployeeMessage(...)` 只允许当前会话成员编辑自己发送的 `TEXT` 消息，状态必须是 `NORMAL` 或 `EDITED`，成功后消息状态变为 `EDITED`。
- `ImService.recallCurrentEmployeeMessage(...)` 只允许发送人撤回自己的 `NORMAL/EDITED` 消息，成功后状态变为 `RECALLED`，内容替换为“消息已撤回”。
- `ImService.toMessageResponse(...)` 对 `RECALLED` 消息不返回附件列表，避免撤回后继续通过消息响应暴露附件元数据。
- `ImService.createCurrentEmployeeMessageAttachmentDownloadTicket(...)` 和 `downloadCurrentEmployeeAttachment(...)` 增加消息状态门禁，撤回消息不能继续申请或消费附件下载。
- `ImRepository.updateMessageContent(...)` 和 `recallMessage(...)` 返回条件更新影响行数，状态已变化时服务层明确返回 409，避免并发或重复操作被误报成功。
- `ModuleImApiIntegrationTest` 新增编辑本人文本、拒绝编辑别人消息、拒绝编辑附件消息、撤回本人消息、撤回附件消息后禁止下载附件、已撤回消息不能再次编辑或撤回等覆盖。

当前后端已经能支撑前端演示的编辑/撤回主路径：本人可编辑文本消息，本人可撤回普通或已编辑消息，撤回后列表只显示撤回提示，且附件下载入口同步阻断。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `EmployeeImController.editMessage(...)`、`ImService.editCurrentEmployeeMessage(...)` | 开发规划中的“消息编辑”此前没有后端入口。当前新增编辑接口，校验当前用户仍是会话成员，且只能编辑自己发送的文本消息，避免普通成员修改别人消息或系统消息。 | 已处理 | 前端接入时只对当前用户发送的文本消息展示编辑入口，后端仍保留强校验。 |
| 2-2 | `EmployeeImController.recallMessage(...)`、`ImService.recallCurrentEmployeeMessage(...)` | 开发规划中的“消息撤回”此前没有后端入口。当前新增撤回接口，限制发送人本人操作，且只允许 `NORMAL/EDITED` 状态进入撤回。 | 已处理 | 后续如要支持管理员撤回或群主撤回，应新增独立权限分支和审计，不要复用本人撤回语义。 |
| 2-3 | `ImService.toMessageResponse(...)`、`buildRecallContentJson()` | 撤回后内容不再保留原文响应，而是替换成“消息已撤回”。这避免前端列表刷新后继续展示原始文本。 | 已处理 | 如果后续要做审计留痕，应单独写入后台审计或消息版本表，不应在员工端响应里暴露原文。 |
| 2-4 | `ImService.ensureMessageAllowsAttachmentDownload(...)` | 撤回附件消息后，附件元数据不再随消息响应返回，且 IM 附件下载票据创建和票据消费都会校验消息状态，撤回状态返回 409。 | 已处理 | 后续消息删除或群可见策略变化也应复用同一状态门禁，不要只拦创建票据。 |
| 2-5 | `ImRepository.updateMessageContent(...)`、`recallMessage(...)`、`ModuleImApiIntegrationTest.recalledMessageShouldNotBeEditedOrRecalledAgain` | 条件更新已返回影响行数，服务层在更新 0 行时返回 409；已撤回消息再次编辑或撤回均有回归测试。 | 已处理 | 后续补真正双线程并发编辑/撤回测试，验证竞争路径也返回稳定 409。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ModuleImApiIntegrationTest` | 当前已覆盖重复状态操作，但还没有真正双线程并发编辑/撤回测试。条件更新会挡住第二次状态迁移，但并发路径下的返回码和回归保护还不够硬。 | P2 | 增加双线程测试：一边编辑、一边撤回同一消息，只允许一个成功，另一个稳定返回 409。 |
| 3-2 | `ImService.editCurrentEmployeeMessage(...)` | 编辑会重写 `content_json` 中的文本和 `attachmentCount`，但当前只允许 `TEXT` 消息编辑，所以不会丢附件。若后续允许图文混合消息编辑，这段逻辑需要重新设计，否则可能丢失富文本、卡片或附件扩展字段。 | P2 | 扩展非纯文本编辑前先定义消息内容模型和版本化更新策略，不要继续用 `Map.of("text", ...)` 覆盖整块 JSON。 |
| 3-3 | `ImService.recallCurrentEmployeeMessage(...)` | 撤回后附件引用仍保留在 `sys_attachment_ref`，只是响应和下载入口阻断。演示上正确，但长期会让被撤回附件继续占用文件引用计数，后续清理任务无法释放对象。 | P2 | 实现撤回附件清理策略：可选择保留审计期后清理，或立即标记 `IM_MESSAGE` 附件删除并递减 `ref_count`。 |
| 3-4 | `ImService.toConversationSummaryResponse(...)` | 会话摘要的 `lastMessage` 如果是撤回消息，会显示“消息已撤回”；但如果撤回的不是最后一条消息，列表不会体现任何变更时间或更新事件。真实 IM 中编辑/撤回通常也要触发会话列表刷新或推送。 | P2 | 接 WebSocket/outbox 时为编辑/撤回发消息变更事件；必要时维护 `conversation.updated_at` 或独立事件序列。 |
| 3-5 | `ImService.ensureOwnMessageInConversation(...)` | 当前只允许发送人本人编辑/撤回，不能支持群主、管理员或企业合规角色撤回违规消息。演示功能足够，但真实企业协作可能需要治理撤回能力。 | P3 | 后续新增管理员/群主撤回接口，并强制写审计和系统消息。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 附件闭环后又加入编辑/撤回，`ImService` 已同时承载消息发送、编辑、撤回、附件下载、群治理、私聊准入和响应映射。继续加 WebSocket 会进一步膨胀。 | P2 | 下一轮优先拆 `ImMessageService` 和 `ImAttachmentService`，再接实时推送。 |
| 4-2 | `ImRepository` | 编辑/撤回更新、附件查询、下载票据、审计日志继续堆在单一 repository。当前能通过测试，但定位 SQL 风险和复用会越来越困难。 | P2 | 拆分消息写仓储、附件仓储和审计仓储；让每个仓储只处理一个表组。 |
| 4-3 | 消息状态常量 | `NORMAL/EDITED/RECALLED` 仍以字符串散落在 service、repository、测试和 DDL 中。状态越多越容易写错或漏改。 | P3 | 增加枚举或常量类，至少集中 IM 消息状态和消息类型。 |
| 4-4 | 编辑/撤回审计 | 当前编辑/撤回没有写 `sys_audit_log` 或系统消息。演示可以接受，但真实协作里谁在何时撤回、撤回了哪条消息应可追溯。 | P2 | 增加 `IM_MESSAGE_EDITED`、`IM_MESSAGE_RECALLED` 审计日志，群聊可同步一条系统事件。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只处理 IM 编辑/撤回和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/` 文件列表 | 已执行 | 当前最新为第 65 审，本轮使用第 66 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node66_im_edit_recall2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖文本会话、附件消息、群聊、编辑、撤回、撤回后附件下载阻断、已撤回消息重复操作 409。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有跑 employee-web Vitest 或浏览器联调，本轮目标是后端接口闭环。
- 没有覆盖编辑和撤回的并发竞争，尤其是同一消息同时编辑/撤回时的精确返回语义。
- 没有接 WebSocket/outbox，因此前端目前需要重新拉消息列表才能感知编辑/撤回。
- 没有写编辑/撤回审计和系统消息。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 编辑/撤回并发 | 当前已补条件更新 0 行返回 409，但还缺真正双线程竞争测试。 | P2 | 增加并发用例，验证编辑/撤回竞争下只允许一次状态迁移。 |
| 6-2 | WebSocket/outbox | 编辑/撤回完成 HTTP 闭环，但前端实时感知还没有。 | P2 | 下一节点优先补消息变更事件或最小 WebSocket 推送。 |
| 6-3 | 附件撤回清理 | 撤回后附件下载已阻断，但引用计数仍保留。 | P2 | 设计审计保留期和清理策略。 |
| 6-4 | 服务拆分 | IM 服务和仓储继续扩展前需要拆分。 | P2 | 先拆消息服务/仓储，再补实时推送。 |

## 第七部分：阶段性结论

本轮 `module-im` 消息编辑/撤回后端闭环已经达到演示可接入状态：前端可以调用编辑接口修改本人文本消息，调用撤回接口撤回本人消息；撤回后消息只显示“消息已撤回”，附件列表不再暴露，下载票据创建和消费都被消息状态阻断。

当前没有发现 P0/P1 阻断项，可以进入前端编辑/撤回按钮接入。但这仍不是完整生产级 IM：并发编辑/撤回返回语义、撤回附件清理、编辑/撤回审计、系统消息和 WebSocket 推送仍是下一批 P2 工作。
