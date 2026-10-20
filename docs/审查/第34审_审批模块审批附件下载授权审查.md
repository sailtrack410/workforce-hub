# 第 34 审 module-workflow 审批附件下载授权审查

- 审查日期：2026-05-06
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/workflow/`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowFlowableIntegrationTest.java`、`docs/API与测试/module-workflow/`、`docs/开发日志/module-workflow.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `module-workflow` 在审批附件业务引用基础上新增下载授权入口后，业务权限、下载票据状态机、审计记录和测试覆盖是否自洽。

---

## 第一部分：本轮内容概览

本轮补齐第 33 审中明确的“审批附件下载授权”缺口：

- 新增 `POST /api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets`。
- 新增 `GET /api/employee/workflow/attachments/download/{ticketNo}`。
- 创建票据前由 `module-workflow` 校验审批参与人、申请附件关系和 `WF_REQUEST` 业务附件归属。
- 下载执行复用 `sys_download_ticket` 和 `DownloadTicketExecutionService`，保持一次性票据状态机。
- 下载前再次校验票据申请人和审批参与人身份。
- 下载执行前重新读取文件对象最新扫描状态，票据创建后如果扫描状态变为 `FAILED`，拒绝下载且不消费票据。
- 复用 `StorageProviderRegistry` 读取物理对象，下载响应复用 `FileDownloadResponseFactory`。
- 写入审批附件下载票据创建和下载执行审计。

本轮没有放宽 `module-file` 员工个人文件下载规则。个人文件仍按上传人拥有授权，审批附件由 `module-workflow` 按业务参与人授权。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 审批附件下载正向用例 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#participantShouldDownloadWorkflowAttachmentWithTicket test` | 通过 | 覆盖创建票据、下载原文件、票据变 `USED`、审计写入 |
| 审批附件下载授权负例 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#nonParticipantOrForeignAttachmentShouldNotDownloadWorkflowAttachment test` | 通过 | 覆盖非参与人返回 `403`，非本申请附件返回 `404` |
| 下载执行扫描状态复核 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#workflowAttachmentDownloadShouldRecheckLatestScanStatus test` | 通过 | 覆盖票据创建后扫描状态变 `FAILED` 时拒绝下载，票据保持 `ACTIVE` |
| workflow 全量小测试 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` | 通过 | 11 个 workflow 用例通过 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 下编译通过 |
| 已完成模块核心回归 | `mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest test` | 通过 | 69 个用例通过 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `EmployeeWorkflowController` | 初始新增下载正向用例时，审批附件下载票据接口不存在，返回 `404`。 | 阻断 | 已新增票据创建接口和下载执行接口。 |
| 1-2 | `module-file` 与 `module-workflow` 权限边界 | 如果直接复用员工个人文件下载接口，会把“上传人拥有”错误扩展到审批附件场景，审批人无法按业务权限下载，或者需要放宽个人文件规则。 | 重要 | 已改为 workflow 自己判定审批参与人与 `WF_REQUEST` 附件归属，下载底座只负责票据状态机和对象读取。 |
| 1-3 | 审批附件下载状态机 | 新增下载入口必须保持“一张票据只使用一次”，否则审批材料下载审计不可信。 | 重要 | 已复用 `DownloadTicketExecutionService.claimActiveTicket`，通过条件更新抢占 `ACTIVE` 票据。 |
| 1-4 | 下载执行最新扫描状态 | 票据创建时校验了附件扫描状态，但如果票据创建后文件对象扫描结果变为 `FAILED`，初版下载执行仍会返回原文件。 | 重要 | 已在下载对象查询中带出 `scan_status`，下载执行阶段二次校验；失败时返回 `附件安全扫描未通过` 且不消费票据。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 创建票据权限 | 创建前先校验当前用户是审批申请参与人。 |
| 附件归属 | 附件必须同时满足 `wf_request_attachment.request_id = requestId`、`owner_type=WF_REQUEST`、`owner_id=requestId`。 |
| 非参与人拦截 | 非申请人、非审批人、非已处理人不能为审批附件创建票据。 |
| 非本申请附件拦截 | 未绑定到该申请的附件返回 `审批附件不存在`。 |
| 票据申请人 | 下载执行要求当前用户等于 `sys_download_ticket.requester_id`。 |
| 一次性语义 | 下载前通过条件更新将 `ACTIVE` 改为 `USED`，并发重复请求会被拒绝。 |
| 扫描状态复核 | 下载执行会按文件对象最新 `scan_status` 拦截 `FAILED`，不会只依赖票据创建时的快照。 |
| 审计记录 | 创建票据和执行下载都写入 `sys_audit_log`，并记录申请 ID 和附件引用 ID。 |
| 文件中心边界 | 没有修改个人文件下载授权模型，审批附件授权留在 workflow 域。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `WorkflowService` | workflow 当前直接复用 file 模块的 DTO、下载响应工厂、存储注册表和票据执行服务。模块化单体内可接受，但后续如果要强化包边界，应抽出文件下载 SPI。 | 建议 | 后续新增 `module-spi` 文件下载能力接口，由 `module-file` 实现，workflow 只依赖接口。 |
| 2-2 | 审批附件保留与撤销 | 已支持下载票据使用和过期，但暂未有审批附件归档保留期、票据撤销策略和管理员审计视图。 | 建议 | 后续模板治理或后台治理阶段补审批材料访问日志查询、票据撤销和归档清理策略。 |
| 2-3 | Flowable 表治理 | Flowable `ACT_*` 表仍依赖开发环境自动建表。 | 重要 | 后续单独做 Flowable 初始化 SQL 纳入 Flyway，并在生产环境关闭自动改库。 |

---

## 第六部分：处理优先级建议

**第一优先级：流程模板和模板版本后台治理。**

- 当前审批流程依赖测试或种子数据，缺少后台可维护入口。
- 模板治理完成后，才能让审批类型从“固定请假直属上级审批”扩展到更多流程。

**第二优先级：Agent 预审入口。**

- 在人工审批前触发 Agent 规则校验。
- 不合规申请自动驳回并写入 `wf_task_action`。
- 人工审批详情页展示 Agent 结论、证据和工具调用结果。

**第三优先级：Flowable 生产治理。**

- 导出 Flowable MySQL 初始化 SQL。
- 纳入 Flyway。
- 生产环境设置 `FLOWABLE_DB_SCHEMA_UPDATE=false`。

---

## 第七部分：阶段性结论

本轮没有发现继续开发前必须阻断的问题。`module-workflow` 已具备“提交申请、审批流转、详情查看、附件归档、按参与人下载附件”的后端闭环。

下一步继续做 workflow 时，建议优先补流程模板和模板版本后台治理；如果先接 Agent，Agent 会依赖模板规则和审批节点配置，缺少治理入口会让预审规则只能硬编码在代码中。
