# 第 38 审 module-attendance 第一版考勤闭环审查

- 审查日期：2026-05-07
- 审查提交：
  - `未提交工作区`（module-attendance 考勤基础配置、员工打卡、日结果与异常第一版闭环）
- 审查范围：`server/src/main/java/com/workforcehub/attendance/`、`server/src/test/java/com/workforcehub/attendance/`、`server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`、`docs/API与测试/module-attendance/`、`docs/开发日志/module-attendance.md`、`docs/开发规划/后续开发计划与测试节点.md`
- 审查目标：复核考勤第一版是否形成可运行后端闭环，重点检查权限、员工状态、考勤组归属并发、日结果重算、测试隔离和后续扩展边界；不把排班、补卡、请假影响考勤等未进入本节点的能力当作已完成验收。

---

## 第一部分：提交内容概览

本轮新增 `module-attendance` 后端包，包含后台考勤配置接口、员工端打卡接口、日结果与异常查询接口、JDBC Repository、DTO、Row 和 `AttendanceService`。后台侧已支持创建工作日历、班次、考勤组、考勤组成员；员工侧已支持上班/下班打卡、查询本人日结果和异常；直属上级可查询直属下级日结果。

本轮同时让 `AttendanceService` 实现 `AttendanceQueryService`，后续 Agent 可以通过 SPI 查询员工日结果和月度异常，而不是直接读取考勤内部 Repository。测试侧新增 `ModuleAttendanceApiIntegrationTest`，并在 `LocalIntegrationTestSupport` 中补充 `att_` 表清理，避免考勤数据污染其他集成测试。

文档侧新增 `docs/API与测试/module-attendance/`，补充 API 文档和测试用例，并更新 `docs/开发日志/module-attendance.md` 与后续开发计划中的考勤节点状态。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `AttendanceService.punch()`、`AttendanceRepository.canEmployeePunch()`、`ModuleAttendanceApiIntegrationTest.offboardedEmployeeShouldNotPunchWithExistingSession()` | 员工登录后如果后台把员工流转到 `OFFBOARDED`，旧 Session 仍可能携带原登录态继续调用打卡接口。当前实现已在打卡写入前实时查询 `org_user` 和 `auth_user_account`，要求员工主档启用、任职状态为 `PROBATION/ACTIVE/PENDING_OFFBOARD` 且账号启用；集成测试覆盖了离职后旧 Session 打下班卡返回 `code=403` 且当天只保留离职前 1 条打卡记录。 | 已处理 | 后续如果引入全局会话失效机制，仍建议保留业务写入前的实时状态校验，避免单靠 Session 生命周期保护核心写入。 |
| 2-2 | `AttendanceService.addGroupMember()`、`AttendanceService.acquireMembershipLock()`、`AttendanceRepository.existsOverlappingMember()`、`ModuleAttendanceApiIntegrationTest.concurrentOverlappingGroupMembershipShouldAllowOnlyOneActiveRecord()` | 同一员工如果并发添加多个生效区间重叠的考勤组成员，单纯先查再插会导致多个请求同时读到不存在重叠记录并各自写入，最终员工同一天命中多个考勤组。当前实现按员工维度使用单实例 JVM 锁，并在锁内开启事务和复查重叠区间；并发测试覆盖 8 个线程同时写入时最终只有 1 条成功。 | 已处理 | 当前收口只适用于单实例单企业部署；如果未来改成多实例部署，需要升级为数据库锁表、唯一约束辅助模型或 Redis 分布式锁。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `AttendanceService.acquireMembershipLock()`、`AttendanceRepository.existsOverlappingMember()` | 考勤组成员重叠归属依赖单 JVM 内存锁来串行化同一员工的写入。当前产品假设是单实例单企业部署，所以本节点可以接受；但只要后端扩为多实例，两个实例会各自持有本地锁，同一员工的重叠成员仍可能被并发写入，进而导致日结果计算命中错误考勤组。 | P1 | 多实例前必须把该保护下沉到数据库或跨实例协调层。可选方案是新增员工维度锁表并在事务内 `SELECT ... FOR UPDATE`，或引入 Redis 分布式锁，并保留事务内重叠复查。 |
| 3-2 | `AttendanceService.recalculateDailyResult()`、`AttendanceRepository.getScheduleId()`、`att_schedule` | 当前日结果只读取 `att_schedule.id`，但没有按排班覆盖默认班次，仍固定使用考勤组默认班次计算迟到、早退、缺卡。若后续直接开放排班写入而不重构计算入口，员工临时班次、调班和特殊工作日会被按默认班次结算，产生错误异常。 | P2 | 下一节点应把排班作为日结果计算的优先规则：先查有效排班并取排班班次，未命中再回退考勤组默认班次，同时补充排班覆盖默认班次的集成测试。 |
| 3-3 | `AttendanceService.recalculateDailyResult()`、`AttendanceService.rebuildExceptions()` | 缺卡异常逻辑已经存在，但当前自动化测试只覆盖迟到和早退，未覆盖只有上班卡、只有下班卡、当天无任何打卡的缺卡场景。缺卡是考勤核心异常，若计算规则后续调整，现有测试无法及时发现缺卡漏算或错误异常描述。 | P2 | 补充缺卡专项测试，至少覆盖只有上班卡、只有下班卡，以及由定时日结或手动重算触发的无打卡缺勤场景。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `AttendanceService` | `AttendanceService` 第一版已经同时承担后台配置、考勤组成员归属、员工打卡、日结果计算、异常重建、员工查询、团队查询和 Agent SPI 输出。当前代码量尚可接受，但如果继续把补卡、排班、请假影响考勤、异常治理都叠进同一个类，会快速变成大 Service，后续状态机和计算规则会难以审查。 | P2 | 下一批扩展前建议按职责拆分为配置服务、打卡服务、日结果计算服务、异常治理服务和查询 SPI 适配层，保持 Controller 调用入口稳定。 |
| 4-2 | `AttendanceRepository` | `AttendanceRepository` 混合了日历、班次、考勤组、成员、打卡、日结果、异常、组织关系查询等多个聚合 SQL。第一版作为模块落地可以接受，但后续排班、补卡、异常治理接入后，仓储会成为跨聚合 SQL 集中点，查询条件遗漏会更难发现。 | P2 | 后续可按 `AttendanceConfigRepository`、`AttendancePunchRepository`、`AttendanceResultRepository`、`AttendanceQueryRepository` 拆分，降低单文件变更冲突和审查成本。 |

## 第五部分：验证与未验证项

本轮审查后已重新执行的验证：

- `mvn -Dtest=ModuleAttendanceApiIntegrationTest#offboardedEmployeeShouldNotPunchWithExistingSession test`
- `mvn -Dtest=ModuleAttendanceApiIntegrationTest test`
- `mvn -DskipTests compile`
- `git diff --check`

前序开发节点已记录过一次大回归：

- `mvn -Dtest=ModuleAuthApiIntegrationTest,ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest,ModuleFileApiIntegrationTest,ModuleFileAdminApiIntegrationTest,ModuleFileCleanupIntegrationTest,ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleLeaveApiIntegrationTest,ModuleLeaveWorkflowIntegrationTest,ModuleAttendanceApiIntegrationTest test`

当前自动化测试已覆盖：后台创建日历、班次、考勤组和成员；员工正常上下班打卡；正常日结果；迟到和早退异常；直属上级查询直属下级日结果；考勤组成员区间重叠拒绝；单实例并发重叠写入只成功一条；离职后旧 Session 不能继续打卡；测试基类清理 `att_` 表。

未验证项：

- 排班覆盖默认班次。
- 缺卡异常专项路径。
- 补卡申请审批通过后修正异常。
- 请假通过后考勤日结果识别为请假而非缺勤。
- 工作日历日明细、休息日、节假日和跨天班次。
- 多实例部署下考勤组成员归属并发写入。
- 管理员异常治理列表、处理流水和人工忽略异常。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | `module-attendance` 第二批 | 下一节点优先补排班接口和排班覆盖默认班次计算，否则考勤日结果会停留在“只有默认班次”的基础模型，无法支撑真实调班和临时排班。 | P1 | 新增排班写入/查询接口，日结果计算优先使用有效排班班次，并补集成测试。 |
| 6-2 | `module-attendance` 第二批 | 缺卡异常、请假影响考勤和补卡审批联动直接影响考勤结果可信度，属于考勤模块进入可用阶段前必须补齐的业务闭环。 | P1 | 先补缺卡专项测试和计算入口，再接入 `module-leave` 的已通过请假查询能力，最后通过 `module-workflow` 做补卡申请和异常修正。 |
| 6-3 | 部署架构演进 | 单实例锁方案不能带到多实例生产部署。当前项目仍按单实例单企业交付推进，因此可以进入下一节点，但必须在部署架构变更前治理。 | P1 | 在后续 Docker/多实例方案设计时，把考勤组成员归属并发保护列为迁移前置项。 |

## 第七部分：阶段性结论

本轮未发现会阻断当前单实例单企业后端继续推进的 P0 问题。`module-attendance` 第一版已经具备基础配置、员工打卡、日结果、异常查询和直属上级查询的可测试闭环，并对离职旧 Session 打卡、单实例并发重叠考勤组归属这两个高风险路径做了测试保护。

阶段性结论是：可以进入 `module-attendance` 第二批，但下一节点不应继续扩散接口数量，而应优先补排班覆盖、缺卡专项、请假影响考勤和补卡审批联动；同时记录多实例锁治理为部署架构升级前的强制事项。
