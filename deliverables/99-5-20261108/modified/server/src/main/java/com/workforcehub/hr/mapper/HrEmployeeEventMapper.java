package com.workforcehub.hr.mapper;

import com.workforcehub.hr.domain.HrEmployeeEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HrEmployeeEventMapper {

    int insert(HrEmployeeEvent event);
}
