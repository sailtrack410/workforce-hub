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

DROP TEMPORARY TABLE IF EXISTS seed_oa_workflow_templates;

CREATE TEMPORARY TABLE seed_oa_workflow_templates (
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

INSERT INTO seed_oa_workflow_templates (
    process_type_id, process_code, process_name, biz_type,
    template_id, template_code, template_name, version_id, flowable_definition_key,
    form_schema_json, rule_snapshot_json
) VALUES
(
    49001, 'EXPENSE', '费用报销流程', 'EXPENSE',
    49101, 'EXPENSE_MANAGER_APPROVAL', '费用报销直属上级审批', 49201, 'expense_manager_approval',
    JSON_OBJECT(
        'formKey', 'expense',
        'title', '费用报销',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'expenseCategory', 'label', '费用类型', 'type', 'select', 'required', TRUE),
            JSON_OBJECT('key', 'amount', 'label', '报销金额', 'type', 'money', 'required', TRUE),
            JSON_OBJECT('key', 'occurredAt', 'label', '发生日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'reason', 'label', '报销事由', 'type', 'textarea', 'required', TRUE),
            JSON_OBJECT('key', 'invoiceAttachments', 'label', '发票/凭证', 'type', 'attachment', 'required', TRUE)
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
        'checks', JSON_ARRAY('费用类型是否符合制度', '金额与凭证是否一致', '发票/付款凭证是否齐全', '是否需要预算或上级补充审批')
    )
),
(
    49002, 'BUSINESS_TRIP', '出差申请流程', 'BUSINESS_TRIP',
    49102, 'BUSINESS_TRIP_MANAGER_APPROVAL', '出差申请直属上级审批', 49202, 'business_trip_manager_approval',
    JSON_OBJECT(
        'formKey', 'businessTrip',
        'title', '出差申请',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'destination', 'label', '出差地点', 'type', 'text', 'required', TRUE),
            JSON_OBJECT('key', 'startAt', 'label', '开始时间', 'type', 'datetime', 'required', TRUE),
            JSON_OBJECT('key', 'endAt', 'label', '结束时间', 'type', 'datetime', 'required', TRUE),
            JSON_OBJECT('key', 'budgetAmount', 'label', '预计预算', 'type', 'money', 'required', FALSE),
            JSON_OBJECT('key', 'reason', 'label', '出差事由', 'type', 'textarea', 'required', TRUE)
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
        'checks', JSON_ARRAY('出差周期是否合理', '目的地和事由是否明确', '预算是否符合差旅标准', '是否涉及跨部门或客户拜访')
    )
),
(
    49003, 'OVERTIME', '加班申请流程', 'OVERTIME',
    49103, 'OVERTIME_MANAGER_APPROVAL', '加班申请直属上级审批', 49203, 'overtime_manager_approval',
    JSON_OBJECT(
        'formKey', 'overtime',
        'title', '加班申请',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'overtimeDate', 'label', '加班日期', 'type', 'date', 'required', TRUE),
            JSON_OBJECT('key', 'startAt', 'label', '开始时间', 'type', 'time', 'required', TRUE),
            JSON_OBJECT('key', 'endAt', 'label', '结束时间', 'type', 'time', 'required', TRUE),
            JSON_OBJECT('key', 'compensationMode', 'label', '补偿方式', 'type', 'select', 'required', TRUE),
            JSON_OBJECT('key', 'reason', 'label', '加班原因', 'type', 'textarea', 'required', TRUE)
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
        'checks', JSON_ARRAY('加班时段是否与排班冲突', '补偿方式是否符合制度', '原因是否充分', '是否满足提前申请要求')
    )
),
(
    49004, 'SEAL', '用章申请流程', 'SEAL',
    49104, 'SEAL_MANAGER_APPROVAL', '用章申请直属上级审批', 49204, 'seal_manager_approval',
    JSON_OBJECT(
        'formKey', 'seal',
        'title', '用章申请',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'sealType', 'label', '印章类型', 'type', 'select', 'required', TRUE),
            JSON_OBJECT('key', 'documentName', 'label', '文件名称', 'type', 'text', 'required', TRUE),
            JSON_OBJECT('key', 'counterparty', 'label', '相对方', 'type', 'text', 'required', FALSE),
            JSON_OBJECT('key', 'purpose', 'label', '用章用途', 'type', 'textarea', 'required', TRUE),
            JSON_OBJECT('key', 'documentAttachments', 'label', '用章文件', 'type', 'attachment', 'required', TRUE)
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
        'checks', JSON_ARRAY('印章类型是否匹配用途', '用章文件是否齐全', '合同/协议是否需法务审核', '是否需要归档留痕')
    )
),
(
    49005, 'PURCHASE', '采购申请流程', 'PURCHASE',
    49105, 'PURCHASE_MANAGER_APPROVAL', '采购申请直属上级审批', 49205, 'purchase_manager_approval',
    JSON_OBJECT(
        'formKey', 'purchase',
        'title', '采购申请',
        'layout', 'single-column',
        'fields', JSON_ARRAY(
            JSON_OBJECT('key', 'itemName', 'label', '采购内容', 'type', 'text', 'required', TRUE),
            JSON_OBJECT('key', 'quantity', 'label', '数量', 'type', 'number', 'required', TRUE),
            JSON_OBJECT('key', 'budgetAmount', 'label', '预算金额', 'type', 'money', 'required', TRUE),
            JSON_OBJECT('key', 'supplier', 'label', '建议供应商', 'type', 'text', 'required', FALSE),
            JSON_OBJECT('key', 'reason', 'label', '采购原因', 'type', 'textarea', 'required', TRUE)
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
        'checks', JSON_ARRAY('采购原因是否充分', '预算金额是否合理', '是否需要比价或采购备案', '验收与资产归属是否明确')
    )
);

INSERT INTO wf_process_type (
    id, process_code, process_name, biz_type, status, created_by, updated_by, is_deleted
)
SELECT process_type_id, process_code, process_name, biz_type, 'ACTIVE', 1, 1, 0
FROM seed_oa_workflow_templates
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
FROM seed_oa_workflow_templates s
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
FROM seed_oa_workflow_templates s
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
JOIN seed_oa_workflow_templates s ON s.template_code = wt.template_code
SET v.status = 'ARCHIVED',
    v.effective_to = COALESCE(v.effective_to, NOW()),
    v.updated_by = 1
WHERE v.version_no <> 1
  AND v.status = 'PUBLISHED'
  AND (v.effective_to IS NULL OR v.effective_to > NOW());

DROP TEMPORARY TABLE IF EXISTS seed_oa_workflow_templates;
DROP TEMPORARY TABLE IF EXISTS seed_company_policy_kb;
