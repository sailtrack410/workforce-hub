# 第 24 审 module-file 后台上传确认闭环审查

- 审查日期：2026-05-05
- 审查提交：`07aa6d74c17e8b7572dd49fc1063fc57d9353457`（实现module-file后台上传确认闭环）
- 审查范围：`server/src/main/java/com/workforcehub/file/controller/AdminFileController.java`、`server/src/main/java/com/workforcehub/file/service/FileCenterService.java`、`server/src/main/resources/db/migration/V7__add_admin_file_upload_policy.sql`、`server/src/test/java/com/workforcehub/file/ModuleFileAdminApiIntegrationTest.java`、`docs/API与测试/module-file/`、`docs/开发日志/module-file.md`
- 审查目标：在不修改业务代码的前提下，复核后台上传确认闭环的正确性风险、冗余实现和可维护性隐患，避免后台文件能力继续以补丁叠加方式扩张。

---

## 第一部分：提交内容概览

本次提交完成了以下事项：

- 后台端新增 `POST /api/admin/files/upload-tickets`。
- 后台端新增 `POST /api/admin/files/confirm-upload`。
- 第一版后台上传仅允许 `ownerType=ADMIN_FILE` 且 `ownerId=null`。
- 后台上传写入 `uploader_type=ADMIN`，只增加企业总池 `GLOBAL` 用量，不占员工个人云空间。
- 新增 `ADMIN_FILE_DEFAULT_200M` 上传策略。
- 新增 `ModuleFileAdminApiIntegrationTest` 覆盖后台上传、确认和秒传主链路。

本次验证记录：

- `mvn -q -DskipTests compile`：通过。
- `mvn -q -Dtest=ModuleFileAdminApiIntegrationTest,ModuleFileApiIntegrationTest test`：通过。

---

## 第二部分：风险审查

### 一、后台文件生命周期

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `AdminFileController` + `FileCenterService` | 本次实现能创建 `uploader_type=ADMIN` 的附件引用并增加 `GLOBAL` 用量，但后台端还没有查询、下载、回收站删除、彻底删除或清理这些 `ADMIN_FILE` 引用的接口。当前形成的是“可写入、可计量、不可治理”的最小链路，后续如果持续产生后台文件，企业总池用量缺少释放路径。 | 重大 | 在继续扩大后台上传场景前，应补后台文件列表、下载、回收站、彻底删除和用量释放口径，或在文档中明确 `ADMIN_FILE` 仅为临时闭环验证能力。 |
| 1-2 | `createAdminUploadTicket` 秒传分支 | 命中已有物理对象时直接创建后台附件引用，并且 `source_upload_ticket_id = null`。如果同一个 `clientRequestId` 重复请求秒传路径，前置幂等回查找不到上传票据，会再次创建附件引用并重复增加 `GLOBAL` 用量。后台新增链路复用了员工秒传问题，但后台文件更容易被批量治理任务重复调用。 | 重大 | 秒传路径也需要幂等记录，或者按 `clientRequestId` 记录一条已完成票据/幂等表，确保重复请求不会重复创建引用和用量。 |
| 1-3 | `findActiveFileObject` + 后台秒传 | 后台秒传只依赖 `contentSha256 + fileSize`，没有证明管理员确实持有该文件内容，也没有校验已有对象来源是否允许被后台治理引用。管理员只要知道 hash 和大小，就能把系统中已有物理对象挂成 `ADMIN_FILE` 引用。当前后台暂未提供下载，风险暂时被限制；一旦后续补后台下载，可能变成跨主体文件读取入口。 | 重大 | 秒传应设计证明持有或权限校验机制；至少在后台下载能力落地前，明确后台引用已有对象是否允许跨上传主体复用。 |

### 二、权限与治理边界

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `/api/admin/files/upload-tickets` | 当前只要是后台登录态即可创建后台上传票据，没有细分后台角色或权限点。配额策略接口也采用同样路径级鉴权，但上传文件会产生长期存储和审计对象，权限影响更大。 | 重要 | 后续接 RBAC 时，应为后台上传、确认、下载、删除分别定义权限点，避免所有管理员都拥有文件治理写入能力。 |
| 2-2 | `validateAdminOwner` | 第一版只允许 `ADMIN_FILE`，这有助于避免提前开放 `KB_DOCUMENT`、`DOC_DOCUMENT`。但 API 文档称“后台上传确认已实现”，容易让调用方误以为知识库和文档后台上传已可直接使用。 | 建议 | 文档和接口说明应持续强调：`ADMIN_FILE` 不等同于知识库文件或文档文件，业务模块接入前仍需各自 ACL 和入库流程。 |

### 三、幂等与状态一致性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `confirmAdminUpload` | 重复确认时若已存在附件引用，会直接 `markUploadTicketConfirmed` 并返回原附件，但不会再次校验本次请求体的 `objectKey`、hash、大小和归属是否与票据一致。这与员工端现状一致，作为宽松幂等可运行，但也可能掩盖客户端把错误确认参数打到已确认票据上的问题。 | 建议 | 如果接口希望严格幂等，应要求重复确认请求体仍与原票据一致；如果希望宽松幂等，应在 API 文档中明确以 `uploadTicketId` 为准。 |
| 3-2 | `findUploadTicketByClientRequestId` | 幂等回查会按已有票据重新构造上传目标，但返回的 `expireAt` 是数据库中的旧过期时间。对真实预签名 Provider 而言，重新构造的 `uploadUrl` 与旧 `expireAt` 可能出现语义不一致；后台链路引入后，该共享方法影响员工和后台两端。 | 建议 | 后续接 MinIO 前，需要明确幂等重试是否复用原 URL，还是重新签发并同步更新过期时间。 |

### 四、迁移与策略种子

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `V7__add_admin_file_upload_policy.sql` | V7 直接插入固定 `id=5002` 和固定 `policy_code`。对全新库没有问题，但如果某个环境在 V5 后已经手工补过 `ADMIN_FILE` 策略，迁移会因唯一键冲突失败。 | 建议 | 如果允许环境手工补策略，应采用可重复的治理脚本或迁移前数据检查；如果不允许手工补策略，应在运维约束中明确。 |
| 4-2 | `ADMIN_FILE_DEFAULT_200M` | 后台默认策略与个人策略复制了同一套扩展名和 MIME 白名单，只调整大小上限。短期减少差异，但后台治理文件和个人文件的风险模型不同，长期复用同一白名单容易让策略演进互相牵制。 | 建议 | 后续上传策略服务拆分时，应为后台治理文件单独维护策略意图，不建议长期复制粘贴个人文件白名单。 |

### 五、测试覆盖

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 5-1 | `ModuleFileAdminApiIntegrationTest` | 新增测试只覆盖后台成功上传、确认和秒传正向路径。尚未覆盖非管理员访问、错误 `ownerType`、`ownerId` 非空、策略拒绝、企业总池不足、重复 `clientRequestId`、重复确认、过期票据和员工不能确认后台票据等风险点。 | 重要 | 后续应补后台文件接口负向矩阵测试，尤其优先覆盖秒传幂等和权限隔离。 |
| 5-2 | `ModuleFileAdminApiIntegrationTest` | 测试断言后台上传不占员工 `USER` 用量，但没有断言后台附件后续是否可下载、可删除或可释放用量。当前测试名称写“闭环”，实际覆盖的是上传确认闭环，不是后台文件治理完整闭环。 | 建议 | 测试命名或文档应区分“上传确认闭环”和“治理闭环”，避免阶段性能力被误解为完整后台文件中心。 |

---

## 第三部分：冗余与可维护性审查

### 一、员工端与后台端重复实现

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `createEmployeeUploadTicket` / `createAdminUploadTicket` | 后台上传票据方法基本复制员工上传票据流程，只替换上传人类型、归属校验和用量口径。重复代码已经覆盖票据插入、秒传、审计、Provider 目标生成和幂等冲突回查，后续修一个边界很容易漏另一个入口。 | 重要 | 建议抽出共享上传票据编排方法，用调用方上下文传入 `uploaderType`、归属校验和用量策略。 |
| 6-2 | `confirmEmployeeUpload` / `confirmAdminUpload` | 后台确认上传同样复制员工确认上传流程，只调整上传人类型和用量扣增口径。后续如果修确认幂等、扫描状态、策略快照或异常处理，需要同步改两份代码。 | 重要 | 建议抽出确认上传模板方法，把差异收敛为上下文参数或策略对象。 |
| 6-3 | `FileCenterService` | 本次继续向 `FileCenterService` 追加后台上传、确认、策略和测试相关逻辑。该类已同时承载员工上传、后台上传、下载、回收站、配额、策略、审计和 Provider 编排，维护成本继续上升。 | 重要 | 下一批继续开发前，建议先拆 `UploadTicketService`、`UploadConfirmService`、`UploadPolicyService`、`StorageUsageService`，避免主服务变成单点复杂度。 |

---

## 第四部分：审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 3 | 1-1、1-2、1-3 |
| 重要 | 5 | 2-1、5-1、6-1、6-2、6-3 |
| 建议 | 7 | 2-2、3-1、3-2、4-1、4-2、5-2 |
| 合计 | 15 |  |

---

## 第五部分：处理优先级建议

**第一优先级（后台上传继续扩展前先处理）：**

1. 明确 `ADMIN_FILE` 的后续治理路径：列表、下载、回收站、彻底删除、用量释放。
2. 修正后台秒传路径的幂等语义，避免重复 `clientRequestId` 重复创建引用和用量。
3. 明确跨主体物理对象秒传是否允许，避免后台下载落地后形成隐性文件读取入口。
4. 为后台上传和确认补权限点，不要长期只依赖后台登录态。

**第二优先级（减少补丁叠加）：**

- 抽出上传票据与确认上传的共享编排，消除员工端/后台端重复代码。
- 补后台上传负向测试矩阵。
- 将 `ADMIN_FILE` 上传策略从复制白名单演进为独立策略配置。

---

## 第六部分：阶段性结论

本次提交让后台端具备了 `ADMIN_FILE` 上传票据、确认上传和秒传的最小可运行链路，且当前编译与文件模块集成测试均通过。

但这还不是完整后台文件治理闭环。当前最大风险是后台文件可以写入并占用企业总池，但缺少后台下载、删除和用量释放能力；同时后台秒传路径没有真正记录幂等票据，重复请求会重复创建附件引用。下一步应先收口后台文件生命周期和上传/确认的公共编排，再继续扩展知识库、文档或 Agent 文件接入。

