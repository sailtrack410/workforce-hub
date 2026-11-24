package com.workforcehub.auth;

import com.workforcehub.auth.controller.EmployeeAuthController;
import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.auth.mapper.OrgUserMapper;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.auth.service.AccountService;
import com.workforcehub.auth.service.AdminPermissionService;
import com.workforcehub.auth.service.EmailCodeService;
import com.workforcehub.auth.service.LoginService;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountSelfServiceTest {
    private final AuthUserAccountMapper accounts = mock(AuthUserAccountMapper.class);
    private final EmailCodeService codes = mock(EmailCodeService.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private AuthUserAccount account;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        account = new AuthUserAccount(); account.setUserId(1L); account.setPasswordHash(encoder.encode("Fixture@2026"));
        when(accounts.selectByUserId(1L)).thenReturn(account);
        var service = new AccountService(accounts, mock(AdminPermissionService.class), mock(OrgUserMapper.class), codes, encoder);
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeAuthController(mock(LoginService.class), service, codes))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
        var user = new EmployeeUserDetails(1L, 10L, "fixture", "", "fixture", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void passwordChangeChecksCurrentPasswordAndPersistsEncodedReplacement() throws Exception {
        mvc.perform(post("/api/employee/auth/password/change").contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"Fixture@2026\",\"newPassword\":\"Updated@2026\"}")).andExpect(status().isOk());
        assertTrue(encoder.matches("Updated@2026", account.getPasswordHash()));
        assertFalse(encoder.matches("Fixture@2026", account.getPasswordHash()));
        assertNotNull(account.getPasswordUpdatedAt()); verify(accounts).updateById(account);
    }

    @Test
    void invalidCurrentPasswordDoesNotWrite() throws Exception {
        mvc.perform(post("/api/employee/auth/password/change").contentType(MediaType.APPLICATION_JSON)
            .content("{\"currentPassword\":\"incorrect\",\"newPassword\":\"Updated@2026\"}")).andExpect(status().isBadRequest());
        verify(accounts, never()).updateById(any());
    }

    @Test
    void sameOrShortReplacementDoesNotWrite() throws Exception {
        for (String next : new String[]{"Fixture@2026", "short"}) {
            mvc.perform(post("/api/employee/auth/password/change").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Fixture@2026\",\"newPassword\":\"" + next + "\"}")).andExpect(status().isBadRequest());
        }
        verify(accounts, never()).updateById(any());
    }

    @Test
    void recoveryRequiresValidEmailCodeBeforeWriting() throws Exception {
        mvc.perform(post("/api/employee/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"fixture@example.test\",\"code\":\"invalid\",\"newPassword\":\"Updated@2026\"}")).andExpect(status().isBadRequest());
        verify(accounts, never()).updateById(any());
        when(codes.verifyCode("fixture@example.test", "RESET_PASSWORD", "valid")).thenReturn(true);
        when(accounts.selectByEmail("fixture@example.test")).thenReturn(account);
        mvc.perform(post("/api/employee/auth/password/reset").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"fixture@example.test\",\"code\":\"valid\",\"newPassword\":\"Updated@2026\"}")).andExpect(status().isOk());
        assertTrue(encoder.matches("Updated@2026", account.getPasswordHash()));
    }
}
