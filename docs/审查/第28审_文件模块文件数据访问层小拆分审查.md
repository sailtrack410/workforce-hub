# 第 28 审 module-file 文件数据访问层小拆分审查

- 审查日期：2026-05-05
- 审查提交：工作区未提交状态
- 审查范围：`server/src/main/java/com/workforcehub/file/service/FileCenterService.java`、`server/src/main/java/com/workforcehub/file/repository/`、`docs/开发日志/module-file.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核本轮小拆分是否降低 `FileCenterService` 膨胀风险，同时确认没有改变文件中心既有接口行为。

---

## 第一部分：提交内容概览

本轮工作完成了以下事项：

- 新增 `AttachmentRefRepository`，集中处理附件引用查询、下载附件查询、上传票据到附件引用的回查、文件对象有效引用计数。
- 新增 `FileObjectRepository`，集中处理文件对象查询、hash/size 活动对象查找、上传主体可复用对象查找、引用计数增加、无引用对象清理候选刷新。
- 新增 `AttachmentRow`、`DownloadAttachmentRow`、`FileObjectRow`，将原本散落在 `FileCenterService` 末尾的内部 record 移到 `repository` 包。
- `FileCenterService` 改为调用 repository 完成文件对象和附件引用访问，保留上传、确认、下载、回收站、配额等业务编排。
- 员工端与后台端彻底删除复用 `refreshRefCountAndCleanupAfter`，减少重复 SQL 和分支漂移风险。
- `FileCenterService` 行数从 `1903` 降到 `1698`，后续继续拆上传票据、生命周期、配额策略时边界更清楚。

本次验证记录：

- 重构前基线：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，34 个文件模块用例通过。
- 重构后编译：`mvn -DskipTests compile` 通过。
- 重构后文件模块回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，34 个文件模块用例通过。
- 差异检查：`git diff --check` 通过。

---

## 第二部分：风险审查

### 一、行为兼容性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 1-1 | `FileCenterService` 调用 repository 后的上传确认链路 | 文件对象查找、秒传复用、上传票据幂等回查都依赖原 SQL 条件。如果迁移时少带 `is_deleted`、`deleted_at` 或上传主体过滤，可能造成跨主体秒传或已删除对象复用。 | 重要 | 已处理。`FileObjectRepository.findReusableForUploader` 保留上传主体、未删除附件、未回收站、文件对象未删除条件；`findActiveBySha256AndSize` 保留活动文件对象条件。 |
| 1-2 | `AttachmentRefRepository.getAttachmentOrThrow` | 原逻辑查询附件引用时不强制过滤 `is_deleted`，由调用方根据状态决定返回 `404`、`409` 或幂等响应。若 repository 擅自过滤，会改变恢复、彻底删除和并发分支行为。 | 重要 | 已处理。repository 保留原查询口径，只在查不到行时抛 `附件引用不存在`，状态判断仍由业务服务负责。 |
| 1-3 | `getDownloadAttachmentOrThrow` | 下载附件查询需要同时返回附件删除状态和文件对象删除状态。若只查附件表，物理对象已清理时可能错误放行。 | 已处理 | 已保留 `attachment_deleted` 与 `file_deleted` 两类状态字段。 |

### 二、职责边界与可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 2-1 | `FileCenterService` | 主服务仍有 `1698` 行，上传票据、配额策略、回收站生命周期、审计仍混在一起。 | 建议 | 本轮先拆最低风险的数据访问层，后续建议继续拆 `FileUploadTicketService`、`FileLifecycleService`、`FileQuotaPolicyService`。 |
| 2-2 | `repository` 包 | 当前 repository 仍使用 `JdbcTemplate` 和 SQL 字符串，尚未统一到 Mapper 或领域仓储接口。 | 建议 | 与当前项目风格一致，先用小组件隔离 SQL；等模块稳定后再评估 MyBatis Mapper 化。 |
| 2-3 | `refreshRefCountAndCleanupAfter` | 引用数刷新从两个业务分支抽到 repository 后，语义更集中，但仍依赖调用方先成功完成附件引用彻底删除。 | 已处理 | 员工端和后台端都只在条件更新成功后调用该方法，现有并发测试覆盖用量只释放一次。 |

### 三、测试与回归

| 序号 | 问题位置 | 问题描述 | 严重程度 | 处理结论 |
| --- | --- | --- | --- | --- |
| 3-1 | 自动化测试 | 本轮是重构，核心风险不是新增接口，而是旧行为漂移。 | 已处理 | 重构前已跑文件模块 34 个用例作为基线；重构后编译和同一组文件模块回归均已通过。 |
| 3-2 | 文档状态 | 开发计划中的 `module-file` 待补清单此前仍列出已完成项，会误导后续排期。 | 已处理 | 本轮同步校准计划，把已完成项和剩余项拆开。 |

---

## 第三部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 2 | 1-1、1-2 |
| 建议 | 2 | 2-1、2-2 |
| 已处理 | 5 | 1-1、1-2、1-3、2-3、3-1、3-2 |
| 合计 | 7 |  |

说明：`1-1`、`1-2` 是本类重构最容易产生的行为漂移风险，已通过保留原 SQL 口径和文件模块回归测试控制。

---

## 第四部分：处理优先级建议

**第一优先级（提交前完成）：**

1. 已跑 `ModuleFileApiIntegrationTest`、`ModuleFileAdminApiIntegrationTest`、`ModuleFileCleanupIntegrationTest` 全量回归。
2. 已跑 `git diff --check`。
3. 提交时确认本轮不夹带 `.idea`、原型、申报图和其他未跟踪文档。

**第二优先级（后续继续做）：**

- 拆 `FileUploadTicketService`：上传票据签发、幂等、秒传复用。
- 拆 `FileLifecycleService`：移入回收站、恢复、彻底删除、引用数刷新。
- 拆 `FileQuotaPolicyService`：配额策略 CRUD 和配额校验。
- 将清理任务运行表、失败明细和批量游标作为生产化增强独立排期。

---

## 第五部分：阶段性结论

本轮拆分属于低风险维护性重构：没有新增 API、没有改表结构，也没有改变文件中心状态机语义。收益是 `FileCenterService` 不再直接承载附件引用与文件对象的所有查询细节，后续继续拆分时有了更明确的落点。

当前没有发现阻断问题。文件模块回归和空白检查均已通过，本轮可以作为 `module-file` 第一版收口前的结构优化提交。
