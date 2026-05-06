# 第 30 审 module-file 至35effd3修复与拆分复核

- 审查日期：2026-05-06
- 审查提交：
  - `9635ae40fac502a72af4966ba68ac5e650a00ec9`（修复module-file过期下载票据状态持久化）
  - `b416bd6cbe3a1dcb67cc74da1a7eedd28b190718`（完善module-file清理任务运行记录）
  - `c632bb02813d209d6069acf40c47fbba13d53603`（拆分module-file配额策略服务）
  - `35effd3452bd50b997ec0084a20704dbdd73d7d7`（拆分module-file上传票据服务）
- 审查范围：`server/src/main/java/com/workforcehub/file/service/`、`server/src/main/java/com/workforcehub/file/repository/`、`server/src/main/java/com/workforcehub/file/dto/CleanupJobRunResponse.java`、`server/src/main/resources/db/migration/V8__add_file_cleanup_job_records.sql`、`server/src/test/java/com/workforcehub/file/`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`
- 审查目标：复核第 29 审之后到 `35effd3` 的修复与拆分提交，确认正确性风险是否收口，并继续识别冗余、可维护性隐患；本审只新增审查文件，不修改业务代码。

---

## 第一部分：提交内容概览

本轮四次提交主要做了三类工作：

- `9635ae4`：下载票据过期状态更新改为 `REQUIRES_NEW` 独立事务，并补员工端、后台端“访问过期下载票据后状态持久化为 `EXPIRED`”测试。
- `b416bd6`：清理任务增加 `sys_file_cleanup_job_run` 和 `sys_file_cleanup_job_detail`，把数据库状态标记与对象存储删除拆成两阶段；物理删除失败时返回 `PARTIAL_FAILED` 并写失败明细；测试基类也清理 `sys_audit_log` 和新增清理任务表。
- `c632bb0`：从 `FileCenterService` 拆出 `FileQuotaPolicyService`，集中配额查询、存储摘要、配额策略 CRUD 和配额阻断逻辑。
- `35effd3`：从 `FileCenterService` 拆出 `FileUploadTicketService`，集中员工/后台上传票据申请、上传策略校验、`clientRequestId` 幂等、同上传主体秒传和上传票据审计。

整体方向正确：第 29 审中“过期下载票据状态会被事务回滚”和“清理任务缺少运行记录、物理删除混在数据库事务里”的核心风险，已经有明确代码与测试收口；服务拆分也明显降低了 `FileCenterService` 的膨胀速度。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 复核结果 | 严重程度 | 结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `DownloadTicketExecutionService.expireActiveTicket` | 过期票据状态更新已标注 `@Transactional(propagation = REQUIRES_NEW)`，员工端和后台端下载入口仍抛 `BizException(410)`，但 `ACTIVE -> EXPIRED` 会在独立事务里提交。新增测试断言数据库状态为 `EXPIRED`。 | 已处理 | 第 29 审 2-1 已收口。 |
| 1-2 | `FileCleanupJobService.runCleanupJob` | 清理任务不再把对象存储删除放在同一个数据库事务中执行。当前先在事务中标记上传票据、下载票据、回收站引用、无引用文件对象，并写运行记录和待删除明细；事务提交后再逐条删除物理对象。 | 已处理 | 第 29 审 2-2 的事务与外部副作用边界已有明显改善。 |
| 1-3 | `V8__add_file_cleanup_job_records.sql`、`ModuleFileCleanupIntegrationTest` | 已新增运行表和明细表，响应体增加 `jobStatus`、`failedPhysicalObjects`，测试覆盖成功明细和物理删除失败明细。 | 已处理 | 第 29 审 2-3 的可观测性缺口基本补上。 |
| 1-4 | `LocalIntegrationTestSupport.resetBusinessTables` | 测试清理逻辑已增加 `sys_audit_log`、`sys_file_cleanup_job_detail`、`sys_file_cleanup_job_run`，避免历史审计或任务记录污染本次断言。 | 已处理 | 前序“审计断言可能被历史数据误报”的问题已收口。 |
| 1-5 | `FileQuotaPolicyService`、`FileUploadTicketService` | 配额策略和上传票据职责已从主服务拆出。当前 `FileCenterService` 从第 29 审约 1698 行下降到约 953 行，服务边界更清楚。 | 已处理 | 第 29 审 3-1 的大服务风险已有阶段性改善。 |

---

## 第三部分：仍需关注的正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `FileCleanupJobService.executePendingPhysicalDeletes`、`markCleanupDetailFailed` | 物理删除失败会被记录为 `FAILED`，但后续普通清理任务不会再次捞起这些失败明细：过期上传票据已经被标记为 `EXPIRED`，无引用文件对象也已经 `is_deleted = 1`，而执行器只处理“本次 `jobRunId` 且 `status='PENDING'`”的明细。结果是一次临时 MinIO/权限/网络故障后，失败对象只能人工查表处理，清理任务本身没有重试入口。 | 重要 | 增加按 `sys_file_cleanup_job_detail.status='FAILED'` 重试的后台接口或定时任务；或把失败明细重新置为可重试状态，并记录 `next_retry_at`、最大重试次数和最后错误。 |
| 2-2 | `FileCleanupJobService.finalizePhysicalCleanup`、`CleanupJobRunResponse.deletedPhysicalObjects` | `deletedPhysicalObjects` 只统计 `UNREFERENCED_FILE_OBJECT` 成功明细，但过期上传票据同样会删除临时物理对象，且失败数 `failedPhysicalObjects` 又统计所有失败明细。这样成功数和失败数口径不对称：同一批过期上传对象删除成功不会进 `deletedPhysicalObjects`，删除失败却会进 `failedPhysicalObjects`。运维看响应和审计时容易误判实际删除规模。 | 建议 | 明确字段语义：要么把字段改成 `deletedFileObjects` 并另加 `deletedUploadTempObjects`，要么让 `deletedPhysicalObjects` 统计所有物理删除成功明细。测试和 API 文档同步按同一口径断言。 |
| 2-3 | `FileCleanupJobService.prepareCleanupJob`、`sys_file_cleanup_job_run.error_message` | 运行记录是在 `transactionTemplate` 包裹的数据库阶段内创建和更新的。如果数据库阶段中途抛异常，`sys_file_cleanup_job_run` 会随事务一起回滚，表里的 `FAILED` 状态和 `error_message` 字段当前也没有写入路径。物理删除失败已可见，但数据库阶段失败仍只能靠接口错误和应用日志定位。 | 建议 | 在 `runCleanupJob` 外层捕获数据库阶段异常，用独立事务写入或更新任务级 `FAILED`、`error_message`、`finished_at`；保留本次 `jobRunId` 方便和接口返回、日志串起来。 |

---

## 第四部分：冗余与可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `FileCenterService.confirmEmployeeUpload`、`confirmAdminUpload` | 上传票据申请已经拆出，但确认上传仍留在 `FileCenterService`，并继续承担对象校验、文件对象创建/复用、附件引用创建、用量增加、票据确认和审计。后续如果继续接审批附件、IM 文件、知识库文件，确认路径会再次成为分支集中点。 | 建议 | 下一步拆 `FileUploadConfirmService`，把员工/后台差异抽成调用上下文，保留“确认票据、校验对象、创建引用、入账、审计”的单一编排入口。 |
| 3-2 | `FileCenterService.createAttachmentRef`、`FileUploadTicketService.createAttachmentRef`、`AttachmentRefRepository.create` | 附件引用创建已经下沉到 Repository，但两个服务仍各自保留一层同名私有包装方法。当前只是薄封装，后续如果附件引用默认字段、审计字段或可见性规则变化，容易出现一边更新一边遗漏。 | 建议 | 统一为一个小的应用服务方法或直接调用 Repository；如果需要保留封装，应让上传票据和确认上传共用同一处业务默认值。 |
| 3-3 | `FileCenterService.audit`、`FileUploadTicketService.audit`、`FileQuotaPolicyService.audit`、`FileCleanupJobService.auditCleanupJob` | 拆分后审计写入方法在多个服务中复制，且仍使用手写 JSON 字符串。当前字段来源大多可控，但随着 ownerType、任务明细、策略字段增加，转义、字段一致性和审计结构演进会继续变难。 | 建议 | 后续拆出轻量 `FileAuditService` 或复用通用审计服务，用结构化对象序列化 JSON，避免各服务自己拼接。 |

---

## 第五部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 1 | 2-1 |
| 建议 | 5 | 2-2、2-3、3-1、3-2、3-3 |
| 已处理 | 5 | 1-1、1-2、1-3、1-4、1-5 |
| 合计 | 11 |  |

---

## 第六部分：处理优先级建议

**第一优先级（清理任务进入定时化或生产化前）：**

1. 给物理删除失败明细补可重试入口，避免 `FAILED` 只可观察、不可恢复。
2. 统一 `deletedPhysicalObjects` 和 `failedPhysicalObjects` 的统计口径，避免响应、审计和测试解释不一致。
3. 给数据库阶段失败补任务级 `FAILED` 记录，真正用上 `error_message` 字段。

**第二优先级（继续降低补丁叠加风险）：**

- 拆出 `FileUploadConfirmService`，把确认上传从主服务中移出。
- 收敛附件引用创建薄封装，减少同名方法重复。
- 把文件中心审计写入改成结构化服务，避免多个服务继续手写 JSON。

---

## 第七部分：阶段性结论

到 `35effd3` 为止，最近几次提交不是简单补丁叠加，而是在沿着第 29 审建议做边界收敛：过期下载票据事务问题已修，清理任务已从“事务内直接删对象”推进到“两阶段 + 运行记录 + 明细”，配额和上传票据也完成了第一轮服务拆分。

当前没有发现阻断级或重大级正确性问题。最需要继续处理的是清理任务失败恢复能力：现在失败可以被记录，但失败明细不会被普通清理任务再次消费。只要把这块补成可重试闭环，清理任务就会从“可观测”再往“可恢复”迈一步。维护性方面，`FileCenterService` 已明显瘦身，但确认上传、生命周期和审计仍值得继续拆，避免后续 ownerType 扩展时重新变回单点复杂度。
