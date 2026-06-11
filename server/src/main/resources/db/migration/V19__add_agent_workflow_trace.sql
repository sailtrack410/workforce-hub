ALTER TABLE `agt_task`
    ADD COLUMN `workflow_snapshot_json` JSON DEFAULT NULL COMMENT '执行时工作流快照' AFTER `waiting_deadline_at`,
    ADD COLUMN `execution_trace_json` JSON DEFAULT NULL COMMENT '工作流执行轨迹' AFTER `workflow_snapshot_json`;
