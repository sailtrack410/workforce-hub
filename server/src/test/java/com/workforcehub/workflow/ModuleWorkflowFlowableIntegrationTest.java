package com.workforcehub.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.file.storage.LocalStorageProvider;
import com.workforcehub.plugin.runtime.PluginEventDispatcher;
import com.workforcehub.plugin.sdk.DomainEvent;
import com.workforcehub.plugin.sdk.EventTypes;
import com.workforcehub.support.FixedClockTestConfiguration;
import com.workforcehub.support.MutableTestClock;
import com.workforcehub.support.WorkflowFlowableIntegrationTestSupport;
import com.workforcehub.support.WorkflowFlowableTestConfiguration;
import jakarta.servlet.http.Cookie;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-workflow 真实 Flowable 引擎集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
@Import({WorkflowFlowableTestConfiguration.class, FixedClockTestConfiguration.class})
class ModuleWorkflowFlowableIntegrationTest extends WorkflowFlowableIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MutableTestClock mutableTestClock;

    @Autowired
    private LocalStorageProvider localStorageProvider;

    @Autowired
    private DataSource dataSource;

    @MockBean
    private PluginEventDispatcher pluginEventDispatcher;

    @Test
    @DisplayName("员工提交请假申请后应启动Flowable并由直属上级审批通过")
    void employeeLeaveRequestShouldBeApprovedByManagerWithFlowable() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

        mockMvc.perform(get("/api/employee/workflow/todos")
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data[0].requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data[0].taskId").value(scenario.managerTask().getId()))
            .andExpect(jsonPath("$.data[0].title").value("张三的请假申请"))
            .andExpect(jsonPath("$.data[0].status").value("PENDING"));

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", scenario.managerTask().getId())
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "同意，请安排好交接"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "审批通过后 Flowable 流程实例应结束");
        assertEquals("APPROVED", requestStatus(scenario.requestId()));
        assertEquals(1L, actionCount(scenario.requestId(), "APPROVE", MANAGER_USER_ID));
        assertEquals(0L, pendingTodoCount(scenario.requestId(), MANAGER_USER_ID));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(pluginEventDispatcher, times(1)).dispatch(eventCaptor.capture());
        assertWorkflowCompletedEvent(
            eventCaptor.getValue(),
            scenario,
            EventTypes.WORKFLOW_APPROVED,
            "approved",
            "APPROVED"
        );
    }

    @Test
    @DisplayName("审批当前节点名称应从库表读取")
    void workflowCurrentStepNameShouldComeFromDatabaseConfig() throws Exception {
        try {
            jdbcTemplate.update("""
                UPDATE wf_step_config
                SET step_name = '部门负责人审批'
                WHERE step_key = 'manager_approval'
                """);
            Cookie employeeSession = loginEmployee();
            LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

            assertEquals("部门负责人审批", currentStepName(scenario.requestId()));
        } finally {
            jdbcTemplate.update("""
                UPDATE wf_step_config
                SET step_name = '直属上级审批'
                WHERE step_key = 'manager_approval'
                """);
        }
    }

    @Test
    @DisplayName("补卡申请审批通过后应写入人工补卡打卡并重算考勤日结果")
    void approvedClockCorrectionRequestShouldCreateManualPunchAndRecalculateDailyResult() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        seedAttendanceRuleForEmployee();
        seedClockCorrectionWorkflowDefinition();
        punch(employeeSession, "2026-05-16T09:00:00", "ON_DUTY");
        assertEquals("ABNORMAL", dailyResultStatus(EMPLOYEE_USER_ID, "2026-05-16"));
        assertEquals(1L, exceptionCount(EMPLOYEE_USER_ID, "2026-05-16", "MISS_PUNCH"));

        MvcResult submitResult = mockMvc.perform(post("/api/employee/workflow/clock-correction-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetDate": "2026-05-16",
                      "missingPunchType": "OFF_DUTY",
                      "expectedPunchAt": "2026-05-16 18:00:00",
                      "reason": "下班时网络异常，补下班卡"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestId").isString())
            .andExpect(jsonPath("$.data.requestNo").isString())
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
            .andExpect(jsonPath("$.data.flowableProcessInstanceId").isString())
            .andReturn();

        JsonNode submitData = data(submitResult);
        long requestId = submitData.get("requestId").asLong();
        String processInstanceId = submitData.get("flowableProcessInstanceId").asText();
        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskAssignee(String.valueOf(MANAGER_USER_ID))
            .singleResult();
        assertNotNull(managerTask, "补卡申请应生成直属上级审批任务");
        assertEquals(requestBizId(requestId), clockCorrectionDetailId(requestId));

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}", requestId)
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.bizType").value("CLOCK_CORRECTION"))
            .andExpect(jsonPath("$.data.clockCorrection.targetDate").value("2026-05-16"))
            .andExpect(jsonPath("$.data.clockCorrection.missingPunchType").value("OFF_DUTY"))
            .andExpect(jsonPath("$.data.clockCorrection.expectedPunchAt").value("2026-05-16T18:00:00"));

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", managerTask.getId())
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "核实后同意补卡"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.requestId", requestId))
            .andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult(), "补卡审批通过后 Flowable 流程实例应结束");
        assertEquals("APPROVED", requestStatus(requestId));
        assertEquals(1L, manualPunchCount(EMPLOYEE_USER_ID, "2026-05-16", "OFF_DUTY"));
        assertEquals("NORMAL", dailyResultStatus(EMPLOYEE_USER_ID, "2026-05-16"));
        assertEquals(0L, exceptionCount(EMPLOYEE_USER_ID, "2026-05-16", "MISS_PUNCH"));

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-16")
                .param("endDate", "2026-05-16"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(false))
            .andExpect(jsonPath("$.data[0].workMinutes").value(540));
    }

    @Test
    @DisplayName("非审批人不能处理他人的Flowable审批任务")
    void nonAssigneeShouldNotApproveWorkflowTask() throws Exception {
        Cookie employeeSession = loginEmployee();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", scenario.managerTask().getId())
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "我自己审批自己的申请"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("当前用户不是该审批任务处理人"))
            .andExpect(jsonPath("$.data").doesNotExist());

        Task managerTask = taskService.createTaskQuery()
            .taskId(scenario.managerTask().getId())
            .taskAssignee(String.valueOf(MANAGER_USER_ID))
            .singleResult();
        assertNotNull(managerTask, "非审批人尝试处理后，直属上级任务仍应保留");
        assertNotNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "非审批人尝试处理后，流程实例仍应运行中");
        assertEquals("IN_APPROVAL", requestStatus(scenario.requestId()));
        assertEquals(0L, actionCount(scenario.requestId(), "APPROVE", EMPLOYEE_USER_ID));
        assertEquals(1L, pendingTodoCount(scenario.requestId(), MANAGER_USER_ID));
        verifyNoInteractions(pluginEventDispatcher);
    }

    @Test
    @DisplayName("直属上级可以驳回Flowable审批任务")
    void managerShouldRejectWorkflowTask() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

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
            .andExpect(jsonLong("$.data.requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data.status").value("REJECTED"));

        assertNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "审批驳回后 Flowable 流程实例应结束");
        assertEquals("REJECTED", requestStatus(scenario.requestId()));
        assertEquals(1L, actionCount(scenario.requestId(), "REJECT", MANAGER_USER_ID));
        assertEquals(0L, pendingTodoCount(scenario.requestId(), MANAGER_USER_ID));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(pluginEventDispatcher, times(1)).dispatch(eventCaptor.capture());
        assertWorkflowCompletedEvent(
            eventCaptor.getValue(),
            scenario,
            EventTypes.WORKFLOW_REJECTED,
            "rejected",
            "REJECTED"
        );
    }

    @Test
    @DisplayName("同一人工任务并发通过和驳回时只允许一个终态并发布一次事件")
    void concurrentApproveAndRejectShouldCompleteOnceAndDispatchOnce() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie approveSession = loginManager();
        Cookie rejectSession = loginManager();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(false);
            try (PreparedStatement statement = lockConnection.prepareStatement(
                    "SELECT id FROM wf_request WHERE id = ? FOR UPDATE")) {
                statement.setLong(1, scenario.requestId());
                statement.executeQuery();

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    Future<MvcResult> approved = executor.submit(() -> completeTask(
                        scenario.managerTask().getId(),
                        "approve",
                        approveSession,
                        ready,
                        start
                    ));
                    Future<MvcResult> rejected = executor.submit(() -> completeTask(
                        scenario.managerTask().getId(),
                        "reject",
                        rejectSession,
                        ready,
                        start
                    ));
                    ready.await();
                    start.countDown();
                    Thread.sleep(200L);
                    lockConnection.commit();

                    List<MvcResult> results = List.of(approved.get(), rejected.get());
                    long successCount = results.stream()
                        .filter(result -> result.getResponse().getStatus() == 200)
                        .count();
                    long missingCount = results.stream()
                        .filter(result -> result.getResponse().getStatus() == 404)
                        .count();
                    assertEquals(1L, successCount);
                    assertEquals(1L, missingCount);
                } finally {
                    executor.shutdownNow();
                }
            }
        }

        String terminalStatus = requestStatus(scenario.requestId());
        assertTrue(Set.of("APPROVED", "REJECTED").contains(terminalStatus));
        assertEquals(1L, terminalActionCount(scenario.requestId()));
        assertEquals(0L, pendingTodoCount(scenario.requestId(), MANAGER_USER_ID));
        ArgumentCaptor<DomainEvent> eventCaptor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(pluginEventDispatcher, times(1)).dispatch(eventCaptor.capture());
        assertEquals(terminalStatus, eventCaptor.getValue().payload().get("afterStatus"));
    }

    @Test
    @DisplayName("申请人可以撤回审批中的请假申请")
    void applicantShouldWithdrawWorkflowRequest() throws Exception {
        Cookie employeeSession = loginEmployee();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

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
            .andExpect(jsonLong("$.data.requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        assertNull(runtimeService.createProcessInstanceQuery()
            .processInstanceId(scenario.processInstanceId())
            .singleResult(), "申请撤回后 Flowable 流程实例应删除");
        assertEquals("WITHDRAWN", requestStatus(scenario.requestId()));
        assertEquals(1L, actionCount(scenario.requestId(), "WITHDRAW", EMPLOYEE_USER_ID));
        assertEquals(0L, pendingTodoCount(scenario.requestId(), MANAGER_USER_ID));
    }

    @Test
    @DisplayName("申请详情和审批轨迹应对流程参与人可查")
    void requestDetailAndActionsShouldBeQueryableByParticipant() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}", scenario.requestId())
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
            .andExpect(jsonPath("$.data.currentStepKey").value("manager_approval"))
            .andExpect(jsonPath("$.data.leave.reason").value("家中有事请假一天"))
            .andExpect(jsonPath("$.data.leave.durationMinutes").value(480));

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}/actions", scenario.requestId())
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/approve", scenario.managerTask().getId())
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "同意"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}", scenario.requestId())
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.finishedAt").isString());

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}/actions", scenario.requestId())
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data[0].requestId", scenario.requestId()))
            .andExpect(jsonLong("$.data[0].actorId", MANAGER_USER_ID))
            .andExpect(jsonPath("$.data[0].actionType").value("APPROVE"))
            .andExpect(jsonPath("$.data[0].comment").value("同意"))
            .andExpect(jsonPath("$.data[0].afterStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("员工只能分页查询自己的审批申请")
    void employeeShouldListOwnWorkflowRequestsOnly() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession);

        mockMvc.perform(get("/api/employee/workflow/requests")
                .cookie(employeeSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.pageNo").value(1))
            .andExpect(jsonPath("$.data.pageSize").value(20))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonLong("$.data.list[0].requestId", scenario.requestId()))
            .andExpect(jsonPath("$.data.list[0].status").value("IN_APPROVAL"))
            .andExpect(jsonPath("$.data.list[0].title").value("张三的请假申请"));

        mockMvc.perform(get("/api/employee/workflow/requests")
                .cookie(managerSession)
                .param("pageNo", "1")
                .param("pageSize", "20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(0))
            .andExpect(jsonPath("$.data.list").isEmpty());
    }

    @Test
    @DisplayName("员工提交请假应使用模板发布时固定的Flowable定义ID")
    void leaveRequestShouldStartPublishedFlowableDefinitionId() throws Exception {
        Cookie employeeSession = loginEmployee();
        ProcessDefinition publishedDefinition = repositoryService.createProcessDefinitionQuery()
            .processDefinitionKey("leave_manager_approval")
            .latestVersion()
            .singleResult();
        assertNotNull(publishedDefinition, "测试环境应存在请假审批流程定义");

        seedWorkflowDefinitions(publishedDefinition.getId());
        Long annualLeaveTypeId = seedAnnualLeaveType();
        repositoryService.createDeployment()
            .name("duplicate-leave-manager-approval")
            .addClasspathResource("processes/leave-manager-approval.bpmn20.xml")
            .deploy();

        MvcResult submitResult = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(annualLeaveTypeId, List.of())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        String processInstanceId = data(submitResult).get("flowableProcessInstanceId").asText();
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertNotNull(processInstance);
        assertEquals(
            publishedDefinition.getId(),
            processInstance.getProcessDefinitionId(),
            "模板发布版本应固定启动发布时绑定的 Flowable 定义 ID"
        );
    }

    @Test
    @DisplayName("请假时长必须与开始结束时间一致")
    void leaveRequestShouldRejectMismatchedDurationMinutes() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long annualLeaveTypeId = seedWorkflowAndLeaveType();

        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaveTypeId": %d,
                      "startAt": "2026-05-07 09:00:00",
                      "endAt": "2026-05-07 18:00:00",
                      "durationMinutes": 60,
                      "reason": "试图少填请假时长"
                    }
                    """.formatted(annualLeaveTypeId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(100400))
            .andExpect(jsonPath("$.message").value("请假时长与开始结束时间不一致"));
    }

    @Test
    @DisplayName("请假时长按半天向上取整")
    void leaveRequestShouldRoundUpToHalfDay() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long annualLeaveTypeId = seedWorkflowAndLeaveType();

        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaveTypeId": %d,
                      "startAt": "2026-05-07 17:00:00",
                      "endAt": "2026-05-08 10:00:00",
                      "durationMinutes": 240,
                      "reason": "半天起算"
                    }
                    """.formatted(annualLeaveTypeId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("请假申请可以绑定当前申请人拥有的有效附件引用")
    void leaveRequestShouldBindOwnedAttachmentRefs() throws Exception {
        Cookie employeeSession = loginEmployee();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(EMPLOYEE_USER_ID, "请假证明.pdf", 0, false);
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, List.of(sourceAttachmentRefId));
        long workflowAttachmentRefId = workflowAttachmentRefId(scenario.requestId(), sourceAttachmentRefId);

        mockMvc.perform(get("/api/employee/workflow/requests/{requestId}", scenario.requestId())
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.attachments[0].attachmentRefId", workflowAttachmentRefId))
            .andExpect(jsonPath("$.data.attachments[0].displayName").value("请假证明.pdf"))
            .andExpect(jsonPath("$.data.attachments[0].mimeType").value("application/pdf"));

        assertEquals(1L, requestAttachmentCount(scenario.requestId(), workflowAttachmentRefId));
        assertEquals("WF_REQUEST", attachmentOwnerType(workflowAttachmentRefId));
        assertEquals(scenario.requestId(), attachmentOwnerId(workflowAttachmentRefId));
        assertEquals(fileObjectId(sourceAttachmentRefId), fileObjectId(workflowAttachmentRefId));
    }

    @Test
    @DisplayName("审批参与人可以为审批附件申请下载票据并下载原文件")
    void participantShouldDownloadWorkflowAttachmentWithTicket() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "请假证明.pdf",
            0,
            false,
            "workflow-attachment-download-content"
        );
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, List.of(sourceAttachmentRefId));
        long workflowAttachmentRefId = workflowAttachmentRefId(scenario.requestId(), sourceAttachmentRefId);

        MvcResult ticketResult = mockMvc.perform(post(
                    "/api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets",
                    scenario.requestId(),
                    workflowAttachmentRefId
                )
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "审批查看证明材料"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.ticketNo").isString())
            .andExpect(jsonPath("$.data.downloadUrl").isString())
            .andExpect(jsonPath("$.data.fileName").value("请假证明.pdf"))
            .andReturn();

        String ticketNo = data(ticketResult).get("ticketNo").asText();
        mockMvc.perform(get("/api/employee/workflow/attachments/download/{ticketNo}", ticketNo)
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .bytes("workflow-attachment-download-content".getBytes(StandardCharsets.UTF_8)));

        assertEquals("USED", downloadTicketStatus(ticketNo));
        assertEquals(1L, auditCount("USER", MANAGER_USER_ID, "WORKFLOW_ATTACHMENT_DOWNLOAD_TICKET_CREATED"));
        assertEquals(1L, auditCount("USER", MANAGER_USER_ID, "WORKFLOW_ATTACHMENT_DOWNLOAD_EXECUTED"));
    }

    @Test
    @DisplayName("审批附件不能绕过workflow授权入口走普通个人文件下载")
    void workflowAttachmentShouldNotUseEmployeePersonalFileDownloadEndpoint() throws Exception {
        Cookie employeeSession = loginEmployee();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "请假证明.pdf",
            0,
            false,
            "workflow-attachment-personal-endpoint-content"
        );
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, List.of(sourceAttachmentRefId));
        long workflowAttachmentRefId = workflowAttachmentRefId(scenario.requestId(), sourceAttachmentRefId);

        mockMvc.perform(post("/api/employee/files/{attachmentRefId}/download-tickets", workflowAttachmentRefId)
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "尝试绕过审批附件授权入口"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("员工端文件接口仅允许下载个人文件"));
    }

    @Test
    @DisplayName("非参与人或非本申请附件不能申请审批附件下载票据")
    void nonParticipantOrForeignAttachmentShouldNotDownloadWorkflowAttachment() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie outsiderSession = login("wangwu.employee", "Employee@123456");
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "请假证明.pdf",
            0,
            false,
            "workflow-attachment-auth-content"
        );
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, List.of(sourceAttachmentRefId));
        long workflowAttachmentRefId = workflowAttachmentRefId(scenario.requestId(), sourceAttachmentRefId);

        mockMvc.perform(post(
                    "/api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets",
                    scenario.requestId(),
                    workflowAttachmentRefId
                )
                .cookie(outsiderSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "越权查看"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权查看该审批申请"));

        long otherPersonalAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "未绑定证明.pdf",
            0,
            false,
            "workflow-foreign-attachment-content"
        );
        mockMvc.perform(post(
                    "/api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets",
                    scenario.requestId(),
                    otherPersonalAttachmentRefId
                )
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "错误附件"
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404))
            .andExpect(jsonPath("$.message").value("审批附件不存在"));
    }

    @Test
    @DisplayName("审批附件创建下载票据后扫描失败不能下载")
    void workflowAttachmentDownloadShouldRecheckLatestScanStatus() throws Exception {
        Cookie employeeSession = loginEmployee();
        Cookie managerSession = loginManager();
        long sourceAttachmentRefId = seedPersonalAttachmentRef(
            EMPLOYEE_USER_ID,
            "扫描状态变化证明.pdf",
            0,
            false,
            "workflow-attachment-scan-status-content"
        );
        LeaveApprovalScenario scenario = submitLeaveRequest(employeeSession, List.of(sourceAttachmentRefId));
        long workflowAttachmentRefId = workflowAttachmentRefId(scenario.requestId(), sourceAttachmentRefId);

        MvcResult ticketResult = mockMvc.perform(post(
                    "/api/employee/workflow/requests/{requestId}/attachments/{attachmentRefId}/download-tickets",
                    scenario.requestId(),
                    workflowAttachmentRefId
                )
                .cookie(managerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "downloadType": "SINGLE",
                      "reason": "审批查看证明材料"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        String ticketNo = data(ticketResult).get("ticketNo").asText();
        updateAttachmentScanStatus(workflowAttachmentRefId, "FAILED");

        mockMvc.perform(get("/api/employee/workflow/attachments/download/{ticketNo}", ticketNo)
                .cookie(managerSession))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value(100409))
            .andExpect(jsonPath("$.message").value("附件安全扫描未通过"));

        assertEquals("ACTIVE", downloadTicketStatus(ticketNo));
    }

    @Test
    @DisplayName("请假申请不能绑定他人或已删除的附件引用")
    void leaveRequestShouldRejectInvalidAttachmentRefs() throws Exception {
        Cookie employeeSession = loginEmployee();
        Long annualLeaveTypeId = seedWorkflowAndLeaveType();
        long managerAttachmentRefId = seedPersonalAttachmentRef(MANAGER_USER_ID, "他人证明.pdf", 0, false);
        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(annualLeaveTypeId, List.of(managerAttachmentRefId))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(100403))
            .andExpect(jsonPath("$.message").value("无权使用该附件引用"));

        long deletedAttachmentRefId = seedPersonalAttachmentRef(EMPLOYEE_USER_ID, "已删除证明.pdf", 1, false);
        mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(annualLeaveTypeId, List.of(deletedAttachmentRefId))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404))
            .andExpect(jsonPath("$.message").value("附件引用不存在"));
    }

    private LeaveApprovalScenario submitLeaveRequest(Cookie employeeSession) throws Exception {
        return submitLeaveRequest(employeeSession, List.of());
    }

    private LeaveApprovalScenario submitLeaveRequest(Cookie employeeSession, List<Long> attachmentRefIds) throws Exception {
        Long annualLeaveTypeId = seedWorkflowAndLeaveType();

        MvcResult submitResult = mockMvc.perform(post("/api/employee/workflow/leave-requests")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leaveRequestJson(annualLeaveTypeId, attachmentRefIds)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.requestId").isString())
            .andExpect(jsonPath("$.data.requestNo").isString())
            .andExpect(jsonPath("$.data.status").value("IN_APPROVAL"))
            .andExpect(jsonPath("$.data.flowableProcessInstanceId").isString())
            .andReturn();

        JsonNode submitData = data(submitResult);
        long requestId = submitData.get("requestId").asLong();
        String processInstanceId = submitData.get("flowableProcessInstanceId").asText();

        assertEquals(requestId, leaveDetailRequestId(requestId));
        assertEquals(leaveDetailId(requestId), requestBizId(requestId));

        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertNotNull(processInstance, "提交请假后应存在运行中的 Flowable 流程实例");

        Task managerTask = taskService.createTaskQuery()
            .processInstanceId(processInstanceId)
            .taskAssignee(String.valueOf(MANAGER_USER_ID))
            .singleResult();
        assertNotNull(managerTask, "直属上级应收到 Flowable 审批任务");
        assertEquals("manager_approval", managerTask.getTaskDefinitionKey());

        return new LeaveApprovalScenario(requestId, processInstanceId, managerTask);
    }

    private Long seedWorkflowAndLeaveType() {
        seedWorkflowDefinitions();
        return seedAnnualLeaveType();
    }

    private void seedWorkflowDefinitions(String flowableDefinitionId) {
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
                (id, template_id, version_no, flowable_definition_key, flowable_definition_id,
                 status, published_by, published_at, effective_from)
            VALUES
                (9301, 9201, 1, 'leave_manager_approval', ?, 'PUBLISHED', 8001, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY))
            """, flowableDefinitionId);
    }

    private String leaveRequestJson(Long annualLeaveTypeId, List<Long> attachmentRefIds) {
        String attachmentsJson = attachmentRefIds.stream()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .map(ids -> ",\n                      \"attachmentRefIds\": [" + ids + "]")
            .orElse("");
        return """
            {
              "leaveTypeId": %d,
              "startAt": "2026-05-07 09:00:00",
              "endAt": "2026-05-07 18:00:00",
              "durationMinutes": 480,
              "reason": "家中有事请假一天"%s
            }
            """.formatted(annualLeaveTypeId, attachmentsJson);
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
        return result.getResponse().getCookie("SESSION");
    }

    private MvcResult completeTask(String taskId,
                                   String action,
                                   Cookie session,
                                   CountDownLatch ready,
                                   CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return mockMvc.perform(post("/api/employee/workflow/tasks/{taskId}/{action}", taskId, action)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "comment": "并发审批"
                    }
                    """))
            .andReturn();
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

    private void seedClockCorrectionWorkflowDefinition() {
        jdbcTemplate.update("""
            INSERT INTO wf_process_type
                (id, process_code, process_name, biz_type, status)
            VALUES
                (9111, 'CLOCK_CORRECTION', '补卡流程', 'CLOCK_CORRECTION', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template
                (id, process_type_id, template_code, template_name, status)
            VALUES
                (9211, 9111, 'CLOCK_CORRECTION_MANAGER_APPROVAL', '补卡直属上级审批', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO wf_template_version
                (id, template_id, version_no, flowable_definition_key, status, published_by, published_at, effective_from)
            VALUES
                (9311, 9211, 1, 'clock_correction_manager_approval', 'PUBLISHED', 8001, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY))
            """);
    }

    private Long seedAnnualLeaveType() {
        long leaveTypeId = 9401L;
        jdbcTemplate.update("""
            INSERT INTO lv_type
                (id, leave_code, leave_name, balance_mode, status)
            VALUES
                (?, 'ANNUAL_LEAVE', '年假', 'DEDUCT', 'ACTIVE')
            """, leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account
                (id, user_id, leave_type_id, available_minutes, frozen_minutes, version, status)
            VALUES
                (9402, ?, ?, 9600, 0, 0, 'ACTIVE')
            """, EMPLOYEE_USER_ID, leaveTypeId);
        jdbcTemplate.update("""
            INSERT INTO lv_account_txn
                (id, account_id, txn_type, change_minutes, balance_after,
                 source_type, source_id, source_txn_key, effective_at)
            VALUES
                (9403, 9402, 'ADJUST', 9600, 9600,
                 'ADMIN', 8001, 'workflow-flowable-seed-leave-balance', NOW())
            """);
        return leaveTypeId;
    }

    private long seedPersonalAttachmentRef(long uploaderId, String displayName, int isDeleted, boolean deletedAt) {
        return seedPersonalAttachmentRef(uploaderId, displayName, isDeleted, deletedAt, "workflow-test-content-" + displayName);
    }

    private long seedPersonalAttachmentRef(long uploaderId,
                                           String displayName,
                                           int isDeleted,
                                           boolean deletedAt,
                                           String content) {
        long suffix = Math.abs((long) content.hashCode());
        long fileObjectId = 9500_0000L + suffix;
        long attachmentRefId = 9600_0000L + suffix;
        String contentSha256 = "%064x".formatted(attachmentRefId);
        String objectKey = "workflow-test/" + attachmentRefId + ".pdf";
        writeLocalObject(objectKey, content.getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("""
            INSERT INTO sys_file_object
                (id, storage_provider, bucket_name, object_key, file_name, file_ext,
                 mime_type, file_size, file_hash, content_sha256, ref_count,
                 upload_status, scan_status, created_by, is_deleted)
            VALUES
                (?, 'LOCAL', 'workforce-local', ?, ?, 'pdf',
                 'application/pdf', ?, ?, ?, 1,
                 'UPLOADED', 'PASSED', ?, 0)
            """,
            fileObjectId,
            objectKey,
            displayName,
            (long) content.getBytes(StandardCharsets.UTF_8).length,
            contentSha256,
            contentSha256,
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

    private void writeLocalObject(String objectKey, byte[] content) {
        try {
            Path target = Path.of(localStorageProvider
                .buildUploadTarget("workforce-local", objectKey, "application/pdf", 60)
                .uploadUrl());
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (Exception e) {
            throw new IllegalStateException("写入本地测试文件失败", e);
        }
    }

    private Long requestAttachmentCount(long requestId, long attachmentRefId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_request_attachment
            WHERE request_id = ? AND attachment_ref_id = ?
            """, Long.class, requestId, attachmentRefId);
    }

    private Long workflowAttachmentRefId(long requestId, long sourceAttachmentRefId) {
        Long sourceFileObjectId = fileObjectId(sourceAttachmentRefId);
        return jdbcTemplate.queryForObject("""
            SELECT wra.attachment_ref_id
            FROM wf_request_attachment wra
            JOIN sys_attachment_ref ar ON ar.id = wra.attachment_ref_id
            WHERE wra.request_id = ?
              AND ar.file_object_id = ?
              AND ar.owner_type = 'WF_REQUEST'
            LIMIT 1
            """, Long.class, requestId, sourceFileObjectId);
    }

    private Long fileObjectId(long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT file_object_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            attachmentRefId
        );
    }

    private void updateAttachmentScanStatus(long attachmentRefId, String scanStatus) {
        jdbcTemplate.update("""
            UPDATE sys_file_object fo
            JOIN sys_attachment_ref ar ON ar.file_object_id = fo.id
            SET fo.scan_status = ?
            WHERE ar.id = ?
            """,
            scanStatus,
            attachmentRefId
        );
    }

    private String attachmentOwnerType(long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT owner_type FROM sys_attachment_ref WHERE id = ?",
            String.class,
            attachmentRefId
        );
    }

    private Long attachmentOwnerId(long attachmentRefId) {
        return jdbcTemplate.queryForObject(
            "SELECT owner_id FROM sys_attachment_ref WHERE id = ?",
            Long.class,
            attachmentRefId
        );
    }

    private JsonNode data(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.get("data");
    }

    private void assertWorkflowCompletedEvent(DomainEvent event,
                                              LeaveApprovalScenario scenario,
                                              String eventType,
                                              String result,
                                              String afterStatus) {
        String stableId = "workflow-" + result + "-" + scenario.requestId() + "-" + scenario.managerTask().getId();
        assertEquals(eventType, event.eventType());
        assertEquals(stableId, event.eventId());
        assertEquals(stableId, event.idempotencyKey());
        assertEquals(1L, event.version());
        assertEquals(String.valueOf(MANAGER_USER_ID), event.triggeredBy());
        assertTrue(event.occurredAt() > 0);
        assertEquals(Set.of(
            "requestId",
            "requestNo",
            "bizType",
            "applicantUserId",
            "taskId",
            "beforeStatus",
            "afterStatus"
        ), event.payload().keySet());
        assertEquals(scenario.requestId(), event.payload().get("requestId"));
        assertEquals(requestNo(scenario.requestId()), event.payload().get("requestNo"));
        assertEquals("LEAVE", event.payload().get("bizType"));
        assertEquals(EMPLOYEE_USER_ID, event.payload().get("applicantUserId"));
        assertEquals(scenario.managerTask().getId(), event.payload().get("taskId"));
        assertEquals("IN_APPROVAL", event.payload().get("beforeStatus"));
        assertEquals(afterStatus, event.payload().get("afterStatus"));
    }

    private String requestStatus(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM wf_request WHERE id = ?",
            String.class,
            requestId
        );
    }

    private String requestNo(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT request_no FROM wf_request WHERE id = ?",
            String.class,
            requestId
        );
    }

    private String currentStepName(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT current_step_name FROM wf_request WHERE id = ?",
            String.class,
            requestId
        );
    }

    private Long requestBizId(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT biz_id FROM wf_request WHERE id = ?",
            Long.class,
            requestId
        );
    }

    private Long leaveDetailId(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM wf_req_leave WHERE request_id = ?",
            Long.class,
            requestId
        );
    }

    private Long leaveDetailRequestId(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT request_id FROM wf_req_leave WHERE request_id = ?",
            Long.class,
            requestId
        );
    }

    private Long clockCorrectionDetailId(long requestId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM wf_req_clock_correction WHERE request_id = ?",
            Long.class,
            requestId
        );
    }

    private Long actionCount(long requestId, String actionType, long actorId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_task_action
            WHERE request_id = ? AND action_type = ? AND actor_type = 'USER' AND actor_id = ?
            """, Long.class, requestId, actionType, actorId);
    }

    private Long terminalActionCount(long requestId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wf_task_action
            WHERE request_id = ?
              AND action_type IN ('APPROVE', 'REJECT')
            """, Long.class, requestId);
    }

    private Long pendingTodoCount(long requestId, long ownerId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_todo_item
            WHERE source_type = 'WORKFLOW_TASK'
              AND source_id = ?
              AND owner_type = 'USER'
              AND owner_id = ?
              AND status = 'PENDING'
            """, Long.class, requestId, ownerId);
    }

    private String downloadTicketStatus(String ticketNo) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM sys_download_ticket WHERE ticket_no = ?",
            String.class,
            ticketNo
        );
    }

    private Long auditCount(String operatorType, long operatorId, String actionType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM sys_audit_log
            WHERE operator_type = ? AND operator_id = ? AND action_type = ?
            """, Long.class, operatorType, operatorId, actionType);
    }

    private void seedAttendanceRuleForEmployee() {
        jdbcTemplate.update("""
            INSERT INTO att_work_calendar
                (id, calendar_code, calendar_name, calendar_type, status)
            VALUES
                (9701, 'CAL_CLOCK_CORRECTION', '补卡考勤日历', 'STANDARD', 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_shift
                (id, shift_code, shift_name, cross_day_flag, required_punch_count, status)
            VALUES
                (9702, 'SHIFT_CLOCK_CORRECTION', '补卡考勤班次', 0, 2, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_shift_segment
                (id, shift_id, segment_no, start_time, end_time,
                 on_duty_punch_start, on_duty_punch_end,
                 off_duty_punch_start, off_duty_punch_end)
            VALUES
                (9703, 9702, 1, '09:00:00', '18:00:00',
                 '08:00:00', '10:00:00',
                 '17:00:00', '19:00:00')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_group
                (id, group_code, group_name, calendar_id, default_shift_id,
                 late_grace_minutes, early_leave_grace_minutes, status)
            VALUES
                (9704, 'GROUP_CLOCK_CORRECTION', '补卡考勤组', 9701, 9702, 0, 0, 'ACTIVE')
            """);
        jdbcTemplate.update("""
            INSERT INTO att_group_member
                (id, group_id, user_id, effective_from, effective_to, source_type, status)
            VALUES
                (9705, 9704, ?, '2026-05-01', NULL, 'MANUAL', 'ACTIVE')
            """, EMPLOYEE_USER_ID);
    }

    private void punch(Cookie employeeSession, String punchAt, String punchType) throws Exception {
        mutableTestClock.setTime(LocalDateTime.parse(punchAt));
        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "%s",
                      "punchType": "%s",
                      "sourceType": "PC"
                    }
                    """.formatted(punchAt, punchType)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    private String dailyResultStatus(long userId, String workDate) {
        return jdbcTemplate.queryForObject("""
            SELECT result_status
            FROM att_daily_result
            WHERE user_id = ? AND work_date = ?
            """, String.class, userId, workDate);
    }

    private Long exceptionCount(long userId, String workDate, String exceptionType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
              AND ae.exception_type = ?
            """, Long.class, userId, workDate, exceptionType);
    }

    private Long manualPunchCount(long userId, String workDate, String punchType) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_punch_record
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = ?
              AND source_type = 'MANUAL'
              AND status = 'NORMAL'
            """, Long.class, userId, workDate, punchType);
    }

    private record LeaveApprovalScenario(
        long requestId,
        String processInstanceId,
        Task managerTask
    ) {
    }
}
