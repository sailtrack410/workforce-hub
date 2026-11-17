# IM / Outbox / Agent / RAG 业务链路

仓库快照：`38d829167042dc26efd7af502ce623c976d6e224`。9张原生Workflow图，schema v2 / zh-CN / repository local-only。

验证范围：源码静态追踪及Archify产物校验/真实浏览器自动检查。未启动应用、未执行后端/前端业务测试、未调用真实供应商、未读取凭证。下面的业务异常与风险是静态推导，不冒充线上复现。

源码工作树已有`docs/审查/说明.md`改动、`.archify/`和第99审记录；本代理仅写当前im-agent-rag目录，不触及业务源码或共享索引。

## 前端边界

默认消息入口是`employee-web/src/hub/views/MessagesView.vue`（/hub/messages）；`employee-web/src/views/sections/MessagesSection.vue`是旧section。`hub-web/src/views/MessagesView.vue`属于独立前端，群治理更完整。功能对照不能跨入口借用。

新入口已接文本、附件发送、引用、撤回/本人删除、改群名/加成员/退群；知识卡与流程卡动作只toast。附件打开误用通用文件API，IM_MESSAGE引用会被403；图中IM专用票据是后端已实现但新入口未正确调用的能力，见IAR-09。

## 消息发送、实时送达与附件访问

[流程图](im-delivery.html)

- 范围：私聊/群聊消息：幂等发送、会话序号、事务Outbox、已读、撤回与下载票据。
- 前端：新入口/hub/messages已接文本/附件发送/引用/撤回；附件打开误用通用文件票据，将被IM_MESSAGE归属校验403；图中IM专用票据是可修复使用的后端能力。
- 正常：消息、附件引用、摘要与Outbox同事务保存；在线推送后前端合并消息，读后更新位点。IM专用票据已实现，新页附件打开误用通用票据而403。
- 异常：无会话权限403；空消息400；无效附件404/409；重复发送返回同一消息；撤回状态禁止附件下载。 新员工入口附件点击使用通用票据，IM_MESSAGE引用返回403，见IAR-09。
- 恢复：发送失败由客户端保留草稿/重试；网络恢复补拉会话及历史。Outbox失败重试见独立链。

## 群聊创建、成员治理与退出解散

[流程图](im-groups.html)

- 范围：自定义群创建、群主治理、成员退出、解散与变更通知。
- 前端：employee-web新页支持改名/加成员/退群；独立hub-web另接公告、移除成员；旧MessagesSection治理入口不作为新页实现证据。
- 正常：创建群及成员；群主校验后治理；Outbox通知并刷新成员与会话列表。
- 异常：无效组织用户拒绝；非群主不能治理；群主退群被拒；移出后API成员校验拦截访问。
- 恢复：前端请求失败展示服务端错误并保留操作；事件遗漏由会话/详情重新加载恢复。

## Outbox认领、失败重试与人工审计

[流程图](im-outbox.html)

- 范围：待发事件轮询、过期SENDING回收、FAILED/DEAD重试、后台审计与指标。
- 前端：后台提供事件列表、失败摘要、单条/批量重试和手动派发API；前端是否可见不替代后台权限结论。
- 正常：定时或手工认领到期事件，发送后标SENT；后台可查看状态、失败分类与治理审计。
- 异常：发送异常累加次数，FAILED延迟重试；达到上限DEAD；非法payload或不可重试状态409。
- 恢复：后台校验payload索引后一条/批量重置，自动轮询继续派发；陈旧SENDING可被重新认领。

## Agent草稿、DAG测试与ACL发布

[流程图](agent-release.html)

- 范围：Agent版本配置、工具绑定、DAG校验、测试签名、使用范围与发布启停。
- 前端：admin-web管理Agent/版本/工作流；员工目录仅返回可用Agent；不能以管理端可配置推断员工已接所有能力。
- 正常：草稿配置工具和可用范围，DAG验证与试运行后签名匹配才正常发布；员工使用再查ACTIVE/use ACL。
- 异常：草稿外修改/重复发布409；无范围400；测试签名不符400；DAG循环/孤立节点拒绝。
- 恢复：修正草稿并重测后发布；停用可以重新启用，弃用不可恢复。自动签名门禁问题见记录。

## 工具任务、人审确认与超时取消

[流程图](agent-tools.html)

- 范围：显式工具调用的人审、恢复执行、拒绝取消、超时扫描及DAG分支差异。
- 前端：员工任务确认/取消API已实现；DAG APPROVAL是提示节点，不代表持久人审暂停。
- 正常：显式工具需确认时先落等待任务，所属员工确认后执行并记审计；AUTO直接执行。
- 异常：非归属用户403；非等待状态/过期409；CONNECTOR执行器501；工具业务异常记录FAILED。
- 恢复：可拒绝/取消等待任务；超时扫描原子取消并记录作业，WF_REQUEST回调业务恢复由审批链负责。

## 会话触发Agent、异步执行与结果回写

[流程图](agent-conversation.html)

- 范围：提交后的Agent任务创建、进程内调度、DAG/模型执行、结果写回IM与失败卡片。
- 前端：employee-web新/hub/messages与独立hub-web均展示Agent消息；employee-web新页知识卡/流程卡动作仅toast，后端回复成功不表示卡片动作可执行。
- 正常：用户文本提交后创建任务，异步置RUNNING并执行；成功事务内更新任务、插入Agent回复及消息事件。
- 异常：触发异常写系统提示；模型异常记FAILED并尝试写失败提示；失败提示写回再次失败仅日志。
- 恢复：重复结果回调按任务消息键去重；用户可重发文本形成新任务。进程中断自动续跑/失败回写补偿为待完善项。

## 知识空间ACL与文档入库生命周期

[流程图](knowledge-documents.html)

- 范围：空间创建/模型维度、ACL保存、附件校验、异步入库、重入库与软删除。
- 前端：admin-web知识库具备空间、文档、ACL与检索测试入口；ACL保存不代表所有读取链已执行ACL。
- 正常：创建空间并保存ACL，上传附件登记文档和作业；解析分块、嵌入、写PG向量和MySQL清单，更新成功。
- 异常：附件归属403；无解析内容422；向量/模型故障FAILED；向量清理失败只日志、不回滚删除。
- 恢复：修复模型或文件后手工reingest新作业；删除软标记与检索标题过滤阻止已删文档返回，跨库残留需要清理补偿。

## 知识检索、提示编排与问答返回

[流程图](rag-qa.html)

- 范围：Agent已授权工具检索知识空间，查询嵌入、向量阈值过滤、补标题并进入模型提示。
- 前端：员工问答通过Agent及IM显示；employee-web新页知识卡动作仅“即将上线”；管理端searchTest返回命中，不等同问答端到端实测。
- 正常：可用Agent调用已绑定知识工具，解析配置、嵌入query、TopK向量检索，补标题后进入模型上下文并回写消息。
- 异常：向量不可用422；模型/检索异常按任务失败处理；已删文档经标题映射过滤。
- 恢复：管理员检索测试定位命中；修复模型与向量配置后再问。空间ACL执行缺口需统一服务端授权修复。

## AI供应商模型配置、探活与自检

[流程图](ai-provider-health.html)

- 范围：供应商与多类模型配置、列表发现、类型化探针、结果持久化及定时自检。
- 前端：admin-web配置供应商和模型；配置响应使用Key是否已配置的标记，图中不包含真实凭证。
- 正常：配置模型，发现供应商模型列表，按能力执行真实协议探针，记录结果和耗时；周期自检复用此链。
- 异常：端点/模型/Key未配置返回失败；远端异常转友好错误；供应商或模型自检异常分开捕获。
- 恢复：修改端点/模型设置后手动重测；定时自检只遍历启用记录。建议限制批量探活成本与并发并审计配置变更。

## 问题与优化记录

第99审已记录的默认放行、陈旧会话、假期类型结算、固定时段与跨JVM ID仅引用，不重复立项。

### IAR-01 · P1

- 状态：静态代码确认；未运行应用复现
- 触发：有Agent使用权限的员工通过已绑定KNOWLEDGE_SEARCH_CHUNKS查询其无read权限的知识空间。
- 影响：Agent use授权不能替代知识空间ACL，PRIVATE/部门范围配置可能不约束该工具读链；实际返回取决于配置与文档内容。
- 证据：Controller直接进入AgentService；上层只检查Agent use。工具对payload.userId作组织授权，但知识工具只传spaceId/query/topK；searchRows无操作者参数，向量SQL仅space_id、is_active和score。kb_space_acl仅在管理端保存/读取，不参与该读链。
- 代码：`server/src/main/java/com/workforcehub/agent/controller/EmployeeAgentController.java:40`；`server/src/main/java/com/workforcehub/agent/service/AgentService.java:518`；`server/src/main/java/com/workforcehub/agent/service/AgentToolExecutionService.java:86`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowKnowledgeSearchService.java:44`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowVectorRepository.java:60`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowAdminService.java:361`
- 修复：在知识检索服务统一传入真实操作者，强制空间ACTIVE、visibility及read/manage ACL；管理员测试明确单独受控；文档检索与问答共用同一授权决策。
- 验收：同一私有空间：空间所有者/已授权员工能命中，无read权限但有Agent use权限者收到403；跨部门、ACL撤销及停用空间同测；不得仅在UI隐藏。

### IAR-02 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：DAG TOOL或节点toolCalls绑定REQUIRE_CONFIRM，员工创建任务时request.toolCalls为空，或由会话触发DAG。
- 影响：配置要求的人审被跳过，工具日志直接标APPROVED；当前实现的SPI为查询且CONNECTOR返回501，因此不声称已有写业务被执行。
- 证据：人审计算仅遍历外部preparedToolCalls。DAG执行器检查绑定存在后直接调用execute，并记录APPROVED；执行器与validator未检查approvalMode。APPROVAL节点也被分派到executePromptNode，不是持久化人工等待。
- 代码：`server/src/main/java/com/workforcehub/agent/service/AgentService.java:531`；`server/src/main/java/com/workforcehub/agent/service/AgentService.java:537`；`server/src/main/java/com/workforcehub/agent/workflow/WorkflowExecutor.java:161`；`server/src/main/java/com/workforcehub/agent/workflow/WorkflowExecutor.java:313`；`server/src/main/java/com/workforcehub/agent/service/AgentToolExecutionService.java:50`；`server/src/main/java/com/workforcehub/agent/workflow/WorkflowValidator.java:49`
- 修复：将审批判断收口到工具执行边界；DAG遇需确认工具持久化节点/参数/版本快照，WAITING_HUMAN_CONFIRM后暂停，确认时从该节点恢复，未批准不得落APPROVED。
- 验收：相同REQUIRE_CONFIRM绑定分别经显式工具、DAG TOOL、内嵌toolCalls、会话触发时均先等待；拒绝/超时不执行；重复确认只执行一次；AUTO保持直通。

### IAR-03 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：创建含内嵌工具调用的DAG草稿，或将非DAG草稿更新为DAG，随后直接发布。
- 影响：部分路径未实际试运行即获得workflow_test_signature/workflow_tested_at，弱化“当前版本完成一次流程测试”的发布门禁。
- 证据：createVersion与旧配置转DAG直接调用updateAgentVersionWorkflowTest；该方法写签名和NOW()。publish只比较签名。仓库测试契约明确“后台发布DAG Agent前必须完成一次当前版本流程测试”，但该测试覆盖simpleWorkflow，非自动签名例外。
- 代码：`server/src/main/java/com/workforcehub/agent/service/AgentService.java:319`；`server/src/main/java/com/workforcehub/agent/service/AgentService.java:350`；`server/src/main/java/com/workforcehub/agent/service/AgentService.java:444`；`server/src/main/java/com/workforcehub/agent/repository/AgentRepository.java:105`；`server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java:240`
- 修复：只有成功试运行入口写测试签名/时间；创建、更新、工具绑定/关键引用变更均清空测试证明；兼容迁移标记与真实测试结果分字段。
- 验收：两类自动签名分支在未试运行时发布400；失败试运行仍不能发布；成功且配置相同可发布；改配置或工具绑定后必须重测。

### IAR-04 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：Agent或知识入库事务已提交，但进程在Executor投递前/执行中退出，或线程池拒绝任务。
- 影响：任务/作业记录仍在表中，可停留PENDING/RUNNING；当前范围未发现自动重新认领/恢复入口，现有人工确认超时扫描不覆盖它们。
- 证据：两类任务提交后仅调用进程内Executor。超时SQL限定WAITING_HUMAN_CONFIRM；ragflow目录中ingest调用仅scheduleIngest。记录持久化已存在，问题是执行投递/恢复，不是数据库任务丢失。
- 代码：`server/src/main/java/com/workforcehub/agent/service/AgentTaskExecutionService.java:111`；`server/src/main/java/com/workforcehub/agent/service/AgentTaskExecutionService.java:164`；`server/src/main/java/com/workforcehub/agent/repository/AgentTaskTimeoutRepository.java:28`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowAdminService.java:287`
- 修复：使用现有任务表实现带租约的待执行认领，保存可恢复输入及重试次数；入库作业亦以表状态调度，定义运行中超时和人工重试边界。
- 验收：在提交后投递前及执行中分别终止可控测试worker；重启后同作业被恢复且消息/切片无重复；拒绝执行可查询、可重试且不永久显示处理中。

### IAR-05 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：同一文档两次reingest并发，或删除与已运行ingest交错。
- 影响：旧作业仍可覆盖新作业文档状态/清单，已删除文档仍可能新增向量；查询有已删标题过滤，不据此声称删除后必定可读。
- 证据：reingest每次创建新作业并改latest指向；运行/成功/失败UPDATE仅WHERE document.id，不约束latest_ingest_job_id或is_deleted；replaceChunks按document整删；PG replaceDocumentVectors亦按document先停旧向量。
- 代码：`server/src/main/java/com/workforcehub/ragflow/service/RagflowAdminService.java:263`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowRepository.java:308`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowRepository.java:318`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowRepository.java:334`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowRepository.java:352`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowIngestService.java:79`
- 修复：按文档租约串行，或在发布结果时比较latest_ingest_job_id与未删除状态；先写作业独立代次，成功后原子切换有效代次，失效作业仅清理自己的结果。
- 验收：控制旧作业晚于新作业完成，最终清单/向量/状态均来自新作业；删除后旧作业完成不得重新激活；重复回调幂等。

### IAR-06 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：重入库写PG向量后MySQL清单失败，或向量插入中途失败。
- 影响：向量与清单跨库非原子，旧向量先停用且部分新向量可能可见；MySQL作业FAILED不能保证检索仍使用完整旧版本。
- 证据：replaceDocumentVectors先UPDATE旧向量is_active=false，再逐条insert active新向量；完成后才调用MySQL replaceChunks/markIngestSuccess。search不连接MySQL作业成功状态，只按space与active。
- 代码：`server/src/main/java/com/workforcehub/ragflow/repository/RagflowVectorRepository.java:35`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowIngestService.java:79`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowRepository.java:334`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowVectorRepository.java:60`
- 修复：按jobId写不可见新代次，完整写入后切换已发布代次；失败删除该job向量并保留旧代次，增加跨库对账修复。
- 验收：在第N条向量插入与MySQL清单阶段分别注入失败，查询保持上一完整版本且不出现半批内容；重试成功后再切换，清理只影响失败job。

### IAR-07 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：知识空间启用rerank并配置重排模型后进行搜索测试或Agent检索。
- 影响：配置面存在重排选项，但当前有效配置及检索链未调用重排，排序仍为向量距离；属于功能闭环缺口。
- 证据：空间更新保存rerankEnabled/provider/model/modelId；RagflowEffectiveConfig解析仅Embedding、chunk、topK与threshold，searchRows直接返回向量排序且未调用rerank客户端。
- 代码：`server/src/main/java/com/workforcehub/ragflow/service/RagflowAdminService.java:193`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowConfigResolver.java:10`；`server/src/main/java/com/workforcehub/ragflow/service/RagflowKnowledgeSearchService.java:44`；`server/src/main/java/com/workforcehub/ragflow/repository/RagflowVectorRepository.java:75`
- 修复：在TopN向量候选后接受控重排并返回实际生效标记，或在接通前禁止可保存的启用选项，避免配置与执行不一致。
- 验收：启用后测试替身断言真实调用重排且返回重排顺序；停用无重排调用；重排超时有明确降级状态与结果，不静默假装生效。

### IAR-08 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：用户在employee-web新入口点击知识卡/流程卡动作，或期待旧MessagesSection已有操作出现在新页。
- 影响：卡片只显示toast而未执行动作；新旧页面和独立hub-web能力不一致，后台能力存在不能作为新入口交付证明。
- 证据：新路由/hub/messages指向src/hub/views/MessagesView，旧/messages重定向。新页handleKnowledgeAction/handleProcessAction仅“即将上线”；新页群编辑只改名+加人+退群，独立hub-web已有移除成员API。
- 代码：`employee-web/src/router/index.ts:35`；`employee-web/src/router/index.ts:60`；`employee-web/src/hub/views/MessagesView.vue:339`；`employee-web/src/hub/views/MessagesView.vue:446`；`hub-web/src/views/MessagesView.vue:553`
- 修复：以新入口逐项登记能力，接通卡片动作的真实业务API/路由；未实现动作隐藏或禁用，避免只toast假闭环；群治理按当前产品要求补齐。
- 验收：从默认入口操作卡片，观察对应业务请求/落库或正确路由；失败可恢复且有错误。新页公告/移除成员等是否交付单独列证，不以旧页测试替代。

### IAR-09 · P2

- 状态：静态代码确认；未运行应用复现
- 触发：在employee-web新/hub/messages上传并发送附件，再点击附件打开；从个人文件转发到消息也一样。
- 影响：消息发送成功且展示附件，但打开走个人文件票据，被IM_MESSAGE归属限制拒绝403；属于下载契约错配，不是越权。
- 证据：上传ownerType=IM_MESSAGE_PENDING；发送时不论源ownerType均复制为IM_MESSAGE引用，消息响应从IM_MESSAGE列表返回新refId；前端保留此refId后交给employeeApi.files.createDownloadTicket。FileDownloadService仅允许PERSONAL_FILE/USER_AVATAR，因此该请求确定不匹配契约。
- 代码：`employee-web/src/composables/usePersonalFileTransfer.ts:109`；`employee-web/src/hub/views/MessagesView.vue:317`；`server/src/main/java/com/workforcehub/im/repository/ImRepository.java:357`；`server/src/main/java/com/workforcehub/im/service/ImService.java:824`；`employee-web/src/stores/im.ts:697`；`employee-web/src/hub/views/MessagesView.vue:329`；`server/src/main/java/com/workforcehub/file/service/FileDownloadService.java:219`
- 修复：新页openAttachment使用会话ID、消息ID、附件refId调用IM专用下载票据API，并使用其downloadUrl；消息/群文件/个人文件分别使用对应入口。
- 验收：上传发送后发送方和正常成员可打开；退出群/被移除、撤回、过期和已用票据均按IM规则拒绝。网络失败保留附件可重试。

## 后续收口顺序

1. 先统一知识检索read授权，再补DAG工具人审及发布测试真实性。
2. 任务表租约调度与文档入库代次切换优先于新增AI功能。
3. 按新员工入口验收卡片与群治理；供应商探活和知识检索各用真实对应能力的探针。

Outbox当前固定延迟重试且SENT不代表收件人确认；保留该语义，可按运行量补指数退避、抖动、告警与积压阈值。模型探活按模型类型有真实探针，建议补成本预算、限并发和配置变更审计；这些是优化建议，不计作已复现缺陷。
