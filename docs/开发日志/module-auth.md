# 开发日志 - module-auth

记录 `module-auth` 相关开发改动，按时间倒序，最新在最上方。

---

## 2026-05-02 module-auth 认证联测与文档闭环

### 改动背景

虽然 `module-auth` 的登录、`me`、邮箱验证码、绑定邮箱、密码重置代码已经基本落位，但当前仍存在两个明显缺口：

- 还没有一套正式的自动化联测去证明认证主链路真的可回归
- 文档层只有系统级接口清单，没有 `module-auth` 专属 API 文档、测试用例和阶段性过程记录

这会带来一个很现实的问题：代码看起来“差不多有了”，但后续一旦改动登录频控、生命周期准入、验证码逻辑，很容易在没人察觉的情况下回归掉。

### 本次目标

- 为 `module-auth` 建立正式自动化联测
- 补齐认证接口 API 文档与测试用例文档
- 顺手收口认证链路里被联测打出来的真实问题
- 确保新的测试支撑方式不会破坏现有 `module-org` 基线

### 执行过程

#### 1. 把本地集成测试支撑升级为“每测例复位”

- `LocalIntegrationTestSupport`
  - 种子装载由“每个测试类一次”改为“每个测试用例一次”
  - 每次用例前先清空测试库业务表
  - 每次用例前再清理 Redis 中的：
    - `workforce:test:session*`
    - `auth:login:fail:*`
    - `auth:email:*`

**意义**：
- 避免不同认证用例之间因为会话、验证码、登录失败计数互相串状态
- 也让现有 `module-org` 测试从“靠唯一后缀规避串测”进一步升级为“天然可重复执行”

#### 2. 落地 `module-auth` 自动化联测

新增：

- `server/src/test/java/com/workforcehub/auth/ModuleAuthApiIntegrationTest.java`

本轮覆盖 `8` 个核心场景：

- 后台管理员登录 + `me`
- 员工登录 + `me` + `logout`
- 未登录访问 `me` 与 `email/bind`
- 验证码发送与重发频控
- 邮箱绑定成功回写账号邮箱
- 密码重置后新密码立即生效
- 连续输错密码触发登录频控锁定
- 员工生命周期不允许登录时返回明确提示

其中邮件发送器在测试中使用 Mock，不依赖真实 SMTP 服务。

**意义**：
- 现在 `module-auth` 不再只是“有接口”，而是已经进入可自动回归状态
- 后续如果有人改坏登录、验证码或密码链路，测试会直接报警

#### 3. 联测打出并修复生命周期登录提示语丢失问题

联测第一次执行时，发现一个真实问题：

- `EmployeeUserDetailsService` 里已经区分了 `PENDING_ONBOARD`、`OFFBOARDED` 等状态提示
- 但进入 Spring Security 后被包装成 `InternalAuthenticationServiceException`
- 最终前端只能拿到通用错误，生命周期语义没有真正透出

本轮修复：

- 新增 `EmploymentLoginBlockedException`
- 生命周期不允许登录时显式抛出该异常
- `LoginService` 中识别 `InternalAuthenticationServiceException` 的 `cause`
- 对应返回：
  - `403`
  - 明确文案，如 `员工已离职，无法登录`

**意义**：
- 让员工生命周期规则真正从领域逻辑落到接口契约
- 后续审批、组织变更、账号治理如果继续依赖生命周期状态，这条语义链路不会再被吞掉

#### 4. 补齐认证接口文档与系统级引用

新增：

- `docs/API与测试/module-auth/API文档_module-auth认证接口.md`
- `docs/API与测试/module-auth/API测试用例_module-auth认证接口.md`

同步更新：

- `docs/架构与设计/接口与事件清单.md`
- `docs/架构与设计/后端工程骨架说明.md`

**意义**：
- `module-auth` 现在也有了和 `module-org` 一致的文档形态
- 系统级接口清单、骨架说明、模块级 API 文档之间形成闭环

### 验证结果

执行：

- `cd server && mvn -DskipTests compile`
- `cd server && mvn -Dtest=ModuleAuthApiIntegrationTest test`
- `cd server && mvn -Dtest=ModuleOrgApiIntegrationTest,ModuleOrgWriteApiIntegrationTest test`

结果：

- 编译通过
- `module-auth` `8` 个联测场景通过
- `module-org` 读写联合 `10` 个场景继续通过

### 当前结果

- `module-auth` 已正式具备自动化联测基线
- 认证接口已有专属 API 文档与测试用例文档
- 生命周期不允许登录时已能返回明确业务提示
- 新的测试支撑方式没有破坏现有 `module-org` 基线

### 已知待补项

- 真实网易 SMTP 仍未做一次发信冒烟验证
- 当前后台管理员 `me` 还没有带角色权限摘要
- 当前还没有 `module-auth` 前端页面或前端联调产物

---

## 2026-05-01 真实 SMTP 兼容与环境变量样板补齐

### 改动背景

第 4 审指出两件事需要优先收口：一是当前 `application.yml` 的邮件配置只覆盖了本地 MailHog/Mailpit 场景，尚未补齐网易 `163 SMTP 465` 这种隐式 SSL 的真实发信边界；二是仓库里还没有正式的 `.env.example`，环境变量仍分散在配置文件注释、开发日志和临时沟通里，不利于后续复位环境，也放大了误提交真实凭证的风险。

### 本次目标

- 让邮件配置同时兼容“本地测试 SMTP”和“真实 163 SMTP”
- 增加仓库级 `.env.example`，把当前已落地变量统一收口
- 配套补齐忽略规则，避免后续把真实 `.env` 或构建产物误提交到仓库

### 执行过程

#### 1. 收口 SMTP 配置边界

先检查 `application.yml` 现状，确认当前只有 `MAIL_SMTP_AUTH` 与 `MAIL_SMTP_STARTTLS` 两个开关，没有覆盖 `465` 端口常用的 `mail.smtp.ssl.enable`。因此补充了：

- `spring.mail.default-encoding=UTF-8`
- SMTP 连接、读写超时配置
- `MAIL_SMTP_STARTTLS_REQUIRED`
- `MAIL_SMTP_SSL_ENABLE`

并在配置注释里明确区分：
- 本地测试：`localhost:1025`
- 网易 163：`smtp.163.com:465`

**意义**：
- 让同一份配置既能支撑本地假 SMTP 联调，也能支撑真实邮箱验证码发送
- 把“163 走 SSL、1025 走明文测试”的边界直接固化在配置层，而不是继续依赖口头约定

#### 2. 新增仓库级 `.env.example`

在仓库根目录新增 `.env.example`，按当前已经落地的模块把变量分为：

- Spring Profile
- MySQL 主业务库
- Redis
- Mail
- Flowable
- MinIO

其中邮件部分默认保留本地测试 SMTP 口径，并额外给出网易 163 的注释示例，但不写入任何真实账号、密码或授权码。

**意义**：
- 后续本机启动、IDE 运行配置、Docker Compose 都可以围绕同一份变量清单展开
- 变量命名不再散落在多个文档和配置片段里，降低回头复位环境的成本

#### 3. 补齐忽略规则

因为 `.env.example` 落地后，后续很容易自然出现真实 `.env` 文件，所以同步新增根目录 `.gitignore`，忽略：

- `.env`
- `.env.*`
- `server/target/`

同时保留 `.env.example` 可提交。

**意义**：
- 把“示例可提交、真实值不可提交”的边界直接落到 Git 层
- 顺手把 Maven 构建产物隔离掉，避免后续提交历史被编译输出污染

#### 4. 更新骨架文档可发现性

仅有 `.env.example` 文件还不够，后续回看工程说明时也需要一眼能找到它。因此在《后端工程骨架说明》中补充了三条说明：

- `.env.example` 的位置
- `.env.example` 不是自动加载文件
- 网易 163 SMTP 最小必填项

**意义**：
- 降低“文件虽然有，但过一段时间没人记得在哪、怎么用”的风险
- 让骨架文档与仓库实际文件形成闭环

#### 5. 构建验证

修改完成后，使用 `JDK 17` 环境执行 Maven 编译，确认本轮配置收口没有破坏工程可编译性。

**意义**：
- 避免只补文档、不验工程
- 确保这次改动是真正可继续往下开发的配置收口，而不是纸面修订

### 当前结果

- `application.yml` 已同时覆盖本地测试 SMTP 与真实 `163 SMTP 465` 场景
- 仓库根目录已新增 `.env.example`
- 仓库根目录已新增 `.gitignore`
- 《后端工程骨架说明》已补充环境变量样板入口说明

### 改动文件清单

- `.env.example` - 本机 / Docker Compose 环境变量样板
- `.gitignore` - 忽略真实 `.env` 与 `server/target/`
- `server/src/main/resources/application.yml` - 增加 SSL、超时、STARTTLS Required 配置
- `docs/架构与设计/后端工程骨架说明.md` - 补充 `.env.example` 入口与 163 SMTP 说明
- `docs/开发日志/module-auth.md` - 记录本次配置收口过程

### 已知待补项

- `.env.example` 当前只覆盖已经落地的变量，后续接入 PostgreSQL、模型服务等配置时需要持续补充
- 这次只做了配置层与编译层验证，还没有用真实网易邮箱做一次发信冒烟验证
- 本机 MySQL 客户端仍未进入默认 `PATH`，该问题与本轮改动无冲突，但仍然保留

---

## 2026-05-01 第 3 审问题修复

### 改动背景

第 3 审针对 `module-auth` 认证骨架与开发日志做了系统审查，提出 1 阻断 + 5 重大 + 4 建议共 10 个问题。本轮把 10 个问题全部在代码层面修掉，但其中 3-1（Flowable 改库边界）只是阶段性收口，终态仍需要等 `module-workflow` 阶段把 Flowable 初始化 SQL 拷入 Flyway 后才能彻底闭环。具体见下文"关于 3-1 阶段性边界"。

### 改动清单

#### 1-1 阻断 - Maven 构建与 Lombok 注解处理器

`pom.xml` 之前只继承 `spring-boot-starter-parent`，没有显式声明 `maven-compiler-plugin` 与 Lombok 注解处理器路径。在某些 Maven/IDE 组合下 Lombok 注解处理器不会自动激活，导致 `@Data`、`@Slf4j` 生成的方法编译期不可见。

**修复**：
- `properties` 中显式声明 `maven.compiler.source/target=17`、`project.build.sourceEncoding=UTF-8`、`lombok.version=1.18.32`
- `build/plugins` 中显式声明 `maven-compiler-plugin`，配置 `annotationProcessorPaths` 引用 Lombok

**JDK 基线（当前正式口径）**：
- 当前工程、本地 Maven、CI、Docker 镜像统一固定为 JDK 17
- `pom.xml` 中增加 `maven-enforcer-plugin`，在 `validate` 阶段强制校验 JDK 17，错误版本直接失败
- JDK 25：当前 Lombok 1.18.32 不支持，编译会失败 ❌
- JDK 21 虽曾实测可编译，但不再作为当前交付承诺范围，避免本地环境与 Docker 镜像出现双基线
- 后续若要升级到更高版本 JDK，需要先单独完成依赖兼容性验证（尤其是 Lombok）

#### 2-1 重大 - 员工端匿名白名单与接口语义冲突

之前 `/api/employee/auth/email/**` 被通配进白名单，但 `email/bind` 业务实现要求已登录；`/api/employee/auth/password/reset` 反而没在白名单，匿名找回密码调不通。

**修复**：在 `SecurityConfig` 中按接口粒度拆开：
- `permitAll`：`login`、`email/send-code`、`password/reset`
- 需要登录态：`email/bind`（依赖 `CurrentUserHolder`）

#### 2-2 重大 - Session Fixation 风险

之前 `LoginService.doLogin` 直接 `httpRequest.getSession(true)` 后写入 SecurityContext，没有先废弃旧 Session。攻击者可预设 JSESSIONID 在受害者登录后劫持会话。

**修复**：登录成功后先 `oldSession.invalidate()`，再 `getSession(true)` 创建新 Session 并写入 SecurityContext，保证登录前后 sessionId 必然轮换。日志中也增加了 `sessionId` 字段便于排查。

#### 2-3 重大 - 认证异常未覆盖锁定/禁用等状态

之前只 catch `BadCredentialsException | UsernameNotFoundException`，`LockedException`、`DisabledException`、`AccountExpiredException`、`CredentialsExpiredException` 都会逃逸到全局异常处理器返回 500。

**修复**：在 `LoginService` 显式增加四类异常的 catch，分别返回：
- `LockedException` → 423 "账号已锁定，请联系管理员"
- `DisabledException` → 403 "账号已被禁用，请联系管理员"
- `AccountExpiredException` → 403 "账号已过期，请联系管理员"
- `CredentialsExpiredException` → 403 "登录凭证已过期，请重置密码"

#### 2-4 建议 - 员工生命周期登录准入矩阵

之前只拒绝 `OFFBOARDED`，其他状态的准入语义没明确。

**修复**：在 `EmployeeUserDetailsService` 内显式定义 `LOGIN_ALLOWED_STATUSES = {PROBATION, ACTIVE, PENDING_OFFBOARD}`：
- `PENDING_ONBOARD`：拒绝（"尚未正式入职，无法登录"）
- `PROBATION`：允许
- `ACTIVE`：允许
- `PENDING_OFFBOARD`：允许（需要处理工作交接）
- `OFFBOARDED`：拒绝（"员工已离职，无法登录"）

#### 3-1 重大 - Flowable 改库边界

之前 `flowable.database-schema-update: true` 是写死的，与"所有结构变更走 Flyway"原则冲突。

**修复**：改为环境变量驱动 `${FLOWABLE_DB_SCHEMA_UPDATE:true}`，开发环境默认 true 便于联调，生产环境部署时显式置为 false。同时在配置中增加阶段性边界说明：进入 `module-workflow` 阶段时把 Flowable 初始化 SQL 拷入 Flyway 后正式收口。

#### 3-2 重大 - Flowable REST starter 依赖膨胀

之前同时引入 `flowable-spring-boot-starter-process` + `flowable-spring-boot-starter-rest`，rest starter 会传递回 cmmn/dmn/app/content 整套依赖。

**修复**：`pom.xml` 中移除 `flowable-spring-boot-starter-rest`。第一版不暴露 Flowable 原生 REST 接口，业务接口全部走 `module-workflow` 服务层。pom 中也加了注释说明这个收敛意图。

#### 4-1 建议 - 开发日志阶段判断过于乐观

之前写"基础设施骨架进入稳定状态"，但实际还有构建链路、认证安全、Flowable 边界未收口。

**修复**：改为"骨架方向基本成型，但构建链路、认证安全链路、Flowable 边界等仍待收口"。

#### 4-2 建议 - 验证码 Redis Key 口径偏移

文档写 `auth:email:code:{email}`，实际代码是 `auth:email:code:{purpose}:{email}`。

**修复**：开发日志中同步修正为 `{purpose}:{email}` 格式，并补充"按 purpose 维度隔离方便扩展更多用途"的设计原因。

#### 4-3 建议 - 表数量统计三处不一致

实际 grep 出 69 张 CREATE TABLE，但 `docs/架构与设计/后端工程骨架说明.md` 写 68，DDL 文件头注释还停留在 46。`wf_` 模块写的 11 实际 13（漏了 timeoff 和 overtime 明细）。

**修复**：
- `V1__init_schema.sql` 头部注释改为"覆盖全部 69 张业务表"，并附完整分域统计
- `docs/架构与设计/后端工程骨架说明.md` 总数 68→69、`wf_` 11→13、目录树注释 68→69

### 改动文件清单（8 个文件）

- `server/pom.xml` - 增加 maven-compiler-plugin 与 Lombok 处理器；移除 flowable-rest starter
- `server/src/main/resources/application.yml` - Flowable schema-update 环境变量化
- `server/src/main/resources/db/migration/V1__init_schema.sql` - 头部注释 46→69
- `server/src/main/java/com/workforcehub/common/config/SecurityConfig.java` - 白名单按接口粒度拆开
- `server/src/main/java/com/workforcehub/auth/service/LoginService.java` - Session 轮换 + 4 类认证异常 catch
- `server/src/main/java/com/workforcehub/auth/security/EmployeeUserDetailsService.java` - 登录准入白名单
- `docs/架构与设计/后端工程骨架说明.md` - 表数 68→69、wf 11→13
- `docs/开发日志/module-auth.md` - 阶段判断口径修正、Redis Key 口径修正、本次修复记录

### 关于第 3 审中"3-1 暂时保留 true"的进一步说明

3-1 这次的处理思路是：把 `database-schema-update` 从硬编码 true 改为环境变量驱动，默认值仍是 true 但生产可强制覆盖。这相比第 2 审的"加注释保留 true"更进了一步，但还不是终态。终态需要在 `module-workflow` 阶段把 Flowable 初始化 SQL 整入 Flyway，届时默认值改为 false。

---

## 2026-05-01 module-auth 启动

### 改动背景

第 2 审 6 个问题修完后，基础设施骨架方向基本成型，但构建链路、认证安全链路、Flowable 边界等仍待收口（见第 3 审）。按用户确认的推进路径，开始实现 P0 模块 `module-auth`，目标是完成员工端 + 后台端的完整登录闭环，作为后续所有业务模块的依赖底座。

### 本次目标

- 员工端账号密码登录（基于 `auth_user_account`）
- 后台端账号密码登录（基于 `admin_user`，独立体系）
- 登录态写入 Redis Session
- 当前用户接口、退出登录接口
- 邮箱验证码（绑定邮箱、找回密码）
- 登录失败频控

### 关键设计取舍

#### 1. 双 UserDetailsService 体系

员工和管理员账号体系完全独立（架构原则：管理员不进入业务组织树）。Spring Security 默认只支持单 `UserDetailsService`，需要自定义两套：

- `EmployeeUserDetailsService`：从 `auth_user_account` 加载
- `AdminUserDetailsService`：从 `admin_user` 加载

通过两个独立的 `AuthenticationProvider` 注册，登录接口分别调用对应 Provider，避免身份串扰。

#### 2. UserDetails 主体区分

为避免业务代码里写 `if (user instanceof EmployeeUser)`，用一个统一接口 `WfhUserDetails` 携带 `userType`（USER/ADMIN）和 `userId`，业务层只关心这两个字段，不直接持有 ORM 实体。

#### 3. Session 隔离

员工端和后台端共享 Redis Session，但通过 Session 中的 `userType` 字段区分。Security 在每次请求时校验当前请求路径与 Session 中的 userType 是否匹配（`/api/employee/**` 必须是 USER，`/api/admin/**` 必须是 ADMIN）。

#### 4. 邮箱验证码不落库

按架构方案要求，验证码只存 Redis，TTL 5 分钟。Key 格式（按 purpose 维度隔离，方便后续扩展更多用途如管理员验证码）：
- `auth:email:code:{purpose}:{email}` 存验证码（purpose 取值：BIND / RESET_PASSWORD）
- `auth:email:send:{purpose}:{email}` 防重发计数（60 秒内不允许重复发送）

#### 5. 登录失败频控

Key 格式：`auth:login:fail:{loginType}:{loginName}`，TTL 30 分钟。失败 5 次锁定 30 分钟。锁定期间即使密码正确也直接返回失败。

#### 6. 密码哈希

按技术方案使用 `BCryptPasswordEncoder`。密码盐由 BCrypt 内置，`auth_user_account.password_salt` 字段保留但不使用（兼容老数据迁移场景）。

### 文件清单（计划）

- `auth/domain/` - OrgUser、AuthUserAccount、AdminUser 三个 MyBatis-Plus 实体
- `auth/mapper/` - 三个 Mapper 接口 + XML
- `auth/security/` - WfhUserDetails、EmployeeUserDetailsService、AdminUserDetailsService、UserTypeFilter
- `auth/service/` - LoginService、EmailCodeService、AuthLockService
- `auth/controller/` - EmployeeAuthController、AdminAuthController
- `auth/dto/` - 各类请求响应 DTO
- `common/util/` - SessionUtils、CurrentUserHolder
- 修改 `SecurityConfig.java` 注册双 Provider 和 UserTypeFilter

预计 25 个文件左右。

### 实际产出（28 个文件）

#### 领域层（auth/domain）
- `OrgUser.java` - org_user 实体，含 employment_status、manager_user_id 等字段
- `AuthUserAccount.java` - auth_user_account 实体
- `AdminUser.java` - admin_user 实体

#### Mapper 层（auth/mapper + resources/mapper/auth）
- `OrgUserMapper.java` - 仅继承 BaseMapper
- `AuthUserAccountMapper.java` + xml - 增加 `selectByLoginName`、`selectByEmail`
- `AdminUserMapper.java` + xml - 增加 `selectByUsername`

#### 安全层（auth/security）
- `WfhUserDetails.java` - 统一用户主体接口（getUserType / getUserId / getDisplayName）
- `EmployeeUserDetails.java` - 员工实现，固定 ROLE_USER 角色
- `AdminUserDetails.java` - 管理员实现，固定 ROLE_ADMIN 角色
- `EmployeeUserDetailsService.java` - 员工端 UserDetailsService，从 auth_user_account 加载并关联 org_user，校验"已离职"状态
- `AdminUserDetailsService.java` - 后台 UserDetailsService
- `WfhAuthenticationProvider.java` - 自定义 DaoAuthenticationProvider 子类，携带 UserType 标记
- `UserTypePathFilter.java` - 路径与身份匹配校验，防止员工访问后台接口或管理员访问员工接口

#### Service 层（auth/service）
- `LoginService.java` - 登录编排：频控校验 → Provider 认证 → 写 SecurityContext / Session → 清频控 → 更新 last_login_at
- `EmailCodeService.java` - Redis 存验证码（5min TTL）+ 防重发（60s TTL）+ JavaMailSender 发送
- `AuthLockService.java` - Redis 实现登录失败计数，5 次锁定 30 分钟
- `AccountService.java` - 当前用户查询、绑定邮箱、找回密码

#### Controller 层（auth/controller）
- `EmployeeAuthController.java` - 6 个接口：login / logout / me / send-code / bind / reset-password
- `AdminAuthController.java` - 3 个接口：login / logout / me

#### DTO 层（auth/dto）
- `LoginRequest.java`、`LoginResponse.java`、`CurrentUserResponse.java`
- `SendCodeRequest.java`、`BindEmailRequest.java`、`ResetPasswordRequest.java`

#### 公共工具
- `common/enums/UserType.java` - 新增枚举
- `common/util/CurrentUserHolder.java` - 取当前登录用户的工具类
- 修改 `common/exception/GlobalExceptionHandler.java` - 增加 `MethodArgumentNotValidException` / `BindException` 处理
- 修改 `common/config/SecurityConfig.java` - 注册双 Provider、关闭 form login、注册 UserTypePathFilter

#### 配置
- 修改 `application.yml` - 增加 spring.mail SMTP 配置（默认指向本地 MailHog/Mailpit 1025 端口）

### 关键技术点回顾

1. **双 UserDetailsService 显式选择**：登录时由 LoginService 显式调用对应 Provider，不依赖 ProviderManager 的默认遍历，避免身份串扰
2. **路径与身份校验**：UserTypePathFilter 在每次请求时检查 `/api/employee/**` 必须 USER、`/api/admin/**` 必须 ADMIN
3. **Session 写入**：登录成功后手动 `session.setAttribute(SPRING_SECURITY_CONTEXT_KEY, context)`，配合 Spring Session + Redis 自动持久化
4. **频控统一 Key**：`auth:login:fail:{userType}:{loginName}`，员工和管理员独立计数
5. **验证码不落库**：`auth:email:code:{purpose}:{email}` Redis 存放，校验通过即删除防重放
6. **last_login_at 更新降级**：更新最后登录时间失败时只 warn 不阻断登录流程

### 已知待补项

- Flowable 改库边界（已确认推迟到 workflow 阶段处理）
- 单元测试与集成测试（暂未补，等业务接口稳定后批量补）
- 后台管理员的邮箱验证码相关接口（暂未实现，按需求第一版后台用账号密码即可）

---
