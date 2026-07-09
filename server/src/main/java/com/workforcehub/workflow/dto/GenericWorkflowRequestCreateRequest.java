package com.workforcehub.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 员工端通用 OA 申请提交请求。
 */
@Data
public class GenericWorkflowRequestCreateRequest {

    @NotBlank(message = "流程编码不能为空")
    @Size(max = 64, message = "流程编码不能超过64个字符")
    private String processCode;

    @Size(max = 256, message = "申请标题不能超过256个字符")
    private String title;

    @Size(max = 512, message = "申请摘要不能超过512个字符")
    private String summary;

    @NotNull(message = "表单数据不能为空")
    private JsonNode formPayload;

    @Size(max = 10, message = "申请附件最多支持10个")
    private List<@Positive(message = "附件引用ID必须大于0") Long> attachmentRefIds = List.of();
}
