package com.workforcehub.ragflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RagflowSearchTestRequest {
    @NotBlank
    @Size(max = 500)
    private String query;
}
