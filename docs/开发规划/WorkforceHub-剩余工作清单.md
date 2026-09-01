# WorkforceHub 剩余工作清单

> 整理时间：2026-05-17 03:04
> 状态：Phase 1-3 已完成，Phase 4-5 待启动

---

## 一、admin-web 前端（10 页面）

### ✅ 已完成（Phase 1-3）

| Phase | 内容 | 状态 |
|---|---|---|
| Phase 1 | 组件化 + composable 抽取 + 4 View 重构 | ✅ |
| Phase 2 | CSS Token 系统化（50 个变量） | ✅ |
| Phase 3 | 新增 6 页面（Dashboard + 组织3 + Agent配置 + 假种） | ✅ |

**当前代码量**：3898 行（+929 vs Phase 1 前）

---

### 🔲 待完成

#### Phase 4：系统管理（预估 2-3 天）

| 页面 | 路由 | 功能 | 后端状态 |
|---|---|---|---|
| 角色管理 | /roles | 角色 CRUD + 权限分配 | 待确认 |
| 权限管理 | /permissions | 权限点列表 + 分配给角色 | 待确认 |
| 审计日志 | /audit-logs | 操作日志查询（时间/用户/模块筛选） | 待确认 |

**前置依赖**：
- 需要确认后端是否已有 RBAC 相关接口
- 审计日志表结构需要确认

**预估工作量**：
- 3 个新页面（约 400-500 行）
- API types 扩展（约 100 行）
- 路由 + 导航更新

---

#### Phase 5：表单验证强化（预估 1-2 天）

**当前问题**：
- 所有表单仅做基础必填校验
- 无实时校验反馈
- 无字段级错误提示
- 提交失败后错误信息不够友好

**改造范围**：

| 页面 | 需要强化的字段 |
|---|---|
| AgentConfigView | agentCode（正则）、agentName（长度） |
| LeaveTypeView | leaveCode（大写字母+数字+下划线，≤64）、leaveName（≤128）、balanceMode |
| DepartmentView | deptCode、deptName、parentId（树选择校验） |
| PositionView | positionCode、positionName |
| UserView | employeeNo、realName、mobile（格式）、primaryDeptId、positionId |

**方案选择**：
- 方案 A：纯手写校验函数（轻量，约 200 行）
- 方案 B：引入 VeeValidate 或 Zod（功能更强，但增加依赖）

**建议**：先用方案 A，不够用再迁移到 VeeValidate

---

## 二、employee-web 前端

### 🔲 P0 高优先级

| 问题 | 位置 | 状态 | 说明 |
|---|---|---|---|
| shell.ts 1197行职责爆炸 | src/stores/shell.ts | 🔲 未修 | 需拆分到 types/config/utils/composables |
| 62处重复错误处理 | 全项目 | 🔲 未修 | 需统一为 ApiClientError 检查 |

**建议策略**：
- shell.ts 拆分优先级更高，会影响后续维护
- 错误处理统一可以和 Phase 5 表单验证一起做

---

### 🔲 P1 中优先级

| 问题 | 位置 | 状态 | 说明 |
|---|---|---|---|
| im.ts 竞态保护 | src/composables/im.ts | 🔲 未修 | loadMessages/loadConversationDetail 无序号保护 |
| API 静默失败 | session.ts 等 | 🔲 未修 | Promise.all+.catch(()=>[]) 掩盖错误 |
| localStorage 无大小保护 | 多处 | 🔲 未修 | 可能超限导致崩溃 |

---

### 🔲 P2 低优先级

| 问题 | 说明 |
|---|---|
| composable 零测试 | 所有 composables 缺单测 |
| 工具函数重复 | statusLabel/formatDate/displayValue 重复定义 |

---

## 三、后端工作

### 🔲 DAG 工作流设计文档

**目标**：自研轻量 Agent DAG 引擎（不依赖 Flowable）

**核心设计**：
- 6 种节点：START/END/LLM/TOOL/CONDITION/LOOP
- 预估核心代码：500-800 行
- 模型解析：≤1500 行

**待输出**：
- `docs/架构与设计/Agent-DAG工作流引擎设计.md`
- 按现有 7 个文档风格撰写

---

### 🔲 后端接口缺口

| 功能 | 状态 | 说明 |
|---|---|---|
| 假种删除 | 🔲 无接口 | DELETE /api/admin/leave/types/{id} 待添加 |
| 假种更新 | 🔲 无接口 | PUT /api/admin/leave/types/{id} 待添加 |
| 审计日志查询 | 🔲 无接口 | 需确认是否已有 |
| RBAC 接口 | 🔲 待确认 | 需确认后端现有权限体系 |

---

## 四、博客写作

### 已完成（3 篇）

1. ✅ Agent 三层权限裁剪模型（约 5KB）
2. ✅ 模块化单体架构选型（约 4KB）
3. ✅ SPI 解耦模式（约 6.7KB）

### 🔲 待完成（2 篇）

4. 🔲 Flowable 工作流集成实战（预估 5-6KB）
5. 🔲 工程骨架与构建系统（预估 4-5KB）

**建议**：每篇 2000-3000 字，聚焦一个核心技术点

---

## 五、黑客松准备（5 月 29-31 日）

### 比赛信息
- **名称**：人工智能学院首届校园黑客松挑战赛
- **主题**：AI赋能基层治理·创意点亮智慧校园
- **时间**：5 月 29-31 日（3 天）
- **形式**：项目已完成，比赛期间展示答辩

### 🔲 待准备

| 项目 | 负责人 | 状态 |
|---|---|---|
| 答辩 PPT | 女朋友 | 🔲 待做 |
| Demo 视频录制 | 室友 | 🔲 待做 |
| 答辩话术准备（3 人） | 各自 | 🔲 待做 |
| 项目包装（校园/社区场景） | sailtrack | 🔲 待确认方向 |

**包装建议**：
- 企业 HR → 校园学生信息管理 / 社区居民管理
- 请假审批 → 校园活动审批 / 社区服务申请
- Agent 助手 → 校园智能助手 / 社区便民助手

---

## 六、优先级建议

### 本周（5 月 17-23 日）

1. **Phase 4 系统管理**（如果后端接口就绪）
2. **DAG 设计文档**（无外部依赖）
3. **博客第 4 篇**（Flowable 实战）

### 下周（5 月 24-30 日）

4. **Phase 5 表单验证**
5. **employee-web P0 修复**
6. **黑客松准备**（PPT + Demo + 话术）

### 黑客松后

7. 博客第 5 篇
8. employee-web P1/P2
9. 后端接口补全

---

## 七、风险与阻塞

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 后端 RBAC/审计日志接口未就绪 | Phase 4 阻塞 | 先做 Phase 5 和 DAG 设计 |
| 黑客松时间紧迫 | 准备不足 | 提前一周启动准备 |
| employee-web shell.ts 拆分复杂 | 改动大易出错 | 拆分后需要充分测试 |

---

## 八、代码仓库位置

| 项目 | 路径 |
|---|---|
| admin-web | `<repo-root>/admin-web/` |
| employee-web | `<repo-root>/employee-web/` |
| 后端 | `<repo-root>/server/` |
| 文档 | `<repo-root>/docs/` |

---

*最后更新：2026-05-17 03:04*
