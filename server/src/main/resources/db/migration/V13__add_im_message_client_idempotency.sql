-- ============================================================
-- Workforce Hub - IM 消息客户端幂等键
-- 版本: V13
-- 说明:
--   - 为员工端消息发送补充 client_message_id
--   - 以会话 + 发送人 + client_message_id 建唯一约束，支撑重试幂等
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `im_message`
    ADD COLUMN `client_message_id` VARCHAR(128) DEFAULT NULL COMMENT '客户端消息幂等键' AFTER `message_type`;

ALTER TABLE `im_message`
    ADD UNIQUE KEY `uk_im_message_client_message`
        (`conversation_id`, `sender_type`, `sender_id`, `client_message_id`);
