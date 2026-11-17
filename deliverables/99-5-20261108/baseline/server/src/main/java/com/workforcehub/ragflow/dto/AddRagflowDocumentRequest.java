package com.workforcehub.ragflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddRagflowDocumentRequest {
    @NotNull
    private Long attachmentRefId;
}
