package com.workforcehub.agent.dto;

import lombok.Data;

import java.util.List;

@Data
public class WorkflowValidationResponse {

    private Boolean valid;
    private List<String> messages;
}
