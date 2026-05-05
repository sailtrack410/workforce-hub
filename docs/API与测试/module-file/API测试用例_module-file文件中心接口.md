# API 测试用例 module-file 文件中心接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-file` 文件中心接口测试用例 |
| 文档版本 | `v1.0` |
| 适用范围 | 两段式上传、秒传、配额、下载票据、回收站、清理任务 |
| 发布日期 | `2026-05-03` |
| 当前状态 | 测试契约已收口；第二批员工端下载执行、后台配额策略、上传限制策略、后台上传确认、后台 `ADMIN_FILE` 生命周期和后台清理任务自动化测试已落地 |

---

## 二、测试目标

本轮测试目标：

- 验证文件中心两段式上传闭环可用
- 验证 `content_sha256 + file_size` 去重语义稳定
- 验证文件名不同不影响物理去重
- 验证个人、空间、组织总池和后端容量的拦截提示清楚
- 验证下载必须先申请短期票据
- 验证回收站删除、恢复、彻底删除语义正确
- 验证过期票据和无引用物理对象可被清理
- 验证上传、下载、删除、恢复、清理都有审计入口

当前已落地自动化测试类：

- `ModuleFileApiIntegrationTest`
- `ModuleFileAdminApiIntegrationTest`
- `ModuleFileCleanupIntegrationTest`

当前已自动化覆盖：

- 员工申请上传票据
- 员工确认上传
- 命中物理去重的秒传
- 下载票据创建
- 使用有效下载票据下载原始文件
- 员工端同一下载票据并发执行只能成功一次
- 过期下载票据拦截
- 个人配额不足拦截
- 回收站删除、恢复、彻底删除
- 彻底删除释放个人与企业总池用量
- 员工端同一附件并发彻底删除不重复释放空间
- 恢复时企业总池不足拦截
- 上传票据 `clientRequestId` 顺序重试幂等
- 确认上传业务归属防篡改
- 员工云空间查询
- 后台存储摘要查询
- 后台创建、查询、更新配额策略
- 后台配额策略阈值校验
- 后台配额不能低于当前已用量校验
- 后台配额策略编码重复拦截
- 后台同一作用域重复策略拦截
- 个人文件上传类型限制拦截
- 个人文件上传大小限制拦截
- 后台上传票据申请与确认上传
- 后台上传命中物理去重
- 后台秒传重复客户端请求 ID 幂等
- 后台不能仅凭员工文件 hash 与大小直接秒传
- 后台 `ADMIN_FILE` 有效文件列表
- 后台列表默认隐藏对象存储内部字段
- 后台下载票据创建与真实下载
- 后台同一下载票据并发执行只能成功一次
- 后台非法 MIME 下载降级为二进制响应
- 后台回收站删除、回收站列表、恢复、彻底删除
- 后台彻底删除释放企业总池用量
- 后台同一附件并发彻底删除不重复释放企业用量
- 后台生命周期审计记录
- 清理任务 dry-run 不修改票据、引用和物理对象
- 清理任务真实执行过期上传票据、过期下载票据、到期回收站引用和无引用物理对象
- 清理任务执行写入审计记录

当前尚未自动化覆盖：

- 真实 `MINIO` Provider

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080` |
| JDK | `17` |
| MySQL | `workforce_hub_test`（自动化）或 `workforce_hub`（手工联调） |
| Redis | 已启动，用于会话、幂等键和限流键 |
| 存储 Provider | 自动化优先 `LOCAL`，联调补 `MINIO` |
| MinIO Bucket | `workforce-hub` |
| 会话模式 | Spring Session + Redis |

---

## 四、测试数据建议

### 4.1 基础账号

| 类型 | 登录名 | 密码 | 用途 |
| --- | --- | --- | --- |
| 后台管理员 | `admin.demo` | `Admin@123456` | 配额策略、回收站治理、清理任务 |
| 员工账号 A | `zhangsan.employee` | `Employee@123456` | 个人文件上传、下载、删除 |
| 员工账号 B | `lisi.manager` | `Employee@123456` | 权限隔离测试 |

### 4.2 测试文件

| 文件编号 | 文件名 | 内容 | 预期 hash 口径 |
| --- | --- | --- | --- |
| `FILE-A1` | `请假证明.pdf` | 固定小文件内容 A | `sha256A + sizeA` |
| `FILE-A2` | `病假材料.pdf` | 与 `FILE-A1` 二进制完全相同 | 同 `sha256A + sizeA` |
| `FILE-B1` | `员工手册.pdf` | 固定小文件内容 B | `sha256B + sizeB` |
| `FILE-LARGE` | `超大文件.zip` | 超过场景限制 | 用于大小拦截 |

说明：

- `FILE-A1` 和 `FILE-A2` 文件名不同，但二进制内容相同，应命中同一个物理文件对象。
- 自动化测试可在测试代码中生成字节数组，不依赖仓库提交二进制样本。

---

## 五、测试覆盖策略

### 5.1 功能覆盖

- 创建上传票据
- 上传确认
- 秒传
- 配额查询
- 下载票据
- 回收站删除
- 回收站恢复
- 彻底删除
- 清理任务

### 5.2 异常覆盖

- 未登录
- 跨端访问
- 文件类型不允许
- 文件过大
- 个人空间不足
- 空间配额不足
- 企业总池不足
- 存储后端真实容量不足
- 上传票据过期
- hash 不一致
- 下载无权限
- 下载已删除文件
- 恢复超过配额
- 重复确认上传：当前按 `uploadTicketId` 宽松幂等返回原附件引用，不用当前请求体重建引用

### 5.3 契约覆盖

- 统一响应结构
- `requestId` 透传
- `Idempotency-Key` 幂等
- `content_sha256 + file_size` 唯一口径
- 文件名不参与物理去重
- 回收站不立即删除物理对象

---

## 六、测试用例明细

## 6.1 上传票据

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-UP-001` | P0 | `POST /api/employee/files/upload-tickets` | 员工申请个人文件上传票据 | 员工 A 已登录；配额充足 | `FILE-A1` 元数据 | HTTP `200`；`code=0`；返回 `uploadTicketId`、`objectKey`、`uploadUrl` |
| `FILE-UP-002` | P0 | `POST /api/employee/files/upload-tickets` | 文件大小为空被拒绝 | 员工 A 已登录 | `fileSize=0` | HTTP `400`；提示文件大小不能为空 |
| `FILE-UP-003` | P1 | `POST /api/employee/files/upload-tickets` | 文件类型不允许 | 员工 A 已登录；存在 `PERSONAL_FILE` 启用上传策略 | `.exe` + `application/x-msdownload` | HTTP `200`；`code=415`；提示当前场景不支持该文件类型 |
| `FILE-UP-004` | P1 | `POST /api/employee/files/upload-tickets` | 文件超过场景限制 | 员工 A 已登录；存在 `PERSONAL_FILE` 启用上传策略 | `超大文件.zip`，超过 `100MB` | HTTP `200`；`code=413`；提示文件超过当前场景大小限制 |
| `FILE-UP-005` | P0 | `POST /api/employee/files/upload-tickets` | 个人空间不足时拦截 | 员工 A 已登录；个人配额调小 | `FILE-B1` | HTTP `507`；提示个人云空间不足 |
| `FILE-UP-006` | P0 | `POST /api/employee/files/upload-tickets` | 企业总池不足时拦截 | 企业总池已满 | `FILE-B1` | HTTP `507`；提示企业文件总空间不足 |
| `FILE-UP-007` | P1 | `POST /api/employee/files/upload-tickets` | 重复 `Idempotency-Key` 返回同一票据 | 员工 A 已登录 | 同一请求头和请求体重复提交 | HTTP `200`；返回同一 `uploadTicketId` |
| `FILE-UP-008` | P0 | `POST /api/employee/files/upload-tickets` | 员工端第一版拒绝跨业务归属 | 员工 A 已登录 | `ownerType=WF_REQUEST`、`ownerId=90001` | HTTP `200`；`code=400`；提示员工端第一版仅支持个人文件 |

## 6.2 上传确认与去重

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-CFM-001` | P0 | `POST /api/employee/files/confirm-upload` | 正常确认上传 | 已创建票据；对象已写入存储 | `FILE-A1` 确认参数 | HTTP `200`；`code=0`；创建 `sys_file_object` 和 `sys_attachment_ref` |
| `FILE-CFM-002` | P0 | `POST /api/employee/files/confirm-upload` | hash 不一致被拒绝 | 已创建票据；对象已写入存储 | 错误 `contentSha256` | HTTP `422`；提示文件校验失败 |
| `FILE-CFM-003` | P0 | `POST /api/employee/files/confirm-upload` | 重复确认保持幂等 | `FILE-CFM-001` 已成功 | 重复同一确认请求 | HTTP `200`；返回同一附件引用或明确已确认结果 |
| `FILE-CFM-004` | P0 | `POST /api/employee/files/upload-tickets` | 同内容不同文件名触发秒传 | `FILE-A1` 已确认 | `FILE-A2` 元数据 | HTTP `200`；`deduplicated=true`；不创建第二条物理对象 |
| `FILE-CFM-005` | P0 | 服务层并发用例 | 同一文件并发确认只生成一条物理对象 | 两个票据指向同一 hash 和大小 | 并发确认 | 仅一条 `sys_file_object`；两条 `sys_attachment_ref` |
| `FILE-CFM-006` | P1 | `POST /api/employee/files/confirm-upload` | 票据过期后不能确认 | 票据已过期 | 确认请求 | HTTP `409`；提示上传票据已过期 |
| `FILE-CFM-007` | P1 | `POST /api/employee/files/confirm-upload` | 非票据创建人不能确认 | 员工 B 登录 | 员工 A 的票据 | HTTP `403`；提示无权确认该上传票据 |
| `FILE-CFM-008` | P0 | `POST /api/employee/files/confirm-upload` | 待确认状态下重复确认仍保持幂等 | 已有 `source_upload_ticket_id` 附件引用；票据状态异常回到待确认 | 同一确认请求再次提交 | HTTP `200`；返回原 `attachmentRefId`；附件引用和用量账本不重复 |
| `FILE-CFM-009` | P0 | `POST /api/employee/files/upload-tickets` | 员工秒传重复客户端请求 ID 不重复加账 | 已存在同 hash 和大小的物理对象 | 同一 `clientRequestId` 重复请求秒传 | HTTP `200`；返回同一秒传票据和同一附件引用；个人与企业用量不重复增加 |
| `FILE-ADM-UP-001` | P0 | `POST /api/admin/files/upload-tickets` | 后台申请治理文件上传票据 | 管理员已登录；存在 `ADMIN_FILE` 启用上传策略；企业总池充足 | `ADMIN_FILE` 文件元数据 | HTTP `200`；`code=0`；返回 `uploadTicketId`、`objectKey`、`uploadUrl` |
| `FILE-ADM-CFM-001` | P0 | `POST /api/admin/files/confirm-upload` | 后台确认治理文件上传 | 已创建后台票据；对象已写入存储 | 确认参数 | HTTP `200`；`code=0`；创建 `uploader_type=ADMIN` 的附件引用；只增加 `GLOBAL` 用量 |
| `FILE-ADM-CFM-002` | P0 | `POST /api/admin/files/upload-tickets` | 后台同内容不同文件名触发秒传 | 后台文件已确认 | 同 hash 与大小、不同文件名 | HTTP `200`；`deduplicated=true`；不创建第二条物理对象 |
| `FILE-ADM-CFM-003` | P0 | `POST /api/admin/files/upload-tickets` | 后台秒传重复客户端请求 ID 不重复加账 | 后台文件已确认；企业总池充足 | 同一 `clientRequestId` 重复请求秒传 | HTTP `200`；返回同一秒传票据和同一附件引用；`GLOBAL` 用量和附件引用数不重复增加 |
| `FILE-ADM-CFM-004` | P0 | `POST /api/admin/files/upload-tickets` | 后台不能仅凭员工文件 hash 直接秒传 | 员工个人文件已确认；管理员知道相同 hash 和大小 | 后台以同 hash 和大小申请上传票据 | HTTP `200`；`deduplicated=false`；返回普通上传票据；不创建后台附件引用 |
| `FILE-ADM-CFM-005` | P1 | `POST /api/admin/files/upload-tickets` | 后台上传第一版不承载知识库或文档文件 | 管理员已登录；存在 `ADMIN_FILE` 策略 | `ownerType=KB_DOCUMENT` 或 `DOC_DOCUMENT` | HTTP `200`；`code=400`；提示后台第一版仅支持后台治理文件 |

## 6.3 配额查询

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-QTA-001` | P0 | `GET /api/employee/files/quota` | 员工查询个人云空间 | 员工 A 已登录 | 无 | HTTP `200`；返回个人配额、已用、企业总池摘要 |
| `FILE-QTA-002` | P1 | `GET /api/employee/files/quota` | 企业进入写入保护时提示原因 | 后端容量低于安全预留 | 无 | HTTP `200`；`uploadWritable=false`；返回 `blockReason` |
| `FILE-QTA-003` | P0 | `GET /api/admin/files/storage-summary` | 后台查询存储摘要 | 管理员已登录 | 无 | HTTP `200`；返回长期池、缓存池、真实后端容量 |

## 6.4 下载票据

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-DL-001` | P0 | `POST /api/employee/files/{attachmentRefId}/download-tickets` | 有权限用户申请下载票据 | 员工 A 拥有附件引用 | `downloadType=SINGLE` | HTTP `200`；返回短期 `downloadUrl` |
| `FILE-DL-002` | P0 | `POST /api/employee/files/{attachmentRefId}/download-tickets` | 无权限用户不能申请下载 | 员工 B 登录 | 员工 A 的个人文件 | HTTP `403`；提示无权访问该文件 |
| `FILE-DL-003` | P0 | `GET /api/employee/files/download/{ticketNo}` | 过期下载票据不能下载 | 下载票据已过期 | 访问下载入口 | HTTP `200`；`code=410`；提示下载链接已过期 |
| `FILE-DL-004` | P1 | `POST /api/employee/files/{attachmentRefId}/download-tickets` | 已进回收站文件不能下载 | 附件引用已删除 | 申请下载 | HTTP `404` 或 `409`；提示文件已删除 |
| `FILE-DL-005` | P1 | 审计断言 | 下载票据创建写审计 | `FILE-DL-001` 成功 | 查询审计 | 存在下载票据创建审计记录 |
| `FILE-DL-006` | P0 | `GET /api/employee/files/download/{ticketNo}` | 有效下载票据返回原始文件并标记已使用 | 下载票据有效且文件对象存在 | 访问下载入口 | HTTP `200`；Body 等于原始文件字节；票据状态更新为 `USED`；写入下载执行审计 |
| `FILE-DL-007` | P0 | 并发集成测试 | 员工端同一下载票据只能成功执行一次 | 员工下载票据有效；两个请求并发访问同一票据 | 并发访问员工下载入口 | 仅一个请求返回文件；另一个返回 `code=409`；票据最终为 `USED` |
| `FILE-ADM-DL-001` | P0 | `GET /api/admin/files` | 后台查询有效治理文件列表 | 管理员已登录；存在 `ADMIN_FILE` 有效文件 | 分页查询 | HTTP `200`；只返回 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的文件 |
| `FILE-ADM-DL-002` | P0 | `POST /api/admin/files/{attachmentRefId}/download-tickets` | 后台申请治理文件下载票据 | 管理员已登录；后台治理文件未删除 | `downloadType=SINGLE` | HTTP `200`；返回 `/api/admin/files/download/{ticketNo}` |
| `FILE-ADM-DL-003` | P0 | `GET /api/admin/files/download/{ticketNo}` | 后台有效下载票据返回原始文件 | 下载票据有效且文件对象存在 | 访问后台下载入口 | HTTP `200`；Body 等于原始文件字节；票据状态更新为 `USED`；写入后台下载执行审计 |
| `FILE-ADM-DL-004` | P0 | 并发集成测试 | 后台同一下载票据只能成功执行一次 | 后台下载票据有效；两个请求并发访问同一票据 | 并发访问后台下载入口 | 仅一个请求返回文件；另一个返回 `code=409`；票据最终为 `USED` |
| `FILE-ADM-DL-005` | P1 | `GET /api/admin/files` | 后台列表默认隐藏存储内部字段 | 管理员已登录；存在后台治理文件 | 分页查询 | 响应不包含 `contentSha256`、`storageProvider`、`bucketName`、`objectKey` |
| `FILE-ADM-DL-006` | P1 | `GET /api/admin/files/download/{ticketNo}` | 非法 MIME 降级为二进制下载 | 历史数据或手工数据写入非法 MIME | 访问后台下载入口 | HTTP `200`；`Content-Type=application/octet-stream`；Body 等于原始文件字节 |

## 6.5 回收站

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-TRASH-001` | P0 | `POST /api/employee/files/{attachmentRefId}/trash` | 文件移入回收站 | 员工 A 拥有附件引用 | 删除原因 | HTTP `200`；写入 `deletedAt` 和 `purgeAfter` |
| `FILE-TRASH-002` | P0 | `POST /api/employee/files/{attachmentRefId}/restore` | 回收站文件恢复 | 文件在保留期内 | 恢复原因 | HTTP `200`；清空删除标记 |
| `FILE-TRASH-003` | P0 | `DELETE /api/employee/files/{attachmentRefId}` | 彻底删除回收站引用 | 文件已进入回收站 | 无 | HTTP `200`；业务引用彻底删除；物理对象按引用数决定是否延迟清理 |
| `FILE-TRASH-004` | P1 | `POST /api/employee/files/{attachmentRefId}/restore` | 恢复时配额不足被拒绝 | 文件在回收站；当前配额被调小 | 恢复原因 | HTTP `507`；提示恢复会超过当前配额 |
| `FILE-TRASH-005` | P1 | `POST /api/employee/files/{attachmentRefId}/trash` | 非所有者不能删除个人文件 | 员工 B 登录 | 员工 A 的附件引用 | HTTP `403` |
| `FILE-TRASH-006` | P1 | 审计断言 | 删除与恢复写审计 | 删除和恢复已执行 | 查询审计 | 存在删除、恢复审计记录 |
| `FILE-TRASH-007` | P0 | 并发集成测试 | 员工端同一附件彻底删除只释放一次用量 | 员工文件已进入回收站；个人空间和企业总池还有其他文件作为账本哨兵 | 两个请求并发彻底删除同一附件 | 仅一个请求 `code=0`；另一个 `code=404`；`USER` 与 `GLOBAL` 只扣减目标文件一次 |
| `FILE-ADM-TRASH-006` | P0 | 并发集成测试 | 后台同一附件彻底删除只释放一次用量 | 后台文件已进入回收站；企业总池还有其他文件作为账本哨兵 | 两个请求并发彻底删除同一附件 | 仅一个请求 `code=0`；另一个 `code=404`；`GLOBAL` 只扣减目标文件一次 |

## 6.6 后台配额治理

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-POL-001` | P0 | `POST /api/admin/files/quota-policies` | 创建用户默认配额策略 | 系统管理员已登录 | `USER_DEFAULT_10G` | HTTP `200`；返回策略详情 |
| `FILE-POL-002` | P0 | `GET /api/admin/files/quota-policies` | 查询配额策略列表 | 已创建策略 | 按 `scopeType` 查询 | HTTP `200`；返回策略数组 |
| `FILE-POL-003` | P0 | `PUT /api/admin/files/quota-policies/{id}` | 更新单用户覆盖配额 | 已创建用户覆盖策略 | 调整 `quotaBytes` | HTTP `200`；策略更新 |
| `FILE-POL-004` | P1 | `PUT /api/admin/files/quota-policies/{id}` | 预警阈值大于阻断阈值被拒绝 | 管理员已登录 | `warningThreshold=1.0`、`blockThreshold=0.8` | HTTP `400`；提示阈值非法 |
| `FILE-POL-005` | P1 | `PUT /api/admin/files/quota-policies/{id}` | 配额低于已用量被拒绝 | 当前作用域已有使用量 | 调小到小于已用 | HTTP `409`；提示配额不能低于当前已用 |
| `FILE-POL-006` | P1 | `POST /api/admin/files/quota-policies` | 配额策略编码重复被拒绝 | 已存在相同 `policyCode` | 重复编码创建另一策略 | HTTP `200`；`code=409`；提示配额策略编码已存在 |
| `FILE-POL-007` | P1 | `POST /api/admin/files/quota-policies` | 同一作用域重复策略被拒绝 | 已存在同一 `scopeType + scopeId` 策略 | 创建同作用域策略 | HTTP `200`；`code=409`；提示同一作用域已存在配额策略 |

## 6.7 后台回收站与清理

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-ADM-TRASH-001` | P0 | `POST /api/admin/files/{attachmentRefId}/trash` | 管理员将后台治理文件移入回收站 | 管理员已登录；存在 `ADMIN_FILE` 有效文件 | 删除原因 | HTTP `200`；写入 `deletedAt` 和 `purgeAfter`；不释放 `GLOBAL` 用量 |
| `FILE-ADM-TRASH-002` | P0 | `GET /api/admin/files/recycle-bin` | 管理员查询后台治理文件回收站 | 管理员已登录；存在后台回收站文件 | 分页查询 | HTTP `200`；只返回 `ownerType=ADMIN_FILE` 且 `uploaderType=ADMIN` 的回收站文件 |
| `FILE-ADM-TRASH-003` | P0 | `POST /api/admin/files/{attachmentRefId}/restore` | 管理员恢复后台治理文件 | 文件在保留期内 | 恢复原因 | HTTP `200`；清空删除标记 |
| `FILE-ADM-TRASH-004` | P0 | `DELETE /api/admin/files/{attachmentRefId}` | 管理员彻底删除后台治理文件 | 文件已进入回收站 | 无 | HTTP `200`；引用彻底删除；释放 `GLOBAL` 用量；按引用数决定是否延迟物理清理 |
| `FILE-ADM-TRASH-005` | P1 | 审计断言 | 后台删除、恢复、彻底删除写审计 | 后台生命周期动作已执行 | 查询审计 | 存在 `FILE_ADMIN_TRASHED`、`FILE_ADMIN_RESTORED`、`FILE_ADMIN_PURGED` 审计记录 |
| `FILE-CLEAN-001` | P0 | `POST /api/admin/files/cleanup-jobs/run` | dry-run 清理任务 | 存在过期上传票据、过期下载票据、到期回收站引用和无引用物理对象 | `dryRun=true` | HTTP `200`；返回预计清理数量；不修改票据、引用或物理对象 |
| `FILE-CLEAN-002` | P0 | `POST /api/admin/files/cleanup-jobs/run` | 执行过期清理任务 | 存在过期票据、到期回收站引用和已到期无引用物理对象 | `dryRun=false` | HTTP `200`；`jobStatus=SUCCESS`；过期票据标记为 `EXPIRED`；到期回收站引用被彻底删除并释放用量；已到期无引用物理对象被删除；运行表和明细表记录成功 |
| `FILE-CLEAN-003` | P1 | 审计断言 | 清理任务写审计 | 清理任务已执行 | 查询审计 | 存在 `FILE_CLEANUP_JOB_RUN` 审计记录 |
| `FILE-CLEAN-004` | P1 | `POST /api/admin/files/cleanup-jobs/run` | 物理删除失败可追踪 | 存在对象存储 Provider 不可用的到期无引用物理对象 | `dryRun=false` | HTTP `200`；`jobStatus=PARTIAL_FAILED`；`failedPhysicalObjects=1`；运行明细记录失败原因 |

## 6.8 鉴权与契约

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `FILE-GUARD-001` | P0 | `POST /api/employee/files/upload-tickets` | 未登录不能创建上传票据 | 无会话 | 正常请求体 | HTTP `401` |
| `FILE-GUARD-002` | P0 | `GET /api/admin/files/storage-summary` | 员工不能访问后台存储摘要 | 员工 A 已登录 | 无 | HTTP `403` |
| `FILE-GUARD-003` | P0 | `POST /api/admin/files/quota-policies` | 非系统管理员不能创建配额策略 | 普通后台账号已登录 | 正常请求体 | HTTP `403` |
| `FILE-CONTRACT-001` | P1 | 任一成功接口 | `requestId` 透传 | 传 `X-Request-Id` | 任一成功请求 | 响应头和响应体 `requestId` 一致 |
| `FILE-CONTRACT-002` | P1 | 写接口 | 幂等键重复提交不产生重复数据 | 传同一 `Idempotency-Key` | 重复请求 | 数据库记录不重复 |

---

## 七、自动化测试建议

后续代码落地时建议新增：

- `server/src/test/java/com/workforcehub/file/ModuleFileApiIntegrationTest.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileQuotaIntegrationTest.java`
- `server/src/test/java/com/workforcehub/file/ModuleFileCleanupIntegrationTest.java`

建议分层：

| 测试类 | 覆盖重点 |
| --- | --- |
| `ModuleFileApiIntegrationTest` | 上传、确认、秒传、下载、回收站 |
| `ModuleFileQuotaIntegrationTest` | 个人配额、组织总池、恢复配额复核 |
| `ModuleFileCleanupIntegrationTest` | 过期票据、回收站到期、无引用物理对象 |

---

## 八、执行顺序建议

1. 准备 `LOCAL` 存储测试目录。
2. 初始化用户、管理员和默认配额策略。
3. 执行 `FILE-UP-*`。
4. 执行 `FILE-CFM-*`。
5. 执行 `FILE-QTA-*`。
6. 执行 `FILE-DL-*`。
7. 执行 `FILE-TRASH-*`。
8. 执行 `FILE-POL-*`。
9. 执行 `FILE-ADM-TRASH-*` 和 `FILE-CLEAN-*`。
10. 最后执行 `FILE-GUARD-*` 与 `FILE-CONTRACT-*`。

## 九、回归通过标准

以下条件全部满足，视为 `module-file` 第一版接口回归通过：

- 所有 P0 用例通过。
- 上传确认不出现重复物理对象。
- 文件名不同但内容相同的文件能复用物理对象。
- 配额不足时返回明确业务提示。
- 下载必须经过短期票据。
- 回收站删除和恢复不误删物理对象。
- 清理任务 dry-run 与真实执行结果可解释。
- 关键动作有审计记录。
