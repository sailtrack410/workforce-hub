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
- `ModuleWorkflowFlowableIntegrationTest` 新增授权负例，覆盖非参与人和非本申请附件不能创建下载票据。
- API 文档和测试用例同步补齐审批附件下载接口、错误码、票据状态和验证命令。

### 红绿记录

- 红灯：新增 `participantShouldDownloadWorkflowAttachmentWithTicket` 后，`POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets` 返回 `404`。
- 绿灯：补齐 workflow 下载票据创建接口、服务层授权、仓储 SQL 和下载执行接口后，该用例通过。
- 回归补充：新增 `nonParticipantOrForeignAttachmentShouldNotDownloadWorkflowAttachment`，覆盖王五作为非参与人返回 `code=403`，以及未绑定到该申请的附件返回 `code=404`；该用例通过。
- 红灯：新增 `workflowAttachmentDownloadShouldRecheckLatestScanStatus` 后，票据创建后把文件对象扫描状态改为 `FAILED`，下载仍返回文件流，暴露下载执行没有二次读取最新扫描状态。
- 绿灯：下载对象行补充 `scan_status`，`WorkflowService.downloadAttachment` 执行前再次校验扫描状态；该用例通过，且安全拦截时票据保持 `ACTIVE`。
- 回归：`mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` 通过，11 个 workflow 用例全部通过。

### 当前完成范围

- 审批申请参与人可以为该申请下的 `WF_REQUEST` 附件创建下载票据。
- 下载票据只允许申请票据的本人使用。
- 下载票据过期会标记为 `EXPIRED`。
- 下载票据使用前通过条件更新抢占为 `USED`，避免并发重复下载。
- 下载执行时再次校验用户仍是审批参与人。
- 下载执行时再次校验文件对象最新安全扫描状态，扫描失败不会返回原文件。
- 非参与人不能创建审批附件下载票据。
- 非本申请附件不能创建审批附件下载票据。
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
