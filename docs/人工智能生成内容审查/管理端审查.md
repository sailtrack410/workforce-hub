# AIGC 特征审查报告 — admin-web 模块

审查范围：
- `admin-web/src/api/admin.ts`
- `admin-web/src/views/LoginView.vue`
- `admin-web/src/views/DashboardView.vue`
- `admin-web/src/components/AdminPanel.vue`
- `admin-web/src/stores/session.ts`

---

## 【AIGC 风险评估】

- **综合评分：38/100**（分数越低越像 AI 生成）
- **风险等级：高**

---

## 【维度分析】

### 1. 命名风格（得分：18/25）

**问题集中点：**

- 类型命名严格遵循 `{Resource}{Action}{Response/Request}` 模板，如 `AdminAgentTaskMetricsResponse`、`WorkflowRequestSummaryResponse`、`AttendanceDailyResultResponse`——全文件 87 个 import 类型无一例外。人类开发者通常会有至少 1-2 个简写或非标命名（如 `MetricsResp`、`TaskListQ`），这里完全没有。
- API 方法名与 URL 路径呈 1:1 映射，无缩写、无习惯用语。例如 `createUploadTicket` / `confirmUpload` / `createDownloadTicket`，人类开发者可能写成 `newTicket` / `confirm` / `dlTicket`。
- 视图组件中的变量名过于"教科书"：`submitting`、`redirectTarget`、`submitLogin`、`taskMetrics`、`storageCards`——全部是 self-descriptive 全称，没有任何领域缩写或团队内部术语。
- `DashboardView.vue` 中 `const [tm, lt, st] = await Promise.all(...)` 的短变量名是唯一的"人类味"特征，但随即又用 `taskMetrics`、`leaveTypes`、`storage` 三个完整名称赋值，风格自相矛盾。

**正面信号：**
- `pathId` 辅助函数命名简洁实用。
- `session.ts` 中 `resolveSessionError` 命名准确，不是泛化的 `handleError`。

### 2. 注释特征（得分：8/20）

**问题集中点：**

- **5 个文件中仅存在 2 条注释**，且都在 `session.ts`：
  - 第 33-34 行：`// 后端已对 ADMIN 用户返回 permissionCodes（AccountService.java:65）。` + `// 字段缺失或为空时默认拒绝，避免后端漏传导致越权。`
- `admin.ts`（218 行）、`LoginView.vue`（122 行）、`DashboardView.vue`（83 行）、`AdminPanel.vue`（33 行）——**零注释**。
- 人类开发者在 218 行的 API 文件中通常会至少加几行分隔注释（如 `// ---- 认证 ----`、`// ---- 考勤 ----`），或在复杂端点（如 `workflowRun` 的内联类型）旁加说明。这里完全没有。
- 没有任何 `TODO`、`FIXME`、`HACK`、`XXX` 标记。5 个文件、477 行代码中无一处临时标记，说明代码是"一次性成型"而非迭代产出。
- `session.ts` 的两条注释质量很高——引用了具体源文件位置 + 解释了安全决策的"为什么"——这恰恰是 AI 在被明确要求添加注释时的典型高质量输出，而非人类开发中常见的零散、不完整注释。

**正面信号：**
- 注释内容本身准确且有价值（引用 `AccountService.java:65`）。

### 3. 代码结构（得分：12/20）

**问题集中点：**

- **函数长度高度统一**：
  - `admin.ts`：所有 API 方法都是单行箭头函数（1-2 行），218 行中无一处超过 3 行的方法体。
  - `session.ts`：`loadCurrentUser`、`login`、`logout` 三个 action 结构完全一致——`loading=true` / `error=''` / `try...catch...finally` / `loading=false`，长度在 8-12 行之间。人类开发者通常会有 1-2 个 action 因为特殊逻辑而明显更长或更短。
  - `DashboardView.vue`：`load` 函数与 `LoginView.vue` 的 `submitLogin` 结构完全对称——try/catch/finally + loading 状态管理。
- **无任何变通或妥协痕迹**：没有"先这样写后面再改"的迹象，没有被注释掉的代码，没有替代实现的痕迹。代码像是从模板一次性生成。
- `DashboardView.vue` 中 `taskCards` 和 `storageCards` 两个 computed 函数结构完全相同（返回 label/value 数组），人类开发者可能会合并为一个带参数的函数，或反过来让其中一个更复杂。
- `AdminPanel.vue` 极度精简（33 行），作为纯展示组件是合理的，但整个文件没有任何多余的设计决策痕迹。

**正面信号：**
- `session.ts` 的 `AdminAuthApi` 接口 + 依赖注入模式体现了可测试性设计，这是有经验开发者的特征。
- `admin.ts` 中 `trialRun` 和 `workflowRun` 方法直接内联复杂类型而非提取到 `types.ts`，是务实的人类决策（虽然可能是偷懒而非刻意）。

### 4. 异常与边界处理（得分：7/15）

**问题集中点：**

- **所有错误处理完全一致**：`try { ... } catch (e) { toast.error(errorMessage(e)) } finally { loading.value = false }`。从 `DashboardView.load()` 到 `LoginView.submitLogin()` 到 `session.ts` 的三个 action，错误处理模式完全相同——捕获异常、提取 message、展示/存储错误、重置 loading。
- `resolveSessionError` 的错误映射覆盖了 401/403/500+/`Failed to fetch`/通用 Error/未知类型——看似全面，但每种情况的处理方式都是"返回中文错误字符串"，没有任何特殊处理（如 401 时自动跳转登录页、500 时提供重试按钮、网络断开时显示离线状态）。
- `LoginView.vue` 的表单校验仅检查"登录名和密码非空"，没有长度限制、格式校验、频率限制等边界处理。
- `admin.ts` 的 API 层**完全没有任何错误处理或参数校验**——全部透传给底层 `ApiClient`。人类开发者通常至少会在某些关键接口加 try/catch 或参数断言。
- `session.ts` 第 59 行 `this.user = await authApi.me().catch(() => loginResult as CurrentUserResponse)` 是唯一的"变通"处理——登录成功但获取用户信息失败时降级使用登录响应。这种 fallback 策略是人类决策的特征。

### 5. 整体一致性（得分：8/20）

**问题集中点：**

- **一致性过高本身就是 AI 特征**。5 个文件的风格差异极小：
  - 所有异步操作使用相同的 try/catch/finally + loading 模式。
  - 所有 API 方法使用相同的单行箭头函数形式。
  - 所有错误消息通过同一个 `errorMessage()` 工具函数处理。
  - 所有 CSS 使用同一套 CSS 变量系统。
- 没有"先写的代码和后写的代码风格不一致"的痕迹。人类开发者在同一项目中通常会因为时间推移、心情变化、或从不同参考复制代码而产生风格漂移，这里完全没有。
- 跨文件的对称性明显：
  - `LoginView.submitLogin` ≈ `session.loadCurrentUser` ≈ `session.login`（结构完全一致）
  - `DashboardView.taskCards` ≈ `DashboardView.storageCards`（结构完全一致）
  - `admin.ts` 中 `auth` / `leave` / `rbac` / `attendance` 各模块的 API 方法风格完全一致

**正面信号：**
- 项目整体设计系统（CSS 变量、组件抽象）是有规划的，说明有架构层面的人类决策。

---

## 【典型 AI 特征清单】

- [x] **导入语句按字母序排列**：`admin.ts` 的 87 个 import 严格按模块路径字母序排列，人类开发者通常按功能分组或按使用频率排列。
- [x] **零 TODO/FIXME/HACK**：477 行代码中无任何临时标记，代码"一次性完成"。
- [x] **零注释密度**：除 `session.ts` 的 2 条高质量注释外，其余 380+ 行完全无注释。
- [x] **错误处理模式完全统一**：所有文件使用相同的 try/catch/finally + 错误消息提取模式，无任何差异化处理。
- [x] **函数长度方差极低**：所有函数都在 1-12 行之间，没有长函数也没有特别短的异常函数。
- [x] **命名风格无例外**：类型命名、方法命名、变量命名严格遵循同一套规则，无简写、无内部术语。
- [x] **无被注释掉的代码**：整个代码库中没有"暂时保留"或"之前写过但废弃"的代码痕迹。
- [x] **CSS 变量使用 100% 一致**：所有样式值都通过 CSS 变量引用，无硬编码值，无 `!important`，无 hack。
- [x] **无代码气味**：没有重复代码（DRY 过度）、没有过长参数列表、没有深层嵌套——代码"太干净"。
- [x] **类型定义与使用完全匹配**：所有请求/响应类型都精确对应，没有 `any`、没有类型断言（除 `session.ts:59`）、没有 `@ts-ignore`。

---

## 【修改建议】

### 降低 AI 痕迹（高优先级）

1. **添加分组注释到 `admin.ts`**：为各模块（auth / agentTasks / workflow / leave 等）添加简短的中文分隔注释，如 `// ---- 考勤管理 ----`。不是每个函数都需要注释，但大文件中应该有导航标记。

2. **打破错误处理的完全一致性**：在 `DashboardView.load()` 中，`toast.error(errorMessage(e))` 可以改为更具场景化的处理，例如"仪表盘数据加载失败，请刷新重试"。不同页面的错误处理应该有不同的用户提示。

3. **在 `resolveSessionError` 中添加差异化处理**：401 状态码不应该只是返回字符串，应该触发路由跳转或 token 刷新逻辑。当前实现把所有错误都扁平化为字符串，缺乏业务深度。

4. **引入至少 1-2 个"不完美"的代码模式**：
   - 在 `admin.ts` 的某个模块中添加一个比其他方法更复杂的函数（带参数校验或重试逻辑）。
   - 在 `DashboardView` 中让其中一个 computed 属性的结构与另一个不同。
   - 在 `LoginView` 的 CSS 中使用一个硬编码值（如特定的 breakpoint 或 shadow 值）而非全部通过变量。

### 提升代码质量（可选）

5. **为 `admin.ts` 的复杂端点添加注释**：`trialRun` 和 `workflowRun` 方法的内联类型定义很长且可读性差，应提取到 `types.ts` 并添加说明注释。

6. **`DashboardView` 的 `v`/`tm`/`lt`/`st` 缩写风格不统一**：要么全部用短名（更 human），要么全部用全称（更 consistent），当前混用状态像是"想写短名但又觉得不够规范"的纠结产物——反而更像人类，可以保留。

7. **考虑为 `session.ts` 的三个 action 提取公共的 loading/error 管理逻辑**：当前三个 action 有大量重复的 `this.loading = true / this.error = '' / try...finally { this.loading = false }`，人类开发者通常会在第二次重复时就提取 helper。但反过来说，这也可能被视为"AI 过度抽象"的信号——取决于团队风格。

---

*审查时间：2026-07-03*
*审查方法：静态代码特征分析 + 跨文件一致性比对 + Git 历史辅助判断*
