# 飞书 Universe Design 设计手册（Workforce Hub 落地版）

> 来源：飞书开放平台「设计应用 / Universe Design」官方文档。
> 官方入口：https://open.feishu.cn/document/tools-and-resources/design-specification/written-in-advance （现重定向至 `https://open.feishu.cn/document/design-specification/written-in-advance`）
> 整理日期：2026-11-15。官方页面所示更新时间：设计应用概述 2025-04-14；设计语言各页 2025-02-17。
> 适用范围：Workforce Hub 的桌面端、员工端、管理端及 Ardot 设计稿。移动端已有独立页面时按其断点适配，不直接照搬桌面密度。
> 本文分为「官方规则」与「项目落地」两部分：数值均来自官方文档；没有官方数值的细节不得冒充飞书规范。后续开发先读本手册，再读 `桌面端界面设计规范.md` 的产品补充规则。

## 1. 设计目标与优先级

飞书 Universe Design 旨在让第三方应用与飞书原生体验契合，并提升可用性与满意度。本项目的优先级为：

1. **结构和交互一致**：页面骨架、导航、反馈、响应式规则先于视觉装饰。
2. **内容完整可用**：不得为了“无滚动”而裁切操作、数据或状态；内容超出时由对应容器滚动。
3. **密度服务任务**：批量浏览、快速操作采用高密度；重点任务、警示与对话框采用较低密度，不随意压缩。
4. **同一系统复用同一规则**：IM、工作台及其他一级页面共用应用壳、字体、颜色、间距和控件状态，不能各自形成一套画布。

## 2. 布局、栅格与响应式

官方：[布局](https://open.feishu.cn/document/design-specification/design-language/layout) · [响应式设计指南](https://open.feishu.cn/document/design-specification/responsive-design/responsive-design-guidelines)

### 2.1 基础布局

- 信息密度由任务决定：批量浏览、快速操作减少滚动、提升同屏效率；日历选择、重点任务、警示等保留充分空间。
- 水平对齐包括左、右、居中、延展；固定宽内容按需对齐，自适应内容使用延展。垂直对齐同理。
- 横向空间不足时表单标签置于输入区上方并左对齐；空间充足时可与输入区并排，标签右对齐。
- 数值建议保持相同有效位数并右对齐。

### 2.2 栅格与间距

| 项目 | Web / 桌面端 | Pad | 移动端 / 小程序 |
| --- | --- | --- | --- |
| 栅格列数 | 12 | 8 | 4 |
| 槽 Gutter | 16px | 8px | 8px |
| 安全边距 Margin | 24px | 16px | 16px |

- Web / 桌面端以 **8px** 为基础单位，小区域可用 **4px**；大间距按 8 的倍数扩展，小间距可按 4 的倍数扩展。
- 元素关系越亲密，间距越小；同层同级元素间距一致。
- 模态组件与容器边缘的通用最小安全边距为 **16px**，空间不足时按组件规则调整展开方向。
- 侧导航、锚点等固定控件不计入内容栅格；内容栅格宽度为屏幕宽度减去固定控件宽度。
- 全屏布局适用于表格、工具和高信息密度页面；定宽布局适用于表单、单一内容，官方建议定宽容器 **1136px**。
- 通栏、两栏、三栏均可使用；同一页面的分栏方式不建议超过 3 种。通栏内模块等高，空间不足可换行、拉伸、内部滚动或收起。

### 2.3 断点

| 断点 | 宽度 | 列数 | 槽 | 安全边距 |
| --- | --- | --- | --- | --- |
| XS | 0–599px | 4 | 8px | 16px |
| S | 600–1023px | 8 | 8px | 16px |
| M | 1024–1439px | 12 | 16px | 24px |
| L | 1440–1919px | 12 | 16px | 24px |
| XL | ≥1920px | 官方表未明示 | 16px | 24px |

- M/L 保持桌面导航完整；S 采用半收起；XS 将导航收进图标触发的抽屉或全屏模态。
- 官方响应式导航示例将 S 写成 600–1440px，与总断点表 600–1023px 不一致；项目以总断点表为基准，结合实际窗口验证，不混用区间。
- 本项目 Electron 已有窗口最小宽度和折叠导航约束时，保留既有约束，新增页面不得横向溢出或遮挡关键操作。

## 3. 色彩

官方：[色彩](https://open.feishu.cn/document/design-specification/design-language/color)

### 3.1 品牌与功能色

- 品牌色：**`#1456F0`**，用于行动号召、选中、信息高亮和插画。
- 功能色文字：Success `#32A645`、Warning `#ED6D0C`、Error `#F54A45`、Info `#1456F0`。
- 功能色内容：Success `#35BD4B`、Warning `#FF811A`、Error `#F54A45`、Info `#1456F0`。
- 功能色在同一产品体系内必须语义一致；辅助色和渐变色只用于功能区分、分类卡片和插画等场景，不滥用大面积装饰。

### 3.2 中性色

| 用途 | 色值 |
| --- | --- |
| 一、二级主标题 / 一级正文 | `#1F2329` |
| 副标题 / 二级正文 | `#646A73` |
| Placeholder / 次要信息 | `#8F959E` |
| Disable | `#BBBFC4` |
| 一级图标 | `#2B2F36` |
| 二、三级 / 表意图标 | `#646A73` / `#8F959E` |
| Background 1 / 2 / 3 | `#EFF0F1` / `#F5F6F7` / `#F8F9FA` |
| 可交互控件描边 Border 1 | `#D0D3D6` |
| 卡片描边 Border 2 | `#DEE0E3` |
| Disable 填充 | `#EFF0F1` |

中性色透明度只以 `#1F2329` 或 `#FFFFFF` 为基色，官方梯度为 5%、10%、15%、20%、30%、40%、50%、60%、69%、80%、89%。

**项目落地**：颜色必须进入 CSS token，禁止在组件中硬编码；新增或替换品牌色时同时核对深色主题、悬浮、选中、禁用、聚焦与错误状态，不能只改默认态。

## 4. 字体与图标

官方：[字体](https://open.feishu.cn/document/design-specification/design-language/font) · [图标](https://open.feishu.cn/document/design-specification/design-language/icon)

### 4.1 PC 字体

| 类型 | 字号 / 行高 | 字重 |
| --- | --- | --- |
| 特大标题 | 30 / 46px | Semibold 600 |
| 一级标题 | 24 / 36px | Semibold 600 |
| 二级标题 | 20 / 30px | Medium 500 |
| 三级标题 | 18 / 28px | Medium 500 |
| 四级标题 | 16 / 24px | Medium 500 |
| 五级标题 | 16 / 24px | Regular 400 |
| 辅助标题 | 14 / 22px | Medium 500 |
| 正文 | 14 / 22px | Regular 400 |
| 正文辅助 | 12 / 20px | Regular 400 |
| 辅助 | 12 / 20px | Medium 500 |
| 小辅助 / 最小辅助 | 10 / 16px | Medium 500 / Regular 400 |

字体颜色以色彩规范为准。数字和正文保持可读，不为压缩卡片而随意使用小于 10px 的文字。

### 4.2 图标

- 输出尺寸为 16、20、24、32px；统一在 24×24px 栅格中绘制，活动区 22×22px。
- 常规线宽 2px，复杂图形可 1.5px；导航以面型为主，操作以线型为主，语义型采用低对比单色线型。
- 坐标尽量取整数并贴齐像素；视觉重心允许人工校准。
- 图标与文字同排时，图标的明度应低于文字。纯图标按钮必须有可识别名称及 Tooltip。

## 5. 圆角、描边与阴影

官方：[样式](https://open.feishu.cn/document/design-specification/design-language/style)

| Token | 数值 | 场景 |
| --- | --- | --- |
| Radius-XS | 4px | 标签、复选框 |
| Radius-S | 6px | 按钮、输入框、提示 |
| Radius-M | 8px | 卡片、对话框、弹窗 |
| Radius-L | 10px | 复杂大卡片、全屏模态页 |
| 全圆角 | 圆形 / 胶囊 | 头像、开关、徽标等 |

- 控件、卡片描边均为 **1px**，分别使用 Border 1 / Border 2。
- 分割线 Web/Desktop 为 **1px**，采用 `#1F2329` 15%；移动端 @1x 为 0.5px。
- 阴影用于表达高程，整体轻量。若阴影已足以区分层级，可不再叠加描边或分割线。
- **项目默认**：普通业务卡优先 `Radius-M + 1px Border 2 + 无阴影`；导航和连续工作区使用细分隔线，不使用悬浮大圆角卡片。

## 6. 导航与工作台画布

官方：[导航菜单](https://open.feishu.cn/document/design-specification/component---navigation/navigation-menu)

### 6.1 通用规则

- 顶部导航承载品牌、一级分类与全局功能；桌面侧边导航承载当前功能的二级导航。
- 一级导航、二级侧栏、主内容、可选详情栏必须保持稳定的层级，不得把业务右栏当作左侧二级导航。
- 同一级页面必须复用同一应用壳，不因工作台或 IM 的内容不同而改变侧栏位置和边界。
- 侧边导航的顶部为品牌/模块区，中部为导航，底部为可选操作区；列表超出时只纵向滚动，Hover 显示滚动条。
- 常规导航最多三级；次级项默认最多展示 5 个，更多通过“查看更多”展开。树导航最多 4 层。

### 6.2 Desktop 侧边导航

- 默认/最小宽度 **220px**，支持配置拖拽，最大宽度 **440px**。
- 菜单项高 **40px**；图标 **20×20px**；左边距 **16px**；文本 14，图标与文本间距 **8px**。
- 右侧操作最多 2 个，按钮 24×24px，间距 8px，右边距 12px。
- 默认项无底色；Hover 使用 N900 10%；Active 文本为 Medium、品牌色，背景为 N900 5%，可配置品牌色选中指示器。
- 非定宽菜单文本最多一行，超出截断，文本域随侧栏宽度自适应。

### 6.3 项目画布验收

- 工作台画布顺序固定为：**公共一级导航 → 当前模块二级侧栏 → 主工作区 → 可选右侧业务/详情栏**。
- 一级导航和二级侧栏从顶栏下方连续到窗口底部；不得在底部出现截断线、独立悬浮底色或无意义的外围留白。
- 侧栏底部主题、头像等操作必须完整可见，主内容内边距不得影响侧栏边界。
- 画布内容超出时，由明确的业务容器滚动；不可通过 `clipsContent` 隐藏考勤、审批、快捷操作等关键内容。

## 7. 数据录入与操作

官方：[基础按钮](https://open.feishu.cn/document/design-specification/component---data-entry/button/button_basic) · [输入框](https://open.feishu.cn/document/design-specification/component---data-entry/input) · [表单](https://open.feishu.cn/document/design-specification/component---data-entry/form)

- 按钮高度：48 / 40 / 32 / 28 / 24px，其中 **32px Medium 最常用**。项目既有 36px 控件属于产品补充规范，若继续沿用须全站一致，不能宣称是飞书官方尺寸。
- 同一模块建议仅一个主要按钮；同组基础按钮样式不超过两种。按钮文本不换行、不省略。
- 输入框高度 28 / 32 / 40px，默认 32px；建议宽 240–600px。图标距边框 12px，图标与文字、多个图标之间均为 8px。
- 长文本使用文本域，推荐高 92px；辅助/校验文本最多两行。
- 表单优先顶部标签；弹窗/Popover 表单项间距建议 20px，页面级表单建议 28px；左侧标签与输入框间距 16px。
- 必填项有明确标记；即时校验在停止键入 500ms 后触发，或使用失焦/提交校验。
- 所有操作具备默认、Hover、聚焦、禁用、加载、成功/失败等适用状态；危险操作与主操作明确区分。

## 8. 数据展示、卡片与可视化

官方：[卡片](https://open.feishu.cn/document/design-specification/component---data-display/card) · [表格](https://open.feishu.cn/document/design-specification/component---data-display/table) · [数据可视化设计指南](https://open.feishu.cn/document/design-specification/data-visualization/data-visualization-design-guide)

### 8.1 卡片与表格

- 卡片只用于聚合同类信息，不作为所有区域的默认外壳；大小由内容所需空间决定。
- 同一界面建议不超过两种圆角。多个小卡片或嵌套卡片优先用描边/背景区分，而非阴影。
- 卡片标题与内容间距 12px；内容组间距建议 8–16px；卡片内容与容器边距建议 12px，按页面密度统一扩展。
- 带投影卡片间距建议 16px，不带投影卡片建议 12px。
- 表格内容 14px Regular / 行高 22px，表头 14px Medium / 行高 22px；数值右对齐，文本/时间/状态左对齐。
- 行距有基础 12、紧凑 8、宽松 16px，默认基础型；长表格固定表头，宽表格可固定关键列/操作列并内部横向滚动。

### 8.2 图表

- 分类数据使用分类色板，有序大小/梯度使用顺序色板，状态使用功能色板，不混用语义。
- 桌面图例默认底部水平居中；Tooltip 使用 Hover 触发。
- 小空间保留优先级：**图形元素 > 图例 > 坐标轴 > 网格线**；标签可省略、换行、旋转，数值过多时改由 Tooltip 展示。
- 折线图分类超过 5 个不建议使用；饼/环图分类少于 9 个；避免有限空间内标签重叠。
- 无数据必须呈现明确空态，不能用高度为 0 的图表伪装已有业务数据。

## 9. 空态、加载与反馈

官方：[空状态](https://open.feishu.cn/document/design-specification/component---data-display/empty) · [加载中](https://open.feishu.cn/document/design-specification/component----feedback/loading) · [临时提示 Toast](https://open.feishu.cn/document/design-specification/component----feedback/toast) · [常驻提示 Notice](https://open.feishu.cn/document/design-specification/component----feedback/notice) · [通知 Notification](https://open.feishu.cn/document/design-specification/component----feedback/notification)

- 数据驱动页面必须覆盖初始加载、空数据、无搜索结果、无权限、失败、成功等状态，不能留下无解释空白。
- 空态文案说明发生了什么，并在有解决方案时提供下一步操作。失败使用负反馈，无结果/无权限使用中性反馈，完成使用正反馈。
- PC 空态插画常用 120×120px，小场景 60×60px；小卡片的文字和间距可缩小，但仍满足 4N。
- 初始且形态可预估的内容优先骨架屏，并按模块渐进加载；局部加载/状态切换用 Spin（局部 24px，全局 40px）。
- 加载提示与空状态不得同时出现；真实缓存存在时优先展示缓存，不用全屏 Spin 覆盖可阅读内容。
- Toast 用于轻量结果反馈，不承载关键操作，不遮挡重要内容；PC 距顶部推荐 72px，最大宽 600px。
- Toast 无操作且 0–40 个中文字符时建议显示 3–5s；Hover 暂停计时。需要持续处理/解决的提示用 Notice 或 Notification，不滥用 Toast。
- 管理端确认弹窗继续复用项目组件，不使用 `window.confirm`。

## 10. 动效与可访问性

官方：[动效](https://open.feishu.cn/document/design-specification/design-language/animation)

- 动效传达状态、层级与操作结果，不用于装饰拖延。
- 按钮 Hover、Badge 等细微变化采用较短时长；明显位移采用较长时长；消失通常比出现更短。
- 进入采用减速曲线，离开采用加速曲线；已有界面元素应快速响应、缓慢结束。
- 字号、字重和文本内容不做过渡；可过渡位移、缩放、旋转、透明度、颜色、圆角、尺寸、描边等。
- 保留键盘焦点、Esc 关闭、图标按钮可访问名称、文本对比度和 `prefers-reduced-motion`；不可用动画代替状态文本。
- 开发验收必须包含真实浏览器/桌面端实测，确认无横向溢出、无关键内容裁切、状态可读、操作有反馈。

## 11. 开发落地清单

每个新页面或画布修改均按以下顺序验收：

1. **骨架**：一级导航、二级侧栏、主工作区、右侧栏的层级与既有 IM 一致。
2. **栅格**：桌面 12 列、16px 槽、24px 安全边距；侧栏不计入内容栅格。
3. **字体与颜色**：使用本手册层级及 CSS token，不在组件中另造色值。
4. **控件与卡片**：按官方尺寸/圆角/描边；项目差异化值必须来自产品补充规范。
5. **完整性**：加载、空态、失败、成功、权限、禁用状态完整，不虚构业务数据填补空白。
6. **响应式**：至少覆盖 1280/1440 桌面窗口和项目最小窗口；侧栏及底部操作贯通且无裁切。
7. **回归**：运行相关测试与构建，保存真实截图，证据要求遵守根目录 `AGENTS.md`。

## 12. 官方文档索引

- [设计应用概述](https://open.feishu.cn/document/design-specification/written-in-advance)
- 设计语言：[布局](https://open.feishu.cn/document/design-specification/design-language/layout) · [动效](https://open.feishu.cn/document/design-specification/design-language/animation) · [色彩](https://open.feishu.cn/document/design-specification/design-language/color) · [图标](https://open.feishu.cn/document/design-specification/design-language/icon) · [样式](https://open.feishu.cn/document/design-specification/design-language/style) · [字体](https://open.feishu.cn/document/design-specification/design-language/font)
- [响应式设计指南](https://open.feishu.cn/document/design-specification/responsive-design/responsive-design-guidelines)
- [数据可视化设计指南](https://open.feishu.cn/document/design-specification/data-visualization/data-visualization-design-guide)
- 组件导航：[锚点](https://open.feishu.cn/document/design-specification/component---navigation/anchor) · [面包屑](https://open.feishu.cn/document/design-specification/component---navigation/breadcrumb) · [下拉菜单](https://open.feishu.cn/document/design-specification/component---navigation/dropdown) · [导航菜单](https://open.feishu.cn/document/design-specification/component---navigation/navigation-menu) · [分页](https://open.feishu.cn/document/design-specification/component---navigation/pagination) · [滚动条](https://open.feishu.cn/document/design-specification/component---navigation/scroll-bar) · [步骤条](https://open.feishu.cn/document/design-specification/component---navigation/steps)
- 数据录入：[按钮](https://open.feishu.cn/document/design-specification/component---data-entry/button/button_basic) · [表单](https://open.feishu.cn/document/design-specification/component---data-entry/form) · [输入框](https://open.feishu.cn/document/design-specification/component---data-entry/input) · [选择器](https://open.feishu.cn/document/design-specification/component---data-entry/select) · [上传](https://open.feishu.cn/document/design-specification/component---data-entry/upload)
- 数据展示：[卡片](https://open.feishu.cn/document/design-specification/component---data-display/card) · [空状态](https://open.feishu.cn/document/design-specification/component---data-display/empty) · [表格](https://open.feishu.cn/document/design-specification/component---data-display/table) · [标签](https://open.feishu.cn/document/design-specification/component---data-display/tag) · [时间轴](https://open.feishu.cn/document/design-specification/component---data-display/timeline)
- 反馈：[对话框](https://open.feishu.cn/document/design-specification/component----feedback/dialog) · [抽屉](https://open.feishu.cn/document/design-specification/component----feedback/drawer) · [加载中](https://open.feishu.cn/document/design-specification/component----feedback/loading) · [通知](https://open.feishu.cn/document/design-specification/component----feedback/notification) · [常驻提示](https://open.feishu.cn/document/design-specification/component----feedback/notice) · [临时提示](https://open.feishu.cn/document/design-specification/component----feedback/toast)
