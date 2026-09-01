package com.workforcehub.common.exception;

import com.workforcehub.common.response.R;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.InvalidFileNameException;
import org.apache.tomcat.util.http.fileupload.MultipartStream;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常：已知的业务规则违反，httpStatus 由 BizException 自己携带
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBizException(BizException e) {
        log.warn("业务异常: httpStatus={}, code={}, message={}", e.getHttpStatus().value(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus()).body(R.fail(e.getCode(), e.getMessage()));
    }

    // Spring Security 抛的认证失败，统一返回 401
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public R<Void> handleBadCredentials(BadCredentialsException e) {
        return R.fail(BusinessErrorCode.UNAUTHORIZED.code(), "用户名或密码错误");
    }

    // Spring Security 抛的权限不足，统一返回 403
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(BusinessErrorCode.FORBIDDEN.code(), "无权限访问");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return R.fail(BusinessErrorCode.BAD_REQUEST.code(), message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public R<Void> handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return R.fail(BusinessErrorCode.BAD_REQUEST.code(), message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNoResourceFound(NoResourceFoundException e) {
        return R.fail(BusinessErrorCode.NOT_FOUND.code(), "请求路径不存在");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public R<Void> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return R.fail(BusinessErrorCode.PAYLOAD_TOO_LARGE.code(), "上传文件过大");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<R<Void>> handleMultipartException(MultipartException e) {
        if (isMalformedMultipart(e)) {
            return ResponseEntity.badRequest()
                .body(R.fail(BusinessErrorCode.BAD_REQUEST.code(), "上传请求格式错误"));
        }
        log.error("解析 multipart 请求失败", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(R.fail(BusinessErrorCode.INTERNAL_ERROR.code(), "系统内部错误"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public R<Void> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return R.fail(BusinessErrorCode.UNSUPPORTED_MEDIA_TYPE.code(), "请求内容类型不支持");
    }

    private boolean isMalformedMultipart(MultipartException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return exception.getCause() == null
            || root instanceof FileUploadException
            || root instanceof InvalidFileNameException
            || root instanceof MultipartStream.IllegalBoundaryException
            || root instanceof MultipartStream.MalformedStreamException;
    }

    // 兜底：未预期的异常统一返回 500，详细信息只记日志不暴露给前端
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return R.fail(BusinessErrorCode.INTERNAL_ERROR.code(), "系统内部错误");
    }
}
