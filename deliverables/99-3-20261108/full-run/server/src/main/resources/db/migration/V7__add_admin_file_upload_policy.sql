-- ============================================================
-- Workforce Hub - 后台治理文件上传策略
-- 版本: V7
-- 说明:
--   - 为后台上传最小闭环补充 ADMIN_FILE 场景策略
--   - 第一版后台治理文件只占企业总池，不占员工个人空间
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO `sys_file_upload_policy`
    (`id`, `policy_code`, `policy_name`, `owner_type`, `max_file_size_bytes`,
     `allowed_extensions`, `allowed_mime_types`, `status`, `created_by`)
VALUES
    (
        5002,
        'ADMIN_FILE_DEFAULT_200M',
        '后台治理文件默认上传策略',
        'ADMIN_FILE',
        209715200,
        '["txt","md","pdf","doc","docx","xls","xlsx","ppt","pptx","csv","png","jpg","jpeg","gif","webp","zip","rar","7z"]',
        '["text/plain","text/markdown","text/csv","application/pdf","application/msword","application/vnd.openxmlformats-officedocument.wordprocessingml.document","application/vnd.ms-excel","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/vnd.ms-powerpoint","application/vnd.openxmlformats-officedocument.presentationml.presentation","image/png","image/jpeg","image/gif","image/webp","application/zip","application/x-rar-compressed","application/x-7z-compressed"]',
        'ENABLED',
        NULL
    );
