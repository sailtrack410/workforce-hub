# 插件化工作台 - plugin-sdk 契约（v2）

本文档是插件 SDK 的编译级规范。所有接口必须可直接编译，不允许伪代码。

---

## 一、接口方向（强制）

| 方向 | 接口类型 | 说明 |
|------|----------|------|
| 插件 → 宿主 | HostService | 插件调用宿主能力 |
| 宿主 → 插件 | PluginExtension | 宿主调用插件逻辑 |
| HTTP → 插件 | PluginActionHandler | 通用网关调用 |

PF4J 的 `ExtensionPoint` **仅用于宿主调用插件**，不用于插件调用宿主。

---

## 二、InvocationContext（宿主绑定，不可伪造）

```java
package com.workforcehub.plugin.sdk;

/**
 * 调用上下文 - 由宿主创建，插件不可修改。
 * 插件通过 PluginContext 获取，不能自行构造。
 */
public final class InvocationContext {

    private final String pluginId;       // 调用插件ID（宿主填入）
    private final Long userId;           // 当前用户ID（定时任务时为null）
    private final CallerType callerType; // 调用者类型
    private final String requestId;      // 请求ID（链路追踪）
    private final TriggerSource source;  // 触发来源

    // 私有构造器 - 插件无法创建
    private InvocationContext(Builder builder) { ... }

    // getter 方法
    public String getPluginId() { ... }
    public Long getUserId() { ... }
    public CallerType getCallerType() { ... }
    public String getRequestId() { ... }
    public TriggerSource getSource() { ... }

    // 宿主内部使用
    public static Builder builder() { return new Builder(); }

    public enum CallerType {
        USER,       // 普通用户
        ADMIN,      // 管理员
        PLUGIN      // 插件自身（定时任务等）
    }

    public enum TriggerSource {
        HTTP_REQUEST,      // HTTP请求
        SCHEDULED_TASK,    // 定时任务
        ASYNC_TASK,        // 异步任务
        EVENT_CALLBACK,    // 事件回调
        STARTUP            // 启动阶段
    }

    public static final class Builder {
        // 宿主使用，插件无法访问
        Builder pluginId(String id) { ... }
        Builder userId(Long id) { ... }
        Builder callerType(CallerType type) { ... }
        Builder requestId(String id) { ... }
        Builder source(TriggerSource source) { ... }
        public InvocationContext build() { ... }
    }
}
```

**关键**：Builder 是包级私有，插件无法访问。

---

## 三、PluginContext（插件运行上下文）

```java
package com.workforcehub.plugin.sdk;

/**
 * 插件运行上下文 - 通过构造器注入。
 */
public interface PluginContext {

    // 获取宿主服务
    <T extends HostService> T getService(Class<T> type);

    // 获取当前调用上下文（由宿主绑定）
    InvocationContext getInvocationContext();

    // 获取插件配置（自动限定命名空间）
    PluginConfig getConfig();

    // 获取插件ID
    String getPluginId();
}
```

插件基类：

```java
package com.workforcehub.plugin.sdk;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public abstract class WorkforcePlugin extends Plugin {

    private final PluginContext context;

    // PF4J 通过自定义 PluginFactory 注入
    protected WorkforcePlugin(PluginWrapper wrapper, PluginContext context) {
        super(wrapper);
        this.context = context;
    }

    protected final PluginContext getContext() {
        return context;
    }
}
```

---

## 四、PluginFactory（宿主创建插件）

```java
package com.workforcehub.plugin;

import org.pf4j.PluginFactory;
import org.pf4j.PluginWrapper;
import com.workforcehub.plugin.sdk.WorkforcePlugin;
import com.workforcehub.plugin.sdk.PluginContext;

public class WorkforcePluginFactory implements PluginFactory {

    private final PluginContextFactory contextFactory;

    public WorkforcePluginFactory(PluginContextFactory contextFactory) {
        this.contextFactory = contextFactory;
    }

    @Override
    public Plugin create(PluginWrapper pluginWrapper) {
        Class<?> pluginClass = pluginWrapper.getPluginClass();
        PluginContext context = contextFactory.create(pluginWrapper);

        try {
            // 通过构造器注入 PluginContext
            return (Plugin) pluginClass
                .getConstructor(PluginWrapper.class, PluginContext.class)
                .newInstance(pluginWrapper, context);
        } catch (Exception e) {
            throw new PluginInstantiationException(
                "插件构造失败: " + pluginClass.getName(), e);
        }
    }
}
```

---

## 五、HostService（插件调用宿主）

```java
package com.workforcehub.plugin.sdk;

/**
 * 宿主服务标记接口。
 * 所有宿主服务必须继承此接口。
 */
public interface HostService {
    // 空接口，用于类型约束
}
```

### 统一返回值

```java
package com.workforcehub.plugin.sdk;

public final class ServiceResult<T> {

    private final int code;        // 0=成功，非0=失败
    private final String message;  // 错误信息
    private final T data;          // 返回数据
    private final boolean retryable; // 是否可重试

    // 静态工厂方法
    public static <T> ServiceResult<T> ok(T data) { ... }
    public static <T> ServiceResult<T> fail(int code, String message) { ... }
    public static <T> ServiceResult<T> fail(int code, String message, boolean retryable) { ... }

    // getter
    public int getCode() { ... }
    public String getMessage() { ... }
    public T getData() { ... }
    public boolean isRetryable() { ... }
}
```

### 错误码

```java
package com.workforcehub.plugin.sdk;

public final class ErrorCodes {
    public static final int SUCCESS = 0;

    // 参数错误 1000-1999
    public static final int INVALID_PARAM = 1000;
    public static final int MISSING_PARAM = 1001;

    // 权限错误 2000-2999
    public static final int PLUGIN_NOT_ALLOWED = 2000;
    public static final int USER_NOT_ALLOWED = 2001;
    public static final int DATA_SCOPE_EXCEEDED = 2002;

    // 业务错误 3000-3999
    public static final int INSUFFICIENT_BALANCE = 3000;
    public static final int DUPLICATE_OPERATION = 3001;
    public static final int STATE_CONFLICT = 3002;

    // 系统错误 4000-4999
    public static final int INTERNAL_ERROR = 4000;
    public static final int TIMEOUT = 4001;
    public static final int RATE_LIMITED = 4002;

    // 版本不兼容 5000-5999
    public static final int SDK_VERSION_MISMATCH = 5000;
    public static final int HOST_VERSION_MISMATCH = 5001;
}
```

---

## 六、组织与身份

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;
import java.util.List;

public interface OrgService extends HostService {

    ServiceResult<UserDTO> getUser(InvocationContext ctx, Long userId);

    ServiceResult<PageResult<UserDTO>> getSubordinates(
        InvocationContext ctx, Long userId, PageRequest page);

    ServiceResult<DepartmentDTO> getDepartment(InvocationContext ctx, Long deptId);

    ServiceResult<List<DepartmentDTO>> getDepartmentTree(InvocationContext ctx);
}
```

---

## 七、人事业务

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;
import java.util.List;

// 假期
public interface LeaveService extends HostService {

    ServiceResult<List<LeaveAccountDTO>> getAccounts(InvocationContext ctx, Long userId);

    ServiceResult<LeaveBalanceDTO> getBalance(
        InvocationContext ctx, Long userId, Long leaveTypeId);

    ServiceResult<LeaveDeductResult> deductBalance(
        InvocationContext ctx,
        Long userId,
        Long leaveTypeId,
        int minutes,
        String idempotencyKey,    // 幂等键（必填）
        String operationReason,   // 操作原因（必填）
        String relatedBizId       // 关联业务ID
    );
}

// 考勤
public interface AttendanceService extends HostService {

    ServiceResult<DailyResultDTO> getTodayResult(InvocationContext ctx, Long userId);

    ServiceResult<PageResult<DailyResultDTO>> getMonthlyResults(
        InvocationContext ctx, Long userId, java.time.YearMonth month, PageRequest page);

    ServiceResult<List<AttendanceExceptionDTO>> getExceptions(
        InvocationContext ctx, Long userId,
        java.time.LocalDate from, java.time.LocalDate to);
}

// 审批流程
public interface WorkflowService extends HostService {

    ServiceResult<WorkflowRequestDTO> submit(
        InvocationContext ctx,
        WorkflowSubmitRequest request,
        String idempotencyKey     // 幂等键（必填）
    );

    ServiceResult<WorkflowRequestDTO> getDetail(InvocationContext ctx, Long requestId);

    ServiceResult<Void> approve(
        InvocationContext ctx, Long requestId, String comment, String idempotencyKey);

    ServiceResult<Void> reject(
        InvocationContext ctx, Long requestId, String reason, String idempotencyKey);

    ServiceResult<Void> withdraw(
        InvocationContext ctx, Long requestId, String reason, String idempotencyKey);
}
```

---

## 八、消息与通知

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;

// 系统通知
public interface NotificationService extends HostService {

    ServiceResult<Void> sendNotification(
        InvocationContext ctx,
        Long userId,
        String title,
        String content,
        String idempotencyKey     // 幂等键（必填）
    );

    ServiceResult<Void> sendBatchNotification(
        InvocationContext ctx,
        java.util.List<Long> userIds,
        String title,
        String content,
        String idempotencyKey     // 幂等键（必填）
    );
}

// IM消息（发送身份由宿主根据ctx确定，插件不能传senderId）
public interface MessageService extends HostService {

    ServiceResult<Long> sendTextMessage(
        InvocationContext ctx,
        Long conversationId,
        String content,
        String idempotencyKey     // 幂等键（必填）
    );

    ServiceResult<Long> createConversation(
        InvocationContext ctx,
        Long userId,
        String title
    );
}
```

---

## 九、文件与存储

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;

public interface FileService extends HostService {

    // 申请上传票据
    ServiceResult<UploadTicket> requestUploadTicket(
        InvocationContext ctx,
        String bizType,
        String fileName,
        long fileSize
    );

    // 确认上传完成
    ServiceResult<Void> confirmUpload(
        InvocationContext ctx,
        String ticketId,
        String idempotencyKey     // 幂等键（必填）
    );

    // 获取下载票据（限时）
    ServiceResult<DownloadTicket> requestDownloadTicket(
        InvocationContext ctx,
        Long attachmentRefId
    );

    // 查询文件信息
    ServiceResult<FileDTO> getFile(InvocationContext ctx, Long attachmentRefId);
}
```

---

## 十、Agent与AI

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;
import java.time.Duration;

public interface AgentService extends HostService {

    // 同步调用（有超时）
    ServiceResult<AgentResponse> chat(
        InvocationContext ctx,
        Long agentId,
        String message,
        Duration timeout
    );

    // 异步调用（返回任务ID）
    ServiceResult<String> chatAsync(
        InvocationContext ctx,
        Long agentId,
        String message
    );

    // 查询异步结果
    ServiceResult<AgentResponse> getAsyncResult(
        InvocationContext ctx,
        String taskId
    );
}

public interface KnowledgeService extends HostService {

    ServiceResult<java.util.List<KnowledgeChunkDTO>> search(
        InvocationContext ctx,
        Long spaceId,
        String query,
        int topK
    );
}
```

---

## 十一、系统基础

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;
import java.util.Map;

// 审计（插件只能补充业务字段，宿主自动记录接口调用）
public interface AuditService extends HostService {

    ServiceResult<Void> appendBusinessDetail(
        InvocationContext ctx,
        String action,
        Map<String, Object> detail
    );
}

// 配置（自动限定当前插件命名空间）
public interface ConfigService extends HostService {

    ServiceResult<String> getConfig(InvocationContext ctx, String key);

    // 敏感配置返回脱敏值
    ServiceResult<String> getSecret(InvocationContext ctx, String key);
}

// 定时任务
public interface SchedulerService extends HostService {

    ServiceResult<Void> registerTask(
        InvocationContext ctx,
        ScheduledTaskDefinition definition
    );

    ServiceResult<Void> unregisterTask(InvocationContext ctx, String taskId);
}

// 定时任务定义
public record ScheduledTaskDefinition(
    String taskId,                    // 任务ID
    String actionCode,                // 动作编码（宿主调用插件的PluginActionHandler）
    String cron,                      // cron表达式
    java.time.ZoneId timezone,        // 时区
    MisfirePolicy misfirePolicy,      // 错过执行策略
    ConcurrencyPolicy concurrency,    // 并发策略
    java.time.Duration timeout,       // 超时
    int maxRetries,                   // 最大重试次数
    java.time.Duration retryDelay     // 重试间隔
) {
    public enum MisfirePolicy {
        FIRE_ONCE,    // 错过一次不补
        FIRE_NOW,     // 立即补一次
        SKIP          // 跳过
    }

    public enum ConcurrencyPolicy {
        SKIP,         // 上一次未完成则跳过
        RUN_AGAINST   // 并行执行
    }
}
```

---

## 十二、事件订阅

```java
package com.workforcehub.plugin.sdk.service;

import com.workforcehub.plugin.sdk.*;

public interface EventService extends HostService {

    ServiceResult<Void> subscribe(
        InvocationContext ctx,
        String eventType,
        String actionCode,        // 事件触发时调用的PluginActionHandler编码
        String idempotencyKey
    );

    ServiceResult<Void> unsubscribe(InvocationContext ctx, String eventType);
}

// 领域事件（宿主发布）
public record DomainEvent(
    String eventId,               // 事件ID（唯一）
    String eventType,             // 事件类型
    long occurredAt,              // 发生时间（毫秒时间戳）
    long version,                 // 事件版本
    String idempotencyKey,        // 幂等键
    String triggeredBy,           // 触发者（用户ID或"system"）
    java.util.Map<String, Object> payload  // 事件数据
) {}

// 事件类型常量
public final class EventTypes {
    public static final String WORKFLOW_APPROVED = "WORKFLOW_APPROVED";
    public static final String WORKFLOW_REJECTED = "WORKFLOW_REJECTED";
    public static final String EMPLOYEE_HIRED = "EMPLOYEE_HIRED";
    public static final String EMPLOYEE_LEFT = "EMPLOYEE_LEFT";
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    public static final String ATTENDANCE_EXCEPTION = "ATTENDANCE_EXCEPTION";
}
```

---

## 十三、PluginExtension（宿主调用插件）

```java
package com.workforcehub.plugin.sdk;

import org.pf4j.ExtensionPoint;
import java.util.Map;

/**
 * 插件动作处理器 - 宿主通过通用网关或事件回调调用。
 */
public interface PluginActionHandler extends ExtensionPoint {

    // 动作编码（唯一标识）
    String getActionCode();

    // 动作描述
    ActionDescriptor getDescriptor();

    // 执行动作
    ServiceResult<Object> execute(InvocationContext ctx, Map<String, Object> params);
}

// 动作描述（用于能力发现和文档生成）
public record ActionDescriptor(
    String actionCode,                    // 动作编码
    String name,                          // 动作名称
    String description,                   // 动作描述
    java.util.Map<String, ParamSchema> inputSchema,   // 输入参数Schema
    java.util.Map<String, ParamSchema> outputSchema,  // 输出参数Schema
    String requiredCapability,            // 所需能力码
    java.time.Duration defaultTimeout,    // 默认超时
    boolean idempotent                    // 是否幂等
) {}

// 参数Schema
public record ParamSchema(
    String type,          // string/number/boolean/object/array
    String description,   // 参数描述
    boolean required,     // 是否必填
    Object defaultValue   // 默认值
) {}
```

---

## 十四、分页

```java
package com.workforcehub.plugin.sdk;

public record PageRequest(
    int page,        // 页码（从1开始，最小1）
    int size,        // 每页数量（最大100，默认20）
    String sortBy,   // 排序字段（可选）
    boolean asc      // 升序（默认true）
) {
    public PageRequest {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
    }
}

public record PageResult<T>(
    java.util.List<T> items,
    int total,
    int page,
    int size
) {}
```

---

## 十五、PluginConfig

```java
package com.workforcehub.plugin.sdk;

/**
 * 插件配置 - 自动限定当前插件命名空间。
 */
public interface PluginConfig {

    // 获取配置值（自动加插件ID前缀）
    String get(String key);

    // 获取配置值（带默认值）
    String get(String key, String defaultValue);

    // 获取敏感配置（返回脱敏值）
    String getSecret(String key);

    // 获取整数配置
    int getInt(String key, int defaultValue);

    // 获取布尔配置
    boolean getBoolean(String key, boolean defaultValue);
}
```

---

## 十六、版本控制

```java
package com.workforcehub.plugin.sdk;

// plugin.json 中声明
// {
//   "sdkVersion": "1.0.0",
//   "minHostVersion": "1.0.0",
//   "maxHostVersion": "2.0.0",
//   "javaVersion": "17",
//   "frontendProtocol": "1.0"
// }

public interface VersionService {

    // 检查插件SDK版本是否兼容
    boolean isSdkCompatible(String pluginSdkVersion);

    // 检查宿主版本是否兼容
    boolean isHostCompatible(String minHostVersion, String maxHostVersion);

    // 获取宿主版本
    String getHostVersion();

    // 获取SDK版本
    String getSdkVersion();
}
```

---

## 十七、通用调用网关 API

### 请求

```
POST /api/plugin-gateway/{pluginId}/actions/{actionCode}
```

请求头：
```
X-Plugin-Id: leave-plugin
X-Idempotency-Key: uuid-xxx
X-Operation-Reason: 审批通过后扣减假期
```

请求体：
```json
{
  "userId": 123456,
  "leaveTypeId": 1,
  "minutes": 480
}
```

### 响应

```json
{
  "code": 0,
  "message": "success",
  "data": { ... },
  "retryable": false
}
```

### 错误响应

```json
{
  "code": 2001,
  "message": "用户无权操作此数据",
  "retryable": false
}
```

---

## 十八、前端 Host SDK

```typescript
// employee-web/src/core/host-sdk.ts

export interface HostSdk {
  Vue: typeof import('vue');
  VueRouter: typeof import('vue-router');
  Pinia: typeof import('pinia');

  // 插件注册
  register(id: string, plugin: PluginRegistration): void;

  // 获取已注册插件
  getPlugin(id: string): PluginRegistration | undefined;

  // 获取所有插件
  getAllPlugins(): Map<string, PluginRegistration>;
}

export interface PluginRegistration {
  id: string;
  name: string;
  component: () => Promise<any>; // 懒加载，不是动态import()
}

export function initHostSdk(): void {
  (window as any).__WORKFORCE_HOST__ = {
    Vue,
    VueRouter,
    Pinia,
    plugins: new Map(),
    register(id: string, plugin: PluginRegistration) {
      this.plugins.set(id, plugin);
    },
    getPlugin(id: string) {
      return this.plugins.get(id);
    },
    getAllPlugins() {
      return this.plugins;
    },
  };
}
```

---

## 十九、权限模型

```java
package com.workforcehub.plugin.sdk;

/**
 * 权限校验 - 双重检查：插件能力 + 用户权限
 */
public interface PermissionService {

    // 检查插件是否被授予某能力
    boolean hasPluginCapability(InvocationContext ctx, String capability);

    // 检查用户是否有某权限
    boolean hasUserPermission(InvocationContext ctx, String permission);

    // 检查用户是否在数据范围内
    boolean isInDataScope(InvocationContext ctx, Long targetUserId);

    // 完整校验（插件能力 + 用户权限 + 数据范围）
    boolean checkPermission(InvocationContext ctx, String capability, Long targetUserId);
}
```

---

## 相关文档

- [公共能力目录.md](./公共能力目录.md) — 所有 HostService 索引
- [后端架构.md](./后端架构.md) — PluginManager、签名校验、Flyway
- [插件规范.md](./插件规范.md) — 能力编码、plugin.json
- [开发计划.md](./开发计划.md) — PoC阻断项
