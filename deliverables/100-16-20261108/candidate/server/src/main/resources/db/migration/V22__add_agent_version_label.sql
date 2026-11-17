ALTER TABLE `agt_agent_version`
    ADD COLUMN `version_label` VARCHAR(64) DEFAULT NULL COMMENT '显示版本号' AFTER `version_no`,
    ADD UNIQUE KEY `uk_agent_version_label` (`agent_id`, `version_label`);
