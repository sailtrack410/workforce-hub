package com.workforcehub.attendance.controller;

import com.workforcehub.attendance.dto.AttendanceDailyResultResponse;
import com.workforcehub.attendance.dto.AttendanceDateRangeQuery;
import com.workforcehub.attendance.dto.AttendanceExceptionResponse;
import com.workforcehub.attendance.dto.CreatePunchRecordRequest;
import com.workforcehub.attendance.dto.PunchRecordResponse;
import com.workforcehub.attendance.dto.TeamAttendanceDateRangeQuery;
import com.workforcehub.attendance.service.AttendanceService;
import com.workforcehub.common.response.R;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工端考勤接口。
 */
// 员工考勤接口：打卡、日结果查询、补卡申请、考勤异常
@RestController
@RequestMapping("/api/employee/attendance")
public class EmployeeAttendanceController {

    private final AttendanceService attendanceService;

    public EmployeeAttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @Operation(summary = "员工打卡", operationId = "createAttendancePunch")
    @PostMapping("/punch")
    public R<PunchRecordResponse> punch(@Valid @RequestBody CreatePunchRecordRequest request) {
        return R.ok(attendanceService.punch(request));
    }

    @Operation(summary = "查询个人每日考勤结果", operationId = "listEmployeeAttendanceDailyResults")
    @GetMapping("/daily-results")
    public R<List<AttendanceDailyResultResponse>> dailyResults(@Valid AttendanceDateRangeQuery query) {
        return R.ok(attendanceService.listCurrentEmployeeDailyResults(query.getStartDate(), query.getEndDate()));
    }

    @Operation(summary = "查询个人考勤异常", operationId = "listEmployeeAttendanceExceptions")
    @GetMapping("/exceptions")
    public R<List<AttendanceExceptionResponse>> exceptions(@Valid AttendanceDateRangeQuery query) {
        return R.ok(attendanceService.listCurrentEmployeeExceptions(query.getStartDate(), query.getEndDate()));
    }

    @Operation(summary = "查询团队每日考勤结果", operationId = "listTeamAttendanceDailyResults")
    @GetMapping("/team/daily-results")
    public R<List<AttendanceDailyResultResponse>> teamDailyResults(@Valid TeamAttendanceDateRangeQuery query) {
        return R.ok(attendanceService.listTeamDailyResults(
            query.getUserId(),
            query.getStartDate(),
            query.getEndDate()
        ));
    }
}
