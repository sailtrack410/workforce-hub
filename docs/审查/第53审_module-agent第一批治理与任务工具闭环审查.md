# 第 53 审 module-agent 第一批治理与任务工具闭环审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/**`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `module-agent` 第一批后台治理、员工授权、任务创建、同步工具调用、人工确认等待和失败审计是否形成可继续开发的后端骨架。

---

## 第一部分：本轮内容概览

本轮完成 `module-agent` 第一批后端闭环：

- 后台端新增 Agent 创建、版本创建、工具创建、版本工具绑定、ACL 授权和版本发布接口。
- 员工端新增可用 Agent 列表和 Agent 任务创建接口。
- Agent ACL 支持 `USER`、`DEPARTMENT`、`GROUP` 三类员工侧授权判断。
- `GROUP` ACL 明确使用 `im_conversation.id`，并按活跃群、活跃群成员判断。
- 任务支持同步执行第一批 `SPI` 工具，工具调用写入 `agt_tool_call_log`。
- 高风险或需确认工具进入 `WAITING_HUMAN_CONFIRM`，不直接执行。
- 业务型工具执行失败时保留 `FAILED` 任务、失败工具日志和审计记录。
- 测试基线补充 `agt_*` 表清理，避免 Agent 测试污染其他模块。

本轮没有新增 Flyway 迁移，直接使用 `V1__init_schema.sql` 已存在的 `agt_*` 表。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 群组 ACL 红灯 | `env TEST_DB_NAME=workforce_hub_agent_group_red mvn -Dtest=ModuleAgentApiIntegrationTest#groupAclShouldAuthorizeOnlyActiveGroupMembers test` | 按预期失败 | 暴露 `GROUP` ACL 写入后员工侧不生效。 |
| 群组 ACL 绿灯 | `env TEST_DB_NAME=workforce_hub_agent_group_green mvn -Dtest=ModuleAgentApiIntegrationTest#groupAclShouldAuthorizeOnlyActiveGroupMembers test` | 通过 | 群成员可见并可用，非群成员不可见且不可用。 |
| 失败任务红灯 | `env TEST_DB_NAME=workforce_hub_agent_failed_task_red mvn -Dtest=ModuleAgentApiIntegrationTest#toolExecutionFailureShouldKeepFailedTaskAndLog test` | 按预期失败 | 暴露工具业务失败被事务回滚，失败任务无法留痕。 |
| 失败任务绿灯 | `env TEST_DB_NAME=workforce_hub_agent_failed_task_green mvn -Dtest=ModuleAgentApiIntegrationTest#toolExecutionFailureShouldKeepFailedTaskAndLog test` | 通过 | 失败任务、失败工具日志和审计均保留。 |
| 空 `toolCalls` 红灯 | `env TEST_DB_NAME=workforce_hub_agent_null_tool_red mvn -Dtest=ModuleAgentApiIntegrationTest#nullToolCallsShouldBeTreatedAsEmptyList test` | 按预期失败 | 暴露外部输入 `toolCalls:null` 导致 NPE。 |
| 空 `toolCalls` 绿灯 | `env TEST_DB_NAME=workforce_hub_agent_null_tool_green mvn -Dtest=ModuleAgentApiIntegrationTest#nullToolCallsShouldBeTreatedAsEmptyList test` | 通过 | 显式 `null` 按空列表处理。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_full_green2 mvn -Dtest=ModuleAgentApiIntegrationTest test` | 通过 | 7 个 Agent 集成用例全部通过。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | Maven 编译成功，Java 17 release 配置生效。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest test` | 通过 | 48 个用例全部通过，认证、组织、IM 群组和 Agent 兼容。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `AgentRepository#listUsableAgentsForUser`、`AgentRepository#canUseAgent` | `GROUP` ACL 可写入但员工侧查询不消费群成员关系，管理员配置群组授权后实际无效。 | 重要 | 授权 SQL 增加 `im_conversation_member`、`im_conversation`、`im_group_profile` 判断，`subjectId` 统一为群聊 `im_conversation.id`。 |
| 1-2 | `AgentService#createCurrentEmployeeTask` | 同步工具执行抛业务异常后重新抛出，事务回滚会抹掉已写入的 `FAILED` 任务和审计。 | 重要 | 业务型工具失败转为任务态 `FAILED`，写失败工具日志和 `TASK_FAILED` 审计后正常返回任务响应。 |
| 1-3 | `AgentService#createCurrentEmployeeTask` | 外部输入显式传 `toolCalls:null` 时空指针，返回 500。 | 重要 | 服务层增加 `safeToolCalls`，把 null 规范化为空列表。 |
| 1-4 | `LocalIntegrationTestSupport` | 测试库复位未清理 `agt_*` 表，后续 Agent 测试会污染其他用例。 | 重要 | 复位逻辑增加 `agt_tool_call_log`、`agt_audit_log`、`agt_task`、`agt_trigger`、`agt_knowledge_binding`、`agt_agent_acl`、`agt_agent_tool`、`agt_agent_version`、`agt_tool`、`agt_agent` 清理。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 后台治理入口 | 创建、版本、工具、绑定、ACL、发布接口已形成可运行链路。 |
| 员工端授权 | `USER`、`DEPARTMENT`、`GROUP` 授权均可影响可见和使用判断。 |
| 工具边界 | 未绑定工具在任务落库前拦截，已绑定工具才进入执行或等待确认。 |
| 任务状态 | 成功、失败、等待人工确认三类状态均有自动化覆盖。 |
| 审计与日志 | 成功、失败、等待确认均写入对应工具调用日志或审计记录。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AgentService`、`AgentRepository` | 第一批为了快速跑通闭环，服务和 JDBC 仓储仍偏大，后续继续追加人工确认、调度、触发器会膨胀。 | 重要 | 第二批优先拆出 `AgentAdminService`、`AgentTaskService`、`AgentToolExecutor`、`AgentAclQueryRepository` 等边界。 |
| 2-2 | `agt_agent_acl.ADMIN_USER/manage` | 表结构和服务枚举允许后台管理员 ACL，但当前后台接口仍以管理员会话全局治理为准，没有按 Agent `manage` ACL 做委托管理。 | 建议 | 后续如果需要“部分管理员只能管理部分 Agent”，应新增后台管理权限拦截和对应测试；否则文档保持为预留。 |
| 2-3 | `agt_tool_call_log.request_payload_json` | 当前完整保存工具请求和响应，后续真实工具可能包含手机号、邮箱、文件内容摘要等敏感数据。 | 重要 | 引入字段级脱敏策略和工具级日志保留策略，避免敏感信息长期明文留存在工具调用日志中。 |
| 2-4 | 同步执行模型 | 当前 `AUTO` 工具同步执行，适合第一批集成测试，不适合长耗时 Agent、RAG、外部连接器和重试。 | 重要 | 后续增加异步任务执行器、重试、超时、取消、运行监控和任务查询接口。 |
| 2-5 | 人工确认链路 | 当前只生成 `WAITING_HUMAN_CONFIRM`，还没有确认继续执行、驳回和超时取消接口。 | 重要 | 下一批补人工确认动作接口和超时扫描，并覆盖并发确认幂等。 |
| 2-6 | Workflow/IM 触发 | 当前只是手动创建 Agent 任务，尚未实现 `WorkflowAgentPreReviewTrigger` 和 `ImAgentTriggerService`。 | 重要 | 后续把 workflow 预审 SPI 和 IM 会话 Agent 触发正式接入真实 Agent 任务。 |

---

## 第六部分：阶段性结论

`module-agent` 第一批已经从“只有表结构和 SPI 预留”进入“后台可配置、员工可发现、任务可落库、工具可调用、失败可追踪”的后端骨架状态。它还不是完整 Agent 平台，但已经具备继续接 workflow 预审、IM 会话 Agent、RAG 知识库和连接器工具的基础。

下一步建议先拆分 Agent 服务边界，再接人工确认和 workflow/IM 触发，避免在单个服务类上继续堆功能。
