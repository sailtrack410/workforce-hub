# -*- coding: utf-8 -*-
import json
import hashlib
from pathlib import Path

b=Path('.archify/workflow-business-chains-20261108-194034/workflow-attendance')
m=json.loads((b/'manifest.json').read_text())
W='server/src/main/java/com/workforcehub/workflow/service/WorkflowService.java'
L='server/src/main/java/com/workforcehub/leave/service/LeaveService.java'
T='server/src/main/java/com/workforcehub/attendance/service/AttendanceService.java'
findings=[]
def f(id,severity,status,trigger,impact,sources,evidence,fix,acceptance):
    findings.append(dict(id=id,severity=severity,status=status,trigger=trigger,impact=impact,sources=[dict(path=p,line=l) for p,l in sources],evidence=evidence,fix=fix,acceptance=acceptance))
f('WF-01','P1','静态确认，入口未接通','员工在当前审批中心选择请假、费用、加班或用印新申请','仅提示表单即将上线，不调用提交接口；旧WorkbenchSection不能视作当前入口能力',[
 ('employee-web/src/router/index.ts',46),('employee-web/src/router/index.ts',78),('employee-web/src/hub/views/ApprovalsView.vue',232)],
 '当前路由使用hub/ApprovalsView；pickCreateType只有showToast，无表单提交。未点真实页面。',
 '在当前工作台和审批路由接入请假、补卡与通用OA表单、附件和提交后状态跳转。',
 '通过当前入口提交各类申请，每次只新增一条；余额不足、无上级、附件越权均显示真实错误。')
f('LV-01','P1','静态确认，未动态复现','余额调整已提交，但响应丢失或请求报错后管理员重试同一次调整','每次提交生成新的Date.now幂等key，服务端把重试当作新业务，可能重复调整',[
 ('admin-web/src/views/LeaveGovernanceView.vue',87),('admin-web/src/views/LeaveGovernanceView.vue',99),(L,104)],
 '后端按sourceTxnKey查询流水并核对指纹；前端每次submitAdjustment均生成新key。未模拟响应丢失。',
 '同一编辑意图保留稳定key，重试复用；成功确认或明确新建调整时再换key。',
 '服务端提交成功后丢弃首次响应，再重试，同一ADJUST流水只有一条且余额只变一次；同key不同参数拒绝。')
f('AT-01','P2','静态确认，展示可信性缺口','employee-web工作台当天workMinutes大于0或达到480分钟','页面显示固定08:57或18:00，可能与真实首末打卡时间不一致',[
 ('employee-web/src/hub/views/WorkbenchView.vue',85),('employee-web/src/hub/views/WorkbenchView.vue',86),('employee-web/src/hub/views/WorkbenchView.vue',88)],
 'punchIn按workMinutes真假选择08:57；punchOut按480阈值选择18:00。hub-web独立工作台则读取实际firstPunchAt/lastPunchAt。',
 '格式化日结果firstPunchAt/lastPunchAt；空值显示未打卡。',
 '输入实际09:12和18:37，分别显示09:12和18:37；只有时长不能凭空产生打卡时间。')
f('WF-02','P2','静态确认，操作资格不一致','未结束的PENDING_REVIEW申请在页面点击撤回','前端按!finishedAt显示撤回，后端只接受IN_APPROVAL，操作会被拒绝，冻结仍等待预审终态',[
 ('employee-web/src/hub/views/ApprovalsView.vue',367),(W,599)],
 '预审请求未finishedAt；withdrawRequest非IN_APPROVAL抛400。未动态提交撤销。',
 '按后端允许状态显示撤回；若产品要求预审可取消，补任务取消、状态锁和冻结释放。',
 '预审中不显示不可用操作；本人IN_APPROVAL可撤回一次并释放冻结；终态和非本人拒绝。')
f('WF-03','P2','静态确认，模板约束未执行','generic-requests提交JSON对象，但漏模板必填字段或类型错误','仅验证对象结构，未按模板formSchema做字段校验，错误业务数据可进入审批',[
 (W,266),(W,292),(W,924),('server/src/main/java/com/workforcehub/workflow/service/WorkflowAdminService.java',120)],
 '版本保存formSchemaJson；通用提交只检查isObject，然后直接压缩保存。未动态提交非法请求。',
 '按选定已发布版本校验必填、类型、长度和枚举，并返回字段错误。',
 '缺字段、错误类型、越界枚举返回400且无新增申请；正确请求可提交，版本更新不改变在途基准。')
f('AT-02','P2','静态确认，既有结果可能陈旧','当天已按默认班次生成日结果后，再首次创建当天个人排班','createSchedule只存排班，日结果维持旧班次，直到另一打卡、审批或重算触发',[
 (T,238),(T,252),(T,262),(T,285)],
 '创建直接返回；修改则显式检查日结果存在并重算。未动态创建排班。',
 '创建排班成功后，与修改路径一致地对已有日结果重算。',
 '默认班次已有结果，创建不同班次后，同一请求结束时shiftId、时长与异常立即更新。')
f('99-3','P1','既有问题，沿用第99审编号','冻结后停用、删除假种或更改balanceMode','终态重新读取当前有效假种/模式，可能阻断结算或跳过冻结处理',[
 (L,68),(L,85),(L,212),(L,262),(L,326)],
 '冻结、扣减、释放均重新getActiveLeaveTypeOrThrow且按当前NO_DEDUCT跳过；仅引用既有问题，非新发现。',
 '提交时固定结算策略，或依据原冻结流水驱动终态结算；停用不影响在途请求。',
 '冻结后改模式/状态，再批准、驳回或撤回，按原规则归零冻结且重复请求不二次结算。')
f('99-4','P2','既有问题，沿用第99审编号','非固定9-12/13-18班次、休息日或小于半天请假','固定工作区间和240分钟取整可能与真实排班不一致',[
 (W,904),(W,930),(W,942)],
 '逐日套固定工作时段，按HALF_DAY_MINUTES向上取整；仅引用第99审，不算新发现。',
 '请假计算统一消费有效日历、排班与假种计量规则。',
 '非标准班次、节假日、跨日和半天边界都与实际规则一致，冻结和扣减同基准。')
m['findings']=findings
for c in m['chains']:
    if c['id'] in ['att-punch','wf-generic-attachments']:
        c['receipt']=str(b/(c['id']+'.review-2')/(c['id']+'.finalize-summary.json'))
(b/'manifest.json').write_text(json.dumps(m,ensure_ascii=False,indent=2)+'\n')

descriptions={
 'wf-template':('管理端创建流程类型、模板和JSON草稿；发布时校验草稿、启用模板及Flowable定义，固化精确definitionId，归档旧版；新请求选择有效发布版。','非草稿、模板停用、Flowable定义不存在均拒绝；发布事务异常回滚；在途申请继续使用原template_version_id。','任意BPMN发布前建议验证任务结构。当前内置流程为单直属上级任务；多级流程不应仅换定义而不改待办推进。并发发布未专项验证，不直接认定缺陷。'),
 'wf-leave-ai':('API校验时段、时长、有效假种和余额；申请、明细、附件与冻结同事务。无AI直接人工；有任务则PENDING_REVIEW。','回调锁申请，核对状态、taskId和无实例；通过或转人工使用原模板创建直属上级待办；拒绝或超时释放冻结并REJECTED。异常抛错回滚。','WF-01、WF-02；沿用99-3、99-4。Agent内部任务执行归Agent链路，本图覆盖回调到业务状态。'),
 'wf-settlement':('核验IN_APPROVAL及Flowable assignee；同意请假则扣冻结，结束任务、申请和待办，按日期重算考勤，提交后发WorkflowCompletedEvent。','请假驳回释放冻结；本人IN_APPROVAL撤回时释放冻结、删除实例并关闭待办。历史无冻结流水有兼容逻辑；异常抛错回滚，未动态验证跨组件共事务。','终态事件当前来自人工通过/驳回公共路径；撤回与AI拒绝没有对应发布调用。若插件需要完整终态应补契约；未证明当前消费者要求，记为优化。'),
 'wf-correction':('API规范化缺失类型，校验时点同日和直属上级；固定模板启动人工审批；通过后插MANUAL打卡并重算。','无上级、日期错误、非处理人拒绝；驳回或撤回不写打卡。相同精确时点记录存在则不重复插入；不可打卡或写入异常会抛错回滚。','这是缺卡补入，没有更正已有打卡的承诺。同日同类型不同时间受V33唯一键限制，建议提前返回业务冲突；为边界/优化，不列已动态复现缺陷。'),
 'wf-generic-attachments':('通用表单对象保存JSON并绑定版本；本人个人附件经校验转为审批引用，可选AI后人工审批。参与人可申请十分钟单次票据，无须等待审批结束。','附件越权、删除或FAILED拒绝；发票据和执行下载均检查参与范围。执行再核验本人、ACTIVE、有效期和文件，认领票据后provider读对象并审计。','WF-03。扫描只拒绝FAILED，不等于只放行PASSED；票据认领后存储失败的恢复语义由文件链路核验，本次未模拟。'),
 'att-governance':('创建日历、同日班次和考勤组；用户行锁内增加不重叠成员区间；个人同日排班唯一；修改已有排班时重算已有日结果。','停用规则拒绝，成员区间重叠409；同日已有排班需修改原记录；修改锁排班及员工，异常事务回滚。','AT-02。日历保存名称/类型，但日结果核心无节假日/休息日判断分支；打卡窗口已保存，punch未使用。先明确业务约束，再补执行与验收，作为边界优化。'),
 'att-punch':('hub-web工作台真实OUT调用；以服务端Clock落记录，校验员工和同日类型排重；个人排班优先组默认班次，获批请假抵扣异常。','无有效考勤组写NO_RULE；请假全覆盖写LEAVE；否则计算NORMAL/ABNORMAL。异常按类型删除、更新、新增，仅新增提交后发事件；唯一键防并发重复打卡。','AT-01。hub-web独立attendance页Placeholder不能否定工作台真实打卡；employee-web工作台另有硬编码展示。未实际打卡、未改变进程。'),
 'leave-governance':('管理员假种CRUD；调整校验有效员工/假种及非零分钟；重复key核对指纹返回，新增key锁账户、非负检查后写ADJUST流水。','同key不同参数拒绝，余额不足拒绝；调整仅改可用余额不改冻结；工作流冻结、扣减、释放使用各自请求级幂等key。','LV-01及既有99-3。后端幂等无法自动识别两个不同key属于同一次人工操作，需要前端稳定业务key。')
}
lines=['# 审批、假期与考勤业务链路记录','',
 '证据基准：`38d829167042dc26efd7af502ce623c976d6e224`；仓库身份`https://github.com/sailtrack410/workforce-hub.git`；源码链接local-only。','',
 '本记录为静态源码追踪。未启动/停止应用，未执行真实数据库、Flowable、AI、存储或业务接口；8张原生流程图通过Archify自动校验及浏览器检查，未做截图感知审阅。未修改业务源码、迁移或共享审查索引；工作区已有docs审查目录改动不属于本子任务。','',
 '## 当前前端入口','',
 '- admin-web `/workflow`、`/leave`、`/leave-types`、`/attendance`指向真实治理页面。',
 '- employee-web唯一工作台/审批入口为`/hub/workbench`、`/hub/approvals`；旧workbench重定向，新建申请仅toast；旧WorkbenchSection代码不证明当前入口可提交。',
 '- employee-web当前审批中心同意、驳回、撤回调用真实API。',
 '- hub-web是独立前端：审批/考勤独立页Placeholder，工作台下班OUT真实调用API。','']
for c in m['chains']:
    normal,error,opt=descriptions[c['id']]
    cand=json.loads(Path(c['candidate']).read_text())
    lines += ['## '+c['title'],'',
      '[流程图]('+Path(c['html']).name+') · [候选]('+Path(c['candidate']).name+') · [自动检查回执]('+str(Path(c['receipt']).relative_to(b))+')','',
      '- 范围：'+c['scope']+'。','- 当前入口：'+c['frontend_status']+'。',
      '- 正常：'+normal,'- 异常/补偿：'+error,'- 问题/优化：'+opt,
      '- 源码：'+'；'.join('`'+node['sources'][0]['path']+':'+str(node['sources'][0]['line'])+'`（'+node['label']+'）' for node in cand['nodes'])+'。','']
lines += ['## 问题与验收','']
for issue in findings:
    lines += ['### '+issue['id']+' · '+issue['severity']+' · '+issue['status'],'',
      '- 触发：'+issue['trigger'],'- 影响：'+issue['impact'],'- 证据：'+issue['evidence'],
      '- 位置：'+'；'.join('`'+s['path']+':'+str(s['line'])+'`' for s in issue['sources']),
      '- 修复：'+issue['fix'],'- 验收：'+issue['acceptance'],'']
lines += ['## 交付检查','',
 '命令：`node /Users/sailtrack/.agents/skills/archify/bin/archify.mjs finalize workflow <candidate> <html> --repo-root /Users/sailtrack/工程文件/java_web/workforce-hub --quality showcase --json`。已有浏览器证据的后续候选使用独立`--out-dir`。','',
 '首次同目录并发触发`delivery/concurrent-attempt`，改为串行后全部完成。通用OA首版mainPath回列触发validate失败，局部调整授权顺序与主路径后通过；打卡全请假分支改由规则解析分叉，保留review-2证据。','',
 '`Workflow artifacts: 8 passed, 0 failed`；最终回执均`validate=pass, deliver=pass, check=pass, browser-check=pass`，执行exit 0。没有业务代码修改，未运行业务项目全量测试。','']
(b/'report.md').write_text('\n'.join(lines))
verified=[]
for c in m['chains']:
    cb=Path(c['candidate']).read_bytes()
    hb=Path(c['html']).read_bytes()
    receipt=json.loads(Path(c['receipt']).read_text())
    assert receipt['ok'] and all(v=='pass' for v in receipt['gates'].values())
    assert receipt['artifact']['sha256']==hashlib.sha256(hb).hexdigest()
    assert receipt['specification']['sha256']==hashlib.sha256(cb).hexdigest()
    verified.append(dict(id=c['id'],candidate=str(Path(c['candidate']).resolve()),html=str(Path(c['html']).resolve()),receipt=str(Path(c['receipt']).resolve()),gates=receipt['gates']))
(b/'artifact-verification.json').write_text(json.dumps(dict(verified=verified,total=8,failed=0),ensure_ascii=False,indent=2)+'\n')
assert len(json.loads((b/'manifest.json').read_text())['chains'])==8
assert '问题与验收' in (b/'report.md').read_text()
print('Workflow artifacts: 8 passed, 0 failed')
print('Findings: 6 new static observations, 2 existing issue references')
print((b/'manifest.json').resolve())
print((b/'report.md').resolve())
