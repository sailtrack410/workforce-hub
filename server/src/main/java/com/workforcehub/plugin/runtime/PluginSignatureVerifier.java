package com.workforcehub.plugin.runtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 使用宿主可信公钥验证插件 JAR 的分离签名。
 */
@Component
public class PluginSignatureVerifier {

    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final Map<String, PublicKey> trustedSigners;

    public PluginSignatureVerifier(
            @Value("${workforce.plugin.trusted-signers:}") String configuredSigners) {
        this.trustedSigners = parseTrustedSigners(configuredSigners);
    }

    public String verify(Path jarPath, String signerId, byte[] signatureBytes) {
        if (!Files.isRegularFile(jarPath)) {
            throw new PluginSignatureException("插件 JAR 不存在或不是普通文件");
        }
        if (signerId == null || signerId.isBlank()) {
            throw new PluginSignatureException("插件签名者不能为空");
        }
        if (signatureBytes == null || signatureBytes.length == 0) {
            throw new PluginSignatureException("插件签名不能为空");
        }

        PublicKey publicKey = trustedSigners.get(signerId);
        if (publicKey == null) {
            throw new PluginSignatureException("插件签名者不受信任: " + signerId);
        }

        byte[] signatureSnapshot = signatureBytes.clone();
        try {
            Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

            try (InputStream input = Files.newInputStream(jarPath)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    verifier.update(buffer, 0, read);
                    digest.update(buffer, 0, read);
                }
            }

            if (!verifier.verify(signatureSnapshot)) {
                throw new PluginSignatureException("插件签名验证失败");
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | GeneralSecurityException e) {
            throw new PluginSignatureException("插件签名验证失败", e);
        }
    }

    private Map<String, PublicKey> parseTrustedSigners(String configuredSigners) {
        if (configuredSigners == null || configuredSigners.isBlank()) {
            return Map.of();
        }

        Map<String, PublicKey> signers = new HashMap<>();
        for (String item : configuredSigners.split(";")) {
            String entry = item.trim();
            if (entry.isEmpty()) {
                continue;
            }

            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                throw new IllegalArgumentException("可信签名者配置格式错误");
            }

            String signerId = entry.substring(0, separator).trim();
            String encodedPublicKey = entry.substring(separator + 1).trim();
            PublicKey publicKey = decodePublicKey(signerId, encodedPublicKey);
            if (signers.putIfAbsent(signerId, publicKey) != null) {
                throw new IllegalArgumentException("可信签名者重复: " + signerId);
            }
        }
        return Map.copyOf(signers);
    }

    private PublicKey decodePublicKey(String signerId, String encodedPublicKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedPublicKey);
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(keyBytes));
            if (!(publicKey instanceof RSAPublicKey rsaPublicKey)
                    || rsaPublicKey.getModulus().bitLength() < 2048) {
                throw new IllegalArgumentException("可信签名者 RSA 公钥长度不能低于 2048 位");
            }
            return publicKey;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            throw new IllegalArgumentException("可信签名者公钥无效: " + signerId, e);
        }
    }

    public static class PluginSignatureException extends RuntimeException {
        public PluginSignatureException(String message) {
            super(message);
        }

        public PluginSignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
