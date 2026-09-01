ALTER TABLE sys_plugin_config
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER is_secret,
    ADD COLUMN is_deleted TINYINT NOT NULL DEFAULT 0 AFTER version;
