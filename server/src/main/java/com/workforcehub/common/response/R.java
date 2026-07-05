package com.workforcehub.common.response;

import com.workforcehub.common.config.RequestIdFilter;
import lombok.Data;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;

/**
 * 统一响应包
 * requestId 自动从当前请求上下文获取
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String requestId;

    public static <T> R<T> ok() {
        return ok(null);
    }

    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(0);
        r.setMessage("ok");
        r.setData(data);
        r.setRequestId(resolveRequestId());
        return r;
    }

    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setRequestId(resolveRequestId());
        return r;
    }

    public static <T> R<T> fail(String message) {
        return fail(1, message);
    }

    // 非 Web 环境（单测、异步线程）拿不到 request，返回 null 不报错
    private static String resolveRequestId() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            Object requestId = servletAttrs.getRequest().getAttribute(RequestIdFilter.MDC_KEY);
            return requestId != null ? requestId.toString() : null;
        }
        return null;
    }
}
