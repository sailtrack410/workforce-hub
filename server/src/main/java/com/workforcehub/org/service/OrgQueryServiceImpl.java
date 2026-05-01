package com.workforcehub.org.service;

import com.workforcehub.org.dto.OrgDepartmentSummaryResponse;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.spi.OrgQueryService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组织查询 SPI 实现
 */
@Service
public class OrgQueryServiceImpl implements OrgQueryService {

    private final OrgDirectoryService orgDirectoryService;

    public OrgQueryServiceImpl(OrgDirectoryService orgDirectoryService) {
        this.orgDirectoryService = orgDirectoryService;
    }

    @Override
    public Map<String, Object> getUserSummary(Long userId) {
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

    @Override
    public Map<String, Object> getDeptSummary(Long deptId) {
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

    @Override
    public Long getManagerUserId(Long userId) {
        return orgDirectoryService.getUserSummary(userId).getManagerUserId();
    }
}
