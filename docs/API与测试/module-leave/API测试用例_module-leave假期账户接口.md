# API 测试用例 module-leave 假期账户接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-leave` 假期账户接口测试用例 |
| 文档版本 | `v0.1` |
| 适用范围 | 假种创建、余额调整、员工余额查询、workflow 余额校验与扣减、审批通过联动考勤重算 |
| 发布日期 | `2026-05-07` |
| 当前状态 | 第一版自动化测试已落地，已覆盖审批通过扣减余额和联动考勤日结果重算 |

---

## 二、测试目标

本轮测试目标：

- 验证管理员可以创建启用假种。
- 验证管理员可以初始化或调整员工假期账户余额。
- 验证余额调整写入 `lv_account_txn`。
- 验证余额调整幂等键不会重复写流水或重复增加余额。
- 验证员工只能查询自己的假期账户。
- 验证请假提交前余额不足会被拦截，且不生成审批申请。
- 验证余额足够时可以提交审批。
- 验证审批通过后扣减余额并写 `DEDUCT` 流水。
- 验证审批通过后触发考勤重算，把覆盖日期的日结果识别为 `LEAVE`。
- 验证审批驳回不扣减余额。
- 验证新 `lv_` 表会被普通集成测试和 workflow 集成测试基线清理，避免测试污染。

当前已落地自动化测试类：

- `ModuleLeaveApiIntegrationTest`
- `ModuleLeaveWorkflowIntegrationTest`

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080` |
| JDK | `17` |
| MySQL | `workforce_hub_test` |
| Redis | 已启动，用于会话 |
| Flowable | workflow 联动测试使用真实 Process Engine |
| 会话模式 | Spring Session + Redis |

---

## 四、测试数据

| 类型 | 登录名 | 密码 | 用途 |
| --- | --- | --- | --- |
| 管理员 | `admin.demo` | `Admin@123456` | 创建假种、调整余额 |
| 员工账号 | `zhangsan.employee` | `Employee@123456` | 查询余额、提交请假 |
| 直属上级 | `lisi.manager` | `Employee@123456` | 审批请假 |

组织关系：

- 张三 `userId=2003`。
- 李四 `userId=2002`。
- 张三的 `managerUserId=2002`。

---

## 五、测试用例明细

## 5.1 假种与账户接口

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `LV-API-001` | P0 | `POST /api/admin/leave/types` | 管理员创建假种 | 管理员已登录 | `leaveCode=ANNUAL_xxx`、`balanceMode=DEDUCT` | HTTP `200`；`code=0`；返回 `leaveTypeId`、假种编码、名称、余额模式和状态 |
| `LV-API-002` | P0 | `POST /api/admin/leave/accounts/adjustments` | 管理员初始化员工余额 | `LV-API-001` 成功 | `userId=2003`、`changeMinutes=960` | HTTP `200`；`code=0`；返回 `availableMinutes=960`、`lastTxnType=ADJUST`、`lastChangeMinutes=960` |
| `LV-API-003` | P0 | `GET /api/employee/leave/accounts` | 员工查询自己的假期账户 | 张三已登录；余额已初始化 | 无 | HTTP `200`；`code=0`；返回对应假种账户、`availableMinutes=960`、`frozenMinutes=0` |
| `LV-API-004` | P0 | 数据库断言 | 初始化余额写入账户和流水 | `LV-API-002` 成功 | 查询 `lv_account`、`lv_account_txn` | 账户唯一存在；流水 `txn_type=ADJUST`、`change_minutes=960` |
| `LV-API-005` | P0 | `POST /api/admin/leave/accounts/adjustments` | 重复幂等键不重复调整 | 管理员已登录；假种已存在 | 同一 `sourceTxnKey` 连续调用两次 | 余额只增加一次；`ADJUST` 流水只写一条 |

## 5.2 workflow 余额联动

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `LV-WF-001` | P0 | `POST /api/employee/workflow/leave-requests` | 余额不足提交请假被拦截 | 张三已登录；假种启用；未初始化余额 | 请假 480 分钟 | HTTP `200`；`code=400`；`message=假期余额不足`；不生成 `wf_request`；不写 `DEDUCT` 流水 |
| `LV-WF-002` | P0 | `POST /api/employee/workflow/leave-requests` | 余额足够时可以提交请假 | 张三余额 960 分钟；流程模板已发布 | 请假 480 分钟 | HTTP `200`；`code=0`；返回 `IN_APPROVAL` 和 Flowable 实例 ID |
| `LV-WF-003` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` | 审批通过扣减余额 | `LV-WF-002` 成功；李四已登录 | 审批通过 | 余额从 960 扣到 480；写入 `txn_type=DEDUCT`、`change_minutes=-480`、`source_type=WORKFLOW_REQUEST` |
| `LV-WF-004` | P0 | `POST /api/employee/workflow/tasks/{taskId}/reject` | 审批驳回不扣减余额 | 张三余额 960 分钟；请假已提交 | 审批驳回 | 余额仍为 960；不写 `DEDUCT` 流水 |
| `LV-WF-005` | P0 | `POST /api/employee/workflow/tasks/{taskId}/approve` + `GET /api/employee/attendance/daily-results` | 审批通过联动考勤日结果为请假 | 张三有有效考勤组；张三提交 `2026-05-07` 请假；李四为直属审批人 | 李四审批通过后，张三查询 `2026-05-07` 日结果 | 日结果 `resultStatus=LEAVE`；`absenceMinutes=0`；`exceptionFlag=false`；当天无 `att_exception` |

---

## 六、验证命令

当前已通过命令：

```bash
cd server
mvn -Dtest=ModuleLeaveApiIntegrationTest test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest test
mvn -Dtest=ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleWorkflowFlowableIntegrationTest test
mvn -Dtest=ModuleLeaveWorkflowIntegrationTest#approvedLeaveRequestShouldRecalculateAttendanceAsLeaveResult test
```

建议进入下一节点前执行：

```bash
cd server
mvn -DskipTests compile
mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest test
```

---

## 七、当前未覆盖

- 年假规则按工龄自动发放。
- 结转、过期清零和自动发放任务。
- 节假日、排班、考勤日历对扣减分钟数的影响。
- 半天假、小时假与考勤打卡结果的分钟级合并。
- 提交时冻结余额，审批通过转扣减，驳回或撤回解冻。
- 管理员查询全部员工假期账户列表。
- 员工余额流水明细查询。
