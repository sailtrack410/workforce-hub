package com.workforcehub.im;

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
