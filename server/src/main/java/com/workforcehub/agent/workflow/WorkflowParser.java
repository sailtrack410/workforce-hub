package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Component
public class WorkflowParser {

    private final ObjectMapper objectMapper;

    public WorkflowParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isWorkflowConfig(String workflowConfigJson) {
        if (!StringUtils.hasText(workflowConfigJson)) {
            return false;
        }
        try {
            JsonNode root = objectMapper.readTree(workflowConfigJson);
            return root.has("nodes") && root.get("nodes").isArray();
        } catch (Exception e) {
            return false;
        }
    }

    public WorkflowDefinition parse(String workflowConfigJson) {
        try {
            JsonNode root = objectMapper.readTree(workflowConfigJson);
            List<WorkflowNode> nodes = new ArrayList<>();
            for (JsonNode node : root.path("nodes")) {
                nodes.add(new WorkflowNode(
                    node.path("id").asText(null),
                    WorkflowNodeType.valueOf(node.path("type").asText("")),
                    node.path("config").isMissingNode() ? objectMapper.createObjectNode() : node.path("config"),
                    node.path("outputKey").asText(null),
                    node.path("outputFormat").isMissingNode() ? null : node.path("outputFormat")
                ));
            }
            List<WorkflowEdge> edges = new ArrayList<>();
            for (JsonNode edge : root.path("edges")) {
                Integer fromPort = edge.path("fromPort").isMissingNode() ? null : edge.path("fromPort").asInt();
                edges.add(new WorkflowEdge(edge.path("from").asText(null), edge.path("to").asText(null), fromPort));
            }
            var nodeMap = new LinkedHashMap<String, WorkflowNode>();
            for (WorkflowNode node : nodes) {
                nodeMap.put(node.id(), node);
            }
            for (WorkflowNode node : nodes) {
                if (node.type() != WorkflowNodeType.CONDITION) {
                    if (node.type() == WorkflowNodeType.INTENT) {
                        normalizeIntentEdges(edges, node);
                    }
                    continue;
                }
                int port = 0;
                for (JsonNode branch : node.config().path("branches")) {
                    String next = branch.path("next").asText(null);
                    if (StringUtils.hasText(next) && edges.stream().noneMatch(edge -> node.id().equals(edge.from()) && next.equals(edge.to()))) {
                        edges.add(new WorkflowEdge(node.id(), next, port));
                    }
                    port++;
                }
                String defaultNext = node.config().path("default").asText(null);
                if (StringUtils.hasText(defaultNext) && edges.stream().noneMatch(edge -> node.id().equals(edge.from()) && defaultNext.equals(edge.to()))) {
                    edges.add(new WorkflowEdge(node.id(), defaultNext, port));
                }
            }
            return new WorkflowDefinition(List.copyOf(nodes), List.copyOf(edges), nodeMap);
        } catch (Exception e) {
            throw new IllegalArgumentException("工作流配置解析失败", e);
        }
    }

    private void normalizeIntentEdges(List<WorkflowEdge> edges, WorkflowNode node) {
        List<WorkflowEdge> direct = edges.stream()
            .filter(edge -> node.id().equals(edge.from()) && StringUtils.hasText(edge.to()))
            .toList();
        if (direct.size() != 1 || direct.get(0).fromPort() != null) {
            return;
        }
        int portCount = node.config().path("intents").isArray() ? node.config().path("intents").size() + 1 : 1;
        for (int port = 1; port < portCount; port++) {
            int currentPort = port;
            if (edges.stream().noneMatch(edge -> node.id().equals(edge.from()) && edge.fromPort() != null && edge.fromPort() == currentPort)) {
                edges.add(new WorkflowEdge(node.id(), direct.get(0).to(), currentPort));
            }
        }
    }
}
