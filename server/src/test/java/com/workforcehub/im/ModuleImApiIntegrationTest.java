package com.workforcehub.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.im.websocket.ImWebSocketOutboxDispatcher;
import com.workforcehub.im.websocket.ImWebSocketSessionRegistry;
import com.workforcehub.spi.ImAgentReplyResult;
import com.workforcehub.spi.ImAgentResultCallback;
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
    private static HttpServer modelServer;
    private static String modelBaseUrl;

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
    private ImAgentResultCallback imAgentResultCallback;

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
    }

    @DynamicPropertySource
    static void registerAgentModelProperties(DynamicPropertyRegistry registry) {
        registry.add("workforce.agent.model.enabled", () -> "true");
    }

    @BeforeEach
    void clearWebSocketSessions() {
        webSocketSessionRegistry.clear();
    }

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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("分页大小必须大于0"));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "2026-05-09T10:10:00")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("会话分页游标必须同时包含时间和会话ID"));

        mockMvc.perform(get("/api/employee/im/conversations")
                .queryParam("beforeLastMessageAt", "bad-time")
                .queryParam("beforeConversationId", "7001")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
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
            .andExpect(jsonPath("$.data.members[0].memberId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonPath("$.data.members[1].memberId").value(MANAGER_USER_ID))
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
            .andExpect(jsonPath("$.data.members[1].memberId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data.members[1].displayName").value("USER:%d".formatted(MANAGER_USER_ID)));
    }

    @Test
    @DisplayName("员工不能读取未加入会话的详情")
    void employeeShouldNotReadUnjoinedConversationDetail() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        seedOtherConversation();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", 7002L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(jsonPath("$.data[0].senderId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].contentText").value("我上午可以处理补卡。"))
            .andExpect(jsonPath("$.data[1].messageId").value(7102))
            .andExpect(jsonPath("$.data[1].seqNo").value(2))
            .andExpect(jsonPath("$.data[1].senderId").value(MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[1].contentText").value("下午同步一下排班。"));
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(jsonPath("$.data.boundAgentId").value(agentId))
            .andExpect(jsonPath("$.data.title").value("IM问答助手"))
            .andExpect(jsonPath("$.data.lastMessageSeq").value(0))
            .andReturn();
        long conversationId = data(conversationResult).get("conversationId").asLong();

        mockMvc.perform(get("/api/employee/im/conversations/{conversationId}", conversationId)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.conversation.conversationType").value("AGENT"))
            .andExpect(jsonPath("$.data.conversation.boundAgentId").value(agentId))
            .andExpect(jsonPath("$.data.members", hasSize(2)))
            .andExpect(jsonPath("$.data.members[0].memberType").value("USER"))
            .andExpect(jsonPath("$.data.members[0].memberId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.members[0].displayName").value("张三"))
            .andExpect(jsonPath("$.data.members[1].memberType").value("AGENT"))
            .andExpect(jsonPath("$.data.members[1].memberId").value(agentId))
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
            .andExpect(jsonPath("$.data.conversationId").value(conversationId));

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
            .andExpect(jsonPath("$.data[1].senderId").value(agentId))
            .andExpect(jsonPath("$.data[1].contentText").value("模型回复：帮我总结今天待办"));

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
            .andExpect(jsonPath("$.data[1].contentText").value("Agent 暂时无法回复，请稍后再试"));

        awaitTaskStatus(agentId, conversationId, "FAILED");
        assertEquals("Agent模型配置缺失", jdbcTemplate.queryForObject("""
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
        assertEquals("Agent 暂时无法回复，请稍后再试", jdbcTemplate.queryForObject("""
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
            .andExpect(jsonPath("$.data[1].contentText").value("Agent 暂时无法回复，请稍后再试"));

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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权使用该Agent"));

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
            .andExpect(jsonPath("$.data.attachments[0].attachmentRefId").isNumber())
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
            .andExpect(jsonPath("$.data[2].attachments[0].attachmentRefId").value(messageAttachmentRefId))
            .andExpect(jsonPath("$.data[2].attachments[0].displayName").value("排班附件.txt"));
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("员工端文件接口仅允许下载个人文件"));
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("当前消息状态不允许编辑"));

        mockMvc.perform(post("/api/employee/im/conversations/{conversationId}/messages/{messageId}/recall", 7001L, 7101L)
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
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
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("无权访问该会话"));

        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT last_read_seq FROM im_conversation_member WHERE conversation_id = 7002 AND member_id = ?",
            Long.class,
            OTHER_EMPLOYEE_USER_ID
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

    private void seedPrivateConversationWithFourMessages() {
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
                (?, 'PERSONAL_FILE', NULL, ?, ?, ?, 'USER', ?, 'OWNER',
                 IF(?, NOW(), NULL), 0, ?, ?)
            """,
            attachmentRefId,
            fileObjectId,
            displayName,
            displayName,
            uploaderId,
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

    private static void handleModelRequest(HttpExchange exchange) throws IOException {
        byte[] body = """
            {"choices":[{"message":{"content":"模型回复：帮我总结今天待办"}}]}
            """.getBytes(StandardCharsets.UTF_8);
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
