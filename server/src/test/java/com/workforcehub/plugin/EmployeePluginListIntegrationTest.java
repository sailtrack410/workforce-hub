package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.support.LocalIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class EmployeePluginListIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper mapper;

    private static final String SRI_A = "sha384-" + "a".repeat(64);
    private static final String SRI_B = "sha384-" + "b".repeat(64);
    private static final String SRI_C = "sha384-" + "c".repeat(64);
    private static final String SRI_D = "sha384-" + "d".repeat(64);
    private static final String SRI_E = "sha384-" + "e".repeat(64);
    private static final String SRI_F = "sha384-" + "f".repeat(64);
    private static final String SRI_K = "sha384-" + "k".repeat(64);
    private static final String SRI_L = "sha384-" + "l".repeat(64);
    private static final String SRI_M = "sha384-" + "m".repeat(64);

    @BeforeEach
    void seed() {
        jdbc.execute("DELETE FROM sys_plugin_visibility");
        jdbc.execute("DELETE FROM sys_plugin");

        // ACTIVE UI 插件 — 各范围
        insPlugin("leave-ext",    "假期扩展",    SRI_A);
        insPlugin("attend-ext",   "考勤扩展",    SRI_B);
        insPlugin("dept-plugin",  "部门插件",    SRI_C);
        insPlugin("pos-plugin",   "岗位插件",    SRI_D);
        insPlugin("mgr-plugin",   "经理插件",    SRI_E);
        insPlugin("multi-scope",  "多范围插件",  SRI_F);
        insPlugin("evil-entry",   "恶意外部入口", SRI_K);

        // 应被排除的插件
        insPluginFull("stopped", "已停用",  "sha384-" + "g".repeat(64), "STOPPED",    "UI");
        insPluginFull("func",    "功能插件", "sha384-" + "h".repeat(64), "ACTIVE",     "FUNCTIONAL");
        insPluginFull("BAD_ID",  "非法ID",  "sha384-" + "i".repeat(64), "ACTIVE",     "UI");
        insPluginFull("bad-sri", "非法SRI", "sha256-" + "j".repeat(64), "ACTIVE",     "UI");

        insPlugin("user-nomatch",   "不匹配USER",    SRI_L);
        insPlugin("dept-nomatch",   "不匹配DEPT",    SRI_L);
        insPlugin("pos-nomatch",    "不匹配POSITION", SRI_L);
        insPlugin("unknown-scope",  "未知范围",       SRI_M);

        // 可见性
        insVis("leave-ext",   "ALL",      null);
        insVis("attend-ext",  "USER",     "2003");
        insVis("dept-plugin", "DEPT",     "111");
        insVis("pos-plugin",  "POSITION", "3001");
        insVis("mgr-plugin",  "MANAGER",  null);
        insVis("multi-scope", "ALL",      null);
        insVis("multi-scope", "USER",     "2003");
        insVis("stopped",     "ALL",      null);
        insVis("func",        "ALL",      null);
        insVis("BAD_ID",      "ALL",      null);
        insVis("bad-sri",     "ALL",      null);
        insVis("user-nomatch",  "USER",     "9999");
        insVis("dept-nomatch",  "DEPT",     "999");
        insVis("pos-nomatch",   "POSITION", "9999");
        insVis("unknown-scope", "UNKNOWN",  "x");

        jdbc.update("UPDATE sys_plugin SET frontend_entry = ? WHERE plugin_id = ?",
            "https://evil.invalid/x.js", "evil-entry");
        insVis("evil-entry", "ALL", null);
    }

    @Test
    void anonymous401() throws Exception {
        mockMvc.perform(get("/api/employee/plugins/mine"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOnEmployeePath403() throws Exception {
        Cookie admin = login("admin.demo", "Admin@123456", "/api/admin/auth/login");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(admin))
            .andExpect(status().isForbidden());
    }

    @Test
    void zhangsanSeesMatchingPlugins() throws Exception {
        Cookie s = login("zhangsan.employee", "Employee@123456", "/api/employee/auth/login");
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andReturn());

        List<String> ids = ids(data);
        assertEquals(6, ids.size(), "应恰好命中 6 个插件");
        assertTrue(ids.containsAll(List.of(
            "leave-ext", "attend-ext", "dept-plugin", "pos-plugin", "multi-scope", "evil-entry")));
        assertFalse(ids.contains("mgr-plugin"), "张三非经理");
        // multi-scope 不重复
        assertEquals(1, ids.stream().filter("multi-scope"::equals).count(), "multi-scope 不重复");

        assertFalse(ids.contains("user-nomatch"), "不匹配 USER");
        assertFalse(ids.contains("dept-nomatch"), "不匹配 DEPT");
        assertFalse(ids.contains("pos-nomatch"), "不匹配 POSITION");
        assertFalse(ids.contains("unknown-scope"), "UNKNOWN 范围");
        assertFalse(ids.contains("stopped"), "STOPPED");
        assertFalse(ids.contains("func"), "FUNCTIONAL");
        assertFalse(ids.contains("BAD_ID"), "非法 id");
        assertFalse(ids.contains("bad-sri"), "非法 integrity");

        // 验证 frontendEntry 固定同源，不透传数据库 frontend_entry
        for (JsonNode item : data) {
            String id = item.get("id").asText();
            assertEquals("/plugins/" + id + "/index.js", item.get("frontendEntry").asText());
            assertEquals(4, item.size(), "只有 4 个字段");
            assertFalse(item.has("jarPath"));
            assertFalse(item.has("status"));
        }
    }

    @Test
    void lisiSeesManagerScope() throws Exception {
        // 李四 (2002) 有直属下属 2003/2004
        Cookie s = login("lisi.manager", "Employee@123456", "/api/employee/auth/login");
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andReturn());
        assertTrue(ids(data).contains("mgr-plugin"), "李四是经理");
    }

    @Test
    void managerScopeRequiresValidSubordinatesWhenNoDeptLeader() throws Exception {
        Cookie s = login("lisi.manager", "Employee@123456", "/api/employee/auth/login");

        // 临时禁用 department_leader，仅测试下属路径
        jdbc.execute("UPDATE org_department_leader SET status='INACTIVE' WHERE user_id=2002");

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET is_deleted=1 WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET status='DISABLED' WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        restoreSubordinates();
        jdbc.execute("UPDATE org_user SET employment_status='OFFBOARDED' WHERE id IN (2003,2004)");
        assertNoManagerPlugin(s);

        // 恢复
        jdbc.execute("UPDATE org_department_leader SET status='ACTIVE' WHERE user_id=2002");
        restoreSubordinates();
    }

    @Test
    void invalidOrgUserReturnsEmpty() throws Exception {
        Cookie s = login("zhangsan.employee", "Employee@123456", "/api/employee/auth/login");

        jdbc.execute("UPDATE org_user SET is_deleted=1 WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id=2003");
        jdbc.execute("UPDATE org_user SET status='DISABLED' WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));

        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id=2003");
        jdbc.execute("UPDATE org_user SET employment_status='OFFBOARDED' WHERE id=2003");
        mockMvc.perform(get("/api/employee/plugins/mine").cookie(s))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(0));
    }

    // --- helpers ---

    private void restoreSubordinates() {
        jdbc.execute("UPDATE org_user SET is_deleted=0,status='ACTIVE',employment_status='ACTIVE' WHERE id IN (2003,2004)");
    }

    private void assertNoManagerPlugin(Cookie session) throws Exception {
        JsonNode data = data(mockMvc.perform(get("/api/employee/plugins/mine").cookie(session))
            .andExpect(status().isOk())
            .andReturn());
        assertFalse(ids(data).contains("mgr-plugin"),
            "下属全部无效且无部门负责人时不应命中 MANAGER");
    }

    private void insPlugin(String id, String name, String sri) {
        insPluginFull(id, name, sri, "ACTIVE", "UI");
    }

    private void insPluginFull(String id, String name, String sri, String status, String type) {
        jdbc.update(
            "INSERT INTO sys_plugin (plugin_id,name,version,type,status,jar_path,frontend_sha384) VALUES (?,?,'1.0.0',?,?,?,?)",
            id, name, type, status, "/tmp/" + id + ".jar", sri
        );
    }

    private void insVis(String pluginId, String scopeType, String scopeValue) {
        jdbc.update(
            "INSERT INTO sys_plugin_visibility (plugin_id,scope_type,scope_value) VALUES (?,?,?)",
            pluginId, scopeType, scopeValue
        );
    }

    private Cookie login(String loginName, String password, String path) throws Exception {
        MvcResult r = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginName\":\"%s\",\"password\":\"%s\"}".formatted(loginName, password)))
            .andExpect(status().isOk())
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return r.getResponse().getCookie("SESSION");
    }

    private JsonNode data(MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    private List<String> ids(JsonNode data) {
        return data.findValuesAsText("id");
    }
}
