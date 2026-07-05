# 终端模块实施计划

> 配套机制文档：`.agent-rules/mechanisms/terminal-architecture.md`
>
> 参考实现：[Termux](https://github.com/termux/termux-app) (GPL-2.0)

## 当前阶段

本计划从属于 `core-agent-plan.md`（Batch 2：核心功能）。

它描述的是**当项目进入核心功能批次之后**，如何专项推进真实 shell 终端；不再表示“项目最早阶段就立刻实现终端”。

当前策略仍然是直接基于 Termux 架构构建真实 shell 终端，跳过“内置命令”过渡方案，但执行时机以后续批次计划为准。

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

- [ ] 终端显示 shell prompt
- [ ] 可执行 `echo`、`ls`、`cat` 等基础命令
- [ ] 终端输入无延迟
- [ ] 长输出可滚动查看
- [ ] 多行粘贴正常
- [ ] `exit` 正常关闭终端

## 不在本期做的事

- 颜色主题切换
- 触控选择/复制
- 分屏多 Session
- AI 命令补全

## 进度

- [ ] 步骤 1: 引入 Termux 源码
- [ ] 步骤 2: JNI PTY 层
- [ ] 步骤 3: TerminalSession 适配
- [ ] 步骤 4: TerminalCanvas 渲染
- [ ] 步骤 5: TerminalViewModel
- [ ] 步骤 6: 端到端验证
