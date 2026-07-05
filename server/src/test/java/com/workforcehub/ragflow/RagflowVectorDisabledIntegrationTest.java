package com.workforcehub.ragflow;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "workforce.ragflow.vector.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("api")
class RagflowVectorDisabledIntegrationTest extends LocalIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("未配置向量库时检索测试应返回明确错误")
    void searchShouldFailWhenVectorDbDisabled() throws Exception {
        Cookie adminSession = loginAdmin();
        long spaceId = createSpace(adminSession);

        mockMvc.perform(post("/api/admin/ragflow/spaces/{spaceId}/search-test", spaceId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "query": "请假制度"
                    }
                    """))
            .andExpect(status().is(422))
            .andExpect(jsonPath("$.code").value(100422))
            .andExpect(jsonPath("$.message").value("向量库未配置"));
    }

    private long createSpace(Cookie adminSession) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/ragflow/spaces")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "spaceName": "向量检索测试",
                      "spaceCode": "VECTOR_SEARCH_DISABLED_TEST",
                      "visibility": "PRIVATE",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.spaceId").isString())
            .andReturn();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build()
            .readTree(result.getResponse().getContentAsString())
            .get("data")
            .get("spaceId")
            .asLong();
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
