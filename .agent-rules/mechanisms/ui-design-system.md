# UI 设计系统与多语言机制

## 结论

UI 视觉由集中的设计 token 层统一供给，界面文案全部走 Android 资源，语言状态由框架 `LocaleManager` 单点持久化，默认中文、可切英文。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Theme | `ui/theme/Color.kt` | 色彩 token，对齐 design.md §2 |
| Theme | `ui/theme/Type.kt` | 字号字重 token + `AOSDataText` 等宽样式 |
| Theme | `ui/theme/Dimens.kt` | `AOSSpacing` / `AOSSizing` 间距与尺寸 token |
| Theme | `ui/theme/Theme.kt` | 强制深色 ColorScheme + `AOSTheme` 扩展色下发 |
| Components | `ui/components/AOSSurfaces.kt` | 卡片、区块标题、数据行、指标 |
| Components | `ui/components/AOSControls.kt` | 按钮、状态标签/圆点、功能磁贴、语言开关 |
| Components | `ui/components/AOSLogo.kt` | Canvas 绘制的六边形品牌标记 |
| i18n | `i18n/AppLocale.kt` | `AppLanguage` / `LocaleStore` / `AppLocaleController` |
| 入口 | `AOSAgentApplication.kt` | 首启语言兜底（Activity 创建前完成） |
| 入口 | `MainActivity.kt` | 页面切换 + 语言标签注入 + 切换回调 |
| 资源 | `res/values/strings.xml` | 中文文案（默认资源） |
| 资源 | `res/values-en/strings.xml` | 英文文案 |
| 资源 | `res/xml/locales_config.xml` | 声明 zh-CN / en，manifest `localeConfig` 引用 |
| 页面 | `ui/home/HomeScreen.kt` | 三栏首页 |
| 页面 | `ui/engineer/EngineerModeScreen.kt` | 卡片式诊断面板 |

## 运行链路

```
[启动] AOSAgentApplication.onCreate
   │
   └─► AppLocaleController(SystemLocaleStore(context)).ensureDefault()
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

[语言切换] HomeScreen 语言开关 onToggle
   └─► AppLocaleController.toggle()
          ├─► current() → next() 取目标语言
          └─► LocaleStore.apply(tag) → LocaleManager.applicationLocales
                 └─► 系统重建 Activity → 资源按新 locale 重新解析 → UI 全量刷新
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
| 打包自定义字体 | `res/font/` + `Type.kt` 的两个 FontFamily 常量 | 字体必须含 CJK 字形，否则中文回退导致中英混排字重不一致 |

## 关键约束

1. **应用强制深色**，不提供浅色模式；`AOSAgentTheme` 无 `darkTheme` 参数，`values-night/themes.xml` 与 `values/themes.xml` 取值一致。
   系统兜底主题必须继承 `Theme.DeviceDefault.NoActionBar`：`DeviceDefault` 自带浅色 ActionBar，
   会在深色界面顶部压出一条浅蓝标题栏（已实机复现并修正）。
2. **语言只有一份真相**：状态存于 `LocaleManager`，禁止另建 SharedPreferences 缓存语言，否则与系统 per-app locale 记录冲突。
3. **中文是默认资源**（放 `values/` 而非 `values-zh/`），保证任何未覆盖语言的环境都回落中文，与车机系统语言无关。
4. **切换语言会重建 Activity**，Composable 内的 `remember` 状态会丢失；需要跨切换保留的状态必须提升到 `ViewModel` 或持久层。
5. **触控目标不得低于 56dp**（design.md §1.2 驾驶安全），新增交互组件一律取 `AOSSizing.touchTarget`。
6. **未交付能力渲染为禁用态**而非隐藏（首页磁贴），不伪造数据也不假装功能存在。

## 维护方式

- **新增**：先在 `design.md` 定义规范 → 加 token → 建组件 → 页面消费；文案同时补两份 strings.xml。
- **排查**：语言不生效先查 `adb shell cmd locale get-app-locales com.aos.agent`；文案缺失查 `values-en/` 是否漏配同名 key；视觉偏差比对 `DesignTokensTest` 是否仍与 design.md 一致。
- **验证**：`./gradlew :app:testDebugUnitTest`（token 与语言逻辑）、`./gradlew :app:assembleDebug`（资源与编译）、`./gradlew :app:connectedDebugAndroidTest`（页面要素与语言开关，需 Automotive 模拟器）。

> 更新时间：2026-07-26
