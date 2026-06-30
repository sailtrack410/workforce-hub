package com.workforcehub.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;
    private final HttpStatus httpStatus;

    public BizException(String message) {
        this(BusinessErrorCode.GENERAL, message);
    }

    public BizException(int httpStatus, String message) {
        this(BusinessErrorCode.fromHttpStatus(httpStatus), message);
    }

    public BizException(BusinessErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.code();
        this.httpStatus = errorCode.httpStatus();
    }
}
