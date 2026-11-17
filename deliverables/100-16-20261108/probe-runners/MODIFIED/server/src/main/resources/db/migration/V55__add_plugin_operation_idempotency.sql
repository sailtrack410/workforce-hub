-- 插件写操作幂等账本
CREATE TABLE sys_plugin_operation_idempotency (
    plugin_id        VARCHAR(64)  NOT NULL,
    operation_code   VARCHAR(128) NOT NULL,
    idempotency_key  VARCHAR(128) NOT NULL,
    request_hash     CHAR(64)     NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_id, operation_code, idempotency_key)
) COMMENT '插件写操作幂等账本';
