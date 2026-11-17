package com.workforcehub.auth.mapper;

import com.workforcehub.auth.domain.AdminUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminUserMapper {

    AdminUser selectById(@Param("id") Long id);

    int updateById(AdminUser adminUser);

    /**
     * 按用户名查询管理员
     */
    AdminUser selectByUsername(@Param("username") String username);

    List<String> selectRoleCodes(@Param("adminUserId") Long adminUserId);

    List<String> selectPermissionCodes(@Param("adminUserId") Long adminUserId);

    List<String> selectAllActivePermissionCodes();
}
