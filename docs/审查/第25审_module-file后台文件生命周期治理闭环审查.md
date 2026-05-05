# 第 25 审 module-file 后台文件生命周期治理闭环审查

- 审查日期：2026-05-05
- 审查提交：工作区未提交状态
- 审查范围：`server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`、`server/src/main/java/com/workforcehub/file/service/FileCenterService.java`、`server/src/test/java/com/workforcehub/file/ModuleFileAdminApiIntegrationTest.java`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`
- 审查目标：复核第 24 审 1-1 后台文件生命周期缺口修复是否形成自洽闭环，并检查新增后台下载能力是否引入跨主体秒传与可下载风险。

---

## 第一部分：提交内容概览

本轮工作完成了以下事项：

- 新增后台有效文件列表 `GET /api/admin/files`。
- 新增后台回收站列表 `GET /api/admin/files/recycle-bin`。
- 新增后台下载票据 `POST /api/admin/files/{attachmentRefId}/download-tickets`。
- 新增后台真实下载入口 `GET /api/admin/files/download/{ticketNo}`。
- 新增后台移入回收站、恢复、彻底删除接口。
- 后台彻底删除释放企业总池 `GLOBAL` 用量，并按物理引用数决定是否设置延迟清理时间。
- 申请阶段秒传收窄为仅复用同一上传主体已有有效引用，避免后台仅凭员工文件 hash 和大小直接创建可下载引用。
- 普通上传票据的 `objectKey` 加入上传票据 ID，避免跨主体同 hash 文件拿到同一个上传路径。
- API 文档、测试用例和开发日志同步更新。

本次验证记录：

- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminShouldManageAdminFileLifecycle test`，失败于 `GET /api/admin/files` 返回 `404`。
- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly test`，失败于后台直接返回 `deduplicated=true`。
- 绿灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly test`，通过。
- 回归：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`，`4` 个用例通过。
- 回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`，`26` 个用例通过。

---

## 第二部分：风险审查

### 一、生命周期闭环

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `AdminFileController` + `FileCenterService` | 第 24 审指出的“后台文件可写入但不可治理”问题已基本修复。当前后台 `ADMIN_FILE` 已具备列表、下载、回收站、恢复、彻底删除和 `GLOBAL` 用量释放路径。 | 已处理 | 后续重点转向清理任务和权限细分，不再把 1-1 视为阻塞缺口。 |
| 1-2 | `purgeAdminFile` | 后台彻底删除只释放 `GLOBAL` 用量，不释放 `USER` 用量，和后台文件不占员工个人空间的账本口径一致。当前测试已断言彻底删除后 `GLOBAL` 归零。 | 已处理 | 后续如开放部门空间、群空间或知识库空间，需要为不同 `ownerType` 补独立用量释放策略。 |
| 1-3 | `trashAdminFile` / `restoreAdminFile` | 后台移入回收站与员工端一致，不立即释放用量。恢复时只复核 `GLOBAL`，符合当前后台文件只占企业总池的设计。 | 已处理 | 后续如果后台文件接入空间级配额，需要恢复时同步复核对应空间配额。 |

### 二、跨主体秒传与下载安全

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `createAdminUploadTicket` / `createEmployeeUploadTicket` | 原先只要存在相同 `contentSha256 + fileSize` 的物理对象，后台就能在申请阶段直接秒传并得到可下载引用。后台下载落地后，这会变成“知道他人文件 hash 和大小即可挂载下载”的风险。本轮已通过红灯测试复现并修复：申请阶段秒传必须要求同一上传主体已有有效附件引用。 | 已处理 | 后续如果要支持跨主体秒传，应引入更明确的持有证明或授权证明，而不是退回纯 hash 复用。 |
| 2-2 | `buildObjectKey` | 原先普通上传路径只使用 hash 前缀和扩展名，跨主体相同内容会拿到同一个上传路径。即使申请阶段不秒传，也可能绕过实际上传直接确认已有对象。本轮已把上传票据 ID 加入对象 key。 | 已处理 | 生产环境接 MinIO 时同样需要保持对象 key 唯一，避免对象覆盖和确认绕过。 |
| 2-3 | `findReusableFileObjectForUploader` | 当前可复用判定只看同一 `uploader_type + uploader_id` 下存在有效附件引用，不看 `ownerType`。这意味着同一管理员未来如果拥有多个后台文件归属类型，可能在不同后台场景间秒传。当前第一版后台只开放 `ADMIN_FILE`，风险被限制。 | 建议 | 等 `KB_DOCUMENT`、`DOC_DOCUMENT` 等后台归属开放前，建议把可复用范围扩展为上传主体 + 授权场景，而不只是上传主体。 |

### 三、权限与审计

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | 所有 `/api/admin/files/**` 生命周期接口 | 当前后台生命周期接口仍只依赖后台登录态和路径级身份区分，没有细分“文件查看、下载、删除、彻底删除、配额治理”等权限点。功能已闭环，但企业级权限治理还不够细。 | 重要 | 接 RBAC 时应至少拆 `FILE_ADMIN_VIEW`、`FILE_ADMIN_DOWNLOAD`、`FILE_ADMIN_TRASH`、`FILE_ADMIN_PURGE`、`FILE_QUOTA_MANAGE` 权限点。 |
| 3-2 | 后台下载审计 | 后台下载票据创建和下载执行均已写审计，删除、恢复、彻底删除也已写审计。 | 已处理 | 后续审计详情可增加 IP、User-Agent、文件名、文件大小和原因字段，便于追溯。 |

### 四、清理任务与物理对象

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `cleanup_after` | 彻底删除最后一个引用时会设置 `cleanup_after`，但清理任务 `POST /api/admin/files/cleanup-jobs/run` 仍未实现，物理对象不会被后台任务真正删除。 | 重要 | 下一批优先做清理任务 dry-run、真实执行、过期票据清理、到期回收站引用清理和无引用物理对象清理。 |
| 4-2 | `sys_file_object.ref_count` | 后台彻底删除后通过查询有效附件引用数回写 `ref_count`。当前串行测试可通过，但高并发删除、恢复或确认时仍可能出现引用数短暂不一致。 | 建议 | 后续需要为引用数更新加锁、改为基于数据库事务一致性更新，或将 `ref_count` 视为可重建的冗余计数并提供修复任务。 |

### 五、代码可维护性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 5-1 | `FileCenterService` | 本轮继续追加后台列表、下载、回收站和安全秒传边界，`FileCenterService` 已明显过大，集中承载上传、确认、下载、生命周期、配额、策略、审计、存储 Provider 编排。 | 重要 | 下一批清理任务前建议先拆 `FileQueryService`、`FileLifecycleService`、`UploadTicketService`、`StorageUsageService` 或 Repository，避免继续堆成单点复杂度。 |
| 5-2 | `AdminFileController` / `EmployeeFileController` | 后台下载响应构造和员工端下载响应构造基本重复。当前重复量可接受，但后续若新增下载头、预览、范围下载，容易两端不一致。 | 建议 | 可抽出下载响应工厂或公共方法，保持员工端与后台端下载行为一致。 |

---

## 第三部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 重要 | 3 | 3-1、4-1、5-1 |
| 建议 | 4 | 2-3、4-2、5-2 |
| 已处理 | 6 | 1-1、1-2、1-3、2-1、2-2、3-2 |
| 合计 | 13 |  |

---

## 第四部分：处理优先级建议

**第一优先级（下一批 module-file 推荐先做）：**

1. 实现后台清理任务 `cleanup-jobs/run`，先 dry-run 再真实执行。
2. 拆分 `FileCenterService`，至少把生命周期、查询、上传票据、用量账本拆开。
3. 为后台文件接口接入细粒度权限点，不要长期只依赖后台登录态。

**第二优先级（进入知识库、文档、群文件前处理）：**

- 将申请阶段秒传的复用范围从“同一上传主体”扩展为“同一上传主体 + 授权场景”。
- 为知识库、文档、群文件各自定义 ownerType 授权校验和用量释放策略。
- 补并发确认、并发删除、并发恢复下的引用数与账本一致性测试。

---

## 第五部分：阶段性结论

本轮已经把第 24 审最关键的后台 `ADMIN_FILE` 生命周期缺口补成可运行闭环，并额外收口了后台下载落地后暴露出来的跨主体 hash 秒传风险。当前自动化回归覆盖员工端与后台端共 `26` 个文件模块用例。

剩余主要风险不在“后台文件能不能管”，而在“后台治理是否足够企业级”：清理任务还没落地，权限点仍偏粗，`FileCenterService` 复杂度继续上升。下一步应优先做清理任务和服务拆分，再继续扩展知识库、文档或 Agent 文件接入。
