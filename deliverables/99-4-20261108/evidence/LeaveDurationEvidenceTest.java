package com.workforcehub.workflow;

import com.workforcehub.attendance.repository.*;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.auth.security.WfhUserDetails;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.leave.service.LeaveService;
import com.workforcehub.workflow.service.WorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

class LeaveDurationEvidenceTest {
    @Test
    void sameInputsAgainstProductionCalculator() throws Exception {
        AttendanceRepository repo = mock(AttendanceRepository.class);
        when(repo.getEffectiveGroupForUpdate(eq(1L), any())).thenReturn(new AttendanceGroupRow(1L, "G", "G", 2L, 3L, 0, 0, "ACTIVE"));
        when(repo.getShiftOrThrow(3L)).thenReturn(new AttendanceShiftRow(3L, "S", "S", java.time.LocalTime.of(9,0), java.time.LocalTime.of(18,0), null,null,null,null,"ACTIVE"));
        when(repo.getCalendarOrThrow(2L)).thenReturn(new AttendanceCalendarRow(2L,"C","C","STANDARD","ACTIVE"));
        // Stub new repository methods reflectively so this exact probe also runs against the baseline.
        try {
            Method calendar = AttendanceRepository.class.getMethod("findCalendarWorkday", Long.class, java.time.LocalDate.class);
            doAnswer(inv -> null).when(repo);
            calendar.invoke(repo, eq(2L), any(java.time.LocalDate.class));
            Method segments = AttendanceRepository.class.getMethod("listShiftWorkPeriods", Long.class, java.time.LocalDate.class);
            Class<?> period = Class.forName("com.workforcehub.attendance.repository.AttendanceWorkPeriod");
            var ctor = period.getConstructor(LocalDateTime.class, LocalDateTime.class);
            doAnswer(inv -> {
                java.time.LocalDate date = inv.getArgument(1);
                return java.util.List.of(ctor.newInstance(date.atTime(9,0), date.atTime(12,0)), ctor.newInstance(date.atTime(13,0), date.atTime(18,0)));
            }).when(repo);
            segments.invoke(repo, eq(3L), any(java.time.LocalDate.class));
        } catch (NoSuchMethodException ignored) { }
        AttendanceService attendance = new AttendanceService(repo, mock(LeaveService.class), mock(ObjectProvider.class), null, null);
        WorkflowService workflow = new WorkflowService(null,null,null,null,null,null,null,attendance,null,null,null);
        WfhUserDetails principal = mock(WfhUserDetails.class);
        when(principal.getUserId()).thenReturn(1L);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,java.util.List.of()));
        Method calculate = WorkflowService.class.getDeclaredMethod("calculateLeaveDurationMinutes", LocalDateTime.class, LocalDateTime.class);
        calculate.setAccessible(true);
        try {
            for (String[] input : new String[][]{{"SUNDAY", "2026-05-10T09:00:00", "2026-05-10T18:00:00"},{"AFTERNOON", "2026-05-07T13:00:00", "2026-05-07T18:00:00"},{"CROSS_DAY", "2026-05-07T17:00:00", "2026-05-08T10:00:00"}}) {
                long minutes = (long) calculate.invoke(workflow, LocalDateTime.parse(input[1]), LocalDateTime.parse(input[2]));
                try { Method round = WorkflowService.class.getDeclaredMethod("roundLeaveDurationMinutes", long.class); round.setAccessible(true); minutes = (long) round.invoke(workflow,minutes); } catch (NoSuchMethodException ignored) { }
                System.out.println(input[0]+" durationMinutes="+minutes);
            }
        } finally { SecurityContextHolder.clearContext(); }
    }
}
