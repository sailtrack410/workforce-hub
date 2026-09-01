ALTER TABLE `agt_task`
    ADD COLUMN `failure_code` VARCHAR(64) DEFAULT NULL COMMENT '失败分类码' AFTER `finished_at`,
    ADD KEY `idx_agt_task_status_created` (`status`, `created_at`, `id`),
    ADD KEY `idx_agt_task_failure_code` (`failure_code`, `status`, `created_at`),
    ADD KEY `idx_agt_task_triggered_by` (`triggered_by_type`, `triggered_by_id`, `created_at`);

ALTER TABLE `agt_tool_call_log`
    ADD KEY `idx_agt_tool_call_result_task` (`result_status`, `task_id`);

ALTER TABLE `agt_task_timeout_job_run`
    ADD KEY `idx_agt_timeout_run_trigger_status_started` (`trigger_type`, `status`, `started_at`);

ALTER TABLE `kb_ingest_job`
    ADD KEY `idx_kb_ingest_status_finished` (`status`, `finished_at`, `id`);
