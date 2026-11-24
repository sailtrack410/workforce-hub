package com.workforcehub.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.DatabaseIdGenerator;
import com.workforcehub.personal.controller.EmployeePersonalTodoController;
import com.workforcehub.personal.repository.PersonalTodoRepository;
import com.workforcehub.personal.service.PersonalTodoService;
import com.workforcehub.task.controller.EmployeeTeamTaskController;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.dto.TeamTaskSaveRequest;
import com.workforcehub.task.service.TeamTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 独立 H2、真实 V71 迁移、实际控制器/服务/SQL，验证任务派发与私人待办边界。 */
class TaskTodoBusinessRulesTest {
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule())
        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private JdbcTemplate jdbc;
    private DatabaseIdGenerator ids;
    private MockMvc mvc;
    private static final String TASKS = "/api/employee/team-tasks";
    private static final String TODOS = "/api/employee/personal-todos";

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:task_todo_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V71__add_team_task_and_personal_todo.sql"),
            new ClassPathResource("db/migration/V72__add_personal_todo_start_at.sql"),
            new ClassPathResource("db/migration/V73__add_team_task_collaboration.sql"),
            new ClassPathResource("db/migration/V74__add_personal_todo_schedule_and_reminder.sql"))
            .execute(source);
        jdbc.execute("""
            CREATE TABLE org_user (id BIGINT PRIMARY KEY, real_name VARCHAR(128), primary_dept_id BIGINT,
                manager_user_id BIGINT, status VARCHAR(32) DEFAULT 'ACTIVE',
                employment_status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)
            """);
        jdbc.update("""
            INSERT INTO org_user (id,real_name,primary_dept_id,manager_user_id) VALUES
            (2002,'派发人',10,NULL),(2003,'同事',10,NULL),(2004,'跨部门下属',20,2002),
            (2005,'另一同事',10,NULL),(9999,'其他部门',20,NULL)
            """);
        jdbc.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(64) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES ('global',10000)");
        ids = new DatabaseIdGenerator(source);
        mvc = MockMvcBuilders.standaloneSetup(
            new EmployeeTeamTaskController(new TeamTaskService(new TeamTaskRepository(jdbc))),
            new EmployeePersonalTodoController(new PersonalTodoService(new PersonalTodoRepository(jdbc))))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        jdbc.update("""
            INSERT INTO team_task (task_id,title,creator_id,assignee_id,status,start_date,end_date)
            VALUES (101,'团队工作',2002,2003,'TODO',DATE '2026-11-18',DATE '2026-11-20'),
                   (102,'收到工作',2003,2002,'TODO',DATE '2026-11-19',DATE '2026-11-22'),
                   (103,'无关工作',2004,9999,'TODO',DATE '2026-11-18',DATE '2026-11-20')
            """);
        employee(2002);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (ids != null) ids.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @Test
    void taskIsDispatchedToAnotherEmployeeAndDoesNotCreatePersonalTodo() throws Exception {
        var created = data(post(TASKS), task(2003), 200);
        assertEquals(2002, created.get("creatorId").asLong());
        assertEquals(2003, created.get("assigneeId").asLong());
        assertEquals("TODO", created.get("status").asText());
        employee(2003);
        data(put(TASKS + "/" + created.get("taskId").asLong() + "/status"), "{\"status\":\"DONE\"}", 200);
        assertEquals(0, count("personal_todo"));
    }

    @ParameterizedTest
    @CsvSource({"2003,200", "2004,200", "2005,200", "2002,400", "9999,403", "-1,400", "0,400", "8888,403"})
    void validatesAssigneeAndTeamScope(long assignee, int expected) throws Exception {
        data(post(TASKS), task(assignee), expected);
        assertEquals(expected == 200 ? 4 : 3, count("team_task"));
    }

    @ParameterizedTest
    @CsvSource({"ACTIVE,ACTIVE,0,200", "ACTIVE,PROBATION,0,200", "ACTIVE,PENDING_OFFBOARD,0,200",
        "INACTIVE,ACTIVE,0,403", "ACTIVE,OFFBOARDED,0,403", "ACTIVE,PENDING_ONBOARD,0,403", "ACTIVE,ACTIVE,1,403"})
    void rejectsUnavailableAssignees(String status, String employment, int deleted, int expected) throws Exception {
        jdbc.update("UPDATE org_user SET status=?,employment_status=?,is_deleted=? WHERE id=2003", status, employment, deleted);
        data(post(TASKS), task(2003), expected);
    }

    @Test
    void candidateListAndCreateUseTheSameScope() throws Exception {
        jdbc.update("UPDATE org_user SET status='INACTIVE' WHERE id=2005");
        var candidates = data(get(TASKS + "/assignees"), null, 200);
        assertEquals(List.of(2003L,2004L), userIds(candidates));
        jdbc.update("UPDATE org_user SET primary_dept_id=20 WHERE id=2003");
        data(post(TASKS), task(2003), 403);
        assertEquals(List.of(2004L), userIds(data(get(TASKS + "/assignees"), null, 200)));
    }

    @ParameterizedTest
    @CsvSource({"ALL,2", "ASSIGNED,1", "CREATED,1"})
    void taskListsHaveExplicitReceivedAndDispatchedScopes(String scope, int expected) throws Exception {
        var rows = data(get(TASKS + "?scope=" + scope), null, 200);
        assertEquals(expected, rows.size());
        if (scope.equals("ASSIGNED")) assertEquals(102, rows.get(0).get("taskId").asLong());
        if (scope.equals("CREATED")) assertEquals(101, rows.get(0).get("taskId").asLong());
    }

    @Test
    void unknownScopeAndInvertedQueryRangeAreBadRequests() throws Exception {
        data(get(TASKS + "?scope=PRIVATE"), null, 400);
        data(get(TASKS + "?start=2026-11-22&end=2026-11-18"), null, 400);
        data(get(TODOS + "?start=2026-11-22T00:00:00&end=2026-11-18T00:00:00"), null, 400);
    }

    @Test
    void dateQueryFindsOverlapsAndRespectsOpenBoundsAndScope() throws Exception {
        assertEquals(2, data(get(TASKS + "?start=2026-11-20&end=2026-11-21"), null, 200).size());
        assertEquals(1, data(get(TASKS + "?start=2026-11-21"), null, 200).size());
        assertEquals(1, data(get(TASKS + "?end=2026-11-18"), null, 200).size());
        assertEquals(0, data(get(TASKS + "?scope=CREATED&assigneeId=9999"), null, 200).size());
    }

    @ParameterizedTest
    @CsvSource({"TODO,IN_PROGRESS,200", "TODO,DONE,200", "TODO,TODO,200", "TODO,IN_REVIEW,400",
        "IN_PROGRESS,DONE,200", "IN_PROGRESS,TODO,200", "DONE,TODO,200", "DONE,IN_PROGRESS,409",
        "IN_REVIEW,DONE,200", "IN_REVIEW,IN_PROGRESS,200", "IN_REVIEW,TODO,200"})
    void assigneeControlsExecutionAndCanCompleteDirectly(String from, String to, int expected) throws Exception {
        jdbc.update("UPDATE team_task SET status=? WHERE task_id=101", from);
        employee(2003);
        data(put(TASKS + "/101/status"), "{\"status\":\"" + to + "\"}", expected);
        assertEquals(expected == 200 ? to : from, jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
    }

    @Test
    void dispatcherCannotMarkOtherPersonsWorkStartedOrCompleted() throws Exception {
        data(put(TASKS + "/101/status"), "{\"status\":\"IN_PROGRESS\"}", 403);
        data(put(TASKS + "/101/status"), "{\"status\":\"DONE\"}", 403);
        assertEquals("TODO", jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
    }

    @Test
    void concurrentStatusChangesCannotBeOverwrittenByAnOutdatedExecutionRequest() {
        var repository = repositoryWithConcurrentChange("UPDATE team_task SET status='DONE' WHERE task_id=101");
        employee(2003);
        var failure = assertThrows(BizException.class,
            () -> new TeamTaskService(repository).updateStatus(101L, "IN_PROGRESS"));
        assertEquals(409, failure.getHttpStatus().value());
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
    }

    @Test
    void concurrentCompletionCannotBeOverwrittenByAnOutdatedEdit() throws Exception {
        var repository = repositoryWithConcurrentChange("UPDATE team_task SET status='DONE' WHERE task_id=101");
        var request = json.readValue(task(2003), TeamTaskSaveRequest.class);
        var failure = assertThrows(BizException.class,
            () -> new TeamTaskService(repository).update(101L, request));
        assertEquals(409, failure.getHttpStatus().value());
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
        assertEquals("团队工作", jdbc.queryForObject("SELECT title FROM team_task WHERE task_id=101", String.class));
    }

    @Test
    void legacySelfAssignedTaskRemainsAccessibleAndCompletable() throws Exception {
        jdbc.update("UPDATE team_task SET assignee_id=2002 WHERE task_id=101");
        data(get(TASKS + "/101"), null, 200);
        data(put(TASKS + "/101/status"), "{\"status\":\"DONE\"}", 200);
        data(delete(TASKS + "/101"), null, 200);
    }

    @Test
    void reassignmentRestartsWorkAndRevokesOldAssignee() throws Exception {
        jdbc.update("UPDATE team_task SET status='DONE' WHERE task_id=101");
        var updated = data(put(TASKS + "/101"), task(2005), 200);
        assertEquals("TODO", updated.get("status").asText());
        employee(2003);
        data(get(TASKS + "/101"), null, 404);
        data(put(TASKS + "/101/status"), "{\"status\":\"DONE\"}", 404);
        employee(2005);
        data(put(TASKS + "/101/status"), "{\"status\":\"DONE\"}", 200);
    }

    @Test
    void editingCannotSpoofCreatorOrCompletionStatusAndInvalidReassignmentLeavesOriginalIntact() throws Exception {
        data(put(TASKS + "/101"), task(9999), 403);
        var updated = data(put(TASKS + "/101"), task(2003).replace("{", "{\"creatorId\":9999,\"status\":\"DONE\","), 200);
        assertEquals(2002, updated.get("creatorId").asLong());
        assertEquals(2003, updated.get("assigneeId").asLong());
        assertEquals("TODO", updated.get("status").asText());
    }

    @Test
    void responseExposesNamesAndCurrentUserCapabilitiesForLaterUi() throws Exception {
        var dispatched = data(get(TASKS + "/101"), null, 200);
        assertEquals("派发人", dispatched.path("creatorName").asText());
        assertEquals("同事", dispatched.path("assigneeName").asText());
        assertTrue(dispatched.path("canEdit").asBoolean());
        assertTrue(dispatched.path("canDelete").asBoolean());
        assertFalse(dispatched.path("canComplete").asBoolean());
        employee(2003);
        var received = data(get(TASKS + "/101"), null, 200);
        assertFalse(received.path("canEdit").asBoolean());
        assertTrue(received.path("canComplete").asBoolean());
        assertTrue(received.path("canStart").asBoolean());
        var done = data(put(TASKS + "/101/status"), "{\"status\":\"DONE\"}", 200);
        assertTrue(done.path("canReopen").asBoolean());
        assertFalse(done.path("canStart").asBoolean());
    }

    @ParameterizedTest
    @CsvSource({"title,129", "description,1025", "groupId,65"})
    void taskTextLimitsReturn400BeforeDatabaseWrite(String field, int length) throws Exception {
        var request = (com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(task(2003));
        request.put(field, "中".repeat(length));
        data(post(TASKS), request.toString(), 400);
        assertEquals(3, count("team_task"));
    }

    @Test
    void taskRequiresDatesAndOrderedRange() throws Exception {
        data(post(TASKS), "{\"title\":\"缺日期\",\"assigneeId\":2003}", 400);
        data(post(TASKS), task(2003).replace("2026-11-20", "2026-11-17"), 400);
        data(post(TASKS), task(2003).replace("2026-11-18", "not-a-date"), 400);
        assertEquals(3, count("team_task"));
    }

    @Test
    void privateTodoCanHaveNoDateAndCanBeEditedCompletedReopenedAndDeleted() throws Exception {
        var created = data(post(TODOS), "{\"title\":\"自己的安排\",\"ownerId\":2003}", 200);
        long id = created.get("todoId").asLong();
        assertEquals(2002, created.get("ownerId").asLong());
        assertTrue(created.get("dueAt").isNull());
        assertEquals("TASK", created.get("category").asText());
        data(put(TODOS + "/" + id), "{\"title\":\"更新安排\",\"dueAt\":\"2026-11-19 10:30:00\"}", 200);
        var updated = data(put(TODOS + "/" + id), "{\"title\":\"取消时间\"}", 200);
        assertTrue(updated.get("dueAt").isNull());
        data(put(TODOS + "/" + id + "/complete"), null, 200);
        var stamp = jdbc.queryForObject("SELECT completed_at FROM personal_todo WHERE todo_id=?", java.sql.Timestamp.class, id);
        data(put(TODOS + "/" + id + "/complete"), null, 200);
        assertEquals(stamp, jdbc.queryForObject("SELECT completed_at FROM personal_todo WHERE todo_id=?", java.sql.Timestamp.class, id));
        var reopened = data(put(TODOS + "/" + id + "/reopen"), null, 200);
        assertTrue(reopened.get("completedAt").isNull());
        data(delete(TODOS + "/" + id), null, 200);
        assertEquals(0, count("personal_todo"));
        assertEquals(3, count("team_task"));
    }

    @ParameterizedTest
    @CsvSource({"APPROVAL", "MEETING", "TASK", "REMINDER"})
    void legacyTodoCategoriesRemainPrivateManualLabels(String category) throws Exception {
        data(post(TODOS), "{\"title\":\"手动记录\",\"category\":\"" + category + "\"}", 200);
        assertEquals(1, data(get(TODOS), null, 200).size());
        employee(2003);
        assertEquals(0, data(get(TODOS), null, 200).size());
        assertEquals(3, count("team_task"));
    }

    @Test
    void otherEmployeesCannotModifyPersonalTodo() throws Exception {
        var id = data(post(TODOS), "{\"title\":\"私事\",\"dueAt\":\"2026-11-19 10:30:00\"}", 200).get("todoId").asLong();
        employee(2003);
        data(put(TODOS + "/" + id), "{\"title\":\"篡改\"}", 404);
        data(put(TODOS + "/" + id + "/complete"), null, 404);
        data(put(TODOS + "/" + id + "/reopen"), null, 404);
        data(delete(TODOS + "/" + id), null, 404);
        assertEquals(1, count("personal_todo"));
    }

    @Test
    void completingAnAlreadyDoneTodoPreservesItsOriginalCompletionTime() throws Exception {
        long id = data(post(TODOS), "{\"title\":\"私事\",\"dueAt\":\"2026-11-19 10:30:00\"}", 200).get("todoId").asLong();
        jdbc.update("UPDATE personal_todo SET status='DONE',completed_at=TIMESTAMP '2026-11-18 10:00:00' WHERE todo_id=?", id);
        data(put(TODOS + "/" + id + "/complete"), null, 200);
        assertEquals(java.sql.Timestamp.valueOf("2026-11-18 10:00:00"),
            jdbc.queryForObject("SELECT completed_at FROM personal_todo WHERE todo_id=?", java.sql.Timestamp.class, id));
    }

    @Test
    void queryFiltersComposeWithOwnershipAndExcludeUndatedTodosOnlyWhenDateIsSpecified() throws Exception {
        data(post(TODOS), "{\"title\":\"无日期\"}", 200);
        data(post(TODOS), "{\"title\":\"有日期\",\"dueAt\":\"2026-11-19 10:30:00\"}", 200);
        assertEquals(2, data(get(TODOS + "?status=PENDING&category=TASK"), null, 200).size());
        assertEquals(1, data(get(TODOS + "?start=2026-11-19T00:00:00&end=2026-11-19T23:59:59"), null, 200).size());
        employee(2003);
        assertEquals(0, data(get(TODOS + "?status=PENDING&category=TASK"), null, 200).size());
    }

    @Test
    void anonymousCannotCreateOrReadPrivateTodos() throws Exception {
        SecurityContextHolder.clearContext();
        data(get(TODOS), null, 401);
        data(post(TODOS), "{\"title\":\"私事\"}", 401);
        data(get(TASKS + "/assignees"), null, 401);
        assertEquals(0, count("personal_todo"));
    }

    @Test
    void adminCannotUseMatchingEmployeeIdToAccessPrivateTodo() throws Exception {
        var id = data(post(TODOS), "{\"title\":\"私事\",\"dueAt\":\"2026-11-19 10:30:00\"}", 200).get("todoId").asLong();
        var principal = new AdminUserDetails(2002L,"fixture","","fixture","ACTIVE",List.of(),List.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        data(get(TODOS), null, 403);
        data(post(TODOS), "{\"title\":\"私事\"}", 403);
        data(put(TODOS + "/" + id + "/complete"), null, 403);
        data(put(TODOS + "/" + id + "/reopen"), null, 403);
        data(put(TODOS + "/" + id), "{\"title\":\"篡改\"}", 403);
        data(delete(TODOS + "/" + id), null, 403);
        assertEquals(1, count("personal_todo"));
    }

    @Test
    void malformedInputsAndTodoLengthsAreBadRequests() throws Exception {
        data(post(TODOS), "{\"title\":\"" + "中".repeat(129) + "\"}", 400);
        data(post(TODOS), "{\"title\":\"安排\",\"note\":\"" + "中".repeat(1025) + "\"}", 400);
        data(post(TODOS), "{\"title\":\"安排\",\"dueAt\":\"invalid\"}", 400);
        data(get(TASKS + "/bad-id"), null, 400);
        data(get(TASKS + "?start=invalid"), null, 400);
        data(post(TODOS), "{\"title\":\" \"}", 400);
        assertEquals(0, count("personal_todo"));
    }

    @Test
    void personalTodoRangePersistsAndCanBeCleared() throws Exception {
        var created = data(post(TODOS), """
            {"title":"跨天安排","startAt":"2026-11-18 09:00:00","dueAt":"2026-11-21 18:00:00"}
            """, 200);
        assertTrue(created.path("startAt").asText().contains("2026-11-18"));
        long id = created.path("todoId").asLong();
        var updated = data(put(TODOS + "/" + id), """
            {"title":"修改安排","startAt":"2026-11-19 10:00:00","dueAt":"2026-11-22 18:00:00"}
            """, 200);
        assertTrue(updated.path("startAt").asText().contains("2026-11-19"));
        var cleared = data(put(TODOS + "/" + id), "{\"title\":\"清除时间\",\"startAt\":null,\"dueAt\":null}", 200);
        assertTrue(cleared.path("startAt").isNull() || cleared.path("startAt").isMissingNode());
        assertTrue(cleared.path("dueAt").isNull() || cleared.path("dueAt").isMissingNode());
    }

    @Test
    void personalTodoRangeRejectsInvertedTimesBeforeWriting() throws Exception {
        String invalid = """
            {"title":"反向安排","startAt":"2026-11-20 11:00:00","dueAt":"2026-11-20 10:00:00"}
            """;
        data(post(TODOS), invalid, 400);
        assertEquals(0, count("personal_todo"));
        long id = data(post(TODOS), "{\"title\":\"原安排\"}", 200).path("todoId").asLong();
        data(put(TODOS + "/" + id), invalid, 400);
        assertEquals("原安排", data(get(TODOS), null, 200).get(0).path("title").asText());
        data(post(TODOS), "{\"title\":\"无效时间\",\"startAt\":\"invalid\"}", 400);
    }

    @Test
    void personalTodoRangeAllowsEitherEndpointAndSameTime() throws Exception {
        var startOnly = data(post(TODOS), "{\"title\":\"开始\",\"startAt\":\"2026-11-19 09:00:00\"}", 200);
        assertTrue(startOnly.path("startAt").asText().contains("2026-11-19"));
        data(post(TODOS), "{\"title\":\"截止\",\"dueAt\":\"2026-11-19 09:00:00\"}", 200);
        data(post(TODOS), """
            {"title":"即时安排","startAt":"2026-11-19 09:00:00","dueAt":"2026-11-19 09:00:00"}
            """, 200);
        assertEquals(3, count("personal_todo"));
    }

    @Test
    void personalTodoRangeQueryFindsOverlapsAndKeepsOwnership() throws Exception {
        data(post(TODOS), """
            {"title":"跨周安排","startAt":"2026-11-16 09:00:00","dueAt":"2026-11-25 18:00:00"}
            """, 200);
        data(post(TODOS), "{\"title\":\"开始点\",\"startAt\":\"2026-11-19 12:00:00\"}", 200);
        data(post(TODOS), "{\"title\":\"截止点\",\"dueAt\":\"2026-11-19 12:00:00\"}", 200);
        data(post(TODOS), "{\"title\":\"未安排\"}", 200);
        assertEquals(3, data(get(TODOS + "?start=2026-11-19T12:00:00&end=2026-11-19T12:00:00"), null, 200).size());
        assertEquals(1, data(get(TODOS + "?start=2026-11-25T18:00:00"), null, 200).size());
        assertEquals(1, data(get(TODOS + "?end=2026-11-16T09:00:00"), null, 200).size());
        assertEquals(4, data(get(TODOS), null, 200).size());
        employee(2003);
        assertEquals(0, data(get(TODOS + "?start=2026-11-19T00:00:00"), null, 200).size());
    }

    @Test
    void personalTodoSchedulePersistsAllDayAndReminderAndCanClearThem() throws Exception {
        var created = data(post(TODOS), """
            {"title":"会议","category":"MEETING","startAt":"2026-11-25 10:00:00","dueAt":"2026-11-25 11:00:00","reminderMinutes":10}
            """, 200);
        assertEquals(10, created.path("reminderMinutes").asInt());
        assertTrue(created.path("reminderAt").asText().contains("09:50:00"));
        long id = created.path("todoId").asLong();
        var allDay = data(put(TODOS + "/" + id), """
            {"title":"全天事项","category":"REMINDER","startAt":"2026-11-26 00:00:00","dueAt":"2026-11-27 23:59:59","allDay":true}
            """, 200);
        assertTrue(allDay.path("allDay").asBoolean());
        assertTrue(allDay.path("reminderAt").isNull());
        assertEquals("REMINDER", allDay.path("category").asText());
        var cleared = data(put(TODOS + "/" + id), "{\"title\":\"未安排\"}", 200);
        assertFalse(cleared.path("allDay").asBoolean());
        assertTrue(cleared.path("reminderMinutes").isNull());
        employee(2003); assertEquals(0, data(get(TODOS), null, 200).size());
        data(put(TODOS + "/" + id), "{\"title\":\"越权\"}", 404);
    }

    @Test
    void invalidReminderAndUndatedAllDayNeverWrite() throws Exception {
        data(post(TODOS), "{\"title\":\"缺日期\",\"reminderMinutes\":10}", 400);
        data(post(TODOS), "{\"title\":\"缺日期\",\"allDay\":true}", 400);
        data(post(TODOS), "{\"title\":\"非法间隔\",\"startAt\":\"2026-11-25 10:00:00\",\"reminderMinutes\":-1}", 400);
        data(post(TODOS), "{\"title\":\"非法间隔\",\"startAt\":\"2026-11-25 10:00:00\",\"reminderMinutes\":9999}", 400);
        assertEquals(0, count("personal_todo"));
    }

    @Test
    void reminderUsesStartOrLegacyDeadlineAndPreservesClaimWhenTimeUnchanged() throws Exception {
        long id = data(post(TODOS), "{\"title\":\"截止提醒\",\"dueAt\":\"2026-11-25 10:00:00\",\"reminderMinutes\":0}", 200).path("todoId").asLong();
        jdbc.update("UPDATE personal_todo SET reminded_at = CURRENT_TIMESTAMP WHERE todo_id = ?", id);
        data(put(TODOS + "/" + id), "{\"title\":\"修改标题\",\"dueAt\":\"2026-11-25 10:00:00\",\"reminderMinutes\":0}", 200);
        assertNotNull(jdbc.queryForObject("SELECT reminded_at FROM personal_todo WHERE todo_id = ?", java.sql.Timestamp.class, id));
        data(put(TODOS + "/" + id), "{\"title\":\"重新排程\",\"startAt\":\"2026-11-26 10:00:00\",\"reminderMinutes\":10}", 200);
        assertNull(jdbc.queryForObject("SELECT reminded_at FROM personal_todo WHERE todo_id = ?", java.sql.Timestamp.class, id));
    }

    private JsonNode data(MockHttpServletRequestBuilder request, String payload, int expected) throws Exception {
        if (payload != null) request.contentType("application/json").content(payload);
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).path("data");
    }

    private String task(long assignee) {
        return "{\"title\":\"团队工作\",\"assigneeId\":" + assignee
            + ",\"startDate\":\"2026-11-18\",\"endDate\":\"2026-11-20\"}";
    }

    private List<Long> userIds(JsonNode rows) {
        var values = new java.util.ArrayList<Long>();
        rows.forEach(row -> values.add(row.get("userId").asLong()));
        return values.stream().sorted().toList();
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void employee(long id) {
        var principal = new EmployeeUserDetails(id,id*10,"fixture","","fixture","ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
    }

    private TeamTaskRepository repositoryWithConcurrentChange(String sql) {
        return new TeamTaskRepository(jdbc) {
            private boolean first = true;
            @Override
            public Optional<TeamTaskResponse> findById(Long taskId) {
                var found = super.findById(taskId);
                if (first) {
                    first = false;
                    jdbc.update(sql);
                }
                return found;
            }
        };
    }
}
