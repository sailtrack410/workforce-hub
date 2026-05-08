# 第 47 审 module-attendance日结果原子upsert修复审查

- 审查日期：2026-05-08
- 审查提交：
  - 工作区待提交变更（修复第 42 审 `3-5` 日结果并发 upsert 问题）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java`、`server/src/main/java/com/workforcehub/attendance/repository/AttendanceRepository.java`、`server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核 `att_daily_result` 写入是否从应用层“先查再插”改为以 `uk_user_date(user_id, work_date)` 为边界的数据库原子 upsert，重点检查无考勤组 `NO_RULE` 并发重算是否不再死锁、重复键或产生多行日结果；本审不处理补卡闭环和服务拆分。

---

## 第一部分：提交内容概览

本轮围绕“日结果单日唯一写入必须原子化”做修复。

- `AttendanceRepository.insertDailyResult()` 调整为 `upsertDailyResult()`。
- 写入 SQL 改为 `INSERT ... ON DUPLICATE KEY UPDATE`。
- `AttendanceService.upsertDailyResult()` 不再先调用 `getDailyResultForUpdate()` 分支判断插入或更新，而是统一执行原子 upsert 后读取最新日结果。
- 新增 `ModuleAttendanceApiIntegrationTest.concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow()`。
- 新测试通过 8 个线程同时触发 `AttendanceRecalculationService.recalculateDailyResultForExternalEvent(2003, 2026-07-01)`，覆盖无有效考勤组下的 `NO_RULE` 并发重算。
- API 文档、测试用例、开发日志、后续开发计划和审查索引已同步更新。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.upsertDailyResult()` | 第 42 审 `3-5` 指出原逻辑先 `SELECT ... FOR UPDATE`，查不到再 `INSERT`。无考勤组 `NO_RULE` 场景下没有考勤组成员行可锁，多事务并发重算时会竞争 `att_daily_result.uk_user_date`。当前服务层已改为统一调用数据库原子 upsert。 | 已处理 | 后续拆 `AttendanceCalculationService` 时，所有日结果写入口继续复用同一 upsert 边界。 |
| 2-2 | `AttendanceRepository.upsertDailyResult()` | 当前 SQL 使用 `INSERT ... ON DUPLICATE KEY UPDATE`，以 `uk_user_date(user_id, work_date)` 做唯一边界。并发请求即使同时进入写入，也会落到插入或更新中的一种，不再由应用层判断新增还是修改。 | 已处理 | 如果后续切换 PostgreSQL，需要对应改为 `INSERT ... ON CONFLICT (user_id, work_date) DO UPDATE`。 |
| 2-3 | `ModuleAttendanceApiIntegrationTest.concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow()` | 红测证明原实现不是理论风险：8 个线程同时重算无考勤组日期时，插入 `att_daily_result` 触发 `CannotAcquireLockException: Deadlock found when trying to get lock`。修复后同一测试通过，并断言最终只有 1 条 `NO_RULE` 日结果。 | 已处理 | 后续补卡、月报、批量重算加入后，应继续保留并发重算测试。 |
| 2-4 | 文档同步 | API 文档和测试用例已补充“日结果写入使用数据库原子 upsert”和 `ATT-API-024`，开发计划也将第 42 审 `3-5` 从待处理调整为已完成。 | 已处理 | 新增真实补卡接口时，需要继续把“审批通过后会触发同日原子重算”的契约写入文档。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceRepository.upsertDailyResult()` | 当前 SQL 使用 MySQL 的 `VALUES(column)` 写法，适配当前 MySQL 测试环境可以通过。但如果未来把主业务库迁移到 PostgreSQL 或升级到更严格 SQL 方言，这段 upsert 语法需要数据库方言适配。 | P3 | 在容器化部署文档中明确主业务库为 MySQL；若新增 PostgreSQL 主库支持，单独提供 PostgreSQL 版本 upsert SQL 和集成测试。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 本轮解决的是日结果写入原子性，但打卡、排班修改、请假联动和外部重算仍共享同一个私有 `recalculateDailyResult()`。补卡审批接入后，该服务会继续变宽。 | P2 | 补卡闭环前拆出 `AttendanceCalculationService`，把重算入口、原子 upsert、异常重建和请假抵扣集中到计算服务。 |
| 4-2 | `ModuleAttendanceApiIntegrationTest` | 考勤测试类已增长到覆盖配置、归属、排班、打卡、并发重算、可信时间和异常识别。当前仍可运行，但继续加补卡和月报会降低定位效率。 | P3 | 后续按 `AttendanceScheduleApi`、`AttendancePunchApi`、`AttendanceCalculationIntegration` 拆分测试类。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证：无考勤组并发重算死锁 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow test` | 失败符合预期 | 修复前 8 个线程并发重算时，插入 `att_daily_result` 抛出 `CannotAcquireLockException: Deadlock found when trying to get lock`。 |
| 针对性绿灯：无考勤组并发重算原子 upsert | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#concurrentNoRuleDailyResultRecalculationShouldCreateOnlyOneRow test` | 通过 | 输出为 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`。 |
| module-attendance 回归 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`。 |

未验证项：

- 尚未执行全仓全部集成测试组合。
- 尚未验证补卡审批通过后的二次重算，因为补卡接口尚未实现。
- 尚未验证 PostgreSQL 主业务库语法，因为当前主业务库决策仍是 MySQL。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-attendance` 补卡闭环 | 缺卡、迟到、早退、请假抵扣和日结果原子重算已经具备底座，但员工仍不能通过流程修正真实漏打卡。 | P1 | 下一节点优先接入补卡申请类型：员工提交补卡，直属上级审批通过后写入修正打卡事实，并触发同日重算。 |
| 6-2 | `AttendanceService` 服务边界 | 日结果写入原子性已解决，但计算入口仍在大服务中。 | P2 | 补卡闭环前或补卡闭环中拆出 `AttendanceCalculationService`，避免继续在主服务里堆计算逻辑。 |
| 6-3 | 工作日历与节假日 | 当前日结果仍按班次窗口计算，不处理工作日历日明细、节假日和休息日。 | P2 | 月报和薪资联动前补 `att_work_calendar_day` 规则读取和测试。 |

---

## 第七部分：阶段性结论

第 42 审 `3-5` 已实质修复：`att_daily_result` 不再由应用层先查再插，而是以数据库唯一键 `user_id + work_date` 做原子 upsert。无考勤组 `NO_RULE` 并发重算的红测已经从死锁失败转为通过，完整 `ModuleAttendanceApiIntegrationTest` 也保持绿色。

当前可以进入补卡审批闭环或先拆 `AttendanceCalculationService`。从业务价值看，下一步更适合做补卡闭环，因为日结果、异常、请假抵扣和原子重算已经能支撑“审批通过后修正考勤事实并重算”的最小闭环。
