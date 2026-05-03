# API 文档 module-file 文件中心接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-file` 文件中心接口 API 文档 |
| 文档版本 | `v1.0` |
| 适用范围 | 两段式上传、文件去重、配额查询、下载票据、回收站、后台配额治理 |
| 对应模块 | `server/module-file` |
| 发布日期 | `2026-05-03` |
| 当前状态 | 契约已收口；员工端主链路与后台存储摘要已完成第一批后端实现 |

---

## 二、接口范围

本文档覆盖以下 `17` 个接口。

当前第一批已实现 `8` 个接口：员工端 `7` 个主链路接口，以及后台端 `GET /api/admin/files/storage-summary`。其余后台上传、配额策略、回收站治理和清理任务仍是后续实现契约。

### 1. 员工端

- `POST /api/employee/files/upload-tickets`
- `POST /api/employee/files/confirm-upload`
- `GET /api/employee/files/quota`
- `POST /api/employee/files/{attachmentRefId}/download-tickets`
- `POST /api/employee/files/{attachmentRefId}/trash`
- `POST /api/employee/files/{attachmentRefId}/restore`
- `DELETE /api/employee/files/{attachmentRefId}`

### 2. 后台端

- `POST /api/admin/files/upload-tickets`（契约，待实现）
- `POST /api/admin/files/confirm-upload`（契约，待实现）
- `GET /api/admin/files/storage-summary`（已实现）
- `GET /api/admin/files/quota-policies`（契约，待实现）
- `POST /api/admin/files/quota-policies`（契约，待实现）
- `PUT /api/admin/files/quota-policies/{id}`（契约，待实现）
- `GET /api/admin/files/recycle-bin`（契约，待实现）
- `POST /api/admin/files/recycle-bin/{attachmentRefId}/restore`（契约，待实现）
- `DELETE /api/admin/files/recycle-bin/{attachmentRefId}`（契约，待实现）
- `POST /api/admin/files/cleanup-jobs/run`（契约，待实现）

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
- 后台知识文件上传接口可由知识库管理员调用

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

| 值 | 含义 |
| --- | --- |
| `PERSONAL_FILE` | 个人文件 |
| `IM_MESSAGE` | 消息附件 |
| `IM_GROUP_FILE` | 群文件 |
| `DOC_DOCUMENT` | 文档文件 |
| `KB_DOCUMENT` | 知识库原文件 |
| `WF_REQUEST` | 审批附件 |
| `AGENT_TEMP` | Agent 临时产物 |

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
| `413` | `413` | `文件超过当前场景大小限制` | 文件过大 |
| `415` | `415` | `当前场景不支持该文件类型` | 类型不允许 |
| `422` | `422` | `文件校验失败，请重新上传` | hash 或大小不一致 |
| `429` | `429` | `上传请求过于频繁，请稍后再试` | 限流 |
| `507` | `507` | `个人云空间不足，请清理文件或联系管理员扩容` | 配额或真实容量不足 |
| `500` | `500` | `文件服务异常，请稍后重试` | 系统异常 |

---

## 五、数据模型

### 5.1 CreateUploadTicketRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ownerType` | `string` | 是 | 文件归属业务类型 |
| `ownerId` | `long/null` | 否 | 归属业务 ID，个人文件可为空或传个人空间 ID |
| `fileName` | `string` | 是 | 客户端原始文件名 |
| `mimeType` | `string` | 是 | 客户端识别的 MIME |
| `fileSize` | `long` | 是 | 文件大小，单位字节 |
| `contentSha256` | `string` | 是 | 客户端计算的 SHA-256 |
| `contentMd5` | `string/null` | 否 | 客户端计算的 MD5 |
| `clientRequestId` | `string/null` | 否 | 客户端请求幂等 ID |

示例：

```json
{
  "ownerType": "IM_GROUP_FILE",
  "ownerId": 3001,
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
| `uploadTicketId` | `long/null` | 是 | 上传票据 ID，秒传时可为空 |
| `ticketNo` | `string/null` | 是 | 上传票据编号 |
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
  "uploadTicketId": null,
  "ticketNo": null,
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
- 文件类型和大小符合 `ownerType` 场景限制
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
- 重复确认必须幂等

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

- 后台上传知识库文件、治理附件或系统文档

请求体与员工端保持一致，区别在于：

- 鉴权身份为后台管理员
- `ownerType` 常见为 `KB_DOCUMENT`、`DOC_DOCUMENT`
- 权限由后台角色和业务空间 ACL 共同决定

### 7.2 确认后台上传完成

`POST /api/admin/files/confirm-upload`

请求体与员工端保持一致。

成功后返回 `AttachmentRefResponse`。

### 7.3 查询存储摘要

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

### 7.4 查询配额策略

`GET /api/admin/files/quota-policies?scopeType=USER_DEFAULT&status=ENABLED`

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
        "id": 1,
        "policyCode": "USER_DEFAULT_10G",
        "policyName": "员工默认 10GB",
        "scopeType": "USER_DEFAULT",
        "scopeId": null,
        "quotaBytes": 10737418240,
        "cacheQuotaBytes": null,
        "warningThreshold": 0.8,
        "blockThreshold": 1.0,
        "status": "ENABLED"
      }
    ]
  },
  "requestId": "file202605030102"
}
```

### 7.5 创建配额策略

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

成功响应返回配额策略详情。

### 7.6 更新配额策略

`PUT /api/admin/files/quota-policies/{id}`

请求体同创建接口。

关键校验：

- `quotaBytes` 不能小于当前已用量，除非策略状态改为停用并有替代策略
- `warningThreshold` 必须小于 `blockThreshold`
- `blockThreshold` 不得大于 `1.0`

### 7.7 查询回收站治理列表

`GET /api/admin/files/recycle-bin?ownerType=PERSONAL_FILE&uploaderId=1001&pageNo=1&pageSize=20`

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
        "ownerType": "PERSONAL_FILE",
        "ownerId": null,
        "displayName": "请假证明.pdf",
        "fileSize": 1048576,
        "uploaderType": "USER",
        "uploaderId": 1001,
        "deletedAt": "2026-05-03T11:20:00+08:00",
        "purgeAfter": "2026-06-02T11:20:00+08:00"
      }
    ]
  },
  "requestId": "file202605030103"
}
```

### 7.8 管理员恢复文件

`POST /api/admin/files/recycle-bin/{attachmentRefId}/restore`

请求体：

```json
{
  "reason": "用户误删，管理员恢复"
}
```

成功响应返回恢复结果。

### 7.9 管理员彻底删除文件

`DELETE /api/admin/files/recycle-bin/{attachmentRefId}?reason=超过保留期后人工清理`

说明：第一版不建议在 `DELETE` 请求中依赖 JSON 请求体，删除原因通过 `reason` 查询参数传入，避免客户端或代理层丢弃请求体。

成功响应返回彻底删除结果。

### 7.10 手动触发清理任务

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
| `ALL_EXPIRED` | 执行全部到期清理 |

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "jobRunId": "FILE-CLEAN-20260503-001",
    "jobType": "ALL_EXPIRED",
    "dryRun": false,
    "expiredUploadTickets": 3,
    "expiredDownloadTickets": 12,
    "purgedAttachmentRefs": 2,
    "deletedPhysicalObjects": 1,
    "releasedBytes": 1048576
  },
  "requestId": "file202605030104"
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
