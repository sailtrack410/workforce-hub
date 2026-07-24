# 插件化工作台 - API设计

## 管理端 API

| 方法   | 路径                            | 说明     |
|--------|---------------------------------|----------|
| GET    | /api/admin/plugins              | 插件列表 |
| POST   | /api/admin/plugins/upload       | 上传JAR  |
| PUT    | /api/admin/plugins/{id}/enable  | 启用     |
| PUT    | /api/admin/plugins/{id}/disable | 停用     |
| DELETE | /api/admin/plugins/{id}         | 卸载     |
| GET    | /api/admin/plugins/{id}/config  | 获取配置 |
| PUT    | /api/admin/plugins/{id}/config  | 更新配置 |

权限码：`ADMIN_PLUGIN_MANAGE`

## 员工端 API

| 方法 | 路径                       | 说明             |
|------|----------------------------|------------------|
| GET  | /api/employee/plugins/mine | 可用的UI插件列表 |

响应示例：
```json
[
  {
    "id": "leave-plugin",
    "name": "假期管理",
    "icon": "Calendar",
    "directory": "人事管理",
    "frontendEntry": "/plugins/leave/index.js"
  }
]
```

## 权限说明

- 管理端：需要 `ADMIN_PLUGIN_MANAGE` 权限码
- 员工端：后端根据用户身份过滤，返回可用插件清单
- API调用：每次调用都需重新鉴权，不能因拿到清单就默认允许

---

## 相关文档

- [后端架构.md](./后端架构.md) — 插件管理器、通用调用网关
- [前端架构.md](./前端架构.md) — 插件加载器、SRI校验
- [插件规范.md](./插件规范.md) — plugin.json 字段定义
