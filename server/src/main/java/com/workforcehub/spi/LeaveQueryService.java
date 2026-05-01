package com.workforcehub.spi;

import java.util.List;
import java.util.Map;

/**
 * 假期余额查询接口
 * 由 module-leave 实现，module-agent 通过 SPI 调用
 */
public interface LeaveQueryService {

    /**
     * 查询用户各假种余额
     *
     * @param userId 用户ID
     * @return 余额列表，每项含 leaveName, availableMinutes 等
     */
    List<Map<String, Object>> getLeaveBalances(Long userId);

    /**
     * 查询指定假种余额
     *
     * @param userId 用户ID
     * @param leaveTypeId 假种ID
     * @return 可用余额(分钟)，不存在返回 0
     */
    int getAvailableMinutes(Long userId, Long leaveTypeId);
}
