# 第 56 审 module-agent 后台基础治理接口审查

- 审查日期：2026-05-11
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/agent/**`、`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java`、`docs/开发日志/module-agent.md`、`docs/API与测试/module-agent/**`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核后台 Agent 列表、详情和停用接口是否补齐最小治理闭环，并确认停用后员工端可见性和任务创建语义是否自洽。

---

## 第一部分：本轮内容概览

本轮在已有后台创建、版本、工具、ACL、发布能力上，补齐后台基础治理读取和下线能力：

- 新增 `GET /api/admin/agents`，返回分页 Agent 列表。
- 新增 `GET /api/admin/agents/{agentId}`，返回 Agent 详情。
- 新增 `POST /api/admin/agents/{agentId}/disable`，停用 Agent。
- 停用只更新 `agt_agent.status=INACTIVE`，不清空 `published_version_id`。
- 停用后后台仍可查询详情，员工端列表不再展示，员工创建任务返回 `code=409`。
- 员工创建任务的校验顺序调整为先判断 Agent 发布和启用状态，再判断 ACL，避免已停用 Agent 误报无权限。

本轮没有新增 Flyway 迁移，继续复用 `agt_agent.status` 和 `updated_by`。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 后台治理接口红灯 | `env TEST_DB_NAME=workforce_hub_agent_admin_red mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListGetAndDisablePublishedAgent test` | 按预期失败 | `GET /api/admin/agents` 未实现，返回方法不支持并被包装为 500。 |
| 后台治理接口绿灯 | `env TEST_DB_NAME=workforce_hub_agent_admin_green mvn -Dtest=ModuleAgentApiIntegrationTest#adminShouldListGetAndDisablePublishedAgent test` | 通过 | 覆盖列表、详情、停用和员工侧停用效果。 |
| Agent 模块回归 | `env TEST_DB_NAME=workforce_hub_agent_admin_full mvn -Dtest=ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 14 个 Agent 集成用例全部通过。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 环境下编译成功。 |
| 跨模块回归 | `env TEST_DB_NAME=workforce_hub_agent_admin_regression mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test` | 通过 | 55 个集成用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `AdminAgentController` | 后台只有创建接口，没有列表和详情接口，管理员无法通过 API 查看已创建 Agent。 | 重要 | 新增 `GET /api/admin/agents` 和 `GET /api/admin/agents/{agentId}`。 |
| 1-2 | `AdminAgentController` | 已发布 Agent 没有停用入口，发现配置错误或风险时无法通过 API 下线。 | 重要 | 新增 `POST /api/admin/agents/{agentId}/disable`。 |
| 1-3 | `AgentService#createCurrentEmployeeTask` | 停用后如果先走 ACL 判断，员工创建任务可能返回 `403 无权使用该Agent`，无法区分无权限和已停用。 | 建议 | 调整为先判断发布和启用状态，再判断 ACL；停用后返回 `409 Agent未发布或未启用`。 |
| 1-4 | 停用语义 | 如果停用时清空 `published_version_id`，后台无法区分未发布草稿和曾发布后下线。 | 建议 | 停用只更新 `status=INACTIVE`，保留已发布版本引用。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 后台列表 | 已返回 `PageResult<AgentResponse>`，分页参数做 1 到 100 的安全收敛。 |
| 后台详情 | 复用 `getAgentOrThrow`，不存在或已删除时返回 `Agent不存在`。 |
| 停用效果 | 后台详情可见 `INACTIVE`，员工列表过滤掉停用 Agent。 |
| 员工创建任务 | 停用后创建任务返回 `409 Agent未发布或未启用`，不创建 `agt_task`。 |
| 数据库迁移 | 本轮无 schema 变更，不需要新增 Flyway 迁移。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 后台任务查询 | 当前仍没有后台任务列表和详情，管理员不能直接查看等待、超时、失败、取消任务。 | 重要 | 下一批补 `GET /api/admin/agent-tasks` 和详情接口，支持状态、Agent、触发人和时间筛选。 |
| 2-2 | 停用后的等待任务 | Agent 停用不会自动处理已有 `WAITING_HUMAN_CONFIRM` 任务，旧任务仍需依赖人工动作或超时扫描收口。 | 重要 | 后续定义停用时等待任务是保持、自动取消还是升级管理员。 |
| 2-3 | 重新启用与回滚 | 当前只有停用，没有重新启用和版本回滚。 | 建议 | 增加启用接口、版本回滚接口和对应审计。 |
| 2-4 | 后台查询维度 | 列表目前只有分页，没有按状态、类型、编码/名称搜索。 | 建议 | 后续按后台页面需要补筛选条件。 |
| 2-5 | 后台审计 | 停用 Agent 目前只更新 `updated_by`，没有统一后台审计表记录。 | 建议 | 等后台审计模块落地后接入治理动作审计。 |

---

## 第六部分：阶段性结论

`module-agent` 后台基础治理已从“只能创建配置”推进到“可查看、可确认状态、可下线”的最小闭环。

下一步建议优先补后台 Agent 任务查询，因为已有任务创建、人工确认、取消、超时扫描和停用后，管理员需要一个入口查看任务状态和排查失败原因。
