package com.workforcehub.file.storage;

import org.springframework.core.io.Resource;

public record StorageObjectResource(
    Resource resource,
    Long size
) {}
