# UI 双主题计划（v5.0 晴空仪表：日间浅色 + 夜间深色）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。只改视觉表现层与主题机制，不触碰 Agent 核心、协议与数据层。
>
> 配套机制文档：`../mechanisms/ui-design-system.md`
>
> 设计真来源：`../docs/原型/design.md`（v5.0）、`../docs/原型/design-system/aosagent/MASTER.md`（v5.0）
>
> 前序：phase2（v2.0 琥珀）、phase3（v3.0 竞速荧光）、phase4（v4.0 深空蓝白暗色单主题）——用户指令迭代。

## 当前阶段 / 方向契约

用户指令（2026-10-02）："蓝白浅色系，不是暗色系""日间夜间两套主题可切换"。

- **日间主脸（默认）**：晴空仪表——极浅蓝灰底 `#F5F8FC` + 白卡片 + 仪表蓝 `#2E6FD8` 唯一强调 + 深蓝墨文本；白底徽章语义色全部按 4.5:1 校准（success `#1E7A34` / warning `#B45309` / error `#C23425` / info `#4A6584`）。
- **夜间主题**：沿用 v4.0 深空蓝白整套（`#0E1116` 底 + `#5B9BFF` 强调）。
- **切换机制**：设置页"外观"卡（复用语言开关形态）→ `ThemeStore`（DataStore，唯一真相，默认日间）→ `Activity.recreate()`；启动时同步读偏好并 `setTheme` 选对窗口背景样式，杜绝启动闪屏错色；`values-night/` 已删除（主题跟用户偏好而非系统）。
- **旧约束废止**："应用强制深色"自 v5.0 起废止（用户明确要求）。
- **不变**：功能、文案（新增主题切换文案，中英成对）、双语机制、56dp 触控、≤300ms 动效、灯语规则（点亮色随主题）、组件禁止直引色值常量。

## 改动范围

| 文件 | 动作 |
|---|---|
| `ui/theme/Color.kt` | 重写 — 成对 `*Light`/`*Dark` 色板 |
| `ui/theme/Theme.kt` | 重写 — `darkTheme` 参数 + 双 ColorScheme + 双扩展色 CompositionLocal 下发 |
| `ui/components/AOSLogo.kt` | 调整 — 直引色值改主题取值 |
| `ui/components/AOSControls.kt` | 调整 — DestinationRow 灯点底色改 `AOSTheme.accentDim` |
| `data/store/ThemeStore.kt` | 新增 — 日/夜偏好持久化 |
| `ui/settings/SettingsScreen.kt` | 调整 — 新增"外观"分组 + 主题切换卡（darkTheme/onToggleTheme 参数带默认值，测试兼容） |
| `MainActivity.kt` | 调整 — 启动 setTheme 选窗口样式 + 切换回调（persist + recreate） |
| `res/values/themes.xml` | 重写 — `Theme.AOSAgent` / `Theme.AOSAgent.Dark` 两套窗口样式 |
| `res/values-night/` | 删除 — 主题跟用户偏好，不跟系统 |
| `res/values/colors.xml` | 更新 — `aos_bg_primary`（浅）+ `aos_bg_primary_dark` |
| `res/values*/strings.xml` | 新增 — settings_section_appearance / theme_label / theme_light / theme_dark / theme_switch_description（中英成对） |
| `app/src/test/.../DesignTokensTest.kt` | 重写 — 双色板锚定 |

## 验收清单

- [x] `./gradlew :app:testDebugUnitTest` 通过（114/114）
- [x] `./gradlew :app:assembleDebug` 通过
- [x] 模拟器验收：默认启动即日间浅色（home-light / settings-light）
- [x] 设置页切夜间：立即换装（settings-dark / home-dark）
- [x] 持久化：force-stop 后冷启动保持夜间（home-dark-restart）
- [ ] 车机真机复核（待用户安排）

## 不在本期做的事

- 不做"跟随系统"第三态（用户只要求两套可切换；后续需要时加三段开关）
- 不打包自定义字体；不做 Splash / BottomNav / 驾驶模式遮罩

## 进度

- [x] 设计真来源 v5.0 落盘
- [x] 双色板 + 双主题下发 + 组件去直引
- [x] ThemeStore + 设置页切换 + 启动窗口样式
- [x] 编译 + 单测 + 模拟器双主题验收（含持久化）
- [x] 机制文档同步（强制深色约束废止）
