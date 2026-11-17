ALTER TABLE `att_punch_record`
    ADD COLUMN `punch_date` DATE GENERATED ALWAYS AS (DATE(`punch_at`)) STORED COMMENT '打卡日期';

ALTER TABLE `att_punch_record`
    ADD UNIQUE KEY `uk_att_punch_user_date_type` (`user_id`, `punch_date`, `punch_type`);
