# UI 样式从零重构计划（v4.0 深空蓝白）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。只改视觉表现层与对话页结构，不触碰 Agent 核心、协议与数据层。
>
> 配套机制文档：`../mechanisms/ui-design-system.md`
>
> 设计真来源：`../docs/原型/design.md`（v4.0）、`../docs/原型/design-system/aosagent/MASTER.md`（v4.0）
>
> 前序：`ui-design-system-plan-phase2.md`（v2.0 琥珀）、`ui-design-system-plan-phase3.md`（v3.0 竞速荧光）——均被推翻。

## 当前阶段 / 方向契约

用户两条明确指令（2026-10-02）：

1. **对话页纯化**：右侧运行状态面板整体移除，设置入口移出对话页（设置只在首页进入）；对话页只留顶栏 + 满宽对话流 + 输入行。阻断态（未配置模型）以警示行留在输入区上方，文案改为指向首页设置。
2. **色系钉定**："蓝色白色为主色系"，附车机系统 UI 参考图（系统深底 + 蓝强调 + 白字）。落为 **深空蓝白（Deep Space Blue）**：深蓝黑 `#0E1116` 底 + 白文本 + 仪表蓝 `#5B9BFF` 唯一强调。用户钉定方向优先于一切反俗套机制。

**不变**：功能、文案（除阻断指引文案随结构更新）、双语机制、56dp 触控、≤300ms 动效、强制深色、灯语规则（点亮色换仪表蓝）、点标导航行结构。

## 改动范围

| 文件 | 动作 |
|---|---|
| `ui/theme/Color.kt` | 重写 — v4.0 色板 |
| `ui/chat/ChatScreen.kt` | 重写 — 删 StatusColumn/StatusLine/onOpenSettings，单栏满宽 |
| `MainActivity.kt` | 调整 — ChatConsole/SettingsHost 调用链收窄（设置返回首页） |
| `res/values*/strings.xml` | 更新 — chat_need_config 新指引；删除状态栏专用死资源（chat_status_* 等 10 条，中英成对） |
| `ui/theme/Theme.kt`、`ui/components/*`、`ui/home`、`ui/settings`、`ui/engineer` | 无代码变化，token 换装自动生效 |
| `app/src/test/.../DesignTokensTest.kt` | 重写 — 锚定 v4.0 |
| `res/values/colors.xml` | 同步 aos_bg_primary |

## 验收清单

- [x] `./gradlew :app:testDebugUnitTest` 通过（114/114）
- [x] `./gradlew :app:assembleDebug` 通过
- [x] 模拟器（1408×792 主屏）四页截图逐图检查通过：首页点标行、对话页纯化（无右栏/无设置入口）、设置、工程师模式
- [x] 对话页阻断文案指向首页设置（不再提"右侧"）
- [ ] 车机真机复核（待用户安排）

## 不在本期做的事

- 不打包自定义字体；不新增功能；不做 Splash / BottomNav / 驾驶模式遮罩
- 运行状态信息（模型/技能/工具清单）当前无处展示——如需保留诊断价值，后续可在工程师模式加"运行时"分组，本期不做

## 进度

- [x] 设计真来源 v4.0 落盘
- [x] 色板/对话页/调用链/资源同步
- [x] 编译 + 单测 + 模拟器四页验收
- [x] 机制文档同步
