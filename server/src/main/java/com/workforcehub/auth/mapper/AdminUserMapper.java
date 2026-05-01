package com.workforcehub.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workforcehub.auth.domain.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUser> {

    /**
     * 按用户名查询管理员
     */
    AdminUser selectByUsername(@Param("username") String username);
}
