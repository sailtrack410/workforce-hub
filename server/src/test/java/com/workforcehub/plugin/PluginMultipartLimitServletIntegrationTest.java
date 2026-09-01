package com.workforcehub.plugin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.plugin.controller.admin.AdminPluginController;
import com.workforcehub.plugin.runtime.AdminPluginManagementService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.HttpEncodingAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.util.unit.DataSize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = PluginMultipartLimitServletIntegrationTest.TestApplication.class,
    properties = {
        "spring.config.name=plugin-multipart-limit-isolated",
        "spring.servlet.multipart.max-file-size=1KB",
        "spring.servlet.multipart.max-request-size=2KB",
        "spring.main.banner-mode=off"
    }
)
class PluginMultipartLimitServletIntegrationTest {

    private static final long MAX_TEST_FILE_BYTES = 1024;
    private static Path servletTempDir;

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminPluginManagementService service;

    @TempDir
    Path tempDir;

    @DynamicPropertySource
    static void multipartTempDirectory(DynamicPropertyRegistry registry) {
        try {
            servletTempDir = Files.createTempDirectory("workforce-plugin-multipart-limit-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        registry.add("spring.servlet.multipart.location", servletTempDir::toString);
    }

    @AfterAll
    static void deleteMultipartTempDirectory() throws IOException {
        Files.deleteIfExists(servletTempDir);
    }

    @Test
    void realServletShouldReturnUnified413ForOversizedMultipartFile() throws Exception {
        Path oversizedJar = tempDir.resolve("oversized-plugin.jar");
        try (FileChannel channel = FileChannel.open(
                oversizedJar,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            channel.position(MAX_TEST_FILE_BYTES);
            channel.write(ByteBuffer.wrap(new byte[] {1}));
        }

        String boundary = "----workforce-plugin-upload-limit";
        byte[] prefix = ("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"jar\"; filename=\"plugin.jar\"\r\n"
            + "Content-Type: application/java-archive\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n")
            .getBytes(StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/plugins/upload"))
            .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.concat(
                HttpRequest.BodyPublishers.ofByteArray(prefix),
                HttpRequest.BodyPublishers.ofFile(oversizedJar),
                HttpRequest.BodyPublishers.ofByteArray(suffix)
            ))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(413, response.statusCode());
        assertEquals(MediaType.APPLICATION_JSON_VALUE,
            response.headers().firstValue("Content-Type").orElseThrow());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(BusinessErrorCode.PAYLOAD_TOO_LARGE.code(), body.get("code").asInt());
        assertEquals("上传文件过大", body.get("message").asText());
        try (var files = Files.list(servletTempDir)) {
            assertTrue(files.findAny().isEmpty());
        }
        verifyNoInteractions(service);
    }

    @Test
    void realServletShouldReturn400ForMultipartWithoutBoundary() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/plugins/upload"))
            .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString("not-a-multipart-body"))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(BusinessErrorCode.BAD_REQUEST.code(), body.get("code").asInt());
        assertEquals("上传请求格式错误", body.get("message").asText());
        verifyNoInteractions(service);
    }

    @Test
    void realServletShouldReturn400ForTruncatedMultipartBody() throws Exception {
        String boundary = "----workforce-plugin-truncated";
        String truncatedBody = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"jar\"; filename=\"plugin.jar\"\r\n"
            + "Content-Type: application/java-archive\r\n\r\n"
            + "truncated";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + port + "/api/admin/plugins/upload"))
            .header("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE + "; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(truncatedBody))
            .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );

        assertEquals(400, response.statusCode());
        JsonNode body = objectMapper.readTree(response.body());
        assertEquals(BusinessErrorCode.BAD_REQUEST.code(), body.get("code").asInt());
        assertEquals("上传请求格式错误", body.get("message").asText());
        verifyNoInteractions(service);
    }

    @Test
    void productionConfigurationShouldKeepPluginUploadLimits() throws IOException {
        var propertySources = new YamlPropertySourceLoader().load(
            "application.yml",
            new ClassPathResource("application.yml")
        );
        MultipartProperties properties = new Binder(
            ConfigurationPropertySources.from(propertySources)
        ).bind("spring.servlet.multipart", MultipartProperties.class)
            .orElseThrow(() -> new IllegalStateException("multipart 配置缺失"));

        assertEquals(DataSize.ofMegabytes(32), properties.getMaxFileSize());
        assertEquals(DataSize.ofMegabytes(33), properties.getMaxRequestSize());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AdminPluginController.class, GlobalExceptionHandler.class})
    @ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        ServletWebServerFactoryAutoConfiguration.class,
        EmbeddedWebServerFactoryCustomizerAutoConfiguration.class,
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        MultipartAutoConfiguration.class,
        HttpEncodingAutoConfiguration.class
    })
    static class TestApplication {
    }
}
