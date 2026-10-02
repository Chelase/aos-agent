# 终端架构

## 结论

基于 Termux `terminal-emulator` 源码（v0.118.1）做 VT100/xterm 仿真，经自研 JNI PTY 桥接启动真实 `/system/bin/sh`，
UI 侧用 Compose 逐行 `Text` 渲染等宽字符网格。Phase 1 已于 2026-10-02 在 AAOS 模拟器端到端验收。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| UI | `ui/terminal/TerminalScreen.kt` | 终端页：顶栏 + 画布 + InputBar（Tab/Ctrl+C/Esc/发送）+ 结束态卡片 |
| UI | `ui/terminal/TerminalCanvas.kt` | Compose 渲染：逐行 `Text` + Canvas 光标；拖拽翻回滚；主题色注入仿真调色板 |
| Core | `terminal/TerminalViewModel.kt` | `AndroidViewModel`，`StateFlow<TerminalUiState>`（Starting/Running/Exited）+ `frameTick`；实现 `TerminalSessionClient` |
| Core | `terminal/*.java`（14 文件） | Termux 仿真与 I/O：`TerminalSession`、`TerminalEmulator`、`TerminalBuffer`、`TerminalRow`、`ByteQueue`、`WcWidth`、`KeyHandler`、`TextStyle`、`TerminalColors`、`TerminalColorScheme`、`TerminalOutput`、`TerminalSessionClient`、`JNI`、`Logger` |
| System | `app/src/main/cpp/pty.c` | JNI PTY：`/dev/ptmx` + `TIOCGPTN`/`TIOCSPTLCK` + `fork/setsid/TIOCSCTTY/execve` + `TIOCSWINSZ` + `waitpid` |
| System | `app/src/main/cpp/CMakeLists.txt` | 构建 `libpty.so`（abiFilters 仅 `arm64-v8a` / `x86_64`） |

## 运行链路

```
TerminalScreen ← TerminalViewModel ← TerminalSession ← JNI(libpty.so) → /system/bin/sh
     │                                      │           (ptmx/fork/exec)
     └── TerminalEmulator (Termux Java) ←────┘
```

```
用户输入 → InputBar → ViewModel.send → TerminalSession.write → mTerminalToProcessIOQueue(4KB)
   → 写线程 write(ptm) → PTY slave → shell stdin
shell stdout → PTY master → 读线程 read → mProcessToTerminalIOQueue(64KB)
   → MainThreadHandler(MSG_NEW_INPUT) → TerminalEmulator.append → TerminalBuffer
   → onTextChanged → frameTick++ → Compose 重组
   → TerminalCanvas 逐行 screen.getSelectedText(...) → Text 绘制 + Canvas 光标
```

会话启动时机：`TerminalSession` 构造函数不拉起进程，首次 `updateSize(cols, rows)` 才 `initializeEmulator()`
（建仿真 + `createSubprocess` + 三条 I/O 线程）。因此 `TerminalViewModel` 缓存最近一次画布尺寸，
画布布局回调与 shell 查找谁先到都能正确启动；PTY 创建失败折进 `Exited`，不崩 UI。

### ByteQueue 设计（沿用 Termux）

| 队列 | 大小 | 方向 | 说明 |
|---|---|---|---|
| `mProcessToTerminalIOQueue` | 64KB | 进程 → 终端 | shell 输出可能突发大量数据 |
| `mTerminalToProcessIOQueue` | 4KB | 终端 → 进程 | 用户打字速度远低于 shell 输出 |

## 使用点

- 首页「终端」入口（`Destination.Terminal`）
- 用户手动输入命令；`exit` 后由结束态卡片提供「重新启动」
- 会话挂在 `ViewModelProvider` 的 Activity 作用域：语言/主题切换 `recreate()` 后 shell 不掉

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 改渲染/配色/光标 | `ui/terminal/TerminalCanvas.kt` | 逐行取文本处与光标矩形；字号取 `AOSDataText.terminal` |
| 加逐 cell ANSI 色（Phase 2） | `ui/terminal/TerminalCanvas.kt` | 用 `TerminalRow.getStyle(col)` + `TextStyle.decode*` 拆 run，再按 run 上色 |
| 调整终端行为 | `terminal/TerminalSession.java` | I/O 线程模型与回调；与上游同步时只改包名与 `loadLibrary("pty")` |
| 更新 VT100 解析 | `terminal/TerminalEmulator.java` | 从 Termux 上游同步 |
| 改 PTY 行为 | `app/src/main/cpp/pty.c` | 符号前缀必须是 `Java_com_aos_agent_terminal_JNI_` |
| 改字号 | `ui/theme/Type.kt` 的 `AOSDataText.terminal` | 字号参与行列数计算，改值需重开终端 |

## 关键约束

1. `TerminalEmulator` 等仿真类是纯 Java，**无 JNI 依赖**，直接从 Termux 引入；JNI 侧只有约 150 行 C。
2. Android bionic **没有 `openpty()`**，必须按 `/dev/ptmx` + `TIOCGPTN` 取号 + `TIOCSPTLCK` 解锁 + 子进程开
   `/dev/pts/N` 的路子（与 Termux 一致）。
3. `TerminalSession` 必须在**带 Looper 的线程（主线程）**构造：其 `MainThreadHandler` 绑定构造线程。
4. 进程→终端队列（64KB）必须大于终端→进程队列（4KB）。
5. 终端不需要 `INTERNET` 权限；shell 由 App 自身进程 fork，无需额外权限。
6. **渲染只能用 Compose 文本组件**：本机实测（AAOS API 36 x86_64 模拟器）在同一 Canvas 上
   `android.graphics.Paint.drawText` 静默不渲染（`drawRect` 正常，硬件层与 `LAYER_TYPE_SOFTWARE` 皆然，
   且 `AndroidView` 存在时会连带污染同窗口其他 Compose 文字），因此 `TerminalCanvas` 用逐行 `Text`
   + Canvas 光标矩形；字体度量仍按 Termux 方式（`measureText("X")` / `fontSpacing`）计算行列。
7. **仿真调色板必须在组合期同步写入**（`remember`，不能放 `LaunchedEffect`）：否则首帧按 Termux 出厂
   深色调色板绘制，浅色主题下白字白底不可见。
8. `AndroidView`/`update` 之类的回调 lambda 若需要每帧执行，必须**读取会变化的状态值**（如 `frameTick`），
   否则 lambda 被记忆化、更新被跳过，画面冻在首帧。
9. `MainActivity` 用 `android:windowSoftInputMode="adjustPan"`：`adjustResize` 会让软键盘弹出时把画布压成
   两三行，进而把 PTY 尺寸改成极小值、shell 重排。
10. 整包 ABI 只打 `arm64-v8a` / `x86_64`（车机 + 模拟器），`libpty.so` 约 9KB。

## 维护方式

- **新增转义序列支持**：改 `terminal/TerminalEmulator.java` 或从 Termux 上游合并。
- **排查显示异常**：先确认是仿真缓冲无内容（`getSelectedText` 返回空）还是渲染问题；再看 `frameTick` 是否在涨。
- **排查 shell 无法启动**：看 `pty.c` 的 `open /dev/ptmx` / `fork` / `execve` 返回值（抛 `IOException` 会折进 `Exited`）。
- **验证**：`./gradlew :app:assembleDebug`（含 `libpty.so`）→ 模拟器打开终端 → `echo hello` 有回显 → `exit` 出现结束态。

> 更新时间：2026-10-02（Phase 1 落地并实测；渲染路径由「Compose Canvas + Paint」改为「Compose Text」）
