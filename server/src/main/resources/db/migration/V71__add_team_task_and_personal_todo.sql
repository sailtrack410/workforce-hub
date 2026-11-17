-- Workforce Hub - 团队任务与个人待办
-- V70

CREATE TABLE `team_task` (
    `task_id`     BIGINT       NOT NULL COMMENT '主键',
    `title`       VARCHAR(128) NOT NULL COMMENT '标题',
    `description` VARCHAR(1024) DEFAULT NULL COMMENT '描述',
    `assignee_id` BIGINT       NOT NULL COMMENT '负责人员工ID',
    `creator_id`  BIGINT       NOT NULL COMMENT '创建人ID',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'TODO' COMMENT '状态: TODO/IN_PROGRESS/IN_REVIEW/DONE',
    `start_date`  DATE         DEFAULT NULL COMMENT '开始日期',
    `end_date`    DATE         DEFAULT NULL COMMENT '结束日期',
    `group_id`    VARCHAR(64)  DEFAULT NULL COMMENT '关联部门/群ID',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`task_id`),
    KEY `idx_team_task_assignee` (`assignee_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队任务';

CREATE TABLE `personal_todo` (
    `todo_id`      BIGINT      NOT NULL COMMENT '主键',
    `owner_id`     BIGINT      NOT NULL COMMENT '所属用户ID',
    `title`        VARCHAR(128) NOT NULL COMMENT '标题',
    `category`     VARCHAR(32) NOT NULL DEFAULT 'TASK' COMMENT '分类: APPROVAL/MEETING/TASK/REMINDER',
    `status`       VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/DONE',
    `due_at`       DATETIME    DEFAULT NULL COMMENT '截止时间',
    `note`         VARCHAR(1024) DEFAULT NULL COMMENT '备注',
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `completed_at` DATETIME    DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`todo_id`),
    KEY `idx_personal_todo_owner` (`owner_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='个人待办';
