# 第 95 审 employee-web 第 94 审修复复核与残留风险再剖析

- 审查日期：2026-05-15
- 审查提交：
  - `fad9cdb`（完善后端个人中心与真实配置链路）
  - 工作区增量（第 94 审第八部分修复 + useAgentDirectory/useEmployeeAuthFlow/useCurrentUserAvatarUrl/scroll.ts 等新增 composable）
- 审查范围：`employee-web/src/` 全量
- 审查目标：复核第 94 审 3 个 P0 + 3 个 P1 的修复进展，扫描新增代码引入的风险，重估前端整体健康度。本轮不审查后端变更。

---

## 第一部分：第 94 审问题收口复核

| 第 94 审编号 | 问题 | 当前状态 | 证据 |
|---|---|---|---|
| 3-2 / 6-2 | KnowledgeAnswerCard 与 shell.ts 重复接口定义 | **已收口** | `types/knowledge.ts` 已创建；`KnowledgeAnswerCard.vue`、`stores/shell.ts`、`stores/im.ts` 均从同一处 import；shell.ts 内无 `ConversationKnowledge*` 残留 |
| 3-4 / 6-4 | 工作台汇总 API 全失败被静默吞掉 | **已收口** | `useWorkbenchState.refreshWorkbenchData()` 改用 `Promise.allSettled`；全部失败时设置 `workbenchError`；`WorkbenchSection.vue` 展示错误态 + 重试按钮 |
| 3-5 / 6-5 | 审批详情请求竞态（loadWorkflowRequestDetail） | **已收口（仅审批详情路径）** | `useWorkbenchState.loadWorkflowRequestDetail()` 引入请求序号 `workflowDetailRequestSeq`，只有最后一次请求的回调才写入详情和 loading；`useWorkbenchState.test.ts` 新增竞态用例通过 |
| 4-4 / 6-8 | composable 层零测试覆盖 | **部分收口** | 新增 `useWorkbenchState.test.ts`（2 个测试，覆盖全 API 失败 + 竞态场景）；新增 `scroll.test.ts`（1 个测试）；其余 composable（useMessagesState/useFileUpload/useAttendance/useImSocket）仍零测试 |
| 3-1 / 6-1 | shell.ts 职责爆炸（1256 行，7 种职责混合） | **未收口** | shell.ts 仍 1197 行，14 个接口定义 + localStorage 读写 + 10+ 工具函数 + Pinia store 本体仍在同一文件 |
| 3-3 / 6-3 | 62 处重复错误处理，无统一错误层 | **未收口** | 仅 `session.ts` 使用 `resolveSessionError`；`im.ts`（17 处）、`stores/shell.ts`、`composables/` 下所有文件仍各自内联 `error instanceof Error ? error.message : 'xxx失败'`；`ApiClientError` 的 401 特殊处理未传播到非 session 模块 |
| 3-6 / 6-6 | localStorage 无大小保护 | **未收口** | `writeStoredPersonalFiles` 仍直接 `setItem`，写入失败静默吞掉；超限场景未测试 |
| 4-1 / 4-2 | statusLabel 4 处重复、时间格式化 4 处重复 | **未收口** | `statusLabel` 在 `im.ts`/`workflow.ts`/`employee-format.ts` 仍各有一份；`formatListDate`（shell.ts:1184）与 `formatConversationDate`（im.ts:661）逻辑相同未合并 |
| 4-3 | im.ts Options API 限制 action 复用 | **未收口** | im.ts 仍 853 行 Options API，`pollAgentReply` 仍复制 `loadMessages` 逻辑 |
| 4-5 | KnowledgeAnswerCard 827 行未拆 | **未收口** | 组件仍 786 行（含模板 + 脚本 + 样式），6 状态分支未拆子组件 |

---

## 第二部分：本轮新增正确性风险

### 2-1 【P1】im.ts loadMessages / loadConversationDetail 无竞态保护

**位置**：`src/stores/im.ts:103`（loadMessages）、`src/stores/im.ts:133`（loadConversationDetail）

**触发条件**：用户在会话列表快速切换 A→B→A，或 WebSocket 推送触发并发加载。

**失败路径**：
- `loadMessages` 的 `this.messageLoading` 和 `this.error` 是全局状态。快速切换时，A 的请求晚返回，会覆盖 B 的 loading 状态和 error 状态。
- 具体影响：`MessagesSection.vue:130` 用 `v-if="im.messageLoading"` 控制骨架屏。A 晚返回时将 `messageLoading` 设为 `false`，导致 B 的骨架屏提前消失，用户看到空白消息列表。
- `this.error` 同理：A 的加载失败会覆盖 B 的成功状态，导致 B 的会话列表错误地显示 A 的错误信息。

**影响范围**：所有会话切换场景；影响 UI 状态展示，不污染数据内容（因为 `messagesByConversation[key]` 按会话 ID 分 key）。

**为什么现有测试挡不住**：`im.test.ts` 只测单会话加载成功/失败，没有并发切换场景。

**优化方案**：
- `loadMessages` 和 `loadConversationDetail` 引入请求序号（类似已修复的 `useWorkbenchState` 方案）；或把 `messageLoading` / `error` 也按 `conversationId` 分 key 存储。
- 推荐后者更干净：`messageLoadingByConversation[key]` + `errorByConversation[key]`，`MessagesSection.vue` 按当前激活会话读取对应状态。

---

### 2-2 【P2】useWorkbenchState.test.ts / scroll.test.ts 类型错误（vue-tsc 未通过）

**位置**：`src/composables/useWorkbenchState.test.ts:64`、`src/utils/scroll.test.ts:39`

**问题描述**：
- `useWorkbenchState.test.ts:64`：mock 对象 `workflowDetail()` 返回的字面量包含 `applicantName`、`completedAt`、`withdrawable`、`timeline` 等字段，但 `WorkflowRequestDetailResponse` 接口中不存在这些字段（实际接口字段为 `currentStepKey`、`finishedAt`、`withdrawable` 拼错？）。导致 `tsc --noEmit` 报错 `TS2353`。
- `scroll.test.ts:39`：用了 `Array.prototype.at(-1)`，但 `tsconfig.json` 的 `lib` 未包含 `es2022`，编译目标不支持 `.at()` 语法，导致 `TS2550`。

**触发条件**：运行 `vue-tsc --noEmit` 做类型检查时失败。Vite 构建和 `vitest` 运行不受影响（因为 Vite 用 esbuild 转译，默认支持 `.at()`）。

**影响范围**：CI 类型检查阶段会失败；开发者运行 `vue-tsc` 本地校验时报错。

**为什么没早发现**：第 94 审后新增测试时未同步更新类型定义；scroll.test.ts 的 `.at(-1)` 用法未检查 tsconfig lib 配置。

**优化方案**：
- `useWorkbenchState.test.ts`：mock 数据对齐 `WorkflowRequestDetailResponse` 实际字段（`applicantName` → 接口中无此字段，应删除；`completedAt` → `finishedAt`；`withdrawable` → 检查接口是否有此字段）。
- `scroll.test.ts`：将 `calls.at(-1)` 改为 `calls[calls.length - 1]`，或升级 `tsconfig.json` 的 `lib` 至 `es2022`。

---

### 2-3 【P2】session.ts 的 orgApi.subordinates/colleagues 静默失败策略不一致

**位置**：`src/stores/session.ts:111-113`

**问题描述**：第 94 审已指出 `.catch(() => [])` 和 `.catch(() => null)` 是"有意降级"还是"无提示失败"不明确。`loadOrgProfile` 中：
- `orgApi.manager()` → `.catch(() => null)` —— 无上级时本来就可能返回 null，无法通过返回值区分"无上级"和"API 失败"。
- `orgApi.subordinates()` → `.catch(() => [])` —— 无下属时返回空数组，同样无法区分。
- `orgApi.colleagues()` → `.catch(() => [])` —— 同上。

**触发条件**：后端 org 模块部署失败或网络断开时，前端静默降级为空数据，用户在"同事""下属"列表看到空白，不知道是"确实没有"还是"加载失败了"。

**影响范围**：`session.profile`（个人信息页）、"同事"功能、管理岗用户的下属列表。

**优化方案**：
- 保留降级语义（这些是非关键数据），但在 store 上加 `profileWarning: string` 字段，任一 API 失败时设 `"部分数据暂不可用"`，个人信息页展示黄色警告条。
- 或改为 `Promise.allSettled` 方案，与 `useWorkbenchState.refreshWorkbenchData` 已采用的模式保持一致。

---

### 2-4 【P3】新增 composable 未跟进统一错误处理

**位置**：`src/composables/useAgentDirectory.ts`、`src/composables/useEmployeeAuthFlow.ts`、`src/composables/useCurrentUserAvatarUrl.ts`

**问题描述**：第 94 审 3-3 指出 62 处重复错误处理未收口。新增的 3 个 composable 仍然各自内联：
- `useAgentDirectory.ts:17`：`showToast(error instanceof Error ? error.message : 'Agent加载失败')`
- `useEmployeeAuthFlow.ts`：多处 `session.error = error.message`（未区分 `ApiClientError` 401）
- `useCurrentUserAvatarUrl.ts:17`：`catch {}` 静默吞掉头像加载失败，无任何提示

**影响范围**：新增代码延续旧模式，统一错误处理层越晚引入，需要改的点越多。

**优化方案**：与 P0-3 统一处理，新建 `utils/error-handler.ts` 后，增量修改新增 composable 也一并跟进。

---

## 第三部分：新增代码健康度扫描（第 94 审后新增文件）

### 3-1 新增 composable 质量

| 文件 | 行数 | 测试 | 评价 |
|---|---|---|---|
| `useAgentDirectory.ts` | 38 | 0 | 简单，无复杂逻辑，风险低 |
| `useEmployeeAuthFlow.ts` | 196 | 0 | 复杂（登录/重置密码/邮箱验证 3 个流程），零测试，**风险高**；`submitLogin` 的 `catch {}` 空块吞掉错误，只依赖 `session.error`，但 `session.error` 的赋值在 `session.login()` 内部，若 `login()` 抛出非 `ApiClientError` 的异常，`session.error` 可能未赋值 |
| `useCurrentUserAvatarUrl.ts` | 27 | 0 | 简单，但 catch 静默失败应至少 `showToast` |
| `useWorkbenchState.ts` | 484→？ | 2 | 已有测试，质量较好；但 484 行仍偏大，后续可拆 |
| `useMessagesState.ts` | 433 | 0 | 零测试，且规模较大（433 行），**风险高** |
| `useImSocket.ts` | 163 | 0 | WebSocket 逻辑复杂，零测试 |
| `useFileUpload.ts` | 320 | 0 | 文件上传 + 进度 + 错误，零测试 |

### 3-2 scroll.ts 质量

`src/utils/scroll.ts`（45 行）：实现 `scrollElementToBottomAfterLayout`，用 `requestAnimationFrame` 多帧等待布局稳定。逻辑清晰，无依赖，可测试性好（`scroll.test.ts` 已覆盖基本场景）。

**问题**：`scroll.test.ts:39` 的 `.at(-1)` 类型错误（见 2-2）。

### 3-3 employee-shell-context.ts 收口确认

第 94 审前 `EmployeeShellContexts` 是 200+ key 的 `Record<string, any>`。当前已拆为 7 个 typed interface（`MessagesSectionContext` / `TodosSectionContext` 等），各 Section 只 inject 自己需要的 context。

**评价**：此项修复质量高，类型安全，无剩余风险。

---

## 第四部分：冗余与可维护性隐患（更新）

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
|---|---|---|---|---|
| 4-1 | `im.ts:649`、`workflow.ts:103`、`shell.ts:719`、`utils/employee-format.ts` | 4 个文件各有一个"状态→中文"函数，`APPROVED` 映射不一致；开发者无法判断该复用哪个 | P2 | 统一到 `utils/status-labels.ts`，按业务域命名 |
| 4-2 | `im.ts:661`、`shell.ts:1184` | `formatConversationDate` 与 `formatListDate` 几乎逐行相同 | P2 | 统一到 `utils/date-format.ts` |
| 4-3 | `stores/im.ts`（853 行） | Options API 限制 action 组合复用；`pollAgentReply` 复制 `loadMessages` 逻辑 | P2 | 改写为 Composition API（`defineStore('employee-im', () => { ... })`） |
| 4-4 | `composables/`（13 文件） | `useWorkbenchState`（484 行）有测试；`useMessagesState`（433 行）、`useFileUpload`（320 行）、`useAttendance`（155 行）、`useImSocket`（163 行）零测试 | P2 | 优先补 `useMessagesState` 和 `useFileUpload` 关键路径测试 |
| 4-5 | `components/shell/cards/KnowledgeAnswerCard.vue`（786 行） | 6 种状态渲染路径仍在单个组件内 | P2 | 按状态拆子组件 |
| 4-6 | `views/sections/SettingsSection.vue`（1020 行） | 5 个子面板仍在同一个文件 | P3 | 拆成 `ProfilePanel` / `OrganizationPanel` / `SecurityPanel` / `PreferencesPanel` / `StoragePanel` |
| 4-7 | `scroll.test.ts:39` | `.at(-1)` 在 `tsconfig lib < es2022` 下类型报错 | P2 | 改为 `calls[calls.length - 1]` 或升级 lib |
| 4-8 | `useWorkbenchState.test.ts:64` | mock 数据与 `WorkflowRequestDetailResponse` 实际字段不一致（`applicantName` 不在接口中） | P2 | 对齐实际接口字段，删除不存在的 mock 字段 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
|---|---|---|---|
| TypeScript 类型检查 | `npx vue-tsc --noEmit` | **失败** | 2 个类型错误（2-2 / 4-7 / 4-8） |
| 生产构建 | `npx vite build` | 通过（5.12s） | Vite 用 esbuild，不受 `vue-tsc` 类型错误影响 |
| 单元测试 | `npx vitest run` | 77/77 通过 | 15 个测试文件；但 `vue-tsc` 失败意味着类型安全问题未暴露 |
| 静态扫描：错误处理重复 | `grep -c "error instanceof Error"` | 62 处 | 未减少 |
| 静态扫描：静默失败残留 | `grep "\.catch(() => \(\))" src/ -r` | 10 处 | `useAttendance.ts:78`、`session.ts:111-113`、`useWorkbenchState.ts:277` 等 |
| 静态扫描：localStorage 保护 | `grep -n "localStorage" src/stores/shell.ts` | 4 处 | 写入无大小检查，失败无提示 |

**未验证项**：
- 未跑 Maven 后端测试（本轮只审查前端）
- 无 E2E 测试覆盖会话切换竞态场景（2-1）
- `useEmployeeAuthFlow.ts` 的 `submitLogin` catch 空块未验证实际行为
- localStorage 5MB 超限场景未实际测试

---

## 第六部分：处理优先级建议（更新）

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
|---|---|---|---|---|
| 6-1 | 修复 `vue-tsc` 类型错误（2-2 / 4-7 / 4-8） | 阻断 CI 类型检查 | **P0（新）** | 改 mock 数据对齐接口 + 改 `.at(-1)` 为下标访问或升级 tsconfig lib；预计 30min |
| 6-2 | im.ts loadMessages / loadConversationDetail 竞态保护（2-1） | loading/error 状态会被旧请求覆盖，骨架屏提前消失 | **P1** | 将 `messageLoading`/`error` 按 conversationId 分 key；或引入 AbortController；预计 1-2h |
| 6-3 | session.ts loadOrgProfile 静默降级策略（2-3） | 非关键 API 失败无用户感知 | **P2** | 加 `profileWarning` 字段，或改用 `Promise.allSettled` |
| 6-4 | 统一错误处理层（P0-3 遗留） | 62 处重复，401 不跳登录 | **P0（遗留）** | 创建 `utils/error-handler.ts`，优先处理 `im.ts`（17 处）和 `useEmployeeAuthFlow.ts`（登录流程，最关键）；预计 2-3h |
| 6-5 | shell.ts 职责拆分（P0-1 遗留） | 1197 行，7 种职责混合 | **P0（遗留）** | 拆分为 `types/shell.ts` + `config/` + `composables/usePreferenceStorage.ts` + `utils/date-format.ts` + `utils/status-labels.ts` + `composables/useSearch.ts` + `composables/useContacts.ts`，shell.ts 只留 Pinia store 本体（~300 行）；预计 3-4h |
| 6-6 | 补 composable 测试（P2 遗留） | useMessagesState/useFileUpload/useAttendance/useImSocket 零测试 | **P2** | 优先 `useMessagesState`（消息发送/加载/错误）+ `useFileUpload`（上传成功/失败/超时）；预计 2-3h |
| 6-7 | localStorage 容量保护（P1 遗留） | 写入失败静默吞掉 | **P1（遗留）** | 写入前检查 `JSON.stringify(data).length`；失败时在 UI 给出提示；预计 30min |
| 6-8 | useEmployeeAuthFlow.ts catch 空块（2-4） | 登录流程错误可能被静默吞掉 | **P1（新）** | `submitLogin` 的 `catch {}` 至少设 `authNotice` 或 `session.error`；预计 15min |

---

## 第七部分：阶段性结论

第 94 审的 3 个 P0 问题：

- **P0-2（接口重复）**：已完全收口，质量和迁移干净度均好。
- **P0-3（统一错误处理）**：**未收口**，62 处重复无变化，新增 3 个 composable 继续沿用旧模式。
- **P0-1（shell.ts 职责爆炸）**：**未收口**，1197 行无减少。

第 94 审的 3 个 P1 问题：

- **P1-1（API 静默失败）**：`useWorkbenchState` 已修复；`useAttendance.ts:78`、`session.ts:111-113` 仍用 `.catch(() => [])`/`catch(() => null)`。
- **P1-2（竞态保护）**：审批详情路径已修复；`im.ts loadMessages/loadConversationDetail` **仍未修复**（新发现 2-1，升级为 P1）。
- **P1-3（localStorage 容量）**：未收口。

**新增 2 个 P1/P0 问题**：
- **P0 新（6-1）**：`vue-tsc` 类型检查失败，2 个类型错误需要立即修复，否则 CI 不通过。
- **P1 新（6-8）**：`useEmployeeAuthFlow.ts` 的 `submitLogin` catch 空块，登录错误可能被静默吞掉。

**整体评价**：

第 94 审以来的修复集中在"工作台/审批详情"路径，且质量不错（请求序号方案干净、测试覆盖竞态场景）。但 shell.ts 职责爆炸和统一错误处理这两个系统性问题仍未触动，且新增代码延续旧模式。

当前最紧迫的不是继续拆组件或拆 shell.ts（那是工作量问题），而是 **`vue-tsc` 类型错误必须修** 和 **`useEmployeeAuthFlow.ts` 的 catch 空块必须处理**——前者阻断 CI，后者可能在登录流程中吞掉关键错误。

**全量视角评分（同第 94 审）**：**6.5 / 10**

评分依据：核心数据流（会话加载、消息发送）无正确性 bug；但类型检查不通过、错误处理不统一、composable 零测试覆盖（除 useWorkbenchState）这三个系统性债务继续累积。

---

## 第八部分：第 94 审第八部分更新

| 对应问题 | 处理状态 | 落地位置 | 验证 |
|---|---|---|---|
| 3-1 / 6-1 shell.ts 职责拆分 | **未处理** | — | shell.ts 1197 行 |
| 3-3 / 6-3 全局错误处理层 | **未处理** | — | 62 处重复 |
| 3-6 / 6-7 localStorage 容量保护 | **未处理** | — | 写入无大小检查 |
| 4-1 / 4-2 工具函数去重 | **未处理** | — | statusLabel 4 处、时间格式化 4 处 |
| 4-5 KnowledgeAnswerCard 拆分 | **未处理** | — | 786 行未拆 |
| **新增** vue-tsc 类型错误（2-2 / 4-7 / 4-8） | **未处理** | — | `vue-tsc --noEmit` 失败 |
| **新增** im.ts 消息加载竞态（2-1） | **未处理** | — | loading/error 状态可能错乱 |
| **新增** useEmployeeAuthFlow.ts catch 空块（2-4） | **未处理** | — | 登录错误可能静默吞掉 |

---

*本文件按 `docs/审查/审查规范.md` 生成，是独立于第 94 审的增量审查。第 94 审结论和第八部分保留在 `第94审_employee-web全量前端深度剖析.md`，供后续审查复核。*
