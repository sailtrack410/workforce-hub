-- ============================================================
-- Workforce Hub - 新增数据字典表与种子数据
-- 版本: V58
-- 说明:
--   - 新增 sys_dict 数据字典表，支撑通用枚举的名称/标签集中维护
--   - 幂等种子：common_status、alert_action 两组字典条目
--   - 幂等种子：ADMIN_DICT_MANAGE 后台权限并绑定超级管理员角色
-- ============================================================

SET NAMES utf8mb4;

CREATE TABLE `sys_dict` (
    `id`         BIGINT       NOT NULL COMMENT '主键',
    `dict_type`  VARCHAR(64)  NOT NULL COMMENT '字典类型编码',
    `item_code`  VARCHAR(64)  NOT NULL COMMENT '条目编码',
    `item_label` VARCHAR(128) NOT NULL COMMENT '条目显示名称',
    `sort_no`    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1启用/0停用',
    `remark`     VARCHAR(256) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `created_by` BIGINT       DEFAULT NULL COMMENT '创建人ID',
    `updated_by` BIGINT       DEFAULT NULL COMMENT '更新人ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type_item` (`dict_type`, `item_code`),
    KEY `idx_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据字典';

-- 通用状态字典（id 段 9301 起，93xx 仅 V37 在 wf_template_version 使用，无冲突）
INSERT INTO sys_dict
    (id, dict_type, item_code, item_label, sort_no, status, remark, created_by, updated_by)
VALUES
    (9301, 'common_status', 'ACTIVE', '启用', 1, 1, NULL, NULL, NULL),
    (9302, 'common_status', 'INACTIVE', '停用', 2, 1, NULL, NULL, NULL),
    (9303, 'common_status', 'PENDING', '待处理', 3, 1, NULL, NULL, NULL),
    (9304, 'alert_action', 'ACKNOWLEDGE', '确认', 1, 1, NULL, NULL, NULL),
    (9305, 'alert_action', 'RESOLVE', '已处理', 2, 1, NULL, NULL, NULL),
    (9306, 'alert_action', 'IGNORE', '忽略', 3, 1, NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE
    item_label = VALUES(item_label),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    remark = VALUES(remark);

-- 数据字典管理权限
-- id 选择说明：仓库内 70xx 段已占用到 7010（V42 通知渠道，V56 修复复用冲突）；
-- 但历史版本 V58（插件管理，见提交 52090466）用 MAX(id)+1 动态占位，
-- 已执行过旧 V58 的库上 7011/6111 可能被 ADMIN_PLUGIN_MANAGE 占用，
-- 因此这里沿用 V56 的守卫式插入（按 perm_code 幂等 + MAX(id)+1 取号），避免覆盖既有权限。
UPDATE admin_permission
SET perm_name = '数据字典管理',
    module_code = 'SYSTEM',
    action_code = 'MANAGE',
    status = 'ACTIVE',
    updated_by = NULL,
    is_deleted = 0
WHERE perm_code = 'ADMIN_DICT_MANAGE';

INSERT INTO admin_permission (
    id, perm_code, perm_name, module_code, action_code, status, created_by, updated_by, is_deleted
)
SELECT seq.next_id, 'ADMIN_DICT_MANAGE', '数据字典管理',
       'SYSTEM', 'MANAGE', 'ACTIVE', NULL, NULL, 0
FROM (
    SELECT COALESCE(MAX(id), 7000) + 1 AS next_id
    FROM admin_permission
) seq
WHERE NOT EXISTS (
    SELECT 1 FROM admin_permission WHERE perm_code = 'ADMIN_DICT_MANAGE'
);

-- 绑定超级管理员角色（参考 V42/V46：SUPER_ADMIN；关系 id 同样守卫式取号，禁止覆盖旧关系）
INSERT INTO admin_role_permission (
    id, role_id, permission_id, created_by
)
SELECT seq.next_id, r.id, p.id, NULL
FROM admin_role r
JOIN admin_permission p ON p.perm_code = 'ADMIN_DICT_MANAGE' AND p.is_deleted = 0
CROSS JOIN (
    SELECT COALESCE(MAX(id), 6100) + 1 AS next_id
    FROM admin_role_permission
) seq
WHERE r.role_code = 'SUPER_ADMIN'
  AND r.is_deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM admin_role_permission existing
      WHERE existing.role_id = r.id
        AND existing.permission_id = p.id
  );
