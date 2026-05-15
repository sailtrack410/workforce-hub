# Agent 工作流 DAG 设计

> 说明：
> - 本文档定义 Agent 模块的工作流 DAG 编排引擎设计方案
> - 与《状态机设计.md》的关系是：状态机管「单任务的生命周期」，DAG 管「单任务内部的执行步骤编排」
> - 与《接口与事件清单.md》的关系是：本文定义工作流配置结构，接口文档负责 CRUD 接口

---

## 一、目的与范围

这份文档用于把 Agent 模块从「单次 LLM 调用返回」升级为「多步骤 DAG 工作流编排」，明确：

- 工作流 DAG 的节点类型定义
- 工作流配置的 JSON Schema
- 执行引擎的核心设计与执行逻辑
- 与现有 Agent 模块的集成方式
- 前端展示方案（静态图 + 执行回放）

当前目标不是直接写代码，而是先明确工作流的节点类型、配置结构、执行引擎设计和集成方案。

---

## 二、设计原则

### 1. 最小改动原则

- 已有代码（AgentTaskExecutionService、AgentToolExecutionService、OpenAiCompatibleAgentModelClient）**不改**
- 新增 workflow 包，执行时判断：版本有 workflowConfigJson → 走 DAG 执行；没有 → 走原来的单次调用
- 数据库不用新建表，`workflowConfigJson` 字段已预留

### 2. 节点类型最小化原则（P0）

第一版只实现 6 种节点类型，覆盖 95% 的场景：

| 节点类型 | 作用 | 实现优先级 |
|----------|------|------------|
| START | 入口，接收用户输入 | P0 |
| END | 出口，输出最终结果 | P0 |
| LLM | 调用大模型（支持模板变量） | P0 |
| TOOL | 调用 SPI 工具（复用 AgentToolExecutionService） | P0 |
| CONDITION | 条件分支（根据上下文变量路由） | P0 |
| LOOP | 循环执行子图（直到退出条件满足） | P1 |

P1（答辩加分项）：LOOP 节点，演示「不确定就重试」

### 3. 变量插值统一原则

- 所有节点的 config 中的字符串字段，均支持 `{{变量名}}` 模板语法
- 变量来源：用户输入（input）、上游节点输出（outputKey）

### 4. 前端不动效原则（答辩友好）

- 不做拖拽编排器（Coze 那种是几十人·月的工作量）
- 做两件事：① Mermaid 静态流程图 ② 执行回放（executionTrace 表格）

---

## 三、工作流配置 JSON Schema

### 3.1 顶层结构

```json
{
  "nodes": [ ... ],
  "edges": [ ... ]
}
```

### 3.2 节点定义（nodes）

每个节点是一个 JSON 对象，通用字段：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| id | String | 是 | 节点唯一标识 |
| type | String | 是 | 节点类型：START/END/LLM/TOOL/CONDITION/LOOP |
| config | Object | 否 | 节点配置（各类型不同） |
| outputKey | String | 否 | 输出写入上下文时的 key |

#### START 节点

```json
{
  "id": "start",
  "type": "START"
}
```

- 无 config，执行时把用户输入写入上下文 `input`
- 有且只有一个 START 节点

#### END 节点

```json
{
  "id": "end",
  "type": "END"
}
```

- 无 config，执行时从上下文取 `reply` 或 `answer` 作为最终输出

#### LLM 节点

```json
{
  "id": "classify",
  "type": "LLM",
  "config": {
    "prompt": "判断用户意图，只回复一个词：LEAVE/ATTENDANCE/POLICY",
    "modelConfigOverrides": {
      "temperature": 0.1
    }
  },
  "outputKey": "intent"
}
```

| config 字段 | 类型 | 必填 | 说明 |
|-------------|------|------|------|
| prompt | String | 是 | 支持 `{{变量}}` 模板 |
| modelConfigOverrides | Object | 否 | 覆盖版本级 modelConfig |

#### TOOL 节点

```json
{
  "id": "leave_query",
  "type": "TOOL",
  "config": {
    "toolCode": "LEAVE_GET_BALANCES",
    "args": {
      "userId": "{{input.userId}}"
    }
  },
  "outputKey": "leaveResult"
}
```

| config 字段 | 类型 | 必填 | 说明 |
|-------------|------|------|------|
| toolCode | String | 是 | SPI 工具编码 |
| args | Object | 是 | 工具参数，支持 `{{变量}}` 模板 |

#### CONDITION 节点

```json
{
  "id": "router",
  "type": "CONDITION",
  "config": {
    "field": "{{intent}}",
    "branches": [
      { "match": "LEAVE", "next": "leave_tool" },
      { "match": "ATTENDANCE", "next": "attendance_tool" },
      { "match": "POLICY", "next": "knowledge_tool" }
    ],
    "default": "knowledge_tool"
  }
}
```

- 执行逻辑：计算 field 的值 → 遍历 branches 找到 match 的 → 返回 next 作为下一个节点
- 都未匹配 → 走 default

#### LOOP 节点（P1）

```json
{
  "id": "verify_loop",
  "type": "LOOP",
  "config": {
    "maxIterations": 3,
    "exitCondition": "{{verify_llm.output.confident}} == true",
    "body": ["knowledge_tool", "verify_llm"]
  }
}
```

- maxIterations：最大循环次数（防止死循环）
- exitCondition：退出条件表达式
- body：循环体内节点 id 列表（按顺序执行）

### 3.3 边定义（edges）

```json
{
  "from": "classify",
  "to": "router"
}
```

- 有向边，表示执行流向

### 3.4 完整示例

```json
{
  "nodes": [
    { "id": "start", "type": "START" },
    { "id": "classify", "type": "LLM", "config": { "prompt": "判断意图：LEAVE/ATTENDANCE/POLICY" }, "outputKey": "intent" },
    { "id": "router", "type": "CONDITION", "config": { "field": "{{intent}}", "branches": [{"match":"LEAVE","next":"leave_tool"},{"match":"ATTENDANCE","next":"attendance_tool"},{"match":"POLICY","next":"knowledge_tool"}], "default": "knowledge_tool" }},
    { "id": "leave_tool", "type": "TOOL", "config": { "toolCode": "LEAVE_GET_BALANCES", "args": {"userId":"{{input.userId}}"} }, "outputKey": "toolResult" },
    { "id": "attendance_tool", "type": "TOOL", "config": { "toolCode": "ATTENDANCE_GET_MONTHLY_EXCEPTIONS", "args": {"userId":"{{input.userId}}","yearMonth":"{{input.yearMonth}}"} }, "outputKey": "toolResult" },
    { "id": "knowledge_tool", "type": "TOOL", "config": { "toolCode": "KNOWLEDGE_SEARCH_CHUNKS", "args": {"spaceId":1,"query":"{{input.text}}"} }, "outputKey": "knowledgeResult" },
    { "id": "format_reply", "type": "LLM", "config": { "prompt": "整理回复：{{toolResult}}" }, "outputKey": "reply" },
    { "id": "end", "type": "END" }
  ],
  "edges": [
    { "from": "start", "to": "classify" },
    { "from": "classify", "to": "router" },
    { "from": "leave_tool", "to": "format_reply" },
    { "from": "attendance_tool", "to": "format_reply" },
    { "from": "knowledge_tool", "to": "format_reply" },
    { "from": "format_reply", "to": "end" }
  ]
}
```

---

## 四、执行引擎设计

### 4.1 核心类结构

```
agent/workflow/
├── WorkflowGraph.java           // DAG 图结构
├── WorkflowNode.java            // 节点定义
├── WorkflowEdge.java           // 边定义
├── WorkflowExecutor.java       // 执行引擎核心
├── WorkflowContext.java        // 运行时上下文
├── WorkflowResult.java         // 执行结果
├── WorkflowTraceStep.java      // 执行轨迹中的一步
└── TemplateResolver.java       // {{变量}} 模板解析器
```

### 4.2 WorkflowExecutor 核心逻辑

```java
@Service
public class WorkflowExecutor {

    public WorkflowResult execute(AgentVersionRow version,
                                  String userInput,
                                  Map<String, Object> inputContext) {
        // 1. 解析 workflowConfigJson -> WorkflowGraph
        WorkflowGraph graph = parseGraph(version.getWorkflowConfigJson());

        // 2. 初始化上下文
        WorkflowContext context = new WorkflowContext();
        context.setInput(parseInput(userInput));
        inputContext.forEach(context::setVariable);

        // 3. 执行轨迹
        List<WorkflowTraceStep> trace = new ArrayList<>();

        // 4. 从 START 节点开始遍历
        String currentNodeId = graph.findStartNode().getId();
        while (currentNodeId != null) {
            WorkflowNode node = graph.getNode(currentNodeId);
            long startTime = System.currentTimeMillis();
            Object output = executeNode(node, context, graph);
            long durationMs = System.currentTimeMillis() - startTime;

            trace.add(new WorkflowTraceStep(node.getId(), node.getType(), output, durationMs));

            if (output != null && node.getOutputKey() != null) {
                context.setVariable(node.getOutputKey(), output);
            }

            currentNodeId = resolveNextNode(graph, node, context);
        }

        return new WorkflowResult(context.getVariable("reply"), trace);
    }

    private Object executeNode(WorkflowNode node, WorkflowContext context, WorkflowGraph graph) {
        return switch (node.getType()) {
            case START -> null;
            case END   -> context.getVariable("reply");
            case LLM   -> executeLlmNode(node, context);
            case TOOL  -> executeToolNode(node, context);
            case CONDITION -> null;
            case LOOP  -> executeLoopNode(node, context, graph);
        };
    }

    private String resolveNextNode(WorkflowGraph graph, WorkflowNode node, WorkflowContext context) {
        if (node.getType() == WorkflowNodeType.END) return null;
        if (node.getType() == WorkflowNodeType.CONDITION) {
            String field = templateResolver.resolve(node.getConfig().get("field"), context);
            for (Branch branch : node.getConfig().get("branches")) {
                if (branch.getMatch().equals(field)) return branch.getNext();
            }
            return node.getConfig().get("default");
        }
        return graph.getOutEdges(node.getId()).get(0).getTo();
    }
}
```

### 4.3 TemplateResolver 设计

```java
@Component
public class TemplateResolver {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public String resolve(String template, WorkflowContext context) {
        if (template == null) return null;
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = context.getVariable(varName);
            matcher.appendReplacement(sb, value == null ? "" : value.toString());
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
```

---

## 五、与现有代码集成

### 5.1 AgentTaskExecutionService 改动

在 `executeConversationReplyTask` 方法中，加入工作流判断：

```java
String workflowConfig = version.getWorkflowConfigJson();
if (StringUtils.hasText(workflowConfig)) {
    // 走 DAG 工作流执行
    WorkflowResult result = workflowExecutor.execute(version, userInput, Map.of("userId", userId));
    finishConversationSucceeded(command, context, result.getReply());
    saveExecutionTrace(taskId, result.getTrace());
} else {
    // 原来的逻辑：单次 LLM 调用
    AgentModelResponse modelResponse = agentModelClient.generateReply(...);
    finishConversationSucceeded(command, context, modelResponse.text());
}
```

### 5.2 AgentTaskRow 表改动

新增字段：

```sql
ALTER TABLE agent_tasks ADD COLUMN execution_trace_json TEXT COMMENT '执行轨迹JSON';
```

---

## 六、前端展示方案

### 6.1 静态流程图（L0）

自动生成 Mermaid 图：

```mermaid
graph TD
    start[START] --> classify[LLM: 意图分类]
    classify --> router{CONDITION: 意图路由}
    router -->|LEAVE| leave_tool[TOOL: 查假期]
    router -->|ATTENDANCE| attendance_tool[TOOL: 查考勤]
    router -->|POLICY| knowledge_tool[TOOL: 搜知识]
    leave_tool --> format_reply[LLM: 格式化]
    attendance_tool --> format_reply
    knowledge_tool --> format_reply
    format_reply --> end[END]
```

### 6.2 执行回放（L1）

| 步骤 | 节点 | 类型 | 输出 | 耗时 |
|------|------|------|------|------|
| 1 | classify | LLM | LEAVE | 1200ms |
| 2 | router | CONDITION | leave_tool | 5ms |
| 3 | leave_tool | TOOL | 年假3天 | 300ms |
| 4 | format_reply | LLM | 您还有3天年假 | 800ms |

---

## 七、实现优先级与时间估算

| 优先级 | 内容 | 代码量 | 时间 |
|--------|------|--------|------|
| P0 | 节点模型 + WorkflowExecutor 核心 | ~800行 | 1-2天 |
| P0 | TemplateResolver | ~100行 | 半天 |
| P0 | 集成到 AgentTaskExecutionService | ~50行 | 半天 |
| P1 | LOOP 节点 | ~150行 | 半天 |
| P1 | executionTrace 记录 | ~100行 | 半天 |
| P2 | Mermaid 图生成 | ~200行 | 1天 |

**总计 P0+P1**：~1200行，2-3天

---

## 八、与 Coze/Dify 对比

| 能力 | Coze/Dify | 我们的方案 |
|------|-----------|------------|
| DAG 执行引擎 | 自研 | 自研（P0） |
| 条件分支 | 是 | 是 |
| 循环重试 | 是 | 是（P1） |
| 执行轨迹回放 | ��� | ���（P1） |
| SPI 工具集成 | 否 | 是 |

**答辩话术**：「Coze/Dify 这些平台都是自研 DAG 引擎的，我们也选择了自研，因为 AI 工作流需要原生支持 LLM 调用、变量插值等特性。」

---

## 九、风险与应对

| 风险 | 应对 |
|------|------|
| LLM 输出格式不稳定 | temperature=0.1 + 失败走 default |
| 循环死循环 | maxIterations 硬性上限 |
| 工作流配置JSON写错 | 保存时校验 + 执行前预校验 |
| 变量未定义 | null 时用空字符串替换 |

---

## 十、下一步行动

1. [ ] 创建 agent/workflow/ 包，定义核心类
2. [ ] 实现 TemplateResolver
3. [ ] 实现 WorkflowExecutor 核心
4. [ ] 集成到 AgentTaskExecutionService
5. [ ] 新增 execution_trace_json 字段
6. [ ] 测试：跑通意图分类→分支→回复流程
7. [ ] （可选）P1：实现 LOOP 节点

---

> 文档版本：v1.0
> 创建时间：2026-05-14
> 作者：Workforce Hub 开发组
