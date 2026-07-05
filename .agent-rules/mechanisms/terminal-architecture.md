# 终端架构

## 结论

基于 Termux 终端仿真库，通过 PTY JNI 桥接启动真实 shell，以 Compose Canvas 渲染字符网格，Phase 1 即提供完整 shell 终端能力。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| UI | `terminal/TerminalCanvas.kt` | Compose Canvas 字符网格渲染 |
| UI | `terminal/TerminalViewModel` | 状态驱动 UI，转发输入事件 |
| Core | `terminal/TerminalSession.kt` | PTY 进程 I/O 管理（改编自 Termux） |
| Core | `terminal/TerminalEmulator.java` | VT100/xterm 仿真（来自 Termux，纯 Java） |
| System | `natives/pty/pty.c` | JNI PTY：openpty/fork/exec/read/write |

## 运行链路

### 整体架构

```
TerminalCanvas (Compose) ← TerminalViewModel ← TerminalSession ← JNI PTY → /system/bin/sh
     │                                                │                     (openpty/fork/exec)
     └── TerminalEmulator (Termux, 纯 Java) ←──────────┘
         (VT100/xterm 完整仿真)
```

### 数据流

```
用户键盘输入 → Compose InputBar
    │ write()
    ▼
TerminalSession.mTerminalToProcessIOQueue (4KB)
    │ 写线程 → write(ptm_fd, ...)
    ▼
PTY master → PTY slave → shell stdin
    │
    ▼
shell stdout/stderr → PTY slave → PTY master
    │
    ▼
读线程 read(ptm_fd, ...) → mProcessToTerminalIOQueue (64KB)
    │ Handler.sendMessage(MSG_NEW_INPUT)
    ▼
TerminalEmulator.feed() → VT100 解析 → 更新 TerminalBuffer
    │ invalidate()
    ▼
TerminalCanvas 重新渲染
```

### ByteQueue 设计

| 队列 | 大小 | 方向 | 说明 |
|---|---|---|---|
| `mProcessToTerminalIOQueue` | 64KB | 进程 → 终端 | shell 输出可能突发大量数据 |
| `mTerminalToProcessIOQueue` | 4KB | 终端 → 进程 | 用户打字速度远低于 shell 输出 |

## 使用点

- 主界面终端 Tab 页
- Agent 调用 shell 命令的工具通道
- 用户手动输入命令

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 修改渲染效果 | `TerminalCanvas.kt` | 调整字符网格/光标/颜色绘制逻辑 |
| 调整终端行为 | `TerminalSession.kt` | 修改 I/O 线程模型或回调接口 |
| 更新 VT100 解析 | `TerminalEmulator.java` | 从 Termux 上游同步更新 |
| 修改 JNI 层 | `natives/pty/pty.c` | 调整 PTY 创建参数或权限逻辑 |

## 关键约束

1. `TerminalEmulator.java` 是纯 Java 库，**无 JNI 依赖**，直接从 Termux 源码引入。
2. JNI 代码仅约 120 行 C，只含 openpty/fork/exec/read/write。
3. `TerminalSession` 必须运行在独立线程，不能阻塞主线程。
4. 进程→终端队列（64KB）必须大于终端→进程队列（4KB）。
5. 无 `INTERNET` 权限要求，终端纯粹是本地进程通信。

## 维护方式

- **新增转义序列支持**：修改 `TerminalEmulator.java` 或从 Termux 上游合并。
- **排查终端显示异常**：检查 `TerminalEmulator.feed()` 的 VT100 解析日志。
- **排查 shell 无法启动**：检查 `pty.c` 的 openpty/fork 返回值日志。
- **验证**：启动终端后执行 `echo hello` 确认 shell 正常响应。

> 更新时间：2026-06-27
