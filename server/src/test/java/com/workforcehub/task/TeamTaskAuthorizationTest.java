package com.workforcehub.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.workforcehub.auth.security.AdminUserDetails;
import com.workforcehub.auth.security.EmployeeUserDetails;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.common.util.DatabaseIdGenerator;
import com.workforcehub.task.controller.EmployeeTeamTaskController;
import com.workforcehub.task.dto.TeamTaskResponse;
import com.workforcehub.task.repository.TeamTaskRepository;
import com.workforcehub.task.service.TeamTaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 真实控制器、服务与仓储 SQL；H2 独立数据库，不连接运行中的业务库。 */
class TeamTaskAuthorizationTest {
    private final ObjectMapper json = Jackson2ObjectMapperBuilder.json().modules(new JavaTimeModule()).build();
    private JdbcTemplate jdbc;
    private DatabaseIdGenerator ids;
    private TeamTaskService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:task_acl_" + UUID.randomUUID()
            + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        new ResourceDatabasePopulator(new ClassPathResource(
            "db/migration/V71__add_team_task_and_personal_todo.sql"), new ClassPathResource(
            "db/migration/V73__add_team_task_collaboration.sql")).execute(source);
        jdbc.execute("""
            CREATE TABLE org_user (id BIGINT PRIMARY KEY, real_name VARCHAR(128), primary_dept_id BIGINT,
                manager_user_id BIGINT, status VARCHAR(32) DEFAULT 'ACTIVE',
                employment_status VARCHAR(32) DEFAULT 'ACTIVE', is_deleted INT DEFAULT 0)
            """);
        jdbc.update("INSERT INTO org_user (id,primary_dept_id) VALUES (2002,10),(2003,10),(2004,10),(2005,10),(9999,20)");
        jdbc.execute("CREATE TABLE sys_id_sequence (sequence_name VARCHAR(64) PRIMARY KEY, high_watermark BIGINT)");
        jdbc.update("INSERT INTO sys_id_sequence VALUES ('global', 10000)");
        ids = new DatabaseIdGenerator(source);
        service = new TeamTaskService(new TeamTaskRepository(jdbc));
        mvc = MockMvcBuilders.standaloneSetup(new EmployeeTeamTaskController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json)).build();
        insertTask(101L, 2002L, 2003L, "TODO");
        insertTask(102L, 2004L, 2002L, "DONE");
        insertTask(103L, 2004L, 2005L, "DONE");
        employee(2002L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (ids != null) ids.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @ParameterizedTest
    @CsvSource({
        "creator,detail,200", "creator,update,200", "creator,status,403", "creator,delete,200",
        "assignee,detail,200", "assignee,update,403", "assignee,status,200", "assignee,delete,403",
        "other,detail,404", "other,update,404", "other,status,404", "other,delete,404",
        "admin,detail,403", "admin,update,403", "admin,status,403", "admin,delete,403",
        "anonymous,detail,401", "anonymous,update,401", "anonymous,status,401", "anonymous,delete,401"
    })
    void objectAccessMatrix(String actor, String action, int expected) throws Exception {
        actor(actor);
        mvc.perform(operation(action, 101L)).andExpect(status().is(expected));
        if (expected != 200) {
            assertEquals("权限回归任务", jdbc.queryForObject("SELECT title FROM team_task WHERE task_id=101", String.class));
            assertEquals("TODO", jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
            assertEquals(3, countTasks());
        }
    }

    @ParameterizedTest
    @CsvSource({"creator,2", "assignee,1", "other,0"})
    void listsOnlyTasksRelatedToCurrentEmployee(String actor, int expected) throws Exception {
        actor(actor);
        var response = mvc.perform(get("/api/employee/team-tasks")).andExpect(status().isOk()).andReturn();
        var rows = json.readTree(response.getResponse().getContentAsString()).get("data");
        assertEquals(expected, rows.size());
        for (var row : rows) assertNotEquals(103L, row.get("taskId").asLong());
    }

    @Test
    void filtersCannotExpandTheCurrentEmployeesScope() throws Exception {
        assertTaskIds("?assigneeId=2003", List.of(101L));
        assertTaskIds("?assigneeId=2005", List.of());
        assertTaskIds("?status=DONE", List.of(102L));
        assertTaskIds("?assigneeId=2005&status=DONE", List.of());
        assertTaskIds("?start=2026-11-18&end=2026-11-20", List.of(101L, 102L));
        assertTaskIds("?assigneeId=2005&start=2026-11-18&end=2026-11-20", List.of());
    }

    @ParameterizedTest
    @CsvSource({"admin,403", "anonymous,401"})
    void nonEmployeesCannotListOrCreate(String actor, int expected) throws Exception {
        actor(actor);
        mvc.perform(get("/api/employee/team-tasks")).andExpect(status().is(expected));
        mvc.perform(post("/api/employee/team-tasks").contentType(MediaType.APPLICATION_JSON).content(payload(2003L)))
            .andExpect(status().is(expected));
        assertEquals(3, countTasks());
    }

    @Test
    void creatorCanCreateEditAndDeleteAndCreatorIdCannotBeSpoofed() throws Exception {
        var created = mvc.perform(post("/api/employee/team-tasks").contentType(MediaType.APPLICATION_JSON)
            .content(payload(2003L).replace("{", "{\"creatorId\":9999,")))
            .andExpect(status().isOk()).andReturn();
        var task = json.readTree(created.getResponse().getContentAsString()).get("data");
        assertEquals(2002L, task.get("creatorId").asLong());
        long id = task.get("taskId").asLong();
        mvc.perform(operation("update", id)).andExpect(status().isOk());
        employee(2003L);
        mvc.perform(operation("status", id)).andExpect(status().isOk());
        employee(2002L);
        assertEquals("DONE", service.detail(id).getStatus());
        mvc.perform(operation("delete", id)).andExpect(status().isOk());
        mvc.perform(operation("detail", id)).andExpect(status().isNotFound());
    }

    @Test
    void reassignmentRevokesOldAssigneeAndGrantsNewAssignee() throws Exception {
        mvc.perform(put("/api/employee/team-tasks/101").contentType(MediaType.APPLICATION_JSON).content(payload(2005L)))
            .andExpect(status().isOk());
        employee(2003L);
        assertTaskIds("", List.of());
        mvc.perform(operation("detail", 101L)).andExpect(status().isNotFound());
        mvc.perform(operation("status", 101L)).andExpect(status().isNotFound());
        employee(2005L);
        mvc.perform(operation("detail", 101L)).andExpect(status().isOk());
        mvc.perform(operation("status", 101L)).andExpect(status().isOk());
        employee(2002L);
        mvc.perform(operation("delete", 101L)).andExpect(status().isOk());
    }

    @Test
    void statusWriteRechecksAuthorizationAfterReassignment() {
        var repository = new TeamTaskRepository(jdbc) {
            private boolean reassign = true;
            @Override
            public Optional<TeamTaskResponse> findById(Long taskId) {
                var result = super.findById(taskId);
                if (reassign) {
                    reassign = false;
                    jdbc.update("UPDATE team_task SET assignee_id=2005 WHERE task_id=?", taskId);
                }
                return result;
            }
        };
        employee(2003L);
        var failure = assertThrows(BizException.class,
            () -> new TeamTaskService(repository).updateStatus(101L, "DONE"));
        assertEquals(404, failure.getHttpStatus().value());
        assertEquals("TODO", jdbc.queryForObject("SELECT status FROM team_task WHERE task_id=101", String.class));
    }

    @Test
    void groupLabelDoesNotGrantUnrelatedEmployeesAccess() throws Exception {
        jdbc.update("UPDATE team_task SET group_id='same-department' WHERE task_id IN (101,103)");
        employee(2005L);
        mvc.perform(operation("detail", 101L)).andExpect(status().isNotFound());
        mvc.perform(operation("status", 101L)).andExpect(status().isNotFound());
        assertTaskIds("", List.of(103L));
    }

    @Test
    void missingTaskAndUnrelatedTaskHaveTheSameResponse() throws Exception {
        employee(9999L);
        for (String action : List.of("detail", "update", "status", "delete")) {
            var hidden = mvc.perform(operation(action, 101L)).andExpect(status().isNotFound()).andReturn();
            var missing = mvc.perform(operation(action, 999L)).andExpect(status().isNotFound()).andReturn();
            assertEquals(hidden.getResponse().getContentAsString(), missing.getResponse().getContentAsString());
        }
    }

    @Test
    void invalidStatusStillReturnsBadRequest() throws Exception {
        mvc.perform(put("/api/employee/team-tasks/101/status").contentType(MediaType.APPLICATION_JSON)
            .content("{\"status\":\"INVALID\"}")).andExpect(status().isBadRequest());
        assertEquals("TODO", service.detail(101L).getStatus());
    }

    private void insertTask(long id, long creator, long assignee, String state) {
        jdbc.update("""
            INSERT INTO team_task (task_id,title,creator_id,assignee_id,status,start_date,end_date)
            VALUES (?,'权限回归任务',?,?,?,DATE '2026-11-18',DATE '2026-11-20')
            """, id, creator, assignee, state);
    }

    private int countTasks() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM team_task", Integer.class);
    }

    private String payload(long assignee) {
        return "{\"title\":\"更新任务\",\"assigneeId\":" + assignee
            + ",\"startDate\":\"2026-11-18\",\"endDate\":\"2026-11-20\"}";
    }

    private MockHttpServletRequestBuilder operation(String action, long id) {
        String path = "/api/employee/team-tasks/" + id;
        return switch (action) {
            case "detail" -> get(path);
            case "update" -> put(path).contentType(MediaType.APPLICATION_JSON).content(payload(2003L));
            case "status" -> put(path + "/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}");
            case "delete" -> delete(path);
            default -> throw new IllegalArgumentException(action);
        };
    }

    private void assertTaskIds(String query, List<Long> expected) throws Exception {
        var result = mvc.perform(get("/api/employee/team-tasks" + query)).andExpect(status().isOk()).andReturn();
        var rows = json.readTree(result.getResponse().getContentAsString()).get("data");
        var ids = new java.util.ArrayList<Long>();
        rows.forEach(row -> ids.add(row.get("taskId").asLong()));
        assertEquals(expected.stream().sorted().toList(), ids.stream().sorted().toList());
    }

    private void actor(String actor) {
        switch (actor) {
            case "creator" -> employee(2002L);
            case "assignee" -> employee(2003L);
            case "other" -> employee(9999L);
            case "admin" -> {
                var principal = new AdminUserDetails(2002L, "fixture", "", "fixture", "ACTIVE", List.of(), List.of());
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            }
            case "anonymous" -> SecurityContextHolder.clearContext();
            default -> throw new IllegalArgumentException(actor);
        }
    }

    private void employee(long id) {
        var principal = new EmployeeUserDetails(id, id * 10, "fixture", "", "fixture", "ACTIVE");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
