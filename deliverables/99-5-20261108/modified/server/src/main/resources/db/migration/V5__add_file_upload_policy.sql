-- ============================================================
-- Workforce Hub - 文件中心上传策略配置
-- 版本: V5
-- 说明:
--   - 新增按文件归属场景配置的上传限制
--   - 第一版先内置 PERSONAL_FILE 个人文件策略
--   - 服务层在申请上传票据前校验大小、扩展名和 MIME 类型
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_file_upload_policy` (
    `id`                  BIGINT       NOT NULL COMMENT '主键',
    `policy_code`         VARCHAR(128) NOT NULL COMMENT '策略编码',
    `policy_name`         VARCHAR(128) NOT NULL COMMENT '策略名称',
    `owner_type`          VARCHAR(64)  NOT NULL COMMENT '文件归属场景，如 PERSONAL_FILE/WF_REQUEST/GROUP_MESSAGE',
    `max_file_size_bytes` BIGINT       NOT NULL COMMENT '单文件大小上限',
    `allowed_extensions`  JSON         DEFAULT NULL COMMENT '允许的文件扩展名，小写数组；空表示不限制',
    `allowed_mime_types`  JSON         DEFAULT NULL COMMENT '允许的 MIME 类型，小写数组；空表示不限制',
    `status`              VARCHAR(32)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态: ENABLED/DISABLED',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`          BIGINT       DEFAULT NULL,
    `updated_by`          BIGINT       DEFAULT NULL,
    `is_deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `active_owner_key`    VARCHAR(64)
        GENERATED ALWAYS AS (CASE WHEN `is_deleted` = 0 AND `status` = 'ENABLED' THEN `owner_type` ELSE NULL END) STORED
        COMMENT '启用策略唯一键，禁用或已删除策略不参与唯一约束',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_upload_policy_code` (`policy_code`),
    UNIQUE KEY `uk_upload_policy_owner_active` (`active_owner_key`),
    KEY `idx_upload_policy_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件中心上传场景策略';

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5001,
        'PERSONAL_FILE_DEFAULT_100M',
        '个人文件默认上传策略',
        'PERSONAL_FILE',
        104857600,
        '["txt","md","pdf","doc","docx","xls","xlsx","ppt","pptx","csv","png","jpg","jpeg","gif","webp","zip","rar","7z"]',
        '["text/plain","text/markdown","text/csv","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","image/png","image/jpeg","image/gif","image/webp","application/zip","application/x-rar-compressed","application/x-7z-compressed"]',
        'ENABLED',
        NULL
    );
