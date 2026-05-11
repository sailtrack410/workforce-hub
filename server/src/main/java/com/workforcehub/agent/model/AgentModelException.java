package com.workforcehub.agent.model;

/**
 * Agent 模型调用异常。
 */
public class AgentModelException extends RuntimeException {

    private final String failureType;

    public AgentModelException(String failureType, String message) {
        super(message);
        this.failureType = failureType;
    }

    public AgentModelException(String failureType, String message, Throwable cause) {
        super(message, cause);
        this.failureType = failureType;
    }

    public String failureType() {
        return failureType;
    }
}
