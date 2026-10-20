# 第 74 审 module-im 会话成员展示名后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐 Agent 超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（会话详情成员展示名：USER 取 `org_user.real_name`，AGENT 取 `agt_agent.agent_name`）
- 审查范围：`server/src/main/java/com/workforcehub/im/dto/ImConversationMemberResponse.java`、`server/src/main/java/com/workforcehub/im/repository/ImConversationMemberRow.java`、`server/src/main/java/com/workforcehub/im/repository/ImRepository.java`、`server/src/main/java/com/workforcehub/im/service/ImService.java`、`server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/im/ModuleImGroupIntegrationTest.java`、`docs/审查/README.md`
- 审查目标：复核第 73 审提出的“详情成员缺少展示名”是否收口，重点检查用户成员和 Agent 成员展示名来源、权限边界、接口兼容性和测试覆盖；本轮不补头像、部门、岗位、在线状态和成员分页。

---

## 第一部分：提交内容概览

本轮补齐会话详情和群成员接口的轻量展示字段。

- `ImConversationMemberResponse` 新增 `displayName`。
- `ImConversationMemberRow` 新增 `displayName`。
- `ImRepository.listActiveUserMembers(...)` 关联 `org_user`，为 `USER` 成员返回 `real_name`。
- `ImRepository.listActiveMembers(...)` 同时关联 `org_user` 和 `agt_agent`，为 `USER` 成员返回员工姓名，为 `AGENT` 成员返回 Agent 名称。
- `ImService.toConversationMemberResponse(...)` 映射 `displayName`。
- 私聊详情测试新增 `张三`、`李四` 展示名断言。
- 群成员列表和群详情测试新增用户展示名断言。
- Agent 会话详情测试新增 Agent 成员展示名 `IM问答助手` 断言。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 73 审 3-1、`ImConversationMemberResponse` | 第 73 审指出会话详情成员只返回 ID、类型、角色和状态，前端详情面板会继续依赖 mock 或二次查询。当前响应新增 `displayName`，私聊、群聊和 Agent 会话详情均可直接展示成员名称。 | 已处理 | 前端接入详情面板时优先使用 `members[].displayName`，不要再按 ID 写死 mock 名称。 |
| 2-2 | `ImRepository.listActiveMembers(...)` | 第 73 审已区分“详情全量成员”和“群治理用户成员”。当前全量成员查询只用于详情，能返回 `AGENT` 成员展示名；群成员治理接口仍走 `listActiveUserMembers(...)`，不会把 Agent 成员混入用户拉人/移人语义。 | 已处理 | 后续如果引入机器人进群，仍应明确机器人治理接口，不要复用用户成员治理入口。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImRepository.listActiveUserMembers(...)`、`listActiveMembers(...)` | 展示名使用 `LEFT JOIN`，当成员对应的员工或 Agent 被软删除后，接口仍会返回成员行但 `displayName` 为空。触发条件是历史会话成员对应账号或 Agent 被删除；失败路径是前端需要降级展示 ID。当前测试覆盖正常成员，不覆盖软删除关联对象。 | P2 | 近期增加降级值，例如 `COALESCE(u.real_name, a.agent_name, CONCAT(member_type, ':', member_id))`；或者在响应增加 `displayNameMissing=true` 让前端显式降级。 |
| 3-2 | `ImConversationMemberResponse.displayName` | 当前只返回展示名，不返回头像、部门、岗位和在职状态。触发条件是前端详情面板需要成员卡片或离职成员标识；失败路径是仍需额外调用组织接口。 | P3 | 演示阶段可以先展示名称；进入通讯录联动时再扩展 `avatarFileId`、`deptName`、`positionName`、`employmentStatus`。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImRepository` | 成员展示名查询把 `org_user` 和 `agt_agent` 关联逻辑写在 IM 仓储中，短期实用，但继续加头像、部门、岗位、在线状态后会让 IM 仓储承担通讯录读模型职责。 | P2 | 抽 `ImMemberQueryRepository` 或统一成员卡片读模型，集中处理不同成员类型的展示字段。 |
| 4-2 | `ModuleImApiIntegrationTest`、`ModuleImGroupIntegrationTest` | 展示名断言继续追加在大集成测试类中。虽然覆盖有效，但随着详情接口字段增加，测试类会更难维护。 | P2 | 后续拆 `ModuleImConversationDetailIntegrationTest`，把详情字段断言集中在一个类。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区有多模块增量，本轮只在 IM 成员展示名和审查文档范围继续。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 73 审，本轮新增第 74 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 展示名目标测试 | `env TEST_DB_NAME=workforce_hub_node74_im_member_display mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖私聊详情、群聊详情、群成员列表、Agent 会话详情中的展示名字段。 |
| 本轮差异格式检查 | `git diff --check -- server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java server/src/test/java/com/workforcehub/im/ModuleImGroupIntegrationTest.java docs/审查/README.md docs/审查/第73审_module-im会话详情后端闭环审查.md` | 通过 | 未发现空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，本轮以 IM 目标测试为准。
- 没有跑 employee-web 测试，前端尚未读取 `displayName`。
- 没有覆盖成员关联对象软删除后的展示名降级。
- 没有验证头像、部门、岗位和在线状态。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 展示名降级 | 软删除关联对象会导致 `displayName` 为空。 | P2 | 补 `COALESCE` 降级值，并追加软删除成员测试。 |
| 6-2 | 成员卡片字段 | 前端详情面板如果要展示头像和部门仍不够。 | P3 | 演示先用名称和首字母头像；通讯录节点再补完整卡片。 |
| 6-3 | 查询职责 | IM 仓储开始承担成员展示读模型。 | P2 | 后续拆成员查询仓储或成员卡片服务。 |

## 第七部分：阶段性结论

本轮补齐了会话详情演示最直接的展示缺口：前端不再只能拿成员 ID，私聊、群聊和 Agent 会话都能直接显示成员名称。当前没有新增 P0/P1 阻断项，可以支撑前端详情面板先完成可用展示。

剩余主要是体验增强和可维护性治理：头像、部门、岗位、软删除降级和成员查询服务拆分都应在通讯录联动或组织群同步前处理。
