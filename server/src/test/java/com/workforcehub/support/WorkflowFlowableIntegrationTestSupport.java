package com.workforcehub.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * module-workflow 专用集成测试支撑。
 * 与普通本地集成测试基线不同，本基线显式保留 Flowable 自动配置，用真实流程引擎跑审批闭环。
 */
public abstract class WorkflowFlowableIntegrationTestSupport {

    private static final String MYSQL_URL_TEMPLATE =
        "jdbc:mysql://%s:%s/%s?useUnicode=true&characterEncoding=UTF-8"
            + "&connectionCollation=utf8mb4_0900_ai_ci&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true";
    private static final String FLOWABLE_WORKFLOW_TEST_EXCLUDES = String.join(",",
        "org.flowable.spring.boot.actuate.info.FlowableInfoAutoConfiguration",
        "org.flowable.spring.boot.EndpointAutoConfiguration",
        "org.flowable.spring.boot.RestApiAutoConfiguration",
        "org.flowable.spring.boot.app.AppEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.app.AppEngineAutoConfiguration",
        "org.flowable.spring.boot.FlowableJpaAutoConfiguration",
        "org.flowable.spring.boot.dmn.DmnEngineAutoConfiguration",
        "org.flowable.spring.boot.dmn.DmnEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.idm.IdmEngineAutoConfiguration",
        "org.flowable.spring.boot.idm.IdmEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.eventregistry.EventRegistryAutoConfiguration",
        "org.flowable.spring.boot.eventregistry.EventRegistryServicesAutoConfiguration",
        "org.flowable.spring.boot.cmmn.CmmnEngineAutoConfiguration",
        "org.flowable.spring.boot.cmmn.CmmnEngineServicesAutoConfiguration",
        "org.flowable.spring.boot.ldap.FlowableLdapAutoConfiguration",
        "org.flowable.spring.boot.FlowableSecurityAutoConfiguration"
    );

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> String.format(
            MYSQL_URL_TEMPLATE,
            envOrFallback("TEST_DB_HOST", "DB_HOST", "localhost"),
            envOrFallback("TEST_DB_PORT", "DB_PORT", "3306"),
            env("TEST_DB_NAME", "workforce_hub_test")
        ));
        registry.add("spring.datasource.username", () -> envOrFallback("TEST_DB_USER", "DB_USER", "root"));
        registry.add("spring.datasource.password", () -> envOrFallback("TEST_DB_PASSWORD", "DB_PASSWORD", "REDACTED_DB_PASSWORD"));
        registry.add("spring.data.redis.host", () -> env("REDIS_HOST", "localhost"));
        registry.add("spring.data.redis.port", () -> env("REDIS_PORT", "6379"));
        registry.add("spring.data.redis.password", () -> env("REDIS_PASSWORD", ""));
        registry.add("spring.session.redis.namespace", WorkflowFlowableIntegrationTestSupport::testSessionRedisNamespace);
        registry.add("spring.mail.host", () -> env("MAIL_HOST", "localhost"));
        registry.add("spring.mail.port", () -> env("MAIL_PORT", "1025"));
        registry.add("management.health.mail.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude", () -> FLOWABLE_WORKFLOW_TEST_EXCLUDES);
        registry.add("workforce.workflow.enabled", () -> "true");
        registry.add("logging.level.com.workforcehub", () -> "info");
        registry.add("workforce.agent.timeout-scan.enabled", () -> "false");
        registry.add("workforce.im.outbox.dispatcher.enabled", () -> "false");
        registry.add("flowable.database-schema-update", () -> "false");
        registry.add("flowable.async-executor-activate", () -> "false");
    }

    @BeforeEach
    void seedDatabase() {
        resetRedisState();
        resetBusinessTables();
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(resolveSeedScript());
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private void resetRedisState() {
        deleteKeys(testSessionRedisNamespace() + "*");
        deleteKeys("auth:login:fail:*");
        deleteKeys("auth:email:*");
    }

    private void resetBusinessTables() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            jdbcTemplate.execute("DELETE FROM sys_download_ticket");
            jdbcTemplate.execute("DELETE FROM sys_file_cleanup_job_detail");
            jdbcTemplate.execute("DELETE FROM sys_file_cleanup_job_run");
            deleteIfExists(jdbcTemplate, "agt_task_timeout_job_run");
            deleteIfExists(jdbcTemplate, "sys_job_lock");
            deleteIfExists(jdbcTemplate, "im_event_outbox");
            jdbcTemplate.execute("DELETE FROM sys_todo_item");
            jdbcTemplate.execute("DELETE FROM sys_audit_log");
            jdbcTemplate.execute("DELETE FROM sys_storage_usage");
            jdbcTemplate.execute("DELETE FROM sys_storage_quota_policy");
            jdbcTemplate.execute("DELETE FROM sys_upload_ticket");
            jdbcTemplate.execute("DELETE FROM sys_attachment_ref");
            jdbcTemplate.execute("DELETE FROM sys_file_object");
            jdbcTemplate.execute("DELETE FROM att_exception");
            jdbcTemplate.execute("DELETE FROM att_daily_result");
            jdbcTemplate.execute("DELETE FROM att_punch_record");
            jdbcTemplate.execute("DELETE FROM att_schedule");
            jdbcTemplate.execute("DELETE FROM att_group_member");
            jdbcTemplate.execute("DELETE FROM att_group");
            jdbcTemplate.execute("DELETE FROM att_shift_segment");
            jdbcTemplate.execute("DELETE FROM att_shift");
            jdbcTemplate.execute("DELETE FROM att_work_calendar_day");
            jdbcTemplate.execute("DELETE FROM att_work_calendar");
            deleteIfExists(jdbcTemplate, "wf_request_attachment");
            jdbcTemplate.execute("DELETE FROM wf_task_action");
            jdbcTemplate.execute("DELETE FROM wf_cc_record");
            jdbcTemplate.execute("DELETE FROM wf_urge_record");
            jdbcTemplate.execute("DELETE FROM wf_req_leave");
            jdbcTemplate.execute("DELETE FROM wf_req_timeoff");
            jdbcTemplate.execute("DELETE FROM wf_req_overtime");
            jdbcTemplate.execute("DELETE FROM wf_req_clock_correction");
            jdbcTemplate.execute("DELETE FROM wf_req_hr_change");
            jdbcTemplate.execute("DELETE FROM wf_request");
            jdbcTemplate.execute("DELETE FROM wf_template_version");
            jdbcTemplate.execute("DELETE FROM wf_template");
            jdbcTemplate.execute("DELETE FROM wf_process_type");
            jdbcTemplate.execute("DELETE FROM lv_account_txn");
            jdbcTemplate.execute("DELETE FROM lv_account");
            jdbcTemplate.execute("DELETE FROM lv_type_policy");
            jdbcTemplate.execute("DELETE FROM lv_policy");
            jdbcTemplate.execute("DELETE FROM lv_type");
            jdbcTemplate.execute("DELETE FROM hr_employee_event");
            jdbcTemplate.execute("DELETE FROM auth_user_account");
            jdbcTemplate.execute("DELETE FROM org_department_leader");
            jdbcTemplate.execute("DELETE FROM org_user");
            jdbcTemplate.execute("DELETE FROM org_position");
            jdbcTemplate.execute("DELETE FROM org_department");
            jdbcTemplate.execute("DELETE FROM admin_user");
            resetFlowableRuntimeAndHistoryTables(jdbcTemplate);
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    private void resetFlowableRuntimeAndHistoryTables(JdbcTemplate jdbcTemplate) {
        List.of(
            "ACT_RU_VARIABLE",
            "ACT_RU_IDENTITYLINK",
            "ACT_RU_TASK",
            "ACT_RU_JOB",
            "ACT_RU_TIMER_JOB",
            "ACT_RU_SUSPENDED_JOB",
            "ACT_RU_DEADLETTER_JOB",
            "ACT_RU_HISTORY_JOB",
            "ACT_RU_EXTERNAL_JOB",
            "ACT_RU_ENTITYLINK",
            "ACT_RU_EVENT_SUBSCR",
            "ACT_RU_ACTINST",
            "ACT_RU_EXECUTION",
            "FLW_RU_BATCH_PART",
            "FLW_RU_BATCH",
            "ACT_HI_VARINST",
            "ACT_HI_IDENTITYLINK",
            "ACT_HI_TASKINST",
            "ACT_HI_TSK_LOG",
            "ACT_HI_ENTITYLINK",
            "ACT_HI_ACTINST",
            "ACT_HI_DETAIL",
            "ACT_HI_COMMENT",
            "ACT_HI_ATTACHMENT",
            "ACT_HI_PROCINST",
            "ACT_EVT_LOG"
        ).forEach(tableName -> deleteIfExists(jdbcTemplate, tableName));
    }

    private void deleteIfExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.TABLES
            WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
            """, Integer.class, tableName);
        if (count != null && count > 0) {
            jdbcTemplate.execute("DELETE FROM " + tableName);
        }
    }

    private static Resource resolveSeedScript() {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path modulePath = cwd.resolve("scripts/sql/seed_module_org_smoke_data.sql");
        if (Files.exists(modulePath)) {
            return new FileSystemResource(modulePath);
        }

        Path repoPath = cwd.resolve("server/scripts/sql/seed_module_org_smoke_data.sql");
        if (Files.exists(repoPath)) {
            return new FileSystemResource(repoPath);
        }

        throw new IllegalStateException("未找到 module-org 冒烟种子脚本");
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String envOrFallback(String primaryKey, String fallbackKey, String defaultValue) {
        String primary = System.getenv(primaryKey);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return env(fallbackKey, defaultValue);
    }

    private static String testSessionRedisNamespace() {
        return "workforce:test:session:" + env("TEST_DB_NAME", "workforce_hub_test");
    }

    private void deleteKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}
