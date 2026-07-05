package com.workforcehub.common;

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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台运营告警接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AdminAlertIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("后台告警应聚合 Agent、IM outbox 和 RAGFlow 失败")
    void adminShouldListActiveAlerts() throws Exception {
        Cookie adminSession = loginAdmin();
        seedAlerts();

        mockMvc.perform(get("/api/admin/alerts")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].alertCode", containsInAnyOrder(
                "AGENT_TASK_FAILED",
                "AGENT_TIMEOUT_SCAN_FAILED",
                "IM_OUTBOX_DEAD",
                "IM_OUTBOX_STALE_SENDING",
                "RAGFLOW_INGEST_FAILED"
            )))
            .andExpect(jsonPath("$.data[?(@.alertCode=='IM_OUTBOX_DEAD')].severity").value("CRITICAL"))
            .andExpect(jsonPath("$.data[?(@.alertCode=='AGENT_TASK_FAILED')].count").value(1))
            .andExpect(jsonPath("$.data[?(@.alertCode=='AGENT_TASK_FAILED')].ruleEnabled").value(true))
            .andExpect(jsonPath("$.data[?(@.alertCode=='AGENT_TASK_FAILED')].notifyEnabled").value(false));
    }

    @Test
    @DisplayName("后台告警应支持规则、通知渠道和处置记录")
    void adminShouldManageAlertClosure() throws Exception {
        Cookie adminSession = loginAdmin();
        seedAlertRules();

        mockMvc.perform(post("/api/admin/alerts/channels")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelCode": "OPS_WEBHOOK",
                      "channelName": "运维Webhook",
                      "channelType": "WEBHOOK",
                      "targetRef": "connector:ops-webhook",
                      "enabled": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.channelCode").value("OPS_WEBHOOK"));

        Long channelId = jdbcTemplate.queryForObject(
            "SELECT id FROM ops_alert_channel WHERE channel_code = 'OPS_WEBHOOK'",
            Long.class
        );

        mockMvc.perform(put("/api/admin/alerts/rules/{alertCode}", "AGENT_TASK_FAILED")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": true,
                      "notifyEnabled": true,
                      "channelId": %d,
                      "cooldownMinutes": 15
                    }
                    """.formatted(channelId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.alertCode").value("AGENT_TASK_FAILED"))
            .andExpect(jsonPath("$.data.notifyEnabled").value(true))
            .andExpect(jsonPath("$.data.channelCode").value("OPS_WEBHOOK"))
            .andExpect(jsonPath("$.data.cooldownMinutes").value(15));

        mockMvc.perform(post("/api/admin/alerts/{alertCode}/resolutions", "AGENT_TASK_FAILED")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "actionType": "RESOLVE",
                      "comment": "已重试失败任务"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.alertCode").value("AGENT_TASK_FAILED"))
            .andExpect(jsonPath("$.data.actionType").value("RESOLVE"))
            .andExpect(jsonPath("$.data.comment").value("已重试失败任务"));

        mockMvc.perform(get("/api/admin/alerts/resolutions")
                .cookie(adminSession)
                .param("alertCode", "AGENT_TASK_FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].actionType").value("RESOLVE"));

        mockMvc.perform(get("/api/admin/alerts/channels")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.channelCode=='OPS_WEBHOOK')].channelType").value("WEBHOOK"));

        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(adminSession)
                .param("actionType", "OPS_ALERT_RESOLVE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1));
    }

    private void seedAlerts() {
        seedAlertRules();
        jdbcTemplate.update("""
            INSERT INTO agt_agent
                (id, agent_code, agent_name, agent_type, identity_mode, published_version_id, status)
            VALUES
                (9501, 'ALERT_AGENT', '告警测试Agent', 'QA', 'SYSTEM_IDENTITY', 9601, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO agt_agent_version
                (id, agent_id, version_no, model_config_json, system_prompt, workflow_config_json, publish_status)
            VALUES
                (9601, 9501, 1, JSON_OBJECT('provider', 'test'), 'system', NULL, 'PUBLISHED')
            """);
        jdbcTemplate.update("""
            INSERT INTO agt_task
                (id, agent_id, agent_version_id, trigger_type, context_type, context_id,
                 triggered_by_type, triggered_by_id, status, started_at, finished_at, error_message)
            VALUES
                (9701, 9501, 9601, 'CONVERSATION', 'IM_CONVERSATION', 7001,
                 'USER', 2003, 'FAILED', '2026-06-29 10:00:00', '2026-06-29 10:00:01', '模型服务超时')
            """);
        jdbcTemplate.update("""
            INSERT INTO agt_task_timeout_job_run
                (id, job_run_id, trigger_type, status, batch_size, scanned_count, cancelled_count,
                 skipped_count, lock_owner, error_message, started_at, finished_at)
            VALUES
                (9801, 'alert-timeout-run', 'SCHEDULED', 'FAILED', 100, 0, 0,
                 0, 'test-owner', '扫描异常', '2026-06-29 10:01:00', '2026-06-29 10:01:01')
            """);
        jdbcTemplate.update("""
            INSERT INTO im_event_outbox
                (id, event_type, conversation_id, message_id, seq_no, actor_user_id,
                 recipient_user_ids_json, payload_json, status, retry_count, next_retry_at,
                 last_error, claimed_at, sent_at, created_at, updated_at)
            VALUES
                (9901, 'IM_MESSAGE_CREATED', 7001, 7101, 1, 2003,
                 JSON_ARRAY(2003), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'DEAD', 5, '2026-06-29 10:02:00',
                 'max attempts', NULL, NULL, '2026-06-29 10:02:00', '2026-06-29 10:02:00'),
                (9902, 'IM_MESSAGE_CREATED', 7001, 7102, 2, 2003,
                 JSON_ARRAY(2003), JSON_OBJECT('eventType', 'IM_MESSAGE_CREATED', 'conversationId', 7001),
                 'SENDING', 1, '2026-06-29 10:03:00',
                 NULL, '2000-01-01 00:00:00', NULL, '2026-06-29 10:03:00', '2026-06-29 10:03:00')
            """);
        jdbcTemplate.update("""
            INSERT INTO kb_space
                (id, space_name, space_code, description, visibility, status)
            VALUES
                (9911, '告警知识库', 'ALERT_KB', '测试', 'PRIVATE', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO kb_document
                (id, space_id, source_type, title, content_hash, ingest_status, latest_ingest_job_id, status)
            VALUES
                (9921, 9911, 'FILE', '告警文档', 'hash-alert', 'FAILED', 9931, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO kb_ingest_job
                (id, kb_document_id, space_id, source_file_object_id, job_type, status,
                 vector_status, error_message, started_at, finished_at)
            VALUES
                (9931, 9921, 9911, NULL, 'FULL', 'FAILED',
                 'FAILED', '解析失败', '2026-06-29 10:04:00', '2026-06-29 10:04:01')
            """);
    }

    private void seedAlertRules() {
        jdbcTemplate.update("""
            INSERT INTO ops_alert_channel
                (id, channel_code, channel_name, channel_type, enabled)
            VALUES
                (8501, 'IN_APP_DEFAULT', '后台站内告警', 'IN_APP', 1)
            ON DUPLICATE KEY UPDATE
                channel_name = VALUES(channel_name),
                channel_type = VALUES(channel_type),
                enabled = VALUES(enabled)
            """);
        jdbcTemplate.update("""
            INSERT INTO ops_alert_rule
                (id, alert_code, alert_name, severity, source_module, enabled, notify_enabled, channel_id, cooldown_minutes)
            VALUES
                (8601, 'AGENT_TASK_FAILED', 'Agent任务失败', 'WARN', 'agent', 1, 0, 8501, 30),
                (8602, 'AGENT_TIMEOUT_SCAN_FAILED', 'Agent超时扫描失败', 'WARN', 'agent', 1, 0, 8501, 30),
                (8603, 'IM_OUTBOX_DEAD', 'IM outbox死信', 'CRITICAL', 'im', 1, 0, 8501, 30),
                (8604, 'IM_OUTBOX_STALE_SENDING', 'IM outbox发送滞留', 'WARN', 'im', 1, 0, 8501, 30),
                (8605, 'RAGFLOW_INGEST_FAILED', 'RAGFlow入库失败', 'WARN', 'ragflow', 1, 0, 8501, 30)
            ON DUPLICATE KEY UPDATE
                alert_name = VALUES(alert_name),
                severity = VALUES(severity),
                source_module = VALUES(source_module),
                enabled = VALUES(enabled),
                notify_enabled = VALUES(notify_enabled),
                channel_id = VALUES(channel_id),
                cooldown_minutes = VALUES(cooldown_minutes)
            """);
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
}
