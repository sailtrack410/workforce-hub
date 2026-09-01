-- HTTP 插件动作幂等记录（与事件订阅账本隔离）
CREATE TABLE sys_plugin_action_idempotency (
    plugin_id        VARCHAR(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    action_code      VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    caller_type      VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    caller_id        BIGINT       NOT NULL,
    idempotency_key  VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash     CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status           VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    claim_token      CHAR(36)     CHARACTER SET ascii COLLATE ascii_bin NULL,
    response_json    JSON         NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at       DATETIME(6)  NULL,
    PRIMARY KEY (plugin_id, action_code, caller_type, caller_id, idempotency_key),
    INDEX idx_plugin_action_idempotency_expiry (status, expires_at)
) COMMENT 'HTTP 插件动作幂等与响应回放';
