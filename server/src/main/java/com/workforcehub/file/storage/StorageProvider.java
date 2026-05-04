package com.workforcehub.file.storage;

public interface StorageProvider {

    String providerCode();

    String defaultBucketName();

    StorageUploadTarget buildUploadTarget(String bucketName, String objectKey, String mimeType, int expireMinutes);

    void verifyObject(String bucketName, String objectKey, String expectedSha256, Long expectedSize);

    StorageObjectResource loadObject(String bucketName, String objectKey);

    long usableFreeBytes();
}
