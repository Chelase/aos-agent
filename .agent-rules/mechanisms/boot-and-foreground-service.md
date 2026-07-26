# 开机自启与前台服务

## 结论

车辆通电后由多路开机广播委托拉起前台服务，服务记录启动来源、幂等进入前台，进不去前台时主动退出而不崩溃。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Manifest | `AndroidManifest.xml` | 声明四路开机广播、specialUse 前台服务类型 |
| Receiver | `receiver/BootReceiver.kt` | 开机广播入口，只做委托 |
| Service | `service/AgentForegroundService.kt` | 常驻载体，前台通知与生命周期日志 |
| Service | `service/ServiceStartReason.kt` | 启动来源枚举 + 广播动作白名单（纯逻辑） |
| 资源 | `res/values/strings.xml`、`values-en/strings.xml` | 通知渠道名称与说明 |

## 运行链路

```
[车辆通电 / 快速启动]
   │
   ├─ BOOT_COMPLETED ──────────────┐
   ├─ LOCKED_BOOT_COMPLETED ───────┤
   ├─ QUICKBOOT_POWERON ───────────┤  （部分 OEM 不发 BOOT_COMPLETED）
   └─ com.htc.…QUICKBOOT_POWERON ──┤
                                   ▼
                     [BootReceiver.onReceive]
                        │ ServiceStartReason.isBootAction(action)
                        │    ├─ false → 记 debug 日志后返回
                        │    └─ true  ↓
                        │ createServiceIntent(context, action)
                        │    └─ putExtra(EXTRA_START_REASON, action)
                        ▼
              ContextCompat.startForegroundService
                        │ （try-catch：异常不得穿透广播边界）
                        ▼
         [AgentForegroundService.onCreate] → ensureNotificationChannel
                        ▼
         [AgentForegroundService.onStartCommand]
                        │ ServiceStartReason.from(intent)
                        │    └─ intent == null → SYSTEM_RESTART（记 warn）
                        │ foregroundActive?
                        │    ├─ true  → 跳过，返回 START_STICKY
                        │    └─ false ↓
                        │ enterForeground()
                        │    ├─ 成功 → foregroundActive=true, START_STICKY
                        │    └─ 失败 → stopSelf(), START_NOT_STICKY
                        ▼
                  [服务常驻，通知可点击进入 MainActivity]
```

启动来源经 Intent extra 传递而非解析广播动作本身，因为服务也可被 UI 或系统重启拉起，
来源判定必须覆盖非广播路径。

## 使用点

- 系统在开机时投递四路广播之一，`BootReceiver` 委托启动服务。
- `AgentForegroundServiceTest` 通过 `startForegroundService` 重复下发启动命令验证幂等。
- 通知点击进入 `MainActivity`（`FLAG_ACTIVITY_NEW_TASK or CLEAR_TOP`）。
- `MainActivity` 目前不主动启动服务，服务仅由广播或手动 adb 命令拉起。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 新增开机入口 | `ServiceStartReason.BOOT_ACTIONS` + manifest intent-filter | 两处必须同步；`ServiceStartReasonTest` 固定了动作数量，需一并更新 |
| 新增启动来源 | `ServiceStartReason` 枚举 + `from()` | label 是日志排查依赖的字面量，改动会打断排查手册 |
| 调整通知外观 | `AgentForegroundService.createNotification` | 保持 `setOngoing` + `setSilent`，车机不允许常驻通知发声 |
| 接入电源状态 | 新增 PowerManager 封装，在服务中订阅 | 属 Batch 1 Step 5，需先写子计划 |
| 增加保活兜底 | 暂无实现 | 需观察真车表现后立项，不提前投入 |

## 关键约束

1. **广播接收器内禁止耗时操作**，只允许委托给服务（requirements-analysis 关键约束 1）。
2. **异常不得穿透广播边界**：`onReceive` 内抛出会被系统记为接收器异常，必须自行捕获。
3. **进不了前台就退出**：Android 15+ 可能拒绝从广播启动 specialUse 前台服务，
   此时 `stopSelf()` 并返回 `START_NOT_STICKY`，不保留半启动状态。

   已实测（Android 15 / SDK 35）：应用处于后台时，广播路径抛
   `ForegroundServiceStartNotAllowedException: mAllowStartForeground false`，
   异常发生在 `BootReceiver` 的 `startForegroundService` 调用处，被接收器层 try-catch 接住，
   服务侧兜底不会被触发。**两层兜底都必须保留**。
   应用在前台时同一广播可正常拉起服务并进入前台。

   推论：`QUICKBOOT_POWERON` 不是受保护广播，不享有平台给 `BOOT_COMPLETED` 的
   前台服务启动豁免，仅靠它无法在 Android 12+ 实现可靠自启。真车上的自启可靠性
   需另行验证，可能需要 OEM 白名单或系统级 App 化（Batch 3）。
4. **`onStartCommand` 必须幂等**：车机上开机广播可能多次到达，已在前台时不重复调用
   `startForeground`，否则通知闪烁。
5. **null Intent 表示进程曾被杀死**（START_STICKY 重启），必须与用户主动启动区分并记 warn；
   真车上频繁出现说明保活不足。
6. **通知渠道名称与说明走资源**，随应用语言切换，不得硬编码。

## 维护方式

- **新增**：开机入口改 `BOOT_ACTIONS` 与 manifest 两处；启动来源改枚举与 `from()`。
- **排查**：`adb logcat -s AOSAgent.Boot AOSAgent.Service` 查看委托与启动来源；
  关注 `reason=system-restart` 出现频率判断保活问题。
- **验证**：
  ```bash
  # 手动模拟各路开机广播（--user 取 am get-current-user 的结果）
  adb shell am broadcast --user 10 -a android.intent.action.BOOT_COMPLETED -p com.aos.agent
  adb shell am broadcast --user 10 -a android.intent.action.QUICKBOOT_POWERON -p com.aos.agent
  # 确认服务在前台
  adb shell dumpsys activity services com.aos.agent | grep -E 'isForeground|ServiceRecord'
  ```
  仪器测试按 `dev-plans/framework-foundation-plan.md` 归档小节记录的手动路径执行。

> 更新时间：2026-07-26
