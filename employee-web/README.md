# Workforce Hub 员工端

`employee-web` 是员工/领导 Web 端正式前端工程，基于 Vue 3、TypeScript、Vite、Pinia 和 Vue Router。

## 开发命令

```bash
npm install
npm run dev
npm test
npm run build
```

开发端口约束：

- `employee-web` 固定使用 `http://127.0.0.1:5174`
- `admin-web` 当前占用 `http://127.0.0.1:5173` 时，不要随意改动它的端口
- 前后端联调依赖既有端口约定，变更端口前先核对 Vite 代理、WebSocket Origin 和相关测试

## 本地联调

员工端 API 基础层位于 `src/api/`，默认使用服务端 Session Cookie，所有请求都会带 `credentials: "include"`。

本地前后端分端口联调时，Vite 开发服务默认会把 `/api` 和 `/ws` 代理到 `http://localhost:8080`。如果后端端口不是 8080，可以在 `employee-web/.env.local` 配置：

```bash
VITE_API_PROXY_TARGET=http://localhost:8082
```

生产或预览环境如果需要跨域直连后端，可以配置：

```bash
VITE_API_BASE_URL=http://localhost:8080
```

如果要联调找回密码、登录邮箱二验或绑定邮箱，后端会先读取仓库根目录 `.env` 中的 SMTP 配置；没有真实 SMTP 配置时默认连 `127.0.0.1:1025` 的本地 SMTP。可以直接启动：

```bash
python3 server/scripts/dev/start_debug_smtp.py
```

如果要联调 IM 内 Agent 回复，但暂时没接真实模型，可以启动本地兼容桩：

```bash
python3 server/scripts/dev/start_agent_stub_server.py
```

更完整的后端联调前置、演示账号和非空数据初始化说明见：

- [employee-web后端联调基线.md](/Users/sailtrack/工程文件/java_web/workforce-hub/docs/本地联调/employee-web后端联调基线.md)

消息和待办运行态不再混入静态样例数据；接口加载失败或未登录时会显示空态/错误态。真实接口统一收口在 `src/api/employee.ts`，禁止在 Vue 组件里直接散写 `fetch`。

## 仓库边界

- `src/`、`index.html`、`package.json`、`package-lock.json` 和配置文件属于正式源码。
- `node_modules/`、`dist/`、`file-dist/`、`*.tsbuildinfo` 是本地依赖或构建产物，不进入源码提交。
- `file-preview.html` 只用于单文件验收预览，引用的 `file-dist/` 必须通过 `npm run build:file` 生成，不允许手工维护。
- `prototypes/employee-web/` 是历史静态原型，不能作为真实接口契约来源。

## 接口契约规则

员工端展示层可以保留后续能力入口，但必须明确区分：

- `implemented`：后端 Controller 已开放，可进入联调。
- `planned`：已规划但未实现，不能在前端直接发起真实请求。
- `mockOnly`：仅原型展示，不作为后端接口契约。
