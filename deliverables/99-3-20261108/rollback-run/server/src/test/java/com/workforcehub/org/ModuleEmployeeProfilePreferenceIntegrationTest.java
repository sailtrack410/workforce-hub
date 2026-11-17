package com.workforcehub.org;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("员工更新手机号与头像应回写并被 profile 读取到")
    void employeeShouldUpdateMobileAndAvatar() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-profile-update-001");
        seedAttachment(7777L, 2003L, "USER_AVATAR");

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
    @DisplayName("同部门同事列表应返回联系人头像、签名和在线状态")
    void colleaguesShouldExposePersonalizationFields() throws Exception {
        Cookie colleagueSession = loginEmployee("wangwu.employee", "Employee@123456", "test-colleague-personalization-wangwu");
        seedAttachment(8888L, 2004L, "USER_AVATAR");
        mockMvc.perform(put("/api/employee/me/profile")
                .cookie(colleagueSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "avatarAttachmentRefId": 8888
                    }
                    """))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(colleagueSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "signature": "专注项目推进",
                      "presenceStatus": "AWAY"
                    }
                    """))
            .andExpect(status().isOk());

        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-colleague-personalization-zhangsan");

        mockMvc.perform(get("/api/employee/me/colleagues")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].userId").value(2004))
            .andExpect(jsonPath("$.data[0].avatarAttachmentRefId").value(8888))
            .andExpect(jsonPath("$.data[0].signature").value("专注项目推进"))
            .andExpect(jsonPath("$.data[0].presenceStatus").value("AWAY"));
    }

    @Test
    @DisplayName("员工头像只能绑定本人头像附件")
    void employeeAvatarShouldRequireOwnAvatarAttachment() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-profile-avatar-invalid");
        seedAttachment(8899L, 2003L, "PERSONAL_FILE");

        mockMvc.perform(put("/api/employee/me/profile")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "avatarAttachmentRefId": 8899
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403));
    }

    @Test
    @DisplayName("同部门同事列表不应暴露无效头像附件")
    void colleaguesShouldHideInvalidAvatarAttachment() throws Exception {
        seedAttachment(8900L, 2004L, "PERSONAL_FILE");
        jdbcTemplate.update("UPDATE org_user SET avatar_attachment_ref_id = ? WHERE id = ?", 8900L, 2004L);

        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-colleague-invalid-avatar");

        mockMvc.perform(get("/api/employee/me/colleagues")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].userId").value(2004))
            .andExpect(jsonPath("$.data[0].avatarAttachmentRefId").value(nullValue()));
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
            .andExpect(jsonPath("$.data.listWidth").value(400))
            .andExpect(jsonPath("$.data.signature").value(""))
            .andExpect(jsonPath("$.data.presenceStatus").value("ONLINE"))
            .andExpect(jsonPath("$.data.notifSound").value(true))
            .andExpect(jsonPath("$.data.notifDesktop").value(true))
            .andExpect(jsonPath("$.data.notifMentionOnly").value(false))
            .andExpect(jsonPath("$.data.notifUnreadBadge").value(true))
            .andExpect(jsonPath("$.data.accentColor").value("indigo"))
            .andExpect(jsonPath("$.data.fontScale").value("medium"))
            .andExpect(jsonPath("$.data.bubbleStyle").value("rounded"));

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "theme": "dark",
                      "messageDensity": "compact",
                      "listWidth": 520,
                      "signature": "保持节奏",
                      "presenceStatus": "BUSY",
                      "notifSound": false,
                      "notifDesktop": false,
                      "notifMentionOnly": true,
                      "notifUnreadBadge": false,
                      "accentColor": "violet",
                      "fontScale": "large",
                      "bubbleStyle": "sharp"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.theme").value("dark"))
            .andExpect(jsonPath("$.data.messageDensity").value("compact"))
            .andExpect(jsonPath("$.data.listWidth").value(520))
            .andExpect(jsonPath("$.data.signature").value("保持节奏"))
            .andExpect(jsonPath("$.data.presenceStatus").value("BUSY"))
            .andExpect(jsonPath("$.data.notifSound").value(false))
            .andExpect(jsonPath("$.data.notifDesktop").value(false))
            .andExpect(jsonPath("$.data.notifMentionOnly").value(true))
            .andExpect(jsonPath("$.data.notifUnreadBadge").value(false))
            .andExpect(jsonPath("$.data.accentColor").value("violet"))
            .andExpect(jsonPath("$.data.fontScale").value("large"))
            .andExpect(jsonPath("$.data.bubbleStyle").value("sharp"));

        mockMvc.perform(get("/api/employee/me/preferences")
                .cookie(session))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.theme").value("dark"))
            .andExpect(jsonPath("$.data.messageDensity").value("compact"))
            .andExpect(jsonPath("$.data.listWidth").value(520))
            .andExpect(jsonPath("$.data.signature").value("保持节奏"))
            .andExpect(jsonPath("$.data.presenceStatus").value("BUSY"))
            .andExpect(jsonPath("$.data.notifSound").value(false))
            .andExpect(jsonPath("$.data.accentColor").value("violet"))
            .andExpect(jsonPath("$.data.bubbleStyle").value("sharp"));
    }

    @Test
    @DisplayName("非法的状态 / 强调色 / 字号 / 气泡 / 签名超长 都应被参数校验拦截")
    void preferencesInvalidEnumsShouldBeRejected() throws Exception {
        Cookie session = loginEmployee("zhangsan.employee", "Employee@123456", "test-preferences-invalid-002");

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "presenceStatus": "OFFLINE"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "accentColor": "hotpink"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "fontScale": "huge"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bubbleStyle": "bubbly"
                    }
                    """))
            .andExpect(status().isBadRequest());

        String oversized = "a".repeat(141);
        mockMvc.perform(put("/api/employee/me/preferences")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "signature": "%s"
                    }
                    """.formatted(oversized)))
            .andExpect(status().isBadRequest());
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
            .andExpect(jsonPath("$.code").value(100400));
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

    private void seedAttachment(long attachmentRefId, long uploaderId, String ownerType) {
        long fileObjectId = attachmentRefId + 100000L;
        String contentSha256 = "%064x".formatted(attachmentRefId);
        jdbcTemplate.update("""
            INSERT INTO sys_file_object
                (id, storage_provider, bucket_name, object_key, file_name, file_ext,
                 mime_type, file_size, file_hash, content_sha256, ref_count,
                 upload_status, scan_status, created_by, is_deleted)
            VALUES
                (?, 'LOCAL', 'local', ?, '头像.png', 'png',
                 'image/png', 10, ?, ?, 1,
                 'UPLOADED', 'PASSED', ?, 0)
            """,
            fileObjectId,
            "org-avatar-test/" + attachmentRefId + ".png",
            contentSha256,
            contentSha256,
            uploaderId
        );
        jdbcTemplate.update("""
            INSERT INTO sys_attachment_ref
                (id, owner_type, owner_id, file_object_id, original_file_name,
                 display_name, uploader_type, uploader_id, visibility_scope,
                 sort_order, created_by, is_deleted)
            VALUES
                (?, ?, NULL, ?, '头像.png',
                 '头像.png', 'USER', ?, 'OWNER',
                 0, ?, 0)
            """,
            attachmentRefId,
            ownerType,
            fileObjectId,
            uploaderId,
            uploaderId
        );
    }
}
