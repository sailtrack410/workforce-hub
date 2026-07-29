# 插件化工作台 - plugin-sdk 契约（v0.1）

本文档只描述当前仓库中已经存在的编译级接口。未落地能力集中列在“规划但未实现”一节，不提供伪代码接口。

---

## 一、接口方向

| 方向 | 当前接口 | 说明 |
|------|----------|------|
| 插件 → 宿主 | `HostService` 的具体子接口 | 插件通过 `PluginContext.getService(...)` 获取宿主代理；v0.1 仅实现 `EventService` |
| 宿主 → 插件 | `PluginActionHandler` | 继承 PF4J `ExtensionPoint`，用于 HTTP 网关和事件回调 |

当前源码不存在通用 `PluginExtension`。PF4J `ExtensionPoint` 只用于宿主发现插件扩展，不用于插件调用宿主。

---

## 二、InvocationContext（宿主绑定，不可伪造）

`InvocationContext` 是 `final` 只读类，字段如下：

| 字段 | 类型 | 说明 |
|------|------|------|
| `pluginId` | `String` | 当前插件 ID，非空 |
| `userId` | `Long` | 当前用户 ID；插件启动、定时任务或 system 事件可为 `null` |
| `callerType` | `USER / ADMIN / PLUGIN` | 调用者类型 |
| `requestId` | `String` | 请求或事件链路 ID，非空 |
| `source` | `HTTP_REQUEST / SCHEDULED_TASK / ASYNC_TASK / EVENT_CALLBACK / STARTUP` | 触发来源 |

构造器是 `com.workforcehub.plugin.sdk` 包级可见，SDK 不提供 Builder，也没有公开工厂方法。插件只能调用：

```java
String getPluginId();
Long getUserId();
CallerType getCallerType();
String getRequestId();
TriggerSource getSource();
```

宿主侧 `InvocationContextBinder` 提供以下绑定入口：

```java
InvocationContext bindFromRequest(HttpServletRequest request, String pluginId);
InvocationContext forPluginStartup(String pluginId);
InvocationContext forScheduledTask(String pluginId);
InvocationContext forEventCallback(String pluginId, DomainEvent event);
```

HTTP 绑定要求存在当前登录用户；事件绑定的 `triggeredBy` 只接受用户 ID 或 `system`。

---

## 三、PluginContext 与插件基类

当前 `PluginContext` 只有两个方法：

```java
public interface PluginContext {
    String getPluginId();
    <T extends HostService> T getService(Class<T> type);
}
```

当前没有 `getInvocationContext()`、`getConfig()` 或可长期持有的启动身份。每次业务调用使用 `PluginActionHandler.execute(...)` 或 `HostService` 首参收到的宿主上下文。

插件主类继承 `WorkforcePlugin`，构造器必须公开并接受 `(PluginWrapper, PluginContext)`：

```java
public final class HelloPlugin extends WorkforcePlugin {
    public HelloPlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper, context);
    }

    @Override
    protected void onStart() {
    }

    @Override
    protected void onStop() {
    }
}
```

宿主 `WorkforcePluginFactory` 使用插件 ClassLoader 加载 `plugin.properties` 指定的主类，并通过上述构造器注入 `DefaultPluginContext`；未继承 `WorkforcePlugin` 的主类直接拒绝。v0.1 不创建插件 Spring 子 Context。

`WorkforcePlugin.start()` / `stop()` 为不可覆盖的生命周期边界。插件只实现 `onStart()` / `onStop()`，并在取得线程、线程池、驱动、文件句柄等长期资源后立即注册关闭动作：

```java
registerCleanup(() -> {
    running = false;
    worker.interrupt();
    worker.join(2000);
});
```

关闭动作按注册逆序执行；启动中途失败和每次停止都会执行全部已注册动作。只有成功关闭的动作会移出队列，失败动作保留供后续 `stop` / `unload` / 宿主 `close` 重试；存在未完成动作时禁止再次启动或关闭 ClassLoader。业务生命周期异常作为主异常，关闭异常按发生顺序加入 suppressed；只有关闭异常时以首个关闭异常为主，同一异常实例不会自抑制。`InterruptedException` 只记录到全部动作执行完毕后再恢复线程中断位，非 JVM 致命 `Error` 包装为 `PluginRuntimeException`，因此 PF4J 稳定返回 `FAILED`，`PluginManagerService` 标记 `ERROR`。

资源约束：

1. 构造器和静态初始化器不得创建长期资源；资源在 `onStart()` 调用线程中同步创建并立即注册，禁止在 `onStart()` 返回后由后台任务继续创建或登记长期资源。
2. 关闭动作必须使用有界等待并可安全重复调用；宿主关闭时也会执行 stop/unload。
3. 插件自有线程必须停止、清理线程内 `ThreadLocal`，并释放指向插件 ClassLoader 的 TCCL。
4. 宿主线程中的 `ThreadLocal` 只能在同一次回调中用 `finally remove()`；停止线程无法代替其他线程清理。
5. JDBC `Driver` 必须由插件 ClassLoader 中的关闭动作注销准确实例；`DriverManager` 的调用方 ClassLoader 限制使宿主无法可靠枚举或代为注销插件驱动。

当前 JDBC 自动化仅使用插件 ClassLoader 动态加载的最小 `Driver` 注册表/CallerSensitive 探针，验证插件侧准确注销、宿主侧直接注销被拒绝和 `DriverAction` 回调；不覆盖 MySQL/PostgreSQL 驱动、连接池或厂商后台线程。

该协议只管理插件主动登记的资源，不能自动发现未注册的 native/global 资源；宿主尚未对插件 `onStop()` / cleanup 提供强制执行超时，内存增长监控也未落地。

当前 SDK 仍是 `0.1.0-SNAPSHOT`、尚未对外发布；final `start()` / `stop()` 是发布前契约替换，已有本地旧插件 JAR 必须重新编译。

---

## 四、HostService 与能力校验

`HostService` 是空的标记接口：

```java
public interface HostService {
}
```

所有具体子接口的业务方法必须：

1. 声明一个非空 `@RequiresCapability("能力码")`。
2. 把 `InvocationContext` 作为首个参数。

宿主启动时由 `HostServiceRegistry` 校验这些约束。同一子接口存在多个实现、只有 marker 实现、缺少注解或首参错误时均 fail closed。插件取得的是绑定插件 ID 的 JDK 动态代理，每次调用都会重新校验上下文插件 ID 和数据库能力授权。

当前唯一已实现的具体宿主服务是 `EventService`；`LeaveService`、`OrgService`、`AttendanceService` 等均不存在。

---

## 五、ServiceResult 与错误码

```java
public final class ServiceResult<T> {
    public static <T> ServiceResult<T> ok(T data);
    public static <T> ServiceResult<T> fail(int code, String message);
    public static <T> ServiceResult<T> fail(int code, String message, boolean retryable);

    public int getCode();
    public String getMessage();
    public T getData();
    public boolean isRetryable();
}
```

`ok(...)` 固定返回 `code=0`、`message=success`、`retryable=false`；失败结果的 `data` 为 `null`。

| 范围 | 当前常量 |
|------|----------|
| 1000-1999 参数 | `INVALID_PARAM`、`MISSING_PARAM` |
| 2000-2999 权限 | `PLUGIN_NOT_ALLOWED`、`USER_NOT_ALLOWED`、`DATA_SCOPE_EXCEEDED` |
| 3000-3999 业务 | `INSUFFICIENT_BALANCE`、`DUPLICATE_OPERATION`、`STATE_CONFLICT` |
| 4000-4999 系统 | `INTERNAL_ERROR`、`TIMEOUT`、`RATE_LIMITED` |
| 5000-5999 版本 | `SDK_VERSION_MISMATCH`、`HOST_VERSION_MISMATCH` |

---

## 六、PluginActionHandler

```java
public interface PluginActionHandler extends ExtensionPoint {
    ActionDescriptor getDescriptor();
    ServiceResult<Object> execute(InvocationContext ctx, Map<String, Object> params);
}
```

接口没有单独的 `getActionCode()`；动作编码来自 `getDescriptor().actionCode()`。

`ActionDescriptor` 当前字段：

```java
public record ActionDescriptor(
    String actionCode,
    String name,
    String description,
    Map<String, ParamSchema> inputSchema,
    Map<String, ParamSchema> outputSchema,
    String requiredCapability,
    Duration defaultTimeout,
    boolean idempotent
) {}
```

`actionCode` 不能为空。四参数简化构造器把输入/输出 Schema 设为空、超时设为 30 秒、`idempotent` 设为 `true`。

`ParamSchema` 支持 `string`、`number`、`boolean`、`object`、`array`，并提供对应静态工厂方法。通用网关当前会执行输入 Schema 校验，但尚未执行 `defaultTimeout`，也没有把 `idempotent` 转换为自动幂等保证。

---

## 七、EventService

```java
public interface EventService extends HostService {

    @RequiresCapability("EVENT_SUBSCRIBE")
    ServiceResult<Void> subscribe(
        InvocationContext ctx,
        String eventType,
        String actionCode,
        String idempotencyKey
    );

    @RequiresCapability("EVENT_SUBSCRIBE")
    ServiceResult<Void> unsubscribe(
        InvocationContext ctx,
        String eventType,
        String idempotencyKey
    );
}
```

当前宿主实现 `DefaultEventService`：

- 订阅和退订均要求非空幂等键，并写入 `sys_plugin_operation_idempotency`。
- 重复幂等键且请求摘要一致时返回成功；摘要不一致时返回 `DUPLICATE_OPERATION`。
- 订阅前要求目标 `PluginActionHandler` 已存在。
- 代理层和 `DefaultEventService` 实现层均校验总能力 `EVENT_SUBSCRIBE`；订阅还校验 `EVENT_SUBSCRIBE_{eventType}` 专项能力。
- 退订只要求总能力，便于仍有 API 权限的插件清理订阅。

允许订阅的六个事件类型：

```java
WORKFLOW_APPROVED
WORKFLOW_REJECTED
EMPLOYEE_HIRED
EMPLOYEE_LEFT
FILE_UPLOADED
ATTENDANCE_EXCEPTION
```

---

## 八、DomainEvent

```java
public record DomainEvent(
    String eventId,
    String eventType,
    long occurredAt,
    long version,
    String idempotencyKey,
    String triggeredBy,
    Map<String, Object> payload
) {}
```

构造约束：

- `eventId`、`eventType`、`idempotencyKey`、`triggeredBy` 必须非空白。
- `occurredAt > 0`，`version >= 1`。
- `null` payload 归一化为空 Map。
- payload 会递归快照并变为深度不可变。
- 只接受 `null`、String、Boolean、有限数字、String-key Map 和 List；拒绝 DTO、Date、Set、数组、NaN 与 Infinity。
- `DomainEvent` 本身允许未知事件类型；订阅服务只接受上列六种类型。

### v1 payload schema

以下字段是 v1 设计约定。`FILE_UPLOADED` 已接入真实业务发布点；其余五类尚未接入。当前适配器不在运行时逐字段校验 payload，字段正确性由宿主发布代码和测试保证。

#### WORKFLOW_APPROVED / WORKFLOW_REJECTED

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `requestId` | number | 是 | 审批申请 ID |
| `requestNo` | string | 是 | 审批申请编号 |
| `bizType` | string | 是 | 业务类型 |
| `applicantUserId` | number | 是 | 申请人用户 ID |
| `taskId` | string | 是 | 本次完成人工任务 ID |
| `beforeStatus` | string | 是 | 完成前状态 |
| `afterStatus` | string | 是 | 分别为 `APPROVED` / `REJECTED` |

#### EMPLOYEE_HIRED

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | number | 是 | 员工用户 ID |
| `employeeNo` | string | 是 | 工号 |
| `primaryDeptId` | number | 是 | 主部门 ID |
| `positionId` | number | 否 | 岗位 ID |
| `managerUserId` | number | 否 | 直属上级用户 ID |
| `employmentStatus` | string | 是 | 入职后的在职状态 |
| `hireDate` | string | 否 | `YYYY-MM-DD` |

#### EMPLOYEE_LEFT

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `userId` | number | 是 | 员工用户 ID |
| `employeeNo` | string | 是 | 工号 |
| `primaryDeptId` | number | 是 | 主部门 ID |
| `positionId` | number | 否 | 岗位 ID |
| `managerUserId` | number | 否 | 原直属上级用户 ID |
| `beforeEmploymentStatus` | string | 是 | 离职前状态 |
| `afterEmploymentStatus` | string | 是 | `OFFBOARDED` |

#### FILE_UPLOADED

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `attachmentRefId` | number | 是 | 附件引用 ID |
| `ownerType` | string | 是 | 文件业务归属类型 |
| `ownerId` | number | 否 | 文件业务归属 ID |
| `displayName` | string | 是 | 展示文件名 |
| `mimeType` | string | 是 | MIME 类型 |
| `fileSize` | number | 是 | 文件字节数 |
| `uploaderType` | string | 是 | `USER` 或 `ADMIN` |
| `uploaderId` | number | 是 | 上传者 ID |
| `scanStatus` | string | 是 | 上传确认时的安全扫描状态 |

宿主发布语义：

- 员工和管理员普通确认、秒传仅在首次创建 `sys_attachment_ref` 后注册提交后事件；顺序幂等重试和并发冲突的失败方不重复发布，事务回滚不发布
- `eventId` 与 `idempotencyKey` 均为 `file-uploaded-{attachmentRefId}`，`version=1`，`triggeredBy` 为当前上传操作者 ID，`occurredAt` 使用附件引用创建时间
- 四个顶层上传事务使用 `READ_COMMITTED`，避免 MySQL `REPEATABLE_READ` 下唯一键冲突事务因旧快照看不到并发赢家记录；唯一约束仍负责裁决唯一业务记录
- 分发为事务提交后同步、best-effort；插件分发异常只记录完整堆栈，不反向影响已提交文件业务。当前没有 outbox、持久化重试或崩溃补偿
- `publishEvent`、`@EventListener` 和订阅 handler 沿上传请求线程同步执行；慢或卡死插件会延迟或阻塞响应，`ActionDescriptor.defaultTimeout` 当前不构成事件 handler 超时约束
- 业务发布到 dispatcher、listener 转换与异常隔离已有自动化测试；真实 PF4J 插件订阅处理器端到端仍未完成

#### ATTENDANCE_EXCEPTION

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `exceptionId` | number | 是 | 考勤异常 ID |
| `dailyResultId` | number | 是 | 考勤日结果 ID |
| `userId` | number | 是 | 员工用户 ID |
| `workDate` | string | 是 | `YYYY-MM-DD` |
| `exceptionType` | string | 是 | 异常类型编码 |
| `exceptionDesc` | string | 否 | 异常说明 |
| `status` | string | 是 | 异常记录状态 |

---

## 九、通用调用网关

```text
POST /api/plugin-gateway/{pluginId}/actions/{actionCode}
```

请求体是可选 JSON 对象。调用身份完全来自当前登录会话和宿主 `InvocationContextBinder`；当前协议不读取 `X-Plugin-Id`、`X-Operation-Reason` 等客户端身份头。

网关首项是来源校验：优先精确校验 `Origin`，仅在 `Origin` 缺失时回退 `Referer`；`Origin` 不得带路径或查询，`Referer` 可带页面路径但不得带 query/fragment，来源必须命中白名单。拒绝返回 HTTP `403`、业务码 `4003`；匿名请求仍由 Spring Security 返回 `401`。该校验是插件动作 `POST` 的附加来源防线；全站 Session 写请求由 `SecurityConfig` 的 `CookieCsrfTokenRepository` 统一覆盖。

来源白名单配置为 `workforce.plugin.gateway.csrf.allowed-origins`，环境变量为 `WORKFORCE_PLUGIN_GATEWAY_CSRF_ALLOWED_ORIGINS`；`application` 默认仅本机来源，非本地部署必须显式设置。来源通过后再校验插件状态和生命周期入口，由宿主绑定 `InvocationContext` 并完成插件/动作/用户权限校验；权限通过后才发现 PF4J 动作、校验输入 Schema 并执行处理器。网关返回 `ServiceResult`，内部异常统一转换，不回传插件异常细节。`ActionDescriptor.defaultTimeout` 尚未执行，`idempotent` 仍不构成自动幂等保证。

---

## 十、规划但未实现

以下能力尚未出现在 `plugin-sdk` 源码中，不能作为插件编译依赖：

- `OrgService`、`LeaveService`、`AttendanceService`、`WorkflowService`
- `NotificationService`、`MessageService`、`FileService`
- `AgentService`、`KnowledgeService`
- `AuditService`、`ConfigService`、`SchedulerService`
- `PluginConfig`、`PageRequest`、`PageResult`
- `VersionService`、`PermissionService`
- 通用 `PluginExtension`

后续只在真实业务模块需要时新增具体 `HostService`，同时补齐 DTO、能力码、权限规则、幂等和测试，避免先创建不可执行的接口。

---

## 相关文档

- [后端架构.md](./后端架构.md) — 生命周期、签名、版本和 Flyway
- [插件规范.md](./插件规范.md) — `plugin.json`、`plugin.properties` 和交付包
- [前端架构.md](./前端架构.md) — 前端 Host SDK 与加载器
- [公共能力目录.md](./公共能力目录.md) — 业务宿主能力规划，未落地项不属于 v0.1 SDK
