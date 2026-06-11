package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class WorkflowValidator {

    private final WorkflowParser parser;

    public WorkflowValidator(ObjectMapper objectMapper) {
        this.parser = new WorkflowParser(objectMapper);
    }

    @Autowired
    public WorkflowValidator(WorkflowParser parser) {
        this.parser = parser;
    }

    public WorkflowValidationResult validate(String workflowConfigJson) {
        List<String> messages = new ArrayList<>();
        WorkflowDefinition definition;
        try {
            definition = parser.parse(workflowConfigJson);
        } catch (Exception e) {
            return WorkflowValidationResult.failed(List.of("工作流配置不是合法DAG JSON"));
        }

        validateNodes(definition, messages);
        validateEdges(definition, messages);
        validateReachability(definition, messages);
        validateAcyclic(definition, messages);

        return messages.isEmpty() ? WorkflowValidationResult.ok() : WorkflowValidationResult.failed(messages);
    }

    private void validateNodes(WorkflowDefinition definition, List<String> messages) {
        Set<String> ids = new HashSet<>();
        int startCount = 0;
        int endCount = 0;
        for (WorkflowNode node : definition.nodes()) {
            if (!StringUtils.hasText(node.id())) {
                messages.add("节点 id 不能为空");
                continue;
            }
            if (!ids.add(node.id())) {
                messages.add("节点 id 重复: " + node.id());
            }
            if (node.type() == WorkflowNodeType.START) {
                startCount++;
            }
            if (node.type() == WorkflowNodeType.END) {
                endCount++;
            }
            if (node.type() == WorkflowNodeType.TOOL
                && !node.config().has("dataSources")
                && !StringUtils.hasText(node.config().path("toolCode").asText(null))) {
                messages.add("TOOL 节点缺少 toolCode: " + node.id());
            }
            if (isPromptNode(node) && !StringUtils.hasText(node.config().path("prompt").asText(null))) {
                messages.add(node.type().name() + " 节点缺少 prompt: " + node.id());
            }
            if (node.config().has("toolCalls") && !node.config().path("toolCalls").isArray()) {
                messages.add("节点 toolCalls 必须是数组: " + node.id());
            }
            for (var toolCall : node.config().path("toolCalls")) {
                if (!StringUtils.hasText(toolCall.path("toolCode").asText(null))) {
                    messages.add("节点内工具调用缺少 toolCode: " + node.id());
                }
            }
            validateOutputFormat(node, messages);
        }
        if (startCount != 1) {
            messages.add("工作流必须且只能有一个 START 节点");
        }
        if (endCount < 1) {
            messages.add("工作流至少需要一个 END 节点");
        }
    }

    private void validateEdges(WorkflowDefinition definition, List<String> messages) {
        for (WorkflowEdge edge : definition.edges()) {
            if (!definition.nodeMap().containsKey(edge.from())) {
                messages.add("边引用了不存在的起点: " + edge.from());
            }
            if (!definition.nodeMap().containsKey(edge.to())) {
                messages.add("边引用了不存在的终点: " + edge.to());
            }
        }
        for (WorkflowNode node : definition.nodes()) {
            if (node.type() == WorkflowNodeType.CONDITION) {
                node.config().path("branches").forEach(branch -> {
                    String next = branch.path("next").asText(null);
                    if (!definition.nodeMap().containsKey(next)) {
                        messages.add("CONDITION 节点分支引用了不存在的节点: " + next);
                    }
                });
                String defaultNext = node.config().path("default").asText(null);
                if (StringUtils.hasText(defaultNext) && !definition.nodeMap().containsKey(defaultNext)) {
                    messages.add("CONDITION 节点 default 引用了不存在的节点: " + defaultNext);
                }
            }
        }
    }

    private void validateReachability(WorkflowDefinition definition, List<String> messages) {
        WorkflowNode start = definition.startNode();
        if (start == null) {
            return;
        }
        Map<String, List<String>> outgoing = outgoing(definition);
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> stack = new ArrayDeque<>();
        stack.push(start.id());
        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (String next : outgoing.getOrDefault(current, List.of())) {
                stack.push(next);
            }
        }
        for (WorkflowNode node : definition.nodes()) {
            if (!visited.contains(node.id())) {
                messages.add("存在不可达节点: " + node.id());
            }
        }
    }

    private void validateAcyclic(WorkflowDefinition definition, List<String> messages) {
        Map<String, List<String>> outgoing = outgoing(definition);
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (WorkflowNode node : definition.nodes()) {
            if (hasCycle(node.id(), outgoing, visiting, visited)) {
                messages.add("工作流存在环路");
                return;
            }
        }
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> outgoing, Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        for (String next : outgoing.getOrDefault(nodeId, List.of())) {
            if (hasCycle(next, outgoing, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private Map<String, List<String>> outgoing(WorkflowDefinition definition) {
        Map<String, List<String>> outgoing = new HashMap<>();
        for (WorkflowEdge edge : definition.edges()) {
            outgoing.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to());
        }
        for (WorkflowNode node : definition.nodes()) {
            if (node.type() == WorkflowNodeType.CONDITION) {
                node.config().path("branches").forEach(branch ->
                    outgoing.computeIfAbsent(node.id(), ignored -> new ArrayList<>()).add(branch.path("next").asText(null))
                );
                String defaultNext = node.config().path("default").asText(null);
                if (StringUtils.hasText(defaultNext)) {
                    outgoing.computeIfAbsent(node.id(), ignored -> new ArrayList<>()).add(defaultNext);
                }
            }
        }
        return outgoing;
    }

    private boolean isPromptNode(WorkflowNode node) {
        return node.type() == WorkflowNodeType.LLM
            || node.type() == WorkflowNodeType.INTENT
            || node.type() == WorkflowNodeType.APPROVAL
            || node.type() == WorkflowNodeType.KNOWLEDGE
            || node.type() == WorkflowNodeType.FORM_EXTRACT
            || node.type() == WorkflowNodeType.SUMMARY
            || node.type() == WorkflowNodeType.TRANSFORM;
    }

    private void validateOutputFormat(WorkflowNode node, List<String> messages) {
        JsonNode outputFormat = node.outputFormat();
        if (outputFormat == null || outputFormat.isMissingNode() || outputFormat.isNull()) {
            return;
        }
        if (!outputFormat.isArray()) {
            messages.add("节点 outputFormat 必须是数组: " + node.id());
            return;
        }
        for (JsonNode field : outputFormat) {
            String key = field.path("key").asText("");
            if (!StringUtils.hasText(key)) {
                messages.add("节点 outputFormat 字段缺少 key: " + node.id());
            }
            if ("array".equals(field.path("type").asText(""))) {
                JsonNode itemFields = field.path("itemFields");
                if (!itemFields.isArray() || itemFields.isEmpty()) {
                    messages.add("节点 outputFormat 数组字段缺少 itemFields: " + node.id() + "." + key);
                    continue;
                }
                for (JsonNode itemField : itemFields) {
                    if (!StringUtils.hasText(itemField.path("key").asText(""))) {
                        messages.add("节点 outputFormat 数组项字段缺少 key: " + node.id() + "." + key);
                    }
                }
            }
        }
    }
}
