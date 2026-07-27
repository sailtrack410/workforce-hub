package com.workforcehub.plugin.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PluginSignatureVerifierTest {

    @TempDir
    Path tempDir;

    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
    }

    @Test
    void shouldVerifyTrustedSignerAndReturnHostCalculatedSha256() throws Exception {
        Path jarPath = writeJar("signed-plugin");
        byte[] signature = sign(jarPath);

        String jarSha256 = verifier("official").verify(jarPath, "official", signature);

        assertEquals(sha256(jarPath), jarSha256);
    }

    @Test
    void shouldRejectTamperedJar() throws Exception {
        Path jarPath = writeJar("original-plugin");
        byte[] signature = sign(jarPath);
        Files.writeString(jarPath, "tampered-plugin");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> verifier("official").verify(jarPath, "official", signature)
        );
    }

    @Test
    void shouldRejectUnknownSigner() throws Exception {
        Path jarPath = writeJar("signed-plugin");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> verifier("official").verify(jarPath, "unknown", sign(jarPath))
        );
    }

    @Test
    void shouldRejectMissingSignature() throws Exception {
        Path jarPath = writeJar("unsigned-plugin");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> verifier("official").verify(jarPath, "official", null)
        );
    }

    @Test
    void shouldFailClosedWhenNoTrustedSignerConfigured() throws Exception {
        Path jarPath = writeJar("signed-plugin");

        assertThrows(
            PluginSignatureVerifier.PluginSignatureException.class,
            () -> new PluginSignatureVerifier("").verify(jarPath, "official", sign(jarPath))
        );
    }

    @Test
    void shouldRejectMalformedTrustedSignerConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new PluginSignatureVerifier("official=not-base64"));
    }

    @Test
    void shouldRejectBlankTrustedSignerId() {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

        assertThrows(IllegalArgumentException.class, () -> new PluginSignatureVerifier(" =" + publicKey));
    }

    @Test
    void shouldRejectRsaPublicKeyShorterThan2048Bits() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(1024);
        String publicKey = Base64.getEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded());

        assertThrows(
            IllegalArgumentException.class,
            () -> new PluginSignatureVerifier("weak=" + publicKey)
        );
    }

    private PluginSignatureVerifier verifier(String signerId) {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return new PluginSignatureVerifier(signerId + "=" + publicKey);
    }

    private Path writeJar(String content) throws Exception {
        Path jarPath = tempDir.resolve("plugin.jar");
        Files.writeString(jarPath, content);
        return jarPath;
    }

    private byte[] sign(Path jarPath) throws Exception {
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(Files.readAllBytes(jarPath));
        return signer.sign();
    }

    private String sha256(Path jarPath) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jarPath))
        );
    }
}
