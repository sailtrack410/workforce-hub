package com.workforcehub.common;

import com.workforcehub.support.LocalIntegrationTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 后台数据字典接口集成测试。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("api")
class AdminDictIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDictTestData() {
        // 支撑基类重置业务表时不清理 sys_dict（保留 V58 种子），这里只清测试自建条目并还原被改的种子标签
        jdbcTemplate.update("DELETE FROM sys_dict WHERE dict_type = 'TEST_KIND'");
        jdbcTemplate.update("UPDATE sys_dict SET item_label = '已处理' WHERE dict_type = 'alert_action' AND item_code = 'RESOLVE'");
    }

    @Test
    @DisplayName("后台字典应返回 V58 种子数据并支持状态过滤")
    void adminShouldListSeedDictData() throws Exception {
        Cookie adminSession = loginAdmin();

        mockMvc.perform(get("/api/admin/dicts")
                .cookie(adminSession)
                .param("dictType", "common_status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[*].itemCode", containsInAnyOrder("ACTIVE", "INACTIVE", "PENDING")))
            .andExpect(jsonPath("$.data[?(@.itemCode=='ACTIVE')].itemLabel").value("启用"))
            .andExpect(jsonPath("$.data[?(@.itemCode=='INACTIVE')].itemLabel").value("停用"))
            .andExpect(jsonPath("$.data[?(@.itemCode=='PENDING')].itemLabel").value("待处理"))
            .andExpect(jsonPath("$.data[?(@.itemCode=='ACTIVE')].statusName").value("启用"));

        // 种子条目均为启用，status=0 应查不到数据
        mockMvc.perform(get("/api/admin/dicts")
                .cookie(adminSession)
                .param("dictType", "common_status")
                .param("status", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/admin/dicts/types")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data[?(@.dictType=='common_status')].itemCount").value(3))
            .andExpect(jsonPath("$.data[?(@.dictType=='alert_action')].itemCount").value(3));
    }

    @Test
    @DisplayName("后台应支持字典条目创建、更新、label 生效与删除")
    void adminShouldManageDictItems() throws Exception {
        Cookie adminSession = loginAdmin();

        // 创建条目
        mockMvc.perform(post("/api/admin/dicts")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "dictType": "TEST_KIND",
                      "itemCode": "VIP",
                      "itemLabel": "贵宾",
                      "sortNo": 2,
                      "status": 1,
                      "remark": "集成测试条目"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.dictType").value("TEST_KIND"))
            .andExpect(jsonPath("$.data.itemCode").value("VIP"))
            .andExpect(jsonPath("$.data.itemLabel").value("贵宾"))
            .andExpect(jsonPath("$.data.statusName").value("启用"));

        // 列表可见
        mockMvc.perform(get("/api/admin/dicts")
                .cookie(adminSession)
                .param("dictType", "TEST_KIND"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(1));

        Long itemId = jdbcTemplate.queryForObject(
            "SELECT id FROM sys_dict WHERE dict_type = 'TEST_KIND' AND item_code = 'VIP'",
            Long.class
        );

        // 更新条目
        mockMvc.perform(put("/api/admin/dicts/{id}", itemId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "dictType": "TEST_KIND",
                      "itemCode": "VIP",
                      "itemLabel": "贵宾客户",
                      "sortNo": 5,
                      "status": 1,
                      "remark": "已调整"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.itemLabel").value("贵宾客户"))
            .andExpect(jsonPath("$.data.sortNo").value(5))
            .andExpect(jsonPath("$.data.statusName").value("启用"));

        // 先触发一次告警动作名称翻译（加载 alert_action 标签缓存：ACKNOWLEDGE/RESOLVE/IGNORE）
        mockMvc.perform(post("/api/admin/alerts/AGENT_TASK_FAILED/resolutions")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "actionType": "ACKNOWLEDGE",
                      "comment": "已确认"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.actionType").value("ACKNOWLEDGE"))
            .andExpect(jsonPath("$.data.actionTypeName").value("确认"));

        // label 生效：更新种子 alert_action/RESOLVE 标签后，翻译应取字典新值（内置回退也是"已处理"，
        // 改成不同文案才能证明读取的是字典且缓存已随写操作失效）
        mockMvc.perform(put("/api/admin/dicts/{id}", resolveItemId())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "dictType": "alert_action",
                      "itemCode": "RESOLVE",
                      "itemLabel": "已处理完成",
                      "sortNo": 2,
                      "status": 1
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.itemLabel").value("已处理完成"));

        mockMvc.perform(post("/api/admin/alerts/AGENT_TASK_FAILED/resolutions")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "actionType": "RESOLVE",
                      "comment": "已重试失败任务"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.actionType").value("RESOLVE"))
            .andExpect(jsonPath("$.data.actionTypeName").value("已处理完成"));

        // 写操作应落审计日志（创建 1 条 VIP + 更新 1 条 RESOLVE）
        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(adminSession)
                .param("actionType", "DICT_CREATE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/admin/audit-logs")
                .cookie(adminSession)
                .param("actionType", "DICT_UPDATE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.total").value(2));

        // 删除条目
        mockMvc.perform(delete("/api/admin/dicts/{id}", itemId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        // 删除后列表应为空
        mockMvc.perform(get("/api/admin/dicts")
                .cookie(adminSession)
                .param("dictType", "TEST_KIND"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.length()").value(0));

        // 删除后再更新、再删除应 404
        mockMvc.perform(put("/api/admin/dicts/{id}", itemId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "dictType": "TEST_KIND",
                      "itemCode": "VIP",
                      "itemLabel": "贵宾客户",
                      "sortNo": 5,
                      "status": 1
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404));

        mockMvc.perform(delete("/api/admin/dicts/{id}", itemId)
                .cookie(adminSession))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value(100404));
    }

    private Long resolveItemId() {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM sys_dict WHERE dict_type = 'alert_action' AND item_code = 'RESOLVE'",
            Long.class
        );
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
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(cookie().exists("SESSION"))
            .andReturn();
        return result.getResponse().getCookie("SESSION");
    }
}
