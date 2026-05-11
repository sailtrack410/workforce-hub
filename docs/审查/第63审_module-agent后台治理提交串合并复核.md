# 第 63 审 module-agent 后台治理提交串合并复核

- 审查日期：2026-05-11
- 审查提交：
  - `65c102de1680e26549fb550f4314aa0b0bfdaea9`（补齐Agent后台基础治理接口）
  - `2df8d2e0fcc81a2204c56d903a04d62bbd83dd04`（补齐Agent后台手动超时扫描接口）
  - `2e58ca13797b75139f374eddd0dfba98b5c85f3f`（补齐Agent后台任务查询接口）
  - `a6d654d318bc14dcb8a392eb02934b7fceb0c430`（补齐Agent任务明细与审计轨迹接口）
  - `00409c5424d5b8bb7aed0649cfe4a40bacaa1962`（补齐Agent任务运行指标与失败聚合）
  - `d19aab598332e501ff804e17a32cfe93aaede527`（生产化Agent人工确认超时扫描）
  - `3748724a51f115af42114d558ed982048fb391d7`（补齐Agent超时扫描批次查询）
- 审查范围：`server/src/main/java/com/workforcehub/agent/**`、`server/src/main/resources/db/migration/V12__add_agent_timeout_job_records.sql`、`server/src/test/java/com/workforcehub/agent/**`、`docs/API与测试/module-agent/**`、`docs/开发日志/module-agent.md`、`docs/审查/第56审` 至 `第62审`
- 审查目标：按 `docs/审查/审查规范.md` 对 `65c102d..3748724` 这一串 `module-agent` 最近提交做合并复核，重点检查跨提交后才会暴露的并发一致性、任务状态机、失败留痕和后台治理边界；本审只新增审查文件和更新索引，不修改业务代码。

---

## 第一部分：提交内容概览

这 7 个提交把 `module-agent` 从“有基础任务骨架”推进到“后台可治理、员工可确认、超时可扫描、运行可观测”的第一版闭环：

- 后台补齐 Agent 列表、详情、停用接口。
- 后台补齐人工确认超时扫描手动入口。
- 后台补齐任务列表、详情、工具调用明细、审计轨迹、运行指标、失败聚合和超时扫描批次查询。
- 超时扫描从服务能力升级为带 `sys_job_lock` 和 `agt_task_timeout_job_run` 的生产化任务。
- 员工侧继续复用现有创建、确认、拒绝、取消任务链路。

单次提交各自的接口缺口基本都被补齐，但这串提交合并后，已经开始承载“多管理员治理 + 多工具确认 + 失败审计 + 定时任务观测”的组合语义，风险点不再只在某一个 Controller。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 原问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | 第 56 审 | 后台缺少 Agent 列表、详情和停用入口。 | P1 | 已补 `GET /api/admin/agents`、`GET /api/admin/agents/{agentId}`、`POST /api/admin/agents/{agentId}/disable`。 |
| 1-2 | 第 57 审 | 超时扫描只有服务能力，没有后台手动入口。 | P1 | 已补 `POST /api/admin/agent-tasks/timeout-scan`。 |
| 1-3 | 第 58 审 | 后台缺少 Agent 任务列表和详情查询。 | P1 | 已补 `GET /api/admin/agent-tasks` 和 `GET /api/admin/agent-tasks/{taskId}`。 |
| 1-4 | 第 59 审 | 后台无法查看工具调用明细和审计轨迹。 | P1 | 已补 `/{taskId}/tool-calls` 和 `/{taskId}/audits`。 |
| 1-5 | 第 60 审 | 后台无法直接看任务运行指标和失败聚合。 | P2 | 已补 `GET /metrics` 和 `GET /failure-summary`。 |
| 1-6 | 第 61 审 | 超时扫描缺少生产化调度、任务锁和批次记录。 | P1 | 已补 `V12`、`sys_job_lock`、`agt_task_timeout_job_run` 和 `@Scheduled` 扫描入口。 |
| 1-7 | 第 62 审 | 已有批次记录表，但后台没有批次查询接口。 | P2 | 已补 `GET /api/admin/agent-tasks/timeout-scan-runs`。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AgentService.publishVersion()`、`AgentRepository.publishVersion()`、`agt_agent_version` | 版本发布是“先查草稿状态，再归档旧 `PUBLISHED`，再把目标版本改成 `PUBLISHED`”，但整个过程没有对同一 `agent_id` 做行锁或条件抢占，表结构也没有“同一 Agent 只能有一个 `PUBLISHED` 版本”的约束。两个管理员并发发布不同草稿版本时，两个事务都可能各自归档 0 行，并分别把自己的目标版本改成 `PUBLISHED`；最终 `agt_agent.published_version_id` 只指向最后一次更新的版本，但 `agt_agent_version` 里可能同时残留多个 `PUBLISHED`。现有测试只覆盖串行发布，看不出这个问题。 | P1 | 发布前对 `agt_agent` 或该 Agent 的版本集合加锁，或通过条件更新保证只有一个事务能完成“发布抢占”；同时补并发发布红灯测试，避免多 `PUBLISHED` 版本静默共存。 |
| 2-2 | `AgentService.confirmCurrentEmployeeTask()`、`AgentRepository.listPendingToolCalls()` | 人工确认恢复执行时，只拉取“当前仍然 `ACTIVE` 的绑定和工具”对应的待确认日志。若一个任务原本有多个待确认工具，其中一部分在创建任务后被停用，查询结果会只返回仍然有效的那一部分。当前代码只校验“列表非空”就把任务改成 `RUNNING` 并在子集执行完后标记 `SUCCEEDED`，而被过滤掉的日志会继续停留在 `PENDING/WAITING_CONFIRM`。现有测试只覆盖“唯一一个绑定失效后返回 409”，没有覆盖“部分失效、部分仍有效”的混合场景。 | P1 | 恢复执行前先按 `task_id` 拉全量待确认日志，再校验“待确认日志数 == 可执行日志数”；只要有任何日志失去绑定或工具失效，就应整体返回冲突或显式收口为失败，不能让任务头状态和工具日志状态分叉。 |
| 2-3 | `AgentService.createCurrentEmployeeTask()`、`AgentService.confirmCurrentEmployeeTask()`、`AgentToolExecutionService.execute()` | 任务执行路径只捕获 `BizException`。如果 SPI 实现、未来 CONNECTOR 客户端、JSON 处理或其他库抛出的是普通 `RuntimeException`，异常会直接冲出 `@Transactional` 方法，导致已写入的任务、审计和工具日志整体回滚。这样一来，本轮新增的任务详情、失败聚合、运行指标和审计接口都看不到这类真实失败；后续一旦接入真实外部副作用，还会出现外部动作已发生但内部任务账本没有失败留痕的问题。当前测试只覆盖 `BizException` 失败路径。 | P1 | 把“任务头状态落账”和“工具执行”边界拆开：至少要对非业务异常也做失败收口，并保证失败任务和审计能落库；如果后续接真实写侧 CONNECTOR，建议用独立事务或 outbox/操作账本隔离外部副作用。 |
| 2-4 | `AgentTaskTimeoutJobService.createJobRunAndAcquireLock()`、`agt_task_timeout_job_run.lock_owner` | 超时扫描批次会在真正抢锁前先把 `lock_owner` 写成本次尝试生成的 `ownerId`。若实际因为别的实例已持锁而被标记为 `SKIPPED`，批次记录里的 `lock_owner` 仍然是“自己本次想持有的 owner”，不是数据库里真实的阻塞实例。`3748724` 又新增了后台批次查询接口，管理员据此排障时会被错误的持锁者信息误导。现有测试只断言 `SKIPPED` 状态和计数，没有校验 `lock_owner` 语义。 | P2 | 要么先抢锁成功再落批次记录，要么在跳过分支回写真实 `sys_job_lock.owner_id`；否则后台批次查询的核心排障字段不可信。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AgentService`、`AgentRepository` | 这串提交叠加后，`AgentService` 已经同时承担 Agent 定义治理、ACL、员工任务创建、人工确认恢复执行、后台任务查询参数归一化、指标/失败聚合响应映射等职责；`AgentRepository` 也同时承载定义、版本、ACL、任务、工具日志、审计、超时扫描锁和批次查询 SQL。后续如果继续往里接 workflow/IM 真实触发、重试、告警、回放或真实 CONNECTOR，这两个类会很快变成新的“补丁堆积点”。 | P2 | 在下一轮功能前优先拆边界：`AgentCatalogService`、`AgentTaskExecutionService`、`AgentTaskQueryService`、`AgentTaskTimeoutRepository/JobLockRepository` 至少要分开，避免治理查询和执行状态机继续混在一起。 |
| 3-2 | `agt_tool.risk_level`、`agt_tool.read_only_flag`、`agt_agent_tool.approval_mode`、`risk_control_json`、`AgentToolExecutionService` | 当前治理模型里已经有风险等级、只读标记、审批模式和风控 JSON，但运行时真正生效的只有 `approval_mode` 和 `toolType`。`risk_control_json` 在提交串中仍然只是存库字段，`risk_level/read_only_flag` 也没有和审批模式做服务端一致性约束。这会让后台治理数据模型和实际执行规则越来越分裂，后续一旦在现有逻辑上继续补条件分支，很容易形成“字段很多，但真正生效路径只有维护者自己记得”的状态。 | P2 | 在接真实 CONNECTOR 前，先明确哪些字段是展示、哪些字段是硬约束；如果 `HIGH`/非只读工具必须人工确认或走额外风控，就应在服务端统一校验，不要把规则散落在页面配置和开发者约定里。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态确认 | `git status --short -- docs/审查 server/src/main/java/com/workforcehub/agent employee-web` | 已执行 | 当前工作区存在用户未提交改动；本审没有基于工作区现状直接下结论，而是固定审查 `65c102d^..3748724` 这段已提交历史。 |
| 提交范围确认 | `git log --oneline --reverse 65c102d^..3748724` | 已执行 | 确认本审覆盖 7 个连续提交。 |
| 差异范围确认 | `git diff --name-status 65c102d^..3748724`、`git diff --stat 65c102d^..3748724` | 已执行 | 确认影响范围集中在 `module-agent` 控制器、服务、仓储、测试、`V12` 迁移和文档。 |
| 静态代码取证 | `git show 3748724:...`、`nl -ba`、`rg` | 已执行 | 已逐项核对发布、人工确认、超时扫描、批次查询、指标聚合、测试覆盖和表结构约束。 |
| 审查文档格式检查 | `git diff --check -- docs/审查/README.md docs/审查/第63审_module-agent后台治理提交串合并复核.md` | 待执行 | 本审完成后只检查新增文档和索引，不把既有历史文件空白问题混进本轮结论。 |

未验证项：

- 本轮是静态审查，没有重跑 `mvn` 编译或测试。
- 没有实际构造“并发发布两个版本”“一个任务多个待确认工具部分失效”“普通 `RuntimeException` 逃逸”“跳过批次真实持锁者回填”这几条路径的红绿测试；当前判断来自代码路径和现有测试覆盖缺口分析。

---

## 第六部分：处理优先级建议

| 优先级 | 建议 |
| --- | --- |
| P1 | 先修版本并发发布和人工确认部分失效子集执行这两项状态机问题；这两项会直接破坏后台治理口径和任务头/日志一致性。 |
| P1 | 在接真实 CONNECTOR、workflow 真实触发或更多异步执行前，先把“非 `BizException` 失败也能稳定留痕”的执行边界收口。 |
| P2 | 修正超时扫描批次 `lock_owner` 语义，否则批次查询虽已上线，但关键排障字段不可信。 |
| P2 | 继续扩展 `module-agent` 之前先拆 `AgentService` / `AgentRepository` 责任边界，再决定风险等级、审批模式和风控字段的统一生效规则。 |

---

## 第七部分：阶段性结论

`module-agent` 最近这串提交的方向没有问题，后台治理、员工动作、超时扫描和可观测入口已经形成了第一版可用闭环；单点提交里提出的接口缺口基本都被补齐了。

但按合并状态看，当前还不适合直接进入“多管理员并行治理”或“真实 CONNECTOR/外部副作用接入”节点。最需要优先收口的是三个 P1：版本并发发布唯一性、人工确认恢复执行时的子集成功问题，以及非业务异常失败留痕边界。在这些问题没处理前，任务中心和后台指标虽然看起来齐了，但底层状态机还没有稳到可以继续往真实写侧场景上叠。
