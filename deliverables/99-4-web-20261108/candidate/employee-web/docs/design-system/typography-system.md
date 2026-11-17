# 字号梯度

## 基准

- `1rem = 16px`
- 所有文字必须命中既定字号，不允许直接写 magic number。
- 五档语义从低到高为 `caption / body / subtitle / title / display`。

## 尺度表

| Token | px | rem | 默认行高 | 推荐字重 |
| --- | --- | --- | --- | --- |
| `font-size-12` | 12 | 0.75rem | 1.45 | 500 |
| `font-size-14` | 14 | 0.875rem | 1.5 | 500 |
| `font-size-17` | 17 | 1.0625rem | 1.55 | 500 |
| `font-size-21` | 21 | 1.3125rem | 1.35 | 600 |
| `font-size-26` | 26 | 1.625rem | 1.2 | 700 |
| `font-size-32` | 32 | 2rem | 1.1 | 700 |
| `font-size-40` | 40 | 2.5rem | 1 | 700 |

## 语义层

| 语义 | 默认 token | 场景 |
| --- | --- | --- |
| `caption` | `font-size-12` | 徽章、时间、说明、状态标签 |
| `body` | `font-size-14` | 正文、输入内容、列表描述 |
| `body-lg` | `font-size-17` | 重要正文、会话标题 |
| `subtitle` | `font-size-21` | 面板副标题、卡片摘要 |
| `title` | `font-size-26` | 页面标题、工作台模块标题 |
| `headline` | `font-size-32` | 数据卡数字、中型指标 |
| `display` | `font-size-40` | 核心指标、Agent 数据回答 |

## 字体家族

- `font-family-body`: `Inter`, `HarmonyOS Sans SC`, `Microsoft YaHei UI`, `Microsoft YaHei`, `Noto Sans CJK SC`, `PingFang SC`, `sans-serif`
- `font-family-display-en`: `Inter`, `Avenir Next`, `sans-serif`
- `font-family-display-cn`: `HarmonyOS Sans SC`, `Microsoft YaHei UI`, `Microsoft YaHei`, `PingFang SC`, `sans-serif`
- 数字强调使用 display 栈，保证跨 Windows 和 macOS 的渲染稳定性。

## 使用规则

- 列表标题: `subtitle-sm`
- 列表说明: `body-sm`
- Rail 标签: `caption-md`
- 页级标题: `title-lg`
- 核心数字: `display`
- Agent 结果卡主数值: `display` 或 `title-lg`，禁止退回普通段落字。
