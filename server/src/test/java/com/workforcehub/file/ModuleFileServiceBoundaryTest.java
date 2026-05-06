package com.workforcehub.file;

import com.workforcehub.file.dto.AttachmentRefResponse;
import com.workforcehub.file.dto.ConfirmUploadRequest;
import com.workforcehub.file.dto.CreateDownloadTicketRequest;
import com.workforcehub.file.dto.DownloadFileResult;
import com.workforcehub.file.dto.DownloadTicketResponse;
import com.workforcehub.file.dto.FilePurgeResponse;
import com.workforcehub.file.dto.FileRestoreResponse;
import com.workforcehub.file.dto.FileTrashRequest;
import com.workforcehub.file.dto.FileTrashResponse;
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

    @Test
    @DisplayName("下载链路应从FileCenterService拆到独立服务")
    void downloadShouldBeOwnedByDedicatedService() throws Exception {
        Class<?> serviceClass = Class.forName("com.workforcehub.file.service.FileDownloadService");

        Method createEmployeeTicketMethod = serviceClass.getMethod(
            "createEmployeeDownloadTicket",
            Long.class,
            CreateDownloadTicketRequest.class
        );
        Method downloadEmployeeFileMethod = serviceClass.getMethod("downloadEmployeeFile", String.class);
        Method createAdminTicketMethod = serviceClass.getMethod(
            "createAdminDownloadTicket",
            Long.class,
            CreateDownloadTicketRequest.class
        );
        Method downloadAdminFileMethod = serviceClass.getMethod("downloadAdminFile", String.class);

        assertEquals(DownloadTicketResponse.class, createEmployeeTicketMethod.getReturnType());
        assertEquals(DownloadFileResult.class, downloadEmployeeFileMethod.getReturnType());
        assertEquals(DownloadTicketResponse.class, createAdminTicketMethod.getReturnType());
        assertEquals(DownloadFileResult.class, downloadAdminFileMethod.getReturnType());
    }

    @Test
    @DisplayName("回收站生命周期应从FileCenterService拆到独立服务")
    void lifecycleShouldBeOwnedByDedicatedService() throws Exception {
        Class<?> serviceClass = Class.forName("com.workforcehub.file.service.FileLifecycleService");

        Method trashEmployeeMethod = serviceClass.getMethod("trashEmployeeFile", Long.class, FileTrashRequest.class);
        Method restoreEmployeeMethod = serviceClass.getMethod("restoreEmployeeFile", Long.class, FileTrashRequest.class);
        Method purgeEmployeeMethod = serviceClass.getMethod("purgeEmployeeFile", Long.class);
        Method trashAdminMethod = serviceClass.getMethod("trashAdminFile", Long.class, FileTrashRequest.class);
        Method restoreAdminMethod = serviceClass.getMethod("restoreAdminFile", Long.class, FileTrashRequest.class);
        Method purgeAdminMethod = serviceClass.getMethod("purgeAdminFile", Long.class);

        assertEquals(FileTrashResponse.class, trashEmployeeMethod.getReturnType());
        assertEquals(FileRestoreResponse.class, restoreEmployeeMethod.getReturnType());
        assertEquals(FilePurgeResponse.class, purgeEmployeeMethod.getReturnType());
        assertEquals(FileTrashResponse.class, trashAdminMethod.getReturnType());
        assertEquals(FileRestoreResponse.class, restoreAdminMethod.getReturnType());
        assertEquals(FilePurgeResponse.class, purgeAdminMethod.getReturnType());
    }
}
