# 文件、插件与工作台链路记录

源码基准 `38d829167042dc26efd7af502ce623c976d6e224`。以下是静态源码追踪与HTML产物检查，不是业务运行验收。

## 链路清单

### F01 · 文件上传：票据、秒传与确认

[流程图](F01.html)

- 范围：个人文件、头像、审批及群附件、后台与知识文档共用上传基础；业务归属分别校验。
- 入口：employee-web 文件页可用；管理端知识文档可上传；LOCAL 返回本地路径而非浏览器 PUT 地址，使用前须确认部署存储。
- 记录：FILE-Q：配额读取与用量累加分离；并发确认/复制可同时通过余量检查，属于历史并发治理项的具体复核。

### F02 · 文件下载：鉴权、单次票据与二进制返回

[流程图](F02.html)

- 范围：个人文件/头像走通用票据；审批、会话附件必须走各自业务授权。
- 入口：employee-web 文件页支持预览和下载；管理端通用下载仅支持限定归属。
- 记录：下载票据在返回资源时已消费，不能承诺客户端下载完成；网络中断需重新申请票据。

### F03 · 个人云盘：目录、移动与复制

[流程图](F03.html)

- 范围：目录浏览、分类查询、创建文件夹、文件移动、逻辑复制及配额。
- 入口：employee-web /files 接入；收藏和最近使用在客户端保存。独立 hub-web 无云盘页面路由。
- 记录：FILE-Q：并发复制共用非原子配额检查，需与上传确认统一占额，不能只保护累加 SQL。

### F04 · 文件回收站：删除、恢复与彻底清除

[流程图](F04.html)

- 范围：个人文件和后台文件分别限定归属；删除只隐藏，彻底清除才释放逻辑用量。
- 入口：employee-web 文件页接入删除恢复清除；管理端当前页面主要为目录和清理任务，后端治理能力更多。
- 记录：删除、彻底清除、物理删除是三种状态；流程和容量展示应保持一致。

### F05 · 文件治理：预演、数据库清理与失败重试

[流程图](F05.html)

- 范围：过期上传/下载票据、到期回收引用、无引用对象、失败或超时物理删除。
- 入口：admin-web 文件清理页可预演、确认正式执行和失败物理对象重试。
- 记录：优化：预演与正式执行之间会变化，预演数量应作为估算；现状正式执行会重新扫描。

### P01 · 插件安装与生命周期：校验、启停、卸载

[流程图](P01.html)

- 范围：签名快照、版本兼容、Flyway迁移、PF4J、依赖、排空与错误恢复。
- 入口：admin-web 插件管理页可上传签名包、启用停用卸载；不支持上传覆盖既有ID。
- 记录：优化：安装迁移与安装记录不是一个可回滚外部事务；失败时应说明已执行迁移与人工恢复步骤。

### P02 · 插件动作网关：权限、幂等与结果重放

[流程图](P02.html)

- 范围：来源检查、上下文、三重授权、生命周期守卫、参数校验、请求占用及审计。
- 入口：Host SDK 调用后端插件网关；HTTP仅开放声明幂等的动作，必须携带Idempotency-Key。
- 记录：PLUGIN-U：异常保留IN_PROGRESS避免重复副作用，但缺少显式人工核对/恢复入口，长期同键请求只能409。此项属于恢复能力建议，不能用自动过期后重跑代替。

### P03 · 插件配置：版本校验、敏感值与软删除

[流程图](P03.html)

- 范围：插件状态锁、expectedVersion、类型校验、敏感值保护、恢复及删除幂等。
- 入口：admin-web 插件配置弹窗调用治理API；敏感值只展示设置状态，不读取实际配置数据。
- 记录：优化：requiresRestart 的界面提示与操作回执应明确配置何时生效；本图不把标记等同自动重启。

### P04 · 插件事件与前端运行时：分发、加载及隔离边界

[流程图](P04.html)

- 范围：提交后领域事件→订阅→有界线程池→回调；前端另支加载清单与SRI脚本。
- 入口：employee-web 旧壳加载插件清单，当前新工作台未挂载旧 PluginWorkbenchHost；独立 hub-web 无插件入口。
- 记录：PLUGIN-E：回调队满直接丢弃，失败只日志；重要业务事件需要持久投递账本与可审计补偿。

### W01 · 工作台聚合：各端数据读取与业务跳转

[流程图](W01.html)

- 范围：三个前端分别聚合已有业务API，不是一个共用工作台；失败与空数据的表现需要区分。
- 入口：employee-web /hub/workbench；独立hub-web /workbench；admin-web /dashboard。
- 记录：WORKBENCH-D：employee“我的申请”用workflow.todos映射，混淆本人发起和待本人处理；应改用本人requests。打卡硬编码时间沿用AT-01。

## 问题与优化

### WORKBENCH-D · P2 · “我的申请”混用待办审批数据

- 状态：静态源码确认；未动态业务复现
- 触发：员工进入当前employee-web工作台查看“我的申请”。
- 影响：workflow.todos返回待本人处理项，页面将其当成本人申请展示，归属和进度语义错误。
- 证据：myRequests直接从todos.value映射；没有查询本人requests。独立hub-web有单独requests查询，不能证明employee新页正确。
- 修复：查询本人requests，待办继续单独展示；两类卡片使用各自状态与时间。
- 验收：A发起给B审批：A的我的申请可见该单，B的待办可见该单，B的我的申请不得把该单当作本人发起。
- 位置：`employee-web/src/hub/views/WorkbenchView.vue:142`；`employee-web/src/hub/views/WorkbenchView.vue:181`

### PLUGIN-F · P2 · 新工作台缺少插件宿主入口

- 状态：静态源码确认；未动态业务复现
- 触发：员工使用当前/hub/workbench，希望进入已授权的UI插件。
- 影响：后端可见清单、loader和旧PluginWorkbenchHost均存在，但当前工作台路由未挂载旧WorkbenchSection，无法据这些代码认定用户已可打开插件。
- 证据：当前路由指向hub/WorkbenchView；旧workbench路由重定向。旧壳某些路由仍会加载插件清单，问题限定为当前新工作台的渲染入口，不宣称插件后端不可用。
- 修复：在当前新工作台提供授权插件入口并挂载已有宿主组件；以当前入口重新验收SRI、卸载和失败重试。
- 验收：从默认登录路径进入工作台，能打开授权UI插件；不可见插件无入口且网关仍拒绝，加载失败可恢复。
- 位置：`employee-web/src/router/index.ts:27`；`employee-web/src/router/index.ts:78`；`employee-web/src/views/sections/WorkbenchSection.vue:943`；`employee-web/src/stores/plugins.ts:17`

### 20/3-3 · P1 · 并发配额检查与实际占额分离

- 状态：历史问题具体复核；静态事务交错推导
- 触发：两个不同上传确认或复制事务在同一用户/全局池余量仅容一份时并发执行。
- 影响：两次可先读到相同已用量并通过校验，之后分别累加，最终超出容量限制。此项延续第20审3-3并发配额治理项，不新增重复编号。
- 证据：usedBytes是普通SELECT；ensurePersonalQuota/GlobalQuota先比较；increaseUsage后续单独累加且SQL没有quota条件。V4规范化scope_key已解决NULL唯一性，不能重报那个已修模型问题。未跑真实数据库并发。
- 修复：在同事务内按固定顺序锁定作用域用量并校验占额，或条件更新原子占额；覆盖首次创建账本行及多作用域冲突。
- 验收：余量只容一份时并发两次确认/复制仅一成功，另一返回容量错误，拒绝事务无多余引用；重试与回滚不重复占额。
- 位置：`server/src/main/java/com/workforcehub/file/service/FileQuotaPolicyService.java:197`；`server/src/main/java/com/workforcehub/file/service/StorageUsageLedgerService.java:19`；`server/src/main/java/com/workforcehub/file/service/StorageUsageLedgerService.java:45`；`server/src/main/java/com/workforcehub/file/service/FileQueryService.java:225`；`server/src/main/java/com/workforcehub/file/service/FileUploadConfirmService.java:99`

### PLUGIN-E · P2 · 重要插件事件增加可恢复投递账本

- 状态：优化建议；未动态注入队列故障
- 触发：业务事务已提交后进程退出、回调队列满或处理器返回失败。
- 影响：当前事件提交后进程内发布，有界队列拒绝明确丢弃；回调失败/可重试只日志和尽力审计，没有持久投递重试。当前实现是尽力交付，是否升级取决于插件业务承诺。
- 证据：代码明确日志“队列已满，已丢弃”，不把它伪装为现有可靠消息能力；本项作为能力优化，不等同已发生丢单事故。
- 修复：对影响业务结果的事件用持久投递记录与消费幂等；查询型/提示型事件可继续尽力交付但明确契约。
- 验收：队列拒绝与重启后待投递记录仍可查询并重试；消费重复不产生重复业务结果。
- 位置：`server/src/main/java/com/workforcehub/common/event/DomainEventPublisher.java:27`；`server/src/main/java/com/workforcehub/plugin/runtime/PluginEventDispatcher.java:93`；`server/src/main/java/com/workforcehub/plugin/runtime/PluginEventDispatcher.java:185`

### PLUGIN-U · P2 · 插件动作未知结果增加人工核对恢复

- 状态：优化建议；保留现有防重安全语义
- 触发：处理器已经占用幂等键后异常、返回空值或响应无法序列化。
- 影响：保留IN_PROGRESS防止重复副作用是合理保护；但同键后续持续409，当前清理只删过期COMPLETED，缺少显式人工核对与完成/释放入口。
- 证据：已有测试覆盖异常保持占用，故不建议简单自动超时删除；需让真实未知结果可见、可审计恢复。
- 修复：增加占用状态查询和经授权的结果核对、完成/释放流程；沿用原幂等范围与审计，不盲目重跑。
- 验收：处理器完成外部副作用后模拟响应失败，经人工核对记录结果，再次同键可重放且不再执行业务。
- 位置：`server/src/main/java/com/workforcehub/plugin/runtime/PluginActionIdempotencyService.java:117`；`server/src/main/java/com/workforcehub/plugin/runtime/PluginActionIdempotencyService.java:211`；`server/src/main/java/com/workforcehub/plugin/runtime/PluginActionIdempotencyCleanupJob.java:29`

## 验证边界

10张图完成finalize四门自动检查；F01额外捕获并人工查看1440×900浅色截图。未启动应用，未运行Maven/npm业务测试、真实数据库并发、SMTP、短信或模型请求。

生成器初次同目录并发投递触发Archify目录锁，已串行重跑；F01局部路线/标签问题按诊断修正。错误与最终命令回执保留在本目录，不将失败回执算作交付通过。
