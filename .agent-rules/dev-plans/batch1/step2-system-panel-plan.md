# Batch 1 Step 2：建立基础系统感知面板

> **统一愿景对齐**：与 `../../docs/unified-ecosystem-vision.md` 一致。系统感知是车机 Agent「离线独立运行、
> 与车机系统集成」的基础面（原则一/三），只读本机状态，不涉及 Agent 核心与跨设备协议。
>
> 父计划：[../basic-capabilities-plan.md](../basic-capabilities-plan.md) Step 2
> 配套机制文档：`../../mechanisms/ui-design-system.md`（token 与双语约束）、`../../mechanisms/requirements-analysis.md`

## 当前状态

- 系统信息采集已有：`system/SystemInfoProvider.kt` 一次性快照（设备/系统/网络），工程师页只读展示。
- 缺：电池信息（未采集）、定时刷新（快照 `collect()` 只调一次，网络/电池是动态值）、独立面板页。
- `HomeScreen` 已预埋「系统面板」入口，当前为禁用态（badge coming soon）。
- 工程师页内的 `networkLabel` 注释已预留「Step 2 复用时提取到共享位置」。

## 本 Step 范围

一块可常看的仪表页：设备/系统/网络/电源四个分区 + 顶部指标条，数据定时刷新、可手动刷新，刷新时刻可见。

### 改动文件

| 文件 | 动作 |
|---|---|
| `system/SystemInfoProvider.kt` | 扩展：`SystemInfoReader` 增电池读取；`SystemInfo` 增 `batteryLevelPercent` / `batteryCharging`（不可用为 null）；电池解析抽成纯函数可单测 |
| `ui/components/AOSLabels.kt` | 新增 — 网络/电池状态 → 本地化文案映射（从 EngineerModeScreen 提取共享） |
| `ui/systempanel/SystemPanelScreen.kt` | 新增 — 指标条 + 分区卡片 + 刷新机制 |
| `MainActivity.kt` | 新增 `Destination.SystemPanel` 分支并接线 |
| `ui/home/HomeScreen.kt` | 放开「系统面板」入口（badge ready） |
| `ui/engineer/EngineerModeScreen.kt` | 改用共享 `AOSLabels`，删除私有 `networkLabel` |
| `res/values/strings.xml`、`values-en/strings.xml` | 面板文案成对新增 |
| `app/src/test/.../system/BatterySnapshotTest.kt` | 新增 — 电池解析纯函数单测 |

## 步骤

### 2.1 数据层：电池采集与快照扩展

**内容：** `AndroidSystemInfoReader` 经 `ACTION_BATTERY_CHANGED` 粘性广播读电量与充电态（无需权限）；
解析逻辑抽为纯函数（extras → `BatterySnapshot`），不可用时字段为 null，UI 渲染「Unavailable」。

**验收：** 单测覆盖「满电/充电中/USB/无 extras」分支；fallback 不抛异常。

### 2.2 UI 层：面板页与共享标签

**内容：** `SystemPanelScreen`（design.md §6.x 卡片式面板，结构对齐工程师页）：
顶部指标条（电量/网络/更新时刻）+ 四卡片（系统/设备/网络/电源）。
文案全走 `stringResource`，颜色全走 token；触控目标 ≥ 56dp。

**验收：** 两主题下配色正确（不直引 Color.kt）；中英文案成对。

### 2.3 刷新机制与入口接线

**内容：** 页面激活时每 5s 重新 `collect()`（`LaunchedEffect` + `delay`），另有手动刷新按钮；
`MainActivity` 加 `Destination.SystemPanel`；首页入口放开。

**验收：** 模拟器上改电量（`adb shell cmd battery set level 66`）后 5s 内面板跟随变化，无需离开页面。

## 验收清单

- [x] 面板可见且四分区信息完整、非 Unavailable（模拟器实测：Android 15 / API 35 / Google / Car on x86_64 emulator）
- [x] 电量/网络动态刷新生效（`dumpsys battery set level 33` 后 5s 内面板由 66% 变 33%，未触碰界面）
- [x] 手动刷新有可见反馈（顶栏「更新于 HH:mm:ss」时间戳前进）
- [x] `testDebugUnitTest` 通过（123 用例）；`assembleDebug` BUILD SUCCESSFUL

## 进度

- [x] 2.1 数据层：电池采集与快照扩展 — 2026-10-02
- [x] 2.2 UI 层：面板页与共享标签 — 2026-10-02
- [x] 2.3 刷新机制与入口接线 — 2026-10-02

## 归档

**完成日期：** 2026-10-02

**实测要点：**
- 电池经 `ACTION_BATTERY_CHANGED` 粘性广播读取，无需权限；`dumpsys battery set level/status` 可驱动面板变化，
  验证了 5 秒轮询真的在重采。
- `BatterySnapshot.from(status, level, scale)` 为纯函数，单测覆盖充电/放电/未知/缺字段/scale=0/越界钳制。
- 网络与电池文案提取到 `ui/components/AOSLabels.kt`，工程师页改为共用（兑现该文件原注释预留的「Step 2 复用时提取」）。

**遗留问题：**
- 刷新周期 5s 为写死常量，未做设置项；电源状态监听与 checkpoint 属 Step 5。
- 面板与工程师页信息有重叠（系统/设备/网络三卡），本期不做合并，等 Step 3 工程师模式增强时一并考虑。

**回写机制文档：** `../../mechanisms/ui-design-system.md`（新增页面与共享标签组件、终端字号 token）。

## 不在本 Step 做的事

| 项目 | 归属 |
|---|---|
| 电源状态监听 / STATE_SHUTDOWN_PREPARE checkpoint | Step 5 |
| Car API 车辆属性展示 | 工程师模式增强（Batch 2 Step 5） |
| 历史曲线 / 图表 | 不做，无此需求 |
| 自检报告导出 | Step 4 |
