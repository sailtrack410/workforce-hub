# API 测试用例 module-auth 认证接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-auth` 认证接口测试用例 |
| 文档版本 | `v1.0` |
| 适用范围 | 登录、会话、邮箱验证码、绑定邮箱、密码重置联调与回归 |
| 发布日期 | `2026-05-02` |

---

## 二、测试目标

本轮测试目标：

- 验证后台端与员工端登录闭环可用
- 验证 `me`、`logout`、路径隔离和未登录拦截生效
- 验证验证码发送、重发频控、邮箱绑定、密码重置可跑通
- 验证员工生命周期不允许登录时能返回明确业务提示
- 验证登录失败频控与 `requestId` 透传能力

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080` |
| JDK | `17` |
| MySQL | `workforce_hub_test`（自动化）或 `workforce_hub`（手工联调） |
| Redis | 已启动 |
| 会话模式 | Spring Session + Redis |
| SMTP | 自动化测试可用 Mock；手工联调可用本地 MailHog/Mailpit 或真实 163 SMTP |

---

## 四、测试数据建议

### 4.1 推荐初始化方式

当前仓库已提供种子脚本：

- `server/scripts/sql/seed_module_org_smoke_data.sql`

该脚本同时初始化了本轮认证接口用到的后台管理员、员工账号和员工档案。

### 4.2 演示账号

| 类型 | 登录名 | 密码 | 说明 |
| --- | --- | --- | --- |
| 后台管理员 | `admin.demo` | `Admin@123456` | 用于后台端认证联调 |
| 员工账号 A | `zhangsan.employee` | `Employee@123456` | 普通员工 |
| 员工账号 B | `lisi.manager` | `Employee@123456` | 直属领导 |
| 员工账号 C | `wangwu.employee` | `Employee@123456` | 试用期员工 |

### 4.3 关键邮箱数据

| 登录名 | 默认邮箱 | 说明 |
| --- | --- | --- |
| `zhangsan.employee` | `zhangsan@example.com` | 用于密码重置与邮箱绑定回归 |
| `lisi.manager` | `lisi@example.com` | 管理型员工 |
| `admin.demo` | `admin.demo@example.com` | 后台管理员邮箱 |

---

## 五、测试覆盖策略

## 5.1 功能覆盖

- 后台登录 / 登出 / `me`
- 员工登录 / 登出 / `me`
- 验证码发送
- 邮箱绑定
- 密码重置

## 5.2 异常覆盖

- 未登录访问受保护接口
- 登录失败
- 登录频控锁定
- 验证码重发过频
- 生命周期不允许登录

## 5.3 契约覆盖

- 统一响应结构
- `requestId` 回写
- `SESSION` Cookie 下发
- `HTTP 401/403` 与 `HTTP 200 + code` 并存语义

---

## 六、测试用例明细

## 6.1 后台端认证

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `AUTH-ADMIN-001` | P0 | `POST /api/admin/auth/login` | 后台管理员正常登录 | 已初始化演示管理员账号 | 正确登录名密码 | HTTP `200`；`code=0`；返回 `SESSION` Cookie |
| `AUTH-ADMIN-002` | P0 | `GET /api/admin/auth/me` | 查询当前后台管理员信息 | 已登录后台管理员 | 无参数 | HTTP `200`；`code=0`；`data.userType=ADMIN` |
| `AUTH-ADMIN-003` | P1 | `POST /api/admin/auth/logout` | 后台退出登录 | 已登录后台管理员 | 无参数 | HTTP `200`；`code=0`；后续再访问 `me` 返回 `401` |

## 6.2 员工端认证

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `AUTH-EMP-001` | P0 | `POST /api/employee/auth/login` | 员工正常登录 | 已初始化员工账号 | 正确登录名密码 | HTTP `200`；`code=0`；返回 `SESSION` Cookie |
| `AUTH-EMP-002` | P0 | `GET /api/employee/auth/me` | 查询当前员工身份摘要 | 已登录员工账号 A | 无参数 | HTTP `200`；`code=0`；返回部门、岗位、直属上级、在职状态 |
| `AUTH-EMP-003` | P1 | `POST /api/employee/auth/logout` | 员工退出登录 | 已登录员工账号 A | 无参数 | HTTP `200`；`code=0`；后续再访问 `me` 返回 `401` |
| `AUTH-EMP-004` | P0 | `POST /api/employee/auth/login` | 员工密码错误 | 已初始化员工账号 A | 错误密码 | HTTP `200`；`code=401`；返回剩余尝试次数 |
| `AUTH-EMP-005` | P0 | `POST /api/employee/auth/login` | 连续输错密码触发锁定 | 连续 5 次错误密码 | 第 6 次继续登录 | HTTP `200`；`code=429`；提示锁定 30 分钟 |
| `AUTH-EMP-006` | P1 | `POST /api/employee/auth/login` | 生命周期不允许登录 | 将员工状态改成 `PENDING_ONBOARD` 或 `OFFBOARDED` | 正确密码 | HTTP `200`；`code=403`；返回明确状态提示 |

## 6.3 邮箱验证码与绑定

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `AUTH-MAIL-001` | P0 | `POST /api/employee/auth/email/send-code` | 正常发送绑定邮箱验证码 | SMTP 可用或已 Mock | `purpose=BIND` | HTTP `200`；`code=0` |
| `AUTH-MAIL-002` | P0 | `POST /api/employee/auth/email/send-code` | 同邮箱同用途短时间内重复发送 | 已成功发送一次验证码 | 再次发送相同请求 | HTTP `200`；`code=429`；提示发送过频 |
| `AUTH-MAIL-003` | P0 | `POST /api/employee/auth/email/bind` | 员工正常绑定邮箱 | 已登录员工账号 A；已拿到绑定验证码 | `email + code` | HTTP `200`；`code=0`；数据库邮箱被更新 |
| `AUTH-MAIL-004` | P1 | `POST /api/employee/auth/email/bind` | 未登录绑定邮箱 | 无会话 | `email + code` | HTTP `401`；`code=401` |
| `AUTH-MAIL-SMOKE-001` | P1 | `EmailCodeService.sendCode` | 真实 SMTP 发信冒烟 | 显式配置 `MAIL_SMOKE_ENABLED=true`、真实 SMTP 变量、MySQL、Redis | `purpose=BIND` | Maven 用例通过；收件邮箱能收到验证码；Redis 写入验证码 |

## 6.4 密码重置

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `AUTH-RESET-001` | P0 | `POST /api/employee/auth/password/reset` | 正常重置密码 | 已发送 `RESET_PASSWORD` 验证码 | `email + code + newPassword` | HTTP `200`；`code=0` |
| `AUTH-RESET-002` | P0 | 登录接口组合 | 新密码立即生效 | 已完成 `AUTH-RESET-001` | 先用旧密码登录，再用新密码登录 | 旧密码登录失败；新密码登录成功 |
| `AUTH-RESET-003` | P1 | `POST /api/employee/auth/password/reset` | 邮箱未绑定账号 | 使用未绑定邮箱 | `email + code + newPassword` | HTTP `200`；`code=404` |

## 6.5 鉴权与契约

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `AUTH-GUARD-001` | P0 | `GET /api/admin/auth/me` | 未登录访问后台 `me` | 无会话 | 无参数 | HTTP `401`；`code=401` |
| `AUTH-GUARD-002` | P0 | `GET /api/employee/auth/me` | 未登录访问员工 `me` | 无会话 | 无参数 | HTTP `401`；`code=401` |
| `AUTH-GUARD-003` | P1 | `GET /api/admin/auth/me` | 员工会话访问后台端 | 已登录员工账号 | 无参数 | HTTP `403`；`code=403` |
| `AUTH-GUARD-004` | P1 | `POST /api/employee/auth/email/bind` | 后台会话访问员工端 | 已登录后台管理员 | `email + code` | HTTP `403`；`code=403` |
| `AUTH-CONTRACT-001` | P1 | 任一成功接口 | 透传 requestId | 主动传 `X-Request-Id` | 任一成功请求 | 响应头与响应体 `requestId` 一致 |

---

## 七、自动化测试现状

当前仓库已落地自动化用例：

- `server/src/test/java/com/workforcehub/auth/ModuleAuthApiIntegrationTest.java`
- `server/src/test/java/com/workforcehub/auth/ModuleAuthMailSmtpSmokeTest.java`

当前自动化已覆盖：

- 后台登录 + `me`
- 员工登录 + `me` + `logout`
- 未登录访问受保护接口
- 验证码发送与频控
- 邮箱绑定
- 密码重置
- 登录失败频控
- 生命周期不允许登录
- 真实 SMTP 发信冒烟入口（默认跳过，避免日常回归误发邮件）

真实 SMTP 冒烟执行命令示例：

```bash
cd server
MAIL_SMOKE_ENABLED=true \
MAIL_HOST=smtp.163.com \
MAIL_PORT=465 \
MAIL_USERNAME=yourname@163.com \
MAIL_PASSWORD=your_smtp_authorization_code \
MAIL_SMTP_AUTH=true \
MAIL_SMTP_SSL_ENABLE=true \
MAIL_SMTP_STARTTLS=false \
MAIL_SMOKE_TO=yourname@163.com \
mvn -Dtest=ModuleAuthMailSmtpSmokeTest test
```

注意：真实授权码只能通过本机环境变量、IDE 运行配置或本机 `.env` 注入，不写入仓库文件。

---

## 八、执行步骤建议

1. 准备 MySQL、Redis、SMTP（或 Mock）
2. 装载 `server/scripts/sql/seed_module_org_smoke_data.sql`
3. 先跑后台端 `AUTH-ADMIN-001` 到 `AUTH-ADMIN-003`
4. 再跑员工端 `AUTH-EMP-001` 到 `AUTH-EMP-006`
5. 跑邮箱与密码链路 `AUTH-MAIL-*`、`AUTH-RESET-*`
6. 最后跑鉴权与契约用例 `AUTH-GUARD-*`、`AUTH-CONTRACT-001`

## 九、回归通过标准

以下条件全部满足，视为本轮认证接口回归通过：

- 所有 P0 用例通过
- 登录链路无 `500`
- 邮箱绑定与密码重置都能完成闭环
- 生命周期禁止登录时返回明确业务提示
- `requestId`、`SESSION` Cookie、跨端拦截语义稳定
