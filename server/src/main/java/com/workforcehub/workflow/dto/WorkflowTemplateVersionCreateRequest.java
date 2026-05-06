package com.workforcehub.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowTemplateVersionCreateRequest {

    @NotNull(message = "versionNo 不能为空")
    @Min(value = 1, message = "versionNo 最小为 1")
    private Integer versionNo;

    @Size(max = 8192, message = "formSchemaJson 长度不能超过 8192")
    private String formSchemaJson;

    @Size(max = 8192, message = "ruleSnapshotJson 长度不能超过 8192")
    private String ruleSnapshotJson;
}
