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
            .andExpect(jsonPath("$.code").value(403));

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
            .andExpect(jsonPath("$.code").value(400))
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
            .andExpect(jsonPath("$.code").value(400))
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
            .andExpect(jsonPath("$.code").value(403));
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
            .andExpect(jsonPath("$.code").value(403));
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
