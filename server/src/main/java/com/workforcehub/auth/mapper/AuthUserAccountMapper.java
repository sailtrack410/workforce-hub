package com.workforcehub.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workforcehub.auth.domain.AuthUserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuthUserAccountMapper extends BaseMapper<AuthUserAccount> {

    /**
     * 按登录名查询账号
     */
    AuthUserAccount selectByLoginName(@Param("loginName") String loginName);

    /**
     * 按邮箱查询账号
     */
    AuthUserAccount selectByEmail(@Param("email") String email);
}
