package com.workforcehub.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.common.util.DatabaseIdGenerator;
import com.workforcehub.task.controller.EmployeeTeamTaskController;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.task.service.TeamTaskService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class TeamTaskCollaborationTest {
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json()
        .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    private JdbcTemplate jdbc;
    private DatabaseIdGenerator ids;
    private MockMvc mvc;
    private static final String API = "/api/employee/team-tasks";

    @BeforeEach
    void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:collab_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V71__add_team_task_and_personal_todo.sql"),
            new ClassPathResource("db/migration/V73__add_team_task_collaboration.sql")).execute(source);
        jdbc.execute("""
            CREATE TABLE org_user (id BIGINT PRIMARY KEY, real_name VARCHAR(128), primary_dept_id BIGINT,
              manager_user_id BIGINT, status VARCHAR(32), employment_status VARCHAR(32), is_deleted INT DEFAULT 0)
            """);
        jdbc.update("INSERT INTO org_user VALUES (2002,'派发人',10,NULL,'ACTIVE','ACTIVE',0),(2003,'负责人',10,NULL,'ACTIVE','ACTIVE',0),(9999,'无关人',20,NULL,'ACTIVE','ACTIVE',0)");
        jdbc.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(64) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES ('global',10000)");
        ids = new DatabaseIdGenerator(source);
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeTeamTaskController(new TeamTaskService(new TeamTaskRepository(jdbc))))
            .setControllerAdvice(new GlobalExceptionHandler()).setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        jdbc.update("INSERT INTO team_task (task_id,title,creator_id,assignee_id,start_date,end_date) VALUES (101,'主任务',2002,2003,DATE '2026-11-23',DATE '2026-11-25'),(102,'另一任务',9999,2002,DATE '2026-11-23',DATE '2026-11-25')");
        employee(2002);
    }

    @AfterEach
    void cleanup() { SecurityContextHolder.clearContext(); ids.close(); jdbc.execute("SHUTDOWN"); }

    @Test
    void namedListsPersistRenameAndRemainScopedToParticipants() throws Exception {
        var list = data(post(API + "/lists"), "{\"content\":\"  发布清单  \"}", 200);
        long id = list.path("listId").asLong();
        assertEquals("发布清单", list.path("name").asText());
        data(put(API + "/lists/" + id), "{\"content\":\"本周发布\"}", 200);
        jdbc.update("UPDATE team_task SET group_id = ? WHERE task_id = 101", String.valueOf(id));
        employee(2003);
        var lists = data(get(API + "/lists"), null, 200);
        assertEquals(1, lists.size()); assertEquals("本周发布", lists.get(0).path("name").asText());
        assertFalse(lists.get(0).path("canEdit").asBoolean());
        data(put(API + "/lists/" + id), "{\"content\":\"越权\"}", 403);
        employee(9999); assertTrue(data(get(API + "/lists"), null, 200).isEmpty());
        data(delete(API + "/lists/" + id), null, 404);
        employee(2002); data(delete(API + "/lists/" + id), null, 409);
        jdbc.update("UPDATE team_task SET group_id = NULL WHERE task_id = 101");
        data(delete(API + "/lists/" + id), null, 200);
    }

    @Test
    void listNamesAreValidatedAndCannotBeInjectedIntoAnotherOwnersList() throws Exception {
        data(post(API + "/lists"), json.writeValueAsString(java.util.Map.of("content", "x".repeat(65))), 400);
        data(post(API + "/lists"), "{\"content\":\"   \"}", 400);
        employee(9999); long listId = data(post(API + "/lists"), "{\"content\":\"私有\"}", 200).path("listId").asLong();
        employee(2002);
        data(post(API), "{\"title\":\"任务\",\"assigneeId\":2003,\"startDate\":\"2026-11-23\",\"endDate\":\"2026-11-25\",\"groupId\":\"" + listId + "\"}", 403);
    }

    @Test
    void subtasksPersistAndUseParentPermissionsForTitleAndCompletion() throws Exception {
        var created = data(post(API + "/101/subtasks"), "{\"title\":\"  接口回归  \"}", 200);
        long id = created.path("subtasks").get(0).path("subtaskId").asLong();
        assertEquals("接口回归", created.path("subtasks").get(0).path("title").asText());
        data(put(API + "/101/subtasks/" + id), "{\"title\":\"接口回归\",\"completed\":true}", 403);
        employee(2003);
        data(post(API + "/101/subtasks"), "{\"title\":\"越权创建\"}", 403);
        data(put(API + "/101/subtasks/" + id), "{\"title\":\"越权改名\",\"completed\":true}", 403);
        var done = data(put(API + "/101/subtasks/" + id), "{\"title\":\"接口回归\",\"completed\":true}", 200);
        assertTrue(done.path("subtasks").get(0).path("completed").asBoolean());
        assertEquals("TODO", done.path("status").asText());
        data(delete(API + "/101/subtasks/" + id), null, 403);
        employee(2002);
        data(put(API + "/101/subtasks/" + id), "{\"title\":\"完整接口回归\",\"completed\":true}", 200);
        data(delete(API + "/101/subtasks/" + id), null, 200);
        assertTrue(data(get(API + "/101"), null, 200).path("subtasks").isEmpty());
    }

    @Test
    void childIdsCannotBeMovedAcrossParentTasks() throws Exception {
        long child = data(post(API + "/101/subtasks"), "{\"title\":\"子任务\"}", 200).path("subtasks").get(0).path("subtaskId").asLong();
        data(put(API + "/102/subtasks/" + child), "{\"title\":\"子任务\",\"completed\":true}", 404);
        data(delete(API + "/102/subtasks/" + child), null, 403);
        assertEquals(1, count("team_task_subtask"));
    }

    @Test
    void commentsAndHistoryAreOrderedAndTextIsStoredWithoutHtmlExecution() throws Exception {
        data(post(API + "/101/comments"), "{\"content\":\"  <script>alert(1)</script>  \"}", 200);
        employee(2003); data(put(API + "/101/status"), "{\"status\":\"DONE\"}", 200);
        var events = data(get(API + "/101"), null, 200).path("activities");
        assertEquals(2, events.size());
        assertEquals("COMMENT", events.get(0).path("kind").asText());
        assertEquals("<script>alert(1)</script>", events.get(0).path("content").asText());
        assertEquals("派发人", events.get(0).path("actorName").asText());
        assertEquals("STATUS", events.get(1).path("kind").asText());
        data(post(API + "/101/comments"), "{\"content\":\" \"}", 400);
        data(post(API + "/101/comments"), json.writeValueAsString(java.util.Map.of("content", "x".repeat(2001))), 400);
    }

    @Test
    void repeatedStatusRequestPreservesCollaborationDetailsWithoutExtraHistory() throws Exception {
        data(post(API + "/101/subtasks"), "{\"title\":\"接口回归\"}", 200);
        employee(2003);
        data(put(API + "/101/watch?watching=true"), null, 200);
        data(post(API + "/101/comments"), "{\"content\":\"已检查\"}", 200);
        data(put(API + "/101/status"), "{\"status\":\"DONE\"}", 200);
        var repeated = data(put(API + "/101/status"), "{\"status\":\"DONE\"}", 200);
        assertTrue(repeated.path("watching").asBoolean());
        assertEquals(1, repeated.path("subtasks").size());
        assertEquals(3, repeated.path("activities").size());
        assertEquals(1, repeated.path("watcherNames").size());
        assertEquals(3, count("team_task_activity"));
    }

    @Test
    void watchIsIdempotentAndSurvivesReadAndListReload() throws Exception {
        data(put(API + "/101/watch?watching=true"), null, 200);
        data(put(API + "/101/watch?watching=true"), null, 200); assertEquals(1, count("team_task_watch"));
        assertTrue(data(get(API + "/101"), null, 200).path("watching").asBoolean());
        var rows = data(get(API), null, 200);
        for (var row : rows) if (row.path("taskId").asLong() == 101) assertTrue(row.path("watching").asBoolean());
        employee(2003); assertFalse(data(get(API + "/101"), null, 200).path("watching").asBoolean());
        employee(2002); data(put(API + "/101/watch?watching=false"), null, 200); assertEquals(0, count("team_task_watch"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"comments", "subtasks", "watch?watching=true"})
    void unrelatedEmployeesCannotReadOrWriteTaskCollaboration(String endpoint) throws Exception {
        employee(9999);
        if (endpoint.startsWith("watch")) data(put(API + "/101/" + endpoint), null, 404);
        else data(post(API + "/101/" + endpoint), endpoint.equals("comments") ? "{\"content\":\"越权\"}" : "{\"title\":\"越权\"}", 404);
        assertEquals(0, count("team_task_activity")); assertEquals(0, count("team_task_subtask"));
    }

    @Test
    void reassignmentRevokesFormerAssigneeAccessAndWatchVisibility() throws Exception {
        employee(2003); data(put(API + "/101/watch?watching=true"), null, 200);
        jdbc.update("UPDATE team_task SET assignee_id = 2002 WHERE task_id = 101");
        data(post(API + "/101/comments"), "{\"content\":\"过期权限\"}", 404);
        employee(2002); assertTrue(data(get(API + "/101"), null, 200).path("watcherNames").isEmpty());
    }

    @Test
    void deletingParentRemovesCollaborationRecordsInSameTransaction() throws Exception {
        data(post(API + "/101/comments"), "{\"content\":\"讨论\"}", 200);
        data(post(API + "/101/subtasks"), "{\"title\":\"拆分工作\"}", 200);
        data(put(API + "/101/watch?watching=true"), null, 200);
        data(delete(API + "/101"), null, 200);
        assertEquals(0, count("team_task_activity")); assertEquals(0, count("team_task_subtask")); assertEquals(0, count("team_task_watch"));
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    private void employee(long id) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new EmployeeUserDetails(id, id * 10, "fixture", "", "用户", "ACTIVE"), null, List.of()));
    }
    private JsonNode data(MockHttpServletRequestBuilder request, String body, int status) throws Exception {
        if (body != null) request.contentType("application/json").content(body);
        var response = mvc.perform(request).andReturn().getResponse();
        assertEquals(status, response.getStatus(), response.getContentAsString());
        return json.readTree(response.getContentAsByteArray()).path("data");
    }
}
