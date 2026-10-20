# 第 27 审 module-file 后台清理任务第一版审查

- 审查日期：2026-05-05
- 审查提交：工作区未提交状态
- 审查范围：`server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`、`server/src/main/java/com/workforcehub/file/service/FileCleanupJobService.java`、`server/src/main/java/com/workforcehub/file/storage/`、`server/src/test/java/com/workforcehub/file/ModuleFileCleanupIntegrationTest.java`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`
- 审查目标：复核后台清理任务第一版是否形成可运行闭环，并检查清理任务对数据、物理对象和用量账本的安全边界。

---

## 第一部分：提交内容概览

本轮工作完成了以下事项：

- 新增后台手动触发清理任务接口 `POST /api/admin/files/cleanup-jobs/run`。
- 新增 `RunCleanupJobRequest` 和 `CleanupJobRunResponse`。
- 新增独立 `FileCleanupJobService`，避免继续扩大 `FileCenterService`。
- 支持 `EXPIRED_UPLOAD_TICKET`、`EXPIRED_DOWNLOAD_TICKET`、`EXPIRED_RECYCLE_REF`、`UNREFERENCED_FILE_OBJECT`、`ALL_EXPIRED`。
- 支持 `dryRun=true` 只统计候选数据，不修改票据、引用或物理对象。
- 真实执行会过期上传票据、过期下载票据、到期回收站引用，并删除已到期无引用物理对象。
- `LOCAL` 与 `MINIO` Provider 均补充 `deleteObject`。
- 物理对象采用两阶段清理：业务引用到期先彻底删除引用并进入 `cleanup_after` 候选，物理对象到期后再删除二进制。
- 清理任务第一版只处理 `PERSONAL_FILE` 与 `ADMIN_FILE` 到期回收站引用，避免误处理尚未接入用量策略的 ownerType。
- API 文档、测试用例和开发日志已同步。

本次验证记录：

- 红灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest test`，失败于 `/api/admin/files/cleanup-jobs/run` 返回 `404`。
- 绿灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest test`，2 个清理任务用例通过。
- 回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，34 个文件模块用例通过。
- 差异检查：`git diff --check` 通过。

---

## 第二部分：风险审查

### 一、清理范围与数据安全

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `FileCleanupJobService.summarizeExpiredRecycleRefs`、`purgeExpiredRecycleRefs` | 清理到期回收站引用如果不限制 `owner_type`，未来 `KB_DOCUMENT`、`DOC_DOCUMENT`、`GROUP_FILE` 等引用还没建立用量释放策略时，可能被第一版清理任务提前处理。 | 重要 | 已处理。第一版明确只处理 `PERSONAL_FILE` 与 `ADMIN_FILE`，其他 ownerType 等各模块 ACL 和用量策略落地后再接入。 |
| 1-2 | `deleteUnreferencedFileObjects` | 无引用物理对象删除采用条件查询和条件更新，避免仍有有效引用的物理对象被清理。 | 已处理 | 当前通过 `NOT EXISTS` 双重判断，并在更新成功后才调用 Provider 删除对象。后续可进一步加批量游标和行级锁。 |
| 1-3 | `EXPIRED_UPLOAD_TICKET` | 过期上传票据清理会删除未确认临时对象。若对象存储删除失败，当前事务会回滚数据库更新，但物理删除本身无法跟随数据库事务回滚。 | 建议 | 第一版接受这个边界。后续生产化建议增加清理任务运行表、失败明细和重试机制。 |

### 二、用量账本与状态机

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 2-1 | `purgeExpiredRecycleRefs` | 到期回收站引用彻底删除后，会按 ownerType 释放 `USER` 与 `GLOBAL` 用量。员工个人文件释放两类账本，后台文件只释放 `GLOBAL`，与现有手动彻底删除口径一致。 | 已处理 | 已由 `ModuleFileCleanupIntegrationTest` 覆盖员工与后台两类引用。 |
| 2-2 | `refreshFileObjectCleanupState` | 清理任务不会在同一轮直接删除刚因回收站到期而无引用的物理对象，而是设置 7 天后的 `cleanup_after`，与手动彻底删除保持一致。 | 已处理 | 这样保留缓冲期，降低误删和对象存储异常恢复成本。 |
| 2-3 | `EXPIRED_DOWNLOAD_TICKET` | 过期下载票据只将 `ACTIVE` 改为 `EXPIRED`，不会影响 `USED`、`REVOKED` 或已删除记录。 | 已处理 | 当前 SQL 条件明确。 |

### 三、接口契约与可测试性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 3-1 | `RunCleanupJobRequest.jobType` | `jobType` 使用白名单校验，不支持的任务类型返回业务错误。 | 已处理 | 已通过服务层 `SUPPORTED_JOB_TYPES` 收口。后续可补单独异常用例。 |
| 3-2 | `ModuleFileCleanupIntegrationTest` | 已覆盖 dry-run 不修改数据、真实执行修改状态、释放用量、删除到期无引用对象和审计写入。 | 已处理 | 当前自动化覆盖清理任务主链路。 |
| 3-3 | `docs/API与测试/module-file/` | API 文档已从“契约待实现”更新为“已实现”，并记录两阶段物理清理语义。 | 已处理 | 后续若接定时调度，需要补调度策略和运行表文档。 |

### 四、可维护性与后续演进

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 4-1 | `FileCleanupJobService` | 清理任务没有继续写入 `FileCenterService`，职责边界清楚。 | 已处理 | 后续可围绕该服务补任务运行表、失败重试、批量分页和定时调度。 |
| 4-2 | `StorageProvider.deleteObject` | `StorageProvider` 增加删除对象能力后，`LOCAL` 和 `MINIO` 都已实现，避免清理任务耦合具体存储。 | 已处理 | 后续新增 OSS/WebDAV Provider 时必须实现同一接口。 |
| 4-3 | 批量处理策略 | 当前第一版一次查询所有候选数据，适合开发期和小数据量。生产数据量大时可能需要分页、批大小和任务游标。 | 建议 | 后续进入运维化阶段补 `batchSize`、运行表、失败明细和可恢复执行。 |

---

## 第三部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 1 | 1-1 |
| 建议 | 2 | 1-3、4-3 |
| 已处理 | 8 | 1-1、1-2、2-1、2-2、2-3、3-1、3-2、3-3、4-1、4-2 |
| 合计 | 11 |  |

说明：`1-1` 原本属于重要风险，已在本轮审查前收窄清理范围，因此同时计入已处理。

---

## 第四部分：处理优先级建议

**第一优先级（提交前已处理）：**

1. 清理任务只处理第一版已支持的 `PERSONAL_FILE` 与 `ADMIN_FILE`。
2. 无引用物理对象先条件更新数据库，抢到删除资格后再调用 Provider 删除对象。
3. 文档明确两阶段物理清理，避免误解为同一轮直接删除业务引用和二进制。

**第二优先级（后续生产化前处理）：**

- 增加清理任务运行表，记录开始时间、结束时间、触发人、状态、失败原因和统计结果。
- 增加失败明细与重试，尤其是对象存储删除失败场景。
- 增加批量大小、分页游标和并发互斥，避免大数据量一次扫表。
- 为不支持的 `jobType` 补独立异常测试。

---

## 第五部分：阶段性结论

后台清理任务第一版已经具备可运行闭环：能 dry-run 预估，也能真实处理过期上传票据、过期下载票据、到期回收站引用和到期无引用物理对象。更重要的是，本轮没有继续把逻辑堆进 `FileCenterService`，而是拆成了独立清理任务服务。

当前剩余风险主要是“生产化任务治理”而不是“功能不可用”：还没有任务运行表、失败明细、重试和批量游标。就第一版开发节点而言，清理任务已经可以作为 `module-file` 第一版闭环的一部分进入提交。
