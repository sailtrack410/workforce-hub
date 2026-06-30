package com.workforcehub.org.mapper;

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

    List<OrgUserSummaryResponse> selectUserPage(@Param("query") OrgUserListQuery query,
                                                @Param("offset") long offset,
                                                @Param("limit") long limit);

    long countUserPage(@Param("query") OrgUserListQuery query);

    List<OrgUserSummaryResponse> selectUsersForOrgChart();

    OrgUserSummaryResponse selectUserSummaryById(@Param("userId") Long userId);

    OrgUserDetailResponse selectUserDetailById(@Param("userId") Long userId);

    List<OrgUserSummaryResponse> selectDirectSubordinates(@Param("managerUserId") Long managerUserId);

    List<OrgUserSummaryResponse> selectColleaguesByDept(@Param("deptId") Long deptId,
                                                       @Param("currentUserId") Long currentUserId);

    Long countUsersByDept(@Param("deptId") Long deptId);
}
