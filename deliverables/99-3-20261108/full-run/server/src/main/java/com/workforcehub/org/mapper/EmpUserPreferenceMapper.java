package com.workforcehub.org.mapper;

import com.workforcehub.org.domain.EmpUserPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface EmpUserPreferenceMapper {

    EmpUserPreference selectById(@Param("userId") Long userId);

    int insert(EmpUserPreference preference);

    int updateById(EmpUserPreference preference);
}
