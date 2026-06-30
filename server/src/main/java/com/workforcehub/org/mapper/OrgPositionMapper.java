package com.workforcehub.org.mapper;

import com.workforcehub.org.domain.OrgPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface OrgPositionMapper {

    OrgPosition selectById(@Param("id") Long id);

    int insert(OrgPosition position);

    int updateById(OrgPosition position);

    long countByPositionCode(@Param("positionCode") String positionCode, @Param("exceptId") Long exceptId);

    List<OrgPosition> listActiveOrderByName();
}
