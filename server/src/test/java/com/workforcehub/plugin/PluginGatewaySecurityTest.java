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
            .andExpect(jsonPath("$.code").value(4003));

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
            .andExpect(jsonPath("$.code").value(4003));

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
            .andExpect(jsonPath("$.code").value(4003));
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
            .andExpect(jsonPath("$.code").value(4004));
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
            .andExpect(jsonPath("$.code").value(4009));
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
            .andExpect(jsonPath("$.code").value(4004));

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
            .andExpect(jsonPath("$.code").value(4003));

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
            .andExpect(jsonPath("$.code").value(1000));

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
            .andExpect(jsonPath("$.code").value(3002))
            .andExpect(jsonPath("$.retryable").value(true));
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
            .andExpect(jsonPath("$.code").value(1000))
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
            .andExpect(jsonPath("$.code").value(ErrorCodes.INTERNAL_ERROR))
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
                .andExpect(jsonPath("$.code").value(ErrorCodes.INTERNAL_ERROR))
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
            .andExpect(jsonPath("$.code").value(4009));

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
            .andExpect(jsonPath("$.code").value(ErrorCodes.INTERNAL_ERROR));

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
            .andExpect(jsonPath("$.code").value(4009));

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
