-- 版本: V57
-- 说明: ops_alert_channel / ops_alert_rule 补齐审计列 created_by/updated_by，写入侧由 AdminAlertService 落当前操作人。
-- 前置状态：表结构对齐（target_ref 255 / action_type 64 / operator_id+created_at）已由本次 V35 收编与主库手工对齐完成；
-- 全新库由 V35'（=V40 同构 DDL）建表后走到本迁移。两库终点一致。

ALTER TABLE `ops_alert_channel`
    ADD COLUMN `created_by` BIGINT DEFAULT NULL,
    ADD COLUMN `updated_by` BIGINT DEFAULT NULL;

ALTER TABLE `ops_alert_rule`
    ADD COLUMN `created_by` BIGINT DEFAULT NULL,
    ADD COLUMN `updated_by` BIGINT DEFAULT NULL;
