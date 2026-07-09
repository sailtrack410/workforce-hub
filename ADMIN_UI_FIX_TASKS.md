# 管理端 UI 修复任务清单

执行前先读 `AGENTS.md`，全部约束与验收标准以其为准。任务按顺序执行，每个任务独立提交。

## 任务 1：补齐 ghost-button 样式
- 现状：`RoleView.vue:211`、`LeaveTypeView.vue:212` 使用 `ghost-button danger` 类，但 `admin.css` 无定义，按钮为裸样式。
- 要求：在 `admin.css` 补齐 `ghost-button` 及 `ghost-button.danger` 样式，风格与现有 `secondary-button` 体系一致，danger 色用 `var(--red)`。
- 验收：两处按钮渲染正常；浏览器截图证据。

## 任务 2：window.confirm 替换为 AdminConfirmDialog
- 现状：`UserView.vue:228`、`LeaveTypeView.vue:125`、`RagflowView.vue:602` 使用原生 `window.confirm`。
- 要求：全部替换为项目已有的 `AdminConfirmDialog` 组件，文案保留原语义。替换后全仓库 `grep window.confirm` 结果为 0。
- 验收：三处删除流程可正常确认/取消；grep 结果 + 截图证据。

## 任务 3：硬编码颜色替换为 CSS 变量
- 现状：`LeaveTypeView.vue:238`（`#b42318`）、`AdminTable.vue:141`（`#F8FAFC`）、`AgentDagView.vue:772`（`rgba(15, 23, 42, 0.1)`）绕过 token 体系。
- 要求：替换为 `admin.css` 中已有的对应 CSS 变量；无对应变量时新增变量再引用，禁止留硬编码值。
- 验收：上述三处及同文件内其他硬编码色全部消除；视觉无回归（截图对比）。

## 任务 4：AdminTable 可访问性
- 现状：`AdminTable.vue:80-96` 的 `<th>` 缺 `scope="col"`。
- 要求：加 `scope="col"`，不改其他结构。
- 验收：构建通过即可。

## 任务 5：统一刷新按钮风格
- 现状：UserView/DepartmentView/PositionView/RoleView/LeaveTypeView 用图标按钮，AttendanceView/LeaveGovernanceView/WorkflowGovernanceView 用文字按钮。
- 要求：全部统一为文字按钮（`secondary-button` + RefreshCcw 图标 + "刷新"）。
- 验收：全部列表页刷新按钮样式一致；截图证据。

## 任务 6：抽取通用 AdminDialog 组件
- 现状：`UserView.vue:520`、`RagflowView.vue:1061`、`AdminUserPicker.vue:89`、`AdminConfirmDialog.vue:42` 各自实现 overlay/dialog，遮罩色与 z-index 不一致。
- 要求：新建 `AdminDialog.vue`（overlay + 容器 + 标题栏 + 关闭按钮 + 默认插槽），四处弹窗迁移到该组件；遮罩色、圆角、z-index 统一取自 CSS 变量。不做动画、嵌套、拖拽等额外能力。
- 验收：四处弹窗打开/关闭正常，视觉统一；截图证据。

## 任务 7：超大文件拆分（低优先级）
- 现状：`RagflowView.vue` 1490 行（内嵌 5 个弹窗）、`AgentConfigView.vue` 1237 行。
- 要求：仅将弹窗模板抽为子组件，不重构业务逻辑，不改变行为。
- 验收：两文件降至 800 行以内；相关功能实测无回归。

## 全局验收（每个任务完成后必做）
- `cd admin-web && npm run test`：全部通过，贴最终统计行。
- `cd admin-web && npm run build`：退出码 0，贴输出尾部。
- UI 改动附浏览器截图，命名 `<功能>-admin.png` 放根目录。
- 禁止只声称"已通过"，必须贴命令输出。
