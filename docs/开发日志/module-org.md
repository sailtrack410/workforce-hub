# 开发日志 - module-org

记录 `module-org` 相关开发改动，按时间倒序，最新在最上方。

---

## 2026-05-02 module-org 组织领域事件正式接入

### 改动背景

前一轮 `module-org` 第二批写侧已经能做：

- 部门、岗位、员工、生命周期、账号治理
- `hr_employee_event` 留痕
- 写侧自动化测试

但它仍然停在“本地总账记录”阶段。也就是说：

- 变化虽然被记下来了
- `module-im`、`module-workflow`、`module-agent` 理论上应该感知这些变化
- 但系统里还没有一个正式的内部事件出口把这些变化发出来

这会导致后续消息准入、审批候选刷新、Agent ACL 联动都只能继续靠直接查库或临时耦合，不利于后面扩模块。

### 本次目标

- 为 `module-org` 建立事务提交后的正式领域事件发布能力
- 把用户创建、直属上级变化、状态变化、账号变化、部门结构变化五类事件接入写侧主链路
- 让自动化测试不仅断业务结果，还断事件是否真的发出

### 执行过程

#### 1. 补统一领域事件发布器

新增：

- `DomainEvent`
- `DomainEventPublisher`

并采用“事务提交后再发布”的方式处理内部事件。

**意义**：

- 避免监听方读到未提交数据
- 为后续各模块消费事件建立统一入口，而不是在各服务里自己手搓 `ApplicationEventPublisher`

#### 2. 定义第一批组织领域事件契约

新增：

- `org.user.created`
- `org.user.manager-changed`
- `org.user.status-changed`
- `org.user.account-changed`
- `org.department.structure-changed`

对应事件对象统一放在 `common.event.org` 下。

**意义**：

- 让其他模块依赖的是“稳定事件契约”，而不是 `module-org` 的实现细节
- 后续接 `module-im`、`module-workflow`、`module-agent` 时，直接监听这些事件即可

#### 3. 把事件接入写侧主链路

本轮在 `OrgManagementService` 中把事件接入到了以下操作：

- 新增员工后发布 `org.user.created`
- 修改直属上级后发布 `org.user.manager-changed`
- 修改员工主档状态或生命周期状态后发布 `org.user.status-changed`
- 创建 / 更新 / 自动停用账号后发布 `org.user.account-changed`
- 新增 / 修改部门结构后发布 `org.department.structure-changed`

同时保留原先的 `hr_employee_event` 留痕，不做二选一替换。

**意义**：

- 现在系统里既有“可审计时间线”，也有“可被其他模块消费的实时事件”
- 后续做跨模块联动时，不必再从 `hr_employee_event` 反查推断变化

#### 4. 升级写侧自动化测试

在 `ModuleOrgWriteApiIntegrationTest` 中新增了对领域事件的断言：

- 新增部门 / 员工后，断言 `org.department.structure-changed`、`org.user.created`
- 生命周期与账号联动后，断言 `org.user.status-changed`、`org.user.account-changed`
- 成功修改部门结构与直属上级后，断言 `org.department.structure-changed`、`org.user.manager-changed`

并重新执行：

- `cd server && mvn -DskipTests compile`
- `cd server && mvn -Dtest=ModuleOrgWriteApiIntegrationTest test`
- `cd server && mvn -Dtest=ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest test`

结果：

- 编译通过
- 写侧 `4` 个场景通过
- 读写联合 `10` 个场景通过

**意义**：

- 以后如果有人把业务接口保住了，但把事件链路改没了，测试会直接报警

### 当前结果

- `module-org` 已正式具备内部领域事件发布能力
- `hr_employee_event` 继续承接审计留痕
- 组织事件的发布与业务结果都已进入自动化测试范围

### 已知待补项

- 当前只完成了事件生产端，还没有在 `module-im`、`module-workflow`、`module-agent` 接正式消费者
- 集成测试仍写本地 `workforce_hub`，后续还要拆测试库或容器化环境

---

## 2026-05-02 module-org 第二批写侧提交前复核

### 改动背景

在 `module-org` 第二批写侧代码进入正式提交前，需要先确认三件事：

- 当前控制器、服务层和留痕实体是否已经完整落位
- 写侧自动化测试是否稳定通过
- 之前第 11 审中记录的建议项之外，是否还冒出新的阻断问题

这一步的重点不是继续加功能，而是把“已经写出来的东西”做一次提交前收口。

### 本次目标

- 对未提交的 `module-org` 写侧代码做一轮提交前复核
- 重新执行编译与读写联测
- 判断这批改动是否已经达到可独立提交状态

### 执行过程

#### 1. 复核未提交改动范围

本轮重点复核了以下内容：

- `AdminOrgController` 的 8 个写侧接口入口
- `OrgManagementService` 的部门、岗位、员工、生命周期、账号联动规则
- `HrEmployeeEvent` / `HrEmployeeEventMapper` 的留痕落点
- `ModuleOrgWriteApiIntegrationTest` 与 `ModuleOrgApiIntegrationTest`

**意义**：

- 确认这次提交不会只是“接口露出来了”，而是服务层、测试层、留痕层一起成套收口

#### 2. 重新执行编译验证

执行：

- `cd server && mvn -DskipTests compile`

结果：

- 编译通过

**意义**：

- 说明当前这批写侧代码已经没有基础编译错误或依赖缺口

#### 3. 重新执行写侧与读写联合测试

执行：

- `cd server && mvn -Dtest=ModuleOrgWriteApiIntegrationTest test`
- `cd server && mvn -Dtest=ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest test`

结果：

- 写侧 `3` 个场景通过
- 读写联合 `9` 个场景通过

覆盖重点包括：

- 部门、岗位、员工新增后读侧可回查
- 生命周期与账号启停规则联动
- 部门树循环与上下级循环拦截
- 读侧原有查询路径不被写侧改动破坏

**意义**：

- 证明这批写侧改动不仅自己能跑通，也没有破坏第一批查询接口基线

#### 4. 对照第 11 审复看残留风险

本轮复核后，没有发现新的阻断或重大问题。

当前仍保留、但不阻塞提交的事项主要还是第 11 审已记录的两类：

- `hr_employee_event` 还只是本地留痕，尚未升级为正式跨模块领域事件
- 集成测试仍直接写本地 `workforce_hub`，还没有独立测试库

**意义**：

- 保持对工程边界的诚实口径
- 避免把“可提交”误写成“所有后续问题都已经解决”

### 当前结果

- `module-org` 第二批写侧代码已完成提交前复核
- 编译通过
- 写侧单测 / 读写联测全部通过
- 当前没有发现新的阻断或重大问题

### 已知待补项

- 组织变化还没有升级为正式领域事件发布
- 集成测试仍需后续拆分独立测试库或容器化测试环境

---

## 2026-05-02 module-org 第二批写侧治理基线落地

### 改动背景

前一轮 `module-org` 已经具备：

- 组织树、岗位列表、员工目录的读侧查询
- 本地冒烟种子数据
- 自动化查询测试基线

但如果没有后台写侧，后面的审批、消息、群组和 Agent 权限都拿不到稳定的组织治理入口。尤其是：

- 部门树不能治理
- 岗位不能治理
- 员工档案不能正式维护
- 生命周期与账号启停规则只能停留在文档层

所以这一步的目标不是“再补几个接口”，而是把组织治理真正推进到可用的后台写侧闭环。

### 本次目标

- 落地部门、岗位、员工三类后台写侧接口
- 把员工生命周期状态机和账号启停规则打通
- 为组织关系变化补基础留痕
- 为写侧补自动化测试、API 文档、测试用例文档

### 执行过程

#### 1. 落地后台写侧接口族

本轮在 `AdminOrgController` 中补齐了 8 个写侧接口：

- `POST /api/admin/departments`
- `PUT /api/admin/departments/{deptId}`
- `POST /api/admin/positions`
- `PUT /api/admin/positions/{positionId}`
- `POST /api/admin/users`
- `PUT /api/admin/users/{userId}`
- `PUT /api/admin/users/{userId}/employment-status`
- `PUT /api/admin/users/{userId}/account`

**意义**：

- `module-org` 从“只能查”升级为“能治理”
- 后续后台页面、审批组织底座、IM 关系治理都有了正式写入口

#### 2. 把核心业务规则收口到统一服务层

新增：

- `OrgManagementService`

集中承接以下规则：

- 部门编码、岗位编码、工号唯一性
- 部门树循环校验
- 上下级循环校验
- 员工主档默认值与日期回落规则
- 生命周期合法流转校验
- 员工主档状态 / 生命周期状态与账号状态联动

同时补了写侧 DTO，避免控制器直接堆规则。

**意义**：

- 把高风险规则收在一处，便于后续测试和扩展
- 避免控制器里散落大量 if/else，降低维护成本

#### 3. 补部门树路径与层级联动

部门不仅要能新增和编辑，还要保证树结构持续有效。因此本轮除了基本新增，还补了：

- `parentId` 变更时自动重算 `tree_path`
- `deptLevel` 同步重算
- 子孙节点路径与层级同步级联更新
- 禁止把部门挂到自己或自己的子部门下面

过程中还在自动化测试里打出了一个真实问题：

- `org_department.tree_path` 没有数据库默认值

随后修正为：

- 创建部门时先生成 ID，再在入库前直接写入完整 `tree_path`

**意义**：

- 这让部门树不只是“字段上有层级”，而是真正可持续维护的树结构
- 也避免了写侧上线后出现一新增部门就 500 的基础故障

#### 4. 打通员工生命周期与账号启停

本轮把文档里的状态机真正落实到代码里：

- `PENDING_ONBOARD -> PROBATION / ACTIVE`
- `PROBATION -> ACTIVE / PENDING_OFFBOARD`
- `ACTIVE -> PENDING_OFFBOARD`
- `PENDING_OFFBOARD -> PROBATION / ACTIVE / OFFBOARDED`

并补齐账号联动规则：

- `PENDING_ONBOARD`、`OFFBOARDED` 不允许把业务账号设为 `ACTIVE`
- 员工主档状态变为非 `ACTIVE` 时，自动停用已有账号
- 生命周期流转到不允许登录的状态时，自动把已有账号降为 `DISABLED`

**意义**：

- 生命周期不再只是“写个状态值”，而是真正影响系统行为
- 后续审批、IM、Agent 在读组织状态时不会再遇到“文档说不能登，账号却还开着”的矛盾

#### 5. 为组织变化补基础留痕

新增：

- `HrEmployeeEvent`
- `HrEmployeeEventMapper`

当前已记录的变化类型包括：

- `TRANSFER_DEPARTMENT`
- `TRANSFER_POSITION`
- `CHANGE_MANAGER`
- `ONBOARD`
- `REGULARIZE`
- `PREPARE_OFFBOARD`
- `OFFBOARD`
- `CHANGE_EMPLOYMENT_STATUS`

并把前后快照写入 `hr_employee_event`。

**意义**：

- 员工异动开始具备基础时间线
- 为后续做正式领域事件和审计回放留出了落点

#### 6. 补写侧自动化测试并校正读侧测试稳健性

新增：

- `server/src/test/java/com/workforcehub/org/ModuleOrgWriteApiIntegrationTest.java`

覆盖了：

- 部门新增与回查
- 岗位新增与回查
- 员工新增与默认字段回查
- 待入职员工禁止直接启用账号
- 生命周期流转与账号自动停用
- 部门树循环拦截
- 上下级循环拦截

另外也把旧的读侧测试做了一次稳健性修正：

- 不再假定本地数据库里只有固定 2 个下级
- 改为断言“至少包含种子员工，且直属上级关系正确”

**意义**：

- 写侧不再只有手工验证，而是有自动化回归托底
- 读写测试现在可以一起跑，不会因为断言过脆导致假失败

### 验证结果

执行命令：

```bash
mvn -DskipTests compile
mvn -Dtest=ModuleOrgWriteApiIntegrationTest test
mvn -Dtest=ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest test
```

结果：

- 编译通过
- 写侧自动化测试 `3` 个场景全部通过
- 读侧 + 写侧联合执行 `9` 个场景全部通过

### 当前结果

- `module-org` 已具备第一版后台写侧治理能力
- 部门、岗位、员工、生命周期、账号联动已进入同一套规则体系
- `hr_employee_event` 已开始承接组织变化留痕
- 写侧已补自动化测试、API 文档和测试用例文档

### 改动文件清单

- `server/src/main/java/com/workforcehub/org/controller/AdminOrgController.java` - 补后台写侧接口
- `server/src/main/java/com/workforcehub/org/service/OrgManagementService.java` - 收口写侧核心规则
- `server/src/main/java/com/workforcehub/org/dto/*.java` - 新增写侧请求 DTO
- `server/src/main/java/com/workforcehub/hr/domain/HrEmployeeEvent.java` - 员工变化留痕实体
- `server/src/main/java/com/workforcehub/hr/mapper/HrEmployeeEventMapper.java` - 员工变化留痕 Mapper
- `server/src/test/java/com/workforcehub/org/ModuleOrgWriteApiIntegrationTest.java` - 写侧自动化冒烟测试
- `server/src/test/java/com/workforcehub/org/ModuleOrgApiIntegrationTest.java` - 读侧测试稳健性校正
- `docs/API与测试/module-org/API文档_module-org第二批写侧接口.md` - 第二批写侧 API 文档
- `docs/API与测试/module-org/API测试用例_module-org第二批写侧接口.md` - 第二批写侧测试用例
- `docs/架构与设计/接口与事件清单.md` - 同步系统级接口目录
- `docs/开发日志/module-org.md` - 记录本次写侧落地

### 已知待补项

- 当前 `hr_employee_event` 还是本地总账记录，尚未升级为正式领域事件发布
- 当前测试仍写本地 `workforce_hub`，还没有隔离测试库
- 当前后台端仍未接细粒度 RBAC 权限点校验
- 当前还没有独立的部门领导成员治理接口

---

## 2026-05-02 module-org 第一批查询接口自动化冒烟落地

### 改动背景

前一轮已经完成：

- 后端骨架可编译
- 应用可启动
- `health / login / org query` 主链路可手工联调

但如果只停留在 curl 或手工验证层面，后续每次改代码都要重新人工回归，效率和稳定性都不够。既然这批接口已经有固定种子数据和明确用例，最自然的下一步就是把 P0 主路径沉淀成自动化测试。

### 本次目标

- 为 `module-org` 第一批查询接口补自动化接口测试
- 让健康检查、登录、查询主路径、跨端隔离、参数校验进入可重复回归状态
- 复用现有本地种子数据，不再维护第二套手写测试数据

### 执行过程

#### 1. 增加本地集成测试支撑基类

新增：

- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java`

职责包括：

- 统一注入本地 MySQL / Redis 连接配置
- 为测试使用独立的 Spring Session Redis namespace
- 在测试前自动执行 `seed_module_org_smoke_data.sql`

**意义**：

- 避免每个测试类重复写一遍本地环境参数
- 复用现有联调种子脚本，保证“手工联调数据”和“自动化测试数据”是同一套

#### 2. 增加 module-org 自动化冒烟测试

新增：

- `server/src/test/java/com/workforcehub/org/ModuleOrgApiIntegrationTest.java`

本轮覆盖了 6 组自动化场景：

- 健康探针匿名访问
- 未登录后台接口返回 `401`
- 后台登录后查询组织树与员工直属上级
- 员工登录后查询本人档案、直属上级、空下级语义
- 领导员工查询非空下级、后台端查询 `manager=null`
- 跨端访问 `403` 与参数越界 `400`

**意义**：

- 把当前最核心、最稳定的主链路固化下来
- 以后改动组织查询、会话鉴权、异常处理时，可以第一时间发现回归

#### 3. 真实执行自动化测试

执行命令：

```bash
mvn -Dtest=ModuleOrgApiIntegrationTest test
```

结果：

- `Tests run: 6`
- `Failures: 0`
- `Errors: 0`
- `BUILD SUCCESS`

**意义**：

- 证明这批测试不只是“写出来了”，而是已经真实可执行
- 当前项目正式从“能手工联调”迈到了“有第一批自动化回归基线”

### 当前结果

- `module-org` 第一批查询接口已有自动化冒烟测试
- 健康检查、登录、组织查询、跨端隔离、参数校验都已进入自动化覆盖
- 本地种子数据脚本同时服务于手工联调和自动化测试

### 改动文件清单

- `server/src/test/java/com/workforcehub/support/LocalIntegrationTestSupport.java` - 本地集成测试支撑基类
- `server/src/test/java/com/workforcehub/org/ModuleOrgApiIntegrationTest.java` - `module-org` 第一批查询接口自动化冒烟测试
- `docs/开发日志/module-org.md` - 记录本次自动化测试落地

### 已知待补项

- 当前自动化覆盖仍以 P0 主路径为主，尚未覆盖全部 P1 / P2 场景
- 当前测试依赖本地 MySQL / Redis，不是完全自包含的容器化测试环境
- 还没有接入 CI，测试仍需手动触发

---

## 2026-05-02 启动冒烟修正与本地联调基线补齐

### 改动背景

`module-org` 第一批查询接口虽然已经有代码和 API 文档，但真正开始本地冒烟时暴露出两个阻塞点：

- 服务虽然能启动，但 `GET /actuator/health` 实际不存在
- 数据库还是空库，导致登录和组织查询接口无法做真实联调

如果不先补齐这两块，后面的“按测试点推进”会停留在只能编译、只能启动，却不能稳定验证接口行为的阶段。

### 本次目标

- 补一条真实可用的健康检查链路
- 修正“路径不存在被兜成 500”这个错误语义
- 增加一份可重复执行的本地冒烟种子数据脚本
- 让 `module-org` 第一批查询接口进入可登录、可查询、可回归的状态

### 执行过程

#### 1. 复测启动链路并确认真实阻塞点

本轮先按测试点重新执行：

- `mvn -DskipTests compile`
- `mvn spring-boot:run --server.port=8082`
- `curl /actuator/health`
- `curl /api/admin/departments/tree`

结果确认：

- 编译通过
- 应用可在避开 `8080` 冲突后完整启动
- 未登录后台接口返回 `401` JSON 正常
- `/actuator/health` 返回的是 `500`，根因不是权限，而是项目尚未引入 Actuator，同时全局异常把缺失路径兜成了内部错误

**意义**：

- 先区分“服务没起来”和“服务起来了但健康检查缺失”这两类问题
- 避免后面把测试失败误判成数据库或安全配置问题

#### 2. 补健康检查能力

在后端工程中补入 `spring-boot-starter-actuator`，并显式开放：

- `health`
- `info`

同时把 Spring Security 对健康探针的放行范围扩到：

- `/actuator/health`
- `/actuator/health/**`

并开启健康探针配置，作为后续 Docker 化、反向代理探活、比赛演示时的统一检查入口。

**意义**：

- 满足项目“可观测性”和“健康检查接口”这条工程基线
- 后续不论本地、Docker 还是部署环境，都能用同一条路径验证服务状态

#### 2.1 修正本地健康探针被 SMTP 拖红的问题

复测后进一步确认：默认本地 SMTP 指向 `localhost:1025`，在 MailHog / Mailpit 未启动时，`MailHealthIndicator` 会把整体健康状态拉成 `DOWN`。因此新增环境变量：

- `MAIL_HEALTH_ENABLED`

当前默认值为 `false`，让本地健康探针优先反映主链路依赖；后续如果邮箱链路进入正式联调或部署探活，需要再显式打开。

**意义**：

- 保证当前阶段健康探针真正反映“核心链路是否可用”
- 同时保留后续把邮件纳入健康治理的开关

#### 3. 修正 404 / 500 语义

在 `GlobalExceptionHandler` 中新增 `NoResourceFoundException` 的专门处理，改为：

- HTTP `404`
- `code=404`
- `message=请求路径不存在`

不再把“路径没找到”一律兜成 `500`。

**意义**：

- 让接口错误语义更可信
- 前端、测试、后续监控不会再把错误路径误报成系统故障

#### 4. 补本地冒烟种子数据

新增：

- `server/scripts/sql/seed_module_org_smoke_data.sql`

这份脚本会初始化：

- 部门
- 岗位
- 员工档案
- 员工登录账号
- 后台管理员账号

并使用固定 ID 与固定登录名，对齐现有 API 测试用例里的示例数据。

**意义**：

- 让文档中的“建议测试数据”升级为“可直接导入的数据基线”
- 后续每次联调、回归、演示都能快速回到一致的数据环境

#### 5. 校正文档与真实实现

同步修正了两份文档：

- `SESSION` Cookie 命名
- 本地种子脚本入口
- 冒烟顺序中的初始化步骤
- 本地演示账号说明

**意义**：

- 让 API 文档、测试用例和真实运行时行为保持一致
- 避免联调时按文档带 `JSESSIONID`，实际项目却使用 `SESSION`

### 当前结果

- 应用已具备真实健康检查能力
- 缺失路径不再误报 `500`
- `module-org` 第一批查询接口已有本地冒烟种子数据基线
- 文档、测试数据和当前运行机制已进一步对齐

### 改动文件清单

- `server/pom.xml` - 增加 Actuator 依赖
- `server/src/main/resources/application.yml` - 增加健康检查暴露配置
- `.env.example` - 增加 `SERVER_PORT` 与 `MAIL_HEALTH_ENABLED` 样板
- `server/src/main/java/com/workforcehub/common/exception/GlobalExceptionHandler.java` - 新增 404 异常处理
- `server/src/main/java/com/workforcehub/common/config/SecurityConfig.java` - 放行 `/actuator/health/**`
- `server/scripts/sql/seed_module_org_smoke_data.sql` - 新增本地冒烟种子数据脚本
- `docs/API与测试/module-org/API文档_module-org第一批查询接口.md` - 修正 Session Cookie 说明
- `docs/API与测试/module-org/API测试用例_module-org第一批查询接口.md` - 增加种子脚本与本地演示账号说明
- `docs/开发日志/module-org.md` - 记录本次启动冒烟修正与联调基线补齐

### 已知待补项

- 当前仍未沉淀自动化接口测试脚本，API 回归主要依赖手工或 curl 冒烟
- 当前测试数据脚本定位为本地/临时联调基线，不适合直接进入共享环境
- `module-org` 第二批写侧和事件联动尚未开始

---

## 2026-05-02 module-org API 文档与测试用例补齐

### 改动背景

`module-org` 第一批查询接口已经落地，但仅有 Controller、Service 和 DTO 还不够，后续前端联调、接口测试、比赛演示都需要一份可以直接执行的正式 API 文档。如果继续只靠《接口与事件清单》这种目录型文档，会出现“知道有接口，但不知道请求格式、错误语义和测试边界”的问题。

### 本次目标

- 为 `module-org` 第一批查询接口补详细 API 文档
- 为同一批接口补测试用例文档
- 把统一响应、鉴权、错误码、分页、请求头等通用约定写清楚

### 执行过程

#### 1. 收口文档职责

先明确三类文档的边界：

- 《接口与事件清单》：继续负责系统级目录
- 《API 文档》：负责单模块单批次接口的详细说明
- 《API 测试用例》：负责测试范围、前置条件、步骤和预期结果

**意义**：
- 避免一份文档同时承担目录、说明书、测试单三种职责，后面越写越乱
- 为后续审批、消息、Agent 等模块补 API 文档时建立统一模板

#### 2. 编写正式 API 文档

新增《API文档_module-org第一批查询接口》，覆盖：

- 文档范围
- 基础地址
- Session 鉴权方式
- 请求头 / 响应头
- 统一响应结构
- 分页结构
- 错误处理约定
- 数据模型定义
- 9 个接口的详细说明
- 请求示例与响应示例
- 联调建议

**意义**：
- 前端、测试、后端自己回看时都能直接拿来用
- 不是只知道“接口存在”，而是明确知道“怎么调、会返回什么、异常时怎么处理”

#### 3. 编写测试用例文档

新增《API测试用例_module-org第一批查询接口》，按企业接口测试思路拆成：

- 测试目标
- 测试环境建议
- 测试数据建议
- 覆盖策略
- 明细用例表
- 冒烟顺序
- 回归判定标准

覆盖了：

- 正常查询
- 未登录
- 路径串用
- 参数越界
- 对象不存在
- `requestId` 回写
- `manager = null`
- `subordinates = []`

**意义**：
- 后续不论手工联调还是自动化测试，都有一份结构化测试基线
- 比赛演示前也能快速按用例清单做一遍冒烟

#### 4. 校准文档与代码一致性

在写 API 文档时，逐项核对了：

- Controller 路径
- Query 参数
- DTO 字段
- 统一响应包
- RequestId 透传
- Security 鉴权规则
- 全局异常处理

并特别在文档中写明当前一个容易被忽略的实现边界：

- `401/403/400/500` 会通过 HTTP 状态码直接体现
- 某些业务异常（如“员工不存在”）当前是 `HTTP 200 + code=404`

**意义**：
- 防止文档写成“理想行为”，而实际接口跑起来不是那样
- 减少前端和测试在联调时对错误语义的误判

### 当前结果

- `module-org` 第一批查询接口已有正式 API 文档
- 同批接口已有测试用例文档
- 《接口与事件清单》已补充详细文档入口

### 改动文件清单

- `docs/API与测试/module-org/API文档_module-org第一批查询接口.md` - 详细 API 文档
- `docs/API与测试/module-org/API测试用例_module-org第一批查询接口.md` - 详细测试用例
- `docs/架构与设计/接口与事件清单.md` - 增加详细文档入口说明
- `docs/开发日志/module-org.md` - 记录本次文档补齐过程

### 已知待补项

- 当前只覆盖 `module-org` 第一批查询接口，写侧接口文档还要等第二批实现
- 还没有导出 Postman / Apifox 集合
- 当前测试用例是文档级基线，还不是自动化执行脚本

---

## 2026-05-02 module-org 第一批查询骨架落地

### 改动背景

前一阶段已经把认证骨架、本机环境和开发流程总览收口完成，接下来按《开发流程总览》正式进入 `第 2 流程：认证与组织主链路`。考虑到后续审批找领导、消息私聊准入、Agent 组织查询都依赖统一的组织查询底座，本轮先不急着做组织增删改，而是优先把读侧主链路落下来。

### 本次目标

- 落 `module-org` 的第一批读接口
- 打通部门树、岗位列表、员工列表/详情、直属上级/下级查询
- 同时把 `OrgQueryService` SPI 接上，为后续 Agent 调用提供复用入口

### 执行过程

#### 1. 收口组织模块的第一批边界

这一轮只做“查询主链”，不把新增、编辑、绑定关系、事件发布一股脑塞进来。具体收口为两端共 9 个接口：

- 后台端：
  - `GET /api/admin/departments/tree`
  - `GET /api/admin/positions`
  - `GET /api/admin/users`
  - `GET /api/admin/users/{id}`
  - `GET /api/admin/users/{id}/manager`
  - `GET /api/admin/users/{id}/subordinates`
- 员工端：
  - `GET /api/employee/me/profile`
  - `GET /api/employee/me/manager`
  - `GET /api/employee/me/subordinates`

**意义**：
- 先把最容易被多个模块复用的读侧能力打通
- 避免还没稳定边界就把写侧规则做散、做重

#### 2. 落组织域实体与 Mapper

新增了 `org_department`、`org_position`、`org_department_leader` 三张表的实体和基础 Mapper，同时新增 `OrgDirectoryMapper + XML` 承担跨表查询，把：

- 部门树
- 员工列表
- 员工详情
- 直属上级
- 直属下级

这些高频目录查询统一收口到 `module-org` 内部。

**意义**：
- 避免后续 `workflow`、`im`、`agent` 各自写一套组织查询 SQL
- 让组织读模型先稳定下来，再谈写模型和事件模型

#### 3. 统一组织查询服务层

新增 `OrgDirectoryService`，把树构建、员工分页、详情查询、当前员工档案查询统一放在一个服务里。同时补了 `OrgQueryServiceImpl`，把组织信息转成 SPI 需要的摘要结构，供 Agent 后续通过 `module-spi` 调用。

**意义**：
- 业务端 Controller 不直接碰复杂 SQL
- 后续 Agent、审批、消息三条链都能复用同一份组织查询口径

#### 4. 挂两端 Controller

新增：

- `AdminOrgController`
- `EmployeeOrgController`

这样后台端可以直接查组织树和员工目录，员工端也能直接拿到自己的档案、直属上级和直属下级列表。

**意义**：
- 后续前端接组织管理页、个人资料页、团队页时有真实接口可接
- 私聊准入规则也终于有了第一版后端数据来源

#### 5. 文档同步收口

除了代码本身，本轮还同步更新了：

- 《接口与事件清单》
- 《后端工程骨架说明》
- 《开发日志》

确保“代码已实现”和“文档仍写待实现”不会继续漂移。

**意义**：
- 降低后续自己回看或继续协作时的认知成本
- 让当前工程状态在文档层也能被准确表达

#### 6. 编译验证

使用 `JDK 17` 执行 `mvn -DskipTests compile`，确认本轮新增的 `module-org` 查询骨架没有破坏工程可编译性。

**意义**：
- 证明这次不是“只把包和类建出来”，而是真正接进了现有工程

### 当前结果

- `module-org` 第一批查询接口已落地
- 后台端可查询部门树、岗位列表、员工目录和上下级关系
- 员工端可查询个人档案、直属上级、直属下级
- `OrgQueryService` SPI 已有真实实现
- 工程编译通过

### 改动文件清单

- `server/src/main/java/com/workforcehub/org/domain/*` - 组织域实体
- `server/src/main/java/com/workforcehub/org/dto/*` - 组织模块查询 DTO
- `server/src/main/java/com/workforcehub/org/mapper/*` - 组织模块 Mapper
- `server/src/main/resources/mapper/org/OrgDirectoryMapper.xml` - 组织查询 SQL
- `server/src/main/java/com/workforcehub/org/service/*` - 组织查询服务与 SPI 实现
- `server/src/main/java/com/workforcehub/org/controller/*` - 后台端与员工端组织查询接口
- `docs/架构与设计/接口与事件清单.md` - 新增组织查询接口记录
- `docs/架构与设计/后端工程骨架说明.md` - 模块状态更新
- `docs/开发日志/module-org.md` - 记录本次实现过程

### 已知待补项

- 当前只完成了读侧主链，组织新增、编辑、上下级绑定还没有进入写侧实现
- `org_department_leader` 目前只完成了实体与 Mapper，部门领导成员的独立查询还没单独展开
- 员工列表的 `deptId` 过滤当前按主部门精确匹配，尚未扩展到整棵子树范围
- 组织事件发布（如 `org.user.manager-changed`、`org.user.status-changed`）还没开始接入

---
