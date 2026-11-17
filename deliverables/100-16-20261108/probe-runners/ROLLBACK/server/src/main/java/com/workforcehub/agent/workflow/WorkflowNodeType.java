package com.workforcehub.agent.workflow;

public enum WorkflowNodeType {
    START,
    END,
    LLM,
    TOOL,
    CONDITION,
    INTENT,
    APPROVAL,
    KNOWLEDGE,
    FORM_EXTRACT,
    SUMMARY,
    TRANSFORM
}
