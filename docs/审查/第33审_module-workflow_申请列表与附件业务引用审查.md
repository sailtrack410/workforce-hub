# 第 33 审 module-workflow 申请列表与附件业务引用审查

- 审查日期：2026-05-06
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/main/resources/db/migration/V9__add_workflow_request_attachment.sql`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowFlowableIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/WorkflowFlowableIntegrationTestSupport.java`、`docs/API与测试/module-workflow/`、`docs/开发日志/module-workflow.md`
- 审查目标：复核 `module-workflow` 在 Flowable 最小闭环基础上新增“我的申请列表”和“审批附件业务引用”后，接口语义、数据模型、文件生命周期边界和测试覆盖是否自洽。

---

## 第一部分：本轮内容概览

本轮在第 32 审后的 Flowable 审批闭环上补齐两个员工端基础能力：

- 新增 `GET /api/employee/workflow/requests`，员工可分页查询自己发起的审批申请。
- 请假提交支持 `attachmentRefIds`。
- 新增 `wf_request_attachment`，保存审批申请与附件引用关系。
- 提交审批时不直接复用个人附件引用，而是创建新的 `sys_attachment_ref`：`owner_type=WF_REQUEST`、`owner_id=wf_request.id`。
- 申请详情返回审批申请自己的业务附件引用。
- workflow 测试基线补充文件中心表清理，避免附件测试数据污染。

本轮核心设计选择是“审批附件业务引用克隆”：个人附件只是来源，审批归档使用 `WF_REQUEST` 业务附件引用，物理文件对象仍通过 `sys_file_object` 去重复用。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 申请列表用例 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#employeeShouldListOwnWorkflowRequestsOnly test` | 通过 | 覆盖申请人可查自己申请、审批人不误查他人申请 |
| 附件绑定用例 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldBindOwnedAttachmentRefs test` | 通过 | 覆盖个人附件克隆为 `WF_REQUEST` 业务引用，并复用同一物理对象 |
| 附件非法引用用例 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#leaveRequestShouldRejectInvalidAttachmentRefs test` | 通过 | 覆盖他人附件和已删除附件拦截 |
| workflow 全量小测试 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` | 通过 | 8 个用例通过，审批通过、驳回、撤回、详情、轨迹未被破坏 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 下编译通过 |
| 已完成模块回归 | `mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test` | 通过 | 58 个用例通过，确认普通测试基线关闭 workflow 后未破坏认证、组织、文件中心 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `EmployeeWorkflowController` | 新增列表测试初始失败于 `GET /api/employee/workflow/requests` 返回 `404`，说明员工端申请列表接口缺失。 | 阻断 | 已新增列表接口、查询 DTO、响应 DTO、服务方法和分页 SQL。 |
| 1-2 | 附件绑定初版设计 | 如果直接把个人附件引用写入审批申请，申请人后续删除个人文件会影响审批归档，个人文件生命周期和审批归档生命周期耦合。 | 重要 | 已改为创建 `WF_REQUEST` 业务附件引用，再由 `wf_request_attachment` 绑定审批申请。 |
| 1-3 | `WorkflowFlowableIntegrationTestSupport` | workflow 附件测试会写入文件中心表，如果测试复位不清理 `sys_attachment_ref`、`sys_file_object` 等表，后续用例可能被污染。 | 重要 | workflow 专用测试基线已补文件中心相关表清理。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 申请列表权限 | 只按 `applicant_user_id = 当前用户` 查询，不混入审批待办或他人申请。 |
| 附件所有权 | 提交时要求来源附件为当前用户上传的 `PERSONAL_FILE`，他人附件返回 `403`。 |
| 附件删除态 | 来源附件引用、物理文件对象已删除时返回 `404`。 |
| 附件归档语义 | 审批申请绑定 `WF_REQUEST` 业务附件引用，和个人文件引用解耦。 |
| 物理去重 | 业务附件引用复用来源附件的 `sys_file_object`，不重复存储二进制。 |
| 既有审批动作 | 审批通过、驳回、撤回、详情、轨迹全量 workflow 测试仍通过。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `sys_storage_usage` | 创建 `WF_REQUEST` 业务附件引用时增加了 `sys_file_object.ref_count`，但暂未计入个人或全局空间用量。当前这是有意收口：审批附件属于业务归档，不应简单重复占个人空间，但企业归档空间是否计量还没落表。 | 建议 | 后续新增 `WORKFLOW_ATTACHMENT` 或 `WF_REQUEST` 归档配额策略，再把审批附件纳入企业归档空间统计。 |
| 2-2 | 附件下载 | 详情返回了 `WF_REQUEST` 业务附件引用，但文件下载服务当前员工端下载仍按上传人拥有校验，没有审批参与人下载授权。 | 重要 | 下一批补 workflow 附件下载票据入口，按申请参与人授权生成短期下载票据。 |
| 2-3 | 附件安全扫描 | 当前只拦截 `scan_status=FAILED`，允许 `PENDING`、`PASSED`、`SKIPPED`。如果后续接真实杀毒或内容风控，`PENDING` 是否允许提交需要重新确认。 | 建议 | 接入真实扫描服务后，为审批附件场景配置“待扫描可提交但审批页提示”或“待扫描阻断提交”的策略。 |
| 2-4 | Flowable 表治理 | 本轮新增 V9 业务迁移，但 Flowable `ACT_*` 表仍依赖开发环境自动建表。 | 重要 | 后续仍需做 Flowable 初始化 SQL 纳入 Flyway 的生产治理批次。 |

---

## 第六部分：处理优先级建议

**第一优先级：补 workflow 附件下载授权。**

- 申请参与人可为 `WF_REQUEST` 附件创建下载票据。
- 非参与人不能下载审批附件。
- 下载审计应记录审批申请 ID 和附件引用 ID。

**第二优先级：进入 Agent 预审设计。**

- 在人工审批前增加 Agent 预审结论。
- 不合规申请自动驳回，并写入 `wf_task_action`。
- 人工审批页展示 Agent 结论和证据引用。

**第三优先级：补 Flowable 生产治理。**

- 导出 Flowable MySQL 初始化 SQL。
- 纳入 Flyway。
- 生产环境禁用 `flowable.database-schema-update` 自动改库。

---

## 第七部分：阶段性结论

本轮没有发现继续开发前必须阻断的问题。`module-workflow` 已从“能提交和审批”推进到“员工可追踪申请、申请可携带归档附件”的更完整闭环。

需要明确的是，审批附件目前只完成“提交绑定与详情展示”，还没有完成“按审批参与人授权下载”。因此下一步如果继续做 workflow，建议优先补附件下载授权，再接 Agent 预审；否则 Agent 虽然能引用附件元数据，但人工审批页还缺少可控下载能力。
