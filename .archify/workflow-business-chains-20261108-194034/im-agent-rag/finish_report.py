import json
from pathlib import Path
ROOT=Path(__file__).resolve().parent
BASE=ROOT.relative_to(Path.cwd()).as_posix()
J='server/src/main/java/com/workforcehub/'
chains=json.loads((ROOT/'chains.json').read_text())
status={
'im-delivery':'新入口/hub/messages已接文本/附件发送/引用/撤回；附件打开误用通用文件票据，将被IM_MESSAGE归属校验403；图中IM专用票据是可修复使用的后端能力。',
'im-groups':'employee-web新页支持改名/加成员/退群；独立hub-web另接公告、移除成员；旧MessagesSection治理入口不作为新页实现证据。',
'agent-conversation':'employee-web新/hub/messages与独立hub-web均展示Agent消息；employee-web新页知识卡/流程卡动作仅toast，后端回复成功不表示卡片动作可执行。',
'rag-qa':'员工问答通过Agent及IM显示；employee-web新页知识卡动作仅“即将上线”；管理端searchTest返回命中，不等同问答端到端实测。',
}
repairs=[]
for c in chains:
 if c['id'] not in status: continue
 p=ROOT/(c['id']+'.candidate.json');d=json.loads(p.read_text())
 c['frontend_status']=status[c['id']]
 d['cards'][0]['items'][1]=c['frontend_status']
 if c['id']=='im-delivery':
  node=next(n for n in d['nodes'] if n['id']=='compose')
  node['sources']=[{'path':'employee-web/src/hub/views/MessagesView.vue','line':188,'end_line':210},{'path':'employee-web/src/hub/views/MessagesView.vue','line':304,'end_line':335}]
  d['edges']=[e for e in d['edges'] if (e['from'],e['to']) not in [('recall','ticket'),('download','sync'),('recall','sync'),('persist','ticket')]]
  d['edges'].append({'from':'persist','to':'ticket','label':'专用API能力','role':'branch'})
  d['cards'][1]['items']=[x for x in d['cards'][1]['items'] if not x.startswith('新员工页')]
  d['cards'][1]['items'].append('新员工页误用通用文件票据，IM_MESSAGE引用被403；附件打开闭环尚未接通。')
  c['normal']='消息、附件引用、摘要与Outbox同事务保存；在线推送后前端合并消息，读后更新位点。IM专用票据已实现，新页附件打开误用通用票据而403。'
  c['exception']+=' 新员工入口附件点击使用通用票据，IM_MESSAGE引用返回403，见IAR-09。'
 if c['id']=='im-groups':
  d['nodes'][0]['sources'].append({'path':'employee-web/src/hub/views/MessagesView.vue','line':446,'end_line':475})
 p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n')
 c['receipt']=f'{BASE}/review-3-{c["id"]}/{c["id"]}.finalize-summary.json'
 repairs.append(c['id'])

def source(path,line):return {'path':J+path if path.endswith('.java') and not path.startswith('server/') else path,'line':line}
findings=[]
def f(id,severity,trigger,impact,sources,evidence,fix,acceptance):
 findings.append(dict(id=id,severity=severity,status='静态代码确认；未运行应用复现',trigger=trigger,impact=impact,sources=[source(*s) for s in sources],evidence=evidence,fix=fix,acceptance=acceptance))
f('IAR-01','P1','有Agent使用权限的员工通过已绑定KNOWLEDGE_SEARCH_CHUNKS查询其无read权限的知识空间。','Agent use授权不能替代知识空间ACL，PRIVATE/部门范围配置可能不约束该工具读链；实际返回取决于配置与文档内容。',[
('agent/controller/EmployeeAgentController.java',40),('agent/service/AgentService.java',518),('agent/service/AgentToolExecutionService.java',86),('ragflow/service/RagflowKnowledgeSearchService.java',44),('ragflow/repository/RagflowVectorRepository.java',60),('ragflow/service/RagflowAdminService.java',361)],
'Controller直接进入AgentService；上层只检查Agent use。工具对payload.userId作组织授权，但知识工具只传spaceId/query/topK；searchRows无操作者参数，向量SQL仅space_id、is_active和score。kb_space_acl仅在管理端保存/读取，不参与该读链。',
'在知识检索服务统一传入真实操作者，强制空间ACTIVE、visibility及read/manage ACL；管理员测试明确单独受控；文档检索与问答共用同一授权决策。',
'同一私有空间：空间所有者/已授权员工能命中，无read权限但有Agent use权限者收到403；跨部门、ACL撤销及停用空间同测；不得仅在UI隐藏。')
f('IAR-02','P2','DAG TOOL或节点toolCalls绑定REQUIRE_CONFIRM，员工创建任务时request.toolCalls为空，或由会话触发DAG。','配置要求的人审被跳过，工具日志直接标APPROVED；当前实现的SPI为查询且CONNECTOR返回501，因此不声称已有写业务被执行。',[
('agent/service/AgentService.java',531),('agent/service/AgentService.java',537),('agent/workflow/WorkflowExecutor.java',161),('agent/workflow/WorkflowExecutor.java',313),('agent/service/AgentToolExecutionService.java',50),('agent/workflow/WorkflowValidator.java',49)],
'人审计算仅遍历外部preparedToolCalls。DAG执行器检查绑定存在后直接调用execute，并记录APPROVED；执行器与validator未检查approvalMode。APPROVAL节点也被分派到executePromptNode，不是持久化人工等待。',
'将审批判断收口到工具执行边界；DAG遇需确认工具持久化节点/参数/版本快照，WAITING_HUMAN_CONFIRM后暂停，确认时从该节点恢复，未批准不得落APPROVED。',
'相同REQUIRE_CONFIRM绑定分别经显式工具、DAG TOOL、内嵌toolCalls、会话触发时均先等待；拒绝/超时不执行；重复确认只执行一次；AUTO保持直通。')
f('IAR-03','P2','创建含内嵌工具调用的DAG草稿，或将非DAG草稿更新为DAG，随后直接发布。','部分路径未实际试运行即获得workflow_test_signature/workflow_tested_at，弱化“当前版本完成一次流程测试”的发布门禁。',[
('agent/service/AgentService.java',319),('agent/service/AgentService.java',350),('agent/service/AgentService.java',444),('agent/repository/AgentRepository.java',105),('server/src/test/java/com/workforcehub/agent/ModuleAgentApiIntegrationTest.java',240)],
'createVersion与旧配置转DAG直接调用updateAgentVersionWorkflowTest；该方法写签名和NOW()。publish只比较签名。仓库测试契约明确“后台发布DAG Agent前必须完成一次当前版本流程测试”，但该测试覆盖simpleWorkflow，非自动签名例外。',
'只有成功试运行入口写测试签名/时间；创建、更新、工具绑定/关键引用变更均清空测试证明；兼容迁移标记与真实测试结果分字段。',
'两类自动签名分支在未试运行时发布400；失败试运行仍不能发布；成功且配置相同可发布；改配置或工具绑定后必须重测。')
f('IAR-04','P2','Agent或知识入库事务已提交，但进程在Executor投递前/执行中退出，或线程池拒绝任务。','任务/作业记录仍在表中，可停留PENDING/RUNNING；当前范围未发现自动重新认领/恢复入口，现有人工确认超时扫描不覆盖它们。',[
('agent/service/AgentTaskExecutionService.java',111),('agent/service/AgentTaskExecutionService.java',164),('agent/repository/AgentTaskTimeoutRepository.java',28),('ragflow/service/RagflowAdminService.java',287)],
'两类任务提交后仅调用进程内Executor。超时SQL限定WAITING_HUMAN_CONFIRM；ragflow目录中ingest调用仅scheduleIngest。记录持久化已存在，问题是执行投递/恢复，不是数据库任务丢失。',
'使用现有任务表实现带租约的待执行认领，保存可恢复输入及重试次数；入库作业亦以表状态调度，定义运行中超时和人工重试边界。',
'在提交后投递前及执行中分别终止可控测试worker；重启后同作业被恢复且消息/切片无重复；拒绝执行可查询、可重试且不永久显示处理中。')
f('IAR-05','P2','同一文档两次reingest并发，或删除与已运行ingest交错。','旧作业仍可覆盖新作业文档状态/清单，已删除文档仍可能新增向量；查询有已删标题过滤，不据此声称删除后必定可读。',[
('ragflow/service/RagflowAdminService.java',263),('ragflow/repository/RagflowRepository.java',308),('ragflow/repository/RagflowRepository.java',318),('ragflow/repository/RagflowRepository.java',334),('ragflow/repository/RagflowRepository.java',352),('ragflow/service/RagflowIngestService.java',79)],
'reingest每次创建新作业并改latest指向；运行/成功/失败UPDATE仅WHERE document.id，不约束latest_ingest_job_id或is_deleted；replaceChunks按document整删；PG replaceDocumentVectors亦按document先停旧向量。',
'按文档租约串行，或在发布结果时比较latest_ingest_job_id与未删除状态；先写作业独立代次，成功后原子切换有效代次，失效作业仅清理自己的结果。',
'控制旧作业晚于新作业完成，最终清单/向量/状态均来自新作业；删除后旧作业完成不得重新激活；重复回调幂等。')
f('IAR-06','P2','重入库写PG向量后MySQL清单失败，或向量插入中途失败。','向量与清单跨库非原子，旧向量先停用且部分新向量可能可见；MySQL作业FAILED不能保证检索仍使用完整旧版本。',[
('ragflow/repository/RagflowVectorRepository.java',35),('ragflow/service/RagflowIngestService.java',79),('ragflow/repository/RagflowRepository.java',334),('ragflow/repository/RagflowVectorRepository.java',60)],
'replaceDocumentVectors先UPDATE旧向量is_active=false，再逐条insert active新向量；完成后才调用MySQL replaceChunks/markIngestSuccess。search不连接MySQL作业成功状态，只按space与active。',
'按jobId写不可见新代次，完整写入后切换已发布代次；失败删除该job向量并保留旧代次，增加跨库对账修复。',
'在第N条向量插入与MySQL清单阶段分别注入失败，查询保持上一完整版本且不出现半批内容；重试成功后再切换，清理只影响失败job。')
f('IAR-07','P2','知识空间启用rerank并配置重排模型后进行搜索测试或Agent检索。','配置面存在重排选项，但当前有效配置及检索链未调用重排，排序仍为向量距离；属于功能闭环缺口。',[
('ragflow/service/RagflowAdminService.java',193),('ragflow/service/RagflowConfigResolver.java',10),('ragflow/service/RagflowKnowledgeSearchService.java',44),('ragflow/repository/RagflowVectorRepository.java',75)],
'空间更新保存rerankEnabled/provider/model/modelId；RagflowEffectiveConfig解析仅Embedding、chunk、topK与threshold，searchRows直接返回向量排序且未调用rerank客户端。',
'在TopN向量候选后接受控重排并返回实际生效标记，或在接通前禁止可保存的启用选项，避免配置与执行不一致。',
'启用后测试替身断言真实调用重排且返回重排顺序；停用无重排调用；重排超时有明确降级状态与结果，不静默假装生效。')
f('IAR-08','P2','用户在employee-web新入口点击知识卡/流程卡动作，或期待旧MessagesSection已有操作出现在新页。','卡片只显示toast而未执行动作；新旧页面和独立hub-web能力不一致，后台能力存在不能作为新入口交付证明。',[
('employee-web/src/router/index.ts',35),('employee-web/src/router/index.ts',60),('employee-web/src/hub/views/MessagesView.vue',339),('employee-web/src/hub/views/MessagesView.vue',446),('hub-web/src/views/MessagesView.vue',553)],
'新路由/hub/messages指向src/hub/views/MessagesView，旧/messages重定向。新页handleKnowledgeAction/handleProcessAction仅“即将上线”；新页群编辑只改名+加人+退群，独立hub-web已有移除成员API。',
'以新入口逐项登记能力，接通卡片动作的真实业务API/路由；未实现动作隐藏或禁用，避免只toast假闭环；群治理按当前产品要求补齐。',
'从默认入口操作卡片，观察对应业务请求/落库或正确路由；失败可恢复且有错误。新页公告/移除成员等是否交付单独列证，不以旧页测试替代。')
f('IAR-09','P2','在employee-web新/hub/messages上传并发送附件，再点击附件打开；从个人文件转发到消息也一样。','消息发送成功且展示附件，但打开走个人文件票据，被IM_MESSAGE归属限制拒绝403；属于下载契约错配，不是越权。',[
('employee-web/src/composables/usePersonalFileTransfer.ts',109),('employee-web/src/hub/views/MessagesView.vue',317),('im/repository/ImRepository.java',357),('im/service/ImService.java',824),('employee-web/src/stores/im.ts',697),('employee-web/src/hub/views/MessagesView.vue',329),('file/service/FileDownloadService.java',219)],
'上传ownerType=IM_MESSAGE_PENDING；发送时不论源ownerType均复制为IM_MESSAGE引用，消息响应从IM_MESSAGE列表返回新refId；前端保留此refId后交给employeeApi.files.createDownloadTicket。FileDownloadService仅允许PERSONAL_FILE/USER_AVATAR，因此该请求确定不匹配契约。',
'新页openAttachment使用会话ID、消息ID、附件refId调用IM专用下载票据API，并使用其downloadUrl；消息/群文件/个人文件分别使用对应入口。',
'上传发送后发送方和正常成员可打开；退出群/被移除、撤回、过期和已用票据均按IM规则拒绝。网络失败保留附件可重试。')

manifest={'chains':[{k:v for k,v in c.items() if k not in ('normal','exception','recovery')} for c in chains],'findings':findings}
(ROOT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(ROOT/'chains.json').write_text(json.dumps(chains,ensure_ascii=False,indent=2)+'\n')
(ROOT/'repairs.json').write_text(json.dumps(repairs)+'\n')
lines=['# IM / Outbox / Agent / RAG 业务链路','', '仓库快照：`38d829167042dc26efd7af502ce623c976d6e224`。9张原生Workflow图，schema v2 / zh-CN / repository local-only。','', '验证范围：源码静态追踪及Archify产物校验/真实浏览器自动检查。未启动应用、未执行后端/前端业务测试、未调用真实供应商、未读取凭证。下面的业务异常与风险是静态推导，不冒充线上复现。','', '源码工作树已有`docs/审查/说明.md`改动、`.archify/`和第99审记录；本代理仅写当前im-agent-rag目录，不触及业务源码或共享索引。','', '## 前端边界','', '默认消息入口是`employee-web/src/hub/views/MessagesView.vue`（/hub/messages）；`employee-web/src/views/sections/MessagesSection.vue`是旧section。`hub-web/src/views/MessagesView.vue`属于独立前端，群治理更完整。功能对照不能跨入口借用。','', '新入口已接文本、附件发送、引用、撤回/本人删除、改群名/加成员/退群；知识卡与流程卡动作只toast。附件打开误用通用文件API，IM_MESSAGE引用会被403；图中IM专用票据是后端已实现但新入口未正确调用的能力，见IAR-09。','']
for c in chains:
 lines.extend([f'## {c["title"]}', '',f'[流程图]({Path(c["html"]).name})', '',f'- 范围：{c["scope"]}',f'- 前端：{c["frontend_status"]}',f'- 正常：{c["normal"]}',f'- 异常：{c["exception"]}',f'- 恢复：{c["recovery"]}',''])
lines+=['## 问题与优化记录','', '第99审已记录的默认放行、陈旧会话、假期类型结算、固定时段与跨JVM ID仅引用，不重复立项。','']
for x in findings:
 lines.extend([f'### {x["id"]} · {x["severity"]}', '',f'- 状态：{x["status"]}',f'- 触发：{x["trigger"]}',f'- 影响：{x["impact"]}',f'- 证据：{x["evidence"]}',f'- 代码：'+ '；'.join(f'`{s["path"]}:{s["line"]}`' for s in x['sources']),f'- 修复：{x["fix"]}',f'- 验收：{x["acceptance"]}',''])
lines.extend(['## 后续收口顺序','','1. 先统一知识检索read授权，再补DAG工具人审及发布测试真实性。','2. 任务表租约调度与文档入库代次切换优先于新增AI功能。','3. 按新员工入口验收卡片与群治理；供应商探活和知识检索各用真实对应能力的探针。','','Outbox当前固定延迟重试且SENT不代表收件人确认；保留该语义，可按运行量补指数退避、抖动、告警与积压阈值。模型探活按模型类型有真实探针，建议补成本预算、限并发和配置变更审计；这些是优化建议，不计作已复现缺陷。',''])
(ROOT/'report.md').write_text('\n'.join(lines))
print(f'{len(chains)} chains; {len(findings)} findings; repairs={repairs}')
