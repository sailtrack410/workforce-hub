# 全链路检测记录

## 范围

- 后端 Maven 测试与服务启动
- 管理端 Vitest、构建、登录与 Agent 任务页
- 员工端 Vitest、构建、登录与消息页
- 浏览器截图与控制台错误检查

## 结果

- 后端 `mvn test` 未通过：261 个测试，8 个失败，2 个错误，2 个跳过。
- 后端服务启动成功，`/actuator/health` 返回 `UP`。
- 管理端单测、构建、登录链路通过。
- 员工端单测、构建、登录链路通过。
- 浏览器关键页无控制台 error。

## 问题

- `server/src/main/java/com/workforcehub/agent/service/AgentService.java`
  - `TOOL_TYPES` 当前仅允许 `SPI`，但测试与工具执行服务仍覆盖 `CONNECTOR`，导致创建 CONNECTOR 工具返回“工具类型不支持”。

- `server/src/main/java/com/workforcehub/agent/workflow/WorkflowExecutor.java`
  - 单字段 `outputFormat` 为数组时被直接拆成数组返回，调用侧仍按对象字段读取，导致两个数组字段测试 NPE，并影响 IM DAG 假期余额回复。

- `server/src/main/java/com/workforcehub/file/service/FileDownloadService.java`
  - 员工端非个人文件下载拒绝文案已改为“个人文件或头像”，测试仍期望旧文案“个人文件”。

## 截图

- `01-admin-login.png` / `02-admin-after-login.png` / `03-employee-login.png` / `04-employee-after-login.png`：首次采样过早，页面尚未稳定。
- `05-employee-login-stable.png`
- `06-employee-after-login-stable.png`
- `07-admin-login-stable.png`
- `08-admin-after-login-stable.png`
