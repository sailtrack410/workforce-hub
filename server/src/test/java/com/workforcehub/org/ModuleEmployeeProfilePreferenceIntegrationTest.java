package com.workforcehub.org;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 员工自助资料、同部门同事、系统偏好接口冒烟。
 * - PUT /api/employee/me/profile 写入 mobile / avatar
 * - GET /api/employee/me/colleagues 同部门排除本人
 * - GET / PUT /api/employee/me/preferences 主题、密度、列表宽度落库
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleEmployeeProfilePreferenceIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("员工更新手机号与头像应回写并被 profile 读取到")
    void employeeShouldUpdateMobileAndAvatar() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-profile-update-001");

        mockMvc.perform(put("/api/employee/me/profile")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "mobile": "13900000003",
                      "avatarAttachmentRefId": 7777
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.mobile").value("13900000003"))
            .andExpect(jsonPath("$.data.avatarAttachmentRefId").value(7777));

        mockMvc.perform(get("/api/employee/me/profile")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.mobile").value("13900000003"))
            .andExpect(jsonPath("$.data.avatarAttachmentRefId").value(7777));
    }

    @Test
    @DisplayName("同部门同事列表应排除本人并保持非空")
    void colleaguesShouldExcludeSelf() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-colleagues-001");

        mockMvc.perform(get("/api/employee/me/colleagues")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].userId", hasItem(2004)))
            .andExpect(jsonPath("$.data[*].userId", everyItem(is(2004))));
    }

    @Test
    @DisplayName("首次读取偏好应返回默认值；写入后再次读取应返回新值")
    void preferencesShouldHydrateAndPersist() throws Exception {
        Cookie session = loginEmployee("wangwu.employee", "Employee@123456", "test-preferences-001");

        mockMvc.perform(get("/api/employee/me/preferences")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.theme").value("light"))
            .andExpect(jsonPath("$.data.messageDensity").value("cozy"))
            .andExpect(jsonPath("$.data.listWidth").value(400));

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "theme": "dark",
                      "messageDensity": "compact",
                      "listWidth": 520
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.theme").value("dark"))
            .andExpect(jsonPath("$.data.messageDensity").value("compact"))
            .andExpect(jsonPath("$.data.listWidth").value(520));

        mockMvc.perform(get("/api/employee/me/preferences")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.theme").value("dark"))
            .andExpect(jsonPath("$.data.messageDensity").value("compact"))
            .andExpect(jsonPath("$.data.listWidth").value(520));
    }

    @Test
    @DisplayName("listWidth 越界应被服务端夹到合法区间")
    void preferencesListWidthShouldBeClamped() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-preferences-clamp-001");

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listWidth": 9000
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.listWidth").value(520));

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "listWidth": 100
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.listWidth").value(384));
    }

    @Test
    @DisplayName("无效 theme 应被参数校验拦截返回 400")
    void invalidThemeShouldBeRejected() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-preferences-invalid-001");

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "theme": "rainbow"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    private Cookie loginEmployee(String loginName, String password, String requestId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录后应返回 SESSION Cookie");
        return session;
    }
}
