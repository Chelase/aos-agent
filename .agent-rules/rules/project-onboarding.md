# 项目速览

> 本文件由 Agent 在首次分析项目时自动填充，供后续任务快速投入开发。
> 内容来源：项目 README、目录结构扫描、代码分析。

## 1. 项目概述

AOSAgent — 运行在 Android Automotive OS 上的原生车载 AI Agent，目标覆盖车机全功能：开机自启、系统感知、真实 shell 终端、连续语音对话、AI 桌面宠物、工程师调试模式、多厂商 AAOS 适配。

## 2. 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 语言 | Kotlin + Java + C/C++ | Kotlin 2.3.21 / Java 17 |
| UI | Jetpack Compose + Material3 + Canvas | Compose BOM 2025.10.00 |
| 构建 | Android Gradle Plugin + Gradle | AGP 9.2.1 / Gradle 9.4.1 |
| 最低 API | minSdk 31 (Android 12+，覆盖 AAOS 12 起存量车机) | targetSdk 36 |
| 测试 | JUnit 4 + Compose UI Test + Espresso | - |

## 3. 目录结构

```
aos-agent/
├── .agent-rules/          # Agent 规则、机制、计划、交接（四层）
├── app/
│   └── src/main/java/com/aos/agent/
│       ├── ui/            # Compose UI（theme/home/engineer）
│       ├── service/       # Android Services（ForegroundService）
│       ├── receiver/      # BroadcastReceiver（BootReceiver）
│       ├── system/        # 系统集成（SystemInfoProvider）
│       └── core/          # Agent Core（后续批次）
├── gradle/                # Gradle wrapper + 版本目录
├── build.gradle.kts       # 根构建脚本
├── settings.gradle.kts    # 工程设置
├── AGENTS.md              # Codex / 通用 Agent 桥接入口
├── CLAUDE.md              # Claude Code 桥接入口
└── gradlew / gradlew.bat  # Gradle wrapper
```

### 核心目录说明

| 目录 | 作用 |
|------|------|
| `app/src/main/java/.../ui/` | Compose UI 组件（Home、Engineer、Theme） |
| `app/src/main/java/.../service/` | 前台服务（AgentForegroundService） |
| `app/src/main/java/.../receiver/` | 广播接收器（BootReceiver） |
| `app/src/main/java/.../system/` | 系统集成层（SystemInfoProvider/Reader 接口） |
| `.agent-rules/mechanisms/` | 稳定架构与需求文档 |
| `.agent-rules/dev-plans/` | 按批次（0~3）拆分开发计划 |

## 4. 核心模块

### Batch 0 已交付骨架

- `MainActivity.kt` — Compose 入口，首页 ↔ 工程师模式切换
- `ui/home/HomeScreen.kt` — 首页品牌 + 系统摘要 + 工程师入口
- `ui/engineer/EngineerModeScreen.kt` — 只读系统信息展示面板
- `ui/theme/Theme.kt` — Material3 AOSAgent 主题
- `service/AgentForegroundService.kt` — specialUse 前台服务
- `receiver/BootReceiver.kt` — BOOT_COMPLETED 广播 + 前台服务启动
- `system/SystemInfoProvider.kt` — SystemInfoReader 接口 + Android 实现 + fallback

### 核心设计模式

| 模式 | 说明 |
|------|------|
| Reader 接口 | `SystemInfoReader` 可注入接口，纯 Kotlin 测试无需 Android 环境 |
| 委托式广播 | `BootReceiver` 只做委托，不做耗时逻辑 |
| 分层架构 | UI → Service → Agent Core → System Integration（不允许跨层调用） |
| 分批推进 | Batch 0 框架 → Batch 1 基础 → Batch 2 核心 → Batch 3 扩展 |

## 5. 特殊机制

### 分批推进策略

所有需求按 `P0/P1` 产品优先级分类，实现按 `Batch 0~3` 分批次执行。每个批次有独立开发计划文档，不提前实现后续批次。

### 多厂商兼容

探测 → 适配 → 降级策略。工程师模式全量扫描 VehicleProperty，不可用 API 静默降级，不弹窗不崩溃。

### 性能基线贯穿

冷启动 < 2s、常驻内存 < 200MB、UI 帧率 > 30fps、数据 checkpoint < 50ms、终端 I/O < 100ms。每个功能实现时都必须满足。

## 6. 常用开发路径

### 添加新 UI 页面

1. 在 `ui/` 下创建子目录（如 `ui/settings/`）
2. 创建 Composable 组件
3. 在 `MainActivity.kt` 的 `AOSAgentApp` 中添加导航分支
4. 在 `app/src/androidTest/` 添加 Compose UI 测试

### 添加系统感知能力

1. 在 `system/` 下扩展或新增 Provider 类
2. 实现 `collect()` 返回结构化数据，确保 fallback 值
3. 在对应 UI 页面消费数据

### 添加新测试

- 纯逻辑测试 → `app/src/test/`，使用 JUnit 4 + 可注入接口
- UI 测试 → `app/src/androidTest/`，使用 `createAndroidComposeRule`
- 服务/广播测试 → `app/src/androidTest/`，使用 `ApplicationProvider`

### 添加 Gradle 依赖

1. 在 `gradle/libs.versions.toml` 版本目录中声明版本 + 库名
2. 在 `app/build.gradle.kts` 中引用 `libs.xxx.yyy`
3. 不要直接在模块级写版本号

## 7. 注意事项

- 不要使用 `@Suppress("DEPRECATION")` 等静默警告，优先迁移到最新 API
- 不要使用 `as any` / `@ts-ignore` / `@ts-expect-error`（针对 TypeScript，Kotlin 同理避免 `as?` 滥用）
- 所有系统信息读取必须有 fallback，不可用返回 `"Unavailable"`
- Compose UI 不使用 `AppCompatActivity`，使用 `ComponentActivity` + `setContent`
- 前台服务在 Android 15+ 从 BOOT_COMPLETED 启动可能受限，必须有 try-catch fallback

## 8. 构建与运行

```bash
# 编译 Debug APK
./gradlew :app:assembleDebug

# 运行单元测试
./gradlew :app:testDebugUnitTest

# 运行仪器测试（需要 Automotive 模拟器/真机）
./gradlew :app:connectedDebugAndroidTest

# 安装到设备/模拟器
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动 App
adb shell am start -n com.aos.agent/.MainActivity

# 手动触发开机广播验证
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p com.aos.agent
```

> 更新时间：2026-07-08
