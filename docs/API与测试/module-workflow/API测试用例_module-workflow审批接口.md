# API 测试用例 module-workflow 审批接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-workflow` 审批接口测试用例 |
| 文档版本 | `v0.1` |
| 适用范围 | Flowable 请假直属上级审批最小闭环 |
| 发布日期 | `2026-05-06` |
| 当前状态 | Flowable 请假直属上级审批闭环、我的申请列表和审批附件业务引用已落地；假期余额和 Agent 预审待后续补充 |

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

当前已落地自动化测试类：

- `ModuleWorkflowFlowableIntegrationTest`

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
- 申请详情查询。
- 审批轨迹查询。
- Flowable 流程结束校验。
- `wf_task_action` 写入校验。
- `sys_todo_item` 待办关闭校验。

当前尚未自动化覆盖：

- 假期余额扣减。
- Agent 预审与自动驳回。

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

组织关系：

- 张三 `userId=2003`。
- 李四 `userId=2002`。
- 张三的 `managerUserId=2002`。

测试内置业务数据：

- `wf_process_type.process_code=LEAVE`
- `wf_template.template_code=LEAVE_MANAGER_APPROVAL`
- `wf_template_version.flowable_definition_key=leave_manager_approval`
- `lv_type.leave_code=ANNUAL_LEAVE`

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

## 5.2 待补测试

| 用例 ID | 优先级 | 接口 | 用例名称 | 当前状态 |
| --- | --- | --- | --- | --- |
| `WF-AGT-001` | P1 | 流程节点或事件 | Agent 预审通过后进入人工审批 | 待实现 |
| `WF-AGT-002` | P1 | 流程节点或事件 | Agent 判定不合规后自动驳回 | 待实现 |

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
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test
```

后续建议扩展为：

```bash
cd server
mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowApprovalIntegrationTest test
```

---

## 七、审查关注点

- `wf_request.status` 与 Flowable 运行态是否一致。
- `wf_request.biz_id` 是否始终指向业务明细表主键，而不是申请头 ID。
- `sys_todo_item.source_id` 当前使用 `wf_request.id`，查询 taskId 时需通过 Flowable 运行任务反查。
- 审批动作与业务状态更新必须在同一服务层事务中完成。
- 不允许前端或后台绕过 `module-workflow` 直接操作 Flowable 任务。
- 后续接 Agent 时，workflow 只依赖 SPI 或领域事件，不直接依赖 agent 具体实现。
