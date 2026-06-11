package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class WorkflowContext {

    private final ObjectMapper objectMapper;
    private final ObjectNode variables;

    public WorkflowContext(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.variables = objectMapper.createObjectNode();
    }

    public void put(String key, Object value) {
        variables.set(key, objectMapper.valueToTree(value));
    }

    public void putJson(String key, JsonNode value) {
        variables.set(key, value == null ? objectMapper.nullNode() : value);
    }

    public JsonNode get(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        JsonNode current = variables;
        for (String segment : path.split("\\.")) {
            if (current == null || current.isNull() || current.isMissingNode()) {
                return null;
            }
            if (current.isArray()) {
                try {
                    current = current.get(Integer.parseInt(segment));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                current = current.get(segment);
                if (current == null && "intent".equals(segment)) {
                    return variables.get(path.substring(0, path.length() - ".intent".length()));
                }
            }
        }
        return current;
    }

    public ObjectNode snapshot() {
        return variables.deepCopy();
    }
}
