package com.workforcehub.agent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TemplateResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateResolver resolver = new TemplateResolver(objectMapper);

    @Test
    void shouldResolveNestedPathAndArrayIndex() throws Exception {
        WorkflowContext context = new WorkflowContext(objectMapper);
        context.put("input", objectMapper.readTree("""
            {
              "userId": 2003,
              "leaveBalances": [
                { "availableDays": 3 }
              ]
            }
            """));

        assertEquals("用户 2003 还有 3 天",
            resolver.resolveString("用户 {{input.userId}} 还有 {{input.leaveBalances.0.availableDays}} 天", context));
    }
}
