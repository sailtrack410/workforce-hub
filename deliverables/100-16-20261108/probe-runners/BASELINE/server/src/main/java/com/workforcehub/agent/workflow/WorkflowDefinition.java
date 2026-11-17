package com.workforcehub.agent.workflow;

import java.util.List;
import java.util.Map;

public record WorkflowDefinition(
    List<WorkflowNode> nodes,
    List<WorkflowEdge> edges,
    Map<String, WorkflowNode> nodeMap
) {
    public WorkflowNode node(String nodeId) {
        return nodeMap.get(nodeId);
    }

    public WorkflowNode startNode() {
        return nodes.stream()
            .filter(node -> node.type() == WorkflowNodeType.START)
            .findFirst()
            .orElse(null);
    }
}
