package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.spi.AttendanceQueryService;
import com.workforcehub.spi.KnowledgeSearchService;
import com.workforcehub.spi.LeaveQueryService;
import com.workforcehub.spi.OrgQueryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Map;

/**
 * Agent 工具执行器，供同步任务和人工确认恢复执行复用。
 */
@Service
public class AgentToolExecutionService {

    private final ObjectMapper objectMapper;
    private final OrgQueryService orgQueryService;
    private final ObjectProvider<LeaveQueryService> leaveQueryServiceProvider;
    private final ObjectProvider<AttendanceQueryService> attendanceQueryServiceProvider;
    private final ObjectProvider<KnowledgeSearchService> knowledgeSearchServiceProvider;

    public AgentToolExecutionService(ObjectMapper objectMapper,
                                     OrgQueryService orgQueryService,
                                     ObjectProvider<LeaveQueryService> leaveQueryServiceProvider,
                                     ObjectProvider<AttendanceQueryService> attendanceQueryServiceProvider,
                                     ObjectProvider<KnowledgeSearchService> knowledgeSearchServiceProvider) {
        this.objectMapper = objectMapper;
        this.orgQueryService = orgQueryService;
        this.leaveQueryServiceProvider = leaveQueryServiceProvider;
        this.attendanceQueryServiceProvider = attendanceQueryServiceProvider;
        this.knowledgeSearchServiceProvider = knowledgeSearchServiceProvider;
    }

    public String execute(AgentToolBindingRow binding, String requestPayloadJson) {
        if ("CONNECTOR".equals(binding.toolType())) {
            throw new BizException(501, "连接器工具执行器尚未接入");
        }
        JsonNode payload = readJson(requestPayloadJson);
        Object result = switch (binding.toolCode()) {
            case "ORG_GET_USER_SUMMARY" -> orgQueryService.getUserSummary(requiredLong(payload, "userId"));
            case "ORG_GET_DEPT_SUMMARY" -> orgQueryService.getDeptSummary(requiredLong(payload, "deptId"));
            case "ORG_GET_MANAGER_USER_ID" -> Map.of("managerUserId", orgQueryService.getManagerUserId(requiredLong(payload, "userId")));
            case "LEAVE_GET_BALANCES" -> requireLeaveService().getLeaveBalances(requiredLong(payload, "userId"));
            case "LEAVE_GET_AVAILABLE_MINUTES" -> Map.of(
                "availableMinutes",
                requireLeaveService().getAvailableMinutes(requiredLong(payload, "userId"), requiredLong(payload, "leaveTypeId"))
            );
            case "ATTENDANCE_GET_DAILY_RESULT" -> requireAttendanceService().getDailyResult(
                requiredLong(payload, "userId"),
                LocalDate.parse(requiredText(payload, "workDate"))
            );
            case "ATTENDANCE_GET_MONTHLY_EXCEPTIONS" -> requireAttendanceService().getMonthlyExceptions(
                requiredLong(payload, "userId"),
                requiredText(payload, "yearMonth")
            );
            case "KNOWLEDGE_SEARCH_CHUNKS" -> requireKnowledgeSearchService().searchChunks(
                requiredLong(payload, "spaceId"),
                requiredText(payload, "query"),
                optionalInt(payload, "topK", 5)
            );
            default -> throw new BizException(400, "暂不支持该SPI工具执行");
        };
        return writeJson(Map.of("status", "SUCCESS", "result", result == null ? Map.of() : result));
    }

    private LeaveQueryService requireLeaveService() {
        LeaveQueryService service = leaveQueryServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "假期查询工具未接入");
        }
        return service;
    }

    private AttendanceQueryService requireAttendanceService() {
        AttendanceQueryService service = attendanceQueryServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "考勤查询工具未接入");
        }
        return service;
    }

    private KnowledgeSearchService requireKnowledgeSearchService() {
        KnowledgeSearchService service = knowledgeSearchServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "知识库检索工具未接入");
        }
        return service;
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(StringUtils.hasText(value) ? value : "{}");
        } catch (Exception e) {
            throw new BizException(400, "JSON解析失败");
        }
    }

    private Long requiredLong(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || !node.canConvertToLong()) {
            throw new BizException(400, fieldName + "不能为空");
        }
        return node.asLong();
    }

    private String requiredText(JsonNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        if (node == null || !StringUtils.hasText(node.asText())) {
            throw new BizException(400, fieldName + "不能为空");
        }
        return node.asText().trim();
    }

    private int optionalInt(JsonNode payload, String fieldName, int defaultValue) {
        JsonNode node = payload.get(fieldName);
        return node == null || !node.canConvertToInt() ? defaultValue : node.asInt();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(500, "JSON序列化失败");
        }
    }
}
