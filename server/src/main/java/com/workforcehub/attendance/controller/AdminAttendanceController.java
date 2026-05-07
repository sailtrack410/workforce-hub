package com.workforcehub.attendance.controller;

import com.workforcehub.attendance.dto.AddAttendanceGroupMemberRequest;
import com.workforcehub.attendance.dto.AttendanceCalendarResponse;
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
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台考勤治理接口。
 */
@RestController
@RequestMapping("/api/admin/attendance")
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    public AdminAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/calendars")
    public R<AttendanceCalendarResponse> createCalendar(
            @Valid @RequestBody CreateAttendanceCalendarRequest request) {
        return R.ok(attendanceService.createCalendar(request));
    }

    @PostMapping("/shifts")
    public R<AttendanceShiftResponse> createShift(
            @Valid @RequestBody CreateAttendanceShiftRequest request) {
        return R.ok(attendanceService.createShift(request));
    }

    @PostMapping("/groups")
    public R<AttendanceGroupResponse> createGroup(
            @Valid @RequestBody CreateAttendanceGroupRequest request) {
        return R.ok(attendanceService.createGroup(request));
    }

    @PostMapping("/groups/{groupId}/members")
    public R<AttendanceGroupMemberResponse> addGroupMember(@PathVariable Long groupId,
                                                           @Valid @RequestBody AddAttendanceGroupMemberRequest request) {
        return R.ok(attendanceService.addGroupMember(groupId, request));
    }

    @PostMapping("/schedules")
    public R<AttendanceScheduleResponse> createSchedule(
            @Valid @RequestBody CreateAttendanceScheduleRequest request) {
        return R.ok(attendanceService.createSchedule(request));
    }

    @PutMapping("/schedules/{scheduleId}")
    public R<AttendanceScheduleResponse> updateSchedule(@PathVariable Long scheduleId,
                                                        @Valid @RequestBody UpdateAttendanceScheduleRequest request) {
        return R.ok(attendanceService.updateSchedule(scheduleId, request));
    }
}
