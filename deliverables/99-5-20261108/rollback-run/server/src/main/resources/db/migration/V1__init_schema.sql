-- ============================================================
-- Workforce Hub - MySQL 主业务库初始化 DDL
-- 版本: V1
-- 说明: 基于《核心表结构草图》生成，覆盖全部 69 张业务表
--       (按业务域: org 4 / auth 1 / admin 5 / sys 7 / wf 13 / att 10
--        / lv 5 / hr 1 / im 4 / doc 4 / kb 5 / agt 10 = 69)
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 一、组织与人员 (org_)
-- ============================================================

CREATE TABLE `org_department` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `parent_id`      BIGINT       DEFAULT NULL COMMENT '上级部门ID',
    `tree_path`      VARCHAR(512) NOT NULL COMMENT '树路径，如 /1/3/7/',
    `dept_code`      VARCHAR(64)  NOT NULL COMMENT '部门编码',
    `dept_name`      VARCHAR(128) NOT NULL COMMENT '部门名称',
    `dept_type`      VARCHAR(32)  NOT NULL DEFAULT 'DEPARTMENT' COMMENT '类型: COMPANY/BRANCH/DEPARTMENT/TEAM',
    `dept_level`     INT          NOT NULL DEFAULT 0 COMMENT '层级深度',
    `leader_user_id` BIGINT       DEFAULT NULL COMMENT '部门负责人(第一负责人)',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`     BIGINT       DEFAULT NULL,
    `updated_by`     BIGINT       DEFAULT NULL,
    `is_deleted`     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_code` (`dept_code`),
    KEY `idx_parent_id` (`parent_id`),
    KEY `idx_tree_path` (`tree_path`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织部门';

CREATE TABLE `org_position` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `position_code`    VARCHAR(64)  NOT NULL COMMENT '岗位编码',
    `position_name`    VARCHAR(128) NOT NULL COMMENT '岗位名称',
    `position_category` VARCHAR(32) DEFAULT NULL COMMENT '岗位类别',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT       DEFAULT NULL,
    `updated_by`       BIGINT       DEFAULT NULL,
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_position_code` (`position_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='岗位定义';

CREATE TABLE `org_user` (
    `id`                    BIGINT       NOT NULL COMMENT '主键',
    `employee_no`           VARCHAR(64)  NOT NULL COMMENT '工号',
    `real_name`             VARCHAR(64)  NOT NULL COMMENT '姓名',
    `gender`                VARCHAR(16)  DEFAULT NULL COMMENT '性别',
    `mobile`                VARCHAR(32)  DEFAULT NULL COMMENT '手机号',
    `primary_dept_id`       BIGINT       NOT NULL COMMENT '主部门ID',
    `position_id`           BIGINT       DEFAULT NULL COMMENT '岗位ID',
    `manager_user_id`       BIGINT       DEFAULT NULL COMMENT '直属上级用户ID',
    `employment_status`     VARCHAR(32)  NOT NULL DEFAULT 'PENDING_ONBOARD' COMMENT '在职状态',
    `hire_date`             DATE         DEFAULT NULL COMMENT '入职日期',
    `regularized_at`        DATE         DEFAULT NULL COMMENT '转正日期',
    `service_start_date`    DATE         DEFAULT NULL COMMENT '司龄起算日',
    `annual_leave_start_date` DATE       DEFAULT NULL COMMENT '年假工龄起算日',
    `work_location`         VARCHAR(128) DEFAULT NULL COMMENT '工作地点',
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`            BIGINT       DEFAULT NULL,
    `updated_by`            BIGINT       DEFAULT NULL,
    `is_deleted`            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_employee_no` (`employee_no`),
    KEY `idx_primary_dept` (`primary_dept_id`),
    KEY `idx_manager` (`manager_user_id`),
    KEY `idx_employment_status` (`employment_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工/领导业务主表';

CREATE TABLE `org_department_leader` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `dept_id`     BIGINT      NOT NULL COMMENT '部门ID',
    `user_id`     BIGINT      NOT NULL COMMENT '用户ID',
    `leader_type` VARCHAR(32) NOT NULL DEFAULT 'PRIMARY' COMMENT '类型: PRIMARY/DEPUTY/ACTING',
    `is_primary`  TINYINT     NOT NULL DEFAULT 0 COMMENT '是否正职',
    `sort_order`  INT         NOT NULL DEFAULT 0 COMMENT '排序',
    `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`  BIGINT      DEFAULT NULL,
    `updated_by`  BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dept_user_leader` (`dept_id`, `user_id`, `leader_type`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门领导成员';

-- ============================================================
-- 二、认证与账号 (auth_)
-- ============================================================

CREATE TABLE `auth_user_account` (
    `id`                  BIGINT       NOT NULL COMMENT '主键',
    `user_id`             BIGINT       NOT NULL COMMENT '关联 org_user.id',
    `login_name`          VARCHAR(128) NOT NULL COMMENT '登录名',
    `password_hash`       VARCHAR(256) NOT NULL COMMENT '密码哈希(BCrypt)',
    `password_salt`       VARCHAR(128) DEFAULT NULL COMMENT '密码盐(BCrypt已内置)',
    `email`               VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    `email_verified_at`   DATETIME     DEFAULT NULL COMMENT '邮箱验证时间',
    `account_status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '账号状态',
    `last_login_at`       DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    `password_updated_at` DATETIME     DEFAULT NULL COMMENT '密码最后更新时间',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`          BIGINT       DEFAULT NULL,
    `updated_by`          BIGINT       DEFAULT NULL,
    `is_deleted`          TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`),
    UNIQUE KEY `uk_login_name` (`login_name`),
    KEY `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务用户登录账号';

-- ============================================================
-- 三、后台管理员与 RBAC (admin_)
-- ============================================================

CREATE TABLE `admin_user` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `username`        VARCHAR(128) NOT NULL COMMENT '管理员用户名',
    `password_hash`   VARCHAR(256) NOT NULL,
    `password_salt`   VARCHAR(128) DEFAULT NULL,
    `display_name`    VARCHAR(64)  DEFAULT NULL COMMENT '显示名称',
    `email`           VARCHAR(128) DEFAULT NULL,
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `last_login_at`   DATETIME     DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    `updated_by`      BIGINT       DEFAULT NULL,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台管理员账号';

CREATE TABLE `admin_role` (
    `id`         BIGINT       NOT NULL COMMENT '主键',
    `role_code`  VARCHAR(64)  NOT NULL COMMENT '角色编码',
    `role_name`  VARCHAR(128) NOT NULL COMMENT '角色名称',
    `status`     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by` BIGINT       DEFAULT NULL,
    `updated_by` BIGINT       DEFAULT NULL,
    `is_deleted` TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台角色';

CREATE TABLE `admin_permission` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `perm_code`   VARCHAR(128) NOT NULL COMMENT '权限编码',
    `perm_name`   VARCHAR(128) NOT NULL COMMENT '权限名称',
    `module_code` VARCHAR(64)  DEFAULT NULL COMMENT '所属模块',
    `action_code` VARCHAR(64)  DEFAULT NULL COMMENT '动作编码',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`  BIGINT       DEFAULT NULL,
    `updated_by`  BIGINT       DEFAULT NULL,
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台权限点';

CREATE TABLE `admin_user_role` (
    `id`            BIGINT NOT NULL COMMENT '主键',
    `admin_user_id` BIGINT NOT NULL,
    `role_id`       BIGINT NOT NULL,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`    BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`admin_user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员角色关系';

CREATE TABLE `admin_role_permission` (
    `id`            BIGINT NOT NULL COMMENT '主键',
    `role_id`       BIGINT NOT NULL,
    `permission_id` BIGINT NOT NULL,
    `created_at`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`    BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关系';

-- ============================================================
-- 四、平台共享底座 (sys_)
-- ============================================================

CREATE TABLE `sys_file_object` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `storage_provider` VARCHAR(32) NOT NULL DEFAULT 'MINIO' COMMENT '存储提供方',
    `bucket_name`     VARCHAR(128) DEFAULT NULL COMMENT '存储桶',
    `object_key`      VARCHAR(512) NOT NULL COMMENT '对象键',
    `file_name`       VARCHAR(256) NOT NULL COMMENT '原始文件名',
    `file_ext`        VARCHAR(32)  DEFAULT NULL COMMENT '扩展名',
    `mime_type`       VARCHAR(128) DEFAULT NULL COMMENT 'MIME类型',
    `file_size`       BIGINT       DEFAULT NULL COMMENT '文件大小(字节)',
    `file_hash`       VARCHAR(128) DEFAULT NULL COMMENT '文件哈希',
    `upload_status`   VARCHAR(32)  NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_object_key` (`object_key`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件对象元数据';

CREATE TABLE `sys_attachment_ref` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `owner_type`     VARCHAR(64)  NOT NULL COMMENT '归属业务类型',
    `owner_id`       BIGINT       NOT NULL COMMENT '归属业务ID',
    `file_object_id` BIGINT       NOT NULL COMMENT '文件对象ID',
    `display_name`   VARCHAR(256) DEFAULT NULL COMMENT '展示名称',
    `sort_order`     INT          NOT NULL DEFAULT 0,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`     BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_type`, `owner_id`),
    KEY `idx_file_object` (`file_object_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='附件关联';

CREATE TABLE `sys_upload_ticket` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `ticket_no`     VARCHAR(64)  NOT NULL COMMENT '票据编号',
    `uploader_type` VARCHAR(32)  NOT NULL COMMENT '上传人类型: USER/ADMIN',
    `uploader_id`   BIGINT       NOT NULL COMMENT '上传人ID',
    `biz_type`      VARCHAR(64)  DEFAULT NULL COMMENT '业务类型',
    `biz_id`        BIGINT       DEFAULT NULL COMMENT '业务ID',
    `file_name`     VARCHAR(256) NOT NULL COMMENT '文件名',
    `mime_type`     VARCHAR(128) DEFAULT NULL,
    `file_size`     BIGINT       DEFAULT NULL,
    `object_key`    VARCHAR(512) NOT NULL COMMENT '预分配对象键',
    `expire_at`     DATETIME     NOT NULL COMMENT '票据过期时间',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/CONFIRMED/EXPIRED',
    `confirmed_at`  DATETIME     DEFAULT NULL COMMENT '确认时间',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`    BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ticket_no` (`ticket_no`),
    KEY `idx_uploader` (`uploader_type`, `uploader_id`),
    KEY `idx_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传票据';

CREATE TABLE `sys_todo_item` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `owner_type`  VARCHAR(32)  NOT NULL COMMENT '归属主体类型: USER/ADMIN/AGENT',
    `owner_id`    BIGINT       NOT NULL COMMENT '归属主体ID',
    `source_type` VARCHAR(64)  NOT NULL COMMENT '来源类型',
    `source_id`   BIGINT       NOT NULL COMMENT '来源ID',
    `title`       VARCHAR(256) NOT NULL COMMENT '待办标题',
    `summary`     VARCHAR(512) DEFAULT NULL COMMENT '摘要',
    `route_path`  VARCHAR(512) DEFAULT NULL COMMENT '前端跳转路径',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/READ/DONE/CLOSED',
    `priority`    VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '优先级',
    `due_at`      DATETIME     DEFAULT NULL COMMENT '截止时间',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_owner` (`owner_type`, `owner_id`, `status`),
    KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一待办';

CREATE TABLE `sys_audit_log` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `operator_type` VARCHAR(32)  NOT NULL COMMENT '操作人类型: USER/ADMIN/AGENT/SYSTEM',
    `operator_id`   BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `action_type`   VARCHAR(64)  NOT NULL COMMENT '动作类型',
    `target_type`   VARCHAR(64)  DEFAULT NULL COMMENT '目标类型',
    `target_id`     BIGINT       DEFAULT NULL COMMENT '目标ID',
    `result`        VARCHAR(32)  DEFAULT NULL COMMENT '结果: SUCCESS/FAIL/BLOCKED',
    `detail_json`   JSON         DEFAULT NULL COMMENT '详情JSON',
    `occurred_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator_type`, `operator_id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_occurred_at` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志';

CREATE TABLE `sys_connector` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `connector_code`  VARCHAR(64)  NOT NULL COMMENT '连接器编码',
    `connector_name`  VARCHAR(128) NOT NULL COMMENT '连接器名称',
    `connector_type`  VARCHAR(32)  NOT NULL COMMENT '类型: HTTP/SMTP/WEBHOOK',
    `base_url`        VARCHAR(512) DEFAULT NULL COMMENT '基础URL',
    `auth_type`       VARCHAR(32)  DEFAULT NULL COMMENT '认证类型: NONE/API_KEY/OAUTH2/BASIC',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    `updated_by`      BIGINT       DEFAULT NULL,
    `is_deleted`      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_connector_code` (`connector_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部连接器';

CREATE TABLE `sys_connector_secret` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `connector_id`    BIGINT       NOT NULL COMMENT '连接器ID',
    `secret_name`     VARCHAR(128) NOT NULL COMMENT '凭证名称',
    `secret_ref`      VARCHAR(512) NOT NULL COMMENT '凭证引用(不存明文)',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `last_rotated_at` DATETIME     DEFAULT NULL COMMENT '最后轮换时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    `updated_by`      BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_connector_id` (`connector_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='连接器凭证元数据';

-- ============================================================
-- 五、流程与审批 (wf_)
-- ============================================================

CREATE TABLE `wf_process_type` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `process_code` VARCHAR(64)  NOT NULL COMMENT '流程编码',
    `process_name` VARCHAR(128) NOT NULL COMMENT '流程名称',
    `biz_type`     VARCHAR(64)  DEFAULT NULL COMMENT '关联业务类型',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`   BIGINT       DEFAULT NULL,
    `updated_by`   BIGINT       DEFAULT NULL,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_process_code` (`process_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程类型';

CREATE TABLE `wf_template` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `process_type_id` BIGINT     NOT NULL COMMENT '流程类型ID',
    `template_code` VARCHAR(64)  NOT NULL COMMENT '模板编码',
    `template_name` VARCHAR(128) NOT NULL COMMENT '模板名称',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`    BIGINT       DEFAULT NULL,
    `updated_by`    BIGINT       DEFAULT NULL,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`template_code`),
    KEY `idx_process_type` (`process_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板';

CREATE TABLE `wf_template_version` (
    `id`                       BIGINT       NOT NULL COMMENT '主键',
    `template_id`              BIGINT       NOT NULL COMMENT '模板ID',
    `version_no`               INT          NOT NULL COMMENT '版本号',
    `flowable_definition_key`  VARCHAR(128) DEFAULT NULL COMMENT 'Flowable流程定义Key',
    `flowable_definition_id`   VARCHAR(128) DEFAULT NULL COMMENT 'Flowable流程定义ID',
    `form_schema_json`         JSON         DEFAULT NULL COMMENT '表单Schema',
    `rule_snapshot_json`       JSON         DEFAULT NULL COMMENT '规则快照',
    `published_by`             BIGINT       DEFAULT NULL COMMENT '发布人',
    `published_at`             DATETIME     DEFAULT NULL COMMENT '发布时间',
    `effective_from`           DATETIME     DEFAULT NULL COMMENT '生效起始时间',
    `effective_to`             DATETIME     DEFAULT NULL COMMENT '生效结束时间',
    `status`                   VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    `created_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`               DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`               BIGINT       DEFAULT NULL,
    `updated_by`               BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_version` (`template_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流程模板版本';

CREATE TABLE `wf_request` (
    `id`                          BIGINT       NOT NULL COMMENT '主键',
    `request_no`                  VARCHAR(64)  NOT NULL COMMENT '申请编号',
    `biz_type`                    VARCHAR(64)  NOT NULL COMMENT '业务类型',
    `biz_id`                      BIGINT       NOT NULL COMMENT '业务明细ID',
    `applicant_user_id`           BIGINT       NOT NULL COMMENT '申请人ID',
    `applicant_dept_id`           BIGINT       DEFAULT NULL COMMENT '申请人部门快照',
    `applicant_position_id`       BIGINT       DEFAULT NULL COMMENT '申请人岗位快照',
    `title`                       VARCHAR(256) NOT NULL COMMENT '申请标题',
    `template_version_id`         BIGINT       NOT NULL COMMENT '模板版本ID',
    `flowable_process_instance_id` VARCHAR(128) DEFAULT NULL COMMENT 'Flowable流程实例ID',
    `status`                      VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    `current_step_key`            VARCHAR(64)  DEFAULT NULL COMMENT '当前节点Key',
    `current_step_name`           VARCHAR(128) DEFAULT NULL COMMENT '当前节点名称',
    `submitted_at`                DATETIME     DEFAULT NULL COMMENT '提交时间',
    `finished_at`                 DATETIME     DEFAULT NULL COMMENT '完成时间',
    `created_at`                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`                  BIGINT       DEFAULT NULL,
    `updated_by`                  BIGINT       DEFAULT NULL,
    `is_deleted`                  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_no` (`request_no`),
    UNIQUE KEY `uk_flowable_instance` (`flowable_process_instance_id`),
    KEY `idx_applicant` (`applicant_user_id`, `status`),
    KEY `idx_biz` (`biz_type`, `biz_id`),
    KEY `idx_template_version` (`template_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务申请头表';

-- 业务申请明细表
CREATE TABLE `wf_req_leave` (
    `id`               BIGINT   NOT NULL COMMENT '主键',
    `request_id`       BIGINT   NOT NULL COMMENT '申请头ID',
    `leave_type_id`    BIGINT   NOT NULL COMMENT '假种ID',
    `start_at`         DATETIME NOT NULL COMMENT '开始时间',
    `end_at`           DATETIME NOT NULL COMMENT '结束时间',
    `duration_minutes` INT      NOT NULL COMMENT '时长(分钟)',
    `reason`           VARCHAR(512) DEFAULT NULL COMMENT '理由',
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='请假申请明细';

CREATE TABLE `wf_req_timeoff` (
    `id`               BIGINT   NOT NULL COMMENT '主键',
    `request_id`       BIGINT   NOT NULL,
    `start_at`         DATETIME NOT NULL,
    `end_at`           DATETIME NOT NULL,
    `duration_minutes` INT      NOT NULL,
    `reason`           VARCHAR(512) DEFAULT NULL,
    `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调休申请明细';

CREATE TABLE `wf_req_overtime` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `request_id`       BIGINT       NOT NULL,
    `start_at`         DATETIME     NOT NULL,
    `end_at`           DATETIME     NOT NULL,
    `duration_minutes` INT          NOT NULL,
    `overtime_type`    VARCHAR(32)  DEFAULT NULL COMMENT '加班类型',
    `reason`           VARCHAR(512) DEFAULT NULL,
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='加班申请明细';

CREATE TABLE `wf_req_clock_correction` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `request_id`        BIGINT       NOT NULL,
    `target_date`       DATE         NOT NULL COMMENT '补卡日期',
    `missing_punch_type` VARCHAR(32) NOT NULL COMMENT '缺卡类型: ON_DUTY/OFF_DUTY',
    `expected_punch_at` DATETIME     NOT NULL COMMENT '预期打卡时间',
    `reason`            VARCHAR(512) DEFAULT NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='补卡申请明细';

CREATE TABLE `wf_req_hr_change` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `request_id`           BIGINT       NOT NULL,
    `change_type`          VARCHAR(32)  NOT NULL COMMENT '变更类型: ONBOARD/REGULARIZE/TRANSFER_DEPT/TRANSFER_POS/MANAGER_CHANGE/OFFBOARD',
    `target_user_id`       BIGINT       NOT NULL COMMENT '目标员工ID',
    `effective_at`         DATETIME     NOT NULL COMMENT '生效时间',
    `before_snapshot_json` JSON         DEFAULT NULL COMMENT '变更前快照',
    `after_snapshot_json`  JSON         DEFAULT NULL COMMENT '变更后快照',
    `reason`               VARCHAR(512) DEFAULT NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人事异动申请明细';

CREATE TABLE `wf_task_action` (
    `id`                  BIGINT       NOT NULL COMMENT '主键',
    `request_id`          BIGINT       NOT NULL COMMENT '申请头ID',
    `flowable_task_id`    VARCHAR(128) DEFAULT NULL COMMENT 'Flowable任务ID',
    `step_key`            VARCHAR(64)  DEFAULT NULL COMMENT '节点Key',
    `step_name`           VARCHAR(128) DEFAULT NULL COMMENT '节点名称',
    `actor_type`          VARCHAR(32)  NOT NULL COMMENT '处理人类型: USER/ADMIN/AGENT/SYSTEM',
    `actor_id`            BIGINT       DEFAULT NULL COMMENT '处理人ID',
    `action_type`         VARCHAR(32)  NOT NULL COMMENT '动作: APPROVE/REJECT/RETURN/TRANSFER/ADD_SIGN/AGENT_PRE_REVIEW',
    `action_comment`      VARCHAR(512) DEFAULT NULL COMMENT '处理意见',
    `agent_decision_json` JSON         DEFAULT NULL COMMENT 'Agent预审结构化结论',
    `before_status`       VARCHAR(32)  DEFAULT NULL COMMENT '动作前状态',
    `after_status`        VARCHAR(32)  DEFAULT NULL COMMENT '动作后状态',
    `acted_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
    PRIMARY KEY (`id`),
    KEY `idx_request_id` (`request_id`),
    KEY `idx_flowable_task` (`flowable_task_id`),
    KEY `idx_actor` (`actor_type`, `actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批动作流水';

CREATE TABLE `wf_cc_record` (
    `id`              BIGINT      NOT NULL COMMENT '主键',
    `request_id`      BIGINT      NOT NULL COMMENT '申请头ID',
    `receiver_user_id` BIGINT     NOT NULL COMMENT '抄送接收人ID',
    `status`          VARCHAR(32) NOT NULL DEFAULT 'UNREAD' COMMENT '状态: UNREAD/READ',
    `read_at`         DATETIME    DEFAULT NULL COMMENT '已读时间',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_request` (`request_id`),
    KEY `idx_receiver` (`receiver_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抄送记录';

CREATE TABLE `wf_urge_record` (
    `id`             BIGINT       NOT NULL COMMENT '主键',
    `request_id`     BIGINT       NOT NULL,
    `operator_type`  VARCHAR(32)  NOT NULL COMMENT '催办人类型',
    `operator_id`    BIGINT       NOT NULL COMMENT '催办人ID',
    `target_task_id` VARCHAR(128) DEFAULT NULL COMMENT '目标Flowable任务ID',
    `urged_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_request` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='催办记录';

CREATE TABLE `wf_delegate_rule` (
    `id`                BIGINT      NOT NULL COMMENT '主键',
    `delegator_user_id` BIGINT      NOT NULL COMMENT '委托人ID',
    `delegate_user_id`  BIGINT      NOT NULL COMMENT '被委托人ID',
    `process_type_id`   BIGINT      NOT NULL COMMENT '流程类型ID',
    `start_at`          DATETIME    NOT NULL COMMENT '生效开始',
    `end_at`            DATETIME    NOT NULL COMMENT '生效结束',
    `status`            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`        BIGINT      DEFAULT NULL,
    `updated_by`        BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_delegator` (`delegator_user_id`, `process_type_id`),
    KEY `idx_delegate` (`delegate_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='委托代理审批规则';

-- ============================================================
-- 六、考勤与排班 (att_)
-- ============================================================

CREATE TABLE `att_work_calendar` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `calendar_code` VARCHAR(64)  NOT NULL COMMENT '日历编码',
    `calendar_name` VARCHAR(128) NOT NULL COMMENT '日历名称',
    `calendar_type` VARCHAR(32)  NOT NULL DEFAULT 'STANDARD' COMMENT '类型',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`    BIGINT       DEFAULT NULL,
    `updated_by`    BIGINT       DEFAULT NULL,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_calendar_code` (`calendar_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日历';

CREATE TABLE `att_work_calendar_day` (
    `id`          BIGINT      NOT NULL COMMENT '主键',
    `calendar_id` BIGINT      NOT NULL COMMENT '日历ID',
    `work_date`   DATE        NOT NULL COMMENT '日期',
    `day_type`    VARCHAR(32) NOT NULL DEFAULT 'WORKDAY' COMMENT '日期类型: WORKDAY/WEEKEND/HOLIDAY',
    `is_workday`  TINYINT     NOT NULL DEFAULT 1 COMMENT '是否工作日',
    `remark`      VARCHAR(256) DEFAULT NULL COMMENT '备注',
    `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_calendar_date` (`calendar_id`, `work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工作日历日明细';

CREATE TABLE `att_shift` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `shift_code`           VARCHAR(64)  NOT NULL COMMENT '班次编码',
    `shift_name`           VARCHAR(128) NOT NULL COMMENT '班次名称',
    `cross_day_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '是否跨天',
    `required_punch_count` INT          NOT NULL DEFAULT 2 COMMENT '要求打卡次数',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`           BIGINT       DEFAULT NULL,
    `updated_by`           BIGINT       DEFAULT NULL,
    `is_deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shift_code` (`shift_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次';

CREATE TABLE `att_shift_segment` (
    `id`                   BIGINT      NOT NULL COMMENT '主键',
    `shift_id`             BIGINT      NOT NULL COMMENT '班次ID',
    `segment_no`           INT         NOT NULL COMMENT '段号',
    `start_time`           TIME        NOT NULL COMMENT '上班时间',
    `end_time`             TIME        NOT NULL COMMENT '下班时间',
    `on_duty_punch_start`  TIME        DEFAULT NULL COMMENT '上班打卡开始',
    `on_duty_punch_end`    TIME        DEFAULT NULL COMMENT '上班打卡截止',
    `off_duty_punch_start` TIME        DEFAULT NULL COMMENT '下班打卡开始',
    `off_duty_punch_end`   TIME        DEFAULT NULL COMMENT '下班打卡截止',
    `created_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_shift_id` (`shift_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班次时间段';

CREATE TABLE `att_group` (
    `id`                         BIGINT       NOT NULL COMMENT '主键',
    `group_code`                 VARCHAR(64)  NOT NULL COMMENT '考勤组编码',
    `group_name`                 VARCHAR(128) NOT NULL COMMENT '考勤组名称',
    `calendar_id`                BIGINT       NOT NULL COMMENT '工作日历ID',
    `default_shift_id`           BIGINT       DEFAULT NULL COMMENT '默认班次ID',
    `late_grace_minutes`         INT          NOT NULL DEFAULT 0 COMMENT '迟到宽限(分钟)',
    `early_leave_grace_minutes`  INT          NOT NULL DEFAULT 0 COMMENT '早退宽限(分钟)',
    `location_required`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否要求定位',
    `ip_required`                TINYINT      NOT NULL DEFAULT 0 COMMENT '是否要求IP',
    `face_required`              TINYINT      NOT NULL DEFAULT 0 COMMENT '是否要求人脸',
    `allow_field_punch`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否允许外勤打卡',
    `status`                     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                 DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`                 BIGINT       DEFAULT NULL,
    `updated_by`                 BIGINT       DEFAULT NULL,
    `is_deleted`                 TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_group_code` (`group_code`),
    KEY `idx_calendar` (`calendar_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组';

CREATE TABLE `att_group_member` (
    `id`             BIGINT      NOT NULL COMMENT '主键',
    `group_id`       BIGINT      NOT NULL COMMENT '考勤组ID',
    `user_id`        BIGINT      NOT NULL COMMENT '用户ID',
    `effective_from` DATE        NOT NULL COMMENT '生效开始日',
    `effective_to`   DATE        DEFAULT NULL COMMENT '生效结束日(NULL表示长期)',
    `source_type`    VARCHAR(32) DEFAULT NULL COMMENT '来源: MANUAL/ONBOARD/TRANSFER',
    `status`         VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`     BIGINT      DEFAULT NULL,
    `updated_by`     BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`, `status`),
    KEY `idx_group` (`group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤组成员';

CREATE TABLE `att_schedule` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
    `work_date`       DATE         NOT NULL COMMENT '排班日期',
    `shift_id`        BIGINT       NOT NULL COMMENT '班次ID',
    `calendar_id`     BIGINT       DEFAULT NULL COMMENT '日历ID',
    `schedule_source` VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源: MANUAL/AUTO/TEMPLATE',
    `operator_id`     BIGINT       DEFAULT NULL COMMENT '操作人ID',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    `updated_by`      BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `work_date`),
    KEY `idx_work_date` (`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排班记录';

CREATE TABLE `att_punch_record` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `user_id`       BIGINT       NOT NULL COMMENT '用户ID',
    `punch_at`      DATETIME     NOT NULL COMMENT '打卡时间',
    `punch_type`    VARCHAR(32)  NOT NULL COMMENT '类型: ON_DUTY/OFF_DUTY',
    `source_type`   VARCHAR(32)  NOT NULL DEFAULT 'APP' COMMENT '来源: APP/PC/MANUAL',
    `location_text` VARCHAR(256) DEFAULT NULL COMMENT '位置描述',
    `ip_address`    VARCHAR(64)  DEFAULT NULL COMMENT 'IP地址',
    `device_id`     VARCHAR(128) DEFAULT NULL COMMENT '设备ID',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'NORMAL',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `punch_at`),
    KEY `idx_punch_at` (`punch_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始打卡记录';

CREATE TABLE `att_daily_result` (
    `id`                  BIGINT      NOT NULL COMMENT '主键',
    `user_id`             BIGINT      NOT NULL COMMENT '用户ID',
    `work_date`           DATE        NOT NULL COMMENT '工作日期',
    `schedule_id`         BIGINT      DEFAULT NULL COMMENT '排班ID',
    `group_id`            BIGINT      DEFAULT NULL COMMENT '考勤组ID',
    `shift_id`            BIGINT      DEFAULT NULL COMMENT '班次ID',
    `result_status`       VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '结果状态',
    `late_minutes`        INT         NOT NULL DEFAULT 0 COMMENT '迟到分钟',
    `early_leave_minutes` INT         NOT NULL DEFAULT 0 COMMENT '早退分钟',
    `absence_minutes`     INT         NOT NULL DEFAULT 0 COMMENT '缺勤分钟',
    `work_minutes`        INT         NOT NULL DEFAULT 0 COMMENT '工作分钟',
    `exception_flag`      TINYINT     NOT NULL DEFAULT 0 COMMENT '是否有异常',
    `calculated_at`       DATETIME    DEFAULT NULL COMMENT '计算时间',
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_date` (`user_id`, `work_date`),
    KEY `idx_work_date` (`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='日考勤结果';

CREATE TABLE `att_exception` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `daily_result_id`   BIGINT       NOT NULL COMMENT '日结果ID',
    `exception_type`    VARCHAR(32)  NOT NULL COMMENT '异常类型: LATE/EARLY_LEAVE/ABSENCE/MISS_PUNCH',
    `exception_desc`    VARCHAR(512) DEFAULT NULL COMMENT '异常描述',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `resolved_request_id` BIGINT     DEFAULT NULL COMMENT '解决该异常的申请ID',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_daily_result` (`daily_result_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤异常';

-- ============================================================
-- 七、假期与余额 (lv_)
-- ============================================================

CREATE TABLE `lv_type` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `leave_code`   VARCHAR(64)  NOT NULL COMMENT '假种编码',
    `leave_name`   VARCHAR(128) NOT NULL COMMENT '假种名称',
    `balance_mode` VARCHAR(32)  NOT NULL DEFAULT 'DEDUCT' COMMENT '余额模式: DEDUCT/NO_DEDUCT',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`   BIGINT       DEFAULT NULL,
    `updated_by`   BIGINT       DEFAULT NULL,
    `is_deleted`   TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_leave_code` (`leave_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期类型';

CREATE TABLE `lv_policy` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `policy_code`      VARCHAR(64)  NOT NULL COMMENT '规则编码',
    `policy_name`      VARCHAR(128) NOT NULL COMMENT '规则名称',
    `service_calc_mode` VARCHAR(32) DEFAULT NULL COMMENT '工龄计算模式',
    `grant_rule_json`  JSON         DEFAULT NULL COMMENT '发放规则',
    `carry_rule_json`  JSON         DEFAULT NULL COMMENT '结转规则',
    `expire_rule_json` JSON         DEFAULT NULL COMMENT '过期规则',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT       DEFAULT NULL,
    `updated_by`       BIGINT       DEFAULT NULL,
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_policy_code` (`policy_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期规则';

CREATE TABLE `lv_type_policy` (
    `id`            BIGINT      NOT NULL COMMENT '主键',
    `leave_type_id` BIGINT      NOT NULL COMMENT '假种ID',
    `policy_id`     BIGINT      NOT NULL COMMENT '规则ID',
    `effective_from` DATE       NOT NULL COMMENT '生效开始',
    `effective_to`  DATE        DEFAULT NULL COMMENT '生效结束',
    `status`        VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`    BIGINT      DEFAULT NULL,
    `updated_by`    BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_leave_type` (`leave_type_id`),
    KEY `idx_policy` (`policy_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假种规则关联';

CREATE TABLE `lv_account` (
    `id`               BIGINT      NOT NULL COMMENT '主键',
    `user_id`          BIGINT      NOT NULL COMMENT '用户ID',
    `leave_type_id`    BIGINT      NOT NULL COMMENT '假种ID',
    `available_minutes` INT        NOT NULL DEFAULT 0 COMMENT '可用余额(分钟)',
    `frozen_minutes`   INT         NOT NULL DEFAULT 0 COMMENT '冻结余额(分钟)',
    `version`          INT         NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `status`           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT      DEFAULT NULL,
    `updated_by`       BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_leave_type` (`user_id`, `leave_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期账户';

CREATE TABLE `lv_account_txn` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `account_id`      BIGINT       NOT NULL COMMENT '账户ID',
    `txn_type`        VARCHAR(32)  NOT NULL COMMENT '流水类型: GRANT/DEDUCT/CARRY/CARRY_EXPIRE/ADJUST/REFUND',
    `change_minutes`  INT          NOT NULL COMMENT '变动分钟(正增负减)',
    `balance_after`   INT          NOT NULL COMMENT '变动后余额',
    `source_type`     VARCHAR(64)  DEFAULT NULL COMMENT '来源类型: APPROVAL/ADMIN/SYSTEM',
    `source_id`       BIGINT       DEFAULT NULL COMMENT '来源ID',
    `source_txn_key`  VARCHAR(128) DEFAULT NULL COMMENT '来源幂等键',
    `effective_at`    DATETIME     NOT NULL COMMENT '生效时间',
    `expired_at`      DATETIME     DEFAULT NULL COMMENT '过期时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_source_txn_key` (`source_txn_key`),
    KEY `idx_account_id` (`account_id`),
    KEY `idx_source` (`source_type`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='假期余额流水';

-- ============================================================
-- 八、员工生命周期 (hr_)
-- ============================================================

CREATE TABLE `hr_employee_event` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `user_id`              BIGINT       NOT NULL COMMENT '员工ID',
    `change_type`          VARCHAR(32)  NOT NULL COMMENT '变更类型',
    `request_id`           BIGINT       DEFAULT NULL COMMENT '关联申请ID',
    `before_snapshot_json` JSON         DEFAULT NULL COMMENT '变更前快照',
    `after_snapshot_json`  JSON         DEFAULT NULL COMMENT '变更后快照',
    `effective_at`         DATETIME     NOT NULL COMMENT '生效时间',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'PENDING_EFFECTIVE' COMMENT '状态',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`           BIGINT       DEFAULT NULL,
    `updated_by`           BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_request_id` (`request_id`),
    KEY `idx_effective_at` (`effective_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='员工生命周期事件';

-- ============================================================
-- 九、会话、群组与消息 (im_)
-- ============================================================

CREATE TABLE `im_conversation` (
    `id`               BIGINT       NOT NULL COMMENT '主键',
    `conversation_type` VARCHAR(32) NOT NULL COMMENT '类型: PRIVATE/GROUP/AGENT',
    `conversation_key` VARCHAR(256) DEFAULT NULL COMMENT '唯一键(私聊/Agent直聊)',
    `title`            VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    `avatar_file_id`   BIGINT       DEFAULT NULL COMMENT '头像文件ID',
    `bound_agent_id`   BIGINT       DEFAULT NULL COMMENT '绑定AgentID(仅Agent会话)',
    `last_message_seq` BIGINT       NOT NULL DEFAULT 0 COMMENT '最新消息序号',
    `last_message_id`  BIGINT       DEFAULT NULL COMMENT '最新消息ID',
    `last_message_at`  DATETIME     DEFAULT NULL COMMENT '最新消息时间',
    `status`           VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT       DEFAULT NULL,
    `updated_by`       BIGINT       DEFAULT NULL,
    `is_deleted`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_key` (`conversation_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='统一会话';

CREATE TABLE `im_conversation_member` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `conversation_id` BIGINT       NOT NULL COMMENT '会话ID',
    `member_type`     VARCHAR(32)  NOT NULL COMMENT '成员类型: USER/AGENT',
    `member_id`       BIGINT       NOT NULL COMMENT '成员ID',
    `member_role`     VARCHAR(32)  NOT NULL DEFAULT 'MEMBER' COMMENT '角色: OWNER/ADMIN/MEMBER',
    `join_source`     VARCHAR(32)  DEFAULT NULL COMMENT '加入来源',
    `mute_flag`       TINYINT      NOT NULL DEFAULT 0 COMMENT '免打扰',
    `pin_flag`        TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶',
    `last_read_seq`   BIGINT       NOT NULL DEFAULT 0 COMMENT '已读序号',
    `joined_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `left_at`         DATETIME     DEFAULT NULL COMMENT '离开时间',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_member` (`conversation_id`, `member_type`, `member_id`),
    KEY `idx_member` (`member_type`, `member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话成员';

CREATE TABLE `im_group_profile` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `conversation_id`      BIGINT       NOT NULL COMMENT '会话ID',
    `group_type`           VARCHAR(32)  NOT NULL COMMENT '群类型: ORG/BIZ/CUSTOM',
    `binding_object_type`  VARCHAR(64)  DEFAULT NULL COMMENT '绑定对象类型',
    `binding_object_id`    BIGINT       DEFAULT NULL COMMENT '绑定对象ID',
    `owner_user_id`        BIGINT       DEFAULT NULL COMMENT '群主用户ID',
    `history_visible_mode` VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT '历史消息可见: FULL/AFTER_JOIN',
    `speak_mode`           VARCHAR(32)  NOT NULL DEFAULT 'ALL' COMMENT '发言模式: ALL/ADMIN_ONLY/MUTED',
    `auto_sync_mode`       VARCHAR(32)  NOT NULL DEFAULT 'NONE' COMMENT '自动同步: NONE/AUTO',
    `notice_text`          VARCHAR(1024) DEFAULT NULL COMMENT '群公告',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`           BIGINT       DEFAULT NULL,
    `updated_by`           BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_id` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群组扩展信息';

CREATE TABLE `im_message` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `conversation_id`   BIGINT       NOT NULL COMMENT '会话ID',
    `seq_no`            BIGINT       NOT NULL COMMENT '消息序号',
    `sender_type`       VARCHAR(32)  NOT NULL COMMENT '发送人类型: USER/AGENT/SYSTEM',
    `sender_id`         BIGINT       NOT NULL COMMENT '发送人ID',
    `message_type`      VARCHAR(32)  NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT/IMAGE/FILE/CARD/SYSTEM',
    `content_json`      JSON         NOT NULL COMMENT '消息内容JSON',
    `quoted_message_id` BIGINT       DEFAULT NULL COMMENT '引用消息ID',
    `status`            VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '状态: NORMAL/EDITED/RECALLED',
    `sent_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    `edited_at`         DATETIME     DEFAULT NULL COMMENT '编辑时间',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_conversation_seq` (`conversation_id`, `seq_no`),
    KEY `idx_sender` (`sender_type`, `sender_id`),
    KEY `idx_sent_at` (`sent_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息';

-- ============================================================
-- 十、文档 (doc_)
-- ============================================================

CREATE TABLE `doc_space` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `space_name`  VARCHAR(128) NOT NULL COMMENT '空间名称',
    `space_type`  VARCHAR(32)  NOT NULL DEFAULT 'PERSONAL' COMMENT '类型: PERSONAL/TEAM/PUBLIC',
    `owner_type`  VARCHAR(32)  DEFAULT NULL COMMENT '归属类型',
    `owner_id`    BIGINT       DEFAULT NULL COMMENT '归属ID',
    `visibility`  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE' COMMENT '可见性',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`  BIGINT       DEFAULT NULL,
    `updated_by`  BIGINT       DEFAULT NULL,
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档空间';

CREATE TABLE `doc_document` (
    `id`                    BIGINT       NOT NULL COMMENT '主键',
    `space_id`              BIGINT       NOT NULL COMMENT '空间ID',
    `doc_type`              VARCHAR(32)  NOT NULL DEFAULT 'FILE' COMMENT '文档类型',
    `title`                 VARCHAR(256) NOT NULL COMMENT '文档标题',
    `current_version_no`    INT          NOT NULL DEFAULT 1 COMMENT '当前版本号',
    `current_file_object_id` BIGINT      DEFAULT NULL COMMENT '当前文件对象ID',
    `owner_user_id`         BIGINT       NOT NULL COMMENT '拥有者ID',
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`            BIGINT       DEFAULT NULL,
    `updated_by`            BIGINT       DEFAULT NULL,
    `is_deleted`            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_space` (`space_id`),
    KEY `idx_owner` (`owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档主记录';

CREATE TABLE `doc_document_version` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `document_id`     BIGINT       NOT NULL COMMENT '文档ID',
    `version_no`      INT          NOT NULL COMMENT '版本号',
    `file_object_id`  BIGINT       NOT NULL COMMENT '文件对象ID',
    `change_summary`  VARCHAR(512) DEFAULT NULL COMMENT '变更摘要',
    `published_by`    BIGINT       DEFAULT NULL COMMENT '发布人',
    `published_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doc_version` (`document_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档版本';

CREATE TABLE `doc_document_acl` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `document_id` BIGINT       NOT NULL COMMENT '文档ID',
    `subject_type` VARCHAR(32) NOT NULL COMMENT '授权主体: USER/DEPARTMENT/GROUP',
    `subject_id`  BIGINT       NOT NULL COMMENT '授权主体ID',
    `perm_code`   VARCHAR(32)  NOT NULL COMMENT '权限: read/comment/edit/manage',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`  BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_document` (`document_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档ACL';

-- ============================================================
-- 十一、知识库 (kb_)
-- ============================================================

CREATE TABLE `kb_space` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `space_name`  VARCHAR(128) NOT NULL COMMENT '空间名称',
    `space_code`  VARCHAR(64)  NOT NULL COMMENT '空间编码',
    `owner_type`  VARCHAR(32)  DEFAULT NULL,
    `owner_id`    BIGINT       DEFAULT NULL,
    `visibility`  VARCHAR(32)  NOT NULL DEFAULT 'PRIVATE',
    `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`  BIGINT       DEFAULT NULL,
    `updated_by`  BIGINT       DEFAULT NULL,
    `is_deleted`  TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_code` (`space_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库空间';

CREATE TABLE `kb_space_acl` (
    `id`          BIGINT       NOT NULL COMMENT '主键',
    `space_id`    BIGINT       NOT NULL COMMENT '空间ID',
    `subject_type` VARCHAR(32) NOT NULL COMMENT '授权主体类型',
    `subject_id`  BIGINT       NOT NULL COMMENT '授权主体ID',
    `perm_code`   VARCHAR(32)  NOT NULL COMMENT '权限: read/manage',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `created_by`  BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_space` (`space_id`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库ACL';

CREATE TABLE `kb_document` (
    `id`                     BIGINT       NOT NULL COMMENT '主键',
    `space_id`               BIGINT       NOT NULL COMMENT '知识空间ID',
    `source_type`            VARCHAR(32)  NOT NULL COMMENT '来源: DOC/FILE',
    `source_document_id`     BIGINT       DEFAULT NULL COMMENT '关联文档ID',
    `source_file_object_id`  BIGINT       DEFAULT NULL COMMENT '关联文件对象ID',
    `title`                  VARCHAR(256) NOT NULL COMMENT '标题',
    `content_hash`           VARCHAR(128) DEFAULT NULL COMMENT '内容哈希(用于变更检测)',
    `ingest_status`          VARCHAR(32)  NOT NULL DEFAULT 'NOT_INGESTED' COMMENT '入库状态',
    `latest_ingest_job_id`   BIGINT       DEFAULT NULL COMMENT '最新入库任务ID',
    `status`                 VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`             BIGINT       DEFAULT NULL,
    `updated_by`             BIGINT       DEFAULT NULL,
    `is_deleted`             TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    KEY `idx_space` (`space_id`),
    KEY `idx_ingest_status` (`ingest_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识文档主记录';

CREATE TABLE `kb_ingest_job` (
    `id`              BIGINT       NOT NULL COMMENT '主键',
    `kb_document_id`  BIGINT       NOT NULL COMMENT '知识文档ID',
    `job_type`        VARCHAR(32)  NOT NULL DEFAULT 'FULL' COMMENT '任务类型: FULL/INCREMENTAL',
    `status`          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `chunk_count`     INT          DEFAULT NULL COMMENT '切片数',
    `error_message`   VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `started_at`      DATETIME     DEFAULT NULL COMMENT '开始时间',
    `finished_at`     DATETIME     DEFAULT NULL COMMENT '完成时间',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`      BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_kb_document` (`kb_document_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识入库任务';

CREATE TABLE `kb_chunk_manifest` (
    `id`              BIGINT      NOT NULL COMMENT '主键',
    `kb_document_id`  BIGINT      NOT NULL COMMENT '知识文档ID',
    `ingest_job_id`   BIGINT      NOT NULL COMMENT '入库任务ID',
    `chunk_no`        INT         NOT NULL COMMENT '切片序号',
    `chunk_hash`      VARCHAR(128) DEFAULT NULL COMMENT '切片内容哈希',
    `vector_row_id`   BIGINT      NOT NULL COMMENT 'PostgreSQL向量表行ID',
    `status`          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_chunk` (`ingest_job_id`, `chunk_no`),
    KEY `idx_kb_document` (`kb_document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识切片总账(MySQL)';

-- ============================================================
-- 十二、Agent 平台 (agt_)
-- ============================================================

CREATE TABLE `agt_agent` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `agent_code`           VARCHAR(64)  NOT NULL COMMENT 'Agent编码',
    `agent_name`           VARCHAR(128) NOT NULL COMMENT 'Agent名称',
    `agent_type`           VARCHAR(32)  NOT NULL COMMENT '类型: QA/PRE_REVIEW/REMINDER/AUTOMATION',
    `identity_mode`        VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM_IDENTITY' COMMENT '身份模式',
    `published_version_id` BIGINT       DEFAULT NULL COMMENT '当前发布版本ID',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`           BIGINT       DEFAULT NULL,
    `updated_by`           BIGINT       DEFAULT NULL,
    `is_deleted`           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_code` (`agent_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent主定义';

CREATE TABLE `agt_agent_version` (
    `id`                  BIGINT       NOT NULL COMMENT '主键',
    `agent_id`            BIGINT       NOT NULL COMMENT 'Agent ID',
    `version_no`          INT          NOT NULL COMMENT '版本号',
    `model_config_json`   JSON         DEFAULT NULL COMMENT '模型配置JSON',
    `system_prompt`       TEXT         DEFAULT NULL COMMENT '系统Prompt',
    `workflow_config_json` JSON        DEFAULT NULL COMMENT '工作流配置JSON',
    `publish_status`      VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '发布状态',
    `published_at`        DATETIME     DEFAULT NULL COMMENT '发布时间',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`          BIGINT       DEFAULT NULL,
    `updated_by`          BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_version` (`agent_id`, `version_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent版本';

CREATE TABLE `agt_tool` (
    `id`            BIGINT       NOT NULL COMMENT '主键',
    `tool_code`     VARCHAR(64)  NOT NULL COMMENT '工具编码',
    `tool_name`     VARCHAR(128) NOT NULL COMMENT '工具名称',
    `tool_type`     VARCHAR(32)  NOT NULL COMMENT '类型: SPI/CONNECTOR',
    `connector_id`  BIGINT       DEFAULT NULL COMMENT '连接器ID(仅CONNECTOR类型)',
    `risk_level`    VARCHAR(32)  NOT NULL DEFAULT 'READONLY' COMMENT '风险等级: READONLY/LOW/MEDIUM/HIGH',
    `read_only_flag` TINYINT     NOT NULL DEFAULT 1 COMMENT '是否只读',
    `status`        VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`    BIGINT       DEFAULT NULL,
    `updated_by`    BIGINT       DEFAULT NULL,
    `is_deleted`    TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tool_code` (`tool_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具';

CREATE TABLE `agt_agent_tool` (
    `id`                BIGINT NOT NULL COMMENT '主键',
    `agent_version_id`  BIGINT NOT NULL COMMENT 'Agent版本ID',
    `tool_id`           BIGINT NOT NULL COMMENT '工具ID',
    `permission_mode`   VARCHAR(32) NOT NULL DEFAULT 'ALLOWED' COMMENT '权限模式',
    `approval_mode`     VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT '审批模式: AUTO/REQUIRE_CONFIRM',
    `arg_schema_json`   JSON DEFAULT NULL COMMENT '参数Schema',
    `risk_control_json` JSON DEFAULT NULL COMMENT '风控配置JSON',
    `status`            VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`        BIGINT DEFAULT NULL,
    `updated_by`        BIGINT DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_tool` (`agent_version_id`, `tool_id`),
    KEY `idx_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具绑定';

CREATE TABLE `agt_agent_acl` (
    `id`           BIGINT       NOT NULL COMMENT '主键',
    `agent_id`     BIGINT       NOT NULL COMMENT 'Agent ID',
    `subject_type` VARCHAR(32)  NOT NULL COMMENT '主体类型: USER/DEPARTMENT/GROUP/ADMIN_USER',
    `subject_id`   BIGINT       NOT NULL COMMENT '主体ID',
    `perm_code`    VARCHAR(32)  NOT NULL COMMENT '权限: visible/use/manage',
    `status`       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`   BIGINT       DEFAULT NULL,
    `updated_by`   BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_subject_perm` (`agent_id`, `subject_type`, `subject_id`, `perm_code`),
    KEY `idx_subject` (`subject_type`, `subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent ACL';

CREATE TABLE `agt_knowledge_binding` (
    `id`               BIGINT      NOT NULL COMMENT '主键',
    `agent_version_id` BIGINT      NOT NULL COMMENT 'Agent版本ID',
    `kb_space_id`      BIGINT      NOT NULL COMMENT '知识空间ID',
    `retrieval_mode`   VARCHAR(32) NOT NULL DEFAULT 'SEMANTIC' COMMENT '检索模式',
    `top_k`            INT         NOT NULL DEFAULT 5 COMMENT '返回切片数',
    `status`           VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `created_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`       BIGINT      DEFAULT NULL,
    `updated_by`       BIGINT      DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_version_space` (`agent_version_id`, `kb_space_id`),
    KEY `idx_kb_space` (`kb_space_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent知识绑定';

CREATE TABLE `agt_trigger` (
    `id`                    BIGINT       NOT NULL COMMENT '主键',
    `agent_version_id`      BIGINT       NOT NULL COMMENT 'Agent版本ID',
    `trigger_type`          VARCHAR(32)  NOT NULL COMMENT '触发类型: CONVERSATION/WORKFLOW/TIMER/EVENT/MANUAL',
    `trigger_condition_json` JSON        DEFAULT NULL COMMENT '触发条件JSON',
    `status`                VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`            BIGINT       DEFAULT NULL,
    `updated_by`            BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_agent_version` (`agent_version_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent触发器';

CREATE TABLE `agt_task` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `agent_id`             BIGINT       NOT NULL COMMENT 'Agent ID',
    `agent_version_id`     BIGINT       NOT NULL COMMENT 'Agent版本ID',
    `trigger_type`         VARCHAR(32)  NOT NULL COMMENT '触发类型',
    `context_type`         VARCHAR(64)  DEFAULT NULL COMMENT '上下文类型',
    `context_id`           BIGINT       DEFAULT NULL COMMENT '上下文ID',
    `triggered_by_type`    VARCHAR(32)  NOT NULL COMMENT '触发主体类型: USER/ADMIN/SYSTEM/WORKFLOW/TIMER/EVENT',
    `triggered_by_id`      BIGINT       DEFAULT NULL COMMENT '触发主体ID',
    `waiting_deadline_at`  DATETIME     DEFAULT NULL COMMENT '人工确认截止时间',
    `status`               VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态',
    `started_at`           DATETIME     DEFAULT NULL COMMENT '开始执行时间',
    `finished_at`          DATETIME     DEFAULT NULL COMMENT '完成时间',
    `error_message`        VARCHAR(1024) DEFAULT NULL COMMENT '错误信息',
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `created_by`           BIGINT       DEFAULT NULL,
    `updated_by`           BIGINT       DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_agent` (`agent_id`, `status`),
    KEY `idx_status` (`status`),
    KEY `idx_waiting_deadline` (`waiting_deadline_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent任务实例';

CREATE TABLE `agt_tool_call_log` (
    `id`                   BIGINT       NOT NULL COMMENT '主键',
    `task_id`              BIGINT       NOT NULL COMMENT '任务ID',
    `tool_id`              BIGINT       NOT NULL COMMENT '工具ID',
    `request_payload_json` JSON         DEFAULT NULL COMMENT '请求载荷',
    `response_payload_json` JSON        DEFAULT NULL COMMENT '响应载荷',
    `approval_status`      VARCHAR(32)  DEFAULT NULL COMMENT '审批状态',
    `result_status`        VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS' COMMENT '执行结果',
    `called_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_tool_id` (`tool_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent工具调用日志';

CREATE TABLE `agt_audit_log` (
    `id`                BIGINT       NOT NULL COMMENT '主键',
    `task_id`           BIGINT       NOT NULL COMMENT '任务ID',
    `action_type`       VARCHAR(64)  NOT NULL COMMENT '动作类型',
    `target_type`       VARCHAR(64)  DEFAULT NULL COMMENT '目标类型',
    `target_id`         BIGINT       DEFAULT NULL COMMENT '目标ID',
    `decision_summary`  VARCHAR(512) DEFAULT NULL COMMENT '决策摘要',
    `risk_flag`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否高风险',
    `occurred_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    PRIMARY KEY (`id`),
    KEY `idx_task_id` (`task_id`),
    KEY `idx_occurred_at` (`occurred_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent业务审计日志';

SET FOREIGN_KEY_CHECKS = 1;
