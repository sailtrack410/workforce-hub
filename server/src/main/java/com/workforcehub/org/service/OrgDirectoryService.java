package com.workforcehub.org.service;

import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.response.PageResult;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.org.domain.OrgPosition;
import com.workforcehub.org.dto.OrgDepartmentSummaryResponse;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgPositionResponse;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserListQuery;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import com.workforcehub.org.dto.UpdateEmployeeProfileRequest;
import com.workforcehub.org.mapper.OrgDirectoryMapper;
import com.workforcehub.org.mapper.OrgPositionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织目录查询服务
 * 第一批先收口读侧能力，为审批、IM、Agent 提供稳定底座。
 */
// 组织目录查询：部门树、员工列表、上级链、岗位查询，供审批/IM/Agent 复用
@Service
public class OrgDirectoryService {

    private final OrgDirectoryMapper orgDirectoryMapper;
    private final OrgPositionMapper orgPositionMapper;
    private final OrgUserMapper orgUserMapper;

    public OrgDirectoryService(OrgDirectoryMapper orgDirectoryMapper,
                               OrgPositionMapper orgPositionMapper,
                               OrgUserMapper orgUserMapper) {
        this.orgDirectoryMapper = orgDirectoryMapper;
        this.orgPositionMapper = orgPositionMapper;
        this.orgUserMapper = orgUserMapper;
    }

    public List<OrgDepartmentTreeNodeResponse> getDepartmentTree() {
        List<OrgDepartmentTreeNodeResponse> flatNodes = orgDirectoryMapper.selectDepartmentTreeNodes();
        Map<Long, OrgDepartmentTreeNodeResponse> nodeMap = new LinkedHashMap<>();
        List<OrgDepartmentTreeNodeResponse> roots = new ArrayList<>();

        for (OrgDepartmentTreeNodeResponse node : flatNodes) {
            if (node.getChildren() == null) {
                node.setChildren(new ArrayList<>());
            }
            nodeMap.put(node.getId(), node);
        }

        for (OrgDepartmentTreeNodeResponse node : flatNodes) {
            Long parentId = node.getParentId();
            if (parentId == null) {
                roots.add(node);
                continue;
            }
            OrgDepartmentTreeNodeResponse parent = nodeMap.get(parentId);
            if (parent == null) {
                roots.add(node);
                continue;
            }
            parent.getChildren().add(node);
        }

        return roots;
    }

    public List<OrgPositionResponse> listPositions() {
        List<OrgPosition> positions = orgPositionMapper.listActiveOrderByName();

        List<OrgPositionResponse> result = new ArrayList<>(positions.size());
        for (OrgPosition position : positions) {
            OrgPositionResponse item = new OrgPositionResponse();
            item.setId(position.getId());
            item.setPositionCode(position.getPositionCode());
            item.setPositionName(position.getPositionName());
            item.setPositionCategory(position.getPositionCategory());
            item.setStatus(position.getStatus());
            result.add(item);
        }
        return result;
    }

    public PageResult<OrgUserSummaryResponse> pageUsers(OrgUserListQuery query) {
        long safePageNo = Math.max(query.getPageNo(), 1L);
        long safePageSize = Math.min(Math.max(query.getPageSize(), 1L), 100L);
        long total = orgDirectoryMapper.countUserPage(query);
        List<OrgUserSummaryResponse> rows = orgDirectoryMapper.selectUserPage(
            query,
            (safePageNo - 1) * safePageSize,
            safePageSize
        );
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public List<OrgUserSummaryResponse> listUsersForOrgChart() {
        return orgDirectoryMapper.selectUsersForOrgChart();
    }

    public OrgUserSummaryResponse getUserSummary(Long userId) {
        OrgUserSummaryResponse user = orgDirectoryMapper.selectUserSummaryById(userId);
        if (user == null) {
            throw new BizException(404, "员工不存在");
        }
        return user;
    }

    public OrgUserDetailResponse getUserDetail(Long userId) {
        OrgUserDetailResponse user = orgDirectoryMapper.selectUserDetailById(userId);
        if (user == null) {
            throw new BizException(404, "员工不存在");
        }
        return user;
    }

    public OrgDepartmentSummaryResponse getDepartmentSummary(Long deptId) {
        OrgDepartmentSummaryResponse dept = orgDirectoryMapper.selectDepartmentSummaryById(deptId);
        if (dept == null) {
            throw new BizException(404, "部门不存在");
        }
        return dept;
    }

    public OrgUserSummaryResponse getManager(Long userId) {
        OrgUserDetailResponse detail = getUserDetail(userId);
        if (detail.getManagerUserId() == null) {
            return null;
        }
        return getUserSummary(detail.getManagerUserId());
    }

    public List<OrgUserSummaryResponse> listDirectSubordinates(Long userId) {
        return orgDirectoryMapper.selectDirectSubordinates(userId);
    }

    public OrgUserDetailResponse getCurrentEmployeeProfile() {
        ensureEmployeeContext();
        return getUserDetail(CurrentUserHolder.requireUserId());
    }

    public OrgUserSummaryResponse getCurrentEmployeeManager() {
        ensureEmployeeContext();
        return getManager(CurrentUserHolder.requireUserId());
    }

    public List<OrgUserSummaryResponse> getCurrentEmployeeSubordinates() {
        ensureEmployeeContext();
        return listDirectSubordinates(CurrentUserHolder.requireUserId());
    }

    public List<OrgUserSummaryResponse> getCurrentEmployeeColleagues() {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        OrgUserDetailResponse self = getUserDetail(userId);
        Long deptId = self.getPrimaryDeptId();
        if (deptId == null) {
            return Collections.emptyList();
        }
        return orgDirectoryMapper.selectColleaguesByDept(deptId, userId);
    }

    public Long countDepartmentUsers(Long deptId) {
        if (deptId == null) {
            return 0L;
        }
        Long count = orgDirectoryMapper.countUsersByDept(deptId);
        return count == null ? 0L : count;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrgUserDetailResponse updateCurrentEmployeeProfile(UpdateEmployeeProfileRequest request) {
        ensureEmployeeContext();
        Long userId = CurrentUserHolder.requireUserId();
        OrgUser entity = orgUserMapper.selectById(userId);
        if (entity == null || (entity.getIsDeleted() != null && entity.getIsDeleted() != 0)) {
            throw new BizException(404, "员工不存在");
        }
        if (request == null) {
            return getUserDetail(userId);
        }
        if (request.getMobile() != null) {
            String mobile = request.getMobile().trim();
            entity.setMobile(mobile.isEmpty() ? null : mobile);
        }
        entity.setAvatarAttachmentRefId(request.getAvatarAttachmentRefId());
        entity.setUpdatedBy(userId);
        orgUserMapper.updateById(entity);
        return getUserDetail(userId);
    }

    private void ensureEmployeeContext() {
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持查看个人组织信息");
        }
    }
}
