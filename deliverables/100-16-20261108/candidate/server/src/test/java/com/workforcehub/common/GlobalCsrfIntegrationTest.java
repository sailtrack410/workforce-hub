package com.workforcehub.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.config.RequestIdFilter;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("api")
class GlobalCsrfIntegrationTest extends LocalIntegrationTestSupport {

    private static final String ADMIN_LOGIN_JSON = """
        {
          "loginName": "admin.demo",
          "password": "Admin@123456"
        }
        """;
    private static final String EMPLOYEE_LOGIN_JSON = """
        {
          "loginName": "zhangsan.employee",
          "password": "Employee@123456"
        }
        """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestIdFilter requestIdFilter;

    private MockMvc rawMockMvc;

    @BeforeEach
    void setUpRawMockMvc() {
        rawMockMvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(requestIdFilter)
            .apply(springSecurity())
            .build();
    }

    @Test
    void csrfBootstrapShouldReturnRealCookieAndHeaderContract() throws Exception {
        MvcResult result = rawMockMvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString("XSRF-TOKEN="),
                containsString("Path=/"),
                not(containsString("HttpOnly")),
                not(containsString("Domain="))
            )))
            .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertNotNull(cookie);
        assertFalse(cookie.isHttpOnly());
        assertEquals("/", cookie.getPath());
        assertNull(cookie.getDomain());
        assertEquals("Lax", cookie.getAttribute("SameSite"));
        assertEquals(body.path("data").path("token").asText(), cookie.getValue());
    }

    @Test
    void adminAndEmployeeLoginShouldRequireMatchingCookieAndHeader() throws Exception {
        rawMockMvc.perform(post("/api/admin/auth/login")
                .header("X-Request-Id", "csrf-missing-admin-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ADMIN_LOGIN_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.requestId").value("csrf-missing-admin-login"));

        rawMockMvc.perform(post("/api/employee/auth/login")
                .header("X-Request-Id", "csrf-missing-employee-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EMPLOYEE_LOGIN_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.requestId").value("csrf-missing-employee-login"));

        CsrfPair csrf = bootstrapCsrf();

        rawMockMvc.perform(post("/api/admin/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ADMIN_LOGIN_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        rawMockMvc.perform(post("/api/employee/auth/login")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(EMPLOYEE_LOGIN_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        assertFalse(login("/api/admin/auth/login", ADMIN_LOGIN_JSON, csrf).isInvalid());
        assertFalse(login("/api/employee/auth/login", EMPLOYEE_LOGIN_JSON, csrf).isInvalid());
    }

    @Test
    void anonymousUnsafeRequestWithValidCsrfShouldReachAuthenticationBoundary() throws Exception {
        CsrfPair csrf = bootstrapCsrf();

        rawMockMvc.perform(post("/api/employee/auth/email/bind")
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "csrf-anonymous@example.com",
                      "code": "123456"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(100401));
    }

    @Test
    void authenticatedGetShouldNotRequireCsrfHeader() throws Exception {
        CsrfPair csrf = bootstrapCsrf();
        MockHttpSession session = login("/api/admin/auth/login", ADMIN_LOGIN_JSON, csrf);

        rawMockMvc.perform(get("/api/admin/auth/me")
                .session(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void logoutShouldRejectMissingAndWrongTokenWithoutInvalidatingSession() throws Exception {
        CsrfPair csrf = bootstrapCsrf();
        MockHttpSession session = login("/api/employee/auth/login", EMPLOYEE_LOGIN_JSON, csrf);

        rawMockMvc.perform(post("/api/employee/auth/logout")
                .session(session)
                .cookie(csrf.cookie()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        rawMockMvc.perform(get("/api/employee/auth/me")
                .session(session))
            .andExpect(status().isOk());

        rawMockMvc.perform(post("/api/employee/auth/logout")
                .session(session)
                .cookie(csrf.cookie())
                .header(csrf.headerName(), "wrong-token"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        rawMockMvc.perform(get("/api/employee/auth/me")
                .session(session))
            .andExpect(status().isOk());

        rawMockMvc.perform(post("/api/employee/auth/logout")
                .session(session)
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertTrue(session.isInvalid());
    }

    @Test
    void publicVerificationAndPasswordResetPostsShouldRequireCsrf() throws Exception {
        rawMockMvc.perform(post("/api/employee/auth/login/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"identifier\":\"zhangsan.employee\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        rawMockMvc.perform(post("/api/employee/auth/email/send-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "csrf-public@example.com",
                      "purpose": "BIND"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));

        rawMockMvc.perform(post("/api/employee/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "zhangsan@example.com",
                      "code": "123456",
                      "newPassword": "Employee@654321"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    private CsrfPair bootstrapCsrf() throws Exception {
        MvcResult result = rawMockMvc.perform(get("/api/csrf"))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("XSRF-TOKEN"))
            .andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertNotNull(cookie);
        return new CsrfPair(cookie, body.path("data").path("headerName").asText(),
            body.path("data").path("token").asText());
    }

    private MockHttpSession login(String path, String body, CsrfPair csrf) throws Exception {
        MvcResult result = rawMockMvc.perform(post(path)
                .cookie(csrf.cookie())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertNotNull(session);
        return session;
    }

    private record CsrfPair(Cookie cookie, String headerName, String token) {
    }
}
