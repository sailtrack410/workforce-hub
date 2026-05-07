# 第 45 审 module-leave余额调整幂等指纹修复审查

- 审查日期：2026-05-08
- 审查提交：
  - 工作区待提交变更（修复第 42 审 `3-3` 假期账本幂等键未绑定操作指纹问题）
- 审查范围：`server/src/main/java/com/workforcehub/leave/service/LeaveService.java`、`server/src/test/java/com/workforcehub/leave/ModuleLeaveApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/leave/ModuleLeaveWorkflowIntegrationTest.java`、`docs/API与测试/module-leave/`、`docs/开发日志/module-leave.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 42 审 `3-3` 是否从单纯 `sourceTxnKey` 去重修正为绑定账本操作指纹，重点检查同键不同参数是否显式拒绝、真正重复请求是否仍然幂等、错误目标账户是否不会被创建、审批扣减是否不会被其他流水占用同键后静默跳过；本审不处理第 42 审其他 P1。

---

## 第一部分：提交内容概览

本轮围绕“假期账本幂等键必须绑定具体操作”做修复。

- `LeaveService.adjustAccount()` 命中已有 `sourceTxnKey` 后，不再无条件回放旧流水对应账户结果。
- 新增 `ensureAdjustmentIdempotencyFingerprint()`，用既有流水和账户记录校验 `source_type + txn_type + userId + leaveTypeId + changeMinutes`。
- 同一 `sourceTxnKey` 只有在既有流水为 `ADMIN/ADJUST`，且员工、假种、调整分钟数与本次请求完全一致时才回放。
- 同一 `sourceTxnKey` 被不同员工、不同假种、不同调整分钟数或非管理员调整流水复用时，返回 `code=409` 和 `幂等键已被不同余额调整操作使用`。
- `LeaveService.deductForWorkflowApproval()` 命中 `WORKFLOW_REQUEST:{requestId}` 后，也会校验既有流水必须是 `WORKFLOW_REQUEST/DEDUCT`，且 `sourceId`、员工、假种、扣减分钟数与当前审批一致。
- 审批扣减幂等键被管理员调整或其他扣减流水占用时，返回 `code=409` 和 `幂等键已被不同假期扣减操作使用`，审批事务回滚，流程保持待审批。
- `ModuleLeaveApiIntegrationTest` 新增指纹冲突测试，覆盖同键不同分钟数、不同员工、不同假种三类错账路径。
- `ModuleLeaveWorkflowIntegrationTest` 新增审批扣减指纹冲突测试，覆盖 `WORKFLOW_REQUEST:{requestId}` 被非审批扣减流水占用的错账路径。
- API 文档、测试用例、开发日志和后续开发计划同步更新幂等键契约。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `LeaveService.adjustAccount()`、`ensureAdjustmentIdempotencyFingerprint()` | 第 42 审 `3-3` 指出余额调整只按 `sourceTxnKey` 查旧流水，命中后直接回放旧账户结果。当前命中旧流水后会校验既有流水必须是 `source_type=ADMIN`、`txn_type=ADJUST`，并且既有账户的 `userId/leaveTypeId` 与本次请求一致，既有流水的 `changeMinutes` 也与本次请求一致。 | 已处理 | 后续新增发放、冻结、解冻等流水类型时，应把同类指纹校验沉淀到统一账本服务，不要为每个入口各自实现一套弱幂等逻辑。 |
| 2-2 | `ModuleLeaveApiIntegrationTest.adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint()` | 原测试只覆盖同 payload 重复调用，没有覆盖同一幂等键用于不同业务操作。本轮新增测试先用同键给张三年假增加 480 分钟，再分别用同键调整 960 分钟、调整李四年假、调整张三病假，三类请求均断言返回 `409`。 | 已处理 | 后续余额流水新增批量入口时，仍需补“同键不同 payload”测试，不只断言重复请求不会重复写。 |
| 2-3 | `lv_account`、`lv_account_txn` 数据库断言 | 原风险会导致复用同键时接口返回成功但目标账户没有真实调整，属于静默错账。本轮测试进一步断言错误目标账户没有被创建，原账户余额仍为 480，`ADJUST` 流水仍只有一条。 | 已处理 | 如果未来改为数据库持久化 `operation_fingerprint`，迁移脚本应能从已有 `account_id + txn_type + source_type + change_minutes` 回填历史指纹。 |
| 2-4 | `LeaveService.deductForWorkflowApproval()`、`ModuleLeaveWorkflowIntegrationTest.workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint()` | 提交前复核发现同类风险也存在于审批扣减：如果 `WORKFLOW_REQUEST:{requestId}` 先被管理员调整流水占用，原逻辑会命中旧键后直接返回，导致审批通过但余额未扣。本轮补齐扣减指纹校验，非 `WORKFLOW_REQUEST/DEDUCT` 或 requestId、员工、假种、分钟数不一致时返回 `409`，事务回滚，Flowable 流程和 `wf_request` 保持待审批，既有管理员调整后的余额保持不变。 | 已处理 | 后续所有自动扣减、冻结、解冻入口都要使用同一账本指纹策略，避免某个入口用旧的“命中 key 即成功”语义。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `LeaveService.ensureAdjustmentIdempotencyFingerprint()`、`ensureWorkflowDeductIdempotencyFingerprint()`、`lv_account_txn` | 当前操作指纹由现有字段运行时推导，没有在 `lv_account_txn` 中持久化独立 `operation_fingerprint`。这能收口本次同键不同员工、假种和分钟数错账路径，也能挡住审批扣减被其他流水占用同键后静默跳过；但如果未来出现批量调整、自动发放、冻结解冻等更复杂命令，单靠各入口临时拼字段会提高遗漏风险。 | P2 | 在账本能力扩展前设计统一 `LeaveAccountLedgerService` 和操作指纹模型，必要时新增 `operation_fingerprint` 字段，并由所有账本入口统一生成和校验。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `LeaveService` | 本轮为了最小修复继续在 `LeaveService` 内增加账本幂等规则。该类已经承担假种创建、余额调整、审批扣减、SPI 查询、账户响应转换等职责，后续冻结、发放、过期、结转继续加入会让账本状态机散落在主服务里。 | P2 | 下一轮扩展冻结或自动发放前，优先抽 `LeaveAccountLedgerService`，把账户创建、余额变更、流水写入、幂等指纹和余额校验集中到单一账本边界。 |
| 4-2 | `ModuleLeaveApiIntegrationTest`、`ModuleLeaveWorkflowIntegrationTest` | 账户接口测试继续集中覆盖假种创建、余额调整、员工查询和调整幂等冲突，workflow 集成测试继续覆盖审批扣减、考勤联动和扣减幂等冲突，当前规模仍可接受。但随着管理员账户列表、流水明细、批量发放加入，测试类会变宽。 | P3 | 后续可按 `LeaveTypeApi`、`LeaveAccountAdjustmentApi`、`LeaveAccountQueryApi`、`LeaveWorkflowDeductIntegration` 拆测试类，保持每个测试类关注一个接口族。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证：同键不同操作参数被错误回放 | `mvn -Dtest=ModuleLeaveApiIntegrationTest#adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint test` | 失败符合预期 | 修复前第二次用同一 `sourceTxnKey` 调整不同分钟数时实际返回 `code=0`，证明原问题存在。 |
| 红灯验证：审批扣减幂等键被其他流水占用后静默跳过 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint test` | 失败符合预期 | 修复前 `WORKFLOW_REQUEST:{requestId}` 被管理员调整流水占用后，审批仍返回 `code=0` 并进入 `APPROVED`。 |
| 针对性绿灯：余额调整 | `mvn -Dtest=ModuleLeaveApiIntegrationTest#adjustmentSourceTxnKeyShouldRejectDifferentOperationFingerprint test` | 通过 | 三类指纹冲突均返回 `409`，错误目标账户没有被创建或调整。 |
| 针对性绿灯：审批扣减 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#workflowDeductSourceTxnKeyShouldRejectDifferentFingerprint test` | 通过 | 扣减幂等冲突返回 `409`，Flowable 流程保持运行，申请仍为 `IN_APPROVAL`，既有管理员调整后的余额保持不变且无 `DEDUCT` 流水。 |
| module-leave API 回归 | `mvn -Dtest=ModuleLeaveApiIntegrationTest test` | 通过 | 输出为 `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`。 |
| leave + workflow 组合回归 | `mvn -Dtest=ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest test` | 通过 | 输出为 `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0`。 |

未验证项：

- 尚未执行全仓全部集成测试组合。
- 尚未验证未来自动发放、冻结、解冻、结转、过期清零等新流水类型的统一幂等模型。
- 尚未验证批量余额调整场景，因为当前接口尚未实现批量能力。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 第 42 审 `3-4` 排班状态与唯一键语义 | 当前 `3-3` 已收口，剩余最高优先级是排班 `INACTIVE` 与 `att_schedule.uk_user_date` 唯一键语义冲突。停用后再创建同日排班仍会撞库，属于接口语义和数据模型不一致。 | P1 | 下一节点优先明确“一名员工一天只能一条排班，停用后原地恢复或改班”的模型，或调整唯一键支持停用后重建，并补对应测试。 |
| 6-2 | 第 42 审 `3-5` 日结果并发 upsert | 无考勤组场景下日结果写入仍可能两个事务同时读不到再插入，后一个撞唯一键回滚。 | P2 | 在补卡和月报前改为数据库原子 upsert 或引入 user-day 锁表。 |
| 6-3 | leave 账本服务拆分 | 本轮用最小指纹校验解决当前错账路径，但账本入口仍集中在 `LeaveService`。 | P2 | 冻结余额前拆 `LeaveAccountLedgerService`，把幂等、余额变更、流水和审计统一收口。 |

---

## 第七部分：阶段性结论

第 42 审 `3-3` 已实质修复：余额调整幂等键不再只按字符串命中后无条件回放，而是绑定到管理员调整流水、员工、假种和调整分钟数；审批扣减幂等键也绑定到 `WORKFLOW_REQUEST/DEDUCT/requestId/员工/假种/扣减分钟数`。真正重复请求仍然幂等，不同操作复用同一键会显式返回 `409`，不会创建错误账户、写错误流水，也不会让审批通过但余额未扣。

当前可以进入第 42 审下一个 P1 修复节点，但不建议直接扩展补卡、月报或批量排班，因为排班状态与唯一键语义仍未收口，考勤日结果并发 upsert 和 leave 账本服务拆分也需要排入近期治理。
