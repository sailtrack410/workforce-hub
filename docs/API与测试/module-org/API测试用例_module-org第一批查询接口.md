# API 测试用例 module-org 第一批查询接口

## 一、文档信息

| 项目 | 内容 |
| --- | --- |
| 文档名称 | `module-org` 第一批查询接口测试用例 |
| 文档版本 | `v1.1` |
| 适用范围 | 组织查询读侧接口联调、回归、冒烟、通讯录联系人摘要 |
| 发布日期 | `2026-05-02` |

---

## 二、测试目标

本轮测试目标：

- 验证后台端与员工端查询接口可正常返回
- 验证 Session 鉴权与路径隔离生效
- 验证分页参数和边界参数校验
- 验证直属上级 / 下级的空值与空数组语义
- 验证同部门同事列表返回通讯录展示字段
- 验证 `X-Request-Id` 透传能力

---

## 三、测试环境建议

| 项目 | 建议值 |
| --- | --- |
| 服务地址 | 默认 `http://localhost:8080`；若本机端口占用，按实际启动端口测试，例如 `http://localhost:8082` |
| JDK | `17` |
| 数据库 | 本地 MySQL `workforce_hub` |
| Redis | 已启动 |
| 会话模式 | Spring Session + Redis |

---

## 四、测试数据建议

建议至少准备以下测试数据：

### 4.0 推荐初始化方式

当前仓库已提供本地冒烟种子脚本：

- `server/scripts/sql/seed_module_org_smoke_data.sql`

建议先执行该脚本，再开展本轮接口测试。脚本会初始化本文档后续用例需要的部门、岗位、员工、员工账号和后台管理员账号。

### 4.0.1 本地演示账号

| 类型 | 登录名 | 密码 | 说明 |
| --- | --- | --- | --- |
| 后台管理员 | `admin.demo` | `Admin@123456` | 用于后台端接口联调 |
| 员工账号 A | `zhangsan.employee` | `Employee@123456` | `userId=2003`，普通员工 |
| 员工账号 B | `lisi.manager` | `Employee@123456` | `userId=2002`，直属领导 |
| 员工账号 C | `wangwu.employee` | `Employee@123456` | `userId=2004`，试用期员工 |

### 4.1 部门数据

| deptId | deptCode | deptName | parentId | 说明 |
| --- | --- | --- | --- | --- |
| `100` | `HQ` | 总部 | `null` | 根部门 |
| `110` | `RD` | 研发中心 | `100` | 一级部门 |
| `111` | `RD-BE` | 后端组 | `110` | 二级部门 |

### 4.2 岗位数据

| positionId | positionCode | positionName |
| --- | --- | --- |
| `3001` | `DEV_BACKEND` | 后端开发工程师 |
| `3002` | `DEV_MANAGER` | 研发经理 |

### 4.3 员工数据

| userId | employeeNo | realName | deptId | managerUserId | 角色说明 |
| --- | --- | --- | --- | --- | --- |
| `2002` | `E1002` | 李四 | `110` | `null` | 研发经理 |
| `2003` | `E1003` | 张三 | `111` | `2002` | 普通员工 |
| `2004` | `E1004` | 王五 | `111` | `2002` | 普通员工 |

### 4.4 账号数据

| 类型 | 用途 | 说明 |
| --- | --- | --- |
| 后台管理员账号 | 后台接口测试 | 任一可登录后台账号 |
| 员工账号 A | 普通员工测试 | 绑定 `userId=2003` |
| 员工账号 B | 领导员工测试 | 绑定 `userId=2002` |

---

## 五、测试覆盖策略

## 5.1 功能覆盖

- 组织树返回
- 岗位列表返回
- 员工分页查询
- 员工详情查询
- 上级查询
- 下级查询
- 员工本人查询

## 5.2 异常覆盖

- 未登录
- 端路径串用
- 参数越界
- 不存在对象

## 5.3 契约覆盖

- 统一响应结构
- `requestId` 回写
- `manager = null`
- `subordinates = []`

---

## 六、测试用例明细

## 6.1 后台端接口

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ORG-ADMIN-001` | P0 | `GET /api/admin/departments/tree` | 正常获取组织树 | 已登录后台管理员 | 无参数 | HTTP `200`；`code=0`；返回数组；根节点 `parentId=null`；每个节点包含 `children` |
| `ORG-ADMIN-002` | P0 | `GET /api/admin/positions` | 正常获取岗位列表 | 已登录后台管理员 | 无参数 | HTTP `200`；`code=0`；返回岗位数组 |
| `ORG-ADMIN-003` | P0 | `GET /api/admin/users` | 默认分页查询员工列表 | 已登录后台管理员，至少存在 2 名员工 | `pageNo=1&pageSize=20` | HTTP `200`；`code=0`；`data.pageNo=1`；`data.list` 为数组 |
| `ORG-ADMIN-004` | P1 | `GET /api/admin/users` | 按关键字模糊查询 | 已登录后台管理员，存在姓名含“张”的员工 | `keyword=张` | HTTP `200`；`code=0`；返回列表仅包含命中员工 |
| `ORG-ADMIN-005` | P1 | `GET /api/admin/users` | 按直属上级过滤 | 已登录后台管理员，存在 `managerUserId=2002` 的员工 | `managerUserId=2002` | HTTP `200`；`code=0`；返回列表成员的 `managerUserId` 均为 `2002` |
| `ORG-ADMIN-006` | P1 | `GET /api/admin/users` | 按在职状态过滤 | 已登录后台管理员 | `employmentStatus=ACTIVE` | HTTP `200`；`code=0`；返回列表成员状态均为 `ACTIVE` |
| `ORG-ADMIN-007` | P0 | `GET /api/admin/users/{id}` | 正常获取员工详情 | 已登录后台管理员；`userId=2003` 存在 | `userId=2003` | HTTP `200`；`code=0`；返回 `userId=2003` 的完整详情 |
| `ORG-ADMIN-008` | P1 | `GET /api/admin/users/{id}` | 查询不存在员工 | 已登录后台管理员 | `userId=999999` | HTTP `200`；`code=404`；`message=员工不存在` |
| `ORG-ADMIN-009` | P0 | `GET /api/admin/users/{id}/manager` | 查询员工直属上级成功 | 已登录后台管理员；员工有直属上级 | `userId=2003` | HTTP `200`；`code=0`；`data.userId=2002` |
| `ORG-ADMIN-010` | P1 | `GET /api/admin/users/{id}/manager` | 查询无直属上级员工 | 已登录后台管理员；员工无直属上级 | `userId=2002` | HTTP `200`；`code=0`；`data=null` |
| `ORG-ADMIN-011` | P0 | `GET /api/admin/users/{id}/subordinates` | 查询直属下级成功 | 已登录后台管理员；目标员工有下级 | `userId=2002` | HTTP `200`；`code=0`；返回数组长度大于 `0` |
| `ORG-ADMIN-012` | P1 | `GET /api/admin/users/{id}/subordinates` | 查询无直属下级员工 | 已登录后台管理员；目标员工无下级 | `userId=2003` | HTTP `200`；`code=0`；返回空数组 `[]` |

## 6.2 员工端接口

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ORG-EMP-001` | P0 | `GET /api/employee/me/profile` | 普通员工获取本人档案 | 已登录员工账号 A | 无参数 | HTTP `200`；`code=0`；返回当前登录员工本人信息 |
| `ORG-EMP-002` | P0 | `GET /api/employee/me/manager` | 普通员工获取直属上级 | 已登录员工账号 A；存在直属上级 | 无参数 | HTTP `200`；`code=0`；返回直属上级摘要 |
| `ORG-EMP-003` | P1 | `GET /api/employee/me/subordinates` | 普通员工无直属下级 | 已登录员工账号 A | 无参数 | HTTP `200`；`code=0`；返回空数组 `[]` |
| `ORG-EMP-004` | P0 | `GET /api/employee/me/subordinates` | 领导员工获取直属下级 | 已登录员工账号 B；存在下级 | 无参数 | HTTP `200`；`code=0`；返回直属下级数组 |
| `ORG-EMP-005` | P1 | `GET /api/employee/me/manager` | 顶级领导无直属上级 | 已登录员工账号 B；无上级 | 无参数 | HTTP `200`；`code=0`；`data=null` |
| `ORG-EMP-006` | P0 | `GET /api/employee/me/colleagues` | 普通员工获取同部门同事通讯录字段 | 已登录员工账号 A；同部门存在员工账号 C，且账号 C 已设置头像、签名、在线状态 | 无参数 | HTTP `200`；`code=0`；返回数组不包含本人；同事项包含 `avatarAttachmentRefId`、`signature`、`presenceStatus` |

## 6.3 鉴权与安全

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ORG-AUTH-001` | P0 | 任一后台端接口 | 未登录访问后台接口 | 无会话 | `GET /api/admin/departments/tree` | HTTP `401`；`code=401`；`message=未登录或会话已过期` |
| `ORG-AUTH-002` | P0 | 任一员工端接口 | 未登录访问员工接口 | 无会话 | `GET /api/employee/me/profile` | HTTP `401`；`code=401`；`message=未登录或会话已过期` |
| `ORG-AUTH-003` | P0 | 后台端接口 | 员工会话访问后台接口 | 已登录员工账号 A | `GET /api/admin/users` | HTTP `403`；`code=403`；`message=无权限访问` |
| `ORG-AUTH-004` | P0 | 员工端接口 | 后台会话访问员工接口 | 已登录后台管理员 | `GET /api/employee/me/profile` | HTTP `403`；`code=403`；`message=无权限访问` |

## 6.4 参数与契约

| 用例 ID | 优先级 | 接口 | 用例名称 | 前置条件 | 请求 | 预期结果 |
| --- | --- | --- | --- | --- | --- | --- |
| `ORG-VALID-001` | P0 | `GET /api/admin/users` | `pageNo` 小于最小值 | 已登录后台管理员 | `pageNo=0&pageSize=20` | HTTP `400`；`code=400`；`message=pageNo 最小为 1` |
| `ORG-VALID-002` | P0 | `GET /api/admin/users` | `pageSize` 超过上限 | 已登录后台管理员 | `pageNo=1&pageSize=201` | HTTP `400`；`code=400`；`message=pageSize 最大为 200` |
| `ORG-CONTRACT-001` | P1 | 任一成功接口 | 响应头回写 requestId | 已登录并主动传入请求头 | `X-Request-Id=test-org-001` | HTTP 响应头存在 `X-Request-Id=test-org-001`；响应体 `requestId` 一致 |
| `ORG-CONTRACT-002` | P1 | 任一成功接口 | 未传 requestId 自动生成 | 已登录 | 无 `X-Request-Id` | HTTP 响应头存在自动生成值；响应体 `requestId` 非空 |

---

## 七、执行步骤建议

## 7.1 冒烟顺序

1. 执行 `server/scripts/sql/seed_module_org_smoke_data.sql`
2. 登录后台账号，保存 `SESSION` Cookie
3. 执行 `ORG-ADMIN-001` 到 `ORG-ADMIN-003`
4. 执行 `ORG-ADMIN-007`、`ORG-ADMIN-009`、`ORG-ADMIN-011`
5. 登录员工账号 A，保存 `SESSION` Cookie
6. 执行 `ORG-EMP-001`、`ORG-EMP-002`、`ORG-EMP-003`、`ORG-EMP-006`
7. 执行 `ORG-AUTH-001` 到 `ORG-AUTH-004`
8. 执行 `ORG-VALID-001`、`ORG-VALID-002`

## 7.2 回归判定标准

以下条件全部满足，视为本轮组织查询接口回归通过：

- 所有 P0 用例通过
- 无接口出现 `500`
- `manager=null` 与 `subordinates=[]` 的语义稳定
- `colleagues` 返回数组且同事项包含通讯录展示字段
- 前后端都能读懂 `requestId`

---

## 八、自动化建议

后续如果准备把这批接口纳入持续回归，建议按下面顺序推进：

1. 先在 Postman / Apifox 中落一套集合
2. 再把 P0 用例沉淀为自动化接口测试
3. 等组织写侧上线后，把查询与写入联动一起纳入回归
