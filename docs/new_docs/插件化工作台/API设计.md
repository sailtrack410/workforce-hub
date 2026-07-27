# 插件化工作台 - API设计

## 当前已实现 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/employee/plugins/mine` | 当前员工可见且可加载的 UI 插件清单 |
| POST | `/api/plugin-gateway/{pluginId}/actions/{actionCode}` | 执行已启用插件的动作 |

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

请求体是可选 JSON 对象。网关先校验插件状态和生命周期入口，再绑定当前登录会话并完成插件/动作/用户权限校验；权限通过后才发现 PF4J 动作并校验输入 Schema，最后返回 `ServiceResult<Object>`。拿到员工清单不代表拥有动作调用权限，每次调用都会重新鉴权。

## 规划中的管理端 API（尚未实现）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/plugins` | 插件列表 |
| POST | `/api/admin/plugins/upload` | 上传 JAR 和分离签名 |
| PUT | `/api/admin/plugins/{id}/enable` | 启用 |
| PUT | `/api/admin/plugins/{id}/disable` | 停用 |
| DELETE | `/api/admin/plugins/{id}` | 卸载 |
| GET | `/api/admin/plugins/{id}/config` | 获取配置 |
| PUT | `/api/admin/plugins/{id}/config` | 更新配置 |

`ADMIN_PLUGIN_MANAGE` 权限码及上述路由当前均未落地，不能作为现有 API 调用。

---

## 相关文档

- [后端架构.md](./后端架构.md) — 插件管理器、通用调用网关
- [前端架构.md](./前端架构.md) — 插件加载器、SRI校验
- [插件规范.md](./插件规范.md) — plugin.json 字段定义
