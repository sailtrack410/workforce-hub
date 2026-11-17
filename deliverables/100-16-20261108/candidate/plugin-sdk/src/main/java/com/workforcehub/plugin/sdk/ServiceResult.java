package com.workforcehub.plugin.sdk;

/**
 * 插件 SDK 的统一调用结果。
 */
public final class ServiceResult<T> {

    private final int code;
    private final String message;
    private final T data;
    private final boolean retryable;

    private ServiceResult(int code, String message, T data, boolean retryable) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.retryable = retryable;
    }

    public static <T> ServiceResult<T> ok(T data) {
        return new ServiceResult<>(0, "success", data, false);
    }

    public static <T> ServiceResult<T> fail(int code, String message) {
        return fail(code, message, false);
    }

    public static <T> ServiceResult<T> fail(int code, String message, boolean retryable) {
        if (code == 0) {
            throw new IllegalArgumentException("失败结果不能使用成功码 0");
        }
        return new ServiceResult<>(code, message, null, retryable);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
