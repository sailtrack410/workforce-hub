# Agent 工作流 DAG 设计稿评审报告

**日期**：2026-05-17
**场景**：产品评审（设计稿可行性评估）
**参与成员**：产品评审员（gstack-product-reviewer）
**评审文件**：`docs/架构与设计/Agent工作流DAG设计.md`

---

## 📌 TL;DR（执行摘要）

- **整体结论**：🟡 有条件通过 — 设计方向正确、范围克制，但有 3 个严重问题和 6 个重要问题必须在实施前修正
- **阻塞项数量**：3（TemplateResolver regex 不支持嵌套路径、DAG 无环校验缺失、"已有代码不改"声明不实）
- **关键发现**：原估算 ~1200行/2-3天 严重偏低，修正为 ~3400行/8-9天（含测试）
- **下一步**：修正设计稿中的 3 个🔴严重问题后即可进入实现阶段

---

## 🎯 核心结论卡片

| 项目 | 内容 |
|------|------|
| Go / No-Go | 🟡 条件 Go |
| 严重度分布 | 🔴 3 / 🟠 6 / 🟡 5 / 🟢 4 |
| 关键行动项 | 10 条 |
| 建议负责人 | 后端（6 条）、PM（2 条）、前端（1 条）、架构（1 条） |

---

## 1. 产品评审员核心结论

### 🎯 产品评审员（产品/技术评审）

- **核心判断**：设计稿最大的优点是"自知之明"——明确不做拖拽编排器、P0 只做 6 种节点、Mermaid 静态图方案。但最大的问题是"伪最小化"——声称"已有代码不改"、估算 "~1200行、2-3天"，实际代码分析表明侵入程度和工作量都被低估了约 2 倍。
- **关键建议**：
  1. 修正 TemplateResolver 的 `\w+` regex，支持嵌套路径（如 `result.leaveBalances.0.availableDays`）
  2. 新增 DAG 解析阶段的拓扑排序 + 无环校验
  3. 为每个节点执行添加 try/catch + 错误类型分类 + fallback 路由
  4. 诚实列出需要改动的现有文件清单，修正工作量估算

---

## 2. 综合审查发现（按严重度排序）

| # | 严重度 | 类别 | 位置 | 问题描述 | 建议 | 来源 |
|---|--------|------|------|---------|------|------|
| 1 | 🔴 | 引擎 | 四、TemplateResolver | `\w+` regex 无法匹配嵌套路径，TOOL 节点返回的嵌套 JSON 无法被后续节点引用 | 改为支持点号路径的 regex 或引入 SpEL | 评审员 |
| 2 | 🔴 | 引擎 | 四、WorkflowExecutor | DAG 无环校验和错误处理完全缺失，while 循环可能死循环，节点异常未捕获 | 解析阶段做拓扑排序校验；每节点 try/catch + fallback | 评审员 |
| 3 | 🔴 | 集成 | 五、集成方案 | "已有代码不改"声明与实际不符，AgentTaskExecutionService/AgentToolExecutionService/AgentModelClient 均需改动 | 诚实列出改动文件清单，重新估算 | 评审员 |
| 4 | 🟠 | 集成 | 五、AgentToolExecutionService | `execute(AgentToolBindingRow, String)` 方法签名与 DAG TOOL 节点不兼容 | 新增 `executeByCode(String, Map)` 重载 | 评审员 |
| 5 | 🟠 | 引擎 | 四、LLM 节点 | AgentModelRequest 不支持节点级 modelConfigOverrides | WorkflowExecutor 层合并节点级配置到 modelConfigJson | 评审员 |
| 6 | 🟠 | 引擎 | 四/九 | 单节点/工作流级超时机制未设计，现有 AgentTaskTimeoutJobService 不覆盖 DAG 执行超时 | 引入 per-step timeout + 工作流总超时 | 评审员 |
| 7 | 🟠 | 架构 | 五 | DAG 输出与 Flowable 审批工作流的集成点未定义 | 明确 DAG 输出如何喂给 WorkflowAgentResultCallback | 评审员 |
| 8 | 🟠 | 估算 | 七 | P0 估算 ~1200行/2-3天 严重偏低 | 修正为 ~2500行/4-5天（含测试） | 评审员 |
| 9 | 🟠 | 文档 | 八 | Coze/Dify 对比不诚实，SPI 工具对比有误，缺少关键差异 | 补充无可视化编排器、无并行执行等差异 | 评审员 |
| 10 | 🟡 | 设计 | 二 | 缺少 HUMAN_CONFIRM 节点类型，系统已有完整 SPI | P1 加入该节点类型 | 评审员 |
| 11 | 🟡 | 设计 | 三 | CONDITION 节点只支持字符串相等匹配 | 支持 `>`, `<`, `contains`, `isEmpty` 等操作符 | 评审员 |
| 12 | 🟡 | 前端 | 六 | Mermaid 渲染方案未明确（前端 vs 后端） | 明确技术选型 | 评审员 |
| 13 | 🟡 | 工程 | 三 | 缺少 workflowConfigJson 的 JSON Schema 校验规范 | 保存时做结构校验 | 评审员 |
| 14 | 🟡 | 设计 | 三 | LOOP 节点 exitCondition 表达式语法与 TemplateResolver 不一致 | 统一表达式语言 | 评审员 |
| 15 | 🟢 | 设计 | 二 | 最小改动原则方向正确 | — | 评审员 |
| 16 | 🟢 | 设计 | 二 | 节点类型最小化，P0 只做 5+1 种，克制得当 | — | 评审员 |
| 17 | 🟢 | 工程 | — | workflowConfigJson 字段已预留，降低迁移成本 | — | 评审员 |
| 18 | 🟢 | 前端 | 六 | Mermaid 静态图 + 执行回放方案实用 | — | 评审员 |

---

## ✅ 行动清单

| # | 行动 | 负责方 | 紧急度 | 期望完成 |
|---|------|--------|--------|---------|
| 1 | 修正 TemplateResolver regex，支持嵌套路径（如 `result.leaveBalances.0.availableDays`） | 后端 | P0 | 实现前 |
| 2 | 新增 DAG 解析时的拓扑排序 + 无环校验 + 连通性校验 | 后端 | P0 | 实现前 |
| 3 | 为 WorkflowExecutor 每个节点执行添加 try/catch + 错误类型分类 + fallback 路由 | 后端 | P0 | 实现前 |
| 4 | 新增 `AgentToolExecutionService.executeByCode(String, Map)` 重载方法 | 后端 | P0 | 实现前 |
| 5 | 设计 per-step timeout + 工作流总超时机制 | 后端 | P0 | 实现前 |
| 6 | 诚实列出需要改动的现有文件清单，修正 P0 工作量估算 | PM | P0 | 实现前 |
| 7 | 明确 DAG 输出与 Flowable 审批工作流的集成方案 | 架构 | P1 | 实现中 |
| 8 | P1 加入 HUMAN_CONFIRM 节点类型 | 后端 | P1 | P1 阶段 |
| 9 | 修正 Coze/Dify 对比表，补充关键差异 | PM | P1 | 实现前 |
| 10 | 明确前端 Mermaid 渲染技术方案 | 前端 | P1 | 实现前 |

---

## ⚠️ 待完善 / 已知局限

- 设计稿未覆盖"多轮对话"场景下的 DAG 执行（当前只考虑单次输入→单次输出）
- 并行执行能力未设计（Dify 支持，当前方案是纯串行）
- 可视化编排器明确不做（合理，但应在文档中标注为未来路线图）
- 安全审计未涉及（DAG 配置注入、变量插值 XSS 等）

---

## 📚 评审依据

- 评审文件：`docs/架构与设计/Agent工作流DAG设计.md`（v1.0，2026-05-14）
- 后端代码：`server/src/main/java/com/workforcehub/agent/` 目录（AgentTaskExecutionService、AgentToolExecutionService、OpenAiCompatibleAgentModelClient、AgentVersionRow）
- 数据库：`agt_agent_version` 表（已预留 `workflow_config_json` 字段）
- SPI 接口：`WorkflowAgentPreReviewTrigger`、`WorkflowAgentResultCallback`
- 前端代码：`employee-web/src/composables/useAgentTasks.ts`、`admin-web/src/views/AgentConfigView.vue`
- 相关设计文档：`状态机设计.md`、`接口与事件清单.md`

---

> 本报告由软件工坊 AI 协作生成，关键决策请由工程负责人复核。
