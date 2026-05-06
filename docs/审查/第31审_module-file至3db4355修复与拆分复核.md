# 第 31 审 module-file 至3db4355修复与拆分复核

- 审查日期：2026-05-06
- 审查提交：
  - `73e593aec8266dc4d8ae57983178738fc52e8e21`（修复module-file清理任务失败恢复闭环）
  - `da81dbc1943dbda3a34a4e07f9d65e1c9f3850a9`（拆分module-file上传确认服务）
  - `3db43556783ad8f6b1c4f95ea3ab5b950b59c196`（拆分module-file下载服务）
- 审查范围：`server/src/main/java/com/workforcehub/file/service/FileCleanupJobService.java`、`FileUploadConfirmService.java`、`FileDownloadService.java`、`FileCenterService.java`、`server/src/main/java/com/workforcehub/file/dto/CleanupJobRunResponse.java`、`server/src/test/java/com/workforcehub/file/ModuleFileCleanupIntegrationTest.java`、`ModuleFileServiceBoundaryTest.java`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`
- 审查目标：复核第 30 审之后到 `3db4355` 的清理任务修复和服务拆分是否真正收口正确性风险，并继续识别冗余、可维护性隐患；本审只新增审查文件，不修改业务代码。

---

## 第一部分：提交内容概览

本轮三次提交主要完成了以下事项：

- `73e593a`：清理任务新增 `FAILED_PHYSICAL_OBJECT` 任务类型，支持复制历史最新失败明细进行物理删除重试；数据库阶段失败时保留 `jobStatus=FAILED`、`errorMessage`；`deletedPhysicalObjects` 改为统计本次所有物理删除成功明细；API 文档和测试用例同步。
- `da81dbc`：新增 `FileUploadConfirmService`，把员工端和后台端确认上传逻辑从 `FileCenterService` 中迁出，保留确认上传、对象校验、去重、入账和审计语义。
- `3db4355`：新增 `FileDownloadService`，把下载票据签发、票据消费和物理对象读取从 `FileCenterService` 中迁出，并补 `ModuleFileServiceBoundaryTest` 约束服务边界。

整体判断：本轮是沿着第 30 审继续收口，而不是堆补丁。清理任务的失败恢复、统计口径和数据库阶段失败记录已经有代码和测试落点；`FileCenterService` 也从约 953 行继续降到约 471 行，主服务职责明显变薄。

---

## 第二部分：第 30 审问题复核

| 序号 | 问题位置 | 复核结果 | 严重程度 | 结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `FAILED_PHYSICAL_OBJECT`、`copyFailedCleanupDetailsForRetry` | 第 30 审指出物理删除失败明细“可观察但不可恢复”。当前已新增失败重试任务，按 `storage_provider + bucket_name + object_key` 选择最新仍为 `FAILED` 的明细复制到新任务执行；测试覆盖“两次失败后第三次成功”和成功覆盖后 dry-run 不再重复计数。 | 已处理 | 原 2-1 已阶段性收口。 |
| 1-2 | `finalizePhysicalCleanup`、`deletedPhysicalObjects` | 第 30 审指出成功数和失败数口径不一致。当前 `deletedPhysicalObjects` 改为统计本次所有 `SUCCESS` 明细，过期上传临时对象和无引用文件对象都纳入同一口径；dry-run 也把过期上传临时对象计入预计物理删除数。 | 已处理 | 原 2-2 已收口。 |
| 1-3 | `createJobRun`、`markJobRunFailed`、`CleanupJobRunResponse.errorMessage` | 第 30 审指出数据库阶段失败会回滚运行记录。当前任务运行记录先在事务外创建，数据库阶段异常时回写 `FAILED`、`errorMessage` 和 `finished_at`，测试断言业务数据回滚且任务表保留失败原因。 | 已处理 | 原 2-3 已收口。 |
| 1-4 | `FileUploadConfirmService` | 第 30 审建议拆出上传确认服务。当前员工端、后台端确认上传已迁入独立服务，`FileCenterService` 仅保留委托入口。 | 已处理 | 原 3-1 已阶段性收口。 |
| 1-5 | `FileDownloadService` | 下载票据签发、下载票据执行和物理对象读取已迁入独立服务，主服务不再直接持有下载票据状态机和对象读取逻辑。 | 已处理 | 进一步降低了主服务复杂度。 |

---

## 第三部分：仍需关注的正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `FileCleanupJobService.runCleanupJob`、`executePendingPhysicalDeletes`、`copyFailedCleanupDetailsForRetry` | 当前失败重试只复制历史最新 `FAILED` 明细，正常物理执行只处理本次 `jobRunId` 下的 `PENDING` 明细。如果应用在数据库阶段提交后、物理删除执行前崩溃，或物理删除成功后回写明细/最终运行状态时数据库异常，会留下旧任务的 `RUNNING` 记录和 `PENDING` 明细；这些明细既不会被普通清理任务再次扫描，也不会进入 `FAILED_PHYSICAL_OBJECT` 重试候选，物理对象可能长期卡住。 | 重要 | 增加“恢复悬挂清理任务”能力：按超时的 `RUNNING` 任务或历史 `PENDING` 明细恢复执行；或定时把超过阈值的 `PENDING` 明细标记为 `FAILED` 后进入现有重试链路。物理阶段也建议外层捕获最终状态更新失败，至少把任务置为可恢复状态。 |
| 2-2 | `FileUploadConfirmService.confirmEmployeeUpload`、`confirmAdminUpload` | 普通上传确认阶段会先校验本次上传对象，然后用 `contentSha256 + fileSize` 全局查找已有 `sys_file_object`。命中已有对象时会把附件引用挂到已有物理对象上，但本次上传票据里的临时 `objectKey` 不会生成 `sys_file_object`，票据又会被标记为 `CONFIRMED`，后续过期上传票据清理也不会再处理它。跨主体或跨场景相同内容确认上传时，用户实际上传的重复临时对象可能长期留在对象存储。 | 重要 | 在确认上传命中已有文件对象且 `ticket.objectKey != fileObject.objectKey` 时，成功提交附件引用后应安排本次临时对象清理；可以写入清理明细、标记临时对象待删，或在事务提交后删除并记录失败明细，避免确认成功但临时对象无账可查。 |

---

## 第四部分：冗余与可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `FileCleanupJobService` | 清理任务修复后能力更完整，但单类已经约 721 行，同时承载任务创建、候选标记、失败重试筛选、物理删除执行、统计回写、审计 JSON 和错误文本处理。后续再加定时调度、分页游标、退避重试和最大重试次数时，这个类会重新成为清理域的单点复杂度。 | 建议 | 拆出 `CleanupJobRunRepository`、`CleanupCandidateService`、`PhysicalCleanupExecutor` 或类似边界，让“数据库候选标记”和“对象存储执行/重试”分离。 |
| 3-2 | `ModuleFileServiceBoundaryTest` | 边界测试目前只用反射断言 `FileUploadConfirmService`、`FileDownloadService` 存在指定方法，不能证明 `FileCenterService` 已经不再持有同类业务逻辑，也不能捕获迁移后行为漂移。它适合作为轻量结构提醒，但不应替代上传确认、下载和生命周期集成回归。 | 建议 | 保留现有集成测试作为主保护；若要加强边界测试，可以断言主服务只委托目标服务，或用 ArchUnit 风格约束服务依赖方向。 |
| 3-3 | `FileCenterService.audit`、`FileUploadTicketService.audit`、`FileUploadConfirmService.audit`、`FileDownloadService.audit`、`FileQuotaPolicyService.audit`、`FileCleanupJobService.auditCleanupJob` | 服务拆分后审计写入重复面继续扩大，且多数服务仍手写 JSON 字符串。清理任务已经补了 `safeJsonText` 处理换行和制表符，但其他服务仍是各自的转义口径。 | 建议 | 后续抽一个 `FileAuditService` 或复用通用审计服务，统一结构化序列化、字段命名和失败结果口径。 |
| 3-4 | `FileCenterService` 生命周期方法 | 上传票据、确认上传、下载和配额都已拆出，但回收站删除、恢复、彻底删除、后台列表和生命周期审计仍在 `FileCenterService`。当前已经比前几轮清晰很多，但如果后续扩展 ownerType，生命周期规则仍会先挤回主服务。 | 建议 | 下一步优先拆 `FileLifecycleService` 和后台查询服务，让主服务最终只保留门面委托或直接由 Controller 调用专门服务。 |

---

## 第五部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 2 | 2-1、2-2 |
| 建议 | 4 | 3-1、3-2、3-3、3-4 |
| 已处理 | 5 | 1-1、1-2、1-3、1-4、1-5 |
| 合计 | 11 |  |

---

## 第六部分：处理优先级建议

**第一优先级（清理任务生产化前）：**

1. 增加历史 `PENDING` 明细或超时 `RUNNING` 任务的恢复机制，避免进程崩溃窗口造成清理对象永久悬挂。
2. 处理确认上传命中全局去重时的本次临时对象清理，避免重复内容上传积累对象存储垃圾。

**第二优先级（继续降低维护成本）：**

- 拆分 `FileCleanupJobService` 内的运行记录、候选标记和物理执行职责。
- 继续拆 `FileLifecycleService`，把回收站、恢复、彻底删除和生命周期审计从主服务移出。
- 收敛文件中心审计写入，避免每拆一个服务就复制一份 JSON 拼接。

---

## 第七部分：阶段性结论

到 `3db4355` 为止，本轮提交整体质量是向前的：第 30 审提出的清理任务失败恢复、统计口径、数据库阶段失败记录都已经补上；上传确认和下载服务也完成了行为不变拆分，`FileCenterService` 明显瘦身。

当前没有发现阻断级或重大级问题。剩余风险主要集中在两个“边界窗口”：清理任务提交数据库状态后如果还没来得及执行或回写物理删除，会留下旧 `PENDING` 明细；确认上传全局去重时，本次实际上传的重复临时对象没有清理账本。把这两块补上后，文件中心的清理闭环和存储账本会更接近生产可恢复状态。
