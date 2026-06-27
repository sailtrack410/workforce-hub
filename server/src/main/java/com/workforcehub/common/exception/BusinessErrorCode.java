package com.workforcehub.common.exception;

import org.springframework.http.HttpStatus;

public enum BusinessErrorCode {
    GENERAL(100000, HttpStatus.BAD_REQUEST),
    BAD_REQUEST(100400, HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(100401, HttpStatus.UNAUTHORIZED),
    FORBIDDEN(100403, HttpStatus.FORBIDDEN),
    NOT_FOUND(100404, HttpStatus.NOT_FOUND),
    CONFLICT(100409, HttpStatus.CONFLICT),
    GONE(100410, HttpStatus.GONE),
    UNPROCESSABLE_ENTITY(100422, HttpStatus.UNPROCESSABLE_ENTITY),
    LOCKED(100423, HttpStatus.LOCKED),
    TOO_MANY_REQUESTS(100429, HttpStatus.TOO_MANY_REQUESTS),
    INSUFFICIENT_STORAGE(100507, HttpStatus.INSUFFICIENT_STORAGE),
    NOT_IMPLEMENTED(100501, HttpStatus.NOT_IMPLEMENTED),
    INTERNAL_ERROR(100500, HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final HttpStatus httpStatus;

    BusinessErrorCode(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public int code() {
        return code;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public static BusinessErrorCode fromHttpStatus(int status) {
        for (BusinessErrorCode item : values()) {
            if (item == GENERAL) {
                continue;
            }
            if (item.httpStatus.value() == status) {
                return item;
            }
        }
        return GENERAL;
    }
}
