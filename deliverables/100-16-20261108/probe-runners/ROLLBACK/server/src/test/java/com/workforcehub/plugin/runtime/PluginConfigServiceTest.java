package com.workforcehub.plugin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workforcehub.common.audit.AuditLogWriter;
import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.plugin.dto.PluginConfigSaveRequest;
import com.workforcehub.plugin.repository.PluginConfigRepository;
import com.workforcehub.plugin.repository.PluginConfigRow;
import com.workforcehub.plugin.repository.PluginStatus;
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

class PluginConfigServiceTest {

    private static final String PLUGIN_ID = "plugin-a";
    private static final String CONFIG_KEY = "api.token";

    @Test
    void secretCreateShouldFailWhenEncryptionKeyIsMissing() {
        PluginConfigRepository repository = mock(PluginConfigRepository.class);
        PluginConfigService service = service(repository, new PluginConfigCipher(""));
        when(repository.lockPluginStatus(PLUGIN_ID)).thenReturn(Optional.of(PluginStatus.ACTIVE));
        when(repository.findIncludingDeleted(PLUGIN_ID, CONFIG_KEY)).thenReturn(Optional.empty());
        when(repository.countByPluginId(PLUGIN_ID)).thenReturn(0);

        BizException exception = assertThrows(BizException.class,
            () -> service.save(PLUGIN_ID, CONFIG_KEY, request(0, "new-secret")));

        assertEquals(BusinessErrorCode.UNPROCESSABLE_ENTITY.code(), exception.getCode());
        verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void secretMetadataUpdateShouldFailWhenCurrentKeyCannotDecryptValue() {
        byte[] originalKeyBytes = new byte[32];
        String originalKey = Base64.getEncoder().encodeToString(originalKeyBytes);
        PluginConfigCipher originalCipher = new PluginConfigCipher(originalKey);
        String encrypted = originalCipher.encrypt(PLUGIN_ID, CONFIG_KEY, "stored-secret");

        byte[] otherKeyBytes = new byte[32];
        otherKeyBytes[0] = 1;
        String otherKey = Base64.getEncoder().encodeToString(otherKeyBytes);
        PluginConfigRepository repository = mock(PluginConfigRepository.class);
        PluginConfigService service = service(repository, new PluginConfigCipher(otherKey));
        when(repository.lockPluginStatus(PLUGIN_ID)).thenReturn(Optional.of(PluginStatus.ACTIVE));
        when(repository.findIncludingDeleted(PLUGIN_ID, CONFIG_KEY)).thenReturn(Optional.of(new PluginConfigRow(
            PLUGIN_ID,
            CONFIG_KEY,
            encrypted,
            "SECRET",
            null,
            null,
            false,
            true,
            false,
            1,
            LocalDateTime.of(2026, 7, 29, 0, 0)
        )));

        BizException exception = assertThrows(BizException.class,
            () -> service.save(PLUGIN_ID, CONFIG_KEY, request(1, null)));

        assertEquals(BusinessErrorCode.UNPROCESSABLE_ENTITY.code(), exception.getCode());
        verify(repository, never()).update(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
    }

    private PluginConfigService service(
            PluginConfigRepository repository,
            PluginConfigCipher cipher) {
        return new PluginConfigService(
            repository,
            cipher,
            mock(AuditLogWriter.class),
            new ObjectMapper()
        );
    }

    private PluginConfigSaveRequest request(long expectedVersion, String value) {
        return new PluginConfigSaveRequest(
            expectedVersion,
            "SECRET",
            value,
            null,
            null,
            false
        );
    }
}
