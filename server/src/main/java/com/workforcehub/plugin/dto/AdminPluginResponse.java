package com.workforcehub.plugin.dto;

import com.workforcehub.plugin.repository.PluginRow;
import com.workforcehub.plugin.repository.PluginStatus;

import java.time.LocalDateTime;

public record AdminPluginResponse(
    String id,
    String name,
    String description,
    String version,
    String sdkVersion,
    String minHostVersion,
    String maxHostVersion,
    String javaVersion,
    String type,
    PluginStatus status,
    String frontendEntry,
    String provider,
    LocalDateTime installedAt,
    LocalDateTime updatedAt
) {
    public static AdminPluginResponse from(PluginRow row) {
        return from(row, row.getStatus(), row.getUpdatedAt());
    }

    public static AdminPluginResponse from(
            PluginRow row,
            PluginStatus status,
            LocalDateTime updatedAt) {
        return new AdminPluginResponse(
            row.getPluginId(),
            row.getName(),
            row.getDescription(),
            row.getVersion(),
            row.getSdkVersion(),
            row.getMinHostVersion(),
            row.getMaxHostVersion(),
            row.getJavaVersion(),
            row.getType(),
            status,
            row.getFrontendEntry(),
            row.getProvider(),
            row.getInstalledAt(),
            updatedAt
        );
    }
}
