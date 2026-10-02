# Design System Master File

> **LOGIC:** When building a specific page, first check `design-system/pages/[page-name].md`.
> If that file exists, its rules **override** this Master file.
> If not, strictly follow the rules below.

---

**Project:** AOSAgent
**Generated:** 2026-10-02（v5.0 晴空仪表：蓝白浅色日间主脸（默认）+ 深空蓝白夜间主题，设置页切换）
**Category:** Native Cockpit Blue / Day & Night Dual Theme

---

## Global Rules

### Color Palette（成对 token，经 AOSAgentTheme(darkTheme) 下发）

| Role | Day (Light, default) | Night (Dark) | CSS Variable |
|------|---------------------|--------------|--------------|
| Background | `#F5F8FC` | `#0E1116` | `--color-background` |
| Primary (surface) | `#FFFFFF` | `#161B22` | `--color-primary` |
| Secondary | `#EDF2F8` | `#1D242D` | `--color-secondary` |
| CTA/Accent | `#2E6FD8` | `#5B9BFF` | `--color-cta` |
| Text | `#10151C` | `#F2F5F9` | `--color-text` |

**Color Notes:** 仪表蓝唯一强调；白/浅蓝灰日间，深蓝黑夜间。状态色只做灯点/徽章/轨迹标记：success `#1E7A34`/`#7CC47F` · warning `#B45309`/`#E8A33D` · error `#C23425`/`#EA6E5E` · info `#4A6584`/`#8A9BB0`（日/夜）。装饰性常亮灯点一屏 ≤ 3（承载状态的行灯不计入）。

### Typography

- **Heading/Body:** 几何无衬线（目标 Outfit，暂用系统 sans-serif）；数据一律等宽（目标 JetBrains Mono，暂用系统 monospace）
- **约束:** 目标字体无中文字形，打包含 CJK 覆盖的字体前不得替换系统字族
- **Type Scale:** Display 32sp/700/-0.01em · Headline 24sp/600 · Title 18sp/600 · Body 15sp/400 · Caption 13sp/400 · Legend 13sp/500 + 0.1em 字距 · Data 14sp mono · Data Large 20sp mono（字号字重不随主题）

### Spacing

`--space-xs 4dp` · `sm 8dp` · `md 16dp` · `lg 24dp` · `xl 32dp`

### Shape & Depth

`--radius-card 12dp` · `--radius-badge 14dp` · hairline 描边（日间 `rgba(16,21,28,0.08)` / 夜间 `rgba(242,245,249,0.08)`）· `--border-accent 40% 蓝` · 卡片投影随主题 · 光晕仅状态灯点允许

---

## Component Specs（Compose 语义，实现见 `ui/components/`）

### Lume Point（状态灯点）：亮 = accent/语义色 8dp + 同色 18% 光晕；熄 = text-tertiary 实心
### DestinationRow：满宽 ≥64dp，`灯点 + 名称 + 状态徽章`；就绪点亮蓝、可点；未开放点熄、禁用 + NEUTRAL 徽章
### Primary Button：日间蓝底白字（#2E6FD8/#FFF），夜间蓝底深蓝墨字（#5B9BFF/#0A1428）；Secondary 透明底蓝描边
### Cards：surface 底、12dp 圆角、20dp 内边距、发丝描边；禁止嵌套
### Data Row：左 Caption 标签 + 右 14sp 等宽值右对齐，行距 12dp
### Section Header（Legend）：3dp 蓝刻度短条 + 13sp/500 + 0.1em 字距
### Status Badge：28dp 全圆，语义色 15% 底 + 同色文字
### Inputs：tertiary 底、发丝描边、聚焦转 accent 描边、12dp 圆角
### Chat Page：单栏满宽（TopBar → 对话流 → 输入行），不常驻状态面板，无设置入口
### Theme Switch（设置页"外观"卡）：复用语言开关形态，`当前主题 | 目标主题`，切换持久化并重建

---

## Anti-Patterns (Do NOT Use)

- ❌ 组件/页面直引 `ui/theme/Color.kt` 的具体色值常量（必须经 MaterialTheme / AOSTheme）
- ❌ 只给一套主题适配（两套都必须完整验收）
- ❌ 状态色做大面积底色；卡片嵌卡片；Emoji 当图标
- ❌ 对话页常驻状态面板或页内设置入口
- ❌ 低对比文本（两主题正文/徽章 ≥ 4.5:1）
- ❌ 触控目标 < 56dp；瞬间状态切换；除灯点光晕/呼吸外的循环装饰动画

---

## Pre-Delivery Checklist

- [ ] 视觉值全部来自 token，页面无裸色值
- [ ] 文案走 Android 资源，中英成对
- [ ] 触控 ≥ 56dp；两主题对比度 ≥ 4.5:1
- [ ] 日间/夜间切换：持久化、跨重启、启动不闪错色
- [ ] AAOS 模拟器横屏两套主题分别验收
