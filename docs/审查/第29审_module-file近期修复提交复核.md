# 第 29 审 module-file 近期修复提交复核

- 审查日期：2026-05-05
- 审查提交：
  - `c6e4c39dd1bd178586828bbd0485bdb831049aec`（修复module-file并发状态机与响应边界）
  - `5ca4a75f12db2161454782995f0676f3db03be98`（实现module-file后台清理任务）
  - `2a072e6846d4b8f590803e8722aa2be6f92c84e0`（拆分module-file文件数据访问层）
- 审查范围：`server/src/main/java/com/workforcehub/file/controller/`、`server/src/main/java/com/workforcehub/file/service/`、`server/src/main/java/com/workforcehub/file/repository/`、`server/src/main/java/com/workforcehub/file/storage/`、`server/src/test/java/com/workforcehub/file/`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`、`docs/审查/第27审_module-file后台清理任务第一版审查.md`、`docs/审查/第28审_module-file文件数据访问层小拆分审查.md`
- 审查目标：复核第 26 审后最近几次修复提交是否真正收口并发状态机、响应边界、清理任务和维护性风险；本审只补充审查文件，不修改业务代码。

---

## 第一部分：提交内容概览

最近三次提交完成了以下事项：

- `c6e4c39`：新增 `DownloadTicketExecutionService`，下载票据执行改为先抢占 `ACTIVE -> USED`；员工端和后台端彻底删除改为检查条件更新影响行数后再释放用量；后台列表去掉 `contentSha256`、`storageProvider`、`bucketName`、`objectKey`；新增 `FileDownloadResponseFactory` 统一下载响应与非法 MIME 降级。
- `5ca4a75`：新增 `FileCleanupJobService` 和 `POST /api/admin/files/cleanup-jobs/run`，支持过期上传票据、过期下载票据、到期回收站引用、到期无引用物理对象清理，并支持 dry-run。
- `2a072e6`：新增 `AttachmentRefRepository`、`FileObjectRepository` 和文件行记录类，把附件引用、下载附件和文件对象查询从 `FileCenterService` 中拆出。
- 自动化测试补充了员工端与后台端并发下载、并发彻底删除、后台列表字段隐藏、非法 MIME 降级、清理任务 dry-run 与真实执行。

本次复核结论：第 26 审的核心正确性风险已基本修掉，但清理任务第一版仍有生产化前必须明确的事务与外部副作用边界。

---

## 第二部分：风险审查

### 一、第 26 审问题修复状态

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `DownloadTicketExecutionService.claimActiveTicket`、`downloadEmployeeFile`、`downloadAdminFile` | 第 26 审指出同一下载票据并发请求可能都返回文件。当前已在加载对象前通过条件更新抢占票据，影响行数为 0 时返回 `409`。员工端和后台端均补了并发测试。 | 已处理 | 该风险已收口。后续如果支持范围下载或预览，也应复用同一票据状态机。 |
| 1-2 | `purgeEmployeeFile`、`purgeAdminFile` | 第 26 审指出并发彻底删除可能重复释放用量。当前已检查 `UPDATE sys_attachment_ref ... WHERE is_deleted = 0 AND deleted_at IS NOT NULL` 的影响行数，只有更新成功才释放账本，并补了员工端和后台端并发删除测试。 | 已处理 | 账本重复释放风险已收口。 |
| 1-3 | `AdminFileResponse`、`listAdminFiles` | 第 26 审指出后台列表暴露对象存储内部字段。当前返回字段已去掉 `contentSha256`、`storageProvider`、`bucketName`、`objectKey`，并有后台列表测试断言这些字段不存在。 | 已处理 | 默认列表响应边界已经收窄。 |
| 1-4 | `FileDownloadResponseFactory` | 第 26 审指出员工端和后台端下载响应重复，非法 MIME 会导致 500。当前已抽出响应工厂，非法 MIME 降级为 `application/octet-stream`，后台测试已覆盖。 | 已处理 | 响应构造重复和 MIME 硬化问题已处理。 |

### 二、仍需补充的正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `FileCenterService.downloadEmployeeFile`、`downloadAdminFile`、`DownloadTicketExecutionService.expireActiveTicket` | 下载入口遇到过期票据时会先调用 `expireActiveTicket`，随后抛出 `BizException(410, ...)`。但下载方法本身标了 `@Transactional(rollbackFor = Exception.class)`，`BizException` 是运行时异常，因此这次 `ACTIVE -> EXPIRED` 更新会随事务回滚。结果是接口每次都能返回 `410`，但票据状态仍停留在 `ACTIVE`，现有测试只断言响应，没有断言数据库状态。 | 重要 | 过期状态更新应放到独立事务、调整事务回滚规则，或在返回错误前通过不回滚的方式持久化状态；同时补“访问过期下载票据后状态变为 EXPIRED”的员工端和后台端测试。 |
| 2-2 | `FileCleanupJobService.expireUploadTickets`、`deleteUnreferencedFileObjects` | 清理任务在同一个数据库事务里更新状态后立刻调用对象存储 `deleteObject`。对象存储删除是外部副作用，不能跟随数据库事务回滚：如果某个对象删除成功后，后续另一个对象删除、审计写入或事务提交失败，数据库可能回滚到未清理状态，但物理对象已经被删，形成“DB 仍认为对象可用，存储对象已不存在”的不一致。第 27 审提到删除失败边界，但没有把“后续回滚导致已成功删除无法恢复”单独列为风险。 | 重要 | 生产化前建议改成两阶段任务：先在数据库中标记待物理删除并提交，再由可重试执行器删除对象并记录明细；或至少增加任务运行表、失败明细和可恢复重试，避免一次任务中的部分外部副作用无法追踪。 |
| 2-3 | `FileCleanupJobService.runCleanupJob` | 当前清理任务只有最终成功审计，没有任务运行表、失败明细、重试次数和阶段状态。只要任务中途抛异常，本次运行的已处理行、已删除对象和失败原因就只能靠应用日志追踪。第一版开发可接受，但后续定时任务或生产数据量上来后，排障和恢复成本会很高。 | 建议 | 增加 `sys_file_cleanup_job_run` 与明细表，记录每类清理的候选、成功、失败、重试和对象 key。 |

### 三、冗余与可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `FileCenterService` | 数据访问层小拆分有效降低了主服务复杂度，但 `FileCenterService` 仍约 1698 行，继续承载上传票据、确认上传、下载、回收站生命周期、配额策略、上传策略、审计等编排。当前拆分是好的第一步，但维护性风险还没有完全解除。 | 建议 | 下一步优先拆 `FileUploadTicketService`、`FileUploadConfirmService`、`FileLifecycleService`、`FileQuotaPolicyService`，避免清理任务之外的业务继续回流到主服务。 |
| 3-2 | `docs/审查/第27审...`、`docs/审查/第28审...` | 第 27 审和第 28 审文件已经落到目录中，但“审查提交”仍写为“工作区未提交状态”。当前第 29 审补充记录了真实提交 hash，但原审查文件本身的提交可追溯性仍不完整。 | 建议 | 后续提交前生成的自审文件，在正式提交后应补一次真实 commit hash，或统一由后续补充审记录真实提交范围。 |

---

## 第三部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 2 | 2-1、2-2 |
| 建议 | 3 | 2-3、3-1、3-2 |
| 已处理 | 4 | 1-1、1-2、1-3、1-4 |
| 合计 | 9 |  |

---

## 第四部分：处理优先级建议

**第一优先级（继续向生产化清理任务推进前处理）：**

1. 修正过期下载票据状态更新被事务回滚的问题，并补数据库状态断言。
2. 明确清理任务的数据库状态与对象存储删除之间的两阶段边界，避免外部副作用无法回滚。
3. 为清理任务增加运行记录和失败明细，至少能知道哪一次任务删除了哪些对象、哪里失败。

**第二优先级（继续降低补丁叠加风险）：**

- 继续拆 `FileCenterService` 的上传票据、确认上传、生命周期和配额策略职责。
- 第 27、28 审后续可补真实提交 hash，保持审查文件与提交链路一致。
- 为清理任务补不支持 `jobType`、对象存储删除失败、批量分页或大数据量场景测试。

---

## 第五部分：阶段性结论

最近三笔提交整体方向是对的：第 26 审指出的并发下载、并发彻底删除、后台响应字段泄漏和下载响应重复都已经有代码和测试收口；清理任务也没有继续塞进 `FileCenterService`，而是独立成服务；数据访问层小拆分让后续继续拆服务有了落点。

当前剩余风险集中在清理任务生产化和一个事务细节：过期下载票据的状态更新会因为抛业务异常而回滚；对象存储删除和数据库事务混在一起，一旦后续步骤失败，已删除的外部对象无法随数据库回滚。这两项建议优先处理，再继续扩展定时清理、更多 ownerType 或生产运维能力。
