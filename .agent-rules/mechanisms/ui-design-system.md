# UI 设计系统与多语言机制

## 结论

UI 视觉由集中的设计 token 层统一供给，界面文案全部走 Android 资源；语言状态优先由框架 `LocaleManager` 单点持久化（默认中文、可切英文），平台不支持该能力时降级为跟随系统并把开关渲染成禁用态。

2026-10-02 样式定为 **v5.0 双主题**（`.agent-rules/docs/原型/design.md`，用户钉定）：**日间浅色（蓝白，默认）+ 夜间深色（深空蓝白）**，设置页"外观"卡一键切换；"应用强制深色"约束自 v5.0 废止。结构性约定：首页导航为满宽 DestinationRow 点标行（`AOSDestinationRow`）；对话页纯化——单栏满宽对话流，不常驻运行状态面板，设置入口只在首页；状态色只做"灯"不做大面积底色（§5.9 灯语）；token 成对（`*Light`/`*Dark`），组件禁止直引 `ui/theme/Color.kt` 色值常量，一律经 `MaterialTheme`/`AOSTheme` 取色。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Theme | `ui/theme/Color.kt` | 色彩 token，成对 `*Light`/`*Dark`，对齐 design.md §2（v5.0 双主题） |
| Theme | `ui/theme/Type.kt` | 字号字重 token + `AOSDataText` 等宽样式 + `aosLegendStyle` 图例签名样式 |
| Theme | `ui/theme/Dimens.kt` | `AOSSpacing` / `AOSSizing` 间距与尺寸 token |
| Theme | `ui/theme/Theme.kt` | `AOSAgentTheme(darkTheme)` 双 ColorScheme + 双 `AOSExtendedColors` 经 CompositionLocal 下发 |
| 主题持久化 | `data/store/ThemeStore.kt` | 日/夜偏好存 DataStore（唯一真相，默认日间）；MainActivity 启动同步读取并 `setTheme` 选窗口样式 |
| Components | `ui/components/AOSSurfaces.kt` | 卡片、区块标题、数据行、指标 |
| Components | `ui/components/AOSControls.kt` | 按钮、状态标签/圆点、功能磁贴、语言开关 |
| Components | `ui/components/AOSLabels.kt` | 网络/电池状态 → 本地化文案映射（工程师页与系统面板共用） |
| Components | `ui/components/AOSLogo.kt` | Canvas 绘制的六边形品牌标记 |
| i18n | `i18n/AppLocale.kt` | `AppLanguage` / `LocaleStore`（含 `switchable`）/ `AppLocaleController` / `localeStoreFor()` 能力选择 |
| 入口 | `AOSAgentApplication.kt` | 首启语言兜底（Activity 创建前完成） |
| 入口 | `MainActivity.kt` | 页面切换（Home/Engineer/Chat/Settings/Terminal/SystemPanel）+ 语言标签注入 + 切换回调 |
| 资源 | `res/values/strings.xml` | 中文文案（默认资源） |
| 资源 | `res/values-en/strings.xml` | 英文文案 |
| 资源 | `res/xml/locales_config.xml` | 声明 zh-CN / en，manifest `localeConfig` 引用 |
| 页面 | `ui/home/HomeScreen.kt` | 三栏首页 |
| 页面 | `ui/engineer/EngineerModeScreen.kt` | 卡片式诊断面板 |
| 页面 | `ui/systempanel/SystemPanelScreen.kt` | 系统感知面板：指标条 + 系统/设备/网络/电源四卡 + 5s 轮询刷新 |
| 页面 | `ui/terminal/TerminalScreen.kt`、`ui/terminal/TerminalCanvas.kt` | 终端页与字符网格渲染（详见 `terminal-architecture.md`） |

## 运行链路

```
[启动] AOSAgentApplication.onCreate
   │
   └─► AppLocaleController(localeStoreFor(context)).ensureDefault()
          └─► LocaleStore.currentTag()  ──► LocaleManager.applicationLocales
                 ├─ 空        → apply("zh-CN")：此时尚无 Activity，不触发重建
                 └─ 已有值    → 保留用户选择，直接返回

[启动] MainActivity.onCreate
   │
   └─► setContent { AOSAgentApp }
          │
          ├─► AOSAgentTheme
          │      ├─ MaterialTheme(colorScheme = AOSColorScheme, typography = AOSTypography)
          │      └─ LocalAOSExtendedColors ──► AOSTheme（状态色/三级文本/品牌边框）
          │
          └─► engineerModeVisible ? EngineerModeScreen : HomeScreen
                 │
                 ├─ stringResource(R.string.*) ──► 按当前 locale 命中 values/ 或 values-en/
                 └─ ui/components/* ──► 读 AOSSpacing / AOSSizing / AOSTheme 渲染

[语言切换] MainActivity 取 AppLocaleController.canSwitch → HomeScreen languageSwitchable
   │
   ├─ true  → 开关可点 → onToggle → AppLocaleController.toggle()
   │             ├─► current() → next() 取目标语言
   │             └─► LocaleStore.apply(tag) → LocaleManager.applicationLocales
   │                    └─► 系统重建 Activity → 资源按新 locale 重新解析 → UI 全量刷新
   │
   └─ false → 开关渲染为禁用态（design.md §5.8）：只显示当前语言 + `跟随系统` 标签 + 原因文案
                且 AppLocaleController.switchTo/toggle 直接返回，不写任何本地副本
```

语言切换不走 Compose 状态：写入 `applicationLocales` 后由框架重建 Activity，`stringResource` 自然命中新 locale 的资源，因此无需在 Composable 中持有语言状态。

## 使用点

- `AOSAgentApplication.onCreate` 调用 `ensureDefault()`，保证首启为中文。
  实测：全新安装 + 系统语言 en_US 时，首启界面即为中文，Activity 只创建一次（无重建）。
- `HomeScreen` 的语言卡片调用 `onLanguageToggle`，透传到 `AppLocaleController.toggle()`。
- `HomeScreen` / `EngineerModeScreen` 全部文案通过 `stringResource` 取值，无硬编码字面量。
- `AgentForegroundService.createNotification` 用 `R.string.service_running`，通知文案同样随语言变化。
- 所有 `ui/components/*` 组件读取 token，页面不直接写死色值与尺寸。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 调整色彩/字号/间距 | 先改 `docs/原型/design.md`，再改 `ui/theme/Color.kt`、`Type.kt`、`Dimens.kt` | 同步更新 `DesignTokensTest`，测试是规范与代码的一致性守护 |
| 新增语义色 | `Color.kt` + `Theme.kt` 的 `AOSExtendedColors` | 加字段并在 `AOSExtendedColorValues` 赋值，通过 `AOSTheme` 读取 |
| 新增界面文案 | `res/values/strings.xml` + `res/values-en/strings.xml` | 两份必须成对新增；只加中文会导致英文环境回落中文 |
| 新增支持语言 | `AppLanguage` 枚举 + `res/values-<lang>/` + `locales_config.xml` | 三处同步；`next()` 目前是二元切换，多语言需改为列表轮转或下拉选择 |
| 新增共享组件 | `ui/components/` | 复用 token，触控控件最小高度取 `AOSSizing.touchTarget` |
| 新增等宽文本用途 | `ui/theme/Type.kt` 的 `AOSDataText` | 现有 `standard`（数据行）/ `large`（指标）/ `terminal`（终端网格，字号参与 PTY 行列计算） |
| 打包自定义字体 | `res/font/` + `Type.kt` 的两个 FontFamily 常量 | 字体必须含 CJK 字形，否则中文回退导致中英混排字重不一致 |

## 关键约束

1. **双主题，日间默认**：`AOSAgentTheme(darkTheme)` 日间浅色/夜间深色两套完整下发；主题只有一份真相（`ThemeStore` DataStore，默认日间），禁止另建缓存。
   切换 = 写偏好 → `Activity.recreate()`（与语言切换同链路）；`MainActivity.onCreate` 在 `setContentView` 前同步读偏好并 `setTheme(Theme.AOSAgent[_Dark])` 选对窗口背景，避免启动闪屏错色。
   `values-night/` 已删除（主题跟随用户偏好而非系统夜间模式）；窗口样式在 `values/themes.xml` 内两套并列。
2. **语言只有一份真相**：可切换时状态存于 `LocaleManager`，禁止另建 SharedPreferences 缓存语言；不可切换时（`LocaleStore.switchable == false`）**不写任何本地副本**，直接跟随系统，避免日后能力恢复时两份状态打架。
3. **中文是默认资源**（放 `values/` 而非 `values-zh/`），保证任何未覆盖语言的环境都回落中文，与车机系统语言无关。
4. **切换语言或主题会重建 Activity**，Composable 内的 `remember` 状态会丢失；需要跨切换保留的状态必须提升到 `ViewModel` 或持久层。
5. **触控目标不得低于 56dp**（design.md §1.2 驾驶安全），新增交互组件一律取 `AOSSizing.touchTarget`。
6. **能力缺失渲染为禁用态而非隐藏，且必须说明原因**（首页磁贴、语言开关同理，规范见 `docs/原型/design.md` §5.8）：不伪造数据、不假装功能存在、也不留一个按了没反应的死控件；解释文案取 `onSurfaceVariant` 保证对比度，禁用控件本体才允许降对比。
7. **组件禁止直引色值常量**：`ui/theme/Color.kt` 之外的文件不得 import `*Light`/`*Dark` 色 val，一律经 `MaterialTheme.colorScheme` / `AOSTheme` 取色，保证两主题自动换装（design.md §9.4）。
8. **网格/字符类界面用 Compose 文本组件绘制，不用 `Paint.drawText`**：本机实测（AAOS API 36 x86_64 模拟器）
   `android.graphics.Paint.drawText` 在 Compose 绘制阶段的 nativeCanvas 上静默不渲染（`drawRect` 正常，
   换 `LAYER_TYPE_SOFTWARE` 或 `AndroidView` 亦不解决，且 `AndroidView` 会连带污染同窗口其他 Compose 文字）。
   终端因此按行用 `Text` 渲染、光标用 Canvas 矩形（见 `terminal-architecture.md` 关键约束 6-9）。
9. **含输入框的整屏界面用 `adjustPan`**：`MainActivity` 设 `android:windowSoftInputMode="adjustPan"`，
   否则软键盘弹出会压缩自适应网格区域（终端会把 PTY 尺寸改成极小值导致 shell 重排）。

## 维护方式

- **新增**：先在 `design.md` 定义规范 → 加 token → 建组件 → 页面消费；文案同时补两份 strings.xml。
- **排查**：语言不生效先查 `adb shell cmd locale get-app-locales com.aos.agent`；文案缺失查 `values-en/` 是否漏配同名 key；视觉偏差比对 `DesignTokensTest` 是否仍与 design.md 一致。
- **验证**：`./gradlew :app:testDebugUnitTest`（token 与语言逻辑）、`./gradlew :app:assembleDebug`（资源与编译）、`./gradlew :app:connectedDebugAndroidTest`（页面要素与语言开关，需 Automotive 模拟器）。

> 更新时间：2026-10-02（v4.0 深空蓝白世界 + 对话页纯化；历史版本 v1.0 青 HUD / v2.0 琥珀 / v3.0 竞速荧光均已废弃）
