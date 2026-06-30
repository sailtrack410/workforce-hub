package com.workforcehub.file.storage;

import com.workforcehub.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;

@Component
public class LocalStorageProvider implements StorageProvider {

    public static final String PROVIDER_CODE = "LOCAL";
    public static final String DEFAULT_BUCKET = "local";

    private final Path localRoot;

    public LocalStorageProvider(@Value("${workforce.file.local-root:${user.home}/.workforce-hub/files}") String localRoot) {
        this.localRoot = Path.of(localRoot);
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String defaultBucketName() {
        return DEFAULT_BUCKET;
    }

    @Override
    public StorageUploadTarget buildUploadTarget(String bucketName, String objectKey, String mimeType, int expireMinutes) {
        return new StorageUploadTarget(
            objectPath(objectKey).toString(),
            Map.of("Content-Type", mimeType),
            LocalDateTime.now().plusMinutes(expireMinutes)
        );
    }

    @Override
    public void verifyObject(String bucketName, String objectKey, String expectedSha256, Long expectedSize) {
        Path path = objectPath(objectKey);
        if (!Files.exists(path)) {
            throw new BizException(422, "文件对象不存在，请重新上传");
        }
        try {
            if (Files.size(path) != expectedSize) {
                throw new BizException(422, "文件校验失败，请重新上传");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            String actualSha256 = HexFormat.of().formatHex(digest.digest());
            if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
                throw new BizException(422, "文件校验失败，请重新上传");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(500, "文件服务异常，请稍后重试");
        }
    }

    @Override
    public StorageObjectResource loadObject(String bucketName, String objectKey) {
        Path path = objectPath(objectKey);
        if (!Files.exists(path)) {
            throw new BizException(404, "文件对象不存在");
        }
        try {
            return new StorageObjectResource(new FileSystemResource(path), Files.size(path));
        } catch (Exception e) {
            throw new BizException(500, "文件服务异常，请稍后重试");
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) {
        Path path = objectPath(objectKey);
        try {
            Files.deleteIfExists(path);
        } catch (Exception e) {
            throw new BizException(500, "文件对象清理失败");
        }
    }

    @Override
    public long usableFreeBytes() {
        return localRoot.toFile().getFreeSpace();
    }

    private Path objectPath(String objectKey) {
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot.normalize())) {
            throw new BizException(400, "非法文件对象路径");
        }
        return path;
    }
}
