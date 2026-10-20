# 第 46 审 module-attendance排班唯一模型修复审查

- 审查日期：2026-05-08
- 审查提交：
  - 工作区待提交变更（修复第 42 审 `3-4` 排班状态与唯一键语义冲突）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java`、`server/src/main/java/com/workforcehub/attendance/repository/AttendanceRepository.java`、`server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核排班创建接口是否与 `att_schedule.uk_user_date(user_id, work_date)` 保持一致，重点检查 `INACTIVE` 排班是否仍占用员工单日唯一模型、重复创建是否返回业务 `409`、是否避免数据库重复键暴露为系统 `500`；本审不处理第 42 审其他问题。

---

## 第一部分：提交内容概览

本轮围绕“员工单日排班唯一模型”做最小修复。

- 明确 `att_schedule` 第一版模型为“一名员工一天只有一条排班记录”。
- `INACTIVE` 表示停用该排班，不表示释放当天排班位置。
- `AttendanceRepository.existsActiveSchedule()` 调整为 `existsSchedule()`，按 `userId + workDate` 检查任意状态排班。
- `AttendanceService.createSchedule()` 创建前改为检查任意状态排班，命中后返回 `code=409`。
- 重复排班提示统一为 `员工当天已存在排班，请通过修改原排班恢复或改班`。
- `ModuleAttendanceApiIntegrationTest` 新增停用后重复创建专项测试，断言不会写入第二条排班，旧排班状态保持 `INACTIVE`。
- 同步更新 `module-attendance` API 文档、测试用例、开发日志、开发计划和审查索引。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.createSchedule()` | 第 42 审 `3-4` 指出，服务层只检查 `ACTIVE` 排班，但数据库唯一键不区分状态。当前创建前改为检查任意状态排班，与 `uk_user_date` 保持一致。 | 已处理 | 后续若产品要支持停用后重建同日排班，必须先调整数据模型，例如引入版本表或唯一键纳入版本维度，不能只改服务层。 |
| 2-2 | `AttendanceRepository.existsSchedule()` | 原方法名和 SQL 都表达“有效排班”，容易让调用方误解 `INACTIVE` 不占用日期。当前方法名改为 `existsSchedule`，SQL 不再过滤 `status`。 | 已处理 | 后续排班查询场景如果只需要有效排班，应单独使用读取有效排班的方法，避免把唯一模型判重和日结果计算语义混用。 |
| 2-3 | `ModuleAttendanceApiIntegrationTest.inactiveScheduleShouldStillRejectCreateForSameUserDate()` | 原测试只覆盖已有 `ACTIVE` 排班重复创建，无法发现停用后重复创建会落到数据库重复键。本轮新增测试先创建排班、更新为 `INACTIVE`、再重复创建，断言接口返回业务 `409` 且数据库只有 1 条记录。 | 已处理 | 后续批量排班、模板排班也应复用该唯一模型测试思路，覆盖“部分成功后重复导入”的冲突提示。 |
| 2-4 | `docs/API与测试/module-attendance/` | 原文档写“同一员工同一天只能有一条 `ACTIVE` 排班”，与数据库唯一键不一致。当前文档已改为“`ACTIVE` 和 `INACTIVE` 都占用 `userId + workDate` 唯一模型”，并写明恢复或改班走修改接口。 | 已处理 | 前端表单应按该文档设计：重复创建时展示修改原排班入口，而不是提示用户先停用再新增。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceService` | 当前创建排班前会锁定员工主档，单实例同员工排班写入可以被串行化；但排班创建、修改和日结果重算仍混在同一个大服务里。后续批量排班、模板排班、补卡修正和月报都要复用排班语义，如果不拆分边界，容易出现某个入口绕过“员工单日一条排班”的规则。 | P2 | 在批量排班或补卡闭环前拆出 `AttendanceScheduleService`，把排班唯一模型、状态流转、恢复/改班和重算触发统一放到排班服务边界。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 排班创建、排班修改、打卡、日结果计算、请假抵扣都仍集中在同一个服务类。排班唯一模型已收口，但后续批量排班、模板排班和补卡接入后，该类会继续膨胀。 | P2 | 在补卡闭环或批量排班前拆分 `AttendanceScheduleService` 和 `AttendanceCalculationService`，让排班状态机、日结果重算和打卡事实各自收口。 |
| 4-2 | `ModuleAttendanceApiIntegrationTest` | 当前考勤测试类已经覆盖基础配置、排班、打卡、异常和可信时间边界。新增排班唯一模型测试后仍可接受，但继续加入补卡、批量排班和月报会降低定位效率。 | P3 | 后续按 `AttendanceScheduleApi`、`AttendancePunchApi`、`AttendanceCalculationIntegration` 拆测试类，保留现有类做核心冒烟。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证：停用后重复创建落到数据库唯一键 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#inactiveScheduleShouldStillRejectCreateForSameUserDate test` | 失败符合预期 | 修复前第二次创建触发 `att_schedule.uk_user_date` 重复键，接口返回 `500`。 |
| 针对性绿灯：停用后重复创建返回业务冲突 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#inactiveScheduleShouldStillRejectCreateForSameUserDate,ModuleAttendanceApiIntegrationTest#duplicateActiveScheduleShouldBeRejected test` | 通过 | 输出为 `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`。 |
| module-attendance 回归 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | Maven 编译成功，JDK 17 enforcer 校验通过。 |
| 空白检查 | `git diff --check` | 通过 | 未发现行尾空白或补丁格式问题。 |

未验证项：

- 尚未执行全仓全部集成测试组合。
- 尚未验证批量排班、模板排班和排班复制，因为这些接口尚未实现。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 第 42 审 `3-5` 日结果并发 upsert | 无考勤组场景下日结果写入仍可能两个事务同时读不到再插入，后一个撞唯一键回滚。排班唯一模型收口后，这是第 42 审剩余考勤侧优先级最高的问题。 | P2 | 在补卡和月报前改为数据库原子 upsert 或引入 `userId + workDate` 重算锁。 |
| 6-2 | `module-attendance` 补卡闭环 | 缺卡异常、请假抵扣和排班覆盖已经具备基础能力，但员工仍不能发起补卡申请修正漏打卡。 | P1 | 基于 `module-workflow` 增加补卡申请类型，审批通过后写入修正打卡事实并触发同日重算。 |
| 6-3 | 排班服务边界拆分 | 排班唯一模型已经明确，但排班创建、修改和重算触发仍在 `AttendanceService` 内。 | P2 | 批量排班前拆出 `AttendanceScheduleService`，让所有排班入口复用同一套唯一模型和状态语义。 |

---

## 第七部分：阶段性结论

第 42 审 `3-4` 已实质修复：排班创建接口与 `att_schedule.uk_user_date` 使用同一业务语义，`ACTIVE` 和 `INACTIVE` 都占用员工单日排班唯一模型。停用后重复创建不再暴露数据库重复键和系统 `500`，而是返回明确业务 `409`，提示管理员通过修改原排班恢复或改班。

当前可以进入第 42 审下一个治理节点，但不建议直接扩展批量排班或月报，因为日结果并发 upsert、补卡闭环和排班服务边界拆分仍需要排入近期处理。
