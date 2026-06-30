package com.workforcehub.auth.mapper;

import com.workforcehub.auth.domain.OrgUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrgUserMapper {

    OrgUser selectById(@Param("id") Long id);

    int insert(OrgUser user);

    int updateById(OrgUser user);

    long countByPrimaryDeptId(@Param("deptId") Long deptId);

    long countByEmployeeNo(@Param("employeeNo") String employeeNo, @Param("exceptId") Long exceptId);
}
