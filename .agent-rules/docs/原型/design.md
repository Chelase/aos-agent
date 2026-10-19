---
name: AOS-Agent v6
description: 原生车载 Agent 的蓝灰 Material 工作台
colors:
  primary-light: "#0B57D0"
  primary-dark: "#A8C7FA"
  on-primary-light: "#FFFFFF"
  on-primary-dark: "#06275F"
  background-light: "#F4F6FA"
  background-dark: "#0B0F17"
  surface-light: "#FFFFFF"
  surface-dark: "#131A26"
  container-light: "#E9EEF6"
  container-dark: "#1A2331"
  container-high-light: "#E1E8F2"
  container-high-dark: "#222D40"
  rail-light: "#FFFFFF"
  rail-dark: "#101722"
  on-surface-light: "#191C22"
  on-surface-dark: "#E3E7EE"
  on-surface-variant-light: "#43474E"
  on-surface-variant-dark: "#B4BCC9"
  tertiary-text-light: "#6B7280"
  tertiary-text-dark: "#8A94A6"
  selected-light: "#D3E3FD"
  selected-dark: "#29425F"
  on-selected-light: "#041E49"
  on-selected-dark: "#D3E3FD"
  outline-light: "#C4C6D0"
  outline-dark: "#3A4454"
  success-light: "#146C2E"
  success-dark: "#7BD88F"
  warning-light: "#7A4A00"
  warning-dark: "#F0B429"
  error-light: "#BA1A1A"
  error-dark: "#FFB4AB"
  success-surface-light: "#D3F2D9"
  success-surface-dark: "#0E3B1E"
  warning-surface-light: "#FFDE9E"
  warning-surface-dark: "#4A3200"
  error-surface-light: "#FFDAD6"
  error-surface-dark: "#93000A"
  terminal-background: "#080D14"
  terminal-foreground: "#D3E3FD"
typography:
  display:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "56sp"
    fontWeight: 700
    lineHeight: "64sp"
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "36sp"
    fontWeight: 700
    lineHeight: "44sp"
  title:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "24sp"
    fontWeight: 500
    lineHeight: "32sp"
  body:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "32sp"
  supporting:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "18sp"
    fontWeight: 400
    lineHeight: "26sp"
  label:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "20sp"
    fontWeight: 500
    lineHeight: "28sp"
  data:
    fontFamily: "Android monospace"
    fontSize: "20sp"
    fontWeight: 400
    lineHeight: "28sp"
  metric:
    fontFamily: "Android sans-serif with system CJK fallback"
    fontSize: "44sp"
    fontWeight: 700
    lineHeight: "56sp"
  terminal:
    fontFamily: "Android monospace"
    fontSize: "22sp"
    fontWeight: 400
    lineHeight: "32sp"
rounded:
  card: "20dp"
  control: "18dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "16dp"
  lg: "24dp"
  xl: "32dp"
  xxl: "48dp"
components:
  button-primary:
    backgroundColor: "{colors.primary-light}"
    textColor: "{colors.on-primary-light}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "16dp 32dp"
    height: "min 72dp"
  button-secondary:
    textColor: "{colors.primary-light}"
    typography: "{typography.label}"
    rounded: "{rounded.control}"
    padding: "16dp 24dp"
    height: "min 72dp"
  card:
    backgroundColor: "{colors.surface-light}"
    rounded: "{rounded.card}"
    padding: "28dp"
  status:
    rounded: "{rounded.control}"
    padding: "0dp 16dp"
    height: "min 36dp"
  navigation:
    width: "112dp"
    height: "min 72dp per item"
  input:
    height: "min 72dp"
---

# AOS-Agent v6 原生视觉规范

## Overview

**Creative North Star: "安静的 Android Material 车载工作台"**

用户指定 `.impeccable/mocks/v6/` 七页作为本轮视觉依据。界面帮助驾驶者和调试者快速读懂真实设备状态、发起对话与使用本地能力；功能和数字以实现为准。全局产品事实见 `../../mechanisms/requirements-analysis.md` 和 `../unified-ecosystem-vision.md`，七页策略另存 surface brief。

本规范从已实现的 `ui/theme/`、共享组件和页面提取。Android 系统字体保留完整中文回退；不套用网页的展示字体要求。主题沿用手动持久化、日间默认，没有自动夜间策略。

**Key Characteristics:**

- 常驻 Material 导航轨，六个目的地共用装配点。
- 蓝灰双主题、平面表面、宽松触控和清晰文字层级。
- 状态文字与语义色并用，缺失能力保留原因。

## Colors

主色是明确的蓝，背景和承载面以冷灰递进；完整数值见 frontmatter，来源为 `Color.kt` 与 `Theme.kt`。

### Primary

日间蓝用于主要操作、用户消息和选中状态；夜间浅蓝承担相同角色，前景随主题成对切换。

### Neutral

背景、卡片、容器和较高容器形成低反差层次；主文本、次级文本和辅助文本分别承担结果、说明及弱信息。导航轨独立表面。终端两主题都保持深底浅字。

### Semantic states

成功、警告、错误都有各自的前景及柔和状态底；同一状态必须有文字，不能只靠颜色理解。页面从 MaterialTheme/AOSTheme 取色。

**The Truth Rule.** 原型中的数字只说明排版；显示真实采集结果和缺失原因，不补造车辆或会话数据。

## Typography

显示和正文使用 Android sans-serif 与系统 CJK 回退；测量数据和终端使用 Android monospace。字号为 sp，支持系统字体缩放；长度和间距为 dp，不能把截图像素当原生单位。

frontmatter 记录跨页面常用层级。代码还提供 headlineLarge（44/52sp）、headlineSmall（28/36sp）、titleLarge（26/34sp）、titleSmall（20/28sp）、bodyLarge（24/36sp）、导航 labelMedium（16/22sp）和区块标题（22/30sp）；不要为新页面复制一套样式。

## Layout

共享导航轨常规宽度（112dp），根可用宽度小于（1100dp）时变为（96dp）。页面通常使用安全内边距（32dp），节奏来自 spacing；卡片内部留白（28dp）。

设置、工程师和对话摘要按页面可用宽度及字体比例自适应：至少（1100dp）且 fontScale 不超过（1.2）才用宽屏结构；双栏各自滚动，否则纵向滚动。主按钮和输入至少（72dp），行驶返回至少（88dp）。

## Elevation & Depth

卡片没有阴影，用背景、表面和容器层次区分区域。细分隔线用于数据行和导航边界。状态灯的小范围色晕属于状态标记，不是卡片投影。

**The Flat Surface Rule.** 共用卡片保持平面表面；层级来自色调和留白。

## Shapes

卡片采用柔和圆角；按钮和状态徽章采用更紧的圆角，均复用 frontmatter 对应 token。App 标志保留第一版环形灯组与上方感知弧线的布局，使用蓝底白色，并按用户要求整体放大 7.5%、加粗弧线和灯点；启动图标背景为 `#0B57D0`，弧线与全部灯点均为 `#FFFFFF`，应用内同源图形随主题 primary 着色。源文件与精确尺寸见 `../../../artwork/README.md`。

## Components

- **导航与标题**：`AOSAppShell` 提供六目的地 rail、系统 Back 和选中指示；`AOSPageHeader` 统一页面标题与真实操作。
- **卡片和数据**：`AOSCard`、`AOSMetricCard`、`AOSDataRow`、`AOSSectionHeader` 共享表面、层级和排版。
- **按钮与输入**：主操作使用主题实底，次操作使用描边；输入保留 Material 焦点和禁用语义，附近解释缺失原因。
- **状态**：柔和底色徽章和状态圆点均配合文字；徽章本身不是触控目标。
- **对话**：用户蓝色、助手表面色；普通气泡随内容扩张且至多占（70%）宽度，工具轨迹至多（94%）。空、忙和阻断态有可读说明；摘要显示真实消息、调用、Skill 和 MCP 工具数。
- **驾驶遮罩**：覆盖整个外壳；底页从无障碍树隐藏并拦截手势，保留语音状态、麦克风和返回首页。
- **生命周期**：ChatConsole 在导航切换时保持组合；可保存页面状态和未发送输入由 SaveableStateHolder 保留。设置草稿只在当前根组合内存保留，密钥不进 saved bundle；不承诺对话跨 Activity 重建恢复。终端 ViewModel 和 PTY 生命周期沿用原机制。

## Do's and Don'ts

### Do:

- **Do** 从 theme token 与共享组件创建新页面。
- **Do** 成对维护中文英文、双主题与真实能力禁用原因。
- **Do** 在 AAOS 模拟器检查窄横屏、大字体、关闭动画和行驶遮罩。

### Don't:

- **Don't** 复制原型车辆示例或估算会话 token 用量。
- **Don't** 把导航状态保留写成 Activity 重建后的会话持久化。
- **Don't** 为设置草稿另建密钥持久化副本或 saved-state 字段。

> 提取日期：2026-10-18。原生实现与浏览器示例使用不同单位；`.impeccable/design.json` 中的 HTML/CSS 仅供设计面板预览，不是 Compose 运行代码。最终验收状态见 phase6 计划。
