# Workforce Hub 员工端

`employee-web` 是员工/领导 Web 端正式前端工程，基于 Vue 3、TypeScript、Vite、Pinia 和 Vue Router。

## 开发命令

```bash
npm install
npm run dev
npm test
npm run build
```

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
