# 插件化工作台 - API设计

## 当前已实现 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/employee/plugins/mine` | 当前员工可见且可加载的 UI 插件清单 |
| POST | `/api/plugin-gateway/{pluginId}/actions/{actionCode}` | 执行已启用插件的动作 |
| GET | `/api/admin/plugins` | 管理员插件列表 |
| PUT | `/api/admin/plugins/{pluginId}/enable` | 启用插件 |
| PUT | `/api/admin/plugins/{pluginId}/disable` | 停用插件 |
| DELETE | `/api/admin/plugins/{pluginId}` | 卸载插件并保留注册记录与配置 |
| GET | `/api/admin/plugins/{pluginId}/config` | 查询指定插件配置 |
| PUT | `/api/admin/plugins/{pluginId}/config/{configKey}` | 创建或按版本更新单个配置 |
| DELETE | `/api/admin/plugins/{pluginId}/config/{configKey}?expectedVersion={version}` | 按版本删除单个配置，返回墓碑版本 |

### 员工插件清单

接口返回 `R<List<EmployeePluginResponse>>`。`data` 中单项示例：

```json
{
  "id": "hello-plugin",
  "name": "Hello Plugin",
  "frontendEntry": "/plugins/hello-plugin/index.js",
  "integrity": "sha384-<64字符Base64摘要>"
}
```

后端只返回 `ACTIVE` 的 `UI` 插件，并按 `ALL / USER / DEPT / POSITION / MANAGER` 可见范围过滤。`frontendEntry` 由宿主按插件 ID 固定生成，不读取任意脚本 URL；非法插件 ID 或 SRI 会被过滤。

### 插件动作网关

请求体是可选 JSON 对象。网关首项是来源校验：优先精确校验 `Origin`，仅在 `Origin` 缺失时回退 `Referer`；`Origin` 不得带路径或查询，`Referer` 可带页面路径但不得带 query/fragment，来源必须命中白名单。拒绝返回 HTTP `403`、业务码 `4003`；匿名请求仍由 Spring Security 返回 `401`。该校验是插件动作 `POST` 的附加来源防线；宿主 SecurityFilterChain 已用 `CookieCsrfTokenRepository` 覆盖 Session 写请求，不能用来源白名单替代 CSRF。

来源白名单配置为 `workforce.plugin.gateway.csrf.allowed-origins`，环境变量为 `WORKFORCE_PLUGIN_GATEWAY_CSRF_ALLOWED_ORIGINS`；`application` 默认仅本机来源，非本地部署必须显式设置。来源通过后，网关再校验插件状态和生命周期入口，绑定当前登录会话并完成插件/动作/用户权限校验；权限通过后才发现 PF4J 动作并校验输入 Schema，最后返回 `ServiceResult<Object>`。拿到员工清单不代表拥有动作调用权限，每次调用都会重新鉴权。

`ActionDescriptor.defaultTimeout` 尚未在网关层执行，`idempotent` 仍只是描述字段，不构成动作自动幂等保证。六类事件均已接入真实业务事务提交后发布，其中 `EMPLOYEE_HIRED` 已通过真实签名 PF4J 订阅处理器完成端到端验证；事件 handler 只接受 `idempotent=true`，由分发器执行有界异步回调和协作式超时中断。

### 管理员插件列表与生命周期

四个接口都要求管理员登录并拥有 `ADMIN_PLUGIN_MANAGE`；`SUPER_ADMIN` 按现有后台权限契约放行。`PUT`、`DELETE` 继承全站 Cookie/Header CSRF，员工身份、匿名请求、缺少权限或 CSRF 不合法都会在进入管理服务前拒绝。

列表和生命周期操作返回 `AdminPluginResponse`，包含插件 ID、名称、描述、版本兼容字段、类型、状态、固定前端入口、发布方和安装/更新时间；不会返回宿主 JAR 路径、JAR/SRI 摘要、签名者 ID、签名字节或内部错误信息。当前响应不包含依赖关系：`plugin.json.dependencies` 的可信解析、`sys_plugin_dependency` 权威写入和管理端依赖展示尚未实现，不能据此把“插件列表（含依赖）”标记完成。

状态语义：

- `enable` 只允许 `INSTALLED / STOPPED`；已经 `ACTIVE` 时直接返回成功，不重复调用底层启用。
- `disable` 只允许 `ACTIVE`；已经 `STOPPED` 时直接返回成功，不重复调用底层停用。
- `uninstall` 允许所有已存在的非终态进入底层恢复流程；`DISABLING` 可用于插件已卸载但最终状态写库失败后的安全重试，已经 `UNINSTALLED` 时直接返回成功，不重复调用底层卸载。
- 插件 ID 格式错误返回 HTTP `400`，不存在返回 `404`，非法状态或已加载 dependent 阻断返回脱敏的 `409`；未分类的数据库或程序异常由全局异常处理返回脱敏的 `500`。

上述是基于持久化终态的重试幂等，不接受幂等键，也不提供跨请求、跨进程的持久化去重。管理服务使用与 `PluginManagerService` 相同的单 JVM 监视器包住“读取状态—判断—执行”，避免并发停用/卸载覆盖终态；底层成功后返回终态写库时间，响应不再依赖二次查询，也不会携带操作前的旧 `updatedAt`。每次成功、阻止或失败操作分别写入 `SUCCESS / BLOCKED / FAIL` 脱敏审计，记录管理员 ID、插件 ID、前后状态和动作类型，不记录路径、摘要或签名；PF4J 生命周期与审计无法组成单一事务，审计写入失败只记录错误，不回滚已经成功的生命周期操作。

HTTP 控制器复用 `PluginManagerService` 的入口关闭、最长 30 秒排空、PF4J stop/unload 和 ClassLoader 关闭协议。真实签名 hello-plugin 的 MockMvc 链路已验证未启用插件直接卸载、ClassLoader 关闭失败保留并重试，以及管理 API 启用、守卫存在在途计数时卸载等待、新网关请求拒绝、排空后 `UNINSTALLED`、wrapper/扩展/守卫清理、`PluginClassLoader.isClosed()` 和审计落库；在途请求通过直接占用真实守卫模拟，未构造阻塞型插件 handler。

### 插件配置管理

三个接口都要求管理员登录并拥有 `ADMIN_PLUGIN_MANAGE`；`SUPER_ADMIN` 继续按现有后台权限契约放行。身份、权限和请求体大小过滤统一使用 Spring MVC 解码后的规范路径，百分号编码或矩阵参数不能绕过 `/api/admin/plugins` 边界。`PUT`、`DELETE` 继承全站 Cookie/Header CSRF，不使用插件动作网关的 Origin/Referer 白名单。员工身份、匿名请求、缺少权限或 CSRF 不合法都会在进入配置服务前拒绝。

`pluginId` 只来自路径并作为数据库命名空间；请求体不能指定其他插件 ID。`configKey` 必须匹配 `^[a-z0-9][a-z0-9._-]{0,127}$`。单个插件最多 128 个配置键命名空间，删除墓碑仍占用原命名空间但可原键重建；单个值或默认值按 UTF-8 计算最多 16 KiB。配置 PUT 请求体在 Nginx 入口和应用 JSON 反序列化前均限制为 256 KiB，避免沿用全站大文件入口的 100 MiB 上限。

`PUT` 请求示例：

```json
{
  "expectedVersion": 0,
  "valueType": "STRING",
  "value": "https://example.internal",
  "defaultValue": null,
  "description": "服务地址",
  "requiresRestart": false
}
```

- `expectedVersion=0` 只用于从未存在过的键；更新必须提交当前正版本，删除后同键重建必须提交 DELETE 返回的墓碑版本。并发请求使用同一版本时只允许一个成功，其余返回 HTTP `409`。删除会保留只含命名空间、键和递增版本的内部墓碑并清空配置内容；DELETE 响应丢失时，用原删除版本重复请求会幂等返回同一墓碑版本且不重复审计。重建沿用下一代版本，因此旧创建、更新和删除请求都不能命中重建后的配置。
- 类型只支持 `STRING / INT / BOOL / SECRET`。`INT` 规范化为 64 位十进制，`BOOL` 只接受 `true` 或 `false`；创建后修改类型必须先删除再重建。
- 非敏感配置的 `value` 与 `defaultValue` 不能同时为 `null`，两者都会按同一类型校验并持久化。允许 `value=null + defaultValue`；此时插件侧读取会回退到默认值，更新时也可用该组合切换为默认值生效。
- `SECRET` 不允许默认值。首次创建和删除后重建必须提交非空值；更新时 `value=null` 只在当前主密钥可用且能解密原密文时保留原密文，缺钥、错钥、损坏或历史明文均返回 HTTP `422`。提交新 `value` 可使用当前主密钥重新设置。
- `requiresRestart` 当前只是插件配置元数据；更新 `ACTIVE` 插件不会自动停用、重启或持久化“待重启”状态。

响应中的敏感配置始终返回 `value=null`、`defaultValue=null`，只通过 `secret=true` 和 `hasValue` 表示状态。数据库使用 AES-256-GCM 随机 nonce 加密，AAD 绑定 `pluginId + NUL + configKey`；部署必须通过 `WORKFORCE_PLUGIN_CONFIG_ENCRYPTION_KEY_BASE64` 提供 Base64 编码的 32 字节主密钥。缺少密钥时普通配置仍可用，所有 `SECRET` PUT 均返回 HTTP `422`；密钥格式非法会使应用启动失败。已有明文或无法解密的敏感行不会回退明文，必须提交新值重新设置或删除。

当前密文没有 key ID 或多密钥能力，也没有自动轮换、重加密或 KMS 集成。数据库备份必须与原主密钥分开备份并成对恢复；直接更换或丢失主密钥会使既有敏感配置不可解密。

`GET` 允许查看包括 `UNINSTALLED` 在内的保留配置；`PUT` 拒绝 `DISABLING` 和 `UNINSTALLED`，允许 `INSTALLED / ACTIVE / STOPPED / ERROR`；`DELETE` 拒绝 `DISABLING`，允许管理员显式清理 `UNINSTALLED` 遗留配置。墓碑不会出现在列表或插件读取结果中，但计入 128 个键命名空间上限，防止循环创建/删除不同键导致表无限增长。成功创建、更新和删除会在同一事务写入脱敏审计，只记录插件 ID、配置键、类型、版本和重启标记，不记录值、密文或值哈希。

## 尚未实现的管理端 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/plugins/upload` | 上传 JAR 和分离签名 |

上传接口、可信依赖视图和管理端页面仍未实现。现有安装能力只可由宿主内部调用 `PluginManagerService.install(Path, PluginRow)`，不能视为已有 HTTP 上传入口。

---

## 相关文档

- [后端架构.md](./后端架构.md) — 插件管理器、通用调用网关
- [前端架构.md](./前端架构.md) — 插件加载器、SRI校验
- [插件规范.md](./插件规范.md) — plugin.json 字段定义
