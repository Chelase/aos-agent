# UI 样式全量重构计划（v2.0 琥珀仪表）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。本计划只替换 aos-agent 车机 UI 的视觉表现层，不触碰 Agent 核心、协议与数据层，不引入跨设备能力。
>
> 配套机制文档：`../mechanisms/ui-design-system.md`
>
> 设计真来源：`../docs/原型/design.md`（v2.0）、`../docs/原型/design-system/aosagent/MASTER.md`（v2.0）

## 当前阶段 / 方向契约

用户要求"完全重构本项目样式"。替换对象是 v1.0 的"深蓝底 + 青色 HUD"世界。方向决策记录：

- **候选推导**（跨材质家族，类别俗套——青色 HUD、蓝紫渐变座舱、Tesla 扁平深灰——全部排除）：机械仪表夜光（琥珀）、航空夜视、漆器木饰、灯语信号、SBB 时刻表排版。
- **选定方向：琥珀仪表（Amber Instrument）**。暖炭黑 `#151210` 底 + 琥珀金 `#FFB454` 单强调，暖白文本，状态色只做"灯"不做底色。理由：仪表是车主每天注视最久的界面，暖光贴合夜间驾驶且跳出 AI 产品青/蓝/紫收敛。
- **收编**：灯语信号收编为 design.md §5.9（状态色只做灯）；SBB 排版收编为 §5.3 表格纪律；航空夜视因与被替换世界同族（冷青）落选。
- **保留**：全部文案、页面信息架构、交互行为、双语机制、56dp 触控下限、≤300ms 动效上限、强制深色约束不变。
- **说明**：impeccable 的 concept-seed 掷骰因本仓库规则禁止在根目录新建 PRODUCT.md（真来源映射见 `rules/skill-routing.md` §2）无法执行，方向推导按同一方法论手动完成并在此记录；方向选择题已通过结构化提问探测一次，无回应，按推荐方向继续。

## 改动范围

| 文件 | 动作 |
|---|---|
| `ui/theme/Color.kt` | 重写 — design.md §2 v2.0 全量色彩 token |
| `ui/theme/Type.kt` | 调整 — 加 Display/Legend 字距，字族约束注释保留 |
| `ui/theme/Dimens.kt` | 调整 — cardCorner 16→12dp，其余不变 |
| `ui/theme/Theme.kt` | 调整 — 新色值映射进 M3 色板 |
| `ui/components/AOSLogo.kt` | 重写 — 六边形节点图 → 仪表刻度环 |
| `ui/components/AOSSurfaces.kt` | 调整 — Legend 图例标题、卡片 12dp 圆角 |
| `ui/components/AOSControls.kt` | 调整 — 按钮/磁贴/语言开关新色与新圆角 |
| `ui/home/HomeScreen.kt` | 调整 — 暖色光晕背景、磁贴灯语化 |
| `ui/chat/ChatScreen.kt` | 调整 — 工具轨迹灯语标记、状态卡表格纪律 |
| `ui/settings/SettingsScreen.kt` | 微调 — 继承新 token，结构不动 |
| `ui/engineer/EngineerModeScreen.kt` | 微调 — 表格纪律、继承新 token |
| `res/values/colors.xml` | 更新 — `aos_bg_primary` 同步新值 |
| `app/src/test/.../DesignTokensTest.kt` | 重写 — 锚定 v2.0 色值 |
| `../docs/原型/design.md` | 已重写 — v2.0 |
| `../docs/原型/design-system/aosagent/MASTER.md` | 已重写 — v2.0 |

## 验收清单

- [ ] `./gradlew :app:testDebugUnitTest` 通过（DesignTokensTest 锚定 v2.0）
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 全部 UI 无裸色值（页面不直接写 Color(...)，一律 token）
- [ ] 文案零改动（行为测试 MainActivityTest / SettingsScreenTest 的断言不受影响）
- [ ] 对比度抽查：text-tertiary ≥ 4.5:1，正文 ≥ 4.5:1，琥珀底上文字 ≥ 4.5:1
- [ ] AAOS 模拟器横屏人工验收（本机无 adb/模拟器，待车机环境补验——如实标注）

## 不在本期做的事

- 不打包自定义字体（CJK 字体文件是独立决策，token 层已预留两个 FontFamily 常量）
- 不新增页面/功能、不改导航结构（BottomNav 留给后续批次）
- 不修 ChatTranscript 内置中文串的 i18n 问题（属逻辑层文案治理，另立任务）
- 不做驾驶模式遮罩（依赖 CarUxRestrictions 接入，后续批次）

## 进度

- [x] 设计真来源 v2.0 落盘（design.md + MASTER.md）
- [x] token 层重写（Color/Type/Dimens/Theme；错误色提亮为 #EA6E5E 修复徽章对比度 4.2→4.6:1）
- [x] 组件库重写（Logo → 仪表刻度环；StatusDot → 灯语光晕；SectionHeader → Legend 字距；输入框 12dp 圆角）
- [x] 四页重构（Chat 顶栏 Legend + MCP 行文案抽资源；Settings/Engineer 顶栏 Legend；Home 暖光背景经 token 自动生效）
- [x] 编译 + 单测通过（114/114，`assembleDebug` 通过）
- [x] 机制文档同步（`mechanisms/ui-design-system.md` 结论段补 v2.0 世界）
- [ ] AAOS 模拟器横屏人工验收（本机无 adb/模拟器，待车机环境补验）
