-- ============================================================
-- Workforce Hub - 登录页品牌标语字典种子
-- 版本: V63
-- 说明:
--   - 在 sys_dict 中新增 dict_type = 'brand_login_tagline' 的默认标语
--   - 员工端登录页轮播展示，管理端“企业设置”可维护
--   - 幂等：按 uk_dict_type_item 冲突时仅更新文案与排序
-- ============================================================

SET NAMES utf8mb4;

INSERT INTO sys_dict
    (id, dict_type, item_code, item_label, sort_no, status, remark, created_by, updated_by)
VALUES
    (9307, 'brand_login_tagline', 'T1', '消息、待办、审批与智能助手，在同一个工作区内持续协同。', 1, 1, '登录页轮播标语', NULL, NULL),
    (9308, 'brand_login_tagline', 'T2', '把重复交给系统，把判断留给专业。', 2, 1, '登录页轮播标语', NULL, NULL),
    (9309, 'brand_login_tagline', 'T3', '每一次打卡与审批，都沉淀为组织的秩序。', 3, 1, '登录页轮播标语', NULL, NULL),
    (9310, 'brand_login_tagline', 'T4', 'AI 助手就在消息列表里，随时把知识送到手边。', 4, 1, '登录页轮播标语', NULL, NULL)
ON DUPLICATE KEY UPDATE
    item_label = VALUES(item_label),
    sort_no = VALUES(sort_no),
    status = VALUES(status),
    remark = VALUES(remark);
