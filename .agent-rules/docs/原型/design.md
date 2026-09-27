# AOSAgent 设计规范 (design.md)

> AOSAgent UI 设计规范 v1.0 — Batch 1 基础功能
> 生成日期：2026-07-08 | 设计工具：ui-ux-pro-max + frontend-design

---

## 1. 设计哲学

### 1.1 核心理念
- **Automotive Precision（车载精密感）**：融合 HUD 数据可视化美学与深色车载主题，传递科技可靠感
- **驾驶安全优先**：所有设计决策以不分散驾驶员注意力为前提
- **语音优先，触摸辅助**：主要交互通道为语音，触摸作为补充
- **信息层级 > 装饰效果**：每一处视觉处理都必须服务于信息传达

### 1.2 设计原则
| 原则 | 规则 | 量化指标 |
|------|------|----------|
| 深色主题 | 默认深色，OLED 友好 | 背景 ≤ #111827 |
| 大触控目标 | 驾驶中可操作 | 交互区域 ≥ 56dp |
| 高对比度 | 日间/夜间均清晰 | 文本对比度 ≥ 4.5:1 |
| 快速扫视 | 1秒内获取关键信息 | 核心信息 ≤ 3 个层级 |
| 一致性 | 全局统一设计语言 | 共享组件库 |

---

## 2. 色彩系统

### 2.1 核心色板
| Token | 色值 | 用途 |
|-------|------|------|
| `--bg-primary` | `#0B1120` | 页面最深背景 |
| `--bg-secondary` | `#111827` | 卡片/面板背景 |
| `--bg-tertiary` | `#1E293B` | 提升表面、输入框背景 |
| `--bg-elevated` | `#273549` | Hover/Active 状态 |
| `--accent-primary` | `#00E5FF` | 品牌主强调色 (Cyan) |
| `--accent-secondary` | `#00BCD4` | 品牌次强调色 (Teal) |
| `--accent-dim` | `rgba(0,229,255,0.15)` | 微弱强调背景 |

### 2.2 语义色
| Token | 色值 | 用途 |
|-------|------|------|
| `--status-success` | `#22C55E` | 在线/正常/已授权 |
| `--status-warning` | `#F59E0B` | 警告/未启动/部分功能 |
| `--status-error` | `#EF4444` | 错误/离线/拒绝 |
| `--status-info` | `#3B82F6` | 信息/中性状态 |

### 2.3 文本色
| Token | 色值 | 用途 |
|-------|------|------|
| `--text-primary` | `#F1F5F9` | 高强调 — 标题、关键数据 |
| `--text-secondary` | `#94A3B8` | 中强调 — 正文、描述 |
| `--text-tertiary` | `#64748B` | 低强调 — 标签、辅助信息 |
| `--text-accent` | `#00E5FF` | 品牌文本 — 链接、强调数值 |

### 2.4 边框与阴影
| Token | 值 | 用途 |
|-------|----|------|
| `--border-subtle` | `rgba(148,163,184,0.1)` | 卡片默认边框 |
| `--border-medium` | `rgba(148,163,184,0.2)` | 分隔线、输入框 |
| `--border-accent` | `rgba(0,229,255,0.3)` | 焦点/激活边框 |
| `--shadow-card` | `0 4px 24px rgba(0,0,0,0.4)` | 卡片投影 |
| `--glow-cyan` | `0 0 20px rgba(0,229,255,0.15)` | 微弱品牌光晕 |
| `--glow-cyan-strong` | `0 0 40px rgba(0,229,255,0.25)` | 强调光晕（Splash等） |

### 2.5 Kotlin/Compose 映射
```kotlin
// Color.kt 映射参考
val AOSPrimary = Color(0xFF0B1120)
val AOSSecondary = Color(0xFF111827)
val AOSTertiary = Color(0xFF1E293B)
val AOSAccent = Color(0xFF00E5FF)
val AOSSuccess = Color(0xFF22C55E)
val AOSWarning = Color(0xFFF59E0B)
val AOSError = Color(0xFFEF4444)
```

---

## 3. 字体系统

### 3.1 字体选择
| 角色 | 字体 | Google Fonts | Fallback |
|------|------|-------------|----------|
| Display / UI | **Outfit** | `Outfit:wght@300;400;600;700` | `sans-serif` |
| Mono / Data | **JetBrains Mono** | `JetBrains+Mono:wght@300;400;500` | `monospace` |

选择理由：Outfit 几何感强、可读性好、支持多字重，适合车载大屏展示。JetBrains Mono 等宽清晰，适合系统数据/终端场景。

### 3.2 Type Scale
| 层级 | 字号 | 字重 | 字体 | 用途 |
|------|------|------|------|------|
| Display | 32sp | 700 | Outfit | 页面主标题 |
| Headline | 24sp | 600 | Outfit | 区块标题 |
| Title | 18sp | 600 | Outfit | 卡片标题 |
| Body | 15sp | 400 | Outfit | 正文 |
| Caption | 13sp | 400 | Outfit | 辅助说明 |
| Data | 14sp | 400 | JetBrains Mono | 系统数值 |
| Data Large | 20sp | 500 | JetBrains Mono | 关键指标 |

### 3.3 Compose Typography 映射
```kotlin
val AOSTypography = Typography(
    displayLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    labelSmall = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 13.sp),
)
// Data text: 使用自定义 TextStyle(fontFamily = JetBrainsMono, ...)
```

---

## 4. 布局规范

### 4.1 横屏基准
- **参考分辨率**: 1920 x 1080 (16:9)
- **安全区内边距**: 24dp 全方向（为系统状态栏/导航栏预留）
- **基准网格**: 8dp

### 4.2 标准页面结构
```
┌────────────────────────────────────────────────────┐
│  24dp Top Safe Zone                                 │
│  ┌──────────────────────────────────────────────┐  │
│  │  Top Bar / Status Bar (optional)              │  │
│  ├──────────────────────────────────────────────┤  │
│  │                                              │  │
│  │  Content Area                                 │  │
│  │  (flex/grid layout)                           │  │
│  │                                              │  │
│  ├──────────────────────────────────────────────┤  │
│  │  Bottom Navigation (56dp height)             │  │
│  └──────────────────────────────────────────────┘  │
│  24dp Bottom Safe Zone                             │
└────────────────────────────────────────────────────┘
```

### 4.3 间距系统
| Token | 值 | 用途 |
|-------|----|------|
| xs | 4dp | 图标与文本间距 |
| sm | 8dp | 相关元素间距 |
| md | 16dp | 卡片内间距、卡片间距 |
| lg | 24dp | 区块间距 |
| xl | 32dp | 页面级间距 |

### 4.4 卡片规范
| 属性 | 值 |
|------|----|
| 圆角 | 16dp |
| 内边距 | 20dp |
| 背景 | bg-secondary (#111827) |
| 边框 | border-subtle |
| 阴影 | shadow-card |
| 卡片间距 | 16dp |

---

## 5. 组件规范

### 5.1 按钮
| 类型 | 高度 | 圆角 | 背景 | 文本色 | 边框 |
|------|------|------|------|--------|------|
| Primary | 56dp | 16dp | accent-primary | bg-primary | 无 |
| Secondary | 56dp | 16dp | transparent | accent-primary | border-accent |
| Icon Button | 56dp | 16dp | bg-tertiary | text-primary | 无 |
| FAB | 72dp | 圆形 | accent-primary | bg-primary | 无 |

### 5.2 状态标签 (StatusBadge)
| 状态 | 背景 | 文本色 | 示例 |
|------|------|--------|------|
| Success | rgba(34,197,94,0.15) | #22C55E | "Running" "Granted" |
| Warning | rgba(245,158,11,0.15) | #F59E0B | "Not Started" "Partial" |
| Error | rgba(239,68,68,0.15) | #EF4444 | "Denied" "Offline" |
| Info | rgba(59,130,246,0.15) | #3B82F6 | "Info" "Pending" |
标签高度: 28dp, 圆角: 14dp (全圆), 水平内边距: 12dp

### 5.3 数据行 (DataRow)
布局: Label (caption/tertiary, 左对齐) + Value (data/mono, 右对齐)
间距: 行间距 12dp
分隔: border-subtle 底部（最后一行无）

### 5.4 底部导航 (BottomNav)
| 属性 | 值 |
|------|----|
| 高度 | 72dp（含安全区） |
| 背景 | bg-secondary + backdrop-filter: blur(20px) |
| Tab 数量 | 5 (Home / Terminal / Chat / Apps / Settings) |
| 图标 | 24dp, 非 active 用 text-tertiary, active 用 accent |
| 标签 | Caption 12sp, active 用 accent |
| 指示器 | active tab 下方 3dp 宽 accent 色条 |

### 5.5 顶部状态栏 (TopBar)
- 高度: 56dp
- 左侧: 页面标题 (Headline)
- 右侧: Agent 状态指示 (脉冲圆点 + 文本)
- 可选: 返回箭头 (左侧)

### 5.6 对话框 (Dialog)
| 属性 | 值 |
|------|----|
| 背景 | bg-secondary |
| 圆角 | 24dp |
| 最大宽度 | 480dp |
| 内边距 | 28dp |
| 遮罩 | rgba(0,0,0,0.7) |

### 5.7 通用组件
- **Loading**: accent 色旋转圆环, 40dp
- **Skeleton**: bg-tertiary, shimmer 动画 (300ms)
- **Empty State**: 居中图标(text-tertiary) + 标题(text-secondary) + 描述(text-tertiary)
- **Error State**: 居中图标(status-error) + 错误文本 + "重试" 按钮(Secondary)
- **Toast/Snackbar**: bg-elevated, text-primary, 底部弹出, 3秒自动消失

### 5.8 语言开关 (LanguageSwitch)
- **可切换态**: 满宽条，bg-tertiary，高 `touchTarget`(56dp)，圆角 16dp；内容 `当前语言(text-primary) | 目标语言(accent-primary)`，取 `AOSDataText.standard`；按压 0.97 / 100ms
- **不可切换态**（车机无框架级 per-app locale，如 Android 13 以下或系统未提供该服务）: 不隐藏、也不留一个按了没反应的死按钮。控件收成满宽 inert 条，只显示当前语言(text-tertiary)，**去掉分隔线与目标语言**——两个相同标签会被读成可点项；下方补 `跟随系统` StatusBadge(NEUTRAL) + 原因文案
- **对比度分工**: 禁用控件本身允许降对比，但原因文案是有效信息，必须用 text-secondary(`onSurfaceVariant`) 而非 text-tertiary，保证深色底上 ≥4.5:1
- **必须给出路**: 原因文案要写清"在哪改"（系统设置），不能只说"不支持"
- **无障碍**: 禁用态的 `contentDescription` 用原因文案，不沿用"切换界面语言"

---

## 6. 页面设计

### 6.1 Splash Screen (启动画面)
- **时长**: 1.5~2s
- **布局**: 全屏居中
- **内容**: Logo(SVG六边形+accent光晕) + 标题 "AOSAgent" (Display) + 副标题 "Intelligent Co-Pilot" (Caption)
- **动画**: Logo 淡入(0~0.5s) → 副标题淡入(0.3~0.8s) → 底部进度条(0.5~2s)
- **背景**: bg-primary + 径向渐变 accent-dim

### 6.2 Home Screen (首页)
- **布局**: 三栏 (品牌区 35% | 快捷操作 40% | 信息面板 25%)
- **品牌区**: Logo + 系统摘要 + 状态标签
- **快捷操作**: 2x2 网格 (Terminal / Chat / System / Engineer)
- **信息面板**: 车辆状态小卡片 + Agent 状态 + 驾驶模式
- **底部**: BottomNav (Home active)

### 6.3 System Panel (系统面板)
- **布局**: 顶部返回 + 标题 → 3列卡片网格
- **6张卡片**: Android系统 / 设备信息 / 网络 / 电源 / 内存CPU / Automotive
- **每张卡片**: Section Header + Info Grid (label-value pairs)
- **底部**: BottomNav (Home active)

### 6.4 Engineer Mode (工程师模式)
- **入口**: 首页 Logo 多次点击(5次) → 确认 Dialog
- **布局**: TopBar + Tab导航 + 内容区
- **Tabs**: Overview | Car API | Services | Sensors (Batch 2/3 逐步开放)
- **Overview**: 指标条 + 服务状态列表 + 权限网格 + 快捷操作按钮行
- **底部**: BottomNav

### 6.5 Driving Mode Overlay (驾驶模式遮罩)
- **触发**: CarUxRestrictions = RESTRICTED
- **效果**: 毛玻璃暗色遮罩覆盖当前页面
- **可见元素**: "DRIVING MODE" 大字 + 3个触控目标(语音/状态/首页)
- **隐藏元素**: 所有导航、复杂操作、终端、工程师模式入口

---

## 7. 导航架构

### 7.1 导航层级
```
BottomNav (全局)
├── Home (首页)
│   ├── → System Panel (系统面板)
│   └── → Engineer Mode (隐藏入口)
├── Terminal (终端) — Batch 2
├── Chat (对话) — Batch 2
├── Apps (应用) — Batch 3
└── Settings (设置) — Batch 3
```

### 7.2 导航方式
- **主要**: BottomNav 底部导航（5 Tab）
- **次要**: 页面内返回箭头（TopBar 左侧）
- **隐藏**: 工程师模式（多次点击 Logo）
- **覆盖**: 驾驶模式（系统事件触发）

---

## 8. 动效规范

### 8.1 原则
- 所有动画 ≤ 300ms（驾驶安全）
- 使用 `prefer-reduced-motion` 降级
- 不使用连续循环装饰动画（仅 Loading 指示器可用循环动画）

### 8.2 标准动画
| 场景 | 类型 | 时长 | 缓动 |
|------|------|------|------|
| 页面切换 | 淡入淡出 | 200ms | EaseInOut |
| 卡片出现 | 从下淡入 | 300ms | EaseOut |
| 按钮点击 | 缩放 0.97 → 1.0 | 100ms | EaseInOut |
| 状态切换 | 颜色渐变 | 200ms | Linear |
| 驾驶模式 | 遮罩淡入 | 300ms | EaseInOut |
| 脉冲指示 | opacity 循环 | 2s | EaseInOut |

---

## 9. 驾驶模式规范

### 9.1 模式定义
| 模式 | 触发条件 | UI 状态 |
|------|----------|---------|
| Parked (停车) | CarUxRestrictions = NONE | 全功能 UI |
| Driving (行驶) | CarUxRestrictions = RESTRICTED | 简化 UI |
| Charging (充电) | 电源状态充电中 | 额外电池信息 |

### 9.2 简化规则（Driving 模式）
- 触控目标放大至 ≥ 72dp
- 隐藏: Terminal 入口、Engineer Mode 入口、Settings、Apps
- 保留: Voice Assistant、Quick Status、Home
- 字体: Body → Title (15sp → 18sp)
- 动画: 全部禁用装饰动画

---

## 10. 交付物清单

| 文件 | 说明 |
|------|------|
| `aosagent-batch1-prototype.html` | Batch 1 全部 5 个页面的 HTML/CSS 原型 |
| `design.md` | 本文件 — 设计规范文档 |
| `design-system/aosagent/MASTER.md` | ui-ux-pro-max 设计系统持久化文件 |

---

## 11. 参考与灵感

- Tesla 车机 UI — 信息层级、卡片布局、深色主题
- Android Automotive 官方设计指南 — 导航规范、触控目标
- HUD/FUI 设计 — 数据可视化美学
- Voice-First 交互 — 语音波形、状态指示

---

> 设计 Agent: SOLO Design | 工具: ui-ux-pro-max + frontend-design
> 版本: v1.0 | 日期: 2026-07-08
