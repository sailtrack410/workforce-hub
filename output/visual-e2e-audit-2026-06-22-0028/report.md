# Workforce Hub 可视化全链路检测报告

检测时间：2026-06-22 00:28-00:58  
检测方式：浏览器可视化操作 + Playwright 断点截图 + 前端测试/构建  
代码处理：未修改业务代码

## 结论

核心桌面链路可用：员工登录、消息、工作台、请假申请、领导待办审批、员工侧回看、管理端审批治理均已跑通。

本次新建申请：

- 申请编号：WF-LEAVE-20260622003859-2068735444108177409
- 员工账号：demo.employee1
- 审批账号：demo.leader
- 最终状态：已通过
- 审批意见：可视化全链路审批通过

## 关键截图

- 员工端桌面主流程：01-07、19-23、30
- 管理端桌面主流程：08-18、32
- 领导审批链路：25-29
- 移动端员工链路：43-46、61-64
- 移动端管理端：47-48
- 平板端员工/管理端：49-52、57-58

## 已验证链路

1. 员工端登录：通过。
2. 员工消息页：通过。
3. 员工工作台：通过。
4. 员工请假申请提交：通过。
5. 领导登录与待办列表：通过。
6. 领导审批通过：通过。
7. 员工侧申请状态回看：通过。
8. 管理端审批治理回看：通过。
9. 管理端 Agent、组织、文件、系统等主导航页面：可访问。
10. 移动端与平板端主页面截图：已采集。

## 问题清单

- employee-web 移动端请假表单：390px 视口下请假申请与假期账户仍横向并排，右侧内容被截断；建议在移动端把 `.detail-grid` / `.content-stack` 改为单列。

- employee-web 平板端请假表单：768px 视口下右侧假期账户卡片仍被截断；建议 900px 以下统一单列。

- admin-web Agent 任务页：390px 移动端存在全页横向溢出，`documentElement.scrollWidth` 达 751；建议收敛 `.page-grid`、失败聚合面板和表格宽度。

- admin-web 审批治理页：移动端表格可读但列宽挤压，申请编号换行密集；建议小屏隐藏低优先级列或改卡片列表。

- admin-web 控制台警告：`LeaveTypeView.vue`、`SystemGovernanceView.vue` 使用 `<AdminFilterBar>` 但未 import，运行期 Vue warning；建议补充 import。

- employee-web 员工端无可见退出登录入口；代码有 `session.logout()` 和 API，但页面未暴露按钮；建议在个人中心/设置页增加退出入口。

- employee-web 维护风险：`employee-shell.css` 3653 行、`MessagesView.vue` 1595 行、`WorkbenchSection.vue` 564 行，单文件承担过多布局与业务状态；建议只按现有边界拆分明显独立的移动端布局和工作台表单。

- admin-web 维护风险：`AgentDagEditor.vue` 2694 行、`RagflowView.vue` 1208 行；建议优先拆出画布交互、节点表单、侧栏列表，避免继续堆叠。

## 验证命令

- employee-web：`npm test`，19 个测试文件、86 个用例通过。
- admin-web：`npm test`，3 个测试文件、13 个用例通过。
- employee-web：`npm run build` 通过。
- admin-web：`npm run build` 通过。

## 控制台记录

- 独立 Playwright 首次访问管理端时出现 `/api/admin/auth/me` 401，属于未登录前探测。
- `favicon.ico` 404 出现 2 次。
- in-app Browser 记录到 Vue warning：`AdminFilterBar` 未解析。

## 截图目录

`/Users/sailtrack/工程文件/java_web/workforce-hub/output/visual-e2e-audit-2026-06-22-0028`
