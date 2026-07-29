package com.workforcehub.plugin.runtime;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginConfigCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldEncryptWithRandomNonceAndDecryptWithBoundNamespace() {
        PluginConfigCipher cipher = new PluginConfigCipher(KEY);

        String first = cipher.encrypt("plugin-a", "api.token", "secret-value");
        String second = cipher.encrypt("plugin-a", "api.token", "secret-value");

        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains("secret-value"));
        assertNotEquals(first, second);
        assertEquals("secret-value", cipher.decrypt("plugin-a", "api.token", first));
        assertEquals(
            "secret-value",
            new PluginConfigCipher(KEY).decrypt("plugin-a", "api.token", first)
        );
        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt("plugin-b", "api.token", first));
        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt("plugin-a", "other.token", first));
    }

    @Test
    void shouldRejectTamperedAndPlaintextValues() {
        PluginConfigCipher cipher = new PluginConfigCipher(KEY);
        String encrypted = cipher.encrypt("plugin-a", "api.token", "secret-value");
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1)
            + (last == 'A' ? 'B' : 'A');

        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt("plugin-a", "api.token", tampered));
        assertThrows(IllegalStateException.class,
            () -> cipher.decrypt("plugin-a", "api.token", "plaintext"));
    }

    @Test
    void missingOrInvalidKeyShouldFailClosed() {
        PluginConfigCipher missing = new PluginConfigCipher("");

        assertFalse(missing.isAvailable());
        assertThrows(IllegalStateException.class,
            () -> missing.encrypt("plugin-a", "api.token", "secret-value"));
        assertThrows(IllegalArgumentException.class,
            () -> new PluginConfigCipher("not-base64"));
        assertThrows(IllegalArgumentException.class,
            () -> new PluginConfigCipher(Base64.getEncoder().encodeToString(new byte[16])));
        String encrypted = new PluginConfigCipher(KEY)
            .encrypt("plugin-a", "api.token", "secret-value");
        byte[] otherKeyBytes = new byte[32];
        otherKeyBytes[0] = 1;
        String otherKey = Base64.getEncoder().encodeToString(otherKeyBytes);
        assertThrows(IllegalStateException.class,
            () -> new PluginConfigCipher(otherKey)
                .decrypt("plugin-a", "api.token", encrypted));
    }
}
