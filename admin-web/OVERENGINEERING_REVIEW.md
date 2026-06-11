# admin-web 过度设计审查报告

## 统计

| 级别 | 数量 | 说明 |
|------|------|------|
| CRITICAL | 24 | 死代码：未使用的导出、导入、响应式状态、API 方法、store、类型 |
| HIGH | 4 | 不必要的抽象：单消费者工厂、重复命名空间、重复组件逻辑、未使用 prop |
| MEDIUM | 7 | 可简化：导出内部函数、缺失导入、未使用 defineExpose、未使用接口 |
| LOW | 2 | 轻微：拆分导入、防御性代码 |

## CRITICAL — 死代码

### stores/agentConfig.ts
- 整个文件死代码，`useAgentConfigStore` 从未被导入

### stores/*.ts — 7 个 store 的 reset() 从未被调用
- agentTasks.ts, department.ts, leaveGovernance.ts, leaveType.ts, position.ts, userView.ts, workflowGovernance.ts

### api/admin.ts — 7 个死 API 方法
- `agentConfig.disable`, `agentConfig.createTool`, `agentConfig.bindTool`, `agentConfig.acl`, `agentConfig.grantAcl`
- `org.updateUser`, `org.updateEmploymentStatus`, `org.upsertUserAccount`

### api/types.ts — 10 个死类型（对应死 API 方法）
- `WorkflowValidateRequest`, `CreateAgentToolRequest`, `BindAgentToolRequest`, `GrantAgentAclRequest`
- `AgentAclResponse`, `AgentToolBindingResponse`
- `OrgEmploymentStatusUpdateRequest`, `OrgUserAccountUpsertRequest`, `OrgUserUpdateRequest`
- `AdminAgentTaskFailureScanRequest`（完全未引用）

### components/AdminTable.vue — 多选功能死代码
- `selectable`, `selectedIds`, `allSelected`, `toggleRow`, `toggleAll`, `isMultiSelected` ~30 行从未使用

### views/ — 未使用的导入和变量
- AgentConfigView.vue: `SquarePen` 导入未使用
- AgentTasksView.vue: `selected` ref 死代码（模板用 getSelected()）
- DepartmentView.vue: `AdminConfirmDialog` 导入 + `confirmVisible` ref 死代码
- SettingsView.vue: `detailRows` 变量死代码

### router/index.ts
- `adminRoutes` 导出从未被其他文件导入

## HIGH — 不必要抽象

### api/admin.ts
- `createAdminApi()` 工厂只有 1 个消费者，应直接导出 `adminApi`
- `leave` 和 `leaveTypes` 两个命名空间调用同一个后端接口

### components/AdminPanel.vue
- `span` prop 声明了但组件内无对应 CSS，所有 `:span="7"` 调用无视觉效果

### views/DepartmentView.vue
- 手写了 `.compact-list` 的 markup+CSS，应复用 `AdminCompactList` 组件

## MEDIUM — 可简化

- `api/http.ts`: `parseJsonPreservingUnsafeIntegers` 不应导出
- `stores/session.ts`: `AdminAuthApi` 接口不应导出；`authApi` 默认参数注入从未被利用
- `components/AdminMetricStrip.vue`: `MetricCard` 接口导出但消费者用结构类型
- `components/AdminToast.vue`: `defineExpose(api)` 无父组件访问 ref
- `views/DepartmentView.vue`, `LeaveGovernanceView.vue`: 使用 `<AdminFilterBar>` 但未显式导入

## 修复结果

| 问题 | 状态 | 操作 |
|------|------|------|
| stores/agentConfig.ts 整个文件死代码 | 已删除 | 删除文件 |
| 7 个 store 的 reset() 从未调用 | 已删除 | 删除所有 reset() 方法 |
| admin.ts 7 个死 API 方法 | 已删除 | disable, createTool, bindTool, acl, grantAcl, updateUser, updateEmploymentStatus, upsertUserAccount |
| admin.ts 8 个对应死类型导入 | 已删除 | 清理 import 列表 |
| admin.ts leave/leaveTypes 重复命名空间 | 已合并 | 删除 leaveTypes，leave 新增 createType |
| AdminTable 多选功能死代码 ~30 行 | 已删除 | 移除 selectable/selectedIds/toggleRow/toggleAll |
| AdminToast defineExpose 无消费者 | 已删除 | 移除 defineExpose |
| AgentConfigView SquarePen 未使用导入 | 已删除 | 移除导入 |
| AgentTasksView selected ref 死代码 | 已删除 | 移除无意义的条件 ref |
| DepartmentView AdminConfirmDialog 未使用导入 | 已删除 | 替换为 AdminCompactList/AdminDetailList/AdminFilterBar |
| DepartmentView confirmVisible 未使用 | 已删除 | 移除 |
| DepartmentView 手写 compact-list/detail-list | 已替换 | 使用 AdminCompactList + AdminDetailList 组件 |
| SettingsView detailRows 死变量 | 已删除 | 移除 |

保留未动的项（理由）：
- router adminRoutes 导出：测试文件在用
- session.ts AdminAuthApi 导出 + authApi 默认参数：有效的测试注入点
- http.ts parseJsonPreservingUnsafeIntegers 导出：防御性代码，保留
- types.ts 中仅被 admin.ts 导入的类型：TypeScript 类型安全需要
