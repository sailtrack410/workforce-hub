# 第 94 审 employee-web 全量前端深度剖析

- 审查日期：2026-05-15
- 审查提交：
  - `fad9cdb`（完善后端个人中心与真实配置链路）
  - 工作区增量（employee-web 四轮重构：MessagesView 拆分、composable 提取、context 类型化、ref 主权下放）
- 审查范围：`employee-web/src/` 全量——stores/、composables/、views/、components/、api/、utils/、router/
- 审查目标：前 93 审聚焦后端模块审查，第 49/50 审审查了前端工程化与接口接入。本轮是首次 employee-web 全量前端深度剖析，重点覆盖四轮重构后的数据层健康度、错误处理一致性、请求安全性和测试覆盖完整性。本轮不审查后端变更，不新增业务能力。

---

## 第一部分：提交内容概览

自第 50 审以来，employee-web 经历了四轮重构，核心目标是将 MessagesView.vue 从 3876 行上帝组件拆解为壳组件 + Section 自管 + composable 分层架构。

### 四轮重构轨迹

| 轮次 | 核心动作 | MessagesView 行数 |
| --- | --- | --- |
| 第1轮 | 提取 4 个 composable（useImSocket/useFileUpload/useAttendance/useAgentTasks）+ 2 个 util | 3876→3331 |
| 第2轮 | 提取纯函数 utils + employee-shell-context 拆为 7 个 typed interface | 3331→2921 |
| 第3轮 | ref 声明权下放：useWorkbenchState/useMessagesState/usePersonalFileTransfer | 2921→1484 |
| 第4轮 | context 瘦身至每个 Section 最窄 3 字段 | 1484（稳定） |

### 当前文件结构（69 源文件，14,194 行）

```
employee-web/src/
├── api/           http.ts, employee.ts, types.ts, index.ts
├── stores/        shell.ts(1256), im.ts(851), session.ts(182), workflow.ts(115)
├── composables/   13 文件（useWorkbenchState 462, useMessagesState 439, useFileUpload 320 等）
├── views/         MessagesView.vue(1484) + 7 Section 组件
├── components/    shell/ (MessageBubble, ConversationProcessCard, cards/KnowledgeAnswerCard 827)
│                  ui/ (22 自定义组件)
├── utils/         employee-format.ts, file-format.ts, file-record.ts, auth-login-ui.ts
├── router/        index.ts
├── design-system/ tokens.ts
└── styles/        employee-shell.css
```

### 构建与测试

- `vue-tsc`：通过
- `vite build`：5.12s，MessagesView chunk 48KB（gzip 15.35KB）
- 测试：74/74 通过（13 个测试文件）

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | 第 50 审 3-3，前端 API 层不完整 | 第 50 审指出前端未调用已读位点接口，无法使用私聊/群聊创建和历史分页。当前 `employee.ts` 已补齐 `startPrivateConversation`、`createGroupConversation`、`updateReadPosition`、`messages(conversationId, { beforeSeq, size })`；`im.ts` store 已实现 `loadOlderMessages`、`startPrivateConversation`、`createGroupConversation`。 | 已处理 | 后续保持前端 API 层与后端员工端接口同步，新增后端接口时同步补前端 API 和 store 方法。 |
| 2-2 | 第 50 审 3-1，消息发送幂等 | 第 50 审指出 `clientMessageId` 未落库、无唯一约束，重试会重复发送。当前前端 `useMessagesState` 的 `sendConversationMessage` 仍使用 `clientMessageId`，后端幂等处理状态未在本轮确认。 | 未收口 | 需后端确认 `im_message` 是否已有 `client_message_id + conversation_id + sender_id` 唯一约束；前端侧重试时复用原 `clientMessageId` 而非生成新的。 |
| 2-3 | 第 50 审 4-1，shell.ts 职责过多 | 第 50 审指出 `shell.ts` 混合类型、状态、配置和工具函数。四轮重构后 `shell.ts` 仍 1256 行，职责未拆分。**已升级为 P0，见第三部分 3-1。** | 恶化 | 见 3-1 修复方向。 |
| 2-4 | 第 49 审 4-2，KnowledgeAnswerCard 过大 | 第 49 审指出 KnowledgeAnswerCard 组件内部状态多、模板长。当前仍 827 行，但结构清晰（6 状态分支 + 子分支），功能性完整。 | P2 | 按状态拆子组件，优先级低于数据层治理。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `employee-web/src/stores/shell.ts`（1256 行） | shell.ts 是全前端中心依赖，同时承载 14 个 TypeScript 接口定义（~250行）、静态导航/工作台/偏好配置（~120行）、localStorage 读写（~100行）、工具函数 displayValue/statusValue/formatDisplayDateTime/mapEmployeeFile（~100行）、6 个 filteredXxx 搜索/过滤 getter（~80行）、联系人收集 collectContacts/contactFromOrgUser（~60行）和 Pinia store 定义（~300行）。触发条件：任何新需求（加 Section、改过滤、加偏好字段）都要碰此文件，且 im.ts、所有 Section、所有 composable 都 import 它，改一行牵动全局。当前已出现工具函数重复征兆：`formatListDate()`（shell.ts:1184）与 `formatConversationDate()`（im.ts:661）几乎逐行相同；`statusLabel()` 在 workflow.ts:103 和 im.ts:649 各定义一份且映射枚举不同；`displayValue()`（shell.ts:713）与 `employee-format.ts` 的 `displayField()` 做的事几乎一样但分布在 store 和 utils 两处。失败路径：修改一处时间格式化逻辑忘记同步另一处，导致会话列表和消息列表日期显示不一致。 | P0 | 按 7 个职责拆分：接口→`types/shell.ts`，配置→`config/navigation.ts`+`config/workbench.ts`，localStorage→`composables/usePreferenceStorage.ts`，工具函数→`utils/date-format.ts`+`utils/status-labels.ts`，搜索→`composables/useSearch.ts`，联系人→`composables/useContacts.ts`，shell.ts 只留 ~300 行 Pinia store 定义。 |
| 3-2 | `KnowledgeAnswerCard.vue` + `employee-web/src/stores/shell.ts` | KnowledgeAnswerCard.vue 内部定义了 7 个接口（KnowledgeAnswerState、KnowledgeApiRef、KnowledgeCitation、KnowledgeRelatedFlow、KnowledgeAnswerAction、KnowledgeAnswerRelatedRef），shell.ts 又定义了 7 个几乎一样的 Conversation* 版本（ConversationKnowledgeApiRef、ConversationKnowledgeCitation 等）。字段完全相同，只是命名前缀不同。触发条件：后端新增知识库引用类型（如 `DOC`→`DATABASE`）时，开发者只更新了 shell.ts 的 `ConversationKnowledgeCitation`，忘记同步 KnowledgeAnswerCard.vue 的 `KnowledgeCitation`。失败路径：Card 组件渲染 citation 图标时走旧的类型判断，新类型 fallback 到 UNKNOWN 图标，而会话列表显示正确图标，用户看到不一致的 UI。 | P0 | 统一到 `types/knowledge.ts`，Card 和 store 都从同一处 import，删掉 Conversation* 前缀的重复定义。 |
| 3-3 | `useWorkbenchState.ts:186`、`useFileUpload.ts`、`useMessagesState.ts` 等 10 个文件 | `error instanceof Error ? error.message : 'xxx失败'` 出现 62 次。各 composable 各自吞错误、各自设 `xxxError ref`，没有全局错误拦截。触发条件：(1) API 返回 401 时应该统一跳登录页，但当前各 catch 只设本地 error ref，不处理认证过期；(2) 网络断开时 `error.message` 是 `Failed to fetch`，对用户无意义；(3) `ApiClientError` 带 code=1001 的业务错误，应统一解析业务错误码，但没有地方做。失败路径：用户登录过期后连续操作，每个操作只显示"加载失败"，无法跳转登录页重新认证。 | P0 | 创建 `utils/error-handler.ts`，提供 `resolveErrorMessage(error, fallback): string` 统一处理 ApiClientError（401→跳登录、业务 code→提取 message）、网络错误（Failed to fetch→"服务暂不可用"）、未知错误（fallback）。可选加 `handleApiError(error, { toast, report })` 支持 Sentry 上报。composable 里的 catch 改为调用统一函数。 |
| 3-4 | `useWorkbenchState.ts:186` | `Promise.all` + `.catch(() => [])`：`leave.accounts()`、`attendance.exceptions(dateRange)`、`attendance.dailyResults(dateRange)` 三个 API 各自 catch 为空数组。触发条件：后端全部不可用（部署失败、数据库连接断开）。失败路径：三个 API 全部 reject，全部被 `.catch(() => [])` 吞掉，UI 显示空列表"暂无数据"，用户以为没有请假账户和考勤记录，实际上后端全挂了。当前测试不覆盖全部 API 失败场景。 | P1 | 改用 `Promise.allSettled`，检查 rejected 数量：全部 rejected→显示错误状态；部分 rejected→成功的正常显示 + 失败的标灰/提示。 |
| 3-5 | `useWorkbenchState.ts` 的 `loadWorkflowRequestDetail`、`useMessagesState.ts` 的消息加载、`useAttendance.ts` 的考勤刷新 | 请求无竞态保护。触发条件：用户在审批列表快速切换两条记录：请求 A 发出（详情 A），请求 B 发出（详情 B），B 先返回设置 `workflowDetail = B`，A 后返回覆盖 `workflowDetail = A`。失败路径：UI 显示 B 的标题 + A 的详情，用户基于错误数据执行审批操作。`loadMessages` 同理：快速切换会话后，旧会话的消息可能覆盖新会话的消息列表。 | P1 | 每次新请求前 abort 旧请求（`AbortController`），或用请求序号（`let reqId; reqId++; if (reqId !== currentReqId) return;`）。推荐后者更轻量，前者更标准。 |
| 3-6 | `employee-web/src/stores/shell.ts` localStorage 操作 | `PersonalFileRecord[]` 缓存在 localStorage，上限 5MB。24 个文件 × 多字段序列化后可能超限。触发条件：用户有大量带长文件名的附件，或同一浏览器多个标签页并发写入。失败路径：`writeStoredPersonalFiles` 的 `try { setItem } catch { }` 静默吞掉写入失败，下次打开页面读到的是旧的或不完整的文件列表，与后端实际状态不一致。偏好设置同理：`normalizePreferenceInput` 只做范围裁剪和长度截断，没有完整性校验，XSS 或恶意扩展可注入非预期字段。 | P1 | 写入前检查 `JSON.stringify(data).length`，超限时只缓存最新 N 条或降级为不缓存；偏好读取加 key 白名单校验，非白名单字段丢弃。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `im.ts:649`、`workflow.ts:103`、`shell.ts:719`、`utils/employee-format.ts` | 四个文件各有一个"状态→中文"函数，名字不同（statusLabel/statusLabel/statusValue/statusText），映射枚举不同但有重叠。`APPROVED` 在 workflow 里是"已通过"，在 im 里也是"已通过"但映射的枚举集合不同。开发者无法判断该复用哪个函数，容易新建第五个。 | P2 | 统一到 `utils/status-labels.ts`，按业务域命名：`workflowStatusLabel()`、`conversationStatusLabel()`、`employmentStatusLabel()`。 |
| 4-2 | `im.ts:661`、`shell.ts:1184`、`shell.ts:733`、`utils/employee-format.ts` | 4 个时间格式化函数做 2 种格式（月日、月日时分），分散在 3 个文件。`formatConversationDate()` 和 `formatListDate()` 几乎逐行相同：同样的 ISO 解析、同样的空值判断、同样的 `月日` 输出格式。 | P2 | 统一到 `utils/date-format.ts`：`formatShortDate(value?)` (月日) + `formatFullDateTime(value?)` (月日时分)。 |
| 4-3 | `employee-web/src/stores/im.ts`（851 行） | im.ts 使用 Options API 定义 20+ 个 action，每个都是 `try { ... } catch { ... } finally { loading = false }` 模板代码。Options API 限制 action 之间组合：`pollAgentReply` 复制了 `loadMessages` 的消息处理逻辑，因为 Options API 里不能像普通函数一样组合调用。 | P2 | 改写为 Composition API（`defineStore('employee-im', () => { ... })`），action 变普通函数可自由组合。成本较高，优先级低于 P0 数据层拆分。 |
| 4-4 | `composables/` 目录 13 个文件 | `useWorkbenchState`（462行）、`useMessagesState`（439行）、`useFileUpload`（320行）、`useAttendance`（155行）共 1,376 行核心业务逻辑，**零测试覆盖**。74 个测试全部在 store/utils/api/router 层，composable 层是空白。触发条件：重构 composable 内部逻辑时没有回归保护，只能手动验证。 | P2 | 优先给 useWorkbenchState 和 useMessagesState 补关键路径测试（请假提交、消息加载、错误处理），mock API 层，重点验证错误处理和竞态场景。 |
| 4-5 | `components/shell/cards/KnowledgeAnswerCard.vue`（827 行） | 6 种状态渲染路径（ANSWERED/NO_MATCH/LOW_CONFIDENCE/NO_PERMISSION/INGESTING/SEARCH_FAILED），script ~300 行含 7 个接口 + 15 个函数 + 6 个 computed，template ~400 行，style ~130 行。功能性完整但维护成本高。 | P2 | 按状态拆子组件（AnsweredContent、NoMatchContent 等），主组件只做状态路由和 props 传递。 |
| 4-6 | `views/sections/SettingsSection.vue`（1020 行） | 47 个 ref 自管是对的，但 5 个子面板（个人主页、组织关系、账号安全、偏好设置、文件空间）全是独立表单/展示区，仍在一个文件里。 | P3 | 拆成 5 个子组件（ProfilePanel、OrganizationPanel、SecurityPanel、PreferencesPanel、StoragePanel）。 |
| 4-7 | `views/sections/ContactsSection.vue` | 局部定义 `presenceStatusLabel` 函数，未提取到 utils。前几轮审查已指出，仍未处理。 | P3 | 移入 `utils/status-labels.ts`。 |
| 4-8 | `stores/shell.ts` | `shellUser` 硬编码对象 `{ name: '员工', shortName: '员', ... }` 无 interface、无 `as const`，未登录时用户头像显示"员"。 | P3 | 定义 `ShellUserFallback` interface + `as const`。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| TypeScript 类型检查 | `cd employee-web && npx vue-tsc --noEmit` | 通过 | 全量 69 源文件无类型错误。 |
| 生产构建 | `cd employee-web && npx vite build` | 通过（5.12s） | MessagesView chunk 48KB（gzip 15.35KB），Section 组件正确 code-split。 |
| 单元测试 | `cd employee-web && npx vitest run` | 74/74 通过 | 13 个测试文件，覆盖 store/api/utils/router/context。 |
| 重复函数静态扫描 | `grep -rn "formatListDate\|formatConversationDate" src/` | 确认重复 | shell.ts:1184 和 im.ts:661 逻辑相同。 |
| 错误处理重复扫描 | `grep -c "error instanceof Error ? error.message" src/ -r` | 62 处 | 10 个文件，确认无统一错误处理层。 |
| 接口重复扫描 | `diff <(grep "^export interface K" src/components/shell/cards/KnowledgeAnswerCard.vue \| sed 's/export interface //' \| sed 's/ {//') <(grep "^export interface ConversationK" src/stores/shell.ts \| sed 's/export interface //' \| sed 's/ {//' \| sed 's/Conversation//')` | 确认重复 | 7 个接口字段完全一致，仅命名前缀不同。 |

未验证项：

- 没有跑后端 Maven 测试；本轮只审查前端代码。
- 没有做 E2E 测试；前端没有 E2E 测试框架。
- composable 层逻辑未通过测试验证（因为没有 composable 测试）。
- 没有验证 localStorage 在超限场景下的实际行为。
- 没有验证竞态场景下的实际数据错乱（因为没有并发测试）。

---

## 第六部分：处理优先级建议

| 序号 | 事项 | 当前判断 | 优先级 | 建议 |
| --- | --- | --- | --- | --- |
| 6-1 | 拆分 shell.ts 7 种职责 | shell.ts 1256 行是全前端中心依赖，任何新需求都要碰它，且已出现重复函数征兆。 | P0 | 按 3-1 方案拆分，shell.ts 最终只留 ~300 行 store 定义。预计 2-3h。 |
| 6-2 | 合并 KnowledgeAnswerCard 重复接口 | 7 个接口定义两份，改一边忘另一边 = 类型 bug。 | P0 | 统一到 `types/knowledge.ts`，删掉重复定义。预计 30min。 |
| 6-3 | 统一错误处理层 | 62 处复制粘贴，401 不跳登录、网络错误提示不友好、无法加 Sentry。 | P0 | 创建 `utils/error-handler.ts`，composable catch 改为调用统一函数。预计 1-2h。 |
| 6-4 | API 静默失败 | 三个 API 全挂时 UI 显示"暂无数据"，用户无法感知服务异常。 | P1 | 改用 `Promise.allSettled`，全失败显示错误状态。预计 30min。 |
| 6-5 | 请求竞态保护 | 快速切换审批详情/消息，旧请求覆盖新请求，可能审批错误数据。 | P1 | 加 AbortController 或请求序号。预计 1h。 |
| 6-6 | localStorage 大小保护 | 文件列表缓存可能超 5MB，写入失败静默吞掉。 | P1 | 写入前检查大小，超限降级。预计 30min。 |
| 6-7 | 工具函数去重 | 4 处 statusLabel + 4 处时间格式化，语义混乱。 | P2 | 统一到 `utils/status-labels.ts` + `utils/date-format.ts`。预计 30min。 |
| 6-8 | 补 composable 测试 | 1,376 行核心业务逻辑零测试覆盖。 | P2 | 优先 useWorkbenchState + useMessagesState 关键路径。预计 2-3h。 |
| 6-9 | im.ts Composition API 重写 | Options API 限制 action 复用，pollAgentReply 复制了 loadMessages 逻辑。 | P2 | P0 修完后推进。预计 3-4h。 |
| 6-10 | KnowledgeAnswerCard 拆分 | 827 行 6 状态组件，可按状态拆子组件。 | P2 | 功能完整，优先级低于数据层治理。预计 1-2h。 |

---

## 第七部分：阶段性结论

本轮是第 49/50 审之后首次 employee-web 全量前端深度剖析。四轮重构成功将 MessagesView.vue 从 3876 行拆至 1484 行，context 从 `Record<string, any>` 演进为 7 个 typed interface，组件层架构债务基本清完。

但审查视角从"组件层"扩展到"全量代码"后，暴露了更深层的问题：

1. **数据层是下一个膨胀点**：shell.ts 1256 行承载 7 种职责，是全前端中心依赖。formatListDate/formatConversationDate 重复、statusLabel 在 4 个文件各定义一份——这些重复不是"风格问题"，而是 shell.ts 职责不清导致的必然结果。shell.ts 膨胀到 2000+ 行只是时间问题，除非现在动刀。

2. **错误处理没有统一层**：62 处 `error instanceof Error ? error.message : 'xxx失败'` 复制粘贴，401 不跳登录、网络错误无友好提示、无法加 Sentry。这不是"代码重复"，是"全局行为缺失"。

3. **请求安全有真实风险**：审批详情快速切换的竞态问题可能导致用户基于错误数据执行审批操作，这不是理论推演，是真实可触发的 bug。

存在 3 个 P0 问题，不建议在未修复 P0 的情况下继续扩展前端业务能力。建议按 6-1→6-2→6-3→6-4→6-5 的顺序处理，预计总耗时 5-7h。

---

## 第八部分：本轮修复回写（2026-05-15）

本段记录第94审之后已落地的代码修复，不改写原始审查快照。

| 对应问题 | 处理状态 | 落地位置 | 验证 |
| --- | --- | --- | --- |
| 3-2 / 6-2：KnowledgeAnswerCard 与 shell.ts 重复类型 | 已处理 | 新增 `employee-web/src/types/knowledge.ts`，`KnowledgeAnswerCard.vue`、`stores/shell.ts`、`stores/im.ts` 改为引用同一组知识卡片类型 | `npm run build`、`npm run test` |
| 3-4 / 6-4：工作台汇总 API 全失败被吞掉 | 已处理 | `useWorkbenchState.refreshWorkbenchData()` 改为 `Promise.allSettled`；全部失败时设置 `workbenchError`，`WorkbenchSection.vue` 展示错误态并提供重试 | `useWorkbenchState.test.ts` 新增全失败用例 |
| 3-5 / 6-5：审批详情请求竞态 | 已处理审批详情路径 | `useWorkbenchState.loadWorkflowRequestDetail()` 增加请求序号，只允许最后一次请求写入详情和 loading 状态 | `useWorkbenchState.test.ts` 新增旧请求晚返回用例 |
| 4-4 / 6-8：composable 层缺测试 | 部分处理 | 新增 `useWorkbenchState.test.ts`，覆盖工作台错误与审批详情竞态；新增 `scroll.test.ts`，覆盖消息列表延迟布局后滚动到底部 | `npm run test` 当前 15 个文件 / 77 个测试通过 |

同步修复了一个审查文件未覆盖但用户实际触发的问题：消息发送/回复后列表可能停在顶部。根因是旧 `scrollMessagesToLatest()` 在第一帧 `scrollHeight <= clientHeight` 时提前返回，异步卡片或骨架屏完成渲染后不再补滚动。当前已提取 `employee-web/src/utils/scroll.ts`，滚动逻辑会等待多帧布局稳定，并在 `im.messageLoading` 变化后触发到底部。

仍未处理：3-1 shell.ts 职责拆分、3-3 全局错误处理层、3-6 localStorage 容量保护、4-1/4-2 工具函数进一步去重、4-5 KnowledgeAnswerCard 子组件拆分。这些属于更大迁移，需单独排期，避免在滚动与正确性修复中混入高风险大改。
