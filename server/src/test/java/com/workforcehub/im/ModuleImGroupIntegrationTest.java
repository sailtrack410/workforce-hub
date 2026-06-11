package com.workforcehub.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-im 员工端群聊接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleImGroupIntegrationTest extends LocalIntegrationTestSupport {

    private static final long MANAGER_USER_ID = 2002L;
    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long OTHER_EMPLOYEE_USER_ID = 2004L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${workforce.file.local-root:target/test-file-storage}")
    private String localRoot;

    @Test
    @DisplayName("群成员应能读取自己所在群聊的成员列表")
    void groupMemberShouldListMembersOfJoinedGroupConversation() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/members", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].memberId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].displayName").value("张三"))
            .andExpect(jsonPath("$.data[0].memberRole").value("OWNER"))
            .andExpect(jsonPath("$.data[1].memberId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[1].displayName").value("李四"))
            .andExpect(jsonPath("$.data[1].memberRole").value("MEMBER"));
    }

    @Test
    @DisplayName("非群成员不能读取群聊成员列表")
    void nonMemberShouldNotListMembersOfGroupConversation() throws Exception {
        Cookie nonMemberSession = loginEmployee("wangwu.employee");
        seedCustomGroup();

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/members", 7301L)
                .cookie(nonMemberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权访问该群聊"));
    }

    @Test
    @DisplayName("群成员应能读取群资料和公告")
    void groupMemberShouldReadGroupProfile() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/profile", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationId").value(7301))
            .andExpect(jsonPath("$.data.title").value("项目推进群"))
            .andExpect(jsonPath("$.data.groupType").value("CUSTOM"))
            .andExpect(jsonPath("$.data.ownerUserId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.noticeText").value("第一版公告"))
            .andExpect(jsonPath("$.data.currentMemberRole").value("MEMBER"));
    }

    @Test
    @DisplayName("群成员应能通过会话详情读取群资料和成员")
    void groupMemberShouldReadGroupConversationDetail() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversation.conversationId").value(7301))
            .andExpect(jsonPath("$.data.conversation.conversationType").value("GROUP"))
            .andExpect(jsonPath("$.data.conversation.title").value("项目推进群"))
            .andExpect(jsonPath("$.data.members", hasSize(2)))
            .andExpect(jsonPath("$.data.members[0].memberId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonPath("$.data.members[0].memberRole").value("OWNER"))
            .andExpect(jsonPath("$.data.members[1].memberId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data.members[1].displayName").value("李四"))
            .andExpect(jsonPath("$.data.groupProfile.conversationId").value(7301))
            .andExpect(jsonPath("$.data.groupProfile.noticeText").value("第一版公告"))
            .andExpect(jsonPath("$.data.groupProfile.currentMemberRole").value("MEMBER"));
    }

    @Test
    @DisplayName("群主应能维护群名称和公告")
    void ownerShouldUpdateGroupProfile() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/profile", 7301L)
                .cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "项目推进群-后端",
                      "noticeText": "今天先完成会话闭环"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("项目推进群-后端"))
            .andExpect(jsonPath("$.data.noticeText").value("今天先完成会话闭环"))
            .andExpect(jsonPath("$.data.currentMemberRole").value("OWNER"));

        assertEquals("项目推进群-后端", jdbcTemplate.queryForObject(
            "SELECT title FROM im_conversation WHERE id = 7301",
            String.class
        ));
        assertEquals("今天先完成会话闭环", jdbcTemplate.queryForObject(
            "SELECT notice_text FROM im_group_profile WHERE conversation_id = 7301",
            String.class
        ));
    }

    @Test
    @DisplayName("普通成员不能维护群资料")
    void normalMemberShouldNotUpdateGroupProfile() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/profile", 7301L)
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "普通成员不应修改"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("只有群主可以管理群成员"));

        assertEquals("项目推进群", jdbcTemplate.queryForObject(
            "SELECT title FROM im_conversation WHERE id = 7301",
            String.class
        ));
    }

    @Test
    @DisplayName("员工应能创建自定义群聊并自动成为群主")
    void employeeShouldCreateCustomGroupConversationAsOwner() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/group-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "项目推进群",
                      "memberUserIds": [2002, 2004]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationType").value("GROUP"))
            .andExpect(jsonPath("$.data.title").value("项目推进群"))
            .andExpect(jsonPath("$.data.lastMessageSeq").value(0))
            .andExpect(jsonPath("$.data.lastReadSeq").value(0))
            .andExpect(jsonPath("$.data.unreadCount").value(0))
            .andExpect(jsonPath("$.data.memberRole").value("OWNER"));

        Long conversationId = jdbcTemplate.queryForObject(
            "SELECT id FROM im_conversation WHERE conversation_type = 'GROUP' AND title = '项目推进群'",
            Long.class
        );
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_group_profile WHERE conversation_id = ? AND group_type = 'CUSTOM' AND owner_user_id = ?",
            Long.class,
            conversationId,
            EMPLOYEE_USER_ID
        ));
        assertEquals(3L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = ? AND status = 'ACTIVE'",
            Long.class,
            conversationId
        ));
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = ? AND member_id = ? AND member_role = 'OWNER'",
            Long.class,
            conversationId,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("员工创建群聊时重复成员和自己应被去重")
    void employeeGroupCreationShouldDeduplicateMembersAndSelf() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/group-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "后端协作群",
                      "memberUserIds": [2002, 2002, 2003]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.title").value("后端协作群"));

        Long conversationId = jdbcTemplate.queryForObject(
            "SELECT id FROM im_conversation WHERE conversation_type = 'GROUP' AND title = '后端协作群'",
            Long.class
        );
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = ? AND status = 'ACTIVE'",
            Long.class,
            conversationId
        ));
    }

    @Test
    @DisplayName("员工不能创建没有其他成员的群聊")
    void employeeShouldNotCreateGroupWithoutOtherMembers() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/group-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "空群",
                      "memberUserIds": [2003]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("群聊至少需要选择1名其他成员"));
    }

    @Test
    @DisplayName("员工不能把不存在或不可加入的员工加入群聊")
    void employeeShouldNotCreateGroupWithInvalidMember() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");

        mockMvc.perform(post("/api/employee/im/group-conversations")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "异常成员群",
                      "memberUserIds": [9999]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("目标成员不存在或不可加入群聊"));
    }

    @Test
    @DisplayName("群主应能拉人进入自定义群聊")
    void ownerShouldAddMembersToCustomGroupConversation() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/members", 7301L)
                .cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "memberUserIds": [2004]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[?(@.memberId == 2004)].memberRole").value("MEMBER"));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = 7301 AND member_id = ? AND status = 'ACTIVE'",
            Long.class,
            OTHER_EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("群主应能移出普通群成员")
    void ownerShouldRemoveMemberFromCustomGroupConversation() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/members/remove", 7301L)
                .cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "memberUserIds": [2002]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].memberId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].memberRole").value("OWNER"));

        assertEquals("LEFT", jdbcTemplate.queryForObject(
            "SELECT status FROM im_conversation_member WHERE conversation_id = 7301 AND member_id = ?",
            String.class,
            MANAGER_USER_ID
        ));
    }

    @Test
    @DisplayName("普通成员不能治理群成员")
    void normalMemberShouldNotManageGroupMembers() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/members", 7301L)
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "memberUserIds": [2004]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("只有群主可以管理群成员"));
    }

    @Test
    @DisplayName("群主不能把自己移出群聊")
    void ownerShouldNotRemoveSelfFromGroupConversation() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/members/remove", 7301L)
                .cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "memberUserIds": [2003]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("群主不能把自己移出群聊"));
    }

    @Test
    @DisplayName("普通成员应能主动退群")
    void normalMemberShouldLeaveGroupConversation() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/leave", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].memberId").value(EMPLOYEE_USER_ID));

        assertEquals("LEFT", jdbcTemplate.queryForObject(
            "SELECT status FROM im_conversation_member WHERE conversation_id = 7301 AND member_id = ?",
            String.class,
            MANAGER_USER_ID
        ));
    }

    @Test
    @DisplayName("群主不能直接退群")
    void ownerShouldNotLeaveGroupConversation() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/leave", 7301L)
                .cookie(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("群主不能直接退群，请先解散群聊或转让群主"));

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
            "SELECT status FROM im_conversation_member WHERE conversation_id = 7301 AND member_id = ?",
            String.class,
            EMPLOYEE_USER_ID
        ));
    }

    @Test
    @DisplayName("群主应能解散自定义群聊")
    void ownerShouldDismissCustomGroupConversation() throws Exception {
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/dismiss", 7301L)
                .cookie(ownerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        assertEquals("DISMISSED", jdbcTemplate.queryForObject(
            "SELECT status FROM im_conversation WHERE id = 7301",
            String.class
        ));
        assertEquals("DISMISSED", jdbcTemplate.queryForObject(
            "SELECT status FROM im_group_profile WHERE conversation_id = 7301",
            String.class
        ));
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id = 7301 AND status = 'ACTIVE'",
            Long.class
        ));

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/members", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("仅群聊支持成员列表"));
    }

    @Test
    @DisplayName("普通成员不能解散群聊")
    void normalMemberShouldNotDismissGroupConversation() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/dismiss", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("只有群主可以管理群成员"));

        assertEquals("ACTIVE", jdbcTemplate.queryForObject(
            "SELECT status FROM im_conversation WHERE id = 7301",
            String.class
        ));
    }

    @Test
    @DisplayName("同群成员应能发起私聊")
    void groupMembersShouldStartPrivateConversationWithEachOther() throws Exception {
        Cookie memberSession = loginEmployee("zhangsan.employee");
        seedCustomGroupWithCoworkerOnly();

        mockMvc.perform(post("/api/employee/im/private-conversations")
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": 2004
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversationType").value("PRIVATE"))
            .andExpect(jsonPath("$.data.conversationKey").value("PRIVATE:2003:2004"))
            .andExpect(jsonPath("$.data.memberRole").value("MEMBER"));

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation WHERE conversation_key = 'PRIVATE:2003:2004'",
            Long.class
        ));
        assertEquals(2L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM im_conversation_member WHERE conversation_id IN (SELECT id FROM im_conversation WHERE conversation_key = 'PRIVATE:2003:2004')",
            Long.class
        ));
    }

    @Test
    @DisplayName("群成员应能查看本群独立文件并下载且不串到其他群")
    void groupMemberShouldListAndDownloadOnlyJoinedGroupFiles() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        seedCustomGroup();
        seedOtherCustomGroup();
        long groupFileRefId = seedGroupFileAttachmentRef(
            7301L,
            EMPLOYEE_USER_ID,
            "群资料.txt",
            "group-file-content"
        );
        long otherGroupFileRefId = seedGroupFileAttachmentRef(
            7309L,
            OTHER_EMPLOYEE_USER_ID,
            "其他群资料.txt",
            "other-group-file-content"
        );

        MvcResult listResult = mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/files", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].attachmentRefId").value(groupFileRefId))
            .andExpect(jsonPath("$.data[0].conversationId").value(7301))
            .andExpect(jsonPath("$.data[0].displayName").value("群资料.txt"))
            .andExpect(jsonPath("$.data[0].uploaderId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].canDelete").value(false))
            .andReturn();

        assertEquals(groupFileRefId, data(listResult).get(0).get("attachmentRefId").asLong());

        MvcResult ticketResult = mockMvc.perform(post(
                    "/api/employee/im/group-conversations/{conversationId}/files/{attachmentRefId}/download-tickets",
                    7301L,
                    groupFileRefId
                )
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "查看群文件"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.ticketNo").isString())
            .andExpect(jsonPath("$.data.downloadUrl").value(Matchers.containsString("/api/employee/im/attachments/download/")))
            .andExpect(jsonPath("$.data.fileName").value("群资料.txt"))
            .andReturn();

        String ticketNo = data(ticketResult).get("ticketNo").asText();
        mockMvc.perform(get("/api/employee/im/attachments/download/{ticketNo}", ticketNo)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .bytes("group-file-content".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(post(
                    "/api/employee/im/group-conversations/{conversationId}/files/{attachmentRefId}/download-tickets",
                    7301L,
                    otherGroupFileRefId
                )
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("群文件不存在"));
    }

    @Test
    @DisplayName("群文件仅群主或群管理员可删除")
    void onlyGroupOwnerOrAdminShouldDeleteGroupFile() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        Cookie ownerSession = loginEmployee("zhangsan.employee");
        seedCustomGroup();
        long groupFileRefId = seedGroupFileAttachmentRef(
            7301L,
            MANAGER_USER_ID,
            "待删除群资料.txt",
            "group-file-delete-content"
        );

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/files/{attachmentRefId}/trash", 7301L, groupFileRefId)
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "普通成员尝试删除"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("只有群主或群管理员可以删除群文件"));

        mockMvc.perform(post("/api/employee/im/group-conversations/{conversationId}/files/{attachmentRefId}/trash", 7301L, groupFileRefId)
                .cookie(ownerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "清理群文件"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").value(groupFileRefId))
            .andExpect(jsonPath("$.data.deletedAt").isString());

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/files", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("群成员上传群文件应归属群空间且不进入个人文件中心")
    void groupMemberShouldUploadGroupFileOutsidePersonalFileCenter() throws Exception {
        Cookie memberSession = loginEmployee("lisi.manager");
        Cookie nonMemberSession = loginEmployee("wangwu.employee");
        seedCustomGroup();

        byte[] content = "uploaded group file content".getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(content);

        mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(nonMemberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerType": "IM_GROUP_FILE",
                      "ownerId": 7301,
                      "fileName": "非成员群文件.txt",
                      "mimeType": "text/plain",
                      "fileSize": %s,
                      "contentSha256": "%s",
                      "contentMd5": null,
                      "clientRequestId": "group-file-non-member"
                    }
                    """.formatted(content.length, sha256)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权访问该群聊"));

        MvcResult ticketResult = mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerType": "IM_GROUP_FILE",
                      "ownerId": 7301,
                      "fileName": "群共享资料.txt",
                      "mimeType": "text/plain",
                      "fileSize": %s,
                      "contentSha256": "%s",
                      "contentMd5": null,
                      "clientRequestId": "group-file-upload-1"
                    }
                    """.formatted(content.length, sha256)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(false))
            .andReturn();
        JsonNode ticketData = data(ticketResult);
        Long uploadTicketId = ticketData.get("uploadTicketId").asLong();
        String objectKey = ticketData.get("objectKey").asText();
        writeLocalObject(objectKey, content);

        MvcResult confirmResult = mockMvc.perform(post("/api/employee/files/confirm-upload")
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "uploadTicketId": %s,
                      "objectKey": "%s",
                      "contentSha256": "%s",
                      "fileSize": %s,
                      "ownerType": "IM_GROUP_FILE",
                      "ownerId": 7301,
                      "displayName": "群共享资料.txt"
                    }
                    """.formatted(uploadTicketId, objectKey, sha256, content.length)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber())
            .andReturn();
        long attachmentRefId = data(confirmResult).get("attachmentRefId").asLong();

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE id = ? AND owner_type = 'IM_GROUP_FILE' AND owner_id = 7301",
            Long.class,
            attachmentRefId
        ));
        assertEquals("CONVERSATION_MEMBER", jdbcTemplate.queryForObject(
            "SELECT visibility_scope FROM sys_attachment_ref WHERE id = ?",
            String.class,
            attachmentRefId
        ));
        assertEquals(0L, usedBytes("USER", MANAGER_USER_ID));
        assertEquals(content.length, usedBytes("GLOBAL", null));

        MvcResult deduplicatedResult = mockMvc.perform(post("/api/employee/files/upload-tickets")
                .cookie(memberSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ownerType": "IM_GROUP_FILE",
                      "ownerId": 7301,
                      "fileName": "群共享资料副本.txt",
                      "mimeType": "text/plain",
                      "fileSize": %s,
                      "contentSha256": "%s",
                      "contentMd5": null,
                      "clientRequestId": "group-file-upload-2"
                    }
                    """.formatted(content.length, sha256)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.deduplicated").value(true))
            .andExpect(jsonPath("$.data.attachmentRefId").isNumber())
            .andReturn();
        long deduplicatedAttachmentRefId = data(deduplicatedResult).get("attachmentRefId").asLong();

        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM sys_attachment_ref WHERE id = ? AND owner_type = 'IM_GROUP_FILE' AND owner_id = 7301",
            Long.class,
            deduplicatedAttachmentRefId
        ));
        assertEquals(0L, usedBytes("USER", MANAGER_USER_ID));
        assertEquals(content.length * 2L, usedBytes("GLOBAL", null));

        mockMvc.perform(get("/api/employee/im/group-conversations/{conversationId}/files", 7301L)
                .cookie(memberSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[?(@.attachmentRefId == %s)]".formatted(attachmentRefId)).exists())
            .andExpect(jsonPath("$.data[?(@.attachmentRefId == %s)]".formatted(deduplicatedAttachmentRefId)).exists());

        mockMvc.perform(get("/api/employee/files")
                .cookie(memberSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.list[?(@.attachmentRefId == %s)]".formatted(attachmentRefId)).isEmpty())
            .andExpect(jsonPath("$.data.list[?(@.attachmentRefId == %s)]".formatted(deduplicatedAttachmentRefId)).isEmpty());
    }

    private void seedCustomGroup() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, title, last_message_seq, status, created_by, updated_by, is_deleted)
            VALUES
                (7301, 'GROUP', '项目推进群', 0, 'ACTIVE', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_group_profile
                (id, conversation_id, group_type, owner_user_id, notice_text, status, created_by, updated_by)
            VALUES
                (7302, 7301, 'CUSTOM', 2003, '第一版公告', 'ACTIVE', 2003, 2003)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (7303, 7301, 'USER', ?, 'OWNER', 'CREATE', 0, 'ACTIVE'),
                (7304, 7301, 'USER', ?, 'MEMBER', 'CREATE', 0, 'ACTIVE')
            """, EMPLOYEE_USER_ID, MANAGER_USER_ID);
    }

    private void seedOtherCustomGroup() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, title, last_message_seq, status, created_by, updated_by, is_deleted)
            VALUES
                (7309, 'GROUP', '其他项目群', 0, 'ACTIVE', 2004, 2004, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_group_profile
                (id, conversation_id, group_type, owner_user_id, notice_text, status, created_by, updated_by)
            VALUES
                (7310, 7309, 'CUSTOM', 2004, '其他群公告', 'ACTIVE', 2004, 2004)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (7311, 7309, 'USER', ?, 'OWNER', 'CREATE', 0, 'ACTIVE')
            """, OTHER_EMPLOYEE_USER_ID);
    }

    private long seedGroupFileAttachmentRef(long conversationId,
                                            long uploaderId,
                                            String displayName,
                                            String content) {
        long suffix = Math.abs((long) (conversationId + ":" + content).hashCode());
        long fileObjectId = 9750_0000L + suffix;
        long attachmentRefId = 9850_0000L + suffix;
        String contentSha256 = "%064x".formatted(attachmentRefId);
        String objectKey = "im-group-test/" + attachmentRefId + ".txt";
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
                 'UPLOADED', 'PASSED', ?, 0)
            """,
            fileObjectId,
            objectKey,
            displayName,
            (long) bytes.length,
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
                (?, 'IM_GROUP_FILE', ?, ?, ?, ?, 'USER', ?, 'CONVERSATION_MEMBER',
                 0, ?, 0)
            """,
            attachmentRefId,
            conversationId,
            fileObjectId,
            displayName,
            displayName,
            uploaderId,
            uploaderId
        );
        return attachmentRefId;
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

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String sha256(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(content));
    }

    private long usedBytes(String scopeType, Long scopeId) {
        Long value = jdbcTemplate.query("""
            SELECT used_bytes FROM sys_storage_usage
            WHERE scope_type = ? AND ((scope_id IS NULL AND ? IS NULL) OR scope_id = ?)
            """,
            rs -> rs.next() ? rs.getLong("used_bytes") : 0L,
            scopeType,
            scopeId,
            scopeId
        );
        return value == null ? 0L : value;
    }

    private void seedCustomGroupWithCoworkerOnly() {
        jdbcTemplate.update("""
            INSERT INTO im_conversation
                (id, conversation_type, title, last_message_seq, status, created_by, updated_by, is_deleted)
            VALUES
                (7301, 'GROUP', '同组协作群', 0, 'ACTIVE', 2003, 2003, 0)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_group_profile
                (id, conversation_id, group_type, owner_user_id, status, created_by, updated_by)
            VALUES
                (7302, 7301, 'CUSTOM', 2003, 'ACTIVE', 2003, 2003)
            """);
        jdbcTemplate.update("""
            INSERT INTO im_conversation_member
                (id, conversation_id, member_type, member_id, member_role, join_source, last_read_seq, status)
            VALUES
                (7303, 7301, 'USER', ?, 'OWNER', 'CREATE', 0, 'ACTIVE'),
                (7304, 7301, 'USER', ?, 'MEMBER', 'CREATE', 0, 'ACTIVE')
            """, EMPLOYEE_USER_ID, OTHER_EMPLOYEE_USER_ID);
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
}
