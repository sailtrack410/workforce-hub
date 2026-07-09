DROP TEMPORARY TABLE IF EXISTS seed_company_policy_kb;

CREATE TEMPORARY TABLE seed_company_policy_kb AS
SELECT id AS space_id, space_code
FROM kb_space
WHERE is_deleted = 0
  AND status = 'ACTIVE'
  AND (
      space_name IN ('公司制度', '公司制度知识库')
      OR space_code IN ('COMPANY_POLICY', 'COMPANY_RULES')
  )
ORDER BY CASE
    WHEN space_name = '公司制度' THEN 1
    WHEN space_name = '公司制度知识库' THEN 2
    WHEN space_code = 'COMPANY_POLICY' THEN 3
    ELSE 4
END, id DESC
LIMIT 1;

SET @seed_company_policy_kb_id = (SELECT space_id FROM seed_company_policy_kb LIMIT 1);
SET @seed_company_policy_kb_code = COALESCE((SELECT space_code FROM seed_company_policy_kb LIMIT 1), 'COMPANY_POLICY');

DROP TEMPORARY TABLE IF EXISTS seed_more_oa_workflow_templates;

CREATE TEMPORARY TABLE seed_more_oa_workflow_templates (
    process_type_id BIGINT NOT NULL,
    process_code VARCHAR(64) NOT NULL,
    process_name VARCHAR(128) NOT NULL,
    biz_type VARCHAR(64) NOT NULL,
    template_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    version_id BIGINT NOT NULL,
    flowable_definition_key VARCHAR(128) NOT NULL,
    form_schema_json JSON NOT NULL,
    rule_snapshot_json JSON NOT NULL
);

INSERT INTO seed_more_oa_workflow_templates (
    process_type_id, process_code, process_name, biz_type,
    template_id, template_code, template_name, version_id, flowable_definition_key,
    form_schema_json, rule_snapshot_json
) VALUES
(
    49006, 'CERTIFICATE', '证明开具流程', 'CERTIFICATE',
    49106, 'CERTIFICATE_MANAGER_APPROVAL', '证明开具直属上级审批', 49206, 'certificate_manager_approval',
    JSON_OBJECT(
        'formKey', 'certificate',
        'title', '证明开具',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'certificateType', 'label', '证明类型', 'type', 'select', 'required', TRUE),
            JSON_OBJECT('key', 'recipient', 'label', '接收方', 'type', 'text', 'required', FALSE),
            JSON_OBJECT('key', 'deliveryMode', 'label', '交付方式', 'type', 'select', 'required', TRUE),
            JSON_OBJECT('key', 'purpose', 'label', '申请用途', 'type', 'textarea', 'required', TRUE),
            JSON_OBJECT('key', 'supportingAttachments', 'label', '补充材料', 'type', 'attachment', 'required', FALSE)
        )
    ),
    JSON_OBJECT(
        'policySource', '公司制度',
        'knowledgeBase', JSON_OBJECT(
            'spaceId', @seed_company_policy_kb_id,
            'spaceCode', @seed_company_policy_kb_code,
            'spaceName', '公司制度'
        ),
        'preReview', JSON_OBJECT('mode', 'KNOWLEDGE_BASE', 'required', TRUE),
        'checks', JSON_ARRAY('证明类型是否明确', '用途是否符合出具范围', '接收方信息是否完整', '补充材料是否必要')
    )
),
(
    49007, 'BORROW', '备用金借款流程', 'BORROW',
    49107, 'BORROW_MANAGER_APPROVAL', '备用金借款直属上级审批', 49207, 'borrow_manager_approval',
    JSON_OBJECT(
        'formKey', 'borrow',
        'title', '备用金借款',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'amount', 'label', '借款金额', 'type', 'money', 'required', TRUE),
            JSON_OBJECT('key', 'useDate', 'label', '使用日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'expectedRepayAt', 'label', '预计归还日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'purpose', 'label', '借款用途', 'type', 'textarea', 'required', TRUE),
            JSON_OBJECT('key', 'supportingAttachments', 'label', '预算/说明附件', 'type', 'attachment', 'required', FALSE)
        )
    ),
    JSON_OBJECT(
        'policySource', '公司制度',
        'knowledgeBase', JSON_OBJECT(
            'spaceId', @seed_company_policy_kb_id,
            'spaceCode', @seed_company_policy_kb_code,
            'spaceName', '公司制度'
        ),
        'preReview', JSON_OBJECT('mode', 'KNOWLEDGE_BASE', 'required', TRUE),
        'checks', JSON_ARRAY('借款用途是否明确', '金额是否符合预算或权限', '预计归还日期是否合理', '核销材料是否齐备')
    )
),
(
    49008, 'PROJECT', '项目立项流程', 'PROJECT',
    49108, 'PROJECT_MANAGER_APPROVAL', '项目立项直属上级审批', 49208, 'project_manager_approval',
    JSON_OBJECT(
        'formKey', 'project',
        'title', '项目立项',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'projectName', 'label', '项目名称', 'type', 'text', 'required', TRUE),
            JSON_OBJECT('key', 'startAt', 'label', '开始日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'endAt', 'label', '结束日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'budgetAmount', 'label', '项目预算', 'type', 'money', 'required', TRUE),
            JSON_OBJECT('key', 'goal', 'label', '立项目标', 'type', 'textarea', 'required', TRUE),
            JSON_OBJECT('key', 'projectAttachments', 'label', '立项材料', 'type', 'attachment', 'required', FALSE)
        )
    ),
    JSON_OBJECT(
        'policySource', '公司制度',
        'knowledgeBase', JSON_OBJECT(
            'spaceId', @seed_company_policy_kb_id,
            'spaceCode', @seed_company_policy_kb_code,
            'spaceName', '公司制度'
        ),
        'preReview', JSON_OBJECT('mode', 'KNOWLEDGE_BASE', 'required', TRUE),
        'checks', JSON_ARRAY('立项目标是否明确', '周期和预算是否合理', '负责人和参与范围是否清晰', '是否需要跨部门资源确认')
    )
);

INSERT INTO wf_process_type (
    id, process_code, process_name, biz_type, status, created_by, updated_by, is_deleted
)
SELECT process_type_id, process_code, process_name, biz_type, 'ACTIVE', 1, 1, 0
FROM seed_more_oa_workflow_templates
ON DUPLICATE KEY UPDATE
    process_name = VALUES(process_name),
    biz_type = VALUES(biz_type),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO wf_template (
    id, process_type_id, template_code, template_name, status, created_by, updated_by, is_deleted
)
SELECT s.template_id, wpt.id, s.template_code, s.template_name, 'ACTIVE', 1, 1, 0
FROM seed_more_oa_workflow_templates s
JOIN wf_process_type wpt ON wpt.process_code = s.process_code AND wpt.is_deleted = 0
ON DUPLICATE KEY UPDATE
    process_type_id = VALUES(process_type_id),
    template_name = VALUES(template_name),
    status = VALUES(status),
    updated_by = VALUES(updated_by),
    is_deleted = VALUES(is_deleted);

INSERT INTO wf_template_version (
    id, template_id, version_no, flowable_definition_key, flowable_definition_id,
    form_schema_json, rule_snapshot_json, published_by, published_at,
    effective_from, effective_to, status, created_by, updated_by
)
SELECT s.version_id, wt.id, 1, s.flowable_definition_key, NULL,
       s.form_schema_json, s.rule_snapshot_json, 1, NOW(),
       NOW(), NULL, 'PUBLISHED', 1, 1
FROM seed_more_oa_workflow_templates s
JOIN wf_template wt ON wt.template_code = s.template_code AND wt.is_deleted = 0
ON DUPLICATE KEY UPDATE
    flowable_definition_key = VALUES(flowable_definition_key),
    flowable_definition_id = COALESCE(wf_template_version.flowable_definition_id, VALUES(flowable_definition_id)),
    form_schema_json = VALUES(form_schema_json),
    rule_snapshot_json = VALUES(rule_snapshot_json),
    published_by = COALESCE(wf_template_version.published_by, VALUES(published_by)),
    published_at = COALESCE(wf_template_version.published_at, VALUES(published_at)),
    effective_from = COALESCE(wf_template_version.effective_from, VALUES(effective_from)),
    effective_to = NULL,
    status = VALUES(status),
    updated_by = VALUES(updated_by);

UPDATE wf_template_version v
JOIN wf_template wt ON wt.id = v.template_id AND wt.is_deleted = 0
JOIN seed_more_oa_workflow_templates s ON s.template_code = wt.template_code
SET v.status = 'ARCHIVED',
    v.effective_to = COALESCE(v.effective_to, NOW()),
    v.updated_by = 1
WHERE v.version_no <> 1
  AND v.status = 'PUBLISHED'
  AND (v.effective_to IS NULL OR v.effective_to > NOW());

DROP TEMPORARY TABLE IF EXISTS seed_more_oa_workflow_templates;
DROP TEMPORARY TABLE IF EXISTS seed_company_policy_kb;
