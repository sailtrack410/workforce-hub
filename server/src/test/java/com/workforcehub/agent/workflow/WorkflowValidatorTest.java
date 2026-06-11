package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkflowValidator validator = new WorkflowValidator(objectMapper);

    @Test
    void shouldRejectCycleAndUnknownEdgeTarget() {
        WorkflowValidationResult result = validator.validate("""
            {
              "nodes": [
                { "id": "start", "type": "START" },
                { "id": "loop", "type": "TOOL", "config": { "toolCode": "ORG_GET_USER_SUMMARY", "args": { "userId": 2003 } } },
                { "id": "end", "type": "END" }
              ],
              "edges": [
                { "from": "start", "to": "loop" },
                { "from": "loop", "to": "start" },
                { "from": "loop", "to": "missing" }
              ]
            }
            """);

        assertFalse(result.valid());
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("存在环路")));
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("missing")));
    }

    @Test
    void shouldAcceptMinimalToolWorkflow() {
        WorkflowValidationResult result = validator.validate("""
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "toolCode": "ORG_GET_USER_SUMMARY",
                    "args": { "userId": "{{input.userId}}" }
                  },
                  "outputKey": "user"
                },
                { "id": "end", "type": "END", "config": { "reply": "ok" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """);

        assertTrue(result.valid(), () -> String.join("\n", result.messages()));
    }

    @Test
    void shouldAcceptPromptNodeWithEmbeddedToolCalls() {
        WorkflowValidationResult result = validator.validate("""
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "recognize",
                  "type": "INTENT",
                  "config": {
                    "prompt": "识别用户意图",
                    "toolCalls": [
                      {
                        "toolCode": "ORG_GET_USER_SUMMARY",
                        "args": { "userId": "{{input.userId}}" },
                        "outputKey": "user"
                      }
                    ]
                  },
                  "outputKey": "intent"
                },
                { "id": "end", "type": "END", "config": { "reply": "{{intent}}" } }
              ],
              "edges": [
                { "from": "start", "to": "recognize" },
                { "from": "recognize", "to": "end" }
              ]
            }
            """);

        assertTrue(result.valid(), () -> String.join("\n", result.messages()));
    }

    @Test
    void shouldRejectArrayOutputFormatWithoutItemFields() {
        WorkflowValidationResult result = validator.validate("""
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "answer",
                  "type": "LLM",
                  "config": { "prompt": "test" },
                  "outputFormat": [
                    { "key": "items", "type": "array" }
                  ]
                },
                { "id": "end", "type": "END" }
              ],
              "edges": [
                { "from": "start", "to": "answer" },
                { "from": "answer", "to": "end" }
              ]
            }
            """);

        assertFalse(result.valid());
        assertTrue(result.messages().stream().anyMatch(message -> message.contains("itemFields")));
    }
}
