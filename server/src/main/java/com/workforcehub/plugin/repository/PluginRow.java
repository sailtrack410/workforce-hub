package com.workforcehub.plugin.repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件注册表实体。
 */
public class PluginRow {

    private String pluginId;
    private String name;
    private String description;
    private String version;
    private String sdkVersion;
    private String minHostVersion;
    private String maxHostVersion;
    private String javaVersion;
    private String type;
    private PluginStatus status;
    private String jarPath;
    private String jarSha256;
    private byte[] signature;
    private String signerId;
    private String frontendEntry;
    private String frontendSha384;
    private String provider;
    private String errorMessage;
    private List<PluginDependencySpec> dependencies = List.of();
    private LocalDateTime installedAt;
    private LocalDateTime updatedAt;

    public PluginRow() {
    }

    // getter/setter
    public String getPluginId() { return pluginId; }
    public void setPluginId(String pluginId) { this.pluginId = pluginId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(String sdkVersion) { this.sdkVersion = sdkVersion; }

    public String getMinHostVersion() { return minHostVersion; }
    public void setMinHostVersion(String minHostVersion) { this.minHostVersion = minHostVersion; }

    public String getMaxHostVersion() { return maxHostVersion; }
    public void setMaxHostVersion(String maxHostVersion) { this.maxHostVersion = maxHostVersion; }

    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public PluginStatus getStatus() { return status; }
    public void setStatus(PluginStatus status) { this.status = status; }

    public String getJarPath() { return jarPath; }
    public void setJarPath(String jarPath) { this.jarPath = jarPath; }

    public String getJarSha256() { return jarSha256; }
    public void setJarSha256(String jarSha256) { this.jarSha256 = jarSha256; }

    public byte[] getSignature() { return signature; }
    public void setSignature(byte[] signature) { this.signature = signature; }

    public String getSignerId() { return signerId; }
    public void setSignerId(String signerId) { this.signerId = signerId; }

    public String getFrontendEntry() { return frontendEntry; }
    public void setFrontendEntry(String frontendEntry) { this.frontendEntry = frontendEntry; }

    public String getFrontendSha384() { return frontendSha384; }
    public void setFrontendSha384(String frontendSha384) { this.frontendSha384 = frontendSha384; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<PluginDependencySpec> getDependencies() { return dependencies; }
    public void setDependencies(List<PluginDependencySpec> dependencies) {
        this.dependencies = List.copyOf(dependencies);
    }

    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime installedAt) { this.installedAt = installedAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
