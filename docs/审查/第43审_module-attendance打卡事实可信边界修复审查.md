# 第 43 审 module-attendance打卡事实可信边界修复审查

- 审查日期：2026-05-07
- 审查提交：
  - 工作区待提交变更（修复第 42 审 `3-1` 员工打卡事实可信边界）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/`、`server/src/main/java/com/workforcehub/common/config/TimeConfig.java`、`server/src/test/java/com/workforcehub/attendance/ModuleAttendanceApiIntegrationTest.java`、`server/src/test/java/com/workforcehub/support/`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核第 42 审 `3-1` 是否真正收口，重点检查员工实时打卡时间来源、打卡来源白名单、测试保护和 API 文档契约；本审不处理第 42 审其他 P1。

---

## 第一部分：提交内容概览

本轮围绕“员工实时打卡事实可信边界”做最小收口。

- `AttendanceService.punch()` 不再使用客户端传入的 `punchAt` 作为打卡事实时间，改为通过注入的 `Clock` 取服务端接收时间。
- 新增 `TimeConfig` 提供生产环境 `Clock.systemDefaultZone()`，并新增测试用 `MutableTestClock` 与 `FixedClockTestConfiguration`，让集成测试可以稳定控制服务端时间。
- `CreatePunchRecordRequest.punchAt` 改为兼容字段，不再必填，避免接口契约继续要求客户端提交一个不可信字段。
- 员工实时打卡来源只允许 `APP`、`PC`，拒绝 `MANUAL` 等受控来源伪造。
- `ModuleAttendanceApiIntegrationTest` 新增 3 个回归测试，覆盖客户端历史时间不生效、缺省客户端时间仍可打卡、员工端伪造 `MANUAL` 被拒绝。
- API 文档、测试用例、开发日志和后续开发计划同步更新。

---

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.punch()`、`CreatePunchRecordRequest.punchAt`、`ModuleAttendanceApiIntegrationTest.employeePunchShouldUseServerReceivedTimeInsteadOfClientPunchAt()` | 第 42 审 `3-1` 指出员工可以提交历史或未来 `punchAt` 伪造正常打卡。本轮服务端改为使用 `LocalDateTime.now(clock)` 写入 `att_punch_record.punch_at`，测试验证客户端提交 `2026-05-07T09:00:00` 时，实际响应和数据库记录落在服务端时间 `2026-05-14T10:30:00`。 | 已处理 | 后续历史修正必须走补卡审批或管理员受控入口，不应重新开放员工端直接指定可信打卡时间。 |
| 2-2 | `CreatePunchRecordRequest.punchAt`、`ModuleAttendanceApiIntegrationTest.employeePunchShouldAllowMissingClientPunchAt()` | 因为实时打卡不再信任客户端时间，如果仍要求客户端必传 `punchAt`，接口契约会制造无意义的必填字段。本轮移除 `@NotNull`，测试验证不传 `punchAt` 也能按服务端时间打卡。 | 已处理 | 文档已标明 `punchAt` 仅为兼容字段；未来可在版本升级时废弃该字段或改名为客户端设备时间审计字段。 |
| 2-3 | `AttendanceService.normalizeEmployeePunchSourceType()`、`ModuleAttendanceApiIntegrationTest.employeePunchShouldRejectControlledManualSourceType()` | 第 42 审 `3-1` 指出员工端可以把来源伪造成 `MANUAL`。本轮员工实时打卡只允许 `APP/PC`，提交 `MANUAL` 返回 `code=400` 且不进入写入链路。 | 已处理 | 后续补卡、管理员修正、系统任务应使用独立服务方法或内部入口产生 `MANUAL/AUTO/TEMPLATE` 等受控来源。 |

---

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `TimeConfig.systemClock()`、生产部署环境 | 当前实时打卡事实时间依赖应用进程所在机器或容器的系统时钟。如果宿主机时间漂移、容器时区配置错误或多实例时间不同步，同一员工的真实打卡顺序和日结果计算仍可能出现偏差。现有测试只验证可控时钟语义，不能证明生产时钟同步。 | P2 | Docker/部署阶段必须明确时区和 NTP/宿主机时间同步要求；健康检查或运维文档建议暴露服务端当前时间，便于排查时钟漂移。 |
| 3-2 | `CreatePunchRecordRequest.punchAt` | `punchAt` 目前作为兼容字段保留但不参与计算。短期有利于兼容旧请求，长期如果前端或第三方误以为该字段仍有效，会造成调用方理解偏差。 | P3 | API 文档已标明不信任该字段；下一版可以把它改为 `clientReportedAt` 审计字段，或在接口版本升级时移除。 |

---

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | 本轮继续在 `AttendanceService` 内追加打卡可信边界逻辑。虽然本次改动较小，但该服务已经同时承担配置、排班、打卡、日结果计算、异常重建和请假联动。继续加入补卡、外勤、设备校验后会使边界变重。 | P2 | 下一批补卡前建议先拆 `AttendancePunchService`、`AttendanceCalculationService` 和 `AttendanceScheduleService`，让实时打卡、历史补卡和日结果计算有清晰入口。 |
| 4-2 | `FixedClockTestConfiguration`、`MutableTestClock` | 可控时钟已经能解决考勤测试的时间稳定性，但如果后续其他模块也直接引入该配置，可能形成测试时间全局耦合。 | P3 | 保持该配置只在需要固定服务端时间的集成测试里显式 `@Import`，不要放进通用测试基类默认加载。 |

---

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 红灯验证 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#employeePunchShouldAllowMissingClientPunchAt test` | 失败符合预期 | 修改前不传 `punchAt` 被 DTO 校验拦截，返回 `400` 和 `打卡时间不能为空`。 |
| 针对性绿灯 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest#employeePunchShouldAllowMissingClientPunchAt,ModuleAttendanceApiIntegrationTest#employeePunchShouldUseServerReceivedTimeInsteadOfClientPunchAt,ModuleAttendanceApiIntegrationTest#employeePunchShouldRejectControlledManualSourceType test` | 通过 | 3 个可信边界测试均通过。 |
| 考勤模块回归 | `mvn -Dtest=ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0`。 |
| 请假联动组合回归 | `mvn -Dtest=ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test` | 通过 | 输出为 `Tests run: 16, Failures: 0, Errors: 0, Skipped: 0`。 |
| 编译验证 | `mvn -DskipTests compile` | 通过 | JDK 17 环境下编译成功。 |
| 空白检查 | `git diff --check` | 通过 | 当前工作区差异无空白错误。 |

未验证项：

- 尚未验证生产 Docker/服务器时区和 NTP 配置。

---

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 第 42 审 `3-2` 请假分钟级覆盖 | 当前请假仍按日期整日豁免。打卡事实可信边界收口后，下一处会直接污染考勤日结果的是部分时段请假被整日 `LEAVE`。 | P1 | 下一节点优先处理：要么限制第一版只允许整日请假，要么让 leave SPI 返回请假区间并由 attendance 按分钟合并。 |
| 6-2 | 第 42 审 `3-3` 假期余额调整幂等键 | 幂等键未绑定操作指纹会造成静默错账，与考勤可信边界一样属于账本类风险。 | P1 | 在继续扩展余额冻结、年假规则前先补幂等指纹校验。 |
| 6-3 | 第 42 审 `3-4` 排班 `INACTIVE` 与唯一键语义冲突 | 排班状态和唯一键语义不一致会让后台停用后无法重建有效排班。 | P1 | 明确排班模型后再修：单行原地修改或允许停用后重建，不能两套语义混用。 |

---

## 第七部分：阶段性结论

本轮已实质收口第 42 审 `3-1`：员工实时打卡事实时间由服务端产生，员工端不能通过 `punchAt` 改写历史或未来考勤日，也不能伪造 `MANUAL` 受控来源。新增测试覆盖了原问题的主要触发路径，并同步更新了 API 契约和开发记录。

当前不建议立即进入月度统计、薪资扣款或考勤强约束场景，因为第 42 审仍有 `3-2` 请假分钟级覆盖、`3-3` 余额调整幂等指纹、`3-4` 排班状态唯一模型三个 P1 未处理。下一节点建议优先处理 `3-2`，因为它会直接影响已批准请假与考勤日结果的一致性。
