# AGENTS.md — Workforce Hub 项目地图

## 项目核心信息

Workforce Hub 是一套面向中小企业、支持私有化部署的智能人事协同平台：Spring Boot 模块化单体 + Vue3 双前端，将 Agent（预审、RAG 问答、主动提醒）嵌入考勤、假期、审批、组织、消息等业务链路。

## 仓库结构

| 路径 | 用途 |
|------|------|
| `server/` | Spring Boot 3 后端（Java 17，Maven，模块化单体） |
| `admin-web/` | 管理后台前端（Vue3 + TS + Vite + Pinia） |
| `employee-web/` | 员工端前端（Vue3 + TS + Vite + Pinia，消息优先风格） |
| `shared-web/` | 双前端共享代码（HTTP 封装） |
| `docs/` | 架构、产品、API、部署、审查等全部文档 |
| `deploy/` | Nginx 配置与部署环境变量模板 |
| `docker-compose.yml` | 私有化交付编排（api/worker/mysql/pgvector/redis/minio） |
| `deliverables/` | 交付产物 |
| `prototypes/`、`ui-assets/` | 原型与设计资源 |
| 根目录 `*.png`、`*.log` | 审查截图与构建/测试日志，只作证据，不是源码 |

## 开发约束

- 遵守 `CLAUDE.md` 全部规则（极简代码、KISS/YAGNI、保留现有注释、禁止过度封装）。
- 禁止读取或输出任何密钥、凭证、`.env` 真实值；涉及时用占位符。
- 前端颜色必须使用 CSS 变量（token），禁止硬编码色值。
- admin-web 弹窗统一用组件（`AdminConfirmDialog` 等），禁止 `window.confirm`。
- 数据库变更只能通过 Flyway 迁移脚本，禁止手改已发布的迁移文件。
- 不引入新第三方依赖，除非现有依赖无法实现。
- 不修改 `docker-compose.yml` 服务拓扑，除非任务明确要求。

## 验收标准

任务完成前必须满足以下全部项，并贴出命令输出作为证据：

| 检查项 | 命令 | 通过判定 |
|--------|------|----------|
| 后端编译+测试 | `cd server && mvn test` | `BUILD SUCCESS`，0 failures |
| admin-web 测试 | `cd admin-web && npm run test` | 全部用例通过，0 failed |
| admin-web 构建 | `cd admin-web && npm run build` | 退出码 0，无 TS 错误 |
| employee-web 测试 | `cd employee-web && npm run test` | 全部用例通过，0 failed |
| employee-web 构建 | `cd employee-web && npm run build` | 退出码 0，无 TS 错误 |

- 只需运行与改动范围相关的检查项；跨端改动（如 `shared-web/`）必须全跑。
- UI 改动必须附浏览器实测截图（保存到根目录，命名 `<功能>-<端>.png`）。
- 证据要求：AI 必须粘贴测试命令的最终统计行（如 `Tests: 12 passed`），不允许只声称"已通过"。
