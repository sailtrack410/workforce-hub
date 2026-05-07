package com.workforcehub.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-attendance 第一版考勤接口自动化冒烟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleAttendanceApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("后台配置考勤基础数据后，员工应能打卡并生成正常日结果，直属上级应能查询团队结果")
    void employeePunchShouldCreateNormalDailyResultAndManagerShouldQueryTeamResult() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        Cookie managerSession = loginEmployee("lisi.manager");
        String suffix = uniqueSuffix();

        long calendarId = createCalendar(adminSession, "CAL_" + suffix, "标准日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_" + suffix, "标准班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_" + suffix, "研发考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.groupId").value(groupId));

        punch(employeeSession, "2026-05-07T09:00:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data.punchType").value("ON_DUTY"));
        punch(employeeSession, "2026-05-07T18:00:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(false))
            .andExpect(jsonPath("$.data.dailyResult.workMinutes").value(540));

        mockMvc.perform(get("/api/employee/attendance/daily-results")
                .cookie(employeeSession)
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].userId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].workDate").value("2026-05-07"))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"))
            .andExpect(jsonPath("$.data[0].exceptionFlag").value(false));

        mockMvc.perform(get("/api/employee/attendance/team/daily-results")
                .cookie(managerSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID))
                .param("startDate", "2026-05-07")
                .param("endDate", "2026-05-07"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].userId").value(EMPLOYEE_USER_ID))
            .andExpect(jsonPath("$.data[0].resultStatus").value("NORMAL"));

        assertEquals(2L, punchCount(EMPLOYEE_USER_ID, "2026-05-07"));
        assertEquals(1L, dailyResultCount(EMPLOYEE_USER_ID, "2026-05-07"));
    }

    @Test
    @DisplayName("同一员工考勤组生效区间重叠时应拒绝写入")
    void overlappingGroupMembershipShouldBeRejected() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_OV_" + suffix, "重叠日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_OV_" + suffix, "重叠班次-" + suffix);
        long firstGroupId = createGroup(adminSession, "GROUP_A_" + suffix, "考勤组A-" + suffix, calendarId, shiftId);
        long secondGroupId = createGroup(adminSession, "GROUP_B_" + suffix, "考勤组B-" + suffix, calendarId, shiftId);

        addGroupMember(adminSession, firstGroupId, EMPLOYEE_USER_ID, "2026-05-01", "2026-05-31")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        addGroupMember(adminSession, secondGroupId, EMPLOYEE_USER_ID, "2026-05-15", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409))
            .andExpect(jsonPath("$.message").value("员工考勤组生效区间重叠"));

        assertEquals(1L, activeGroupMemberCount(EMPLOYEE_USER_ID));
    }

    @Test
    @DisplayName("并发添加同一员工重叠考勤组成员时也只能成功一条")
    void concurrentOverlappingGroupMembershipShouldAllowOnlyOneActiveRecord() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_CON_" + suffix, "并发日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_CON_" + suffix, "并发班次-" + suffix);
        List<Long> groupIds = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            groupIds.add(createGroup(
                adminSession,
                "GROUP_CON_" + i + "_" + suffix,
                "并发考勤组" + i + "-" + suffix,
                calendarId,
                shiftId
            ));
        }

        CountDownLatch ready = new CountDownLatch(groupIds.size());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(groupIds.size());
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (Long groupId : groupIds) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS), "并发测试启动超时");
                    MvcResult result = addGroupMember(
                        adminSession,
                        groupId,
                        EMPLOYEE_USER_ID,
                        "2026-06-01",
                        null
                    )
                        .andExpect(status().isOk())
                        .andReturn();
                    return objectMapper.readTree(result.getResponse().getContentAsString()).get("code").asInt();
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "并发测试准备超时");
            start.countDown();

            long successCount = 0L;
            for (Future<Integer> future : futures) {
                if (future.get(10, TimeUnit.SECONDS) == 0) {
                    successCount++;
                }
            }

            assertEquals(1L, successCount);
            assertEquals(1L, activeGroupMemberCount(EMPLOYEE_USER_ID));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("迟到和早退打卡应生成异常日结果与待处理异常")
    void lateAndEarlyPunchShouldCreateAbnormalDailyResultAndExceptions() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_EX_" + suffix, "异常日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_EX_" + suffix, "异常班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_EX_" + suffix, "异常考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        punch(employeeSession, "2026-05-08T09:20:00", "ON_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        punch(employeeSession, "2026-05-08T17:40:00", "OFF_DUTY")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dailyResult.resultStatus").value("ABNORMAL"))
            .andExpect(jsonPath("$.data.dailyResult.lateMinutes").value(20))
            .andExpect(jsonPath("$.data.dailyResult.earlyLeaveMinutes").value(20))
            .andExpect(jsonPath("$.data.dailyResult.exceptionFlag").value(true));

        mockMvc.perform(get("/api/employee/attendance/exceptions")
                .cookie(employeeSession)
                .param("startDate", "2026-05-08")
                .param("endDate", "2026-05-08"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[?(@.exceptionType=='LATE')]", hasSize(1)))
            .andExpect(jsonPath("$.data[?(@.exceptionType=='EARLY_LEAVE')]", hasSize(1)));

        assertEquals(2L, exceptionCount(EMPLOYEE_USER_ID, "2026-05-08"));
    }

    @Test
    @DisplayName("员工登录后被离职时，旧会话也不能继续打卡")
    void offboardedEmployeeShouldNotPunchWithExistingSession() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee");
        String suffix = uniqueSuffix();
        long calendarId = createCalendar(adminSession, "CAL_OFF_" + suffix, "离职日历-" + suffix);
        long shiftId = createShift(adminSession, "SHIFT_OFF_" + suffix, "离职班次-" + suffix);
        long groupId = createGroup(adminSession, "GROUP_OFF_" + suffix, "离职考勤组-" + suffix, calendarId, shiftId);
        addGroupMember(adminSession, groupId, EMPLOYEE_USER_ID, "2026-05-01", null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "2026-05-09T09:00:00",
                      "punchType": "ON_DUTY",
                      "sourceType": "PC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", EMPLOYEE_USER_ID)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "PENDING_OFFBOARD"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", EMPLOYEE_USER_ID)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "OFFBOARDED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "2026-05-09T18:00:00",
                      "punchType": "OFF_DUTY",
                      "sourceType": "PC"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message").value("员工当前状态不允许打卡"));

        assertEquals(1L, punchCount(EMPLOYEE_USER_ID, "2026-05-09"));
    }

    private long createCalendar(Cookie adminSession, String calendarCode, String calendarName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/calendars")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "calendarCode": "%s",
                      "calendarName": "%s",
                      "calendarType": "STANDARD",
                      "status": "ACTIVE"
                    }
                    """.formatted(calendarCode, calendarName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.calendarId").isNumber())
            .andReturn();
        return data(result).get("calendarId").asLong();
    }

    private long createShift(Cookie adminSession, String shiftCode, String shiftName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/shifts")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shiftCode": "%s",
                      "shiftName": "%s",
                      "startTime": "09:00:00",
                      "endTime": "18:00:00",
                      "onDutyPunchStart": "08:00:00",
                      "onDutyPunchEnd": "10:00:00",
                      "offDutyPunchStart": "17:00:00",
                      "offDutyPunchEnd": "19:00:00",
                      "status": "ACTIVE"
                    }
                    """.formatted(shiftCode, shiftName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.shiftId").isNumber())
            .andReturn();
        return data(result).get("shiftId").asLong();
    }

    private long createGroup(Cookie adminSession,
                             String groupCode,
                             String groupName,
                             long calendarId,
                             long shiftId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/attendance/groups")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "groupCode": "%s",
                      "groupName": "%s",
                      "calendarId": %d,
                      "defaultShiftId": %d,
                      "lateGraceMinutes": 0,
                      "earlyLeaveGraceMinutes": 0,
                      "status": "ACTIVE"
                    }
                    """.formatted(groupCode, groupName, calendarId, shiftId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.groupId").isNumber())
            .andReturn();
        return data(result).get("groupId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions addGroupMember(Cookie adminSession,
                                                                              long groupId,
                                                                              long userId,
                                                                              String effectiveFrom,
                                                                              String effectiveTo) throws Exception {
        String effectiveToField = effectiveTo == null ? "" : "\"effectiveTo\": \"%s\",".formatted(effectiveTo);
        return mockMvc.perform(post("/api/admin/attendance/groups/{groupId}/members", groupId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": %d,
                      "effectiveFrom": "%s",
                      %s
                      "sourceType": "MANUAL"
                    }
                    """.formatted(userId, effectiveFrom, effectiveToField)));
    }

    private org.springframework.test.web.servlet.ResultActions punch(Cookie employeeSession,
                                                                     String punchAt,
                                                                     String punchType) throws Exception {
        return mockMvc.perform(post("/api/employee/attendance/punch")
                .cookie(employeeSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "punchAt": "%s",
                      "punchType": "%s",
                      "sourceType": "PC",
                      "locationText": "杭州总部",
                      "ipAddress": "127.0.0.1",
                      "deviceId": "test-device"
                    }
                    """.formatted(punchAt, punchType)));
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
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "后台登录后应返回 SESSION");
        return session;
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
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "员工登录后应返回 SESSION");
        return session;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private String uniqueSuffix() {
        return Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    private long punchCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_punch_record
            WHERE user_id = ?
              AND DATE(punch_at) = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private long dailyResultCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_daily_result
            WHERE user_id = ?
              AND work_date = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }

    private long activeGroupMemberCount(long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_group_member
            WHERE user_id = ?
              AND status = 'ACTIVE'
            """, Long.class, userId);
        return count == null ? 0L : count;
    }

    private long exceptionCount(long userId, String workDate) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date = ?
            """, Long.class, userId, workDate);
        return count == null ? 0L : count;
    }
}
