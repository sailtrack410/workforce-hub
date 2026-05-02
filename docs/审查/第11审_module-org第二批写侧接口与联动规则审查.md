# 第 11 审 module-org 第二批写侧接口与联动规则审查

- 审查日期：2026-05-02
- 审查范围：`server/src/main/java/com/workforcehub/org/controller/AdminOrgController.java`、`server/src/main/java/com/workforcehub/org/service/OrgManagementService.java`、`server/src/main/java/com/workforcehub/org/dto/*.java`、`server/src/main/java/com/workforcehub/hr/domain/HrEmployeeEvent.java`、`server/src/main/java/com/workforcehub/hr/mapper/HrEmployeeEventMapper.java`、`server/src/test/java/com/workforcehub/org/ModuleOrgWriteApiIntegrationTest.java`、`docs/API与测试/module-org/API文档_module-org第二批写侧接口.md`、`docs/API与测试/module-org/API测试用例_module-org第二批写侧接口.md`、`docs/开发日志/module-org.md`
- 审查目标：确认 `module-org` 第二批写侧是否已经把部门、岗位、员工、生命周期与账号联动收口为自洽的后台治理基线，并识别当前阶段仍然保留的工程与业务风险

---

## 第一部分：逻辑审查

### 一、写侧闭环完整度

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `OrgManagementService` + `AdminOrgController` | 本轮已经把部门、岗位、员工、生命周期和账号联动串成了一个可用闭环，当前没有发现“关键对象无法落库”或“核心规则缺失”的阻断问题。 | 建议 | 后续继续沿这个接口族补更细的 RBAC、事件发布和后台查询页，而不是再另起一套平行模型。 |

### 二、生命周期与账号联动

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `OrgManagementService#updateEmploymentStatus` + `#upsertUserAccount` | 当前已经做到了“待入职/离职禁止启用账号”和“离职自动停用账号”，这条主链是对的。剩余边界是：恢复到 `PROBATION/ACTIVE` 后，系统不会自动重新启用账号，而是要求后台再显式操作一次账号状态。这个策略更安全，但需要在后台交互和文档里持续讲清楚。 | 建议 | 后续后台页面实现时，把“可登录但账号仍禁用”的状态显式提示出来，避免运营侧误以为生命周期恢复后账号会自动恢复。 |

### 三、事件与跨模块联动

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `hr_employee_event` 记录策略 | 当前已经把部门/岗位/上级/生命周期变化写入 `hr_employee_event`，对留痕是加分项。但它还只是本地总账记录，没有进一步向 `module-im`、`module-agent`、`module-workflow` 发布正式领域事件。 | 建议 | 下一步可基于 Spring 事件或统一事件总线，把 `org.user.status-changed`、`org.user.manager-changed` 等变化升级为正式内部事件，供私聊规则、审批链和 Agent ACL 实时消费。 |

### 四、测试环境隔离

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `LocalIntegrationTestSupport` + 两组 module-org 集成测试 | 本轮已经通过“读写测试一起并跑”验证了测试兼容性，并把读侧断言修成了对本地脏库更稳的形式。但根本问题仍在：测试仍直接写本地 `workforce_hub`，不是隔离测试库。 | 建议 | 后续优先拆出 `workforce_hub_test` 或引入容器化测试库，避免本地长期积累的测试数据继续增长。 |

### 五、审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 0 |  |
| 重大 | 0 |  |
| 建议 | 4 | 1-1、2-1、3-1、4-1 |
| 合计 | 4 |  |

### 六、优先处理建议

**第一优先级（进入下一批模块联动前建议补齐）：**

1. **3-1** 把 `hr_employee_event` 再向正式领域事件推进
2. **4-1** 拆测试库或容器化测试环境

**第二优先级（后台界面联调前建议补齐）：**

- **2-1** 把“生命周期恢复后账号不自动启用”的策略显式体现在后台页面和交互上
- **1-1** 基于现有接口族补更细粒度 RBAC，而不是重复造接口

---

## 第二部分：阶段性成熟度评估

### 一、当前正向结论

- `module-org` 不再只有读侧，已经具备第一版后台写侧治理能力
- 部门树、岗位、员工档案、直属上级、生命周期与账号联动已经进入同一套规则体系
- `hr_employee_event` 已开始承接变化留痕
- 自动化测试已经覆盖关键写侧主路径与高风险约束

### 二、当前核心风险判断

- 当前主要风险已经不在“接口能不能用”，而在“跨模块联动事件”和“测试环境隔离”两件事
- 这不影响继续开发，但会影响后续 IM、审批、Agent 直接接这套组织底座时的实时性和稳定性

### 三、阶段性结论

可以把这一轮视为：

`module-org 第二批写侧治理基线已经落地，可作为后续审批、消息、Agent 权限底座继续开发`

### 四、建议的下一步动作

1. 继续把 `org.user.status-changed`、`org.user.manager-changed` 做成正式领域事件
2. 进入 `module-workflow` 或 `module-im` 时直接复用本轮的组织写侧与生命周期规则
3. 补独立测试库后，再扩写更多 P1 写侧用例
