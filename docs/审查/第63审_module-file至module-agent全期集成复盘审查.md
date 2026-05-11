# 第 63 审 module-file 至 module-agent 全期集成复盘审查

- 审查日期：2026-05-11
- 审查提交：`864f44e..3748724a`（共 61 个提交，跨 module-file / module-workflow / module-attendance / module-leave / employee-web / module-im / module-agent 七条主线，约 315 个文件、46,000+ 新增行）
- 审查范围：`server/src/main/java/com/workforcehub/{file,workflow,attendance,leave,im,agent,spi}/`、`server/src/main/resources/db/migration/V2..V12`、`server/src/main/resources/processes/`、`server/src/test/java/com/workforcehub/{support,file,workflow,attendance,leave,im,agent}/`、`employee-web/src/`、`prototypes/employee-web/`、`docs/审查/README.md`
- 审查目标：本轮不是单 commit 增量审查，而是把 `864f44e` 起的整段开发节点放进模块演进路径里复盘，重点找单 commit 审查（第 18-62 审）可能漏掉的"跨模块 / 跨提交 / 集成层面"风险，并复核此前 P1 是否真闭环。

---

## 第一部分：提交内容概览

本轮 61 个提交按模块整理如下：

| 模块 | 关键能力 | 对应已有单点审查 |
| --- | --- | --- |
| module-file | 第一/二批后端骨架、上传策略配置化、上传确认闭环、生命周期治理、清理任务、补偿闭环、服务/仓储拆分 | 第 18-31 审 |
| module-workflow | Flowable 最小闭环、审批附件下载授权、模板治理、Agent 预审 SPI、第 37 审风险修复、Flowable Flyway 治理 | 第 32-37、51-52 审 |
| module-attendance | 第一版考勤闭环、排班覆盖与缺卡、排班变更重算、请假联动重算、打卡事实可信、请假分钟级覆盖、排班唯一模型、日结果原子 upsert、补卡审批 | 第 38-44、46-48 审 |
| module-leave | 假期账户与审批扣减、余额调整幂等指纹 | 第 41、45 审（间接） |
| employee-web | 前端工程化、API 基础层、IM 接口接入、消息已读、群成员治理、自定义群聊 | 第 49-50 审 |
| module-agent | 治理与工具闭环、人工确认动作、人工确认超时扫描、后台基础治理、后台手动超时扫描、任务/明细/审计/运行指标/失败聚合、超时扫描生产化、批次查询 | 第 53-62 审 |
| Flyway 迁移 | V2 文件中心增强、V3 上传票据、V4 文件中心幂等、V5-V7 上传策略、V8 清理任务、V9 审批附件、V10 Agent 预审任务 ID、V11 Flowable 表结构、V12 Agent 超时批次记录 | 第 18-31、52、61-62 审 |

本审不重复逐项复述以上单点审查，仅做"合并状态"层面的复盘。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 复核结果 | 严重程度 | 结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `FileLifecycleService.ensureUploaderOwnsAttachment`、`ModuleFileApiIntegrationTest.workflowAttachmentShouldNotBeManagedByPersonalFileLifecycleApi` | 第 37 审 P1：审批附件被个人文件生命周期接口误删。`cf085f4` 起员工生命周期入口必须 `owner_type=PERSONAL_FILE`，红灯/绿灯均落点。 | 已处理 | 跨模块附件归属边界已闭环。 |
| 1-2 | `WorkflowService.completeAgentPreReviewWithHumanApproval`、`WorkflowRepository.getRequestByIdForUpdateOrThrow`、`ModuleWorkflowAgentPreReviewIntegrationTest.concurrentAgentApprovedCallbacksShouldNotStartDuplicateFlowableInstances` | 第 37 审 P1：Agent 回调并发启动重复 Flowable 实例。当前 `wf_request` 在回调入口加 `FOR UPDATE`，并发用例验证只会留下一个 Flowable 实例。 | 已处理 | Agent 回调并发已收口。 |
| 1-3 | `WorkflowRepository.getPublishedTemplateVersion`、`WorkflowService.startHumanApproval` | 第 37 审 P1：模板版本快照失效。当前已使用 `flowable_definition_id` 启动，覆盖测试存在。 | 已处理 | 模板版本治理已收口。 |
| 1-4 | `WorkflowService.validateLeaveRequest`、`calculateLeaveDurationMinutes` | 第 37 审 P1：请假时长由客户端可控。当前服务端强制重算，午休扣除明确。 | 已处理 | 请假时长口径可信。 |
| 1-5 | `AttendanceService.punch`、`server/src/main/java/com/workforcehub/attendance/dto/CreatePunchRecordRequest.java` | 第 42 审 P1：员工伪造打卡时间与来源。`d96baca` 起以服务端时间为准、来源白名单化、补录走补卡审批。 | 已处理 | 打卡事实可信边界已收口（第 43 审）。 |
| 1-6 | `AttendanceService.recalculateDailyResult`、`LeaveQueryService.listApprovedLeavePeriodsOnDate`、`AttendanceService.calculateApprovedLeaveCoveredMinutes` | 第 42 审 P1：部分时段请假被整日豁免。当前 SPI 返回区间，迟到/早退/缺勤均按区间叠加计算。 | 已处理 | 请假分钟级覆盖已收口（第 44 审）。 |
| 1-7 | `LeaveService.adjustAccount`、`ensureWorkflowDeductIdempotencyFingerprint`、`lv_account_txn.uk_source_txn_key` | 第 42 审 P1：余额调整幂等键未绑定操作指纹。当前命中已有流水必须重新校验 `userId/leaveTypeId/changeMinutes`，否则抛冲突。 | 已处理 | 幂等指纹模型已收口（第 45 审）。 |
| 1-8 | `att_schedule` 唯一键、`AttendanceService.createSchedule` | 第 42 审 P1：排班停用后无法重建。`a89a73a` 已改为"停用必须改原行"，唯一键语义与服务层一致。 | 已处理 | 排班唯一模型已收口（第 46 审）。 |
| 1-9 | `AttendanceService.upsertDailyResult`、`att_daily_result.uk_user_date` | 第 42 审 P1：双端打卡撞日结果唯一键。当前使用数据库原子 upsert 替代先查后写。 | 已处理 | 日结果原子写入已收口（第 47 审）。 |
| 1-10 | `V11__init_flowable_process_schema.sql`、`application.yml`、`WorkflowFlowableIntegrationTestSupport` | 第 52 审：Flowable `ACT_*/FLW_*` 自动建表收口到 Flyway 治理，自动改库默认关闭，半成品库失败、版本不匹配库失败。 | 已处理 | Flowable 表结构治理已收口。 |
| 1-11 | `WorkflowService.downloadAttachment`、`workflowRepository.getWorkflowAttachmentDownloadScopeOrThrow` | 第 34、37、51 审：审批附件下载授权链路。当前票据签发前校验申请人/审批参与人，下载消费前再次校验票据归属与附件 scope，普通员工/无关用户均无法获取票据。 | 已处理 | 审批附件下载授权已经稳定。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AgentTaskTimeoutJobService.cancelExpiredWaitingTasksInTransaction` (Lines 146-171)、`WorkflowService.completeAgentPreReviewWithHumanApproval` | Agent 模块的"等待人工确认"超时只更新 `agt_task` 状态和写 `agt_audit_log`，没有任何回写或事件通知机制把跨模块的依赖方拉回正确状态。当前模块代码搜索 `wf_request|WorkflowService|workflowRepository` 在 `server/src/main/java/com/workforcehub/agent/` 下为空，说明 Agent 不知道哪些 `agt_task` 关联到 workflow 预审。结果：如果 workflow 预审通过 Agent 触发后 Agent 任务长时间不回调（例如外部模型挂掉、Agent 服务宕机、人工确认超时），`wf_request` 会永久卡在 `PENDING_REVIEW`，而 `agt_task` 已被静默标为 `CANCELLED`。Agent 模块的可观测在第 56-62 审已建得很完整，但跨模块这一段没有 SLA 兜底。 | P1 | 两种方向之一：(a) Agent 超时取消任务时按 `source_type=WF_REQUEST` 关联回 workflow，通过 SPI 把对应 `wf_request` 状态推到 `REJECTED` 或显式"Agent 无响应"中间态，并写审计；(b) workflow 侧增加 `PENDING_REVIEW` 自身的 SLA 扫描（独立于 Agent），超过 N 分钟后强制转人工或自动驳回。任一方向上线前都不应让 workflow 预审进入更大量的真实使用场景。 |
| 2-2 | `LeaveService.deductForWorkflowApproval` (Lines 128-173)、`LeaveService.ensureLeaveRequestBalance` (Lines 114-126)、`lv_account.frozen_minutes` 列 | `lv_account` 在 V1 阶段就为冻结预留了 `frozen_minutes` 列，`LeaveAccountResponse` 也已返回该字段（`LeaveService.java:184/223`），但当前提交申请只做 `available_minutes` 校验，审批通过仅直接扣 `available_minutes`，整个流程没有任何路径写入或释放 `frozen_minutes`。结果：(a) 第 42 审 3-6 P2 风险仍然存在——两个员工同时提交请假，第二个审批通过时才发现"假期余额不足"，流程进入半成品；(b) 前端如果按 `frozenMinutes` 计算可申请上限会得到错误结果，因为该字段永远为 0；(c) 数据模型与业务行为不一致，后续做退回/解冻/批量调整时容易把"冻结但未使用"误处理为"已扣减"。 | P2 | 把冻结接入提交-审批闭环：提交申请时调用 `LeaveService.freezeForWorkflowSubmission` 把 `available_minutes` 转入 `frozen_minutes`；审批通过时 `frozen_minutes - durationMinutes` 转流水（不再二次扣 `available_minutes`）；驳回/撤回/失败回弹时把 `frozen_minutes` 回写 `available_minutes`。冻结必须和审批扣减用同一个 `source_txn_key` 串起来，保证幂等。短期不接也要在 `LeaveAccountResponse` 上注明"该字段当前永远为 0"，避免前端误用。 |
| 2-3 | `LocalIntegrationTestSupport.registerProperties` (Line 70)、`WorkflowFlowableIntegrationTestSupport.registerProperties` (Line 67) | 两个测试支撑基类都把 `spring.session.redis.namespace` 硬编码为 `workforce:test:session`，与 `TEST_DB_NAME` 解耦。本审实测：把 4 个 mvn test 套件并行起在 4 个独立 `TEST_DB_NAME` 上，套件间会互相把对方的会话删除，造成 `IllegalState Session was invalidated` 和 `creationTime key must not be null` 错误，每个套件出现 5–17 个 Failure/Error；改为严格串行后全部 16/16、15/15、8/8 通过。本地开发或 CI 如果想用多库并行加速测试，会被这条隐性约束扎到。 | P2 | 把 Redis namespace 与 `TEST_DB_NAME` 拼起来：`spring.session.redis.namespace = "workforce:test:session:" + env(TEST_DB_NAME)`；同样把 `auth:login:fail:*` / `auth:email:*` 等业务前缀也按库做命名空间。短期至少在 `docs/开发日志` 或 README 上加一行"集成测试套件之间必须串行"。 |
| 2-4 | `WorkflowFlowableIntegrationTestSupport.resetBusinessTables` (Lines 95-149) | workflow 集成测试基类在 `@BeforeEach` 清理了所有 `wf_*` / `att_*` / `lv_*` / `sys_*` 业务表，但没有清理 Flowable 的 `ACT_RU_*` / `ACT_HI_*` / `ACT_GE_*` 运行时和历史表。当前测试基本按"申请编号 / 业务键"查询任务，因此暂时没暴露问题，但若新增"列出当前用户全部待办"、"按申请人查询所有历史流程"这类用例，会因为前一个测试遗留的 `ACT_RU_TASK` 行得出顺序依赖或假阳性。第 32 审、第 52 审已经把 Flowable 表治理收口，但测试隔离这条没跟上。 | P2 | 在 `resetBusinessTables` 末尾按已知 `ACT_RU_*` / `ACT_HI_*` 表列表 truncate（参考 Flowable 7.0.1 schema 中的 `ACT_RU_EXECUTION`、`ACT_RU_TASK`、`ACT_RU_VARIABLE`、`ACT_RU_IDENTITYLINK`、`ACT_HI_PROCINST`、`ACT_HI_TASKINST`、`ACT_HI_ACTINST`、`ACT_HI_VARINST`、`ACT_HI_IDENTITYLINK`、`ACT_HI_COMMENT`、`ACT_EVT_LOG`、`ACT_RU_EVENT_SUBSCR`、`ACT_RU_JOB`、`ACT_RU_TIMER_JOB`、`ACT_RU_SUSPENDED_JOB`、`ACT_RU_DEADLETTER_JOB`），并保留 `ACT_RE_*` 部署表不动。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AgentRepository` (1010 行)、`WorkflowRepository` (895 行)、`AttendanceRepository` (879 行)、`WorkflowService` (859 行)、`AttendanceService` (878 行)、`AgentService` (807 行)、`OrgManagementService` (978 行)、`FileCleanupJobService` (784 行) | 第 37 审、第 42 审、第 47 审、第 62 审都建议过拆分，但合并到 `3748724a` 时，workflow / attendance / agent 三个主类都比上一次复盘时更胖：`WorkflowService` 从 662→737→859，`AttendanceService` 从 728→878，`AgentRepository` 从首版→1010。`module-file` 已在第 28-31 审拆出 `FileLifecycleService` / `FileQuotaPolicyService` / `FileUploadTicketService` / `FileUploadConfirmService` / `FileDownloadService`，整批被拆成 5 个 300–500 行的边界；`workflow` / `attendance` / `agent` 走了相反方向，继续往大类里堆。工作区已经有 `AgentTaskTimeoutRepository.java` 准备拆 agent 仓储（未提交），但 workflow 和 attendance 没动。 | P2 | 进入下一阶段功能（catch-up 跨日请假、补卡批量、Agent 调度扩展、抄送催办委托）之前先做一次"拆服务/仓储"窗口：workflow 拆出 `WorkflowAttachmentService` / `WorkflowTaskService` / `WorkflowAgentPreReviewService` / `WorkflowRequestQueryService`，attendance 拆出 `AttendanceCalculationService` / `AttendanceScheduleService` / `AttendanceLeaveOverlayService`，agent 拆出 `AgentDefinitionRepository` / `AgentTaskRepository` / `AgentTaskTimeoutRepository`（工作区已起头）/ `AgentAuditRepository`。 |
| 3-2 | `WorkflowService` 对 `module-file` 实现类的直接依赖：`DownloadTicketExecutionService`、`StorageProviderRegistry`、`DownloadAttachmentRow` | 第 37 审 3-2 提示过这条；合并后 `WorkflowService.downloadAttachment` (Lines 476-508) 仍同时直接持有 file 模块的 `loadObject` / `StorageObjectResource` 和 workflow 自己的 scope/票据签发/审计；同一个方法跨了两个模块的内部模型。 | P2 | 抽 `WorkflowAttachmentDownloadGateway` SPI：workflow 仅声明"我要按 attachmentRefId + requestId 拿 `DownloadFileResult`"；file 模块实现具体的票据消费、storage 读取、内容类型解析。 |
| 3-3 | `docs/审查/README.md` (Lines 1-67) | 当前 README 的"当前清单"只列到第 50 审，第 51-62 审 12 份审查文件已存在但未补入索引；规范第九节明确"每新增一份审查文件，必须更新 README"，连续 12 轮违反。新人或负责人按 README 找审查清单会漏掉最近 2 周的所有审查记录，包括第 52 审 Flowable 治理和第 53-62 审整批 module-agent 审查。 | P3 | 本审同步补齐第 51-63 审入索引；以后单点审查交付时必须把"更新 README"和审查文件视为同一个原子改动。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区有未提交改动：agent 仓储拆分（`AgentTaskTimeoutRepository.java`+ 边界测试）、employee-web stores/views、prototypes 调整、`V13__add_im_message_client_idempotency.sql` 未跟踪。本审范围内不评估这些改动。 |
| 提交边界 | `git log --oneline --reverse 864f44e..3748724a` | 已执行 | 确认 61 个提交、最早 `fe665b4`、最新 `3748724`。 |
| 差异结构 | `git diff --name-status / --stat 864f44e..3748724a` | 已执行 | 315 文件、46,425 行新增、1,422 行删除；按模块归类于第一部分。 |
| Agent 模块测试（串行） | `env TEST_DB_NAME=workforce_hub_audit63_serial2 mvn -Dtest=ModuleAgentApiIntegrationTest test` | 通过 | 16 用例全部通过。 |
| Workflow Flowable 集成测试（串行） | `env TEST_DB_NAME=workforce_hub_audit63_wf_solo mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` | 通过 | 15 用例全部通过。 |
| Leave 跨 workflow 联动测试（串行） | `env TEST_DB_NAME=workforce_hub_audit63_lvwf_solo mvn -Dtest=ModuleLeaveWorkflowIntegrationTest test` | 通过 | 8 用例全部通过。 |
| 并发跑套件红灯 | 4 个 `mvn test` 套件以独立 `TEST_DB_NAME` 并行启动 | 按预期失败 | agent 16 pass/10 fail、workflow 12 pass/13 fail、attendance+leave 8 pass/17 fail、im+file 55 pass/9 fail；全部错误模式为 `Session was invalidated` / `creationTime key must not be null`，证据指向 3-3 的 Redis namespace 共享问题。 |
| V1 校验 | `git log --oneline 864f44e..3748724a -- server/src/main/resources/db/migration/V1__init_schema.sql` | 已执行 | 输出为空，V1 在本审范围内未被修改，无 Flyway checksum mismatch 风险。 |
| V4-V12 DDL 审视 | 静态读 V2..V12 | 已执行 | 全部为 `CREATE TABLE` 或对新增列的 `ADD COLUMN`/`ADD INDEX`/`MODIFY COMMENT`，没有破坏性 `DROP COLUMN` / 类型回退；V11 用存储过程做空库/完整库/半成品库三态判断，与第 52 审一致。 |
| 静态代码抽查 | `grep -n / wc -l / find` 服务类/仓储/测试基类 | 已执行 | 已核对服务行数、跨模块依赖、附件下载授权链路、leave 冻结字段使用情况、Agent 超时取消调用范围、employee-web stores 与原型隔离。 |
| 全仓差异空白检查 | `git diff --check` | 未执行 | 本审仅新增审查文件并更新 README，将在写入后单独检查（见第七部分备注）。 |

补充说明：本轮没有运行 module-file / module-attendance / module-im / module-org / module-auth 的全部测试套件。前述模块在第 18-50 审、第 56-62 审已有覆盖；本审重点是跨模块复盘，并以 workflow / leave-workflow / agent 三套关键集成测试作为代表样本。

---

## 第六部分：处理优先级建议

**第一优先级（P1，阻断下一阶段功能扩展前必须先处理）：**

- **2-1 Agent 预审超时与 workflow 跨模块兜底**：在让 workflow 预审进入更大量真实业务（如外部 Agent 模型、长时间审批节点）之前，必须有跨模块超时回写或 workflow 侧独立 SLA 扫描，否则会留下"`wf_request` 永久卡 `PENDING_REVIEW`、`agt_task` 已 `CANCELLED`"的不可恢复状态。该问题不会立刻在测试里冒出，但在生产真实 Agent 不稳定时会成事故。

**第二优先级（P2，下一开发节点之前应处理）：**

- **2-2 leave 余额冻结模型半成品**：要么真正接入提交-审批闭环，要么在响应里下沉/隐藏 `frozenMinutes` 字段。当前是"数据库字段存在、响应字段返回、业务路径不更新"的不一致状态。
- **2-3 测试 Redis namespace 共享**：在不修复前应在文档明确"集成测试必须串行"，避免 CI 或本地多库并行误用。修复成本极低（一行 namespace 拼接）。
- **2-4 Flowable runtime 表测试不清理**：在新增"按用户列待办全集"或"按申请人列流程历史"类测试用例之前修，否则后写测试会有顺序依赖。
- **3-1 大类继续膨胀**：在补卡批量、月报、抄送催办、Agent 调度扩展上线前，应先按 module-file 的拆分窗口同样拆 workflow / attendance / agent。
- **3-2 workflow 对 file 实现类直依赖**：抽 SPI，避免后续 file 改 storage 适配层时联动改动 workflow。

**第三优先级（P3，可纳入近期治理）：**

- **3-3 README 索引漏 12 份审查**：本审同步补齐第 51-63 审入索引。

---

## 第七部分：阶段性结论

`864f44e..3748724a` 这 61 个提交完成了项目的核心后端骨架：文件中心、审批闭环（含 Flowable 引擎和 Agent 预审）、考勤+请假联动、员工端基础接口、IM 接入、Agent 任务/超时/审计后台治理。第 18-62 审已逐点压住"单 commit 维度可见"的风险，第 37 审 P1 全数收口（cf085f4），第 42 审 P1 全数收口（43-48 审），第 52 审 Flowable 表治理已闭环。这一阶段已经为后续上线和真实联调建立了相对稳定的底座。

但本审从集成视角找到 4 条尚未被任何历史审查覆盖的累积风险：(1) Agent 超时与 workflow 预审之间没有跨模块状态回写，是 P1；(2) leave 余额冻结模型只建了字段没接入业务路径，是 P2；(3) 测试基线两个 Redis namespace 硬编码，会在多库并行测试时静默失效；(4) Flowable runtime 表没纳入测试清理。同时第 37 / 42 / 47 / 62 审重复建议过的"workflow / attendance / agent 大服务拆分"在合并状态下反而继续恶化，单从行数看 workflow / attendance / agent 三主服务都已经比上次复盘时更胖。

结论：当前节点不存在 P0 阻断问题，已合并代码可继续作为开发底座；但**在让 Agent 预审进入更真实的业务量、或继续往 workflow / attendance / agent 主类堆功能之前，必须先处理 2-1 这一 P1，并尽快治理 2-2、2-3、2-4、3-1、3-2 五项 P2**。规范第九节要求的"每轮审查同步更新 README"也连续 12 轮被忽略，本轮一并补齐。

本审是静态审查 + 三套关键集成测试的串行回归，未修改任何业务代码。`git diff --check` 由本审写完审查文件后单独检查；如发现历史空白警告与本审无关，应在结果里区分。
