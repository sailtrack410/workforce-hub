package com.workforcehub.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.repository.AgentToolBindingRow;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.org.dto.OrgDepartmentSummaryResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.service.OrgDirectoryService;
import com.workforcehub.ragflow.service.RagflowKnowledgeSearchService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.workforcehub.common.util.JsonValues.write;

/**
 * Agent 工具执行器，供同步任务和人工确认恢复执行复用。
 */
// 工具执行：根据 toolCode 路由到具体实现（组织查询、假期、考勤、知识库搜索）
@Service
public class AgentToolExecutionService {

    private final ObjectMapper objectMapper;
    private final OrgDirectoryService orgDirectoryService;
    private final ObjectProvider<LeaveService> leaveServiceProvider;
    private final ObjectProvider<AttendanceService> attendanceServiceProvider;
    private final ObjectProvider<RagflowKnowledgeSearchService> knowledgeSearchServiceProvider;

    public AgentToolExecutionService(ObjectMapper objectMapper,
                                     OrgDirectoryService orgDirectoryService,
                                     ObjectProvider<LeaveService> leaveServiceProvider,
                                     ObjectProvider<AttendanceService> attendanceServiceProvider,
                                     ObjectProvider<RagflowKnowledgeSearchService> knowledgeSearchServiceProvider) {
        this.objectMapper = objectMapper;
        this.orgDirectoryService = orgDirectoryService;
        this.leaveServiceProvider = leaveServiceProvider;
        this.attendanceServiceProvider = attendanceServiceProvider;
        this.knowledgeSearchServiceProvider = knowledgeSearchServiceProvider;
    }

    public String execute(AgentToolBindingRow binding, String requestPayloadJson) {
        if ("CONNECTOR".equals(binding.toolType())) {
            throw new BizException(501, "连接器工具执行器尚未接入");
        }
        JsonNode payload = readJson(requestPayloadJson);
        Object result = switch (binding.toolCode()) {
            case "ORG_GET_USER_SUMMARY" -> getUserSummary(requiredLong(payload, "userId"));
            case "ORG_GET_DEPT_SUMMARY" -> getDeptSummary(requiredLong(payload, "deptId"));
            case "ORG_GET_MANAGER_USER_ID" -> Map.of("managerUserId", orgDirectoryService.getUserSummary(requiredLong(payload, "userId")).getManagerUserId());
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
            default -> throw new BizException(400, "暂不支持该" + binding.toolType() + "工具执行");
        };
        return write(objectMapper, Map.of("status", "SUCCESS", "result", result == null ? Map.of() : result));
    }

    private Map<String, Object> getUserSummary(Long userId) {
        OrgUserSummaryResponse user = orgDirectoryService.getUserSummary(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getUserId());
        result.put("name", user.getRealName());
        result.put("employeeNo", user.getEmployeeNo());
        result.put("deptId", user.getPrimaryDeptId());
        result.put("deptName", user.getPrimaryDeptName());
        result.put("positionId", user.getPositionId());
        result.put("positionName", user.getPositionName());
        result.put("managerUserId", user.getManagerUserId());
        result.put("managerName", user.getManagerName());
        result.put("employmentStatus", user.getEmploymentStatus());
        result.put("status", user.getStatus());
        return result;
    }

    private Map<String, Object> getDeptSummary(Long deptId) {
        OrgDepartmentSummaryResponse dept = orgDirectoryService.getDepartmentSummary(deptId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deptId", dept.getId());
        result.put("parentId", dept.getParentId());
        result.put("treePath", dept.getTreePath());
        result.put("deptCode", dept.getDeptCode());
        result.put("deptName", dept.getDeptName());
        result.put("deptType", dept.getDeptType());
        result.put("deptLevel", dept.getDeptLevel());
        result.put("leaderUserId", dept.getLeaderUserId());
        result.put("leaderName", dept.getLeaderName());
        result.put("status", dept.getStatus());
        return result;
    }

    private LeaveService requireLeaveService() {
        LeaveService service = leaveServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "假期查询工具未接入");
        }
        return service;
    }

    private AttendanceService requireAttendanceService() {
        AttendanceService service = attendanceServiceProvider.getIfAvailable();
        if (service == null) {
            throw new BizException(501, "考勤查询工具未接入");
        }
        return service;
    }

    private RagflowKnowledgeSearchService requireKnowledgeSearchService() {
        RagflowKnowledgeSearchService service = knowledgeSearchServiceProvider.getIfAvailable();
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

}
