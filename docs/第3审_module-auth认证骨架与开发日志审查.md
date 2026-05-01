# 第 3 审 module-auth 认证骨架与开发日志审查

- 审查日期：2026-05-01
- 审查范围：`docs/开发日志.md`、`docs/后端工程骨架说明.md`、`server/pom.xml`、`server/src/main/resources/application.yml`、`server/src/main/java/com/workforcehub/auth/**`、`server/src/main/java/com/workforcehub/common/config/**`、`server/src/main/java/com/workforcehub/common/response/**`、`server/src/main/java/com/workforcehub/common/exception/**`、`server/src/main/java/com/workforcehub/common/util/**`
- 审查目标：识别 `module-auth` 认证骨架在构建可用性、认证安全链路、Flowable 组件边界和文档一致性上的阻断性与重大问题，判断当前骨架是否适合继续承接后续业务模块开发

---

## 第一部分：逻辑审查

### 一、构建与工程可用性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 1-1 | `server/pom.xml` + `server/` 构建链路 | 当前在 `server/` 目录执行 `mvn -DskipTests compile` 无法通过，报错集中在 DTO、实体、统一响应和日志类的 Lombok 生成成员缺失，如 `getLoginName`、`setCode`、`log` 等。项目源码大量依赖 `@Data`、`@Getter`、`@Slf4j`，现象与 Maven 构建时注解处理链路未稳定生效高度一致；同时本机 `java -version` 与 `mvn -version` 实际使用的 JDK 版本不一致，也进一步放大了构建不确定性。 | 阻断 | 在 `pom.xml` 中显式补齐 `maven-compiler-plugin` 与 Lombok `annotationProcessorPaths`，统一 Maven/JDK 到项目受支持版本（建议 JDK 17 或 21），并将 `mvn -DskipTests compile` 设为后续提交前的硬校验。 |

### 二、认证与安全链路

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `server/src/main/java/com/workforcehub/common/config/SecurityConfig.java` + `server/src/main/java/com/workforcehub/auth/controller/EmployeeAuthController.java` + `server/src/main/java/com/workforcehub/auth/service/AccountService.java` | 员工端安全白名单与接口语义存在冲突。当前仅放行 `/api/employee/auth/email/**`，却没有放行 `/api/employee/auth/password/reset`；与此同时 `/api/employee/auth/email/bind` 被整体放入公开白名单，但业务实现又要求员工已登录后才能绑定邮箱。结果就是“找回密码接口被拦截，绑定邮箱接口放宽过头”。 | 重大 | 将员工端白名单按接口粒度拆开，只公开 `login`、`email/send-code`、`password/reset` 等匿名入口，`email/bind` 继续走登录态校验，避免路径通配把不同安全级别的接口混在一起。 |
| 2-2 | `server/src/main/java/com/workforcehub/auth/service/LoginService.java` | 登录成功后当前是直接获取已有 Session 并手动写入 `SPRING_SECURITY_CONTEXT`，没有先废弃旧 Session 或轮换 session id。这会留下典型的 session fixation 风险，在基于 Session 的 B 端系统里属于应尽早收口的安全问题。 | 重大 | 登录成功后先使旧 Session 失效，再创建新 Session 写入认证上下文，或直接使用 Spring Security 提供的会话固定防护能力，保证登录前后的会话标识发生轮换。 |
| 2-3 | `server/src/main/java/com/workforcehub/auth/service/LoginService.java` + `server/src/main/java/com/workforcehub/common/exception/GlobalExceptionHandler.java` | 认证流程当前只捕获了 `BadCredentialsException` 和 `UsernameNotFoundException`。如果 Spring Security 抛出 `LockedException`、`DisabledException` 等账户状态异常，大概率会掉入全局兜底，最终返回 500，而不是清晰的认证失败响应。 | 重大 | 在登录服务或统一异常层显式处理账户锁定、禁用、凭证过期等常见认证异常，统一返回明确的业务码和提示文案，避免把正常认证失败误报为系统错误。 |
| 2-4 | `server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java` | 当前员工登录只明确禁止 `OFFBOARDED` 用户，`PENDING_ONBOARD`、`PENDING_OFFBOARD` 等状态是否允许登录没有和员工生命周期规则对齐。如果后续生命周期设计要求“未正式入职前不得登录”，这里会和业务口径发生偏差。 | 建议 | 结合 `org_user.employment_status` 的最终业务定义补一份登录准入矩阵，明确哪些状态允许登录、哪些状态只能查看只读内容、哪些状态必须完全禁止。 |

### 三、Flowable 组件边界与迁移治理

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `server/src/main/resources/application.yml` | 项目已经启用了 Flyway 管理主业务库迁移，但当前仍保留 `flowable.database-schema-update: true`。这意味着 Flowable 依旧可以绕过迁移脚本直接改库，数据库治理边界还没有真正收口。 | 重大 | 明确唯一的结构变更入口。若 Flowable 表后续也纳入迁移体系，则关闭自动改库；若暂时保留 Flowable 自管，也应在文档与部署说明中明确阶段性边界，避免环境差异失控。 |
| 3-2 | `server/pom.xml` | 依赖虽然已从“单个大总包”收窄到 `flowable-spring-boot-starter-process` 和 `flowable-spring-boot-starter-rest`，但 `rest` starter 仍会把 `starter`、`app`、`cmmn`、`dmn` 等整串依赖重新带回 classpath。也就是说，“只保留审批流程所需最小组件”的目标目前并未真正落地。 | 重大 | 如果当前阶段并不准备直接暴露 Flowable 原生 REST 接口，建议移除 `flowable-spring-boot-starter-rest`；若必须保留，则在工程说明中明确这是有意识接受的依赖扩张，而不是“已收敛完成”的状态。 |

### 四、文档一致性

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `docs/开发日志.md` | 文档中写“第 2 审 6 个问题修完后，基础设施骨架进入稳定状态”，但当前 `server` 在 Maven 构建下仍无法通过编译，且 Flowable 改库边界也还处于“暂时保留 true、后续再处理”的阶段。这个表述明显比当前工程真实状态更乐观。 | 建议 | 将开发日志中的阶段性判断改为更准确的口径，例如“骨架方向基本成型，但构建链路、认证安全链路和 Flowable 边界仍待收口”，避免误导后续排期和协作判断。 |
| 4-2 | `docs/开发日志.md` + `server/src/main/java/com/workforcehub/auth/service/EmailCodeService.java` | 开发日志前文将验证码 Redis Key 口径写成 `auth:email:code:{email}` / `auth:email:send:{email}`，而当前实现实际使用的是带 `purpose` 维度的 `auth:email:code:{purpose}:{email}` / `auth:email:send:{purpose}:{email}`。设计说明与实现已经出现偏移。 | 建议 | 同步修正文档中的 Redis Key 设计描述，使其与实际代码一致，并补充“按 purpose 隔离验证码上下文”的设计原因，方便后续扩展管理员端或更多验证码用途。 |
| 4-3 | `docs/后端工程骨架说明.md` + `server/src/main/resources/db/migration/V1__init_schema.sql` | 文档中仍写 MySQL 主业务库为 68 张表，而实际 `V1__init_schema.sql` 中 `CREATE TABLE` 数量已达到 69 张；同一个 DDL 文件头部注释又仍保留“覆盖全部 46 张业务表”的旧口径。当前表数量说明已经有多处不一致。 | 建议 | 重新按真实 DDL 统一总表数、各业务域小计和 DDL 头注释，确保设计文档、初始化脚本和后续排期口径完全一致。 |

### 五、审查问题汇总统计

| 严重程度 | 数量 | 涉及序号 |
| --- | --- | --- |
| 阻断 | 1 | 1-1 |
| 重大 | 5 | 2-1、2-2、2-3、3-1、3-2 |
| 建议 | 4 | 2-4、4-1、4-2、4-3 |
| 合计 | 10 |  |

### 六、优先处理建议

**第一优先级（继续承接业务开发前建议先处理）：**

1. **1-1** 修复 Maven 构建链路，先让 `server` 恢复到可稳定编译状态
2. **2-1** 收口员工端匿名白名单，修正找回密码与邮箱绑定的安全边界
3. **2-2** 在登录成功后补 Session 轮换，消除会话固定风险
4. **2-3** 补齐锁定/禁用等认证异常的明确处理，避免正常认证失败掉成 500
5. **3-1** 明确 Flyway 与 Flowable 的改库边界，避免后续环境差异

**第二优先级（建议在这一轮骨架整理时一并处理）：**

- **3-2** 重新确认是否真的需要保留 Flowable 原生 REST 相关依赖
- **2-4** 将员工生命周期状态与登录准入规则对齐
- **4-1、4-2、4-3** 修正文档中“稳定状态”“验证码 Key 口径”“表数量统计”三类偏差

---

## 第二部分：module-auth 骨架成熟度评估

### 一、当前正向结论

- `module-auth` 的分层方向是对的，已经具备 `controller + service + security + mapper + dto` 的基础形态。
- 员工端与后台端的账号体系拆分思路正确，`WfhUserDetails`、双 `UserDetailsService`、`UserTypePathFilter` 这些基础抽象方向没有问题。
- `requestId` 透传、邮箱验证码、登录失败频控等基础能力已经开始落位，说明骨架不是空壳，而是进入了可收口的工程阶段。

### 二、当前核心风险判断

- 当前最大的风险已经不是“方案没想清楚”，而是“工程状态还没真正闭环”：Maven 编译不过会直接阻断后续开发。
- 认证链路虽然已经有最小骨架，但匿名入口划分和 Session 安全边界还不够严谨，一旦继续接更多接口，问题会快速放大。
- Flowable 依赖与改库边界目前仍带有明显的阶段性妥协，如果不尽早说明白，后续会在部署和排障时反复消耗精力。

### 三、阶段性结论

当前这版 `module-auth` 骨架可以视为“方向已成型、但尚未收口完成”。

更准确地说：

- 它已经具备继续演进的基础
- 但还不适合在当前状态下直接大规模铺开后续业务模块
- 更合适的顺序是先把本次审查中的构建链路与认证安全高优项收掉，再继续推进组织、流程、消息等模块

### 四、建议的下一步动作

1. 先修 Maven 编译链路与 JDK 使用口径
2. 再修员工端匿名接口白名单和登录后 Session 轮换
3. 再补认证异常统一处理
4. 最后收口 Flowable 边界与文档一致性

完成以上整理后，`module-auth` 才更适合作为后续业务开发的稳定基底。
