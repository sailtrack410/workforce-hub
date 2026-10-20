# 第 68 审 module-im 最小实时推送后端闭环审查

- 审查日期：2026-05-11
- 审查提交：
  - `3748724`（补齐Agent超时扫描批次查询，作为当前工作区增量基线）
  - 工作区增量（module-im 最小 WebSocket 实时推送：员工会话握手、用户维度连接注册、消息/编辑/撤回/已读事件推送）
- 审查范围：`server/src/main/java/com/workforcehub/im/`、`server/src/test/java/com/workforcehub/im/`、`docs/审查/README.md`
- 审查目标：复核 IM 是否从“只能 HTTP 拉取”推进到“前端可接入实时事件”的最小后端闭环，重点看握手鉴权、事件推送时机、会话成员边界、失败隔离和测试保护。

---

## 第一部分：提交内容概览

本轮围绕 `module-im` 补齐最小实时推送能力。

- 新增 `ImWebSocketConfig`，注册员工端 WebSocket 入口 `GET /ws/employee/im`。
- 新增 `ImWebSocketHandshakeInterceptor`，从现有登录 Session 或当前 `SecurityContext` 解析 `WfhUserDetails`，只允许 `UserType.USER` 建立 IM 连接。
- 新增 `ImWebSocketHandler`，连接建立后按员工用户 ID 注册 Session，并支持简单 `PING/PONG`。
- 新增 `ImWebSocketSessionRegistry`，按用户 ID 维护多端连接，推送失败时移除异常连接。
- 新增 `ImWebSocketPushService`，把推送和业务服务解耦，并通过 `TransactionSynchronization.afterCommit()` 保证事务提交后才发送事件。
- `ImService.sendCurrentEmployeeMessage(...)` 在消息写入、会话最近消息和发送人已读位点更新后，向会话活跃成员推送 `IM_MESSAGE_CREATED`。
- `ImService.editCurrentEmployeeMessage(...)` 和 `recallCurrentEmployeeMessage(...)` 分别推送 `IM_MESSAGE_EDITED`、`IM_MESSAGE_RECALLED`。
- `ImService.updateCurrentEmployeeReadPosition(...)` 推送 `IM_READ_POSITION_UPDATED`，方便前端做已读状态刷新。
- `ModuleImApiIntegrationTest` 新增模拟 WebSocket Session，覆盖发送消息、编辑消息、撤回消息、更新已读位点后的会话成员推送，且断言非会话成员不会收到消息创建事件。

当前后端已经能支撑前端演示的基础实时刷新：前端登录后连接 `/ws/employee/im`，收到消息、编辑、撤回和已读事件后刷新对应会话或局部更新消息列表。

## 第二部分：已收口问题复核

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 2-1 | `ImWebSocketConfig`、`ImWebSocketHandler` | 开发规划中的“WebSocket 连接”和“新消息推送”此前没有后端入口。当前新增 `/ws/employee/im`，员工端可以建立连接并接收 JSON 事件。 | 已处理 | 前端接入时连接该路径，收到 `CONNECTED` 后再认为实时通道可用。 |
| 2-2 | `ImWebSocketHandshakeInterceptor` | 实时通道如果不复用登录态，会形成绕过 HTTP 鉴权的新入口。当前握手阶段强制解析员工登录 Session，未登录返回 401，非员工身份返回 403。 | 已处理 | 后续如果支持 token 握手，也必须复用同一用户类型边界，不要新增独立弱鉴权逻辑。 |
| 2-3 | `ImWebSocketPushService.pushToUsersAfterCommit(...)` | 如果事务未提交就推送，前端可能收到数据库最终回滚的假消息。当前通过事务同步在 `afterCommit` 后发送，避免“响应失败但消息已推送”的不一致。 | 已处理 | 后续接 outbox 时继续保持“业务提交后发布事件”的边界。 |
| 2-4 | `ImService.pushConversationEventAfterCommit(...)` | 推送收件人来自 `im_conversation_member` 活跃成员，而不是客户端传入用户列表。当前非会话成员不会收到消息创建事件。 | 已处理 | 后续组织事件导致成员离开时，推送成员查询必须继续过滤 `status='ACTIVE'`。 |

## 第三部分：本轮新增正确性风险

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 3-1 | `ImWebSocketSessionRegistry` | 当前连接注册表是单 JVM 内存结构。单实例演示可用，但如果后端横向扩容，用户连接分布在不同实例上，某个实例内的业务动作只能推到本机连接，其他实例上的连接收不到事件。 | P2 | 单实例阶段可以接受；进入多实例前接 Redis pub/sub、消息队列或持久 outbox，把事件分发到所有节点。 |
| 3-2 | `ImWebSocketPushService.pushToUsers(...)` | 推送失败现在只记录日志并返回 0，不会阻断业务事务。这保证主链路可用，但也意味着前端可能漏事件且服务端没有可重试账本。 | P2 | 增加 `im_event_outbox` 或统一通知 outbox，失败事件可查询、重试和补偿；前端继续保留 HTTP 拉取兜底。 |
| 3-3 | `ImWebSocketHandshakeInterceptor` | 握手只校验登录态和用户类型，没有再次校验 `org_user` 是否仍处于可使用 IM 的员工状态。若员工登录后被离职，HTTP IM 接口会被 `requireActiveEmployeeUserId()` 拦住，但旧 WebSocket 连接在断开前仍可能保持在线并接收别人发来的会话事件。 | P1 | 握手阶段和推送收件人查询都应过滤登录允许员工状态；组织离职事件应主动关闭该用户 WebSocket 连接。 |
| 3-4 | `ImWebSocketConfig.setAllowedOriginPatterns("*")` | 当前为了本地前端演示放开 Origin。若生产环境继续使用通配符，浏览器可从非受信站点发起携带 Cookie 的 WebSocket 握手，增加 CSRF 型连接风险。 | P1 | 将允许来源改为配置项，生产只允许 employee-web 域名；必要时 WebSocket 握手增加一次性 CSRF token。 |
| 3-5 | `ImService.updateCurrentEmployeeReadPosition(...)` | 已读事件广播给会话所有成员。当前只包含 `userId` 和 `lastReadSeq`，不暴露敏感内容，演示可用；但真实群聊中已读回执是否对所有成员可见是产品策略问题。 | P3 | 前端先用于本地刷新；后续按产品策略决定群聊是否展示成员级已读，必要时只推给本人或管理员。 |

## 第四部分：冗余与可维护性隐患

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 4-1 | `ImService` | 当前通过 `pushConversationEventAfterCommit(...)` 在业务服务中直接组装推送事件。虽然推送实现已拆到 `im.websocket`，但业务服务仍知道具体事件名。 | P2 | 引入 IM 领域事件或 `ImEventPublisher`，业务服务只发布语义事件，WebSocket/outbox/通知中心订阅处理。 |
| 4-2 | `ImWebSocketEvent.payload` | 事件载荷使用 `Object`，目前直接放 DTO 或 Map，迭代快但缺少版本化契约。前端接入后字段变更容易造成兼容问题。 | P2 | 定义 `ImMessageEventPayload`、`ImReadPositionPayload` 等稳定 DTO，并增加 `eventVersion`。 |
| 4-3 | `ModuleImApiIntegrationTest` | 为了快速覆盖推送闭环，测试在现有大集成测试类里加入模拟 `WebSocketSession`。继续增长会让该类过重。 | P2 | 下一轮把实时推送测试拆到 `ModuleImWebSocketIntegrationTest` 或专门的 push service 测试类。 |
| 4-4 | 连接生命周期治理 | 当前有注册、断开和错误移除，但没有心跳超时扫描、连接数限制或用户多端策略。 | P2 | 增加心跳时间记录、空闲关闭和单用户最大连接数，防止异常客户端长期占用内存。 |

## 第五部分：验证与未验证项

| 验证项 | 命令 | 结果 | 说明 |
| --- | --- | --- | --- |
| 工作区状态 | `git status --short` | 已执行 | 工作区存在多模块未提交改动；本轮只处理 IM 实时推送、测试和审查文档，不回滚无关改动。 |
| 审查编号确认 | 读取 `docs/审查/README.md` 和 `docs/审查/审查规范.md` | 已执行 | 当前最新为第 67 审，本轮新增第 68 审。 |
| 后端编译 | `mvn -q -DskipTests compile` | 通过 | 在 `server/` 下执行。 |
| IM 目标测试 | `env TEST_DB_NAME=workforce_hub_node68_im_websocket_push3 mvn -q -Dtest=ModuleImApiIntegrationTest,ModuleImGroupIntegrationTest test` | 通过 | 覆盖会话、分页、群聊、附件、编辑撤回、已读位点和 WebSocket 推送事件。 |
| 差异格式检查 | `git diff --check -- docs/审查/第67审_module-im会话列表分页后端闭环审查.md docs/审查/README.md server/src/main/java/com/workforcehub/im server/src/test/java/com/workforcehub/im` | 通过 | 当前 IM 与审查文档差异没有空白错误。 |

未验证项：

- 没有跑全仓 Maven 测试，原因是当前工作区已有多模块未提交改动，全量回归会混入非本轮变量。
- 没有用真实浏览器或真实 WebSocket 客户端完成端到端握手，只通过后端模拟 Session 验证推送链路。
- 没有验证生产跨域配置，因为当前仍是本地演示通配 Origin。
- 没有做断线重连、心跳超时、消息补偿和多实例广播。
- 没有接 employee-web 实时刷新逻辑，本轮只完成后端事件出口。

## 第六部分：处理优先级建议

| 序号 | 问题位置 | 问题描述 | 严重程度 | 优化方案 |
| --- | --- | --- | --- | --- |
| 6-1 | 握手与推送员工状态 | 离职或禁用后的旧连接仍可能接收事件，这是本轮最需要尽快收口的权限边界。 | P1 | 握手时校验员工状态，组织状态变化时关闭连接；推送收件人查询也过滤可登录员工。 |
| 6-2 | WebSocket Origin | 演示通配 Origin 不能带到生产。 | P1 | 增加配置项并按环境收紧允许来源。 |
| 6-3 | 推送可靠性 | 当前没有 outbox，漏推只能靠前端重新拉取。 | P2 | 下一阶段补 outbox 或至少补事件序列号，前端断线后按序补拉。 |
| 6-4 | 群聊剩余治理 | 实时通道已可用，但群聊退群、解散、公告资料维护还缺后端接口。 | P2 | 继续补群治理接口，保证前端演示不只停留在拉人/移人。 |

## 第七部分：阶段性结论

本轮 `module-im` 最小实时推送后端闭环已经达到演示可接入状态：员工端可建立 WebSocket，发送、编辑、撤回和已读位点更新会在事务提交后向会话活跃成员推送事件，目标集成测试已经覆盖主路径。

当前存在两个必须在生产化前处理的 P1：离职旧连接收件边界和 WebSocket Origin 通配。演示阶段可以继续推进前端接入，但后续不能把当前最小实现直接视为生产级实时通信方案。
