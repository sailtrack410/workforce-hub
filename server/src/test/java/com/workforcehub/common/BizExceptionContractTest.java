package com.workforcehub.common;

import com.workforcehub.common.exception.BizException;
import com.workforcehub.common.exception.BusinessErrorCode;
import com.workforcehub.common.exception.GlobalExceptionHandler;
import com.workforcehub.common.response.R;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
