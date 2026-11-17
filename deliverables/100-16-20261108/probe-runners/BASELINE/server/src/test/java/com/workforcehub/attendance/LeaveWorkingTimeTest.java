package com.workforcehub.attendance;

import com.workforcehub.attendance.repository.*;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.repository.ApprovedLeavePeriod;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.workflow.controller.EmployeeWorkflowController;
import com.workforcehub.workflow.dto.LeaveRequestCreateRequest;
import com.workforcehub.workflow.repository.WorkflowRepository;
import com.workforcehub.workflow.service.WorkflowService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.InvocationTargetException;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class LeaveWorkingTimeTest {
    JdbcTemplate jdbc;
    AttendanceRepository repository;
    AttendanceService attendance;
    LeaveService leave;
    WorkflowService workflow;

    @BeforeEach
    void setup() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:working_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE org_user(id BIGINT, employee_no VARCHAR, real_name VARCHAR)");
        jdbc.execute("CREATE TABLE att_work_calendar(id BIGINT, calendar_code VARCHAR, calendar_name VARCHAR, calendar_type VARCHAR, status VARCHAR, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE att_work_calendar_day(id BIGINT, calendar_id BIGINT, work_date DATE, is_workday INT)");
        jdbc.execute("CREATE TABLE att_shift(id BIGINT, shift_code VARCHAR, shift_name VARCHAR, status VARCHAR, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE att_shift_segment(id BIGINT, shift_id BIGINT, segment_no INT, start_time TIME, end_time TIME, on_duty_punch_start TIME, on_duty_punch_end TIME, off_duty_punch_start TIME, off_duty_punch_end TIME)");
        jdbc.execute("CREATE TABLE att_group(id BIGINT, group_code VARCHAR, group_name VARCHAR, calendar_id BIGINT, default_shift_id BIGINT, late_grace_minutes INT DEFAULT 0, early_leave_grace_minutes INT DEFAULT 0, status VARCHAR, is_deleted INT DEFAULT 0)");
        jdbc.execute("CREATE TABLE att_group_member(id BIGINT, group_id BIGINT, user_id BIGINT, effective_from DATE, effective_to DATE, status VARCHAR)");
        jdbc.execute("CREATE TABLE att_schedule(id BIGINT, user_id BIGINT, work_date DATE, shift_id BIGINT, calendar_id BIGINT, schedule_source VARCHAR, status VARCHAR)");
        jdbc.execute("CREATE TABLE att_punch_record(user_id BIGINT, punch_at TIMESTAMP, punch_type VARCHAR, status VARCHAR)");
        jdbc.update("INSERT INTO att_work_calendar VALUES(2,'C','C','STANDARD','ACTIVE',0)");
        jdbc.update("INSERT INTO att_shift VALUES(3,'S','S','ACTIVE',0)");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(1,3,1,'09:00:00','12:00:00'),(2,3,2,'13:00:00','18:00:00')");
        jdbc.update("INSERT INTO att_group(id,group_code,group_name,calendar_id,default_shift_id,status) VALUES(4,'G','G',2,3,'ACTIVE')");
        jdbc.update("INSERT INTO att_group_member VALUES(5,4,1,'2026-01-01',NULL,'ACTIVE')");
        repository = spy(new AttendanceRepository(jdbc));
        leave = mock(LeaveService.class);
        ObjectProvider<Clock> clocks = mock(ObjectProvider.class);
        when(clocks.getIfAvailable(any())).thenReturn(Clock.systemUTC());
        attendance = new AttendanceService(repository, leave, clocks, null, null);
        WorkflowRepository workflows = mock(WorkflowRepository.class);
        when(workflows.existsActiveLeaveType(2L)).thenReturn(true);
        workflow = new WorkflowService(workflows,null,null,null,null,null,leave,attendance,null,null,null);
        WfhUserDetails principal = mock(WfhUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,List.of()));
    }

    @AfterEach
    void close() { SecurityContextHolder.clearContext(); jdbc.execute("SHUTDOWN"); }

    int minutes(String start, String end) { return attendance.calculateLeaveDurationMinutes(1L, LocalDateTime.parse(start), LocalDateTime.parse(end)); }

    @Test
    void actualMinutesExcludeLunchAndMeasureUnequalHalfDays() {
        assertEquals(480,minutes("2026-05-07T09:00", "2026-05-07T18:00"));
        assertEquals(180,minutes("2026-05-07T09:00", "2026-05-07T12:00"));
        assertEquals(300,minutes("2026-05-07T13:00", "2026-05-07T18:00"));
        assertEquals(0,minutes("2026-05-07T12:00", "2026-05-07T13:00"));
        assertEquals(120,minutes("2026-05-07T17:00", "2026-05-08T10:00"));
    }

    @Test
    void calendarHolidaysAndMakeupDaysOverrideStandardWeek() {
        assertEquals(0,minutes("2026-05-10T09:00", "2026-05-10T18:00"));
        jdbc.update("INSERT INTO att_work_calendar_day VALUES(1,2,'2026-05-07',0),(2,2,'2026-05-10',1)");
        assertEquals(0,minutes("2026-05-07T09:00", "2026-05-07T18:00"));
        assertEquals(480,minutes("2026-05-10T09:00", "2026-05-10T18:00"));
    }

    @Test
    void explicitScheduleUsesItsOwnShiftAndCalendar() {
        jdbc.update("INSERT INTO att_shift VALUES(6,'ALT','ALT','ACTIVE',0)");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(3,6,1,'10:00','14:00'),(4,6,2,'15:00','19:00')");
        jdbc.update("INSERT INTO att_work_calendar VALUES(7,'ALT','ALT','STANDARD','ACTIVE',0)");
        jdbc.update("INSERT INTO att_schedule VALUES(8,1,'2026-05-10',6,7,'MANUAL','ACTIVE')");
        assertEquals(420,minutes("2026-05-10T10:00", "2026-05-10T18:00"));
        jdbc.update("INSERT INTO att_work_calendar_day VALUES(9,7,'2026-05-10',0)");
        assertEquals(0,minutes("2026-05-10T10:00", "2026-05-10T19:00"));
    }

    @Test
    void overnightShiftIncludesPreviousWorkDateAndMidnightBreak() {
        jdbc.update("DELETE FROM att_shift_segment");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(1,3,1,'22:00','02:00'),(2,3,2,'03:00','06:00')");
        assertEquals(420,minutes("2026-05-08T22:00", "2026-05-09T06:00"));
        assertEquals(240,minutes("2026-05-09T01:00", "2026-05-09T06:00"));
        assertEquals(0,minutes("2026-05-09T02:00", "2026-05-09T03:00"));
    }

    @Test
    void midnightEndDoesNotRequireNextDaysConfiguration() {
        jdbc.update("INSERT INTO att_schedule VALUES(8,1,'2026-05-08',999,2,'MANUAL','ACTIVE')");
        assertEquals(60,minutes("2026-05-07T17:00", "2026-05-08T00:00"));
    }

    @Test
    void overlappingAdjacentWorkDatesAreRejectedBeforeChargingLeave() {
        jdbc.update("DELETE FROM att_shift_segment");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(1,3,1,'22:00','06:00')");
        jdbc.update("INSERT INTO att_shift VALUES(6,'ALT','ALT','ACTIVE',0)");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(2,6,1,'05:00','13:00')");
        jdbc.update("INSERT INTO att_schedule VALUES(8,1,'2026-05-09',6,2,'MANUAL','ACTIVE')");
        BizException error = assertThrows(BizException.class,()->minutes("2026-05-08T22:00", "2026-05-09T07:00"));
        assertEquals("员工相邻排班工作时段重叠，请调整排班",error.getMessage());
    }

    @Test
    void invalidOrUnconfiguredPeriodsFailExplicitly() {
        assertThrows(BizException.class,()->minutes("2026-05-07T10:00", "2026-05-07T09:00"));
        assertThrows(BizException.class,()->minutes("2026-05-07T10:00:01", "2026-05-07T11:00"));
        assertThrows(BizException.class,()->minutes("2026-05-07T10:00", "2027-05-09T10:00"));
        jdbc.update("DELETE FROM att_group_member");
        assertThrows(BizException.class,()->minutes("2026-05-07T10:00", "2026-05-07T11:00"));
    }

    @Test
    void previewIsAuthenticatedAndSubmissionRecalculatesRatherThanTrustingClient() throws Exception {
        MockMvcBuilders.standaloneSetup(new EmployeeWorkflowController(workflow,null)).build()
            .perform(get("/api/employee/workflow/leave-duration").param("startAt","2026-05-07T13:00:00").param("endAt","2026-05-07T18:00:00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.durationMinutes").value(300));
        var validate = WorkflowService.class.getDeclaredMethod("validateLeaveRequest", LeaveRequestCreateRequest.class);
        validate.setAccessible(true);
        LeaveRequestCreateRequest request = new LeaveRequestCreateRequest();
        request.setStartAt(LocalDateTime.parse("2026-05-07T13:00")); request.setEndAt(LocalDateTime.parse("2026-05-07T18:00"));
        request.setLeaveTypeId(2L); request.setDurationMinutes(300);
        validate.invoke(workflow,request);
        verify(leave).ensureLeaveRequestBalance(1L,2L,300);
        request.setDurationMinutes(480);
        assertInstanceOf(BizException.class, assertThrows(InvocationTargetException.class,()->validate.invoke(workflow,request)).getCause());
        request.setStartAt(LocalDateTime.parse("2026-05-10T09:00")); request.setEndAt(LocalDateTime.parse("2026-05-10T18:00")); request.setDurationMinutes(0);
        assertInstanceOf(BizException.class, assertThrows(InvocationTargetException.class,()->validate.invoke(workflow,request)).getCause());
        SecurityContextHolder.clearContext();
        assertThrows(BizException.class,()->workflow.previewLeaveDuration(request.getStartAt(),request.getEndAt()));
    }

    @Test
    void dailyAttendanceUsesSamePeriodsForWorkAbsenceAndApprovedLeave() throws Exception {
        doReturn(null).when(repository).getDailyResult(anyLong(),any());
        doReturn(null).when(repository).getDailyResultForUpdate(anyLong(),any());
        doReturn(List.of()).when(repository).listExceptionsByDailyResultId(any());
        doNothing().when(repository).upsertDailyResult(anyLong(),anyLong(),any(),any(),any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean(),any());
        var row = mock(AttendanceDailyResultRow.class);
        when(row.dailyResultId()).thenReturn(100L);
        doReturn(row).when(repository).getDailyResultForUpdate(anyLong(),any());
        when(leave.listApprovedLeavePeriodsOnDate(eq(1L),any())).thenReturn(List.of(new ApprovedLeavePeriod(LocalDateTime.parse("2026-05-07T09:00"),LocalDateTime.parse("2026-05-07T10:00"))));
        var recalc = AttendanceService.class.getDeclaredMethod("recalculateDailyResult",Long.class,LocalDate.class); recalc.setAccessible(true);
        // Avoid publishing exceptions; verify the real computed upsert before rebuilding exceptions.
        doThrow(new IllegalStateException("observed")).when(repository).upsertDailyResult(anyLong(),anyLong(),any(),any(),any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean(),any());
        assertThrows(InvocationTargetException.class,()->recalc.invoke(attendance,1L,LocalDate.parse("2026-05-07")));
        verify(repository).upsertDailyResult(anyLong(),eq(1L),any(),isNull(),eq(4L),eq(3L),eq("ABNORMAL"),eq(0),eq(0),eq(420),eq(0),eq(true),any());
        jdbc.update("INSERT INTO att_punch_record VALUES(1,'2026-05-07 06:00:00','ON_DUTY','NORMAL'),(1,'2026-05-07 21:00:00','OFF_DUTY','NORMAL')");
        assertThrows(InvocationTargetException.class,()->recalc.invoke(attendance,1L,LocalDate.parse("2026-05-07")));
        verify(repository).upsertDailyResult(anyLong(),eq(1L),any(),isNull(),eq(4L),eq(3L),eq("NORMAL"),eq(0),eq(0),eq(0),eq(480),eq(false),any());
        assertThrows(InvocationTargetException.class,()->recalc.invoke(attendance,1L,LocalDate.parse("2026-05-10")));
        verify(repository).upsertDailyResult(anyLong(),eq(1L),any(),isNull(),eq(4L),eq(3L),eq("REST"),eq(0),eq(0),eq(0),eq(0),eq(false),any());
    }

    @Test
    void overnightDailyAttendanceFindsNextDayPunchAndExcludesBreak() throws Exception {
        jdbc.update("DELETE FROM att_shift_segment");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(1,3,1,'22:00','02:00'),(2,3,2,'03:00','06:00')");
        jdbc.update("INSERT INTO att_punch_record VALUES(1,'2026-05-08 21:30:00','ON_DUTY','NORMAL'),(1,'2026-05-09 06:30:00','OFF_DUTY','NORMAL')");
        doThrow(new IllegalStateException("observed")).when(repository).upsertDailyResult(anyLong(),anyLong(),any(),any(),any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean(),any());
        var recalc = AttendanceService.class.getDeclaredMethod("recalculateDailyResult",Long.class,LocalDate.class); recalc.setAccessible(true);
        assertThrows(InvocationTargetException.class,()->recalc.invoke(attendance,1L,LocalDate.parse("2026-05-08")));
        verify(repository).upsertDailyResult(anyLong(),eq(1L),eq(LocalDate.parse("2026-05-08")),isNull(),eq(4L),eq(3L),eq("NORMAL"),eq(0),eq(0),eq(0),eq(420),eq(false),any());
    }

    @Test
    void afterMidnightApprovalRecalculatesPreviousWorkDate() {
        jdbc.update("DELETE FROM att_shift_segment");
        jdbc.update("INSERT INTO att_shift_segment(id,shift_id,segment_no,start_time,end_time) VALUES(1,3,1,'22:00','02:00'),(2,3,2,'03:00','06:00')");
        var start = LocalDateTime.parse("2026-05-09T01:00");
        var end = LocalDateTime.parse("2026-05-09T06:00");
        when(leave.listApprovedLeavePeriodsOnDate(eq(1L),any())).thenReturn(List.of(new ApprovedLeavePeriod(start,end)));
        doThrow(new IllegalStateException("observed")).when(repository).upsertDailyResult(anyLong(),anyLong(),any(),any(),any(),any(),anyString(),anyInt(),anyInt(),anyInt(),anyInt(),anyBoolean(),any());
        assertThrows(IllegalStateException.class,()->attendance.recalculateDailyResultsForApprovedLeave(1L,start,end));
        verify(repository).upsertDailyResult(anyLong(),eq(1L),eq(LocalDate.parse("2026-05-08")),isNull(),eq(4L),eq(3L),eq("ABNORMAL"),eq(0),eq(0),eq(180),eq(0),eq(true),any());
    }
}
