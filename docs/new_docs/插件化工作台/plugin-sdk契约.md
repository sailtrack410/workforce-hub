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
}
```

宿主 `WorkforcePluginFactory` 使用插件 ClassLoader 加载 `plugin.properties` 指定的主类，并通过上述构造器注入 `DefaultPluginContext`。v0.1 不创建插件 Spring 子 Context。

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

### 规划中的 v1 payload schema

以下字段是设计约定，当前只存在通用 `PluginDomainEventAdapter` 和分发器，尚未接入六类真实业务发布点，也没有在运行时逐字段校验这些 payload。

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

网关先校验插件状态和生命周期入口，再由宿主绑定 `InvocationContext` 并完成插件/动作/用户权限校验；权限通过后才发现 PF4J 动作、校验输入 Schema 并执行处理器。网关返回 `ServiceResult`，内部异常统一转换，不回传插件异常细节。

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
