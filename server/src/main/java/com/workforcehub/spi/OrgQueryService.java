package com.workforcehub.spi;

import java.util.Map;

/**
 * 组织信息查询接口
 * 由 module-org 实现，module-agent 通过 SPI 调用
 */
public interface OrgQueryService {

    /**
     * 查询员工基本信息摘要
     *
     * @param userId 用户ID
     * @return 员工信息Map(name, dept, position, manager 等)
     */
    Map<String, Object> getUserSummary(Long userId);

    /**
     * 查询部门信息摘要
     *
     * @param deptId 部门ID
     * @return 部门信息Map
     */
    Map<String, Object> getDeptSummary(Long deptId);

    /**
     * 查询用户直属上级
     *
     * @param userId 用户ID
     * @return 上级用户ID，无上级返回 null
     */
    Long getManagerUserId(Long userId);
}
