package com.workforcehub.org.mapper;

import com.workforcehub.org.domain.OrgDepartmentLeader;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrgDepartmentLeaderMapper {

    int insert(OrgDepartmentLeader leader);

    int updateById(OrgDepartmentLeader leader);

    List<OrgDepartmentLeader> listByDeptId(@Param("deptId") Long deptId);

    List<OrgDepartmentLeader> listPrimaryByDeptId(@Param("deptId") Long deptId);

    OrgDepartmentLeader selectByDeptIdAndUserIdAndLeaderType(@Param("deptId") Long deptId,
                                                             @Param("userId") Long userId,
                                                             @Param("leaderType") String leaderType);
}
