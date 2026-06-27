package com.workforcehub.attendance.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.workforcehub.attendance.dto.AddAttendanceGroupMemberRequest;
import com.workforcehub.attendance.dto.AttendanceCalendarResponse;
import com.workforcehub.attendance.dto.AttendanceDailyResultResponse;
import com.workforcehub.attendance.dto.AttendanceExceptionResponse;
import com.workforcehub.attendance.dto.AttendanceGroupMemberResponse;
import com.workforcehub.attendance.dto.AttendanceGroupResponse;
import com.workforcehub.attendance.dto.AttendanceScheduleResponse;
import com.workforcehub.attendance.dto.AttendanceShiftResponse;
import com.workforcehub.attendance.dto.CreateAttendanceCalendarRequest;
import com.workforcehub.attendance.dto.CreateAttendanceGroupRequest;
import com.workforcehub.attendance.dto.CreateAttendanceScheduleRequest;
import com.workforcehub.attendance.dto.CreateAttendanceShiftRequest;
import com.workforcehub.attendance.dto.CreatePunchRecordRequest;
import com.workforcehub.attendance.dto.PunchRecordResponse;
import com.workforcehub.attendance.dto.UpdateAttendanceScheduleRequest;
import com.workforcehub.attendance.repository.AttendanceCalendarRow;
import com.workforcehub.attendance.repository.AttendanceDailyResultRow;
import com.workforcehub.attendance.repository.AttendanceExceptionRow;
import com.workforcehub.attendance.repository.AttendanceGroupMemberRow;
import com.workforcehub.attendance.repository.AttendanceGroupRow;
import com.workforcehub.attendance.repository.AttendanceRepository;
import com.workforcehub.attendance.repository.AttendanceScheduleRow;
import com.workforcehub.attendance.repository.AttendanceShiftRow;
import com.workforcehub.attendance.repository.PunchRecordRow;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.util.CurrentUserHolder;
import com.workforcehub.spi.AttendanceClockCorrectionService;
import com.workforcehub.spi.AttendanceQueryService;
import com.workforcehub.spi.AttendanceRecalculationService;
import com.workforcehub.spi.LeaveQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * module-attendance 第一版考勤服务。
 */
@Service
public class AttendanceService implements AttendanceQueryService, AttendanceRecalculationService, AttendanceClockCorrectionService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";
    private static final String PUNCH_TYPE_ON_DUTY = "ON_DUTY";
    private static final String PUNCH_TYPE_OFF_DUTY = "OFF_DUTY";
    private static final String RESULT_NORMAL = "NORMAL";
    private static final String RESULT_ABNORMAL = "ABNORMAL";
    private static final String RESULT_NO_RULE = "NO_RULE";
    private static final String RESULT_LEAVE = "LEAVE";
    private static final String EXCEPTION_LATE = "LATE";
    private static final String EXCEPTION_EARLY_LEAVE = "EARLY_LEAVE";
    private static final String EXCEPTION_MISS_PUNCH = "MISS_PUNCH";
    private static final String SOURCE_APP = "APP";
    private static final String SOURCE_PC = "PC";
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_AUTO = "AUTO";
    private static final String SOURCE_TEMPLATE = "TEMPLATE";

    private final AttendanceRepository attendanceRepository;
    private final LeaveQueryService leaveQueryService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final ConcurrentMap<Long, ReentrantLock> membershipLocks = new ConcurrentHashMap<>();

    public AttendanceService(AttendanceRepository attendanceRepository,
                             LeaveQueryService leaveQueryService,
                             Clock clock,
                             TransactionTemplate transactionTemplate) {
        this.attendanceRepository = attendanceRepository;
        this.leaveQueryService = leaveQueryService;
        this.clock = clock;
        this.transactionTemplate = transactionTemplate;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceCalendarResponse createCalendar(CreateAttendanceCalendarRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String calendarCode = normalizeCode(request.getCalendarCode());
        String calendarName = request.getCalendarName().trim();
        String calendarType = normalizeCalendarType(request.getCalendarType());
        String status = normalizeStatus(request.getStatus());
        if (attendanceRepository.existsCalendarCode(calendarCode)) {
            throw new BizException(409, "日历编码已存在");
        }

        Long calendarId = IdWorker.getId();
        attendanceRepository.insertCalendar(calendarId, calendarCode, calendarName, calendarType, status, operatorId);
        return toCalendarResponse(attendanceRepository.getCalendarOrThrow(calendarId));
    }

    public List<AttendanceCalendarResponse> listCalendars() {
        return attendanceRepository.listCalendars().stream()
            .map(this::toCalendarResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceShiftResponse createShift(CreateAttendanceShiftRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String shiftCode = normalizeCode(request.getShiftCode());
        String shiftName = request.getShiftName().trim();
        String status = normalizeStatus(request.getStatus());
        if (attendanceRepository.existsShiftCode(shiftCode)) {
            throw new BizException(409, "班次编码已存在");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BizException(400, "第一版班次不支持跨天");
        }

        Long shiftId = IdWorker.getId();
        attendanceRepository.insertShift(shiftId, shiftCode, shiftName, status, operatorId);
        attendanceRepository.insertShiftSegment(
            IdWorker.getId(),
            shiftId,
            request.getStartTime(),
            request.getEndTime(),
            defaultTime(request.getOnDutyPunchStart(), request.getStartTime().minusHours(1)),
            defaultTime(request.getOnDutyPunchEnd(), request.getStartTime().plusHours(1)),
            defaultTime(request.getOffDutyPunchStart(), request.getEndTime().minusHours(1)),
            defaultTime(request.getOffDutyPunchEnd(), request.getEndTime().plusHours(1))
        );
        return toShiftResponse(attendanceRepository.getShiftOrThrow(shiftId));
    }

    public List<AttendanceShiftResponse> listShifts() {
        return attendanceRepository.listShifts().stream()
            .map(this::toShiftResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceGroupResponse createGroup(CreateAttendanceGroupRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        String groupCode = normalizeCode(request.getGroupCode());
        String groupName = request.getGroupName().trim();
        String status = normalizeStatus(request.getStatus());
        AttendanceCalendarRow calendar = attendanceRepository.getCalendarOrThrow(request.getCalendarId());
        AttendanceShiftRow shift = attendanceRepository.getShiftOrThrow(request.getDefaultShiftId());
        ensureActive(calendar.status(), "工作日历已停用");
        ensureActive(shift.status(), "班次已停用");
        if (attendanceRepository.existsGroupCode(groupCode)) {
            throw new BizException(409, "考勤组编码已存在");
        }

        Long groupId = IdWorker.getId();
        attendanceRepository.insertGroup(
            groupId,
            groupCode,
            groupName,
            calendar.calendarId(),
            shift.shiftId(),
            request.getLateGraceMinutes(),
            request.getEarlyLeaveGraceMinutes(),
            status,
            operatorId
        );
        return toGroupResponse(attendanceRepository.getGroupOrThrow(groupId));
    }

    public List<AttendanceGroupResponse> listGroups() {
        return attendanceRepository.listGroups().stream()
            .map(this::toGroupResponse)
            .toList();
    }

    public AttendanceGroupMemberResponse addGroupMember(Long groupId, AddAttendanceGroupMemberRequest request) {
        ReentrantLock lock = acquireMembershipLock(request.getUserId());
        try {
            return transactionTemplate.execute(status -> doAddGroupMember(groupId, request));
        } finally {
            releaseMembershipLock(request.getUserId(), lock);
        }
    }

    public List<AttendanceGroupMemberResponse> listGroupMembers(Long groupId) {
        attendanceRepository.getGroupOrThrow(groupId);
        return attendanceRepository.listGroupMembers(groupId).stream()
            .map(this::toGroupMemberResponse)
            .toList();
    }

    private AttendanceGroupMemberResponse doAddGroupMember(Long groupId, AddAttendanceGroupMemberRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AttendanceGroupRow group = attendanceRepository.getGroupOrThrow(groupId);
        ensureActive(group.status(), "考勤组已停用");
        Long userId = request.getUserId();
        attendanceRepository.lockActiveUserOrThrow(userId);
        if (request.getEffectiveTo() != null && request.getEffectiveTo().isBefore(request.getEffectiveFrom())) {
            throw new BizException(400, "生效结束日不能早于开始日");
        }
        if (attendanceRepository.existsOverlappingMember(
            userId,
            request.getEffectiveFrom(),
            request.getEffectiveTo()
        )) {
            throw new BizException(409, "员工考勤组生效区间重叠");
        }

        Long memberId = IdWorker.getId();
        attendanceRepository.insertGroupMember(
            memberId,
            group.groupId(),
            userId,
            request.getEffectiveFrom(),
            request.getEffectiveTo(),
            normalizeSourceType(request.getSourceType()),
            operatorId
        );
        return toGroupMemberResponse(attendanceRepository.getGroupMemberOrThrow(memberId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceScheduleResponse createSchedule(CreateAttendanceScheduleRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        attendanceRepository.lockActiveUserOrThrow(request.getUserId());
        AttendanceShiftRow shift = attendanceRepository.getShiftOrThrow(request.getShiftId());
        ensureActive(shift.status(), "班次已停用");
        if (request.getCalendarId() != null) {
            AttendanceCalendarRow calendar = attendanceRepository.getCalendarOrThrow(request.getCalendarId());
            ensureActive(calendar.status(), "工作日历已停用");
        }
        if (attendanceRepository.existsSchedule(request.getUserId(), request.getWorkDate())) {
            throw new BizException(409, "员工当天已存在排班，请通过修改原排班恢复或改班");
        }

        Long scheduleId = IdWorker.getId();
        attendanceRepository.insertSchedule(
            scheduleId,
            request.getUserId(),
            request.getWorkDate(),
            shift.shiftId(),
            request.getCalendarId(),
            normalizeScheduleSource(request.getScheduleSource()),
            operatorId
        );
        return toScheduleResponse(attendanceRepository.getScheduleOrThrow(scheduleId));
    }

    @Transactional(rollbackFor = Exception.class)
    public AttendanceScheduleResponse updateSchedule(Long scheduleId, UpdateAttendanceScheduleRequest request) {
        Long operatorId = CurrentUserHolder.requireUserId();
        AttendanceScheduleRow existing = attendanceRepository.getScheduleForUpdateOrThrow(scheduleId);
        attendanceRepository.lockActiveUserOrThrow(existing.userId());
        AttendanceShiftRow shift = attendanceRepository.getShiftOrThrow(request.getShiftId());
        ensureActive(shift.status(), "班次已停用");
        if (request.getCalendarId() != null) {
            AttendanceCalendarRow calendar = attendanceRepository.getCalendarOrThrow(request.getCalendarId());
            ensureActive(calendar.status(), "工作日历已停用");
        }

        attendanceRepository.updateSchedule(
            scheduleId,
            shift.shiftId(),
            request.getCalendarId(),
            normalizeScheduleSource(request.getScheduleSource()),
            normalizeStatus(request.getStatus()),
            operatorId
        );
        if (attendanceRepository.getDailyResult(existing.userId(), existing.workDate()) != null) {
            recalculateDailyResult(existing.userId(), existing.workDate());
        }
        return toScheduleResponse(attendanceRepository.getScheduleOrThrow(scheduleId));
    }

    public List<AttendanceScheduleResponse> listSchedules(Long userId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return attendanceRepository.listSchedules(userId, startDate, endDate).stream()
            .map(this::toScheduleResponse)
            .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public PunchRecordResponse punch(CreatePunchRecordRequest request) {
        Long userId = CurrentUserHolder.requireUserId();
        ensureCanPunch(userId);
        String punchType = normalizePunchType(request.getPunchType());
        String sourceType = normalizeEmployeePunchSourceType(request.getSourceType());
        LocalDateTime punchAt = LocalDateTime.now(clock);
        Long punchRecordId = IdWorker.getId();
        attendanceRepository.insertPunchRecord(
            punchRecordId,
            userId,
            punchAt,
            punchType,
            sourceType,
            trimToNull(request.getLocationText()),
            trimToNull(request.getIpAddress()),
            trimToNull(request.getDeviceId())
        );

        AttendanceDailyResultRow dailyResult = recalculateDailyResult(userId, punchAt.toLocalDate());
        return toPunchResponse(attendanceRepository.getPunchRecordOrThrow(punchRecordId), dailyResult);
    }

    private void ensureCanPunch(Long userId) {
        if (!attendanceRepository.canEmployeePunch(userId)) {
            throw new BizException(403, "员工当前状态不允许打卡");
        }
    }

    public List<AttendanceDailyResultResponse> listCurrentEmployeeDailyResults(LocalDate startDate,
                                                                                LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Long userId = CurrentUserHolder.requireUserId();
        return attendanceRepository.listDailyResults(userId, startDate, endDate).stream()
            .map(this::toDailyResultResponse)
            .toList();
    }

    public List<AttendanceDailyResultResponse> listDailyResultsForAdmin(Long userId, LocalDate startDate, LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return attendanceRepository.listDailyResultsForAdmin(userId, startDate, endDate).stream()
            .map(this::toDailyResultResponse)
            .toList();
    }

    public List<AttendanceExceptionResponse> listCurrentEmployeeExceptions(LocalDate startDate,
                                                                            LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Long userId = CurrentUserHolder.requireUserId();
        return attendanceRepository.listExceptions(userId, startDate, endDate).stream()
            .map(this::toExceptionResponse)
            .toList();
    }

    public List<AttendanceDailyResultResponse> listTeamDailyResults(Long targetUserId,
                                                                     LocalDate startDate,
                                                                     LocalDate endDate) {
        validateDateRange(startDate, endDate);
        Long managerUserId = CurrentUserHolder.requireUserId();
        if (!attendanceRepository.isDirectManager(managerUserId, targetUserId)) {
            throw new BizException(403, "只能查询直属下级考勤");
        }
        return attendanceRepository.listDailyResults(targetUserId, startDate, endDate).stream()
            .map(this::toDailyResultResponse)
            .toList();
    }

    @Override
    public List<Map<String, Object>> getMonthlyExceptions(Long userId, String yearMonth) {
        LocalDate startDate = LocalDate.parse(yearMonth + "-01");
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        return attendanceRepository.listExceptions(userId, startDate, endDate).stream()
            .map(row -> {
                Map<String, Object> value = new HashMap<>();
                value.put("exceptionId", row.exceptionId());
                value.put("dailyResultId", row.dailyResultId());
                value.put("userId", row.userId());
                value.put("workDate", row.workDate().toString());
                value.put("exceptionType", row.exceptionType());
                value.put("exceptionDesc", row.exceptionDesc());
                value.put("status", row.status());
                return value;
            })
            .toList();
    }

    @Override
    public Map<String, Object> getDailyResult(Long userId, LocalDate workDate) {
        AttendanceDailyResultRow row = attendanceRepository.getDailyResult(userId, workDate);
        if (row == null) {
            return null;
        }
        Map<String, Object> value = new HashMap<>();
        value.put("dailyResultId", row.dailyResultId());
        value.put("userId", row.userId());
        value.put("workDate", row.workDate().toString());
        value.put("resultStatus", row.resultStatus());
        value.put("lateMinutes", row.lateMinutes());
        value.put("earlyLeaveMinutes", row.earlyLeaveMinutes());
        value.put("absenceMinutes", row.absenceMinutes());
        value.put("workMinutes", row.workMinutes());
        value.put("exceptionFlag", row.exceptionFlag());
        return value;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recalculateDailyResultForExternalEvent(Long userId, LocalDate workDate) {
        recalculateDailyResult(userId, workDate);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyApprovedClockCorrection(Long userId,
                                             LocalDate targetDate,
                                             String punchType,
                                             LocalDateTime expectedPunchAt,
                                             Long requestId,
                                             Long approverUserId) {
        ensureCanPunch(userId);
        String normalizedPunchType = normalizePunchType(punchType);
        if (!targetDate.equals(expectedPunchAt.toLocalDate())) {
            throw new BizException(400, "补卡时间必须落在补卡日期内");
        }
        if (!attendanceRepository.existsNormalPunchRecord(userId, expectedPunchAt, normalizedPunchType)) {
            attendanceRepository.insertPunchRecord(
                IdWorker.getId(),
                userId,
                expectedPunchAt,
                normalizedPunchType,
                SOURCE_MANUAL,
                "审批补卡 requestId=" + requestId,
                null,
                null
            );
        }
        recalculateDailyResult(userId, targetDate);
    }

    private AttendanceDailyResultRow recalculateDailyResult(Long userId, LocalDate workDate) {
        AttendanceGroupRow group = attendanceRepository.getEffectiveGroupForUpdate(userId, workDate);
        if (group == null) {
            return upsertDailyResult(userId, workDate, null, null, null, RESULT_NO_RULE, 0, 0, 0, 0, true);
        }

        AttendanceScheduleRow schedule = attendanceRepository.getActiveSchedule(userId, workDate);
        Long scheduleId = schedule == null ? null : schedule.scheduleId();
        Long shiftId = schedule == null ? group.defaultShiftId() : schedule.shiftId();
        AttendanceShiftRow shift = attendanceRepository.getShiftOrThrow(shiftId);
        LocalDateTime firstPunchAt = attendanceRepository.getFirstPunchAt(userId, workDate);
        LocalDateTime lastPunchAt = attendanceRepository.getLastPunchAt(userId, workDate);
        List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods =
            leaveQueryService.listApprovedLeavePeriodsOnDate(userId, workDate);
        int requiredWorkMinutes = minutesBetween(shift.startTime(), shift.endTime());
        int approvedLeaveCoveredMinutes = calculateApprovedLeaveCoveredMinutes(
            approvedLeavePeriods,
            workDate,
            shift.startTime(),
            shift.endTime()
        );
        if (approvedLeaveCoveredMinutes >= requiredWorkMinutes) {
            AttendanceDailyResultRow row = upsertDailyResult(
                userId,
                workDate,
                scheduleId,
                group.groupId(),
                shiftId,
                RESULT_LEAVE,
                0,
                0,
                0,
                0,
                false
            );
            attendanceRepository.deleteExceptionsByDailyResultId(row.dailyResultId());
            return attendanceRepository.getDailyResult(userId, workDate);
        }

        int lateMinutes = calculateLateMinutes(
            firstPunchAt,
            workDate,
            shift.startTime(),
            group.lateGraceMinutes(),
            approvedLeavePeriods
        );
        int earlyLeaveMinutes = calculateEarlyLeaveMinutes(
            lastPunchAt,
            workDate,
            shift.endTime(),
            group.earlyLeaveGraceMinutes(),
            approvedLeavePeriods
        );
        int absenceMinutes = calculateAbsenceMinutes(
            firstPunchAt,
            lastPunchAt,
            workDate,
            shift.startTime(),
            shift.endTime(),
            approvedLeavePeriods
        );
        int workMinutes = calculateWorkMinutes(firstPunchAt, lastPunchAt);
        boolean exceptionFlag = lateMinutes > 0 || earlyLeaveMinutes > 0 || absenceMinutes > 0;
        String resultStatus = exceptionFlag ? RESULT_ABNORMAL : RESULT_NORMAL;

        AttendanceDailyResultRow row = upsertDailyResult(
            userId,
            workDate,
            scheduleId,
            group.groupId(),
            shiftId,
            resultStatus,
            lateMinutes,
            earlyLeaveMinutes,
            absenceMinutes,
            workMinutes,
            exceptionFlag
        );
        rebuildExceptions(row.dailyResultId(), lateMinutes, earlyLeaveMinutes, absenceMinutes);
        return attendanceRepository.getDailyResult(userId, workDate);
    }

    private AttendanceDailyResultRow upsertDailyResult(Long userId,
                                                       LocalDate workDate,
                                                       Long scheduleId,
                                                       Long groupId,
                                                       Long shiftId,
                                                       String resultStatus,
                                                       int lateMinutes,
                                                       int earlyLeaveMinutes,
                                                       int absenceMinutes,
                                                       int workMinutes,
                                                       boolean exceptionFlag) {
        LocalDateTime calculatedAt = LocalDateTime.now();
        attendanceRepository.upsertDailyResult(
            IdWorker.getId(),
            userId,
            workDate,
            scheduleId,
            groupId,
            shiftId,
            resultStatus,
            lateMinutes,
            earlyLeaveMinutes,
            absenceMinutes,
            workMinutes,
            exceptionFlag,
            calculatedAt
        );
        return attendanceRepository.getDailyResult(userId, workDate);
    }

    private void rebuildExceptions(Long dailyResultId,
                                   int lateMinutes,
                                   int earlyLeaveMinutes,
                                   int absenceMinutes) {
        attendanceRepository.deleteExceptionsByDailyResultId(dailyResultId);
        if (lateMinutes > 0) {
            attendanceRepository.insertException(
                IdWorker.getId(),
                dailyResultId,
                EXCEPTION_LATE,
                "迟到%s分钟".formatted(lateMinutes)
            );
        }
        if (earlyLeaveMinutes > 0) {
            attendanceRepository.insertException(
                IdWorker.getId(),
                dailyResultId,
                EXCEPTION_EARLY_LEAVE,
                "早退%s分钟".formatted(earlyLeaveMinutes)
            );
        }
        if (absenceMinutes > 0) {
            attendanceRepository.insertException(
                IdWorker.getId(),
                dailyResultId,
                EXCEPTION_MISS_PUNCH,
                "缺卡导致缺勤%s分钟".formatted(absenceMinutes)
            );
        }
    }

    private int calculateLateMinutes(LocalDateTime firstPunchAt,
                                     LocalDate workDate,
                                     LocalTime startTime,
                                     int graceMinutes,
                                     List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods) {
        if (firstPunchAt == null) {
            return 0;
        }
        LocalDateTime expected = LocalDateTime.of(workDate, startTime).plusMinutes(graceMinutes);
        if (!firstPunchAt.isAfter(expected)) {
            return 0;
        }
        int rawLateMinutes = minutesBetween(expected, firstPunchAt);
        int leaveCoveredMinutes = calculateApprovedLeaveCoveredMinutes(approvedLeavePeriods, expected, firstPunchAt);
        return Math.max(0, rawLateMinutes - leaveCoveredMinutes);
    }

    private int calculateEarlyLeaveMinutes(LocalDateTime lastPunchAt,
                                           LocalDate workDate,
                                           LocalTime endTime,
                                           int graceMinutes,
                                           List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods) {
        if (lastPunchAt == null) {
            return 0;
        }
        LocalDateTime expected = LocalDateTime.of(workDate, endTime).minusMinutes(graceMinutes);
        if (!lastPunchAt.isBefore(expected)) {
            return 0;
        }
        int rawEarlyLeaveMinutes = minutesBetween(lastPunchAt, expected);
        int leaveCoveredMinutes = calculateApprovedLeaveCoveredMinutes(approvedLeavePeriods, lastPunchAt, expected);
        return Math.max(0, rawEarlyLeaveMinutes - leaveCoveredMinutes);
    }

    private int calculateAbsenceMinutes(LocalDateTime firstPunchAt,
                                        LocalDateTime lastPunchAt,
                                        LocalDate workDate,
                                        LocalTime startTime,
                                        LocalTime endTime,
                                        List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods) {
        int rawAbsenceMinutes;
        if (firstPunchAt == null && lastPunchAt == null) {
            rawAbsenceMinutes = minutesBetween(startTime, endTime);
            return Math.max(0, rawAbsenceMinutes - calculateApprovedLeaveCoveredMinutes(
                approvedLeavePeriods,
                workDate,
                startTime,
                endTime
            ));
        }
        if (firstPunchAt == null) {
            rawAbsenceMinutes = minutesBetween(startTime, endTime) / 2;
            return Math.max(0, rawAbsenceMinutes - calculateApprovedLeaveCoveredMinutes(
                approvedLeavePeriods,
                workDate,
                startTime,
                startTime.plusMinutes(rawAbsenceMinutes)
            ));
        }
        if (lastPunchAt == null) {
            rawAbsenceMinutes = minutesBetween(startTime, endTime) / 2;
            return Math.max(0, rawAbsenceMinutes - calculateApprovedLeaveCoveredMinutes(
                approvedLeavePeriods,
                workDate,
                endTime.minusMinutes(rawAbsenceMinutes),
                endTime
            ));
        }
        return 0;
    }

    private int calculateApprovedLeaveCoveredMinutes(List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods,
                                                     LocalDate workDate,
                                                     LocalTime startTime,
                                                     LocalTime endTime) {
        if (approvedLeavePeriods.isEmpty() || !endTime.isAfter(startTime)) {
            return 0;
        }
        LocalDateTime expectedStart = LocalDateTime.of(workDate, startTime);
        LocalDateTime expectedEnd = LocalDateTime.of(workDate, endTime);
        return calculateApprovedLeaveCoveredMinutes(approvedLeavePeriods, expectedStart, expectedEnd);
    }

    private int calculateApprovedLeaveCoveredMinutes(List<LeaveQueryService.ApprovedLeavePeriod> approvedLeavePeriods,
                                                     LocalDateTime expectedStart,
                                                     LocalDateTime expectedEnd) {
        if (approvedLeavePeriods.isEmpty() || !expectedEnd.isAfter(expectedStart)) {
            return 0;
        }
        List<TimeRange> coveredRanges = approvedLeavePeriods.stream()
            .map(period -> overlapRange(period, expectedStart, expectedEnd))
            .filter(range -> range != null)
            .sorted(Comparator.comparing(TimeRange::startAt))
            .toList();
        if (coveredRanges.isEmpty()) {
            return 0;
        }
        List<TimeRange> mergedRanges = new ArrayList<>();
        for (TimeRange range : coveredRanges) {
            if (mergedRanges.isEmpty()) {
                mergedRanges.add(range);
                continue;
            }
            TimeRange last = mergedRanges.get(mergedRanges.size() - 1);
            if (!range.startAt().isAfter(last.endAt())) {
                LocalDateTime mergedEnd = range.endAt().isAfter(last.endAt()) ? range.endAt() : last.endAt();
                mergedRanges.set(mergedRanges.size() - 1, new TimeRange(last.startAt(), mergedEnd));
                continue;
            }
            mergedRanges.add(range);
        }
        return mergedRanges.stream()
            .mapToInt(range -> minutesBetween(range.startAt(), range.endAt()))
            .sum();
    }

    private TimeRange overlapRange(LeaveQueryService.ApprovedLeavePeriod period,
                                   LocalDateTime expectedStart,
                                   LocalDateTime expectedEnd) {
        LocalDateTime overlapStart = period.startAt().isAfter(expectedStart) ? period.startAt() : expectedStart;
        LocalDateTime overlapEnd = period.endAt().isBefore(expectedEnd) ? period.endAt() : expectedEnd;
        if (!overlapEnd.isAfter(overlapStart)) {
            return null;
        }
        return new TimeRange(overlapStart, overlapEnd);
    }

    private record TimeRange(LocalDateTime startAt, LocalDateTime endAt) {
    }

    private int calculateWorkMinutes(LocalDateTime firstPunchAt, LocalDateTime lastPunchAt) {
        if (firstPunchAt == null || lastPunchAt == null || !lastPunchAt.isAfter(firstPunchAt)) {
            return 0;
        }
        return minutesBetween(firstPunchAt, lastPunchAt);
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BizException(400, "结束日期不能早于开始日期");
        }
        if (startDate.plusDays(62).isBefore(endDate)) {
            throw new BizException(400, "查询跨度不能超过63天");
        }
    }

    private ReentrantLock acquireMembershipLock(Long userId) {
        ReentrantLock lock = membershipLocks.computeIfAbsent(userId, key -> new ReentrantLock());
        try {
            if (!lock.tryLock(5, TimeUnit.SECONDS)) {
                throw new BizException(409, "员工考勤组归属正在调整，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(409, "员工考勤组归属正在调整，请稍后重试");
        }
        return lock;
    }

    private void releaseMembershipLock(Long userId, ReentrantLock lock) {
        try {
            lock.unlock();
        } finally {
            if (!lock.isLocked() && !lock.hasQueuedThreads()) {
                membershipLocks.remove(userId, lock);
            }
        }
    }

    private AttendanceCalendarResponse toCalendarResponse(AttendanceCalendarRow row) {
        AttendanceCalendarResponse response = new AttendanceCalendarResponse();
        response.setCalendarId(row.calendarId());
        response.setCalendarCode(row.calendarCode());
        response.setCalendarName(row.calendarName());
        response.setCalendarType(row.calendarType());
        response.setStatus(row.status());
        return response;
    }

    private AttendanceShiftResponse toShiftResponse(AttendanceShiftRow row) {
        AttendanceShiftResponse response = new AttendanceShiftResponse();
        response.setShiftId(row.shiftId());
        response.setShiftCode(row.shiftCode());
        response.setShiftName(row.shiftName());
        response.setStartTime(row.startTime());
        response.setEndTime(row.endTime());
        response.setOnDutyPunchStart(row.onDutyPunchStart());
        response.setOnDutyPunchEnd(row.onDutyPunchEnd());
        response.setOffDutyPunchStart(row.offDutyPunchStart());
        response.setOffDutyPunchEnd(row.offDutyPunchEnd());
        response.setStatus(row.status());
        return response;
    }

    private AttendanceGroupResponse toGroupResponse(AttendanceGroupRow row) {
        AttendanceGroupResponse response = new AttendanceGroupResponse();
        response.setGroupId(row.groupId());
        response.setGroupCode(row.groupCode());
        response.setGroupName(row.groupName());
        response.setCalendarId(row.calendarId());
        response.setDefaultShiftId(row.defaultShiftId());
        response.setLateGraceMinutes(row.lateGraceMinutes());
        response.setEarlyLeaveGraceMinutes(row.earlyLeaveGraceMinutes());
        response.setStatus(row.status());
        return response;
    }

    private AttendanceGroupMemberResponse toGroupMemberResponse(AttendanceGroupMemberRow row) {
        AttendanceGroupMemberResponse response = new AttendanceGroupMemberResponse();
        response.setMemberId(row.memberId());
        response.setGroupId(row.groupId());
        response.setUserId(row.userId());
        response.setEffectiveFrom(row.effectiveFrom());
        response.setEffectiveTo(row.effectiveTo());
        response.setSourceType(row.sourceType());
        response.setStatus(row.status());
        return response;
    }

    private AttendanceScheduleResponse toScheduleResponse(AttendanceScheduleRow row) {
        AttendanceScheduleResponse response = new AttendanceScheduleResponse();
        response.setScheduleId(row.scheduleId());
        response.setUserId(row.userId());
        response.setWorkDate(row.workDate());
        response.setShiftId(row.shiftId());
        response.setCalendarId(row.calendarId());
        response.setScheduleSource(row.scheduleSource());
        response.setStatus(row.status());
        return response;
    }

    private PunchRecordResponse toPunchResponse(PunchRecordRow row, AttendanceDailyResultRow dailyResult) {
        PunchRecordResponse response = new PunchRecordResponse();
        response.setPunchRecordId(row.punchRecordId());
        response.setUserId(row.userId());
        response.setPunchAt(row.punchAt());
        response.setPunchType(row.punchType());
        response.setSourceType(row.sourceType());
        response.setStatus(row.status());
        response.setDailyResult(toDailyResultResponse(dailyResult));
        return response;
    }

    private AttendanceDailyResultResponse toDailyResultResponse(AttendanceDailyResultRow row) {
        AttendanceDailyResultResponse response = new AttendanceDailyResultResponse();
        response.setDailyResultId(row.dailyResultId());
        response.setUserId(row.userId());
        response.setWorkDate(row.workDate());
        response.setScheduleId(row.scheduleId());
        response.setGroupId(row.groupId());
        response.setShiftId(row.shiftId());
        response.setResultStatus(row.resultStatus());
        response.setLateMinutes(row.lateMinutes());
        response.setEarlyLeaveMinutes(row.earlyLeaveMinutes());
        response.setAbsenceMinutes(row.absenceMinutes());
        response.setWorkMinutes(row.workMinutes());
        response.setExceptionFlag(row.exceptionFlag());
        response.setCalculatedAt(row.calculatedAt());
        return response;
    }

    private AttendanceExceptionResponse toExceptionResponse(AttendanceExceptionRow row) {
        AttendanceExceptionResponse response = new AttendanceExceptionResponse();
        response.setExceptionId(row.exceptionId());
        response.setDailyResultId(row.dailyResultId());
        response.setUserId(row.userId());
        response.setWorkDate(row.workDate());
        response.setExceptionType(row.exceptionType());
        response.setExceptionDesc(row.exceptionDesc());
        response.setStatus(row.status());
        return response;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCalendarType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!"STANDARD".equals(normalized)) {
            throw new BizException(400, "第一版日历类型仅支持 STANDARD");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_INACTIVE.equals(normalized)) {
            throw new BizException(400, "状态仅支持 ACTIVE/INACTIVE");
        }
        return normalized;
    }

    private String normalizePunchType(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!PUNCH_TYPE_ON_DUTY.equals(normalized) && !PUNCH_TYPE_OFF_DUTY.equals(normalized)) {
            throw new BizException(400, "打卡类型仅支持 ON_DUTY/OFF_DUTY");
        }
        return normalized;
    }

    private String normalizeSourceType(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeEmployeePunchSourceType(String value) {
        String normalized = normalizeSourceType(value);
        if (!SOURCE_APP.equals(normalized) && !SOURCE_PC.equals(normalized)) {
            throw new BizException(400, "员工实时打卡来源仅支持 APP/PC");
        }
        return normalized;
    }

    private String normalizeScheduleSource(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!SOURCE_MANUAL.equals(normalized) && !SOURCE_AUTO.equals(normalized) && !SOURCE_TEMPLATE.equals(normalized)) {
            throw new BizException(400, "排班来源仅支持 MANUAL/AUTO/TEMPLATE");
        }
        return normalized;
    }

    private void ensureActive(String status, String message) {
        if (!STATUS_ACTIVE.equals(status)) {
            throw new BizException(409, message);
        }
    }

    private LocalTime defaultTime(LocalTime value, LocalTime defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int minutesBetween(LocalDateTime start, LocalDateTime end) {
        return Math.toIntExact(Duration.between(start, end).toMinutes());
    }

    private int minutesBetween(LocalTime start, LocalTime end) {
        return Math.toIntExact(Duration.between(start, end).toMinutes());
    }
}
