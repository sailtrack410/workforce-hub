package com.workforcehub.common;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.common.response.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BizExceptionContractTest {

    @Test
    void businessCodeShouldBeSeparatedFromHttpStatus() {
        BizException exception = new BizException(BusinessErrorCode.CONFLICT, "重复提交");

        assertEquals(100409, exception.getCode());
        assertEquals(HttpStatus.CONFLICT, exception.getHttpStatus());
    }

    @Test
    void legacyHttpStatusConstructorShouldReturnBusinessCode() {
        ResponseEntity<R<Void>> response = new GlobalExceptionHandler()
            .handleBizException(new BizException(409, "重复提交"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(100409, response.getBody().getCode());
    }

    @Test
    void legacyBadRequestShouldReturnBadRequestBusinessCode() {
        BizException exception = new BizException(400, "参数错误");

        assertEquals(100400, exception.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, exception.getHttpStatus());
    }

    @Test
    void oversizedMultipartShouldReturnPayloadTooLargeBusinessCode() {
        R<Void> response = new GlobalExceptionHandler()
            .handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(32L * 1024 * 1024));

        assertEquals(BusinessErrorCode.PAYLOAD_TOO_LARGE.code(), response.getCode());
        assertEquals("上传文件过大", response.getMessage());
    }

    @Test
    void malformedMultipartShouldReturnBadRequestBusinessCode() {
        ResponseEntity<R<Void>> response = new GlobalExceptionHandler()
            .handleMultipartException(new MultipartException("private parser details"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(BusinessErrorCode.BAD_REQUEST.code(), response.getBody().getCode());
        assertEquals("上传请求格式错误", response.getBody().getMessage());
    }

    @Test
    void multipartIoFailureShouldReturnInternalError() {
        ResponseEntity<R<Void>> response = new GlobalExceptionHandler()
            .handleMultipartException(new MultipartException(
                "private parser details",
                new IOException("private storage details")
            ));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(BusinessErrorCode.INTERNAL_ERROR.code(), response.getBody().getCode());
        assertEquals("系统内部错误", response.getBody().getMessage());
    }

    @Test
    void unsupportedContentTypeShouldReturnUnsupportedMediaTypeBusinessCode() {
        R<Void> response = new GlobalExceptionHandler().handleMediaTypeNotSupported(
            new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON, List.of())
        );

        assertEquals(BusinessErrorCode.UNSUPPORTED_MEDIA_TYPE.code(), response.getCode());
        assertEquals("请求内容类型不支持", response.getMessage());
    }
}
