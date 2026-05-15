-- ============================================================
-- Workforce Hub - 个人中心扩展：个性签名 / 在线状态 / 通知 / 外观
-- 版本: V16
-- 说明:
--   - emp_user_preference 增加个性签名、在线状态、通知开关、外观个性化字段
--   - 沿用 V15 标量列风格，不引入 JSON column
-- ============================================================

SET NAMES utf8mb4;

ALTER TABLE `emp_user_preference`
    ADD COLUMN `signature`          VARCHAR(140) NOT NULL DEFAULT ''         COMMENT '个性签名'           AFTER `list_width`,
    ADD COLUMN `presence_status`    VARCHAR(16)  NOT NULL DEFAULT 'ONLINE'   COMMENT '在线状态: ONLINE/BUSY/AWAY/DND' AFTER `signature`,
    ADD COLUMN `notif_sound`        TINYINT(1)   NOT NULL DEFAULT 1          COMMENT '消息声音提醒'       AFTER `presence_status`,
    ADD COLUMN `notif_desktop`      TINYINT(1)   NOT NULL DEFAULT 1          COMMENT '桌面通知'           AFTER `notif_sound`,
    ADD COLUMN `notif_mention_only` TINYINT(1)   NOT NULL DEFAULT 0          COMMENT '仅 @ 我提醒'        AFTER `notif_desktop`,
    ADD COLUMN `notif_unread_badge` TINYINT(1)   NOT NULL DEFAULT 1          COMMENT '未读角标'           AFTER `notif_mention_only`,
    ADD COLUMN `accent_color`       VARCHAR(16)  NOT NULL DEFAULT 'indigo'   COMMENT '强调色: indigo/violet/blue/teal/amber/rose' AFTER `notif_unread_badge`,
    ADD COLUMN `font_scale`         VARCHAR(16)  NOT NULL DEFAULT 'medium'   COMMENT '字号: small/medium/large' AFTER `accent_color`,
    ADD COLUMN `bubble_style`       VARCHAR(16)  NOT NULL DEFAULT 'rounded'  COMMENT '气泡样式: rounded/sharp' AFTER `font_scale`;
