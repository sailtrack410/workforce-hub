package com.workforcehub.org.service;

import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.common.enums.EmploymentStatus;
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
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbcTemplate;

    public OrgDirectoryService(OrgDirectoryMapper orgDirectoryMapper,
                               OrgPositionMapper orgPositionMapper,
                               OrgUserMapper orgUserMapper,
                               JdbcTemplate jdbcTemplate) {
        this.orgDirectoryMapper = orgDirectoryMapper;
        this.orgPositionMapper = orgPositionMapper;
        this.orgUserMapper = orgUserMapper;
        this.jdbcTemplate = jdbcTemplate;
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

        roots.forEach(this::fillDepartmentLabels);
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
            fillPositionLabels(item);
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
        rows.forEach(this::fillUserLabels);
        return PageResult.of(safePageNo, safePageSize, total, rows);
    }

    public List<OrgUserSummaryResponse> listUsersForOrgChart() {
        List<OrgUserSummaryResponse> rows = orgDirectoryMapper.selectUsersForOrgChart();
        rows.forEach(this::fillUserLabels);
        return rows;
    }

    public OrgUserSummaryResponse getUserSummary(Long userId) {
        OrgUserSummaryResponse user = orgDirectoryMapper.selectUserSummaryById(userId);
        if (user == null) {
            throw new BizException(404, "员工不存在");
        }
        fillUserLabels(user);
        return user;
    }

    public OrgUserDetailResponse getUserDetail(Long userId) {
        OrgUserDetailResponse user = orgDirectoryMapper.selectUserDetailById(userId);
        if (user == null) {
            throw new BizException(404, "员工不存在");
        }
        fillUserDetailLabels(user);
        return user;
    }

    public OrgDepartmentSummaryResponse getDepartmentSummary(Long deptId) {
        OrgDepartmentSummaryResponse dept = orgDirectoryMapper.selectDepartmentSummaryById(deptId);
        if (dept == null) {
            throw new BizException(404, "部门不存在");
        }
        dept.setDeptTypeName(departmentTypeName(dept.getDeptType()));
        dept.setStatusName(statusName(dept.getStatus()));
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
        List<OrgUserSummaryResponse> rows = orgDirectoryMapper.selectDirectSubordinates(userId);
        rows.forEach(this::fillUserLabels);
        return rows;
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
        ensureEmployeeAvatarAttachment(userId, request.getAvatarAttachmentRefId());
        entity.setAvatarAttachmentRefId(request.getAvatarAttachmentRefId());
        entity.setUpdatedBy(userId);
        orgUserMapper.updateById(entity);
        return getUserDetail(userId);
    }

    private void ensureEmployeeAvatarAttachment(Long userId, Long attachmentRefId) {
        if (attachmentRefId == null) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_attachment_ref ar
            JOIN sys_file_object fo ON fo.id = ar.file_object_id
            WHERE ar.id = ?
              AND ar.owner_type = 'USER_AVATAR'
              AND ar.uploader_type = 'USER'
              AND ar.uploader_id = ?
              AND ar.deleted_at IS NULL
              AND ar.is_deleted = 0
              AND fo.is_deleted = 0
            """, Integer.class, attachmentRefId, userId);
        if (count == null || count == 0) {
            throw new BizException(403, "无权使用该头像");
        }
    }

    private void ensureEmployeeContext() {
        if (!CurrentUserHolder.isEmployee()) {
            throw new BizException(403, "仅员工端支持查看个人组织信息");
        }
    }

    private void fillUserLabels(OrgUserSummaryResponse user) {
        if (user == null) {
            return;
        }
        user.setEmploymentStatusName(employmentStatusName(user.getEmploymentStatus()));
        user.setStatusName(statusName(user.getStatus()));
        user.setPresenceStatusName(presenceStatusName(user.getPresenceStatus()));
    }

    private void fillUserDetailLabels(OrgUserDetailResponse user) {
        if (user == null) {
            return;
        }
        user.setGenderName(genderName(user.getGender()));
        user.setEmploymentStatusName(employmentStatusName(user.getEmploymentStatus()));
        user.setStatusName(statusName(user.getStatus()));
    }

    private void fillDepartmentLabels(OrgDepartmentTreeNodeResponse node) {
        if (node == null) {
            return;
        }
        node.setDeptTypeName(departmentTypeName(node.getDeptType()));
        node.setStatusName(statusName(node.getStatus()));
        if (node.getChildren() != null) {
            node.getChildren().forEach(this::fillDepartmentLabels);
        }
    }

    private void fillPositionLabels(OrgPositionResponse item) {
        if (item == null) {
            return;
        }
        item.setPositionCategoryName(positionCategoryName(item.getPositionCategory()));
        item.setStatusName(statusName(item.getStatus()));
    }

    private String employmentStatusName(String value) {
        if (value == null) {
            return null;
        }
        for (EmploymentStatus item : EmploymentStatus.values()) {
            if (item.getCode().equals(value)) {
                return item.getDesc();
            }
        }
        return "其他状态";
    }

    private String statusName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "ACTIVE" -> "启用";
            case "INACTIVE" -> "停用";
            case "LOCKED" -> "锁定";
            case "DISABLED" -> "禁用";
            default -> "其他状态";
        };
    }

    private String genderName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "MALE" -> "男";
            case "FEMALE" -> "女";
            default -> "未填写";
        };
    }

    private String presenceStatusName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "ONLINE" -> "在线";
            case "BUSY" -> "忙碌";
            case "AWAY" -> "离开";
            case "DND" -> "勿扰";
            default -> "其他状态";
        };
    }

    private String departmentTypeName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "ROOT" -> "组织";
            case "COMPANY" -> "公司";
            case "BRANCH" -> "分支机构";
            case "DEPARTMENT" -> "部门";
            case "TEAM" -> "团队";
            default -> "其他组织";
        };
    }

    private String positionCategoryName(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "ENGINEER" -> "专业岗";
            case "MANAGER" -> "管理岗";
            default -> "其他分类";
        };
    }
}
