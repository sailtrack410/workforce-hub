# 认证、组织、通讯录与系统运维业务链路

固定源码版本：`38d829167042dc26efd7af502ce623c976d6e224`。9张流程图均为schema v2、zh-CN、showcase，节点带真实行号，仓库链接local-only。仅新增本目录产物，没有修改业务代码或启动应用。

## 链路清单

| 链路 | 入口与实际范围 | 图 |
|---|---|---|
| 登录、失败锁定与会话退出 | admin-web 实接；employee-web 当前新登录页缺绑定分支；独立hub-web未处理二验 | [打开](auth-login.html) |
| 邮箱绑定与找回密码 | 后端能力齐；employee-web当前找回页错误发送LOGIN码；旧composable发送RESET_PASSWORD码 | [打开](auth-email.html) |
| 邀请注册与短信演示边界 | employee-web和独立hub-web注册均为占位；公开后端可直接调用；无管理端邀请码生成入口 | [打开](auth-register.html) |
| 组织维护、员工入转离与账号停用 | admin-web 部门/岗位/员工实接；employee-web查询组织；独立hub-web查询组织档案 | [打开](org-lifecycle.html) |
| 管理员角色配置与接口鉴权 | admin-web角色与权限页实接；员工端不具备后台RBAC管理入口 | [打开](auth-rbac.html) |
| 通讯录好友申请、接受、备注与移除 | employee-web通讯录实接；独立hub-web API仅见备注更新，联系人主要源于组织 | [打开](contacts-friends.html) |
| 数据字典维护与品牌配置发布 | admin-web字典和企业品牌维护实接；当前employee与独立hub登录页使用固定内容 | [打开](system-dict-brand.html) |
| 站内通知、邮件测试、告警处理与审计 | admin-web设置/审计实接；告警仅后端入口；独立hub工作台与顶栏已消费通知API | [打开](system-operations.html) |
| 个人档案、头像、偏好、密码与退出 | employee-web /settings实接；独立hub未发现同等个人设置页面，不混同旧壳与独立工程 | [打开](employee-personal.html) |

## 事实与缺口

- employee-web实际/login挂载`src/hub/views/AuthView.vue`；旧`useEmployeeAuthFlow`里的正确多步流程不能证明当前入口正确。
- 个人设置姓名/岗位/部门只读，手机号和头像可自助修改；偏好持久化、验证码改密、退出均有实际入口。
- 独立hub-web是另一套前端，其登录、注册状态与employee-web的新壳不能混同。
- 注册后端返回账号标识，未签发Session；短信为日志演示，邀请码没有管理生成入口。前端注册尚未接通。
- 组织变更有状态机、行锁、账号禁用和事件发布；注册直接插表，未沿用OrgManagementService的创建事件。需明确注册是否要求触发下游事件。
- 好友接受形成双向ACTIVE；没有独立拒绝接口，当前页面没有明确拒绝操作。独立hub好友API主要提供备注，联系人来自组织。
- 品牌配置可维护和公开读取，当前新登录页面展示固定内容，配置发布与展示未闭环。
- 独立hub顶栏与工作台已实际调用通知读取/全部已读；通知push入口未发现业务调用。不能据通知查询接口宣称审批已自动发站内通知。
- 告警聚合五类持久化失败状态；通知开关/渠道/冷却字段已保存，未发现真实定时通知执行器。处置记录只追加登记，不改变原失败任务。
- 审计为显式写入方调用AuditLogWriter；不把存在审计表等同于所有变更均审计。

## 已有问题引用

- **99-1**：AdminPermissionFilter未知后台路由默认放行；本次覆盖告警、审计接口但不另起新问题。
- **99-2**：停用、撤权后Session权限快照不立即失效；登录/组织/RBAC图引用同一问题。
- **99-3**：可变假期类型沿用原记录；**99-4/99-5**不在本分组新增包装。

## 问题与修复验收

### AUTH-01 · P1

触发：默认开启邮箱二验，首次使用未绑定邮箱的员工账号；或独立hub-web用户需要二验
影响：employee当前页未处理requiresEmailBinding，显示成功后跳转但无Session；独立hub把待二验响应保存为user，前后端认证状态不一致。
证据：当前路由指向HubAuthView；新页只检查requiresEmailCode。employee store同时识别两种待验证响应并不设置user；独立hub store直接赋值。后端两种pending返回发生于写Session之前。
位置：`employee-web/src/router/index.ts:10`；`employee-web/src/hub/views/AuthView.vue:370`；`employee-web/src/stores/session.ts:81`；`hub-web/src/stores/session.ts:65`；`server/src/main/java/com/workforcehub/auth/service/LoginService.java:187`
建议：当前登录页消费两种pending状态并接通绑定流程；独立hub共用相同认证状态判定。
验收：分别用已绑定、未绑定账号验证：pending时无已登录状态；完成BIND/LOGIN后me成功。
边界：静态确认，未执行真实业务调用

### AUTH-02 · P1

触发：从当前employee /login页面进入忘记密码，发送验证码后提交重置
影响：发出LOGIN码，重置要求RESET_PASSWORD；未另发正确用途码时始终失败。
证据：sendResetCode直接调用employeeApi.auth.sendLoginCode；LoginService按LOGIN发码；AccountService.resetPassword按RESET_PASSWORD核验。正确的sendPasswordResetCode存在但当前页未调用。
位置：`employee-web/src/hub/views/AuthView.vue:467`；`server/src/main/java/com/workforcehub/auth/service/LoginService.java:105`；`server/src/main/java/com/workforcehub/auth/service/AccountService.java:122`；`employee-web/src/stores/session.ts:147`
建议：当前找回页调用session.sendPasswordResetCode(email)。
验收：从实际/login路由发RESET_PASSWORD码；使用该码重置成功，LOGIN码不可重置。
边界：静态确认，未执行真实业务调用

### AUTH-03 · P2

触发：两个并发请求以同一邮箱、用途和正确验证码进入verifyCode
影响：GET/DELETE分离允许两请求先读到相同码并均返回true；不保证一次性核销。
证据：代码顺序为GET→比较→DELETE→true；错误码仅返回false，无尝试次数限制。此项为静态并发推导，未对真实Redis压测。
位置：`server/src/main/java/com/workforcehub/auth/service/EmailCodeService.java:91`
建议：用Redis Lua执行比较与删除，并为邮箱/用途设置有限校验尝试次数。
验收：真实Redis并发核验同一码仅一次成功；错误尝试达到阈值后失效，重发/用途隔离仍正确。
边界：静态并发推导，未运行Redis竞争验证

### AUTH-04 · P1

触发：两个不同手机号同时提交同一个有效邀请码，各持各自有效短信码
影响：两个事务可能先读到同一邀请码并各建账号；后者核销UPDATE影响0行也返回成功，单次邀请码可被复用。
证据：邀请码SELECT无行锁；markInviteUsed返回void且忽略条件UPDATE影响行数；service无核销成功判断。此项为静态事务交错推导，未运行MySQL并发复现。
位置：`server/src/main/java/com/workforcehub/auth/service/EmployeeRegistrationService.java:70`；`server/src/main/java/com/workforcehub/auth/repository/AuthRegistrationRepository.java:67`；`server/src/main/java/com/workforcehub/auth/repository/AuthRegistrationRepository.java:81`
建议：锁定邀请码行或原子领取并校验更新恰为1行，失败回滚员工和账号创建。
验收：两个手机号并发消费同一邀请码恰一成功，另一返回业务冲突且无孤立员工/账号。
边界：静态并发推导，未运行数据库竞争验证

### AUTH-05 · P2

触发：调用公开短信注册发码接口，或用户在当前注册页点击发送短信
影响：后端只演示日志发送且明文记录手机号与验证码；当前employee页面仅本地“已发送”，未调用后端。真实注册交付缺口明确。
证据：公开服务生成/持久化码后sendSms仅log.info；当前employee sendSmsCode仅设置sent与倒计时，独立hub仅toast。服务内未见频控。
位置：`server/src/main/java/com/workforcehub/auth/service/EmployeeRegistrationService.java:39`；`server/src/main/java/com/workforcehub/auth/service/EmployeeRegistrationService.java:89`；`employee-web/src/hub/views/AuthView.vue:410`；`hub-web/src/views/AuthView.vue:111`
建议：前端接实际发码/注册接口；生产接短信网关并移除验证码日志，添加现有Redis能力实现的发送频控。
验收：供应商接收成功才显示已发送；失败可重试；日志不含验证码；单位时间重复请求被限制；注册成功后明确转登录。
边界：静态确认，未执行真实业务调用

### AUTH-06 · P2

触发：管理员保存并测试EMAIL_DEFAULT，期望认证邮件使用该配置
影响：测试可成功但登录/绑定/找回邮件仍走注入JavaMailSender配置，数据库渠道保存不影响认证码发送。
证据：渠道测试新建JavaMailSenderImpl使用请求/数据库参数；EmailCodeService注入JavaMailSender且未查询NotificationChannelRepository。未发送真实SMTP邮件。
位置：`server/src/main/java/com/workforcehub/system/service/NotificationChannelService.java:63`；`server/src/main/java/com/workforcehub/system/service/NotificationChannelService.java:172`；`server/src/main/java/com/workforcehub/auth/service/EmailCodeService.java:36`
建议：将认证邮件明确接到已保存邮件渠道，或把配置页面标明只管理测试渠道并提供实际认证渠道状态。
验收：更改保存的渠道后，通过实际/login触发认证邮件使用相同配置；断开渠道时两条链均给一致错误。
边界：静态确认，未执行真实业务调用

### AUTH-07 · P2

触发：直接切换当前employee登录页到验证码登录，不先输入密码
影响：提交伪密码email-code:<code>，后端先执行密码认证；合法验证码不能构成独立登录。重复失败还可能消耗登录尝试。
证据：密码为空时前端合成字符串；后端先provider.authenticate，再进入邮箱二验；这是二因素登录而非验证码独立登录。
位置：`employee-web/src/hub/views/AuthView.vue:356`；`employee-web/src/hub/views/AuthView.vue:365`；`server/src/main/java/com/workforcehub/auth/service/LoginService.java:120`；`server/src/main/java/com/workforcehub/auth/service/LoginService.java:160`
建议：将验证码入口限定为密码认证后的二验步骤；保留密码后再提交验证码。
验收：新会话无法只凭验证码登录；密码通过后进入二验并成功，不产生伪密码认证失败。switchLoginMode只改视图与状态、不清密码，因此保留原密码的密码后二验路径不在此缺陷范围。
边界：静态确认，未执行真实业务调用

### AUTH-08 · P2

触发：在实例A修改字典，实例B已缓存该类型标签
影响：仅本机缓存失效，其他实例继续展示旧标签，直到缓存重建/进程重启。
证据：ConcurrentHashMap按类型缓存，无TTL；写操作labelCache.remove仅当前JVM。未运行双JVM验证。
位置：`server/src/main/java/com/workforcehub/system/service/AdminDictService.java:35`；`server/src/main/java/com/workforcehub/system/service/AdminDictService.java:107`；`server/src/main/java/com/workforcehub/system/service/AdminDictService.java:137`
建议：当前规模优先去除该轻量标签缓存或加短TTL；按实际部署验收跨实例一致性。
验收：A修改后B在约定窗口读取新标签；禁用/删除同样刷新。
边界：静态多实例推导，待验证

### AUTH-09 · P2

触发：个人设置将已有手机号清空后保存
影响：页面显示手机号已更新，但请求传null，后端跳过写入，实际手机号保持原值。
证据：前端mobile || null；后端仅request.getMobile()!=null时trim并更新。后端空串可清空，前端恰将其转换成null。仅静态追踪，未真实保存。
位置：`employee-web/src/views/sections/SettingsSection.vue:296`；`server/src/main/java/com/workforcehub/org/service/OrgDirectoryService.java:205`；`server/src/main/java/com/workforcehub/org/dto/UpdateEmployeeProfileRequest.java:14`
建议：将清空语义统一：前端发送空字符串，或后端明确区分字段缺失与null清空。
验收：已有手机号清空保存后重新加载为null，非空手机号仍可正常更新。
边界：静态确认，未执行真实业务调用

## 优化记录

- 好友增加发出申请状态与拒绝入口；补同时互加、接受竞争的专项事务验收。
- 组织/角色管理的重要变更补显式审计记录；优先复用现有AuditLogWriter，不增加通用切面。
- 告警展示目前按count>0列出且携带enabled状态，是否隐藏禁用规则需产品约定；不能把配置字段当已实现投递。
- 保留唯一验证码用途与入口一致，复用已有session.sendPasswordResetCode，避免两套认证状态逻辑继续漂移。

## 验证结果与边界

```text
current-reset-uses-login-purpose-entry: PASS
backend-reset-requires-reset-password: PASS
current-page-omits-binding-pending: PASS
email-verification-is-get-delete: PASS
invite-consumption-ignores-row-count: PASS
register-is-placeholder: PASS
Static contracts: 6 passed, 0 failed
Workflow finalize: 9 passed, 0 failed
```

9张图的validate、deliver、strict check、browser-check均通过；浏览器验证针对生成HTML，不是业务应用实测，未做人工视觉审阅。未运行项目全量测试、真实SMTP/短信投递、数据库/Redis并发、双JVM测试；问题中的竞争时序仅为静态推导。

初次邮箱图出现同单元节点重叠，按诊断仅移动绑定入口到空列后通过；组织图曾因同目录并发finalize产生provenance-locked，候选不变串行重跑通过。浏览器证据对应最终通过的候选。

源码工作区原有文档改动：`docs/审查/说明.md`及未跟踪第99审报告，本分组未修改。
