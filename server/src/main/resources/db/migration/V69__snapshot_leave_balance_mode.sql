-- NULL 保留历史规则未知的事实；已有 FREEZE 流水在结算时仍是扣减依据，禁止用当前配置回填。
ALTER TABLE wf_req_leave
    ADD COLUMN balance_mode_snapshot VARCHAR(32) DEFAULT NULL COMMENT '提交时余额扣减模式';
