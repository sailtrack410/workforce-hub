package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.plugin.repository.PluginDependencySpec;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;

/**
 * 从已签名 JAR 的 plugin.json 读取并校验版本兼容性。
 */
@Component
public class PluginVersionService {

    private static final int MAX_PLUGIN_JSON_BYTES = 64 * 1024;
    private static final int MAX_DEPENDENCIES = 128;
    private static final String PF4J_DESCRIPTOR_MISMATCH =
        "PF4J 插件描述符与 plugin.json 不一致";
    private static final Set<String> DEPENDENCY_FIELDS = Set.of(
        "pluginId", "minVersion", "maxVersion"
    );
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$"
    );
    private static final Pattern JAVA_VERSION = Pattern.compile("^(0|[1-9]\\d*)$");
    private static final Pattern PLUGIN_ID = Pattern.compile("^[a-z0-9-]{1,64}$");
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
        if ("FUNCTIONAL".equals(type) && pluginJson.has("frontendSha384")) {
            throw new PluginVersionException("FUNCTIONAL 插件不得声明 frontendSha384");
        }
        String pluginId = requiredText(pluginJson, "id");
        if (!PLUGIN_ID.matcher(pluginId).matches()) {
            throw new PluginVersionException("plugin.json id 仅支持 1-64 位小写字母、数字或连字符");
        }
        List<PluginDependencySpec> dependencies = parseDependencies(pluginJson, pluginId);
        PluginVersionDescriptor descriptor = new PluginVersionDescriptor(
            pluginId,
            requiredText(pluginJson, "version"),
            requiredText(pluginJson, "sdkVersion"),
            requiredText(pluginJson, "minHostVersion"),
            optionalText(pluginJson, "maxHostVersion"),
            requiredText(pluginJson, "javaVersion"),
            type,
            provider,
            frontendSha384,
            dependencies
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

    public void validatePf4jDescriptor(
            PluginVersionDescriptor manifest,
            PluginDescriptor descriptor) {
        if (descriptor == null
                || !Objects.equals(manifest.pluginId(), descriptor.getPluginId())
                || !Objects.equals(manifest.pluginVersion(), descriptor.getVersion())) {
            throw new PluginVersionException(PF4J_DESCRIPTOR_MISMATCH);
        }

        List<PluginDependency> pf4jDependencies = descriptor.getDependencies();
        if (pf4jDependencies == null || pf4jDependencies.size() != manifest.dependencies().size()) {
            throw new PluginVersionException(PF4J_DESCRIPTOR_MISMATCH);
        }
        Set<String> seen = new HashSet<>();
        for (PluginDependency dependency : pf4jDependencies) {
            if (dependency == null || dependency.isOptional()
                    || !seen.add(dependency.getPluginId())) {
                throw new PluginVersionException(PF4J_DESCRIPTOR_MISMATCH);
            }
            PluginDependencySpec expected = manifest.dependencies().stream()
                .filter(item -> item.pluginId().equals(dependency.getPluginId()))
                .findFirst()
                .orElseThrow(() -> new PluginVersionException(PF4J_DESCRIPTOR_MISMATCH));
            if (!pf4jVersionSupport(expected).equals(dependency.getPluginVersionSupport())) {
                throw new PluginVersionException(PF4J_DESCRIPTOR_MISMATCH);
            }
        }
    }

    public boolean matchesVersion(PluginDependencySpec dependency, String version) {
        SemanticVersion actual = parseSemanticVersion("依赖插件版本", version);
        SemanticVersion minimum = dependency.minVersion() == null
            ? null
            : parseSemanticVersion("依赖最低版本", dependency.minVersion());
        SemanticVersion maximum = dependency.maxVersion() == null
            ? null
            : parseSemanticVersion("依赖最高版本", dependency.maxVersion());
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new PluginVersionException("插件依赖版本范围无效: " + dependency.pluginId());
        }
        return (minimum == null || actual.compareTo(minimum) >= 0)
            && (maximum == null || actual.compareTo(maximum) <= 0);
    }

    private JsonNode readPluginJson(Path jarPath) {
        if (!Files.isRegularFile(jarPath)) {
            throw new IllegalStateException("插件 JAR 不存在或不是普通文件");
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
        } catch (JsonProcessingException | ZipException | EOFException e) {
            throw new PluginVersionException("读取 plugin.json 失败", e);
        } catch (IOException e) {
            throw new IllegalStateException("读取 plugin.json 失败", e);
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

    private List<PluginDependencySpec> parseDependencies(JsonNode pluginJson, String pluginId) {
        JsonNode dependencies = pluginJson.get("dependencies");
        if (dependencies == null) {
            return List.of();
        }
        if (!dependencies.isArray()) {
            throw new PluginVersionException("plugin.json dependencies 必须是数组");
        }
        if (dependencies.size() > MAX_DEPENDENCIES) {
            throw new PluginVersionException("plugin.json dependencies 不能超过 128 项");
        }

        List<PluginDependencySpec> result = new ArrayList<>(dependencies.size());
        Set<String> dependencyIds = new HashSet<>();
        for (JsonNode dependency : dependencies) {
            if (dependency == null || !dependency.isObject()) {
                throw new PluginVersionException("plugin.json dependency 必须是对象");
            }
            dependency.fieldNames().forEachRemaining(field -> {
                if (!DEPENDENCY_FIELDS.contains(field)) {
                    throw new PluginVersionException("plugin.json dependency 包含未知字段: " + field);
                }
            });
            String dependencyId = requiredText(dependency, "pluginId");
            if (!PLUGIN_ID.matcher(dependencyId).matches()) {
                throw new PluginVersionException("plugin.json dependency pluginId 格式无效");
            }
            if (pluginId.equals(dependencyId)) {
                throw new PluginVersionException("插件不能依赖自身");
            }
            if (!dependencyIds.add(dependencyId)) {
                throw new PluginVersionException("plugin.json dependency 不能重复: " + dependencyId);
            }

            String minVersion = dependencyVersion(dependency, "minVersion");
            String maxVersion = dependencyVersion(dependency, "maxVersion");
            SemanticVersion minimum = minVersion == null
                ? null
                : parseSemanticVersion("依赖最低版本", minVersion);
            SemanticVersion maximum = maxVersion == null
                ? null
                : parseSemanticVersion("依赖最高版本", maxVersion);
            if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
                throw new PluginVersionException("插件依赖版本范围无效: " + dependencyId);
            }
            result.add(new PluginDependencySpec(dependencyId, minVersion, maxVersion));
        }
        result.sort(Comparator.comparing(PluginDependencySpec::pluginId));
        return List.copyOf(result);
    }

    private String dependencyVersion(JsonNode dependency, String field) {
        JsonNode value = dependency.get(field);
        if (value == null) {
            return null;
        }
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new PluginVersionException("plugin.json dependency 字段无效: " + field);
        }
        return value.textValue();
    }

    private String pf4jVersionSupport(PluginDependencySpec dependency) {
        if (dependency.minVersion() == null && dependency.maxVersion() == null) {
            return "*";
        }
        if (dependency.minVersion() == null) {
            return "<=" + dependency.maxVersion();
        }
        if (dependency.maxVersion() == null) {
            return ">=" + dependency.minVersion();
        }
        return ">=" + dependency.minVersion() + " & <=" + dependency.maxVersion();
    }

    private SemanticVersion parseSemanticVersion(String name, String value) {
        if (value == null) {
            throw new PluginVersionException(name + "必须使用 major.minor.patch 格式: null");
        }
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
        String frontendSha384,
        List<PluginDependencySpec> dependencies
    ) {
        public PluginVersionDescriptor {
            dependencies = Objects.requireNonNull(dependencies).stream()
                .sorted(Comparator.comparing(PluginDependencySpec::pluginId))
                .toList();
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
