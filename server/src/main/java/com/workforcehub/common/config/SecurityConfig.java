package com.workforcehub.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.security.WfhAuthenticationProvider;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.response.R;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 安全配置
 * - 双 AuthenticationProvider（员工端 + 后台端独立）
 * - 关闭默认表单登录
 * - 未认证返回 401 JSON
 * - 无权限返回 403 JSON
 * - 通过 UserTypePathFilter 强校验路径与身份匹配
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ObjectMapper objectMapper;
    private final EmployeeUserDetailsService employeeUserDetailsService;
    private final AdminUserDetailsService adminUserDetailsService;
    private final UserTypePathFilter userTypePathFilter;
    private final AdminPermissionFilter adminPermissionFilter;

    public SecurityConfig(ObjectMapper objectMapper,
                          @Qualifier("employeeUserDetailsService") EmployeeUserDetailsService employeeUserDetailsService,
                          @Qualifier("adminUserDetailsService") AdminUserDetailsService adminUserDetailsService,
                          UserTypePathFilter userTypePathFilter,
                          AdminPermissionFilter adminPermissionFilter) {
        this.objectMapper = objectMapper;
        this.employeeUserDetailsService = employeeUserDetailsService;
        this.adminUserDetailsService = adminUserDetailsService;
        this.userTypePathFilter = userTypePathFilter;
        this.adminPermissionFilter = adminPermissionFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 员工端 Provider：暴露给登录服务按 UserType 选择
     */
    @Bean("employeeAuthProvider")
    public WfhAuthenticationProvider employeeAuthProvider(PasswordEncoder passwordEncoder) {
        return new WfhAuthenticationProvider(employeeUserDetailsService, passwordEncoder, UserType.USER);
    }

    /**
     * 后台端 Provider
     */
    @Bean("adminAuthProvider")
    public WfhAuthenticationProvider adminAuthProvider(PasswordEncoder passwordEncoder) {
        return new WfhAuthenticationProvider(adminUserDetailsService, passwordEncoder, UserType.ADMIN);
    }

    /**
     * AuthenticationManager 注册两个 Provider
     * 但实际登录服务会显式选择 Provider，不依赖默认遍历
     */
    @Bean
    public AuthenticationManager authenticationManager(
            @Qualifier("employeeAuthProvider") WfhAuthenticationProvider employeeProvider,
            @Qualifier("adminAuthProvider") WfhAuthenticationProvider adminProvider) {
        return new ProviderManager(employeeProvider, adminProvider);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .authorizeHttpRequests(auth -> auth
                // 匿名入口（按接口粒度拆开，避免通配符把不同安全级别接口混在一起）
                .requestMatchers("/api/employee/auth/login").permitAll()
                .requestMatchers("/api/employee/auth/login/send-code").permitAll()
                .requestMatchers("/api/employee/auth/email/send-code").permitAll()
                .requestMatchers("/api/employee/auth/password/reset").permitAll()
                .requestMatchers("/api/admin/auth/login").permitAll()
                // email/bind 等其余 auth 接口需要登录态
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health/**").permitAll()
                .anyRequest().authenticated()
            )

            // 路径与身份匹配校验
            .addFilterAfter(userTypePathFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(adminPermissionFilter, UserTypePathFilter.class)

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), R.fail(BusinessErrorCode.UNAUTHORIZED.code(), "未登录或会话已过期"));
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding("UTF-8");
                    objectMapper.writeValue(response.getWriter(), R.fail(BusinessErrorCode.FORBIDDEN.code(), "无权限访问"));
                })
            );

        return http.build();
    }
}
