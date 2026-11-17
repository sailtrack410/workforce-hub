package com.workforcehub.workflow.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * 通用 OA 申请明细响应。
 */
@Data
public class WorkflowGenericDetailResponse {

    private String processCode;
    private String summary;
    private JsonNode formPayload;
}
