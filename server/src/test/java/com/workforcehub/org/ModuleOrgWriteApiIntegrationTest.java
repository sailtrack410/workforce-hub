package com.workforcehub.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.auth.domain.AuthUserAccount;
import com.workforcehub.auth.mapper.AuthUserAccountMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * module-org 第二批写侧接口自动化冒烟
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleOrgWriteApiIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthUserAccountMapper authUserAccountMapper;

    @Test
    @DisplayName("后台应能新增部门、岗位、员工并通过读接口回查")
    void adminShouldCreateDepartmentPositionAndEmployee() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();

        Long deptId = createResourceAndReadId("/api/admin/departments", adminSession, """
            {
              "parentId": 110,
              "deptCode": "QA-%s",
              "deptName": "测试质量组-%s",
              "deptType": "TEAM",
              "leaderUserId": 2002,
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix));

        mockMvc.perform(get("/api/admin/departments/tree")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[0].children[0].children[?(@.id==%s)].deptCode"
                .formatted(deptId)).value("QA-" + suffix));

        Long positionId = createResourceAndReadId("/api/admin/positions", adminSession, """
            {
              "positionCode": "QA_ENGINEER_%s",
              "positionName": "测试工程师-%s",
              "positionCategory": "ENGINEER",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix));

        mockMvc.perform(get("/api/admin/positions")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.id==%s)].positionCode"
                .formatted(positionId)).value("QA_ENGINEER_" + suffix));

        Long managerUserId = createManagerUser(adminSession, suffix, "A");

        Long userId = createResourceAndReadId("/api/admin/users", adminSession, """
            {
              "employeeNo": "E9%s",
              "realName": "测试员工%s",
              "gender": "FEMALE",
              "mobile": "1390000%s",
              "primaryDeptId": %s,
              "positionId": %s,
              "managerUserId": %s,
              "employmentStatus": "PENDING_ONBOARD",
              "hireDate": "2026-05-02",
              "workLocation": "杭州",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix, suffix, deptId, positionId, managerUserId));

        mockMvc.perform(get("/api/admin/users/{userId}", userId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.employeeNo").value("E9" + suffix))
            .andExpect(jsonPath("$.data.primaryDeptId").value(deptId))
            .andExpect(jsonPath("$.data.positionId").value(positionId))
            .andExpect(jsonPath("$.data.managerUserId").value(managerUserId))
            .andExpect(jsonPath("$.data.serviceStartDate").value("2026-05-02"))
            .andExpect(jsonPath("$.data.annualLeaveStartDate").value("2026-05-02"));
    }

    @Test
    @DisplayName("员工生命周期与账号启停规则应联动生效")
    void employmentLifecycleShouldDriveAccountStatus() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        Long managerUserId = createManagerUser(adminSession, suffix, "B");

        Long userId = createResourceAndReadId("/api/admin/users", adminSession, """
            {
              "employeeNo": "E8%s",
              "realName": "待入职员工%s",
              "gender": "MALE",
              "mobile": "1370000%s",
              "primaryDeptId": 111,
              "positionId": 3001,
              "managerUserId": %s,
              "employmentStatus": "PENDING_ONBOARD",
              "hireDate": "2026-05-03",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix, suffix, managerUserId));

        mockMvc.perform(put("/api/admin/users/{userId}/account", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "pending.%s",
                      "newPassword": "Employee@123456",
                      "email": "pending.%s@example.com",
                      "accountStatus": "ACTIVE"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message", containsString("不允许启用登录账号")));

        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "PROBATION"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/{userId}/account", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "loginName": "probation.%s",
                      "newPassword": "Employee@123456",
                      "email": "probation.%s@example.com",
                      "accountStatus": "ACTIVE"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        AuthUserAccount createdAccount = authUserAccountMapper.selectByLoginName("probation." + suffix);
        assertNotNull(createdAccount, "试用期员工应可成功创建启用账号");
        assertEquals("ACTIVE", createdAccount.getAccountStatus());

        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "PENDING_OFFBOARD"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/admin/users/{userId}/employment-status", userId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employmentStatus": "OFFBOARDED"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        AuthUserAccount disabledAccount = authUserAccountMapper.selectByLoginName("probation." + suffix);
        assertNotNull(disabledAccount, "离职后账号仍应存在，供审计与留痕");
        assertEquals("DISABLED", disabledAccount.getAccountStatus());
    }

    @Test
    @DisplayName("部门树循环与上下级循环应被拦截")
    void hierarchyCyclesShouldBeRejected() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = uniqueSuffix();
        Long managerUserId = createManagerUser(adminSession, suffix, "C");

        Long parentDeptId = createResourceAndReadId("/api/admin/departments", adminSession, """
            {
              "parentId": 110,
              "deptCode": "OPS-%s",
              "deptName": "运维组-%s",
              "deptType": "TEAM",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix));
        Long childDeptId = createResourceAndReadId("/api/admin/departments", adminSession, """
            {
              "parentId": %s,
              "deptCode": "OPS-CHILD-%s",
              "deptName": "运维子组-%s",
              "deptType": "TEAM",
              "status": "ACTIVE"
            }
            """.formatted(parentDeptId, suffix, suffix));

        mockMvc.perform(put("/api/admin/departments/{deptId}", parentDeptId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "parentId": %s,
                      "deptCode": "OPS-%s",
                      "deptName": "运维组-%s",
                      "deptType": "TEAM",
                      "status": "ACTIVE"
                    }
                    """.formatted(childDeptId, suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message", containsString("子部门")));

        Long userAId = createResourceAndReadId("/api/admin/users", adminSession, """
            {
              "employeeNo": "E7%s",
              "realName": "循环A%s",
              "primaryDeptId": 111,
              "positionId": 3001,
              "managerUserId": %s,
              "employmentStatus": "ACTIVE",
              "hireDate": "2026-05-04",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix, managerUserId));
        Long userBId = createResourceAndReadId("/api/admin/users", adminSession, """
            {
              "employeeNo": "E6%s",
              "realName": "循环B%s",
              "primaryDeptId": 111,
              "positionId": 3001,
              "managerUserId": %s,
              "employmentStatus": "ACTIVE",
              "hireDate": "2026-05-04",
              "status": "ACTIVE"
            }
            """.formatted(suffix, suffix, userAId));

        mockMvc.perform(put("/api/admin/users/{userId}", userAId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "employeeNo": "E7%s",
                      "realName": "循环A%s",
                      "primaryDeptId": 111,
                      "positionId": 3001,
                      "managerUserId": %s,
                      "hireDate": "2026-05-04",
                      "status": "ACTIVE"
                    }
                    """.formatted(suffix, suffix, userBId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message", containsString("下级")));
    }

    private Long createResourceAndReadId(String path, Cookie session, String body) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();
        return readDataAsLong(result);
    }

    private Long readDataAsLong(MvcResult result) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").asLong();
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

    private Long createManagerUser(Cookie session, String suffix, String marker) throws Exception {
        return createResourceAndReadId("/api/admin/users", session, """
            {
              "employeeNo": "M%s%s",
              "realName": "临时经理%s%s",
              "primaryDeptId": 110,
              "positionId": 3002,
              "employmentStatus": "ACTIVE",
              "hireDate": "2026-05-01",
              "status": "ACTIVE"
            }
            """.formatted(marker, suffix, marker, suffix));
    }

    private String uniqueSuffix() {
        String raw = String.valueOf(System.nanoTime());
        return raw.substring(raw.length() - 6);
    }
}
