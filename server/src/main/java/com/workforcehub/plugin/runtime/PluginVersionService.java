package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从已签名 JAR 的 plugin.json 读取并校验版本兼容性。
 */
@Component
public class PluginVersionService {

    private static final int MAX_PLUGIN_JSON_BYTES = 64 * 1024;
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$"
    );
    private static final Pattern JAVA_VERSION = Pattern.compile("^(0|[1-9]\\d*)$");
    private static final Pattern VALID_SRI = Pattern.compile(
        "^sha384-[A-Za-z0-9+/]{64}$"
    );

    private final ObjectMapper objectMapper;
    private final SemanticVersion hostVersion;
    private final SemanticVersion sdkVersion;

    public PluginVersionService(
            ObjectMapper objectMapper,
            @Value("${workforce.plugin.host-version:0.1.0}") String hostVersion,
            @Value("${workforce.plugin.sdk-version:0.1.0}") String sdkVersion) {
        this.objectMapper = objectMapper;
        this.hostVersion = parseSemanticVersion("宿主版本", hostVersion);
        this.sdkVersion = parseSemanticVersion("SDK版本", sdkVersion);
    }

    public PluginVersionDescriptor checkCompatibility(Path jarPath) {
        JsonNode pluginJson = readPluginJson(jarPath);
        String type = requiredText(pluginJson, "type");
        String provider = requiredText(pluginJson, "provider");
        String frontendSha384 = optionalText(pluginJson, "frontendSha384");
        if (!"UI".equals(type) && !"FUNCTIONAL".equals(type)) {
            throw new PluginVersionException("plugin.json type 仅支持 UI 或 FUNCTIONAL");
        }
        if ("UI".equals(type)
                && (frontendSha384 == null || !VALID_SRI.matcher(frontendSha384).matches())) {
            throw new PluginVersionException("UI 插件必须声明有效 frontendSha384");
        }
        if ("FUNCTIONAL".equals(type) && frontendSha384 != null) {
            throw new PluginVersionException("FUNCTIONAL 插件不得声明 frontendSha384");
        }
        PluginVersionDescriptor descriptor = new PluginVersionDescriptor(
            requiredText(pluginJson, "id"),
            requiredText(pluginJson, "version"),
            requiredText(pluginJson, "sdkVersion"),
            requiredText(pluginJson, "minHostVersion"),
            optionalText(pluginJson, "maxHostVersion"),
            requiredText(pluginJson, "javaVersion"),
            type,
            provider,
            frontendSha384
        );

        parseSemanticVersion("插件版本", descriptor.pluginVersion());
        SemanticVersion pluginSdkVersion = parseSemanticVersion("插件SDK版本", descriptor.sdkVersion());
        SemanticVersion minHostVersion = parseSemanticVersion("最低宿主版本", descriptor.minHostVersion());
        SemanticVersion maxHostVersion = descriptor.maxHostVersion() == null
            ? null
            : parseSemanticVersion("最高宿主版本", descriptor.maxHostVersion());

        if (pluginSdkVersion.major() != sdkVersion.major()
                || sdkVersion.major() == 0 && pluginSdkVersion.minor() != sdkVersion.minor()
                || pluginSdkVersion.compareTo(sdkVersion) > 0) {
            throw new PluginVersionException(
                "插件SDK版本不兼容: " + descriptor.sdkVersion() + "，宿主SDK版本: " + sdkVersion
            );
        }
        if (maxHostVersion != null && minHostVersion.compareTo(maxHostVersion) > 0) {
            throw new PluginVersionException("插件宿主版本范围无效");
        }
        if (hostVersion.compareTo(minHostVersion) < 0
                || maxHostVersion != null && hostVersion.compareTo(maxHostVersion) > 0) {
            throw new PluginVersionException(
                "宿主版本不兼容: " + hostVersion + "，插件要求: "
                    + descriptor.minHostVersion() + "-"
                    + (descriptor.maxHostVersion() == null ? "无上限" : descriptor.maxHostVersion())
            );
        }

        int requiredJavaVersion = parseJavaVersion(descriptor.javaVersion());
        if (requiredJavaVersion > Runtime.version().feature()) {
            throw new PluginVersionException(
                "Java版本不兼容: 需要 " + requiredJavaVersion
                    + "，当前 " + Runtime.version().feature()
            );
        }
        return descriptor;
    }

    private JsonNode readPluginJson(Path jarPath) {
        if (!Files.isRegularFile(jarPath)) {
            throw new PluginVersionException("插件 JAR 不存在或不是普通文件");
        }

        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            List<JarEntry> entries = jarFile.stream()
                .filter(entry -> "plugin.json".equals(entry.getName()))
                .limit(2)
                .toList();
            if (entries.size() != 1 || entries.get(0).isDirectory()) {
                throw new PluginVersionException("插件 JAR 必须包含唯一的 plugin.json");
            }

            JarEntry entry = entries.get(0);
            if (entry.getSize() > MAX_PLUGIN_JSON_BYTES) {
                throw new PluginVersionException("plugin.json 超过 64KB");
            }

            try (InputStream input = jarFile.getInputStream(entry)) {
                byte[] content = input.readNBytes(MAX_PLUGIN_JSON_BYTES + 1);
                if (content.length > MAX_PLUGIN_JSON_BYTES) {
                    throw new PluginVersionException("plugin.json 超过 64KB");
                }
                JsonNode json = objectMapper.readerFor(JsonNode.class)
                    .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(content);
                if (json == null || !json.isObject()) {
                    throw new PluginVersionException("plugin.json 必须是 JSON 对象");
                }
                return json;
            }
        } catch (IOException e) {
            throw new PluginVersionException("读取 plugin.json 失败", e);
        }
    }

    private String requiredText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new PluginVersionException("plugin.json 缺少有效字段: " + field);
        }
        return value.textValue();
    }

    private String optionalText(JsonNode json, String field) {
        JsonNode value = json.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new PluginVersionException("plugin.json 字段无效: " + field);
        }
        return value.textValue();
    }

    private SemanticVersion parseSemanticVersion(String name, String value) {
        Matcher matcher = SEMANTIC_VERSION.matcher(value);
        if (!matcher.matches()) {
            throw new PluginVersionException(name + "必须使用 major.minor.patch 格式: " + value);
        }
        try {
            return new SemanticVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
            );
        } catch (NumberFormatException e) {
            throw new PluginVersionException(name + "超出支持范围: " + value, e);
        }
    }

    private int parseJavaVersion(String value) {
        if (!JAVA_VERSION.matcher(value).matches()) {
            throw new PluginVersionException("javaVersion 必须是正整数: " + value);
        }
        try {
            int version = Integer.parseInt(value);
            if (version <= 0) {
                throw new PluginVersionException("javaVersion 必须是正整数: " + value);
            }
            return version;
        } catch (NumberFormatException e) {
            throw new PluginVersionException("javaVersion 超出支持范围: " + value, e);
        }
    }

    public record PluginVersionDescriptor(
        String pluginId,
        String pluginVersion,
        String sdkVersion,
        String minHostVersion,
        String maxHostVersion,
        String javaVersion,
        String type,
        String provider,
        String frontendSha384
    ) {
        public PluginVersionDescriptor(
                String pluginId,
                String pluginVersion,
                String sdkVersion,
                String minHostVersion,
                String maxHostVersion,
                String javaVersion) {
            this(
                pluginId,
                pluginVersion,
                sdkVersion,
                minHostVersion,
                maxHostVersion,
                javaVersion,
                null,
                null,
                null
            );
        }
    }

    private record SemanticVersion(int major, int minor, int patch)
            implements Comparable<SemanticVersion> {
        @Override
        public int compareTo(SemanticVersion other) {
            int majorResult = Integer.compare(major, other.major);
            if (majorResult != 0) {
                return majorResult;
            }
            int minorResult = Integer.compare(minor, other.minor);
            return minorResult != 0 ? minorResult : Integer.compare(patch, other.patch);
        }

        @Override
        public String toString() {
            return major + "." + minor + "." + patch;
        }
    }

    public static class PluginVersionException extends RuntimeException {
        public PluginVersionException(String message) {
            super(message);
        }

        public PluginVersionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
