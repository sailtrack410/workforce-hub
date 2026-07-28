# 全站 CSRF Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为所有宿主不安全 HTTP 方法启用 Spring Security CSRF，并让 admin-web、employee-web 自动取得和发送 token。

**Architecture:** 后端使用 `CookieCsrfTokenRepository` 和匿名 `/api/csrf` bootstrap；双前端通过 `shared-web/api-http.ts` 在共享传输层读取 Cookie、缺失时自动 bootstrap，再注入 Header。真实 Cookie 集成测试验证协议，既有 MockMvc 通过测试专用默认 token 保持业务断言不被 CSRF 短路。

**Tech Stack:** Spring Boot 3.2.5、Spring Security 6、MockMvc、Vue 3、TypeScript、Vitest、Fetch API。

---

### Task 1：后端真实 CSRF 协议 RED

**Files:**
- Create: `server/src/test/java/com/workforcehub/common/GlobalCsrfIntegrationTest.java`

- [ ] **Step 1: 写 `/api/csrf` 与缺 token 的失败测试**

测试使用 `MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build()` 创建不带测试默认 token 的 `rawMockMvc`，断言：

```java
rawMockMvc.perform(get("/api/csrf"))
    .andExpect(status().isOk())
    .andExpect(cookie().exists("XSRF-TOKEN"))
    .andExpect(header().string("Cache-Control", "no-store"))
    .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
    .andExpect(jsonPath("$.data.token").isNotEmpty());

rawMockMvc.perform(post("/api/admin/auth/login")
        .header("X-Request-Id", "csrf-missing-login")
        .contentType(MediaType.APPLICATION_JSON)
        .content(validAdminLoginJson()))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value(100403))
    .andExpect(jsonPath("$.requestId").value("csrf-missing-login"));
```

- [ ] **Step 2: 运行 RED**

```bash
TEST_DB_NAME=workforce_hub_csrf_tdd_20260728 mvn -pl server -am -Dtest=GlobalCsrfIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：`/api/csrf` 为 404，缺 token 登录仍进入现有登录流程，测试失败原因与未启用 CSRF 一致。

- [ ] **Step 3: 补齐真实 Cookie/Header 矩阵**

用 bootstrap 响应取得 token Cookie 和响应 token，覆盖错误 Header、正确 Cookie+Header、匿名有 token 后 401、GET me 无 Header、logout 缺/错/正确 token、公开验证码和密码重置 POST 缺 token。

### Task 2：后端最小实现 GREEN

**Files:**
- Modify: `server/src/main/java/com/workforcehub/common/config/SecurityConfig.java`
- Create: `server/src/main/java/com/workforcehub/common/controller/CsrfController.java`

- [ ] **Step 1: 启用标准 Cookie CSRF**

核心配置：

```java
CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

http.csrf(csrf -> csrf
    .csrfTokenRepository(repository)
    .csrfTokenRequestHandler(requestHandler)
);
```

授权规则加入：

```java
.requestMatchers("/api/csrf").permitAll()
```

不增加任何 ignore matcher。

- [ ] **Step 2: 新增 bootstrap Controller**

```java
@RestController
public class CsrfController {
    @GetMapping("/api/csrf")
    public R<Map<String, String>> csrf(CsrfToken token, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return R.ok(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
}
```

- [ ] **Step 3: 运行 GREEN**

使用 Task 1 命令，预期全部通过。

### Task 3：既有后端测试在真实安全链下回归

**Files:**
- Create: `server/src/test/java/com/workforcehub/support/CsrfMockMvcTestConfiguration.java`
- Modify: `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`
- Modify: `server/src/test/java/com/workforcehub/support/WorkflowFlowableIntegrationTestSupport.java`
- Modify: `server/src/test/java/com/workforcehub/plugin/PluginGatewaySecurityTest.java`

- [ ] **Step 1: 为既有业务测试配置有效 token**

```java
@TestConfiguration(proxyBeanMethods = false)
public class CsrfMockMvcTestConfiguration {
    @Bean
    MockMvcBuilderCustomizer csrfMockMvcBuilderCustomizer() {
        return builder -> builder.defaultRequest(get("/").with(csrf().asHeader()));
    }
}
```

两个集成测试基类通过 `@Import(CsrfMockMvcTestConfiguration.class)` 使用；`PluginGatewaySecurityTest.gatewayRequest()` 显式 `.with(csrf().asHeader())`。

- [ ] **Step 2: 运行安全链定向测试**

```bash
TEST_DB_NAME=workforce_hub_csrf_tdd_20260728 mvn -pl server -am -Dtest=GlobalCsrfIntegrationTest,ModuleAuthApiIntegrationTest,PluginGatewaySecurityTest,PluginGatewayCsrfIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

预期：CSRF 专项、认证和插件双层防护全部通过，原有 403 不因缺 token 假绿。

- [ ] **Step 3: 提交后端批次**

只暂存上述七个生产/测试文件，提交：

```text
feat: 启用全站 CSRF 防护
```

### Task 4：共享前端 CSRF RED

**Files:**
- Modify: `employee-web/src/api/http.test.ts`
- Create: `admin-web/src/api/http.test.ts`

- [ ] **Step 1: 写共享客户端失败测试**

覆盖：

```typescript
it('bootstraps and adds csrf header before the first unsafe request', async () => {
  // 第一次 fetch 返回 /api/csrf token，第二次才是业务 POST。
  // 断言 GET /api/csrf 不带 token，POST 带 X-XSRF-TOKEN。
});

it('reads the latest csrf cookie for every unsafe request', async () => {
  // 两次请求之间改变 document.cookie，断言第二次 Header 使用新 token。
});

it('does not attach csrf to safe or caller-overridden requests', async () => {
  // GET 无 Header；显式 Header 保持原值且不 bootstrap。
});
```

同时覆盖 PUT、DELETE、损坏 Cookie 编码、无 `document` 和并发首次写请求只 bootstrap 一次。

- [ ] **Step 2: 运行 RED**

```bash
cd employee-web && npm run test -- src/api/http.test.ts
cd admin-web && npm run test -- src/api/http.test.ts
```

预期：当前共享客户端未请求 `/api/csrf`、未发送 `X-XSRF-TOKEN`，新增测试失败。

### Task 5：共享前端最小实现 GREEN

**Files:**
- Modify: `shared-web/api-http.ts`

- [ ] **Step 1: 增加不安全方法判断和 Cookie 读取**

```typescript
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE']);

function readCookie(name: string) {
  if (typeof document === 'undefined') return null;
  const prefix = `${name}=`;
  const raw = document.cookie.split(';').map((value) => value.trim()).find((value) => value.startsWith(prefix));
  if (!raw) return null;
  try {
    return decodeURIComponent(raw.slice(prefix.length));
  } catch {
    return null;
  }
}
```

- [ ] **Step 2: 缺 token 时共享 bootstrap Promise**

`createApiClient` 内维护一个仅用于首次缺 Cookie 场景的 Promise，请求 `${baseUrl}/api/csrf`，解析 `R` 中的 `headerName/token`。每次业务写请求先读 Cookie，Cookie 优先于 bootstrap 返回值；调用方已显式提供同名 Header 时不覆盖。

- [ ] **Step 3: 运行 GREEN**

使用 Task 4 命令，预期全部通过。

### Task 6：直传隔离测试

**Files:**
- Modify: `admin-web/src/views/RagflowView.test.ts`
- Create: `employee-web/src/composables/usePersonalFileTransfer.test.ts`

- [ ] **Step 1: 固定对象存储不泄漏宿主 token**

管理员测试确认 XHR 直传不设置 `withCredentials` 和 `X-XSRF-TOKEN`；员工测试直接调用 `uploadToTicketTarget`，断言远程 URL 与 Vite bridge 的 fetch options 都没有 `credentials` 和 CSRF Header。

- [ ] **Step 2: 运行定向测试**

```bash
cd admin-web && npm run test -- src/api/http.test.ts src/views/RagflowView.test.ts
cd employee-web && npm run test -- src/api/http.test.ts src/composables/usePersonalFileTransfer.test.ts src/composables/useFilePreview.test.ts
```

- [ ] **Step 3: 提交前端批次**

只暂存 shared-web 和上述前端测试文件，提交：

```text
feat: 为双前端请求附加 CSRF token
```

### Task 7：全量验证、浏览器和文档

**Files:**
- Modify: `docs/new_docs/插件化工作台/风险与约束.md`
- Modify: `docs/new_docs/插件化工作台/开发计划.md`
- Modify: `docs/new_docs/插件化工作台/验收记录.md`
- Modify: `docs/new_docs/安全/全站CSRF设计.md`

- [ ] **Step 1: 后端全量**

```bash
TEST_DB_NAME=workforce_hub_csrf_full_20260728 mvn -pl server -am test
```

- [ ] **Step 2: 双前端全量**

```bash
cd admin-web && npm run test && npm run build
cd employee-web && npm run test && npm run build
```

- [ ] **Step 3: 浏览器验证**

分别验证员工、管理员：首次登录前出现 `/api/csrf`；登录 POST 带 `X-XSRF-TOKEN`；登录后代表性写请求成功；手工移除 Header 后同请求为 403；对象存储 PUT 不含宿主 token。保存非敏感截图到根目录 `csrf-admin.png`、`csrf-employee.png`。

- [ ] **Step 4: 同步文档与风险**

记录真实测试统计、Cookie/Header 契约、全站 403 语义、同源部署边界；将插件计划中的“全站 CSRF 未完成”改为已完成，同时保留插件 Origin/Referer 为附加防线。

- [ ] **Step 5: 独立复审和最终提交**

执行 `git diff --check`，独立复审安全语义、测试覆盖与文档准确性，只精确暂存文档并提交：

```text
docs: 记录全站 CSRF 验收结果
```
