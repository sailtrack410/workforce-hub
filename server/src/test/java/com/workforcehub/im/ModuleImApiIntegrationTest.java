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
 * module-im 员工端会话读取接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleImApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long MANAGER_USER_ID = 2002L;
    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long OTHER_EMPLOYEE_USER_ID = 2004L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("员工应只能看到自己参与的活跃会话并返回最后消息和未读数")
    void employeeShouldListOnlyJoinedActiveConversations() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
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
            .andExpect(jsonPath("$.data[0].lastMessageSeq").value(2))
            .andExpect(jsonPath("$.data[0].lastMessage.senderType").value("USER"))
            .andExpect(jsonPath("$.data[0].lastMessage.senderId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[0].lastMessage.contentText").value("下午同步一下排班。"))
            .andExpect(jsonPath("$.data[0].unreadCount").value(1))
            .andExpect(jsonPath("$.data[0].pin").value(false))
            .andExpect(jsonPath("$.data[0].mute").value(false));
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
            .andExpect(jsonPath("$.data[0].senderId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].contentText").value("我上午可以处理补卡。"))
            .andExpect(jsonPath("$.data[1].messageId").value(7102))
            .andExpect(jsonPath("$.data[1].seqNo").value(2))
            .andExpect(jsonPath("$.data[1].senderId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[1].contentText").value("下午同步一下排班。"));
    }

    @Test
    @DisplayName("员工不能读取未加入会话的消息")
    void employeeShouldNotReadMessagesOfUnjoinedConversation() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}/messages", 7002L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));
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
            .andExpect(jsonPath("$.data.senderId").value(EMPLOYEE_USER_ID))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("只能和直属上级或直属下级发起私聊"));

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
