import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent
BASE=ROOT.relative_to(Path.cwd()).as_posix()
JAVA='server/src/main/java/com/workforcehub/'
def src(path,line,end=None):
    d={'path':JAVA+path if path.endswith('.java') else path,'line':line}
    if end: d['end_line']=end
    return d
def n(id,lane,col,label,sub,path,line,end=None,typ='backend'):
    return dict(id=id,lane=lane,col=col,type=typ,label=label,sublabel=sub,sources=[src(path,line,end)])
def e(a,b,label='',role=None):
    d=dict(from_=a,to=b)
    d={'from':a,'to':b}
    if label:d['label']=label
    if role:d['role']=role
    if role=='error':d['variant']='security'
    if role=='async':d['variant']='dashed'
    return d
chains=[]
def flow(id,title,scope,status,nodes,edges,main,cards,normal,exception,recovery):
    output=f'{BASE}/{id}.html'
    d={'schema_version':2,'diagram_type':'workflow','meta':{'title':title,'locale':'zh-CN','output':output,'quality_profile':'showcase','repository':{'url':'https://github.com/sailtrack410/workforce-hub.git','revision':'38d829167042dc26efd7af502ce623c976d6e224','link_mode':'local-only'}},'lanes':[{'id':'entry','label':'业务入口与结果'},{'id':'core','label':'服务执行与落库'},{'id':'recover','label':'分支、异常与恢复','variant':'exception'}],'mainPath':main,'nodes':nodes,'edges':edges,'cards':[{'dot':'cyan','title':'范围与实现','items':[scope,status]},{'dot':'amber','title':'异常与恢复','items':cards}]}
    (ROOT/f'{id}.candidate.json').write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
    chains.append({'id':id,'title':title,'scope':scope,'frontend_status':status,'html':output,'candidate':f'{BASE}/{id}.candidate.json','receipt':f'{BASE}/{id}.finalize-summary.json','summary':normal,'normal':normal,'exception':exception,'recovery':recovery})

flow('im-delivery','消息发送、实时送达与附件访问','私聊/群聊消息：幂等发送、会话序号、事务Outbox、已读、撤回与下载票据。','employee-web旧MessagesSection有发送/操作入口；新Messages壳与独立hub-web使用各自消息页，能力需分别核对。',[
n('compose','entry',0,'员工发送','文本、附件、引用、提及','employee-web/src/composables/useMessagesState.ts',676,733,'frontend'),
n('access','core',1,'校验会话成员','内容与附件归属','im/service/ImService.java',197,232,'security'),
n('persist','core',2,'幂等写入消息','会话行锁；seq递增','im/service/ImService.java',206,267,'database'),
n('enqueue','core',3,'同事务写Outbox','提交后触发派发','im/websocket/ImWebSocketPushService.java',37,53,'messagebus'),
n('deliver','core',4,'派发在线连接','SENT不等于员工已读','im/websocket/ImWebSocketOutboxDispatcher.java',97,119,'messagebus'),
n('sync','entry',5,'同步会话消息','重连补拉列表与历史','employee-web/src/composables/useImSocket.ts',102,169,'frontend'),
n('read','recover',5,'更新已读位点','不超过会话最大seq','im/service/ImService.java',582,603,'database'),
n('recall','recover',2,'撤回本人消息','状态与会话归属校验','im/service/ImService.java',319,337),
n('ticket','recover',3,'附件下载票据','10分钟；执行时复查','im/service/ImService.java',464,523,'security'),
n('download','recover',4,'领取票据读对象','撤回或移出后拒绝','im/service/ImService.java',526,573,'backend'),
n('duplicate','recover',1,'返回已有消息','同clientMessageId不重写','im/service/ImService.java',215,226),
],[e('compose','access'),e('access','persist','通过'),e('persist','enqueue'),e('enqueue','deliver','提交后','async'),e('deliver','sync'),e('sync','read','阅读'),e('persist','duplicate','已存在','branch'),e('persist','recall','本人操作','branch'),e('recall','ticket','仍可访问？'),e('ticket','download','状态正常'),e('download','sync','返回文件','return')],['compose','access','persist','enqueue','deliver','sync'],['在线连接不存在也可标SENT；重连通过HTTP补拉，Outbox不是离线收件箱。','重复clientMessageId返回已有消息；撤回后下载受消息状态限制。'],'发送成功同时保存消息、附件引用、会话摘要与Outbox；在线推送后前端合并消息，读后更新位点。','无会话权限403；空消息400；无效附件404/409；重复发送返回同一消息；撤回状态禁止附件下载。','发送失败由客户端保留草稿/重试；网络恢复补拉会话及历史。Outbox失败重试见独立链。')

flow('im-groups','群聊创建、成员治理与退出解散','自定义群创建、群主治理、成员退出、解散与变更通知。','独立hub-web已接群资料/成员增删/退群；employee-web旧section有治理入口，新Messages页不按旧section能力推定。',[
n('create','entry',0,'员工建群','至少一位其他有效员工','im/service/ImGroupService.java',74,98),
n('members','core',1,'保存群和成员','OWNER与MEMBER','im/service/ImGroupService.java',89,95,'database'),
n('owner','core',2,'群主权限校验','仅自定义群支持治理','im/service/ImGroupService.java',398,407,'security'),
n('change','core',3,'资料或成员变更','添加、移除、公告摘要','im/service/ImGroupService.java',244,305),
n('event','core',4,'写变更事件','移除前收件人快照','im/service/ImGroupService.java',286,304,'messagebus'),
n('refresh','entry',5,'刷新群与列表','失败保留弹窗和错误','hub-web/src/views/MessagesView.vue',422,457,'frontend'),
n('denied','recover',2,'拒绝越权治理','非群主403；非自定义400','im/service/ImGroupService.java',398,407,'security'),
n('leave','recover',3,'普通成员退群','群主不得直接退出','im/service/ImGroupService.java',308,325),
n('dismiss','recover',4,'群主解散','通知变更前成员集合','im/service/ImGroupService.java',329,345),
],[e('create','members'),e('members','owner','管理时'),e('owner','change','允许'),e('change','event'),e('event','refresh','提交后','async'),e('owner','denied','不允许','error'),e('members','leave','成员退出','branch'),e('leave','refresh','刷新','return'),e('owner','dismiss','解散','branch'),e('dismiss','refresh','通知','async')],['create','members','owner','change','event','refresh'],['移除、退群、解散采用变更前收件人，确保退出方收到治理事件。','群主不能直接退群；提示涉及转让，但此链不宣称转让接口已经实现。'],'创建群及成员；群主校验后治理；Outbox通知并刷新成员与会话列表。','无效组织用户拒绝；非群主不能治理；群主退群被拒；移出后API成员校验拦截访问。','前端请求失败展示服务端错误并保留操作；事件遗漏由会话/详情重新加载恢复。')

flow('im-outbox','Outbox认领、失败重试与人工审计','待发事件轮询、过期SENDING回收、FAILED/DEAD重试、后台审计与指标。','后台提供事件列表、失败摘要、单条/批量重试和手动派发API；前端是否可见不替代后台权限结论。',[
n('pending','entry',0,'待发事件','PENDING与到期FAILED','im/websocket/ImEventOutboxRepository.java',43,70,'database'),
n('claim','core',1,'独立事务认领','SKIP LOCKED；置SENDING','im/websocket/ImWebSocketOutboxDispatcher.java',82,94),
n('socket','core',2,'投递用户连接','失败连接解绑并抛错','im/websocket/ImWebSocketSessionRegistry.java',64,90,'messagebus'),
n('sent','core',3,'标记SENT','无在线连接也可成功','im/websocket/ImWebSocketOutboxDispatcher.java',97,105,'database'),
n('metrics','entry',4,'指标与失败摘要','状态统计、延迟与积压','im/service/ImOutboxAdminService.java',95,118),
n('failed','recover',2,'FAILED或DEAD','固定延迟；达到次数终止','im/websocket/ImWebSocketOutboxDispatcher.java',109,119,'database'),
n('retry','recover',3,'管理员重试','只接受FAILED/DEAD','im/service/ImOutboxAdminService.java',141,163,'security'),
n('audit','recover',4,'记录治理审计','批量部分成功；派发结果','im/service/ImOutboxAdminService.java',166,199,'database'),
n('validate','recover',1,'校验原始载荷','类型/会话索引必须一致','im/service/ImOutboxAdminService.java',202,214,'security'),
],[e('pending','claim'),e('claim','socket'),e('socket','sent','无异常'),e('sent','metrics'),e('socket','failed','异常','error'),e('failed','retry','人工恢复','branch'),e('retry','validate','校验'),e('validate','pending','重置待发','return'),e('retry','audit'),e('audit','metrics')],['pending','claim','socket','sent','metrics'],['状态SENT仅表示本轮连接派发未抛异常，不证明每位员工收到或读过。','过期SENDING由下一轮认领；DEAD必须人工重试，重置与审计同事务。'],'定时或手工认领到期事件，发送后标SENT；后台可查看状态、失败分类与治理审计。','发送异常累加次数，FAILED延迟重试；达到上限DEAD；非法payload或不可重试状态409。','后台校验payload索引后一条/批量重置，自动轮询继续派发；陈旧SENDING可被重新认领。')

flow('agent-release','Agent草稿、DAG测试与ACL发布','Agent版本配置、工具绑定、DAG校验、测试签名、使用范围与发布启停。','admin-web管理Agent/版本/工作流；员工目录仅返回可用Agent；不能以管理端可配置推断员工已接所有能力。',[
n('draft','entry',0,'创建草稿版本','配置、提示词、DAG','agent/service/AgentService.java',293,329),
n('bind','core',1,'绑定工具与ACL','草稿绑定；可用范围','agent/service/AgentService.java',386,426,'security'),
n('validate','core',2,'校验DAG与知识库','起止、可达性、环路','agent/workflow/WorkflowValidator.java',31,90),
n('test','core',3,'试运行记录签名','到达END后保存签名','agent/service/AgentService.java',1757,1773),
n('gate','core',4,'发布门禁','DRAFT、ACL、测试签名','agent/service/AgentService.java',429,455,'security'),
n('available','entry',5,'员工可见可用','ACTIVE且use ACL通过','agent/service/AgentService.java',487,503),
n('invalid','recover',2,'校验失败','修改配置再验证','agent/service/AgentService.java',211,218,'security'),
n('autostamp','recover',1,'自动写入测试签名','内嵌工具或旧配置转DAG','agent/service/AgentService.java',319,357),
n('disabled','recover',5,'停用或弃用','弃用不可重新启用','agent/service/AgentService.java',458,484),
],[e('draft','bind'),e('bind','validate'),e('validate','test','合法'),e('test','gate','成功'),e('gate','available'),e('validate','invalid','不合法','error'),e('invalid','draft','修改','return'),e('draft','autostamp','特定配置','branch'),e('autostamp','gate','已有签名'),e('available','disabled','管理操作','branch')],['draft','bind','validate','test','gate','available'],['实际存在自动写测试签名路径；签名存在不一定证明模型/工具试运行成功。','发布会再次验证DAG和知识引用；权限use与visible不是同一概念。'],'草稿配置工具和可用范围，DAG验证与试运行后签名匹配才正常发布；员工使用再查ACTIVE/use ACL。','草稿外修改/重复发布409；无范围400；测试签名不符400；DAG循环/孤立节点拒绝。','修正草稿并重测后发布；停用可以重新启用，弃用不可恢复。自动签名门禁问题见记录。')

flow('agent-tools','工具任务、人审确认与超时取消','显式工具调用的人审、恢复执行、拒绝取消、超时扫描及DAG分支差异。','员工任务确认/取消API已实现；DAG APPROVAL是提示节点，不代表持久人审暂停。',[
n('request','entry',0,'员工创建任务','校验已发布与use ACL','agent/service/AgentService.java',518,535),
n('mode','core',1,'计算审批模式','仅显式toolCalls参与','agent/service/AgentService.java',531,552,'security'),
n('waiting','core',2,'等待人工确认','24小时；工具日志待确认','agent/service/AgentService.java',560,573,'database'),
n('confirm','core',3,'所属员工确认','锁任务；检查状态与期限','agent/service/AgentService.java',605,627,'security'),
n('execute','core',4,'调用工具写结果','成功或BizException失败','agent/service/AgentService.java',625,635),
n('done','entry',5,'任务终态和审计','SUCCEEDED或FAILED','agent/service/AgentService.java',629,636,'database'),
n('cancel','recover',3,'拒绝或主动取消','REJECTED或CANCELLED','agent/service/AgentService.java',608,649),
n('timeout','recover',2,'超时扫描取消','分布式锁；条件更新','agent/scheduler/AgentTaskTimeoutJobService.java',117,191),
n('dag','recover',1,'DAG直接执行工具','未检查REQUIRE_CONFIRM','agent/workflow/WorkflowExecutor.java',161,184),
],[e('request','mode'),e('mode','waiting','需确认'),e('waiting','confirm','未过期'),e('confirm','execute','同意'),e('execute','done'),e('mode','execute','AUTO'),e('confirm','cancel','拒绝','branch'),e('waiting','cancel','主动取消','branch'),e('waiting','timeout','到期','async'),e('mode','dag','DAG分支','branch'),e('dag','done','直接执行')],['request','mode','waiting','confirm','execute','done'],['显式工具调用有持久化人审；DAG工具路径没有等价审批门禁。','超时扫描只取消WAITING_HUMAN_CONFIRM，不恢复PENDING/RUNNING。'],'显式工具需确认时先落等待任务，所属员工确认后执行并记审计；AUTO直接执行。','非归属用户403；非等待状态/过期409；CONNECTOR执行器501；工具业务异常记录FAILED。','可拒绝/取消等待任务；超时扫描原子取消并记录作业，WF_REQUEST回调业务恢复由审批链负责。')

flow('agent-conversation','会话触发Agent、异步执行与结果回写','提交后的Agent任务创建、进程内调度、DAG/模型执行、结果写回IM与失败卡片。','employee-web与hub-web展示Agent会话；后端只对绑定Agent的USER TEXT触发，附件纯FILE不触发。',[
n('usertext','entry',0,'绑定Agent的文本','USER且TEXT触发','im/service/ImAgentConversationService.java',165,185),
n('task','core',1,'新事务创建任务','持久PENDING和审计','agent/service/AgentTaskExecutionService.java',76,108,'database'),
n('schedule','core',2,'提交后内存调度','Executor执行回调','agent/service/AgentTaskExecutionService.java',111,123,'messagebus'),
n('run','core',3,'DAG或单轮模型','短事务置RUNNING后调用','agent/service/AgentTaskExecutionService.java',127,184),
n('writeback','core',4,'成功结果同事务回写','任务成功+IM消息','agent/service/AgentTaskExecutionService.java',187,200,'database'),
n('reply','entry',5,'Agent消息与事件','task幂等键；更新seq','im/service/ImAgentConversationService.java',109,161,'messagebus'),
n('failed','recover',3,'失败任务与系统消息','失败回写异常只记日志','agent/service/AgentTaskExecutionService.java',207,225),
n('triggerfail','recover',1,'触发失败系统消息','助手不可用或创建失败','im/service/ImAgentConversationService.java',188,214),
n('recoverygap','recover',2,'运行恢复缺口','超时扫描仅等待确认','agent/repository/AgentTaskTimeoutRepository.java',28,38,'database'),
],[e('usertext','task','消息提交后','async'),e('task','schedule'),e('schedule','run','进程内','async'),e('run','writeback','成功'),e('writeback','reply'),e('run','failed','异常','error'),e('failed','reply','失败消息'),e('usertext','triggerfail','触发异常','error'),e('schedule','recoverygap','进程中断','error')],['usertext','task','schedule','run','writeback','reply'],['任务表持久化不等于调度持久化；未找到PENDING/RUNNING扫描恢复入口。','回复按agent-task幂等；请求idempotencyKey不在此图中宣称已经持久去重。'],'用户文本提交后创建任务，异步置RUNNING并执行；成功事务内更新任务、插入Agent回复及消息事件。','触发异常写系统提示；模型异常记FAILED并尝试写失败提示；失败提示写回再次失败仅日志。','重复结果回调按任务消息键去重；用户可重发文本形成新任务。进程中断自动续跑/失败回写补偿为待完善项。')

flow('knowledge-documents','知识空间ACL与文档入库生命周期','空间创建/模型维度、ACL保存、附件校验、异步入库、重入库与软删除。','admin-web知识库具备空间、文档、ACL与检索测试入口；ACL保存不代表所有读取链已执行ACL。',[
n('space','entry',0,'创建空间并授权','探测Embedding维度','ragflow/service/RagflowAdminService.java',134,173),
n('attach','core',1,'校验知识库附件','归属、删除状态、重复','ragflow/service/RagflowAdminService.java',214,245,'security'),
n('job','core',2,'持久文档和入库作业','绑定latest_ingest_job','ragflow/service/RagflowAdminService.java',231,255,'database'),
n('parse','core',3,'提交后解析分块','OCR/Tika/UTF-8','ragflow/service/RagflowIngestService.java',64,83),
n('vectors','core',4,'写向量与切片清单','先PG后MySQL；非原子','ragflow/service/RagflowIngestService.java',79,84,'database'),
n('success','entry',5,'完成状态与计数','SUCCESS；刷新空间','ragflow/repository/RagflowRepository.java',352,367,'database'),
n('failed','recover',3,'FAILED与友好错误','原始异常保留服务日志','ragflow/service/RagflowIngestService.java',85,90),
n('reingest','recover',2,'人工重新入库','新作业替换latest指向','ragflow/service/RagflowAdminService.java',263,284),
n('delete','recover',4,'删除空间或文档','软删除并停用向量','ragflow/service/RagflowAdminService.java',309,339),
n('acl','recover',0,'保存空间ACL','ROLE/部门/岗位；read/manage','ragflow/service/RagflowAdminService.java',354,374,'security'),
],[e('space','attach'),e('attach','job'),e('job','parse','提交后','async'),e('parse','vectors','解析成功'),e('vectors','success'),e('parse','failed','解析/模型异常','error'),e('failed','reingest','修复后'),e('reingest','parse','新作业','async'),e('success','delete','删除','branch'),e('space','acl','授权','branch')],['space','attach','job','parse','vectors','success'],['入库作业先持久化，执行仍依赖提交后Executor，缺少重启恢复证据。','重入库未按latest作业代次隔离；删除期间已有作业仍可能后写向量。'],'创建空间并保存ACL，上传附件登记文档和作业；解析分块、嵌入、写PG向量和MySQL清单，更新成功。','附件归属403；无解析内容422；向量/模型故障FAILED；向量清理失败只日志、不回滚删除。','修复模型或文件后手工reingest新作业；删除软标记与检索标题过滤阻止已删文档返回，跨库残留需要清理补偿。')

flow('rag-qa','知识检索、提示编排与问答返回','Agent已授权工具检索知识空间，查询嵌入、向量阈值过滤、补标题并进入模型提示。','知识问答走Agent工具与IM卡片；管理端searchTest仅返回检索命中，不等同员工问答端到端验收。',[
n('agentuse','entry',0,'员工可用Agent','已发布ACTIVE与use ACL','agent/service/AgentService.java',494,503,'security'),
n('tool','core',1,'知识检索工具','绑定校验；spaceId/query','agent/service/AgentToolExecutionService.java',69,93),
n('config','core',2,'解析有效知识配置','空间覆盖或继承全局','ragflow/service/RagflowKnowledgeSearchService.java',44,50),
n('vector','core',3,'向量TopK检索','相似度阈值；active','ragflow/repository/RagflowVectorRepository.java',54,86,'database'),
n('title','core',4,'过滤与补文档标题','只保留存在的文档','ragflow/service/RagflowKnowledgeSearchService.java',54,60),
n('answer','entry',5,'模型生成问答回复','工具输出进入上下文','agent/workflow/WorkflowExecutor.java',219,253),
n('unavailable','recover',3,'检索不可用','向量未配置返回422','ragflow/repository/RagflowVectorRepository.java',107,110,'security'),
n('aclmissing','recover',1,'空间ACL未消费','操作者未传入检索服务','ragflow/service/RagflowKnowledgeSearchService.java',30,60,'security'),
n('adminsearch','recover',2,'管理员搜索测试','相同检索链；返回命中','ragflow/service/RagflowAdminService.java',342,351),
],[e('agentuse','tool'),e('tool','config'),e('config','vector','查询Embedding'),e('vector','title'),e('title','answer','命中上下文'),e('vector','unavailable','未配置','error'),e('tool','aclmissing','权限缺口','branch'),e('adminsearch','config','测试')],['agentuse','tool','config','vector','title','answer'],['Agent use ACL与知识空间read ACL不同；当前检索签名没有操作者参数。','空命中会传空工具结果，是否拒答由Agent提示配置决定；未见统一引用可信校验。'],'可用Agent调用已绑定知识工具，解析配置、嵌入query、TopK向量检索，补标题后进入模型上下文并回写消息。','向量不可用422；模型/检索异常按任务失败处理；已删文档经标题映射过滤。','管理员检索测试定位命中；修复模型与向量配置后再问。空间ACL执行缺口需统一服务端授权修复。')

flow('ai-provider-health','AI供应商模型配置、探活与自检','供应商与多类模型配置、列表发现、类型化探针、结果持久化及定时自检。','admin-web配置供应商和模型；配置响应使用Key是否已配置的标记，图中不包含真实凭证。',[
n('configure','entry',0,'配置供应商和模型','端点、类型、能力开关','agent/service/AgentService.java',1420,1455),
n('discover','core',1,'获取供应商模型','GET models与模型归类','agent/service/AiModelHealthCheckService.java',73,90),
n('probe','core',2,'选择探针类型','TEXT/VISION/OCR等','agent/service/AiModelHealthCheckService.java',97,127),
n('request','core',3,'发真实协议请求','Embedding/Rerank/TTS等','agent/service/AiModelHealthCheckService.java',130,240,'cloud'),
n('store','core',4,'保存最近检测结果','success/message/时间','agent/service/AgentService.java',1397,1417,'database'),
n('result','entry',5,'返回状态与耗时','成功/失败；友好消息','agent/service/AiModelHealthCheckService.java',439,460),
n('schedule','recover',1,'30分钟周期自检','供应商和模型分别处理','agent/scheduler/AiProviderHealthCheckJobService.java',28,45,'messagebus'),
n('skip','recover',2,'跳过停用记录','供应商disabled/模型非ACTIVE','agent/service/AgentService.java',1388,1407),
n('provider','recover',3,'供应商连通探针','GET models只证明可访问','agent/service/AiModelHealthCheckService.java',51,69),
],[e('configure','discover'),e('discover','probe','选择模型'),e('probe','request','配置齐全'),e('request','store','成功或失败'),e('store','result'),e('schedule','skip'),e('skip','probe','已启用'),e('skip','provider','供应商'),e('provider','store')],['configure','discover','probe','request','store','result'],['供应商GET models成功不保证聊天/嵌入功能可用，需对应模型探针。','本次仅静态追踪探针代码；未访问供应商、未读取Key、未执行外网模型请求。'],'配置模型，发现供应商模型列表，按能力执行真实协议探针，记录结果和耗时；周期自检复用此链。','端点/模型/Key未配置返回失败；远端异常转友好错误；供应商或模型自检异常分开捕获。','修改端点/模型设置后手动重测；定时自检只遍历启用记录。建议限制批量探活成本与并发并审计配置变更。')

(ROOT/'chains.json').write_text(json.dumps(chains,ensure_ascii=False,indent=2)+'\n')
print(f'created {len(chains)} candidates')
