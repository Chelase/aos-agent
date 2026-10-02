# 终端模块实施计划

> 配套机制文档：`.agent-rules/mechanisms/terminal-architecture.md`
>
> 参考实现：[Termux](https://github.com/termux/termux-app) (GPL-2.0)

## 当前阶段

本计划从属于 `core-agent-plan.md`（Batch 2：核心功能），并作为该计划 Step 1 的子计划直接执行。

MVP 专项（`mvp-core-plan.md`）已于 2026-10 完成，本计划 **Phase 1（核心终端）自 2026-10-02 起实施**。
Phase 2/3 仍只留计划不编码。

### Phase 1 实施决策（2026-10-02）

| 决策 | 内容 | 原因 |
|---|---|---|
| 源码引入方式 | 直接下载 termux-app v0.118.1 tag 的 `terminal-emulator` 模块源码，包名整体改 `com.aos.agent.terminal` | 无 Maven 包；整目录拷贝后 sed 改包名，文件清单见下 |
| `TerminalSession` 保留 Java | 不改写为 Kotlin，仅改包名与 JNI 库名（`libpty.so`） | 原计划「改写为 Kotlin」风险高（约 600 行线程模型），保留 Java 便于后续从 Termux 上游同步；其余新增层（Canvas/ViewModel）为 Kotlin |
| JNI 代码位置 | `app/src/main/cpp/pty.c` + 同目录 `CMakeLists.txt`（而非 `natives/pty/`） | AGP 默认 externalNativeBuild 目录，零额外配置 |
| 渲染 | Compose `Canvas` + `drawIntoCanvas` 走 `Paint` 绘制字符网格，`FontFamily.Monospace`；不引 Termux `terminal-view` | 机制文档要求 Compose 渲染；native Canvas 性能可控 |
| 配色 | 终端背景取 `MaterialTheme.colorScheme.surface`、默认前景取 `onSurface`，ANSI 16 色用 Termux 默认调色板，光标取 `AOSTheme` 主色 | 遵守 ui-design-system 约束 7（组件禁止直引 Color.kt）；ANSI 色属内容色例外 |
| 输入 | 底部 InputBar（单行输入 + 发送，含换行即多行粘贴）+ Tab / Ctrl+C 快捷键按钮；画布纵向拖拽翻回滚 | Phase 1 范围，不做软键盘终端仿真 |

Phase 1 文件清单：

| 文件 | 动作 |
|---|---|
| `terminal/*.java`（Termux 10 文件） | 引入，改包名，`JNI.java` 指向 `libpty` |
| `cpp/pty.c`、`cpp/CMakeLists.txt` | 新增 |
| `app/build.gradle.kts` | 加 externalNativeBuild/ndkVersion |
| `terminal/TerminalViewModel.kt`、`terminal/TerminalCanvas.kt`、`ui/terminal/TerminalScreen.kt` | 新增 |
| `ui/home/HomeScreen.kt` | 放开终端入口 |
| `res/values*/strings.xml` | 文案成对新增 |

## 改动范围

| 模块 | 文件 | 改动类型 |
|---|---|---|
| `terminal/` | `TerminalEmulator.java` | 新增（从 Termux 引入） |
| `terminal/` | `TerminalSession.kt` | 新增（改编自 Termux） |
| `terminal/` | `TerminalCanvas.kt` | 新增（Compose Canvas 自研） |
| `terminal/` | `TerminalViewModel.kt` | 新增 |
| `natives/pty/` | `pty.c` | 新增 |
| `natives/pty/` | `CMakeLists.txt` | 新增 |
| `app/` | `build.gradle.kts` | 修改（添加 CMake 配置） |

## 步骤

### Phase 1: 核心终端（2-3 天）

**步骤 1：引入 Termux terminal-emulator 源码**
- 拷贝 `termux-app/terminal-emulator/src/main/java/com/termux/terminal/` 到 `app/src/main/java/com/aos/agent/terminal/`
- 文件清单：`TerminalEmulator.java`、`TerminalBuffer.java`、`TerminalSession.java`、`TerminalOutput.java`、`ByteQueue.java`、`TranscriptScreen.java`、`JNI.java`
- 无需修改 Java 文件，直接可用
- **验收**：Java 编译通过，无依赖缺失

**步骤 2：实现 JNI PTY 层**
- 新建 `natives/pty/pty.c`（约 120 行 C）：
  - `openpty()` → 获取 master/slave FD
  - `fork()` + `exec()` → 启动 `/system/bin/sh`
  - `read()` / `write()` → 与子进程通信
  - `waitpid()` → 进程退出监控
- 新建 `natives/pty/CMakeLists.txt`
- 修改 `app/build.gradle.kts` 添加 CMake 配置
- **验收**：`libpty.so` 编译成功

**步骤 3：适配 TerminalSession**
- 基于 Termux 的 `TerminalSession.java` 改写为 Kotlin
- 适配包名为 `com.aos.agent.terminal`
- 将 JNI 调用指向我们自己编译的 `libpty.so`
- 保留双 ByteQueue 线程模型
- **验收**：Session 可启动 shell 进程

**步骤 4：实现 TerminalCanvas (Compose)**
- 新建 `TerminalCanvas.kt`
- 基于 Termux `TerminalView` 的逻辑，用 `Canvas { ... }` 绘制
- 实现：背景绘制 → 逐 cell 字符渲染 → 光标闪烁 → 滚动
- 实现触控滚动
- **验收**：终端可显示 shell 输出

**步骤 5：实现 TerminalViewModel**
- `StateFlow<ScreenState>` 驱动 UI
- Session 生命周期管理
- 输入事件转发 → Session
- **验收**：ViewModel 层与 Compose UI 正常绑定

**步骤 6：端到端验证**
- 启动终端 → 显示 shell prompt
- 输入 `echo hello` → 输出 `hello`
- 输入 `ls /` → 显示根目录文件列表
- 输入 `exit` → 终端关闭
- **验收**：真实 shell 可用

### Phase 2: 终端增强（3-5 天）

| 子任务 | 说明 |
|--------|------|
| 颜色主题系统 | Solarized / Dracula / Classic |
| 触控交互 | 双指缩放、长按选择、复制粘贴 |
| 多 Session | 分屏 / Tab 切换 |
| 命令历史持久化 | Room DB 存储 |
| Tab 补全 | 基于 shell 补全 |

### Phase 3: Agent 终端融合（1-2 周）

| 子任务 | 说明 |
|--------|------|
| AI 辅助命令 | NL→shell 翻译 |
| 命令审计 | 危险命令确认拦截 |
| 车辆状态嵌入提示 | 电量/速度等动态 PS1 |
| MCP 协议集成 | 终端作为 Agent 的工具通道 |

## 关键决策

### 为什么引入 Termux 源码而非 Gradle 依赖？
Termux 的 `terminal-emulator` 模块没有发布 Maven 包。方式选择：
- **Phase 1**：手动拷贝源码
- **长期**：Git subtree 引入，便于同步上游更新

### 权限说明
- `FOREGROUND_SERVICE` — 后台 shell 进程保活
- 无 `INTERNET` 权限要求
- PTY 通过 App 自身进程创建，无需额外权限

## 验收清单

- [x] 终端显示 shell prompt
- [x] 可执行 `echo`、`ls`、`cat` 等基础命令
- [x] 终端输入无延迟
- [x] 长输出可滚动查看
- [x] 多行粘贴正常（发送前 `\n` → `\r`）
- [x] `exit` 正常关闭终端（并给出退出码与重新启动入口）

## 不在本期做的事

- 颜色主题切换
- 触控选择/复制
- 分屏多 Session
- AI 命令补全

## 进度

- [x] 步骤 1: 引入 Termux 源码 — 2026-10-02（v0.118.1 `terminal-emulator` 14 文件，包名改 `com.aos.agent.terminal`）
- [x] 步骤 2: JNI PTY 层 — 2026-10-02（`app/src/main/cpp/pty.c` + CMake，`libpty.so` 出 arm64-v8a / x86_64）
- [x] 步骤 3: TerminalSession 适配 — 2026-10-02（保留 Java，仅改包名与 `loadLibrary("pty")`）
- [x] 步骤 4: 渲染层 — 2026-10-02（**改为 Compose `Text` 逐行 + Canvas 光标**，原因见机制文档关键约束 6）
- [x] 步骤 5: TerminalViewModel — 2026-10-02（`AndroidViewModel` + `StateFlow`，会话挂 Activity 作用域，语言/主题切换不掉线）
- [x] 步骤 6: 端到端验证 — 2026-10-02（AAOS API 36 x86_64 模拟器实测，见下）

## 归档

**完成日期：** 2026-10-02（Phase 1）

**端到端实测（Automotive_1408p_landscape_with_Google_Play）：**

| 项 | 结果 |
|---|---|
| 提示符 | `:/data/user/10/com.aos.agent/files $` + 块光标 |
| `echo hello` | 回显 `hello` + 新提示符 |
| `ls h`（不存在路径） | `ls: h: No such file or directory` |
| 历史滚动 | 多条命令记录保留在屏，拖拽翻回滚可用 |
| `exit` | 徽章转「未运行」+ 卡片「会话已结束（退出码 0）」+「重新启动」可再开 shell |
| 重进页面 | 自动拉起新会话（`Starting → Running`） |

**遗留问题：**
- 逐 cell ANSI 属性色/粗斜体/下划线未实现（Phase 2），当前整行单色绘制
- 无软键盘终端仿真（仅 InputBar + Tab/Ctrl+C/Esc 快捷键），物理键盘 `KeyEvent` 未接
- 拖拽翻回滚后新输出会跳回最新行（Phase 1 简化，未做「阅读时保持位置」）
- 选区/复制粘贴、多 Session、颜色主题切换均在 Phase 2

**回写机制文档：** `../mechanisms/terminal-architecture.md`（文件清单、渲染路径变更、新增约束）。
