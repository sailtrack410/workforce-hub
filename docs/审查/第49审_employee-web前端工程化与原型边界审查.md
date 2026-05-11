# 第 49 审 employee-web前端工程化与原型边界审查

- 审查日期：2026-05-09
- 审查提交：
  - 当前工作区新增与修改内容（`employee-web/` Vue 员工端工程、`prototypes/employee-web/` 静态原型改动、员工端截图与审查规范文件）
- 审查范围：`employee-web/`、`prototypes/employee-web/app.js`、`prototypes/employee-web/styles.css`、`employee-web-desktop.png`、`employee-web-mobile.png`、`employee-web-mobile-check.png`、`docs/审查/README.md`、`docs/审查/审查规范.md`
- 审查目标：只审查当前前端内容的正确性风险、契约风险、冗余和可维护性隐患；不修改业务代码、不替前端补丁化修复。

---

## 第一部分：提交内容概览

本轮工作区在上一轮考勤/补卡闭环之后，新增了独立的 `employee-web/` Vue 员工端工程，同时继续修改了原有静态原型 `prototypes/employee-web/`。

主要变化包括：

- `employee-web/` 新增 Vue 3 + Vite + Pinia + Vue Router 工程。
- `employee-web/src/views/MessagesView.vue` 实现消息优先的员工端主界面：侧边导航、列表区、主内容区、详情面板和移动端底部导航。
- `employee-web/src/stores/shell.ts` 内置消息、待办、文件、工作台和个人中心的静态展示数据。
- `employee-web/src/stores/inbox.ts` 另存一套消息会话数据与 Pinia store。
- `employee-web/file-preview.html` 指向 `employee-web/file-dist/` 下的文件版构建产物。
- `prototypes/employee-web/app.js` 和 `prototypes/employee-web/styles.css` 仍在继续大幅演进，当前 diff 约 `1395 insertions / 633 deletions`。
- 工作区还存在根目录截图文件 `employee-web-desktop.png`、`employee-web-mobile.png`、`employee-web-mobile-check.png`。

本轮 `npm run build` 在 `employee-web/` 下已通过，说明当前 Vue 工程可完成 TypeScript 检查和 Vite 构建；但该验证不等于后端接口契约、真实交互、仓库收口和生产可维护性已经达标。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `docs/审查/审查规范.md` | 本轮工作区新增了审查规范文件，内容覆盖提交边界、权限、并发、事务、数据库迁移、API 契约、测试质量和可维护性维度，能支撑后续审查按统一标准输出。 | 已处理 | README 必须指向真实存在的 `审查规范.md`，避免索引再次回到不存在的历史文件名。 |
| 2-2 | `employee-web/package.json` | 新员工端工程已经具备独立构建脚本，`npm run build` 可执行 `vue-tsc --noEmit && vite build`，基础编译链路已跑通。 | 已处理 | 后续进入正式开发前仍需补 lint、格式化和真实接口联调检查，不能只靠构建通过判断可上线。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `employee-web/src/stores/shell.ts:288-400`、`employee-web/src/stores/inbox.ts:153-312` | 前端展示数据中列出了多组当前后端没有实现的员工端接口，例如 `GET /api/employee/groups/:id`、`POST /api/employee/groups/:id/reply`、`GET /api/employee/agent/recommendations`、`POST /api/employee/agent/request-draft`、`GET /api/employee/attendance/issues/:id`、`POST /api/employee/attendance/issues/:id/appeal`、`GET /api/employee/files/:id`、`GET /api/employee/files/:id/token`、`POST /api/employee/files/:id/open`。这些接口目前是静态展示文案，不是后端契约。若后续联调或文档直接以此为准，会把未实现能力误判为已开放，导致前后端契约漂移。 | P2 | 将静态原型接口标记为 `mockOnly` 或集中到原型说明；正式员工端只保留已由 Controller 暴露的接口，未实现能力进入 roadmap，而不是写成可调用路径。 |
| 3-2 | `employee-web/src/views/MessagesView.vue:196-215`、`employee-web/src/views/MessagesView.vue:240-329` | 搜索框会写入 `shell.search[activeSection]`，筛选 chip 也展示了“全部、未处理、Agent、组织群、私聊”，但列表渲染仍直接遍历 `shell.conversations`、`shell.todos`、`shell.files`、`shell.workbench`、`shell.profile`。用户输入搜索或点击筛选不会改变列表结果，属于“看起来可操作但没有行为”的交互风险，后续接真实数据后也容易遗漏服务端过滤和本地过滤的边界。 | P2 | 在 store 中增加各 section 的 filtered getter，明确搜索字段、筛选字段和空结果状态；若暂不实现，隐藏筛选 chip，避免把占位控件当成已完成能力。 |
| 3-3 | `employee-web/src/views/MessagesView.vue:50-70` | 多个操作通过 `SectionAction.action='toast'` 进入 `runAction()`，实际执行 `window.alert(action.message)`。浏览器阻塞弹窗会打断消息输入、移动端面板切换和未来异步请求状态，也无法承载失败重试、加载中和成功回写。作为原型可临时说明，但进入正式员工端会形成交互和状态管理断点。 | P2 | 抽出非阻塞 toast/notification 状态，操作按钮要么绑定真实请求，要么显式保持 disabled/mock 状态；不要把 `alert` 留在正式交互路径。 |
| 3-4 | `employee-web/file-preview.html:7-11`、`employee-web/file-dist/` | 文件预览页直接引用 `./file-dist/style.css?v=20260508-layout-5` 和 `./file-dist/employee-web.js?v=20260508-layout-5`。当前 `file-dist` 属于构建产物，如果源文件改了但没有执行 `npm run build:file`，预览会继续加载旧版本；如果手动改 query 版本号，又会让版本语义脱离真实构建哈希。 | P3 | 文件版预览应由构建脚本生成入口和资源引用；若只是临时验收文件，应在 README 或脚本中明确“源文件为准、file-dist 不参与人工维护”。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `employee-web/`、根 `.gitignore`、`employee-web/.gitignore` | 当前 `employee-web/` 整个目录处于未跟踪状态，其中 `node_modules` 约 105M、4416 个文件，另有 `dist/` 和 `file-dist/` 构建产物。虽然 `employee-web/.gitignore` 写了 `node_modules/`、`dist/`、`file-dist/`，但新顶层目录整体未纳入版本管理时，后续如果从根目录执行递归 add 或手动选择目录，仍容易把依赖树、构建产物和临时预览文件混入提交。 | P1 | 在根 `.gitignore` 明确增加 `employee-web/node_modules/`、`employee-web/dist/`、`employee-web/file-dist/` 等规则；提交时只纳入源码、配置和必要锁文件，截图和构建产物按验收附件或发布产物单独管理。 |
| 4-2 | `employee-web/src/stores/shell.ts`、`employee-web/src/stores/inbox.ts` | `shell.ts` 和 `inbox.ts` 定义了高度相似的 `ConversationRecord`、`ConversationMessage`、`ConversationPanel` 以及同一批会话静态数据；页面实际只从 `stores/shell` 导入 `useShellStore`，未发现 `useInboxStore` 被引用。该重复会让后续字段、接口文案和展示数据双份漂移，属于典型的原型迁移残留。 | P2 | 合并为单一数据源：要么删除未接入的 `inbox.ts`，要么把消息领域抽成 `messageFixtures`/`conversationStore` 后由 `shell` 组合引用，避免两份同构模型长期并存。 |
| 4-3 | `prototypes/employee-web/app.js`、`prototypes/employee-web/styles.css`、`employee-web/src/**` | 旧静态原型仍在大幅修改，同时新 Vue 工程已经实现同一员工端主体验。两个前端入口都承载“消息、待办、文档、工作台、我的”等概念，且都维护接口文案和演示数据。若不明确哪个是正式源，后续需求会在原型和 Vue 工程之间反复同步，维护成本会快速上升。 | P2 | 明确 `employee-web/src` 为正式源码还是 `prototypes/employee-web` 为设计原型；一旦 Vue 工程成为主线，旧原型应冻结为历史参考或只保留跳转说明，不再双轨开发。 |
| 4-4 | 根目录 `employee-web-desktop.png`、`employee-web-mobile.png`、`employee-web-mobile-check.png` | 截图文件直接放在仓库根目录且处于未跟踪状态，未与审查、设计验收或开发日志建立目录关系。后续多轮截图会污染根目录，也难以判断哪些截图对应哪次构建、哪个视口和哪个版本。 | P3 | 若截图用于审查证据，放入 `docs/审查/assets/` 或对应审查文件同名资产目录；若只是临时验收产物，不进入源码提交。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short --branch` | 已执行 | 当前分支 `main...origin/main [ahead 11]`，存在 `prototypes/employee-web/*` 修改、`employee-web/` 未跟踪目录、员工端截图和 `docs/审查/审查规范.md`。 |
| 前端构建 | `npm run build`（在 `employee-web/` 下） | 通过 | 输出包含 `vue-tsc --noEmit && vite build`，Vite 构建成功。 |
| 依赖体积检查 | `du -sh employee-web employee-web/node_modules employee-web/dist employee-web/file-dist`、`find employee-web/node_modules -type f | wc -l` | 已执行 | `employee-web` 约 106M，`node_modules` 约 105M，`node_modules` 文件数 4416。 |
| 未接后端接口核对 | `rg "@RequestMapping|@GetMapping|@PostMapping|@PutMapping|@DeleteMapping" server/src/main/java/com/workforcehub` | 已执行 | 当前后端 Controller 暴露了 auth、org、workflow、file、attendance、leave 等已实现接口，但未看到 `groups`、`agent`、`attendance/issues`、`files/:id/token` 等前端展示路径。 |
| 未使用 store 核对 | `rg "useInboxStore|stores/inbox" employee-web/src` | 已执行 | 仅 `inbox.ts` 自身定义 `useInboxStore`，页面实际使用 `useShellStore`。 |

未验证项：

- 本轮没有运行 Maven 编译或后端集成测试，因为本轮审查对象是当前前端工程化与原型边界，且用户约束为不改业务代码。
- 本轮没有启动浏览器做交互截图复核，截图文件只作为工作区存在状态记录，不作为通过依据。
- 本轮没有检查移动端全部断点下的视觉重叠、键盘可达性和焦点顺序。
- 本轮没有执行 `npm run build:file`，因此 `file-dist` 是否与当前源码一致未验证。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `employee-web/` 仓库边界 | 先处理根 `.gitignore` 与提交清单，避免把 `node_modules`、`dist`、`file-dist`、临时截图混进主仓库。这是进入下一次提交前最优先的收口点。 | P1 | 先定“源码可提交清单”和“产物不可提交清单”，再 staging；不要直接 `git add employee-web/`。 |
| 6-2 | 前端源头收敛 | 旧原型和新 Vue 工程必须二选一作为主线，否则后续 UI、接口文案、数据模型会双轨漂移。 | P2 | 将 `prototypes/employee-web` 标记为历史设计稿或临时预览，把开发主线集中到 `employee-web/src`。 |
| 6-3 | 契约与 mock 边界 | `shell.ts` 和 `inbox.ts` 中的接口映射不能直接冒充后端已实现接口。 | P2 | 新建接口契约清单，按“已实现 / 待实现 / 原型占位”分组，前端只对已实现接口做真实调用。 |
| 6-4 | 交互真实度 | 搜索、筛选、按钮操作应尽快从静态占位切换到可测试状态，否则后续联调无法判断是 UI 问题、状态管理问题还是后端问题。 | P2 | 先补 filtered getter、空状态和非阻塞 toast，再接真实 API。 |

---

## 第七部分：阶段性结论

当前 `employee-web` 适合作为员工端 Web 体验的工程化起点：独立 Vue 工程可以构建，页面结构已经围绕消息优先、待办、文件、工作台和个人中心展开。

但它还不适合直接作为正式前端提交进入稳定主线。首要阻断不是业务逻辑，而是仓库边界和源头边界：`employee-web/` 未跟踪目录中混有 105M 依赖树和构建产物，旧静态原型与新 Vue 工程并行演进，且部分展示接口并不存在于后端 Controller。建议先完成 git hygiene、源代码主线确认、mock/真实 API 分层，再继续做真实联调。
