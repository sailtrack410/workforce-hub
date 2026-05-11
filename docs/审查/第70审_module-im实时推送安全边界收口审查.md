# 第 70 审 module-im 实时推送安全边界收口审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 实时推送安全边界：WebSocket 握手员工状态校验、推送收件人过滤、Origin 配置化）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/main/resources/application.yml`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核第 68 审指出的实时通道 P1 风险是否收口，重点看离职/禁用员工是否还能建立或接收 IM WebSocket 事件，以及跨域 Origin 是否仍硬编码通配。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 实时推送的安全边界做收口。

- `ImWebSocketHandshakeInterceptor` 注入 `ImRepository`，握手阶段除了校验登录态和 `UserType.USER`，还校验 `existsLoginAllowedOrgUser(userId)`。
- `ImRepository.listPushableActiveUserMemberIds(...)` 新增推送收件人查询，只返回活跃会话成员中仍处于可登录、可使用员工端状态的员工。
- `ImService.activeUserMemberIds(...)` 改为使用可推送成员查询，消息、已读、群治理事件不再推给离职或账号禁用成员。
- `ImWebSocketConfig` 将 `setAllowedOriginPatterns("*")` 改为读取配置 `workforce.im.websocket.allowed-origin-patterns`。
- `application.yml` 默认允许 `http://localhost:5173`、`http://127.0.0.1:5173`，并支持通过 `IM_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS` 覆盖。
- `ModuleImApiIntegrationTest.unavailableEmployeeShouldNotReceiveWebSocketPush` 覆盖会话成员离职后不再收到消息创建事件。

当前实时通道已经从演示通配和单纯登录态校验，收口到“只有仍可使用员工端的用户才能握手和接收 IM 推送”。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImWebSocketHandshakeInterceptor.beforeHandshake(...)` | 第 68 审指出，员工登录后如果被离职，旧 Session 仍可能建立 WebSocket。当前握手阶段复用 `existsLoginAllowedOrgUser`，离职、删除、账号禁用员工会被 403 拒绝。 | 已处理 | 后续组织事件触发时可进一步主动关闭已存在连接，但新握手已不再放行。 |
| 2-2 | `ImService.activeUserMemberIds(...)`、`ImRepository.listPushableActiveUserMemberIds(...)` | 第 68 审指出，离职旧连接可能继续接收别人发来的会话事件。当前推送收件人不再只看 `im_conversation_member.status='ACTIVE'`，而是联查 `org_user` 和 `auth_user_account`，过滤不可登录员工。 | 已处理 | 后续增加连接主动踢下线，减少旧连接空占内存。 |
| 2-3 | `ImWebSocketConfig.registerWebSocketHandlers(...)` | 第 68 审指出，WebSocket Origin 硬编码通配存在生产 CSRF 型连接风险。当前改为配置项，默认只允许本地前端开发域名，生产可通过环境变量指定正式域名。 | 已处理 | 部署时必须显式配置正式 employee-web 域名，不要把 `*` 带到生产。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImWebSocketSessionRegistry` | 当前过滤保证不可用员工不会成为新事件收件人，但已经注册的旧连接不会被立即关闭。它收不到新事件，但仍占用内存直到断开或发送失败被移除。 | P2 | 监听 `OrgUserStatusChangedEvent`、`OrgUserAccountChangedEvent`，主动关闭对应用户的 WebSocket 连接。 |
| 3-2 | `workforce.im.websocket.allowed-origin-patterns` | 默认值适合本地 Vite 开发。如果生产未配置环境变量，正式前端域名会连接失败。 | P2 | 在部署清单和环境样板中补 `IM_WEBSOCKET_ALLOWED_ORIGIN_PATTERNS=https://employee.example.com`。 |
| 3-3 | `ImRepository.listPushableActiveUserMemberIds(...)` | 推送收件人联查 `org_user` 与 `auth_user_account`，安全性更好，但每次推送都会多一次联查。小规模演示可接受，高频群聊下会增加数据库压力。 | P2 | 缓存会话成员可推送列表，或在 outbox/推送层批量预取；同时给成员表和用户表补查询索引评估。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImWebSocketHandshakeInterceptor` | 握手拦截器直接依赖 `ImRepository`，当前能快速复用员工状态判断，但会让 WebSocket 层知道 IM 仓储细节。 | P2 | 后续抽出 `ImUserAccessService` 或复用统一的员工可用性 SPI。 |
| 4-2 | `application.yml` | WebSocket Origin 配置已新增，但还没有同步到示例环境文件、部署文档或 API 文档。 | P2 | 补充环境变量说明，避免部署时遗漏导致前端无法连接。 |
| 4-3 | 测试覆盖 | 当前只覆盖“离职成员不会收到推送”，没有真实握手 403 测试。 | P2 | 增加真实 WebSocket 客户端集成测试或握手拦截器单测，覆盖未登录、管理员、离职员工三类拒绝。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只处理 IM 实时推送安全边界、配置、测试和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 69 审，本轮新增第 70 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node70_im_ws_security mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖 IM 主链路、群治理、实时推送和不可用员工推送过滤。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有跑真实 WebSocket 握手端到端测试。
- 没有补生产部署环境样板。
- 没有实现组织事件触发后主动关闭旧 WebSocket 连接。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 旧连接主动关闭 | 离职旧连接不再收到事件，但仍可能占用内存。 | P2 | 接组织事件监听，调用注册表按 userId 关闭连接。 |
| 6-2 | 生产 Origin 配置文档 | 配置项已有，但部署文档未更新。 | P2 | 补环境变量样板和部署说明。 |
| 6-3 | 推送可靠性 | 安全边界已收口，但 outbox 仍未实现。 | P2 | 后续补事件表和断线补拉能力。 |

## 第七部分：阶段性结论

本轮已经把第 68 审中两个 P1 安全风险降级收口：WebSocket 不再只靠旧登录态放行，推送也不再发给不可用员工；Origin 通配也改为配置化默认本地前端域名。

当前没有新的 P0/P1 阻断项，可以继续推进前端实时接入或后端组织事件同步。生产化前仍需要补旧连接主动关闭、部署环境样板和 outbox 可靠投递。
