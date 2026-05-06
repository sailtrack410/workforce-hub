# API 文档 module-file 文件中心接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-file` 文件中心接口 API 文档 |
| 文档版本 | `v1.0` |
| 适用范围 | 两段式上传、文件去重、配额查询、下载票据、回收站、后台配额治理 |
| 对应模块 | `server/module-file` |
| 发布日期 | `2026-05-03` |
| 当前状态 | 契约已收口；员工端主链路、真实下载入口、后台 `ADMIN_FILE` 上传确认、后台文件生命周期治理、后台存储摘要、配额策略 CRUD、上传限制策略已完成后端实现 |

---

## 二、接口范围

本文档覆盖以下 `22` 个接口。

当前已实现 `22` 个接口：员工端 `8` 个接口（含真实下载执行入口），后台端 `14` 个接口（后台上传确认、后台文件生命周期治理、清理任务、存储摘要、配额策略 CRUD）。

### 1. 员工端

- `POST /api/employee/files/upload-tickets`
- `POST /api/employee/files/confirm-upload`
- `GET /api/employee/files/quota`
- `POST /api/employee/files/{attachmentRefId}/download-tickets`
- `GET /api/employee/files/download/{ticketNo}`
- `POST /api/employee/files/{attachmentRefId}/trash`
- `POST /api/employee/files/{attachmentRefId}/restore`
- `DELETE /api/employee/files/{attachmentRefId}`

### 2. 后台端

- `GET /api/admin/files`（已实现）
- `GET /api/admin/files/recycle-bin`（已实现）
- `POST /api/admin/files/upload-tickets`（已实现）
- `POST /api/admin/files/confirm-upload`（已实现）
- `POST /api/admin/files/{attachmentRefId}/download-tickets`（已实现）
- `GET /api/admin/files/download/{ticketNo}`（已实现）
- `POST /api/admin/files/{attachmentRefId}/trash`（已实现）
- `POST /api/admin/files/{attachmentRefId}/restore`（已实现）
- `DELETE /api/admin/files/{attachmentRefId}`（已实现）
- `GET /api/admin/files/storage-summary`（已实现）
- `GET /api/admin/files/quota-policies`（已实现）
- `POST /api/admin/files/quota-policies`（已实现）
- `PUT /api/admin/files/quota-policies/{id}`（已实现）
- `POST /api/admin/files/cleanup-jobs/run`

---

## 三、调用约定

### 3.1 基础地址

开发环境默认：

```text
http://localhost:8080
```

### 3.2 鉴权方式

当前项目采用服务端 Session 鉴权。

- 员工端接口必须使用员工会话
- 后台端接口必须使用后台管理员会话
- 后台配额治理接口要求系统管理员或具备文件治理权限
- 知识库、文档、群文件、Agent 文件暂未通过文件中心后台上传入口开放，需等待对应业务模块的 ACL 与入库流程落地

### 3.3 请求头约定

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | `POST/PUT` 时是 | `application/json` | 请求体类型 |
| `Cookie` | 是 | `SESSION=...` | 会话维持 |
| `X-Request-Id` | 否 | `file202605030001` | 请求链路 ID |
| `Idempotency-Key` | 写接口建议 | `upload-ticket-20260503-001` | 创建票据、确认上传、删除恢复等幂等控制 |

### 3.4 响应体格式

统一响应结构：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

### 3.5 时间与容量单位

| 类型 | 约定 |
| --- | --- |
| 日期时间 | ISO 8601 带时区字符串 |
| 文件大小 | 字节数，字段名统一使用 `*Bytes` 或 `fileSize` |
| 链接有效期 | 秒数或明确过期时间 |

### 3.6 关键枚举

#### storageProvider

| 值 | 含义 |
| --- | --- |
| `LOCAL` | 本地磁盘 |
| `MINIO` | MinIO 对象存储 |

#### ownerType

第一版员工端上传仅开放 `PERSONAL_FILE`，后台端仅开放 `ADMIN_FILE`。其他 `ownerType` 作为跨模块附件归属枚举保留，需等消息、文档、审批、知识库或 Agent 模块提供归属存在性与授权校验后，再通过专门入口开放。

| 值 | 含义 |
| --- | --- |
| `PERSONAL_FILE` | 个人文件，员工端第一版已开放 |
| `ADMIN_FILE` | 后台治理文件，后台端第一版已开放；不等同于知识库文件或文档文件 |
| `IM_MESSAGE` | 消息附件，契约保留，待消息授权策略接入 |
| `IM_GROUP_FILE` | 群文件，契约保留，待群组授权策略接入 |
| `DOC_DOCUMENT` | 文档文件，契约保留，待文档空间授权策略接入 |
| `KB_DOCUMENT` | 知识库原文件，契约保留，待知识库权限策略接入 |
| `WF_REQUEST` | 审批附件，契约保留，待审批单授权策略接入 |
| `AGENT_TEMP` | Agent 临时产物，契约保留，待 Agent 工具权限策略接入 |

#### uploadPolicy

员工端申请上传票据前会按 `ownerType` 读取 `sys_file_upload_policy` 中启用的场景策略，先校验单文件大小，再校验扩展名和 MIME 类型。

该策略属于上传票据申请前的元数据前置拦截，只能阻断明显不符合场景的文件名、扩展名和 MIME 声明，不能替代服务端内容识别、安全扫描或杀毒流程。真实安全边界应在确认上传后结合对象内容嗅探、扫描状态和异步处置流程继续收口。

第一版内置策略：

| ownerType | 策略编码 | 单文件上限 | 说明 |
| --- | --- | --- | --- |
| `PERSONAL_FILE` | `PERSONAL_FILE_DEFAULT_100M` | `104857600` 字节，约 `100MB` | 允许常见文档、图片、文本、压缩包；不允许可执行文件等高风险类型 |
| `ADMIN_FILE` | `ADMIN_FILE_DEFAULT_200M` | `209715200` 字节，约 `200MB` | 后台治理文件最小闭环策略；不代表知识库或文档空间上传策略 |

后续接入群文件、审批附件、知识库原文件、文档空间文件、Agent 临时产物时，应为对应 `ownerType` 单独配置策略，避免所有场景共用同一套限制。

#### deduplicateProof

第一版物理去重口径仍是 `content_sha256 + file_size`，但申请上传票据阶段的“秒传”不能被理解为只要知道 hash 和大小就能直接挂载已有对象。

- 同一上传主体已经拥有相同内容的有效附件引用时，申请上传票据可直接生成已确认票据和新业务引用，返回 `deduplicated=true`。
- 跨上传主体发现相同物理对象时，不在申请阶段直接秒传；调用方仍需拿到上传票据并实际上传对象。
- 确认上传阶段会重新读取并校验对象内容；校验通过后才允许复用已有物理对象。
- 这个口径用于避免后台或员工仅凭他人文件的 hash 和大小直接创建可下载引用。

#### scopeType

| 值 | 含义 |
| --- | --- |
| `GLOBAL` | 企业总池 |
| `USER_DEFAULT` | 用户默认配额 |
| `USER` | 单个用户覆盖配额 |
| `DEPARTMENT` | 部门策略 |
| `ROLE` | 角色策略 |
| `GROUP` | 群空间 |
| `KNOWLEDGE_SPACE` | 知识空间 |
| `TEMP_CACHE` | 临时缓存池 |

#### uploadTicketStatus

| 值 | 含义 |
| --- | --- |
| `CREATED` | 已创建，等待上传 |
| `UPLOADED` | 对象已上传，等待确认 |
| `CONFIRMED` | 已确认并创建引用 |
| `EXPIRED` | 已过期 |
| `CANCELLED` | 已取消 |

#### downloadTicketStatus

| 值 | 含义 |
| --- | --- |
| `ACTIVE` | 可使用 |
| `USED` | 已使用 |
| `EXPIRED` | 已过期 |
| `REVOKED` | 已撤销 |

---

## 四、公共错误码

| HTTP 状态 | 业务码 | message 示例 | 场景 |
| --- | --- | --- | --- |
| `200` | `0` | `ok` | 成功 |
| `400` | `400` | `文件大小不能为空` | 参数校验失败 |
| `401` | `401` | `未登录或会话已过期` | 未登录 |
| `403` | `403` | `无权访问该文件` | 权限不足 |
| `404` | `404` | `附件引用不存在` | 对象不存在 |
| `409` | `409` | `上传票据已确认` | 幂等或状态冲突 |
| `413` | `413` | `文件超过当前场景大小限制` | 超过当前 `ownerType` 上传策略大小上限 |
| `415` | `415` | `当前场景不支持该文件类型` | 扩展名或 MIME 不在当前 `ownerType` 上传策略允许列表内 |
| `422` | `422` | `文件校验失败，请重新上传` | hash 或大小不一致 |
| `429` | `429` | `上传请求过于频繁，请稍后再试` | 限流 |
| `507` | `507` | `个人云空间不足，请清理文件或联系管理员扩容` | 配额或真实容量不足 |
| `500` | `500` | `文件服务异常，请稍后重试` | 系统异常 |

---

## 五、数据模型

### 5.1 CreateUploadTicketRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ownerType` | `string` | 是 | 员工端第一版只允许 `PERSONAL_FILE` |
| `ownerId` | `long/null` | 否 | 员工端第一版必须为空；跨模块附件需后续授权策略接入 |
| `fileName` | `string` | 是 | 客户端原始文件名 |
| `mimeType` | `string` | 是 | 客户端识别的 MIME |
| `fileSize` | `long` | 是 | 文件大小，单位字节；第一版 `PERSONAL_FILE` 默认不超过 `100MB` |
| `contentSha256` | `string` | 是 | 客户端计算的 SHA-256 |
| `contentMd5` | `string/null` | 否 | 客户端计算的 MD5 |
| `clientRequestId` | `string/null` | 否 | 客户端请求幂等 ID |

示例：

```json
{
  "ownerType": "PERSONAL_FILE",
  "ownerId": null,
  "fileName": "员工手册.pdf",
  "mimeType": "application/pdf",
  "fileSize": 2097152,
  "contentSha256": "f42a4d8c9a0b7c1d2e3f4567890abcdeffedcba09876543211234567890abcd",
  "contentMd5": "4f9f2cab7f8f2a0c0d5aa5c6e4b7a111",
  "clientRequestId": "web-upload-20260503-001"
}
```

### 5.2 UploadTicketResponse

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `uploadTicketId` | `long/null` | 是 | 上传票据 ID；第一版秒传也会生成已确认票据用于幂等回查 |
| `ticketNo` | `string/null` | 是 | 上传票据编号；秒传时对应已确认秒传票据 |
| `deduplicated` | `boolean` | 是 | 是否命中物理去重 |
| `attachmentRefId` | `long/null` | 是 | 秒传时直接返回业务引用 ID |
| `storageProvider` | `string` | 是 | `LOCAL` 或 `MINIO` |
| `bucketName` | `string` | 是 | 存储桶 |
| `objectKey` | `string` | 是 | 对象 key |
| `uploadUrl` | `string/null` | 是 | 客户端上传地址 |
| `headers` | `object` | 是 | 上传对象时需要携带的头 |
| `expireAt` | `string/null` | 是 | 上传票据过期时间 |

未命中去重示例：

```json
{
  "uploadTicketId": 1001,
  "ticketNo": "UP202605030001",
  "deduplicated": false,
  "attachmentRefId": null,
  "storageProvider": "MINIO",
  "bucketName": "workforce-hub",
  "objectKey": "uploads/2026/05/03/f42a4d8c.pdf",
  "uploadUrl": "http://localhost:9000/workforce-hub/uploads/2026/05/03/f42a4d8c.pdf?signature=short",
  "headers": {
    "Content-Type": "application/pdf"
  },
  "expireAt": "2026-05-03T11:30:00+08:00"
}
```

命中秒传示例：

```json
{
  "uploadTicketId": 1002,
  "ticketNo": "UP1002",
  "deduplicated": true,
  "attachmentRefId": 9001,
  "storageProvider": "MINIO",
  "bucketName": "workforce-hub",
  "objectKey": "objects/f42a4d8c.pdf",
  "uploadUrl": null,
  "headers": {},
  "expireAt": null
}
```

### 5.3 ConfirmUploadRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `uploadTicketId` | `long` | 是 | 上传票据 ID |
| `objectKey` | `string` | 是 | 已上传对象 key |
| `contentSha256` | `string` | 是 | 客户端再次提交的 SHA-256 |
| `fileSize` | `long` | 是 | 文件大小 |
| `ownerType` | `string` | 是 | 业务归属类型 |
| `ownerId` | `long/null` | 否 | 业务归属 ID |
| `displayName` | `string` | 是 | 展示文件名 |

示例：

```json
{
  "uploadTicketId": 1001,
  "objectKey": "uploads/2026/05/03/f42a4d8c.pdf",
  "contentSha256": "f42a4d8c9a0b7c1d2e3f4567890abcdeffedcba09876543211234567890abcd",
  "fileSize": 2097152,
  "ownerType": "IM_GROUP_FILE",
  "ownerId": 3001,
  "displayName": "员工手册.pdf"
}
```

### 5.4 AttachmentRefResponse

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `attachmentRefId` | `long` | 是 | 附件引用 ID |
| `fileObjectId` | `long` | 是 | 物理文件对象 ID |
| `displayName` | `string` | 是 | 展示文件名 |
| `mimeType` | `string` | 是 | MIME |
| `fileSize` | `long` | 是 | 文件大小 |
| `contentSha256` | `string` | 是 | SHA-256 |
| `deduplicated` | `boolean` | 是 | 是否复用已存在物理对象 |
| `scanStatus` | `string` | 是 | 扫描状态，第一版可返回 `PENDING` |
| `createdAt` | `string` | 是 | 创建时间 |

### 5.5 QuotaResponse

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `personalQuotaBytes` | `long` | 是 | 当前用户个人配额 |
| `personalUsedBytes` | `long` | 是 | 当前用户个人已用 |
| `personalUsagePercent` | `decimal` | 是 | 个人使用百分比 |
| `orgPoolQuotaBytes` | `long` | 是 | 企业长期文件池上限 |
| `orgPoolUsedBytes` | `long` | 是 | 企业长期文件池已用 |
| `cacheQuotaBytes` | `long` | 是 | 临时缓存池上限 |
| `cacheUsedBytes` | `long` | 是 | 临时缓存池已用 |
| `uploadWritable` | `boolean` | 是 | 当前是否允许继续上传 |
| `blockReason` | `string/null` | 是 | 不允许上传时的原因 |

### 5.6 CreateDownloadTicketRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `downloadType` | `string` | 是 | 第一版支持 `SINGLE` |
| `reason` | `string/null` | 否 | 敏感文件下载原因，第一版可为空 |

### 5.7 DownloadTicketResponse

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `downloadTicketId` | `long` | 是 | 下载票据 ID |
| `ticketNo` | `string` | 是 | 票据编号 |
| `downloadUrl` | `string` | 是 | 短期下载入口 |
| `expireAt` | `string` | 是 | 过期时间 |
| `fileName` | `string` | 是 | 下载展示文件名 |
| `fileSize` | `long` | 是 | 文件大小 |

### 5.8 QuotaPolicyRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `policyCode` | `string` | 是 | 策略编码 |
| `policyName` | `string` | 是 | 策略名称 |
| `scopeType` | `string` | 是 | 配额作用域 |
| `scopeId` | `long/null` | 否 | 作用域 ID |
| `quotaBytes` | `long` | 是 | 长期文件池配额 |
| `cacheQuotaBytes` | `long/null` | 否 | 临时缓存配额 |
| `warningThreshold` | `decimal` | 是 | 预警阈值，例如 `0.8` |
| `blockThreshold` | `decimal` | 是 | 阻断阈值，例如 `1.0` |
| `status` | `string` | 是 | `ENABLED` 或 `DISABLED` |

---

## 六、员工端接口

### 6.1 申请上传票据

`POST /api/employee/files/upload-tickets`

用途：

- 为员工端个人文件、消息附件、群文件、文档文件申请上传入口
- 如果命中 `content_sha256 + file_size` 去重，可直接创建附件引用并返回秒传结果
- 当前实现只允许员工端个人文件；消息、群文件、文档、审批、知识库附件需由后续业务模块接入授权策略后再开放

请求体：

```json
{
  "ownerType": "PERSONAL_FILE",
  "ownerId": null,
  "fileName": "请假证明.pdf",
  "mimeType": "application/pdf",
  "fileSize": 1048576,
  "contentSha256": "9b7d4d8c9a0b7c1d2e3f4567890abcdeffedcba09876543211234567890abcd",
  "contentMd5": "2c569aabbf7775ef8fc570e228c16b98",
  "clientRequestId": "personal-upload-001"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "uploadTicketId": 1001,
    "ticketNo": "UP202605030001",
    "deduplicated": false,
    "attachmentRefId": null,
    "storageProvider": "MINIO",
    "bucketName": "workforce-hub",
    "objectKey": "uploads/2026/05/03/9b7d.pdf",
    "uploadUrl": "http://localhost:9000/workforce-hub/uploads/2026/05/03/9b7d.pdf?signature=short",
    "headers": {
      "Content-Type": "application/pdf"
    },
    "expireAt": "2026-05-03T11:30:00+08:00"
  },
  "requestId": "file202605030001"
}
```

关键校验：

- 当前用户已登录且为员工身份
- `fileSize > 0`
- `contentSha256` 为合法 SHA-256
- `ownerType=PERSONAL_FILE` 且 `ownerId=null`
- 文件类型和大小符合 `sys_file_upload_policy` 中 `PERSONAL_FILE` 启用策略
- 若同一用户、同一 `clientRequestId` 已签发上传票据，重复申请优先返回原票据，不因后续策略调整重新判定
- 申请阶段秒传仅复用同一员工本人已有的有效附件引用；如果相同内容只存在于其他上传主体名下，则仍返回普通上传票据
- 个人空间、目标空间、企业总池、存储后端真实容量均允许写入

### 6.2 确认上传完成

`POST /api/employee/files/confirm-upload`

用途：

- 客户端上传对象后，通知服务端校验并生成物理对象与业务引用

请求体：

```json
{
  "uploadTicketId": 1001,
  "objectKey": "uploads/2026/05/03/9b7d.pdf",
  "contentSha256": "9b7d4d8c9a0b7c1d2e3f4567890abcdeffedcba09876543211234567890abcd",
  "fileSize": 1048576,
  "ownerType": "PERSONAL_FILE",
  "ownerId": null,
  "displayName": "请假证明.pdf"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "attachmentRefId": 9001,
    "fileObjectId": 8001,
    "displayName": "请假证明.pdf",
    "mimeType": "application/pdf",
    "fileSize": 1048576,
    "contentSha256": "9b7d4d8c9a0b7c1d2e3f4567890abcdeffedcba09876543211234567890abcd",
    "deduplicated": false,
    "scanStatus": "PENDING",
    "createdAt": "2026-05-03T11:05:00+08:00"
  },
  "requestId": "file202605030002"
}
```

关键校验：

- 票据属于当前用户
- 票据未过期且未取消
- 对象在 `LOCAL` 或 `MINIO` 中真实存在
- 服务端重新计算 hash 与大小
- 重复确认当前采用宽松幂等：如果 `uploadTicketId` 已经生成附件引用，则以原票据对应附件为准返回，不再次按当前请求体重建引用

### 6.3 查询我的云空间用量

`GET /api/employee/files/quota`

用途：

- 展示个人云空间与企业文件池摘要
- 上传按钮、空间管理页和文件中心首页复用

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "personalQuotaBytes": 10737418240,
    "personalUsedBytes": 2147483648,
    "personalUsagePercent": 0.2,
    "orgPoolQuotaBytes": 429496729600,
    "orgPoolUsedBytes": 322122547200,
    "cacheQuotaBytes": 53687091200,
    "cacheUsedBytes": 10737418240,
    "uploadWritable": true,
    "blockReason": null
  },
  "requestId": "file202605030003"
}
```

### 6.4 申请下载票据

`POST /api/employee/files/{attachmentRefId}/download-tickets`

请求体：

```json
{
  "downloadType": "SINGLE",
  "reason": null
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "downloadTicketId": 7001,
    "ticketNo": "DL202605030001",
    "downloadUrl": "http://localhost:8080/api/employee/files/download/DL202605030001",
    "expireAt": "2026-05-03T11:15:00+08:00",
    "fileName": "请假证明.pdf",
    "fileSize": 1048576
  },
  "requestId": "file202605030004"
}
```

关键校验：

- 附件引用存在且未彻底删除
- 当前用户对该业务引用有下载权限
- 下载行为写入审计日志

### 6.4.1 使用下载票据下载文件

`GET /api/employee/files/download/{ticketNo}`

用途：

- 使用短期下载票据获取文件二进制
- 下载成功后将票据标记为 `USED`
- 下载执行写入 `FILE_DOWNLOAD_EXECUTED` 审计

成功响应：

- HTTP `200`
- Body 为原始文件二进制
- `Content-Disposition` 使用附件下载方式返回展示文件名
- `Content-Type` 优先使用文件记录的 MIME，缺失时退回 `application/octet-stream`

关键校验：

- 当前用户必须是票据申请人
- 票据必须为 `ACTIVE`
- 票据未过期；过期时返回业务码 `410`
- 附件引用和物理文件对象未删除
- 存储 Provider 中的对象必须存在

### 6.5 移入回收站

`POST /api/employee/files/{attachmentRefId}/trash`

请求体：

```json
{
  "reason": "个人清理文件"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "attachmentRefId": 9001,
    "deletedAt": "2026-05-03T11:20:00+08:00",
    "purgeAfter": "2026-06-02T11:20:00+08:00"
  },
  "requestId": "file202605030005"
}
```

### 6.6 从回收站恢复

`POST /api/employee/files/{attachmentRefId}/restore`

请求体：

```json
{
  "reason": "误删恢复"
}
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "attachmentRefId": 9001,
    "restoredAt": "2026-05-03T11:25:00+08:00"
  },
  "requestId": "file202605030006"
}
```

关键校验：

- 引用仍在回收站保留期内
- 当前用户有恢复权限
- 恢复后不会突破当前配额和企业总池限制

### 6.7 彻底删除业务引用

`DELETE /api/employee/files/{attachmentRefId}`

用途：

- 用户主动彻底删除自己的回收站文件
- 只彻底删除业务引用，不保证立即删除物理对象

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "attachmentRefId": 9001,
    "purgedAt": "2026-05-03T11:30:00+08:00",
    "physicalCleanupScheduled": true
  },
  "requestId": "file202605030007"
}
```

---

## 七、后台端接口

### 7.1 申请后台上传票据

`POST /api/admin/files/upload-tickets`

用途：

- 后台上传治理附件或系统文档
- 第一版先支撑后台治理文件最小闭环，知识库文件和文档文件需等 `module-kb` / `module-doc` 的空间 ACL 落地后再开放
- 当前后台文件生命周期已覆盖票据、确认、秒传、列表、下载、回收站、恢复、彻底删除和企业总池用量释放闭环

请求体与员工端保持一致，区别在于：

- 鉴权身份为后台管理员
- 第一版仅允许 `ownerType=ADMIN_FILE` 且 `ownerId=null`
- 命中同一管理员已有有效附件引用的 `content_sha256 + file_size` 去重时，会创建一张 `CONFIRMED` 秒传票据、创建后台附件引用并返回秒传结果
- 如果相同物理对象只存在于员工或其他上传主体名下，后台申请阶段不会直接秒传，必须先上传对象并在确认阶段完成服务端校验
- 同一管理员、同一 `clientRequestId` 重复请求秒传时，返回同一张秒传票据和同一附件引用，不重复增加 `GLOBAL` 用量
- 后台治理文件只计入企业总池，不占员工个人云空间
- 权限当前由后台管理员会话隔离，细分后台角色权限后再接入权限点

### 7.2 查询后台文件列表

`GET /api/admin/files?pageNo=1&pageSize=20`

用途：

- 分页查询当前有效的后台治理文件
- 第一版只返回 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的附件引用
- 不返回员工个人文件、知识库文件、文档空间文件或群文件
- 默认不返回 `contentSha256`、`storageProvider`、`bucketName`、`objectKey` 等对象存储内部字段；排障详情后续通过更高权限接口单独开放

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 1,
    "list": [
      {
        "attachmentRefId": 9001,
        "fileObjectId": 8001,
        "ownerType": "ADMIN_FILE",
        "ownerId": null,
        "originalFileName": "后台制度文件.pdf",
        "displayName": "后台制度文件.pdf",
        "uploaderType": "ADMIN",
        "uploaderId": 8001,
        "mimeType": "application/pdf",
        "fileSize": 1048576,
        "scanStatus": "PENDING",
        "deletedAt": null,
        "purgeAfter": null,
        "createdAt": "2026-05-05T13:20:00",
        "updatedAt": "2026-05-05T13:20:00"
      }
    ]
  },
  "requestId": "file202605050101"
}
```

### 7.3 确认后台上传完成

`POST /api/admin/files/confirm-upload`

请求体与员工端保持一致。

成功后返回 `AttachmentRefResponse`，并写入：

- `sys_upload_ticket.uploader_type = ADMIN`
- `sys_attachment_ref.uploader_type = ADMIN`
- `sys_storage_usage.scope_type = GLOBAL`
- `sys_audit_log.action_type = FILE_ADMIN_UPLOAD_CONFIRMED`

### 7.4 申请后台下载票据

`POST /api/admin/files/{attachmentRefId}/download-tickets`

请求体：

```json
{
  "downloadType": "SINGLE",
  "reason": "后台治理文件下载"
}
```

成功响应字段同员工端 `DownloadTicketResponse`，但 `downloadUrl` 为：

```text
/api/admin/files/download/{ticketNo}
```

关键校验：

- 仅允许下载 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的后台治理文件
- 文件必须未进入回收站且未彻底删除
- 第一版仅支持 `downloadType=SINGLE`
- 写入 `FILE_ADMIN_DOWNLOAD_TICKET_CREATED` 审计

### 7.5 使用后台下载票据下载文件

`GET /api/admin/files/download/{ticketNo}`

成功响应：

- HTTP `200`
- Body 为原始文件二进制
- 下载成功后票据状态更新为 `USED`
- 写入 `FILE_ADMIN_DOWNLOAD_EXECUTED` 审计

关键校验：

- 当前后台管理员必须是票据申请人
- 票据必须为 `ACTIVE` 且未过期
- 附件引用必须仍为后台治理文件且未删除

### 7.6 后台文件移入回收站

`POST /api/admin/files/{attachmentRefId}/trash`

请求体：

```json
{
  "reason": "后台治理文件清理"
}
```

成功响应字段同员工端 `FileTrashResponse`。

关键语义：

- 回收站保留期第一版为 `30` 天
- 移入回收站不释放企业总池用量，也不减少物理对象引用数
- 写入 `FILE_ADMIN_TRASHED` 审计

### 7.7 后台文件从回收站恢复

`POST /api/admin/files/{attachmentRefId}/restore`

请求体：

```json
{
  "reason": "误删恢复"
}
```

成功响应字段同员工端 `FileRestoreResponse`。

关键语义：

- 仅允许恢复仍在回收站保留期内的后台治理文件
- 恢复时复核企业总池是否仍允许写入
- 写入 `FILE_ADMIN_RESTORED` 审计

### 7.8 后台文件彻底删除

`DELETE /api/admin/files/{attachmentRefId}`

用途：

- 彻底删除已进入回收站的后台治理文件引用
- 释放企业总池 `GLOBAL` 用量
- 如果物理对象已无有效业务引用，则设置延迟物理清理时间

成功响应字段同员工端 `FilePurgeResponse`。

关键语义：

- 文件必须先进入回收站，不能直接彻底删除有效文件
- 后台治理文件不占员工个人云空间，因此彻底删除只释放 `GLOBAL` 用量，不释放 `USER` 用量
- 写入 `FILE_ADMIN_PURGED` 审计

### 7.9 查询存储摘要

`GET /api/admin/files/storage-summary`

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "orgPoolQuotaBytes": 429496729600,
    "orgPoolUsedBytes": 322122547200,
    "orgPoolWarningThreshold": 0.8,
    "orgPoolBlockThreshold": 1.0,
    "cacheQuotaBytes": 53687091200,
    "cacheUsedBytes": 10737418240,
    "backendFreeBytes": 96636764160,
    "backendWritable": true,
    "writeProtectReason": null,
    "fileObjectCount": 1280,
    "attachmentRefCount": 3560,
    "recycleBinCount": 42,
    "pendingCleanupObjectCount": 8
  },
  "requestId": "file202605030101"
}
```

### 7.10 查询配额策略

`GET /api/admin/files/quota-policies?scopeType=USER_DEFAULT`

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "policyId": 1,
      "policyCode": "USER_DEFAULT_10G",
      "policyName": "员工默认 10GB",
      "scopeType": "USER_DEFAULT",
      "scopeId": null,
      "quotaBytes": 10737418240,
      "cacheQuotaBytes": null,
      "warningThreshold": 0.8,
      "blockThreshold": 1.0,
      "status": "ENABLED",
      "createdAt": "2026-05-03T11:00:00",
      "updatedAt": "2026-05-03T11:00:00"
    }
  ],
  "requestId": "file202605030102"
}
```

### 7.11 创建配额策略

`POST /api/admin/files/quota-policies`

请求体：

```json
{
  "policyCode": "USER_1001_50G",
  "policyName": "张三个人 50GB",
  "scopeType": "USER",
  "scopeId": 1001,
  "quotaBytes": 53687091200,
  "cacheQuotaBytes": null,
  "warningThreshold": 0.8,
  "blockThreshold": 1.0,
  "status": "ENABLED"
}
```

成功响应返回配额策略详情，字段同 `QuotaPolicyResponse`。

### 7.12 更新配额策略

`PUT /api/admin/files/quota-policies/{id}`

请求体：

```json
{
  "policyName": "张三个人 80GB",
  "quotaBytes": 85899345920,
  "cacheQuotaBytes": null,
  "warningThreshold": 0.75,
  "blockThreshold": 0.95,
  "status": "ENABLED"
}
```

关键校验：

- `quotaBytes` 不能小于当前作用域已用量
- `warningThreshold` 必须小于 `blockThreshold`
- `blockThreshold` 不得大于 `1.0`
- `GLOBAL`、`USER_DEFAULT`、`TEMP_CACHE` 不允许传 `scopeId`
- `USER`、`DEPARTMENT`、`ROLE`、`GROUP`、`KNOWLEDGE_SPACE` 必须传 `scopeId`

### 7.13 查询后台文件回收站列表

`GET /api/admin/files/recycle-bin?pageNo=1&pageSize=20`

用途：

- 分页查询后台治理文件回收站
- 第一版只返回 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的附件引用
- 员工个人回收站仍由员工端自己治理，后台暂不接管

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "pageNo": 1,
    "pageSize": 20,
    "total": 1,
    "list": [
      {
        "attachmentRefId": 9001,
        "fileObjectId": 8001,
        "ownerType": "ADMIN_FILE",
        "ownerId": null,
        "displayName": "后台制度文件.pdf",
        "fileSize": 1048576,
        "uploaderType": "ADMIN",
        "uploaderId": 8001,
        "deletedAt": "2026-05-03T11:20:00+08:00",
        "purgeAfter": "2026-06-02T11:20:00+08:00"
      }
    ]
  },
  "requestId": "file202605030103"
}
```

### 7.14 手动触发清理任务

`POST /api/admin/files/cleanup-jobs/run`

请求体：

```json
{
  "jobType": "ALL_EXPIRED",
  "dryRun": false
}
```

支持的 `jobType`：

| 值 | 含义 |
| --- | --- |
| `EXPIRED_UPLOAD_TICKET` | 清理过期上传票据和未确认临时对象 |
| `EXPIRED_DOWNLOAD_TICKET` | 清理过期下载票据 |
| `EXPIRED_RECYCLE_REF` | 清理到期回收站引用 |
| `UNREFERENCED_FILE_OBJECT` | 清理无引用物理对象 |
| `FAILED_PHYSICAL_OBJECT` | 重试历史物理删除失败对象，并恢复可补偿的悬挂清理明细 |
| `ALL_EXPIRED` | 执行全部到期清理 |

执行规则：

- `dryRun=true` 只统计候选数量和预计释放字节，不修改票据、引用或物理对象；其中 `deletedPhysicalObjects` 表示预计会触发物理删除的对象数。
- 过期上传票据会从 `PENDING` 标记为 `EXPIRED`，并清理未确认的临时上传对象。
- 过期下载票据会从 `ACTIVE` 标记为 `EXPIRED`。
- 到期回收站引用第一版只处理 `PERSONAL_FILE` 与 `ADMIN_FILE`，会被标记为彻底删除，并释放对应空间用量。
- 物理对象采用两阶段清理：数据库事务内先标记业务状态并写入 `sys_file_cleanup_job_run` / `sys_file_cleanup_job_detail`；事务提交后再逐条删除对象存储，成功或失败都会回写明细。
- 到期回收站引用清理后，物理对象先进入 `cleanup_after` 候选；只有已经无有效引用且 `cleanup_after` 到期的对象，才会在 `UNREFERENCED_FILE_OBJECT` 阶段标记文件对象删除并进入物理删除明细。
- `FAILED_PHYSICAL_OBJECT` 会把当前仍未成功覆盖的最新 `FAILED` 明细复制到本次任务中重新执行；历史失败明细保留为审计链路，不会被直接改写。
- `FAILED_PHYSICAL_OBJECT` 也会恢复超过 30 分钟仍处于 `RUNNING` 任务下的历史 `PENDING` 物理清理明细：原悬挂明细和任务会被标记为 `FAILED`，并复制到本次任务继续删除。
- 确认上传阶段如果命中全局去重但本次确实已经上传了新的临时对象，系统会登记 `DEDUPLICATED_UPLOAD_OBJECT` 清理明细；该明细由 `FAILED_PHYSICAL_OBJECT` 统一删除，避免确认上传事务内直接执行对象存储副作用。
- `LOCAL` 与 `MINIO` Provider 均提供删除对象入口；第一版由后台手动触发，后续可接入定时调度。
- `deletedPhysicalObjects` 统计本次所有物理对象删除成功明细，包括过期上传临时对象、无引用文件对象和失败重试对象；`failedPhysicalObjects` 统计本次所有物理对象删除失败明细。
- `jobStatus=SUCCESS` 表示本次清理全部完成；`jobStatus=PARTIAL_FAILED` 表示数据库状态已提交，但存在物理对象删除失败，需要后续按明细排查或重试；`jobStatus=FAILED` 表示数据库阶段失败，任务运行记录会保留 `errorMessage`。

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "jobRunId": "FILE-CLEAN-20260503-001",
    "jobType": "ALL_EXPIRED",
    "dryRun": false,
    "jobStatus": "SUCCESS",
    "expiredUploadTickets": 3,
    "expiredDownloadTickets": 12,
    "purgedAttachmentRefs": 2,
    "deletedPhysicalObjects": 4,
    "failedPhysicalObjects": 0,
    "releasedBytes": 1048576,
    "errorMessage": null
  },
  "requestId": "file202605030104"
}
```

部分失败响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "jobRunId": "FILE-CLEAN-20260503-002",
    "jobType": "UNREFERENCED_FILE_OBJECT",
    "dryRun": false,
    "jobStatus": "PARTIAL_FAILED",
    "expiredUploadTickets": 0,
    "expiredDownloadTickets": 0,
    "purgedAttachmentRefs": 0,
    "deletedPhysicalObjects": 0,
    "failedPhysicalObjects": 1,
    "releasedBytes": 0,
    "errorMessage": null
  },
  "requestId": "file202605030105"
}
```

数据库阶段失败响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "jobRunId": "FILE-CLEAN-20260503-003",
    "jobType": "EXPIRED_RECYCLE_REF",
    "dryRun": false,
    "jobStatus": "FAILED",
    "expiredUploadTickets": 0,
    "expiredDownloadTickets": 0,
    "purgedAttachmentRefs": 0,
    "deletedPhysicalObjects": 0,
    "failedPhysicalObjects": 0,
    "releasedBytes": 0,
    "errorMessage": "数据库阶段失败原因"
  },
  "requestId": "file202605030106"
}
```

---

## 八、实现注意事项

### 8.1 幂等

必须做幂等的接口：

- 创建上传票据
- 确认上传完成
- 移入回收站
- 恢复
- 彻底删除
- 手动触发清理任务

建议组合：

- `Idempotency-Key`
- `clientRequestId`
- 数据库唯一约束

### 8.2 并发去重

同一 `contentSha256 + fileSize` 并发确认时：

- 只能成功插入一条 `sys_file_object`
- 其他并发请求复用已存在物理对象
- 每个业务动作仍可以创建自己的 `sys_attachment_ref`

### 8.3 配额一致性

上传确认和恢复都要重新校验配额。

原因：

- 申请票据到确认之间，其他用户可能已经占用了空间
- 删除后恢复时，当前配额可能已经被管理员调小

### 8.4 审计

第一版至少记录：

- 上传票据创建
- 上传确认
- 下载票据创建
- 下载执行
- 移入回收站
- 恢复
- 彻底删除
- 清理任务执行

---

## 九、联调顺序建议

1. 先跑后台配额策略创建和存储摘要查询
2. 再跑员工个人文件上传票据
3. 跑未命中去重的确认上传
4. 跑命中去重的秒传
5. 跑下载票据
6. 跑回收站删除、恢复、彻底删除
7. 跑清理任务 dry-run
8. 跑权限不足和配额不足异常
