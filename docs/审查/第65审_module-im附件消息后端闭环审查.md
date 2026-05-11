# 第 65 审 module-im 附件消息后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 附件消息后端闭环：个人文件克隆为 IM 消息附件、消息响应返回附件、IM 专属下载票据与成员鉴权）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核第 64 审中“附件消息未完成”的后端缺口是否已形成可演示闭环，重点看 ownerType 边界、附件归属校验、会话成员授权、下载票据复核、幂等与测试保护。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 附件消息做后端收口。

- `SendImMessageRequest` 新增 `attachmentRefIds`，允许文本消息携带附件，也允许附件消息在无文本时发送。
- `ImService.sendCurrentEmployeeMessage(...)` 在发送消息前校验会话成员身份、当前员工状态和附件可用性；附件必须是当前用户拥有的 `PERSONAL_FILE`，且未删除、物理对象未删除、安全扫描未失败。
- `ImRepository.createImMessageAttachmentRef(...)` 将个人文件引用克隆为 `owner_type='IM_MESSAGE'`、`owner_id=messageId` 的业务附件，并递增 `sys_file_object.ref_count`，没有放开员工上传接口直接写 `IM_MESSAGE`。
- `ImMessageResponse` 新增 `attachments`，消息列表和发送结果会返回克隆后的 IM 消息附件元数据。
- `EmployeeImController` 新增 IM 附件下载票据接口和下载执行入口：`POST /api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets`、`GET /api/employee/im/attachments/download/{ticketNo}`。
- `ImService.createCurrentEmployeeMessageAttachmentDownloadTicket(...)` 和 `downloadCurrentEmployeeAttachment(...)` 在创建票据和消费票据时都复核当前用户仍是会话成员，避免绕过个人文件下载入口。
- `ModuleImApiIntegrationTest` 新增附件发送、非本人附件拒绝、成员下载、非成员下载拒绝、普通个人文件入口隔离等覆盖。

当前后端已经能支撑前端演示的附件主路径：员工先通过文件模块上传个人文件，再在 IM 发送消息时引用个人附件，服务端生成 IM 消息附件，其他会话成员通过 IM 授权入口申请下载票据并下载文件。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `SendImMessageRequest.attachmentRefIds`、`ImService.sendCurrentEmployeeMessage(...)` | 第 64 审明确附件消息尚未完成。当前发送接口已支持附件引用，并允许“有附件、无文本”的消息进入后端主路径，避免前端继续用本地 mock 或把附件塞进纯文本字段。 | 已处理 | 前端接入时应使用 `attachmentRefIds`，不要把文件 URL、文件名或个人附件 ID 拼进 `text`。 |
| 2-2 | `ImService.ensureUsablePersonalAttachment(...)`、`ImRepository.createImMessageAttachmentRef(...)` | 当前没有放开员工端上传 `ownerType=IM_MESSAGE`，而是在发送消息时把当前用户拥有的 `PERSONAL_FILE` 克隆为 `IM_MESSAGE`。这样保留了文件模块“员工上传只进入个人文件池”的边界，也让 IM 附件拥有独立业务授权入口。 | 已处理 | 后续如果要支持直接上传到会话，应新增 IM 专用上传策略和会话成员校验，不应修改个人文件接口去兼容业务附件。 |
| 2-3 | `ImMessageResponse.attachments`、`ImRepository.listMessageAttachments(...)` | 消息发送和消息列表现在会返回克隆后的 IM 消息附件元数据，包括 `attachmentRefId`、`displayName`、`mimeType`、`fileSize`、`contentSha256`、`scanStatus`。前端不需要再反查个人文件。 | 已处理 | 前端应存储并展示响应里的 IM 附件 ID，下载时也传 IM 附件 ID，不要继续使用源个人附件 ID。 |
| 2-4 | `ImService.createCurrentEmployeeMessageAttachmentDownloadTicket(...)`、`downloadCurrentEmployeeAttachment(...)` | IM 附件下载不复用 `/api/employee/files/{attachmentRefId}/download-tickets`，而是通过 IM 专属入口校验会话、消息、附件三者绑定关系。非会话成员不能申请下载票据，票据消费时也会重新校验当前用户仍可访问该会话。 | 已处理 | 若后续加入退群后历史可见策略，应在票据创建和消费两处同时接入历史可见判断。 |
| 2-5 | `ModuleImApiIntegrationTest.imAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint` | 已补测试证明 `owner_type='IM_MESSAGE'` 的附件不能绕过 IM 授权入口走普通个人文件下载接口，普通文件接口仍只允许 `PERSONAL_FILE`。 | 已处理 | 继续保持各业务附件通过各自模块授权入口下载，避免文件模块变成宽权限后门。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImService.ensureUsablePersonalAttachment(...)`、`ensureDownloadableMessageAttachment(...)` | 当前只拒绝 `scan_status='FAILED'`，没有拒绝 `PENDING`。这延续了审批附件当前口径，能保证演示中刚上传的文件可立即发送；但在真实生产环境，如果异步扫描未完成，附件可能在安全结论出来前被会话成员下载。 | P2 | 上线前明确扫描策略：要么只允许 `PASSED/SKIPPED`，并给前端返回“扫描中”；要么在租户配置中把“扫描未完成可下载”作为显式风险开关。 |
| 3-2 | `ImService.sendCurrentEmployeeMessage(...)`、`ImRepository.findMessageByClientMessageId(...)` | 重复 `clientMessageId` 会直接返回既有消息，不比较第二次请求的文本和附件集合是否一致。网络重试场景正确，但如果前端错误复用同一个客户端消息 ID 发送不同附件，服务端会静默返回旧消息，调用方可能误以为新附件已发送。 | P2 | 在消息内容稳定后保存客户端请求摘要或附件 ID 指纹；重复幂等键但内容不一致时返回 409，避免错误重试被误认为成功。 |
| 3-3 | `ImService.toMessageResponse(...)`、`ImRepository.listMessageAttachments(...)` | 每条消息响应都会单独查询附件。当前默认分页最多 100 条，演示数据量可以接受；但真实会话历史加载时会形成 N+1 查询，附件消息多时会拉高首屏耗时。 | P2 | 将消息分页和附件列表改成批量查询：先取一页消息 ID，再一次性查 `owner_type='IM_MESSAGE' AND owner_id IN (...)`，按消息 ID 分组回填。 |
| 3-4 | `ImRepository.createImMessageAttachmentRef(...)` | 克隆 IM 附件会递增 `sys_file_object.ref_count`，但当前没有 IM 消息删除、撤回或附件清理路径来递减引用。只要后续实现撤回/删除消息，如果不同时处理附件引用和对象引用计数，会导致文件对象长期无法清理。 | P2 | 实现消息撤回/删除时同步标记 `IM_MESSAGE` 附件删除，并调用文件对象引用计数递减或统一清理服务。 |
| 3-5 | `ImService.downloadCurrentEmployeeAttachment(...)` | 下载票据消费时会重新校验请求人和会话成员身份，但测试尚未覆盖“票据创建后用户被移出群聊或会话成员状态变更”的场景。当前代码路径理论上会拒绝，但缺少回归保护。 | P2 | 补集成测试：成员创建下载票据后被移出群聊，再消费票据应返回 403 且票据保持可观测状态。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 服务层现在同时承担员工状态复核、会话成员授权、私聊准入、群成员治理、消息发送、附件克隆、下载票据、下载执行、DTO 映射和内容 JSON 解析。附件闭环后该类已经进入拆分窗口。 | P2 | 下一节点前拆出 `ImMessageService`、`ImAttachmentService`、`ImGroupService`，避免 WebSocket、撤回、系统消息继续堆到一个服务里。 |
| 4-2 | `ImRepository` | 仓储层继续聚合会话查询、组织用户状态、成员关系、消息写入、附件引用、下载票据、审计日志。当前可跑，但职责已经跨越 IM、文件、审计多个边界。 | P2 | 拆成 `ImConversationRepository`、`ImMessageRepository`、`ImAttachmentRepository`、`ImAuditRepository`；附件读模型可以独立维护。 |
| 4-3 | `ImMessageResponse.contentJson` 与 `attachments` | 响应同时暴露原始 `contentJson` 和结构化 `attachments`。短期方便前端兼容，但长期会出现前端直接解析 `contentJson` 和使用 DTO 字段两套读取方式。 | P3 | 明确前端契约：展示文本用 `contentText`，附件用 `attachments`，`contentJson` 只作为兼容字段或调试字段。 |
| 4-4 | 测试辅助方法 `ModuleImApiIntegrationTest.seedPersonalAttachmentRef(...)` | IM 测试直接插入 `sys_file_object` 和 `sys_attachment_ref`，与 workflow 测试已有类似 helper，存在重复测试造数逻辑。 | P3 | 后续可把个人附件种子方法抽到测试 support，统一字段默认值和本地对象写入方式。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在 employee-web、agent、leave、workflow 等多模块未提交改动；本轮只处理 IM 附件闭环和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/` 文件列表 | 已执行 | 当前最新为第 64 审，本轮使用第 65 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node65_im_attachment2 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖文本会话、群聊、同群私聊、离职状态拦截、消息幂等、附件发送、附件下载和越权拒绝。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有跑 employee-web Vitest 或浏览器联调，本轮目标是后端接口闭环。
- 没有覆盖 IM 附件下载票据并发消费测试；当前复用 `DownloadTicketExecutionService.claimActiveTicket(...)` 的条件更新模型，但缺少 IM 专属并发用例。
- 没有覆盖附件扫描 `PENDING` 的产品策略测试；当前只拒绝 `FAILED`，这需要后续按安全策略确认。
- 没有覆盖消息撤回/删除后的 IM 附件引用清理，因为撤回/删除功能尚未实现。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 附件扫描策略 | 当前附件闭环可演示，但生产安全口径仍需确认 `PENDING` 是否可发送和下载。 | P2 | 若准备真实试点，优先补扫描中状态和下载阻断策略。 |
| 6-2 | `ImService`、`ImRepository` | 附件闭环已让 IM 服务/仓储继续膨胀，下一批 WebSocket 或撤回前应拆职责。 | P2 | 先拆消息和附件服务，再接推送/outbox。 |
| 6-3 | 消息附件批量查询 | 当前 N+1 查询会影响历史消息分页扩展。 | P2 | 在前端大量拉历史消息前补批量附件查询。 |
| 6-4 | 票据状态变更测试 | 票据创建后成员退出、员工离职、账号停用等场景缺少专门测试。 | P2 | 补“创建票据后权限变化”的回归测试。 |

## 第七部分：阶段性结论

本轮 `module-im` 附件消息后端闭环已经达到演示可接入状态：前端可以复用个人文件上传结果发送附件消息，后端克隆为 IM 消息附件，消息列表返回附件元数据，会话成员可通过 IM 专属入口申请下载票据并下载文件，非成员和普通个人文件下载入口均不能绕过授权。

当前没有发现 P0/P1 阻断项，可以进入前端附件消息接入。但这不是完整生产级 IM：扫描中附件策略、消息撤回后的附件清理、附件查询 N+1、下载票据并发/权限变化测试、WebSocket 推送和 IM 服务拆分仍应作为下一批 P2 工作继续收口。
