# UI 样式从零重构计划（v3.0 竞速荧光）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。本计划只替换 aos-agent 车机 UI 的视觉表现层与首页结构，不触碰 Agent 核心、协议与数据层。
>
> 配套机制文档：`../mechanisms/ui-design-system.md`
>
> 设计真来源：`../docs/原型/design.md`（v3.0）、`../docs/原型/design-system/aosagent/MASTER.md`（v3.0）
>
> 前序计划：`ui-design-system-plan-phase2.md`（v2.0 琥珀仪表）——被用户全部推翻，本计划从零重推。

## 当前阶段 / 方向契约

用户指令："全部推翻，从零重构"。淘汰池 = 此前出现过的全部方向（青色 HUD、琥珀仪表、信号橙、冰川银）+ 类别俗套（蓝紫渐变座舱、Tesla 扁平深灰）。

- **选定方向：竞速荧光（Acid Lume）**。绿调石墨黑 `#12140F` 底 + 酸性荧光柠檬 `#C8FF3D` 唯一强调，冷白文本。签名元素是**荧光点（亮/熄）**：像腕表夜光刻度与 F1 维修站灯板，"活着的机器"。
- **结构重推**：首页导航从磁贴网格改为满宽 **DestinationRow 点标行列表**（行即界面，收编时刻表纯排印候选的结构纪律）；品牌标记从仪表刻度环改为**点火灯组**。
- **不变（产品真值与硬约束）**：全部文案、功能、双语机制、56dp 触控下限、≤300ms 动效、强制深色、能力缺失渲染禁用态并说明原因。
- **说明**：concept-seed 仍因仓库规则禁止根目录 PRODUCT.md 无法执行，方向推导手动完成并记录；方向选择题探测一次无回应，按推荐方向继续（与 phase2 相同的替代流程，如实披露）。

## 改动范围

| 文件 | 动作 |
|---|---|
| `ui/theme/Color.kt` | 重写 — v3.0 色板 |
| `ui/theme/Type.kt` | 保留（Legend 样式与字族约束为世界中立工艺） |
| `ui/theme/Dimens.kt` | 保留（12dp 圆角 / 56dp 触控为世界中立工艺） |
| `ui/theme/Theme.kt` | 微调 — 注释与色板自动生效，无结构变化 |
| `ui/components/AOSLogo.kt` | 重写 — 点火灯组（环上灯点，顶点常亮） |
| `ui/components/AOSControls.kt` | 重写 — 移除 AOSActionTile，新增 AOSDestinationRow（点标导航行）；灯点光晕保留 |
| `ui/home/HomeScreen.kt` | 重构 — 中列磁贴网格 → 点标行列表 |
| `ui/chat/ChatScreen.kt` | 微调 — token 换装 |
| `ui/settings/SettingsScreen.kt` | 微调 — token 换装 |
| `ui/engineer/EngineerModeScreen.kt` | 微调 — token 换装 |
| `app/src/test/.../DesignTokensTest.kt` | 重写 — 锚定 v3.0 |

## 验收清单

- [ ] `./gradlew :app:testDebugUnitTest` 通过
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 全部 UI 无裸色值，页面一律 token
- [ ] 文案零改动（行为测试断言不受影响）
- [ ] AAOS 模拟器（1408×792 横屏）四页截图逐图检查
- [ ] 对比度抽查：text-tertiary ≥ 4.5:1，徽章语义色 ≥ 4.5:1

## 不在本期做的事

- 不打包自定义字体（独立决策，token 层已预留常量）
- 不新增功能页面、不动导航数据流
- 不修 ChatTranscript 内置中文串 i18n（另立任务）
- 不做 Splash / BottomNav / 驾驶模式遮罩（后续批次）

## 进度

- [x] 设计真来源 v3.0 落盘（design.md + MASTER.md）
- [ ] token 层重写
- [ ] Logo + 导航行组件重写
- [ ] 首页结构重推 + 三页换装
- [ ] 编译 + 单测通过
- [ ] 模拟器四页截图验收
- [ ] 机制文档同步
