# 开发日志 module-workflow

## 2026-05-06 Flowable 请假直属上级审批最小闭环

### 背景

`module-file` 第一版后端能力已经进入收口阶段，按照 `docs/开发规划/后续开发计划与测试节点.md` 的顺序，下一步进入 `module-workflow`。本轮原本可先做自研轻量审批路由，但已确认第一版直接接入 Flowable，避免后面再把自研状态迁移到真实流程引擎。

### 本次处理

- 新增 `ModuleWorkflowFlowableIntegrationTest`，用真实 Flowable Process Engine 覆盖员工请假申请到直属上级审批通过的闭环。
- 新增 `WorkflowFlowableIntegrationTestSupport`，和普通本地集成测试基线隔离；普通测试仍默认排除 Flowable，workflow 测试单独启用真实流程引擎。
- 新增 `WorkflowFlowableTestConfiguration`，在 workflow 测试环境关闭 Flowable IDM/EventRegistry 等非首批引擎配置器，并把 Flowable catalog 固定到 `workforce_hub_test`，避免本机正式库已有 `ACT_*` 表时污染测试库表探测。
- 新增 BPMN 定义 `leave_manager_approval`，流程结构为开始事件、直属上级用户任务、结束事件。
- 新增员工端 workflow 接口：
  - `POST /api/employee/workflow/leave-requests`
  - `GET /api/employee/workflow/todos`
  - `POST /api/employee/workflow/tasks/{taskId}/approve`
  - `POST /api/employee/workflow/tasks/{taskId}/reject`
  - `POST /api/employee/workflow/requests/{requestId}/withdraw`
  - `GET /api/employee/workflow/requests/{requestId}`
  - `GET /api/employee/workflow/requests/{requestId}/actions`
- 新增 `WorkflowService`，集中处理请假提交、Flowable 流程启动、直属上级待办写入、审批通过和业务状态回写。
- 新增 `WorkflowRepository`，集中维护 `wf_request`、`wf_req_leave`、`sys_todo_item`、`wf_task_action` 的 SQL。
- 明确 `wf_request.biz_id` 指向请假明细 `wf_req_leave.id`，`wf_req_leave.request_id` 回指申请头 `wf_request.id`，并用集成测试锁住这条数据模型约束。

### 红绿记录

- 红灯：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test`，失败于 Flowable EventRegistry 初始化访问不存在的 `ACT_GE_PROPERTY`。
- 修复：关闭 workflow 测试环境中的 Flowable IDM/EventRegistry 配置器。
- 红灯：同一命令继续失败于主 BPMN 引擎误判测试库已有 `ACT_RU_EXECUTION`，再访问测试库不存在的 `ACT_GE_PROPERTY`。
- 修复：将测试环境 Flowable `databaseCatalog` 固定为 `workforce_hub_test`，避免正式库 `workforce_hub` 已存在 Flowable 表时污染测试库表探测。
- 红灯：同一命令推进到 `/api/employee/workflow/leave-requests` 返回 `404`。
- 绿灯：新增 workflow controller/service/repository/BPMN 后，同一命令通过。
- 红灯：补充 `wf_request.biz_id = wf_req_leave.id` 断言后，同一命令失败，暴露初版实现把 `biz_id` 写成了申请头 ID。
- 绿灯：先分配申请头 ID 与明细 ID，再写入头表和明细表，同一命令通过。
- 红灯：复跑同一命令时再次出现 `404`，确认 `@ConditionalOnBean(RuntimeService.class)` 在 controller/service 扫描阶段会因为 Flowable RuntimeService 尚未注册而跳过业务接口。
- 修复：改为 `workforce.workflow.enabled` 模块开关；生产默认启用，普通集成测试显式关闭，workflow Flowable 集成测试显式启用，避免条件装配时序影响接口注册。
- 回归补充：新增 `nonAssigneeShouldNotApproveWorkflowTask`，锁定非审批人不能处理他人的 Flowable 审批任务；现有服务层 assignee 校验已满足该行为，测试通过。
- 红灯：新增驳回、撤回、详情与轨迹查询测试，均失败于接口 `404`。
- 绿灯：补齐 reject、withdraw、request detail、request actions 接口与服务/仓储实现后，三个新用例通过。

### 当前完成范围

- 员工张三可以提交请假申请。
- 提交后写入 `wf_request` 与 `wf_req_leave`。
- 提交后启动 Flowable 流程实例。
- 直属上级李四会收到 Flowable 人工任务。
- 直属上级待办写入 `sys_todo_item`。
- 非审批人不能处理直属上级的审批任务。
- 李四可以通过审批任务。
- 审批通过后 Flowable 流程结束。
- 审批通过后 `wf_request.status` 更新为 `APPROVED`。
- 李四可以驳回审批任务，状态更新为 `REJECTED`。
- 张三可以撤回审批中的申请，状态更新为 `WITHDRAWN`。
- 申请参与人可以查询申请详情和审批轨迹。
- 审批动作写入 `wf_task_action`。
- 待办状态从 `PENDING` 更新为 `DONE`。

### 暂未覆盖

- 请假申请列表。
- 流程模板和模板版本后台治理接口。
- 附件引用接入 `module-file`。
- 假期余额扣减与 `module-leave` 联动。
- Agent 预审、自动驳回和人工审批页展示 Agent 结论。
- Flowable 表结构纳入 Flyway 的生产治理终态。

### 意义

- `module-workflow` 已从文档设计进入真实 Flowable 可运行闭环。
- workflow 测试基线和普通集成测试基线隔离，避免后续组织、认证、文件测试被 Flowable 引擎反向耦合。
- 业务状态、Flowable 任务、统一待办、审批动作流水已经形成第一条端到端链路。
- 当前代码边界按 controller、service、repository、BPMN 拆分，后续补驳回、撤回、附件和 Agent 预审时不用继续往单个类里堆逻辑。

## 2026-05-06 我的申请列表与审批附件业务引用

### 背景

Flowable 请假直属上级审批闭环已经跑通，但 `module-workflow` 仍缺少两个直接影响员工端可用性的能力：申请人提交后查看自己的申请列表，以及请假申请携带证明附件。附件不能简单复用个人文件引用，否则申请人后续删除个人文件会影响审批归档；因此本轮采用“个人附件作为来源，审批申请克隆业务附件引用”的方式。

### 本次处理

- 新增 `GET /api/employee/workflow/requests`，支持员工端按页查询自己发起的审批申请。
- 新增 `WorkflowRequestListQuery`、`WorkflowRequestSummaryResponse`，列表返回申请编号、业务类型、业务明细、标题、状态、当前节点和提交/完成时间。
- `LeaveRequestCreateRequest` 新增 `attachmentRefIds`，最多支持 10 个附件引用。
- 新增 `wf_request_attachment` 表，保存审批申请到业务附件引用的关系。
- 请假提交时先校验来源附件必须是当前申请人上传的 `PERSONAL_FILE`，且附件引用、物理文件未删除，安全扫描未失败。
- 校验通过后创建新的 `sys_attachment_ref`，`owner_type=WF_REQUEST`、`owner_id=wf_request.id`，再写入 `wf_request_attachment`。
- 详情接口返回 `attachments`，返回的是审批申请自己的 `WF_REQUEST` 业务附件引用，而不是来源个人附件引用。
- workflow 专用测试基线补充文件中心表清理，避免附件测试数据污染后续 workflow 用例。
- API 文档和测试用例同步列表、附件、错误码和落库语义。

### 红绿记录

- 红灯：新增 `employeeShouldListOwnWorkflowRequestsOnly` 后，`GET /api/employee/workflow/requests` 返回 `404`。
- 绿灯：补齐列表 DTO、Controller、Service、Repository 分页查询后，该用例通过。
- 红灯：新增 `leaveRequestShouldBindOwnedAttachmentRefs`，初版只把来源个人附件引用直接写入申请附件关系，不满足审批归档独立引用要求。
- 绿灯：改为提交时创建 `WF_REQUEST` 业务附件引用，再把申请绑定到业务引用；用例通过。
- 红灯：新增 `leaveRequestShouldRejectInvalidAttachmentRefs`，覆盖他人附件和已删除附件拦截。
- 绿灯：补齐附件所有权、删除态和安全扫描状态校验后，用例通过。
- 回归：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` 通过，8 个 workflow 用例全部通过。

### 当前完成范围

- 员工可以查询自己发起的申请列表。
- 审批人不会在“我的申请列表”中看到别人发起的申请。
- 请假申请可以携带本人已有个人附件。
- 审批申请会生成独立 `WF_REQUEST` 业务附件引用。
- 审批附件与来源个人附件复用同一个 `sys_file_object`，不重复存储二进制。
- 他人附件、已删除附件和安全扫描失败附件不能用于提交审批。
- 申请详情可以返回审批附件列表。

### 暂未覆盖

- 审批附件专用下载票据授权入口。
- 审批附件归档保留期和清理策略。
- 流程模板和模板版本后台治理接口。
- 假期余额扣减与 `module-leave` 联动。
- Agent 预审、自动驳回和人工审批页展示 Agent 结论。
- Flowable 表结构纳入 Flyway 的生产治理终态。

### 意义

- 申请列表让员工端可以从“提交动作”进入“可追踪的个人流程中心”。
- 附件采用业务引用克隆后，个人文件生命周期和审批归档生命周期解耦，后续做文件回收站、审批归档、审计下载时不会互相破坏。
- workflow 对文件中心保持低耦合：只依赖 `sys_attachment_ref` 元数据和 `attachment_ref_id`，不处理二进制上传和下载执行。

## 2026-05-06 审批附件下载授权入口

### 背景

上一轮已经让请假申请可以克隆 `WF_REQUEST` 业务附件引用，但详情页只能看到附件元数据，不能按审批参与人下载原文件。不能直接放宽 `module-file` 员工下载规则，因为个人文件下载仍应保持“上传人拥有”的边界；审批附件属于 `module-workflow` 的业务归档，授权主体应是申请参与人，而不是原始上传人。

因此本轮采用“workflow 判定业务权限，file 复用下载底座”的方式：`module-workflow` 负责校验审批参与人与附件归属，下载票据、对象读取和一次性状态机复用文件中心已有能力。

### 本次处理

- 新增员工端审批附件下载票据接口：
  - `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets`
  - `GET /api/employee/workflow/attachments/download/{ticketNo}`
- `EmployeeWorkflowController` 注入 `FileDownloadResponseFactory`，下载执行返回真实文件流，不包裹统一 `R`。
- `WorkflowService` 新增 `createAttachmentDownloadTicket`，按当前用户是否为审批参与人、附件是否属于该申请、附件是否为 `WF_REQUEST` 业务引用做授权。
- `WorkflowService` 新增 `downloadAttachment`，校验票据申请人、票据状态、过期时间和参与人权限后，复用 `DownloadTicketExecutionService` 抢占 `ACTIVE` 票据为 `USED`。
- 下载执行阶段重新读取文件对象最新 `scan_status`，如果票据创建后安全扫描结果变为 `FAILED`，拒绝下载且不消费票据。
- `WorkflowRepository` 新增审批附件归属查询、审批下载票据查询、下载对象查询和下载审计写入 SQL。
- 下载审计新增：
  - `WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED`
  - `WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED`
- `ModuleWorkflowFlowableIntegrationTest` 新增审批附件下载正向用例，覆盖创建票据、下载原文件、票据变为 `USED`、审计写入。
- `ModuleWorkflowFlowableIntegrationTest` 新增普通个人文件下载入口隔离用例，覆盖 `WF_REQUEST` 附件不能绕过 workflow 授权入口开票。
- `ModuleWorkflowFlowableIntegrationTest` 新增授权负例，覆盖非参与人和非本申请附件不能创建下载票据。
- `FileDownloadService` 员工端普通下载入口补充 `owner_type=PERSONAL_FILE` 边界校验，避免申请人用个人文件下载接口下载审批归档附件。
- API 文档和测试用例同步补齐审批附件下载接口、错误码、票据状态和验证命令。

### 红绿记录

- 红灯：新增 `participantShouldDownloadWorkflowAttachmentWithTicket` 后，`POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets` 返回 `404`。
- 绿灯：补齐 workflow 下载票据创建接口、服务层授权、仓储 SQL 和下载执行接口后，该用例通过。
- 回归补充：新增 `nonParticipantOrForeignAttachmentShouldNotDownloadWorkflowAttachment`，覆盖王五作为非参与人返回 `code=403`，以及未绑定到该申请的附件返回 `code=404`；该用例通过。
- 红灯：新增 `workflowAttachmentDownloadShouldRecheckLatestScanStatus` 后，票据创建后把文件对象扫描状态改为 `FAILED`，下载仍返回文件流，暴露下载执行没有二次读取最新扫描状态。
- 绿灯：下载对象行补充 `scan_status`，`WorkflowService.downloadAttachment` 执行前再次校验扫描状态；该用例通过，且安全拦截时票据保持 `ACTIVE`。
- 红灯：新增 `workflowAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint` 后，申请人可用 `/api/employee/files/{attachmentRefId}/download-tickets` 为 `WF_REQUEST` 附件创建普通下载票据，暴露普通个人文件入口缺少 ownerType 边界。
- 绿灯：`FileDownloadService` 员工端创建票据和下载执行均校验 `owner_type=PERSONAL_FILE`，该用例通过。
- 回归：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` 通过，15 个 workflow 用例全部通过。

### 当前完成范围

- 审批申请参与人可以为该申请下的 `WF_REQUEST` 附件创建下载票据。
- 下载票据只允许申请票据的本人使用。
- 下载票据过期会标记为 `EXPIRED`。
- 下载票据使用前通过条件更新抢占为 `USED`，避免并发重复下载。
- 下载执行时再次校验用户仍是审批参与人。
- 下载执行时再次校验文件对象最新安全扫描状态，扫描失败不会返回原文件。
- 非参与人不能创建审批附件下载票据。
- 非本申请附件不能创建审批附件下载票据。
- 审批附件不能通过员工端普通个人文件下载入口绕过 workflow 授权开票。
- 下载票据创建和下载执行均写入审计。

### 暂未覆盖

- 流程模板和模板版本后台治理接口。
- 审批附件归档保留期和清理策略。
- 假期余额扣减与 `module-leave` 联动。
- Agent 预审、自动驳回和人工审批页展示 Agent 结论。
- Flowable 表结构纳入 Flyway 的生产治理终态。

### 意义

- 审批详情页已经具备“看得到附件、也能按业务权限下载附件”的完整后端闭环。
- 文件中心个人文件下载权限没有被审批场景污染，后续群文件、知识库文件也可以采用同样模式：业务模块先做场景授权，再复用下载票据和对象读取底座。
- 审批附件下载有独立审计动作，后续做合规追踪、Agent 预审证据引用和审批材料访问记录时有可查依据。

## 2026-05-06 后台流程模板与模板版本治理

### 背景

审批闭环此前依赖测试或种子数据准备 `wf_process_type`、`wf_template` 和 `wf_template_version`。这会导致两个问题：一是流程模板没有后台可维护入口，二是后续 Agent 预审规则只能硬编码，无法随着流程模板版本演进。因此本轮先补业务侧模板治理，不做在线 BPMN 编辑器，只绑定工程侧已经部署到 Flowable 的流程定义。

### 本次处理

- 新增后台接口：
  - `POST /api/admin/workflow/process-types`
  - `POST /api/admin/workflow/templates`
  - `GET /api/admin/workflow/templates`
  - `POST /api/admin/workflow/templates/{templateId}/versions`
  - `GET /api/admin/workflow/templates/{templateId}/versions`
  - `POST /api/admin/workflow/template-versions/{versionId}/publish`
- 新增 `WorkflowAdminService`，把后台治理和员工审批流转服务拆开，避免 `WorkflowService` 继续膨胀。
- 新增 `WorkflowAdminRepository` 和后台治理专用 Row，负责流程类型、模板、版本草稿、发布和查询 SQL。
- 新增流程类型、模板、模板版本请求与响应 DTO。
- 创建模板版本时保存 `formSchemaJson` 与 `ruleSnapshotJson`，并校验必须是合法 JSON。
- 发布模板版本时校验 Flowable 最新流程定义存在，写入 `flowable_definition_key` 和 `flowable_definition_id`。
- 同一模板发布新版本时，自动把旧的当前有效 `PUBLISHED` 版本改为 `ARCHIVED` 并写入 `effective_to`，保证员工端提交审批时只会命中一个当前有效版本。
- 查询模板列表时返回 `currentVersion`，只展示当前有效发布版本。
- 查询版本列表时返回全部版本，包含 `DRAFT`、`PUBLISHED`、`ARCHIVED`。
- API 文档和测试用例同步补齐后台模板治理接口、错误码、状态流转和验证命令。

### 红绿记录

- 红灯：新增 `ModuleWorkflowAdminApiIntegrationTest` 后，`POST /api/admin/workflow/process-types` 返回 `404`，确认后台流程模板治理接口不存在。
- 绿灯：补齐后台 Controller、Service、Repository 和 DTO 后，用例推进到版本列表断言。
- 红灯：版本列表返回的 MySQL JSON 字段带空格，和接口期望的紧凑 JSON 字符串不一致，暴露响应格式受数据库 JSON 格式影响。
- 绿灯：服务响应层统一把 `formSchemaJson`、`ruleSnapshotJson` 压成紧凑 JSON，避免前端和测试依赖数据库格式细节。
- 回归：`mvn -Dtest=ModuleWorkflowAdminApiIntegrationTest test` 通过，3 个后台模板治理用例全部通过。

### 当前完成范围

- 管理员可以创建流程类型。
- 管理员可以创建流程模板。
- 管理员可以创建模板版本草稿。
- 管理员可以发布模板版本并绑定已部署 Flowable 定义。
- 发布不存在的 Flowable 定义会返回业务错误，草稿状态不变。
- 同一模板发布新版本后旧版本归档，避免多个当前有效发布版本。
- 模板列表可以展示当前有效发布版本。
- 版本列表可以展示规则快照，为后续 Agent 预审提供规则入口。

### 暂未覆盖

- 在线 BPMN 可视化设计器。
- 模板状态编辑、删除和版本撤回。
- Agent 预审读取 `ruleSnapshotJson` 并自动驳回。
- Flowable `ACT_*` 表结构纳入 Flyway 的生产治理终态。

### 意义

- `module-workflow` 从“固定测试种子流程”推进到“后台可治理流程模板”。
- 版本发布语义已经和员工端 `getPublishedTemplateVersion` 查询保持一致，后续新增审批类型时不需要改员工端提交逻辑。
- `ruleSnapshotJson` 给 Agent 介入流程留出了稳定入口：Agent 后续可以按模板版本读取规则快照、执行工具校验，并把预审结果写回审批轨迹。

## 2026-05-07 Agent 预审入口与自动驳回闭环

### 背景

流程模板版本已经可以保存 `ruleSnapshotJson`，但请假提交仍会直接进入 Flowable 人工审批。这样 Agent 无法在流程前置阶段介入，也无法把明显不合规的申请直接驳回。本轮先做 workflow 侧最小闭环：workflow 只依赖 `WorkflowAgentPreReviewTrigger` 和 `WorkflowAgentResultCallback` 两个 SPI，不直接依赖 `module-agent` 的任务调度、工具调用或后台创建实现。

### 本次处理

- `WorkflowService` 实现 `WorkflowAgentResultCallback`，作为 `module-agent` 后续回写预审结果的 workflow 侧入口。
- 请假提交后先落 `wf_request`、`wf_req_leave` 和附件，再可选调用 `WorkflowAgentPreReviewTrigger`。
- Agent 触发器返回 `agentTaskId` 时，申请进入 `PENDING_REVIEW`，`current_step_key=agent_pre_review`，不启动 Flowable，不生成直属上级待办。
- Agent 触发器返回 `null` 时，保持旧行为，直接启动 Flowable 直属上级审批。
- 新增 `wf_request.agent_pre_review_task_id` 字段和 V10 Flyway 迁移，用于持久化当前申请的预审任务 ID。
- Agent 回调通过或转人工时，校验申请状态和 `agentTaskId` 匹配，写入 `AGENT_PRE_REVIEW` 动作，再启动 Flowable 直属上级审批。
- Agent 回调驳回时，校验申请状态和 `agentTaskId` 匹配，写入 `AGENT_PRE_REVIEW` 动作，申请直接进入 `REJECTED`，不启动 Flowable。
- 审批轨迹响应新增 `agentDecisionJson`，返回 Agent 结构化预审结论。
- Agent 决策 JSON 响应层做稳定紧凑输出，避免 MySQL JSON 字段键顺序影响接口测试。
- 新增 `ModuleWorkflowAgentPreReviewIntegrationTest`，覆盖预审等待、通过后转人工、驳回终止、任务 ID 不匹配拒绝回写、任务 ID 为空拒绝回写、触发器返回空时保持旧流程。

### 红绿记录

- 红灯：`mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest test` 失败于缺少 `WorkflowAgentResultCallback` Bean，确认 workflow 侧回调入口不存在。
- 绿灯：`WorkflowService` 实现回调接口，并拆出 `startHumanApproval` 复用人工审批启动逻辑后，测试推进到触发器状态污染问题。
- 修复：测试触发器新增 `reset`，每个用例前恢复默认开启，避免单例测试 Bean 被上一个用例关闭后影响后续用例。
- 红灯：Agent 驳回轨迹中的 `agentDecisionJson` 因 MySQL JSON 输出键顺序不稳定导致断言失败。
- 绿灯：响应层对 Agent 决策 JSON 做紧凑与稳定字段顺序输出。
- 红灯：新增 `agentCallbackShouldMatchPendingAgentTaskId` 后，错误 `agentTaskId` 回调被接受，暴露缺少当前预审任务 ID 持久化与匹配校验。
- 修复：新增 V10 迁移 `agent_pre_review_task_id`，提交时记录触发任务 ID，回调时强制匹配。
- 过程问题：曾尝试直接修改已执行的 `V1__init_schema.sql`，触发 Flyway checksum mismatch；已还原 V1，只保留 V10 增量迁移，符合迁移治理原则。
- 校准：Agent 通过或转人工动作流水的 `after_status` 统一记录申请状态 `IN_APPROVAL`，Agent 决策枚举保存在 `agent_decision_json.decision`，避免状态字段混入非状态值。
- 修复：`ensurePendingAgentPreReview` 增加空 `agentTaskId` 防御，避免后续内部调用或 HTTP 封装把参数问题变成空指针异常。
- 绿灯：`mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest test` 通过，6 个 Agent 预审用例全部通过。
- 回归：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test` 通过，20 个 workflow 用例全部通过。

### 当前完成范围

- workflow 侧已经具备 Agent 预审前置入口。
- 提交申请可以按 Agent 触发结果分流到 `PENDING_REVIEW` 或直接 `IN_APPROVAL`。
- Agent 预审通过后可以启动直属上级人工审批。
- Agent 预审驳回后可以直接终止申请。
- Agent 预审动作写入 `wf_task_action`，并在轨迹接口展示结构化结论。
- Agent 回调必须匹配当前申请记录的 `agent_pre_review_task_id`。
- workflow 与 agent 仍通过 SPI 解耦，没有引入 `module-agent` 具体依赖。

### 暂未覆盖

- `module-agent` 后台创建、版本发布、ACL、工具绑定和任务调度。
- Agent 实际读取 `ruleSnapshotJson`、调用工具、生成预审结论。
- 人工审批页对 Agent 结论的前端展示。
- 假期余额扣减与 `module-leave` 联动。
- Flowable `ACT_*` 表结构纳入 Flyway 的生产治理终态。

### 意义

- 审批流程现在具备“Agent 先看一眼”的稳定插槽，明显不合规申请可以在人工审批前被自动收口。
- 回调入口有任务 ID 匹配校验，后续多个 Agent、重试任务或异步执行时不容易串写申请状态。
- 当前只做 workflow 侧接入口，没有把 Agent 平台实现提前硬塞进 workflow，后续开发 `module-agent` 时可以独立完善任务、工具、版本和权限模型。

## 2026-05-07 第37审正确性风险修复

### 背景

第37审复核了第31审之后到 `b425dc2` 的 module-file 与 module-workflow 合并状态，指出四个会影响真实协作和后续联动的正确性风险：个人文件生命周期接口可误操作审批归档附件、Agent 预审回调并发重复启动 Flowable、模板版本保存了 Flowable 定义 ID 但运行时仍按 Key 跟随最新版本、请假时长未和起止时间做服务端一致性校验。

### 本次处理

- `FileLifecycleService` 的员工个人文件回收站、恢复、彻底删除入口增加 `owner_type=PERSONAL_FILE` 硬边界，`WF_REQUEST` 审批附件不能再走个人文件生命周期语义。
- `WorkflowRepository` 增加 `getRequestByIdForUpdateOrThrow`，Agent 预审通过、转人工、驳回回调先对 `wf_request` 加行锁，再校验 `PENDING_REVIEW` 和 `agent_pre_review_task_id`。
- `WorkflowTemplateVersionRow` 和员工端模板版本查询补齐 `flowable_definition_id`，`WorkflowService.startHumanApproval` 优先用 `startProcessInstanceById` 启动发布版本绑定的 Flowable 定义，缺失时才回退 Key。
- 请假提交增加服务端工作时长一致性校验：第一版按起止时间扣除每天 `12:00-13:00` 午休，要求结果等于 `durationMinutes`；节假日和排班日历留到 `module-leave` 深化。
- API 文档同步说明 `durationMinutes` 的工作分钟口径，避免前端误以为服务端只校验正数。

### 红绿记录

- 红灯：新增 `employeeLifecycleShouldRejectWorkflowAttachmentRef` 后，个人文件删除接口对 `WF_REQUEST` 审批附件返回 `code=0`，确认跨模块生命周期边界缺失。
- 绿灯：限制个人生命周期入口只接受 `PERSONAL_FILE` 后，该用例通过，审批归档附件仍保持未删除。
- 红灯：新增 `concurrentAgentApprovedCallbacksShouldNotStartDuplicateFlowableInstances` 后，两个并发 Agent 通过回调会创建两个 Flowable 实例。
- 绿灯：回调前对申请行加锁后，同一申请只启动一个 Flowable 实例，重复回调返回业务错误。
- 红灯：新增 `leaveRequestShouldStartPublishedFlowableDefinitionId` 后，同 Key 重新部署后员工申请会启动最新定义，而不是模板发布时绑定的定义 ID。
- 绿灯：运行时按 `flowable_definition_id` 启动后，流程实例定义 ID 与发布版本一致。
- 红灯：新增 `leaveRequestShouldRejectMismatchedDurationMinutes` 后，`09:00-18:00` 却填 `60` 分钟仍可提交。
- 校准：现有 API 示例和测试已把 `09:00-18:00` 视为 `480` 分钟，因此服务端口径应扣除 `12:00-13:00` 午休，而不是简单自然分钟。
- 绿灯：工作分钟校验落地后，正常 `480` 分钟申请通过，伪造 `60` 分钟申请返回 `请假时长与开始结束时间不一致`。

### 验证

- `mvn -Dtest=ModuleFileApiIntegrationTest#employeeLifecycleShouldRejectWorkflowAttachmentRef test`
- `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldRejectMismatchedDurationMinutes test`
- `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldStartPublishedFlowableDefinitionId test`
- `mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest#concurrentAgentApprovedCallbacksShouldNotStartDuplicateFlowableInstances test`
- `mvn -Dtest=ModuleFileApiIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test`

### 意义

- 审批附件归档边界不再被个人文件删除、恢复、彻底删除接口污染。
- Agent 预审回调具备基础并发幂等保护，后续接真实任务重试和消息重复投递时不会重复启动审批流。
- 流程模板版本治理的“发布快照”语义和运行时启动行为一致，历史发布版本不会被同 Key 新部署静默改写。
- 请假时长从客户端自报字段变成后端可信校验字段，为后续假期余额扣减和 Agent 规则判断打基础。

## 2026-05-08 补卡申请审批闭环

### 背景

考勤侧已经有缺卡异常和日结果重算能力，但 workflow 还只能提交请假申请。补卡不能直接复用请假流程语义：它不扣假期余额，也不写 `wf_req_leave`，审批通过后的副作用是写入考勤人工打卡事实并重算当天结果。因此本轮把 `CLOCK_CORRECTION` 作为独立业务类型接入 workflow。

### 本次处理

- 新增员工端接口 `POST /api/employee/workflow/clock-correction-requests`。
- 新增补卡 BPMN `clock_correction_manager_approval`，第一版仍采用直属上级单节点审批。
- 新增 `ClockCorrectionRequestCreateRequest`、`WorkflowClockCorrectionDetailResponse`、`WorkflowClockCorrectionDetailRow`。
- `WorkflowRepository.insertWorkflowRequest(...)` 从固定 `LEAVE` 改为传入 `bizType`，让申请头可以承载多业务类型。
- 补卡提交写入 `wf_request.biz_type=CLOCK_CORRECTION`，`wf_request.biz_id=wf_req_clock_correction.id`。
- 申请详情新增 `clockCorrection` 明细返回。
- 审批通过逻辑按 `wf_request.biz_type` 分支：请假走余额扣减和请假考勤重算，补卡走 `AttendanceClockCorrectionService`，避免补卡误触发请假扣减。
- 申请提交响应类从 `LeaveRequestCreateResponse` 收口为 `WorkflowRequestCreateResponse`，避免补卡接口返回请假命名的 DTO。
- `ModuleWorkflowFlowableIntegrationTest` 新增补卡审批闭环用例，验证提交、详情、审批通过、Flowable 结束、业务状态和考勤副作用。
- 同步更新 workflow API 文档、测试用例、开发日志和后续开发计划。

### 红绿记录

- 红灯：新增 `approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult` 后，补卡提交接口返回 `404`。
- 绿灯：补齐 Controller、Service、Repository、DTO、BPMN 和考勤 SPI 后，目标用例通过。
- 回归绿灯：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` 通过，确认补卡新增业务类型没有破坏原请假审批、撤回、附件和下载票据能力。

### 当前完成范围增量

- workflow 支持 `LEAVE` 与 `CLOCK_CORRECTION` 两类申请头。
- 补卡申请可以提交、查看详情、进入直属上级审批和审批通过。
- 审批通过后的业务副作用由 `bizType` 驱动，不再把所有申请默认当作请假。
- workflow 与 attendance 通过 SPI 连接，workflow 不直接写考勤表。

### 暂未覆盖

- 补卡 Agent 预审规则，例如超期补卡、频繁补卡、缺少附件自动驳回。
- 补卡次数限制、可申请窗口和后台规则配置。
- 多业务类型审批列表的前端展示细化。
- Flowable `ACT_*` 表结构纳入 Flyway 的生产治理终态。

### 意义

- workflow 从“请假专用审批”推进到“可承载多业务申请的审批底座”。
- `bizType + bizId` 的申请头模型开始真正发挥作用，后续加加班、外出、调休、HR 变更不需要重建审批主干。
- 补卡闭环把考勤异常、审批和日结果重算串起来，形成员工端高频场景的可演示链路。

## 2026-05-11 Flowable 引擎表纳入 Flyway 治理

### 背景

前面多轮审查都把 Flowable `ACT_*` 表治理列为生产化待办。之前开发环境依赖 `flowable.database-schema-update=true` 自动建表，虽然便于早期接入，但会绕过 Flyway，导致不同环境的表结构来源不一致，也不利于后续容器化交付和生产回滚。

### 本次处理

- 新增 `V11__init_flowable_process_schema.sql`，把 Flowable 7.0.1 Process Engine 需要的 `ACT_*` 与 `FLW_*` 初始化 SQL 纳入主业务库 Flyway 迁移。
- `application.yml` 将 `flowable.database-schema-update` 默认值改为 `false`，只允许临时诊断旧库时显式打开。
- workflow 专用集成测试基线同样关闭 Flowable 自动改库，用真实 Flyway 初始化后的表结构启动流程引擎。
- V11 对三类数据库状态做了明确治理：
  - 空库：创建完整 Flowable 引擎表。
  - 旧开发库：如果 32 张必需 Flowable 表齐全，且 `schema.version=7.0.1.1`，则只记录 V11 已执行，不重复建表。
  - 半成品库或版本不匹配库：迁移阶段直接失败，提示先人工修复数据库，避免应用启动后才暴露隐性结构缺口。
- V11 开头和结尾都使用 `DROP PROCEDURE IF EXISTS` 清理临时过程，避免失败重试时被残留过程卡住。

### 验证

- `env TEST_DB_NAME=workforce_hub_flowable_flyway_final mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test`
- `env TEST_DB_NAME=workforce_hub_flowable_partial mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test`
- `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test`

### 结果

- 全新库可以从 V1 到 V11 一次性执行，Flowable 自动改库关闭时仍能启动真实流程引擎并跑通请假审批闭环。
- 只预置部分 Flowable 表的半成品库会在 V11 迁移阶段失败，错误信息为 `Partial Flowable schema detected before Flyway V11 migration`。
- 重建默认测试库后，workflow 三类集成测试 25 个用例全部通过。
- 本轮把 `module-workflow` 的数据库治理边界从“阶段性自动建表”收口为“Flyway 统一管理”。

### 仍需关注

- 未提交迁移在本机反复打磨时，已执行过旧版迁移的测试库可能出现 Flyway checksum mismatch；本轮提交前已重建默认测试库并完成回归。
- 后续升级 Flowable 版本时，不能修改 V11，必须新增 V12+ 增量迁移。
- 完整 `module-agent` 的任务调度、工具绑定、后台治理和审批页展示仍是下一阶段重点。
