# 第 75 审 module-im 会话偏好设置后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（员工端会话置顶、免打扰偏好更新接口与推送事件）
- 审查范围：`server/src/main/java/com/workforcehub/im/controller/EmployeeImController.java`、`server/src/main/java/com/workforcehub/im/dto/UpdateConversationPreferenceRequest.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`docs/审查/README.md`
- 审查目标：复核会话摘要中的 `pin`、`mute` 是否从“只读字段”补齐为员工可更新的后端闭环，重点检查成员归属、空请求、局部更新、实时事件和测试保护；本轮不处理置顶排序规则、偏好批量更新和前端页面接入。

---

## 第一部分：提交内容概览

本轮新增员工端会话偏好设置能力。

- 新增 `UpdateConversationPreferenceRequest`，支持 `pin` 和 `mute` 两个可选布尔字段。
- 新增 `POST /api/employee/im/conversations/{conversationId}/preference`。
- `ImService.updateCurrentEmployeeConversationPreference(...)` 校验当前员工状态和会话成员关系。
- 空请求返回 `400`：`会话偏好更新内容不能为空`。
- `ImRepository.updateMemberPreference(...)` 只更新当前员工自己的 `im_conversation_member` 行，支持只传 `pin` 或只传 `mute`。
- 更新后返回最新 `ImConversationSummaryResponse`，前端可立即刷新列表项。
- 更新成功后推送 `IM_CONVERSATION_PREFERENCE_UPDATED`，payload 包含 `userId`、`pin`、`mute`。
- `ModuleImApiIntegrationTest` 新增偏好更新成功、空请求拒绝、非成员拒绝三类覆盖。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImConversationSummaryResponse.pin/mute`、`EmployeeImController.updatePreference(...)` | 会话列表和详情此前已经返回 `pin`、`mute`，但没有员工端修改入口，前端无法完成置顶和免打扰交互闭环。当前新增偏好更新接口，更新后直接返回摘要。 | 已处理 | 前端可把会话详情或列表中的置顶/免打扰开关接到该接口。 |
| 2-2 | `ImRepository.updateMemberPreference(...)` | 偏好属于当前用户在会话里的成员关系，而不是会话全局属性。当前 SQL 按 `conversation_id + member_type='USER' + member_id + status='ACTIVE'` 更新，只影响当前员工自己的成员行。 | 已处理 | 后续增加群管理员偏好时不要写入 `im_conversation` 全局表。 |
| 2-3 | `ModuleImApiIntegrationTest.employeeShouldNotUpdatePreferenceOfUnjoinedConversation` | 非成员更新偏好会先经过 `ensureCanAccessConversation(...)`，存在会话但不是成员返回 403。 | 已处理 | 继续保持与消息读取、已读位点更新相同的访问边界。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImRepository.listActiveConversationsForUser(...)` | 本轮只补偏好更新，没有恢复“置顶会话优先排序”。触发条件是用户把某会话设为置顶但该会话最后消息较旧；失败路径是列表接口仍按最新消息时间排序，置顶字段为 true 但排序不靠前。第 67 审已记录该风险，本轮没有收口。 | P2 | 若前端演示包含置顶排序，应补基于 `pin_flag DESC, lastMessageAt DESC, id DESC` 的游标排序，并同步调整游标参数。 |
| 3-2 | `IM_CONVERSATION_PREFERENCE_UPDATED` | 偏好更新事件广播给会话活跃成员，但偏好本身是当前用户私有状态。触发条件是 A 用户设置免打扰或置顶；失败路径是 B 用户收到事件但 payload 里的偏好不属于自己，如果前端不按 `userId` 过滤可能误刷新。 | P2 | 偏好事件应只推给操作者本人，或事件类型明确为用户私有事件。当前前端接入时必须按 payload `userId` 判断。 |
| 3-3 | `UpdateConversationPreferenceRequest` | 请求字段都是可空布尔，JSON 里传入非布尔类型会依赖 Jackson 默认反序列化错误路径。触发条件是前端传 `"pin":"yes"`；失败路径是返回通用参数错误，不是业务错误。 | P3 | 如需更清晰错误，可增加全局 JSON 类型错误处理或改成字符串枚举动作接口。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 偏好设置继续追加在已很大的 `ImService` 中。该类现在同时承担会话查询、详情、消息写入、附件、群治理、Agent 和偏好。 | P2 | 拆 `ImConversationPreferenceService` 或先拆 `ImConversationQueryService`，避免所有会话能力继续堆在一个类。 |
| 4-2 | `ImRepository.updateMemberPreference(...)` | 动态 SQL 使用局部列表拼接，当前字段少且可控；后续偏好字段增加后容易扩展成一组重复动态赋值逻辑。 | P3 | 偏好字段继续增长时改为固定参数 SQL 或偏好 JSON 模型，但不要提前过度设计。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块增量，本轮只在 IM 偏好设置和审查文档范围继续。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 74 审，本轮新增第 75 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 偏好目标测试 | `env TEST_DB_NAME=workforce_hub_node75_im_preference mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖偏好更新成功、空请求、非成员拒绝，并回归 IM 与群治理主路径。 |
| 本轮差异格式检查 | `git diff --check -- server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java server/src/test/java/com/workforcehub/im/ModuleImGroupIntegrationTest.java docs/审查/README.md docs/审查/第74审_module-im会话成员展示名后端闭环审查.md` | 通过 | 未发现空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，本轮以 IM 目标测试为准。
- 没有跑 employee-web 测试，前端尚未接入偏好接口。
- 没有验证置顶排序，因为本轮没有调整列表排序规则。
- 没有真实浏览器/WebSocket 客户端验证，当前推送验证仍是测试内存 session。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 置顶排序 | 已能设置置顶，但列表排序还未体现置顶优先。 | P2 | 如果演示要展示置顶，把排序和游标一起补齐。 |
| 6-2 | 偏好事件收件人 | 当前广播给会话活跃成员，语义偏宽。 | P2 | 改成只推送给操作者本人，或前端按 `userId` 过滤。 |
| 6-3 | 服务拆分 | IM 服务和仓储继续膨胀。 | P2 | 下一阶段进入组织群同步或 outbox 前先拆查询、偏好、消息写侧。 |

## 第七部分：阶段性结论

本轮把会话偏好设置从只读字段补成了可更新接口。前端现在可以完成置顶和免打扰开关的基本交互，并拿到更新后的会话摘要。

当前没有新增 P0/P1 阻断项。需要注意的是，置顶“设置成功”和“列表置顶排序”不是同一个闭环；如果演示明确展示置顶排序，下一节点必须补列表排序和游标规则。
