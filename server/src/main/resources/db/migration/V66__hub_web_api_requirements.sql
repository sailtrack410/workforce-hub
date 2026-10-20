-- Workforce Hub - hub-web 接口需求表 P1/P2 配套结构（#2/#3/#4/#5/#10/#12）
-- V66

-- #2/#3/#4 会话成员偏好扩展：保存到通讯录 / 显示成员昵称 / 本群昵称 / 清空游标
ALTER TABLE `im_conversation_member`
    ADD COLUMN `save_to_contacts_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '保存到通讯录',
    ADD COLUMN `show_member_nickname_flag` TINYINT NOT NULL DEFAULT 1 COMMENT '显示成员昵称',
    ADD COLUMN `group_nickname` VARCHAR(64) DEFAULT NULL COMMENT '本群昵称',
    ADD COLUMN `cleared_seq` BIGINT NOT NULL DEFAULT 0 COMMENT '清空游标(该成员视角早于此seq的消息不再展示)';

-- #5 群公告多条记录
CREATE TABLE `im_group_notice` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `conversation_id` BIGINT      NOT NULL COMMENT '群会话ID',
    `title`          VARCHAR(128) DEFAULT NULL COMMENT '公告标题',
    `content`        VARCHAR(2048) NOT NULL COMMENT '公告内容',
    `publisher_id`   BIGINT       NOT NULL COMMENT '发布人ID',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_group_notice_conv` (`conversation_id`, `is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告';

-- #10 通知中心
CREATE TABLE `sys_notification` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `user_id`        BIGINT       NOT NULL COMMENT '接收人ID',
    `type`           VARCHAR(32)  NOT NULL COMMENT '类型: APPROVAL/SYSTEM/ATTENDANCE/ANNOUNCEMENT',
    `title`          VARCHAR(256) NOT NULL COMMENT '标题',
    `content`        VARCHAR(1024) DEFAULT NULL COMMENT '内容',
    `link_type`      VARCHAR(32)  DEFAULT NULL COMMENT '跳转类型',
    `link_id`        VARCHAR(64)  DEFAULT NULL COMMENT '跳转目标ID',
    `read_flag`      TINYINT      NOT NULL DEFAULT 0 COMMENT '已读',
    `read_at`        DATETIME     DEFAULT NULL COMMENT '已读时间',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_notification_user` (`user_id`, `read_flag`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工站内通知';

-- #12 注册短信验证码
CREATE TABLE `auth_sms_code` (
    `id`           BIGINT      NOT NULL COMMENT '主键',
    `phone`        VARCHAR(20) NOT NULL COMMENT '手机号',
    `scene`        VARCHAR(32) NOT NULL COMMENT '场景: REGISTER/RESET_PASSWORD',
    `code_hash`    VARCHAR(128) NOT NULL COMMENT '验证码哈希(SHA-256)',
    `expired_at`   DATETIME    NOT NULL COMMENT '过期时间',
    `used_at`      DATETIME    DEFAULT NULL COMMENT '核销时间',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_sms_code_phone` (`phone`, `scene`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短信验证码';

-- #12 企业邀请码(管理端预生成,一次性,绑定入职部门)
CREATE TABLE `auth_invite_code` (
    `id`               BIGINT      NOT NULL COMMENT '主键',
    `invite_code`      VARCHAR(64) NOT NULL COMMENT '邀请码',
    `primary_dept_id`  BIGINT      NOT NULL COMMENT '入职部门',
    `used_by`          BIGINT      DEFAULT NULL COMMENT '核销用户ID',
    `used_at`          DATETIME    DEFAULT NULL COMMENT '核销时间',
    `expired_at`       DATETIME    DEFAULT NULL COMMENT '过期时间',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='企业邀请码';
