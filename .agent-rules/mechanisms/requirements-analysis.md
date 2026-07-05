# 需求分析

## 结论

> 说明：本文件中的 `P0 / P1` 表示**产品需求优先级**，不是**实现批次顺序**。
>
> 实施时允许采用“先框架、再基础、再核心、再扩展”的分批推进方式：
> - 某些 `P0` 能力可在框架稳定后进入后续批次实现；
> - 只要路线图明确说明阶段边界，就不视为需求降级。

AOS-Agent 的七大核心需求：

| # | 需求 | 优先级 | 说明 |
|---|------|--------|------|
| 1 | **开机自启 + 后台持续运行** | **硬性要求** | 车辆通电即启动，关机前保存状态，全天候在后台存活 |
| 2 | **EV 电源循环适应** | P0 | 支持电动汽车频繁开关机，不掉数据 |
| 3 | **CLI 终端** | P0 | 真实 shell 终端，可执行系统命令 |
| 4 | **系统感知 + 车机 API 全覆盖** | P0 | 调用所有车机 API，全面感知车辆状态 |
| 5 | **连续语音对话** | P0 | 驾驶场景免手动操作，语音唤醒 + 持续对话 |
| 6 | **AI 桌面宠物** | P1 | 车机上的智能伙伴 |
| 7 | **工程师模式** | P0 | 调试诊断面板，查看所有车机 API、服务、系统版本 |
| -- | **性能优化** | **贯穿所有任务** | 非独立任务，每个功能实现时都必须满足性能基线 |

## 涉及对象

| 层 | 功能 | 角色 |
|---|---|---|
| System | Boot Receiver | 开机自启入口 |
| Service | AgentForegroundService | 后台持续运行保活 |
| Service | AgentCrashHandler | 崩溃自恢复 |
| Service | PowerManager | EV 电源状态管理 |
| UI/System | Terminal | CLI 终端交互 |
| System | Car API 封装 | 系统数据感知 |
| Audio | VoiceManager | 语音唤醒 + 连续对话管理 |
| Core | ASR/TTS Engine | 语音识别与合成 |
| UI | Pet Canvas | 桌面宠物渲染 |
| UI | EngineerMode | 工程师调试诊断面板 |
| Core | AgentEngine | AI 对话能力 |
| Core | OEM Compatibility Layer | 多厂商适配层（API 差异抹平） |
| Core | AgentEngine | Agent 推理核心（工具/插件/规划） |
| Core | PluginManager | 插件热加载系统 |
| Core | AppMarket | 第三方应用管理 |
| -- | Performance Baseline | 贯穿所有模块的性能约束 |

## 运行链路

### 开机自启

```
[车辆通电]
    │
    ▼
[BOOT_COMPLETED / QUICKBOOT_POWERON 广播]
    │ BroadcastReceiver (5秒内)
    ▼
[AgentForegroundService.startForeground()]
    │ 创建通知渠道 (PRIORITY_LOW)
    ▼
[服务运行中 ← 用户可打开 UI]
```

### EV 电源循环

```
STATE_ON ──→ STATE_SHUTDOWN_PREPARE ──→ STATE_SUSPEND_ENTER
                                           ↓
                                          STATE_HIBERNATION_ENTER → STATE_OFF
                                           ↓
                                          (下次点火) STATE_ON
```

关键点：`STATE_SHUTDOWN_PREPARE` 中保存所有状态，`STATE_ON` 恢复。

### 多厂商兼容性策略

不同车厂对 AAOS 的定制差异体现在：

| 差异点 | 说明 | 应对策略 |
|--------|------|---------|
| Car API 支持度 | 同一 PropertyId 在不同车型上可用性不同 | 工程师模式全量扫描，运行时 try-catch |
| 自定义 Property | 部分厂商新增私有 VehicleProperty | 预留扩展接口，工程师模式可发现 |
| 权限策略 | 系统 App 与用户 App 权限不同 | Phase 1 用户 App，Phase 3 升级系统 App |
| 电源管理 | CarPowerManager 状态机实现差异 | 统一接口封装，各厂商适配 |
| 系统属性 | Build.* 值因厂商而异 | OEM Detector 统一读取 |

核心策略：**探测 -> 适配 -> 降级**。先用工程师模式全面扫描，根据报告编写适配层，不可用 API 静默降级。

### 系统感知数据分类

| 数据 | API | 权限 | 优先级 |
|---|---|---|---|
| 车速 | `CarPropertyManager` / `PERF_VEHICLE_SPEED` | `CAR_SPEED` | P0 |
| 电池电量 | `BatteryManager.EXTRA_LEVEL` | 无 | P0 |
| 续航里程 | `CarHardwareManager.carInfo` | `CAR_ENERGY` | P0 |
| 充电状态 | `BatteryManager.EXTRA_STATUS` | 无 | P0 |
| Android 版本 | `Build.VERSION.SDK_INT` + `Build.VERSION.RELEASE` | 无 | P0 |
| 系统信息 | `Build.*` / `SystemProperties` | 无 | P1 |
| 驾驶模式 | `CarUxRestrictionsManager` | `CAR_UX_RESTRICTION` | P1 |
| 网络状态 | `ConnectivityManager` | `ACCESS_NETWORK_STATE` | P0 |
| 内存/CPU | `ActivityManager.MemoryInfo` | 无 | P1 |
| 位置 | `FusedLocationProviderClient` | `ACCESS_FINE_LOCATION` | P1 |

### AI 桌面宠物渲染方案

| 方案 | 性能 | 文件大小 | 阶段 |
|---|---|---|---|
| Compose Canvas 程序化绘制 | ⭐⭐⭐⭐⭐ | 极小 | Phase 1 |
| Live2D (Cubism SDK) | ⭐⭐⭐⭐ | 中 | Phase 2 |

动画状态机：`Idle → Thinking → Speaking → Happy → Sleeping`

## 使用点

- 开机自启：每次车辆启动时触发
- 系统感知：Agent 对话上下文、系统面板展示
- 桌面宠物：主界面常驻显示

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 新增系统感知数据 | `system/carapi/` + `ui/systempanel/` | 添加 API 调用 + UI 展示 |
| 修改宠物动画 | `ui/pet/` | 扩展动画状态机 |

## 关键约束

1. `BroadcastReceiver` 内禁止耗时操作，必须委托给 Service。
2. Android 8+ 必须用 `startForegroundService()` + 5秒内 `startForeground()`。
3. EV 每次状态变更都必须 checkpoint（Room DB 或文件快照）。
4. 驾驶模式下（`CarUxRestrictions` 限制）必须简化 UI，禁止复杂交互。

## 维护方式

- **新增需求**：补充涉及对象表和运行链路图。
- **验证**：AAOS 模拟器中验证各 API 可用性。

> 更新时间：2026-06-28




