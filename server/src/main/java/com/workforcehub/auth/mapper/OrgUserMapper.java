package com.workforcehub.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workforcehub.auth.domain.OrgUser;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrgUserMapper extends BaseMapper<OrgUser> {
}
