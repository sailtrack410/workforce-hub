# 第 51 审 module-workflow 审批附件普通下载入口隔离审查

- 审查日期：2026-05-10
- 审查提交：未提交工作区
- 审查范围：`server/src/main/java/com/workforcehub/file/service/FileDownloadService.java`、`server/src/test/java/com/workforcehub/workflow/ModuleWorkflowFlowableIntegrationTest.java`、`docs/API与测试/module-workflow/`、`docs/API与测试/module-file/`、`docs/开发日志/module-workflow.md`
- 审查目标：复核审批附件下载授权入口落地后，普通员工文件下载入口是否仍可能绕过 workflow 业务授权。

---

## 第一部分：本轮内容概览

第 34 审已补齐 workflow 专用审批附件下载票据入口，但后续复核发现一个边界缺口：审批申请提交时会克隆 `WF_REQUEST` 业务附件引用，且 `uploader_id` 仍是申请人。如果申请人拿到该业务附件引用 ID，仍可调用普通个人文件下载入口 `/api/employee/files/{attachmentRefId}/download-tickets` 创建下载票据。

本轮补齐该隔离边界：

- `FileDownloadService.createEmployeeDownloadTicket` 增加 `owner_type=PERSONAL_FILE` 校验。
- `FileDownloadService.downloadEmployeeFile` 下载执行阶段同样复核 `owner_type=PERSONAL_FILE`。
- 新增 `workflowAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint`，锁定 `WF_REQUEST` 附件不能走普通个人文件入口。
- workflow 与 file API 文档同步说明：审批附件必须走 workflow 授权入口，普通员工文件入口只服务个人文件。

---

## 第二部分：验证结果

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#workflowAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint test` | 失败 | 修复前普通文件接口对 `WF_REQUEST` 附件返回 `code=0` 并创建下载票据。 |
| 绿灯验证 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest#workflowAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint test` | 通过 | 修复后返回 `code=403`，提示 `员工端文件接口仅允许下载个人文件`。 |
| workflow 回归 | `mvn -Dtest=ModuleWorkflowFlowableIntegrationTest test` | 通过 | 15 个 Flowable 审批集成用例全部通过。 |

---

## 第三部分：本轮发现并已修复的问题

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结果 |
| --- | --- | --- | --- | --- |
| 1-1 | `FileDownloadService.createEmployeeDownloadTicket` | 员工普通文件下载票据创建只校验 `uploader_type=USER` 和 `uploader_id=当前用户`，没有校验 `owner_type`。审批附件克隆后 `uploader_id` 仍是申请人，因此申请人可以绕过 workflow 授权入口为 `WF_REQUEST` 附件开普通下载票据。 | 重要 | 已增加 `owner_type=PERSONAL_FILE` 校验，非个人文件返回 `403`。 |
| 1-2 | `FileDownloadService.downloadEmployeeFile` | 如果历史或并发路径中已经存在指向业务附件的普通下载票据，下载执行阶段仍可能只按上传人校验后返回文件。 | 重要 | 下载执行阶段同样增加 `owner_type=PERSONAL_FILE` 复核，避免只在创建票据阶段拦截。 |
| 1-3 | API 文档 | file 文档此前写“当前用户对该业务引用有下载权限”，容易误解为普通文件入口可以承载审批附件、群文件等业务授权。 | 建议 | 已改为“当前用户必须是个人文件上传人”，并明确业务附件走对应业务模块授权入口。 |

---

## 第四部分：当前没有发现的阻断问题

| 检查点 | 结论 |
| --- | --- |
| 普通员工文件入口 | 已收口为 `PERSONAL_FILE`，不再承载审批归档附件。 |
| workflow 审批附件入口 | 仍按审批参与人、申请附件关系和 `WF_REQUEST` 归属校验。 |
| 下载执行兜底 | 普通下载票据执行阶段也会复核 `PERSONAL_FILE`，历史异常票据不能继续下载业务附件。 |
| 审计语义 | 个人文件下载审计和审批附件下载审计继续分离，便于后续合规追踪。 |

---

## 第五部分：仍需关注的风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 业务附件通用下载模式 | 当前审批附件已走 workflow 专用入口。后续群文件、知识库文件、文档文件如果也复用 `sys_attachment_ref`，同样不能走普通个人文件入口。 | 重要 | 每新增一种业务附件 ownerType，都必须由对应业务模块提供授权入口，普通 file 员工入口保持 `PERSONAL_FILE` 专用。 |
| 2-2 | 文件下载 SPI | workflow 目前直接复用 file 的下载票据执行服务和存储读取能力。模块化单体可接受，但业务授权模式变多后，需要更明确的 SPI。 | 建议 | 中期抽出 `FileDownloadTicketPort` 或场景化下载授权 SPI，让业务模块只传授权结果和审计上下文。 |

---

## 第六部分：阶段性结论

本轮修复后，审批附件下载链路的边界更清晰：

- 个人文件：走 `/api/employee/files/**`，只允许 `PERSONAL_FILE`。
- 审批附件：走 `/api/employee/workflow/**`，由 workflow 按流程参与人授权。

这个边界应作为后续群文件、文档文件、知识库文件接入下载能力时的默认规则。
