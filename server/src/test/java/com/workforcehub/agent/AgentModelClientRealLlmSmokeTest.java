package com.workforcehub.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.agent.model.AgentModelProperties;
import com.workforcehub.agent.model.AgentModelRequest;
import com.workforcehub.agent.model.AgentModelResponse;
import com.workforcehub.agent.model.OpenAiCompatibleAgentModelClient;
import com.workforcehub.agent.model.SpringAiOpenAiChatClientFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.convert.DurationStyle;

import java.time.Duration;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 LLM OpenAI 兼容服务冒烟测试。
 *
 * 默认跳过，避免日常回归消耗真实额度。需要真实联调时显式设置：
 * AGENT_MODEL_SMOKE_ENABLED=true
 */
@EnabledIfEnvironmentVariable(named = "AGENT_MODEL_SMOKE_ENABLED", matches = "true")
class AgentModelClientRealLlmSmokeTest {

    @Test
    @DisplayName("真实OpenAI兼容模型服务应能通过Spring AI返回回复")
    void realOpenAiCompatibleModelShouldReplyThroughSpringAi() {
        AgentModelProperties properties = new AgentModelProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(requiredEnv("AGENT_MODEL_BASE_URL"));
        properties.setApiKey(requiredEnv("AGENT_MODEL_API_KEY"));
        properties.setModel(requiredEnv("AGENT_MODEL_NAME"));
        properties.setTimeout(parseDuration(env("AGENT_MODEL_TIMEOUT", "30s")));
        properties.setMaxTokens(Integer.parseInt(env("AGENT_MODEL_MAX_TOKENS", "32")));
        properties.setTemperature(Double.parseDouble(env("AGENT_MODEL_TEMPERATURE", "0.1")));

        OpenAiCompatibleAgentModelClient client = new OpenAiCompatibleAgentModelClient(
            properties,
            new ObjectMapper(),
            new SpringAiOpenAiChatClientFactory()
        );

        AgentModelResponse response = client.generateReply(new AgentModelRequest(
            1L,
            1L,
            "真实模型冒烟",
            "你是接口连通性测试助手。只回复 OK 两个字母，不要输出其他内容。",
            "只回复 OK",
            null
        ));

        assertFalse(response.text().isBlank(), "真实模型应返回非空文本");
        assertTrue(
            response.text().toUpperCase(Locale.ROOT).contains("OK"),
            "真实模型应按连通性提示返回 OK"
        );
    }

    private static String requiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 未配置，无法执行真实 LLM 冒烟测试");
        }
        return value;
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static Duration parseDuration(String value) {
        return DurationStyle.detectAndParse(value);
    }
}
