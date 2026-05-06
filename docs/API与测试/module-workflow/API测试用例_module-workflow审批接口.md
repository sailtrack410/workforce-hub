# API 测试用例 module-workflow 审批接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-workflow` 审批接口测试用例 |
| 文档版本 | `v0.1` |
| 适用范围 | Flowable 请假直属上级审批闭环、后台模板治理和 Agent 预审入口 |
| 发布日期 | `2026-05-06` |
| 当前状态 | Flowable 请假直属上级审批闭环、我的申请列表、审批附件业务引用、审批附件下载授权、后台流程模板治理和 Agent 预审入口已落地；假期余额和完整 Agent 平台待后续补充 |

---

## 二、测试目标

本轮测试目标：

- 验证 workflow 测试基线能真实启动 Flowable Process Engine。
- 验证员工提交请假申请后生成业务申请头与请假明细。
- 验证业务申请头 `biz_id` 正确指向请假明细。
- 验证请假申请会启动 Flowable 流程实例。
- 验证直属上级能收到 Flowable 人工审批任务。
- 验证统一待办 `sys_todo_item` 写入。
- 验证直属上级审批通过后 Flowable 流程结束。
- 验证审批动作写入 `wf_task_action`。
- 验证业务申请状态更新为 `APPROVED`。
- 验证待办被关闭。
- 验证直属上级可以驳回审批任务。
- 验证申请人可以撤回审批中的申请。
- 验证申请详情与审批轨迹可查询。
- 验证申请人只能查询自己发起的审批申请列表。
- 验证请假申请可绑定当前申请人拥有的有效个人附件，并克隆为 `WF_REQUEST` 业务附件引用。
- 验证请假申请不能绑定他人附件或已删除附件。
- 验证审批参与人可以为 `WF_REQUEST` 附件创建下载票据。
- 验证审批参与人可以用本人下载票据下载原文件。
- 验证非参与人不能创建审批附件下载票据。
- 验证非本申请附件不能创建审批附件下载票据。
- 验证下载票据使用后状态变为 `USED`，并写入下载审计。
- 验证审批附件创建下载票据后，如果文件最新扫描状态变为 `FAILED`，下载执行会拒绝且不消费票据。
- 验证管理员可以创建流程类型、流程模板和模板版本草稿。
- 验证模板版本发布时必须绑定已存在的 Flowable 定义 Key。
- 验证同一模板发布新版本后旧版本自动归档，避免多个当前有效版本。
- 验证模板列表能返回当前有效发布版本，版本列表能返回规则快照。
- 验证存在 Agent 预审任务时，提交后先进入 `PENDING_REVIEW`，不启动 Flowable，不生成直属上级待办。
- 验证 Agent 预审通过后启动 Flowable 并生成直属上级待办。
- 验证 Agent 预审驳回后直接终止申请，不启动 Flowable。
- 验证 Agent 回调任务 ID 必须匹配申请上的 `agent_pre_review_task_id`。
- 验证没有 Agent 任务时保持原直属上级审批流程。

当前已落地自动化测试类：

- `ModuleWorkflowFlowableIntegrationTest`
- `ModuleWorkflowAdminApiIntegrationTest`
- `ModuleWorkflowAgentPreReviewIntegrationTest`

当前已自动化覆盖：

- 员工登录。
- 直属上级登录。
- 流程类型、模板、模板版本和假种测试数据准备。
- 员工提交请假申请。
- `wf_request` 与 `wf_req_leave` 数据关系校验。
- Flowable 流程实例校验。
- Flowable 直属上级任务校验。
- 上级待办查询。
- 非审批人审批被拒绝。
- 上级审批通过。
- 上级审批驳回。
- 申请人撤回。
- 我的申请列表查询。
- 审批附件业务引用克隆。
- 他人附件、已删除附件拒绝。
- 审批附件下载票据创建。
- 审批附件下载执行。
- 非参与人和非本申请附件下载授权拒绝。
- 下载执行阶段最新扫描状态失败拦截。
- 下载票据状态和审计写入。
- 申请详情查询。
- 审批轨迹查询。
- Flowable 流程结束校验。
- `wf_task_action` 写入校验。
- `sys_todo_item` 待办关闭校验。
- 管理员登录。
- 流程类型创建。
- 流程模板创建。
- 模板版本草稿创建。
- 模板版本发布。
- 发布不存在 Flowable 定义时保持草稿状态。
- 发布新版本后旧版本归档。
- 后台模板列表和版本列表查询。
- Agent 预审触发后申请停留在 `PENDING_REVIEW`。
- Agent 预审通过回调后启动直属上级审批。
- Agent 预审驳回回调后直接置为 `REJECTED`。
- Agent 预审回调任务 ID 不匹配时拒绝回写。
- Agent 触发器返回 `null` 时保持原人工审批链路。

当前尚未自动化覆盖：

- 假期余额扣减。
- 完整 `module-agent` 后台创建、任务调度、工具调用和运行监控。

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080` |
| JDK | `17` |
| MySQL | `workforce_hub_test` |
| Redis | 已启动，用于会话 |
| Flowable | 真实 Process Engine，测试环境关闭 IDM/EventRegistry |
| 会话模式 | Spring Session + Redis |

说明：

- workflow 测试不继承普通 `LocalIntegrationTestSupport`，因为普通集成测试默认排除 Flowable。
- workflow 测试基线需要把 Flowable catalog 固定到测试库，避免本机其他库已有 `ACT_*` 表时污染表存在性探测。

---

## 四、测试数据

| 类型 | 登录名 | 密码 | 用途 |
| --- | --- | --- | --- |
| 员工账号 | `zhangsan.employee` | `Employee@123456` | 申请人 |
| 直属上级 | `lisi.manager` | `Employee@123456` | 审批人 |
| 管理员 | `admin.demo` | `Admin@123456` | 后台流程模板治理 |

组织关系：

- 张三 `userId=2003`。
- 李四 `userId=2002`。
- 张三的 `managerUserId=2002`。

测试内置业务数据：

- `wf_process_type.process_code=LEAVE`
- `wf_template.template_code=LEAVE_MANAGER_APPROVAL`
- `wf_template_version.flowable_definition_key=leave_manager_approval`
- `lv_type.leave_code=ANNUAL_LEAVE`
- Flowable 自动部署流程定义 `leave_manager_approval`

---

## 五、测试用例明细

## 5.1 提交与审批通过

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `WF-LEAVE-001` | P0 | `POST /api/employee/workflow/leave-requests` | 员工提交请假申请 | 张三已登录；已存在启用假种和已发布流程模板 | 请假 1 天 | HTTP `200`；`code=0`；返回 `requestId`、`requestNo`、`IN_APPROVAL`、`flowableProcessInstanceId` |
| `WF-LEAVE-002` | P0 | 数据库断言 | 申请头与请假明细关系正确 | `WF-LEAVE-001` 成功 | 查询 `wf_request` 与 `wf_req_leave` | `wf_request.biz_id = wf_req_leave.id`；`wf_req_leave.request_id = wf_request.id` |
| `WF-LEAVE-003` | P0 | Flowable 断言 | 提交申请后启动 Flowable 实例 | `WF-LEAVE-001` 成功 | 查询 RuntimeService | 存在运行中流程实例 |
| `WF-LEAVE-004` | P0 | Flowable 断言 | 直属上级收到审批任务 | `WF-LEAVE-001` 成功 | 查询 TaskService | 存在 assignee 为 `2002` 的 `manager_approval` 任务 |
| `WF-LEAVE-005` | P0 | `GET /api/employee/workflow/todos` | 直属上级查询待办 | 李四已登录；存在审批任务 | 无 | HTTP `200`；返回 `requestId`、`taskId`、`title=张三的请假申请`、`status=PENDING` |
| `WF-LEAVE-006` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` | 直属上级审批通过 | 李四已登录；存在待审批任务 | 审批意见 | HTTP `200`；返回 `requestId`、`status=APPROVED` |
| `WF-LEAVE-007` | P0 | Flowable 断言 | 审批通过后流程结束 | `WF-LEAVE-006` 成功 | 查询 RuntimeService | 运行中流程实例不存在 |
| `WF-LEAVE-008` | P0 | 数据库断言 | 审批通过后业务状态与动作流水正确 | `WF-LEAVE-006` 成功 | 查询业务表 | `wf_request.status=APPROVED`；`wf_task_action.action_type=APPROVE` |
| `WF-LEAVE-009` | P0 | 数据库断言 | 审批通过后待办关闭 | `WF-LEAVE-006` 成功 | 查询 `sys_todo_item` | 对应申请和审批人无 `PENDING` 待办 |
| `WF-SEC-001` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` | 非审批人不能审批 | 张三已提交请假；李四是任务 assignee；张三已登录 | 张三使用李四的 `taskId` 调用审批通过 | HTTP `200`；`code=403`；提示 `当前用户不是该审批任务处理人`；Flowable 任务仍保留；申请状态仍为 `IN_APPROVAL`；李四待办仍为 `PENDING` |
| `WF-REJ-001` | P0 | `POST /api/employee/workflow/tasks/{taskId}/reject` | 直属上级审批驳回 | 李四已登录；存在待审批任务 | 驳回原因 | HTTP `200`；返回 `requestId`、`status=REJECTED` |
| `WF-REJ-002` | P0 | 数据库断言 | 审批驳回后业务状态与动作流水正确 | `WF-REJ-001` 成功 | 查询业务表 | `wf_request.status=REJECTED`；`wf_task_action.action_type=REJECT`；对应待办关闭 |
| `WF-WDR-001` | P0 | `POST /api/employee/workflow/requests/{requestId}/withdraw` | 申请人撤回申请 | 张三已登录；申请处于 `IN_APPROVAL` | 撤回原因 | HTTP `200`；返回 `requestId`、`status=WITHDRAWN` |
| `WF-WDR-002` | P0 | 数据库断言 | 撤回后流程和待办收口 | `WF-WDR-001` 成功 | 查询 Flowable 与业务表 | Flowable 运行实例不存在；`wf_request.status=WITHDRAWN`；`wf_task_action.action_type=WITHDRAW`；李四无 `PENDING` 待办 |
| `WF-DETAIL-001` | P1 | `GET /api/employee/workflow/requests/{requestId}` | 参与人查询请假申请详情 | 张三已提交请假；李四是待办处理人 | 无 | HTTP `200`；返回申请状态、当前节点、请假理由、请假时长 |
| `WF-ACTION-001` | P1 | `GET /api/employee/workflow/requests/{requestId}/actions` | 查询审批轨迹 | 审批通过后 | 无 | HTTP `200`；返回 `APPROVE` 动作、处理人、处理意见、前后状态 |
| `WF-LIST-001` | P1 | `GET /api/employee/workflow/requests` | 查询我的申请列表 | 张三已提交请假申请 | `pageNo=1&pageSize=20` | HTTP `200`；`total=1`；列表返回张三自己的申请；李四登录查询时 `total=0` |
| `WF-FILE-001` | P1 | `POST /api/employee/workflow/leave-requests` | 提交请假时绑定本人附件 | 张三已有有效 `PERSONAL_FILE` 附件引用 | 请求体传 `attachmentRefIds` | HTTP `200`；创建 `WF_REQUEST` 业务附件引用；`wf_request_attachment` 指向业务附件引用；详情返回业务附件 |
| `WF-FILE-002` | P1 | `POST /api/employee/workflow/leave-requests` | 不能绑定他人附件 | 张三登录；传入李四上传的附件引用 | 请求体传他人 `attachmentRefIds` | HTTP `200`；`code=403`；提示 `无权使用该附件引用` |
| `WF-FILE-003` | P1 | `POST /api/employee/workflow/leave-requests` | 不能绑定已删除附件 | 张三登录；传入已删除附件引用 | 请求体传已删除 `attachmentRefIds` | HTTP `200`；`code=404`；提示 `附件引用不存在` |
| `WF-FILE-DL-001` | P0 | `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets` | 审批参与人创建审批附件下载票据 | 张三提交带附件请假；李四是直属上级审批人 | `downloadType=SINGLE` | HTTP `200`；`code=0`；返回 `ticketNo`、`downloadUrl`、`fileName`、`fileSize` |
| `WF-FILE-DL-002` | P0 | `GET /api/employee/workflow/attachments/download/{ticketNo}` | 审批参与人使用下载票据下载原文件 | `WF-FILE-DL-001` 成功；李四登录 | 下载票据编号 | HTTP `200`；返回原文件字节；`sys_download_ticket.status=USED` |
| `WF-FILE-DL-003` | P1 | 数据库断言 | 审批附件下载审计写入 | `WF-FILE-DL-002` 成功 | 查询 `sys_audit_log` | 存在 `WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED` 和 `WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED` |
| `WF-FILE-DL-004` | P0 | `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets` | 非参与人不能创建审批附件下载票据 | 王五不是申请人也不是审批参与人 | 申请下载 | HTTP `200`；`code=403`；提示 `无权查看该审批申请` |
| `WF-FILE-DL-005` | P0 | `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets` | 非本申请附件不能创建下载票据 | 张三登录；传入未绑定到该申请的附件引用 | 申请下载 | HTTP `200`；`code=404`；提示 `审批附件不存在` |
| `WF-FILE-DL-006` | P1 | `GET /api/employee/workflow/attachments/download/{ticketNo}` | 票据创建后扫描失败不能下载 | 李四已创建审批附件下载票据；文件对象最新 `scan_status=FAILED` | 下载票据编号 | HTTP `200`；`code=409`；提示 `附件安全扫描未通过`；票据仍为 `ACTIVE` |

## 5.2 后台流程模板治理

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `WF-ADMIN-001` | P0 | `POST /api/admin/workflow/process-types` | 管理员创建流程类型 | 管理员已登录 | `processCode=LEAVE` | HTTP `200`；`code=0`；返回 `processTypeId`、`processCode=LEAVE` |
| `WF-ADMIN-002` | P0 | `POST /api/admin/workflow/templates` | 管理员创建流程模板 | `WF-ADMIN-001` 成功 | `templateCode=LEAVE_MANAGER_APPROVAL` | HTTP `200`；`code=0`；返回 `templateId`、流程类型和模板基础信息 |
| `WF-ADMIN-003` | P0 | `POST /api/admin/workflow/templates/{templateId}/versions` | 管理员创建模板版本草稿 | `WF-ADMIN-002` 成功 | `versionNo=1`，传入 `formSchemaJson` 和 `ruleSnapshotJson` | HTTP `200`；`code=0`；返回 `status=DRAFT`；JSON 快照保存为合法 JSON |
| `WF-ADMIN-004` | P0 | `POST /api/admin/workflow/template-versions/{versionId}/publish` | 管理员发布模板版本 | `WF-ADMIN-003` 成功；Flowable 已部署 `leave_manager_approval` | `flowableDefinitionKey=leave_manager_approval` | HTTP `200`；`code=0`；返回 `status=PUBLISHED`、`flowableDefinitionId`、`publishedBy=8001`、`effectiveFrom` |
| `WF-ADMIN-005` | P1 | `GET /api/admin/workflow/templates` | 管理员查询模板列表 | 模板版本已发布 | `processCode=LEAVE` | HTTP `200`；返回模板基础信息和 `currentVersion.status=PUBLISHED` |
| `WF-ADMIN-006` | P1 | `GET /api/admin/workflow/templates/{templateId}/versions` | 管理员查询模板版本列表 | 模板版本已发布 | 无 | HTTP `200`；返回版本列表，包含 `ruleSnapshotJson` |
| `WF-ADMIN-007` | P0 | `POST /api/admin/workflow/template-versions/{versionId}/publish` | 发布不存在的 Flowable 定义应失败 | 存在草稿版本 | `flowableDefinitionKey=missing_definition_key` | HTTP `200`；`code=404`；提示 `Flowable流程定义不存在`；版本状态仍为 `DRAFT` |
| `WF-ADMIN-008` | P0 | 数据库断言 | 新版本发布后旧版本归档 | 同模板第 1 版已发布；第 2 版草稿存在 | 发布第 2 版 | 旧版本 `status=ARCHIVED` 且 `effective_to` 不为空；新版本 `status=PUBLISHED`；同模板当前有效发布版本只有一个 |

## 5.3 Agent 预审入口

| 用例 ID | 优先级 | 接口 | 用例名称 | 当前状态 |
| --- | --- | --- | --- | --- |
| `WF-AGT-001` | P0 | `POST /api/employee/workflow/leave-requests` | 触发 Agent 预审后申请先停留在待预审状态 | 已自动化 |
| `WF-AGT-002` | P0 | `WorkflowAgentResultCallback#onPreReviewApproved` | Agent 预审通过后进入直属上级审批 | 已自动化 |
| `WF-AGT-003` | P0 | `WorkflowAgentResultCallback#onPreReviewRejected` | Agent 预审驳回后直接终止申请 | 已自动化 |
| `WF-AGT-004` | P0 | `WorkflowAgentResultCallback#onPreReviewRejected` | Agent 回调任务 ID 必须匹配申请当前预审任务 | 已自动化 |
| `WF-AGT-005` | P0 | `WorkflowAgentResultCallback#onPreReviewRejected` | Agent 回调任务 ID 为空时按任务不匹配处理 | 已自动化 |
| `WF-AGT-006` | P0 | `POST /api/employee/workflow/leave-requests` | Agent 触发器返回空时保持原人工审批链路 | 已自动化 |

预期要点：

- `WF-AGT-001`：响应 `status=PENDING_REVIEW`，返回 `agentTaskId`；`wf_request.current_step_key=agent_pre_review`；`flowable_process_instance_id` 为空；直属上级无 `PENDING` 待办。
- `WF-AGT-002`：回调后写入 `AGENT_PRE_REVIEW` 动作，`after_status=IN_APPROVAL`；随后启动 Flowable，状态进入 `IN_APPROVAL`，直属上级收到待办；Agent 决策枚举保存在 `agent_decision_json.decision`。
- `WF-AGT-003`：回调后写入 `AGENT_PRE_REVIEW` 动作，`actor_type=AGENT`，`agent_decision_json` 返回结构化结论；申请直接 `REJECTED`，不启动 Flowable。
- `WF-AGT-004`：传入非当前申请记录的 `agentTaskId` 时抛出 `Agent预审任务不匹配`，申请仍保持 `PENDING_REVIEW`，不写动作流水。
- `WF-AGT-005`：传入空 `agentTaskId` 时抛出 `Agent预审任务不匹配`，申请仍保持 `PENDING_REVIEW`，不写动作流水。
- `WF-AGT-006`：触发器返回 `null` 时响应 `IN_APPROVAL`，生成 Flowable 实例和直属上级待办，不返回 `agentTaskId`。

---

## 六、验证命令

当前已通过命令：

```bash
cd server
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeLeaveRequestShouldBeApprovedByManagerWithFlowable test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#nonAssigneeShouldNotApproveWorkflowTask test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#managerShouldRejectWorkflowTask test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#applicantShouldWithdrawWorkflowRequest test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#requestDetailAndActionsShouldBeQueryableByParticipant test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeShouldListOwnWorkflowRequestsOnly test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldBindOwnedAttachmentRefs test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldRejectInvalidAttachmentRefs test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#participantShouldDownloadWorkflowAttachmentWithTicket test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#nonParticipantOrForeignAttachmentShouldNotDownloadWorkflowAttachment test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#workflowAttachmentDownloadShouldRecheckLatestScanStatus test
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test
mvn -Dtest=ModuleWorkflowAdminApiIntegrationTest test
mvn -Dtest=ModuleWorkflowAgentPreReviewIntegrationTest test
```

后续建议扩展为：

```bash
cd server
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest test
```

---

## 七、审查关注点

- `wf_request.status` 与 Flowable 运行态是否一致。
- `wf_request.biz_id` 是否始终指向业务明细表主键，而不是申请头 ID。
- `sys_todo_item.source_id` 当前使用 `wf_request.id`，查询 taskId 时需通过 Flowable 运行任务反查。
- 审批动作与业务状态更新必须在同一服务层事务中完成。
- 不允许前端或后台绕过 `module-workflow` 直接操作 Flowable 任务。
- 后台模板治理只绑定已部署 Flowable 定义，不在线生成或修改 BPMN。
- 同一模板任意时刻只能有一个当前有效 `PUBLISHED` 版本。
- `ruleSnapshotJson` 是后续 Agent 预审的规则入口，必须保持合法 JSON。
- workflow 只依赖 Agent SPI，不直接依赖 `module-agent` 具体实现。
- Agent 预审回调必须校验 `wf_request.agent_pre_review_task_id`，防止错误任务串写申请状态。
