ALTER TABLE `agt_agent_version`
    ADD COLUMN `workflow_test_signature` VARCHAR(64) DEFAULT NULL COMMENT '最近成功模拟测试签名' AFTER `publish_status`,
    ADD COLUMN `workflow_tested_at` DATETIME DEFAULT NULL COMMENT '最近成功模拟测试时间' AFTER `workflow_test_signature`;
