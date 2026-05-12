package com.workforcehub.agent.model;

import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring AI OpenAI 兼容 ChatClient 工厂。
 */
@Component
public class SpringAiOpenAiChatClientFactory {

    public OpenAiChatClient create(ResolvedAgentModelConfig config) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build()
        );
        requestFactory.setReadTimeout(config.timeout());
        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestFactory(requestFactory);
        OpenAiApi openAiApi = new OpenAiApi(
            config.baseUrl(),
            config.apiKey(),
            restClientBuilder,
            RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER
        );
        OpenAiChatOptions options = OpenAiChatOptions.builder()
            .withModel(config.model())
            .withMaxTokens(config.maxTokens())
            .withTemperature(config.temperature().floatValue())
            .build();
        return new OpenAiChatClient(openAiApi, options);
    }

    public record ResolvedAgentModelConfig(
        String baseUrl,
        String apiKey,
        String model,
        Integer maxTokens,
        Double temperature,
        Duration timeout
    ) {
    }
}
