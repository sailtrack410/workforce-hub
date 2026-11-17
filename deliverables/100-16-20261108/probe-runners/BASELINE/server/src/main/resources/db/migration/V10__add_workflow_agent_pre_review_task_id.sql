ALTER TABLE `wf_request`
    ADD COLUMN `agent_pre_review_task_id` BIGINT DEFAULT NULL COMMENT 'Agent预审任务ID'
        AFTER `flowable_process_instance_id`,
    ADD KEY `idx_agent_pre_review_task` (`agent_pre_review_task_id`);
