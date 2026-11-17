# coding: utf-8
import json,pathlib
r=pathlib.Path('.archify/workflow-business-chains-20261108-194034/auth-org');manifest=json.loads((r/'manifest.json').read_text());b='server/src/main/java/com/workforcehub/'
def n(id,lane,col,label,sub,path,line,end,t='backend'):
 return {'id':id,'lane':lane,'col':col,'label':label,'sublabel':sub,'type':t,'sources':[{'path':b+path if path.endswith('.java') else path,'line':line,'end_line':end}]}
def e(a,b,label=''):return {'from':a,'to':b,**({'label':label} if label else {})}
id='employee-personal';out=str(r/(id+'.html'));d={'schema_version':2,'diagram_type':'workflow','meta':{'title':'个人档案、头像、偏好、密码与退出','locale':'zh-CN','quality_profile':'showcase','output':out,'repository':{'url':'https://github.com/sailtrack410/workforce-hub.git','revision':'38d829167042dc26efd7af502ce623c976d6e224','link_mode':'local-only'}},'lanes':[{'id':'profile','label':'档案与个人资料'},{'id':'avatar','label':'头像与附件门禁'},{'id':'prefs','label':'偏好与密码（独立操作）'},{'id':'exit','label':'退出与会话清理'}],'nodes':[
n('entry','profile',0,'进入个人设置','姓名岗位组织只读','employee-web/src/router/index.ts',104,108,'frontend'),
n('mobile','profile',1,'提交手机号修改','保留当前头像引用','employee-web/src/views/sections/SettingsSection.vue',289,306,'frontend'),
n('write','profile',2,'更新本人档案','手机号非null才写入','org/service/OrgDirectoryService.java',195,213,'database'),
n('refresh','profile',3,'重载账号与档案','同步shell状态','employee-web/src/views/sections/SettingsSection.vue',300,305,'frontend'),
n('pick','avatar',1,'选择图片头像','图片类型前置检查','employee-web/src/views/sections/SettingsSection.vue',427,440,'frontend'),
n('upload','avatar',2,'上传头像附件','USER_AVATAR所有者','employee-web/src/composables/usePersonalFileTransfer.ts',101,108),
n('owner','avatar',3,'核验附件所有权','本人上传且附件未删除','org/service/OrgDirectoryService.java',216,234,'security'),
n('avatarwrite','avatar',4,'保存头像并刷新','updateProfile后重取URL','employee-web/src/views/sections/SettingsSection.vue',440,448,'database'),
n('pref','prefs',0,'读取与更改偏好','主题通知签名等','employee-web/src/views/sections/SettingsSection.vue',213,245,'frontend'),
n('prefsave','prefs',1,'按本人插入或更新','缺省值与字段裁剪','org/service/EmployeePreferenceService.java',50,115,'database'),
n('code','prefs',3,'发送改密邮件码','RESET_PASSWORD用途','employee-web/src/views/sections/SettingsSection.vue',380,408,'frontend'),
n('pwd','prefs',4,'核验并写密码','不退出已有Session','auth/service/AccountService.java',121,135,'security'),
n('confirm','exit',0,'确认退出登录','明确对话框确认','employee-web/src/views/sections/SettingsSection.vue',458,471,'frontend'),
n('invalidate','exit',1,'销毁会话上下文','后端Session.invalidate','auth/service/LoginService.java',254,260,'security'),
n('clear','exit',2,'清前端状态并重载','账号档案关系清空','employee-web/src/stores/session.ts',132,145,'frontend')],
'edges':[e('entry','mobile'),e('mobile','write'),e('write','refresh'),e('entry','pick','更换头像'),e('pick','upload'),e('upload','owner'),e('owner','avatarwrite'),e('avatarwrite','refresh'),e('pref','prefsave'),e('code','pwd'),e('confirm','invalidate'),e('invalidate','clear')],'mainPath':['entry','mobile','write','refresh'],'cards':[{'dot':'cyan','title':'可自管字段与独立操作','items':['个人资料仅自助修改手机号与头像；姓名岗位部门由管理端维护。','偏好、邮件验证码改密和退出是独立操作，不是自动串行执行。']},{'dot':'rose','title':'边界与优化','items':['AUTH-09：UI把空手机号发成null，后端null表示保持，清空动作未生效。','偏好读取失败仍允许写本地快照；后端失败被静默吞掉，应增加保存状态与重试。','密码重置后旧Session失效沿用99-2；本图没有实测应用或外部邮件。']}]}
(r/(id+'.json')).write_text(json.dumps(d,ensure_ascii=False,indent=2))
c={'id':id,'title':d['meta']['title'],'scope':'姓名组织只读、手机修改、头像上传、偏好同步、验证码改密、确认退出','frontend_status':'employee-web /settings实接；独立hub未发现同等个人设置页面，不混同旧壳与独立工程','html':out,'candidate':str(r/(id+'.json')),'receipt':str(r/(id+'.finalize-summary.json')),'summary':'个人自管字段仅手机号与头像；设置、偏好、密码、退出分别执行业务操作。'}
manifest['chains']=[x for x in manifest['chains'] if x['id']!=id]+[c];(r/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
