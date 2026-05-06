package com.workforcehub.file;

import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * module-file 内部服务边界测试。
 */
class ModuleFileServiceBoundaryTest {

    @Test
    @DisplayName("上传确认应从FileCenterService拆到独立服务")
    void uploadConfirmShouldBeOwnedByDedicatedService() throws Exception {
        Class<?> serviceClass = Class.forName("com.workforcehub.file.service.FileUploadConfirmService");

        Method employeeMethod = serviceClass.getMethod("confirmEmployeeUpload", ConfirmUploadRequest.class);
        Method adminMethod = serviceClass.getMethod("confirmAdminUpload", ConfirmUploadRequest.class);

        assertEquals(AttachmentRefResponse.class, employeeMethod.getReturnType());
        assertEquals(AttachmentRefResponse.class, adminMethod.getReturnType());
    }
}
