package com.workforcehub.plugin.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 插件敏感配置加密，AAD 绑定插件和配置键，禁止跨命名空间搬运密文。
 */
@Component
public final class PluginConfigCipher {

    private static final String PREFIX = "v1:";
    private static final int NONCE_BYTES = 12;
    private static final int KEY_BYTES = 32;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PluginConfigCipher(
            @Value("${workforce.plugin.config.encryption-key-base64:}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("插件配置加密密钥必须是 Base64", e);
        }
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("插件配置加密密钥必须是 32 字节");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isAvailable() {
        return key != null;
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String encrypt(String pluginId, String configKey, String value) {
        ensureAvailable();
        byte[] nonce = new byte[NONCE_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(pluginId, configKey));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] envelope = ByteBuffer.allocate(nonce.length + ciphertext.length)
                .put(nonce)
                .put(ciphertext)
                .array();
            return PREFIX + Base64.getEncoder().encodeToString(envelope);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("敏感配置不可用");
        }
    }

    public String decrypt(String pluginId, String configKey, String value) {
        ensureAvailable();
        if (!isEncrypted(value)) {
            throw new IllegalStateException("敏感配置不可用");
        }
        try {
            byte[] envelope = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (envelope.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("密文长度不正确");
            }
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] ciphertext = new byte[envelope.length - NONCE_BYTES];
            System.arraycopy(envelope, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(envelope, NONCE_BYTES, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aad(pluginId, configKey));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("敏感配置不可用");
        }
    }

    private void ensureAvailable() {
        if (key == null) {
            throw new IllegalStateException("敏感配置不可用");
        }
    }

    private byte[] aad(String pluginId, String configKey) {
        return (pluginId + '\0' + configKey).getBytes(StandardCharsets.UTF_8);
    }
}
