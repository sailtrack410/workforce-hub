package com.workforcehub.im.websocket;

import java.util.Set;

/**
 * IM WebSocket 事件类型常量。
 */
public final class ImWebSocketEventTypes {

    public static final String MESSAGE_CREATED = "IM_MESSAGE_CREATED";
    public static final String MESSAGE_EDITED = "IM_MESSAGE_EDITED";
    public static final String MESSAGE_RECALLED = "IM_MESSAGE_RECALLED";
    public static final String READ_POSITION_UPDATED = "IM_READ_POSITION_UPDATED";
    public static final String CONVERSATION_PREFERENCE_UPDATED = "IM_CONVERSATION_PREFERENCE_UPDATED";
    public static final String GROUP_PROFILE_UPDATED = "IM_GROUP_PROFILE_UPDATED";
    public static final String GROUP_MEMBERS_ADDED = "IM_GROUP_MEMBERS_ADDED";
    public static final String GROUP_MEMBERS_REMOVED = "IM_GROUP_MEMBERS_REMOVED";
    public static final String GROUP_MEMBER_LEFT = "IM_GROUP_MEMBER_LEFT";
    public static final String GROUP_DISMISSED = "IM_GROUP_DISMISSED";
    public static final String AGENT_MESSAGE_CREATED = "IM_AGENT_MESSAGE_CREATED";
    public static final String AGENT_REPLY_FAILED = "IM_AGENT_REPLY_FAILED";

    public static final Set<String> OUTBOX_EVENT_TYPES = Set.of(
        MESSAGE_CREATED,
        MESSAGE_EDITED,
        MESSAGE_RECALLED,
        READ_POSITION_UPDATED,
        CONVERSATION_PREFERENCE_UPDATED,
        GROUP_PROFILE_UPDATED,
        GROUP_MEMBERS_ADDED,
        GROUP_MEMBERS_REMOVED,
        GROUP_MEMBER_LEFT,
        GROUP_DISMISSED,
        AGENT_MESSAGE_CREATED,
        AGENT_REPLY_FAILED
    );

    private ImWebSocketEventTypes() {
    }
}
