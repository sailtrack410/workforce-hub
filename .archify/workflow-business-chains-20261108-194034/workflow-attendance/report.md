# 审批、假期与考勤业务链路记录

证据基准：`38d829167042dc26efd7af502ce623c976d6e224`；仓库身份`https://github.com/sailtrack410/workforce-hub.git`；源码链接local-only。

本记录为静态源码追踪。未启动/停止应用，未执行真实数据库、Flowable、AI、存储或业务接口；8张原生流程图通过Archify自动校验及浏览器检查，未做截图感知审阅。未修改业务源码、迁移或共享审查索引；工作区已有docs审查目录改动不属于本子任务。

## 当前前端入口

- admin-web `/workflow`、`/leave`、`/leave-types`、`/attendance`指向真实治理页面。
- employee-web唯一工作台/审批入口为`/hub/workbench`、`/hub/approvals`；旧workbench重定向，新建申请仅toast；旧WorkbenchSection代码不证明当前入口可提交。
- employee-web当前审批中心同意、驳回、撤回调用真实API。
- hub-web是独立前端：审批/考勤独立页Placeholder，工作台下班OUT真实调用API。

## 审批模板版本创建与发布

[流程图](wf-template.html) · [候选](wf-template.candidate.json) · [自动检查回执](wf-template.finalize-summary.json)

- 范围：流程类型→模板→草稿JSON→绑定Flowable精确版本→旧版归档→新申请选版。
- 当前入口：admin-web /workflow 路由可达；版本发布调用真实接口。
- 正常：管理端创建流程类型、模板和JSON草稿；发布时校验草稿、启用模板及Flowable定义，固化精确definitionId，归档旧版；新请求选择有效发布版。
- 异常/补偿：非草稿、模板停用、Flowable定义不存在均拒绝；发布事务异常回滚；在途申请继续使用原template_version_id。
- 问题/优化：任意BPMN发布前建议验证任务结构。当前内置流程为单直属上级任务；多级流程不应仅换定义而不改待办推进。并发发布未专项验证，不直接认定缺陷。
- 源码：`admin-web/src/views/WorkflowGovernanceView.vue:195`（治理页面）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:66`（创建类型与模板）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:109`（保存草稿）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:134`（校验发布条件）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:158`（归档旧发布版）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:158`（发布新版本）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:138`（拒绝发布）；`server/src/main/java/com/workforcehub/workflow/repository/WorkflowRepository.java:37`（新申请选择发布版）。

## 请假提交、冻结与AI预审回调

[流程图](wf-leave-ai.html) · [候选](wf-leave-ai.candidate.json) · [自动检查回执](wf-leave-ai.finalize-summary.json)

- 范围：请假API→校验时长与余额→事务冻结→可选AI→人工或驳回释放。
- 当前入口：employee-web 新申请仅toast，旧WorkbenchSection未被当前工作台路由挂载；图中API入口为后端能力。
- 正常：API校验时段、时长、有效假种和余额；申请、明细、附件与冻结同事务。无AI直接人工；有任务则PENDING_REVIEW。
- 异常/补偿：回调锁申请，核对状态、taskId和无实例；通过或转人工使用原模板创建直属上级待办；拒绝或超时释放冻结并REJECTED。异常抛错回滚。
- 问题/优化：WF-01、WF-02；沿用99-3、99-4。Agent内部任务执行归Agent链路，本图覆盖回调到业务状态。
- 源码：`server/src/main/java/com/workforcehub/workflow/controller/EmployeeWorkflowController.java:59`（提交请假API）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:900`（校验申请）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:157`（保存并冻结余额）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:402`（触发可选AI）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:185`（等待AI结果）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:370`（锁申请并核对任务）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:383`（启动人工审批）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:333`（拒绝或超时）；`employee-web/src/hub/views/ApprovalsView.vue:232`（新申请按钮）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:151`（提交事务回滚）。

## 人工审批、驳回与撤回结算

[流程图](wf-settlement.html) · [候选](wf-settlement.candidate.json) · [自动检查回执](wf-settlement.review-2/wf-settlement.finalize-summary.json)

- 范围：任务处理人授权→请假扣减或释放→Flowable结束→考勤重算；申请人撤回。
- 当前入口：employee-web /hub/approvals 同意、驳回、撤回接真实API；hub-web独立审批页为Placeholder。
- 正常：核验IN_APPROVAL及Flowable assignee；同意请假则扣冻结，结束任务、申请和待办，按日期重算考勤，提交后发WorkflowCompletedEvent。
- 异常/补偿：请假驳回释放冻结；本人IN_APPROVAL撤回时释放冻结、删除实例并关闭待办。历史无冻结流水有兼容逻辑；异常抛错回滚，未动态验证跨组件共事务。
- 问题/优化：终态事件当前来自人工通过/驳回公共路径；撤回与AI拒绝没有对应发布调用。若插件需要完整终态应补契约；未证明当前消费者要求，记为优化。
- 源码：`employee-web/src/hub/views/ApprovalsView.vue:170`（审批中心操作）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:725`（锁申请核验处理人）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:740`（请假通过结算）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:767`（完成任务与业务）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:779`（重算请假日期）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:794`（提交后发布完成事件）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:763`（请假驳回补偿）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:767`（结束驳回申请）；`employee-web/src/hub/views/ApprovalsView.vue:209`（申请人撤回）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:593`（核验本人和状态）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:602`（释放请假冻结）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:605`（WITHDRAWN）。

## 缺卡补卡审批与考勤恢复

[流程图](wf-correction.html) · [候选](wf-correction.candidate.json) · [自动检查回执](wf-correction.finalize-summary.json)

- 范围：补卡API→同日时点校验→直属上级审批→虚拟MANUAL打卡→重算。
- 当前入口：当前employee-web新工作台未提供可提交补卡表单；后端API和审批处理可用。
- 正常：API规范化缺失类型，校验时点同日和直属上级；固定模板启动人工审批；通过后插MANUAL打卡并重算。
- 异常/补偿：无上级、日期错误、非处理人拒绝；驳回或撤回不写打卡。相同精确时点记录存在则不重复插入；不可打卡或写入异常会抛错回滚。
- 问题/优化：这是缺卡补入，没有更正已有打卡的承诺。同日同类型不同时间受V33唯一键限制，建议提前返回业务冲突；为边界/优化，不列已动态复现缺陷。
- 源码：`server/src/main/java/com/workforcehub/workflow/controller/EmployeeWorkflowController.java:66`（提交补卡API）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:217`（校验同日与上级）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:245`（创建人工审批）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:725`（上级同意）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:444`（写MANUAL打卡）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:468`（重算并完成）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:763`（驳回或撤回）；`server/src/main/resources/db/migration/V33__add_attendance_punch_daily_unique.sql:1`（同日同类型限制）。

## 通用OA提交与审批附件下载

[流程图](wf-generic-attachments.html) · [候选](wf-generic-attachments.candidate.json) · [自动检查回执](wf-generic-attachments.review-2/wf-generic-attachments.finalize-summary.json)

- 范围：通用表单保存→个人附件转审批引用→可选预审→人工审批；参与人票据下载。
- 当前入口：employee-web当前新建费用等类型仅toast；后端通用OA及附件API存在。
- 正常：通用表单对象保存JSON并绑定版本；本人个人附件经校验转为审批引用，可选AI后人工审批。参与人可申请十分钟单次票据，无须等待审批结束。
- 异常/补偿：附件越权、删除或FAILED拒绝；发票据和执行下载均检查参与范围。执行再核验本人、ACTIVE、有效期和文件，认领票据后provider读对象并审计。
- 问题/优化：WF-03。扫描只拒绝FAILED，不等于只放行PASSED；票据认领后存储失败的恢复语义由文件链路核验，本次未模拟。
- 源码：`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:266`（通用OA API）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:277`（保存申请和明细）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:844`（绑定附件）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:301`（AI可选路由）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:740`（人工审批结算）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:861`（个人文件核验）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:640`（参与人申请票据）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:685`（重验并认领票据）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:705`（读取存储对象）；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:708`（返回文件并审计）。

## 考勤日历、班次、成员与排班变更

[流程图](att-governance.html) · [候选](att-governance.candidate.json) · [自动检查回执](att-governance.finalize-summary.json)

- 范围：管理配置→有效考勤组→成员区间锁→排班创建/修改→已有日结果重算。
- 当前入口：admin-web /attendance 路由真实配置接口；employee-web读取排班。
- 正常：创建日历、同日班次和考勤组；用户行锁内增加不重叠成员区间；个人同日排班唯一；修改已有排班时重算已有日结果。
- 异常/补偿：停用规则拒绝，成员区间重叠409；同日已有排班需修改原记录；修改锁排班及员工，异常事务回滚。
- 问题/优化：AT-02。日历保存名称/类型，但日结果核心无节假日/休息日判断分支；打卡窗口已保存，punch未使用。先明确业务约束，再补执行与验收，作为边界优化。
- 源码：`admin-web/src/views/AttendanceView.vue:200`（考勤治理页面）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:101`（创建日历与班次）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:158`（创建考勤组）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:192`（添加有效期成员）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:238`（创建个人排班）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:265`（修改原排班）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:285`（已有日结果重算）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:208`（区间或规则冲突）。

## 员工打卡、日结果与异常协调

[流程图](att-punch.html) · [候选](att-punch.candidate.json) · [自动检查回执](att-punch.review-2/att-punch.finalize-summary.json)

- 范围：hub真实OUT→服务端当前时钟→同日类型去重→规则/请假计算→异常增删与提交后事件。
- 当前入口：hub-web工作台OUT真实提交；其attendance独立页为Placeholder；employee-web时间展示有硬编码。
- 正常：hub-web工作台真实OUT调用；以服务端Clock落记录，校验员工和同日类型排重；个人排班优先组默认班次，获批请假抵扣异常。
- 异常/补偿：无有效考勤组写NO_RULE；请假全覆盖写LEAVE；否则计算NORMAL/ABNORMAL。异常按类型删除、更新、新增，仅新增提交后发事件；唯一键防并发重复打卡。
- 问题/优化：AT-01。hub-web独立attendance页Placeholder不能否定工作台真实打卡；employee-web工作台另有硬编码展示。未实际打卡、未改变进程。
- 源码：`hub-web/src/views/WorkbenchView.vue:127`（工作台下班打卡）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:300`（服务端打卡核验）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:305`（写入打卡记录）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:472`（解析当日规则）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:517`（计算日结果）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:539`（保存日结果）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:473`（无有效考勤组）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:488`（批准请假全覆盖）；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:590`（协调异常并发布）。

## 假种治理与管理员余额调整

[流程图](leave-governance.html) · [候选](leave-governance.candidate.json) · [自动检查回执](leave-governance.finalize-summary.json)

- 范围：假种CRUD→员工/假种有效性→外部key指纹幂等→账户行锁→非负余额→ADJUST流水。
- 当前入口：admin-web /leave 与 /leave-types 路由可达，余额调整真实调用。
- 正常：管理员假种CRUD；调整校验有效员工/假种及非零分钟；重复key核对指纹返回，新增key锁账户、非负检查后写ADJUST流水。
- 异常/补偿：同key不同参数拒绝，余额不足拒绝；调整仅改可用余额不改冻结；工作流冻结、扣减、释放使用各自请求级幂等key。
- 问题/优化：LV-01及既有99-3。后端幂等无法自动识别两个不同key属于同一次人工操作，需要前端稳定业务key。
- 源码：`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:51`（假种创建更新删除）；`admin-web/src/views/LeaveGovernanceView.vue:87`（管理员提交调整）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:95`（校验调整条件）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:104`（查询sourceTxnKey）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:113`（锁定或创建账户）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:119`（计算可用余额）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:125`（写ADJUST流水）；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:104`（重复请求返回账户）。

## 问题与验收

### WF-01 · P1 · 静态确认，入口未接通

- 触发：员工在当前审批中心选择请假、费用、加班或用印新申请
- 影响：仅提示表单即将上线，不调用提交接口；旧WorkbenchSection不能视作当前入口能力
- 证据：当前路由使用hub/ApprovalsView；pickCreateType只有showToast，无表单提交。未点真实页面。
- 位置：`employee-web/src/router/index.ts:46`；`employee-web/src/router/index.ts:78`；`employee-web/src/hub/views/ApprovalsView.vue:232`
- 修复：在当前工作台和审批路由接入请假、补卡与通用OA表单、附件和提交后状态跳转。
- 验收：通过当前入口提交各类申请，每次只新增一条；余额不足、无上级、附件越权均显示真实错误。

### LV-01 · P1 · 静态确认，未动态复现

- 触发：余额调整已提交，但响应丢失或请求报错后管理员重试同一次调整
- 影响：每次提交生成新的Date.now幂等key，服务端把重试当作新业务，可能重复调整
- 证据：后端按sourceTxnKey查询流水并核对指纹；前端每次submitAdjustment均生成新key。未模拟响应丢失。
- 位置：`admin-web/src/views/LeaveGovernanceView.vue:87`；`admin-web/src/views/LeaveGovernanceView.vue:99`；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:104`
- 修复：同一编辑意图保留稳定key，重试复用；成功确认或明确新建调整时再换key。
- 验收：服务端提交成功后丢弃首次响应，再重试，同一ADJUST流水只有一条且余额只变一次；同key不同参数拒绝。

### AT-01 · P2 · 静态确认，展示可信性缺口

- 触发：employee-web工作台当天workMinutes大于0或达到480分钟
- 影响：页面显示固定08:57或18:00，可能与真实首末打卡时间不一致
- 证据：punchIn按workMinutes真假选择08:57；punchOut按480阈值选择18:00。hub-web独立工作台则读取实际firstPunchAt/lastPunchAt。
- 位置：`employee-web/src/hub/views/WorkbenchView.vue:85`；`employee-web/src/hub/views/WorkbenchView.vue:86`；`employee-web/src/hub/views/WorkbenchView.vue:88`
- 修复：格式化日结果firstPunchAt/lastPunchAt；空值显示未打卡。
- 验收：输入实际09:12和18:37，分别显示09:12和18:37；只有时长不能凭空产生打卡时间。

### WF-02 · P2 · 静态确认，操作资格不一致

- 触发：未结束的PENDING_REVIEW申请在页面点击撤回
- 影响：前端按!finishedAt显示撤回，后端只接受IN_APPROVAL，操作会被拒绝，冻结仍等待预审终态
- 证据：预审请求未finishedAt；withdrawRequest非IN_APPROVAL抛400。未动态提交撤销。
- 位置：`employee-web/src/hub/views/ApprovalsView.vue:367`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:599`
- 修复：按后端允许状态显示撤回；若产品要求预审可取消，补任务取消、状态锁和冻结释放。
- 验收：预审中不显示不可用操作；本人IN_APPROVAL可撤回一次并释放冻结；终态和非本人拒绝。

### WF-03 · P2 · 静态确认，模板约束未执行

- 触发：generic-requests提交JSON对象，但漏模板必填字段或类型错误
- 影响：仅验证对象结构，未按模板formSchema做字段校验，错误业务数据可进入审批
- 证据：版本保存formSchemaJson；通用提交只检查isObject，然后直接压缩保存。未动态提交非法请求。
- 位置：`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:266`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:292`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:924`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java:120`
- 修复：按选定已发布版本校验必填、类型、长度和枚举，并返回字段错误。
- 验收：缺字段、错误类型、越界枚举返回400且无新增申请；正确请求可提交，版本更新不改变在途基准。

### AT-02 · P2 · 静态确认，既有结果可能陈旧

- 触发：当天已按默认班次生成日结果后，再首次创建当天个人排班
- 影响：createSchedule只存排班，日结果维持旧班次，直到另一打卡、审批或重算触发
- 证据：创建直接返回；修改则显式检查日结果存在并重算。未动态创建排班。
- 位置：`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:238`；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:252`；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:262`；`server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java:285`
- 修复：创建排班成功后，与修改路径一致地对已有日结果重算。
- 验收：默认班次已有结果，创建不同班次后，同一请求结束时shiftId、时长与异常立即更新。

### 99-3 · P1 · 既有问题，沿用第99审编号

- 触发：冻结后停用、删除假种或更改balanceMode
- 影响：终态重新读取当前有效假种/模式，可能阻断结算或跳过冻结处理
- 证据：冻结、扣减、释放均重新getActiveLeaveTypeOrThrow且按当前NO_DEDUCT跳过；仅引用既有问题，非新发现。
- 位置：`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:68`；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:85`；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:212`；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:262`；`server/src/main/java/com/workforcehub/leave/service/LeaveService.java:326`
- 修复：提交时固定结算策略，或依据原冻结流水驱动终态结算；停用不影响在途请求。
- 验收：冻结后改模式/状态，再批准、驳回或撤回，按原规则归零冻结且重复请求不二次结算。

### 99-4 · P2 · 既有问题，沿用第99审编号

- 触发：非固定9-12/13-18班次、休息日或小于半天请假
- 影响：固定工作区间和240分钟取整可能与真实排班不一致
- 证据：逐日套固定工作时段，按HALF_DAY_MINUTES向上取整；仅引用第99审，不算新发现。
- 位置：`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:904`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:930`；`server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java:942`
- 修复：请假计算统一消费有效日历、排班与假种计量规则。
- 验收：非标准班次、节假日、跨日和半天边界都与实际规则一致，冻结和扣减同基准。

## 交付检查

命令：`node /Users/sailtrack/.agents/skills/archify/bin/archify.mjs finalize workflow <candidate> <html> --repo-root /Users/sailtrack/工程文件/java_web/workforce-hub --quality showcase --json`。已有浏览器证据的后续候选使用独立`--out-dir`。

首次同目录并发触发`delivery/concurrent-attempt`，改为串行后全部完成。通用OA首版mainPath回列触发validate失败，局部调整授权顺序与主路径后通过；打卡全请假分支改由规则解析分叉，保留review-2证据。

`Workflow artifacts: 8 passed, 0 failed`；最终回执均`validate=pass, deliver=pass, check=pass, browser-check=pass`，执行exit 0。没有业务代码修改，未运行业务项目全量测试。
