-- ============================================================
-- Workforce Hub - 群文件上传归属策略
-- 版本: V18
-- 说明:
--   - IM_GROUP_FILE 用于群聊文件空间，按群会话隔离，不进入个人文件中心
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5005,
        'IM_GROUP_FILE_DEFAULT_100M',
        '群文件默认上传策略',
        'IM_GROUP_FILE',
        104857600,
        '["txt","md","pdf","doc","docx","xls","xlsx","ppt","pptx","csv","png","jpg","jpeg","gif","webp","zip","rar","7z"]',
        '["text/plain","text/markdown","text/csv","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","image/png","image/jpeg","image/gif","image/webp","application/zip","application/x-rar-compressed","application/x-7z-compressed"]',
        'ENABLED',
        NULL
    );
