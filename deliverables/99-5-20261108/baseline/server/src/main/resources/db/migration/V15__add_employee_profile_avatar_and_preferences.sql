-- ============================================================
-- Workforce Hub - 员工头像与系统偏好
-- 版本: V15
-- 说明:
--   - org_user 增加头像附件引用，支持员工自助换头像
--   - 新建 emp_user_preference 表持久化主题 / 会话密度 / 列表宽度，配合前端云端同步
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `org_user`
    ADD COLUMN `avatar_attachment_ref_id` BIGINT DEFAULT NULL COMMENT '头像附件引用ID' AFTER `mobile`,
    ADD KEY `idx_avatar_attachment` (`avatar_attachment_ref_id`);

CREATE TABLE `emp_user_preference` (
    `user_id`        BIGINT       NOT NULL COMMENT '员工用户ID, 对应 org_user.id',
    `theme`          VARCHAR(16)  NOT NULL DEFAULT 'light' COMMENT '主题: light/dark',
    `message_density` VARCHAR(16) NOT NULL DEFAULT 'cozy'  COMMENT '会话密度: cozy/compact',
    `list_width`     INT          NOT NULL DEFAULT 400     COMMENT '列表面板宽度(px), 限制在 [384, 520]',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工系统偏好持久化';
