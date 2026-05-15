# API 文档 module-auth 认证接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-auth` 认证接口 API 文档 |
| 文档版本 | `v1.0` |
| 适用范围 | 后台管理员认证、员工认证、邮箱验证码、邮箱绑定、密码重置 |
| 对应模块 | `server/module-auth` |
| 发布日期 | `2026-05-02` |
| 当前状态 | 已随代码与自动化测试落地，可用于联调与回归 |

---

## 二、文档范围

本文档覆盖以下 `9` 个接口：

### 1. 后台端

- `POST /api/admin/auth/login`
- `POST /api/admin/auth/logout`
- `GET /api/admin/auth/me`

### 2. 员工端

- `POST /api/employee/auth/login`
- `POST /api/employee/auth/logout`
- `GET /api/employee/auth/me`
- `POST /api/employee/auth/email/send-code`
- `POST /api/employee/auth/email/bind`
- `POST /api/employee/auth/password/reset`

---

## 三、调用约定

## 3.1 基础地址

开发环境默认：

```text
http://localhost:8080
```

本地找回密码、登录邮箱二验和绑定邮箱链路额外依赖 SMTP。后端从 `server/` 启动时会自动读取仓库根目录 `.env`；未配置真实 SMTP 时使用默认本地调试 SMTP：

```text
SMTP: 127.0.0.1:1025
```

如果本地没有启动调试 SMTP，`POST /api/employee/auth/email/send-code` 会返回业务失败：

- `code=500`
- `message=邮件发送失败，请稍后重试`

推荐本地调试命令：

```bash
python3 server/scripts/dev/start_debug_smtp.py
```

## 3.2 鉴权方式

当前项目采用服务端 Session 鉴权，不使用 Bearer Token。

- 登录成功后会写入 `SESSION` Cookie
- 后续请求通过 `Cookie: SESSION=...` 维持会话
- 员工端会话与后台端会话共享同一套 Session 存储，但通过路径强校验防止串端访问

### 当前权限边界说明

- 后台端认证接口：仅后台管理员会话可访问 `me`、`logout`
- 员工端认证接口：仅员工会话可访问 `me`、`email/bind`
- `email/send-code` 与 `password/reset` 当前允许匿名调用
- 员工端没有前台自注册接口
- 员工会话访问后台端路径会被拒绝
- 后台会话访问员工端路径会被拒绝

### 员工账号开通说明

当前产品口径是“员工账号由企业管理员后台创建并开通”，不是 C 端自助注册。

前端如需展示“开通账号”入口，应明确提示：

- 员工账号不支持前台自注册
- 需联系企业管理员通过后台创建账号

## 3.3 请求头约定

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | `POST` 时是 | `application/json` | 请求体类型 |
| `Cookie` | 需登录接口时是 | `SESSION=...` | 会话维持 |
| `X-Request-Id` | 否 | `auth202605020001` | 请求链路 ID；不传则服务端自动生成 |

## 3.4 响应头约定

| Header | 说明 |
| --- | --- |
| `X-Request-Id` | 服务端回写的请求链路 ID |

## 3.5 响应体格式

所有接口统一返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "requestId": "cb4cb41d69424374a9df7c1d7775287a"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `int` | 业务码，`0` 表示成功 |
| `message` | `string` | 业务提示 |
| `data` | `object/null` | 业务数据 |
| `requestId` | `string` | 请求链路 ID |

## 3.6 错误处理约定

当前认证接口的错误有两种出口：

### 1. Security 过滤器直接返回 HTTP 错误

适用于：

- 未登录访问受保护接口：HTTP `401`
- 会话身份与路径不匹配：HTTP `403`
- 参数校验失败：HTTP `400`

### 2. Controller / Service 抛出业务异常，返回 HTTP 200 + 业务码

适用于：

- 登录失败
- 登录频控锁定
- 员工生命周期不允许登录
- 验证码频控
- 验证码错误
- 邮箱未绑定等业务错误

联调时需要同时看：

- HTTP 状态码
- 响应体中的 `code`

### 常见错误码

| HTTP 状态 | 业务码 | message 示例 | 场景 |
| --- | --- | --- | --- |
| `200` | `0` | `ok` | 成功 |
| `200` | `401` | `用户名或密码错误，剩余尝试次数 4` | 登录失败 |
| `200` | `403` | `员工已离职，无法登录` | 生命周期不允许登录 |
| `200` | `429` | `登录失败次数过多，账号已锁定 30 分钟` | 登录频控锁定 |
| `200` | `429` | `验证码发送过于频繁，请稍后再试` | 验证码频控 |
| `400` | `400` | `邮箱格式不正确` | 参数校验失败 |
| `401` | `401` | `未登录或会话已过期` | 未登录访问受保护接口 |
| `403` | `403` | `当前账号不是员工身份，无法访问员工端` | 跨端访问 |
| `500` | `500` | `邮件发送失败，请稍后重试` 或 `系统内部错误` | 系统异常 |

---

## 四、数据模型定义

## 4.1 LoginRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `loginName` | `string` | 是 | 登录名 |
| `password` | `string` | 是 | 密码 |

## 4.2 LoginResponse

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `userId` | `long` | 是 | 当前登录用户 ID |
| `userType` | `string` | 是 | `ADMIN` 或 `USER` |
| `displayName` | `string` | 是 | 展示名 |
| `loginName` | `string` | 是 | 登录名 |

## 4.3 CurrentUserResponse

公共字段：

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `userId` | `long` | 是 | 当前登录用户 ID |
| `userType` | `string` | 是 | `ADMIN` 或 `USER` |
| `displayName` | `string` | 是 | 展示名 |
| `loginName` | `string` | 是 | 登录名 |

员工端额外字段：

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `primaryDeptId` | `long/null` | 是 | 主部门 ID |
| `positionId` | `long/null` | 是 | 岗位 ID |
| `managerUserId` | `long/null` | 是 | 直属上级 ID |
| `employmentStatus` | `string/null` | 是 | 在职状态 |

## 4.4 SendCodeRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | `string` | 是 | 邮箱地址 |
| `purpose` | `string` | 是 | `BIND` 或 `RESET_PASSWORD` |

## 4.5 BindEmailRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | `string` | 是 | 待绑定邮箱 |
| `code` | `string` | 是 | 邮箱验证码 |

## 4.6 ResetPasswordRequest

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | `string` | 是 | 已绑定邮箱 |
| `code` | `string` | 是 | 密码重置验证码 |
| `newPassword` | `string` | 是 | 新密码，长度 `8-64` |

注意：

- 当前请求体不需要 `loginName`
- employee-web 当前已按 `email + code + newPassword` 接入

## 4.7 关键枚举说明

### purpose

| 值 | 含义 |
| --- | --- |
| `BIND` | 绑定邮箱 |
| `RESET_PASSWORD` | 找回密码 |

### userType

| 值 | 含义 |
| --- | --- |
| `ADMIN` | 后台管理员 |
| `USER` | 员工/领导 |

### employmentStatus

| 值 | 含义 | 登录准入 |
| --- | --- | --- |
| `PENDING_ONBOARD` | 待入职 | 不允许 |
| `PROBATION` | 试用期 | 允许 |
| `ACTIVE` | 在职 | 允许 |
| `PENDING_OFFBOARD` | 待离职 | 允许 |
| `OFFBOARDED` | 已离职 | 不允许 |

---

## 五、接口清单总表

| 序号 | 方法 | 路径 | 调用方 | 用途 |
| --- | --- | --- | --- | --- |
| 1 | `POST` | `/api/admin/auth/login` | 后台登录页 | 后台账号密码登录 |
| 2 | `POST` | `/api/admin/auth/logout` | 后台已登录用户 | 销毁后台会话 |
| 3 | `GET` | `/api/admin/auth/me` | 后台已登录用户 | 查询当前管理员信息 |
| 4 | `POST` | `/api/employee/auth/login` | 员工登录页 | 员工账号密码登录 |
| 5 | `POST` | `/api/employee/auth/logout` | 员工已登录用户 | 销毁员工会话 |
| 6 | `GET` | `/api/employee/auth/me` | 员工已登录用户 | 查询当前员工身份摘要 |
| 7 | `POST` | `/api/employee/auth/email/send-code` | 登录前 / 登录后 | 发送邮箱验证码 |
| 8 | `POST` | `/api/employee/auth/email/bind` | 员工已登录用户 | 绑定邮箱 |
| 9 | `POST` | `/api/employee/auth/password/reset` | 登录前 | 通过邮箱验证码重置密码 |

---

## 六、接口明细

## 6.1 `POST /api/admin/auth/login`

### 用途

后台管理员登录。

### 请求体

```json
{
  "loginName": "admin.demo",
  "password": "Admin@123456"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 8001,
    "userType": "ADMIN",
    "displayName": "演示管理员",
    "loginName": "admin.demo"
  },
  "requestId": "auth-admin-login-001"
}
```

### 失败响应示例

```json
{
  "code": 401,
  "message": "用户名或密码错误，剩余尝试次数 4",
  "data": null,
  "requestId": "auth-admin-login-002"
}
```

## 6.2 `POST /api/admin/auth/logout`

### 用途

后台退出登录并销毁当前会话。

### 请求体

无

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null,
  "requestId": "auth-admin-logout-001"
}
```

## 6.3 `GET /api/admin/auth/me`

### 用途

查询当前后台管理员信息。

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 8001,
    "userType": "ADMIN",
    "displayName": "演示管理员",
    "loginName": "admin.demo"
  },
  "requestId": "auth-admin-me-001"
}
```

## 6.4 `POST /api/employee/auth/login`

### 用途

员工端账号密码登录。

### 请求体

```json
{
  "loginName": "zhangsan.employee",
  "password": "Employee@123456"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 2003,
    "userType": "USER",
    "displayName": "张三",
    "loginName": "zhangsan.employee"
  },
  "requestId": "auth-emp-login-001"
}
```

### 失败响应示例

生命周期不允许登录：

```json
{
  "code": 403,
  "message": "员工已离职，无法登录",
  "data": null,
  "requestId": "auth-emp-login-002"
}
```

频控锁定：

```json
{
  "code": 429,
  "message": "登录失败次数过多，账号已锁定 30 分钟",
  "data": null,
  "requestId": "auth-emp-login-003"
}
```

## 6.5 `POST /api/employee/auth/logout`

### 用途

员工退出登录并销毁会话。

## 6.6 `GET /api/employee/auth/me`

### 用途

查询当前员工身份摘要。

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "userId": 2003,
    "userType": "USER",
    "displayName": "张三",
    "loginName": "zhangsan.employee",
    "primaryDeptId": 111,
    "positionId": 3001,
    "managerUserId": 2002,
    "employmentStatus": "ACTIVE"
  },
  "requestId": "auth-emp-me-001"
}
```

## 6.7 `POST /api/employee/auth/email/send-code`

### 用途

发送邮箱验证码，当前支持：

- `BIND`
- `RESET_PASSWORD`

### 请求体

```json
{
  "email": "bind.demo@example.com",
  "purpose": "BIND"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null,
  "requestId": "auth-send-code-001"
}
```

### 失败响应示例

```json
{
  "code": 429,
  "message": "验证码发送过于频繁，请稍后再试",
  "data": null,
  "requestId": "auth-send-code-002"
}
```

## 6.8 `POST /api/employee/auth/email/bind`

### 用途

员工绑定邮箱。

### 请求体

```json
{
  "email": "bind.demo@example.com",
  "code": "123456"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null,
  "requestId": "auth-bind-001"
}
```

## 6.9 `POST /api/employee/auth/password/reset`

### 用途

通过邮箱验证码重置密码。

### 请求体

```json
{
  "email": "zhangsan@example.com",
  "code": "123456",
  "newPassword": "Employee@654321"
}
```

### 成功响应

```json
{
  "code": 0,
  "message": "ok",
  "data": null,
  "requestId": "auth-reset-password-001"
}
```

---

## 七、当前实现边界

- 当前只实现基础登录、会话、验证码、邮箱绑定、密码重置
- 当前还没有把真实网易邮箱发信作为自动化测试的一部分
- 当前后台管理员 `me` 只返回基础身份信息，尚未返回角色权限摘要
- 当前登录失败采用“HTTP 200 + 业务码”表达业务异常，这与过滤器层 `401/403` 并存，前后端联调时必须按本文档处理
