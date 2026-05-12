package com.workforcehub.im.support;

import java.util.Set;

/**
 * IM outbox 后台操作审计动作。
 */
public final class ImOutboxAdminAuditActions {

    public static final String RETRY_REQUESTED = "IM_OUTBOX_RETRY_REQUESTED";
    public static final String BATCH_RETRY_REQUESTED = "IM_OUTBOX_BATCH_RETRY_REQUESTED";
    public static final String DISPATCH_TRIGGERED = "IM_OUTBOX_DISPATCH_TRIGGERED";

    public static final Set<String> SUPPORTED_ACTION_TYPES = Set.of(
        RETRY_REQUESTED,
        BATCH_RETRY_REQUESTED,
        DISPATCH_TRIGGERED
    );

    private ImOutboxAdminAuditActions() {
    }
}
