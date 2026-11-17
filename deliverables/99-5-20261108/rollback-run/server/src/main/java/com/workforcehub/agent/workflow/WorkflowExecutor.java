package com.workforcehub.agent.workflow;

import com.workforcehub.common.util.IdGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.repository.AgentRepository;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.agent.repository.AgentVersionRow;
import com.workforcehub.agent.repository.LlmModelRow;
import com.workforcehub.agent.service.AgentToolExecutionService;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.dto.LeaveAccountResponse;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

import static com.workforcehub.common.util.JsonValues.write;
import static com.workforcehub.common.util.TextValues.safeSummary;

@Component
public class WorkflowExecutor {

    private static final int MAX_STEPS = 100;

    private final AgentRepository agentRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowParser workflowParser;
    private final WorkflowValidator workflowValidator;
    private final TemplateResolver templateResolver;
    private final AgentToolExecutionService toolExecutionService;
    private final OpenAiCompatibleAgentModelClient agentModelClient;
    private final OrgDirectoryService orgDirectoryService;
    private final LeaveService leaveService;

    public WorkflowExecutor(AgentRepository agentRepository,
                            ObjectMapper objectMapper,
                            WorkflowParser workflowParser,
                            WorkflowValidator workflowValidator,
                            TemplateResolver templateResolver,
                            AgentToolExecutionService toolExecutionService,
                            OpenAiCompatibleAgentModelClient agentModelClient,
                            OrgDirectoryService orgDirectoryService,
                            LeaveService leaveService) {
        this.agentRepository = agentRepository;
        this.objectMapper = objectMapper;
        this.workflowParser = workflowParser;
        this.workflowValidator = workflowValidator;
        this.templateResolver = templateResolver;
        this.toolExecutionService = toolExecutionService;
        this.agentModelClient = agentModelClient;
        this.orgDirectoryService = orgDirectoryService;
        this.leaveService = leaveService;
    }

    // 工作流执行入口：验证配置、初始化上下文、按节点顺序执行
    public WorkflowExecutionResult execute(Long taskId,
                                           Long agentId,
                                           String agentName,
                                           AgentVersionRow version,
                                           String inputPayloadJson,
                                           Long currentUserId) {
        WorkflowValidationResult validation = workflowValidator.validate(version.workflowConfigJson());
        if (!validation.valid()) {
            throw new BizException(400, "工作流配置无效: " + String.join("; ", validation.messages()));
        }
        WorkflowDefinition definition = workflowParser.parse(version.workflowConfigJson());
        WorkflowContext context = new WorkflowContext(objectMapper);
        JsonNode input = normalizeInput(readJson(StringUtils.hasText(inputPayloadJson) ? inputPayloadJson : "{}"));
        if (currentUserId != null && input instanceof ObjectNode inputObject) {
            // 仅在客户端未提供时注入；input.userId 保留客户端值，数据源/工具层各自做授权校验
            if (!inputObject.has("currentUserId")) inputObject.put("currentUserId", currentUserId);
            if (!inputObject.has("userId")) inputObject.put("userId", currentUserId);
        }
        input = withRequestSummary(input);
        context.putJson("input", input);
        putInputFields(context, input);
        if (currentUserId != null) {
            context.put("currentUserId", currentUserId);
            context.putJson("currentUser", objectMapper.valueToTree(orgDirectoryService.getUserDetail(currentUserId)));
        }
        context.put("taskId", taskId);
        ArrayNode trace = objectMapper.createArrayNode();
        WorkflowNode current = definition.startNode();
        String replyText = null;
        for (int i = 0; i < MAX_STEPS && current != null; i++) {
            StepOutcome outcome = executeNode(taskId, agentId, agentName, version, definition, current, context);
            trace.add(outcome.traceStep());
            if (current.type() == WorkflowNodeType.END) {
                replyText = outcome.nextNodeId();
                break;
            }
            current = definition.node(outcome.nextNodeId());
        }
        if (replyText == null) {
            throw new BizException(400, "工作流未到达 END 节点");
        }
        return new WorkflowExecutionResult(replyText, write(objectMapper, trace));
    }

    private StepOutcome executeNode(Long taskId,
                                    Long agentId,
                                    String agentName,
                                    AgentVersionRow version,
                                    WorkflowDefinition definition,
                                    WorkflowNode node,
                                    WorkflowContext context) {
        long started = System.nanoTime();
        ObjectNode step = objectMapper.createObjectNode();
        step.put("nodeId", node.id());
        step.put("type", node.type().name());
        try {
            String next = switch (node.type()) {
                case START -> {
                    JsonNode input = context.get("input");
                    if (input != null && !input.isMissingNode()) {
                        context.putJson(outputKey(node), input);
                        step.set("output", input);
                    }
                    yield singleNext(definition, node.id());
                }
                case TOOL -> node.config().has("dataSources")
                    ? executeDataNode(definition, node, context, step)
                    : executeToolNode(taskId, version, definition, node, context, step);
                case INTENT -> executeIntentNode(taskId, agentId, agentName, version, definition, node, context, step);
                case LLM, APPROVAL, KNOWLEDGE, FORM_EXTRACT, SUMMARY, TRANSFORM ->
                    executePromptNode(taskId, agentId, agentName, version, definition, node, context, step);
                case CONDITION -> selectConditionNext(node, context);
                case END -> resolveEndReply(node, context);
            };
            step.put("status", "SUCCEEDED");
            step.put("durationMs", elapsedMillis(started));
            return new StepOutcome(next, step);
        } catch (Exception e) {
            step.put("status", "FAILED");
            step.put("durationMs", elapsedMillis(started));
            step.put("errorMessage", safeSummary(e.getMessage()));
            throw e;
        }
    }

    // 从工作流上下文取服务端注入的任务归属人；取不到返回 null，由工具层按"不强制"兜底（仅管理端试运行场景）
    private Long operatorUserId(WorkflowContext context) {
        JsonNode userId = context.get("currentUserId");
        return userId == null || userId.isNull() ? null : userId.asLong();
    }

    private String executeToolNode(Long taskId,
                                   AgentVersionRow version,
                                   WorkflowDefinition definition,
                                   WorkflowNode node,
                                   WorkflowContext context,
                                   ObjectNode step) {
        String toolCode = node.config().path("toolCode").asText("").trim().toUpperCase();
        AgentToolBindingRow binding = agentRepository.getBindingByVersionAndToolCode(version.id(), toolCode);
        if (binding == null) {
            agentRepository.getActiveToolByCodeOrThrow(toolCode);
            throw new BizException(403, "AI助手未授权调用该工具");
        }
        JsonNode args = templateResolver.resolveJson(node.config().path("args"), context);
        String requestPayloadJson = write(objectMapper, args == null || args.isMissingNode() ? objectMapper.createObjectNode() : args);
        try {
            String responsePayloadJson = toolExecutionService.execute(binding, requestPayloadJson, operatorUserId(context));
            agentRepository.insertToolCallLog(
                IdGenerator.nextId(),
                taskId,
                binding.toolId(),
                requestPayloadJson,
                responsePayloadJson,
                "APPROVED",
                "SUCCESS"
            );
            JsonNode response = applyOutputFormat(node, readJson(responsePayloadJson));
            context.putJson(outputKey(node), response);
            step.set("request", readJson(requestPayloadJson));
            step.set("output", response);
            return singleNext(definition, node.id());
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.insertToolCallLog(
                IdGenerator.nextId(),
                taskId,
                binding.toolId(),
                requestPayloadJson,
                write(objectMapper, java.util.Map.of(
                    "status", "FAILED",
                    "errorMessage", errorMessage == null ? "工具执行失败" : errorMessage
                )),
                "APPROVED",
                "FAILED"
            );
            throw e;
        }
    }

    private String executeDataNode(WorkflowDefinition definition,
                                   WorkflowNode node,
                                   WorkflowContext context,
                                   ObjectNode step) {
        JsonNode output = applyOutputFormat(node, loadDataSources(node, context));
        context.putJson(outputKey(node), output);
        step.set("output", output);
        return singleNext(definition, node.id());
    }

    private String executePromptNode(Long taskId,
                                     Long agentId,
                                     String agentName,
                                     AgentVersionRow version,
                                     WorkflowDefinition definition,
                                     WorkflowNode node,
                                     WorkflowContext context,
                                     ObjectNode step) {
        ObjectNode dataSourceOutput = loadDataSources(node, context);
        if (!dataSourceOutput.isEmpty()) {
            step.set("dataSources", dataSourceOutput);
        }
        executeNodeToolCalls(taskId, version, node, context, step);
        String prompt = withOutputFormatInstruction(
            templateResolver.resolveString(node.config().path("prompt").asText(""), context),
            node.outputFormat()
        );
        AgentModelResponse response = agentModelClient.generateReply(new AgentModelRequest(
            agentId,
            taskId,
            agentName,
            version.systemPrompt(),
            prompt,
            resolveModelConfig(node, version.modelConfigJson())
        ));
        String text = response.text();
        JsonNode output = applyOutputFormat(node, readJsonOrText(text));
        context.putJson(outputKey(node), output);
        step.set("output", output);
        return singleNext(definition, node.id());
    }

    private String executeIntentNode(Long taskId,
                                     Long agentId,
                                     String agentName,
                                     AgentVersionRow version,
                                     WorkflowDefinition definition,
                                     WorkflowNode node,
                                     WorkflowContext context,
                                     ObjectNode step) {
        ObjectNode dataSourceOutput = loadDataSources(node, context);
        if (!dataSourceOutput.isEmpty()) {
            step.set("dataSources", dataSourceOutput);
        }
        executeNodeToolCalls(taskId, version, node, context, step);
        String prompt = withOutputFormatInstruction(
            intentPrompt(node, context),
            node.outputFormat()
        );
        AgentModelResponse response = agentModelClient.generateReply(new AgentModelRequest(
            agentId,
            taskId,
            agentName,
            version.systemPrompt(),
            prompt,
            resolveModelConfig(node, version.modelConfigJson())
        ));
        String text = response.text();
        JsonNode output = applyOutputFormat(node, readJsonOrText(text));
        context.putJson(outputKey(node), output);
        step.set("output", output);
        String matchedIntent = output.isObject() ? output.path("intent").asText("") : output.asText(text.trim()).trim();
        int portIdx = resolveIntentPort(node, matchedIntent);
        step.put("matchedIntent", matchedIntent);
        step.put("portIndex", portIdx);
        String nextId = findNodeByPort(definition, node.id(), portIdx);
        return nextId != null ? nextId : singleNext(definition, node.id());
    }

    private void executeNodeToolCalls(Long taskId,
                                      AgentVersionRow version,
                                      WorkflowNode node,
                                      WorkflowContext context,
                                      ObjectNode step) {
        JsonNode toolCalls = node.config().path("toolCalls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            return;
        }
        ArrayNode outputs = objectMapper.createArrayNode();
        int index = 0;
        for (JsonNode toolCall : toolCalls) {
            ObjectNode output = executeToolCall(taskId, version, toolCall, context);
            outputs.add(output);
            String outputKey = toolCall.path("outputKey").asText("");
            if (StringUtils.hasText(outputKey)) {
                context.putJson(outputKey, output.path("response"));
            } else {
                context.putJson(node.id() + "_tool_" + index, output.path("response"));
            }
            index++;
        }
        step.set("toolCalls", outputs);
    }

    private ObjectNode executeToolCall(Long taskId,
                                       AgentVersionRow version,
                                       JsonNode toolCall,
                                       WorkflowContext context) {
        String toolCode = toolCall.path("toolCode").asText("").trim().toUpperCase();
        AgentToolBindingRow binding = agentRepository.getBindingByVersionAndToolCode(version.id(), toolCode);
        if (binding == null) {
            agentRepository.getActiveToolByCodeOrThrow(toolCode);
            throw new BizException(403, "AI助手未授权调用该工具");
        }
        JsonNode args = templateResolver.resolveJson(toolCall.path("args"), context);
        String requestPayloadJson = write(objectMapper, args == null || args.isMissingNode() ? objectMapper.createObjectNode() : args);
        try {
            String responsePayloadJson = toolExecutionService.execute(binding, requestPayloadJson, operatorUserId(context));
            agentRepository.insertToolCallLog(
                IdGenerator.nextId(),
                taskId,
                binding.toolId(),
                requestPayloadJson,
                responsePayloadJson,
                "APPROVED",
                "SUCCESS"
            );
            ObjectNode output = objectMapper.createObjectNode();
            JsonNode response = readJson(responsePayloadJson);
            output.put("toolCode", toolCode);
            output.set("request", readJson(requestPayloadJson));
            output.set("response", response);
            return output;
        } catch (BizException e) {
            String errorMessage = safeSummary(e.getMessage());
            agentRepository.insertToolCallLog(
                IdGenerator.nextId(),
                taskId,
                binding.toolId(),
                requestPayloadJson,
                write(objectMapper, java.util.Map.of(
                    "status", "FAILED",
                    "errorMessage", errorMessage == null ? "工具执行失败" : errorMessage
                )),
                "APPROVED",
                "FAILED"
            );
            throw e;
        }
    }

    private String selectConditionNext(WorkflowNode node, WorkflowContext context) {
        JsonNode value = context.get(node.config().path("field").asText(""));
        String actual = value == null || value.isNull() ? "" : value.asText();
        for (JsonNode branch : node.config().path("branches")) {
            if (actual.equals(branch.path("match").asText())) {
                return branch.path("next").asText(null);
            }
        }
        return node.config().path("default").asText(null);
    }

    private String intentPrompt(WorkflowNode node, WorkflowContext context) {
        JsonNode intentsNode = node.config().path("intents");
        StringBuilder intentList = new StringBuilder();
        if (intentsNode.isArray() && intentsNode.size() > 0) {
            for (int i = 0; i < intentsNode.size(); i++) {
                if (i > 0) intentList.append(" / ");
                intentList.append(intentsNode.get(i).asText());
            }
        }
        JsonNode queryNode = context.get("query");
        String inputText = queryNode == null || queryNode.isNull() ? "" : queryNode.asText("");
        String basePrompt = templateResolver.resolveString(node.config().path("prompt").asText(""), context);
        if (StringUtils.hasText(basePrompt)) {
            basePrompt += "\n\n";
        }
        return basePrompt
            + "请根据以下输入内容判断属于哪个意图类别。\n\n"
            + "可选意图：" + intentList + " / 其他\n\n"
            + "判断规则：先判断用户是不是在问制度、政策、规则、流程说明、知识库或文档；"
            + "只要是在问这些说明类内容，即使同时包含请假、年假、申请、办理、我想请假等词，也优先返回知识库；"
            + "只有用户明确要求发起、提交或办理请假，且没有询问制度、政策、规则、流程说明、知识库或文档时，才返回发起请假流程；"
            + "如果可选意图包含更细的 OA 意图，部门人数、成员数、同事数量返回查询部门人数，假期余额、年假剩余返回查询假期；"
            + "只有没有细分意图时，查询个人假期余额、审批、流程或申请办理才返回 OA。\n\n"
            + "输入内容：" + inputText + "\n\n"
            + "只返回 JSON：{\"intent\":\"意图类别\"}";
    }

    private int resolveIntentPort(WorkflowNode node, String matchedIntent) {
        JsonNode intents = node.config().path("intents");
        if (!intents.isArray() || !StringUtils.hasText(matchedIntent)) return intents.isArray() ? intents.size() : 0;
        for (int i = 0; i < intents.size(); i++) {
            if (matchedIntent.equals(intents.get(i).asText())) return i;
        }
        return intents.size();
    }

    private String findNodeByPort(WorkflowDefinition definition, String fromNodeId, int portIdx) {
        for (var edge : definition.edges()) {
            if (edge.from().equals(fromNodeId)) {
                int edgePort = edge.fromPort() != null ? edge.fromPort() : 0;
                if (edgePort == portIdx) return edge.to();
            }
        }
        return null;
    }

    private String resolveEndReply(WorkflowNode node, WorkflowContext context) {
        JsonNode replyJson = node.config().path("replyJson");
        if (!replyJson.isMissingNode() && !replyJson.isNull()) {
            return write(objectMapper, templateResolver.resolveJson(replyJson, context));
        }
        String template = node.config().path("reply").asText(null);
        if (StringUtils.hasText(template)) {
            return templateResolver.resolveString(template, context);
        }
        JsonNode answer = context.get("answer");
        if (answer != null && !answer.isNull()) {
            return answer.asText(answer.toString());
        }
        return "";
    }

    private ObjectNode loadDataSources(WorkflowNode node, WorkflowContext context) {
        ObjectNode output = objectMapper.createObjectNode();
        JsonNode dataSources = node.config().path("dataSources");
        if (!dataSources.isArray() || dataSources.isEmpty()) {
            return output;
        }
        for (JsonNode item : dataSources) {
            String key = item.asText("");
            if (!StringUtils.hasText(key)) {
                continue;
            }
            JsonNode value = loadDataSource(key, node, context);
            context.putJson(key, value);
            output.set(key, value);
        }
        return output;
    }

    private JsonNode loadDataSource(String key, WorkflowNode node, WorkflowContext context) {
        Long targetUserId = resolveAuthorizedTargetUserId(node, context);
        return switch (key) {
            case "employeeInfo" -> objectMapper.valueToTree(orgDirectoryService.getUserDetail(targetUserId));
            case "leaveBalance" -> objectMapper.valueToTree(leaveService.listAdminAccounts(targetUserId, null));
            case "leaveBalanceFields" -> leaveBalanceFields(targetUserId);
            case "departmentHeadcountFields" -> departmentHeadcountFields(targetUserId);
            case "orgChart" -> orgChartNode(targetUserId);
            default -> objectMapper.valueToTree(java.util.Map.of("status", "UNAVAILABLE"));
        };
    }

    private ArrayNode leaveBalanceFields(Long targetUserId) {
        ArrayNode fields = objectMapper.createArrayNode();
        List<LeaveAccountResponse> accounts = leaveService.listAdminAccounts(targetUserId, null);
        for (LeaveAccountResponse account : accounts) {
            ObjectNode name = fields.addObject();
            name.put("label", "假期名称");
            name.put("value", account.getLeaveName());
            name.put("tone", "green");

            ObjectNode available = fields.addObject();
            available.put("label", "可用天数");
            available.put("value", leaveDayLabel(account.getAvailableMinutes()));
            available.put("tone", "green");

            ObjectNode frozen = fields.addObject();
            frozen.put("label", "冻结天数");
            frozen.put("value", leaveDayLabel(account.getFrozenMinutes()));
            frozen.put("tone", "amber");
        }
        return fields;
    }

    private ArrayNode departmentHeadcountFields(Long targetUserId) {
        ArrayNode fields = objectMapper.createArrayNode();
        OrgUserDetailResponse user = orgDirectoryService.getUserDetail(targetUserId);

        ObjectNode department = fields.addObject();
        department.put("label", "部门");
        department.put("value", user.getPrimaryDeptName());
        department.put("tone", "blue");

        ObjectNode headcount = fields.addObject();
        headcount.put("label", "部门人数");
        headcount.put("value", orgDirectoryService.countDepartmentUsers(user.getPrimaryDeptId()) + " 人");
        headcount.put("tone", "green");

        return fields;
    }

    private String leaveDayLabel(Integer minutes) {
        BigDecimal days = BigDecimal.valueOf(Math.max(0, minutes == null ? 0 : minutes))
            .divide(BigDecimal.valueOf(480), 1, java.math.RoundingMode.HALF_UP);
        return days.stripTrailingZeros().toPlainString() + " 天";
    }

    private ObjectNode orgChartNode(Long targetUserId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("manager", objectMapper.valueToTree(orgDirectoryService.getManager(targetUserId)));
        node.set("directSubordinates", objectMapper.valueToTree(orgDirectoryService.listDirectSubordinates(targetUserId)));
        return node;
    }

    private Long resolveAuthorizedTargetUserId(WorkflowNode node, WorkflowContext context) {
        Long currentUserId = nodeLong(context.get("currentUserId"));
        Long targetUserId = nodeLong(templateResolver.resolveJson(node.config().path("targetUserId"), context));
        if (targetUserId == null) targetUserId = nodeLong(context.get("applicantId"));
        if (targetUserId == null) targetUserId = nodeLong(context.get("input.applicantId"));
        if (targetUserId == null) targetUserId = nodeLong(context.get("input.userId"));
        if (targetUserId == null) targetUserId = currentUserId;
        if (targetUserId == null || currentUserId == null) {
            throw new BizException(403, "缺少员工身份上下文，无法查询员工数据");
        }
        if (targetUserId.equals(currentUserId)) {
            return targetUserId;
        }
        OrgUserDetailResponse target = orgDirectoryService.getUserDetail(targetUserId);
        if (currentUserId.equals(target.getManagerUserId())) {
            return targetUserId;
        }
        throw new BizException(403, "无权查询该员工信息");
    }

    private String singleNext(WorkflowDefinition definition, String nodeId) {
        List<String> next = definition.edges().stream()
            .filter(edge -> nodeId.equals(edge.from()))
            .map(WorkflowEdge::to)
            .toList();
        if (next.size() != 1) {
            throw new BizException(400, "节点必须且只能有一条后续边: " + nodeId);
        }
        return next.get(0);
    }

    private String outputKey(WorkflowNode node) {
        return StringUtils.hasText(node.outputKey()) ? node.outputKey() : node.id();
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception e) {
            throw new BizException(400, "JSON解析失败");
        }
    }

    private JsonNode normalizeInput(JsonNode input) {
        if (input == null || input.isMissingNode() || input.isNull()) {
            return objectMapper.createObjectNode();
        }
        if (input.isObject()) {
            return input.deepCopy();
        }
        ObjectNode wrapper = objectMapper.createObjectNode();
        wrapper.set("value", input);
        return wrapper;
    }

    private JsonNode withRequestSummary(JsonNode input) {
        if (!(input instanceof ObjectNode inputObject)) {
            return input;
        }
        String query = inputObject.path("query").asText("");
        if (!StringUtils.hasText(query)) {
            query = inputObject.path("text").asText("");
        }
        if (!StringUtils.hasText(query)) {
            return input;
        }
        String category = "QA";
        if (query.contains("制度") || query.contains("知识库") || query.contains("政策") || query.contains("文档")) {
            category = "KNOWLEDGE";
        } else if (query.contains("假期") || query.contains("年假") || query.contains("请假") || query.contains("审批") || query.contains("申请")
            || query.contains("部门") || query.contains("人数") || query.contains("成员") || query.contains("同事")) {
            category = "OA";
        }
        inputObject.put("category", category);
        inputObject.put("isQa", "QA".equals(category));
        inputObject.put("isOa", "OA".equals(category));
        inputObject.put("isKnowledge", "KNOWLEDGE".equals(category));
        inputObject.put("queryTitle", query.length() > 18 ? query.substring(0, 18) : query);
        return inputObject;
    }

    private void putInputFields(WorkflowContext context, JsonNode input) {
        if (!input.isObject()) {
            return;
        }
        input.fields().forEachRemaining(entry -> context.putJson(entry.getKey(), entry.getValue()));
    }

    private Long nodeLong(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual() && StringUtils.hasText(node.asText())) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private JsonNode readJsonOrText(String value) {
        if (!StringUtils.hasText(value)) {
            return TextNode.valueOf("");
        }
        String trimmed = value.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception e) {
            if (trimmed.startsWith("```")) {
                int lineEnd = trimmed.indexOf('\n');
                int fenceEnd = trimmed.lastIndexOf("```");
                if (lineEnd >= 0 && fenceEnd > lineEnd) {
                    String fencedJson = trimmed.substring(lineEnd + 1, fenceEnd).trim();
                    try {
                        return objectMapper.readTree(fencedJson);
                    } catch (Exception ignored) {
                        return TextNode.valueOf(value);
                    }
                }
            }
            return TextNode.valueOf(value);
        }
    }

    private String withOutputFormatInstruction(String prompt, JsonNode outputFormat) {
        if (!hasOutputFormat(outputFormat)) {
            return prompt;
        }
        String rootType = rootOutputType(outputFormat);
        if (!"object".equals(rootType)) {
            return prompt + "\n\n输出要求：只返回 " + rootType + " 类型结果，不要输出解释或 Markdown。"
                + ("array".equals(rootType) ? "数组字段的每个元素只允许返回 itemFields 配置的字段；缺失值返回 null。\n" : "\n")
                + "outputFormat: " + outputFormatInstruction(outputFormat);
        }
        return prompt + "\n\n输出要求：只返回 JSON 对象，不要输出解释或 Markdown。"
            + "只允许返回 outputFormat 配置的字段；缺失值返回 null；array/object 字段只允许返回 itemFields 配置的字段。\n"
            + "outputFormat: " + outputFormatInstruction(outputFormat);
    }

    private String outputFormatInstruction(JsonNode outputFormat) {
        return write(objectMapper, outputFormat);
    }

    private JsonNode applyOutputFormat(WorkflowNode node, JsonNode output) {
        if (!hasOutputFormat(node.outputFormat())) {
            return output;
        }
        if (node.outputFormat().size() == 1) {
            JsonNode field = node.outputFormat().get(0);
            String key = field.path("key").asText("").trim();
            String type = field.path("type").asText("string");
            if (!StringUtils.hasText(key) && !"object".equals(type) && !"json".equals(type)) {
                JsonNode sourceValue = output != null && output.isObject()
                    ? output.path(key)
                    : output;
                return normalizeOutputField(sourceValue, field);
            }
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        JsonNode source = output != null && output.isObject() ? output : objectMapper.createObjectNode();
        for (JsonNode field : node.outputFormat()) {
            String key = field.path("key").asText("").trim();
            if (!StringUtils.hasText(key)) {
                continue;
            }
            normalized.set(key, normalizeOutputField(source.path(key), field));
        }
        return normalized;
    }

    private JsonNode normalizeOutputField(JsonNode value, JsonNode format) {
        String type = format.path("type").asText("string");
        if (value == null || value.isMissingNode() || value.isNull()) {
            return NullNode.getInstance();
        }
        return switch (type) {
            case "array" -> normalizeArray(value, format.path("itemFields"));
            case "object" -> normalizeObject(value, format.path("itemFields"));
            case "json" -> value.deepCopy();
            case "number" -> normalizeNumber(value);
            case "boolean" -> {
                if (value.isBoolean()) yield BooleanNode.valueOf(value.asBoolean());
                String text = value.asText("").trim();
                if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
                    yield BooleanNode.valueOf(Boolean.parseBoolean(text));
                }
                yield BooleanNode.valueOf(value.asBoolean(false));
            }
            default -> value.isValueNode() ? TextNode.valueOf(value.asText()) : TextNode.valueOf(value.toString());
        };
    }

    private JsonNode normalizeNumber(JsonNode value) {
        if (value.isNumber()) {
            return value.deepCopy();
        }
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            try {
                return DecimalNode.valueOf(new BigDecimal(value.asText().trim()));
            } catch (NumberFormatException ignored) {
                return NullNode.getInstance();
            }
        }
        return NullNode.getInstance();
    }

    private ArrayNode normalizeArray(JsonNode value, JsonNode itemFields) {
        ArrayNode array = objectMapper.createArrayNode();
        if (!value.isArray()) {
            return array;
        }
        if (!itemFields.isArray() || itemFields.isEmpty()) {
            value.forEach(item -> array.add(item.deepCopy()));
            return array;
        }
        for (JsonNode item : value) {
            ObjectNode normalizedItem = objectMapper.createObjectNode();
            JsonNode source = item.isObject() ? item : objectMapper.createObjectNode();
            for (JsonNode field : itemFields) {
                String key = field.path("key").asText("").trim();
                if (StringUtils.hasText(key)) {
                    normalizedItem.set(key, normalizeOutputField(source.path(key), field));
                }
            }
            array.add(normalizedItem);
        }
        return array;
    }

    private JsonNode normalizeObject(JsonNode value, JsonNode itemFields) {
        if (!value.isObject()) {
            return objectMapper.createObjectNode();
        }
        if (!itemFields.isArray() || itemFields.isEmpty()) {
            return value.deepCopy();
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        for (JsonNode field : itemFields) {
            String key = field.path("key").asText("").trim();
            if (StringUtils.hasText(key)) {
                normalized.set(key, normalizeOutputField(value.path(key), field));
            }
        }
        return normalized;
    }

    private boolean hasOutputFormat(JsonNode outputFormat) {
        return outputFormat != null && outputFormat.isArray() && !outputFormat.isEmpty();
    }

    private String rootOutputType(JsonNode outputFormat) {
        if (outputFormat.size() != 1) {
            return "object";
        }
        return outputFormat.get(0).path("type").asText("string");
    }

    private String resolveModelConfig(WorkflowNode node, String fallbackModelConfigJson) {
        String modelIdText = node.config() == null ? "" : node.config().path("modelId").asText("");
        try {
            if (!StringUtils.hasText(modelIdText) && StringUtils.hasText(fallbackModelConfigJson)) {
                return fallbackModelConfigJson;
            }
            LlmModelRow model = StringUtils.hasText(modelIdText)
                ? agentRepository.getLlmModel(Long.parseLong(modelIdText.trim()))
                : firstActiveModel();
            if (model != null && StringUtils.hasText(model.apiKey())) {
                ObjectNode modelConfig = objectMapper.createObjectNode();
                modelConfig.put("baseUrl", model.endpoint() == null ? "" : model.endpoint());
                modelConfig.put("apiKey", model.apiKey());
                modelConfig.put("model", model.modelName());
                copyModelTuningFields(modelConfig, readModelConfig(fallbackModelConfigJson));
                copyModelTuningFields(modelConfig, node.config());
                return write(objectMapper, modelConfig);
            }
        } catch (NumberFormatException ignored) {
            return StringUtils.hasText(fallbackModelConfigJson) ? fallbackModelConfigJson : "{}";
        }
        return StringUtils.hasText(fallbackModelConfigJson) ? fallbackModelConfigJson : "{}";
    }

    private JsonNode readModelConfig(String modelConfigJson) {
        if (!StringUtils.hasText(modelConfigJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(modelConfigJson);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private void copyModelTuningFields(ObjectNode target, JsonNode source) {
        if (source == null || !source.isObject()) {
            return;
        }
        for (String field : List.of("timeout", "maxTokens", "temperature")) {
            JsonNode value = source.get(field);
            if (value != null && !value.isNull()) {
                target.set(field, value.deepCopy());
            }
        }
    }

    private LlmModelRow firstActiveModel() {
        return agentRepository.listLlmModels().stream()
            .filter(model -> "ACTIVE".equals(model.status()) && "TEXT".equals(model.modelType()) && StringUtils.hasText(model.apiKey()))
            .filter(model -> Boolean.TRUE.equals(model.defaultFlag()))
            .findFirst()
            .orElseGet(() -> agentRepository.listLlmModels().stream()
                .filter(model -> "ACTIVE".equals(model.status()) && "TEXT".equals(model.modelType()) && StringUtils.hasText(model.apiKey()))
                .findFirst()
                .orElse(null));
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private record StepOutcome(String nextNodeId, ObjectNode traceStep) {
    }

    // ==================== Trial Run ====================

    public java.util.Map<String, Object> executeNode(WorkflowNode node, WorkflowDefinition definition,
                                                      java.util.Map<String, Object> input,
                                                      String systemPrompt, String modelConfigJson) {
        return executeNode(node, definition, input, systemPrompt, modelConfigJson, null);
    }

    public java.util.Map<String, Object> executeNode(WorkflowNode node, WorkflowDefinition definition,
                                                      java.util.Map<String, Object> input,
                                                      String systemPrompt, String modelConfigJson,
                                                      AgentVersionRow version) {
        WorkflowContext context = new WorkflowContext(objectMapper);
        JsonNode inputNode = normalizeInput(objectMapper.valueToTree(input));
        inputNode = withRequestSummary(inputNode);
        context.putJson("input", inputNode);
        putInputFields(context, inputNode);
        Long currentUserId = nodeLong(inputNode.path("currentUserId"));
        if (currentUserId == null) currentUserId = nodeLong(inputNode.path("userId"));
        if (currentUserId != null) {
            context.put("currentUserId", currentUserId);
            context.putJson("currentUser", objectMapper.valueToTree(orgDirectoryService.getUserDetail(currentUserId)));
        }
        input.forEach((k, v) -> {
            if (v instanceof String s) {
                context.put(k, s);
            } else {
                context.putJson(k, objectMapper.valueToTree(v));
            }
        });

        ObjectNode result = objectMapper.createObjectNode();
        result.put("nodeId", node.id());
        result.put("type", node.type().name());

        try {
            switch (node.type()) {
                case START -> {
                    result.put("nextNodeId", singleNext(definition, node.id()));
                    result.set("output", objectMapper.valueToTree(input));
                }
                case TOOL -> {
                    if (node.config().has("dataSources")) {
                        JsonNode output = applyOutputFormat(node, loadDataSources(node, context));
                        context.putJson(outputKey(node), output);
                        result.set("output", output);
                    } else {
                        String toolCode = node.config().path("toolCode").asText("").trim().toUpperCase();
                        JsonNode args = templateResolver.resolveJson(node.config().path("args"), context);
                        String requestPayloadJson = write(objectMapper, args == null || args.isMissingNode() ? objectMapper.createObjectNode() : args);
                        result.put("toolCode", toolCode);
                        result.set("request", readJson(requestPayloadJson));
                        if (version != null) {
                            AgentToolBindingRow binding = agentRepository.getBindingByVersionAndToolCode(version.id(), toolCode);
                            if (binding == null) {
                                agentRepository.getActiveToolByCodeOrThrow(toolCode);
                                throw new BizException(403, "AI助手未授权调用该工具");
                            }
                            JsonNode output = applyOutputFormat(node, readJson(toolExecutionService.execute(binding, requestPayloadJson, operatorUserId(context))));
                            context.putJson(outputKey(node), output);
                            result.set("output", output);
                        }
                    }
                    result.put("nextNodeId", singleNext(definition, node.id()));
                }
                case CONDITION -> {
                    String next = selectConditionNext(node, context);
                    result.put("nextNodeId", next);
                }
                case END -> {
                    String reply = resolveEndReply(node, context);
                    result.put("reply", reply);
                }
                default -> {
                    ObjectNode dataSourceOutput = loadDataSources(node, context);
                    if (!dataSourceOutput.isEmpty()) {
                        result.set("dataSources", dataSourceOutput);
                    }
                    if (version != null) {
                        ArrayNode toolCalls = executeNodeToolCallsForPreview(version, node, context);
                        if (!toolCalls.isEmpty()) {
                            result.set("toolCalls", toolCalls);
                        }
                    }
                    String prompt;
                    if (node.type() == WorkflowNodeType.INTENT) {
                        prompt = intentPrompt(node, context);
                    } else {
                        prompt = templateResolver.resolveString(node.config().path("prompt").asText(""), context);
                    }
                    prompt = withOutputFormatInstruction(prompt, node.outputFormat());
                    result.put("resolvedPrompt", prompt);

                    String sp = systemPrompt != null ? systemPrompt : "你是一个企业协同助手。";
                    String mc = resolveModelConfig(node, modelConfigJson);

                    AgentModelResponse response = agentModelClient.generateReply(new AgentModelRequest(
                        0L, 0L, "trial", sp, prompt, mc
                    ));

                    String text = response.text();
                    JsonNode output = applyOutputFormat(node, readJsonOrText(text));
                    context.putJson(outputKey(node), output);
                    result.set("output", output);

                    if (node.type() == WorkflowNodeType.INTENT) {
                        String matchedIntent = output.isObject() ? output.path("intent").asText("") : output.asText(text.trim()).trim();
                        int portIdx = resolveIntentPort(node, matchedIntent);
                        result.put("matchedIntent", matchedIntent);
                        result.put("portIndex", portIdx);
                        String nextId = findNodeByPort(definition, node.id(), portIdx);
                        result.put("nextNodeId", nextId != null ? nextId : singleNext(definition, node.id()));
                    } else {
                        result.put("nextNodeId", singleNext(definition, node.id()));
                    }
                }
            }
            result.put("status", "SUCCESS");
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", safeSummary(e.getMessage()));
        }

        return objectMapper.convertValue(result, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private ArrayNode executeNodeToolCallsForPreview(AgentVersionRow version,
                                                     WorkflowNode node,
                                                     WorkflowContext context) {
        ArrayNode outputs = objectMapper.createArrayNode();
        JsonNode calls = node.config().path("toolCalls");
        if (!calls.isArray() || calls.isEmpty()) {
            return outputs;
        }
        for (JsonNode toolCall : calls) {
            ObjectNode output = executeSingleToolCallForPreview(version, toolCall, context);
            outputs.add(output);
            String outputKey = toolCall.path("outputKey").asText("");
            if (StringUtils.hasText(outputKey)) {
                context.putJson(outputKey, output.path("response"));
            }
        }
        return outputs;
    }

    private ObjectNode executeSingleToolCallForPreview(AgentVersionRow version,
                                                       JsonNode toolCall,
                                                       WorkflowContext context) {
        String toolCode = toolCall.path("toolCode").asText("").trim().toUpperCase();
        AgentToolBindingRow binding = agentRepository.getBindingByVersionAndToolCode(version.id(), toolCode);
        if (binding == null) {
            agentRepository.getActiveToolByCodeOrThrow(toolCode);
            throw new BizException(403, "AI助手未授权调用该工具");
        }
        JsonNode args = templateResolver.resolveJson(toolCall.path("args"), context);
        String requestPayloadJson = write(objectMapper, args == null || args.isMissingNode() ? objectMapper.createObjectNode() : args);
        String responsePayloadJson = toolExecutionService.execute(binding, requestPayloadJson, operatorUserId(context));
        ObjectNode output = objectMapper.createObjectNode();
        output.put("toolCode", toolCode);
        output.set("request", readJson(requestPayloadJson));
        output.set("response", readJson(responsePayloadJson));
        return output;
    }

}
