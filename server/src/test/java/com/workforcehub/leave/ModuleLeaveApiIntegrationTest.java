package com.workforcehub.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.leave.repository.LeaveRepository;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-leave 第一版假期账户接口自动化冒烟。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleLeaveApiIntegrationTest extends LocalIntegrationTestSupport {

    private static final long EMPLOYEE_USER_ID = 2003L;
    private static final long MANAGER_USER_ID = 2002L;
    private static final long ADMIN_USER_ID = 8001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LeaveRepository leaveRepository;

    @Test
    @DisplayName("后台应能创建假种、调整员工余额，员工应能查询自己的假期账户")
    void adminShouldCreateLeaveTypeAdjustBalanceAndEmployeeShouldQueryOwnAccount() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee();
        String suffix = uniqueSuffix();

        MvcResult typeResult = mockMvc.perform(post("/api/admin/leave/types")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaveCode": "ANNUAL_%s",
                      "leaveName": "年假-%s",
                      "balanceMode": "DEDUCT",
                      "status": "ACTIVE"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.leaveTypeId").isString())
            .andExpect(jsonPath("$.data.leaveCode").value("ANNUAL_" + suffix))
            .andExpect(jsonPath("$.data.leaveName").value("年假-" + suffix))
            .andExpect(jsonPath("$.data.balanceMode").value("DEDUCT"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andReturn();

        long leaveTypeId = data(typeResult).get("leaveTypeId").asLong();

        mockMvc.perform(get("/api/admin/leave/types")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)]", leaveTypeId).value(hasSize(1)))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].leaveCode", leaveTypeId).value("ANNUAL_" + suffix))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].leaveName", leaveTypeId).value("年假-" + suffix))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].balanceMode", leaveTypeId).value("DEDUCT"))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].status", leaveTypeId).value("ACTIVE"));

        mockMvc.perform(post("/api/admin/leave/accounts/adjustments")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": %d,
                      "leaveTypeId": %d,
                      "changeMinutes": 960,
                      "reason": "初始化两天年假",
                      "sourceTxnKey": "init-%s"
                    }
                    """.formatted(EMPLOYEE_USER_ID, leaveTypeId, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonLong("$.data.userId", EMPLOYEE_USER_ID))
            .andExpect(jsonLong("$.data.leaveTypeId", leaveTypeId))
            .andExpect(jsonPath("$.data.availableMinutes").value(960))
            .andExpect(jsonPath("$.data.lastTxnType").value("ADJUST"))
            .andExpect(jsonPath("$.data.lastChangeMinutes").value(960));

        mockMvc.perform(get("/api/employee/leave/accounts")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)]", leaveTypeId).value(hasSize(1)))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].leaveCode", leaveTypeId).value("ANNUAL_" + suffix))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].availableMinutes", leaveTypeId).value(960))
            .andExpect(jsonPath("$.data[?(@.leaveTypeId==%d)].frozenMinutes", leaveTypeId).value(0));

        mockMvc.perform(get("/api/admin/leave/accounts")
                .cookie(adminSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID))
                .param("leaveTypeId", String.valueOf(leaveTypeId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonLong("$.data[0].userId", EMPLOYEE_USER_ID))
            .andExpect(jsonLong("$.data[0].leaveTypeId", leaveTypeId))
            .andExpect(jsonPath("$.data[0].availableMinutes").value(960));

        mockMvc.perform(get("/api/admin/leave/accounts")
                .cookie(employeeSession)
                .param("userId", String.valueOf(EMPLOYEE_USER_ID)))
            .andExpect(status().isForbidden());

        assertEquals(1L, accountCount(EMPLOYEE_USER_ID, leaveTypeId));
        assertEquals(1L, txnCount(EMPLOYEE_USER_ID, leaveTypeId, "ADJUST", 960));
    }

    @Test
    @DisplayName("余额更新应按读取到的version收口")
    void accountBalanceUpdateShouldRejectStaleVersion() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long leaveTypeId = createLeaveType(adminSession, "CAS_" + suffix, "版本测试-" + suffix);
        adjustBalance(adminSession, leaveTypeId, 480, "cas-init-" + suffix);
        long accountId = accountId(EMPLOYEE_USER_ID, leaveTypeId);
        int version = accountVersion(accountId);

        BizException exception = assertThrows(
            BizException.class,
            () -> leaveRepository.updateAccountBalance(accountId, 960, ADMIN_USER_ID, version + 1)
        );

        assertEquals("假期账户已被其他事务更新", exception.getMessage());
        assertEquals(480, availableMinutes(EMPLOYEE_USER_ID, leaveTypeId));
    }

    @Test
    @DisplayName("后台重复使用同一余额调整幂等键时不应重复写流水")
    void repeatedAdjustmentSourceTxnKeyShouldBeIdempotent() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        long leaveTypeId = createLeaveType(adminSession, "SICK_" + suffix, "病假-" + suffix);

        adjustBalance(adminSession, leaveTypeId, 480, "same-key-" + suffix);
        adjustBalance(adminSession, leaveTypeId, 480, "same-key-" + suffix);

        assertEquals(480, availableMinutes(EMPLOYEE_USER_ID, leaveTypeId));
        assertEquals(1L, txnCount(EMPLOYEE_USER_ID, leaveTypeId, "ADJUST", 480));
    }

    @Test
    @DisplayName("后台余额调整幂等键被不同操作参数复用时应拒绝")
    void adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        String sourceTxnKey = "fingerprint-" + suffix;
        long annualLeaveTypeId = createLeaveType(adminSession, "ANNUAL_FP_" + suffix, "年假指纹-" + suffix);
        long sickLeaveTypeId = createLeaveType(adminSession, "SICK_FP_" + suffix, "病假指纹-" + suffix);

        adjustBalance(adminSession, annualLeaveTypeId, 480, sourceTxnKey);

        adjustBalance(adminSession, EMPLOYEE_USER_ID, annualLeaveTypeId, 960, sourceTxnKey, 409);
        adjustBalance(adminSession, MANAGER_USER_ID, annualLeaveTypeId, 480, sourceTxnKey, 409);
        adjustBalance(adminSession, EMPLOYEE_USER_ID, sickLeaveTypeId, 480, sourceTxnKey, 409);

        assertEquals(480, availableMinutes(EMPLOYEE_USER_ID, annualLeaveTypeId));
        assertEquals(0, availableMinutes(MANAGER_USER_ID, annualLeaveTypeId));
        assertEquals(0, availableMinutes(EMPLOYEE_USER_ID, sickLeaveTypeId));
        assertEquals(1L, txnCount(EMPLOYEE_USER_ID, annualLeaveTypeId, "ADJUST", 480));
        assertEquals(0L, accountCount(MANAGER_USER_ID, annualLeaveTypeId));
        assertEquals(0L, accountCount(EMPLOYEE_USER_ID, sickLeaveTypeId));
    }

    private long createLeaveType(Cookie adminSession, String leaveCode, String leaveName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/leave/types")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaveCode": "%s",
                      "leaveName": "%s",
                      "balanceMode": "DEDUCT",
                      "status": "ACTIVE"
                    }
                    """.formatted(leaveCode, leaveName)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return data(result).get("leaveTypeId").asLong();
    }

    private void adjustBalance(Cookie adminSession,
                               long leaveTypeId,
                               int changeMinutes,
                               String sourceTxnKey) throws Exception {
        adjustBalance(adminSession, EMPLOYEE_USER_ID, leaveTypeId, changeMinutes, sourceTxnKey, 0);
    }

    private void adjustBalance(Cookie adminSession,
                               long userId,
                               long leaveTypeId,
                               int changeMinutes,
                               String sourceTxnKey,
                               int expectedCode) throws Exception {
        mockMvc.perform(post("/api/admin/leave/accounts/adjustments")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": %d,
                      "leaveTypeId": %d,
                      "changeMinutes": %d,
                      "reason": "幂等调整",
                      "sourceTxnKey": "%s"
                    }
                    """.formatted(userId, leaveTypeId, changeMinutes, sourceTxnKey)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(expectedCode));
    }

    private Cookie loginAdmin() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "admin.demo",
                      "password": "Admin@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "后台登录后应返回 SESSION");
        return session;
    }

    private Cookie loginEmployee() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/employee/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "zhangsan.employee",
                      "password": "Employee@123456"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "员工登录后应返回 SESSION");
        return session;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private long accountCount(long userId, long leaveTypeId) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Long.class, userId, leaveTypeId);
        return count == null ? 0L : count;
    }

    private long txnCount(long userId, long leaveTypeId, String txnType, int changeMinutes) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM lv_account_txn lat
            JOIN lv_account la ON la.id = lat.account_id
            WHERE la.user_id = ?
              AND la.leave_type_id = ?
              AND lat.txn_type = ?
              AND lat.change_minutes = ?
            """, Long.class, userId, leaveTypeId, txnType, changeMinutes);
        return count == null ? 0L : count;
    }

    private int availableMinutes(long userId, long leaveTypeId) {
        Integer minutes = jdbcTemplate.queryForObject("""
            SELECT COALESCE((
                SELECT available_minutes
                FROM lv_account
                WHERE user_id = ? AND leave_type_id = ?
                LIMIT 1
            ), 0)
            """, Integer.class, userId, leaveTypeId);
        return minutes == null ? 0 : minutes;
    }

    private long accountId(long userId, long leaveTypeId) {
        Long accountId = jdbcTemplate.queryForObject("""
            SELECT id
            FROM lv_account
            WHERE user_id = ? AND leave_type_id = ?
            """, Long.class, userId, leaveTypeId);
        return accountId == null ? 0L : accountId;
    }

    private int accountVersion(long accountId) {
        Integer version = jdbcTemplate.queryForObject("""
            SELECT version
            FROM lv_account
            WHERE id = ?
            """, Integer.class, accountId);
        return version == null ? 0 : version;
    }

    private String uniqueSuffix() {
        String raw = String.valueOf(System.nanoTime());
        return raw.substring(raw.length() - 6);
    }
}
