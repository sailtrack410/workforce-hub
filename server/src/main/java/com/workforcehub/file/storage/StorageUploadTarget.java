package com.workforcehub.file.storage;

import java.time.LocalDateTime;
import java.util.Map;

public record StorageUploadTarget(
    String uploadUrl,
    Map<String, String> headers,
    LocalDateTime expireAt
) {}
