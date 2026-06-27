package com.workforcehub.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * module-org 第一批查询接口自动化冒烟
 * 覆盖当前已确认通过的 P0 主链路与关键边界：
 * - 健康检查
 * - 后台 / 员工登录
 * - 组织查询主路径
 * - 跨端身份隔离
 * - 参数校验
 * - manager / subordinates 空值语义
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class ModuleOrgApiIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("健康探针应可匿名访问且返回 UP")
    void healthEndpointsShouldBeUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("未登录访问后台接口应返回 401 并回写 requestId")
    void unauthenticatedAdminRequestShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/admin/departments/tree")
                .header("X-Request-Id", "test-unauth-001"))
            .andExpect(status().isUnauthorized())
            .andExpect(header().string("X-Request-Id", "test-unauth-001"))
            .andExpect(jsonPath("$.code").value(401))
            .andExpect(jsonPath("$.message").value("未登录或会话已过期"))
            .andExpect(jsonPath("$.requestId").value("test-unauth-001"));
    }

    @Test
    @DisplayName("后台登录后应能查询组织树与员工直属上级")
    void adminShouldLoginAndQueryDirectory() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(get("/api/admin/departments/tree")
                .cookie(adminSession)
                .header("X-Request-Id", "test-admin-tree-001"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "test-admin-tree-001"))
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].deptCode").value("HQ"))
            .andExpect(jsonPath("$.data[0].children[0].deptCode").value("RD"))
            .andExpect(jsonPath("$.data[0].children[0].leaderName").value("李四"))
            .andExpect(jsonPath("$.data[0].children[0].children[0].deptCode").value("RD-BE"));

        mockMvc.perform(get("/api/admin/users")
                .cookie(adminSession)
                .param("pageNo", "1")
                .param("pageSize", "20")
                .param("keyword", "张"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.list[0].userId").value(2003))
            .andExpect(jsonPath("$.data.list[0].realName").value("张三"));

        mockMvc.perform(get("/api/admin/users/2003/manager")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2002))
            .andExpect(jsonPath("$.data.realName").value("李四"));

        mockMvc.perform(get("/api/admin/users/2003")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.employeeNo").value("E1003"))
            .andExpect(jsonPath("$.data.mobile").value("13800000003"))
            .andExpect(jsonPath("$.data.email").value("zhangsan@example.com"));

        mockMvc.perform(get("/api/admin/users/org-chart")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].userId", hasItems(2002, 2003, 2004)))
            .andExpect(jsonPath("$.data[?(@.userId == 2003)].primaryDeptId").value(hasItems(111)));
    }

    @Test
    @DisplayName("后台应能新增并删除空部门")
    void adminShouldCreateAndDeleteEmptyDepartment() throws Exception {
        Cookie adminSession = loginAdmin();
        String suffix = String.valueOf(System.currentTimeMillis());
        MvcResult created = mockMvc.perform(post("/api/admin/departments")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "parentId": 111,
                      "deptCode": "TMP_%s",
                      "deptName": "临时部门%s",
                      "deptType": "TEAM",
                      "status": "ACTIVE"
                    }
                    """.formatted(suffix, suffix)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        JsonNode payload = objectMapper.readTree(created.getResponse().getContentAsString());
        long deptId = payload.get("data").asLong();

        mockMvc.perform(delete("/api/admin/departments/{deptId}", deptId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(delete("/api/admin/departments/{deptId}", deptId)
                .cookie(adminSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(404))
            .andExpect(jsonPath("$.message").value("部门不存在"));
    }

    @Test
    @DisplayName("员工登录后应能查询本人、直属上级与空下级语义")
    void employeeShouldQueryProfileManagerAndEmptySubordinates() throws Exception {
        Cookie employeeSession = loginEmployee("zhangsan.employee", "Employee@123456", "test-emp-login-001");

        mockMvc.perform(get("/api/employee/me/profile")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2003))
            .andExpect(jsonPath("$.data.realName").value("张三"))
            .andExpect(jsonPath("$.data.primaryDeptName").value("后端组"));

        mockMvc.perform(get("/api/employee/me/manager")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.userId").value(2002))
            .andExpect(jsonPath("$.data.realName").value("李四"));

        mockMvc.perform(get("/api/employee/me/subordinates")
                .cookie(employeeSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("领导员工与后台端应满足下级非空和 manager=null 语义")
    void managerAndAdminShouldObserveNullAndNonEmptySemantics() throws Exception {
        Cookie managerSession = loginEmployee("lisi.manager", "Employee@123456", "test-manager-login-001");
        Cookie adminSession = loginAdmin();

        mockMvc.perform(get("/api/employee/me/subordinates")
                .cookie(managerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].userId", hasItems(2003, 2004)))
            .andExpect(jsonPath("$.data[*].managerUserId", everyItem(is(2002))));

        mockMvc.perform(get("/api/admin/users/2002/manager")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data").value(nullValue()));
    }

    @Test
    @DisplayName("跨端访问应返回 403，参数越界应返回 400")
    void pathIsolationAndValidationShouldWork() throws Exception {
        Cookie adminSession = loginAdmin();
        Cookie employeeSession = loginEmployee("zhangsan.employee", "Employee@123456", "test-emp-login-002");

        mockMvc.perform(get("/api/admin/users")
                .cookie(employeeSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message", containsString("管理员身份")));

        mockMvc.perform(get("/api/employee/me/profile")
                .cookie(adminSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403))
            .andExpect(jsonPath("$.message", containsString("员工身份")));

        mockMvc.perform(get("/api/admin/users")
                .cookie(adminSession)
                .param("pageNo", "0")
                .param("pageSize", "20"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value("pageNo 最小为 1"));
    }

    private Cookie loginAdmin() throws Exception {
        return login("/api/admin/auth/login", "admin.demo", "Admin@123456", "test-admin-login-001");
    }

    private Cookie loginEmployee(String loginName, String password, String requestId) throws Exception {
        return login("/api/employee/auth/login", loginName, password, requestId);
    }

    private Cookie login(String path, String loginName, String password, String requestId) throws Exception {
        MvcResult result = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Request-Id", requestId)
                .content("""
                    {
                      "loginName": "%s",
                      "password": "%s"
                    }
                    """.formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", requestId))
            .andExpect(cookie().exists("SESSION"))
            .andExpect(jsonPath("$.code").value(0))
            .andReturn();

        Cookie session = result.getResponse().getCookie("SESSION");
        assertNotNull(session, "登录后应返回 SESSION Cookie");
        return session;
    }
}
