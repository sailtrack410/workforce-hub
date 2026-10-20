# employee-web 后端联调基线

## 一、目标

这份文档只解决一件事：让 `employee-web` 在本地联调时登录后不是空页面，并且找回密码、IM、审批、Agent 这些演示链路有清晰前置条件。

适用时间点：

- 前端开始接 `auth / me / workflow / im / agent / leave`
- 演示前需要快速恢复一套可见数据
- 后端接口没问题，但本地库还是空的

---

## 二、当前账号边界

### 1. 后台管理员

| 登录名 | 密码 | 作用 |
| --- | --- | --- |
| `admin.demo` | `Admin@123456` | 后台治理、初始化假种、流程模板、Agent |

### 2. 员工 / 领导演示账号

| 登录名 | 密码 | userId | 角色 |
| --- | --- | --- | --- |
| `demo.leader` | `Employee@123456` | `22001` | 领导 |
| `demo.employee1` | `Employee@123456` | `22002` | 员工一 |
| `demo.employee2` | `Employee@123456` | `22003` | 员工二 |

说明：

- 两个员工的直属上级都是 `demo.leader`
- 员工端不支持前台自注册
- “开通账号”必须走后台接口：`POST /api/admin/users`、`PUT /api/admin/users/{userId}/account`

---

## 三、当前真实接口状态

前端当前直接可接：

- 认证：`/api/employee/auth/*`
- 个人组织信息：`/api/employee/me/*`
- 审批待办与申请：`/api/employee/workflow/*`
- IM 会话：`/api/employee/im/*`
- Agent 列表与任务：`/api/employee/agents/*`
- 假期余额：`/api/employee/leave/accounts`

已确认真实可用：

- 登录、`me`、`profile`、`manager`
- IM 私聊创建、发文本、拉会话列表、拉消息列表
- 后台创建假种、调整余额

当前最常见的“看起来像后端没做，其实是环境或数据没准备”问题：

1. 会话 / 待办 / Agent 列表为空
2. 找回密码发送验证码失败
3. 流程模板没初始化，提交请假/补卡时返回“未找到已发布的流程模板”

---

## 四、本地前置

### 1. 后端

默认地址：

```text
http://localhost:8080
```

健康检查：

```bash
curl http://localhost:8080/actuator/health
```

期望返回：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

### 2. SMTP

找回密码、登录邮箱二验和绑定邮箱都依赖 SMTP。

后端从 `server/` 启动时会自动读取仓库根目录 `.env`。如果 `.env` 配置了真实 SMTP，例如 163 邮箱，验证码会走真实邮箱；没有配置时使用本地调试默认值：

```bash
MAIL_HOST=127.0.0.1
MAIL_PORT=1025
```

本地调试 SMTP 启动方式：

```bash
python3 server/scripts/dev/start_debug_smtp.py
```

如果走本地调试 SMTP 且服务没启动：

- `POST /api/employee/auth/email/send-code` 会返回 `code=500`
- 提示文案通常是 `邮件发送失败，请稍后重试`

### 3. Agent 本地模型桩

如果要演示 IM 内 Agent 回复，但还没接真实模型，可以先启动本地兼容桩：

```bash
python3 server/scripts/dev/start_agent_stub_server.py
```

默认地址：

```text
http://127.0.0.1:18080/v1/chat/completions
```

---

## 五、最小演示数据准备

推荐直接跑一键引导脚本，不再按下面各小节手工拼 curl：

```bash
python3 server/scripts/dev/bootstrap_demo_data.py
```

脚本会幂等确保：

- `demo.leader / demo.employee1 / demo.employee2` 三个员工端账号可登录
- 三个账号的直属上下级关系正确
- `ANNUAL_DEMO` 假种和演示余额存在
- `LEAVE` 与 `CLOCK_CORRECTION` 流程模板已发布
- `demo.employee1` 与 `demo.leader` 至少有一个私聊会话和消息
- `demo.employee1` 有一条请假申请，`demo.leader` 有对应待办
- `demo.employee1` 可以看到演示 Agent，并且 IM 内 Agent 会话已有首轮回复

脚本默认连接：

```text
http://127.0.0.1:8080
```

可用环境变量覆盖：

```bash
WFH_BASE_URL=http://127.0.0.1:8080 \
WFH_ADMIN_LOGIN=admin.demo \
WFH_ADMIN_PASSWORD=Admin@123456 \
WFH_EMPLOYEE_PASSWORD=Employee@123456 \
WFH_AGENT_MODEL_BASE_URL=http://127.0.0.1:18080/v1 \
WFH_AGENT_MODEL_API_KEY=demo-key \
WFH_AGENT_MODEL_NAME=stub-chat \
python3 server/scripts/dev/bootstrap_demo_data.py
```

如果要演示 Agent 回复，后端启动时也要打开真实模型客户端，并指向本地兼容桩：

```bash
cd server
DB_USER=root \
DB_PASSWORD=$$your-db-password \
AGENT_MODEL_ENABLED=true \
AGENT_MODEL_BASE_URL=http://127.0.0.1:18080/v1 \
AGENT_MODEL_API_KEY=demo-key \
AGENT_MODEL_NAME=stub-chat \
mvn spring-boot:run
```

### 1. 组织与账号

先保证组织和基础账号存在。

如果需要重建基础种子，执行：

```text
server/scripts/sql/seed_module_org_smoke_data.sql
```

说明：

- 这份脚本覆盖的是 `admin.demo / lisi.manager / zhangsan.employee / wangwu.employee`
- `demo.leader / demo.employee1 / demo.employee2` 是当前本地新增演示账号，不在这份脚本内

### 2. 假期余额

后台管理员先创建一条演示年假：

```bash
curl -X POST http://localhost:8080/api/admin/leave/types \
  -H 'Content-Type: application/json' \
  -H 'Cookie: SESSION=<admin-session>' \
  -d '{
    "leaveCode": "ANNUAL_DEMO",
    "leaveName": "演示年假",
    "balanceMode": "DEDUCT",
    "status": "ACTIVE"
  }'
```

然后分别给三位演示账号初始化余额：

```bash
curl -X POST http://localhost:8080/api/admin/leave/accounts/adjustments \
  -H 'Content-Type: application/json' \
  -H 'Cookie: SESSION=<admin-session>' \
  -d '{
    "userId": 22002,
    "leaveTypeId": "<上一步返回的 leaveTypeId>",
    "changeMinutes": 960,
    "reason": "初始化演示年假",
    "sourceTxnKey": "demo-annual-22002"
  }'
```

建议初始化：

| userId | 建议分钟数 | 说明 |
| --- | --- | --- |
| `22001` | `1440` | 领导 |
| `22002` | `960` | 员工一 |
| `22003` | `480` | 员工二 |

### 3. IM 私聊

用 `demo.employee1` 发起和领导的私聊：

```bash
curl -X POST http://localhost:8080/api/employee/im/private-conversations \
  -H 'Content-Type: application/json' \
  -H 'Cookie: SESSION=<employee1-session>' \
  -d '{"targetUserId":22001}'
```

然后双方各发一条消息，列表就不是空的：

```bash
curl -X POST http://localhost:8080/api/employee/im/conversations/<conversationId>/messages \
  -H 'Content-Type: application/json' \
  -H 'Cookie: SESSION=<employee1-session>' \
  -d '{
    "messageType": "TEXT",
    "text": "领导您好，今天的演示链路我这边已经准备好了。",
    "clientMessageId": "demo-emp1-msg-1"
  }'
```

```bash
curl -X POST http://localhost:8080/api/employee/im/conversations/<conversationId>/messages \
  -H 'Content-Type: application/json' \
  -H 'Cookie: SESSION=<leader-session>' \
  -d '{
    "messageType": "TEXT",
    "text": "收到，待会我们按登录、会话、审批、Agent 的顺序演示。",
    "clientMessageId": "demo-leader-msg-1"
  }'
```

### 4. 审批流程模板

员工端要提交请假或补卡，后台必须先初始化流程模板。

请假流程：

1. 创建 `processCode=LEAVE`
2. 创建模板
3. 创建版本
4. 发布时使用 `flowableDefinitionKey=leave_manager_approval`

补卡流程：

1. 创建 `processCode=CLOCK_CORRECTION`
2. 创建模板
3. 创建版本
4. 发布时使用 `flowableDefinitionKey=clock_correction_manager_approval`

如果这一步没做，员工端提交时会返回：

- `code=404`
- `message=未找到已发布的流程模板`

### 5. Agent

如果要让消息页里直接出现 Agent：

1. 后台创建 Agent
2. 创建版本
3. 版本里写入模型配置
4. 发布版本
5. 给员工授予 ACL

本地兼容桩配置示例：

```json
{
  "provider": "openai-compatible",
  "baseUrl": "http://127.0.0.1:18080/v1",
  "apiKey": "demo-key",
  "model": "stub-chat"
}
```

ACL 至少给：

- `subjectType=USER`
- `subjectId=22002`
- `permCode=use`

---

## 六、前端当前必须知道的契约

### 1. 员工端没有前台自注册

前端“开通账号”页只能引导用户联系管理员，不能直接调注册接口。

### 2. 找回密码请求体

`POST /api/employee/auth/password/reset`

请求体是：

```json
{
  "email": "demo.employee1@example.com",
  "code": "123456",
  "newPassword": "NewPassword@123456"
}
```

不需要 `loginName`。

### 3. 补卡请求体

`POST /api/employee/workflow/clock-correction-requests`

当前后端真实字段是：

```json
{
  "targetDate": "2026-05-16",
  "missingPunchType": "OFF_DUTY",
  "expectedPunchAt": "2026-05-16 18:00:00",
  "reason": "下班时网络异常，补下班卡"
}
```

不是旧字段：

- `workDate`
- `correctionType`
- `correctedAt`

### 4. IM 发消息幂等

前端发消息时要带 `clientMessageId`，避免重复点击发送导致重复消息。

---

## 七、推荐联调顺序

1. 登录：`POST /api/employee/auth/login`
2. 当前用户：`GET /api/employee/auth/me`
3. 组织资料：`GET /api/employee/me/profile`
4. IM 列表：`GET /api/employee/im/conversations`
5. IM 消息：`GET /api/employee/im/conversations/{conversationId}/messages`
6. 发消息：`POST /api/employee/im/conversations/{conversationId}/messages`
7. 假期余额：`GET /api/employee/leave/accounts`
8. 待办列表：`GET /api/employee/workflow/todos`
9. 我的申请：`GET /api/employee/workflow/requests`
10. 找回密码：`POST /api/employee/auth/email/send-code`

---

## 八、当前已确认的本地现实状态

截至本轮核对：

- 后端健康检查正常
- `demo.employee1` 登录、`me`、`profile`、`manager` 正常
- `demo.employee1` 与 `demo.leader` 的 IM 私聊已真实创建并有消息
- 三位 demo 账号已有演示年假余额
- workflow 模板已由 `bootstrap_demo_data.py` 初始化，默认包含 `LEAVE` 和 `CLOCK_CORRECTION`
- `demo.employee1` 的 Agent 列表已由 `bootstrap_demo_data.py` 初始化，默认包含 `DEMO_IM_AGENT`
- 找回密码链路依赖本地 `127.0.0.1:1025` SMTP
