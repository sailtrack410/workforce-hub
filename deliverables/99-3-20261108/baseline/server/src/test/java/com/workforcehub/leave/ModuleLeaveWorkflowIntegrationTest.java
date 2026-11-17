package com.workforcehub.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.FixedClockTestConfiguration;
import com.workforcehub.support.MutableTestClock;
import com.workforcehub.support.WorkflowFlowableIntegrationTestSupport;
import com.workforcehub.support.WorkflowFlowableTestConfiguration;
import jakarta.servlet.http.Cookie;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-leave 与 module-workflow 的余额联动集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import({WorkflowFlowableTestConfiguration.class, FixedClockTestConfiguration.class})
class ModuleLeaveWorkflowIntegrationTest extends WorkflowFlowableIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MutableTestClock mutableTestClock;

    @Test
    @DisplayName("员工请假余额不足时应在提交阶段被拦截且不生成审批申请")
    void leaveRequestShouldRejectWhenBalanceInsufficient() throws Exception {
        Cookie employeeSession = loginEmployee();
        long leaveTypeId = seedWorkflowAndLeaveType();

        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("假期余额不足"));

        assertEquals(0L, workflowRequestCount());
        assertEquals(0L, leaveDeductTxnCount(leaveTypeId));
    }

    @Test
    @DisplayName("请假审批通过后应扣减申请人假期余额并写审批扣减流水")
    void approvedLeaveRequestShouldDeductBalance() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);

        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, leaveTypeId);
        assertEquals(480, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));
        assertEquals(1L, leaveWorkflowTxnCount(leaveTypeId, "FREEZE"));

        approveManagerTask(managerSession, scenario.managerTask().getId());

        assertNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "审批通过后 Flowable 流程实例应结束");
        assertEquals(480, availableMinutes(leaveTypeId));
        assertEquals(0, frozenMinutes(leaveTypeId));
        assertEquals(1L, leaveDeductTxnCount(leaveTypeId));
        assertEquals(-480, leaveDeductChangeMinutes(scenario.requestId()));
        assertEquals(480, leaveDeductBalanceAfter(scenario.requestId()));
    }

    @Test
    @DisplayName("请假提交后应冻结余额并阻止超额重复提交")
    void submittedLeaveRequestShouldFreezeBalanceBeforeApproval() throws Exception {
        Cookie employeeSession = loginEmployee();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 720);

        submitLeaveRequest(employeeSession, leaveTypeId);

        assertEquals(240, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));
        assertEquals(1L, leaveWorkflowTxnCount(leaveTypeId, "FREEZE"));

        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("假期余额不足"));

        assertEquals(1L, workflowRequestCount());
        assertEquals(240, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));
    }

    @Test
    @DisplayName("审批扣减幂等键被非审批扣减流水占用时应拒绝")
    void workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);

        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, leaveTypeId);
        seedAdminAdjustmentWithSourceTxnKey(leaveTypeId, "WORKFLOW_REQUEST:" + scenario.requestId());

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", scenario.managerTask().getId())
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "同意，请安排好交接"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("幂等键已被不同假期扣减操作使用"));

        assertNotNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "扣减幂等冲突时 Flowable 流程应保持待审批");
        assertEquals(900, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));
        assertEquals(0L, leaveDeductTxnCount(leaveTypeId));
        assertEquals("IN_APPROVAL", workflowRequestStatus(scenario.requestId()));
    }

    @Test
    @DisplayName("请假审批通过后应重算当天考勤为请假结果而不是缺卡异常")
    void approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);
        seedAttendanceRuleForEmployee();

        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, leaveTypeId);
        approveManagerTask(managerSession, scenario.managerTask().getId());

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].resultStatus").value("LEAVE"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(false))
            .andExpect(jsonPath("$.data[0].absenceMinutes").value(0));

        assertEquals(1L, dailyResultCount("LEAVE"));
        assertEquals(0L, attendanceExceptionCount());
    }

    @Test
    @DisplayName("小时假审批通过后只应抵扣实际覆盖的缺勤分钟")
    void approvedHourlyLeaveShouldOnlyOffsetCoveredAbsenceMinutes() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);
        seedAttendanceRuleForEmployee();

        LeaveApprovalScenario scenario = submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 09:00:00",
            "2026-05-07 10:00:00",
            240,
            "上午临时请假一小时"
        );
        approveManagerTask(managerSession, scenario.managerTask().getId());

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(true))
            .andExpect(jsonPath("$.data[0].absenceMinutes").value(480));

        assertEquals(1L, attendanceExceptionCount());
    }

    @Test
    @DisplayName("跨天请假提交时应自动扣除非工作时段，仅按工作时间计算分钟数")
    void crossDayLeaveShouldOnlyCountWorkingMinutes() throws Exception {
        Cookie employeeSession = loginEmployee();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);

        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(
                    leaveTypeId,
                    "2026-05-07 17:00:00",
                    "2026-05-08 10:00:00",
                    240,
                    "跨天请假"
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"));

        assertEquals(720, availableMinutes(leaveTypeId));
        assertEquals(240, frozenMinutes(leaveTypeId));
    }

    @Test
    @DisplayName("重叠小时假审批通过后不应重复抵扣同一段缺勤")
    void overlappingApprovedHourlyLeavesShouldNotDoubleOffsetAbsenceMinutes() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);
        seedAttendanceRuleForEmployee();

        LeaveApprovalScenario firstScenario = submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 09:00:00",
            "2026-05-07 10:00:00",
            240,
            "上午临时请假一小时"
        );
        approveManagerTask(managerSession, firstScenario.managerTask().getId());
        LeaveApprovalScenario secondScenario = submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 09:30:00",
            "2026-05-07 10:30:00",
            240,
            "继续请假一小时"
        );
        approveManagerTask(managerSession, secondScenario.managerTask().getId());

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(true))
            .andExpect(jsonPath("$.data[0].absenceMinutes").value(450));

        assertEquals(1L, attendanceExceptionCount());
    }

    @Test
    @DisplayName("小时假审批通过后应抵扣对应迟到和早退窗口")
    void approvedHourlyLeaveShouldOffsetLateAndEarlyWindows() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);
        seedAttendanceRuleForEmployee();

        LeaveApprovalScenario morningScenario = submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 09:00:00",
            "2026-05-07 10:00:00",
            240,
            "上午请假一小时"
        );
        approveManagerTask(managerSession, morningScenario.managerTask().getId());
        LeaveApprovalScenario eveningScenario = submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 17:00:00",
            "2026-05-07 18:00:00",
            240,
            "下午请假一小时"
        );
        approveManagerTask(managerSession, eveningScenario.managerTask().getId());

        punch(employeeSession, "2026-05-07T10:00:00", "ON_DUTY");
        punch(employeeSession, "2026-05-07T17:00:00", "OFF_DUTY");

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(false))
            .andExpect(jsonPath("$.data[0].lateMinutes").value(0))
            .andExpect(jsonPath("$.data[0].earlyLeaveMinutes").value(0))
            .andExpect(jsonPath("$.data[0].absenceMinutes").value(0));

        assertEquals(0L, attendanceExceptionCount());
    }

    @Test
    @DisplayName("请假审批驳回后不应扣减假期余额")
    void rejectedLeaveRequestShouldNotDeductBalance() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);

        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, leaveTypeId);
        assertEquals(480, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/reject", scenario.managerTask().getId())
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "请假理由不充分"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertEquals(960, availableMinutes(leaveTypeId));
        assertEquals(0, frozenMinutes(leaveTypeId));
        assertEquals(0L, leaveDeductTxnCount(leaveTypeId));
        assertEquals(1L, leaveWorkflowTxnCount(leaveTypeId, "UNFREEZE"));
    }

    @Test
    @DisplayName("请假申请撤回后应释放已冻结余额")
    void withdrawnLeaveRequestShouldReleaseFrozenBalance() throws Exception {
        Cookie employeeSession = loginEmployee();
        long leaveTypeId = seedWorkflowAndLeaveType();
        seedLeaveAccount(leaveTypeId, 960);

        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, leaveTypeId);
        assertEquals(480, availableMinutes(leaveTypeId));
        assertEquals(480, frozenMinutes(leaveTypeId));

        mockMvc.perform(post("/api/employee/workflow/requests/{requestId}/withdraw", scenario.requestId())
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "计划有变，取消请假"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        assertEquals(960, availableMinutes(leaveTypeId));
        assertEquals(0, frozenMinutes(leaveTypeId));
        assertEquals(0L, leaveDeductTxnCount(leaveTypeId));
        assertEquals(1L, leaveWorkflowTxnCount(leaveTypeId, "UNFREEZE"));
    }

    private LeaveApprovalScenario submitLeaveRequest(Cookie employeeSession, long leaveTypeId) throws Exception {
        return submitLeaveRequest(
            employeeSession,
            leaveTypeId,
            "2026-05-07 09:00:00",
            "2026-05-07 18:00:00",
            480,
            "家中有事请假一天"
        );
    }

    private LeaveApprovalScenario submitLeaveRequest(Cookie employeeSession,
                                                     long leaveTypeId,
                                                     String startAt,
                                                     String endAt,
                                                     int durationMinutes,
                                                     String reason) throws Exception {
        MvcResult submitResult = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(leaveTypeId, startAt, endAt, durationMinutes, reason)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
            .andReturn();

        JsonNode submitData = data(submitResult);
        long requestId = submitData.get("requestId").asLong();
        String processInstanceId = submitData.get("flowableProcessInstanceId").asText();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertNotNull(processInstance, "提交请假后应存在运行中的 Flowable 流程实例");
        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskAssignee(String.valueOf(MANAGER_USER_ID))
            .singleResult();
        assertNotNull(managerTask, "直属上级应收到 Flowable 审批任务");
        return new LeaveApprovalScenario(requestId, processInstanceId, managerTask);
    }

    private void approveManagerTask(Cookie managerSession, String taskId) throws Exception {
        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", taskId)
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "同意，请安排好交接"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    private long seedWorkflowAndLeaveType() {
        seedWorkflowDefinitions();
        return seedAnnualLeaveType();
    }

    private void seedWorkflowDefinitions() {
        jdbcTemplate.update("""
            INSERT INTO wf_process_type
                (id, process_code, process_name, biz_type, status)
            VALUES
                (9101, 'LEAVE', '请假流程', 'LEAVE', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template
                (id, process_type_id, template_code, template_name, status)
            VALUES
                (9201, 9101, 'LEAVE_MANAGER_APPROVAL', '请假直属上级审批', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template_version
                (id, template_id, version_no, flowable_definition_key, status, published_by, published_at, effective_from)
            VALUES
                (9301, 9201, 1, 'leave_manager_approval', 'PUBLISHED', 8001, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY))
            """);
    }

    private long seedAnnualLeaveType() {
        long leaveTypeId = 9401L;
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status)
            VALUES
                (?, 'ANNUAL_LEAVE', '年假', 'DEDUCT', 'ACTIVE')
            """, leaveTypeId);
        return leaveTypeId;
    }

    private void seedLeaveAccount(long leaveTypeId, int availableMinutes) {
        long accountId = 9501L;
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status)
            VALUES
                (?, ?, ?, ?, 0, 0, 'ACTIVE')
            """, accountId, EMPLOYEE_USER_ID, leaveTypeId, availableMinutes);
        jdbcTemplate.update("""
            INSERT INTO lv_account_txn
                (id, account_id, txn_type, change_minutes, balance_after,
                 source_type, source_id, source_txn_key, effective_at)
            VALUES
                (9502, ?, 'ADJUST', ?, ?, 'ADMIN', 8001, 'seed-leave-balance', NOW())
            """, accountId, availableMinutes, availableMinutes);
    }

    private void seedAdminAdjustmentWithSourceTxnKey(long leaveTypeId, String sourceTxnKey) {
        Long accountId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            LIMIT 1
            """, Long.class, EMPLOYEE_USER_ID, leaveTypeId);
        assertNotNull(accountId, "测试前应已初始化假期账户");
        jdbcTemplate.update("""
            UPDATE lv_account
            SET available_minutes = 900,
                version = version + 1
            WHERE id = ?
            """, accountId);
        jdbcTemplate.update("""
            INSERT INTO lv_account_txn
                (id, account_id, txn_type, change_minutes, balance_after,
                 source_type, source_id, source_txn_key, effective_at)
            VALUES
                (9503, ?, 'ADJUST', -60, 900, 'ADMIN', 8001, ?, NOW())
            """, accountId, sourceTxnKey);
    }

    private void seedAttendanceRuleForEmployee() {
        jdbcTemplate.update("""
            INSERT INTO att_work_calendar
                (id, calendar_code, calendar_name, calendar_type, status)
            VALUES
                (9601, 'CAL_LEAVE_ATT', '请假考勤日历', 'STANDARD', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_shift
                (id, shift_code, shift_name, cross_day_flag, required_punch_count, status)
            VALUES
                (9602, 'SHIFT_LEAVE_ATT', '请假考勤班次', 0, 2, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_shift_segment
                (id, shift_id, segment_no, start_time, end_time,
                 on_duty_punch_start, on_duty_punch_end,
                 off_duty_punch_start, off_duty_punch_end)
            VALUES
                (9603, 9602, 1, '09:00:00', '18:00:00',
                 '08:00:00', '10:00:00',
                 '17:00:00', '19:00:00')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_group
                (id, group_code, group_name, calendar_id, default_shift_id,
                 late_grace_minutes, early_leave_grace_minutes, status)
            VALUES
                (9604, 'GROUP_LEAVE_ATT', '请假考勤组', 9601, 9602, 0, 0, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_group_member
                (id, group_id, user_id, effective_from, effective_to, source_type, status)
            VALUES
                (9605, 9604, ?, '2026-05-01', NULL, 'MANUAL', 'ACTIVE')
            """, EMPLOYEE_USER_ID);
    }

    private String leaveRequestJson(long leaveTypeId) {
        return leaveRequestJson(
            leaveTypeId,
            "2026-05-07 09:00:00",
            "2026-05-07 18:00:00",
            480,
            "家中有事请假一天"
        );
    }

    private String leaveRequestJson(long leaveTypeId,
                                    String startAt,
                                    String endAt,
                                    int durationMinutes,
                                    String reason) {
        return """
            {
              "leaveTypeId": %d,
              "startAt": "%s",
              "endAt": "%s",
              "durationMinutes": %d,
              "reason": "%s"
            }
            """.formatted(leaveTypeId, startAt, endAt, durationMinutes, reason);
    }

    private void punch(Cookie employeeSession, String punchAt, String punchType) throws Exception {
        mutableTestClock.setTime(LocalDateTime.parse(punchAt));
        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchType": "%s",
                      "sourceType": "PC"
                    }
                    """.formatted(punchType)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private Cookie loginEmployee() throws Exception {
        return login("zhangsan.employee", "Employee@123456");
    }

    private Cookie loginManager() throws Exception {
        return login("lisi.manager", "Employee@123456");
    }

    private Cookie login(String loginName, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录后应返回 SESSION");
        return session;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long workflowRequestCount() {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_request
            """, Long.class);
        return count == null ? 0L : count;
    }

    private String workflowRequestStatus(long requestId) {
        return jdbcTemplate.queryForObject("""
            SELECT status
            FROM wf_request
            WHERE id = ?
            """, String.class, requestId);
    }

    private int availableMinutes(long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT available_minutes
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Integer.class, EMPLOYEE_USER_ID, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }

    private int frozenMinutes(long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT frozen_minutes
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Integer.class, EMPLOYEE_USER_ID, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }

    private long leaveDeductTxnCount(long leaveTypeId) {
        return leaveWorkflowTxnCount(leaveTypeId, "DEDUCT");
    }

    private long leaveWorkflowTxnCount(long leaveTypeId, String txnType) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM lv_account_txn lat
            JOIN lv_account la ON la.id = lat.account_id
            WHERE la.user_id = ?
              AND la.leave_type_id = ?
              AND lat.txn_type = ?
              AND lat.source_type = 'WORKFLOW_REQUEST'
            """, Long.class, EMPLOYEE_USER_ID, leaveTypeId, txnType);
        return count == null ? 0L : count;
    }

    private int leaveDeductChangeMinutes(long requestId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT change_minutes
            FROM lv_account_txn
            WHERE source_type = 'WORKFLOW_REQUEST'
              AND source_id = ?
              AND txn_type = 'DEDUCT'
            """, Integer.class, requestId);
        return minutes == null ? 0 : minutes;
    }

    private int leaveDeductBalanceAfter(long requestId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT balance_after
            FROM lv_account_txn
            WHERE source_type = 'WORKFLOW_REQUEST'
              AND source_id = ?
              AND txn_type = 'DEDUCT'
            """, Integer.class, requestId);
        return minutes == null ? 0 : minutes;
    }

    private long dailyResultCount(String resultStatus) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_daily_result
            WHERE user_id = ?
              AND work_date = '2026-05-07'
              AND result_status = ?
              AND absence_minutes = 0
              AND exception_flag = 0
            """, Long.class, EMPLOYEE_USER_ID, resultStatus);
        return count == null ? 0L : count;
    }

    private long attendanceExceptionCount() {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = '2026-05-07'
            """, Long.class, EMPLOYEE_USER_ID);
        return count == null ? 0L : count;
    }

    private record LeaveApprovalScenario(
        long requestId,
        String processInstanceId,
        Task managerTask
    ) {
    }
}
