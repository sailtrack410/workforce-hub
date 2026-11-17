-- 员工好友关系表（单向：user_id 把 friend_user_id 加为好友）
CREATE TABLE IF NOT EXISTS `ct_friend` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '拥有者',
  `friend_user_id` BIGINT NOT NULL COMMENT '被加为好友的用户',
  `remark` VARCHAR(60) DEFAULT NULL COMMENT '好友备注名',
  `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / BLOCKED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `created_by` BIGINT DEFAULT NULL,
  `updated_by` BIGINT DEFAULT NULL,
  `is_deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_user_id`),
  KEY `idx_friend_user` (`friend_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工好友关系';
