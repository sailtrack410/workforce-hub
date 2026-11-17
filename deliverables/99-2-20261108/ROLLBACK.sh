#!/bin/sh
set -eu
target=${1:?Usage: ROLLBACK.sh /absolute/path/to/project-copy}
case "$target" in /*) ;; *) echo "absolute target path required" >&2; exit 2;; esac
hash() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d " " -f 1; }
if [ -f "$target/server/src/main/java/com/workforcehub/common/config/SecurityConfig.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/common/config/SecurityConfig.java")
 [ "$actual" = "7b51d1bff332b2bc9f8a931b5e773174c551770fec059f80501264bb1a88de3b" ] || [ "$actual" = "5b9987f9ec2e4a9c6524eb8e49bb21011200db9a1293f81cdff6b5899f5b8287" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/common/config/SecurityConfig.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandshakeInterceptor.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandshakeInterceptor.java")
 [ "$actual" = "d9104a7021a4b10a8e17e3353211c77217fe5c5f2986f5c96db884498da80daf" ] || [ "$actual" = "c6a537236500fe61d67edb706418b68dc35549219a24305b0a712b5bf0dad05c" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandshakeInterceptor.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandler.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandler.java")
 [ "$actual" = "353b95ec033c5e1767434300eaf40d4edc7a2fc68533da02526ff1473952341f" ] || [ "$actual" = "ae0959e6323a2e63b7c2e8be03b19f08e2a0bb59394d768fb050ec435099aa8d" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandler.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketSessionRegistry.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketSessionRegistry.java")
 [ "$actual" = "f96e0ca818aa9b1758b81b936a02f58a28ddb140e577489e8744d404363d7710" ] || [ "$actual" = "f70d264c62a9728ac0efac68afaf71914034aac0c433fffeb0598963fa8ef6d1" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/im/websocket/ImWebSocketSessionRegistry.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java")
 [ "$actual" = "4f0754906ddfcf9de9a37c92c3b6bb0aa4e386d0a42382ed2bc9e3989241f3cc" ] || [ "$actual" = "0ad85c4ecf8d13075032e87d115b1de9c8e4d96b9d6a90e44c12015de366e385" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java")
 [ "$actual" = "6ca17d6de15a6cb4f712beb9dbb3a38148ca5fe1985e3a901e8f819a128e332e" ] || [ "$actual" = "b1ef4705b10433500a6c2cb858a5c0edf2b3e8c3be9480142b7e0f2ad9ce982a" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/im/ModuleImOutboxIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/im/ModuleImOutboxIntegrationTest.java")
 [ "$actual" = "675cf647c3a9e9af0a182e122644c5053a3f04e828abd03b0378deb6e47bf12e" ] || [ "$actual" = "5e3efed591f813983a311febff32c8c34a4c1701a76291fa49d8dedad0030079" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/im/ModuleImOutboxIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/auth/AdminPermissionWebTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/auth/AdminPermissionWebTest.java")
 [ "$actual" = "aeff6ced2cad925ffee430b48ffdac2a1ce12d2000ab9ba1d4bb108f00f6ca6c" ] || [ "$actual" = "2c63399f3aadb76781e65ee84c38fdf3278da5a7c64a975c34165001e39b0ae9" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/auth/AdminPermissionWebTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/plugin/AdminPluginManagementWebTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/plugin/AdminPluginManagementWebTest.java")
 [ "$actual" = "47a9988f357fb5e594859ec5a1666a40af10ddaff6e9cedfeb263a78496cf65b" ] || [ "$actual" = "3201e7f9dba6ff052bc6a4050f1ff57ebccb9e163d13210c0ed65cbe04602fa6" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/plugin/AdminPluginManagementWebTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewaySecurityTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewaySecurityTest.java")
 [ "$actual" = "e882b817859e1d3973bfb6fcd68dee76511b1dd1392dc2a9a70001ec14c47376" ] || [ "$actual" = "7e7ec4923d8a564f44968175efd70730744ece107a5b957b41108dd0f6880243" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/plugin/PluginGatewaySecurityTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java")
 [ "$actual" = "403cd945d05ea83bb818ce9de6181d7aa881bbe0d3577ad80ceaef080697d76b" ] || [ "$actual" = "cffb516cacee79933adcc3ae9fb134e38623a3576957277015d29c786136344b" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewayPermissionMatrixIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewayPermissionMatrixIntegrationTest.java")
 [ "$actual" = "8b41feb09c9a433470820438160c0b171ce660e7ee094477290eb5ba847af1bd" ] || [ "$actual" = "8d0c7fa0dcadba40f50b3d8a0f706d7195d3f27bb02210399570fb483c3b8e9c" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/plugin/PluginGatewayPermissionMatrixIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/plugin/EmployeePluginListIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/plugin/EmployeePluginListIntegrationTest.java")
 [ "$actual" = "287d5d3b75780c9187fd297dd46d65d069ead91f927837b19a26b56f204db614" ] || [ "$actual" = "7a35f6a83242d329608ffb51140fefacbe3e7e66d4837c16661229c2ae13dece" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/plugin/EmployeePluginListIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/plugin/PluginManagerServiceIntegrationTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/plugin/PluginManagerServiceIntegrationTest.java")
 [ "$actual" = "5d991dbae7d4ddd7f3cc6e8b19355ca139c5df7a86a12658e8ad7d301b66fecc" ] || [ "$actual" = "9edd4c3a99f06a84ec663b8e8ec4bd1fae5ef573f9413d3407988a55ba695503" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/plugin/PluginManagerServiceIntegrationTest.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityService.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityService.java")
 [ "$actual" = "203a92171dbc43290e4b36a53deef045391c33d83672708930ac727a7ff1b122" ] || [ "$actual" = "" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/auth/security/SessionValidityService.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityFilter.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityFilter.java")
 [ "$actual" = "708ef14593f9b66bf3e39b1dce3633f140d867391f20e461f9ecb8a237fdedfe" ] || [ "$actual" = "" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/auth/security/SessionValidityFilter.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketAuthentication.java" ]; then
 actual=$(hash "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketAuthentication.java")
 [ "$actual" = "6e43d495a98fa352dbf129f5927a9e0b423c36151005e459730d5ba32d64174a" ] || [ "$actual" = "" ] || { echo "refusing to overwrite concurrent edits: server/src/main/java/com/workforcehub/im/websocket/ImWebSocketAuthentication.java" >&2; exit 3; }
fi
if [ -f "$target/server/src/test/java/com/workforcehub/auth/SessionValidityRequestChainTest.java" ]; then
 actual=$(hash "$target/server/src/test/java/com/workforcehub/auth/SessionValidityRequestChainTest.java")
 [ "$actual" = "3bea59b6d0cb42c67a07059dab13ab90ccec5d4b6be295d196a31ae4c4aedfa4" ] || [ "$actual" = "" ] || { echo "refusing to overwrite concurrent edits: server/src/test/java/com/workforcehub/auth/SessionValidityRequestChainTest.java" >&2; exit 3; }
fi
mkdir -p "$target/server/src/main/java/com/workforcehub/common/config"
cat > "$target/server/src/main/java/com/workforcehub/common/config/SecurityConfig.java" <<'WFH_99_2_BASELINE_0'
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
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(repository)
                .csrfTokenRequestHandler(requestHandler)
            )
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
            )

            .authorizeHttpRequests(auth -> auth
                // 匿名入口（按接口粒度拆开，避免通配符把不同安全级别接口混在一起）
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/api/employee/auth/login").permitAll()
                .requestMatchers("/api/employee/auth/login/send-code").permitAll()
                .requestMatchers("/api/employee/auth/email/send-code").permitAll()
                .requestMatchers("/api/employee/auth/password/reset").permitAll()
                .requestMatchers("/api/public/auth/register/send-sms-code").permitAll()
                .requestMatchers("/api/public/auth/register").permitAll()
                .requestMatchers("/api/admin/auth/login").permitAll()
                .requestMatchers("/api/public/branding").permitAll()
                // email/bind 等其余 auth 接口需要登录态
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/doc.html", "/webjars/**", "/favicon.ico").permitAll()
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
                    R<Void> body = R.fail(BusinessErrorCode.FORBIDDEN.code(), "无权限访问");
                    Object requestId = request.getAttribute(RequestIdFilter.MDC_KEY);
                    if (requestId != null) {
                        body.setRequestId(requestId.toString());
                    }
                    objectMapper.writeValue(response.getWriter(), body);
                })
            );

        return http.build();
    }
}
WFH_99_2_BASELINE_0
mkdir -p "$target/server/src/main/java/com/workforcehub/im/websocket"
cat > "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandshakeInterceptor.java" <<'WFH_99_2_BASELINE_1'
package com.workforcehub.im.websocket;

import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.im.repository.ImRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * 从现有员工登录 Session 解析 WebSocket 身份。
 */
// WebSocket 握手拦截：从 HTTP Session 中提取已认证的员工身份，拒绝未登录连接
@Component
public class ImWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    private final ImRepository imRepository;

    public ImWebSocketHandshakeInterceptor(ImRepository imRepository) {
        this.imRepository = imRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        WfhUserDetails user = resolveCurrentUser(request);
        if (user == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (user.getUserType() != UserType.USER) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        if (!imRepository.existsLoginAllowedOrgUser(user.getUserId())) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
        attributes.put(ImWebSocketHandler.USER_ID_ATTRIBUTE, user.getUserId());
        attributes.put(ImWebSocketHandler.DISPLAY_NAME_ATTRIBUTE, user.getDisplayName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private WfhUserDetails resolveCurrentUser(ServerHttpRequest request) {
        WfhUserDetails current = resolveFromAuthentication(SecurityContextHolder.getContext().getAuthentication());
        if (current != null) {
            return current;
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpSession session = servletRequest.getServletRequest().getSession(false);
            if (session == null) {
                return null;
            }
            Object context = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context instanceof SecurityContext securityContext) {
                return resolveFromAuthentication(securityContext.getAuthentication());
            }
        }
        return null;
    }

    private WfhUserDetails resolveFromAuthentication(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof WfhUserDetails user ? user : null;
    }
}
WFH_99_2_BASELINE_1
mkdir -p "$target/server/src/main/java/com/workforcehub/im/websocket"
cat > "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketHandler.java" <<'WFH_99_2_BASELINE_2'
package com.workforcehub.im.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.LocalDateTime;

/**
 * 员工端 IM WebSocket 处理器。
 */
@Component
public class ImWebSocketHandler extends TextWebSocketHandler {

    public static final String USER_ID_ATTRIBUTE = "imUserId";
    public static final String DISPLAY_NAME_ATTRIBUTE = "imDisplayName";

    private final ImWebSocketSessionRegistry sessionRegistry;

    public ImWebSocketHandler(ImWebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = currentUserId(session);
        if (userId == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        sessionRegistry.register(userId, session);
        session.sendMessage(new TextMessage("""
            {"eventType":"CONNECTED","occurredAt":"%s"}
            """.formatted(LocalDateTime.now())));
    }

    // 心跳响应：兼容纯文本 PING 和 JSON 格式 {"PING"}
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if ("PING".equalsIgnoreCase(payload) || payload.contains("\"PING\"")) {
            session.sendMessage(new TextMessage("""
                {"eventType":"PONG","occurredAt":"%s"}
                """.formatted(LocalDateTime.now())));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private void unregister(WebSocketSession session) {
        Long userId = currentUserId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
        }
    }

    private Long currentUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID_ATTRIBUTE);
        return value instanceof Long userId ? userId : null;
    }
}
WFH_99_2_BASELINE_2
mkdir -p "$target/server/src/main/java/com/workforcehub/im/websocket"
cat > "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketSessionRegistry.java" <<'WFH_99_2_BASELINE_3'
package com.workforcehub.im.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 按员工用户 ID 维护当前活跃 IM WebSocket 连接。
 */
// WebSocket 会话注册表：ConcurrentHashMap 存储每个用户的多个连接（多标签页），支持广播推送
@Component
public class ImWebSocketSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ImWebSocketSessionRegistry.class);

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessionsByUserId.computeIfAbsent(userId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long userId, WebSocketSession session) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByUserId.remove(userId, sessions);
        }
    }

    public void clear() {
        sessionsByUserId.clear();
    }

    public int sendToUsers(Collection<Long> userIds, String payload) {
        try {
            return sendToUsersOrThrow(userIds, payload);
        } catch (IOException e) {
            log.warn("IM WebSocket 推送失败", e);
            return 0;
        }
    }

    public int sendToUsersOrThrow(Collection<Long> userIds, String payload) throws IOException {
        Set<Long> uniqueUserIds = new LinkedHashSet<>(userIds);
        int deliveredCount = 0;
        for (Long userId : uniqueUserIds) {
            deliveredCount += sendToUserOrThrow(userId, payload);
        }
        return deliveredCount;
    }

    private int sendToUserOrThrow(Long userId, String payload) throws IOException {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByUserId.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }

        int deliveredCount = 0;
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                unregister(userId, session);
                continue;
            }
            sendOrThrow(userId, session, payload);
            deliveredCount++;
        }
        return deliveredCount;
    }

    private void sendOrThrow(Long userId, WebSocketSession session, String payload) throws IOException {
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(payload));
            }
        } catch (IOException e) {
            log.warn("IM WebSocket 推送失败 userId={} sessionId={}", userId, session.getId(), e);
            unregister(userId, session);
            throw e;
        }
    }
}
WFH_99_2_BASELINE_3
mkdir -p "$target/server/src/main/java/com/workforcehub/auth/security"
cat > "$target/server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java" <<'WFH_99_2_BASELINE_4'
package com.workforcehub.auth.security;

import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.domain.OrgUser;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 员工/领导端 UserDetailsService
 * 通过 login_name 加载账号，并关联 org_user 主档
 *
 * 登录准入矩阵（按 employment_status）：
 * - PENDING_ONBOARD（待入职）：不允许登录，档案虽建但尚未正式生效上岗
 * - PROBATION（试用期）：允许登录
 * - ACTIVE（在职）：允许登录
 * - PENDING_OFFBOARD（待离职）：允许登录，需要处理工作交接
 * - OFFBOARDED（已离职）：不允许登录
 */
@Service("employeeUserDetailsService")
public class EmployeeUserDetailsService implements UserDetailsService {

    /**
     * 允许登录的员工状态白名单
     */
    private static final Set<String> LOGIN_ALLOWED_STATUSES = Set.of(
        "PROBATION",
        "ACTIVE",
        "PENDING_OFFBOARD"
    );

    private final AuthUserAccountMapper accountMapper;
    private final OrgUserMapper orgUserMapper;

    public EmployeeUserDetailsService(AuthUserAccountMapper accountMapper,
                                      OrgUserMapper orgUserMapper) {
        this.accountMapper = accountMapper;
        this.orgUserMapper = orgUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String loginName) throws UsernameNotFoundException {
        AuthUserAccount account = accountMapper.selectByLoginName(loginName);
        if (account == null && loginName != null && loginName.contains("@")) {
            // 兼容员工端使用邮箱登录：仅在输入形似邮箱时回退查询，避免每次登录都查两次。
            account = accountMapper.selectByEmail(loginName);
        }
        if (account == null) {
            throw new UsernameNotFoundException("账号不存在");
        }

        OrgUser orgUser = orgUserMapper.selectById(account.getUserId());
        if (orgUser == null) {
            throw new UsernameNotFoundException("员工档案不存在");
        }

        // 按登录准入矩阵校验
        String status = orgUser.getEmploymentStatus();
        if (!LOGIN_ALLOWED_STATUSES.contains(status)) {
            String message = switch (status) {
                case "PENDING_ONBOARD" -> "尚未正式入职，无法登录";
                case "OFFBOARDED" -> "员工已离职，无法登录";
                default -> "当前员工状态不允许登录";
            };
            throw new EmploymentLoginBlockedException(message);
        }

        return new EmployeeUserDetails(
            orgUser.getId(),
            account.getId(),
            account.getLoginName(),
            account.getPasswordHash(),
            orgUser.getRealName(),
            account.getAccountStatus()
        );
    }
}
WFH_99_2_BASELINE_4
mkdir -p "$target/server/src/test/java/com/workforcehub/im"
cat > "$target/server/src/test/java/com/workforcehub/im/ModuleImApiIntegrationTest.java" <<'WFH_99_2_BASELINE_5'
package com.workforcehub.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.service.ImAgentReplyResult;
import com.workforcehub.im.service.ImAgentConversationService;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatcher;
import com.workforcehub.im.websocket.ImWebSocketSessionRegistry;
import com.workforcehub.support.LocalIntegrationTestSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-im 员工端会话读取接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleImApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long MANAGER_USER_ID = 2002L;
    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long OTHER_EMPLOYEE_USER_ID = 2004L;
    private static final ObjectMapper STATIC_OBJECT_MAPPER = new ObjectMapper();
    private static HttpServer modelServer;
    private static String modelBaseUrl;

    static {
        System.setProperty("test.workforce.agent.model.enabled", "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImWebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    private ImWebSocketOutboxDispatcher imWebSocketOutboxDispatcher;

    @Autowired
    private ImAgentConversationService imAgentResultCallback;

    @Value("${workforce.file.local-root:target/test-file-storage}")
    private String localRoot;

    @BeforeAll
    static void startModelServer() throws IOException {
        modelServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        modelServer.createContext("/v1/chat/completions", ModuleImApiIntegrationTest::handleModelRequest);
        modelServer.start();
        modelBaseUrl = "http://127.0.0.1:" + modelServer.getAddress().getPort() + "/v1";
    }

    @AfterAll
    static void stopModelServer() {
        if (modelServer != null) {
            modelServer.stop(0);
        }
        System.clearProperty("test.workforce.agent.model.enabled");
    }

    @DynamicPropertySource
    static void registerAgentModelProperties(DynamicPropertyRegistry registry) {
        registry.add("workforce.agent.model.enabled", () -> "true");
        registry.add("workforce.agent.model.base-url", () -> "");
        registry.add("workforce.agent.model.api-key", () -> "");
        registry.add("workforce.agent.model.model", () -> "");
        registry.add("workforce.file.local-root", () -> "target/test-file-storage");
    }

    @BeforeEach
    void clearWebSocketSessions() {
        webSocketSessionRegistry.clear();
    }

    @Test
    @DisplayName("员工应只能看到自己参与的活跃会话并返回最后消息和未读数")
    void employeeShouldListOnlyJoinedActiveConversations() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long avatarAttachmentRefId = seedAttachmentRef(
            MANAGER_USER_ID,
            "manager-avatar.png",
            "manager-avatar-content",
            "CLEAN",
            0,
            false,
            "USER_AVATAR",
            "PUBLIC"
        );
        jdbcTemplate.update("UPDATE org_user SET avatar_attachment_ref_id = ? WHERE id = ?", avatarAttachmentRefId, MANAGER_USER_ID);
        seedPrivateConversation();
        seedOtherConversation();
        seedInactiveConversation();

        mockMvc.perform(get("/api/employee/im/conversations")
                .cookie(employeeSession)
                .header("X-Request-Id", "im-list-001"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.requestId").value("im-list-001"))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7001))
            .andExpect(jsonPath("$.data[0].conversationType").value("PRIVATE"))
            .andExpect(jsonPath("$.data[0].title").value("张三与李四"))
            .andExpect(jsonLong("$.data[0].avatarFileId", avatarAttachmentRefId))
            .andExpect(jsonPath("$.data[0].lastMessageSeq").value(2))
            .andExpect(jsonPath("$.data[0].lastMessage.senderType").value("USER"))
            .andExpect(jsonLong("$.data[0].lastMessage.senderId", MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[0].lastMessage.contentText").value("下午同步一下排班。"))
            .andExpect(jsonPath("$.data[0].unreadCount").value(1))
            .andExpect(jsonPath("$.data[0].pin").value(false))
            .andExpect(jsonPath("$.data[0].mute").value(false));
    }

    @Test
    @DisplayName("私聊对方头像不是有效头像附件时会话列表不返回头像ID")
    void conversationListShouldHideInvalidPrivateAvatarAttachment() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long invalidAvatarAttachmentRefId = seedPersonalAttachmentRef(
            MANAGER_USER_ID,
            "manager-file.txt",
            "manager-file-content",
            "CLEAN",
            0,
            false
        );
        jdbcTemplate.update("UPDATE org_user SET avatar_attachment_ref_id = ? WHERE id = ?", invalidAvatarAttachmentRefId, MANAGER_USER_ID);
        seedPrivateConversation();
        jdbcTemplate.update("UPDATE im_conversation SET avatar_file_id = ? WHERE id = ?", invalidAvatarAttachmentRefId, 7001L);

        mockMvc.perform(get("/api/employee/im/conversations")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7001))
            .andExpect(jsonPath("$.data[0].avatarFileId").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("员工会话列表应支持按最新消息时间游标分页")
    void employeeShouldPageConversationsByLastMessageCursor() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        seedPagedConversation(7010L, 7110L, "分页会话A", "2026-05-09 12:00:00", "分页第一条");
        seedPagedConversation(7011L, 7111L, "分页会话B", "2026-05-09 11:30:00", "分页第二条");

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("size", "2")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7010))
            .andExpect(jsonPath("$.data[1].conversationId").value(7011));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "2026-05-09T11:30:00")
                .queryParam("beforeConversationId", "7011")
                .queryParam("size", "2")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7001));
    }

    @Test
    @DisplayName("员工置顶会话应优先出现在会话列表前面")
    void employeePinnedConversationShouldSortBeforeRecentUnpinnedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        seedPagedConversation(7010L, 7110L, "分页会话A", "2026-05-09 12:00:00", "分页第一条");
        seedPagedConversation(7011L, 7111L, "分页会话B", "2026-05-09 11:30:00", "分页第二条");
        jdbcTemplate.update("""
            UPDATE im_conversation_member
            SET pin_flag = 1
            WHERE conversation_id = 7001
              AND member_type = 'USER'
              AND member_id = ?
            """, EMPLOYEE_USER_ID);

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("size", "3")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7001))
            .andExpect(jsonPath("$.data[0].pin").value(true))
            .andExpect(jsonPath("$.data[1].conversationId").value(7010))
            .andExpect(jsonPath("$.data[2].conversationId").value(7011));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "2026-05-09T10:10:00")
                .queryParam("beforeConversationId", "7001")
                .queryParam("size", "2")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].conversationId").value(7010))
            .andExpect(jsonPath("$.data[1].conversationId").value(7011));
    }

    @Test
    @DisplayName("员工会话列表分页参数非法时应返回业务错误")
    void employeeConversationPageParamsShouldBeValidated() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("size", "0")
                .cookie(employeeSession))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("分页大小必须大于0"));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "2026-05-09T10:10:00")
                .cookie(employeeSession))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("会话分页游标必须同时包含时间和会话ID"));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "bad-time")
                .queryParam("beforeConversationId", "7001")
                .cookie(employeeSession))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("会话分页时间游标格式错误"));
    }

    @Test
    @DisplayName("员工应能读取自己参与会话的详情")
    void employeeShouldReadJoinedConversationDetail() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", 7001L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversation.conversationId").value(7001))
            .andExpect(jsonPath("$.data.conversation.conversationType").value("PRIVATE"))
            .andExpect(jsonPath("$.data.conversation.lastMessage.contentText").value("下午同步一下排班。"))
            .andExpect(jsonPath("$.data.members", hasSize(2)))
            .andExpect(jsonLong("$.data.members[0].memberId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonLong("$.data.members[1].memberId", MANAGER_USER_ID))
            .andExpect(jsonPath("$.data.members[1].displayName").value("李四"))
            .andExpect(jsonPath("$.data.groupProfile").doesNotExist());
    }

    @Test
    @DisplayName("会话成员关联用户软删除后应返回稳定展示名降级值")
    void conversationDetailShouldFallbackDisplayNameWhenMemberProfileDeleted() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        jdbcTemplate.update("UPDATE org_user SET is_deleted = 1 WHERE id = ?", MANAGER_USER_ID);

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", 7001L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.members", hasSize(2)))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonLong("$.data.members[1].memberId", MANAGER_USER_ID))
            .andExpect(jsonPath("$.data.members[1].displayName").value("USER:%d".formatted(MANAGER_USER_ID)));
    }

    @Test
    @DisplayName("员工不能读取未加入会话的详情")
    void employeeShouldNotReadUnjoinedConversationDetail() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", 7002L)
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));
    }

    @Test
    @DisplayName("员工应能读取自己参与会话的消息并按序号升序返回")
    void employeeShouldListMessagesOfJoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].messageId").value(7101))
            .andExpect(jsonPath("$.data[0].seqNo").value(1))
            .andExpect(jsonLong("$.data[0].senderId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].contentText").value("我上午可以处理补卡。"))
            .andExpect(jsonPath("$.data[1].messageId").value(7102))
            .andExpect(jsonPath("$.data[1].seqNo").value(2))
            .andExpect(jsonLong("$.data[1].senderId", MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[1].contentText").value("下午同步一下排班。"));
    }

    @Test
    @DisplayName("员工应能翻译会话消息")
    void employeeShouldTranslateConversationMessage() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        seedActiveLlmModel();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/translate", 7001L, 7102L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.messageId").value(7102))
            .andExpect(jsonPath("$.data.sourceText").value("下午同步一下排班。"))
            .andExpect(jsonPath("$.data.translatedText").value("Translated: 下午同步一下排班。"));
    }

    @Test
    @DisplayName("员工应能收藏提醒删除和转发会话消息")
    void employeeShouldRunMessageActions() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        seedPagedConversation(7010L, 7110L, "分页会话A", "2026-05-09 12:00:00", "分页第一条");

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/favorite", 7001L, 7102L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.actionType").value("FAVORITE"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/reminder", 7001L, 7102L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "note": "稍后处理"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.actionType").value("REMINDER"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/forward", 7001L, 7102L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetConversationId": 7010
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7010))
            .andExpect(jsonPath("$.data.contentText").value("下午同步一下排班。"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/delete", 7001L, 7102L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.actionType").value("DELETE"));

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].messageId").value(7101));

        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message_user_action WHERE conversation_id = 7001 AND user_id = ?",
            Long.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("员工应能按 beforeSeq 和 size 分页读取更早消息")
    void employeeShouldListMessagesByBeforeSeqAndSize() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversationWithFourMessages();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .queryParam("beforeSeq", "4")
                .queryParam("size", "2")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].seqNo").value(2))
            .andExpect(jsonPath("$.data[0].contentText").value("第二条消息。"))
            .andExpect(jsonPath("$.data[1].seqNo").value(3))
            .andExpect(jsonPath("$.data[1].contentText").value("第三条消息。"));
    }

    @Test
    @DisplayName("员工读取消息分页 size 应限制在服务端上限内")
    void employeeMessagePageSizeShouldBeCappedByServerLimit() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversationWithFourMessages();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .queryParam("size", "200")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(4)))
            .andExpect(jsonPath("$.data[0].seqNo").value(1))
            .andExpect(jsonPath("$.data[3].seqNo").value(4));
    }

    @Test
    @DisplayName("员工不能读取未加入会话的消息")
    void employeeShouldNotReadMessagesOfUnjoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7002L)
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));
    }

    @Test
    @DisplayName("员工离职后的旧会话不能继续发送消息")
    void offboardedEmployeeShouldNotSendMessageWithOldSession() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        jdbcTemplate.update("UPDATE org_user SET employment_status = 'OFFBOARDED' WHERE id = ?", EMPLOYEE_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "离职后不应继续发送",
                      "clientMessageId": "client-msg-offboarded"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("当前员工状态不允许使用会话"));

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = 7001",
            Long.class
        ));
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT last_message_seq FROM im_conversation WHERE id = 7001",
            Long.class
        ));
    }

    @Test
    @DisplayName("员工应能向自己参与的会话发送文本消息并更新会话最后消息")
    void employeeShouldSendTextMessageToJoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "我下午两点同步。",
                      "clientMessageId": "client-msg-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7001))
            .andExpect(jsonPath("$.data.seqNo").value(3))
            .andExpect(jsonPath("$.data.senderType").value("USER"))
            .andExpect(jsonLong("$.data.senderId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.messageType").value("TEXT"))
            .andExpect(jsonPath("$.data.contentText").value("我下午两点同步。"))
            .andExpect(jsonPath("$.data.status").value("NORMAL"));

        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT last_message_seq FROM im_conversation WHERE id = 7001",
            Long.class
        ));
        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7001 AND member_id = ?",
            Long.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("授权员工应能创建Agent会话，发送消息后自动生成Agent回复")
    void authorizedEmployeeShouldStartAgentConversationAndReceiveAgentReply() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_QA_AGENT", "IM问答助手");
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationType").value("AGENT"))
            .andExpect(jsonPath("$.data.conversationKey").value("AGENT:%d:%d".formatted(EMPLOYEE_USER_ID, agentId)))
            .andExpect(jsonLong("$.data.boundAgentId", agentId))
            .andExpect(jsonPath("$.data.title").value("IM问答助手"))
            .andExpect(jsonPath("$.data.lastMessageSeq").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversation.conversationType").value("AGENT"))
            .andExpect(jsonLong("$.data.conversation.boundAgentId", agentId))
            .andExpect(jsonPath("$.data.members", hasSize(2)))
            .andExpect(jsonPath("$.data.members[0].memberType").value("USER"))
            .andExpect(jsonLong("$.data.members[0].memberId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonPath("$.data.members[1].memberType").value("AGENT"))
            .andExpect(jsonLong("$.data.members[1].memberId", agentId))
            .andExpect(jsonPath("$.data.members[1].displayName").value("IM问答助手"))
            .andExpect(jsonPath("$.data.groupProfile").doesNotExist());

        mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.conversationId", conversationId));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "帮我总结今天待办",
                      "clientMessageId": "client-msg-agent-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(1))
            .andExpect(jsonPath("$.data.senderType").value("USER"));

        awaitMessageCount(conversationId, 2L);
        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].seqNo").value(1))
            .andExpect(jsonPath("$.data[0].senderType").value("USER"))
            .andExpect(jsonPath("$.data[0].contentText").value("帮我总结今天待办"))
            .andExpect(jsonPath("$.data[1].seqNo").value(2))
            .andExpect(jsonPath("$.data[1].senderType").value("AGENT"))
            .andExpect(jsonLong("$.data[1].senderId", agentId))
            .andExpect(jsonPath("$.data[1].messageType").value("CARD"))
            .andExpect(jsonPath("$.data[1].contentText").value("年假与考勤影响"))
            .andExpect(jsonPath("$.data[1].contentJson").value(Matchers.allOf(
                Matchers.containsString("\"cardType\""),
                Matchers.containsString("\"knowledge_answer\"")
            )));

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT last_message_seq FROM im_conversation WHERE id = ?",
            Long.class,
            conversationId
        ));
        assertEquals(1L, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_task
            WHERE agent_id = ?
              AND trigger_type = 'CONVERSATION'
              AND context_type = 'IM_CONVERSATION'
              AND context_id = ?
              AND status = 'SUCCEEDED'
            """,
            Long.class,
            agentId,
            conversationId
        ));
        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", conversationId);
        assertSocketEvent(employeeSocket, "IM_AGENT_MESSAGE_CREATED", conversationId);
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
    }

    @Test
    @DisplayName("IM触发DAG时应注入当前员工并支持鉴权查询本人信息")
    void imDagAgentShouldReadCurrentEmployeeInfo() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedWorkflowAgent(adminSession, "IM_DAG_USER_AGENT", "员工信息助手", """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["employeeInfo"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "userData"
                },
                { "id": "end", "type": "END", "config": { "reply": "当前员工 {{userData.employeeInfo.realName}}，部门 {{userData.employeeInfo.primaryDeptName}}" } }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """);
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "我是谁",
                      "clientMessageId": "client-msg-dag-user"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        awaitMessageCount(conversationId, 2L);
        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[1].senderType").value("AGENT"))
            .andExpect(jsonPath("$.data[1].contentText").value(Matchers.containsString("张三")))
            .andExpect(jsonPath("$.data[1].contentText").value(Matchers.containsString("后端组")));

        awaitTaskStatus(agentId, conversationId, "SUCCEEDED");
        String traceJson = jdbcTemplate.queryForObject("""
            SELECT execution_trace_json
            FROM agt_task
            WHERE agent_id = ?
              AND context_id = ?
            """, String.class, agentId, conversationId);
        assertTrue(traceJson.contains("employeeInfo"));
        assertTrue(traceJson.contains("张三"));
    }

    @Test
    @DisplayName("IM触发DAG时应按意图端口分流")
    void imDagAgentShouldRouteByIntentPort() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedWorkflowAgent(adminSession, "IM_DAG_INTENT_AGENT", "意图分流助手", """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "recognize",
                  "type": "INTENT",
                  "config": {
                    "prompt": "请识别用户意图。",
                    "intents": ["申请请假", "查询假期", "问答"]
                  },
                  "outputKey": "intentResult",
                  "outputFormat": [
                    { "key": "intent", "type": "string", "description": "意图" }
                  ]
                },
                { "id": "leave_apply", "type": "END", "config": { "replyJson": { "intent": "申请请假", "target": "LEAVE_WORKFLOW" } } },
                {
                  "id": "load_leave",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["leaveBalance"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "leaveData",
                  "outputFormat": [
                    {
                      "key": "leaveBalance",
                      "type": "array",
                      "description": "假期账户",
                      "itemFields": [
                        { "key": "leaveName", "type": "string", "description": "假期名称" },
                        { "key": "availableMinutes", "type": "number", "description": "可用分钟" },
                        { "key": "frozenMinutes", "type": "number", "description": "冻结分钟" }
                      ]
                    }
                  ]
                },
                { "id": "leave_query", "type": "END", "config": { "replyJson": { "intent": "查询假期", "target": "LEAVE_BALANCE", "leaveAccounts": "{{leaveData}}" } } },
                { "id": "qa", "type": "END", "config": { "replyJson": { "intent": "问答", "target": "QA" } } },
                { "id": "other", "type": "END", "config": { "replyJson": { "intent": "其他", "target": "QA" } } }
              ],
              "edges": [
                { "from": "start", "to": "recognize" },
                { "from": "recognize", "fromPort": 0, "to": "leave_apply" },
                { "from": "recognize", "fromPort": 1, "to": "load_leave" },
                { "from": "recognize", "fromPort": 2, "to": "qa" },
                { "from": "recognize", "fromPort": 3, "to": "other" },
                { "from": "load_leave", "to": "leave_query" }
              ]
            }
            """);
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "帮我查一下假期余额",
                      "clientMessageId": "client-msg-dag-intent"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        awaitMessageCount(conversationId, 2L);
        MvcResult messagesResult = mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[1].senderType").value("AGENT"))
            .andReturn();

        JsonNode response = objectMapper.readTree(messagesResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode reply = objectMapper.readTree(response.path("data").get(1).path("contentText").asText());
        assertEquals("查询假期", reply.path("intent").asText());
        assertEquals("LEAVE_BALANCE", reply.path("target").asText());
        assertTrue(reply.path("leaveAccounts").isArray());
    }

    @Test
    @DisplayName("IM触发DAG时应支持JSON回复")
    void imDagAgentShouldWriteReplyJson() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedWorkflowAgent(adminSession, "IM_DAG_JSON_AGENT", "员工JSON助手", """
            {
              "nodes": [
                { "id": "start", "type": "START" },
                {
                  "id": "load_user",
                  "type": "TOOL",
                  "config": {
                    "dataSources": ["employeeInfo"],
                    "targetUserId": "{{input.userId}}"
                  },
                  "outputKey": "userData"
                },
                {
                  "id": "end",
                  "type": "END",
                  "config": {
                    "replyJson": {
                      "employee": {
                        "name": "{{userData.employeeInfo.realName}}",
                        "department": "{{userData.employeeInfo.primaryDeptName}}",
                        "position": "{{userData.employeeInfo.positionName}}"
                      }
                    }
                  }
                }
              ],
              "edges": [
                { "from": "start", "to": "load_user" },
                { "from": "load_user", "to": "end" }
              ]
            }
            """);
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "我是谁",
                      "clientMessageId": "client-msg-dag-json"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        awaitMessageCount(conversationId, 2L);
        MvcResult messagesResult = mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[1].senderType").value("AGENT"))
            .andReturn();

        JsonNode response = objectMapper.readTree(messagesResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        JsonNode reply = objectMapper.readTree(response.path("data").get(1).path("contentText").asText());
        assertEquals("张三", reply.path("employee").path("name").asText());
        assertEquals("后端组", reply.path("employee").path("department").asText());
        assertFalse(reply.path("employee").has("userId"));
    }

    @Test
    @DisplayName("Agent模型配置缺失时应失败任务并向会话写入系统提示")
    void agentModelMissingConfigShouldFailTaskAndWriteSystemMessage() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(
            adminSession,
            "IM_MODEL_MISSING_AGENT",
            "配置缺失助手",
            "{\"provider\":\"openai-compatible\",\"model\":\"stub-chat\"}"
        );
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "模型配置缺失时要失败可见",
                      "clientMessageId": "client-msg-agent-model-missing"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(1));

        awaitMessageCount(conversationId, 2L);
        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].senderType").value("USER"))
            .andExpect(jsonPath("$.data[1].senderType").value("SYSTEM"))
            .andExpect(jsonPath("$.data[1].contentText").value("AI助手暂时无法回复，请稍后再试"));

        awaitTaskStatus(agentId, conversationId, "FAILED");
        assertEquals("AI模型配置缺失", jdbcTemplate.queryForObject("""
            SELECT error_message
            FROM agt_task
            WHERE agent_id = ?
              AND context_id = ?
            """, String.class, agentId, conversationId));
        assertSocketEvent(employeeSocket, "IM_AGENT_REPLY_FAILED", conversationId);
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
    }

    @Test
    @DisplayName("Agent结果重复回写同一任务时应保持消息幂等")
    void repeatedAgentResultCallbackShouldReuseExistingMessage() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_IDEMPOTENT_AGENT", "幂等助手");
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        imAgentResultCallback.onAgentReply(new ImAgentReplyResult(
            conversationId,
            null,
            agentId,
            880071L,
            "TEXT",
            "{\"text\":\"第一次回写\"}",
            "SUCCEEDED",
            null,
            null,
            "agent-task-880071"
        ));
        imAgentResultCallback.onAgentReply(new ImAgentReplyResult(
            conversationId,
            null,
            agentId,
            880071L,
            "TEXT",
            "{\"text\":\"重复回写不应新建消息\"}",
            "SUCCEEDED",
            null,
            null,
            "agent-task-880071"
        ));

        assertEquals(1L, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_message
            WHERE conversation_id = ?
              AND sender_type = 'AGENT'
              AND sender_id = ?
              AND client_message_id = 'agent-task-880071'
            """,
            Long.class,
            conversationId,
            agentId
        ));
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT last_message_seq FROM im_conversation WHERE id = ?",
            Long.class,
            conversationId
        ));
        assertEquals("第一次回写", jdbcTemplate.queryForObject("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.text'))
            FROM im_message
            WHERE conversation_id = ?
              AND sender_type = 'AGENT'
            """, String.class, conversationId));
    }

    @Test
    @DisplayName("Agent失败结果重复回写同一任务时应保持系统提示幂等")
    void repeatedAgentFailureCallbackShouldReuseExistingSystemMessage() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_FAIL_IDEMPOTENT_AGENT", "失败幂等助手");
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        imAgentResultCallback.onAgentReply(new ImAgentReplyResult(
            conversationId,
            null,
            agentId,
            880072L,
            null,
            null,
            "FAILED",
            "TIMEOUT",
            "第一次失败",
            "agent-task-880072"
        ));
        imAgentResultCallback.onAgentReply(new ImAgentReplyResult(
            conversationId,
            null,
            agentId,
            880072L,
            null,
            null,
            "FAILED",
            "TIMEOUT",
            "重复失败不应新建系统提示",
            "agent-task-880072"
        ));

        assertEquals(1L, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM im_message
            WHERE conversation_id = ?
              AND sender_type = 'SYSTEM'
              AND sender_id = 0
              AND client_message_id = 'agent-task-880072'
            """,
            Long.class,
            conversationId
        ));
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT last_message_seq FROM im_conversation WHERE id = ?",
            Long.class,
            conversationId
        ));
        assertEquals("AI助手暂时无法回复，请稍后再试", jdbcTemplate.queryForObject("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.text'))
            FROM im_message
            WHERE conversation_id = ?
              AND sender_type = 'SYSTEM'
            """, String.class, conversationId));
    }

    @Test
    @DisplayName("Agent触发失败时应向会话写入系统提示消息")
    void agentTriggerFailureShouldWriteSystemMessage() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_FAILING_AGENT", "失效助手");
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);
        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();
        jdbcTemplate.update("UPDATE agt_agent SET status = 'INACTIVE' WHERE id = ?", agentId);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "Agent 已失效时也要有提示",
                      "clientMessageId": "client-msg-agent-failure"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(1));

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].senderType").value("USER"))
            .andExpect(jsonPath("$.data[1].senderType").value("SYSTEM"))
            .andExpect(jsonPath("$.data[1].contentText").value("AI助手暂时无法回复，请稍后再试"));

        assertEquals(0L, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_task
            WHERE agent_id = ?
              AND context_id = ?
            """, Long.class, agentId, conversationId));
        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", conversationId);
        assertSocketEvent(employeeSocket, "IM_AGENT_REPLY_FAILED", conversationId);
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
    }

    @Test
    @DisplayName("已弃用Agent的既有IM会话再次提问应写入弃用提示")
    void deprecatedAgentConversationShouldWriteDeprecatedSystemMessage() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_DEPRECATED_AGENT", "弃用IM助手");
        grantAgentAcl(adminSession, agentId, "USER", EMPLOYEE_USER_ID, "use");
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);

        MvcResult conversationResult = mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(delete("/api/admin/agents/{agentId}", agentId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当前 Agent 已弃用，无法继续对话"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "弃用后继续提问",
                      "clientMessageId": "client-msg-agent-deprecated"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(1));

        awaitMessageCount(conversationId, 2L);
        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].senderType").value("USER"))
            .andExpect(jsonPath("$.data[1].senderType").value("SYSTEM"))
            .andExpect(jsonPath("$.data[1].contentText").value("当前 Agent 已弃用，无法继续对话"));

        assertEquals(0L, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM agt_task
            WHERE agent_id = ?
              AND context_id = ?
            """, Long.class, agentId, conversationId));
        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", conversationId);
        assertSocketEvent(employeeSocket, "IM_AGENT_REPLY_FAILED", conversationId);
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
    }

    @Test
    @DisplayName("未授权员工不能创建Agent会话")
    void unauthorizedEmployeeShouldNotStartAgentConversation() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        long agentId = createPublishedAgentWithoutTool(adminSession, "IM_PRIVATE_AGENT", "私有IM助手");

        mockMvc.perform(post("/api/employee/im/agent-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentId": %d
                    }
                    """.formatted(agentId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权使用该AI助手"));

        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation WHERE conversation_type = 'AGENT'",
            Long.class
        ));
    }

    @Test
    @DisplayName("员工发送消息后应向会话成员推送WebSocket事件")
    void sendingMessageShouldPushWebSocketEventToConversationMembers() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);
        CapturingWebSocketSession outsiderSocket = registerSocket(OTHER_EMPLOYEE_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "实时推送消息。",
                      "clientMessageId": "client-msg-push-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", 7001L);
        assertSocketEvent(managerSocket, "IM_MESSAGE_CREATED", 7001L);
        assertEquals(0, outsiderSocket.messages().size());
        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "SENT");
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
        unregisterSocket(OTHER_EMPLOYEE_USER_ID, outsiderSocket);
    }

    @Test
    @DisplayName("员工状态不可用时不应继续收到IM WebSocket推送")
    void unavailableEmployeeShouldNotReceiveWebSocketPush() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);
        jdbcTemplate.update("UPDATE org_user SET employment_status = 'OFFBOARDED' WHERE id = ?", MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "只推给仍可用成员。",
                      "clientMessageId": "client-msg-push-active-only"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", 7001L);
        assertEquals(0, managerSocket.messages().size());
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("员工更新已读位点后应推送已读事件")
    void updatingReadPositionShouldPushWebSocketEvent() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/read-position", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSeq": 2
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertSocketEvent(employeeSocket, "IM_READ_POSITION_UPDATED", 7001L);
        assertSocketEvent(managerSocket, "IM_READ_POSITION_UPDATED", 7001L);
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("员工应能发送个人文件作为IM附件并在消息列表返回克隆后的消息附件")
    void employeeShouldSendOwnedPersonalFileAsMessageAttachment() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "排班附件.txt",
            "im-attachment-message-content",
            "PASSED",
            0,
            false
        );

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "FILE",
                      "text": "",
                      "attachmentRefIds": [%s],
                      "clientMessageId": "client-msg-file-001"
                    }
                    """.formatted(sourceAttachmentRefId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(3))
            .andExpect(jsonPath("$.data.messageType").value("FILE"))
            .andExpect(jsonPath("$.data.attachments", hasSize(1)))
            .andExpect(jsonPath("$.data.attachments[0].attachmentRefId").isString())
            .andExpect(jsonPath("$.data.attachments[0].displayName").value("排班附件.txt"))
            .andExpect(jsonPath("$.data.attachments[0].mimeType").value("text/plain"));

        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);
        Long messageId = messageId(7001L, 3L);
        assertEquals("IM_MESSAGE", attachmentOwnerType(messageAttachmentRefId));
        assertEquals(messageId, attachmentOwnerId(messageAttachmentRefId));
        assertEquals(fileObjectId(sourceAttachmentRefId), fileObjectId(messageAttachmentRefId));

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data[2].attachments[0].attachmentRefId", messageAttachmentRefId))
            .andExpect(jsonPath("$.data[2].attachments[0].displayName").value("排班附件.txt"));
    }

    @Test
    @DisplayName("员工应能发送即时消息上传附件并克隆成IM消息附件")
    void employeeShouldSendPendingImAttachmentAsMessageAttachment() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedAttachmentRef(
            EMPLOYEE_USER_ID,
            "群聊即时附件.txt",
            "im-pending-attachment-message-content",
            "PASSED",
            0,
            false,
            "IM_MESSAGE_PENDING",
            "OWNER"
        );

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "FILE",
                      "text": "",
                      "attachmentRefIds": [%s],
                      "clientMessageId": "client-msg-file-pending-001"
                    }
                    """.formatted(sourceAttachmentRefId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.messageType").value("FILE"))
            .andExpect(jsonPath("$.data.attachments", hasSize(1)))
            .andExpect(jsonPath("$.data.attachments[0].displayName").value("群聊即时附件.txt"));

        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);
        assertEquals("IM_MESSAGE", attachmentOwnerType(messageAttachmentRefId));
        assertEquals(fileObjectId(sourceAttachmentRefId), fileObjectId(messageAttachmentRefId));
    }

    @Test
    @DisplayName("员工不能发送不属于自己的个人文件作为IM附件")
    void employeeShouldNotSendOtherUserPersonalFileAsMessageAttachment() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long otherAttachmentRefId = seedPersonalAttachmentRef(
            MANAGER_USER_ID,
            "经理附件.txt",
            "im-other-owner-attachment-content",
            "PASSED",
            0,
            false
        );

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "FILE",
                      "attachmentRefIds": [%s],
                      "clientMessageId": "client-msg-file-forbidden"
                    }
                    """.formatted(otherAttachmentRefId)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权使用该附件引用"));

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = 7001",
            Long.class
        ));
    }

    @Test
    @DisplayName("会话成员应能为IM附件申请下载票据并下载原文件")
    void conversationMemberShouldDownloadImAttachmentWithTicket() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie managerSession = loginEmployee("lisi.manager");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "会话资料.txt",
            "im-attachment-download-content",
            "PASSED",
            0,
            false
        );
        Long messageId = sendAttachmentMessage(employeeSession, sourceAttachmentRefId);
        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);

        MvcResult ticketResult = mockMvc.perform(post(
                    "/api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets",
                    7001L,
                    messageId,
                    messageAttachmentRefId
                )
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "查看会话资料"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.ticketNo").isString())
            .andExpect(jsonPath("$.data.downloadUrl").value(Matchers.containsString("/api/employee/im/attachments/download/")))
            .andExpect(jsonPath("$.data.fileName").value("会话资料.txt"))
            .andReturn();

        String ticketNo = extractTicketNo(ticketResult);
        mockMvc.perform(get("/api/employee/im/attachments/download/{ticketNo}", ticketNo)
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .bytes("im-attachment-download-content".getBytes(StandardCharsets.UTF_8)));

        assertEquals("USED", downloadTicketStatus(ticketNo));
        assertEquals(1L, auditCount("USER", MANAGER_USER_ID, "IM_ATTACHMENT_DOWNLOAD_TICKET_CREATED"));
        assertEquals(1L, auditCount("USER", MANAGER_USER_ID, "IM_ATTACHMENT_DOWNLOAD_EXECUTED"));
    }

    @Test
    @DisplayName("非会话成员不能为IM附件申请下载票据")
    void nonMemberShouldNotCreateImAttachmentDownloadTicket() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie outsiderSession = loginEmployee("wangwu.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "越权资料.txt",
            "im-attachment-outsider-content",
            "PASSED",
            0,
            false
        );
        Long messageId = sendAttachmentMessage(employeeSession, sourceAttachmentRefId);
        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);

        mockMvc.perform(post(
                    "/api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets",
                    7001L,
                    messageId,
                    messageAttachmentRefId
                )
                .cookie(outsiderSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "越权查看"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));
    }

    @Test
    @DisplayName("IM附件不能绕过IM授权入口走普通个人文件下载")
    void imAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "普通入口隔离.txt",
            "im-attachment-personal-endpoint-content",
            "PASSED",
            0,
            false
        );
        sendAttachmentMessage(employeeSession, sourceAttachmentRefId);
        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", messageAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "尝试绕过IM附件授权入口"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("员工端文件接口仅允许下载个人文件或头像"));
    }

    @Test
    @DisplayName("员工应能编辑自己发送的文本消息")
    void employeeShouldEditOwnTextMessage() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/edit", 7001L, 7101L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text": "我上午可以处理补卡和附件整理。"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.messageId").value(7101))
            .andExpect(jsonPath("$.data.status").value("EDITED"))
            .andExpect(jsonPath("$.data.contentText").value("我上午可以处理补卡和附件整理。"));

        assertEquals("EDITED", messageStatus(7101L));
        assertEquals("我上午可以处理补卡和附件整理。", messageText(7101L));
        assertSocketEvent(managerSocket, "IM_MESSAGE_EDITED", 7001L);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("员工不能编辑别人发送的消息")
    void employeeShouldNotEditOtherUserMessage() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/edit", 7001L, 7102L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text": "不应编辑别人消息"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("只能操作自己发送的消息"));

        assertEquals("NORMAL", messageStatus(7102L));
    }

    @Test
    @DisplayName("员工不能编辑附件消息")
    void employeeShouldNotEditAttachmentMessage() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "不可编辑附件.txt",
            "im-attachment-edit-content",
            "PASSED",
            0,
            false
        );
        Long messageId = sendAttachmentMessage(employeeSession, sourceAttachmentRefId);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/edit", 7001L, messageId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text": "附件消息不支持编辑"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("当前仅支持编辑文本消息"));

        assertEquals("NORMAL", messageStatus(messageId));
    }

    @Test
    @DisplayName("员工应能撤回自己发送的消息并隐藏原文")
    void employeeShouldRecallOwnMessage() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/recall", 7001L, 7101L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.messageId").value(7101))
            .andExpect(jsonPath("$.data.status").value("RECALLED"))
            .andExpect(jsonPath("$.data.contentText").value("消息已撤回"));

        assertEquals("RECALLED", messageStatus(7101L));
        assertEquals("消息已撤回", messageText(7101L));
        assertSocketEvent(managerSocket, "IM_MESSAGE_RECALLED", 7001L);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("员工撤回附件消息后不能继续申请附件下载票据")
    void recalledAttachmentMessageShouldNotCreateDownloadTicket() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "撤回附件.txt",
            "im-attachment-recall-content",
            "PASSED",
            0,
            false
        );
        Long messageId = sendAttachmentMessage(employeeSession, sourceAttachmentRefId);
        Long messageAttachmentRefId = messageAttachmentRefId(7001L, 3L);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/recall", 7001L, messageId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("RECALLED"));

        mockMvc.perform(post(
                    "/api/employee/im/conversations/{conversationId}/messages/{messageId}/attachments/{attachmentRefId}/download-tickets",
                    7001L,
                    messageId,
                    messageAttachmentRefId
                )
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "撤回后下载"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当前消息状态不允许下载附件"));
    }

    @Test
    @DisplayName("已撤回消息不能再次编辑或撤回")
    void recalledMessageShouldNotBeEditedOrRecalledAgain() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/recall", 7001L, 7101L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("RECALLED"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/edit", 7001L, 7101L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "text": "撤回后不应编辑"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当前消息状态不允许编辑"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/recall", 7001L, 7101L)
                .cookie(employeeSession))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当前消息状态不允许撤回"));
    }

    @Test
    @DisplayName("员工重复发送同一 clientMessageId 应复用既有消息")
    void employeeShouldReuseExistingMessageWhenClientMessageIdRepeated() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        String payload = """
            {
              "messageType": "TEXT",
              "text": "我下午两点同步。",
              "clientMessageId": "client-msg-001"
            }
            """;

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(3));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.seqNo").value(3))
            .andExpect(jsonPath("$.data.contentText").value("我下午两点同步。"));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = 7001 AND sender_id = ? AND client_message_id = ?",
            Long.class,
            EMPLOYEE_USER_ID,
            "client-msg-001"
        ));
        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = 7001",
            Long.class
        ));
    }

    @Test
    @DisplayName("员工不能向未加入会话发送消息")
    void employeeShouldNotSendMessageToUnjoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7002L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "不应写入",
                      "clientMessageId": "client-msg-forbidden"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = 7002",
            Long.class
        ));
    }

    @Test
    @DisplayName("员工应能和直属上级发起私聊")
    void employeeShouldStartPrivateConversationWithDirectManager() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/private-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2002
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationType").value("PRIVATE"))
            .andExpect(jsonPath("$.data.conversationKey").value("PRIVATE:2002:2003"))
            .andExpect(jsonPath("$.data.title").value("张三与李四"))
            .andExpect(jsonPath("$.data.lastMessageSeq").value(0))
            .andExpect(jsonPath("$.data.lastReadSeq").value(0))
            .andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.data.memberRole").value("MEMBER"));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation WHERE conversation_key = 'PRIVATE:2002:2003'",
            Long.class
        ));
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id IN (SELECT id FROM im_conversation WHERE conversation_key = 'PRIVATE:2002:2003')",
            Long.class
        ));
    }

    @Test
    @DisplayName("领导应能和直属下级发起私聊")
    void managerShouldStartPrivateConversationWithDirectSubordinate() throws Exception {
        Cookie managerSession = loginEmployee("lisi.manager");

        mockMvc.perform(post("/api/employee/im/private-conversations")
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2003
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationType").value("PRIVATE"))
            .andExpect(jsonPath("$.data.conversationKey").value("PRIVATE:2002:2003"))
            .andExpect(jsonPath("$.data.title").value("张三与李四"));
    }

    @Test
    @DisplayName("无直属关系员工不能发起私聊")
    void employeeShouldNotStartPrivateConversationWithoutDirectRelation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/private-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2004
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("只能和直属上级、直属下级或同群成员发起私聊"));

        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation WHERE conversation_key = 'PRIVATE:2003:2004'",
            Long.class
        ));
    }

    @Test
    @DisplayName("重复发起私聊应复用已有会话")
    void startingExistingPrivateConversationShouldReuseConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/private-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2002
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7001))
            .andExpect(jsonPath("$.data.conversationKey").value("PRIVATE:2002:2003"))
            .andExpect(jsonPath("$.data.lastMessageSeq").value(2));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation WHERE conversation_key = 'PRIVATE:2002:2003'",
            Long.class
        ));
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = 7001",
            Long.class
        ));
    }

    @Test
    @DisplayName("员工应能更新自己参与会话的已读位点并刷新未读数")
    void employeeShouldUpdateReadPositionOfJoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/read-position", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSeq": 2
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7001))
            .andExpect(jsonPath("$.data.lastReadSeq").value(2))
            .andExpect(jsonPath("$.data.unreadCount").value(0));

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7001 AND member_id = ?",
            Long.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("员工应能更新自己参与会话的置顶和免打扰偏好")
    void employeeShouldUpdateConversationPreference() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        CapturingWebSocketSession employeeSocket = registerSocket(EMPLOYEE_USER_ID);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/preference", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pin": true,
                      "mute": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7001))
            .andExpect(jsonPath("$.data.pin").value(true))
            .andExpect(jsonPath("$.data.mute").value(true));

        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT pin_flag
            FROM im_conversation_member
            WHERE conversation_id = 7001
              AND member_id = ?
            """, Integer.class, EMPLOYEE_USER_ID));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT mute_flag
            FROM im_conversation_member
            WHERE conversation_id = 7001
              AND member_id = ?
            """, Integer.class, EMPLOYEE_USER_ID));
        assertSocketEvent(employeeSocket, "IM_CONVERSATION_PREFERENCE_UPDATED", 7001L);
        imWebSocketOutboxDispatcher.dispatchDueEvents();
        assertFalse(hasSocketEvent(managerSocket, "IM_CONVERSATION_PREFERENCE_UPDATED", 7001L));
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("员工更新会话偏好时空请求应返回业务错误")
    void employeeConversationPreferenceShouldRejectEmptyRequest() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/preference", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("会话偏好更新内容不能为空"));
    }

    @Test
    @DisplayName("员工不能更新未加入会话的偏好")
    void employeeShouldNotUpdatePreferenceOfUnjoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/preference", 7002L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "pin": true
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));
    }

    @Test
    @DisplayName("员工更新已读位点不能倒退")
    void employeeReadPositionShouldNotMoveBackward() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/read-position", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSeq": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.lastReadSeq").value(1))
            .andExpect(jsonPath("$.data.unreadCount").value(1));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7001 AND member_id = ?",
            Long.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("员工更新已读位点不能超过会话最新消息序号")
    void employeeReadPositionShouldBeCappedByLastMessageSeq() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/read-position", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSeq": 999
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.lastReadSeq").value(2))
            .andExpect(jsonPath("$.data.unreadCount").value(0));

        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7001 AND member_id = ?",
            Long.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("员工不能更新未加入会话的已读位点")
    void employeeShouldNotUpdateReadPositionOfUnjoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/read-position", 7002L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSeq": 1
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));

        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7002 AND member_id = ?",
            Long.class,
            OTHER_EMPLOYEE_USER_ID
        ));
    }

    private void seedPrivateConversation() {
        jdbcTemplate.update("DELETE FROM im_message_user_action WHERE conversation_id = 7001");
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (7001, 'PRIVATE', 'PRIVATE:2002:2003', '张三与李四', 2,
                 7102, '2026-05-09 10:10:00', 'ACTIVE', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (7201, 7001, 'USER', ?, 'MEMBER', 1, 'ACTIVE'),
                (7202, 7001, 'USER', ?, 'MEMBER', 2, 'ACTIVE')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (7101, 7001, 1, 'USER', ?, 'TEXT', JSON_OBJECT('text', '我上午可以处理补卡。'), 'NORMAL', '2026-05-09 10:00:00'),
                (7102, 7001, 2, 'USER', ?, 'TEXT', JSON_OBJECT('text', '下午同步一下排班。'), 'NORMAL', '2026-05-09 10:10:00')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
    }

    private void seedActiveLlmModel() {
        jdbcTemplate.update("DELETE FROM agt_llm_model WHERE id = 9301");
        jdbcTemplate.update("""
            INSERT INTO agt_llm_model
                (id, provider, model_name, display_name, endpoint, api_key, status)
            VALUES
                (9301, 'openai', 'test-model', '测试模型', ?, 'test-key', 'ACTIVE')
            """, modelBaseUrl);
    }

    private void seedPrivateConversationWithFourMessages() {
        jdbcTemplate.update("DELETE FROM im_message_user_action WHERE conversation_id = 7001");
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (7001, 'PRIVATE', 'PRIVATE:2002:2003', '张三与李四', 4,
                 7104, '2026-05-09 10:15:00', 'ACTIVE', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (7201, 7001, 'USER', ?, 'MEMBER', 1, 'ACTIVE'),
                (7202, 7001, 'USER', ?, 'MEMBER', 4, 'ACTIVE')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (7101, 7001, 1, 'USER', ?, 'TEXT', JSON_OBJECT('text', '第一条消息。'), 'NORMAL', '2026-05-09 10:00:00'),
                (7102, 7001, 2, 'USER', ?, 'TEXT', JSON_OBJECT('text', '第二条消息。'), 'NORMAL', '2026-05-09 10:05:00'),
                (7103, 7001, 3, 'USER', ?, 'TEXT', JSON_OBJECT('text', '第三条消息。'), 'NORMAL', '2026-05-09 10:10:00'),
                (7104, 7001, 4, 'USER', ?, 'TEXT', JSON_OBJECT('text', '第四条消息。'), 'NORMAL', '2026-05-09 10:15:00')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID);
    }

    private void seedOtherConversation() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (7002, 'PRIVATE', 'PRIVATE:2002:2004', '王五与李四', 1,
                 7103, '2026-05-09 11:00:00', 'ACTIVE', 2004, 2004, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (7203, 7002, 'USER', ?, 'MEMBER', 0, 'ACTIVE'),
                (7204, 7002, 'USER', ?, 'MEMBER', 1, 'ACTIVE')
            """, OTHER_EMPLOYEE_USER_ID, MANAGER_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (7103, 7002, 1, 'USER', ?, 'TEXT', JSON_OBJECT('text', '这是另一个员工的会话。'), 'NORMAL', '2026-05-09 11:00:00')
            """, OTHER_EMPLOYEE_USER_ID);
    }

    private void seedPagedConversation(Long conversationId,
                                       Long messageId,
                                       String title,
                                       String lastMessageAt,
                                       String text) {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (?, 'PRIVATE', ?, ?, 1,
                 ?, ?, 'ACTIVE', 2003, 2003, 0)
            """,
            conversationId,
            "PRIVATE:PAGED:" + conversationId,
            title,
            messageId,
            lastMessageAt
        );
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (?, ?, 'USER', ?, 'MEMBER', 0, 'ACTIVE'),
                (?, ?, 'USER', ?, 'MEMBER', 1, 'ACTIVE')
            """,
            conversationId * 10 + 1,
            conversationId,
            EMPLOYEE_USER_ID,
            conversationId * 10 + 2,
            conversationId,
            MANAGER_USER_ID
        );
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (?, ?, 1, 'USER', ?, 'TEXT', JSON_OBJECT('text', ?), 'NORMAL', ?)
            """,
            messageId,
            conversationId,
            EMPLOYEE_USER_ID,
            text,
            lastMessageAt
        );
    }

    private void seedInactiveConversation() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (7003, 'GROUP', 'GROUP:ARCHIVED', '已归档群', 1,
                 7104, '2026-05-09 12:00:00', 'ARCHIVED', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (7205, 7003, 'USER', ?, 'MEMBER', 0, 'ACTIVE')
            """, EMPLOYEE_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (7104, 7003, 1, 'SYSTEM', 0, 'SYSTEM', JSON_OBJECT('text', '归档会话不应出现在列表。'), 'NORMAL', '2026-05-09 12:00:00')
            """);
    }

    private long seedPersonalAttachmentRef(long uploaderId,
                                           String displayName,
                                           String content,
                                           String scanStatus,
                                           int isDeleted,
                                           boolean deletedAt) {
        return seedAttachmentRef(uploaderId, displayName, content, scanStatus, isDeleted, deletedAt, "PERSONAL_FILE", "OWNER");
    }

    private long seedAttachmentRef(long uploaderId,
                                   String displayName,
                                   String content,
                                   String scanStatus,
                                   int isDeleted,
                                   boolean deletedAt,
                                   String ownerType,
                                   String visibilityScope) {
        long suffix = Math.abs((long) content.hashCode());
        long fileObjectId = 9700_0000L + suffix;
        long attachmentRefId = 9800_0000L + suffix;
        String contentSha256 = "%064x".formatted(attachmentRefId);
        String objectKey = "im-test/" + attachmentRefId + ".txt";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeLocalObject(objectKey, bytes);
        jdbcTemplate.update("""
            INSERT INTO sys_file_object
                (id, storage_provider, bucket_name, object_key, file_name, file_ext,
                 mime_type, file_size, file_hash, content_sha256, ref_count,
                 upload_status, scan_status, created_by, is_deleted)
            VALUES
                (?, 'LOCAL', 'local', ?, ?, 'txt',
                 'text/plain', ?, ?, ?, 1,
                 'UPLOADED', ?, ?, 0)
            """,
            fileObjectId,
            objectKey,
            displayName,
            (long) bytes.length,
            contentSha256,
            contentSha256,
            scanStatus,
            uploaderId
        );
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope,
                 deleted_at, sort_order, created_by, is_deleted)
            VALUES
                (?, ?, NULL, ?, ?, ?, 'USER', ?, ?,
                 IF(?, NOW(), NULL), 0, ?, ?)
            """,
            attachmentRefId,
            ownerType,
            fileObjectId,
            displayName,
            displayName,
            uploaderId,
            visibilityScope,
            deletedAt,
            uploaderId,
            isDeleted
        );
        return attachmentRefId;
    }

    private Long sendAttachmentMessage(Cookie employeeSession, long sourceAttachmentRefId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "FILE",
                      "attachmentRefIds": [%s],
                      "clientMessageId": "client-msg-file-helper-%s"
                    }
                    """.formatted(sourceAttachmentRefId, sourceAttachmentRefId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("messageId").asLong();
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String extractTicketNo(MvcResult result) throws Exception {
        return data(result).get("ticketNo").asText();
    }

    private void writeLocalObject(String objectKey, byte[] content) {
        try {
            Path target = Path.of(localRoot).resolve(objectKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (Exception e) {
            throw new IllegalStateException("写入本地测试文件失败", e);
        }
    }

    private Long messageId(Long conversationId, Long seqNo) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM im_message WHERE conversation_id = ? AND seq_no = ?",
            Long.class,
            conversationId,
            seqNo
        );
    }

    private Long messageAttachmentRefId(Long conversationId, Long seqNo) {
        return jdbcTemplate.queryForObject("""
            SELECT ar.id
            FROM sys_attachment_ref ar
            JOIN im_message m ON m.id = ar.owner_id
            WHERE ar.owner_type = 'IM_MESSAGE'
              AND m.conversation_id = ?
              AND m.seq_no = ?
            """,
            Long.class,
            conversationId,
            seqNo
        );
    }

    private Long attachmentOwnerId(Long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT owner_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            attachmentRefId
        );
    }

    private String attachmentOwnerType(Long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT owner_type FROM sys_attachment_ref WHERE id = ?",
            String.class,
            attachmentRefId
        );
    }

    private Long fileObjectId(Long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT file_object_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            attachmentRefId
        );
    }

    private String downloadTicketStatus(String ticketNo) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM sys_download_ticket WHERE ticket_no = ?",
            String.class,
            ticketNo
        );
    }

    private String messageStatus(Long messageId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM im_message WHERE id = ?",
            String.class,
            messageId
        );
    }

    private String messageText(Long messageId) {
        return jdbcTemplate.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(content_json, '$.text')) FROM im_message WHERE id = ?",
            String.class,
            messageId
        );
    }

    private Long auditCount(String operatorType, Long operatorId, String actionType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE operator_type = ?
              AND operator_id = ?
              AND action_type = ?
            """,
            Long.class,
            operatorType,
            operatorId,
            actionType
        );
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private long createPublishedAgentWithoutTool(Cookie adminSession, String agentCode, String agentName) throws Exception {
        return createPublishedAgentWithoutTool(
            adminSession,
            agentCode,
            agentName,
            """
            {"provider":"openai-compatible","baseUrl":"%s","apiKey":"test-key","model":"stub-chat"}
            """.formatted(modelBaseUrl).trim()
        );
    }

    private long createPublishedAgentWithoutTool(Cookie adminSession,
                                                 String agentCode,
                                                 String agentName,
                                                 String modelConfigJson) throws Exception {
        long agentId = createAgent(adminSession, agentCode, agentName);
        long versionId = createAgentVersion(adminSession, agentId, modelConfigJson);
        grantAgentAcl(adminSession, agentId, "ADMIN_USER", 1001L, "manage");
        publishAgentVersion(adminSession, versionId);
        return agentId;
    }

    private long createPublishedWorkflowAgent(Cookie adminSession,
                                              String agentCode,
                                              String agentName,
                                              String workflowConfigJson) throws Exception {
        long agentId = createAgent(adminSession, agentCode, agentName);
        long versionId = createWorkflowAgentVersion(adminSession, agentId, workflowConfigJson);
        grantAgentAcl(adminSession, agentId, "ADMIN_USER", 1001L, "manage");
        publishAgentVersion(adminSession, versionId);
        return agentId;
    }

    private long createAgent(Cookie adminSession, String agentCode, String agentName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentCode": "%s",
                      "agentName": "%s",
                      "agentType": "QA",
                      "identityMode": "SYSTEM_IDENTITY"
                    }
                    """.formatted(agentCode, agentName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("agentId").asLong();
    }

    private long createAgentVersion(Cookie adminSession, long agentId, String modelConfigJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": 1,
                      "modelConfigJson": %s,
                      "systemPrompt": "你是IM会话助手。",
                      "workflowConfigJson": "{\\"mode\\":\\"conversation-demo\\"}"
                    }
                    """.formatted(objectMapper.writeValueAsString(modelConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("versionId").asLong();
    }

    private long createWorkflowAgentVersion(Cookie adminSession, long agentId, String workflowConfigJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/agents/{agentId}/versions", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionNo": 1,
                      "modelConfigJson": "%s",
                      "systemPrompt": "你是IM会话助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(escapeJsonString("""
                        {"provider":"openai-compatible","baseUrl":"%s","apiKey":"test-key","model":"stub-chat"}
                        """.formatted(modelBaseUrl).trim()), escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long versionId = data(result).get("versionId").asLong();
        mockMvc.perform(post("/api/admin/agents/workflow-run")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "versionId": %d,
                      "inputJson": "{\\"userId\\":2003,\\"query\\":\\"我是谁\\"}",
                      "modelConfigJson": "%s",
                      "systemPrompt": "你是IM会话助手。",
                      "workflowConfigJson": "%s"
                    }
                    """.formatted(versionId, escapeJsonString("""
                        {"provider":"openai-compatible","baseUrl":"%s","apiKey":"test-key","model":"stub-chat"}
                        """.formatted(modelBaseUrl).trim()), escapeJsonString(workflowConfigJson))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.success").value(true));
        return versionId;
    }

    private void publishAgentVersion(Cookie adminSession, long versionId) throws Exception {
        mockMvc.perform(post("/api/admin/agents/versions/{versionId}/publish", versionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private void grantAgentAcl(Cookie adminSession,
                               long agentId,
                               String subjectType,
                               long subjectId,
                               String permCode) throws Exception {
        mockMvc.perform(post("/api/admin/agents/{agentId}/acl", agentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "subjectType": "%s",
                      "subjectId": %d,
                      "permCode": "%s",
                      "status": "ACTIVE"
                    }
                    """.formatted(subjectType, subjectId, permCode)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private CapturingWebSocketSession registerSocket(Long userId) {
        CapturingWebSocketSession session = new CapturingWebSocketSession(userId);
        webSocketSessionRegistry.register(userId, session);
        return session;
    }

    private void unregisterSocket(Long userId, CapturingWebSocketSession session) {
        webSocketSessionRegistry.unregister(userId, session);
    }

    private void assertSocketEvent(CapturingWebSocketSession session,
                                   String eventType,
                                   Long conversationId) throws Exception {
        awaitCondition(() -> {
            imWebSocketOutboxDispatcher.dispatchDueEvents();
            return hasSocketEvent(session, eventType, conversationId);
        }, "应收到 IM WebSocket 事件: " + eventType);
    }

    private boolean hasSocketEvent(CapturingWebSocketSession session,
                                   String eventType,
                                   Long conversationId) throws Exception {
        for (String message : session.messages()) {
            JsonNode node = objectMapper.readTree(message);
            if (eventType.equals(node.path("eventType").asText())
                && conversationId.equals(node.path("conversationId").asLong())) {
                return true;
            }
        }
        return false;
    }

    private void awaitMessageCount(Long conversationId, Long expectedCount) throws Exception {
        awaitCondition(() -> expectedCount.equals(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_message WHERE conversation_id = ?",
            Long.class,
            conversationId
        )), "等待IM消息数量达到 " + expectedCount);
    }

    private void awaitTaskStatus(Long agentId, Long conversationId, String expectedStatus) throws Exception {
        awaitCondition(() -> expectedStatus.equals(jdbcTemplate.queryForObject("""
            SELECT status
            FROM agt_task
            WHERE agent_id = ?
              AND context_id = ?
            """, String.class, agentId, conversationId)), "等待Agent任务状态变为 " + expectedStatus);
    }

    private void awaitOutboxStatus(String eventType, Long conversationId, String expectedStatus) throws Exception {
        imWebSocketOutboxDispatcher.dispatchDueEvents();
        awaitCondition(() -> expectedStatus.equals(jdbcTemplate.queryForObject("""
            SELECT status
            FROM im_event_outbox
            WHERE event_type = ?
              AND conversation_id = ?
            ORDER BY id DESC
            LIMIT 1
            """, String.class, eventType, conversationId)), "等待IM outbox状态变为 " + expectedStatus);
    }

    private void awaitCondition(CheckedBooleanSupplier condition, String message) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private String escapeJsonString(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private static void handleModelRequest(HttpExchange exchange) throws IOException {
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (requestBody.contains("请翻译以下消息")) {
            writeModelResponse(exchange, "Translated: 下午同步一下排班。");
            return;
        }
        if (requestBody.contains("可选意图")) {
            String intent = requestBody.contains("假期") ? "查询假期"
                : requestBody.contains("请假") ? "申请请假"
                : "问答";
            writeModelResponse(exchange, """
                {"intent":"%s"}
                """.formatted(intent).trim());
            return;
        }
        String cardJson = """
            {
              "cardType": "knowledge_answer",
              "state": "ANSWERED",
              "title": "年假与考勤影响",
              "answer": "审批通过后会扣减年假余额，并同步生成请假考勤结果。",
              "confidence": "high",
              "citations": [
                {
                  "id": "policy-annual-leave",
                  "title": "员工年假制度",
                  "sourceType": "POLICY",
                  "score": 0.87
                }
              ]
            }
            """;
        writeModelResponse(exchange, cardJson);
    }

    private static void writeModelResponse(HttpExchange exchange, String content) throws IOException {
        String escapedContent = STATIC_OBJECT_MAPPER.writeValueAsString(content);
        byte[] body = """
            {
              "id": "chatcmpl-im-test",
              "object": "chat.completion",
              "created": 1710000000,
              "model": "stub-chat",
              "usage": {
                "prompt_tokens": 8,
                "completion_tokens": 4,
                "total_tokens": 12
              },
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": %s
                  }
                }
              ]
            }
            """.formatted(escapedContent).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private Cookie loginEmployee(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "Employee@123456"
                    }
                    """.formatted(loginName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private static class CapturingWebSocketSession implements WebSocketSession {

        private final String id;
        private final Map<String, Object> attributes;
        private final List<String> messages = new ArrayList<>();
        private boolean open = true;

        private CapturingWebSocketSession(Long userId) {
            this.id = "test-im-ws-" + userId;
            this.attributes = Map.of("imUserId", userId);
        }

        protected List<String> messages() {
            return messages;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/ws/employee/im");
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("localhost", 0);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("localhost", 0);
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            // no-op
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 8192;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            // no-op
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 8192;
        }

        @Override
        public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return List.of();
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            messages.add(message.getPayload().toString());
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(org.springframework.web.socket.CloseStatus status) {
            open = false;
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {

        boolean getAsBoolean() throws Exception;
    }
}
WFH_99_2_BASELINE_5
mkdir -p "$target/server/src/test/java/com/workforcehub/im"
cat > "$target/server/src/test/java/com/workforcehub/im/ModuleImOutboxIntegrationTest.java" <<'WFH_99_2_BASELINE_6'
package com.workforcehub.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatcher;
import com.workforcehub.im.websocket.ImWebSocketSessionRegistry;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-im outbox 可靠投递与后台治理接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleImOutboxIntegrationTest extends LocalIntegrationTestSupport {

    private static final long MANAGER_USER_ID = 2002L;
    private static final long EMPLOYEE_USER_ID = 2003L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ImWebSocketSessionRegistry webSocketSessionRegistry;

    @Autowired
    private ImWebSocketOutboxDispatcher imWebSocketOutboxDispatcher;

    @BeforeEach
    void clearWebSocketSessions() {
        webSocketSessionRegistry.clear();
    }

    @Test
    @DisplayName("WebSocket推送失败时outbox应保留失败记录并支持重试")
    void websocketOutboxShouldRetryFailedDelivery() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        FailingOnceWebSocketSession employeeSocket = new FailingOnceWebSocketSession(EMPLOYEE_USER_ID);
        webSocketSessionRegistry.register(EMPLOYEE_USER_ID, employeeSocket);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "失败后要能重试。",
                      "clientMessageId": "client-msg-push-retry-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "FAILED");
        assertEquals(0, employeeSocket.messages().size());
        assertEquals(0, managerSocket.messages().size());
        webSocketSessionRegistry.register(EMPLOYEE_USER_ID, employeeSocket);
        jdbcTemplate.update("""
            UPDATE im_event_outbox
            SET next_retry_at = NOW()
            WHERE event_type = 'IM_MESSAGE_CREATED'
              AND conversation_id = ?
              AND status = 'FAILED'
            """, 7001L);

        imWebSocketOutboxDispatcher.dispatchDueEvents();

        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", 7001L);
        assertSocketEvent(managerSocket, "IM_MESSAGE_CREATED", 7001L);
        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "SENT");
        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("管理员应能查询、查看、重试并派发IM outbox事件")
    void adminShouldManageImOutboxEvents() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedPrivateConversation();
        FailingOnceWebSocketSession employeeSocket = new FailingOnceWebSocketSession(EMPLOYEE_USER_ID);
        webSocketSessionRegistry.register(EMPLOYEE_USER_ID, employeeSocket);
        CapturingWebSocketSession managerSocket = registerSocket(MANAGER_USER_ID);

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "后台要能看见并重试outbox。",
                      "clientMessageId": "client-msg-admin-outbox-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "FAILED");
        Long eventId = latestOutboxEventId("IM_MESSAGE_CREATED", 7001L);

        mockMvc.perform(get("/api/admin/im/outbox-events")
                .queryParam("status", "FAILED")
                .queryParam("eventType", "IM_MESSAGE_CREATED")
                .queryParam("conversationId", "7001")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonLong("$.data.list[0].eventId", eventId))
            .andExpect(jsonPath("$.data.list[0].eventType").value("IM_MESSAGE_CREATED"))
            .andExpect(jsonPath("$.data.list[0].status").value("FAILED"))
            .andExpect(jsonPath("$.data.list[0].lastError").value("simulated websocket failure"));

        mockMvc.perform(get("/api/admin/im/outbox-events/{eventId}", eventId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.eventId", eventId))
            .andExpect(jsonPath("$.data.payloadJson").value(Matchers.containsString("IM_MESSAGE_CREATED")));

        mockMvc.perform(get("/api/admin/im/outbox-events")
                .queryParam("status", "UNKNOWN")
                .cookie(adminSession))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("outbox事件状态不支持"));

        mockMvc.perform(post("/api/admin/im/outbox-events/dispatch")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestedBatchSize").value(50))
            .andExpect(jsonPath("$.data.dispatchedCount").value(0));

        mockMvc.perform(get("/api/admin/im/outbox-events")
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        mockMvc.perform(post("/api/admin/im/outbox-events/{eventId}/retry", eventId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.lastError").doesNotExist());
        assertEquals(1L, auditCount("ADMIN", 8001L, "IM_OUTBOX_RETRY_REQUESTED"));
        JsonNode retryAudit = latestAuditDetail("IM_OUTBOX_RETRY_REQUESTED");
        assertEquals(eventId.longValue(), retryAudit.path("eventId").asLong());

        webSocketSessionRegistry.register(EMPLOYEE_USER_ID, employeeSocket);
        mockMvc.perform(post("/api/admin/im/outbox-events/dispatch")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "batchSize": 10
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestedBatchSize").value(10))
            .andExpect(jsonPath("$.data.dispatchedCount").value(1));
        assertEquals(2L, auditCount("ADMIN", 8001L, "IM_OUTBOX_DISPATCH_TRIGGERED"));
        JsonNode dispatchAudit = latestAuditDetail("IM_OUTBOX_DISPATCH_TRIGGERED");
        assertEquals(1, dispatchAudit.path("attemptedCount").asInt());
        assertEquals(1, dispatchAudit.path("dispatchedCount").asInt());
        assertEquals(eventId.longValue(), dispatchAudit.path("dispatchedEventIds").get(0).asLong());

        mockMvc.perform(get("/api/admin/im/outbox-events/audits")
                .queryParam("eventId", String.valueOf(eventId))
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.list[*].actionType",
                Matchers.containsInAnyOrder("IM_OUTBOX_RETRY_REQUESTED", "IM_OUTBOX_DISPATCH_TRIGGERED")))
            .andExpect(jsonPath("$.data.list[0].detailJson").exists());

        mockMvc.perform(get("/api/admin/im/outbox-events/audits")
                .queryParam("actionType", "UNKNOWN")
                .cookie(adminSession))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("outbox审计动作不支持"));

        assertSocketEvent(employeeSocket, "IM_MESSAGE_CREATED", 7001L);
        assertSocketEvent(managerSocket, "IM_MESSAGE_CREATED", 7001L);
        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "SENT");

        mockMvc.perform(post("/api/admin/im/outbox-events/{eventId}/retry", eventId)
                .cookie(adminSession))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当前outbox事件状态不允许手动重试"));

        FailingOnceWebSocketSession batchEmployeeSocket = new FailingOnceWebSocketSession(EMPLOYEE_USER_ID);
        webSocketSessionRegistry.register(EMPLOYEE_USER_ID, batchEmployeeSocket);
        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages", 7001L)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "messageType": "TEXT",
                      "text": "批量重试也要能处理。",
                      "clientMessageId": "client-msg-admin-outbox-batch-001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        awaitOutboxStatus("IM_MESSAGE_CREATED", 7001L, "FAILED");
        Long batchRetryEventId = latestOutboxEventId("IM_MESSAGE_CREATED", 7001L);

        mockMvc.perform(post("/api/admin/im/outbox-events/batch-retry")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventIds": [%d, %d, 999999999999]
                    }
                    """.formatted(batchRetryEventId, eventId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestedCount").value(3))
            .andExpect(jsonPath("$.data.succeededCount").value(1))
            .andExpect(jsonPath("$.data.skippedCount").value(2))
            .andExpect(jsonLong("$.data.items[0].eventId", batchRetryEventId))
            .andExpect(jsonPath("$.data.items[0].succeeded").value(true))
            .andExpect(jsonLong("$.data.items[1].eventId", eventId))
            .andExpect(jsonPath("$.data.items[1].succeeded").value(false))
            .andExpect(jsonPath("$.data.items[1].skipReason").value("当前outbox事件状态不允许手动重试"))
            .andExpect(jsonPath("$.data.items[2].eventId").value(999999999999L))
            .andExpect(jsonPath("$.data.items[2].succeeded").value(false))
            .andExpect(jsonPath("$.data.items[2].skipReason").value("IM outbox事件不存在"));

        JsonNode batchRetryAudit = latestAuditDetail("IM_OUTBOX_BATCH_RETRY_REQUESTED");
        assertEquals(3, batchRetryAudit.path("requestedCount").asInt());
        assertEquals(1, batchRetryAudit.path("succeededCount").asInt());
        assertEquals(2, batchRetryAudit.path("skippedCount").asInt());
        assertEquals(batchRetryEventId.longValue(), batchRetryAudit.path("succeededEventIds").get(0).asLong());

        mockMvc.perform(get("/api/admin/im/outbox-events/audits")
                .queryParam("actionType", "IM_OUTBOX_BATCH_RETRY_REQUESTED")
                .queryParam("eventId", String.valueOf(batchRetryEventId))
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].actionType").value("IM_OUTBOX_BATCH_RETRY_REQUESTED"));

        unregisterSocket(EMPLOYEE_USER_ID, employeeSocket);
        unregisterSocket(EMPLOYEE_USER_ID, batchEmployeeSocket);
        unregisterSocket(MANAGER_USER_ID, managerSocket);
    }

    @Test
    @DisplayName("管理员批量重试IM outbox应校验事件ID列表")
    void adminBatchRetryOutboxEventsShouldValidateEventIds() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(post("/api/admin/im/outbox-events/batch-retry")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventIds": []
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("outbox事件ID不能为空"));

        mockMvc.perform(post("/api/admin/im/outbox-events/batch-retry")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "eventIds": [0]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("outbox事件ID必须大于0"));

        mockMvc.perform(post("/api/admin/im/outbox-events/batch-retry")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventIds\":%s}".formatted(tooManyEventIdsJson())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("单次批量重试最多支持100个事件"));
    }

    @Test
    @DisplayName("管理员应能查看IM outbox状态总览")
    void adminShouldSummarizeImOutboxEvents() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOutboxSummaryEvents();

        mockMvc.perform(get("/api/admin/im/outbox-events/summary")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.totalCount").value(6))
            .andExpect(jsonPath("$.data.dueDispatchCount").value(3))
            .andExpect(jsonPath("$.data.staleSendingCount").value(1))
            .andExpect(jsonPath("$.data.earliestCreatedAt").value("2026-05-12T08:00:00"))
            .andExpect(jsonPath("$.data.earliestNextRetryAt").value("2000-01-01T00:00:00"))
            .andExpect(jsonPath("$.data.generatedAt").exists())
            .andExpect(jsonPath("$.data.sendingStaleBefore").exists())
            .andExpect(jsonPath("$.data.statusCounts[*].status",
                Matchers.contains("PENDING", "SENDING", "SENT", "FAILED", "DEAD")))
            .andExpect(jsonPath("$.data.statusCounts[*].count",
                Matchers.contains(2, 1, 1, 1, 1)));

        mockMvc.perform(get("/api/admin/im/outbox-events/summary")
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    @Test
    @DisplayName("管理员应能查看IM outbox失败原因聚合")
    void adminShouldGetImOutboxFailureSummary() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOutboxFailureSummaryEvents();

        mockMvc.perform(get("/api/admin/im/outbox-events/failure-summary")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[0].status").value("FAILED"))
            .andExpect(jsonPath("$.data[0].lastError").value("simulated websocket failure"))
            .andExpect(jsonPath("$.data[0].eventCount").value(2))
            .andExpect(jsonPath("$.data[0].maxRetryCount").value(3))
            .andExpect(jsonPath("$.data[0].firstOccurredAt").value("2026-05-12T08:00:00"))
            .andExpect(jsonPath("$.data[0].lastOccurredAt").value("2026-05-12T08:05:00"))
            .andExpect(jsonPath("$.data[1].status").value("FAILED"))
            .andExpect(jsonPath("$.data[1].lastError").value("UNKNOWN"))
            .andExpect(jsonPath("$.data[1].eventCount").value(1))
            .andExpect(jsonPath("$.data[2].status").value("DEAD"))
            .andExpect(jsonPath("$.data[2].lastError").value("max attempts"))
            .andExpect(jsonPath("$.data[2].eventCount").value(1));

        mockMvc.perform(get("/api/admin/im/outbox-events/failure-summary")
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    private void seedPrivateConversation() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, conversation_key, title, last_message_seq,
                 last_message_id, last_message_at, status, created_by, updated_by, is_deleted)
            VALUES
                (7001, 'PRIVATE', 'PRIVATE:2002:2003', '张三与李四', 2,
                 7102, '2026-05-09 10:10:00', 'ACTIVE', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, last_read_seq, status)
            VALUES
                (7201, 7001, 'USER', ?, 'MEMBER', 1, 'ACTIVE'),
                (7202, 7001, 'USER', ?, 'MEMBER', 2, 'ACTIVE')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
        jdbcTemplate.update("""
            INSERT INTO im_message
                (id, conversation_id, seq_no, sender_type, sender_id, message_type, content_json, status, sent_at)
            VALUES
                (7101, 7001, 1, 'USER', ?, 'TEXT', JSON_OBJECT('text', '我上午可以处理补卡。'), 'NORMAL', '2026-05-09 10:00:00'),
                (7102, 7001, 2, 'USER', ?, 'TEXT', JSON_OBJECT('text', '下午同步一下排班。'), 'NORMAL', '2026-05-09 10:10:00')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
    }

    private void seedOutboxSummaryEvents() {
        jdbcTemplate.update("""
            INSERT INTO im_event_outbox
                (id, event_type, conversation_id, message_id, seq_no, actor_user_id,
                 recipient_user_ids_json, payload_json, status, retry_count, next_retry_at,
                 last_error, claimed_at, sent_at, created_at, updated_at)
            VALUES
                (7901, 'IM_MESSAGE_CREATED', 7001, 7101, 1, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'PENDING', 0, '2000-01-01 00:00:00',
                 NULL, NULL, NULL, '2026-05-12 08:00:00', '2026-05-12 08:00:00'),
                (7902, 'IM_MESSAGE_CREATED', 7001, 7102, 2, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'PENDING', 0, '2099-05-12 08:00:00',
                 NULL, NULL, NULL, '2026-05-12 08:05:00', '2026-05-12 08:05:00'),
                (7903, 'IM_MESSAGE_CREATED', 7001, 7103, 3, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'FAILED', 1, '2000-01-01 00:05:00',
                 'manual failure', NULL, NULL, '2026-05-12 08:10:00', '2026-05-12 08:10:00'),
                (7904, 'IM_MESSAGE_CREATED', 7001, 7104, 4, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'SENDING', 0, '2026-05-12 08:15:00',
                 NULL, '2000-01-01 00:10:00', NULL, '2026-05-12 08:15:00', '2026-05-12 08:15:00'),
                (7905, 'IM_MESSAGE_CREATED', 7001, 7105, 5, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'SENT', 0, '2026-05-12 08:20:00',
                 NULL, '2026-05-12 08:20:00', '2026-05-12 08:20:01', '2026-05-12 08:20:00', '2026-05-12 08:20:01'),
                (7906, 'IM_MESSAGE_CREATED', 7001, 7106, 6, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'DEAD', 5, '2026-05-12 08:25:00',
                 'max attempts', NULL, NULL, '2026-05-12 08:25:00', '2026-05-12 08:25:00')
            """,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID
        );
    }

    private void seedOutboxFailureSummaryEvents() {
        jdbcTemplate.update("""
            INSERT INTO im_event_outbox
                (id, event_type, conversation_id, message_id, seq_no, actor_user_id,
                 recipient_user_ids_json, payload_json, status, retry_count, next_retry_at,
                 last_error, claimed_at, sent_at, created_at, updated_at)
            VALUES
                (7911, 'IM_MESSAGE_CREATED', 7001, 7111, 1, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'FAILED', 1, '2026-05-12 08:00:00',
                 'simulated websocket failure', NULL, NULL, '2026-05-12 07:59:00', '2026-05-12 08:00:00'),
                (7912, 'IM_MESSAGE_CREATED', 7001, 7112, 2, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'FAILED', 3, '2026-05-12 08:05:00',
                 'simulated websocket failure', NULL, NULL, '2026-05-12 08:04:00', '2026-05-12 08:05:00'),
                (7913, 'IM_MESSAGE_CREATED', 7001, 7113, 3, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'DEAD', 5, '2026-05-12 08:10:00',
                 'max attempts', NULL, NULL, '2026-05-12 08:09:00', '2026-05-12 08:10:00'),
                (7914, 'IM_MESSAGE_CREATED', 7001, 7114, 4, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'FAILED', 2, '2026-05-12 08:15:00',
                 NULL, NULL, NULL, '2026-05-12 08:14:00', '2026-05-12 08:15:00'),
                (7915, 'IM_MESSAGE_CREATED', 7001, 7115, 5, ?,
                 JSON_ARRAY(?, ?), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'SENT', 0, '2026-05-12 08:20:00',
                 'sent item should be ignored', NULL, '2026-05-12 08:20:01', '2026-05-12 08:20:00', '2026-05-12 08:20:01')
            """,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID,
            EMPLOYEE_USER_ID, EMPLOYEE_USER_ID, MANAGER_USER_ID
        );
    }

    private CapturingWebSocketSession registerSocket(Long userId) {
        CapturingWebSocketSession session = new CapturingWebSocketSession(userId);
        webSocketSessionRegistry.register(userId, session);
        return session;
    }

    private void unregisterSocket(Long userId, CapturingWebSocketSession session) {
        webSocketSessionRegistry.unregister(userId, session);
    }

    private void assertSocketEvent(CapturingWebSocketSession session,
                                   String eventType,
                                   Long conversationId) throws Exception {
        awaitCondition(() -> {
            imWebSocketOutboxDispatcher.dispatchDueEvents();
            return hasSocketEvent(session, eventType, conversationId);
        }, "应收到 IM WebSocket 事件: " + eventType);
    }

    private boolean hasSocketEvent(CapturingWebSocketSession session,
                                   String eventType,
                                   Long conversationId) throws Exception {
        for (String message : session.messages()) {
            JsonNode node = objectMapper.readTree(message);
            if (eventType.equals(node.path("eventType").asText())
                && conversationId.equals(node.path("conversationId").asLong())) {
                return true;
            }
        }
        return false;
    }

    private void awaitOutboxStatus(String eventType, Long conversationId, String expectedStatus) throws Exception {
        imWebSocketOutboxDispatcher.dispatchDueEvents();
        awaitCondition(() -> expectedStatus.equals(jdbcTemplate.queryForObject("""
            SELECT status
            FROM im_event_outbox
            WHERE event_type = ?
              AND conversation_id = ?
            ORDER BY id DESC
            LIMIT 1
            """, String.class, eventType, conversationId)), "等待IM outbox状态变为 " + expectedStatus);
    }

    private Long latestOutboxEventId(String eventType, Long conversationId) {
        return jdbcTemplate.queryForObject("""
            SELECT id
            FROM im_event_outbox
            WHERE event_type = ?
              AND conversation_id = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            Long.class,
            eventType,
            conversationId
        );
    }

    private Long auditCount(String operatorType, Long operatorId, String actionType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE operator_type = ?
              AND operator_id = ?
              AND action_type = ?
            """,
            Long.class,
            operatorType,
            operatorId,
            actionType
        );
    }

    private JsonNode latestAuditDetail(String actionType) throws Exception {
        String detailJson = jdbcTemplate.queryForObject("""
            SELECT CAST(detail_json AS CHAR)
            FROM sys_audit_log
            WHERE action_type = ?
            ORDER BY occurred_at DESC, id DESC
            LIMIT 1
            """,
            String.class,
            actionType
        );
        return objectMapper.readTree(detailJson);
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private Cookie loginEmployee(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "Employee@123456"
                    }
                    """.formatted(loginName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private String tooManyEventIdsJson() {
        List<Long> eventIds = new ArrayList<>();
        for (long i = 1; i <= 101; i++) {
            eventIds.add(i);
        }
        return eventIds.toString();
    }

    private void awaitCondition(CheckedBooleanSupplier condition, String message) throws Exception {
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50L);
        }
        assertTrue(condition.getAsBoolean(), message);
    }

    private static class CapturingWebSocketSession implements WebSocketSession {

        private final String id;
        private final Map<String, Object> attributes;
        private final List<String> messages = new ArrayList<>();
        private boolean open = true;

        private CapturingWebSocketSession(Long userId) {
            this.id = "test-im-ws-" + userId;
            this.attributes = Map.of("imUserId", userId);
        }

        protected List<String> messages() {
            return messages;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public URI getUri() {
            return URI.create("ws://localhost/ws/employee/im");
        }

        @Override
        public HttpHeaders getHandshakeHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return attributes;
        }

        @Override
        public Principal getPrincipal() {
            return null;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return new InetSocketAddress("localhost", 0);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return new InetSocketAddress("localhost", 0);
        }

        @Override
        public String getAcceptedProtocol() {
            return null;
        }

        @Override
        public void setTextMessageSizeLimit(int messageSizeLimit) {
            // no-op
        }

        @Override
        public int getTextMessageSizeLimit() {
            return 8192;
        }

        @Override
        public void setBinaryMessageSizeLimit(int messageSizeLimit) {
            // no-op
        }

        @Override
        public int getBinaryMessageSizeLimit() {
            return 8192;
        }

        @Override
        public List<org.springframework.web.socket.WebSocketExtension> getExtensions() {
            return List.of();
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            messages.add(message.getPayload().toString());
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public void close(org.springframework.web.socket.CloseStatus status) {
            open = false;
        }
    }

    private static class FailingOnceWebSocketSession extends CapturingWebSocketSession {

        private boolean failed;

        private FailingOnceWebSocketSession(Long userId) {
            super(userId);
        }

        @Override
        public void sendMessage(org.springframework.web.socket.WebSocketMessage<?> message) throws IOException {
            if (!failed) {
                failed = true;
                throw new IOException("simulated websocket failure");
            }
            super.sendMessage(message);
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {

        boolean getAsBoolean() throws Exception;
    }
}
WFH_99_2_BASELINE_6
mkdir -p "$target/server/src/test/java/com/workforcehub/auth"
cat > "$target/server/src/test/java/com/workforcehub/auth/AdminPermissionWebTest.java" <<'WFH_99_2_BASELINE_7'
package com.workforcehub.auth;

import com.workforcehub.auth.controller.AdminAuthController;
import com.workforcehub.auth.dto.CurrentUserResponse;
import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.im.controller.AdminImOutboxController;
import com.workforcehub.im.dto.AdminImOutboxDispatchResponse;
import com.workforcehub.im.service.ImOutboxAdminService;
import com.workforcehub.system.controller.AdminAlertController;
import com.workforcehub.system.controller.AdminSystemController;
import com.workforcehub.system.service.AdminAlertService;
import com.workforcehub.system.service.AdminSystemService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AdminAlertController.class, AdminImOutboxController.class, AdminSystemController.class,
    AdminAuthController.class})
@ContextConfiguration(classes = {AdminAlertController.class, AdminImOutboxController.class,
    AdminSystemController.class, AdminAuthController.class, SecurityConfig.class,
    UserTypePathFilter.class, AdminPermissionFilter.class})
class AdminPermissionWebTest {

    @Autowired private MockMvc mvc;
    @MockBean private AdminAlertService alerts;
    @MockBean private ImOutboxAdminService outbox;
    @MockBean private AdminSystemService system;
    @MockBean private LoginService login;
    @MockBean private AccountService account;
    @MockBean(name = "employeeUserDetailsService") private EmployeeUserDetailsService employees;
    @MockBean(name = "adminUserDetailsService") private AdminUserDetailsService admins;

    record Endpoint(String method, String path, String permission, String body, String serviceMethod) {}

    static Stream<Endpoint> previouslyUnmappedEndpoints() {
        return Stream.of(
            new Endpoint("GET", "/api/admin/alerts", "ADMIN_ALERT_MANAGE", "", "listActiveAlerts"),
            new Endpoint("GET", "/api/admin/alerts/channels", "ADMIN_ALERT_MANAGE", "", "listChannels"),
            new Endpoint("POST", "/api/admin/alerts/channels", "ADMIN_ALERT_MANAGE",
                "{\"channelCode\":\"FIXTURE\",\"channelName\":\"fixture\",\"channelType\":\"IN_APP\"}", "createChannel"),
            new Endpoint("PUT", "/api/admin/alerts/rules/FIXTURE", "ADMIN_ALERT_MANAGE",
                "{\"enabled\":true,\"notifyEnabled\":false,\"cooldownMinutes\":0}", "updateRule"),
            new Endpoint("POST", "/api/admin/alerts/FIXTURE/resolutions", "ADMIN_ALERT_MANAGE",
                "{\"actionType\":\"RESOLVE\"}", "createResolution"),
            new Endpoint("GET", "/api/admin/alerts/resolutions", "ADMIN_ALERT_MANAGE", "", "pageResolutions"),
            new Endpoint("GET", "/api/admin/im/outbox-events", "ADMIN_IM_OUTBOX_MANAGE", "", "pageOutboxEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/summary", "ADMIN_IM_OUTBOX_MANAGE", "", "summarizeOutboxEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/failure-summary", "ADMIN_IM_OUTBOX_MANAGE", "", "listOutboxFailureSummary"),
            new Endpoint("GET", "/api/admin/im/outbox-events/audits", "ADMIN_IM_OUTBOX_MANAGE", "", "pageOutboxAudits"),
            new Endpoint("POST", "/api/admin/im/outbox-events/batch-retry", "ADMIN_IM_OUTBOX_MANAGE",
                "{\"eventIds\":[1]}", "batchRetryOutboxEvents"),
            new Endpoint("POST", "/api/admin/im/outbox-events/dispatch", "ADMIN_IM_OUTBOX_MANAGE",
                "{\"batchSize\":7}", "dispatchDueEvents"),
            new Endpoint("GET", "/api/admin/im/outbox-events/1", "ADMIN_IM_OUTBOX_MANAGE", "", "getOutboxEvent"),
            new Endpoint("POST", "/api/admin/im/outbox-events/1/retry", "ADMIN_IM_OUTBOX_MANAGE", "", "retryOutboxEvent"),
            new Endpoint("GET", "/api/admin/system/roles", "ADMIN_RBAC_MANAGE", "", "listRoles"),
            new Endpoint("GET", "/api/admin/roles", "ADMIN_RBAC_MANAGE", "", "listRoles"),
            new Endpoint("GET", "/api/admin/system/permissions", "ADMIN_RBAC_MANAGE", "", "listPermissions"),
            new Endpoint("GET", "/api/admin/permissions", "ADMIN_RBAC_MANAGE", "", "listPermissions"),
            new Endpoint("GET", "/api/admin/system/audit-logs", "ADMIN_AUDIT_LOG_VIEW", "", "pageAuditLogs"),
            new Endpoint("GET", "/api/admin/audit-logs", "ADMIN_AUDIT_LOG_VIEW", "", "pageAuditLogs")
        );
    }

    @ParameterizedTest
    @MethodSource("previouslyUnmappedEndpoints")
    void deniedRequestsMustNeverReachBusinessAndExplicitPermissionMustReachExpectedHandler(Endpoint endpoint)
            throws Exception {
        Object service = endpoint.path().contains("/alerts") ? alerts
            : endpoint.path().contains("/im/") ? outbox : system;
        Class<?> controller = service == alerts ? AdminAlertController.class
            : service == outbox ? AdminImOutboxController.class : AdminSystemController.class;
        for (List<String> permissions : List.of(List.<String>of(), List.of("UNRELATED_PERMISSION"))) {
            mvc.perform(request(HttpMethod.valueOf(endpoint.method()), endpoint.path())
                    .with(admin(List.of(), permissions)).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(100403));
            verifyNoInteractions(alerts, outbox, system);
        }
        for (List<String> roles : List.of(List.<String>of(), List.of("SUPER_ADMIN"))) {
            mvc.perform(request(HttpMethod.valueOf(endpoint.method()), endpoint.path())
                    .with(admin(roles, roles.isEmpty() ? List.of(endpoint.permission()) : List.of())).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(endpoint.body()))
                .andExpect(status().isOk()).andExpect(handler().handlerType(controller))
                .andExpect(jsonPath("$.code").value(0));
            assertEquals(1, mockingDetails(service).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals(endpoint.serviceMethod())).count());
            clearInvocations(alerts, outbox, system);
        }
    }

    @Test
    void dispatchResponseMustComeFromRealControllerAndExplicitServiceInvocation() throws Exception {
        when(outbox.dispatchDueEvents(7)).thenReturn(AdminImOutboxDispatchResponse.of(7, 3));
        mvc.perform(post("/api/admin/im/outbox-events/dispatch").with(csrf())
                .with(admin(List.of(), List.of("ADMIN_IM_OUTBOX_MANAGE")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"batchSize\":7}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.requestedBatchSize").value(7))
            .andExpect(jsonPath("$.data.dispatchedCount").value(3));
        verify(outbox).dispatchDueEvents(7);
    }

    @Test
    void unknownRouteAndWrongMethodMustBeDeniedEvenForSuperAdmin() throws Exception {
        mvc.perform(get("/api/admin/new-business").with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/system/audit-logs").with(csrf())
                .with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/auth/new-business").with(admin(List.of("SUPER_ADMIN"), List.of())))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alerts, outbox, system, account, login);
    }

    @Test
    void anonymousMustRemain401AndEmployeeMustRemain403() throws Exception {
        mvc.perform(get("/api/admin/alerts")).andExpect(status().isUnauthorized());
        var employee = new EmployeeUserDetails(2L, 2L, "fixture", "unused", "fixture", "ACTIVE");
        mvc.perform(get("/api/admin/auth/me").with(authentication(
                new UsernamePasswordAuthenticationToken(employee, null, employee.getAuthorities()))))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alerts, outbox, system, account, login);
    }

    @Test
    void authenticatedAdminMustAccessOnlyExplicitCommonEndpoints() throws Exception {
        var current = new CurrentUserResponse();
        current.setDisplayName("fixture-current-admin");
        when(account.getCurrentUser()).thenReturn(current);
        mvc.perform(get("/api/admin/auth/me").with(admin(List.of(), List.of())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.displayName").value("fixture-current-admin"));
        verify(account).getCurrentUser();
        mvc.perform(post("/api/admin/auth/logout").with(admin(List.of(), List.of())).with(csrf()))
            .andExpect(status().isOk());
        verify(login).logout(org.mockito.ArgumentMatchers.any());
        mvc.perform(get("/api/admin/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/auth/logout").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousLoginMustReachExistingControllerWithCsrf() throws Exception {
        mvc.perform(post("/api/admin/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"fixture\",\"password\":\"fixture-password\"}"))
            .andExpect(status().isOk()).andExpect(handler().handlerType(AdminAuthController.class));
        verify(login).loginAdmin(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private RequestPostProcessor admin(List<String> roles, List<String> permissions) {
        var user = new AdminUserDetails(1L, "fixture", "unused", "fixture", "ACTIVE", roles, permissions);
        return authentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
WFH_99_2_BASELINE_7
mkdir -p "$target/server/src/test/java/com/workforcehub/plugin"
cat > "$target/server/src/test/java/com/workforcehub/plugin/AdminPluginManagementWebTest.java" <<'WFH_99_2_BASELINE_8'
package com.workforcehub.plugin;

import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.plugin.controller.admin.AdminPluginController;
import com.workforcehub.plugin.dto.AdminPluginResponse;
import com.workforcehub.plugin.dto.AdminPluginResponse.DependencyResponse;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.AdminPluginManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminPluginController.class)
@ContextConfiguration(classes = {
    AdminPluginController.class,
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    UserTypePathFilter.class,
    AdminPermissionFilter.class
})
class AdminPluginManagementWebTest {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final String MANAGE_PERMISSION = "ADMIN_PLUGIN_MANAGE";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminPluginManagementService service;

    @MockBean(name = "employeeUserDetailsService")
    private EmployeeUserDetailsService employeeUserDetailsService;

    @MockBean(name = "adminUserDetailsService")
    private AdminUserDetailsService adminUserDetailsService;

    @Test
    void anonymousShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/plugins"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(service);
    }

    @Test
    void employeeShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/plugins").with(employee()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void adminWithoutPermissionShouldReturn403() throws Exception {
        mockMvc.perform(get("/api/admin/plugins").with(admin(List.of())))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdminShouldListPluginsWithoutInternalFields() throws Exception {
        when(service.list()).thenReturn(List.of(pluginResponse()));

        mockMvc.perform(get("/api/admin/plugins").with(authorizedAdmin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].id").value(PLUGIN_ID))
            .andExpect(jsonPath("$.data[0].status").value("INSTALLED"))
            .andExpect(jsonPath("$.data[0].dependencies[0].pluginId").value("alpha-plugin"))
            .andExpect(jsonPath("$.data[0].dependencies[0].minVersion").value("1.0.0"))
            .andExpect(jsonPath("$.data[0].dependencies[0].maxVersion").value("1.9.9"))
            .andExpect(jsonPath("$.data[0].dependencies[0].id").doesNotExist())
            .andExpect(jsonPath("$.data[0].dependencies[0].status").doesNotExist())
            .andExpect(jsonPath("$.data[0].dependencies[1].pluginId").value("zeta-plugin"))
            .andExpect(jsonPath("$.data[0].jarPath").doesNotExist())
            .andExpect(jsonPath("$.data[0].jarSha256").doesNotExist())
            .andExpect(jsonPath("$.data[0].signature").doesNotExist())
            .andExpect(jsonPath("$.data[0].signerId").doesNotExist())
            .andExpect(jsonPath("$.data[0].integrity").doesNotExist())
            .andExpect(jsonPath("$.data[0].errorMessage").doesNotExist());

        verify(service).list();
    }

    @Test
    void stateChangingRequestsWithoutCsrfShouldReturn403() throws Exception {
        mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(jarPart())
                .file(signaturePart())
                .param("signerId", "official")
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/plugins/{pluginId}", PLUGIN_ID)
                .with(authorizedAdmin()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void uploadShouldRequireAdminIdentityAndManagePermission() throws Exception {
        mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(jarPart())
                .file(signaturePart())
                .param("signerId", "official")
                .with(csrf().asHeader()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(jarPart())
                .file(signaturePart())
                .param("signerId", "official")
                .with(employee())
                .with(csrf().asHeader()))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(jarPart())
                .file(signaturePart())
                .param("signerId", "official")
                .with(admin(List.of()))
                .with(csrf().asHeader()))
            .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdminWithCsrfShouldUploadMultipartPlugin() throws Exception {
        when(service.upload(any(), any(), eq("official"), eq("Hello Plugin"), eq("测试插件")))
            .thenReturn(pluginResponse());

        mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(jarPart())
                .file(signaturePart())
                .param("signerId", "official")
                .param("name", "Hello Plugin")
                .param("description", "测试插件")
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(PLUGIN_ID))
            .andExpect(jsonPath("$.data.jarPath").doesNotExist())
            .andExpect(jsonPath("$.data.signature").doesNotExist());

        verify(service).upload(
            any(), any(), eq("official"), eq("Hello Plugin"), eq("测试插件"));
    }

    @Test
    void uploadShouldRejectNonMultipartContentTypeBeforeService() throws Exception {
        mockMvc.perform(post("/api/admin/plugins/upload")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("请求内容类型不支持"));

        verifyNoInteractions(service);
    }

    @Test
    void authorizedAdminWithCsrfShouldInvokeLifecycleOperations() throws Exception {
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/enable", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/admin/plugins/{pluginId}/disable", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/plugins/{pluginId}", PLUGIN_ID)
                .with(authorizedAdmin())
                .with(csrf().asHeader()))
            .andExpect(status().isOk());

        verify(service).enable(PLUGIN_ID);
        verify(service).disable(PLUGIN_ID);
        verify(service).uninstall(PLUGIN_ID);
    }

    private AdminPluginResponse pluginResponse() {
        LocalDateTime installedAt = LocalDateTime.of(2026, 7, 30, 10, 0);
        return new AdminPluginResponse(
            PLUGIN_ID,
            "Hello Plugin",
            "测试插件",
            "1.0.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            PluginStatus.INSTALLED,
            null,
            "Workforce Hub",
            List.of(
                new DependencyResponse("zeta-plugin", "2.0.0", null),
                new DependencyResponse("alpha-plugin", "1.0.0", "1.9.9")
            ),
            installedAt,
            installedAt
        );
    }

    private RequestPostProcessor authorizedAdmin() {
        return admin(List.of(MANAGE_PERMISSION));
    }

    private RequestPostProcessor admin(List<String> permissions) {
        AdminUserDetails principal = new AdminUserDetails(
            1L,
            "plugin.admin",
            "password",
            "插件管理员",
            "ACTIVE",
            List.of(),
            permissions
        );
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        );
        return authentication(authentication);
    }

    private RequestPostProcessor employee() {
        WfhUserDetails principal = org.mockito.Mockito.mock(WfhUserDetails.class);
        when(principal.getUserType()).thenReturn(UserType.USER);
        when(principal.getUserId()).thenReturn(2L);
        when(principal.getUsername()).thenReturn("employee");
        when(principal.getAuthorities()).thenReturn(List.of());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        );
        return authentication(authentication);
    }

    private MockMultipartFile jarPart() {
        return new MockMultipartFile(
            "jar",
            "hello-plugin.jar",
            "application/java-archive",
            "jar-content".getBytes(StandardCharsets.UTF_8)
        );
    }

    private MockMultipartFile signaturePart() {
        return new MockMultipartFile(
            "signature",
            "hello-plugin.sig",
            "application/octet-stream",
            new byte[256]
        );
    }
}
WFH_99_2_BASELINE_8
mkdir -p "$target/server/src/test/java/com/workforcehub/plugin"
cat > "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewaySecurityTest.java" <<'WFH_99_2_BASELINE_9'
package com.workforcehub.plugin;

import com.workforcehub.auth.security.AdminPermissionFilter;
import com.workforcehub.auth.security.AdminUserDetailsService;
import com.workforcehub.auth.security.EmployeeUserDetailsService;
import com.workforcehub.auth.security.UserTypePathFilter;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.config.SecurityConfig;
import com.workforcehub.common.enums.UserType;
import com.workforcehub.plugin.controller.PluginGatewayController;
import com.workforcehub.plugin.controller.PluginGatewayRequestSizeFilter;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginActionIdempotencyService;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginParamValidator;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.ErrorCodes;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.InvocationContextBinder;
import com.workforcehub.plugin.sdk.ParamSchema;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 插件网关安全测试（MockMvc）。
 * 覆盖：匿名401、权限缺失403、插件不存在404、动作不存在404、未启用409、参数缺失400。
 */
@WebMvcTest(PluginGatewayController.class)
@TestPropertySource(properties =
    "workforce.plugin.gateway.csrf.allowed-origins=http://localhost:5174,http://127.0.0.1:5174,"
        + "https://app.example.com,http://standard.example,https://secure.example")
@ContextConfiguration(classes = {
    PluginGatewayController.class,
    SecurityConfig.class,
    UserTypePathFilter.class,
    AdminPermissionFilter.class,
    InvocationContextBinder.class,
    PluginParamValidator.class,
    PluginLifecycleGuard.class,
    PluginGatewayRequestSizeFilter.class
})
class PluginGatewaySecurityTest {

    private static final String PLUGIN_ID = "hello-plugin";
    private static final String ACTION_CODE = "hello.greet";
    private static final String TRUSTED_ORIGIN = "http://localhost:5174";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PluginManagerService pluginManagerService;

    @MockBean
    private PluginPermissionService permissionService;

    @MockBean
    private PluginActionIdempotencyService idempotencyService;

    @MockBean(name = "employeeUserDetailsService")
    private EmployeeUserDetailsService employeeUserDetailsService;

    @MockBean(name = "adminUserDetailsService")
    private AdminUserDetailsService adminUserDetailsService;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    private PluginActionHandler handler;

    @BeforeEach
    void setUp() {
        // 每个测试前 enable 插件，使 guard 有 slot
        lifecycleGuard.enable(PLUGIN_ID);

        PluginRow plugin = new PluginRow();
        plugin.setPluginId(PLUGIN_ID);
        plugin.setStatus(PluginStatus.ACTIVE);

        handler = mock(PluginActionHandler.class);
        ActionDescriptor descriptor = new ActionDescriptor(
            ACTION_CODE,
            "问候",
            "返回问候语",
            Map.of("name", ParamSchema.string("姓名", true)),
            Map.of(),
            "PLUGIN_HELLO",
            Duration.ofSeconds(5),
            true
        );

        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(descriptor);
        when(handler.execute(any(InvocationContext.class), anyMap()))
            .thenReturn(ServiceResult.ok(Map.of("message", "Hello")));
        when(idempotencyService.execute(
                anyString(),
                anyString(),
                anyString(),
                any(InvocationContext.class),
                anyMap(),
                any(PluginActionHandler.class)))
            .thenAnswer(invocation -> invocation.<PluginActionHandler>getArgument(5).execute(
                invocation.getArgument(3),
                invocation.getArgument(4)
            ));
    }

    @Test
    void shouldReturn401WhenAnonymous() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isUnauthorized());

        verifyGatewayNotEntered();
    }

    @Test
    void shouldAcceptTrustedOrigin() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authenticatedPrincipal(UserType.ADMIN, 1L)))
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldAcceptTrustedRefererWithPath() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authenticatedPrincipal(UserType.ADMIN, 1L)))
                .header(HttpHeaders.REFERER, TRUSTED_ORIGIN + "/messages/42")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://standard.example:80", "https://secure.example:443"})
    void shouldNormalizeStandardOriginPorts(String origin) throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authenticatedPrincipal(UserType.ADMIN, 1L)))
                .header(HttpHeaders.ORIGIN, origin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectAuthenticatedRequestWithoutOriginOrReferer() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authenticatedPrincipal(UserType.ADMIN, 1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verifyGatewayNotEntered();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("untrustedRequestSources")
    void shouldRejectUntrustedRequestBeforePluginLookup(
            String caseName, String origin, String referer) throws Exception {
        var request = gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authenticatedPrincipal(UserType.ADMIN, 1L)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}");
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        if (referer != null) {
            request.header(HttpHeaders.REFERER, referer);
        }

        mockMvc.perform(request)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verifyGatewayNotEntered();
    }

    @Test
    void shouldReturn403ForAuthenticatedNonWorkforcePrincipal() throws Exception {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            "admin", null, List.of());

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authentication(authentication))
                .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    @Test
    void shouldReturn404WhenPluginNotFound() throws Exception {
        when(pluginManagerService.getPlugin("missing"))
            .thenThrow(new NoSuchElementException("插件不存在"));

        mockMvc.perform(gatewayRequest("missing", ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404));
    }

    @ParameterizedTest
    @EnumSource(value = PluginStatus.class, names = {
        "INSTALLED", "STOPPED", "ERROR", "DISABLING", "UNINSTALLED"
    })
    void shouldReturn409WhenPluginNotActive(PluginStatus status) throws Exception {
        PluginRow plugin = new PluginRow();
        plugin.setPluginId(PLUGIN_ID);
        plugin.setStatus(status);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin);

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409));
    }

    @Test
    void shouldReturn404WhenHostPermissionAllowsButActionHandlerMissing() throws Exception {
        doNothing().when(permissionService)
            .checkPermission(any(), eq(PLUGIN_ID), eq("missing.action"));
        when(pluginManagerService.findActionHandler(PLUGIN_ID, "missing.action"))
            .thenReturn(Optional.empty());

        mockMvc.perform(gatewayRequest(PLUGIN_ID, "missing.action")
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404));

        verify(permissionService).checkPermission(any(), eq(PLUGIN_ID), eq("missing.action"));
        verify(pluginManagerService).findActionHandler(PLUGIN_ID, "missing.action");
    }

    @Test
    void shouldReturn403WhenHostPermissionDenied() throws Exception {
        doThrow(new PluginPermissionService.PermissionDeniedException("动作未授权"))
            .when(permissionService).checkPermission(any(), eq(PLUGIN_ID), eq(ACTION_CODE));

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verify(pluginManagerService, never()).getPlugin(PLUGIN_ID);
        verify(pluginManagerService, org.mockito.Mockito.never())
            .findActionHandler(PLUGIN_ID, ACTION_CODE);
        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @Test
    void shouldRejectOversizedRequestInRealFilterChain() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + "x".repeat(256 * 1024) + "\"}"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value(1000));

        verifyGatewayNotEntered();
    }

    @Test
    void shouldReturn400WhenMissingRequiredParam() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400));

        verify(permissionService).checkPermission(any(), eq(PLUGIN_ID), eq(ACTION_CODE));
        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @Test
    void shouldValidateSchemaBeforeMissingIdempotencyKey() throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("缺少必填参数: name"));

        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @Test
    void shouldRequireIdempotencyKeyForValidAction() throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1000));

        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"contains space", "slash/value", "comma,value"})
    void shouldRejectUnsafeIdempotencyKey(String key) throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .header("Idempotency-Key", key)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1000));

        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @Test
    void shouldRejectDuplicateIdempotencyHeaders() throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .header("Idempotency-Key", "key-a", "key-b")
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void shouldRejectTooLongIdempotencyKey() throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .header("Idempotency-Key", "k".repeat(129))
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(1000));
    }

    @Test
    void shouldAccept128CharacterIdempotencyKey() throws Exception {
        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .header("Idempotency-Key", "k".repeat(128))
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void shouldRejectNonIdempotentHttpAction() throws Exception {
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "问候",
            "返回问候语",
            Map.of("name", ParamSchema.string("姓名", true)),
            Map.of(),
            "PLUGIN_HELLO",
            Duration.ofSeconds(5),
            false
        ));

        mockMvc.perform(gatewayRequestWithoutIdempotencyKey(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(3002));

        verify(handler, never()).execute(any(), anyMap());
        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
    }

    @Test
    void shouldReturnRetryable409WhenSameRequestIsInProgress() throws Exception {
        doThrow(new PluginActionIdempotencyService.ActionInProgressException())
            .when(idempotencyService).execute(
                anyString(), anyString(), anyString(), any(), anyMap(), any());

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(HttpHeaders.RETRY_AFTER, "1"))
            .andExpect(jsonPath("$.code").value(3002));
    }

    @Test
    void shouldReturn409WhenIdempotencyKeyHasDifferentRequest() throws Exception {
        doThrow(new PluginActionIdempotencyService.IdempotencyConflictException())
            .when(idempotencyService).execute(
                anyString(), anyString(), anyString(), any(), anyMap(), any());

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(3001));
    }

    @Test
    void shouldReturn400ForMalformedJson() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("请求参数格式错误"));
    }

    @Test
    void shouldReturn500WithoutLeakingInternalMessage() throws Exception {
        when(pluginManagerService.getPlugin(PLUGIN_ID))
            .thenThrow(new IllegalStateException("internal detail"));

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(100500))
            .andExpect(jsonPath("$.message").value("服务内部错误"));
    }

    @Test
    void shouldReturn500AndRestoreThreadStateWhenDescriptorThrowsError() throws Exception {
        Thread requestThread = Thread.currentThread();
        ClassLoader original = requestThread.getContextClassLoader();
        ClassLoader pluginClassLoader = new ClassLoader(original) { };
        when(handler.getDescriptor()).thenAnswer(invocation -> {
            requestThread.setContextClassLoader(pluginClassLoader);
            requestThread.interrupt();
            throw new AssertionError("descriptor failure");
        });

        try {
            mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                    .with(authenticated(UserType.ADMIN, 1L))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"test\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(100500))
                .andExpect(jsonPath("$.message").value("动作执行失败"));

            assertSame(original, requestThread.getContextClassLoader());
            assertFalse(requestThread.isInterrupted());
            assertEquals(0, lifecycleGuard.getActiveCount(PLUGIN_ID));
        } finally {
            requestThread.setContextClassLoader(original);
            Thread.interrupted();
        }
    }

    @Test
    void shouldBindAuthoritativeAdminContext() throws Exception {
        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 42L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(permissionService).checkPermission(
            org.mockito.ArgumentMatchers.argThat(ctx ->
                ctx.getUserId().equals(42L)
                    && ctx.getCallerType() == InvocationContext.CallerType.ADMIN
                    && ctx.getPluginId().equals(PLUGIN_ID)),
            eq(PLUGIN_ID),
            eq(ACTION_CODE)
        );
    }

    @Test
    void shouldReturn409WhenGuardClosedAndHandlerNotExecuted() throws Exception {
        // 关门
        lifecycleGuard.beginDisable(PLUGIN_ID);

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409));

        // handler 不应被调用
        verify(handler, org.mockito.Mockito.never()).execute(any(), anyMap());
    }

    @Test
    void shouldReleaseGuardWhenHandlerThrows() throws Exception {
        when(handler.execute(any(InvocationContext.class), anyMap()))
            .thenThrow(new RuntimeException("handler boom"));

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(100500));

        // guard 应释放，activeCount 回到 0
        assertEquals(0, lifecycleGuard.getActiveCount(PLUGIN_ID));
    }

    @Test
    void shouldCompleteAcquiredRequestEvenAfterConcurrentDisable() throws Exception {
        // acquire 成功后，即使并发关门/状态进入 DISABLING，已在途请求仍完成
        // 网关 acquire 前检查 ACTIVE，acquire 后 guard 是权威

        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch handlerFinish = new CountDownLatch(1);
        when(handler.execute(any(InvocationContext.class), anyMap()))
            .thenAnswer(inv -> {
                handlerStarted.countDown();
                assertTrue(handlerFinish.await(5, TimeUnit.SECONDS), "handlerFinish 应在 5 秒内到达");
                return ServiceResult.ok(Map.of("message", "done"));
            });

        CompletableFuture<MvcResult> resultFuture =
            CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                        .with(authenticated(UserType.ADMIN, 1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"test\"}"))
                    .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(handlerStarted.await(2, TimeUnit.SECONDS), "handler 应在 2 秒内开始");

        lifecycleGuard.beginDisable(PLUGIN_ID);

        mockMvc.perform(gatewayRequest(PLUGIN_ID, ACTION_CODE)
                .with(authenticated(UserType.ADMIN, 1L))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"test\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409));

        handlerFinish.countDown();

        var result = resultFuture.get(5, TimeUnit.SECONDS);
        assertEquals(200, result.getResponse().getStatus());
    }

    private MockHttpServletRequestBuilder gatewayRequest(String pluginId, String actionCode) {
        return gatewayRequestWithoutIdempotencyKey(pluginId, actionCode)
            .header("Idempotency-Key", "gateway-test-key");
    }

    private MockHttpServletRequestBuilder gatewayRequestWithoutIdempotencyKey(String pluginId, String actionCode) {
        return post("/api/plugin-gateway/" + pluginId + "/actions/" + actionCode)
            .with(csrf().asHeader());
    }

    private static Stream<Arguments> untrustedRequestSources() {
        return Stream.of(
            Arguments.of("Origin null", "null", null),
            Arguments.of("相似域名前缀", "https://app.example.com.evil.test", null),
            Arguments.of("端口不匹配", "http://localhost:5173", null),
            Arguments.of("非法 URI", TRUSTED_ORIGIN + "/%", null),
            Arguments.of("非 HTTP(S)", "ftp://localhost:5174", null),
            Arguments.of("userinfo", "http://user@localhost:5174", null),
            Arguments.of("Origin 携带路径", TRUSTED_ORIGIN + "/messages", null),
            Arguments.of("Origin 尾斜杠", TRUSTED_ORIGIN + "/", null),
            Arguments.of("Origin 携带查询", TRUSTED_ORIGIN + "?next=/messages", null),
            Arguments.of("恶意 Referer", null, "https://app.example.com.evil.test/messages"),
            Arguments.of("非法 Referer", null, "not a uri"),
            Arguments.of("Origin 优先于 Referer", "https://attacker.example", TRUSTED_ORIGIN + "/messages")
        );
    }

    private void verifyGatewayNotEntered() {
        verify(pluginManagerService, never()).getPlugin(anyString());
        verify(pluginManagerService, never()).findActionHandler(anyString(), anyString());
        verify(permissionService, never()).checkPermission(any(), anyString(), anyString());
        verify(idempotencyService, never()).execute(anyString(), anyString(), anyString(), any(), anyMap(), any());
        verify(handler, never()).execute(any(), anyMap());
        assertEquals(0, lifecycleGuard.getActiveCount(PLUGIN_ID));
    }

    private RequestPostProcessor authenticated(UserType userType, Long userId) {
        return request -> {
            authentication(authenticatedPrincipal(userType, userId)).postProcessRequest(request);
            request.addHeader(HttpHeaders.ORIGIN, TRUSTED_ORIGIN);
            return request;
        };
    }

    private Authentication authenticatedPrincipal(UserType userType, Long userId) {
        WfhUserDetails principal = mock(WfhUserDetails.class);
        when(principal.getUserType()).thenReturn(userType);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("test-user");
        when(principal.getAuthorities()).thenReturn(List.of());

        return UsernamePasswordAuthenticationToken.authenticated(
            principal, null, principal.getAuthorities());
    }
}
WFH_99_2_BASELINE_9
mkdir -p "$target/server/src/test/java/com/workforcehub/attendance"
cat > "$target/server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java" <<'WFH_99_2_BASELINE_10'
package com.workforcehub.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.plugin.runtime.PluginEventDispatcher;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.support.FixedClockTestConfiguration;
import com.workforcehub.support.LocalIntegrationTestSupport;
import com.workforcehub.support.MutableTestClock;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-attendance 第一版考勤接口自动化冒烟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import(FixedClockTestConfiguration.class)
class ModuleAttendanceApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableTestClock mutableTestClock;

    @Autowired
    private AttendanceService attendanceRecalculationService;

    @MockBean
    private PluginEventDispatcher pluginEventDispatcher;

    @Test
    @DisplayName("后台配置考勤基础数据后，员工应能打卡并生成正常日结果，直属上级应能查询团队结果")
    void employeePunchShouldCreateNormalDailyResultAndManagerShouldQueryTeamResult() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie managerSession = loginEmployee("lisi.manager");
        String suffix = uniqueSuffix();

        long calendarId = createCalendar(adminSession, "CAL_" + suffix, "标准日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_" + suffix, "标准班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_" + suffix, "研发考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.userId", EMPLOYEE_USER_ID))
            .andExpect(jsonLong("$.data.groupId", groupId));

        punch(employeeSession, "2026-05-07T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.userId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.punchType").value("ON_DUTY"));
        punch(employeeSession, "2026-05-07T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(false))
            .andExpect(jsonPath("$.data.dailyResult.workMinutes").value(540));

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].userId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].workDate").value("2026-05-07"))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(false));

        mockMvc.perform(get("/api/employee/attendance/team/daily-results")
                .cookie(managerSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID))
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].userId", EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"));

        assertEquals(2L, punchCount(EMPLOYEE_USER_ID, "2026-05-07"));
        assertEquals(1L, dailyResultCount(EMPLOYEE_USER_ID, "2026-05-07"));
    }

    @Test
    @DisplayName("后台应能查询考勤配置、排班和日结果")
    void adminShouldListAttendanceGovernanceData() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();

        long calendarId = createCalendar(adminSession, "CAL_LIST_" + suffix, "查询日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_LIST_" + suffix, "查询班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_LIST_" + suffix, "查询考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        MvcResult scheduleResult = createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-17", shiftId, calendarId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long scheduleId = data(scheduleResult).get("scheduleId").asLong();

        punch(employeeSession, "2026-05-17T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-17T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/admin/attendance/calendars").cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.calendarId==%d)]".formatted(calendarId), hasSize(1)));
        mockMvc.perform(get("/api/admin/attendance/shifts").cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.shiftId==%d)]".formatted(shiftId), hasSize(1)));
        mockMvc.perform(get("/api/admin/attendance/groups").cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.groupId==%d)]".formatted(groupId), hasSize(1)));
        mockMvc.perform(get("/api/admin/attendance/groups/{groupId}/members", groupId).cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].userId").value(EMPLOYEE_USER_ID));
        mockMvc.perform(get("/api/admin/attendance/schedules")
                .cookie(adminSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID))
                .param("startDate", "2026-05-17")
                .param("endDate", "2026-05-17"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].scheduleId").value(scheduleId));
        mockMvc.perform(get("/api/admin/attendance/daily-results")
                .cookie(adminSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID))
                .param("startDate", "2026-05-17")
                .param("endDate", "2026-05-17"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"));
    }

    @Test
    @DisplayName("同一员工考勤组生效区间重叠时应拒绝写入")
    void overlappingGroupMembershipShouldBeRejected() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_OV_" + suffix, "重叠日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_OV_" + suffix, "重叠班次-" + suffix);
        long firstGroupId = createGroup(adminSession, "GROUP_A_" + suffix, "考勤组A-" + suffix, calendarId, shiftId);
        long secondGroupId = createGroup(adminSession, "GROUP_B_" + suffix, "考勤组B-" + suffix, calendarId, shiftId);

        addGroupMember(adminSession, firstGroupId, EMPLOYEE_USER_ID, "2026-05-01", "2026-05-31")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        addGroupMember(adminSession, secondGroupId, EMPLOYEE_USER_ID, "2026-05-15", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("员工考勤组生效区间重叠"));

        assertEquals(1L, activeGroupMemberCount(EMPLOYEE_USER_ID));
    }

    @Test
    @DisplayName("并发添加同一员工重叠考勤组成员时也只能成功一条")
    void concurrentOverlappingGroupMembershipShouldAllowOnlyOneActiveRecord() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_CON_" + suffix, "并发日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_CON_" + suffix, "并发班次-" + suffix);
        List<Long> groupIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            groupIds.add(createGroup(
                adminSession,
                "GROUP_CON_" + i + "_" + suffix,
                "并发考勤组" + i + "-" + suffix,
                calendarId,
                shiftId
            ));
        }

        CountDownLatch ready = new CountDownLatch(groupIds.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(groupIds.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Long groupId : groupIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "并发测试启动超时");
                    MvcResult result = addGroupMember(
                        adminSession,
                        groupId,
                        EMPLOYEE_USER_ID,
                        "2026-06-01",
                        null
                    )
                        .andReturn();
                    int responseStatus = result.getResponse().getStatus();
                    assertTrue(responseStatus == 200 || responseStatus == 409);
                    return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发测试准备超时");
            start.countDown();

            long successCount = 0L;
            for (Future<Integer> future : futures) {
                if (future.get(10, TimeUnit.SECONDS) == 0) {
                    successCount++;
                }
            }

            assertEquals(1L, successCount);
            assertEquals(1L, activeGroupMemberCount(EMPLOYEE_USER_ID));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("无考勤组并发重算日结果时也只能生成一条NO_RULE结果")
    void concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow() throws Exception {
        int workerCount = 8;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "并发重算启动超时");
                    attendanceRecalculationService.recalculateDailyResultForExternalEvent(
                        EMPLOYEE_USER_ID,
                        java.time.LocalDate.parse("2026-07-01")
                    );
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发重算准备超时");
            start.countDown();

            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertEquals(1L, dailyResultCount(EMPLOYEE_USER_ID, "2026-07-01"));
            assertEquals("NO_RULE", dailyResultStatus(EMPLOYEE_USER_ID, "2026-07-01"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("迟到和早退打卡应生成异常日结果与待处理异常")
    void lateAndEarlyPunchShouldCreateAbnormalDailyResultAndExceptions() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_EX_" + suffix, "异常日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_EX_" + suffix, "异常班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_EX_" + suffix, "异常考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-08T09:20:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-08T17:40:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.lateMinutes").value(20))
            .andExpect(jsonPath("$.data.dailyResult.earlyLeaveMinutes").value(20))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(true));

        mockMvc.perform(get("/api/employee/attendance/exceptions")
                .cookie(employeeSession)
                .param("startDate", "2026-05-08")
                .param("endDate", "2026-05-08"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[?(@.exceptionType=='LATE')]", hasSize(1)))
            .andExpect(jsonPath("$.data[?(@.exceptionType=='EARLY_LEAVE')]", hasSize(1)));

        assertEquals(2L, exceptionCount(EMPLOYEE_USER_ID, "2026-05-08"));
    }

    @Test
    @DisplayName("重复重算和异常说明变化应保留异常ID且不重复发布事件")
    void recalculationShouldPreserveExceptionIdentityAndPublishNoDuplicateEvent() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_STABLE_" + suffix, "稳定异常日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_STABLE_" + suffix, "稳定异常班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_STABLE_" + suffix, "稳定异常组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-20T09:20:00", "ON_DUTY")
            .andExpect(status().isOk());
        punch(employeeSession, "2026-05-20T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk());

        long initialExceptionId = exceptionId(EMPLOYEE_USER_ID, "2026-05-20", "LATE");
        clearInvocations(pluginEventDispatcher);
        attendanceRecalculationService.recalculateDailyResultForExternalEvent(
            EMPLOYEE_USER_ID,
            java.time.LocalDate.parse("2026-05-20")
        );
        attendanceRecalculationService.recalculateDailyResultForExternalEvent(
            EMPLOYEE_USER_ID,
            java.time.LocalDate.parse("2026-05-20")
        );

        assertEquals(initialExceptionId, exceptionId(EMPLOYEE_USER_ID, "2026-05-20", "LATE"));
        assertEquals("迟到20分钟", exceptionDescription(EMPLOYEE_USER_ID, "2026-05-20", "LATE"));
        verifyNoInteractions(pluginEventDispatcher);

        jdbcTemplate.update("""
            UPDATE att_punch_record
            SET punch_at = ?
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = 'ON_DUTY'
            """,
            Timestamp.valueOf("2026-05-20 09:30:00"),
            EMPLOYEE_USER_ID,
            "2026-05-20"
        );
        attendanceRecalculationService.recalculateDailyResultForExternalEvent(
            EMPLOYEE_USER_ID,
            java.time.LocalDate.parse("2026-05-20")
        );

        assertEquals(initialExceptionId, exceptionId(EMPLOYEE_USER_ID, "2026-05-20", "LATE"));
        assertEquals("迟到30分钟", exceptionDescription(EMPLOYEE_USER_ID, "2026-05-20", "LATE"));
        verifyNoInteractions(pluginEventDispatcher);
    }

    @Test
    @DisplayName("异常消失后再次出现应生成新ID并发布精确白名单事件")
    void reappearingExceptionShouldCreateNewIdentityAndEvent() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_REAPPEAR_" + suffix, "重现异常日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_REAPPEAR_" + suffix, "重现异常班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_REAPPEAR_" + suffix, "重现异常组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-21T09:20:00", "ON_DUTY")
            .andExpect(status().isOk());
        punch(employeeSession, "2026-05-21T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk());
        long initialExceptionId = exceptionId(EMPLOYEE_USER_ID, "2026-05-21", "LATE");

        jdbcTemplate.update("""
            UPDATE att_punch_record
            SET punch_at = ?
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = 'ON_DUTY'
            """,
            Timestamp.valueOf("2026-05-21 09:00:00"),
            EMPLOYEE_USER_ID,
            "2026-05-21"
        );
        attendanceRecalculationService.recalculateDailyResultForExternalEvent(
            EMPLOYEE_USER_ID,
            java.time.LocalDate.parse("2026-05-21")
        );
        assertEquals(0L, exceptionTypeCount(EMPLOYEE_USER_ID, "2026-05-21", "LATE"));

        clearInvocations(pluginEventDispatcher);
        jdbcTemplate.update("""
            UPDATE att_punch_record
            SET punch_at = ?
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = 'ON_DUTY'
            """,
            Timestamp.valueOf("2026-05-21 09:20:00"),
            EMPLOYEE_USER_ID,
            "2026-05-21"
        );
        attendanceRecalculationService.recalculateDailyResultForExternalEvent(
            EMPLOYEE_USER_ID,
            java.time.LocalDate.parse("2026-05-21")
        );

        long newExceptionId = exceptionId(EMPLOYEE_USER_ID, "2026-05-21", "LATE");
        assertNotEquals(initialExceptionId, newExceptionId);
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(pluginEventDispatcher).dispatch(eventCaptor.capture());
        DomainEvent event = eventCaptor.getValue();
        String stableId = "attendance-exception-" + newExceptionId;
        assertEquals(EventTypes.ATTENDANCE_EXCEPTION, event.eventType());
        assertEquals(stableId, event.eventId());
        assertEquals(stableId, event.idempotencyKey());
        assertEquals("system", event.triggeredBy());
        assertEquals(Set.of(
            "exceptionId",
            "dailyResultId",
            "userId",
            "workDate",
            "exceptionType",
            "exceptionDesc",
            "status"
        ), event.payload().keySet());
        assertEquals(newExceptionId, event.payload().get("exceptionId"));
        assertEquals(dailyResultId(EMPLOYEE_USER_ID, "2026-05-21"), event.payload().get("dailyResultId"));
        assertEquals(EMPLOYEE_USER_ID, event.payload().get("userId"));
        assertEquals("2026-05-21", event.payload().get("workDate"));
        assertEquals("LATE", event.payload().get("exceptionType"));
        assertEquals("迟到20分钟", event.payload().get("exceptionDesc"));
        assertEquals("PENDING", event.payload().get("status"));
    }

    @Test
    @DisplayName("同一日结果并发重算只能新建并发布一个同类异常")
    void concurrentRecalculationShouldCreateAndPublishOneExceptionPerType() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_EVENT_CON_" + suffix, "并发异常日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_EVENT_CON_" + suffix, "并发异常班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_EVENT_CON_" + suffix, "并发异常组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-22T09:20:00", "ON_DUTY")
            .andExpect(status().isOk());
        punch(employeeSession, "2026-05-22T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk());

        jdbcTemplate.update("""
            DELETE ae
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ? AND adr.work_date = ?
            """, EMPLOYEE_USER_ID, "2026-05-22");
        jdbcTemplate.update(
            "DELETE FROM att_daily_result WHERE user_id = ? AND work_date = ?",
            EMPLOYEE_USER_ID,
            "2026-05-22"
        );
        clearInvocations(pluginEventDispatcher);

        int workerCount = 6;
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (int i = 0; i < workerCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "并发重算启动超时");
                    attendanceRecalculationService.recalculateDailyResultForExternalEvent(
                        EMPLOYEE_USER_ID,
                        java.time.LocalDate.parse("2026-05-22")
                    );
                    return null;
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发重算准备超时");
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1L, dailyResultCount(EMPLOYEE_USER_ID, "2026-05-22"));
        assertEquals(1L, exceptionTypeCount(EMPLOYEE_USER_ID, "2026-05-22", "LATE"));
        verify(pluginEventDispatcher, times(1)).dispatch(org.mockito.ArgumentMatchers.any(DomainEvent.class));
    }

    @Test
    @DisplayName("管理员排班后，日结果计算应优先使用排班班次而不是考勤组默认班次")
    void scheduleShouldOverrideDefaultGroupShiftWhenCalculatingDailyResult() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_SCH_" + suffix, "排班日历-" + suffix);
        long defaultShiftId = createShift(adminSession, "SHIFT_DEF_" + suffix, "默认班次-" + suffix);
        long scheduledShiftId = createShift(
            adminSession,
            "SHIFT_SCH_" + suffix,
            "排班班次-" + suffix,
            "10:00:00",
            "19:00:00"
        );
        long groupId = createGroup(adminSession, "GROUP_SCH_" + suffix, "排班考勤组-" + suffix, calendarId, defaultShiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        MvcResult scheduleResult = mockMvc.perform(post("/api/admin/attendance/schedules")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": %d,
                      "workDate": "2026-05-10",
                      "shiftId": %d,
                      "calendarId": %d,
                      "scheduleSource": "MANUAL"
                    }
                    """.formatted(EMPLOYEE_USER_ID, scheduledShiftId, calendarId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.scheduleId").isString())
            .andExpect(jsonLong("$.data.shiftId", scheduledShiftId))
            .andReturn();
        long scheduleId = data(scheduleResult).get("scheduleId").asLong();

        punch(employeeSession, "2026-05-10T10:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-10T19:00:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.dailyResult.scheduleId", scheduleId))
            .andExpect(jsonLong("$.data.dailyResult.shiftId", scheduledShiftId))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(false))
            .andExpect(jsonPath("$.data.dailyResult.workMinutes").value(540));

        assertEquals(1L, dailyResultCount(EMPLOYEE_USER_ID, "2026-05-10"));
    }

    @Test
    @DisplayName("同一员工同一天重复创建有效排班时应返回冲突")
    void duplicateActiveScheduleShouldBeRejected() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_DUP_SCH_" + suffix, "重复排班日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_DUP_SCH_" + suffix, "重复排班班次-" + suffix);

        createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-12", shiftId, calendarId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-12", shiftId, calendarId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("员工当天已存在排班，请通过修改原排班恢复或改班"));
    }

    @Test
    @DisplayName("已停用排班仍占用员工单日排班唯一模型，不能通过新增绕过")
    void inactiveScheduleShouldStillRejectCreateForSameUserDate() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_INACT_SCH_" + suffix, "停用排班日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_INACT_SCH_" + suffix, "停用排班班次-" + suffix);

        MvcResult scheduleResult = createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-16", shiftId, calendarId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long scheduleId = data(scheduleResult).get("scheduleId").asLong();

        mockMvc.perform(put("/api/admin/attendance/schedules/{scheduleId}", scheduleId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shiftId": %d,
                      "calendarId": %d,
                      "scheduleSource": "MANUAL",
                      "status": "INACTIVE"
                    }
                    """.formatted(shiftId, calendarId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-16", shiftId, calendarId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("员工当天已存在排班，请通过修改原排班恢复或改班"));

        assertEquals(1L, scheduleCount(EMPLOYEE_USER_ID, "2026-05-16"));
        assertEquals("INACTIVE", scheduleStatus(scheduleId));
    }

    @Test
    @DisplayName("管理员修改已有排班后，应重算已有日结果")
    void updatingScheduleShouldRecalculateExistingDailyResult() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_UP_SCH_" + suffix, "改排日历-" + suffix);
        long defaultShiftId = createShift(adminSession, "SHIFT_UP_DEF_" + suffix, "改排默认班次-" + suffix);
        long firstShiftId = createShift(adminSession, "SHIFT_UP_A_" + suffix, "改排班次A-" + suffix, "10:00:00", "19:00:00");
        long secondShiftId = createShift(adminSession, "SHIFT_UP_B_" + suffix, "改排班次B-" + suffix, "09:00:00", "18:00:00");
        long groupId = createGroup(adminSession, "GROUP_UP_SCH_" + suffix, "改排考勤组-" + suffix, calendarId, defaultShiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        MvcResult scheduleResult = createSchedule(adminSession, EMPLOYEE_USER_ID, "2026-05-13", firstShiftId, calendarId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        long scheduleId = data(scheduleResult).get("scheduleId").asLong();

        punch(employeeSession, "2026-05-13T10:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-13T19:00:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("NORMAL"))
            .andExpect(jsonLong("$.data.dailyResult.shiftId", firstShiftId));

        mockMvc.perform(put("/api/admin/attendance/schedules/{scheduleId}", scheduleId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shiftId": %d,
                      "calendarId": %d,
                      "scheduleSource": "MANUAL",
                      "status": "ACTIVE"
                    }
                    """.formatted(secondShiftId, calendarId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.shiftId", secondShiftId));

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-13")
                .param("endDate", "2026-05-13"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data[0].scheduleId", scheduleId))
            .andExpect(jsonLong("$.data[0].shiftId", secondShiftId))
            .andExpect(jsonPath("$.data[0].resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data[0].lateMinutes").value(60))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(true));
    }

    @Test
    @DisplayName("只有上班卡时应生成缺卡异常")
    void missingOffDutyPunchShouldCreateMissPunchException() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_MISS_" + suffix, "缺卡日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_MISS_" + suffix, "缺卡班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_MISS_" + suffix, "缺卡考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-11T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.absenceMinutes").value(270))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(true));

        mockMvc.perform(get("/api/employee/attendance/exceptions")
                .cookie(employeeSession)
                .param("startDate", "2026-05-11")
                .param("endDate", "2026-05-11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].exceptionType").value("MISS_PUNCH"));

        assertEquals(1L, exceptionCount(EMPLOYEE_USER_ID, "2026-05-11"));
    }

    @Test
    @DisplayName("员工登录后被离职时，旧会话也不能继续打卡")
    void offboardedEmployeeShouldNotPunchWithExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_OFF_" + suffix, "离职日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_OFF_" + suffix, "离职班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_OFF_" + suffix, "离职考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-09T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", EMPLOYEE_USER_ID)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "PENDING_OFFBOARD"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", EMPLOYEE_USER_ID)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "OFFBOARDED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "2026-05-09T18:00:00",
                      "punchType": "OFF_DUTY",
                      "sourceType": "PC"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("员工当前状态不允许打卡"));

        assertEquals(1L, punchCount(EMPLOYEE_USER_ID, "2026-05-09"));
    }

    @Test
    @DisplayName("员工实时打卡不应信任客户端传入的历史打卡时间")
    void employeePunchShouldUseServerReceivedTimeInsteadOfClientPunchAt() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        mutableTestClock.setTime(LocalDateTime.of(2026, 5, 14, 10, 30));
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_TRUST_" + suffix, "可信打卡日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_TRUST_" + suffix, "可信打卡班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_TRUST_" + suffix, "可信打卡考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "2026-05-07T09:00:00",
                      "punchType": "ON_DUTY",
                      "sourceType": "PC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.punchAt").value("2026-05-14T10:30:00"));

        assertEquals(0L, punchCount(EMPLOYEE_USER_ID, "2026-05-07"));
        assertEquals(1L, punchCount(EMPLOYEE_USER_ID, "2026-05-14"));
    }

    @Test
    @DisplayName("员工实时打卡不应要求客户端传入打卡时间")
    void employeePunchShouldAllowMissingClientPunchAt() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        mutableTestClock.setTime(LocalDateTime.of(2026, 5, 15, 9, 5));
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_NO_CLIENT_TIME_" + suffix, "无客户端时间日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_NO_CLIENT_TIME_" + suffix, "无客户端时间班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_NO_CLIENT_TIME_" + suffix, "无客户端时间考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchType": "ON_DUTY",
                      "sourceType": "PC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.punchAt").value("2026-05-15T09:05:00"));

        assertEquals(1L, punchCount(EMPLOYEE_USER_ID, "2026-05-15"));
    }

    @Test
    @DisplayName("员工同一天同类型重复打卡应返回冲突")
    void duplicateSameDayPunchTypeShouldBeRejected() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_DUP_PUNCH_" + suffix, "重复打卡日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_DUP_PUNCH_" + suffix, "重复打卡班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_DUP_PUNCH_" + suffix, "重复打卡考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-18T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-18T09:01:00", "ON_DUTY")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("当天同类型打卡已存在"));

        assertEquals(1L, punchCount(EMPLOYEE_USER_ID, "2026-05-18"));
    }

    @Test
    @DisplayName("员工实时打卡不应允许伪造受控来源")
    void employeePunchShouldRejectControlledManualSourceType() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "2026-05-07T09:00:00",
                      "punchType": "ON_DUTY",
                      "sourceType": "MANUAL"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("员工实时打卡来源仅支持 APP/PC"));
    }

    private long createCalendar(Cookie adminSession, String calendarCode, String calendarName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/calendars")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "calendarCode": "%s",
                      "calendarName": "%s",
                      "calendarType": "STANDARD",
                      "status": "ACTIVE"
                    }
                    """.formatted(calendarCode, calendarName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.calendarId").isString())
            .andReturn();
        return data(result).get("calendarId").asLong();
    }

    private long createShift(Cookie adminSession, String shiftCode, String shiftName) throws Exception {
        return createShift(adminSession, shiftCode, shiftName, "09:00:00", "18:00:00");
    }

    private long createShift(Cookie adminSession,
                             String shiftCode,
                             String shiftName,
                             String startTime,
                             String endTime) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/shifts")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shiftCode": "%s",
                      "shiftName": "%s",
                      "startTime": "%s",
                      "endTime": "%s",
                      "onDutyPunchStart": "08:00:00",
                      "onDutyPunchEnd": "10:00:00",
                      "offDutyPunchStart": "17:00:00",
                      "offDutyPunchEnd": "19:00:00",
                      "status": "ACTIVE"
                    }
                    """.formatted(shiftCode, shiftName, startTime, endTime)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.shiftId").isString())
            .andReturn();
        return data(result).get("shiftId").asLong();
    }

    private long createGroup(Cookie adminSession,
                             String groupCode,
                             String groupName,
                             long calendarId,
                             long shiftId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/groups")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "groupCode": "%s",
                      "groupName": "%s",
                      "calendarId": %d,
                      "defaultShiftId": %d,
                      "lateGraceMinutes": 0,
                      "earlyLeaveGraceMinutes": 0,
                      "status": "ACTIVE"
                    }
                    """.formatted(groupCode, groupName, calendarId, shiftId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.groupId").isString())
            .andReturn();
        return data(result).get("groupId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions addGroupMember(Cookie adminSession,
                                                                              long groupId,
                                                                              long userId,
                                                                              String effectiveFrom,
                                                                              String effectiveTo) throws Exception {
        String effectiveToField = effectiveTo == null ? "" : "\"effectiveTo\": \"%s\",".formatted(effectiveTo);
        return mockMvc.perform(post("/api/admin/attendance/groups/{groupId}/members", groupId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": %d,
                      "effectiveFrom": "%s",
                      %s
                      "sourceType": "MANUAL"
                    }
                    """.formatted(userId, effectiveFrom, effectiveToField)));
    }

    private org.springframework.test.web.servlet.ResultActions punch(Cookie employeeSession,
                                                                     String punchAt,
                                                                     String punchType) throws Exception {
        mutableTestClock.setTime(LocalDateTime.parse(punchAt));
        return mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "%s",
                      "punchType": "%s",
                      "sourceType": "PC",
                      "locationText": "杭州总部",
                      "ipAddress": "127.0.0.1",
                      "deviceId": "test-device"
                    }
                    """.formatted(punchAt, punchType)));
    }

    private org.springframework.test.web.servlet.ResultActions createSchedule(Cookie adminSession,
                                                                              long userId,
                                                                              String workDate,
                                                                              long shiftId,
                                                                              long calendarId) throws Exception {
        return mockMvc.perform(post("/api/admin/attendance/schedules")
            .cookie(adminSession)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "userId": %d,
                  "workDate": "%s",
                  "shiftId": %d,
                  "calendarId": %d,
                  "scheduleSource": "MANUAL"
                }
                """.formatted(userId, workDate, shiftId, calendarId)));
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "后台登录后应返回 SESSION");
        return session;
    }

    private Cookie loginEmployee(String loginName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "Employee@123456"
                    }
                    """.formatted(loginName)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "员工登录后应返回 SESSION");
        return session;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String uniqueSuffix() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private long punchCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_punch_record
            WHERE user_id = ?
              AND DATE(punch_at) = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private long dailyResultCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_daily_result
            WHERE user_id = ?
              AND work_date = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private long dailyResultId(long userId, String workDate) {
        Long id = jdbcTemplate.queryForObject("""
            SELECT id
            FROM att_daily_result
            WHERE user_id = ? AND work_date = ?
            """, Long.class, userId, workDate);
        return id == null ? 0L : id;
    }

    private String dailyResultStatus(long userId, String workDate) {
        return jdbcTemplate.queryForObject("""
            SELECT result_status
            FROM att_daily_result
            WHERE user_id = ?
              AND work_date = ?
            """, String.class, userId, workDate);
    }

    private long scheduleCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_schedule
            WHERE user_id = ?
              AND work_date = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private String scheduleStatus(long scheduleId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM att_schedule WHERE id = ?",
            String.class,
            scheduleId
        );
    }

    private long activeGroupMemberCount(long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_group_member
            WHERE user_id = ?
              AND status = 'ACTIVE'
            """, Long.class, userId);
        return count == null ? 0L : count;
    }

    private long exceptionCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private long exceptionId(long userId, String workDate, String exceptionType) {
        Long id = jdbcTemplate.queryForObject("""
            SELECT ae.id
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
              AND ae.exception_type = ?
            """, Long.class, userId, workDate, exceptionType);
        return id == null ? 0L : id;
    }

    private String exceptionDescription(long userId, String workDate, String exceptionType) {
        return jdbcTemplate.queryForObject("""
            SELECT ae.exception_desc
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
              AND ae.exception_type = ?
            """, String.class, userId, workDate, exceptionType);
    }

    private long exceptionTypeCount(long userId, String workDate, String exceptionType) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
              AND ae.exception_type = ?
            """, Long.class, userId, workDate, exceptionType);
        return count == null ? 0L : count;
    }
}
WFH_99_2_BASELINE_10
mkdir -p "$target/server/src/test/java/com/workforcehub/plugin"
cat > "$target/server/src/test/java/com/workforcehub/plugin/PluginGatewayPermissionMatrixIntegrationTest.java" <<'WFH_99_2_BASELINE_11'
package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.sdk.ActionDescriptor;
import com.workforcehub.plugin.sdk.InvocationContext;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.plugin.sdk.ServiceResult;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 插件网关真实数据库权限矩阵集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@TestPropertySource(properties =
    "workforce.plugin.gateway.csrf.allowed-origins=http://localhost:5174")
class PluginGatewayPermissionMatrixIntegrationTest extends LocalIntegrationTestSupport {

    private static final String PLUGIN_ID = "permission-matrix-plugin";
    private static final String ACTION_CODE = "permission.matrix.execute";
    private static final String DB_CAPABILITY = "PLUGIN_PERMISSION_MATRIX";
    private static final String DESCRIPTOR_ONLY_CAPABILITY = "PLUGIN_DESCRIPTOR_ONLY";
    private static final String TRUSTED_ORIGIN = "http://localhost:5174";
    private static final String VALID_SRI = "sha384-" + "a".repeat(64);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @MockBean
    private PluginManagerService pluginManagerService;

    private PluginActionHandler handler;

    @BeforeEach
    void setUpPluginFixture() {
        jdbcTemplate.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type, status, jar_path, frontend_sha384)
            VALUES (?, ?, '1.0.0', '1.0.0', '17', 'UI', 'ACTIVE', ?, ?)
            """, PLUGIN_ID, PLUGIN_ID, "target/permission-matrix-plugin.jar", VALID_SRI);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_capability (plugin_id, capability_code)
            VALUES (?, ?)
            """, PLUGIN_ID, DB_CAPABILITY);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, ?, ?)
            """, PLUGIN_ID, ACTION_CODE, DB_CAPABILITY);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_visibility (plugin_id, scope_type, scope_value)
            VALUES (?, 'USER', '2003')
            """, PLUGIN_ID);

        lifecycleGuard.enable(PLUGIN_ID);
        handler = org.mockito.Mockito.mock(PluginActionHandler.class);
        reset(pluginManagerService);
        PluginRow plugin = new PluginRow();
        plugin.setPluginId(PLUGIN_ID);
        plugin.setStatus(PluginStatus.ACTIVE);
        when(pluginManagerService.getPlugin(PLUGIN_ID)).thenReturn(plugin);
        when(pluginManagerService.findActionHandler(PLUGIN_ID, ACTION_CODE)).thenReturn(Optional.of(handler));
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            DB_CAPABILITY
        ));
        when(handler.execute(any(InvocationContext.class), anyMap()))
            .thenReturn(ServiceResult.ok(Map.of("executed", true)));
    }

    @AfterEach
    void tearDownPluginFixture() {
        lifecycleGuard.remove(PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin_capability WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", PLUGIN_ID);
        jdbcTemplate.update("""
            UPDATE admin_user
            SET status = 'ACTIVE', is_deleted = 0
            WHERE id = 8001
            """);
        jdbcTemplate.update("""
            UPDATE admin_permission
            SET status = 'ACTIVE', is_deleted = 0
            WHERE perm_code = 'ADMIN_PLUGIN_CALL'
            """);
        jdbcTemplate.update("""
            INSERT INTO admin_role_permission (id, role_id, permission_id, created_by)
            SELECT seq.next_id, r.id, p.id, NULL
            FROM admin_role r
            JOIN admin_permission p ON p.perm_code = 'ADMIN_PLUGIN_CALL'
            CROSS JOIN (
                SELECT COALESCE(MAX(id), 6100) + 1 AS next_id
                FROM admin_role_permission
            ) seq
            WHERE r.role_code = 'SUPER_ADMIN'
              AND r.is_deleted = 0
              AND NOT EXISTS (
                  SELECT 1
                  FROM admin_role_permission existing
                  WHERE existing.role_id = r.id
                    AND existing.permission_id = p.id
              )
            """);
    }

    @Test
    void adminDemoWithMappedCapabilityShouldExecuteAction() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.executed").value(true));

        verify(handler).execute(any(InvocationContext.class), anyMap());
        assertEquals(1, pluginActionAuditCount());
        Map<String, Object> audit = jdbcTemplate.queryForMap("""
            SELECT operator_type, operator_id, result, target_type, target_id,
                   CAST(detail_json AS CHAR) AS detail_json
            FROM sys_audit_log
            WHERE action_type = 'PLUGIN_ACTION_EXECUTED'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            """, PLUGIN_ID);
        assertEquals("ADMIN", audit.get("operator_type"));
        assertEquals(8001L, ((Number) audit.get("operator_id")).longValue());
        assertEquals("SUCCESS", audit.get("result"));
        assertEquals("PLUGIN_ACTION", audit.get("target_type"));
        assertEquals(null, audit.get("target_id"));
        JsonNode detail = objectMapper.readTree((String) audit.get("detail_json"));
        assertEquals(
            Set.of("pluginId", "actionCode", "triggerSource", "serviceCode", "retryable"),
            objectMapper.convertValue(detail, Map.class).keySet()
        );
    }

    @Test
    void visibleEmployeeWithSpoofedIdentityHeadersShouldStillBeDenied() throws Exception {
        Cookie employeeSession = loginEmployee();

        mockMvc.perform(get("/api/employee/plugins/mine").cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].id").value(hasItem(PLUGIN_ID)));

        mockMvc.perform(gatewayRequest(employeeSession)
                .header("X-User-Id", "8001")
                .header("X-User-Type", "ADMIN"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verify(pluginManagerService, never()).getPlugin(PLUGIN_ID);
        verify(handler, never()).execute(any(), anyMap());
        assertEquals(0, pluginActionAuditCount());
    }

    @Test
    void removingCapabilityAfterSuccessfulRequestShouldDenyNextRequest() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ? AND capability_code = ?",
            PLUGIN_ID,
            DB_CAPABILITY
        );

        invokeGateway(adminSession)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void duplicateSameRequestShouldReplayWithoutExecutingAgain() throws Exception {
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executed").value(true));
        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.executed").value(true));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void sameKeyWithDifferentParamsShouldConflict() throws Exception {
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            Map.of("value", com.workforcehub.plugin.sdk.ParamSchema.string("值", false)),
            Map.of(),
            DB_CAPABILITY,
            java.time.Duration.ofSeconds(5),
            true
        ));
        Cookie adminSession = loginAdmin();

        mockMvc.perform(gatewayRequest(adminSession, "same-key", "{\"value\":\"a\"}"))
            .andExpect(status().isOk());
        mockMvc.perform(gatewayRequest(adminSession, "same-key", "{\"value\":\"b\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(3001));

        verify(handler, times(1)).execute(any(), anyMap());
        assertEquals(1, pluginActionAuditCount());
    }

    @Test
    void idempotencyKeyShouldBeCaseSensitive() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(gatewayRequest(adminSession, "Case-Key", "{}"))
            .andExpect(status().isOk());
        mockMvc.perform(gatewayRequest(adminSession, "case-key", "{}"))
            .andExpect(status().isOk());

        verify(handler, times(2)).execute(any(), anyMap());
    }

    @Test
    void removingActionMappingShouldDenyBeforeHandlerExecution() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ? AND action_code = ?",
            PLUGIN_ID,
            ACTION_CODE
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void inactiveAdminPluginCallPermissionShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "UPDATE admin_permission SET status = 'INACTIVE' WHERE perm_code = 'ADMIN_PLUGIN_CALL'"
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void deletedAdminPluginCallPermissionShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update(
            "UPDATE admin_permission SET is_deleted = 1 WHERE perm_code = 'ADMIN_PLUGIN_CALL'"
        );

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void disabledAdminUserShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update("UPDATE admin_user SET status = 'DISABLED' WHERE id = 8001");

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void removedSuperAdminPluginPermissionRelationShouldDenyExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        jdbcTemplate.update("""
            DELETE rp
            FROM admin_role_permission rp
            JOIN admin_role r ON r.id = rp.role_id
            JOIN admin_permission p ON p.id = rp.permission_id
            WHERE r.role_code = 'SUPER_ADMIN'
              AND p.perm_code = 'ADMIN_PLUGIN_CALL'
            """);

        assertGatewayForbidden(adminSession);

        verify(handler, never()).execute(any(), anyMap());
    }

    @Test
    void hostAuthoritativeDbMappingShouldAllowDescriptorCapabilityMismatch() throws Exception {
        // 网关授权以宿主 action_permission 映射为权威，不要求插件 descriptor 自报能力一致。
        when(handler.getDescriptor()).thenReturn(new ActionDescriptor(
            ACTION_CODE,
            "权限矩阵动作",
            "权限矩阵集成测试动作",
            DESCRIPTOR_ONLY_CAPABILITY
        ));
        Cookie adminSession = loginAdmin();

        invokeGateway(adminSession)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(handler).execute(any(InvocationContext.class), anyMap());
    }

    private ResultActions invokeGateway(Cookie session) throws Exception {
        return mockMvc.perform(gatewayRequest(session));
    }

    private void assertGatewayForbidden(Cookie session) throws Exception {
        invokeGateway(session)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    private MockHttpServletRequestBuilder gatewayRequest(Cookie session) {
        return gatewayRequest(session, "permission-matrix-key", "{}");
    }

    private MockHttpServletRequestBuilder gatewayRequest(Cookie session, String idempotencyKey, String content) {
        return post("/api/plugin-gateway/" + PLUGIN_ID + "/actions/" + ACTION_CODE)
            .cookie(session)
            .header(HttpHeaders.ORIGIN, TRUSTED_ORIGIN)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .content(content);
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    private int pluginActionAuditCount() {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE action_type = 'PLUGIN_ACTION_EXECUTED'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            """, Integer.class, PLUGIN_ID);
        return count == null ? 0 : count;
    }

    private Cookie loginEmployee() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "zhangsan.employee",
                      "password": "Employee@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }
}
WFH_99_2_BASELINE_11
mkdir -p "$target/server/src/test/java/com/workforcehub/plugin"
cat > "$target/server/src/test/java/com/workforcehub/plugin/EmployeePluginListIntegrationTest.java" <<'WFH_99_2_BASELINE_12'
package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class EmployeePluginListIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private static final String SRI_A = "sha384-" + "a".repeat(64);
    private static final String SRI_B = "sha384-" + "b".repeat(64);
    private static final String SRI_C = "sha384-" + "c".repeat(64);
    private static final String SRI_D = "sha384-" + "d".repeat(64);
    private static final String SRI_E = "sha384-" + "e".repeat(64);
    private static final String SRI_F = "sha384-" + "f".repeat(64);
    private static final String SRI_K = "sha384-" + "k".repeat(64);
    private static final String SRI_L = "sha384-" + "l".repeat(64);
    private static final String SRI_M = "sha384-" + "m".repeat(64);

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM sys_plugin_visibility");
        jdbc.execute("DELETE FROM sys_plugin");

        // ACTIVE UI 插件 — 各范围
        insPlugin("leave-ext",    "假期扩展",    SRI_A);
        insPlugin("attend-ext",   "考勤扩展",    SRI_B);
        insPlugin("dept-plugin",  "部门插件",    SRI_C);
        insPlugin("pos-plugin",   "岗位插件",    SRI_D);
        insPlugin("mgr-plugin",   "经理插件",    SRI_E);
        insPlugin("multi-scope",  "多范围插件",  SRI_F);
        insPlugin("evil-entry",   "恶意外部入口", SRI_K);

        // 应被排除的插件
        insPluginFull("stopped", "已停用",  "sha384-" + "g".repeat(64), "STOPPED",    "UI");
        insPluginFull("func",    "功能插件", "sha384-" + "h".repeat(64), "ACTIVE",     "FUNCTIONAL");
        insPluginFull("BAD_ID",  "非法ID",  "sha384-" + "i".repeat(64), "ACTIVE",     "UI");
        insPluginFull("bad-sri", "非法SRI", "sha256-" + "j".repeat(64), "ACTIVE",     "UI");

        insPlugin("user-nomatch",   "不匹配USER",    SRI_L);
        insPlugin("dept-nomatch",   "不匹配DEPT",    SRI_L);
        insPlugin("pos-nomatch",    "不匹配POSITION", SRI_L);
        insPlugin("unknown-scope",  "未知范围",       SRI_M);

        // 可见性
        insVis("leave-ext",   "ALL",      null);
        insVis("attend-ext",  "USER",     "2003");
        insVis("dept-plugin", "DEPT",     "111");
        insVis("pos-plugin",  "POSITION", "3001");
        insVis("mgr-plugin",  "MANAGER",  null);
        insVis("multi-scope", "ALL",      null);
        insVis("multi-scope", "USER",     "2003");
        insVis("stopped",     "ALL",      null);
        insVis("func",        "ALL",      null);
        insVis("BAD_ID",      "ALL",      null);
        insVis("bad-sri",     "ALL",      null);
        insVis("user-nomatch",  "USER",     "9999");
        insVis("dept-nomatch",  "DEPT",     "999");
        insVis("pos-nomatch",   "POSITION", "9999");
        insVis("unknown-scope", "UNKNOWN",  "x");

        jdbc.update("UPDATE sys_plugin SET frontend_entry = ? WHERE plugin_id = ?",
            "https://evil.invalid/x.js", "evil-entry");
        insVis("evil-entry", "ALL", null);
    }

    @Test
    void anonymous401() throws Exception {
        mockMvc.perform(get("/api/employee/plugins/mine"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnEmployeePath403() throws Exception {
        Cookie admin = login("admin.demo", "Admin@123456", "/api/admin/auth/login");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(admin))
            .andExpect(status().isForbidden());
    }

    @Test
    void zhangsanSeesMatchingPlugins() throws Exception {
        Cookie s = login("zhangsan.employee", "Employee@123456", "/api/employee/auth/login");
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn());

        List<String> ids = ids(data);
        assertEquals(6, ids.size(), "应恰好命中 6 个插件");
        assertTrue(ids.containsAll(List.of(
            "leave-ext", "attend-ext", "dept-plugin", "pos-plugin", "multi-scope", "evil-entry")));
        assertFalse(ids.contains("mgr-plugin"), "张三非经理");
        // multi-scope 不重复
        assertEquals(1, ids.stream().filter("multi-scope"::equals).count(), "multi-scope 不重复");

        assertFalse(ids.contains("user-nomatch"), "不匹配 USER");
        assertFalse(ids.contains("dept-nomatch"), "不匹配 DEPT");
        assertFalse(ids.contains("pos-nomatch"), "不匹配 POSITION");
        assertFalse(ids.contains("unknown-scope"), "UNKNOWN 范围");
        assertFalse(ids.contains("stopped"), "STOPPED");
        assertFalse(ids.contains("func"), "FUNCTIONAL");
        assertFalse(ids.contains("BAD_ID"), "非法 id");
        assertFalse(ids.contains("bad-sri"), "非法 integrity");

        // 验证 frontendEntry 固定同源，不透传数据库 frontend_entry
        for (JsonNode item : data) {
            String id = item.get("id").asText();
            assertEquals("/plugins/" + id + "/index.js", item.get("frontendEntry").asText());
            assertEquals(4, item.size(), "只有 4 个字段");
            assertFalse(item.has("jarPath"));
            assertFalse(item.has("status"));
        }
    }

    @Test
    void lisiSeesManagerScope() throws Exception {
        // 李四 (2002) 有直属下属 2003/2004
        Cookie s = login("lisi.manager", "Employee@123456", "/api/employee/auth/login");
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andReturn());
        assertTrue(ids(data).contains("mgr-plugin"), "李四是经理");
    }

    @Test
    void managerScopeRequiresValidSubordinatesWhenNoDeptLeader() throws Exception {
        Cookie s = login("lisi.manager", "Employee@123456", "/api/employee/auth/login");

        // 临时禁用 department_leader，仅测试下属路径
        jdbc.execute("UPDATE org_department_leader SET status='INACTIVE' WHERE user_id=2002");

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET is_deleted=1 WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET status='DISABLED' WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET employment_status='OFFBOARDED' WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        // 恢复
        jdbc.execute("UPDATE org_department_leader SET status='ACTIVE' WHERE user_id=2002");
        restoreSubordinates();
    }

    @Test
    void invalidOrgUserReturnsEmpty() throws Exception {
        Cookie s = login("zhangsan.employee", "Employee@123456", "/api/employee/auth/login");

        jdbc.execute("UPDATE org_user SET is_deleted=1 WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id=2003");
        jdbc.execute("UPDATE org_user SET status='DISABLED' WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id=2003");
        jdbc.execute("UPDATE org_user SET employment_status='OFFBOARDED' WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- helpers ---

    private void restoreSubordinates() {
        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id IN (2003,2004)");
    }

    private void assertNoManagerPlugin(Cookie session) throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(session))
            .andExpect(status().isOk())
            .andReturn());
        assertFalse(ids(data).contains("mgr-plugin"),
            "下属全部无效且无部门负责人时不应命中 MANAGER");
    }

    private void insPlugin(String id, String name, String sri) {
        insPluginFull(id, name, sri, "ACTIVE", "UI");
    }

    private void insPluginFull(String id, String name, String sri, String status, String type) {
        jdbc.update(
            "INSERT INTO sys_plugin (plugin_id,name,version,type,status,jar_path,frontend_sha384) VALUES (?,?,'1.0.0',?,?,?,?)",
            id, name, type, status, "/tmp/" + id + ".jar", sri
        );
    }

    private void insVis(String pluginId, String scopeType, String scopeValue) {
        jdbc.update(
            "INSERT INTO sys_plugin_visibility (plugin_id,scope_type,scope_value) VALUES (?,?,?)",
            pluginId, scopeType, scopeValue
        );
    }

    private Cookie login(String loginName, String password, String path) throws Exception {
        MvcResult r = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"%s\",\"password\":\"%s\"}".formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return r.getResponse().getCookie("SESSION");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private List<String> ids(JsonNode data) {
        return data.findValuesAsText("id");
    }
}
WFH_99_2_BASELINE_12
mkdir -p "$target/server/src/test/java/com/workforcehub/plugin"
cat > "$target/server/src/test/java/com/workforcehub/plugin/PluginManagerServiceIntegrationTest.java" <<'WFH_99_2_BASELINE_13'
package com.workforcehub.plugin;

import com.jayway.jsonpath.JsonPath;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.plugin.repository.PluginDependencySpec;
import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginRepository;
import com.workforcehub.plugin.repository.PluginStatus;
import com.workforcehub.plugin.runtime.PluginFlywayMigrator;
import com.workforcehub.plugin.runtime.PluginLifecycleGuard;
import com.workforcehub.plugin.runtime.PluginManagerService;
import com.workforcehub.plugin.runtime.PluginPermissionService;
import com.workforcehub.plugin.runtime.PluginSignatureVerifier;
import com.workforcehub.plugin.runtime.PluginVersionService;
import com.workforcehub.plugin.runtime.WorkforcePluginManager;
import com.workforcehub.plugin.sdk.HostService;
import com.workforcehub.plugin.sdk.PluginActionHandler;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest(properties = "workforce.plugin.root=target/test-plugins")
@AutoConfigureMockMvc
@ActiveProfiles("api")
class PluginManagerServiceIntegrationTest extends LocalIntegrationTestSupport {

    private static final String SUCCESS_PLUGIN_ID = "migration-plugin-success";
    private static final String FAILURE_PLUGIN_ID = "migration-plugin-failure";
    private static final String DEPENDENCY_FAILURE_PLUGIN_ID = "dependency-resolution-failure";
    private static final String UNRESOLVED_DEPENDENT_PLUGIN_ID = "existing-unresolved-dependent";
    private static final String PERSISTENCE_FAILURE_PLUGIN_ID = "migration-plugin-persistence-failure";
    private static final String TAMPERED_PLUGIN_ID = "migration-plugin-tampered";
    private static final String REINSTALL_PLUGIN_ID = "plugin-reinstall";
    private static final String UPLOAD_PLUGIN_ID = "plugin-upload-http";
    private static final String HELLO_PLUGIN_ID = "hello-plugin";
    private static final String DEPENDENCY_BASE_PLUGIN_ID = "dependency-base";
    private static final String DEPENDENCY_CONSUMER_PLUGIN_ID = "dependency-consumer";
    private static final String HELLO_PLUGIN_FRONTEND_SRI =
        "sha384-c527zMMh7jwa0gTNl9fI1Mg5xEqGXyEFnTFCcjr7fokaoyPHzkIY7kC9f13c4zUQ";
    private static final String PERSISTENCE_GUARD_TABLE = "plugin_migration_persistence_guard";
    private static final String DEPENDENCY_MANIFEST_GUARD_TABLE =
        "plugin_dependency_manifest_guard";
    private static final String REINSTALL_DATA_TABLE = "plugin_reinstall_data";
    private static final String REINSTALL_TRANSACTION_GUARD_TABLE =
        "plugin_reinstall_transaction_guard";
    private static final String TEST_SIGNER_ID = "plugin-integration-test";
    private static final KeyPair SIGNING_KEY = createSigningKey();

    @DynamicPropertySource
    static void configureTrustedSigner(DynamicPropertyRegistry registry) {
        registry.add(
            "workforce.plugin.trusted-signers",
            () -> TEST_SIGNER_ID + "="
                + Base64.getEncoder().encodeToString(SIGNING_KEY.getPublic().getEncoded())
        );
    }

    @TempDir
    Path tempDir;

    @Autowired
    private PluginManagerService pluginManagerService;

    @SpyBean
    private PluginRepository pluginRepository;

    @SpyBean
    private WorkforcePluginManager pluginManager;

    @Autowired
    private PluginFlywayMigrator flywayMigrator;

    @Autowired
    private PluginSignatureVerifier signatureVerifier;

    @Autowired
    private PluginVersionService versionService;

    @Autowired
    private PluginPermissionService permissionService;

    @Autowired
    private List<HostService> hostServices;

    @Autowired
    private PluginLifecycleGuard lifecycleGuard;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment environment;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void resetPluginState() {
        unloadIfPresent(SUCCESS_PLUGIN_ID);
        unloadIfPresent(FAILURE_PLUGIN_ID);
        unloadIfPresent(DEPENDENCY_FAILURE_PLUGIN_ID);
        unloadIfPresent(UNRESOLVED_DEPENDENT_PLUGIN_ID);
        unloadIfPresent(PERSISTENCE_FAILURE_PLUGIN_ID);
        unloadIfPresent(TAMPERED_PLUGIN_ID);
        unloadIfPresent(REINSTALL_PLUGIN_ID);
        unloadIfPresent(UPLOAD_PLUGIN_ID);
        unloadIfPresent(HELLO_PLUGIN_ID);
        unloadIfPresent(DEPENDENCY_CONSUMER_PLUGIN_ID);
        unloadIfPresent(DEPENDENCY_BASE_PLUGIN_ID);
        dropTable(REINSTALL_TRANSACTION_GUARD_TABLE);
        jdbcTemplate.update("""
            DELETE FROM sys_plugin_dependency
            WHERE plugin_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?)
               OR depends_on IN (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            UNRESOLVED_DEPENDENT_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID,
            TAMPERED_PLUGIN_ID,
            REINSTALL_PLUGIN_ID,
            UPLOAD_PLUGIN_ID,
            HELLO_PLUGIN_ID,
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            UNRESOLVED_DEPENDENT_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID,
            TAMPERED_PLUGIN_ID,
            REINSTALL_PLUGIN_ID,
            UPLOAD_PLUGIN_ID,
            HELLO_PLUGIN_ID
        );
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_operation_idempotency WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_event_subscription WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_dependency WHERE plugin_id = ? OR depends_on = ?",
            REINSTALL_PLUGIN_ID,
            REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_visibility WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_idempotency WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_action_permission WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_capability WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_config WHERE plugin_id = ?", HELLO_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?, ?, ?, ?, ?)",
            SUCCESS_PLUGIN_ID,
            FAILURE_PLUGIN_ID,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            PERSISTENCE_FAILURE_PLUGIN_ID,
            TAMPERED_PLUGIN_ID,
            HELLO_PLUGIN_ID
        );
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("DELETE FROM sys_plugin WHERE plugin_id = ?", UPLOAD_PLUGIN_ID);
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_dependency WHERE plugin_id IN (?, ?) OR depends_on IN (?, ?)",
            DEPENDENCY_BASE_PLUGIN_ID,
            DEPENDENCY_CONSUMER_PLUGIN_ID,
            DEPENDENCY_BASE_PLUGIN_ID,
            DEPENDENCY_CONSUMER_PLUGIN_ID
        );
        jdbcTemplate.update(
            "DELETE FROM sys_plugin WHERE plugin_id IN (?, ?)",
            DEPENDENCY_BASE_PLUGIN_ID,
            DEPENDENCY_CONSUMER_PLUGIN_ID
        );
        jdbcTemplate.update("""
            DELETE FROM sys_audit_log
            WHERE target_type = 'PLUGIN'
              AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
            """, UPLOAD_PLUGIN_ID);
        dropTable("plugin_migration_success");
        dropTable(PERSISTENCE_GUARD_TABLE);
        dropTable(DEPENDENCY_MANIFEST_GUARD_TABLE);
        dropTable(REINSTALL_DATA_TABLE);
        dropTable(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(DEPENDENCY_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(TAMPERED_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(REINSTALL_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(UPLOAD_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(DEPENDENCY_BASE_PLUGIN_ID));
        dropTable(flywayMigrator.getHistoryTable(DEPENDENCY_CONSUMER_PLUGIN_ID));
        clearTestSnapshots();
    }

    @AfterEach
    void cleanupPluginState() {
        resetPluginState();
    }

    @Test
    void shouldInstallMigrateAndPersistLifecycle() throws Exception {
        Path jarPath = buildPluginJar(
            SUCCESS_PLUGIN_ID,
            "CREATE TABLE plugin_migration_success (id INT PRIMARY KEY);"
        );

        PluginRow installed = pluginManagerService.install(jarPath, metadata("Migration Success", jarPath));

        Path snapshotPath = Path.of(installed.getJarPath());
        PluginRow persisted = pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID);
        assertEquals(SUCCESS_PLUGIN_ID, installed.getPluginId());
        assertEquals(TEST_SIGNER_ID, installed.getSignerId());
        assertEquals(sha256(jarPath), installed.getJarSha256());
        assertEquals("FUNCTIONAL", persisted.getType());
        assertEquals("Workforce Hub", persisted.getProvider());
        assertNull(persisted.getFrontendEntry());
        assertNull(persisted.getFrontendSha384());
        assertNotEquals(jarPath.toAbsolutePath(), snapshotPath);
        assertTrue(snapshotPath.startsWith(
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().resolve(".snapshots")
        ));
        assertEquals(installed.getJarSha256(), sha256(snapshotPath));
        assertEquals(snapshotPath.toString(), persisted.getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
        assertEquals(PluginStatus.INSTALLED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());
        assertEquals(1, tableExists("plugin_migration_success"));
        assertEquals(1, tableExists(flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID)));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + flywayMigrator.getHistoryTable(SUCCESS_PLUGIN_ID)
                + " WHERE version = '1' AND type = 'SQL' AND success = 1",
            Integer.class
        ));

        pluginManagerService.enable(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.ACTIVE, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());

        pluginManagerService.disable(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.STOPPED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());

        pluginManagerService.uninstall(SUCCESS_PLUGIN_ID);
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(SUCCESS_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(SUCCESS_PLUGIN_ID));
        assertTrue(Files.isRegularFile(snapshotPath));
        assertFalse(hasMigrationTempDirectory(SUCCESS_PLUGIN_ID));
    }

    @Test
    void shouldPersistExposeAndRestoreTrustedDependencyGraph() throws Exception {
        PluginDependencySpec dependency = new PluginDependencySpec(
            DEPENDENCY_BASE_PLUGIN_ID,
            "1.0.0",
            "2.0.0"
        );
        Path baseJar = buildPluginJar(
            DEPENDENCY_BASE_PLUGIN_ID,
            "1.5.0",
            "SELECT 1;",
            null,
            null
        );
        Path consumerJar = buildPluginJar(
            DEPENDENCY_CONSUMER_PLUGIN_ID,
            "0.1.0",
            "SELECT 1;",
            null,
            DEPENDENCY_BASE_PLUGIN_ID + "@>=1.0.0 & <=2.0.0",
            "[{\"pluginId\":\"" + DEPENDENCY_BASE_PLUGIN_ID
                + "\",\"minVersion\":\"1.0.0\",\"maxVersion\":\"2.0.0\"}]"
        );

        PluginRow base = pluginManagerService.install(
            baseJar,
            metadata("Dependency Base", baseJar)
        );
        PluginRow consumer = pluginManagerService.install(
            consumerJar,
            metadata("Dependency Consumer", consumerJar)
        );

        assertEquals(List.of(dependency), consumer.getDependencies());
        assertEquals(List.of(dependency),
            pluginManagerService.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getDependencies());
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_plugin_dependency
            WHERE plugin_id = ? AND depends_on = ?
              AND min_version = '1.0.0' AND max_version = '2.0.0'
            """, Integer.class, DEPENDENCY_CONSUMER_PLUGIN_ID, DEPENDENCY_BASE_PLUGIN_ID));

        MvcResult listResult = mockMvc.perform(get("/api/admin/plugins")
                .with(adminPluginManager()))
            .andReturn();
        assertEquals(200, listResult.getResponse().getStatus());
        String responseBody = listResult.getResponse().getContentAsString();
        assertEquals(List.of(DEPENDENCY_BASE_PLUGIN_ID), JsonPath.read(
            responseBody,
            "$.data[?(@.id == '" + DEPENDENCY_CONSUMER_PLUGIN_ID
                + "')].dependencies[0].pluginId"
        ));
        assertEquals(List.of("1.0.0"), JsonPath.read(
            responseBody,
            "$.data[?(@.id == '" + DEPENDENCY_CONSUMER_PLUGIN_ID
                + "')].dependencies[0].minVersion"
        ));
        assertEquals(List.of("2.0.0"), JsonPath.read(
            responseBody,
            "$.data[?(@.id == '" + DEPENDENCY_CONSUMER_PLUGIN_ID
                + "')].dependencies[0].maxVersion"
        ));

        assertThrows(
            IllegalStateException.class,
            () -> pluginManagerService.enable(DEPENDENCY_CONSUMER_PLUGIN_ID)
        );
        assertEquals(PluginState.RESOLVED,
            pluginManager.getPlugin(DEPENDENCY_BASE_PLUGIN_ID).getPluginState());
        assertEquals(PluginState.RESOLVED,
            pluginManager.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getPluginState());

        pluginManagerService.enable(DEPENDENCY_BASE_PLUGIN_ID);
        pluginManagerService.enable(DEPENDENCY_CONSUMER_PLUGIN_ID);
        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(DEPENDENCY_BASE_PLUGIN_ID).getStatus());
        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getStatus());
        assertThrows(
            IllegalStateException.class,
            () -> pluginManagerService.disable(DEPENDENCY_BASE_PLUGIN_ID)
        );

        assertTrue(pluginManager.unloadPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(DEPENDENCY_BASE_PLUGIN_ID));
        jdbcTemplate.update("""
            UPDATE sys_plugin
            SET installed_at = CASE plugin_id
                WHEN ? THEN '2026-01-01 00:00:00'
                ELSE '2026-01-02 00:00:00'
            END
            WHERE plugin_id IN (?, ?)
            """,
            DEPENDENCY_CONSUMER_PLUGIN_ID,
            DEPENDENCY_BASE_PLUGIN_ID,
            DEPENDENCY_CONSUMER_PLUGIN_ID
        );
        clearInvocations(pluginManager);

        pluginManagerService.restorePlugins();

        InOrder restoreOrder = inOrder(pluginManager);
        restoreOrder.verify(pluginManager).loadPlugin(Path.of(base.getJarPath()));
        restoreOrder.verify(pluginManager).startPlugin(DEPENDENCY_BASE_PLUGIN_ID);
        restoreOrder.verify(pluginManager).loadPlugin(Path.of(consumer.getJarPath()));
        restoreOrder.verify(pluginManager).startPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID);
        assertEquals(PluginState.STARTED,
            pluginManager.getPlugin(DEPENDENCY_BASE_PLUGIN_ID).getPluginState());
        assertEquals(PluginState.STARTED,
            pluginManager.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getPluginState());
        assertThrows(
            IllegalStateException.class,
            () -> pluginManagerService.uninstall(DEPENDENCY_BASE_PLUGIN_ID)
        );

        pluginManagerService.uninstall(DEPENDENCY_CONSUMER_PLUGIN_ID);
        pluginManagerService.disable(DEPENDENCY_BASE_PLUGIN_ID);
        pluginManagerService.uninstall(DEPENDENCY_BASE_PLUGIN_ID);
        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getStatus());
        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(DEPENDENCY_BASE_PLUGIN_ID).getStatus());
    }

    @Test
    void shouldRejectPersistedDependencyDriftBeforeRestoreLoad() throws Exception {
        Path baseJar = buildPluginJar(
            DEPENDENCY_BASE_PLUGIN_ID,
            "1.5.0",
            "SELECT 1;",
            null,
            null
        );
        Path consumerJar = buildPluginJar(
            DEPENDENCY_CONSUMER_PLUGIN_ID,
            "0.1.0",
            "SELECT 1;",
            null,
            DEPENDENCY_BASE_PLUGIN_ID + "@>=1.0.0 & <=2.0.0",
            "[{\"pluginId\":\"" + DEPENDENCY_BASE_PLUGIN_ID
                + "\",\"minVersion\":\"1.0.0\",\"maxVersion\":\"2.0.0\"}]"
        );
        PluginRow base = pluginManagerService.install(
            baseJar,
            metadata("Dependency Base", baseJar)
        );
        PluginRow consumer = pluginManagerService.install(
            consumerJar,
            metadata("Dependency Consumer", consumerJar)
        );
        assertTrue(pluginManager.unloadPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(DEPENDENCY_BASE_PLUGIN_ID));
        jdbcTemplate.update(
            "DELETE FROM sys_plugin_dependency WHERE plugin_id = ?",
            DEPENDENCY_CONSUMER_PLUGIN_ID
        );
        clearInvocations(pluginManager);

        pluginManagerService.restorePlugins();

        assertNotNull(pluginManager.getPlugin(DEPENDENCY_BASE_PLUGIN_ID));
        assertNull(pluginManager.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID));
        assertEquals(PluginStatus.ERROR,
            pluginManagerService.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getStatus());
        assertEquals("插件清单与安装记录不一致",
            pluginManagerService.getPlugin(DEPENDENCY_CONSUMER_PLUGIN_ID).getErrorMessage());
        verify(pluginManager).loadPlugin(Path.of(base.getJarPath()));
        verify(pluginManager, never()).loadPlugin(Path.of(consumer.getJarPath()));
    }

    @Test
    void shouldReplaceUninstalledSameIdAndPreserveDurableState() throws Exception {
        String firstMigration = """
            CREATE TABLE plugin_reinstall_data (
                id INT PRIMARY KEY,
                business_value VARCHAR(64) NOT NULL
            );
            """;
        Path firstJar = buildPluginJar(
            REINSTALL_PLUGIN_ID,
            "0.1.0",
            firstMigration,
            null,
            null
        );
        PluginRow firstInstall = pluginManagerService.install(
            firstJar,
            metadata("Reinstall Plugin v1", firstJar)
        );
        Path previousSnapshot = Path.of(firstInstall.getJarPath());
        PluginClassLoader previousClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(REINSTALL_PLUGIN_ID)
        );

        jdbcTemplate.update(
            "INSERT INTO " + REINSTALL_DATA_TABLE + " (id, business_value) VALUES (1, 'preserved')");
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_config
                (plugin_id, config_key, config_value, value_type, default_value,
                 description, requires_restart, is_secret)
            VALUES (?, 'secret.key', 'encrypted-placeholder', 'STRING', 'default-value',
                    'preserved config', 1, 1)
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update(
            "INSERT INTO sys_plugin_capability (plugin_id, capability_code) VALUES (?, 'employee:read')",
            REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_visibility (plugin_id, scope_type, scope_value)
            VALUES (?, 'DEPT', 'dept-1')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, 'legacy.action', 'employee:read')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_idempotency
                (plugin_id, action_code, caller_type, caller_id, idempotency_key,
                 request_hash, status, claim_token)
            VALUES (?, 'legacy.action', 'ADMIN', 1, 'legacy-action-key', ?, 'IN_PROGRESS',
                    '00000000-0000-0000-0000-000000000001')
            """, REINSTALL_PLUGIN_ID, "a".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_operation_idempotency
                (plugin_id, operation_code, idempotency_key, request_hash)
            VALUES (?, 'event.subscribe', 'legacy-operation-key', ?)
            """, REINSTALL_PLUGIN_ID, "b".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_event_subscription (plugin_id, event_type, action_code)
            VALUES (?, 'employee.updated', 'legacy.action')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES (?, 'legacy-dependency', '0.1.0')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES ('dependent-plugin', ?, '0.1.0')
            """, REINSTALL_PLUGIN_ID);

        pluginManagerService.uninstall(REINSTALL_PLUGIN_ID);

        assertTrue(previousClassLoader.isClosed());
        assertTrue(Files.isRegularFile(previousSnapshot));
        Path replacementJar = buildPluginJar(
            REINSTALL_PLUGIN_ID,
            "0.2.0",
            firstMigration,
            "ALTER TABLE plugin_reinstall_data ADD COLUMN version_marker VARCHAR(32);",
            null
        );

        PluginRow replacement = pluginManagerService.install(
            replacementJar,
            metadata("Reinstall Plugin v2", replacementJar)
        );

        Path replacementSnapshot = Path.of(replacement.getJarPath());
        PluginRow persisted = pluginManagerService.getPlugin(REINSTALL_PLUGIN_ID);
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(PluginStatus.INSTALLED, persisted.getStatus());
        assertEquals("0.2.0", persisted.getVersion());
        assertEquals(TEST_SIGNER_ID, persisted.getSignerId());
        assertEquals(sha256(replacementJar), persisted.getJarSha256());
        assertEquals(replacementSnapshot.toString(), persisted.getJarPath());
        assertNull(persisted.getErrorMessage());
        assertFalse(Files.exists(previousSnapshot));
        assertTrue(Files.isRegularFile(replacementSnapshot));
        assertEquals(persisted.getJarSha256(), sha256(replacementSnapshot));
        assertNotSame(previousClassLoader, pluginManager.getPluginClassLoader(REINSTALL_PLUGIN_ID));
        assertEquals("0.2.0",
            pluginManager.getPlugin(REINSTALL_PLUGIN_ID).getDescriptor().getVersion());
        assertEquals(1L, pluginManager.getPlugins().stream()
            .filter(wrapper -> REINSTALL_PLUGIN_ID.equals(wrapper.getPluginId()))
            .count());
        assertFalse(lifecycleGuard.tryAcquire(REINSTALL_PLUGIN_ID));

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + REINSTALL_DATA_TABLE
                + " WHERE id = 1 AND business_value = 'preserved'",
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = ?
              AND COLUMN_NAME = 'version_marker'
            """, Integer.class, REINSTALL_DATA_TABLE));
        assertEquals(2, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + flywayMigrator.getHistoryTable(REINSTALL_PLUGIN_ID)
                + " WHERE version IN ('1', '2') AND type = 'SQL' AND success = 1",
            Integer.class
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'secret.key'
              AND config_value = 'encrypted-placeholder' AND value_type = 'STRING'
              AND default_value = 'default-value' AND description = 'preserved config'
              AND requires_restart = 1 AND is_secret = 1
            """, Integer.class, REINSTALL_PLUGIN_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_capability WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_visibility WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_action_permission WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_action_idempotency
            WHERE plugin_id = ? AND status = 'IN_PROGRESS'
              AND claim_token = '00000000-0000-0000-0000-000000000001'
            """, Integer.class, REINSTALL_PLUGIN_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_operation_idempotency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_event_subscription WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_dependency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM sys_plugin_dependency
            WHERE plugin_id = 'dependent-plugin' AND depends_on = ?
            """, Integer.class, REINSTALL_PLUGIN_ID));
    }

    @Test
    void replaceUninstalledShouldRollbackRecordAndCleanupWhenRuntimeResetFails() {
        String previousJarPath = tempDir.resolve("previous-plugin.jar").toString();
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type,
                 status, jar_path, signer_id)
            VALUES (?, 'Reinstall Plugin v1', '0.1.0', '0.1.0', '17', 'FUNCTIONAL',
                    'UNINSTALLED', ?, ?)
            """, REINSTALL_PLUGIN_ID, previousJarPath, TEST_SIGNER_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_operation_idempotency
                (plugin_id, operation_code, idempotency_key, request_hash)
            VALUES (?, 'event.subscribe', 'rollback-operation-key', ?)
            """, REINSTALL_PLUGIN_ID, "b".repeat(64));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_event_subscription (plugin_id, event_type, action_code)
            VALUES (?, 'employee.updated', 'legacy.action')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on, min_version)
            VALUES (?, 'legacy-dependency', '0.1.0')
            """, REINSTALL_PLUGIN_ID);
        jdbcTemplate.execute("""
            CREATE TABLE plugin_reinstall_transaction_guard (
                plugin_id VARCHAR(64) NOT NULL,
                depends_on VARCHAR(64) NOT NULL,
                PRIMARY KEY (plugin_id, depends_on),
                CONSTRAINT fk_plugin_reinstall_transaction_guard
                    FOREIGN KEY (plugin_id, depends_on)
                    REFERENCES sys_plugin_dependency (plugin_id, depends_on)
            ) ENGINE=InnoDB
            """);
        jdbcTemplate.update("""
            INSERT INTO plugin_reinstall_transaction_guard (plugin_id, depends_on)
            VALUES (?, 'legacy-dependency')
            """, REINSTALL_PLUGIN_ID);

        PluginRow replacement = new PluginRow();
        replacement.setPluginId(REINSTALL_PLUGIN_ID);
        replacement.setName("Reinstall Plugin v2");
        replacement.setVersion("0.2.0");
        replacement.setSdkVersion("0.1.0");
        replacement.setJavaVersion("17");
        replacement.setType("FUNCTIONAL");
        replacement.setStatus(PluginStatus.INSTALLED);
        replacement.setJarPath(tempDir.resolve("replacement-plugin.jar").toString());
        replacement.setJarSha256("c".repeat(64));
        replacement.setSignature(new byte[]{1, 2, 3});
        replacement.setSignerId(TEST_SIGNER_ID);
        replacement.setProvider("Workforce Hub");
        replacement.setInstalledAt(LocalDateTime.now());
        replacement.setUpdatedAt(LocalDateTime.now());

        try {
            assertThrows(
                DataAccessException.class,
                () -> pluginRepository.replaceUninstalled(
                    replacement,
                    previousJarPath,
                    TEST_SIGNER_ID
                )
            );
        } finally {
            dropTable(REINSTALL_TRANSACTION_GUARD_TABLE);
        }

        PluginRow persisted = pluginRepository.findById(REINSTALL_PLUGIN_ID).orElseThrow();
        assertEquals(PluginStatus.UNINSTALLED, persisted.getStatus());
        assertEquals("0.1.0", persisted.getVersion());
        assertEquals(previousJarPath, persisted.getJarPath());
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_operation_idempotency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_event_subscription WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin_dependency WHERE plugin_id = ?",
            Integer.class,
            REINSTALL_PLUGIN_ID
        ));
    }

    @Test
    void realPf4jPluginShouldReadBoundConfigAndUninstallShouldPreserveIt() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_config
                (plugin_id, config_key, config_value, value_type)
            VALUES (?, 'shared.key', 'configured-value', 'STRING')
            """, HELLO_PLUGIN_ID);
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        Object plugin = pluginManager.getPlugin(HELLO_PLUGIN_ID).getPlugin();
        assertEquals(
            "configured-value",
            plugin.getClass().getMethod("getConfigValue", String.class)
                .invoke(plugin, "shared.key")
        );
        assertNull(plugin.getClass().getMethod("getConfigValue", String.class)
            .invoke(plugin, "missing.key"));

        pluginManagerService.uninstall(HELLO_PLUGIN_ID);

        assertEquals(1, jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_plugin_config
            WHERE plugin_id = ? AND config_key = 'shared.key'
            """, Integer.class, HELLO_PLUGIN_ID));
    }

    @Test
    void shouldSurviveOneHundredSerialEnableDisableCycles() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        ClassLoader classLoader = pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID);
        assertNotNull(classLoader);
        PluginClassLoader pluginClassLoader = assertInstanceOf(PluginClassLoader.class, classLoader);

        for (int cycle = 1; cycle <= 100; cycle++) {
            String round = "第 " + cycle + " 轮";
            pluginManagerService.enable(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.ACTIVE,
                pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus(), round);
            assertEquals(PluginState.STARTED,
                pluginManager.getPlugin(HELLO_PLUGIN_ID).getPluginState(), round);
            assertSame(wrapper, pluginManager.getPlugin(HELLO_PLUGIN_ID), round);
            assertSame(classLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID), round);
            var handlers = pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID);
            assertEquals(1, handlers.size(), round);
            assertEquals("hello.greet", handlers.get(0).getDescriptor().actionCode(), round);
            assertTrue(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID), round);
            lifecycleGuard.release(HELLO_PLUGIN_ID);
            assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID), round);

            pluginManagerService.disable(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.STOPPED,
                pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus(), round);
            assertEquals(PluginState.STOPPED,
                pluginManager.getPlugin(HELLO_PLUGIN_ID).getPluginState(), round);
            assertSame(wrapper, pluginManager.getPlugin(HELLO_PLUGIN_ID), round);
            assertSame(classLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID), round);
            assertTrue(pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID).isEmpty(), round);
            assertTrue(pluginManagerService.findActionHandler(HELLO_PLUGIN_ID, "hello.greet").isEmpty(), round);
            assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID), round);
            assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID), round);
            assertEquals(1L,
                pluginManager.getPlugins().stream()
                    .filter(item -> HELLO_PLUGIN_ID.equals(item.getPluginId()))
                    .count(),
                round);
        }

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            HELLO_PLUGIN_ID
        ));

        pluginManagerService.uninstall(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(pluginClassLoader.isClosed());
        assertTrue(pluginManager.getExtensions(PluginActionHandler.class).stream()
            .noneMatch(handler -> {
                PluginWrapper owner = pluginManager.whichPlugin(handler.getClass());
                return owner != null && HELLO_PLUGIN_ID.equals(owner.getPluginId());
            }));
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID));
    }

    @Test
    void shouldRestoreActivePluginWithFreshManagerInSameJvm() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        PluginRow installed = pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        Path snapshotPath = Path.of(installed.getJarPath());
        assertNotEquals(jarPath.toAbsolutePath(), snapshotPath);
        assertTrue(snapshotPath.startsWith(
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().resolve(".snapshots")
        ));
        assertEquals(snapshotPath.toString(),
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        PluginWrapper oldWrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(oldWrapper);
        PluginClassLoader oldClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );

        lifecycleGuard.beginDisable(HELLO_PLUGIN_ID);
        assertTrue(lifecycleGuard.awaitDrain(HELLO_PLUGIN_ID));
        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(HELLO_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        lifecycleGuard.remove(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertTrue(oldClassLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));

        WorkforcePluginManager restartedManager = new WorkforcePluginManager(
            pluginManager.getPluginsRoot().toString(),
            hostServices,
            permissionService
        );
        PluginLifecycleGuard restartedGuard = new PluginLifecycleGuard();
        PluginManagerService restartedService = new PluginManagerService(
            restartedManager,
            pluginRepository,
            signatureVerifier,
            versionService,
            flywayMigrator,
            restartedGuard
        );

        try {
            restartedService.restorePlugins();

            PluginWrapper restartedWrapper = restartedManager.getPlugin(HELLO_PLUGIN_ID);
            assertNotNull(restartedWrapper);
            PluginClassLoader restartedClassLoader = assertInstanceOf(
                PluginClassLoader.class,
                restartedManager.getPluginClassLoader(HELLO_PLUGIN_ID)
            );
            assertEquals(PluginStatus.ACTIVE,
                restartedService.getPlugin(HELLO_PLUGIN_ID).getStatus());
            assertEquals(PluginState.STARTED, restartedWrapper.getPluginState());
            assertNotSame(oldWrapper, restartedWrapper);
            assertNotSame(oldClassLoader, restartedClassLoader);

            var handlers = restartedManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID);
            assertEquals(1, handlers.size());
            assertEquals("hello.greet", handlers.get(0).getDescriptor().actionCode());
            assertTrue(restartedGuard.tryAcquire(HELLO_PLUGIN_ID));
            restartedGuard.release(HELLO_PLUGIN_ID);
            assertEquals(0, restartedGuard.getActiveCount(HELLO_PLUGIN_ID));

            restartedService.uninstall(HELLO_PLUGIN_ID);

            assertEquals(PluginStatus.UNINSTALLED,
                restartedService.getPlugin(HELLO_PLUGIN_ID).getStatus());
            assertNull(restartedManager.getPlugin(HELLO_PLUGIN_ID));
            assertTrue(restartedClassLoader.isClosed());
            assertFalse(restartedGuard.tryAcquire(HELLO_PLUGIN_ID));
        } finally {
            try {
                if (restartedManager.getPlugin(HELLO_PLUGIN_ID) != null) {
                    restartedGuard.beginDisable(HELLO_PLUGIN_ID);
                    restartedGuard.awaitDrain(HELLO_PLUGIN_ID);
                    try {
                        restartedManager.stopPlugin(HELLO_PLUGIN_ID);
                    } finally {
                        assertTrue(restartedManager.unloadPlugin(HELLO_PLUGIN_ID));
                    }
                }
            } finally {
                restartedGuard.remove(HELLO_PLUGIN_ID);
            }
        }
    }

    @Test
    void shouldRestoreActivePluginAcrossTwoIndependentJvmProcesses() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        PluginRow installed = pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        Path snapshotPath = Path.of(installed.getJarPath());
        Files.delete(jarPath);
        assertFalse(Files.exists(jarPath));
        assertTrue(Files.isRegularFile(snapshotPath));
        pluginManagerService.enable(HELLO_PLUGIN_ID);

        PluginClassLoader parentClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        lifecycleGuard.beginDisable(HELLO_PLUGIN_ID);
        assertTrue(lifecycleGuard.awaitDrain(HELLO_PLUGIN_ID));
        assertEquals(PluginState.STOPPED, pluginManager.stopPlugin(HELLO_PLUGIN_ID));
        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        lifecycleGuard.remove(HELLO_PLUGIN_ID);

        assertEquals(PluginStatus.ACTIVE,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertTrue(parentClassLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));

        PluginRow activeBeforeRestart = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        long parentPid = ProcessHandle.current().pid();
        long firstChildPid = runRestartProcessProbe("verify");
        assertNotEquals(parentPid, firstChildPid);
        PluginRow activeAfterFirstChild = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        assertEquals(PluginStatus.ACTIVE, activeAfterFirstChild.getStatus());
        assertEquals(activeBeforeRestart.getUpdatedAt(), activeAfterFirstChild.getUpdatedAt());
        assertTrue(Files.isRegularFile(snapshotPath));

        long secondChildPid = runRestartProcessProbe("uninstall");
        assertNotEquals(parentPid, secondChildPid);
        assertNotEquals(firstChildPid, secondChildPid);
        assertEquals(PluginStatus.UNINSTALLED,
            pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertTrue(Files.isRegularFile(snapshotPath));
    }

    @Test
    void shouldUnloadPluginWhenMigrationFails() throws Exception {
        Path jarPath = buildPluginJar(FAILURE_PLUGIN_ID, "THIS IS NOT VALID SQL;");

        assertThrows(
            PluginFlywayMigrator.PluginMigrationException.class,
            () -> pluginManagerService.install(jarPath, metadata("Migration Failure", jarPath))
        );

        assertNull(pluginManager.getPlugin(FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertFalse(hasMigrationTempDirectory(FAILURE_PLUGIN_ID));
    }

    @Test
    void shouldUnloadPluginWhenDependencyResolutionFailsBeforeLoadReturns() throws Exception {
        Path jarPath = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );

        PluginVersionService.PluginVersionException thrown = assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> pluginManagerService.install(jarPath, metadata("Dependency Failure", jarPath))
        );

        assertEquals("插件依赖或描述符校验失败", thrown.getMessage());
        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> DEPENDENCY_FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldRejectPf4jDependencyMissingFromSignedManifestBeforeMigration() throws Exception {
        Path jarPath = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "0.1.0",
            "CREATE TABLE " + DEPENDENCY_MANIFEST_GUARD_TABLE + " (id INT PRIMARY KEY);",
            null,
            "missing-required-plugin",
            null
        );

        PluginVersionService.PluginVersionException thrown = assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> pluginManagerService.install(jarPath, metadata("Dependency Mismatch", jarPath))
        );

        assertEquals("PF4J 插件描述符与 plugin.json 不一致", thrown.getMessage());
        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> DEPENDENCY_FAILURE_PLUGIN_ID.equals(plugin.getPluginId())));
        assertEquals(0, tableExists(DEPENDENCY_MANIFEST_GUARD_TABLE));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(DEPENDENCY_FAILURE_PLUGIN_ID)));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldUnloadPluginWhenRestoreDependencyResolutionFailsBeforeLoadReturns() throws Exception {
        Path jarPath = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, min_host_version, max_host_version,
                 java_version, type, status, jar_path, jar_sha256, signature, signer_id,
                 provider, installed_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
            """,
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "Dependency Restore Failure",
            "0.1.0",
            "0.1.0",
            "0.1.0",
            "0.2.0",
            "17",
            "FUNCTIONAL",
            PluginStatus.INSTALLED.name(),
            jarPath.toAbsolutePath().toString(),
            sha256(jarPath),
            sign(jarPath),
            TEST_SIGNER_ID,
            "Workforce Hub"
        );
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_dependency (plugin_id, depends_on)
            VALUES (?, 'missing-required-plugin')
            """, DEPENDENCY_FAILURE_PLUGIN_ID);

        pluginManagerService.restorePlugins();

        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        PluginRow failed = pluginManagerService.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID);
        assertEquals(PluginStatus.ERROR, failed.getStatus());
        assertNotEquals("插件清单与安装记录不一致", failed.getErrorMessage());
        assertEquals("插件依赖不可用: missing-required-plugin", failed.getErrorMessage());
    }

    @Test
    void shouldNotUnloadExistingUnresolvedDependentWhenFailedPluginCleanupRuns() throws Exception {
        Path dependentJar = buildPluginJar(
            UNRESOLVED_DEPENDENT_PLUGIN_ID,
            "SELECT 1;",
            DEPENDENCY_FAILURE_PLUGIN_ID
        );
        assertThrows(RuntimeException.class, () -> pluginManager.loadPlugin(dependentJar));
        assertNotNull(pluginManager.getPlugin(UNRESOLVED_DEPENDENT_PLUGIN_ID));

        Path failedJar = buildPluginJar(
            DEPENDENCY_FAILURE_PLUGIN_ID,
            "SELECT 1;",
            "missing-required-plugin"
        );
        PluginVersionService.PluginVersionException thrown = assertThrows(
            PluginVersionService.PluginVersionException.class,
            () -> pluginManagerService.install(failedJar, metadata("Dependency Failure", failedJar))
        );

        assertEquals("插件依赖或描述符校验失败", thrown.getMessage());
        assertEquals(0, thrown.getSuppressed().length);
        assertNotNull(pluginManager.getPlugin(UNRESOLVED_DEPENDENT_PLUGIN_ID));
        assertNotNull(pluginManager.getPluginClassLoader(UNRESOLVED_DEPENDENT_PLUGIN_ID));
        assertNull(pluginManager.getPlugin(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(DEPENDENCY_FAILURE_PLUGIN_ID));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldRejectExistingPluginBeforeMigration() throws Exception {
        jdbcTemplate.update("""
            INSERT INTO sys_plugin
                (plugin_id, name, version, sdk_version, java_version, type, status, jar_path)
            VALUES (?, 'Existing Plugin', '0.1.0', '1.0.0', '17', 'FUNCTIONAL', 'INSTALLED', 'existing.jar')
            """, PERSISTENCE_FAILURE_PLUGIN_ID);
        Path jarPath = buildPluginJar(
            PERSISTENCE_FAILURE_PLUGIN_ID,
            "CREATE TABLE " + PERSISTENCE_GUARD_TABLE + " (id INT PRIMARY KEY);"
        );

        assertThrows(
            IllegalStateException.class,
            () -> pluginManagerService.install(jarPath, metadata("Persistence Failure", jarPath))
        );

        assertNull(pluginManager.getPlugin(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            PERSISTENCE_FAILURE_PLUGIN_ID
        ));
        assertEquals(0, tableExists(PERSISTENCE_GUARD_TABLE));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(PERSISTENCE_FAILURE_PLUGIN_ID));
    }

    @Test
    void shouldUnloadAndDeleteSnapshotWhenPersistenceFailsAfterMigration() throws Exception {
        Path jarPath = buildPluginJar(
            PERSISTENCE_FAILURE_PLUGIN_ID,
            "CREATE TABLE " + PERSISTENCE_GUARD_TABLE + " (id INT PRIMARY KEY);"
        );
        doThrow(new DataAccessResourceFailureException("simulated insert failure"))
            .when(pluginRepository)
            .insert(argThat(row -> PERSISTENCE_FAILURE_PLUGIN_ID.equals(row.getPluginId())));

        assertThrows(
            DataAccessResourceFailureException.class,
            () -> pluginManagerService.install(jarPath, metadata("Persistence Failure", jarPath))
        );

        verify(pluginRepository).insert(argThat(
            row -> PERSISTENCE_FAILURE_PLUGIN_ID.equals(row.getPluginId())
        ));
        assertNull(pluginManager.getPlugin(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            PERSISTENCE_FAILURE_PLUGIN_ID
        ));
        assertEquals(1, tableExists(PERSISTENCE_GUARD_TABLE));
        assertEquals(1, tableExists(flywayMigrator.getHistoryTable(PERSISTENCE_FAILURE_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(PERSISTENCE_FAILURE_PLUGIN_ID));
        assertSnapshotDirectoryEmpty();
    }

    @Test
    void shouldRejectTamperedJarBeforePf4jLoad() throws Exception {
        Path jarPath = buildPluginJar(TAMPERED_PLUGIN_ID, "SELECT 1;");
        PluginRow metadata = metadata("Tampered Plugin", jarPath);
        Files.writeString(jarPath, "tampered");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> pluginManagerService.install(jarPath, metadata)
        );

        assertNull(pluginManager.getPlugin(TAMPERED_PLUGIN_ID));
        assertFalse(pluginManagerService.listPlugins().stream()
            .anyMatch(plugin -> TAMPERED_PLUGIN_ID.equals(plugin.getPluginId())));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(TAMPERED_PLUGIN_ID)));
        assertFalse(hasMigrationTempDirectory(TAMPERED_PLUGIN_ID));
    }

    @ParameterizedTest
    @EnumSource(ManifestDrift.class)
    void shouldFailClosedBeforePf4jLoadWhenRealUiManifestPersistenceDrifts(
            ManifestDrift drift) throws Exception {
        Path jarPath = buildRealHelloPluginJar();

        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginRow persisted = pluginRepository.findById(HELLO_PLUGIN_ID).orElseThrow();
        assertEquals("UI", persisted.getType());
        assertEquals("Workforce Hub", persisted.getProvider());
        assertEquals("/plugins/hello-plugin/index.js", persisted.getFrontendEntry());
        assertEquals(HELLO_PLUGIN_FRONTEND_SRI, persisted.getFrontendSha384());
        assertEquals(
            "com.workforcehub.plugin.hello.HelloPlugin",
            pluginManager.getPlugin(HELLO_PLUGIN_ID).getPlugin().getClass().getName()
        );

        assertTrue(pluginManager.unloadPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        clearInvocations(pluginManager);
        jdbcTemplate.update(
            "UPDATE sys_plugin SET " + drift.column + " = ? WHERE plugin_id = ?",
            drift.value,
            HELLO_PLUGIN_ID
        );

        pluginManagerService.restorePlugins();

        PluginRow failed = pluginRepository.findById(HELLO_PLUGIN_ID).orElseThrow();
        assertEquals(PluginStatus.ERROR, failed.getStatus());
        assertEquals("插件清单与安装记录不一致", failed.getErrorMessage());
        verify(pluginManager, never()).loadPlugin(any(Path.class));
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
    }

    @Test
    void adminHttpUploadShouldInstallSignedJarAndRejectSameIdReplacement() throws Exception {
        Path jarPath = buildPluginJar(UPLOAD_PLUGIN_ID, "SELECT 1;");
        byte[] jarBytes = Files.readAllBytes(jarPath);
        byte[] signature = sign(jarPath);

        MvcResult installedResult = mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(new MockMultipartFile(
                    "jar", "plugin.jar", "application/java-archive", jarBytes))
                .file(new MockMultipartFile(
                    "signature", "plugin.sig", "application/octet-stream", signature))
                .param("signerId", TEST_SIGNER_ID)
                .param("name", "HTTP Upload Plugin")
                .param("description", "HTTP upload integration test")
                .with(adminPluginManager())
                .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, installedResult.getResponse().getStatus());
        assertEquals(UPLOAD_PLUGIN_ID, JsonPath.read(
            installedResult.getResponse().getContentAsString(), "$.data.id"));
        assertEquals("INSTALLED", JsonPath.read(
            installedResult.getResponse().getContentAsString(), "$.data.status"));
        assertFalse(installedResult.getResponse().getContentAsString().contains("jarPath"));
        assertFalse(installedResult.getResponse().getContentAsString().contains("jarSha256"));
        assertFalse(installedResult.getResponse().getContentAsString().contains("signerId"));
        assertFalse(installedResult.getResponse().getContentAsString().contains("signature"));

        PluginRow installed = pluginManagerService.getPlugin(UPLOAD_PLUGIN_ID);
        assertEquals(PluginStatus.INSTALLED, installed.getStatus());
        assertEquals(TEST_SIGNER_ID, installed.getSignerId());
        assertEquals(sha256(jarPath), installed.getJarSha256());
        assertNotNull(pluginManager.getPlugin(UPLOAD_PLUGIN_ID));
        assertEquals(1, tableExists(flywayMigrator.getHistoryTable(UPLOAD_PLUGIN_ID)));
        assertEquals(1L, snapshotCount());

        MvcResult duplicateResult = mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(new MockMultipartFile(
                    "jar", "plugin.jar", "application/java-archive", jarBytes))
                .file(new MockMultipartFile(
                    "signature", "plugin.sig", "application/octet-stream", signature))
                .param("signerId", TEST_SIGNER_ID)
                .with(adminPluginManager())
                .with(csrf().asHeader()))
            .andReturn();

        assertEquals(409, duplicateResult.getResponse().getStatus());
        assertEquals("插件 ID 已存在，上传接口不支持替换安装", JsonPath.read(
            duplicateResult.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.message"));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            UPLOAD_PLUGIN_ID
        ));
        assertEquals(1L, snapshotCount());
        assertEquals(
            List.of("PLUGIN_UPLOADED:SUCCESS", "PLUGIN_UPLOADED:BLOCKED"),
            jdbcTemplate.query("""
                SELECT action_type, result
                FROM sys_audit_log
                WHERE target_type = 'PLUGIN'
                  AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
                ORDER BY occurred_at, id
                """, (rs, rowNum) -> rs.getString("action_type") + ":" + rs.getString("result"),
                UPLOAD_PLUGIN_ID)
        );
    }

    @Test
    void adminHttpUploadShouldRejectInvalidSignatureWithoutResidue() throws Exception {
        Path jarPath = buildPluginJar(UPLOAD_PLUGIN_ID, "SELECT 1;");
        byte[] invalidSignature = sign(jarPath);
        invalidSignature[0] ^= 1;

        MvcResult result = mockMvc.perform(multipart("/api/admin/plugins/upload")
                .file(new MockMultipartFile(
                    "jar", "plugin.jar", "application/java-archive", Files.readAllBytes(jarPath)))
                .file(new MockMultipartFile(
                    "signature", "plugin.sig", "application/octet-stream", invalidSignature))
                .param("signerId", TEST_SIGNER_ID)
                .with(adminPluginManager())
                .with(csrf().asHeader()))
            .andReturn();

        assertEquals(422, result.getResponse().getStatus());
        assertEquals("插件签名校验失败", JsonPath.read(
            result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.message"));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_plugin WHERE plugin_id = ?",
            Integer.class,
            UPLOAD_PLUGIN_ID
        ));
        assertNull(pluginManager.getPlugin(UPLOAD_PLUGIN_ID));
        assertEquals(0, tableExists(flywayMigrator.getHistoryTable(UPLOAD_PLUGIN_ID)));
        assertEquals(0L, snapshotCount());
    }

    @Test
    void adminHttpShouldUninstallInstalledPluginWithoutStartingIt() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        assertEquals(PluginState.RESOLVED, wrapper.getPluginState());
        PluginClassLoader classLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        clearInvocations(pluginManager);

        MvcResult uninstallResult = mockMvc.perform(
                delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, uninstallResult.getResponse().getStatus());
        assertEquals("UNINSTALLED", JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.status"));
        LocalDateTime responseUpdatedAt = LocalDateTime.parse(JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.updatedAt"));
        PluginRow persisted = pluginManagerService.getPlugin(HELLO_PLUGIN_ID);
        assertEquals(PluginStatus.UNINSTALLED, persisted.getStatus());
        assertEquals(persisted.getUpdatedAt(), responseUpdatedAt);
        verify(pluginManager, never()).stopPlugin(HELLO_PLUGIN_ID);
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(classLoader.isClosed());
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
    }

    @Test
    void adminHttpShouldKeepPluginRecoverableWhenClassLoaderCloseFails() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));

        PluginClassLoader originalClassLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        PluginClassLoader failingClassLoader = mock(PluginClassLoader.class);
        doThrow(new IOException("close failed")).when(failingClassLoader).close();

        MvcResult failedResult;
        try {
            doReturn(failingClassLoader)
                .when(pluginManager)
                .getPluginClassLoader(HELLO_PLUGIN_ID);
            failedResult = mockMvc.perform(
                    delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                        .with(adminPluginManager())
                        .with(csrf().asHeader()))
                .andReturn();
        } finally {
            doCallRealMethod()
                .when(pluginManager)
                .getPluginClassLoader(HELLO_PLUGIN_ID);
        }

        assertEquals(500, failedResult.getResponse().getStatus());
        assertEquals(PluginStatus.ERROR, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNotNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertSame(originalClassLoader, pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertFalse(originalClassLoader.isClosed());

        MvcResult retryResult = mockMvc.perform(
                delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, retryResult.getResponse().getStatus());
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(originalClassLoader.isClosed());
    }

    @Test
    void adminHttpLifecycleShouldDrainAndUnloadRealPlugin() throws Exception {
        Path jarPath = buildRealHelloPluginJar();
        pluginManagerService.install(jarPath, metadata("Hello Plugin", jarPath));
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_capability (plugin_id, capability_code)
            VALUES (?, 'HELLO_GREET')
            """, HELLO_PLUGIN_ID);
        jdbcTemplate.update("""
            INSERT INTO sys_plugin_action_permission (plugin_id, action_code, capability_code)
            VALUES (?, 'hello.greet', 'HELLO_GREET')
            """, HELLO_PLUGIN_ID);

        MvcResult enableResult = mockMvc.perform(
                put("/api/admin/plugins/{pluginId}/enable", HELLO_PLUGIN_ID)
                    .with(adminPluginManager())
                    .with(csrf().asHeader()))
            .andReturn();

        assertEquals(200, enableResult.getResponse().getStatus());
        assertEquals("ACTIVE", JsonPath.read(
            enableResult.getResponse().getContentAsString(), "$.data.status"));
        assertEquals(PluginStatus.ACTIVE, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        PluginWrapper wrapper = pluginManager.getPlugin(HELLO_PLUGIN_ID);
        assertNotNull(wrapper);
        assertEquals(PluginState.STARTED, wrapper.getPluginState());
        PluginClassLoader classLoader = assertInstanceOf(
            PluginClassLoader.class,
            pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID)
        );
        assertFalse(classLoader.isClosed());
        assertEquals(1, pluginManager.getExtensions(PluginActionHandler.class, HELLO_PLUGIN_ID).size());

        // 模拟已经越过网关 acquire 的在途请求，后续新请求仍通过真实 MockMvc 网关验证拒绝。
        assertTrue(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        CompletableFuture<MvcResult> uninstallFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(delete("/api/admin/plugins/{pluginId}", HELLO_PLUGIN_ID)
                        .with(adminPluginManager())
                        .with(csrf().asHeader()))
                    .andReturn();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        PluginStatus statusWhileDraining;
        boolean uninstallCompletedWhileHeld;
        MvcResult rejectedResult;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus() != PluginStatus.DISABLING
                    && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(10);
            }
            statusWhileDraining = pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus();
            uninstallCompletedWhileHeld = uninstallFuture.isDone();
            rejectedResult = mockMvc.perform(post(
                    "/api/plugin-gateway/{pluginId}/actions/{actionCode}",
                    HELLO_PLUGIN_ID,
                    "hello.greet")
                    .with(adminPluginManager())
                    .with(csrf().asHeader())
                    .header(HttpHeaders.ORIGIN, "http://localhost")
                    .header("Idempotency-Key", "lifecycle-drain-key")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"test\"}"))
                .andReturn();
        } finally {
            lifecycleGuard.release(HELLO_PLUGIN_ID);
        }

        MvcResult uninstallResult = uninstallFuture.get(10, TimeUnit.SECONDS);
        assertEquals(PluginStatus.DISABLING, statusWhileDraining);
        assertFalse(uninstallCompletedWhileHeld);
        assertEquals(409, rejectedResult.getResponse().getStatus());
        assertEquals(100409, ((Number) JsonPath.read(
            rejectedResult.getResponse().getContentAsString(), "$.code")).intValue());
        assertEquals(200, uninstallResult.getResponse().getStatus());
        assertEquals("UNINSTALLED", JsonPath.read(
            uninstallResult.getResponse().getContentAsString(), "$.data.status"));
        assertEquals(PluginStatus.UNINSTALLED, pluginManagerService.getPlugin(HELLO_PLUGIN_ID).getStatus());
        assertNull(pluginManager.getPlugin(HELLO_PLUGIN_ID));
        assertNull(pluginManager.getPluginClassLoader(HELLO_PLUGIN_ID));
        assertTrue(classLoader.isClosed());
        assertTrue(pluginManager.getExtensions(PluginActionHandler.class).stream()
            .noneMatch(handler -> {
                PluginWrapper owner = pluginManager.whichPlugin(handler.getClass());
                return owner != null && HELLO_PLUGIN_ID.equals(owner.getPluginId());
            }));
        assertFalse(lifecycleGuard.tryAcquire(HELLO_PLUGIN_ID));
        assertEquals(0, lifecycleGuard.getActiveCount(HELLO_PLUGIN_ID));
        assertEquals(
            List.of("PLUGIN_ENABLED:SUCCESS", "PLUGIN_UNINSTALLED:SUCCESS"),
            jdbcTemplate.query("""
                SELECT action_type, result
                FROM sys_audit_log
                WHERE target_type = 'PLUGIN'
                  AND JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.pluginId')) = ?
                ORDER BY occurred_at, id
                """, (rs, rowNum) -> rs.getString("action_type") + ":" + rs.getString("result"),
                HELLO_PLUGIN_ID)
        );
    }

    private RequestPostProcessor adminPluginManager() {
        AdminUserDetails principal = new AdminUserDetails(
            8_001L,
            "admin.demo",
            "unused",
            "插件生命周期管理员",
            "ACTIVE",
            List.of(),
            List.of("ADMIN_PLUGIN_MANAGE")
        );
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
            principal,
            null,
            principal.getAuthorities()
        ));
    }

    private PluginRow metadata(String name, Path jarPath) throws Exception {
        PluginRow row = new PluginRow();
        row.setName(name);
        row.setSignerId(TEST_SIGNER_ID);
        row.setSignature(sign(jarPath));
        return row;
    }

    private static KeyPair createSigningKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private byte[] sign(Path jarPath) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(SIGNING_KEY.getPrivate());
        signer.update(Files.readAllBytes(jarPath));
        return signer.sign();
    }

    private String sha256(Path jarPath) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jarPath))
        );
    }

    private Path buildPluginJar(String pluginId, String migrationSql) throws IOException {
        return buildPluginJar(pluginId, migrationSql, null);
    }

    private Path buildPluginJar(String pluginId, String migrationSql, String dependencies) throws IOException {
        return buildPluginJar(pluginId, "0.1.0", migrationSql, null, dependencies);
    }

    private Path buildPluginJar(
            String pluginId,
            String version,
            String migrationSql,
            String secondMigrationSql,
            String dependencies) throws IOException {
        String manifestDependencies = dependencies == null || dependencies.isBlank()
            ? null
            : "[{\"pluginId\":\"" + dependencies + "\"}]";
        return buildPluginJar(
            pluginId,
            version,
            migrationSql,
            secondMigrationSql,
            dependencies,
            manifestDependencies
        );
    }

    private Path buildPluginJar(
            String pluginId,
            String version,
            String migrationSql,
            String secondMigrationSql,
            String dependencies,
            String manifestDependencies) throws IOException {
        Path source = Path.of("..", "plugins", "hello-plugin", "src", "main", "java",
            "com", "workforcehub", "plugin", "hello", "HelloPlugin.java").toAbsolutePath().normalize();
        Path classes = tempDir.resolve(pluginId + "-" + version + "-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            source.toString()
        );
        assertEquals(0, result, "编译失败");

        Path properties = classes.resolve("plugin.properties");
        Files.writeString(properties, """
            plugin.id=%s
            plugin.class=com.workforcehub.plugin.hello.HelloPlugin
            plugin.version=%s
            plugin.provider=Workforce Hub
            plugin.description=Plugin migration integration test
            plugin.dependencies=%s
            """.formatted(pluginId, version, dependencies == null ? "" : dependencies));

        Path jar = tempDir.resolve(pluginId + "-" + version + ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            try (var files = Files.walk(classes)) {
                for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                    String entryName = classes.relativize(file).toString().replace('\\', '/');
                    output.putNextEntry(new JarEntry(entryName));
                    Files.copy(file, output);
                    output.closeEntry();
                }
            }
            output.putNextEntry(new JarEntry("db/migration/V1__init.sql"));
            output.write(migrationSql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
            if (secondMigrationSql != null) {
                output.putNextEntry(new JarEntry("db/migration/V2__replace.sql"));
                output.write(secondMigrationSql.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry("plugin.json"));
            String manifestDependencyField = manifestDependencies == null
                ? ""
                : ",\n  \"dependencies\":" + manifestDependencies;
            output.write("""
                {
                  "id":"%s",
                  "version":"%s",
                  "sdkVersion":"0.1.0",
                  "minHostVersion":"0.1.0",
                  "maxHostVersion":"0.2.0",
                  "javaVersion":"17",
                  "type":"FUNCTIONAL",
                  "provider":"Workforce Hub"%s
                }
                """.formatted(pluginId, version, manifestDependencyField)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }

    private Path buildRealHelloPluginJar() throws IOException {
        Path pluginSource = Path.of("..", "plugins", "hello-plugin", "src", "main")
            .toAbsolutePath()
            .normalize();
        Path javaSource = pluginSource.resolve("java/com/workforcehub/plugin/hello");
        Path classes = tempDir.resolve("hello-plugin-real-classes");
        Files.createDirectories(classes);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "需要 JDK");
        int result = compiler.run(
            null, null, null,
            "-classpath", System.getProperty("java.class.path"),
            "-d", classes.toString(),
            javaSource.resolve("HelloPlugin.java").toString(),
            javaSource.resolve("HelloActionHandler.java").toString()
        );
        assertEquals(0, result, "编译失败");

        Path resources = pluginSource.resolve("resources");
        Files.copy(resources.resolve("plugin.properties"), classes.resolve("plugin.properties"));
        Files.copy(resources.resolve("plugin.json"), classes.resolve("plugin.json"));

        Path jar = tempDir.resolve("hello-plugin-real.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String entryName = classes.relativize(file).toString().replace('\\', '/');
                output.putNextEntry(new JarEntry(entryName));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private long runRestartProcessProbe(String mode) throws Exception {
        String configuredClasspath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        Path workingDirectory = Files.createDirectory(tempDir.resolve("restart-process-" + mode));
        Path launchDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        String childClasspath = Arrays.stream(configuredClasspath.split(File.pathSeparator))
            .map(Path::of)
            .map(path -> path.isAbsolute() ? path : launchDirectory.resolve(path).normalize())
            .map(Path::toString)
            .collect(Collectors.joining(File.pathSeparator));
        ProcessBuilder processBuilder = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            childClasspath,
            "com.workforcehub.plugin.PluginRestartProcessProbe",
            mode,
            "--spring.config.location=optional:classpath:/plugin-restart-process-none.properties",
            "--mybatis.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl"
        );
        processBuilder.directory(workingDirectory.toFile());
        processBuilder.redirectErrorStream(true);

        var childEnvironment = processBuilder.environment();
        childEnvironment.clear();
        childEnvironment.put("SPRING_PROFILES_ACTIVE", "api");
        childEnvironment.put("SPRING_MAIN_BANNER_MODE", "off");
        childEnvironment.put("SERVER_PORT", "0");
        String datasourceUrl = environment.getRequiredProperty("spring.datasource.url");
        String datasourceUsername = environment.getRequiredProperty("spring.datasource.username");
        String datasourcePassword = environment.getRequiredProperty("spring.datasource.password");
        String redisHost = environment.getRequiredProperty("spring.data.redis.host");
        String redisPort = environment.getRequiredProperty("spring.data.redis.port");
        String redisPassword = environment.getProperty("spring.data.redis.password", "");
        childEnvironment.put("SPRING_DATASOURCE_URL", datasourceUrl);
        childEnvironment.put("SPRING_DATASOURCE_USERNAME", datasourceUsername);
        childEnvironment.put("SPRING_DATASOURCE_PASSWORD", datasourcePassword);
        childEnvironment.put("SPRING_DATA_REDIS_HOST", redisHost);
        childEnvironment.put("SPRING_DATA_REDIS_PORT", redisPort);
        childEnvironment.put("SPRING_DATA_REDIS_PASSWORD", redisPassword);
        childEnvironment.put("SPRING_SESSION_REDIS_NAMESPACE",
            environment.getRequiredProperty("spring.session.redis.namespace"));
        childEnvironment.put("WORKFORCE_AUTH_REDIS_KEY_PREFIX",
            environment.getRequiredProperty("workforce.auth.redis-key-prefix"));
        childEnvironment.put("SPRING_AUTOCONFIGURE_EXCLUDE",
            environment.getRequiredProperty("spring.autoconfigure.exclude"));
        childEnvironment.put("WORKFORCE_PLUGIN_ROOT",
            pluginManager.getPluginsRoot().toAbsolutePath().normalize().toString());
        String trustedSigners = TEST_SIGNER_ID + "="
            + Base64.getEncoder().encodeToString(SIGNING_KEY.getPublic().getEncoded());
        childEnvironment.put("WORKFORCE_PLUGIN_TRUSTED_SIGNERS", trustedSigners);
        childEnvironment.put("WORKFORCE_WORKFLOW_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AUTH_EMAIL_VERIFY_ENABLED", "false");
        childEnvironment.put("MANAGEMENT_HEALTH_MAIL_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_MODEL_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_TIMEOUT_SCAN_ENABLED", "false");
        childEnvironment.put("WORKFORCE_AGENT_PROVIDER_HEALTH_CHECK_ENABLED", "false");
        childEnvironment.put("WORKFORCE_IM_OUTBOX_DISPATCHER_ENABLED", "false");
        childEnvironment.put("FLOWABLE_ASYNC_EXECUTOR_ACTIVATE", "false");
        childEnvironment.put("WORKFORCE_PLUGIN_GATEWAY_CSRF_ALLOWED_ORIGINS", "http://127.0.0.1:8080");
        String mailHost = environment.getRequiredProperty("spring.mail.host");
        String mailPort = environment.getRequiredProperty("spring.mail.port");
        String mailUsername = environment.getProperty("spring.mail.username", "");
        String mailPassword = environment.getProperty("spring.mail.password", "");
        childEnvironment.put("SPRING_MAIL_HOST", mailHost);
        childEnvironment.put("SPRING_MAIL_PORT", mailPort);
        childEnvironment.put("SPRING_MAIL_USERNAME", mailUsername);
        childEnvironment.put("SPRING_MAIL_PASSWORD", mailPassword);
        String minioEndpoint = environment.getRequiredProperty("minio.endpoint");
        String minioAccessKey = environment.getRequiredProperty("minio.access-key");
        String minioSecretKey = environment.getRequiredProperty("minio.secret-key");
        String minioBucket = environment.getRequiredProperty("minio.bucket");
        childEnvironment.put("MINIO_ENDPOINT", minioEndpoint);
        childEnvironment.put("MINIO_ACCESS_KEY", minioAccessKey);
        childEnvironment.put("MINIO_SECRET_KEY", minioSecretKey);
        childEnvironment.put("MINIO_BUCKET", minioBucket);
        childEnvironment.put("WORKFORCE_FILE_LOCAL_ROOT",
            tempDir.resolve("restart-process-file-storage").toAbsolutePath().toString());
        childEnvironment.put("LOGGING_LEVEL_ROOT", "warn");
        childEnvironment.put("LOGGING_LEVEL_COM_WORKFORCEHUB", "info");

        List<String> sensitiveValues = new ArrayList<>();
        for (String value : List.of(
            datasourceUrl, datasourceUsername, datasourcePassword,
            redisHost, redisPassword, trustedSigners,
            mailHost, mailPort, mailUsername, mailPassword,
            minioEndpoint, minioAccessKey, minioSecretKey, minioBucket
        )) {
            if (value != null && !value.isBlank()) {
                sensitiveValues.add(value);
            }
        }
        sensitiveValues.sort(Comparator.comparingInt(String::length).reversed());

        Process process = processBuilder.start();
        long pid = process.pid();
        SanitizedLogTail logTail = new SanitizedLogTail(8192);
        Thread logReader = new Thread(
            () -> captureSanitizedLog(process, sensitiveValues, logTail),
            "plugin-restart-process-" + mode + "-log"
        );
        logReader.setDaemon(true);
        logReader.start();
        try {
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("插件重启子进程强制终止后仍存活，mode=" + mode);
                    }
                }
                awaitLogReaderTermination(process, logReader, mode);
                throw new AssertionError(
                    "插件重启子进程超时，mode=" + mode + "\n" + logTail.text()
                );
            }
            awaitLogReaderTermination(process, logReader, mode);
            assertEquals(
                0,
                process.exitValue(),
                () -> "插件重启子进程失败，mode=" + mode + "\n" + logTail.text()
            );
            return pid;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("插件重启子进程清理后仍存活，mode=" + mode);
                }
            }
            awaitLogReaderTermination(process, logReader, mode);
        }
    }

    private void awaitLogReaderTermination(Process process, Thread logReader, String mode)
        throws Exception {
        if (!logReader.isAlive()) {
            return;
        }
        try {
            process.getInputStream().close();
        } catch (IOException ignored) {
            // 子进程已结束时，关闭其输出管道失败不影响清理判定。
        }
        logReader.interrupt();
        logReader.join(5_000);
        if (logReader.isAlive()) {
            throw new AssertionError("插件重启子进程日志线程清理后仍存活，mode=" + mode);
        }
    }

    private void captureSanitizedLog(
        Process process,
        List<String> sensitiveValues,
        SanitizedLogTail logTail
    ) {
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String sanitized = line;
                for (String sensitiveValue : sensitiveValues) {
                    sanitized = sanitized.replace(sensitiveValue, "<redacted>");
                }
                logTail.append(sanitized);
                logTail.append(System.lineSeparator());
            }
        } catch (IOException e) {
            logTail.append("<无法读取子进程日志>");
        }
    }

    private static final class SanitizedLogTail {

        private final byte[] bytes;
        private int length;

        private SanitizedLogTail(int maxBytes) {
            this.bytes = new byte[maxBytes];
        }

        private synchronized void append(String value) {
            byte[] incoming = value.getBytes(StandardCharsets.UTF_8);
            if (incoming.length >= bytes.length) {
                System.arraycopy(incoming, incoming.length - bytes.length, bytes, 0, bytes.length);
                length = bytes.length;
                return;
            }
            int overflow = Math.max(0, length + incoming.length - bytes.length);
            if (overflow > 0) {
                System.arraycopy(bytes, overflow, bytes, 0, length - overflow);
                length -= overflow;
            }
            System.arraycopy(incoming, 0, bytes, length, incoming.length);
            length += incoming.length;
        }

        private synchronized String text() {
            String text = new String(bytes, 0, length, StandardCharsets.UTF_8);
            while (text.startsWith("\uFFFD")) {
                text = text.substring(1);
            }
            while (text.endsWith("\uFFFD")) {
                text = text.substring(0, text.length() - 1);
            }
            byte[] encoded = text.getBytes(StandardCharsets.UTF_8);
            while (encoded.length > bytes.length && !text.isEmpty()) {
                text = text.substring(1);
                encoded = text.getBytes(StandardCharsets.UTF_8);
            }
            return text;
        }
    }

    private int tableExists(String tableName) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """, Integer.class, tableName);
    }

    private void dropTable(String tableName) {
        jdbcTemplate.execute("DROP TABLE IF EXISTS `" + tableName + "`");
    }

    private void unloadIfPresent(String pluginId) {
        if (pluginManager.getPlugin(pluginId) != null) {
            pluginManager.stopPlugin(pluginId);
            pluginManager.unloadPlugin(pluginId);
        }
        lifecycleGuard.remove(pluginId);
    }

    private boolean hasMigrationTempDirectory(String pluginId) throws IOException {
        String prefix = "wf_plugin_migration_" + flywayMigrator.getHistoryTable(pluginId)
            .substring("flyway_plugin_".length()) + "_";
        try (var paths = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return paths.anyMatch(path -> path.getFileName().toString().startsWith(prefix));
        }
    }

    private long snapshotCount() throws IOException {
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        if (!Files.isDirectory(snapshotDirectory)) {
            return 0;
        }
        try (var snapshots = Files.list(snapshotDirectory)) {
            return snapshots.filter(Files::isRegularFile).count();
        }
    }

    private void clearTestSnapshots() {
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        if (!Files.isDirectory(snapshotDirectory)) {
            return;
        }
        try (var snapshots = Files.list(snapshotDirectory)) {
            for (Path snapshot : snapshots.filter(Files::isRegularFile).toList()) {
                Files.deleteIfExists(snapshot);
            }
        } catch (IOException e) {
            throw new IllegalStateException("清理测试插件快照失败", e);
        }
    }

    private void assertSnapshotDirectoryEmpty() throws IOException {
        Path snapshotDirectory = pluginManager.getPluginsRoot()
            .toAbsolutePath()
            .normalize()
            .resolve(".snapshots");
        assertTrue(Files.isDirectory(snapshotDirectory));
        try (var snapshots = Files.list(snapshotDirectory)) {
            assertEquals(0, snapshots.filter(Files::isRegularFile).count());
        }
    }

    private enum ManifestDrift {
        TYPE("type", "FUNCTIONAL"),
        PROVIDER("provider", "Tampered Provider"),
        FRONTEND_ENTRY("frontend_entry", "/plugins/tampered/index.js"),
        FRONTEND_SHA384("frontend_sha384", "sha384-" + "B".repeat(64));

        private final String column;
        private final String value;

        ManifestDrift(String column, String value) {
            this.column = column;
            this.value = value;
        }
    }
}
WFH_99_2_BASELINE_13
rm -f "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityService.java"
rm -f "$target/server/src/main/java/com/workforcehub/auth/security/SessionValidityFilter.java"
rm -f "$target/server/src/main/java/com/workforcehub/im/websocket/ImWebSocketAuthentication.java"
rm -f "$target/server/src/test/java/com/workforcehub/auth/SessionValidityRequestChainTest.java"
echo "ROLLBACK restored pristine 99-2 files; unrelated paths preserved"
