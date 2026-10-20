# Workforce Hub — 智能人事协同平台

面向中小企业的私有化部署人事协同平台：**Spring Boot 模块化单体 + Vue3 双前端**，将 Agent（预审、RAG 问答、主动提醒）深度嵌入考勤、假期、审批、组织、消息等业务链路。

区别于只做信息记录的传统 HR 系统，这里的 Agent 不是单一的聊天机器人，而是**模型配置 + Prompt 模板 + 工具绑定 + 知识库绑定 + 触发器 + 权限审计**的组合平台：

- **审批预审**：Agent 作为审批流节点，硬规则违规自动驳回，低置信度转人工复核
- **RAG 知识问答**：会话中 @Agent，按授权空间检索知识库并结合上下文回答
- **主动提醒**：异常打卡、审批超时等关键节点定时推送通知

## 功能模块

| 模块 | 功能要点 |
|------|---------|
| 组织与人员 | 部门树、员工档案、上下级关系、角色权限、员工全生命周期状态机 |
| 考勤管理 | 打卡、考勤组、工作日历、班次、排班、异常计算 |
| 假期管理 | 年假/调休余额、发放/扣减/结转、有效期管理 |
| 申请与审批 | 请假/调休/加班/离职全流程，Flowable 引擎驱动 |
| 协同沟通 | 私聊、群组、组织群、业务群、@Agent |
| Agent 平台 | Agent 创建/发布/停用、工具绑定、知识库 RAG、多触发器、三线裁剪权限 |
| 文档中心 | 云盘文件夹、附件管理、知识库入库（pgvector 向量检索） |
| 统计报表 | 个人/团队考勤、请假、排班、异常分析 |
| 系统配置 | 打卡规则、审批模板、字典管理、Agent 模板、连接器管理 |

## 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.x、Spring Security（Session + Redis）、MyBatis、Flowable、Spring AI、WebSocket (STOMP)、Flyway |
| 管理后台 | Vue3 + TypeScript + Vite + Pinia |
| 员工端 | Vue3 + TypeScript + Vite + Pinia（消息优先风格） |
| 存储 | MySQL 8（主业务库）、PostgreSQL + pgvector（RAG 向量检索）、Redis（会话/缓存）、MinIO（对象存储） |

### 架构总览

```
employee-web ─┐
              ├─> Nginx ─> Spring Boot 模块化单体 ─> MySQL
admin-web   ──┘              │  ├─ auth/org/workflow/attendance/leave
                             │  ├─ im/contact/file/ragflow
                             │  └─ agent/plugin/system/common
                             ├─> PostgreSQL + pgvector（向量检索）
                             ├─> Redis（会话/缓存）
                             └─> MinIO（文件存储）
```

代码层面保持单体简单性，运行层面通过同一镜像不同启动参数分离角色：`SPRING_PROFILES_ACTIVE=api`（HTTP/WebSocket）与 `worker`（Agent 任务、知识入库、定时任务）。

## 快速开始

### Docker Compose 一键部署

```bash
cp deploy/deploy.env.example .env   # 填写数据库、MinIO 等占位值
docker compose up -d --build
```

服务组成：`web`（Nginx，默认 80）、`server`（8080）、`mysql`、`redis`、`minio`、`mailpit`。详细步骤见 [docs/部署与交付/部署与初始化.md](docs/部署与交付/部署与初始化.md)。

### 本地开发

```bash
cp .env.example .env                # 后端启动时自动读取根目录 .env

cd server && mvn spring-boot:run    # 后端，默认 8080

cd admin-web && npm install && npm run dev      # 管理后台
cd employee-web && npm install && npm run dev   # 员工端
```

> 环境变量只存占位值，真实密码与密钥一律放在本地 `.env`，不会提交到仓库。

## 文档

- [docs/说明.md](docs/说明.md) — 文档总索引（产品、架构、接口、部署、开发日志）
- [docs/部署与交付/部署与初始化.md](docs/部署与交付/部署与初始化.md) — 部署手册
- [PROJECT_INTRO.md](PROJECT_INTRO.md) — 完整项目介绍（创新点、竞品对比、部署方案）

## 许可证

本项目基于 [PolyForm Noncommercial 1.0.0](LICENSE.md) 许可证开源：

- ✅ 个人学习、课程作业、毕业设计、研究与非营利用途——可自由使用、修改、分发
- ❌ 商业使用（包括公司内部生产经营使用、销售、集成到商业产品）——需另行获得作者授权

选择该许可证的原因：它专为"可自由分享、禁止商用"的软件场景设计，条款比通用创作共用（CC BY-NC）协议更适合源代码。
