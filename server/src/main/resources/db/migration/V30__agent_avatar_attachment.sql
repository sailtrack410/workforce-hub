-- ============================================================
-- Workforce Hub - Agent 头像
-- 版本: V30
-- 说明:
--   - Agent 主定义增加头像附件引用，前端无图片时使用默认图标
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `agt_agent`
    ADD COLUMN `avatar_attachment_ref_id` BIGINT DEFAULT NULL COMMENT 'Agent头像附件引用ID' AFTER `agent_name`,
    ADD KEY `idx_agent_avatar_attachment` (`avatar_attachment_ref_id`);
