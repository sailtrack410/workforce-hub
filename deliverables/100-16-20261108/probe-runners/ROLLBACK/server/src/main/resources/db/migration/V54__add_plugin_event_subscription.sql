-- 插件事件订阅
CREATE TABLE sys_plugin_event_subscription (
    plugin_id        VARCHAR(64)  NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    action_code      VARCHAR(128) NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_id, event_type)
) COMMENT '插件事件订阅';

CREATE INDEX idx_event_type ON sys_plugin_event_subscription (event_type);
