ALTER TABLE personal_todo ADD COLUMN all_day BOOLEAN NOT NULL DEFAULT FALSE COMMENT '全天事项';
ALTER TABLE personal_todo ADD COLUMN reminder_minutes INT NULL COMMENT '提前提醒分钟数，NULL为不提醒';
ALTER TABLE personal_todo ADD COLUMN reminder_at DATETIME NULL COMMENT '计划提醒时间';
ALTER TABLE personal_todo ADD COLUMN reminded_at DATETIME NULL COMMENT '已发送提醒时间';

CREATE INDEX idx_personal_todo_reminder ON personal_todo (status, reminded_at, reminder_at);
