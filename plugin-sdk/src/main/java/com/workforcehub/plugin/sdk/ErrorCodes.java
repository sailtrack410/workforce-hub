package com.workforcehub.plugin.sdk;

/**
 * 插件 SDK 统一错误码。
 */
public final class ErrorCodes {

    private ErrorCodes() {
    }

    /** 成功 */
    public static final int SUCCESS = 0;

    // 参数错误 1000-1999
    /** 无效参数 */
    public static final int INVALID_PARAM = 1000;
    /** 缺少必填参数 */
    public static final int MISSING_PARAM = 1001;

    // 权限错误 2000-2999
    /** 插件未被授予该能力 */
    public static final int PLUGIN_NOT_ALLOWED = 2000;
    /** 用户无权操作 */
    public static final int USER_NOT_ALLOWED = 2001;
    /** 超出数据范围 */
    public static final int DATA_SCOPE_EXCEEDED = 2002;

    // 业务错误 3000-3999
    /** 余额不足 */
    public static final int INSUFFICIENT_BALANCE = 3000;
    /** 重复操作 */
    public static final int DUPLICATE_OPERATION = 3001;
    /** 状态冲突 */
    public static final int STATE_CONFLICT = 3002;

    // 系统错误 4000-4999
    /** 内部错误 */
    public static final int INTERNAL_ERROR = 4000;
    /** 超时 */
    public static final int TIMEOUT = 4001;
    /** 限流 */
    public static final int RATE_LIMITED = 4002;

    // 版本不兼容 5000-5999
    /** SDK 版本不兼容 */
    public static final int SDK_VERSION_MISMATCH = 5000;
    /** 宿主版本不兼容 */
    public static final int HOST_VERSION_MISMATCH = 5001;
}
