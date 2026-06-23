CREATE TABLE `im_message_user_action` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `conversation_id` BIGINT       NOT NULL COMMENT '会话ID',
    `message_id`      BIGINT       NOT NULL COMMENT '消息ID',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `action_type`     VARCHAR(32)  NOT NULL COMMENT '动作: FAVORITE/REMINDER/DELETE',
    `payload_json`    JSON         DEFAULT NULL COMMENT '动作附加内容',
    `remind_at`       DATETIME     DEFAULT NULL COMMENT '提醒时间',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_message_user_action` (`message_id`, `user_id`, `action_type`),
    KEY `idx_im_message_user_action_user` (`user_id`, `action_type`, `status`),
    KEY `idx_im_message_user_action_conversation` (`conversation_id`, `user_id`, `action_type`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM消息用户动作';
