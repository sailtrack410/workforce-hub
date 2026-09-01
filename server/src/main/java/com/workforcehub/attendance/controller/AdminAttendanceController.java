package com.workforcehub.attendance.controller;

import com.workforcehub.attendance.dto.AddAttendanceGroupMemberRequest;
import com.workforcehub.attendance.dto.AttendanceCalendarResponse;
import com.workforcehub.attendance.dto.AttendanceDailyResultResponse;
import com.workforcehub.attendance.dto.AttendanceGroupMemberResponse;
import com.workforcehub.attendance.dto.AttendanceGroupResponse;
import com.workforcehub.attendance.dto.AttendanceScheduleResponse;
import com.workforcehub.attendance.dto.AttendanceShiftResponse;
import com.workforcehub.attendance.dto.CreateAttendanceCalendarRequest;
import com.workforcehub.attendance.dto.CreateAttendanceGroupRequest;
import com.workforcehub.attendance.dto.CreateAttendanceScheduleRequest;
import com.workforcehub.attendance.dto.CreateAttendanceShiftRequest;
import com.workforcehub.attendance.dto.UpdateAttendanceScheduleRequest;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 后台考勤治理接口。
 */
// 考勤后台接口：考勤组/班次/日历/排班 CRUD，假期类型管理
@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    public AdminAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "创建考勤日历", operationId = "createAttendanceCalendar")
    @PostMapping("/calendars")
    public R<AttendanceCalendarResponse> createCalendar(
            @Valid @RequestBody CreateAttendanceCalendarRequest request) {
        return R.ok(attendanceService.createCalendar(request));
    }

    @Operation(summary = "查询考勤日历列表", operationId = "listAttendanceCalendars")
    @GetMapping("/calendars")
    public R<List<AttendanceCalendarResponse>> calendars() {
        return R.ok(attendanceService.listCalendars());
    }

    @Operation(summary = "创建考勤班次", operationId = "createAttendanceShift")
    @PostMapping("/shifts")
    public R<AttendanceShiftResponse> createShift(
            @Valid @RequestBody CreateAttendanceShiftRequest request) {
        return R.ok(attendanceService.createShift(request));
    }

    @Operation(summary = "查询考勤班次列表", operationId = "listAttendanceShifts")
    @GetMapping("/shifts")
    public R<List<AttendanceShiftResponse>> shifts() {
        return R.ok(attendanceService.listShifts());
    }

    @Operation(summary = "创建考勤组", operationId = "createAttendanceGroup")
    @PostMapping("/groups")
    public R<AttendanceGroupResponse> createGroup(
            @Valid @RequestBody CreateAttendanceGroupRequest request) {
        return R.ok(attendanceService.createGroup(request));
    }

    @Operation(summary = "查询考勤组列表", operationId = "listAttendanceGroups")
    @GetMapping("/groups")
    public R<List<AttendanceGroupResponse>> groups() {
        return R.ok(attendanceService.listGroups());
    }

    @Operation(summary = "添加考勤组成员", operationId = "addAttendanceGroupMember")
    @PostMapping("/groups/{groupId}/members")
    public R<AttendanceGroupMemberResponse> addGroupMember(@PathVariable Long groupId,
                                                           @Valid @RequestBody AddAttendanceGroupMemberRequest request) {
        return R.ok(attendanceService.addGroupMember(groupId, request));
    }

    @Operation(summary = "查询考勤组成员列表", operationId = "listAttendanceGroupMembers")
    @GetMapping("/groups/{groupId}/members")
    public R<List<AttendanceGroupMemberResponse>> groupMembers(@PathVariable Long groupId) {
        return R.ok(attendanceService.listGroupMembers(groupId));
    }

    @Operation(summary = "创建考勤排班", operationId = "createAttendanceSchedule")
    @PostMapping("/schedules")
    public R<AttendanceScheduleResponse> createSchedule(
            @Valid @RequestBody CreateAttendanceScheduleRequest request) {
        return R.ok(attendanceService.createSchedule(request));
    }

    @Operation(summary = "查询考勤排班列表", operationId = "listAttendanceSchedules")
    @GetMapping("/schedules")
    public R<List<AttendanceScheduleResponse>> schedules(@RequestParam(required = false) Long userId,
                                                         @RequestParam LocalDate startDate,
                                                         @RequestParam LocalDate endDate) {
        return R.ok(attendanceService.listSchedules(userId, startDate, endDate));
    }

    @Operation(summary = "更新考勤排班", operationId = "updateAttendanceSchedule")
    @PutMapping("/schedules/{scheduleId}")
    public R<AttendanceScheduleResponse> updateSchedule(@PathVariable Long scheduleId,
                                                        @Valid @RequestBody UpdateAttendanceScheduleRequest request) {
        return R.ok(attendanceService.updateSchedule(scheduleId, request));
    }

    @Operation(summary = "查询每日考勤结果", operationId = "listAdminAttendanceDailyResults")
    @GetMapping("/daily-results")
    public R<List<AttendanceDailyResultResponse>> dailyResults(@RequestParam(required = false) Long userId,
                                                               @RequestParam LocalDate startDate,
                                                               @RequestParam LocalDate endDate) {
        return R.ok(attendanceService.listDailyResultsForAdmin(userId, startDate, endDate));
    }
}
