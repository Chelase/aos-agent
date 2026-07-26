# AOSAgent — 设计交付资料包

> 本文件由 Sisyphus（开发 Agent）整理，用于将项目完整上下文交付给专业设计 Agent。
> 设计 Agent 应据此产出 UI 原型和 `design.md` 设计规范。

---

## 1. 项目概述

| 项目 | 值 |
|---|---|
| 名称 | **AOSAgent** |
| 定位 | 运行在 Android Automotive OS 上的原生车载 AI Agent |
| 首发车型 | 五菱星光 2025 款 |
| 开发阶段 | Batch 0 框架已搭建，即将进入 Batch 1 基础功能 |
| 仓库 | `https://github.com/Chelase/aos-agent`（私有） |
| 代码规模 | 8 个 UI 源文件，4 个测试文件 |

### 一句话产品描述

> AOSAgent 是车机的"智能副驾"——开机自启、常驻后台、能与驾驶者自然对话、在屏幕上展现拟人化形象、提供真实 shell 终端、同时展示车辆状态和诊断信息。最终形态是一个可语音交互的车载 AI Agent。

---

## 2. 平台与技术约束（设计必须遵守）

| 约束 | 值 |
|---|---|
| 操作系统 | Android Automotive OS 14+（AAOS） |
| UI 框架 | **Jetpack Compose + Material3**（无 XML 布局） |
| 屏幕方向 | **横屏 Landscape**（车机标准） |
| 分辨率基准 | Automotive 模拟器 1024p 横屏（需适配其他分辨率） |
| 主题 | 默认**深色主题**（夜间驾驶） |
| 交互方式 | **触摸 + 语音**双通道，语音为主要交互手段 |
| 最低 SDK | API 34（Android 14） |
| 性能基线 | 冷启动 < 2s，常驻内存 < 200MB，UI 帧率 > 30fps |
| 驾驶限制 | 驾驶中（CarUxRestrictions）必须简化 UI，禁止复杂交互 |

### 图片资源现状

- Launcher icon：默认 Android 自适应图标（绿色 Android 机器人主题）
- **无自定义品牌图形、Logo、启动画面**
- 所有 UI 当前使用 Material3 默认色板 + 系统默认字体

### 当前主题配置

```kotlin
// Theme.kt — 当前几乎是 Material3 默认值，未做任何品牌定制
private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

@Composable
fun AOSAgentTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
```

```xml
<!-- themes.xml → Compose 不直接使用，仅系统级兜底 -->
<style name="Theme.AOSAgent" parent="@android:style/Theme.DeviceDefault" />
```

---

## 3. 当前已存在的 UI（Batch 0）

### 3.1 首页（HomeScreen）

当前状态是纯粹的功能验证 UI，无设计可言：

```
┌──────────────────────────────────────────────────┐
│ AOSAgent                                         │
│ Android 14 (SDK 34) · SAIC-GM-Wuling / 星光      │
│    · Automotive=true                              │
│                                                  │
│ [进入工程师模式]                                   │
└──────────────────────────────────────────────────┘
```

- 纯 `Column` 布局，24dp padding
- 顶部 app 名称（`headlineMedium`, Bold）
- 中间系统信息摘要（`bodyLarge`）
- 底部按钮进入工程师模式
- 点击按钮切换到工程师模式（简单 `if/else`，无 NavHost）

### 3.2 工程师模式（EngineerModeScreen）

```
┌──────────────────────────────────────────────────┐
│ 工程师模式                                       │
│ Android 版本: 14                                  │
│ API Level: 34                                     │
│ 厂商: SAIC-GM-Wuling                               │
│ 品牌: wuling                                      │
│ 型号: 星光                                        │
│ 设备名: astra_ev                                  │
│ Automotive: true                                  │
│                                                  │
│ [返回]                                            │
└──────────────────────────────────────────────────┘
```

- 纯文本列表，12dp 间距
- 底部 "返回" 按钮切换回首页
- **当前不包含 Car API 扫描、传感器图表、日志查看器等**（留到 Batch 1/2）

### 3.3 当前导航模式

当前使用最简单的 `if/else` 状态切换：

```kotlin
if (engineerModeVisible) {
    EngineerModeScreen(systemInfo, onBackClick)
} else {
    HomeScreen(systemInfo, onEngineerModeClick)
}
```

**后续需要升级到 NavHost / 底部导航 / 侧边栏**，由设计 Agent 决策。

---

## 4. 需要设计的全部界面（按批次）

### Batch 1（基础功能）— 即将开发

| 界面 | 优先级 | 说明 |
|------|--------|------|
| **HomeScreen 升级** | P0 | 从当前纯文本升级为有品牌感的主页 |
| **System Panel** | P0 | 系统状态面板（Android 版本/网络/电源/设备信息） |
| **Engineer Mode 增强** | P0 | 从纯文本列表改为分组卡片式诊断面板 |
| **隐藏入口** | P0 | 工程师模式的进入方式（多次点击/长按等） |
| **启动画面 / Splash** | P1 | App 启动时的品牌过渡 |

### Batch 2（核心功能）— 后续

| 界面 | 优先级 | 说明 |
|------|--------|------|
| **CLI 终端** | P0 | 真实 shell 终端，Compose Canvas 渲染，支持键盘 + 触控 |
| **Agent Chat** | P0 | AI 对话界面（文本气泡 + 工具调用展示 + 状态指示） |
| **语音对话 UI** | P0 | 语音唤醒动画 + TTS 状态指示 + 声波可视化 |
| **Status Bar** | P1 | 系统状态常驻指示（Agent 状态、车辆数据摘要） |
| **工程师模式增强** | P0 | 增加 Car API 扫描结果、服务检查、传感器数据 |

### Batch 3（扩展功能）— 远期

| 界面 | 说明 |
|------|------|
| **桌面宠物** | Compose Canvas → Live2D 可动角色，多动画状态机 |
| **第三方应用管理** | APK 上传、已安装列表、网络搜索、安装管理 |
| **设置页面** | Agent 配置、偏好设置 |
| **驾驶模式简化 UI** | CarUxRestrictions 下的极简安全模式 |

---

## 5. 交互模型（设计 Agent 需重点考虑）

### 核心交互层级

```
主交互路径（语音优先）:
  唤醒 → 对话 → Agent 执行 → TTS 反馈
                        ↕ (可选)
                    屏幕展示结果

次交互路径（触摸）:
  主界面（车辆状态/宠物）
    ├── Agent Chat（历史对话 / 手动输入）
    ├── 终端（shell 交互）
    ├── 系统面板（状态一览）
    ├── 工程师模式（诊断/配置）
    └── 设置
```

### 驾驶模式约束

- 车辆行驶时（`CarUxRestrictions`）UI 必须自动简化
- 简化规则由设计 Agent 定义，例如：
  - 隐藏终端入口（行驶中禁止 shell 操作）
  - 放大触摸目标（> 72dp）
  - 增大字体对比度
  - 限制可展示的信息数量
  - 限制滚动/复杂手势

### 设计原则

| 原则 | 说明 |
|------|------|
| **深色主题优先** | 车机环境默认深色，不刺眼，适配夜间驾驶 |
| **大触摸目标** | 驾驶中操作，目标 > 48dp，建议 56dp+ |
| **高对比度** | 车机屏幕亮度变化大（日间/夜间），需要高对比度文本 |
| **信息层级清晰** | 驾驶中扫视时间 < 1s，信息必须有明确的视觉层级 |
| **一致的设计语言** | 所有页面共享同一套组件库和设计语义 |
| **品牌感** | 科技、可靠、汽车行业，不卡通不轻浮 |

---

## 6. 需要设计 Agent 输出的设计规范（design.md）

期望设计 Agent 产出的核心内容包括：

### 6.1 品牌设计系统

- 品牌色板（Primary / Secondary / Tertiary / Neutral / Error，含深色模式）
- 字体层级（Headline / Title / Body / Label 的 Type Scale）
- 间距系统（4dp 网格，8dp 基准）
- 圆角/阴影规范
- 图标风格指南

### 6.2 布局规范

- 横屏安全区（系统状态栏/导航栏避开区域）
- 页面标准布局模板
- 卡片系统规范
- 列表/网格展示规范

### 6.3 组件规范

- 按钮（Primary / Secondary / Icon / FAB）状态与尺寸
- 状态标签 / Badge
- 信息卡片
- 列表行
- 输入框
- Toast / Snackbar
- Loading / Skeleton
- Dialog / BottomSheet

### 6.4 界面原型

每批次的页面至少产出：
- 视觉定稿（Figma 或同等设计稿）
- 页面间的导航流
- 组件的交互/动效规范
- 深色/浅色模式对比（强制深色，可选浅色）

### 6.5 驾驶模式规范

- 全功能模式 vs 简化模式的切换规则
- 简化模式下的 UI 裁减清单
- 触控目标最小尺寸

---

## 7. 参考架构图

```
┌────────────────────────────────────────────────────────┐
│                   UI LAYER (Compose)                    │
│  Pet Canvas | Terminal | System Panel | Agent Chat      │
├────────────────────────────────────────────────────────┤
│                SERVICE LAYER (Android Services)          │
│  AgentForegroundService | PowerManager | SessionManager │
├────────────────────────────────────────────────────────┤
│                 AGENT CORE LAYER                         │
│  AgentEngine | LLM Manager | Tool Executor | Plugin     │
├────────────────────────────────────────────────────────┤
│                 SYSTEM INTEGRATION LAYER                 │
│  Boot Receiver | Car API | PTY JNI (libpty.so)          │
└────────────────────────────────────────────────────────┘
```

- UI 层不直接调用 System Integration 层（通过 Service / ViewModel 中转）
- 所有 Car API 调用在服务端执行（非 UI 线程）
- 性能基线贯穿所有 UI 功能

---

## 8. 原始参考文件清单

以下是设计 Agent 可能需要的项目源文件索引：

| 文件 | 内容 |
|------|------|
| `app/src/main/.../MainActivity.kt` | Compose 入口 + 页面切换 |
| `app/src/main/.../ui/home/HomeScreen.kt` | 当前首页 |
| `app/src/main/.../ui/engineer/EngineerModeScreen.kt` | 当前工程师模式 |
| `app/src/main/.../ui/theme/Theme.kt` | 当前主题（默认值） |
| `app/src/main/res/values/colors.xml` | 当前色板（Android 默认色） |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Launcher icon 前景 |
| `.agent-rules/mechanisms/architecture-overview.md` | 完整架构说明 |
| `.agent-rules/mechanisms/requirements-analysis.md` | 7 大需求分析 |
| `.agent-rules/mechanisms/engineer-mode-architecture.md` | 工程师模式完整功能设计 |
| `.agent-rules/mechanisms/agent-capabilities.md` | Agent 能力架构 |
| `.agent-rules/dev-plans/roadmap.md` | 4 批次路线图 |

---

## 9. 竞品/参考方向（设计 Agent 可选参考）

AOSAgent 在 AAOS 车机上的独特定位，以下可作为设计参考：

- **Tesla 车机 UI** — 深色主题、信息层级、卡片布局
- **Android Automotive 原生 UI** — Google 官方车机设计语言
- **Claude Code / OpenClaw** — AI Agent 交互范式
- **Termux** — 移动端终端 UI 参考
- **Live2D 样例** — 桌面宠物交互参考

---

> 整理日期：2026-07-08
> 整理者：Sisyphus（Dev Agent）
> 用途：交付给设计 Agent 作为设计输入
