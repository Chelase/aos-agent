# UI 设计系统落地与多语言计划

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。本计划只做车载差异化的 UI 表现层与本地化基建，不触碰 Agent 核心架构，不自研跨设备协议。
>
> 配套机制文档：`../mechanisms/ui-design-system.md`
>
> 设计输入：`../docs/原型/design.md`、`../docs/原型/design-system/aosagent/MASTER.md`、`../docs/原型/aosagent-batch1-prototype.html`

## 当前阶段

Batch 0 交付的 UI 是 Material3 裸默认值 + 纯文本 `Column`，与 `design.md` 定义的设计系统完全脱节。本计划把设计系统落到 Compose 代码，并建立中文默认、可切换英文的本地化基建。

本计划是 Batch 1 各 Step 的**共用前置**：`basic-capabilities-plan.md` 的 Step 2（系统感知面板）、Step 3（工程师模式最小版）都要复用这里产出的 token 与组件库，不再各自造样式。

**范围边界**：只重做已存在的两个页面（Home、Engineer Mode）的视觉与交互外壳，不新增功能页面，不接入新数据源。

## 改动范围

### 模块

- `ui/theme/`（新增 token 层）
- `ui/components/`（新增共享组件库）
- `ui/home/`、`ui/engineer/`（重做视觉）
- `i18n/`（新增语言控制）
- `res/values/`、`res/values-en/`、`res/xml/`（字符串与 locale 配置）

### 重点文件

| 文件 | 动作 |
|---|---|
| `ui/theme/Color.kt` | 新增 — design.md §2 全量色彩 token |
| `ui/theme/Type.kt` | 新增 — design.md §3.2 Type Scale |
| `ui/theme/Dimens.kt` | 新增 — design.md §4.3 间距与组件尺寸 |
| `ui/theme/Theme.kt` | 重写 — 强制深色 + 扩展 token 通过 CompositionLocal 下发 |
| `ui/components/AOSSurfaces.kt` | 新增 — 卡片、区块标题、数据行 |
| `ui/components/AOSControls.kt` | 新增 — 按钮、状态标签、语言开关、功能磁贴 |
| `ui/components/AOSLogo.kt` | 新增 — Canvas 绘制六边形品牌标记 |
| `ui/home/HomeScreen.kt` | 重写 — design.md §6.2 三栏布局 |
| `ui/engineer/EngineerModeScreen.kt` | 重写 — design.md §6.4 卡片式诊断面板 |
| `i18n/AppLocale.kt` | 新增 — LocaleManager 封装 |
| `MainActivity.kt` | 修改 — 首启中文兜底 + 语言切换回调 |
| `res/values/strings.xml` | 重写 — 中文为默认资源 |
| `res/values-en/strings.xml` | 新增 — 英文资源 |
| `res/xml/locales_config.xml` | 新增 — 声明 zh-CN / en |

## 步骤

### Step 1. 建立设计 token 层

**内容：** 按 design.md §2/§3/§4 建立 Color、Type、Dimens，Theme 强制深色并通过 CompositionLocal 下发 Material3 覆盖不到的扩展 token（状态色、边框、光晕）。

**验收：** 单元测试断言 token 值与 design.md 一致；`assembleDebug` 通过。

### Step 2. 建立本地化基建

**内容：** 中文进 `values/`（默认），英文进 `values-en/`；`locales_config.xml` + manifest `localeConfig`；`AppLocale` 封装 API 34 框架级 `LocaleManager`；首次启动 `applicationLocales` 为空时写入 `zh-CN`，保证与车机系统语言无关地默认中文。

**验收：** 纯逻辑测试覆盖「空 → zh-CN 兜底」「zh ↔ en 切换」；UI 无硬编码字面量。

### Step 3. 建立共享组件库

**内容：** 卡片、区块标题、数据行、主/次按钮、状态标签、功能磁贴、语言开关、六边形 Logo。全部满足 ≥56dp 触控目标与 4.5:1 对比度。

**验收：** 组件被两个页面复用，无重复样式定义。

### Step 4. 重做首页

**内容：** design.md §6.2 三栏（品牌 / 2x2 功能磁贴 / 信息面板）。未交付能力的磁贴渲染为禁用态，不伪造数据。语言开关置于信息面板。

**验收：** Compose UI 测试覆盖首页要素可见、工程师模式可进入、语言可切换。

### Step 5. 重做工程师模式

**内容：** design.md §6.4 顶栏 + 指标条 + 分组卡片 + 数据行，替换纯文本列表。数据源仍为现有 `SystemInfoProvider`，不新增采集能力。

**验收：** Compose UI 测试覆盖进入 / 返回 / 关键信息可见。

## 验收清单

- [x] 设计 token 与 design.md 一致，且有测试守护
- [x] 默认语言中文，可切换英文并持久生效
- [x] 两个页面无硬编码字符串
- [x] 组件库被复用，无样式重复
- [x] `testDebugUnitTest` 通过（13 个用例）
- [x] `assembleDebug` 通过

## 不在本期做的事

| 项目 | 归属 |
|---|---|
| Splash Screen | Batch 1 P1，独立子计划 |
| System Panel（6 张系统卡片） | Batch 1 Step 2 |
| 服务状态 / 权限真实检查 | Batch 1 Step 3 |
| 自检报告导出 | Batch 1 Step 4 |
| 驾驶模式遮罩（CarUxRestrictions） | Batch 2 |
| 终端 / 对话 / 语音 UI | Batch 2 |
| BottomNav 五 Tab 与 NavHost | 目标页面就绪后再引入，当前只有两页不值得 |
| 自定义字体文件打包 | 见机制文档「关键约束」中的 CJK 字形取舍 |

## 进度

- [x] Step 1. 建立设计 token 层 — 2026-07-26
- [x] Step 2. 建立本地化基建 — 2026-07-26
- [x] Step 3. 建立共享组件库 — 2026-07-26
- [x] Step 4. 重做首页 — 2026-07-26
- [x] Step 5. 重做工程师模式 — 2026-07-26

## 归档

**完成日期：** 2026-07-26

**冒烟结果：**
- `./gradlew :app:testDebugUnitTest` — 13 用例通过（DesignTokens 6 / AppLocaleController 6 / SystemInfoProvider 1）
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL

**遗留问题：**
- Compose UI 测试（`MainActivityTest`）已按新文案更新，但未在 Automotive 模拟器上执行，需真机/模拟器补验。
- design.md §3.1 指定的 Outfit / JetBrains Mono 未打包，当前用系统字族占位；原因见机制文档「关键约束」第 6 条相邻的字体说明与「修改点」表。
- 首页磁贴中终端 / 对话 / 系统面板为禁用占位，随对应 Step 交付后逐个启用。

**回写机制文档：** 新增 `../mechanisms/ui-design-system.md`，并同步 `mechanisms/README.md` 与 `../README.md` §6 索引。
