package com.workforcehub.org.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workforcehub.org.dto.OrgDepartmentSummaryResponse;
import com.workforcehub.org.dto.OrgDepartmentTreeNodeResponse;
import com.workforcehub.org.dto.OrgUserDetailResponse;
import com.workforcehub.org.dto.OrgUserListQuery;
import com.workforcehub.org.dto.OrgUserSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrgDirectoryMapper {

    List<OrgDepartmentTreeNodeResponse> selectDepartmentTreeNodes();

    OrgDepartmentSummaryResponse selectDepartmentSummaryById(@Param("deptId") Long deptId);

    Page<OrgUserSummaryResponse> selectUserPage(Page<OrgUserSummaryResponse> page,
                                                @Param("query") OrgUserListQuery query);

    List<OrgUserSummaryResponse> selectUsersForOrgChart();

    OrgUserSummaryResponse selectUserSummaryById(@Param("userId") Long userId);

    OrgUserDetailResponse selectUserDetailById(@Param("userId") Long userId);

    List<OrgUserSummaryResponse> selectDirectSubordinates(@Param("managerUserId") Long managerUserId);

    List<OrgUserSummaryResponse> selectColleaguesByDept(@Param("deptId") Long deptId,
                                                       @Param("currentUserId") Long currentUserId);

    Long countUsersByDept(@Param("deptId") Long deptId);
}
