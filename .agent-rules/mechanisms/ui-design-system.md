# UI 设计系统与多语言机制

## 结论

v6 原生 UI 以 `.impeccable/mocks/v6/` 七页为视觉依据，通过共享 Compose 外壳、主题 token 与控件表达六个目的地及全屏驾驶遮罩；真实能力、语言和主题仍由现有业务机制决定。视觉真来源为 [design.md](../docs/原型/design.md) 与 [MASTER.md](../docs/原型/design-system/aosagent/MASTER.md)，实施状态见 [phase6](../dev-plans/ui-design-system-plan-phase6.md)。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Theme | `ui/theme/Color.kt`、`Theme.kt`、`Type.kt`、`Dimens.kt` | 双主题语义色、系统 CJK 字体、等宽数据和共享尺寸 |
| Components | `ui/components/AOSAppShell.kt` | 六目的地导航轨、Back、标题和自适应双栏 |
| Components | `AOSSurfaces.kt`、`AOSControls.kt`、`AOSLabels.kt`、`AOSLogo.kt` | 平面卡片、指标、按钮、状态、双语映射和品牌图形 |
| 入口 | `MainActivity.kt` | 唯一装配点；页面保存状态、常驻 ChatConsole、设置草稿及遮罩 |
| 页面 | `ui/home/`、`chat/`、`terminal/`、`systempanel/`、`settings/`、`engineer/` | 六个目的地消费共享视觉与业务状态 |
| 数据 | `ui/state/SystemSnapshot.kt` | 首页和系统面板共用采集口径；VHAL 电量合并与 5 秒刷新 |
| 草稿 | `ui/settings/SettingsDraft.kt` | 当前 UI 会话内未保存的模型、MCP、名字与密钥输入 |
| 驾驶 | `ui/voice/DriveVoiceMask.kt` | 覆盖外壳，拦截触控，隐藏底页无障碍语义 |
| 偏好 | `data/store/ThemeStore.kt`、`i18n/AppLocale.kt` | 手动主题 DataStore；框架 LocaleManager 单点语言状态 |
| 资源 | `res/values*/strings.xml`、`res/xml/locales_config.xml` | 默认中文、英文及支持语言声明 |
| 图标 | `artwork/aos-agent-icon.svg`、`res/drawable/ic_launcher*` | 首版环形灯组的蓝白改色母版、自适应前景/背景和单色层 |

## 运行链路

```text
[Application] ensureDefault → LocaleManager（空值设 zh-CN，否则保留）
[Activity] ThemeStore 读手动主题 → setTheme → AOSAgentTheme
  └─ [AOSAgentApp] SaveableStateHolder + remember SettingsDraft
      ├─ [AOSAppShell] 六目的地导航轨 → 当前页面 → theme/components
      │   ├─ ChatConsole 始终组合 → entries/request scope/voice callbacks
      │   │   └─ 仅 Chat 可见时渲染 ChatScreen（输入/滚动按目的地保存）
      │   ├─ Home/SystemPanel → rememberSystemSnapshot
      │   │   └─ collect + VHAL 电量 → 5s 刷新；离页释放连接/定时任务
      │   └─ TerminalScreen → Activity 作用域 TerminalViewModel → PTY
      └─ 受限且非首页 → DriveVoiceMask 覆盖全壳 → 语音/返回首页
[偏好操作] 语言 → LocaleManager / 主题 → ThemeStore → Activity 重建
```

导航切换保留 ChatConsole 的会话、在途请求和语音回调；各目的地的可保存 UI 状态由 `SaveableStateHolder` 保留。设置草稿只由根组合的 `remember` 持有，因此跨导航保留，未承诺跨 Activity 重建保留。API Key 不进 saved bundle、日志或另一份持久化存储。对话记录也未实现跨 Activity 重建持久化。

## 使用点

- 六页共用导航轨；系统 Back 从非首页返回首页；驾驶受限时终端和工程师入口禁用。
- 首页和系统面板使用同一采集函数而非同一个常驻对象；两者显示真实设备信息、车辆电量和缺失状态，面板支持手动刷新。
- 设置与工程师页通过 `AOSAdaptivePanes`：可用宽度至少 1100dp 且字体缩放不超过 1.2 才双栏独立滚动，否则纵向滚动。
- 共享卡片圆角 20dp、内边距 28dp；常规触控至少 72dp、驾驶返回至少 88dp；导航轨宽度 112dp，窄横屏 96dp。
- 语言入口调用 `AppLocaleController`；通知和页面文案读取 Android 资源。平台不支持应用语言切换时保留当前语言并说明禁用原因。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 调整视觉体系 | 映射的 `design.md`、`MASTER.md` → `ui/theme/` | 先同步规范，再改 token；核对 `DesignTokensTest` 与双主题截图 |
| 新增目的地或共用布局 | `AOSAppShell.kt` → `MainActivity.kt` → 页面 | 由唯一装配点接线，复用导航、标题与承载面，不复制业务状态 |
| 新增语义色/控件 | `Theme.kt` / `Color.kt`、`ui/components/` | 通过 MaterialTheme/AOSTheme 下发；页面仅消费语义角色 |
| 调整草稿和会话保留 | `SettingsDraft.kt`、`MainActivity.kt` | 区分导航保留、重建保留和持久化；密钥保持当前内存边界 |
| 修改数据展示 | `SystemSnapshot.kt`、`AOSLabels.kt` | 保持 VHAL 合并、缺失原因与轮询释放；不从原型复制示例数字 |
| 新增文案/语言 | 两份 strings、`AppLanguage`、`locales_config.xml` | 中文英文成对；多语言再同步枚举和轮转逻辑 |
| 调整图标或终端字体 | `artwork/README.md`、`ic_launcher*` / `AOSDataText.terminal` | 图标保持同源几何；终端字号关联 PTY 行列，遵循终端机制 |

## 关键约束

1. **颜色与排版单点供给**：页面通过 MaterialTheme/AOSTheme 取色，禁止直引 `*Light/*Dark`；尺寸/字号复用 theme token。卡片平面无阴影，状态用文字加语义色。
2. **手动双主题、日间默认**：ThemeStore 是唯一偏好真相；切换重建 Activity，创建内容前选择对应窗口主题。没有自动夜间策略，不能因示例原型引入系统夜间跟随。
3. **语言单点、默认中文**：可用时仅 LocaleManager 持久化；不可用时直接跟随系统、不建副本。默认中文在 `values/`，英文在 `values-en/`。
4. **状态边界显式**：导航保留不等于重建持久化；API Key 草稿只在内存。终端会话继续由 Activity 作用域 ViewModel 持有，布局和导航不得另建 PTY 生命周期。
5. **驾驶与能力如实表达**：触控至少取 72dp token；遮罩阻断整个外壳及底页无障碍访问。缺失能力禁用并说明原因，不能藏起或展示虚构数据；禁用原因用可读的次级文本色。
6. **终端渲染与键盘不变量**：字符用 Compose Text、光标用 Canvas，不用 nativeCanvas 的 Paint.drawText；窗口保持 `adjustPan`，避免软键盘压缩网格并错误调整 PTY，详见 `terminal-architecture.md`。

## 维护方式

- **新增**：先读映射设计规范和对应业务机制；token → 共享组件 → 页面装配；文案补齐中英；侧边栏与系统 Back 一并走查。
- **排查**：语言先查 `adb shell cmd locale get-app-locales com.aos.agent` 和英文资源；主题查 ThemeStore 与窗口样式；跨导航状态查 ChatConsole 是否仍组合、草稿是否由根持有；数据查 VHAL 合并与释放。
- **验证**：运行 `:app:testDebugUnitTest`、`:app:assembleDebug` 和相关 `:app:connectedDebugAndroidTest`；在 AAOS 模拟器核对六页及遮罩、双主题、英文、字体 1.3、关闭动画、输入/滚动跨导航保留及真实禁用原因。截图证据与验收结论分开记录，未完成的最终验证留在 phase6 清单。

> 更新时间：2026-10-18
