# workforce-hub 全链路可视化测试报告

时间：2026-06-22

## 结论

未全部通过。

上一轮没有完成全量测试。本轮已补测员工端、员工请假到领导审批、员工 Agent、管理端核心页面和深层功能。

## 已做可视化覆盖

- 员工端：登录、消息、私聊发送、Agent 会话、待办、请假申请、我的申请、考勤、假期账户、Agent 任务、文件、通讯录、个人中心、组织关系、账号安全、偏好设置、文件空间。
- 请假审批：员工提交请假、领导查看待办、领导审批、员工回看结果。
- 管理端：仪表盘、Agent 任务、Agent 配置、Agent DAG、DAG ACL、DAG 校验、DAG 草稿流程测试、流程治理、假期余额、假种、部门、岗位、人员、文件清理 Dry-run、RAGFlow 设置/上传/检索、系统角色/权限/审计、设置。

截图：本目录共 81 张 PNG。

## 关键证据

- 员工提交请假后进入领导审批：`18-leave-submit-result.png`、`19-leader-todos-after-submit.png`、`21-leader-approval-result.png`、`22-employee-request-after-leader-approval.png`
- 员工 Agent 任务：`42-employee-agent-task-before-submit.png`、`43-employee-agent-task-after-submit.png`
- 员工 Agent 会话：`44c-employee-agent-conversation-before-ui-send.png`、`44d-employee-agent-conversation-after-ui-send.png`
- 管理端 Agent DAG：`45-admin-agent-dag.png`、`46-admin-agent-dag-validate.png`、`47-admin-agent-dag-acl.png`、`49d-admin-agent-dag-draft-workflow-test-success.png`
- RAGFlow：`55-admin-ragflow-detail.png`、`57-admin-ragflow-upload-result.png`、`58-admin-ragflow-space-settings-acl.png`、`59-admin-ragflow-search-test.png`
- WebSocket 复核：`67-employee-websocket-recheck.png`

## 问题

- Agent 预审真实网页链路未生效：员工请假提交后直接进入 `IN_APPROVAL`，不是 `PENDING_REVIEW`，页面无 Agent 预审记录。原因：生产环境没有真实 `WorkflowAgentPreReviewTrigger` 实现；`WorkflowService.triggerAgentPreReview()` 在 provider 不存在时返回 `null`，直接进入人工审批。
- Agent 预审自动化通过的是测试桩，不是生产网页真实链路。测试桩在 `server/src/test/java/com/workforcehub/support/WorkflowAgentPreReviewTestConfiguration.java`。
- 后端回归存在失败：`ModuleAgentApiIntegrationTest` 6 失败 2 错误；`ModuleWorkflowFlowableIntegrationTest` 1 个文案断言失败。
- 管理端控制台存在组件解析警告：`AdminFilterBar` 在 `SystemGovernanceView`、`LeaveTypeView`、`PositionView` 未解析。
- 旧截图中出现过 WebSocket 403；本轮登录后直接 WebSocket 复核为 `open`，但登录前 `/api/employee/auth/me` 仍会返回 403。

## 通过项

- employee-web：重新执行 `npm test && npm run build`，19 个测试文件、86 个用例通过，构建通过。
- admin-web：重新执行 `npm test && npm run build`，3 个测试文件、13 个用例通过，构建通过。
- 管理端 DAG 草稿流程测试：接口和 UI 均验证成功。
- RAGFlow 上传、空间设置、检索测试可视化通过。
- 文件清理 Dry-run 可视化通过。

## 后端回归复测

重新执行：

```bash
env TEST_DB_NAME=workforce_hub_visual_full_rerun_$(date +%s) mvn -Dtest=ModuleWorkflowFlowableIntegrationTest,ModuleWorkflowAdminApiIntegrationTest,ModuleWorkflowAgentPreReviewIntegrationTest,ModuleAgentApiIntegrationTest,ModuleAgentTaskTimeoutIntegrationTest test
```

结果：68 个测试，7 failures，2 errors。

## 未完成

- 没有修复任何问题。
- 没有把 Agent 预审接入生产真实触发器。
- 没有修复后端 Agent API 回归失败。
