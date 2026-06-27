package com.workforcehub.org.mapper;

import com.workforcehub.org.domain.OrgDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrgDepartmentMapper {

    OrgDepartment selectById(@Param("id") Long id);

    int insert(OrgDepartment department);

    int updateById(OrgDepartment department);

    int deleteById(@Param("id") Long id);

    long countByParentId(@Param("parentId") Long parentId);

    long countByDeptCode(@Param("deptCode") String deptCode, @Param("exceptId") Long exceptId);

    List<OrgDepartment> listByTreePathPrefixExcludingId(@Param("treePath") String treePath,
                                                        @Param("exceptId") Long exceptId);
}
