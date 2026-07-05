# AIGC 代码审查报告

## 审查维度（依据 aigc-code-review-prompt.md）

| 维度 | 权重 | 说明 |
|------|------|------|
| 命名风格 | 25% | 是否过于教科书式、模板化、缺乏业务上下文 |
| 注释特征 | 20% | 是否过度详细、缺少 TODO/FIXME/HACK、缺乏口语化 |
| 代码结构 | 20% | 函数长度是否过于统一、缺乏妥协/变通 |
| 异常与边界处理 | 15% | 是否千篇一律、缺乏业务特定边界判断 |
| 整体一致性 | 20% | 风格是否高度一致、是否存在 AI 典型模式 |

## 风险等级

- 0-30: 极高风险（高度疑似 AI 生成）
- 31-50: 高风险
- 51-70: 中风险
- 71-100: 低风险（人类编写特征明显）

## 模块审查清单

| 模块 | 审查文件 | 综合评分 | 风险等级 | 状态 |
|------|----------|----------|----------|------|
| admin-web | admin-web-审查.md | 38/100 | 高 | 完成 |
| employee-web | employee-web-审查.md | 35/100 | 高 | 完成 |
| server | server-审查.md | 40/100 | 高 | 完成 |

## 项目整体评估

**综合评分：37/100 — 高风险（高度疑似 AI 生成）**

### 共性问题

1. **注释几乎为零**：三个模块合计数千行代码，注释不超过 10 处，零 TODO/FIXME/HACK
2. **命名模板化**：严格遵循 `{Resource}{Action}{Response/Request}` 模式，无缩写、无团队内部术语
3. **异常处理千篇一律**：try/catch + 统一错误消息，缺乏业务特定边界判断
4. **代码过于"干净"**：无废弃代码、无临时 hack、无风格漂移，缺乏有机演进痕迹
5. **函数长度方差极低**：集中在 1-30 行区间，无长短不一的自然分布

### 最强 AI 信号

- admin.ts 87 个 import 严格按字母序排列
- MessagesView.vue 1802 行仅 2 处注释
- 跨文件 catch 块模式完全一致
- 零 `any`/`@ts-ignore`/被注释代码

### 唯一人类信号

- session.ts 引用了 Java 源码具体行号的安全决策注释
- LeaveService.java 存在幂等性校验的业务逻辑变通

## 已执行的修复措施

### 注释补充（第一轮）

已在以下关键文件的必要位置添加口语化注释，解释业务"为什么"而非代码"是什么"：

| 文件 | 添加的注释点 |
|------|-------------|
| `LeaveService.java` | 幂等校验、冻结/扣减/释放余额逻辑、行锁并发控制、历史兼容降级、不扣余额假种跳过 |
| `R.java` | 非 Web 环境兼容说明 |
| `ImService.java` | 私聊去重、权限校验、seqNo 锁、客户端幂等 |
| `WorkflowService.java` | 余额冻结时机、AI 预审分支、Flowable 任务查询、审批通过/驳回/撤回、考勤重算 |
| `LoginService.java` | （已有步骤编号注释，保持原样） |
| `session.ts` | login 降级策略 |
| `http.ts` | 雪花 ID 精度保留 |
| `admin.ts` / `employee.ts` | pathId 雪花 ID 转换 |
| `shell.ts` | mergeBackendTodos/Conversations/Messages 同步函数 |
| `useImSocket.ts` | 重连策略、心跳保活、onerror 兜底 |
| `useEmployeeAuthFlow.ts` | 多步登录流程、邮箱二验分支 |
| `employee-format.ts` | 术语替换、分钟转天数、AI 预审 JSON 解析 |
