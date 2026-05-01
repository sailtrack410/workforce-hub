package com.workforcehub.spi;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 考勤查询接口
 * 由 module-attendance 实现，module-agent 通过 SPI 调用
 */
public interface AttendanceQueryService {

    /**
     * 查询用户某月考勤异常记录
     *
     * @param userId 用户ID
     * @param yearMonth 年月，格式 2026-05
     * @return 异常记录列表
     */
    List<Map<String, Object>> getMonthlyExceptions(Long userId, String yearMonth);

    /**
     * 查询用户某日考勤结果
     *
     * @param userId 用户ID
     * @param workDate 工作日期
     * @return 考勤结果Map，无记录返回 null
     */
    Map<String, Object> getDailyResult(Long userId, LocalDate workDate);
}
