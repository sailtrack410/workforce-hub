-- ============================================================
-- Workforce Hub - IM WebSocket 事件 outbox
-- 版本: V14
-- 说明:
--   - IM 事件先落可靠账本，再由 dispatcher 异步投递 WebSocket
--   - 支撑推送失败重试和失败可见性
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `im_event_outbox` (
    `id`                      BIGINT       NOT NULL COMMENT '主键',
    `event_type`              VARCHAR(64)  NOT NULL COMMENT '事件类型',
    `conversation_id`         BIGINT       NOT NULL COMMENT '会话ID',
    `message_id`              BIGINT       DEFAULT NULL COMMENT '消息ID',
    `seq_no`                  BIGINT       DEFAULT NULL COMMENT '消息序号或读位点',
    `actor_user_id`           BIGINT       DEFAULT NULL COMMENT '触发用户ID',
    `recipient_user_ids_json` JSON         NOT NULL COMMENT '推送目标用户ID数组',
    `payload_json`            JSON         NOT NULL COMMENT 'WebSocket事件JSON',
    `status`                  VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENDING/SENT/FAILED/DEAD',
    `retry_count`             INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_at`           DATETIME     NOT NULL COMMENT '下次可投递时间',
    `last_error`              VARCHAR(512) DEFAULT NULL COMMENT '最近失败原因',
    `claimed_at`              DATETIME     DEFAULT NULL COMMENT '本次抢占时间',
    `sent_at`                 DATETIME     DEFAULT NULL COMMENT '成功投递时间',
    `created_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_im_event_outbox_due` (`status`, `next_retry_at`, `id`),
    KEY `idx_im_event_outbox_conversation` (`conversation_id`, `created_at`),
    KEY `idx_im_event_outbox_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IM WebSocket事件outbox';
