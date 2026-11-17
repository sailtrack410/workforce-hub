import json,pathlib
root=pathlib.Path('.archify/workflow-business-chains-20261108-194034/auth-org')
base='server/src/main/java/com/workforcehub/'
def src(p,l,e=None): return {'path':p if not p.endswith('.java') else base+p,'line':l,**({'end_line':e} if e else {})}
def n(id,lane,col,label,sub,p,l,e=None,t='backend'):return {'id':id,'lane':lane,'col':col,'type':t,'label':label,'sublabel':sub,'sources':[src(p,l,e)]}
def edge(a,b,label='',role='main'):return {'from':a,'to':b,**({'label':label} if label else {}),'role':role}
chains=[]
def add(id,title,scope,front,lanes,nodes,edges,main,cards):
 out=str(root/(id+'.html')); c={'schema_version':2,'diagram_type':'workflow','meta':{'title':title,'locale':'zh-CN','quality_profile':'showcase','output':out,'repository':{'url':'https://github.com/sailtrack410/workforce-hub.git','revision':'38d829167042dc26efd7af502ce623c976d6e224','link_mode':'local-only'}},'lanes':[{'id':a,'label':b} for a,b in lanes],'nodes':nodes,'edges':edges,'mainPath':main,'cards':[{'dot':color,'title':title,'items':items} for color,title,items in cards]}
 (root/(id+'.json')).write_text(json.dumps(c,ensure_ascii=False,indent=2))
 chains.append({'id':id,'title':title,'scope':scope,'frontend_status':front,'html':out,'candidate':str(root/(id+'.json')),'receipt':str(root/(id+'.finalize-summary.json')),'summary':cards[0][2][0]})
add('auth-login','登录、失败锁定与会话退出','管理员/员工密码认证、邮箱门禁、Redis锁定、Session轮换、退出','admin-web 实接；employee-web 当前新登录页缺绑定分支；独立hub-web未处理二验', [('entry','前端入口'),('auth','认证与门禁'),('state','会话与终态')],[
n('form','entry',0,'提交登录','管理端／员工端','auth/service/LoginService.java',78,86,t='frontend'),
n('lock','auth',0,'检查失败锁定','按输入登录名计数','auth/service/AuthLockService.java',39,54,t='security'),
n('provider','auth',1,'账号密码认证','状态与密码校验','auth/service/LoginService.java',120,156,t='security'),
n('mail','auth',2,'员工邮箱门禁','启用时要求二验或绑定','auth/service/LoginService.java',160,199,t='security'),
n('pending','entry',2,'等待邮箱资料','不签发会话','auth/service/LoginService.java',165,196,t='frontend'),
n('session','state',3,'轮换会话','废旧Session写认证上下文','auth/service/LoginService.java',202,220,t='database'),
n('me','entry',4,'加载当前账号','管理端补全权限','admin-web/src/stores/session.ts',55,69,t='frontend'),
n('logout','state',5,'退出并清上下文','Session失效','auth/service/LoginService.java',254,260,t='security'),
n('failed','state',1,'失败累计与拒绝','五次后锁定窗口','auth/service/AuthLockService.java',15,25,t='security')],
[edge('form','lock'),edge('lock','provider','未锁定'),edge('provider','mail','凭证通过'),edge('provider','failed','凭证失败','error'),edge('mail','pending','资料缺失','branch'),edge('mail','session','验证完成'),edge('session','me'),edge('me','logout')],['form','lock','provider','mail','session','me','logout'],[('cyan','实际行为',['管理员不走员工邮箱二验；员工已绑邮箱用LOGIN码，未绑邮箱用BIND码。','旧会话销毁后创建新会话；last_login_at更新失败不阻断登录。']),('rose','缺口与既有问题',['AUTH-01：当前employee新页漏处理requiresEmailBinding；独立hub-web漏处理两种待验证响应。','99-2：停用／撤权后已签发Session的失效问题沿用既有记录。'])])
add('auth-email','邮箱绑定与找回密码','验证码发信、用途隔离、绑定唯一性、密码重置与当前入口错误','后端能力齐；employee-web当前找回页错误发送LOGIN码；旧composable发送RESET_PASSWORD码', [('ui','当前员工端与后端入口'),('code','邮件与验证码'),('account','账号写入')],[
n('resetui','ui',0,'找回页发送验证码','当前错误调用登录发码','employee-web/src/hub/views/AuthView.vue',458,474,t='frontend'),
n('send','code',1,'发出LOGIN码','登录名／邮箱解析','auth/service/LoginService.java',93,107),
n('redis','code',2,'按用途存储','五分钟码／六十秒重发锁','auth/service/EmailCodeService.java',54,81,t='database'),
n('smtp','code',3,'SMTP发送邮件','失败清验证码与锁','auth/service/EmailCodeService.java',73,84,t='external'),
n('submit','ui',3,'提交密码重置','邮箱＋码＋新密码','employee-web/src/hub/views/AuthView.vue',485,501,t='frontend'),
n('reset','account',4,'验证重置用途','仅接受RESET_PASSWORD','auth/service/AccountService.java',121,133,t='security'),
n('reject','account',5,'用途不匹配拒绝','未另发正确用途码时失败','auth/service/AccountService.java',121,128,t='security'),
n('bind','ui',0,'绑定邮箱请求','员工会话＋BIND码','auth/service/AccountService.java',89,108,t='frontend'),
n('unique','account',1,'核验绑定与唯一性','核销BIND码后查邮箱占用','auth/service/AccountService.java',94,113,t='security'),
n('write','account',2,'更新邮箱或密码','邮箱验证时间／密码时间','auth/service/AccountService.java',111,133,t='database')],
[edge('resetui','send'),edge('send','redis'),edge('redis','smtp'),edge('smtp','submit'),edge('submit','reset'),edge('reset','reject','LOGIN码','error'),edge('reset','write','正确用途码','branch'),edge('bind','unique'),edge('unique','write')],['resetui','send','redis','smtp','submit','reset','reject'],[('rose','当前入口断点',['AUTH-02：当前employee找回页发LOGIN码，重置接口验证RESET_PASSWORD，正常操作无法闭环。','旧useEmployeeAuthFlow调用sendPasswordResetCode，不能代替当前路由入口的证明。']),('amber','并发与体验',['AUTH-03：verifyCode采用GET后DELETE，成功核销不是原子操作；并发风险为静态推导。','绑定占用检查发生在验证码核销后；冲突时用户需要重新取码。'])])
add('auth-register','邀请注册与短信演示边界','公开发码、邀请码部门、注册事务、核销、回到登录','employee-web和独立hub-web注册均为占位；公开后端可直接调用；无管理端邀请码生成入口', [('ui','页面与公开入口'),('service','注册服务'),('store','注册事务与终态')],[
n('page','ui',0,'注册页占位','当前页面不调用注册API','employee-web/src/hub/views/AuthView.vue',410,443,t='frontend'),
n('public','ui',1,'公开注册API','发短信码与提交注册','auth/controller/PublicRegistrationController.java',35,50,t='external'),
n('sms','service',1,'生成短信码','六位码哈希入库十分钟','auth/service/EmployeeRegistrationService.java',39,48),
n('demo','store',2,'短信仅写日志','未调用真实短信网关','auth/service/EmployeeRegistrationService.java',89,92,t='external'),
n('validate','service',2,'校验注册输入','手机号／密码／短信码','auth/service/EmployeeRegistrationService.java',53,69,t='security'),
n('invite','service',3,'查询有效邀请码','部门来自未核销邀请码','auth/repository/AuthRegistrationRepository.java',66,77,t='security'),
n('create','store',3,'事务创建员工账号','PROBATION＋ACTIVE账号','auth/repository/AuthRegistrationRepository.java',88,101,t='database'),
n('consume','store',4,'核销短信与邀请码','邀请码更新未检查行数','auth/repository/AuthRegistrationRepository.java',42,85,t='database'),
n('return','store',5,'返回账号标识','无Session，需另行登录','auth/service/EmployeeRegistrationService.java',73,80)],
[edge('page','public','页面尚未接入','branch'),edge('public','sms','发码'),edge('sms','demo'),edge('public','validate','提交'),edge('validate','invite'),edge('invite','create'),edge('create','consume'),edge('consume','return')],['public','validate','invite','create','consume','return'],[('amber','实现边界',['短信为SMS-DEMO，前端注册没有真正提交；注册成功返回userId和loginName，不自动登录。','邀请码只有消费侧，生成管理接口缺失；不把文档期望当实际能力。']),('rose','并发与发送问题',['AUTH-04：并发使用同一邀请码时核销影响行数未检查，可能两份账号均提交。','AUTH-05：公开短信发码未见频控，且演示日志包含明文验证码；均为代码事实／风险推导。'])])
add('org-lifecycle','组织维护、员工入转离与账号停用','部门树路径、岗位、员工档案、状态机、账号禁用及领域事件','admin-web 部门/岗位/员工实接；employee-web查询组织；独立hub-web查询组织档案', [('admin','管理入口'),('org','组织与员工事务'),('out','记录、事件与登录')],[
n('manage','admin',0,'部门岗位员工入口','管理端权限路由','admin-web/src/router/index.ts',54,56,t='frontend'),
n('dept','org',0,'维护部门树','树路径级联与负责人同步','org/service/OrgManagementService.java',140,184),
n('delete','out',1,'删除部门门禁','有子部门／员工则拒绝','org/service/OrgManagementService.java',188,209,t='security'),
n('create','org',1,'创建员工档案','关联部门岗位上级校验','org/service/OrgManagementService.java',260,296,t='database'),
n('edit','org',2,'调部门岗位上级','记录变更前后快照','org/service/OrgManagementService.java',300,357),
n('transition','org',3,'入转离状态迁移','按允许状态表＋行锁','org/service/OrgManagementService.java',362,379,t='security'),
n('disable','out',3,'不准入则禁用账号','账号改为DISABLED','org/service/OrgManagementService.java',767,783,t='database'),
n('event','out',4,'人事记录与领域事件','入职／离职／状态变更','org/service/OrgManagementService.java',386,396,t='messagebus'),
n('login','out',5,'下次登录准入检查','生命周期＋账号状态','auth/security/EmployeeUserDetailsService.java',57,80,t='security'),
n('account','admin',4,'独立维护登录账号','启用须满足员工准入','org/service/OrgManagementService.java',401,439,t='security')],
[edge('manage','dept','部门'),edge('dept','delete','删除','branch'),edge('manage','create','员工'),edge('create','edit'),edge('edit','transition'),edge('transition','disable','不准入','branch'),edge('transition','event','迁移成功'),edge('disable','event'),edge('event','login'),edge('account','login')],['manage','create','edit','transition','event','login'],[('cyan','状态事实',['待入职→试用/在职→待离职→离职；待离职可回试用/在职，离职不可迁出。','员工档案停用或不再满足准入时自动禁用账号；账号恢复须管理端显式启用。']),('rose','会话边界',['新登录会执行状态准入，已发Session是否即时失效沿用99-2。','注册服务直接插入员工，未走OrgManagementService创建领域事件；下游影响需专项验证。'])])
add('auth-rbac','管理员角色配置与接口鉴权','角色CRUD、权限分配、管理员角色、超级管理员保留、Session权限','admin-web角色与权限页实接；员工端不具备后台RBAC管理入口', [('ui','管理端'),('rbac','角色配置事务'),('enforce','加载与鉴权')],[
n('roleui','ui',0,'角色与权限配置','ADMIN_RBAC_MANAGE','admin-web/src/router/index.ts',52,62,t='frontend'),
n('role','rbac',1,'保存角色','编码唯一／状态校验','auth/service/RbacService.java',68,106,t='database'),
n('permission','rbac',2,'替换角色权限','校验ID后删旧插新','auth/service/RbacService.java',109,123,t='database'),
n('assign','ui',2,'分配管理员角色','管理端角色API','admin-web/src/api/admin.ts',190,196,t='frontend'),
n('guard','rbac',3,'保留活跃超级管理员','拒改内置超级角色','auth/service/RbacService.java',269,307,t='security'),
n('save','rbac',4,'替换管理员角色','同事务写关联表','auth/service/RbacService.java',149,163,t='database'),
n('load','enforce',4,'登录加载权限快照','角色与权限进UserDetails','auth/security/AdminUserDetailsService.java',28,44,t='security'),
n('filter','enforce',5,'后台请求权限匹配','匹配路径使用认证权限','auth/security/AdminPermissionFilter.java',66,92,t='security')],
[edge('roleui','role'),edge('role','permission'),edge('permission','assign'),edge('assign','guard'),edge('guard','save'),edge('save','load','后续登录'),edge('load','filter')],['roleui','role','permission','assign','guard','save','load','filter'],[('cyan','操作边界',['权限和角色通过替换关联表更新；禁止修改内置超级角色，禁止移除最后活跃超级管理员。']),('rose','沿用既有记录',['99-1：未配置映射的后台路径默认放行。','99-2：Session持有权限快照，角色调整后即时失效未闭环。'])])
add('contacts-friends','通讯录好友申请、接受、备注与移除','员工好友列表、双向申请自动接受、双向ACTIVE、单方备注、双向删除','employee-web通讯录实接；独立hub-web API仅见备注更新，联系人主要源于组织', [('ui','员工通讯录'),('friend','关系服务'),('store','状态与展示')],[
n('list','ui',0,'展示好友与申请','当前员工ID隔离','contact/service/ContactFriendService.java',35,42,t='frontend'),
n('send','ui',1,'发送好友申请','联系人详情加好友','employee-web/src/views/sections/ContactsSection.vue',340,362,t='frontend'),
n('validate','friend',1,'校验目标与重复','拒绝自身／不存在／已申请','contact/service/ContactFriendService.java',46,61,t='security'),
n('incoming','friend',2,'检查反向待接受','反向PENDING则直接接受','contact/service/ContactFriendService.java',62,79),
n('pending','store',2,'创建单向PENDING','重复键转409','contact/service/ContactFriendService.java',66,79,t='database'),
n('acceptui','ui',3,'接受收到的申请','刷新申请与好友列表','employee-web/src/views/sections/ContactsSection.vue',366,377,t='frontend'),
n('active','store',3,'双向置ACTIVE','事务写两条方向关系','contact/service/ContactFriendService.java',83,103,t='database'),
n('remark','friend',4,'修改个人备注','仅允许ACTIVE关系','contact/service/ContactFriendService.java',114,121),
n('remove','store',5,'删除双向关系','也可移除待处理关系','contact/service/ContactFriendService.java',107,110,t='database')],
[edge('list','send'),edge('send','validate'),edge('validate','incoming'),edge('incoming','pending','无反向申请','branch'),edge('pending','acceptui'),edge('acceptui','active'),edge('incoming','active','有反向申请','branch'),edge('active','remark'),edge('remark','remove')],['list','send','validate','incoming','pending','acceptui','active','remark','remove'],[('cyan','关系语义',['接受好友申请后形成双向ACTIVE；备注只更新本人方向；移除同时删除两个方向。','没有独立拒绝接口；DELETE可删除待接受关系，但当前页面未提供明确拒绝操作。']),('amber','优化建议',['补发出申请状态与拒绝操作，避免用户只看到“已发送好友申请”冲突提示。','同时互加／接受的真实并发结果尚未验证，应以唯一键与事务竞争专项测试验收。'])])
add('system-dict-brand','数据字典维护与品牌配置发布','字典CRUD、标签缓存、审计、公开品牌读取、当前登录页接线','admin-web字典和企业品牌维护实接；当前employee与独立hub登录页使用固定内容', [('ui','管理端配置'),('service','字典与品牌服务'),('read','消费与审计')],[
n('edit','ui',0,'维护字典与品牌','品牌使用固定字典类型','admin-web/src/views/EnterpriseView.vue',15,17,t='frontend'),
n('save','ui',1,'提交条目保存','新增／编辑排序启用状态','admin-web/src/views/EnterpriseView.vue',82,98,t='frontend'),
n('write','service',2,'字典写入事务','创建更新删除','system/service/AdminDictService.java',51,75,t='database'),
n('cache','service',3,'失效本机标签缓存','按字典类型移除','system/service/AdminDictService.java',106,139),
n('audit','read',3,'追加审计记录','DICT_CREATE／UPDATE／DELETE','system/service/AdminDictService.java',93,107,t='database'),
n('brand','read',4,'公开读取启用标语','从sys_dict按序取标签','system/service/BrandingService.java',24,33),
n('fixed','read',5,'当前登录固定内容','新员工页未读取品牌接口','employee-web/src/hub/views/AuthView.vue',11,28,t='frontend')],
[edge('edit','save'),edge('save','write'),edge('write','cache'),edge('write','audit','记录','branch'),edge('cache','brand','后续读取'),edge('brand','fixed','当前页未接入','branch')],['edit','save','write','cache','brand'],[('cyan','实际消费边界',['品牌配置已落sys_dict并有公开读取服务；当前新员工登录页与独立hub页展示固定品牌内容。','旧MessagesView使用品牌接口的能力不能证明当前/login页面已消费。']),('amber','多实例优化',['labelCache是进程内ConcurrentMap，写后只失效当前实例；多JVM标签一致性需要验收。','99-3可变假期类型问题沿用；不把字典维护本身重复列为新问题。'])])
add('system-operations','站内通知、邮件测试、告警处理与审计','三条独立运维能力：通知读取、SMTP测试、告警聚合处置；共享审计查询','admin-web设置/审计实接；告警仅后端入口；独立hub有通知API，未证明消息页消费', [('notice','站内通知（独立能力）'),('mail','邮件配置测试（独立能力）'),('alert','运维告警与审计（独立能力）')],[
n('push','notice',0,'通知写入能力','push入口尚无业务调用','system/service/SysNotificationService.java',58,64),
n('read','notice',1,'按本人读取通知','列表／未读数','system/service/SysNotificationService.java',29,40,t='database'),
n('mark','notice',2,'单条或全部已读','条件更新read_flag','system/repository/SysNotificationRepository.java',42,57,t='database'),
n('config','mail',0,'保存邮件渠道','EMAIL_DEFAULT配置','system/service/NotificationChannelService.java',62,98,t='database'),
n('gate','mail',1,'测试收件人门禁','当前管理员邮箱＋三十秒','system/service/NotificationChannelService.java',101,110,t='security'),
n('send','mail',2,'使用提交参数发信','独立JavaMailSenderImpl','system/service/NotificationChannelService.java',172,193,t='external'),
n('result','mail',3,'记录测试结果','成功／失败及脱敏消息','system/service/NotificationChannelService.java',115,140,t='database'),
n('aggregate','alert',0,'聚合五类失败状态','按SQL计数＋规则查询','system/service/AdminAlertService.java',30,73),
n('rule','alert',1,'维护规则和渠道','保存通知开关和冷却配置','system/service/AdminAlertService.java',76,128,t='database'),
n('resolve','alert',2,'登记处置记录','不修改原始失败任务','system/service/AdminAlertService.java',132,157,t='database'),
n('audit','alert',4,'写sys_audit_log','渠道测试／告警处置','common/audit/AuditLogWriter.java',16,32,t='database'),
n('query','alert',5,'管理端审计查询','按过滤条件分页','system/repository/AdminSystemRepository.java',76,144,t='frontend')],
[edge('push','read','存在通知'),edge('read','mark'),edge('config','gate'),edge('gate','send'),edge('send','result'),edge('result','audit','写测试审计','branch'),edge('aggregate','rule','查询／维护'),edge('rule','resolve','操作员处置'),edge('resolve','audit'),edge('audit','query')],['aggregate','rule','resolve','audit','query'],[('cyan','独立能力与边界',['站内通知、邮件测试、告警聚合为独立流程，图中未连接为自动投递链。','站内通知push未发现业务调用；告警规则有通知配置字段，未发现实际调度投递。']),('rose','配置与处置缺口',['AUTH-06：管理端邮件渠道测试使用数据库／请求参数，认证码使用注入的JavaMailSender，两者未连通。','处置登记只追加ops_alert_resolution，不会恢复原任务；邮件与告警真实外部投递未验证。'])])
(root/'manifest.json').write_text(json.dumps({'chains':chains,'findings':[]},ensure_ascii=False,indent=2))
