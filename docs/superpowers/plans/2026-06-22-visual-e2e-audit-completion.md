# Visual E2E Audit Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐 workforce-hub 可视化点击测试、修复暴露问题、沉淀审查文件并提交当前未提交改动。

**Architecture:** 以现有 Spring Boot + Vue/Vite 双前端为准，不改业务边界。用 Playwright 驱动浏览器点击并保存截图，审查文档只记录当前证据、问题、修复和验证命令。

**Tech Stack:** Spring Boot, Maven, Vue 3, Vite, Vitest, Playwright, Chrome。

---

### Task 1: 覆盖矩阵与审查文档

**Files:**
- Create: `docs/审查/第96审_可视化全链路补测与未提交改动审查.md`

- [x] **Step 1: 读取当前路由、已有报告和 git 状态**

Run: `git status --short`

Expected: 看见 admin-web、employee-web、server 和 output 目录仍有未提交改动。

- [x] **Step 2: 写遗漏自检**

记录：
- 主请假链路已经补到 `PENDING_REVIEW -> Agent 预审 -> 领导点击审批 -> APPROVED`
- 假种创建/修改/删除已经补点击截图
- 管理端组织/人员/岗位等写操作已补独立点击证据
- 未提交改动已统一验证并提交

### Task 2: 补齐点击测试

**Files:**
- Create: `output/visual-admin-click-regression-2026-06-22/`

- [x] **Step 1: 启动本地服务**

Run:
```bash
python3 server/scripts/dev/start_agent_stub_server.py
cd server && mvn spring-boot:run
cd admin-web && npm run dev -- --port 5173
cd employee-web && npm run dev -- --port 5174
```

Expected: `18080`、`8080`、`5173`、`5174` 监听。

- [x] **Step 2: 初始化演示数据**

Run:
```bash
python3 server/scripts/dev/bootstrap_demo_data.py
```

Expected: demo 管理员、员工、领导、假种、模板、Agent 均可用。

- [x] **Step 3: 管理端点击写操作**

用 Playwright 点击验证：
- 假种创建、修改、删除
- 假期余额调整
- 部门筛选/编辑入口
- 岗位创建/修改/删除
- 人员创建入口和账号状态入口
- Agent 任务超时扫描
- 文件清理 dry-run
- 系统审计筛选
- RAGFlow 真实上传、重新入库、删除
- 工作流模板版本发布

- [x] **Step 4: 员工端点击写操作**

用 Playwright 点击验证：
- IM 文本发送
- 文件上传入口
- 请假提交、Agent 预审、领导审批
- 补卡提交、领导审批
- 设置页偏好切换
- 文件真实上传、预览、下载、删除

### Task 3: 修复问题

**Files:**
- Modify only files related to failures found by Task 2.

- [x] **Step 1: 失败先定位根因**

Run focused DOM/screenshot/API checks for the failing screen.

Expected: 每个问题能说明是前端选择器、后端接口、数据、样式还是权限问题。

- [x] **Step 2: 最小修复**

只改失败相关文件，不做额外重构。

### Task 4: 验证与提交

**Files:**
- Update: `docs/审查/第96审_可视化全链路补测与未提交改动审查.md`

- [x] **Step 1: 运行自动化验证**

Run:
```bash
cd admin-web && npm test && npm run build
cd employee-web && npm test && npm run build
cd server && mvn test
```

Expected: 命令退出码为 0。

- [x] **Step 2: 写最终审查结论**

记录实际截图目录、通过项、问题项、修复项、未覆盖限制。

- [x] **Step 3: 提交未提交改动**

Run:
```bash
git add .
git commit -m "test: complete visual e2e audit"
```

Expected: 生成新提交，工作区干净。
