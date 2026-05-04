package com.workforcehub.file.storage;

import com.workforcehub.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StorageProviderRegistry {

    private final Map<String, StorageProvider> providers;

    public StorageProviderRegistry(List<StorageProvider> providers) {
        this.providers = providers.stream()
            .collect(Collectors.toUnmodifiableMap(StorageProvider::providerCode, Function.identity()));
    }

    public StorageProvider require(String providerCode) {
        StorageProvider provider = providers.get(providerCode);
        if (provider == null) {
            throw new BizException(500, "文件存储提供方未配置: " + providerCode);
        }
        return provider;
    }
}
