# 颜色系统

## 目标

员工端的颜色不再以 `--green-soft` 这类局部命名组织，而是分成基础阶梯和语义映射两层。工程层只消费语义色，避免组件自己挑色。

## 基础色阶

### Primary

`primary` 用于品牌、主动作、高亮流程和消息中的关键引导。

| Token | Hex | 用途 |
| --- | --- | --- |
| `primary-50` | `#edf5fb` | 浅色背景、弱提示 |
| `primary-100` | `#d7e7f4` | 选中背景 |
| `primary-200` | `#b4d1e8` | 线条悬停 |
| `primary-300` | `#8ab6d8` | 低权重图形 |
| `primary-400` | `#5b93c2` | 次级强调 |
| `primary-500` | `#3f719f` | 品牌主色 |
| `primary-600` | `#305982` | Hover |
| `primary-700` | `#28496a` | Active |
| `primary-800` | `#233c57` | 深色容器文本 |
| `primary-900` | `#1f3349` | 反白模式深背景 |

### Neutral

`neutral` 是界面的骨架色，负责背景、描边、正文和层级关系。

| Token | Hex |
| --- | --- |
| `neutral-0` | `#ffffff` |
| `neutral-50` | `#f6f8fb` |
| `neutral-100` | `#eaeff5` |
| `neutral-200` | `#d6dfe9` |
| `neutral-300` | `#b8c5d3` |
| `neutral-400` | `#9ba6b4` |
| `neutral-500` | `#717d8c` |
| `neutral-600` | `#57626f` |
| `neutral-700` | `#424b56` |
| `neutral-800` | `#2c333c` |
| `neutral-900` | `#181d23` |

### Success

| Token | Hex |
| --- | --- |
| `success-50` | `#edf7f1` |
| `success-100` | `#d7ecdf` |
| `success-200` | `#b1d8c0` |
| `success-300` | `#86c09b` |
| `success-400` | `#5fa377` |
| `success-500` | `#4d8562` |
| `success-600` | `#3f6c50` |
| `success-700` | `#355843` |
| `success-800` | `#2b4736` |
| `success-900` | `#22372a` |

### Warning

| Token | Hex |
| --- | --- |
| `warning-50` | `#fbf5e8` |
| `warning-100` | `#f3e5c1` |
| `warning-200` | `#e8d08d` |
| `warning-300` | `#d9b85f` |
| `warning-400` | `#c39a3f` |
| `warning-500` | `#a87e34` |
| `warning-600` | `#88652a` |
| `warning-700` | `#6b5023` |
| `warning-800` | `#523f1d` |
| `warning-900` | `#3d3017` |

### Danger

| Token | Hex |
| --- | --- |
| `danger-50` | `#fcf1ef` |
| `danger-100` | `#f5ddd8` |
| `danger-200` | `#ebbdb3` |
| `danger-300` | `#db9283` |
| `danger-400` | `#c86b59` |
| `danger-500` | `#b15345` |
| `danger-600` | `#934338` |
| `danger-700` | `#77372f` |
| `danger-800` | `#5f2e28` |
| `danger-900` | `#4a2622` |

### Info

| Token | Hex |
| --- | --- |
| `info-50` | `#eef5fb` |
| `info-100` | `#dae8f4` |
| `info-200` | `#bdd6ea` |
| `info-300` | `#96bddb` |
| `info-400` | `#6b9bc9` |
| `info-500` | `#4f7fb3` |
| `info-600` | `#3f6691` |
| `info-700` | `#355274` |
| `info-800` | `#2e425d` |
| `info-900` | `#263549` |

### Signal

| Token | Hex | 用途 |
| --- | --- | --- |
| `accent-signal` | `#ff5a4e` | 未读徽章、强提醒、必须立即处理 |
| `accent-signal-soft` | `#ffe4e0` | 信号色弱背景 |

## 语义映射

工程里直接使用下面三组语义色，不直接写基础色阶。

### Surface

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| `surface-canvas` | `neutral-50` | `neutral-900` | 页面底色 |
| `surface-panel` | `neutral-0` | `#202832` | 面板、卡片 |
| `surface-panel-muted` | `neutral-100` | `#26313d` | 弱卡片、输入底 |
| `surface-panel-strong` | `neutral-200` | `#303b49` | 强分组、嵌入块 |
| `surface-accent` | `primary-50` | `#1f3349` | 主色背景 |
| `surface-info` | `info-50` | `info-900` | 信息提示背景 |
| `surface-danger` | `danger-50` | `#4a2622` | 危险块背景 |

### Border

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| `border-subtle` | `neutral-100` | `#2a3542` | 弱分割线 |
| `border-default` | `neutral-200` | `#394656` | 常规边框 |
| `border-strong` | `neutral-400` | `neutral-300` | 焦点、强边框 |
| `border-accent` | `primary-400` | `primary-300` | 强调态 |
| `border-danger` | `danger-400` | `danger-300` | 破坏态 |

### Text

| Token | Light | Dark | 用途 |
| --- | --- | --- | --- |
| `text-primary` | `neutral-900` | `neutral-0` | 正文 |
| `text-secondary` | `neutral-700` | `neutral-200` | 次级正文 |
| `text-tertiary` | `neutral-500` | `neutral-400` | 说明、占位 |
| `text-inverse` | `neutral-0` | `neutral-0` | 深背景文字 |
| `text-accent` | `primary-700` | `primary-200` | 关键引导 |
| `text-danger` | `danger-700` | `danger-200` | 破坏态说明 |

## 状态规则

- Hover: 默认从 `500` 压到 `600`。
- Active: 默认压到 `700`。
- Disabled: 不再单独造颜色，统一降到 `surface-panel-muted + text-tertiary`。
- Agent 过程卡: 背景使用 `surface-panel` / `surface-accent` 的轻渐变。
- Rail 未处理徽章和当前入口标记: 背景使用 `accent-signal`，文字使用 `text-inverse`。

## 工程规则

- 组件只能引用 `surface / border / text / primary / success / warning / danger / info` 的语义 token。
- 业务页面不允许新建临时颜色变量。
- Figma library 命名必须与 `src/design-system/tokens.ts` 保持一致。
