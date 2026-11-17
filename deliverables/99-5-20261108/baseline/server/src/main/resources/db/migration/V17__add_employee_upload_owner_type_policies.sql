-- ============================================================
-- Workforce Hub - 员工端上传归属场景补充
-- 版本: V17
-- 说明:
--   - USER_AVATAR 用于个人头像，不进入个人文件中心
--   - IM_MESSAGE_PENDING 用于聊天即时附件，发送后克隆为 IM_MESSAGE
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5003,
        'USER_AVATAR_DEFAULT_10M',
        '员工头像默认上传策略',
        'USER_AVATAR',
        10485760,
        '["png","jpg","jpeg","gif","webp"]',
        '["image/png","image/jpeg","image/gif","image/webp"]',
        'ENABLED',
        NULL
    ),
    (
        5004,
        'IM_MESSAGE_PENDING_DEFAULT_100M',
        '聊天即时附件默认上传策略',
        'IM_MESSAGE_PENDING',
        104857600,
        '["txt","md","pdf","doc","docx","xls","xlsx","ppt","pptx","csv","png","jpg","jpeg","gif","webp","zip","rar","7z"]',
        '["text/plain","text/markdown","text/csv","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","image/png","image/jpeg","image/gif","image/webp","application/zip","application/x-rar-compressed","application/x-7z-compressed"]',
        'ENABLED',
        NULL
    );
