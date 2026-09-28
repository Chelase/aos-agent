# 架构总览

## 结论

AOS-Agent 是一个运行在 Android Automotive OS 上的原生桌面 AI Agent，作为车机的"智能助手"，提供 CLI 终端交互、系统感知、AI 形象展示等功能。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| UI | `ui/pet/` | 桌面宠物 Canvas 渲染 |
| UI | `ui/terminal/` | CLI 终端 Compose 组件 |
| UI | `ui/systempanel/` | 系统信息面板 |
| UI | `ui/chat/` | Agent 对话组件 |
| UI | `ui/theme/` | 主题系统 |
| Service | `AgentForegroundService` | 前台服务保活 |
| Service | `PowerManager` | 电源循环管理 |
| Core | `AgentEngine` | Agent 推理引擎 |
| Core | `LLM Manager` | Gemini/本地 LLM 集成 |
| Core | `Tool Executor` | 工具执行（Car API/Shell 等） |
| System | `Car API` | 车辆数据接口封装 |
| System | `PTY JNI` | 终端 shell 进程桥接 |

## 运行链路

```
[车辆启动]
    │
    ▼
[BootReceiver]
    │ 发 Intent
    ▼
[AgentForegroundService]
    │ 初始化
    ├──► [PowerManager] ── 注册 CarPowerManager 监听
    ├──► [System Monitor] ── 读取车速/电池/系统信息
    ├──► [AgentEngine] ── 加载 LLM 配置
    └──► [Compose UI] ── 显示桌面宠物 + 终端入口
```

### 整体架构（四层）

```
┌────────────────────────────────────────────────────────┐
│                   UI LAYER (Compose)                    │
│  Pet Canvas | Terminal | System Panel | Agent Chat      │
├────────────────────────────────────────────────────────┤
│                SERVICE LAYER (Android Services)          │
│  AgentForegroundService | PowerManager | SessionManager │
├────────────────────────────────────────────────────────┤
│                 AGENT CORE LAYER                         │
│  AgentEngine | LLM Manager | Tool Executor | Plugin     │
├────────────────────────────────────────────────────────┤
│                 DATA LAYER                               │
│  Room DB | DataStore | Vehicle State Cache              │
├────────────────────────────────────────────────────────┤
│             SYSTEM INTEGRATION LAYER                     │
│  Boot Receiver | Car API | PTY JNI (libpty.so)          │
└────────────────────────────────────────────────────────┘
```

### 技术选型

| 领域 | 方案 | 说明 |
|------|------|------|
| 语言 | Kotlin + C/C++ | Android 首选 + PTY JNI 必需 |
| UI | Jetpack Compose + Canvas | 声明式 UI + 程序化绘制 |
| LLM | Gemini API → llama.cpp | Phase 1 云端 → Phase 2 本地 |
| 终端 | Termux terminal-emulator + PTY JNI + Compose Canvas | 真实 shell |
| 宠物 | Compose Canvas → Live2D | Phase 1 几何角色 → Phase 2 动态模型 |
| 数据 | Room DB + DataStore | 结构化数据 + 偏好设置 |
| 网络 | Retrofit + OkHttp | AI API 调用 |

### 开发语言与工具链

- **Kotlin** — Android 开发首选，Jetpack Compose 原生支持
- **C/C++** — PTY 终端实现、JNI 桥接必要
- **minSdk = 31** (Android 12+，目标是"任意车机可装"，存量车机大量停在 AAOS 12/13), **targetSdk = 36**
- **JDK 17+**, **AGP + Kotlin Compiler** 标准 Android 工具链

### 模块结构

```
aos-agent/
├── app/
│   ├── src/main/java/com/aos/agent/
│   │   ├── ui/             # Compose UI (pet/terminal/systempanel/chat/theme/engineer)
│   │   ├── service/        # Android Services (ForegroundService/BootReceiver/PowerManager)
│   │   ├── core/           # Agent Core (engine/llm/tools/plugin)
│   │   ├── system/         # System Integration (carapi/monitor/native)
│   │   ├── data/           # Data Layer (db/store/model)
│   │   └── terminal/       # Terminal Emulator (TerminalEmulator.java/TerminalSession.kt/TerminalCanvas.kt)
│   └── build.gradle.kts
├── natives/pty/            # PTY JNI C code
└── docs/                   # Project documentation
```

## 使用点

- 所有开发任务依赖此架构图理解模块边界
- 新增功能时需遵守四层架构，不跨层调用

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 新增 UI 组件 | `app/src/main/java/com/aos/agent/ui/` | 在对应子目录下创建 Composable |
| 新增系统集成 | `app/src/main/java/com/aos/agent/system/` | 封装对应 API 接口 |
| 新增 Agent 能力 | `app/src/main/java/com/aos/agent/core/` | 扩展 engine 或 tools |

## 关键约束

1. UI 层不能直接调用 System Integration 层，必须通过 Service 或 ViewModel 中转。
2. JNI 代码只能放在 `natives/` 中，App 模块只保留 JNI 声明类。
3. 所有 Car API 调用必须在服务端（非 UI 线程）执行。
4. 数据持久化统一使用 Room DB 或 DataStore，不使用 SharedPreferences。
5. **性能基线**（所有模块必须遵守）：
   - 冷启动到前台服务就绪 < 2 秒
   - 常驻内存 < 200MB（含宠物动画）
   - Compose UI 帧率 > 30fps
   - 数据 checkpoint 写入 < 50ms
   - 终端 I/O 延迟 < 100ms
   - release 未签名整包 ≤ 20.8MB（2026-09-26 实测基线，未做 ABI 拆分）；新增依赖须按 `risk-assessment.md` 评估体积与启动代价
6. **能力探测优先于版本假定**：任何依赖 API 版本或系统特性的功能（Car API、AppFunctions、框架 per-app locale、specialUse 前台服务类型），必须先探测再使用，缺失时返回结构化"不支持 + 原因"，不得假定存在。统一入口是 `system/Capabilities.kt`（`hasCarApi` / `hasPerAppLocale` / `hasAppFunctions`），另有 `i18n/AppLocale.kt` 的 `localeStoreFor()` 作为按版本选实现的样例。`android.car` 用 `useLibrary` 引入 SDK 可选库：编译期有类型检查，不打进 APK。

## 维护方式

- **新增模块**：按四层架构放入对应目录，更新本文件的涉及对象表。
- **调整技术选型**：更新技术选型表并注明变更原因。
- **验证**：确保分层约束不被违反（UI ↔ System 不直连）。

> 更新时间：2026-09-26（minSdk 34 → 31；补能力探测与轻量体积基线）



