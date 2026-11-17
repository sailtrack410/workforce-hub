package com.workforcehub.auth.mapper;

import com.workforcehub.auth.domain.AuthUserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthUserAccountMapper {

    int insert(AuthUserAccount account);

    int updateById(AuthUserAccount account);

    AuthUserAccount selectByUserId(@Param("userId") Long userId);

    long countByLoginName(@Param("loginName") String loginName, @Param("exceptId") Long exceptId);

    long countByEmail(@Param("email") String email, @Param("exceptId") Long exceptId);

    /**
     * 按登录名查询账号
     */
    AuthUserAccount selectByLoginName(@Param("loginName") String loginName);

    /**
     * 按邮箱查询账号
     */
    AuthUserAccount selectByEmail(@Param("email") String email);
}
