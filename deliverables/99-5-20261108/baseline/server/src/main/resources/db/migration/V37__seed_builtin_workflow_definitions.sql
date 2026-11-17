SET @builtin_leave_process_type_id = COALESCE(
    (SELECT `id` FROM `wf_process_type` WHERE `process_code` = 'LEAVE' AND `is_deleted` = 0 LIMIT 1),
    9101
);
SET @builtin_clock_process_type_id = COALESCE(
    (SELECT `id` FROM `wf_process_type` WHERE `process_code` = 'CLOCK_CORRECTION' AND `is_deleted` = 0 LIMIT 1),
    9111
);

INSERT INTO `wf_process_type`
    (`id`, `process_code`, `process_name`, `biz_type`, `status`, `created_by`, `updated_by`)
VALUES
    (@builtin_leave_process_type_id, 'LEAVE', '请假流程', 'LEAVE', 'ACTIVE', 0, 0),
    (@builtin_clock_process_type_id, 'CLOCK_CORRECTION', '补卡流程', 'CLOCK_CORRECTION', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `process_name` = VALUES(`process_name`),
    `biz_type` = VALUES(`biz_type`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`);

SET @builtin_leave_template_id = COALESCE(
    (SELECT `id` FROM `wf_template` WHERE `template_code` = 'LEAVE_MANAGER_APPROVAL' AND `is_deleted` = 0 LIMIT 1),
    9201
);
SET @builtin_clock_template_id = COALESCE(
    (SELECT `id` FROM `wf_template` WHERE `template_code` = 'CLOCK_CORRECTION_MANAGER_APPROVAL' AND `is_deleted` = 0 LIMIT 1),
    9211
);

INSERT INTO `wf_template`
    (`id`, `process_type_id`, `template_code`, `template_name`, `status`, `created_by`, `updated_by`)
VALUES
    (@builtin_leave_template_id, @builtin_leave_process_type_id, 'LEAVE_MANAGER_APPROVAL', '请假直属上级审批', 'ACTIVE', 0, 0),
    (@builtin_clock_template_id, @builtin_clock_process_type_id, 'CLOCK_CORRECTION_MANAGER_APPROVAL', '补卡直属上级审批', 'ACTIVE', 0, 0)
ON DUPLICATE KEY UPDATE
    `process_type_id` = VALUES(`process_type_id`),
    `template_name` = VALUES(`template_name`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`);

INSERT INTO `wf_template_version`
    (`id`, `template_id`, `version_no`, `flowable_definition_key`, `form_schema_json`,
     `rule_snapshot_json`, `published_by`, `published_at`, `effective_from`, `status`,
     `created_by`, `updated_by`)
VALUES
    (9301, @builtin_leave_template_id, 1, 'leave_manager_approval',
     JSON_OBJECT('bizType', 'LEAVE'),
     JSON_OBJECT('approval', 'manager_approval', 'preReview', 'agent_pre_review'),
     0, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY), 'PUBLISHED', 0, 0),
    (9311, @builtin_clock_template_id, 1, 'clock_correction_manager_approval',
     JSON_OBJECT('bizType', 'CLOCK_CORRECTION'),
     JSON_OBJECT('approval', 'manager_approval'),
     0, NOW(), DATE_SUB(NOW(), INTERVAL 1 DAY), 'PUBLISHED', 0, 0)
ON DUPLICATE KEY UPDATE
    `flowable_definition_key` = VALUES(`flowable_definition_key`),
    `form_schema_json` = VALUES(`form_schema_json`),
    `rule_snapshot_json` = VALUES(`rule_snapshot_json`),
    `published_by` = VALUES(`published_by`),
    `published_at` = VALUES(`published_at`),
    `effective_from` = VALUES(`effective_from`),
    `status` = VALUES(`status`),
    `updated_by` = VALUES(`updated_by`);
