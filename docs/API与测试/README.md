# API 与测试文档索引

本目录统一存放接口文档与测试用例，并按模块拆分维护。

当前清单：

- `module-auth/`
  - 认证接口 API 文档与测试用例
- `module-org/`
  - 组织模块查询接口、写侧接口 API 文档与测试用例
- `module-file/`
  - 文件中心第一版接口 API 文档与测试用例
- `module-workflow/`
  - 员工端审批接口 API 文档与测试用例
- `module-leave/`
  - 假期账户接口 API 文档与测试用例
- `module-attendance/`
  - 考勤基础配置、打卡、日结果与异常接口 API 文档与测试用例
- `module-im/`
  - 员工端 IM 会话、群治理、消息、WebSocket 推送和 IM x Agent 会话接口 API 文档与测试用例
- `module-agent/`
  - Agent 治理、授权、任务、工具调用与审计接口 API 文档和测试用例

维护约定：

- 新模块优先创建 `docs/API与测试/<module>/`
- API 文档命名建议为 `API文档_<module><主题>.md`
- 测试用例命名建议为 `API测试用例_<module><主题>.md`
- 系统级接口目录继续维护在 `docs/架构与设计/接口与事件清单.md`
