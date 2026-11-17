package com.workforcehub.im.websocket;

import java.util.List;

/**
 * IM WebSocket outbox 派发结果。
 */
public record ImWebSocketOutboxDispatchResult(
    List<Long> attemptedEventIds,
    List<Long> dispatchedEventIds,
    List<Long> failedEventIds
) {

    public int dispatchedCount() {
        return dispatchedEventIds == null ? 0 : dispatchedEventIds.size();
    }
}
