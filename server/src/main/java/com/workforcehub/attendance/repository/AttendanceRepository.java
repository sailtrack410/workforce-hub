package com.workforcehub.attendance.repository;

import com.workforcehub.common.exception.BizException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * module-attendance 第一版 JDBC 仓储。
 */
@Repository
public class AttendanceRepository {

    private final JdbcTemplate jdbcTemplate;

    public AttendanceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsCalendarCode(String calendarCode) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_work_calendar
            WHERE calendar_code = ?
              AND is_deleted = 0
            """, Long.class, calendarCode);
        return count != null && count > 0;
    }

    public void insertCalendar(Long calendarId,
                               String calendarCode,
                               String calendarName,
                               String calendarType,
                               String status,
                               Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO att_work_calendar
                (id, calendar_code, calendar_name, calendar_type, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?)
            """,
            calendarId,
            calendarCode,
            calendarName,
            calendarType,
            status,
            operatorId,
            operatorId
        );
    }

    public AttendanceCalendarRow getCalendarOrThrow(Long calendarId) {
        List<AttendanceCalendarRow> rows = jdbcTemplate.query("""
            SELECT id, calendar_code, calendar_name, calendar_type, status
            FROM att_work_calendar
            WHERE id = ?
              AND is_deleted = 0
            LIMIT 1
            """,
            (rs, rowNum) -> new AttendanceCalendarRow(
                rs.getLong("id"),
                rs.getString("calendar_code"),
                rs.getString("calendar_name"),
                rs.getString("calendar_type"),
                rs.getString("status")
            ),
            calendarId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "工作日历不存在");
        }
        return rows.get(0);
    }

    public boolean existsShiftCode(String shiftCode) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_shift
            WHERE shift_code = ?
              AND is_deleted = 0
            """, Long.class, shiftCode);
        return count != null && count > 0;
    }

    public void insertShift(Long shiftId,
                            String shiftCode,
                            String shiftName,
                            String status,
                            Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO att_shift
                (id, shift_code, shift_name, cross_day_flag, required_punch_count,
                 status, created_by, updated_by)
            VALUES
                (?, ?, ?, 0, 2, ?, ?, ?)
            """,
            shiftId,
            shiftCode,
            shiftName,
            status,
            operatorId,
            operatorId
        );
    }

    public void insertShiftSegment(Long segmentId,
                                   Long shiftId,
                                   LocalTime startTime,
                                   LocalTime endTime,
                                   LocalTime onDutyPunchStart,
                                   LocalTime onDutyPunchEnd,
                                   LocalTime offDutyPunchStart,
                                   LocalTime offDutyPunchEnd) {
        jdbcTemplate.update("""
            INSERT INTO att_shift_segment
                (id, shift_id, segment_no, start_time, end_time,
                 on_duty_punch_start, on_duty_punch_end,
                 off_duty_punch_start, off_duty_punch_end)
            VALUES
                (?, ?, 1, ?, ?, ?, ?, ?, ?)
            """,
            segmentId,
            shiftId,
            startTime,
            endTime,
            onDutyPunchStart,
            onDutyPunchEnd,
            offDutyPunchStart,
            offDutyPunchEnd
        );
    }

    public AttendanceShiftRow getShiftOrThrow(Long shiftId) {
        List<AttendanceShiftRow> rows = jdbcTemplate.query("""
            SELECT s.id,
                   s.shift_code,
                   s.shift_name,
                   ss.start_time,
                   ss.end_time,
                   ss.on_duty_punch_start,
                   ss.on_duty_punch_end,
                   ss.off_duty_punch_start,
                   ss.off_duty_punch_end,
                   s.status
            FROM att_shift s
            JOIN att_shift_segment ss ON ss.shift_id = s.id
            WHERE s.id = ?
              AND s.is_deleted = 0
            ORDER BY ss.segment_no ASC
            LIMIT 1
            """,
            this::mapShift,
            shiftId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "班次不存在");
        }
        return rows.get(0);
    }

    public boolean existsGroupCode(String groupCode) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_group
            WHERE group_code = ?
              AND is_deleted = 0
            """, Long.class, groupCode);
        return count != null && count > 0;
    }

    public void insertGroup(Long groupId,
                            String groupCode,
                            String groupName,
                            Long calendarId,
                            Long defaultShiftId,
                            Integer lateGraceMinutes,
                            Integer earlyLeaveGraceMinutes,
                            String status,
                            Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO att_group
                (id, group_code, group_name, calendar_id, default_shift_id,
                 late_grace_minutes, early_leave_grace_minutes,
                 status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            groupId,
            groupCode,
            groupName,
            calendarId,
            defaultShiftId,
            lateGraceMinutes,
            earlyLeaveGraceMinutes,
            status,
            operatorId,
            operatorId
        );
    }

    public AttendanceGroupRow getGroupOrThrow(Long groupId) {
        List<AttendanceGroupRow> rows = jdbcTemplate.query("""
            SELECT id, group_code, group_name, calendar_id, default_shift_id,
                   late_grace_minutes, early_leave_grace_minutes, status
            FROM att_group
            WHERE id = ?
              AND is_deleted = 0
            LIMIT 1
            """,
            this::mapGroup,
            groupId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "考勤组不存在");
        }
        return rows.get(0);
    }

    public boolean existsActiveUser(Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user
            WHERE id = ?
              AND status = 'ACTIVE'
              AND employment_status <> 'OFFBOARDED'
              AND is_deleted = 0
            """, Long.class, userId);
        return count != null && count > 0;
    }

    public boolean canEmployeePunch(Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user u
            JOIN auth_user_account a ON a.user_id = u.id
                                  AND a.is_deleted = 0
            WHERE u.id = ?
              AND u.status = 'ACTIVE'
              AND u.employment_status IN ('PROBATION', 'ACTIVE', 'PENDING_OFFBOARD')
              AND u.is_deleted = 0
              AND a.account_status = 'ACTIVE'
            """, Long.class, userId);
        return count != null && count > 0;
    }

    public void lockActiveUserOrThrow(Long userId) {
        List<Long> rows = jdbcTemplate.query("""
            SELECT id
            FROM org_user
            WHERE id = ?
              AND status = 'ACTIVE'
              AND employment_status <> 'OFFBOARDED'
              AND is_deleted = 0
            LIMIT 1
            FOR UPDATE
            """,
            (rs, rowNum) -> rs.getLong("id"),
            userId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "员工不存在或不可用");
        }
    }

    public boolean existsOverlappingMember(Long userId, LocalDate effectiveFrom, LocalDate effectiveTo) {
        LocalDate normalizedTo = normalizeOpenEnd(effectiveTo);
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM att_group_member
            WHERE user_id = ?
              AND status = 'ACTIVE'
              AND effective_from <= ?
              AND COALESCE(effective_to, '9999-12-31') >= ?
            """,
            Long.class,
            userId,
            normalizedTo,
            effectiveFrom
        );
        return count != null && count > 0;
    }

    public void insertGroupMember(Long memberId,
                                  Long groupId,
                                  Long userId,
                                  LocalDate effectiveFrom,
                                  LocalDate effectiveTo,
                                  String sourceType,
                                  Long operatorId) {
        jdbcTemplate.update("""
            INSERT INTO att_group_member
                (id, group_id, user_id, effective_from, effective_to,
                 source_type, status, created_by, updated_by)
            VALUES
                (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?)
            """,
            memberId,
            groupId,
            userId,
            effectiveFrom,
            effectiveTo,
            sourceType,
            operatorId,
            operatorId
        );
    }

    public AttendanceGroupMemberRow getGroupMemberOrThrow(Long memberId) {
        List<AttendanceGroupMemberRow> rows = jdbcTemplate.query("""
            SELECT id, group_id, user_id, effective_from, effective_to, source_type, status
            FROM att_group_member
            WHERE id = ?
            LIMIT 1
            """,
            this::mapGroupMember,
            memberId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "考勤组成员不存在");
        }
        return rows.get(0);
    }

    public AttendanceGroupRow getEffectiveGroupForUpdate(Long userId, LocalDate workDate) {
        List<AttendanceGroupRow> rows = jdbcTemplate.query("""
            SELECT g.id, g.group_code, g.group_name, g.calendar_id, g.default_shift_id,
                   g.late_grace_minutes, g.early_leave_grace_minutes, g.status
            FROM att_group_member gm
            JOIN att_group g ON g.id = gm.group_id
            WHERE gm.user_id = ?
              AND gm.status = 'ACTIVE'
              AND gm.effective_from <= ?
              AND (gm.effective_to IS NULL OR gm.effective_to >= ?)
              AND g.status = 'ACTIVE'
              AND g.is_deleted = 0
            ORDER BY gm.effective_from DESC, gm.id DESC
            LIMIT 1
            FOR UPDATE
            """,
            this::mapGroup,
            userId,
            workDate,
            workDate
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public Long getScheduleId(Long userId, LocalDate workDate) {
        List<Long> rows = jdbcTemplate.query("""
            SELECT id
            FROM att_schedule
            WHERE user_id = ?
              AND work_date = ?
              AND status = 'ACTIVE'
            LIMIT 1
            """,
            (rs, rowNum) -> rs.getLong("id"),
            userId,
            workDate
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertPunchRecord(Long punchRecordId,
                                  Long userId,
                                  LocalDateTime punchAt,
                                  String punchType,
                                  String sourceType,
                                  String locationText,
                                  String ipAddress,
                                  String deviceId) {
        jdbcTemplate.update("""
            INSERT INTO att_punch_record
                (id, user_id, punch_at, punch_type, source_type,
                 location_text, ip_address, device_id, status)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, 'NORMAL')
            """,
            punchRecordId,
            userId,
            punchAt,
            punchType,
            sourceType,
            locationText,
            ipAddress,
            deviceId
        );
    }

    public PunchRecordRow getPunchRecordOrThrow(Long punchRecordId) {
        List<PunchRecordRow> rows = jdbcTemplate.query("""
            SELECT id, user_id, punch_at, punch_type, source_type, status
            FROM att_punch_record
            WHERE id = ?
            LIMIT 1
            """,
            (rs, rowNum) -> new PunchRecordRow(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getTimestamp("punch_at").toLocalDateTime(),
                rs.getString("punch_type"),
                rs.getString("source_type"),
                rs.getString("status")
            ),
            punchRecordId
        );
        if (rows.isEmpty()) {
            throw new BizException(404, "打卡记录不存在");
        }
        return rows.get(0);
    }

    public LocalDateTime getFirstPunchAt(Long userId, LocalDate workDate) {
        return queryPunchAt("""
            SELECT punch_at
            FROM att_punch_record
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = 'ON_DUTY'
              AND status = 'NORMAL'
            ORDER BY punch_at ASC
            LIMIT 1
            """,
            userId,
            workDate
        );
    }

    public LocalDateTime getLastPunchAt(Long userId, LocalDate workDate) {
        return queryPunchAt("""
            SELECT punch_at
            FROM att_punch_record
            WHERE user_id = ?
              AND DATE(punch_at) = ?
              AND punch_type = 'OFF_DUTY'
              AND status = 'NORMAL'
            ORDER BY punch_at DESC
            LIMIT 1
            """,
            userId,
            workDate
        );
    }

    public AttendanceDailyResultRow getDailyResultForUpdate(Long userId, LocalDate workDate) {
        List<AttendanceDailyResultRow> rows = jdbcTemplate.query(dailyResultSelectSql() + """
            WHERE user_id = ?
              AND work_date = ?
            LIMIT 1
            FOR UPDATE
            """,
            this::mapDailyResult,
            userId,
            workDate
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public AttendanceDailyResultRow getDailyResult(Long userId, LocalDate workDate) {
        List<AttendanceDailyResultRow> rows = jdbcTemplate.query(dailyResultSelectSql() + """
            WHERE user_id = ?
              AND work_date = ?
            LIMIT 1
            """,
            this::mapDailyResult,
            userId,
            workDate
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertDailyResult(Long dailyResultId,
                                  Long userId,
                                  LocalDate workDate,
                                  Long scheduleId,
                                  Long groupId,
                                  Long shiftId,
                                  String resultStatus,
                                  Integer lateMinutes,
                                  Integer earlyLeaveMinutes,
                                  Integer absenceMinutes,
                                  Integer workMinutes,
                                  boolean exceptionFlag,
                                  LocalDateTime calculatedAt) {
        jdbcTemplate.update("""
            INSERT INTO att_daily_result
                (id, user_id, work_date, schedule_id, group_id, shift_id,
                 result_status, late_minutes, early_leave_minutes, absence_minutes,
                 work_minutes, exception_flag, calculated_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            dailyResultId,
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
    }

    public void updateDailyResult(Long dailyResultId,
                                  Long scheduleId,
                                  Long groupId,
                                  Long shiftId,
                                  String resultStatus,
                                  Integer lateMinutes,
                                  Integer earlyLeaveMinutes,
                                  Integer absenceMinutes,
                                  Integer workMinutes,
                                  boolean exceptionFlag,
                                  LocalDateTime calculatedAt) {
        jdbcTemplate.update("""
            UPDATE att_daily_result
            SET schedule_id = ?,
                group_id = ?,
                shift_id = ?,
                result_status = ?,
                late_minutes = ?,
                early_leave_minutes = ?,
                absence_minutes = ?,
                work_minutes = ?,
                exception_flag = ?,
                calculated_at = ?
            WHERE id = ?
            """,
            scheduleId,
            groupId,
            shiftId,
            resultStatus,
            lateMinutes,
            earlyLeaveMinutes,
            absenceMinutes,
            workMinutes,
            exceptionFlag,
            calculatedAt,
            dailyResultId
        );
    }

    public void deleteExceptionsByDailyResultId(Long dailyResultId) {
        jdbcTemplate.update("""
            DELETE FROM att_exception
            WHERE daily_result_id = ?
            """, dailyResultId);
    }

    public void insertException(Long exceptionId,
                                Long dailyResultId,
                                String exceptionType,
                                String exceptionDesc) {
        jdbcTemplate.update("""
            INSERT INTO att_exception
                (id, daily_result_id, exception_type, exception_desc, status)
            VALUES
                (?, ?, ?, ?, 'PENDING')
            """,
            exceptionId,
            dailyResultId,
            exceptionType,
            exceptionDesc
        );
    }

    public List<AttendanceDailyResultRow> listDailyResults(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(dailyResultSelectSql() + """
            WHERE user_id = ?
              AND work_date BETWEEN ? AND ?
            ORDER BY work_date ASC
            """,
            this::mapDailyResult,
            userId,
            startDate,
            endDate
        );
    }

    public List<AttendanceExceptionRow> listExceptions(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query("""
            SELECT ae.id,
                   ae.daily_result_id,
                   adr.user_id,
                   adr.work_date,
                   ae.exception_type,
                   ae.exception_desc,
                   ae.status
            FROM att_exception ae
            JOIN att_daily_result adr ON adr.id = ae.daily_result_id
            WHERE adr.user_id = ?
              AND adr.work_date BETWEEN ? AND ?
            ORDER BY adr.work_date ASC, ae.id ASC
            """,
            this::mapException,
            userId,
            startDate,
            endDate
        );
    }

    public boolean isDirectManager(Long managerUserId, Long userId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM org_user
            WHERE id = ?
              AND manager_user_id = ?
              AND is_deleted = 0
            """, Long.class, userId, managerUserId);
        return count != null && count > 0;
    }

    private String dailyResultSelectSql() {
        return """
            SELECT id, user_id, work_date, schedule_id, group_id, shift_id,
                   result_status, late_minutes, early_leave_minutes,
                   absence_minutes, work_minutes, exception_flag, calculated_at
            FROM att_daily_result
            """;
    }

    private AttendanceShiftRow mapShift(ResultSet rs, int rowNum) throws SQLException {
        return new AttendanceShiftRow(
            rs.getLong("id"),
            rs.getString("shift_code"),
            rs.getString("shift_name"),
            localTime(rs, "start_time"),
            localTime(rs, "end_time"),
            localTime(rs, "on_duty_punch_start"),
            localTime(rs, "on_duty_punch_end"),
            localTime(rs, "off_duty_punch_start"),
            localTime(rs, "off_duty_punch_end"),
            rs.getString("status")
        );
    }

    private AttendanceGroupRow mapGroup(ResultSet rs, int rowNum) throws SQLException {
        return new AttendanceGroupRow(
            rs.getLong("id"),
            rs.getString("group_code"),
            rs.getString("group_name"),
            rs.getLong("calendar_id"),
            rs.getObject("default_shift_id", Long.class),
            rs.getInt("late_grace_minutes"),
            rs.getInt("early_leave_grace_minutes"),
            rs.getString("status")
        );
    }

    private AttendanceGroupMemberRow mapGroupMember(ResultSet rs, int rowNum) throws SQLException {
        return new AttendanceGroupMemberRow(
            rs.getLong("id"),
            rs.getLong("group_id"),
            rs.getLong("user_id"),
            rs.getDate("effective_from").toLocalDate(),
            localDate(rs, "effective_to"),
            rs.getString("source_type"),
            rs.getString("status")
        );
    }

    private AttendanceDailyResultRow mapDailyResult(ResultSet rs, int rowNum) throws SQLException {
        return new AttendanceDailyResultRow(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getDate("work_date").toLocalDate(),
            rs.getObject("schedule_id", Long.class),
            rs.getObject("group_id", Long.class),
            rs.getObject("shift_id", Long.class),
            rs.getString("result_status"),
            rs.getInt("late_minutes"),
            rs.getInt("early_leave_minutes"),
            rs.getInt("absence_minutes"),
            rs.getInt("work_minutes"),
            rs.getBoolean("exception_flag"),
            localDateTime(rs, "calculated_at")
        );
    }

    private AttendanceExceptionRow mapException(ResultSet rs, int rowNum) throws SQLException {
        return new AttendanceExceptionRow(
            rs.getLong("id"),
            rs.getLong("daily_result_id"),
            rs.getLong("user_id"),
            rs.getDate("work_date").toLocalDate(),
            rs.getString("exception_type"),
            rs.getString("exception_desc"),
            rs.getString("status")
        );
    }

    private LocalDateTime queryPunchAt(String sql, Long userId, LocalDate workDate) {
        List<LocalDateTime> rows = jdbcTemplate.query(sql,
            (rs, rowNum) -> rs.getTimestamp("punch_at").toLocalDateTime(),
            userId,
            Date.valueOf(workDate)
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    private LocalTime localTime(ResultSet rs, String column) throws SQLException {
        Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    private LocalDate localDate(ResultSet rs, String column) throws SQLException {
        Date value = rs.getDate(column);
        return value == null ? null : value.toLocalDate();
    }

    private LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private LocalDate normalizeOpenEnd(LocalDate effectiveTo) {
        return effectiveTo == null ? LocalDate.of(9999, 12, 31) : effectiveTo;
    }
}
