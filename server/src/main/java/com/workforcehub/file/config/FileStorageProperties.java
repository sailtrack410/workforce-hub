package com.workforcehub.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 文件中心存储配置。
 */
@ConfigurationProperties(prefix = "workforce.file")
@Component
public class FileStorageProperties {

    private String storageProvider = "LOCAL";

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = StringUtils.hasText(storageProvider)
            ? storageProvider.trim().toUpperCase()
            : "LOCAL";
    }
}
