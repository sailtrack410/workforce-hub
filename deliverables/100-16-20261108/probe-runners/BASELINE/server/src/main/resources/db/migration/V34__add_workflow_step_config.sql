CREATE TABLE IF NOT EXISTS `wf_step_config` (
    `step_key` VARCHAR(64) NOT NULL COMMENT '节点Key',
    `step_name` VARCHAR(128) NOT NULL COMMENT '节点名称',
    PRIMARY KEY (`step_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程节点配置';

INSERT INTO `wf_step_config` (`step_key`, `step_name`) VALUES
    ('manager_approval', '直属上级审批'),
    ('agent_pre_review', 'Agent预审')
ON DUPLICATE KEY UPDATE
    `step_name` = VALUES(`step_name`);
