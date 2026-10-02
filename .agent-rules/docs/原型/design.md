# AOSAgent 设计规范 (design.md)

> AOSAgent UI 设计规范 v5.0 — **蓝白浅色为日间主脸 + 日间/夜间双主题可切换**（用户指令）
> 生成日期：2026-10-02 | 设计工具：ui-ux-pro-max + frontend-design + impeccable
> 视觉世界：**晴空仪表（Daylight Instrument）** — 蓝白浅色日间主题 + 深空蓝白夜间主题

---

## 1. 设计哲学

### 1.1 核心理念
- **晴空仪表**：日间主脸是蓝白浅色——白卡片浮在极浅蓝灰底上，仪表蓝唯一强调；夜间切深空蓝白。车机里"原生感"优先，日间可读性优先
- **双主题，用户说了算**：日间（浅色，默认）与夜间（深色）两套完整主题，设置页一键切换；切换立即生效并持久化，跨重启保留
- **对话即对话**：对话页只承载对话流与工具轨迹，不常驻运行状态面板；配置入口一律在首页设置
- **驾驶安全优先**：两套主题都满足对比度下限；夜间主题服务夜间驾驶（低亮度、深底），日间主题服务日间强光环境（浅底、深字）
- **信息层级 > 装饰效果**

版本记录：v1.0 青 HUD → v2.0 琥珀 → v3.0 竞速荧光 → v4.0 深空蓝白（暗色单主题）→ **v5.0 蓝白浅色 + 双主题**（用户钉定）。"强制深色"约束自 v5.0 起废止：日间浅色是主脸，夜间保留深色。

### 1.2 设计原则
| 原则 | 规则 | 量化指标 |
|------|------|----------|
| 双主题 | 日间浅色（默认）+ 夜间深色，设置页切换 | 两套各自完整、对比度达标 |
| 大触控目标 | 驾驶中可操作 | 交互区域 ≥ 56dp |
| 高对比度 | 两主题文本对比度 ≥ 4.5:1 | 徽章语义色同样达标 |
| 快速扫视 | 1秒内获取关键信息 | 核心信息 ≤ 3 个层级 |
| 一致性 | 全局统一设计语言 | 共享组件库 |

---

## 2. 色彩系统（两套）

所有 token 成对定义（`*Light` / `*Dark`），由 `AOSAgentTheme(darkTheme)` 下发。组件与页面只允许经 `MaterialTheme.colorScheme` / `AOSTheme` 取色，禁止直引具体色值常量。

### 2.1 核心色板
| Token | 日间 Light | 夜间 Dark | 用途 |
|-------|-----------|-----------|------|
| `--bg-primary` | `#F5F8FC` | `#0E1116` | 页面最深背景 |
| `--bg-secondary` | `#FFFFFF` | `#161B22` | 卡片/面板背景 |
| `--bg-tertiary` | `#EDF2F8` | `#1D242D` | 输入框/提升表面 |
| `--bg-elevated` | `#E2E9F2` | `#242D38` | Hover/Active |
| `--accent-primary` | `#2E6FD8` | `#5B9BFF` | 仪表蓝，唯一强调色 |
| `--accent-secondary` | `#1E55B0` | `#3E7BD6` | 深仪表蓝 |
| `--accent-dim` | `rgba(46,111,216,0.15)` | `rgba(91,155,255,0.15)` | 微弱强调背景 |

### 2.2 语义色
| Token | 日间 | 夜间 |
|-------|------|------|
| `--status-success` | `#1E7A34` | `#7CC47F` |
| `--status-warning` | `#B45309` | `#E8A33D` |
| `--status-error` | `#C23425` | `#EA6E5E` |
| `--status-info` | `#4A6584` | `#8A9BB0` |
语义色只做"灯"（§5.9）；两套数值均保证在各自徽章底上 ≥ 4.5:1。

### 2.3 文本色
| Token | 日间 | 夜间 |
|-------|------|------|
| `--text-primary` | `#10151C` | `#F2F5F9` |
| `--text-secondary` | `#45505C` | `#A8B3C1` |
| `--text-tertiary` | `#5E6B7A` | `#7C8899` |
| `--text-accent` | 同 accent-primary | 同 accent-primary |

### 2.4 边框与阴影
| Token | 日间 | 夜间 |
|-------|------|------|
| `--border-subtle` | `rgba(16,21,28,0.08)` | `rgba(242,245,249,0.08)` |
| `--border-medium` | `rgba(16,21,28,0.16)` | `rgba(242,245,249,0.16)` |
| `--border-accent` | `rgba(46,111,216,0.40)` | `rgba(91,155,255,0.40)` |
| `--shadow-card` | `0 4px 24px rgba(16,21,28,0.08)` | `0 4px 24px rgba(0,0,0,0.4)` |
| `--glow-blue` | 仅状态灯点微光晕（两主题同规则，强度随主题） | 同左 |

### 2.5 主按钮底/字
- 日间：蓝实底 `#2E6FD8` + 白字 `#FFFFFF`（4.8:1）
- 夜间：蓝实底 `#5B9BFF` + 深蓝墨字 `#0A1428`（7.7:1）

### 2.6 Kotlin/Compose 映射
```kotlin
// Color.kt：*Light / *Dark 成对常量；Theme.kt 按 darkTheme 组装 AOSColorScheme 与 AOSExtendedColors
val AOSBgPrimaryLight = Color(0xFFF5F8FC); val AOSBgPrimaryDark = Color(0xFF0E1116)
val AOSAccentPrimaryLight = Color(0xFF2E6FD8); val AOSAccentPrimaryDark = Color(0xFF5B9BFF)
// …其余同理，见 Color.kt
```

---

## 3. 字体系统
同 v4.0（系统字族 + CJK 约束；Type Scale 32/24/18/15/13 + Legend 0.1em + Data 14/20 mono）。字号字重不随主题切换。

---

## 4. 布局规范
同 v4.0（横屏 8dp 网格、24dp 安全区、卡片 12dp 圆角 20dp 内边距、间距 4/8/16/24/32）。

---

## 5. 组件规范

### 5.1 按钮 / 5.2 状态标签 / 5.3 数据行 / 5.4 BottomNav / 5.5 TopBar / 5.6 Dialog / 5.7 通用组件 / 5.8 语言开关
结构规范同 v4.0，色值经主题下发（§2）。

### 5.9 灯语 (Lamp Language)
- 状态灯点是界面原子：亮（仪表蓝或语义色 + 同色微光晕）/ 熄（text-tertiary 实心）
- 状态色只做灯点/徽章/轨迹标记，不做大面积底色
- 装饰性常亮灯点一屏 ≤ 3；承载状态的行灯不计入
- 品牌标记 = 点火灯组：环上灯点，顶点常亮（随主题取 accent）

### 5.10 导航行 (DestinationRow)
同 v4.0 结构；就绪行灯点亮仪表蓝（随主题）。

---

## 6. 页面设计

### 6.1 Home / 6.3 Chat / 6.4 Engineer
结构与 v4.0 一致（首页三区点标行、对话页单栏纯化、工程师模式指标条+分组卡）。全部页面经主题取色，日间夜间自动换装。

### 6.2 Settings (设置) — v5.0 新增"外观"分组
- **外观卡**：`日间 | 夜间` 切换控件（复用语言开关形态：当前主题 + 目标主题 + 点击切换）
- 切换 → 持久化（DataStore）→ Activity 重建 → 全局换装；跨重启保留
- **默认主题：日间（浅色）**
- 其余分组（模型服务 / MCP 工具来源）同 v4.0

### 6.5 Driving Mode Overlay
遮罩随主题：日间浅遮罩、夜间深遮罩（后续批次实现）。

---

## 7. 导航架构
同 v4.0。设置入口只在首页；对话页无配置入口。

---

## 8. 动效规范
同 v4.0。主题切换为整体换装（Activity 重建），不做补间动画。

---

## 9. 主题机制约束

1. **只有一份真相**：主题状态存 DataStore（`ThemeStore`），禁止另建缓存；与语言机制同构
2. **切换即重建**：写偏好 → `Activity.recreate()`，与语言切换同链路；Compose 内不持有主题状态
3. **启动读取**：`MainActivity.onCreate` 在 `setContentView` 前同步读取主题偏好，先 `setTheme()` 选对窗口背景样式（`Theme.AOSAgent.Light` / `Theme.AOSAgent.Dark`），避免启动闪屏错色
4. **组件禁止直引色值常量**：一切经 `MaterialTheme` / `AOSTheme`；`ui/theme/Color.kt` 之外的文件不得 import 具体颜色 val
5. **对比度双双达标**：两套主题的正文/徽章语义色 ≥ 4.5:1，由 DesignTokensTest 锚定色值、人工抽查对比度

---

## 10. 交付物清单

| 文件 | 说明 |
|------|------|
| `design.md` | 本文件 — 设计规范 v5.0（晴空仪表 + 双主题） |
| `design-system/aosagent/MASTER.md` | ui-ux-pro-max 设计系统持久化文件（同步 v5.0） |

---

## 11. 方向推导记录（v5.0）

用户指令（2026-10-02）："蓝白浅色系，不是暗色系""日间夜间两套主题可切换"。浅色蓝白为日间主脸（默认），v4.0 深空蓝白降级为夜间主题；"强制深色"旧约束废止。参考系：车机系统 UI 的 blue-on-light/day 与 blue-on-dark/night 双模语言（Android Automotive 原生行为）。

---

> 设计 Agent: SOLO Design | 工具: ui-ux-pro-max + frontend-design + impeccable
> 版本: v5.0 | 日期: 2026-10-02
