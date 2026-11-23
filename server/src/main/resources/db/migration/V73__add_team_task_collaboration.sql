-- 团队任务清单、子任务、关注与讨论记录；保留现有任务及权限关系。
CREATE TABLE team_task_list (
    list_id BIGINT NOT NULL PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_list_owner (owner_id)
);

CREATE TABLE team_task_subtask (
    subtask_id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    title VARCHAR(128) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_task_subtask (task_id, created_at)
);

CREATE TABLE team_task_watch (
    task_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, user_id)
);

CREATE TABLE team_task_activity (
    activity_id BIGINT NOT NULL PRIMARY KEY,
    task_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_task_activity (task_id, created_at, activity_id)
);

CREATE TABLE team_task_attachment (
    task_id BIGINT NOT NULL,
    attachment_ref_id BIGINT NOT NULL,
    added_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, attachment_ref_id)
);
