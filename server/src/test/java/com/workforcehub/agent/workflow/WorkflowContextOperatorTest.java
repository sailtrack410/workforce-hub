package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkflowContextOperatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void inputAndNodeOutputsCannotOverrideTrustedOperator() {
        WorkflowContext context = new WorkflowContext(mapper, 7L);
        context.put("currentUserId", 8L);
        context.put("operatorUserId", 8L);
        context.putJson("input", mapper.createObjectNode().put("currentUserId", 8L).put("operatorUserId", 8L));
        assertEquals(7L, context.operatorUserId());
        context.putJson("currentUserId", mapper.nullNode());
        context.putJson("operatorUserId", mapper.createObjectNode().put("userId", 8L));
        context.snapshot().put("currentUserId", 9L);
        assertEquals(7L, context.operatorUserId());
    }

    @Test
    void legacyPreviewContextNeverDerivesAnOperatorFromClientFields() {
        WorkflowContext context = new WorkflowContext(mapper);
        context.put("currentUserId", 8L);
        context.put("userId", 8L);
        context.put("operatorUserId", 8L);
        assertNull(context.operatorUserId());
    }
}
