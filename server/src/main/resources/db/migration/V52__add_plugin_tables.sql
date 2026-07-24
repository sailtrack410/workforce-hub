-- 插件注册表
CREATE TABLE sys_plugin (
    plugin_id         VARCHAR(64)  PRIMARY KEY,
    name              VARCHAR(128) NOT NULL,
    description       VARCHAR(512),
    version           VARCHAR(32)  NOT NULL,
    sdk_version       VARCHAR(32)  NOT NULL      DEFAULT '1.0.0',
    min_host_version  VARCHAR(32),
    max_host_version  VARCHAR(32),
    java_version      VARCHAR(16)  NOT NULL      DEFAULT '17',
    type              VARCHAR(32)  NOT NULL       DEFAULT 'FUNCTIONAL',
    status            VARCHAR(32)  NOT NULL       DEFAULT 'INSTALLED',
    jar_path          VARCHAR(512) NOT NULL,
    jar_sha256        VARCHAR(64),
    signature         BLOB,
    signer_id         VARCHAR(128),
    frontend_entry    VARCHAR(512),
    frontend_sha384   VARCHAR(96),
    provider          VARCHAR(128),
    error_message     TEXT,
    installed_at      DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '插件注册表';

-- 插件配置
CREATE TABLE sys_plugin_config (
    plugin_id        VARCHAR(64)  NOT NULL,
    config_key       VARCHAR(128) NOT NULL,
    config_value     TEXT,
    value_type       VARCHAR(32)  NOT NULL       DEFAULT 'STRING',
    default_value    TEXT,
    description      VARCHAR(256),
    requires_restart TINYINT      NOT NULL       DEFAULT 0,
    is_secret        TINYINT      NOT NULL       DEFAULT 0,
    updated_at       DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_id, config_key)
) COMMENT '插件配置';

-- 插件能力授权（申请的能力）
CREATE TABLE sys_plugin_capability (
    plugin_id        VARCHAR(64)  NOT NULL,
    capability_code  VARCHAR(128) NOT NULL,
    description      VARCHAR(256),
    granted_at       DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (plugin_id, capability_code)
) COMMENT '插件能力授权';

-- 插件可见范围
CREATE TABLE sys_plugin_visibility (
    id               BIGINT       NOT NULL       AUTO_INCREMENT PRIMARY KEY,
    plugin_id        VARCHAR(64)  NOT NULL,
    scope_type       VARCHAR(32)  NOT NULL       COMMENT 'ALL/MANAGER/DEPT/POSITION/USER',
    scope_value      VARCHAR(128)                COMMENT '部门ID/岗位ID/用户ID（ALL时为NULL）',
    created_at       DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_plugin_scope (plugin_id, scope_type, scope_value)
) COMMENT '插件可见范围';

-- 插件依赖关系
CREATE TABLE sys_plugin_dependency (
    plugin_id        VARCHAR(64)  NOT NULL,
    depends_on       VARCHAR(64)  NOT NULL,
    min_version      VARCHAR(32),
    max_version      VARCHAR(32),
    PRIMARY KEY (plugin_id, depends_on)
) COMMENT '插件依赖关系';

-- 插件动作权限
CREATE TABLE sys_plugin_action_permission (
    plugin_id        VARCHAR(64)  NOT NULL,
    action_code      VARCHAR(128) NOT NULL,
    capability_code  VARCHAR(128) NOT NULL      COMMENT '执行此动作所需的能力',
    PRIMARY KEY (plugin_id, action_code)
) COMMENT '插件动作权限';

-- 插件升级历史
CREATE TABLE sys_plugin_upgrade_history (
    id               BIGINT       NOT NULL       AUTO_INCREMENT PRIMARY KEY,
    plugin_id        VARCHAR(64)  NOT NULL,
    from_version     VARCHAR(32),
    to_version       VARCHAR(32)  NOT NULL,
    upgrade_type     VARCHAR(32)  NOT NULL       COMMENT 'INSTALL/UPGRADE/DOWNGRADE',
    status           VARCHAR(32)  NOT NULL       COMMENT 'SUCCESS/FAILED',
    error_message    TEXT,
    upgraded_at      DATETIME     NOT NULL       DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plugin_id (plugin_id)
) COMMENT '插件升级历史';
