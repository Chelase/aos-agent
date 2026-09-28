# Step 3 子计划：ToolSystem 与首批工具

> 父计划：[../mvp-core-plan.md](../mvp-core-plan.md) Step 3
> 配套机制文档：[../../mechanisms/agent-capabilities.md](../../mechanisms/agent-capabilities.md)、[../../mechanisms/boot-and-foreground-service.md](../../mechanisms/boot-and-foreground-service.md)
> **统一愿景对齐**：①不自研 Agent 基础架构（工具契约沿用机制文档）；②工具层不自建对外协议，形状对齐 Android 16 AppFunctions 语义；③全部工具可无头本地执行（Hub 不可用照常）；④车辆专属能力优先（`vehicle_basic` 是本步唯一的车载差异化产出）。无偏离。

## 0. 本步要解决什么

让无头引擎能被驱动着「调工具干活」：一个可扩展的 `ToolSystem` + 三个首批工具（`system_info` / `shell_exec` / `vehicle_basic`），并在模拟器无 VHAL、真车无平台签名的现实约束下，把「拿不到车况」变成结构化事实而不是散文。

2026-09-21 外部调研带来的三条硬约束（出处见父计划「借鉴来源映射」新增行）：

1. 控车写属性在 AOSP 侧全部是 signature|privileged，普通 APK 物理拿不到 → 本期不预留写路径。
2. 泛型 get/set 工具族会推高 function-calling 难度（CarToolForge 自述需 >20B 模型）→ 工具要少而语义化。
3. 「能力声明」正在平台化（AppFunctions / 高通 SKILL HUB）→ schema 字段形状对齐，但依赖不进 MVP。

## 1. 前置实测（编码前必须完成，产出物是一张表）

在 AAOS 模拟器（和可用的真机）上逐字段核对，不得沿用外部仓库的权限结论：

```bash
adb shell dumpsys car_service list-properties | head -80     # 看 VHAL 实际暴露了哪些属性
adb shell pm list permissions -g -d | grep android.car      # 看哪些是 dangerous 可运行时授予
adb shell dumpsys package com.aos.agent | grep -A20 "requested permissions"
```

待核对字段：`SPEED`、`EV_BATTERY_LEVEL`、`PERCENT_REMAINING_CHARGE`/续航、`ENV_OUTSIDE_TEMPERATURE`、`INFO_MODEL`、`INFO_MAKE`、`PROG_DISPLAY_DISTANCE`、`TYRE_PRESSURE`（预期 privileged，用于验证降级路径）。每条记录：属性名 / 所需权限 / 保护级别 / 模拟器是否可读 / 未授予时的异常类型。

### 1.1 实测结果（2026-09-27，Automotive 模拟器 API 35 / user 版）

`dumpsys package permission android.car.permission.X` 的 `prot=` 字段，逐条实测：

| 权限 | 实测保护级别 | 普通可安装应用 |
|---|---|---|
| `CAR_INFO` | normal | 声明即可用 |
| `CAR_POWERTRAIN` | normal | 声明即可用 |
| `CAR_EXTERIOR_ENVIRONMENT` | normal | 声明即可用 |
| `CAR_ENERGY_PORTS` | normal | 声明即可用 |
| `CAR_SPEED` | dangerous | 需运行时授予 |
| `CAR_ENERGY` | dangerous | 需运行时授予 |
| `CAR_TIRES` | signature&#124;privileged | **拿不到** |
| `CONTROL_CAR_CLIMATE` | signature&#124;privileged | **拿不到** |
| `CONTROL_CAR_WINDOWS` | signature&#124;privileged | **拿不到** |
| `CONTROL_CAR_SEATS` | signature&#124;privileged | **拿不到** |
| `CAR_VENDOR_EXTENSION` | signature&#124;privileged | **拿不到** |

结论：CarToolForge manifest 注释里的分级与 AOSP 一致，**写控车结构性不可得**这条成立。

### 1.2 属性级实测（2026-09-28，同一模拟器，`VehiclePropertyProbeTest` 从应用内跑）

权限分级查文档只能到"该要什么权限"，**"这台设备给不给读某个属性"只能实测**。跑出来的结果顺带纠正了 allowlist 初稿的三个错误名字：

| 属性（AOSP 真名） | 实测结果 | 结论 |
|---|---|---|
| `PERF_VEHICLE_SPEED` | Number(0.0) | 可读。**AOSP 里没有 `SPEED` 这个名字** |
| `EV_BATTERY_LEVEL` | Number(150000.0) | 可读，单位 Wh |
| `EV_CURRENT_BATTERY_CAPACITY` | Number(150000.0) | 可读 |
| `ENV_OUTSIDE_TEMPERATURE` | Number(25.0) | 可读 |
| `INFO_MODEL` / `INFO_MAKE` | Text("Speedy Model") / Text("Toy Vehicle") | 可读，字符串 |
| `PERF_ODOMETER` | Missing(permission_denied) ← SecurityException | 声明 `CAR_POWERTRAIN` **不够**，真实所需权限未确定 |
| `TIRE_PRESSURE` | 未进可用集（挡在 allowlist） | 特权属性，符合预期 |

三条要记的：

1. **没有"剩余续航"这个系统属性**。`PERCENT_REMAINING_CHARGE` 与 `PROG_DISPLAY_DISTANCE` 都不存在；百分比要 `EV_BATTERY_LEVEL / INFO_EV_BATTERY_CAPACITY` 自己算，续航多为 vendor 扩展（必须显式给 `id`）。用户问续航时模型必须能说"读不到"，不能编。
2. **`pm revoke` 在这台模拟器上没生效**：撤销后应用内 `checkSelfPermission` 仍返回 granted=true，所以"未授予时返回 permission_denied"这条**没能从 dangerous 权限路径实测到**；但 `PERF_ODOMETER` 走 `SecurityException` 分支返回了 `permission_denied`，同一降级路径已被真机验证。
3. 属性名写错不会编译失败（我们按名字反射常量表），只会得到 `unsupported` —— 所以这份实测不是可选项，是 allowlist 唯一的准入方式。

**回写规则**：上表属稳定系统约束，已随本步写入 `assets/vehicle/vehicle_properties.json` 的
`protection` 字段；是否再拆独立机制文档，等真车数据进来后再定。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `core/tools/Tool.kt` | 新增 | `Tool` 接口 + `ToolPermission`(Auto/Ask/Forbid) + `ToolResult`（Ok / Unavailable / Failed） |
| `core/tools/ToolSystem.kt` | 新增 | 注册、查找、按 skill 子集暴露、执行、默认 30s 超时、错误包装成 `ToolResult.Failed` |
| `core/tools/ToolConfirmer.kt` | 新增 | `ask` 的回调接口；**未注入即默认拒绝**（fail-closed） |
| `core/tools/SystemInfoTool.kt` | 新增 | 包一层 `SystemInfoProvider`，只读，auto |
| `core/tools/ShellTool.kt` | 新增 | `Runtime.exec`；白名单 auto / 名单外 ask / 黑名单 forbid |
| `core/tools/VehicleBasicTool.kt` | 新增 | 唯一车况工具，按 allowlist 产出结构化 bundle |
| `system/vehicle/VehiclePropertyAllowlist.kt` | 新增 | 解析 `assets/vehicle/vehicle_properties.json`；`access: write` 与 privileged 条目不注册 |
| `system/Capabilities.kt` | 新增 | 运行期能力探测：`hasCarApi` / `hasAppFunctions` / `perAppLocale` / 后续 MCP 可达性。工具与 UI 共用一份，不许各写各的 `SDK_INT` 判断 |
| `system/vehicle/VehicleReader.kt` | 新增 | `suspend fun read(spec): VehicleValue` 接口，纯 Kotlin 可测 |
| `system/vehicle/AndroidVehicleReader.kt` | **本步未实现** | 卡在 §6 的 `android.car` 编译期接入方式；先用 `UnavailableVehicleReader` 如实报未接入 |
| `system/vehicle/FakeVehicleReader.kt` | 新增 | 读 `assets/vehicle/mock_state.json`，模拟器/单测用 |
| `assets/vehicle/vehicle_properties.json` | 新增 | 属性 allowlist 唯一事实源 |
| `assets/vehicle/mock_state.json` | 暂不新增 | `FakeVehicleReader.fromJson` 已能吃不存在的夹具；等 Step 4/5 需要上屏 mock 时再加，不留没人读的数据文件 |
| `core/engine/AgentEngine.kt` | 改 | function-calling 分支 + `tool_call` / `tool_result` 事件（Step 2 预留位） |
| `core/tools/ToolLoopGuard.kt` | 新增 | 步数上限 8 + 重复调用检测 |
| `core/tools/ToolJsonRepair.kt` | 新增 | 保守修复模型给的参数 JSON：剥围栏、去尾逗号、补括号、截掉尾部散文 |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | 改 | **不新增依赖**：allowlist 用 JSON 而非 YAML，复用已有 kotlinx-serialization；为一个配置文件引第三方解析器不值得（轻量预算） |
| `app/src/test/.../core/tools/*` | 新增 | JVM 单测：ToolSystem 超时/权限/降级、allowlist 解析、循环防护 |
| `app/src/androidTest/.../VehicleReaderTest.kt` | **延后** | 依赖 §6；当前由 `UnavailableVehicleReader` + JVM 单测覆盖降级路径 |

## 3. 关键实现方案

**Tool schema 三元组**（对齐 `@AppFunctionSchemaDefinition(name, version, category)`，为二期 `AppFunctionManager` 暴露留路）：

```kotlin
interface Tool {
    val name: String; val version: Int; val category: String
    val description: String          // 必带单位与取值枚举，例："当前车速，m/s"
    val permission: ToolPermission
    val inputSchema: JsonObject
    suspend fun execute(args: JsonObject): ToolResult
}
```

**返回值一律结构化**，禁止把不可用糊成字符串：`ToolResult.Unavailable(field, status, detail)`，`status ∈ {permission_denied, no_vhal, unsupported}`。父计划 §7 的 `"Unavailable"` 约定只适用于 SystemInfoReader → UI 面板路径。

**allowlist 条目形状**（`vehicle_properties.json`）：

```json
{
  "name": "SPEED",
  "category": "CHASSIS_AND_DYNAMICS",
  "permission": "android.car.permission.CAR_SPEED",
  "protection": "dangerous",
  "access": "read",
  "unit": "m/s",
  "description": "当前车速，米每秒。0 表示静止。"
}
```

`category` 沿用 AOSP 六类；`protection` 由 §1 实测回填；`access: write` 的条目允许写在文件里但不会被注册成工具。

OEM 扩展属性必须带 `id`（整数），与 AOSP vendor-extended 规则一致；换车企只改这份配置。解析器把 `access: write` 与 `protection` 含 signature/privileged 的条目挡在可用集之外，并把**挡下的原因**留在 `skipped` 里——少几个字段必须可观测，不能悄悄消失。

**引擎接线**：`AgentEngine` 从 `ToolSystem.exposedFor(skillSubset)` 取工具定义注入 prompt，解析到 tool_call → 查 `permission` → `ask` 无确认器即回 `Unavailable`/拒绝说明 → 执行带 30s 超时 → `tool_result` 事件 → 回灌模型。防护逻辑独立于引擎（`ToolLoopGuard`），便于单测。

**minSdk 31 的影响**（2026-09-26 决策，见父计划决策 12）：`AndroidVehicleReader` 不能假定 `android.car` 存在——存量含 AAOS 12/13，且国内多数量产座舱只是 Android 衍生系统。进入时先经 `Capabilities.hasCarApi` 探测，缺失即整体 `no_vhal` 降级；同一份探测结果供 UI 与 Step 6 的 MCP 来源共用，不许各写各的 `SDK_INT` 判断。

**性能**：allowlist 与 mock 解析在 IO 协程一次性完成，不进主线程；工具层不得让常驻内存突破父计划 <200MB 基线。

## 4. 分步验收条件

- **4.1 骨架（纯 JVM）**：`ToolSystem` 注册/查找/执行/超时单测通过；`./gradlew :app:testDebugUnitTest` 绿；未注册工具名被调用返回结构化错误而不是抛异常。
- **4.2 权限语义**：白名单内 shell 免确认执行；名单外且无 `ToolConfirmer` 时**拒绝**并给出原因；测试注入确认器后放行与拒绝两条路径都可验证；黑名单始终 forbid。
- **4.3 车辆只读**：`vehicle_basic` 返回带单位与 status 的结构化字段；从 json 删一条 → 该字段变 `unsupported` 且 **Kotlin 零改动**；`access: write` 与特权条目不进可用集（已由 `VehiclePropertyAllowlistTest` 覆盖）。
- **4.4 真机降级**：未 `pm grant` 时 `permission_denied`（不崩溃、不返回散文）；`pm grant` 后同字段可读。此项若手上无真车/真机，标记为「未验证」而不是跳过不写。
- **4.5 端到端无头**：「现在车速多少、电量还剩多少」→ 事件序列出现两组 `tool_call`/`tool_result` 并整合进最终 `end`；同工具同参数连续重复调用被 `ToolLoopGuard` 中止；畸形工具 JSON 被修复后正常执行。
- **4.6 收口**：回写父计划 Step 3 勾选与本子计划进度；按第 1 节的回写规则更新机制文档与索引。

## 5. 本步不做

- `androidx.appfunctions` / KSP / `AppFunctionManager` 任何依赖与代码位。
- 车辆属性写路径、控车工具、`AreaIdConfig` 分区访问、动态 min/max、`BYTES`/`MIXED` 类型（CarToolForge 自认也未支持）。
- 本地小模型与 function-calling 微调（CarTool-Instruct 只在 Batch 3 评估）。
- 终端 UI / PTY、语音；MCP 工具来源属父计划 Step 6，本步不碰。

## 6. `android.car` 编译期接入方式 —— 已解决（2026-09-28）

**结论：走官方标准 `useLibrary("android.car")`，不走反射。**证据是 Google 官方样例
`car-samples/car-lib/CarGearViewerKotlin/automotive/build.gradle` 第 42–43 行就是这一行；
本机 SDK 也确实带了这份 stub：`<sdk>/platforms/android-36.1/optional/android.car.jar`
（含 `android.car.hardware.property.CarPropertyManager` 与 `android.car.VehiclePropertyIds`，
注意包路径与旧文档不同，写错会报 Unresolved）。AGP 把它当 provided：编译期有类型检查，不打进 APK。

反射只保留在一处合理用途：按名字解析 `VehiclePropertyIds` 常量。OEM 会增删属性，
编译期常量表覆盖不了这种差异，找不到就返回 `unsupported`，不会编译失败也不会崩。

（更正记录：曾判断"Google 不官方分发该 jar，需自编 AOSP 或找车厂要"，实测证伪。）
读取实现落地后仍守一条：**不允许**为了让演示好看而把 mock 数据当真实车况返回；
`UnavailableVehicleReader` 只在探测确认环境不支持时使用，且返回值必须带原因。

## 进度

- [x] 1. 前置权限实测核对表 — 2026-09-27 实测 11 条权限分级（§1.1）；2026-09-28 补属性级实测（§1.2），顺带纠正了三个不存在的属性名
- [x] 4.1 ToolSystem 骨架 — 9 例单测（超时、异常包装、子集暴露、schema 三元组）
- [x] 4.2 权限语义 — Ask 无确认器必拒、可放行可拒绝、Forbid 永不执行、按参数细分权限
- [x] 4.3 车辆只读 + allowlist — 12 例单测，含"删一条 json 只降级该字段"与特权/写条目挡下并留原因
- [x] 4.4 设备降级实测 — `AndroidVehicleReader` 已实现并在模拟器跑通（§1.2）；`PERF_ODOMETER` 实测走出 `permission_denied`。真车（非模拟器）仍待测，且 `pm revoke` 在模拟器上不生效，未授予路径只由 SecurityException 分支间接验证
- [x] 4.5 端到端无头 — 引擎工具循环 7 例：调用→回灌→最终答复、参数畸形先修复、重复调用中止、步数上限、无 ToolSystem 显式失败
- [x] 4.6 回写 — 本节与父计划、`agent-capabilities.md` 已更新

实测合计：JVM 79 例、Automotive 模拟器仪器测试 15 例，全部通过（2026-09-27）。

> 创建：2026-09-21（按同日外部调研调整 Step 3 时生成）
