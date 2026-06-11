package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TemplateResolver {

    private static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}");
    private static final Pattern EXACT_TEMPLATE_PATTERN = Pattern.compile("^\\s*\\{\\{\\s*([A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)*)\\s*}}\\s*$");

    private final ObjectMapper objectMapper;

    public TemplateResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String resolveString(String template, WorkflowContext context) {
        if (template == null) {
            return null;
        }
        Matcher matcher = TEMPLATE_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            JsonNode value = context.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(toText(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public JsonNode resolveJson(JsonNode node, WorkflowContext context) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isTextual()) {
            String text = node.asText();
            Matcher exactMatcher = EXACT_TEMPLATE_PATTERN.matcher(text);
            if (exactMatcher.matches()) {
                JsonNode value = context.get(exactMatcher.group(1));
                return value == null ? objectMapper.nullNode() : value.deepCopy();
            }
            return TextNode.valueOf(resolveString(text, context));
        }
        if (node.isObject()) {
            var copy = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> copy.set(entry.getKey(), resolveJson(entry.getValue(), context)));
            return copy;
        }
        if (node.isArray()) {
            var copy = objectMapper.createArrayNode();
            node.forEach(child -> copy.add(resolveJson(child, context)));
            return copy;
        }
        return node.deepCopy();
    }

    private String toText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
