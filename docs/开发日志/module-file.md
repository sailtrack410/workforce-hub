# 开发日志 - module-file

记录 `module-file` 文件中心相关开发改动，按时间倒序，最新在最上方。

---

## 2026-05-06 module-file 上传确认服务拆分

### 改动背景

上传票据申请已经拆到 `FileUploadTicketService`，但确认上传仍然留在 `FileCenterService` 中。确认上传同时包含上传票据归属校验、对象完整性校验、物理对象校验、文件对象去重、附件引用创建、配额记账、票据确认和审计写入。后续群文件、文档文件、审批附件和知识库文件都会复用确认上传链路，如果继续堆在主服务里，生命周期、下载和确认上传会互相牵连。

本轮继续做行为不变拆分：只迁移“确认上传”职责，不改接口契约、不改 DDL、不改变员工端和后台端已有响应结构。

### 本次处理

- 新增 `FileUploadConfirmService`，集中处理员工端和后台端确认上传
- 将上传票据读取、上传人归属校验、业务归属校验和过期/重复确认判断迁入新服务
- 将 LOCAL 对象校验、文件对象去重创建、附件引用创建、引用计数增加、配额使用量入账迁入新服务
- 将确认上传审计动作迁入新服务，保留员工端 `FILE_UPLOAD_CONFIRMED` 和后台端 `FILE_ADMIN_UPLOAD_CONFIRMED`
- `FileCenterService` 的确认上传入口改为委托调用，继续保留下载、列表、回收站和清理编排
- 新增 `ModuleFileServiceBoundaryTest`，用结构测试约束上传确认必须由独立服务承接
- `FileCenterService` 从 `953` 行降到 `657` 行，新增 `FileUploadConfirmService` `386` 行

### 验证记录

- 红灯验证：`mvn -Dtest=ModuleFileServiceBoundaryTest test`，因 `FileUploadConfirmService` 不存在失败
- 结构绿灯：`mvn -Dtest=ModuleFileServiceBoundaryTest test`，1 个结构用例通过
- 上传确认回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`，33 个文件 API 用例通过

### 意义

- 上传票据申请、上传确认、下载和生命周期开始形成独立边界，后续新增 ownerType 时不需要继续把分支堆进主服务
- 确认上传内部的去重、配额、审计和附件引用创建集中在一个服务里，更容易继续抽取公共校验或适配群文件/文档文件/知识库文件
- 这仍然是行为不变重构，下一步可以继续拆 `FileDownloadService` 或 `FileLifecycleService`，把 `FileCenterService` 收敛成薄编排门面

---

## 2026-05-05 module-file 上传票据服务拆分

### 改动背景

配额策略已经从 `FileCenterService` 中拆出，但上传票据申请仍然把员工/后台 owner 校验、上传策略读取、`clientRequestId` 幂等回查、同主体秒传、普通上传票据落库、上传地址生成、对象 key 生成和审计写入都堆在主服务里。后续 `IM_GROUP_FILE`、审批附件、文档文件和知识库文件都会扩展上传入口，如果继续在主服务中追加分支，确认上传和生命周期逻辑会被一起拖复杂。

本轮继续做行为不变拆分：只迁移“申请上传票据”职责，不迁移确认上传、不改接口、不改 DDL。

### 本次处理

- 新增 `FileUploadTicketService`，集中处理员工端和后台端上传票据申请
- 将上传策略读取、扩展名/MIME 校验、单文件大小校验迁入 `FileUploadTicketService`
- 将 `clientRequestId` 幂等回查、并发唯一键兜底、同上传主体秒传票据生成迁入新服务
- 将普通上传票据落库、LOCAL 上传地址生成、对象 key 生成和创建上传票据审计迁入新服务
- `FileCenterService` 的上传票据入口改为委托调用，确认上传、下载、回收站生命周期暂时保留在主服务
- `AttachmentRefRepository` 新增附件引用创建方法，供上传票据秒传和后续确认上传拆分复用
- `FileCenterService` 从 `1377` 行降到 `953` 行，新增 `FileUploadTicketService` `495` 行

### 验证记录

- 重构前上传票据基线：`mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldRejectWhenPersonalQuotaIsInsufficient,ModuleFileApiIntegrationTest#uploadTicketShouldRejectUnsupportedFileType,ModuleFileApiIntegrationTest#uploadTicketShouldRejectWhenFileExceedsSceneLimit,ModuleFileApiIntegrationTest#uploadTicketShouldBeIdempotentByClientRequestId,ModuleFileApiIntegrationTest#uploadTicketShouldReturnExistingTicketWhenPolicyChangesAfterFirstRequest,ModuleFileAdminApiIntegrationTest#adminShouldUploadConfirmAndDeduplicateFile,ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly,ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldBeIdempotentWhenDeduplicated test`，7 个上传票据相关用例通过
- 重构后定向回归：同一条上传票据定向命令，7 个用例通过
- 编译验证：`mvn -DskipTests compile`
- 文件模块回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`
- 空白检查：`git diff --check`

### 意义

- 上传票据申请从文件中心主服务中独立出来，后续新增群文件、文档文件、审批附件和知识库文件的上传策略时，有更清晰的扩展入口
- 主服务继续回到“确认上传、下载、回收站生命周期编排器”的位置，减少后续改一个上传规则影响删除/下载流程的概率
- 本轮刻意不拆确认上传，避免一次性跨越票据申请、对象校验、附件引用和账本入账多个边界；下一轮适合继续拆 `FileUploadConfirmService`

---

## 2026-05-05 module-file 配额策略服务拆分

### 改动背景

第 29 审建议继续降低 `FileCenterService` 的职责堆叠。上一轮已经拆出附件引用和文件对象数据访问层，但配额查询、存储摘要、配额策略 CRUD、上传/恢复前配额阻断仍然留在主服务里。后续个人云空间、企业总池、缓存保留池、知识库空间和群组空间都会依赖配额规则，如果继续混在文件上传生命周期里，后面会很难判断“是文件流程问题”还是“空间策略问题”。

本轮不改接口、不改 DDL、不改响应结构，只把配额相关逻辑收敛到独立服务。

### 本次处理

- 新增 `FileQuotaPolicyService`，集中处理员工配额查询、后台存储摘要、配额策略列表/创建/更新
- 将个人云空间和企业总池的上传/恢复前容量校验迁入 `FileQuotaPolicyService`
- 将配额策略 payload 校验、作用域唯一性校验、策略编码唯一性校验、配额使用率计算、阻断原因计算迁出 `FileCenterService`
- `FileCenterService` 保留对外方法入口和文件主流程编排，通过委托方式调用配额服务
- 清理主服务中迁移后不再使用的默认配额常量和导入
- `FileCenterService` 从 `1698` 行继续降到 `1377` 行，新增 `FileQuotaPolicyService` `405` 行

### 验证记录

- 重构前基线：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，36 个文件模块用例通过
- 配额相关定向回归：`mvn -Dtest=ModuleFileApiIntegrationTest#userAndAdminShouldQueryQuotaAndStorageSummary,ModuleFileApiIntegrationTest#adminShouldCreateListAndUpdateQuotaPolicy,ModuleFileApiIntegrationTest#adminQuotaPolicyShouldValidateThresholdAndUsedBytes,ModuleFileApiIntegrationTest#adminQuotaPolicyShouldRejectDuplicateScope,ModuleFileApiIntegrationTest#databaseShouldRejectDuplicateNullScopeQuotaPolicy,ModuleFileApiIntegrationTest#adminQuotaPolicyShouldRejectDuplicatePolicyCode,ModuleFileApiIntegrationTest#uploadTicketShouldRejectWhenPersonalQuotaIsInsufficient,ModuleFileApiIntegrationTest#restoreShouldRejectWhenGlobalQuotaIsInsufficient test`，8 个配额相关用例通过
- 文件模块回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，36 个文件模块用例通过
- 编译验证：`mvn -DskipTests compile`
- 空白检查：`git diff --check`

### 意义

- 配额策略从文件主流程中独立出来，后续接群文件、知识库文件、审批附件和 Agent 临时文件时，可以优先复用配额服务而不是继续扩写主服务
- 主服务现在更接近“上传、确认、下载、回收站生命周期编排器”，配额规则有了单独演进边界
- 本轮属于行为不变的安全重构，为后续继续拆 `FileUploadTicketService`、`FileUploadConfirmService` 和 `FileLifecycleService` 降低冲突面

---

## 2026-05-05 module-file 文件数据访问层小拆分

### 改动背景

后台清理任务已经按独立服务落地，但 `FileCenterService` 仍然同时承担业务编排、附件引用查询、文件对象查询、引用计数刷新和下载附件装载等职责。后续流程、IM、文档和 Agent 都会复用文件引用，如果继续把 SQL 堆在一个服务里，后续每接一个 ownerType 都会增加误改风险。

本轮不改变接口契约、不改表结构，只做低风险职责拆分：先把附件引用与文件对象的数据访问从主服务中剥离出来。

### 本次处理

- 新增 `AttachmentRefRepository`，集中处理附件引用查询、下载附件查询、按上传票据回查附件引用、统计文件对象有效引用数
- 新增 `FileObjectRepository`，集中处理文件对象查询、按 hash/size 查找活动对象、按上传主体查找可复用对象、引用计数增加和清理候选刷新
- 新增 `AttachmentRow`、`DownloadAttachmentRow`、`FileObjectRow`，把原先 `FileCenterService` 内部 record 迁到 `repository` 包
- `FileCenterService` 改为调用 repository 完成数据访问，保留上传、下载、回收站和配额业务编排
- 员工端和后台端彻底删除统一调用 `refreshRefCountAndCleanupAfter`，避免同一段引用数刷新 SQL 在两个分支里重复维护
- `FileCenterService` 从 `1903` 行降到 `1698` 行，为后续继续拆 `FileLifecycleService`、`FileUploadTicketService` 和 `FileQuotaPolicyService` 留出边界

### 验证记录

- 重构前基线：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，34 个文件模块用例通过
- 重构后编译：`mvn -DskipTests compile`
- 重构后回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest test`，34 个文件模块用例通过
- 空白检查：`git diff --check`

### 意义

- 主服务开始从“全量 SQL 容器”回到“业务编排服务”
- 附件引用、文件对象和引用计数的访问边界更清楚，后续接审批附件、IM 文件、知识库文件时更容易复用和审查
- 这次只拆读写边界，不改 API 行为和数据结构，适合作为 `module-file` 第一版收口前的安全重构

---

## 2026-05-05 module-file 后台文件生命周期治理闭环

### 改动背景

第 24 审指出后台 `ADMIN_FILE` 已经可以上传和确认，但缺少后续治理闭环。如果后台文件只能入库，不能列表查看、下载、进入回收站、恢复和彻底删除，就会形成“只进不出”的管理黑洞，也无法验证企业总池用量释放是否自洽。

### 红灯验证

新增自动化用例：

- `ModuleFileAdminApiIntegrationTest#adminShouldManageAdminFileLifecycle`
- `ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly`

红灯结果：

- `GET /api/admin/files` 返回 `404 请求路径不存在`
- 后台仅凭员工文件的 `contentSha256 + fileSize` 申请上传票据时返回 `deduplicated=true`

这说明后台文件生命周期接口尚未落地，同时后台下载落地后，申请阶段秒传必须补“上传主体已拥有有效引用”的证明边界，不能只凭 hash 和大小直接挂载他人文件。

### 修订内容

新增：

- `server/src/main/java/com/workforcehub/file/dto/AdminFileResponse.java`

修改：

- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileAdminApiIntegrationTest.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 新增后台有效文件列表 `GET /api/admin/files`
- 新增后台回收站列表 `GET /api/admin/files/recycle-bin`
- 新增后台下载票据和真实下载入口
- 新增后台移入回收站、恢复、彻底删除接口
- 后台生命周期仅治理 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的文件，不接管员工个人文件、知识库文件、文档文件或群文件
- 后台彻底删除只释放企业总池 `GLOBAL` 用量，不释放员工 `USER` 用量
- 后台下载、删除、恢复、彻底删除均写入 `FILE_ADMIN_*` 审计动作
- 申请阶段秒传收窄为只复用同一上传主体已有的有效附件引用；跨上传主体同内容必须先拿普通上传票据并实际上传对象
- 普通上传票据的 `objectKey` 加入上传票据 ID，避免不同主体相同 hash 拿到同一个上传路径

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminShouldManageAdminFileLifecycle test`
- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly test`
- 绿灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminShouldManageAdminFileLifecycle test`
- 绿灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminUploadTicketShouldNotDeduplicateEmployeeFileByHashOnly test`
- 后台文件回归：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`
- 文件模块回归：`mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`
- 空白检查：`git diff --check`

### 后续边界

- 后台清理任务 `POST /api/admin/files/cleanup-jobs/run` 仍未实现，需要下一批补 dry-run、真实执行和物理对象清理统计
- `FileCenterService` 已继续膨胀，下一批清理任务前建议拆出文件查询、生命周期、票据、审计等内部服务或 Repository
- 细粒度后台权限点尚未接入，目前仍依赖后台管理员会话边界

---

## 2026-05-05 module-file 后台上传确认最小闭环

### 改动背景

后续知识库、文档和 Agent 文件能力都会依赖后台治理侧上传入口。员工端上传已经收口为 `PERSONAL_FILE`，不能继续拿员工入口承载后台文件，否则会把后台治理文件错误计入个人云空间，也会让业务归属授权边界变模糊。

### 红灯验证

新增自动化用例：

- `ModuleFileAdminApiIntegrationTest#adminShouldUploadConfirmAndDeduplicateFile`

红灯结果：

- `POST /api/admin/files/upload-tickets` 返回 `404`

这说明后台上传票据和确认上传接口尚未落地。

### 修订内容

新增：

- `server/src/main/resources/db/migration/V7__add_admin_file_upload_policy.sql`
- `server/src/test/java/com/workforcehub/file/ModuleFileAdminApiIntegrationTest.java`

修改：

- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 新增后台端 `POST /api/admin/files/upload-tickets`
- 新增后台端 `POST /api/admin/files/confirm-upload`
- 第一版后台上传仅允许 `ownerType=ADMIN_FILE` 且 `ownerId=null`
- 后台上传人写入 `uploader_type=ADMIN`，只计入企业总池 `GLOBAL`，不占员工个人云空间
- 新增 `ADMIN_FILE_DEFAULT_200M` 上传策略，后台治理文件默认单文件上限 `200MB`
- 后台确认上传复用物理去重、上传票据确认幂等和审计链路

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`
- 绿灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`

### 后续边界

- `ADMIN_FILE` 只是后台治理文件最小闭环，不等同于知识库文件或文档文件
- `KB_DOCUMENT`、`DOC_DOCUMENT` 等归属类型需等对应模块空间 ACL、权限校验和入库流程落地后再开放
- 后续应继续拆出上传策略服务，避免 `FileCenterService` 持续膨胀

---

## 2026-05-05 module-file 上传限制策略配置化

### 改动背景

前面文件中心已经跑通员工端个人文件上传、确认、秒传、下载、回收站和配额主链路，但“文件类型与大小限制”仍停留在契约层。如果不在申请上传票据阶段拦截，前端可以对 `.exe` 等高风险文件或超大文件拿到上传地址，后续会浪费存储资源，也会让不同业务场景共用一套模糊规则。

### 红灯验证

新增自动化用例：

- `uploadTicketShouldRejectUnsupportedFileType`
- `uploadTicketShouldRejectWhenFileExceedsSceneLimit`

红灯结果：

- `.exe + application/x-msdownload` 实际返回 `code=0`
- 超过 `100MB` 的 `.zip` 实际返回 `code=0`

这说明当前上传票据创建阶段没有读取任何文件类型或大小策略。

### 修订内容

新增：

- `server/src/main/resources/db/migration/V5__add_file_upload_policy.sql`

修改：

- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 新增 `sys_file_upload_policy`，按 `owner_type` 管理单文件大小、允许扩展名、允许 MIME 类型
- 内置 `PERSONAL_FILE_DEFAULT_100M` 策略，个人文件默认单文件上限 `100MB`
- 上传票据创建前先校验 `ownerType`，并优先按 `clientRequestId` 返回已签发票据；新票据才读取启用上传策略并校验大小、扩展名和 MIME
- 文件过大返回业务码 `413`，文件类型不允许返回业务码 `415`
- 唯一约束只限制同一 `ownerType` 同时存在一条启用策略，为后续后台配置页保留禁用和历史策略空间

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldRejectUnsupportedFileType,ModuleFileApiIntegrationTest#uploadTicketShouldRejectWhenFileExceedsSceneLimit test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 后续边界

- 当前只内置 `PERSONAL_FILE` 第一版策略，后台上传、群文件、审批附件、知识库、Agent 临时文件需要在各自授权模型落地后再补对应 `ownerType` 策略
- 还没有后台上传策略 CRUD；下一步可先做管理员查询和调整上传策略，再接前端配置页
- 上传限制策略只是元数据前置拦截，不能替代服务端内容识别、安全扫描或杀毒流程；后续需要在真实上传确认或异步扫描阶段补文件嗅探和扫描状态流转
- `FileCenterService` 继续增大，后续应拆出上传策略 Repository / Service，避免文件服务变成“万能抽屉”

### 第 23 审修订补充

第 23 审指出上传策略校验先于 `clientRequestId` 幂等回查，会导致已签发票据在策略禁用、调整或误删后重试失败。本轮补充回归测试并调整顺序：

- 已签发票据重复申请时，先按同一用户和同一 `clientRequestId` 回查原票据
- 只有新上传票据才执行当前上传策略校验
- 新增 `V6__align_file_upload_policy_owner_type_comment.sql`，将 `sys_file_upload_policy.owner_type` 列注释中的群文件 ownerType 示例统一为 `IM_GROUP_FILE`

补充验证：

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldReturnExistingTicketWhenPolicyChangesAfterFirstRequest test`

---

## 2026-05-04 module-file 第22审风险复核修订

### 改动背景

第 22 审复核的是第一批后端骨架风险。由于当前代码已经推进到第二批，部分问题已经被第二批覆盖，例如真实下载入口已经落地；但仍有几类问题在当前 `HEAD` 下属实：

- 员工端上传仍允许 `WF_REQUEST`、`IM_MESSAGE` 等跨业务归属类型
- 同一上传票据在异常状态或并发确认下可能重复生成附件引用并重复加账
- MySQL 唯一索引对 `scope_id = NULL` 无法约束全局配额和全局用量
- 集成测试重置没有清理 `sys_audit_log`，审计断言存在历史数据误报风险

### 红灯验证

新增测试覆盖：

- 员工端第一版拒绝非 `PERSONAL_FILE` 归属
- 已有关联附件引用时，即使上传票据状态异常回到待确认，再次确认也必须返回原附件引用
- 数据库层拒绝重复的 `GLOBAL` 空作用域配额策略
- 数据库层拒绝重复的 `GLOBAL` 空作用域用量账本
- 集成测试重置必须清理审计日志

红灯结果：

- `employeeUploadTicketShouldRejectBusinessOwnerType` 失败，实际返回 `code=0`
- `confirmUploadShouldBeIdempotentWhenTicketStatusIsStale` 失败，实际创建了第二条附件引用
- `databaseShouldRejectDuplicateNullScopeQuotaPolicy` 失败，重复 `GLOBAL` 配额策略未被数据库拒绝
- `databaseShouldRejectDuplicateNullScopeUsage` 失败，重复 `GLOBAL` 用量账本未被数据库拒绝
- `integrationTestResetShouldClearAuditLog` 失败，历史审计仍保留

### 修订内容

新增：

- `server/src/main/resources/db/migration/V4__harden_file_center_idempotency.sql`

修改：

- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`
- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`
- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

关键修订：

- 员工端上传和确认上传第一版只允许 `ownerType=PERSONAL_FILE` 且 `ownerId=null`
- 上传确认在校验前先按 `source_upload_ticket_id` 回查已有附件引用，避免异常状态下重复加账
- `sys_attachment_ref` 增加 `uk_attachment_upload_ticket_active`，数据库层保证同一上传票据只生成一条未删除附件引用
- `sys_storage_quota_policy` 和 `sys_storage_usage` 增加 `scope_key` 生成列，用 `__NONE__` 表达空作用域，并重建唯一索引
- `increaseUsage` 改为 `INSERT ... ON DUPLICATE KEY UPDATE`，避免用量行首次写入的先查再插并发窗口
- 上传票据创建捕获 `clientRequestId` 唯一冲突后回查已有票据，避免并发重试冒成 `500`
- 集成测试重置清理 `sys_audit_log`，审计断言不再被历史数据污染

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#employeeUploadTicketShouldRejectBusinessOwnerType,ModuleFileApiIntegrationTest#confirmUploadShouldBeIdempotentWhenTicketStatusIsStale,ModuleFileApiIntegrationTest#databaseShouldRejectDuplicateNullScopeQuotaPolicy,ModuleFileApiIntegrationTest#databaseShouldRejectDuplicateNullScopeUsage,ModuleFileApiIntegrationTest#integrationTestResetShouldClearAuditLog test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 后续边界

- `FileCenterService` 仍然偏大，下一批后台治理前建议继续拆 Repository 与 AuditService
- `visibility_scope='OWNER'` 暂时只服务个人文件；跨业务附件开放前必须先定义 ownerType 到授权策略的映射
- `uk_file_sha256_size_active` 的物理对象软删除语义留到清理任务阶段统一设计
- MinIO 仍需真实容器端到端联调

---

## 2026-05-04 module-file 第二批文件中心能力落地

### 改动背景

第一批已经跑通员工端上传、确认、秒传、下载票据、配额查询和回收站主链路，但还存在三个明显缺口：

- 下载票据只能生成短期地址，不能真正返回文件二进制
- `LOCAL` 存储逻辑直接写在 `FileCenterService` 中，后续接 `MINIO` 会继续堆大 Service
- 后台端只有存储摘要，管理员还不能通过接口维护用户默认配额或单人覆盖配额

本轮目标是补第二批后端能力，并保持“先测试、再实现、再审查”的节奏。

### 执行过程

#### 1. 先补红灯测试

修改：

- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`

新增测试覆盖：

- 有效下载票据可以下载原始文件字节
- 下载成功后票据状态更新为 `USED`
- 下载执行写入 `FILE_DOWNLOAD_EXECUTED` 审计
- 过期下载票据返回业务码 `410`
- 后台创建、查询、更新配额策略
- 配额策略阈值非法时拦截
- 配额低于当前已用量时拦截
- 同一作用域不能重复创建配额策略
- 相同 `policyCode` 不能重复创建配额策略，避免数据库唯一键异常冒成 `500`

红灯结果：

- `mvn -Dtest=ModuleFileApiIntegrationTest test`
- 新增 4 个用例按预期失败在 `404`
- 失败原因是 `GET /api/employee/files/download/{ticketNo}` 与后台配额策略接口尚未实现

**意义**：

- 先用测试把本轮要补的业务行为钉住，避免只做“看起来有代码”的空接口

#### 2. 抽象存储 Provider

新增：

- `server/src/main/java/com/workforcehub/file/storage/StorageProvider.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageProviderRegistry.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageUploadTarget.java`
- `server/src/main/java/com/workforcehub/file/storage/StorageObjectResource.java`
- `server/src/main/java/com/workforcehub/file/storage/LocalStorageProvider.java`
- `server/src/main/java/com/workforcehub/file/storage/MinioStorageProvider.java`

本轮 Provider 边界：

- `LOCAL` 负责本地上传目标、服务端 hash 校验、下载资源读取和后端剩余容量读取
- `MINIO` 提供预签名上传地址、对象校验和对象读取结构
- 普通自动化测试仍只依赖 `LOCAL`，不会要求 MinIO 服务在线

**意义**：

- 文件中心的业务 Service 不再直接依赖本地磁盘路径
- 后续切换 MinIO、OSS、WebDAV 时，可以沿 Provider 接口扩展

#### 3. 实现真实下载入口

修改：

- `server/src/main/java/com/workforcehub/file/controller/EmployeeFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`
- `server/src/main/java/com/workforcehub/file/dto/DownloadFileResult.java`

新增接口：

- `GET /api/employee/files/download/{ticketNo}`

接口行为：

- 校验当前用户必须是下载票据申请人
- 校验票据状态必须为 `ACTIVE`
- 校验票据未过期，过期时更新为 `EXPIRED` 并返回业务码 `410`
- 校验附件引用和物理文件对象未删除
- 通过 `StorageProvider` 读取文件资源
- 下载成功后把票据状态更新为 `USED`
- 写入 `FILE_DOWNLOAD_EXECUTED` 审计

**意义**：

- 文件下载从“只创建票据”推进到“短期票据真正可用”
- 后续消息附件、群文件、文档、审批附件都可以复用同一下载底座

#### 4. 实现后台配额策略 CRUD

新增：

- `server/src/main/java/com/workforcehub/file/dto/CreateQuotaPolicyRequest.java`
- `server/src/main/java/com/workforcehub/file/dto/UpdateQuotaPolicyRequest.java`
- `server/src/main/java/com/workforcehub/file/dto/QuotaPolicyResponse.java`

修改：

- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`

新增接口：

- `GET /api/admin/files/quota-policies`
- `POST /api/admin/files/quota-policies`
- `PUT /api/admin/files/quota-policies/{policyId}`

关键校验：

- `warningThreshold` 必须小于 `blockThreshold`
- `blockThreshold` 不得大于 `1.0`
- `quotaBytes` 不能低于当前作用域已用量
- `GLOBAL`、`USER_DEFAULT`、`TEMP_CACHE` 不允许传 `scopeId`
- `USER`、`DEPARTMENT`、`ROLE`、`GROUP`、`KNOWLEDGE_SPACE` 必须传 `scopeId`
- `status` 仅支持 `ENABLED`、`DISABLED`
- `policyCode` 必须唯一，重复时返回业务码 `409`
- 同一 `scopeType + scopeId` 只能保留一条未删除策略，显式规避 MySQL 唯一键遇到 `NULL` 时不能约束重复默认策略的问题

**意义**：

- 管理员可以通过后台接口维护默认配额和单人覆盖配额
- 配额治理开始从“写死种子数据”进入“后台可配置”阶段

#### 5. 同步 API 文档与测试用例

修改：

- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`
- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

同步内容：

- 已实现接口数量从第一批 `8` 个校准为第二批 `12` 个
- 补 `GET /api/employee/files/download/{ticketNo}` 文档
- 校准配额策略列表响应为数组结构
- 自动化覆盖清单加入下载执行、过期下载拦截和配额策略 CRUD
- 未覆盖清单移除“真实下载执行入口”和“配额策略 CRUD”

### 自审结果

| 角度 | 结论 | 后续建议 |
| --- | --- | --- |
| 功能完整性 | 第二批目标已覆盖：Provider 抽象、真实下载、配额策略 CRUD | 后台回收站治理、清理任务、后台上传仍需后续批次 |
| 数据一致性 | 下载票据执行后会更新为 `USED`，过期会更新为 `EXPIRED` | 后续可补重复使用下载票据的自动化测试 |
| 存储扩展性 | `FileCenterService` 已从直接碰磁盘改为通过 Provider 访问存储 | 后续建议继续拆 Repository，避免 Service 继续膨胀 |
| MinIO 边界 | 已有 Provider 结构和 SDK 编译校验 | 仍未接真实 MinIO 容器做端到端上传/下载联调 |
| 配额治理 | 已校验阈值、作用域、当前已用量、重复编码和同作用域重复创建 | 后续要把群空间、知识空间、部门策略的实际选择优先级补成测试 |
| 安全与审计 | 下载执行、配额策略创建和更新都有审计入口 | 后台配额策略接口后续需要细化角色权限，不应长期只依赖管理员会话 |

### 验证记录

- `mvn -DskipTests compile`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 当前未完成边界

- `MINIO` Provider 已编译接入，但还没有真实 MinIO 服务端到端联调
- 后台上传与确认上传未实现
- 后台回收站治理未实现
- 清理任务 dry-run 与执行未实现
- 文件类型、大小场景限制仍未配置化
- 并发去重、并发配额扣减、并发幂等还需要专项测试

### 下一步建议

下一批建议继续做：

1. 后台回收站治理接口
2. 清理任务 dry-run 与执行
3. 文件类型与大小限制配置化
4. MinIO 本地容器联调和可选测试入口
5. `FileCenterService` 仓储层拆分

---

## 2026-05-03 module-file 第一批后端骨架落地与自审修订

### 改动背景

上一轮已经把文件中心第一版范围、API 契约和测试用例收口。本轮开始进入代码落地，优先选择员工端主链路和后台存储摘要，而不是一次性把 17 个接口全部铺开。

这样做的原因是：

- 文件中心会被消息、文档、审批、知识库、Agent 共同依赖，第一步必须先把共享底座跑通
- 上传、确认、去重、配额、回收站这几条链路一旦语义不稳，后续业务模块都会被污染
- 后台配额策略、管理员回收站和清理任务可以在主链路稳定后继续做第二批

### 本次目标

- 新增文件中心 DDL 增量迁移
- 落地员工端上传票据、确认上传、秒传、下载票据、回收站、配额查询接口
- 落地后台端存储摘要查询接口
- 增加文件中心集成测试，按“到一个测试点就测试”的方式推进
- 自审并修复容量账本、幂等和上传归属一致性问题

### 执行过程

#### 1. 新增 V2 文件中心增强迁移

新增：

- `server/src/main/resources/db/migration/V2__enhance_file_center_schema.sql`

本迁移增强了：

- `sys_file_object`
- `sys_attachment_ref`
- `sys_upload_ticket`

并新增：

- `sys_storage_quota_policy`
- `sys_storage_usage`
- `sys_download_ticket`

**意义**：

- 让 V1 已有的文件对象、附件引用、上传票据具备第一版文件中心所需字段
- 用 `content_sha256 + file_size + is_deleted` 支撑物理去重
- 用独立的配额策略表和用量表承载个人空间、企业总池和临时缓存池

#### 2. 新增 V3 上传票据与附件引用幂等关联

新增：

- `server/src/main/resources/db/migration/V3__link_attachment_to_upload_ticket.sql`

本迁移给 `sys_attachment_ref` 增加 `source_upload_ticket_id`。

**意义**：

- 已确认上传票据重复确认时，可以准确返回原附件引用
- 避免前端重试确认时重复创建业务引用

#### 3. 新增文件中心控制器、DTO 和服务

新增：

- `server/src/main/java/com/workforcehub/file/controller/EmployeeFileController.java`
- `server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`
- `server/src/main/java/com/workforcehub/file/dto/*.java`
- `server/src/main/java/com/workforcehub/file/service/FileCenterService.java`

当前已实现接口：

- `POST /api/employee/files/upload-tickets`
- `POST /api/employee/files/confirm-upload`
- `GET /api/employee/files/quota`
- `POST /api/employee/files/{attachmentRefId}/download-tickets`
- `POST /api/employee/files/{attachmentRefId}/trash`
- `POST /api/employee/files/{attachmentRefId}/restore`
- `DELETE /api/employee/files/{attachmentRefId}`
- `GET /api/admin/files/storage-summary`

**意义**：

- 员工端文件中心主链路已经能通过 JSON API 调用
- 后台端可以先看到企业总池、缓存池、文件对象数、引用数、回收站数和待清理对象数
- 文件中心先以 `LOCAL` Provider 跑通自动化，`MINIO` 保留依赖和枚举口径，后续补真实实现

#### 4. 新增文件中心集成测试

新增：

- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`

测试覆盖：

- LOCAL 上传票据、确认上传、秒传、下载票据
- 回收站删除、恢复、彻底删除
- 个人配额不足拦截
- 恢复时企业总池不足拦截
- 彻底删除释放个人和企业用量
- 重复彻底删除不重复释放空间
- `clientRequestId` 重复申请上传票据幂等返回同一票据
- 确认上传不能篡改上传票据的业务归属
- 员工云空间和后台存储摘要查询

同步修改：

- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`

**意义**：

- 集成测试前置清理文件中心表，保证每个测试独立
- 通过真实 MySQL、Redis、Session、MockMvc 验证接口链路，不只测 Service 层

#### 5. 自审修订

本轮自审发现并修复了以下问题：

| 问题 | 修订结果 |
| --- | --- |
| 彻底删除业务引用后没有释放 `sys_storage_usage` | 新增 `releaseUsage`，彻底删除时释放个人与企业总池用量 |
| 回收站恢复只校验个人空间，没有校验企业总池 | 恢复时同时调用 `ensureGlobalQuota(0L)` |
| 重复彻底删除可能造成重复释放空间 | 对 `is_deleted=1` 的附件引用返回 `404`，避免二次扣减 |
| 重复 `clientRequestId` 申请上传票据会触发唯一键异常 | 申请前回查已有票据并直接返回 |
| 确认上传阶段可以篡改业务归属 | 确认时校验请求归属必须和上传票据归属一致 |

### 当前结果

- `module-file` 第一批后端骨架已经落到可运行代码
- 员工端文件中心主链路已具备自动化测试保护
- 后台端先具备存储摘要查询入口
- DDL 已从 V1 增量演进到 V3

### 当前未完成边界

- `MINIO` 真实 Provider 还未实现，当前自动化链路只跑 `LOCAL`
- 下载票据目前只生成短期入口，还没有实现真实下载执行接口
- 后台上传、后台确认上传、配额策略 CRUD、后台回收站、清理任务尚未实现
- 文件类型、大小场景限制还未配置化
- `FileCenterService` 当前用 `JdbcTemplate` 快速落地，后续建议拆 Repository 和 Provider 层
- `clientRequestId` 幂等目前覆盖顺序重试，并发重复请求还需要捕获唯一键冲突后回查

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest#uploadTicketShouldBeIdempotentByClientRequestId test`
- `mvn -Dtest=ModuleFileApiIntegrationTest#confirmUploadShouldRejectWhenOwnerDoesNotMatchTicket test`
- `mvn -Dtest=ModuleFileApiIntegrationTest test`

### 下一步建议

下一步建议继续做 `module-file` 第二批后端能力：

1. 抽象 `StorageProvider` 接口，补 `LocalStorageProvider` 和 `MinioStorageProvider`
2. 实现真实下载执行入口
3. 实现后台配额策略 CRUD
4. 实现后台回收站治理和清理任务
5. 将文件类型、大小限制配置化

---

## 2026-05-03 module-file 第一版开发范围与接口契约收口

### 改动背景

前一轮已经把文件存储与云空间设计补成了平台级架构文档，明确了：

- 第一版支持 `LOCAL` 和 `MINIO`
- 物理去重使用 `content_sha256 + file_size`
- 文件名不影响物理去重
- 个人空间、群空间、知识空间和企业总池要分层控制
- 回收站先删除业务引用，不立即删除物理对象
- Agent 读取文件必须通过业务授权上下文

但这些内容还停留在架构说明层。进入开发前，需要把它进一步拆成 `module-file` 第一版可执行范围、接口契约和测试点。

### 本次目标

- 明确 `module-file` 第一版必须做什么、不做什么
- 把文件上传、确认、秒传、配额、下载、回收站、清理任务拆成可开发流程
- 补员工端和后台端文件中心 API 文档
- 补企业级测试用例清单
- 为下一步 `V2` 数据库迁移和后端实现建立稳定依据

### 执行过程

#### 1. 新增第一版开发范围文档

新增：

- `docs/开发规划/module-file第一版开发范围.md`

文档中明确了第一版必须做：

- `LOCAL` / `MINIO` Provider
- 两段式上传
- 物理文件去重
- 业务引用创建
- 配额校验
- 下载票据
- 回收站
- 延迟清理
- 访问审计

同时明确第一版暂不做：

- 在线预览与转码
- 防病毒真实扫描
- 水印
- WebDAV
- OSS/COS/S3
- Agent 流程执行
- 公网匿名永久分享

**意义**：

- 让第一版文件中心保持克制，不一开始就变成完整网盘工程
- 先把后续模块都会依赖的共享底座做稳

#### 2. 新增文件中心 API 文档

新增：

- `docs/API与测试/module-file/API文档_module-file文件中心接口.md`

本次覆盖 `17` 个接口：

- 员工端上传票据、确认上传、配额查询、下载票据、回收站、恢复、彻底删除
- 后台端上传票据、确认上传、存储摘要、配额策略、回收站治理、清理任务

**意义**：

- 后续后端实现不需要临时猜请求体和响应体
- 前端和测试可以提前按接口契约准备联调

#### 3. 新增文件中心测试用例

新增：

- `docs/API与测试/module-file/API测试用例_module-file文件中心接口.md`

测试用例覆盖：

- 上传票据
- 上传确认与去重
- 配额查询
- 下载票据
- 回收站
- 后台配额治理
- 后台回收站与清理
- 鉴权与契约

**意义**：

- 后续进入代码实现时，可以做到“到一个测试点就开始测试”
- 文件中心最容易出问题的并发去重、配额复核、回收站恢复都有明确用例

#### 4. 同步文档索引

同步更新：

- `docs/README.md`
- `docs/API与测试/README.md`
- `docs/开发日志/README.md`
- `docs/审查/README.md`
- `docs/架构与设计/接口与事件清单.md`

**意义**：

- 新增文档可以通过总索引和模块索引被快速找到
- 系统级接口清单能正确指向详细 API 文档和测试用例

### 当前结果

- `module-file` 第一版从“架构设计”推进到了“可开发契约”
- 接口与测试点已经具备后续实现依据
- 下一步适合进入数据库迁移设计和后端代码落地

### 下一步建议

下一步建议新建 `V2__enhance_file_center_schema.sql`，不要直接改已经存在的 `V1__init_schema.sql`。

优先落地顺序：

1. 增强 `sys_file_object`
2. 增强 `sys_attachment_ref`
3. 增强 `sys_upload_ticket`
4. 新增 `sys_storage_quota_policy`
5. 新增 `sys_storage_usage`
6. 新增 `sys_download_ticket`
7. 补文件中心集成测试骨架

### 已知待补项

- 当前仍是开发范围和接口契约，尚未修改 DDL 与后端代码
- MinIO 本地联调配置还未写入工程配置
- 并发去重、配额扣减一致性需要在实现阶段用数据库约束和事务测试验证

## 2026-05-05 第24审后台秒传幂等修复

### 背景

第24审指出后台上传命中秒传时直接创建 `sys_attachment_ref`，但没有创建可被 `clientRequestId` 回查的上传票据，导致同一管理员重复提交同一个秒传请求时，会重复创建附件引用并重复增加 `GLOBAL` 用量。

### 本次处理

- 秒传命中已有 `sys_file_object` 时，先创建一张 `status=CONFIRMED` 的 `sys_upload_ticket`
- 秒传创建的 `sys_attachment_ref.source_upload_ticket_id` 绑定这张已确认票据
- `clientRequestId` 幂等回查如果发现票据已有附件引用，则返回同一张秒传票据和同一附件引用
- 秒传路径捕获唯一键冲突后回查已有结果，避免重复请求或并发重试冒成重复加账
- 后台集成测试新增重复 `clientRequestId` 秒传用例，断言同一票据、同一附件引用、同一用量账本结果
- API 文档和测试用例同步说明“秒传也会生成已确认票据”的第一版契约

### 验证记录

- `mvn -Dtest=ModuleFileAdminApiIntegrationTest#adminDeduplicatedUploadShouldBeIdempotentByClientRequestId test`：通过
- `mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`：通过

### 意义

- 后台秒传从“直接建引用”收口为“票据驱动的幂等结果”
- 后续后台文件下载、删除、审计追踪时，可以统一从上传票据追溯来源
- 员工端与后台端共用秒传幂等回查逻辑，减少两个入口行为分叉

### 文档边界校准

- API 文档明确 `ADMIN_FILE` 只是后台治理文件第一版归属，不等同于 `KB_DOCUMENT` 或 `DOC_DOCUMENT`
- 知识库、文档、群文件、Agent 文件仍需等待各自业务模块 ACL 与入库流程落地后再开放
- 重复确认当前采用宽松幂等：以 `uploadTicketId` 已生成的附件引用为准返回，不用当前请求体重建引用
- 后台上传确认闭环不等于完整后台治理闭环，后台文件列表、下载、回收站、彻底删除和用量释放仍归入后续生命周期开发

## 2026-05-05 第26审后台生命周期并发与响应边界修复

### 背景

第26审指出后台文件生命周期闭环已经可用，但状态机在并发下还不够硬：下载票据和彻底删除都存在“先读状态、后条件更新但不看影响行数”的问题；后台列表默认暴露对象存储内部字段；员工端和后台端下载响应构造重复，非法 MIME 会在 Controller 层抛 500。

### 本次处理

- 下载执行改为先通过 `UPDATE sys_download_ticket ... WHERE status='ACTIVE'` 抢占票据，影响行数为 0 时返回 `code=409`
- 员工端和后台端共用同一套下载票据抢占逻辑，避免只修后台而员工端遗留同类风险
- 员工端和后台端彻底删除都改为条件更新成功后才释放用量、重算引用数和写成功审计
- 移入回收站、恢复增加条件更新结果处理，避免并发交错时“未改到行但返回成功审计”的状态漂移
- 后台文件列表默认移除 `contentSha256`、`storageProvider`、`bucketName`、`objectKey` 等存储内部字段
- 新增 `FileDownloadResponseFactory`，统一下载响应头构造；非法 MIME 降级为 `application/octet-stream`
- 新增并发下载、并发彻底删除、后台列表字段最小化、非法 MIME 降级测试

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`，新增 4 个用例中并发下载、列表字段、非法 MIME、并发彻底删除均能复现旧实现问题
- 绿灯：`mvn -Dtest=ModuleFileAdminApiIntegrationTest test`，8 个后台文件用例通过

### 意义

- 下载票据从“读到 ACTIVE 就可能返回文件”收口为“抢到 ACTIVE 才能返回文件”
- 彻底删除从“可能重复释放账本”收口为“状态真正变更后才执行副作用”
- 后台默认响应面更克制，避免 MinIO 或对象存储路径在普通治理列表里泄露
- 下载响应构造集中到一个组件，后续加范围下载、预览、缓存头时不用员工端和后台端双改

## 2026-05-05 员工端并发镜像测试与服务小拆分

### 背景

第26审修复后，后台端已经有并发下载和并发彻底删除测试兜底；员工端代码虽然共用了同一套修复逻辑，但缺少镜像测试。与此同时，`FileCenterService` 继续承载票据状态机、用量账本、上传、下载、生命周期和配额治理，后续继续叠功能会越来越难维护。

### 本次处理

- 员工端新增同一下载票据并发执行测试，断言仅一个请求能返回文件，另一个返回 `code=409`
- 员工端新增同一附件并发彻底删除测试，断言 `USER` 与 `GLOBAL` 用量只释放一次
- 抽出 `DownloadTicketExecutionService`，集中处理下载票据 `ACTIVE -> USED` 抢占和过期标记
- 抽出 `StorageUsageLedgerService`，集中处理 `sys_storage_usage` 的用量读取、增加和释放
- `FileCenterService` 保留业务编排职责，不再直接承载下载票据执行和用量账本 SQL
- 校准第 25 审的真实审查提交 hash，补齐第 26 审指出的审查追溯残留
- 校准 API 测试用例编号，避免后台彻底删除并发用例与后台移入回收站用例共用同一个 ID

### 验证记录

- `mvn -Dtest=ModuleFileApiIntegrationTest test`：24 个员工端文件模块用例通过
- `mvn -Dtest=ModuleFileApiIntegrationTest#employeeDownloadTicketShouldBeClaimedOnceUnderConcurrency,ModuleFileApiIntegrationTest#employeePurgeShouldReleaseUsageOnlyWhenStateChangedUnderConcurrency test`：2 个员工端并发镜像用例通过
- `git diff --check`：无空白错误
- 测试用例 ID 去重检查：无重复 `FILE-*` 用例 ID
- `mvn -Dtest=ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`：32 个文件模块用例通过

### 意义

- 员工端和后台端的关键并发语义都有自动化测试兜底
- 下载票据状态机和用量账本从大服务中剥离，后续拆 `FileLifecycleService`、清理任务或更多 ownerType 时不需要继续往一个类里堆 SQL
- 这次只做小拆分，不改接口契约和表结构，降低对当前文件中心链路的扰动
- 审查文件、开发日志、API 文档和测试用例编号保持一致，后续按审查轮次回溯时不会断链

## 2026-05-05 后台清理任务第一版

### 背景

`module-file` 第一版还缺后台清理任务。此前手动彻底删除只会把无引用物理对象标记为 `cleanup_after` 候选，过期上传票据和过期下载票据也缺少统一回收入口。为了避免继续扩大 `FileCenterService`，本轮按方案 A 单独拆出清理任务服务。

### 本次处理

- 新增 `POST /api/admin/files/cleanup-jobs/run` 后台接口
- 新增 `RunCleanupJobRequest` 与 `CleanupJobRunResponse`
- 新增 `FileCleanupJobService`，集中处理过期上传票据、过期下载票据、到期回收站引用和无引用物理对象
- `dryRun=true` 只统计候选数量和预计释放字节，不修改业务数据或物理对象
- 真实执行会将过期上传票据标记为 `EXPIRED` 并清理未确认临时对象
- 真实执行会将过期下载票据从 `ACTIVE` 标记为 `EXPIRED`
- 真实执行第一版只处理 `PERSONAL_FILE` / `ADMIN_FILE` 到期回收站引用，并按对应空间释放用量
- 无引用物理对象采用两阶段清理：到期回收站引用被清理后，物理对象先进入 `cleanup_after` 候选；后续到期后才删除二进制并标记 `sys_file_object.is_deleted=1`
- `LOCAL` 和 `MINIO` Provider 均补充 `deleteObject` 接口
- 新增 `ModuleFileCleanupIntegrationTest`，覆盖 dry-run、真实执行和审计

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest test`，失败于 `/api/admin/files/cleanup-jobs/run` 返回 `404`
- 绿灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest test`，2 个清理任务用例通过

### 意义

- 清理任务从文件中心主服务中解耦，后续定时调度或任务运行表可以围绕 `FileCleanupJobService` 扩展
- 文件中心形成“上传、确认、下载、回收站、彻底删除、物理清理”的闭环
- 两阶段物理清理给误删恢复、并发插入和对象存储异常留出缓冲，默认比到期引用清理后立刻删二进制更稳

## 2026-05-05 清理任务两阶段与运行记录

### 背景

第 29 审指出后台清理任务第一版把数据库状态更新和对象存储删除放在同一个事务方法里。对象存储删除属于外部副作用，数据库回滚无法恢复已经删除的二进制对象；同时任务缺少运行记录和失败明细，中途失败只能靠应用日志追踪。

### 本次处理

- 新增 `sys_file_cleanup_job_run`，记录清理任务运行编号、类型、状态、统计值、开始/结束时间和执行管理员
- 新增 `sys_file_cleanup_job_detail`，记录每个待物理删除对象的 Provider、bucket、objectKey、处理状态、尝试次数和失败原因
- `FileCleanupJobService` 改为两阶段边界：事务内先标记上传票据、下载票据、回收站引用、无引用文件对象，并写入清理明细；事务提交后再逐条执行对象存储删除
- 物理删除失败不再让接口整体返回 `500`，而是回写明细失败并返回 `jobStatus=PARTIAL_FAILED`
- `CleanupJobRunResponse` 增加 `jobStatus` 与 `failedPhysicalObjects`
- 集成测试补充成功明细断言和物理删除失败可追踪场景

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest#cleanupExecuteShouldExpireTicketsPurgeRefsDeleteObjectsAndAudit,ModuleFileCleanupIntegrationTest#cleanupPhysicalDeleteFailureShouldBeRecordedWithoutRollingBackRun test`，失败于响应缺少 `jobStatus`，以及对象存储 Provider 不存在时接口返回 `code=500`
- 绿灯：同一命令通过，2 个清理任务运行记录用例通过
- 回归：`mvn -Dtest=ModuleFileCleanupIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest test`，36 个用例通过

### 意义

- 清理任务从“同步脚本”升级为“可观测、可恢复作业”
- 数据库状态和对象存储删除的边界更清楚，失败后能按 `jobRunId` 和明细追踪
- 后续接定时调度、失败重试、分页游标时，不需要推翻当前清理模型

## 2026-05-06 第30审清理任务失败恢复闭环

### 背景

第30审指出清理任务虽然已经能记录物理删除失败明细，但失败明细只停留在 `FAILED`，普通清理任务不会再次消费；同时 `deletedPhysicalObjects` 只统计无引用文件对象成功数，和失败数统计所有物理对象失败数的口径不一致；数据库阶段异常也会让任务运行记录随事务回滚，`error_message` 没有写入路径。

### 本次处理

- 新增清理任务类型 `FAILED_PHYSICAL_OBJECT`，复用 `POST /api/admin/files/cleanup-jobs/run` 作为失败物理对象重试入口。
- 失败重试采用“复制最新失败明细到本次任务”的方式执行，旧失败记录保留作历史审计，不直接改写。
- 重试候选限定为同一 `storageProvider + bucketName + objectKey` 的最新明细仍为 `FAILED` 的对象；如果后续已有 `SUCCESS` 或新的 `PENDING` 明细覆盖，则不再重复重试。
- 清理任务运行记录改为先在事务外创建；数据库阶段异常时保留同一个 `jobRunId`，并回写 `jobStatus=FAILED`、`errorMessage`、`finishedAt`。
- `deletedPhysicalObjects` 统一为“本次所有物理删除成功明细数”，不再只统计 `UNREFERENCED_FILE_OBJECT`。
- `dryRun=true` 时，过期上传票据的临时对象也计入预计 `deletedPhysicalObjects`，和真实执行的物理删除规模保持一致。
- `CleanupJobRunResponse` 增加 `errorMessage`，API 文档和测试用例同步失败重试、数据库阶段失败与统计口径。
- 审计记录的 `result` 按任务结果区分 `SUCCESS` / `FAIL`，审计详情同步记录 `errorMessage`。

### 验证记录

- 红灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest#cleanupFailedPhysicalDeleteShouldBeRetried,ModuleFileCleanupIntegrationTest#cleanupDatabaseStageFailureShouldRecordFailedRun,ModuleFileCleanupIntegrationTest#cleanupDryRunShouldOnlyReportCandidates,ModuleFileCleanupIntegrationTest#cleanupExecuteShouldExpireTicketsPurgeRefsDeleteObjectsAndAudit test`，失败于 `FAILED_PHYSICAL_OBJECT` 不支持、统计口径仍为 1、数据库阶段异常返回 500。
- 绿灯：同一命令通过，4 个清理任务关键用例通过。
- 红灯：`mvn -Dtest=ModuleFileCleanupIntegrationTest#cleanupFailedPhysicalDeleteShouldBeRetried test`，追加“已成功覆盖的旧失败明细不应继续 dry-run 计数”后失败于 `deletedPhysicalObjects=1`。
- 绿灯：同一命令通过，确认已成功覆盖的旧失败明细不再重复进入候选。
- 红灯：同一用例追加“两次失败后第三次成功”后失败于同一对象被复制成两条成功明细，`deletedPhysicalObjects=2`。
- 绿灯：同一命令通过，确认同一物理对象只取最新失败明细重试。

### 意义

- 清理任务从“可观测失败”补成“可恢复失败”，一次 MinIO、权限或网络抖动不会把对象永久卡在人工查表处理。
- 运行记录覆盖数据库阶段失败和物理删除阶段失败，排障时能用 `jobRunId` 把接口响应、任务表、明细表和审计串起来。
- 成功数和失败数口径统一为物理删除明细，后台响应更容易被运维解释。
- 当前仍未引入定时调度、最大重试次数和退避时间；这些可以在清理任务生产化阶段基于现有运行表继续扩展。
