package com.workforcehub.file.storage;

import com.workforcehub.common.exception.BizException;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class MinioStorageProvider implements StorageProvider {

    public static final String PROVIDER_CODE = "MINIO";

    private final MinioClient minioClient;
    private final String bucketName;

    public MinioStorageProvider(@Value("${minio.endpoint}") String endpoint,
                                @Value("${minio.access-key}") String accessKey,
                                @Value("${minio.secret-key}") String secretKey,
                                @Value("${minio.bucket}") String bucketName,
                                @Value("${minio.region:us-east-1}") String region) {
        MinioClient.Builder builder = MinioClient.builder()
            .endpoint(endpoint)
            .credentials(accessKey, secretKey);
        if (StringUtils.hasText(region)) {
            builder.region(region.trim());
        }
        this.minioClient = builder.build();
        this.bucketName = bucketName;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String defaultBucketName() {
        return bucketName;
    }

    @Override
    public StorageUploadTarget buildUploadTarget(String bucketName, String objectKey, String mimeType, int expireMinutes) {
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.PUT)
                .bucket(bucketName)
                .object(objectKey)
                .expiry(expireMinutes, TimeUnit.MINUTES)
                .build());
            return new StorageUploadTarget(uploadUrl, Map.of("Content-Type", mimeType), java.time.LocalDateTime.now().plusMinutes(expireMinutes));
        } catch (Exception e) {
            throw new BizException(500, "MinIO上传地址生成失败");
        }
    }

    @Override
    public void verifyObject(String bucketName, String objectKey, String expectedSha256, Long expectedSize) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .build());
            if (stat.size() != expectedSize) {
                throw new BizException(422, "文件校验失败，请重新上传");
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectKey).build())) {
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
            throw new BizException(500, "MinIO文件校验失败");
        }
    }

    @Override
    public StorageObjectResource loadObject(String bucketName, String objectKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .build());
            GetObjectResponse object = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .build());
            return new StorageObjectResource(new InputStreamResource(object), stat.size());
        } catch (Exception e) {
            throw new BizException(404, "文件对象不存在");
        }
    }

    @Override
    public void deleteObject(String bucketName, String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucketName)
                .object(objectKey)
                .build());
        } catch (Exception e) {
            throw new BizException(500, "MinIO文件清理失败");
        }
    }

    @Override
    public long usableFreeBytes() {
        return Long.MAX_VALUE;
    }
}
