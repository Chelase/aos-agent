---
name: "AOS-Agent Website"
description: "白底蓝色的开源车机 Agent 官网，以真实界面帮助访客决定下载与安装。"
colors:
  blue: "#0b57d0"
  blue-hover: "#0847ad"
  ink: "#191c22"
  muted: "#626b7a"
  line: "#dde3ed"
  pale: "#f4f6fa"
  white: "#fff"
  secondary-bg: "#f0f3f8"
  secondary-hover: "#e4eaf3"
  download-bg: "#edf4ff"
  screenshot-bg: "#0b0f17"
typography:
  display:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "clamp(56px, 7.5vw, 108px)"
    fontWeight: 800
    lineHeight: 1.12
    letterSpacing: "-.035em"
  headline:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "clamp(30px, 4.1vw, 54px)"
    fontWeight: 800
    lineHeight: 1.2
    letterSpacing: "-.025em"
  title-scene:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "26px"
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: "-.025em"
  body:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "16px"
    fontWeight: 400
    lineHeight: 1.75
    letterSpacing: "normal"
  body-section:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "19px"
    fontWeight: 400
    lineHeight: 1.85
    letterSpacing: "normal"
  label-action:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "20px"
    fontWeight: 650
    lineHeight: 1.5
    letterSpacing: "normal"
  label-tab:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "17px"
    fontWeight: 550
    lineHeight: 1.75
    letterSpacing: "normal"
  caption:
    fontFamily: '"AOS Sans", "Noto Sans SC", sans-serif'
    fontSize: "12px"
    fontWeight: 400
    lineHeight: 1.6
    letterSpacing: "normal"
rounded:
  screenshot: "14px"
  control: "12px"
  control-mobile: "10px"
  screenshot-mobile: "9px"
  compact: "8px"
spacing:
  compact: "12px"
  text-gap: "16px"
  action-gap: "20px"
  mobile-column-gap: "24px"
  action-top: "30px"
  section-mobile: "50px"
  section-tablet: "68px"
  section-desktop: "88px"
components:
  button-primary:
    backgroundColor: "{colors.blue}"
    textColor: "{colors.white}"
    typography: "{typography.label-action}"
    rounded: "{rounded.control}"
    padding: "14px 26px"
  button-primary-hover:
    backgroundColor: "{colors.blue-hover}"
  button-secondary:
    backgroundColor: "{colors.secondary-bg}"
    textColor: "{colors.ink}"
    typography: "{typography.label-action}"
    rounded: "{rounded.control}"
    padding: "14px 26px"
  button-secondary-hover:
    backgroundColor: "{colors.secondary-hover}"
  tab:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    typography: "{typography.label-tab}"
    padding: "14px 8px 15px"
  tab-selected:
    textColor: "{colors.blue}"
  screenshot:
    backgroundColor: "{colors.screenshot-bg}"
    rounded: "{rounded.screenshot}"
    padding: "0"
    width: "100%"
---

# Design System: AOS-Agent Website

## Overview

**Creative North Star: "把 Agent，放进你的车机。"**

这是独立官网 `site/` 的设计记录，按已经实现并通过独立终审的页面提取。模式为 **Persuade**：先用清楚的产品主张和真实 AAOS 界面建立理解，再提供下载、安装和源码入口。用户明确选定的 Product Design 第三版是视觉依据（[reference.png](reference.png)）；没有重新探索或替换方向。

网站采用白色场域、左对齐的黑蓝双行大标题、下载 APK 与 GitHub 双 CTA、比正文更宽的真实截图及连续功能介绍。版面开放，组件平实，以中文粗体、品牌蓝和产品画面形成记忆点。原生 App 是画面里的产品，不把 App 的深色界面扩散为官网背景。

本文件只约束网站；原生 App 的真来源仍按 [skill-routing.md](../../.agent-rules/rules/skill-routing.md) 映射到既有视觉规范和机制文档。产品定位读取需求分析与统一生态愿景；网站实施事实读取 [website-and-pages.md](../../.agent-rules/mechanisms/website-and-pages.md)。本文件及 `site/.impeccable/design.json` 不替代根目录或原生 App 的设计源。

**Key Characteristics:**

- 白底、黑蓝标题，下载主行动清晰。
- 大幅真实 16:9 AAOS 截图，配可见来源说明。
- 场景标签、细分隔线和开放网格，避免把所有内容装进卡片。
- 自托管中文可变字体、轮廓图标，以及完整键盘状态。

## Colors

单一品牌蓝负责强调与操作，中性白灰负责信息层次；前面的 frontmatter 是网站抽取 token，CSS 实现位于 [site.css](../styles/site.css)。

### Primary

- **品牌蓝**：`blue` 用于标题第二行、主要按钮、活动标签、功能图标和文字链接。
- **悬停深蓝**：`blue-hover` 是主要按钮的悬停色；当前没有独立的 `:active` 色值。

### Neutral

- **白色**：`white` 是页面及导航基底，也是蓝色按钮文字色。
- **墨色**：`ink` 用于主要文字、标题和次要按钮文字。
- **灰蓝文字**：`muted` 用于说明、未选标签、兼容信息与图注。
- **浅分隔线**：`line` 区分标签栏、兼容项、FAQ 与内容分组。
- **浅灰底**：`pale` 承载开发者区；`secondary-bg` / `secondary-hover` 表达次要按钮状态。
- **浅蓝下载区**：`download-bg` 承载页尾重复下载入口。
- **截图深底**：`screenshot-bg` 仅用于真实图片的容器。

**The One Accent Rule.** 网站操作与重点沿用同一个品牌蓝，截图内部颜色属于产品素材，不新增为网页强调色。

sidecar 的八阶 tonalRamp 仅用于面板预览，由已用颜色转换为 OKLCH 后分级生成，不是 CSS 已实现的色阶或新增可用色板；网页仍以本文件 frontmatter 和实际 CSS 为准。

## Typography

**Display Font:** AOS Sans（本地 Noto Sans SC 子集；回退 Noto Sans SC、sans-serif）。
**Body Font:** 同一字体；没有另一套展示字体或网页等宽字体。

字体来自固定版本的 Noto Sans SC 可变字体，站内别名为 AOS Sans，文件 `assets/fonts/aos-sans-sc.woff2`，字重范围（100–900），`font-display: swap`。正文、标题和按钮靠字重及尺度区分，中文和 Agent/AAOS 等拉丁词保持同一字形关系。许可与固定来源见 [README.md](../README.md) 和 [sources.json](../assets/sources.json)。

### Hierarchy

- **Display**：首屏双行标题；默认值见 `display`。最大文字宽度（1100px）。宽屏从（1600px）起字号（112px）。
- **Headline**：区块标题；默认见 `headline`，手机为（30px / 1.35）。末尾下载标题使用（clamp(30px, 3.3vw, 44px)）。
- **Scene title**：场景叙述标题；默认见 `title-scene`，平板（23px），手机（21px），行高维持（1.5）。
- **Body**：基础文字见 `body`。区块说明见 `body-section`，最大宽度（740px）；场景说明最大宽度（390px）、行高（1.9）。
- **Hero body**：默认（clamp(18px, 1.8vw, 24px) / 1.75），最大宽度（950px）；平板（20px），手机（16px / 1.9）。
- **Action / tab labels**：基础见 frontmatter；首屏桌面 CTA（22px），手机按钮（16px）、版本（14px）、标签（15px）。
- **Caption / metadata**：图注（12px / 1.6）；兼容信息默认（16px），平板（13px），手机（12px）；页脚（12px）。

**The Subset Maintenance Rule.** 新增文案后按 README 的页面 HTML、脚本字符及 ASCII 字符集更新字体子集；不要把字体缺字产生的回退视为新的字形风格。

## Layout

采用居中的单一正文容器。默认宽度（min(1280px, 100% − 140px)），左右留白各（70px）；不以固定画布缩放整页。页头高度（88px），非吸顶。首屏正文与后续区块沿同一左边线，主截图独立放宽到（min(1380px, 100% − 64px)），与正文形成层次。

主标题之后依次为说明、双 CTA、兼容信息、截图及来源说明。默认 hero 顶部（18px）、说明顶部（20px）、CTA 顶部（30px）、兼容信息顶部（28px）、主截图顶部（36px）。一般区块纵向 padding 见 frontmatter 的 section scale；功能区起始（44px），安装区以顶部分隔线及（66px）内距衔接。蓝色短线仅标记功能区标题，不是另一个文字层级。

桌面场景区为（.65fr / 1.35fr）两栏、间距（48px）；能力概览四列，安装三列，开发者两等列、间距（100px）；FAQ（.75fr / 1.25fr）、间距（84px）。末尾下载区标题与按钮横向排列。能力行和开发者链接靠细线分组，没有卡片阴影。

### Responsive behavior

| 条件 | 已实现的重排 |
|---|---|
| 宽度 ≥1600px | hero 顶部（50px），主标题（112px） |
| 宽度 ≤1000px | 正文左右（32px），截图左右（16px）；一般区块纵向（68px）；场景（.75fr / 1.25fr）、间距（28px），其余列间距收紧 |
| 宽度 ≤700px | 正文左右（20px），截图左右（12px）；页头（78px）；菜单按钮替代桌面导航；主标题（clamp(42px, 9.5vw, 65px) / 1.2） |
| 宽度 ≤700px | 说明取消桌面强制换行；CTA 自动换行，兼容信息两列；场景图文纵向、能力概览两列、安装单列、开发者与 FAQ 单列、下载区纵向 |
| 宽度 ≤700px | 一般区块纵向（50px）；标签栏可横向滚动；来源说明保留；图片完整等比展示并可放大 |

主图及所有场景截图原始比例（16:9），以 `object-fit: contain` 展示。源稿的较扁截图不作为比例 token；真实图高度使下方功能区后移是确认的适配。日期从正式发布数据生成，源稿日期也不是视觉常量。

## Elevation & Depth

官网没有 `box-shadow`。层次由白底、浅灰/浅蓝区段、细分隔线及真实深色产品画面提供。按钮是平面纯色，不沿用生成图中的高光或渐变。图片放大用原生 modal dialog，深色面板（#101722）、白色文字与半透明遮罩（rgba(0,0,0,.82)）；这属于查看图片的临时状态。

**The Image Owns Depth Rule.** 产品截图自带车机层次；网页容器延续当前的平面和细线表达，不把截图内部的材质当作网页卡片规则。

## Shapes

截图默认圆角见 `rounded.screenshot`；按钮及放大对话框沿用 `rounded.control`。手机截图与按钮分别使用 `screenshot-mobile` 与 `control-mobile`。品牌图标本身来自既有 SVG；页头图标默认（48px），平板（42px），手机（39px），页脚（32px）。标签为无底色、无圆角的文字按钮，选中态以（3px）底线标示。

图标由本地 Tabler SVG mask 提供，跟随文字颜色；基础尺寸（25px），按场景调整到（18–39px），不使用 Unicode 字符替代。侧车机屏幕内的原生 App 控件不属于这些网页形状 token。

## Components

### Buttons

明确、平实的双行动层级。Primary 为品牌蓝/白字；Secondary 为浅灰/墨字。基础 padding 与圆角见 frontmatter，默认最小高度（64px）、图标间距（13px）；首屏桌面按钮水平 padding（30px），手机最小高度（55px）、padding（13px 18px）、图标间距（9px）。开发者区 CTA 最小高度（56px）、字号（17px）。

悬停只变背景色，过渡（.2s）；文字链接悬停增加下划线。所有链接、按钮、summary 与可聚焦面板的 `:focus-visible` 使用品牌蓝（3px）轮廓、偏移（5px）；截图（6px），标签内缩（−5px）。当前没有 disabled、loading 或独立 active 按钮状态，不由记录补造。

### Navigation

品牌左、正文导航右。桌面导航字重（550）、字号（17px）、间距（42px），悬停蓝色、过渡（.18s）。手机菜单通过 `aria-expanded` 与 `is-open` 控制，导航从页头下方（top: 78px）纵向展开；点击入口或 Escape 关闭，Escape 后焦点回菜单按钮。窗口回到（min-width: 701px）时收起手机菜单。没有当前页药丸或滚动定位高亮。

### Scene tabs and panel

四个场景：对话、终端、车辆信息、语音（设备支持时）。选中标签为蓝色、字重（700）、底边（3px）；未选为灰蓝、字重（550），悬停蓝色。左右方向键循环切换，Home/End 跳到首尾；roving tabindex 保留一个 tab 停靠点，tabpanel 的 `aria-labelledby` 跟随选中项。标题、描述、图标、图片、来源说明和文档链接一起切换，无交叉淡入或自动轮播。

### Screenshot viewer

截图按钮占满容器，图片不重绘。桌面悬停和键盘焦点显示“查看大图”提示（opacity 过渡 .2s）；手机隐藏提示文字但保留可访问名称与放大操作。dialog 使用（min(94vw, 1800px)）、最大高度（94vh），图像最大高度（76vh）；手机宽（96vw）。关闭按钮、Escape 或点击 dialog 空白区域关闭，关闭后焦点回到打开它的按钮，打开时禁止背景页面滚动。

### FAQ and open content rows

FAQ 使用原生 details/summary。问题字重（600），默认（18px），手机（16px）；展开时箭头旋转（180deg）、过渡（.2s），答案灰蓝色、行高（1.9）。能力概览、安装步骤与开发者资源使用开放网格和细分隔线；没有输入框、表单、筛选 chip 或通用卡片库，不为侧栏预览虚构这些控件。

### Motion and asset truth

唯一入场动画作用于首屏文字容器：`arrive`（.65s，cubic-bezier(.22,1,.36,1)），从 opacity（.45）及 translateY（12px）到完整显示。默认锚点平滑滚动。系统偏好 `prefers-reduced-motion: reduce` 时取消平滑滚动，并禁用全部 animation 与 transition；首屏动画只在 no-preference 下启用。验收确认了已加载 CSS 分支，未模拟操作系统偏好切换。

首页、终端、车辆面板和语音图来自 AAOS 模拟器真实截图；对话图是明确标记的 UI fixture，语音图展示麦克风权限缺失状态。源路径、许可证及固定版本见 README/sources.json；图注和 alt 保留各自上下文，页面文字不把模拟器或测试内容写成实车证明。发布版本与 APK 地址由构建注入，主路径为 **官网 → 官方 GitHub Release APK → 设备允许的安装方式**；GitHub、三步安装、FAQ 和页尾下载帮助访客完成这个决策，不添加在线模型密钥输入。

## Do's and Don'ts

### Do:

- **Do** 延续已确认第三版的白底、左对齐黑蓝双行标题、下载优先双 CTA 与宽幅真实截图。
- **Do** 从网站 CSS 提取及维护 token；让正文容器、场景网格和手机重排一起工作。
- **Do** 使用原比例截图，保留模拟器、fixture、权限及设备条件说明，并保留放大与焦点返回。
- **Do** 新文案同步检查字体子集；以官方正式版数据渲染下载版本和日期。
- **Do** 后续迭代核对源稿与同尺度实现，同时检查手机、桌面、宽屏、键盘和减少动态效果；验收依据见 design-qa.md 与独立终审。

### Don't:

- **Don't** 为追求生成稿像素分数压扁真实 16:9 截图，或恢复生成稿的错误发布日期。
- **Don't** 把原生 App 深色截图里的颜色、卡片和状态提炼成官网通用 token。
- **Don't** 用虚构实时车辆数据、车型兼容承诺、下载量或能力填补页面证据。
- **Don't** 把网站规范写回根 PRODUCT.md、DESIGN.md 或原生 App 设计文档；网站实现继续采用独立静态 HTML/CSS/JS。

本记录核对了 source CSS/HTML/JS、选定源图、comparison-final.png、桌面/宽屏联系表、mobile-final.png 与 user-1280.png。独立终审 disposition 为 ship；未发现须传承为规范的 craft-floor 缺陷。真实截图比例、来源图注及发布日期的差异按产品事实保留，不当作缺陷或新装饰规则。
