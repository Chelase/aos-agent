# Batch 1 Step 1：稳定开机自启与前台服务

> **统一愿景对齐**：与 `../../docs/unified-ecosystem-vision.md` 一致。开机自启与常驻是「离线独立运行」
> 的地基（原则三），属车机系统集成差异化能力（原则一），不涉及 Agent 核心与跨设备协议。
>
> 父计划：[../basic-capabilities-plan.md](../basic-capabilities-plan.md) Step 1
> 配套机制文档：`../../mechanisms/requirements-analysis.md`（开机自启链路、关键约束 1/2）

## 当前状态

Batch 0 交付的骨架能跑，但只是「能启动」，不具备车载环境所需的健壮性：

| 现状 | 问题 |
|---|---|
| 只监听 `BOOT_COMPLETED` | 车机快速启动走 `QUICKBOOT_POWERON`，直接开机不启动 |
| `startForeground` 无异常处理 | Android 14+ 从广播启动 specialUse 前台服务可能抛 `ForegroundServiceStartNotAllowedException`，当前会崩溃 |
| 通知无渠道描述、无点击入口 | 车机通知中心里是一条无说明、点不开的常驻条目 |
| 启动日志只有 BootReceiver 一行 | 无法判断服务由谁拉起、是否重复启动、是否被系统重启 |
| `onStartCommand` 每次都重建通知 | 重复启动路径没有幂等保障 |

## 本 Step 范围

只解决「服务能可靠地起来、起不来时不崩、起来后能看清是谁拉起的」。

### 改动文件

| 文件 | 动作 |
|---|---|
| `receiver/BootReceiver.kt` | 扩展支持的广播动作，提取可测的动作判定 |
| `service/AgentForegroundService.kt` | 通知完善、`startForeground` 异常兜底、启动来源日志、幂等 |
| `service/ServiceStartReason.kt` | 新增 — 启动来源枚举 + 从 Intent 解析（纯逻辑，可单测） |
| `AndroidManifest.xml` | 补 `QUICKBOOT_POWERON` / `LOCKED_BOOT_COMPLETED` intent-filter |
| `res/values/strings.xml`、`values-en/strings.xml` | 通知渠道描述等文案 |
| `app/src/test/.../service/ServiceStartReasonTest.kt` | 新增单元测试 |
| `app/src/test/.../receiver/BootActionTest.kt` | 新增单元测试 |
| `app/src/androidTest/.../service/AgentForegroundServiceTest.kt` | 扩展：重复启动不崩 |

## 步骤

### 1.1 扩展开机广播覆盖面

**内容：** BootReceiver 支持 `BOOT_COMPLETED`、`LOCKED_BOOT_COMPLETED`、`QUICKBOOT_POWERON`
（后者为部分 OEM 快速启动私有广播）。动作判定提取为纯函数。

**验收：** 单元测试覆盖「支持的动作放行 / 不支持的动作拒绝 / null 拒绝」。

### 1.2 建立启动来源可观测性

**内容：** 新增 `ServiceStartReason`（`BOOT` / `QUICK_BOOT` / `LOCKED_BOOT` / `MANUAL` / `SYSTEM_RESTART`），
由 Intent 解析。服务在 `onStartCommand` 记录来源与启动序号。

**验收：** 单元测试覆盖各来源解析；`adb logcat` 能看到来源与序号。

### 1.3 前台服务异常兜底

**内容：** `startForeground` 包 try-catch，捕获 `ForegroundServiceStartNotAllowedException`
与通用异常，记录告警并 `stopSelf()` 退出，不崩溃、不留半启动状态。

**验收：** 代码路径显式覆盖；正常路径服务保持运行。

### 1.4 通知完善与启动幂等

**内容：** 通知渠道加描述、关闭震动与角标；通知加点击打开 `MainActivity` 的 PendingIntent；
`onStartCommand` 重复调用时不重复创建渠道、不重置通知。

**验收：** 仪器测试重复启动服务不崩溃；模拟器上通知可见且可点击进入 App。

## 验收清单

- [x] 四路开机广播均被接收器识别（`BOOT_COMPLETED` 为受保护广播，shell 无法模拟，
      以 `QUICKBOOT_POWERON` 实测链路）
- [x] 启动来源与序号在日志中可辨：`reason=quick-boot startId=1 count=1`
- [x] `startForeground` 失败时不崩溃 —— 实测捕获真实的
      `ForegroundServiceStartNotAllowedException`
- [x] 重复启动不崩溃、不重复进入前台（三次广播均命中幂等分支，服务保持存活）
- [x] 通知可见、静音常驻、可点击进入 App（`isForeground=true foregroundId=1001`）
- [x] `testDebugUnitTest` 通过（20 用例）
- [x] 仪器测试通过（11 用例）

## 不在本 Step 做的事

| 项目 | 归属 |
|---|---|
| 电源状态监听、`STATE_SHUTDOWN_PREPARE` checkpoint | Step 5 |
| 系统感知面板 | Step 2 |
| 工程师模式服务状态检查 | Step 3 |
| 自检报告 | Step 4 |
| 崩溃自恢复 `AgentCrashHandler` | 需独立子计划，本 Step 只做 `START_STICKY` + 异常兜底 |
| 保活对抗（白名单、JobScheduler 兜底） | 观察真车表现后再定，不提前投入 |

## 进度

- [x] 1.1 扩展开机广播覆盖面 — 2026-07-26
- [x] 1.2 建立启动来源可观测性 — 2026-07-26
- [x] 1.3 前台服务异常兜底 — 2026-07-26
- [x] 1.4 通知完善与启动幂等 — 2026-07-26

## 归档

**完成日期：** 2026-07-26

**关键实测发现：** Android 15 下应用处于后台时，广播路径启动前台服务被系统拒绝
（`mAllowStartForeground false`），异常在 `BootReceiver` 层抛出。这意味着仅靠广播接收器
无法保证真车自启可靠性，`QUICKBOOT_POWERON` 也不享有平台给 `BOOT_COMPLETED` 的豁免。
详见 `../../mechanisms/boot-and-foreground-service.md` 关键约束 3。

**遗留问题：**
- 真车自启可靠性未验证，可能需要 OEM 白名单或系统级 App 化（Batch 3）
- 崩溃自恢复 `AgentCrashHandler` 未做，当前仅有 `START_STICKY` + 两层异常兜底

**回写机制文档：** 新增 `../../mechanisms/boot-and-foreground-service.md`。
