# Workforce Hub 全站 CSRF 设计

## 目标

为所有基于 `SESSION` Cookie 的宿主写接口启用 Spring Security CSRF，覆盖管理员端、员工端、匿名登录、验证码和密码重置接口；保持对象存储直传、WebSocket 和插件网关既有边界不变。

## 实施前基线

- `SecurityConfig` 当前全局 `csrf.disable`，Session 策略为 `IF_REQUIRED`。
- 双前端全部宿主业务请求统一经过 `shared-web/api-http.ts`，默认发送 `credentials: include`。
- 直接 `fetch`/`XMLHttpRequest` 仅用于文件 GET 预览、远程对象存储 PUT 或 Vite 本地上传桥，不经过 Spring 写接口。
- 插件动作网关已有 Origin/Referer 白名单，WebSocket 握手已有 Origin 与 Session 校验；两者都不能替代全站 CSRF。

## 实施后状态

- `SecurityConfig` 已启用 `CookieCsrfTokenRepository`，未配置任何 `ignoringRequestMatchers`。
- `GET /api/csrf` 匿名可访问，返回 `headerName/token`，设置 `Cache-Control: no-store`；所有宿主不安全方法（POST、PUT、PATCH、DELETE）均进入同一 CSRF 过滤器。
- 登录、验证码、密码重置、登出和插件动作均要求 Cookie/Header 双提交；缺失或错误 token 返回统一 HTTP 403，正确 token 的匿名写请求继续进入认证链并返回 401。
- `shared-web/api-http.ts` 已在两端接线：每次写请求重读 Cookie，缺失时共享 bootstrap Promise；bootstrap 返回的 `headerName` 会在同一 client 的后续请求持续使用，调用方显式 Header 优先，bootstrap 失败可由后续请求重试，业务 403 不自动重放。

## 方案比较

### 方案 A：标准 Cookie Token + 共享客户端自动 bootstrap（采用）

- 后端使用 `CookieCsrfTokenRepository`。
- Cookie 为 `XSRF-TOKEN`，Header 为 `X-XSRF-TOKEN`。
- 匿名 `GET /api/csrf` 生成 token；共享客户端首次写请求发现 Cookie 缺失时自动获取。
- 所有 POST、PUT、PATCH、DELETE 均受保护，不设置路径豁免。

优点：覆盖完整、无初始化竞态、双端只改共享传输层、符合当前同源部署。缺点：首次写请求会多一次安全 GET。

### 方案 B：各前端启动时显式获取 token

在两端 session store 的首次 `me()` 前调用 `/api/csrf`。实现直观，但需要修改两套 API/store，并存在用户操作早于初始化完成的竞态，因此不采用。

### 方案 C：只保护已登录接口或只做 Origin 校验

迁移较少，但会留下登录 CSRF、验证码滥用和匿名密码重置触发风险，也不满足“全站 Spring Security CSRF”，不采用。

## 后端契约

### Spring Security

- 启用 `CookieCsrfTokenRepository.withHttpOnlyFalse()`。
- Cookie 固定 `Path=/`、`SameSite=Lax`，不设置 Domain。
- 使用 `CsrfTokenRequestAttributeHandler` 接收前端发送的原始 Cookie token，避免默认 XOR 处理与 SPA Header 不一致。
- `GET /api/csrf` 为 `permitAll`，但不属于 CSRF ignore；它只读取 `CsrfToken#getToken()` 触发延迟生成。
- 响应继续使用统一 `R`，数据只包含 `headerName` 和 `token`，并设置 `Cache-Control: no-store`。
- 不配置 `ignoringRequestMatchers`。登录、验证码、密码重置、登出和插件动作 POST 都必须带 token。

### 错误语义

- 不安全请求缺失或 token 不匹配：HTTP 403、现有禁止访问业务码和 JSON 格式。
- 匿名请求携带正确 token 后仍由认证链返回 401。
- GET/HEAD/OPTIONS/TRACE 不要求 token。

### Token 生命周期

首版 token 使用标准双提交 Cookie，与 Redis Session 独立，不写入 localStorage/sessionStorage，也不作为认证凭证。登录轮换 Session、登出失效 Session 后 token 可以继续复用；本轮不增加自定义 token 轮换，避免引入跨标签页失效和写请求自动重放。

## 前端契约

`shared-web/api-http.ts` 在每次不安全请求前执行：

1. 调用方已显式提供当前 CSRF Header（默认 `X-XSRF-TOKEN`）时保持原值，不覆盖。
2. 从 `document.cookie` 精确读取并 URL 解码 `XSRF-TOKEN`。
3. Cookie 不存在时，用同一 `fetcher` 请求 `GET /api/csrf`；并发首次写请求共享同一个 Promise。
4. 将 token 写入后端返回的 `headerName`，并在同一 client 的后续写请求继续使用该名称；默认是 `X-XSRF-TOKEN`。
5. 浏览器 Cookie 优先于 bootstrap 缓存，保证服务端以后更新 Cookie 时下一次请求读取最新值。

Cookie 缺失、损坏编码或无 `document` 的测试/非浏览器环境不得直接抛错；bootstrap 响应体提供 token 作为回退。

## 不携带宿主 CSRF Header 的请求

- 管理端 RAGFlow 文件：同源/本地桥 fetch 使用 `credentials: 'omit'`，跨源预签名 URL 使用 `withCredentials=false` 的 XHR 并报告进度；远程、同源和桥接分支均过滤大小写变体的 `X-XSRF-TOKEN`，不发送浏览器 `SESSION` Cookie。
- 员工端文件 fetch PUT：远程预签名 URL或 `/__employee-local-upload`，使用 `credentials: 'omit'` 并过滤大小写变体的 `X-XSRF-TOKEN`，不发送浏览器 `SESSION` Cookie。
- 文件预览和下载 GET 可按既有会话策略发送凭证，但不附加 CSRF Header。
- WebSocket Upgrade。

不得向对象存储 URL 附加 `SESSION` 或 `X-XSRF-TOKEN`，避免泄漏 token、破坏签名或触发额外 CORS 预检。

## 测试设计

### 真实后端协议

已新增独立集成测试，使用真实 `XSRF-TOKEN` Cookie 和 `X-XSRF-TOKEN` Header，覆盖：

- `/api/csrf` 的响应、Cookie 属性和禁止缓存。
- 管理员/员工登录缺 token、错误 token、正确 token。
- 匿名写接口携带正确 token 后由 403 进入 401 认证边界。
- 已登录 GET 不要求 token。
- 已登录登出缺 token、错误 token、正确 token，会话状态保持或失效符合预期。
- 匿名验证码、密码重置写入口缺 token 均为 403。
- CSRF 403 保留请求 ID 和统一 JSON。

### 既有 MockMvc 回归

既有业务测试不是 CSRF 协议测试。测试基类统一为自动配置的 MockMvc 增加有效 `csrf().asHeader()`，保证原有 403/401/业务错误仍真正进入对应过滤器或控制器；独立 CSRF 测试自行构建不带默认 token 的原始 MockMvc。唯一直接加载 `SecurityConfig` 的 WebMvc 切片测试显式附加有效 token。

### 前端

- 当前共享客户端暴露的 POST/PUT/DELETE 注入，GET 不注入；Spring 后端同时保护未来可能出现的 PATCH。
- Cookie 精确匹配、URL 解码、损坏编码、无 `document`。
- 调用方 Header 优先、每次重读新 Cookie、动态 Header 名持续生效、并发 bootstrap 只发一次 GET、失败后可重试且 403 不重放。
- 远程对象存储和 Vite 上传桥不发送浏览器 `SESSION` Cookie 或宿主 `X-XSRF-TOKEN` Header；管理端上传 helper 与员工端传输 composable 均有可执行 fetch/XHR 测试。
- `shared-web` 改动后运行 admin-web、employee-web 全量测试和构建。

## 部署边界

- 当前 Vite `/api` 代理和 Nginx `/api/` 反代均为同源模型，方案直接适用。
- 首版不支持页面与 API 分属不同站点的部署；`VITE_API_BASE_URL` 跨站使用需另行设计 CORS、SameSite 与 Secure Cookie。
- `localhost` 与 `127.0.0.1` 是不同 Cookie 主机，联调必须保持页面和 API 主机一致。
- 生产 TLS 终止必须正确传递并识别 `X-Forwarded-Proto`，再验证 Cookie `Secure` 属性。

## 非本轮范围

- 修改前端端口。
- 修改 Docker Compose 服务拓扑。
- 删除插件网关 Origin/Referer 或 WebSocket Origin 防护。
- 为对象存储直传添加宿主认证 Header。
- 跨站部署、强制 token 轮换、自动重放被 CSRF 拒绝的写请求。
