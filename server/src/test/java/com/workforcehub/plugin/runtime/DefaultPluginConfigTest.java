package com.workforcehub.plugin.runtime;

import com.workforcehub.plugin.repository.PluginConfigRepository;
import com.workforcehub.plugin.repository.PluginConfigRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultPluginConfigTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldReadOnlyFromBoundPluginNamespace() {
        PluginConfigRepository repository = mock(PluginConfigRepository.class);
        PluginConfigCipher cipher = new PluginConfigCipher(KEY);
        DefaultPluginConfig config = new DefaultPluginConfig("plugin-a", repository, cipher);
        when(repository.find("plugin-a", "shared.key")).thenReturn(Optional.of(row(
            "plugin-a", "shared.key", "value-a", "STRING", null, false
        )));

        assertEquals(Optional.of("value-a"), config.get("shared.key"));
        verify(repository).find("plugin-a", "shared.key");
    }

    @Test
    void shouldUseDefaultAndDecryptSecret() {
        PluginConfigRepository repository = mock(PluginConfigRepository.class);
        PluginConfigCipher cipher = new PluginConfigCipher(KEY);
        DefaultPluginConfig config = new DefaultPluginConfig("plugin-a", repository, cipher);
        when(repository.find("plugin-a", "feature.enabled")).thenReturn(Optional.of(row(
            "plugin-a", "feature.enabled", null, "BOOL", "true", false
        )));
        String encrypted = cipher.encrypt("plugin-a", "api.token", "secret-value");
        when(repository.find("plugin-a", "api.token")).thenReturn(Optional.of(row(
            "plugin-a", "api.token", encrypted, "SECRET", null, true
        )));

        assertEquals(Optional.of("true"), config.get("feature.enabled"));
        assertEquals(Optional.of("secret-value"), config.get("api.token"));
    }

    @Test
    void shouldRejectInvalidKeyAndLegacyPlaintextSecret() {
        PluginConfigRepository repository = mock(PluginConfigRepository.class);
        DefaultPluginConfig config = new DefaultPluginConfig(
            "plugin-a", repository, new PluginConfigCipher(KEY));

        assertThrows(IllegalArgumentException.class, () -> config.get("../other"));
        verify(repository, never()).find("plugin-a", "../other");

        when(repository.find("plugin-a", "api.token")).thenReturn(Optional.of(row(
            "plugin-a", "api.token", "legacy-plaintext", "SECRET", null, true
        )));
        assertThrows(IllegalStateException.class, () -> config.get("api.token"));
    }

    private PluginConfigRow row(
            String pluginId,
            String configKey,
            String value,
            String valueType,
            String defaultValue,
            boolean secret) {
        return new PluginConfigRow(
            pluginId,
            configKey,
            value,
            valueType,
            defaultValue,
            null,
            false,
            secret,
            false,
            1,
            LocalDateTime.of(2026, 7, 29, 0, 0)
        );
    }
}
