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

**回写规则**：核对出的事实属稳定系统约束。字段少于 8 条时补进 `mechanisms/agent-capabilities.md` 关键约束；达到 8 条以上则新增 `mechanisms/vehicle-property-permissions.md` 并同步双索引。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `core/tools/Tool.kt` | 新增 | `Tool` 接口 + `ToolPermission`(Auto/Ask/Forbid) + `ToolResult`（Ok / Unavailable / Failed） |
| `core/tools/ToolSystem.kt` | 新增 | 注册、查找、按 skill 子集暴露、执行、默认 30s 超时、错误包装成 `ToolResult.Failed` |
| `core/tools/ToolConfirmer.kt` | 新增 | `ask` 的回调接口；**未注入即默认拒绝**（fail-closed） |
| `core/tools/SystemInfoTool.kt` | 新增 | 包一层 `SystemInfoProvider`，只读，auto |
| `core/tools/ShellTool.kt` | 新增 | `Runtime.exec`；白名单 auto / 名单外 ask / 黑名单 forbid |
| `core/tools/VehicleBasicTool.kt` | 新增 | 唯一车况工具，按 allowlist 产出结构化 bundle |
| `system/vehicle/VehiclePropertyAllowlist.kt` | 新增 | 解析 `assets/vehicle/vehicle_properties.yaml`；`access: write` 与 privileged 条目不注册 |
| `system/Capabilities.kt` | 新增 | 运行期能力探测：`hasCarApi` / `hasAppFunctions` / `perAppLocale` / 后续 MCP 可达性。工具与 UI 共用一份，不许各写各的 `SDK_INT` 判断 |
| `system/vehicle/VehicleReader.kt` | 新增 | `suspend fun read(spec): VehicleValue` 接口，纯 Kotlin 可测 |
| `system/vehicle/AndroidVehicleReader.kt` | 新增 | `Car.createCar` + `CarPropertyManager`；`SecurityException`→`permission_denied`，连接失败→`no_vhal` |
| `system/vehicle/FakeVehicleReader.kt` | 新增 | 读 `assets/vehicle/mock_state.json`，模拟器/单测用 |
| `assets/vehicle/vehicle_properties.yaml` | 新增 | 属性 allowlist 唯一事实源 |
| `assets/vehicle/mock_state.json` | 新增 | mock 车况 |
| `core/engine/AgentEngine.kt` | 改 | function-calling 分支 + `tool_call` / `tool_result` 事件（Step 2 预留位） |
| `core/engine/ToolLoopGuard.kt` | 新增 | 步数上限 8、重复调用检测、畸形 JSON 修复重试 |
| `app/build.gradle.kts` + `gradle/libs.versions.toml` | 改 | 加 yaml 解析库（版本进 libs 目录，不在模块级写版本号） |
| `app/src/test/.../core/tools/*` | 新增 | JVM 单测：ToolSystem 超时/权限/降级、allowlist 解析、循环防护 |
| `app/src/androidTest/.../VehicleReaderTest.kt` | 新增 | 模拟器实跑，验证无 VHAL 时的结构化降级 |

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

**allowlist 条目形状**（`vehicle_properties.yaml`）：

```yaml
properties:
  - name: SPEED
    category: CHASSIS_AND_DYNAMICS      # AOSP 六类之一
    permission: android.car.permission.CAR_SPEED
    protection: dangerous               # 实测回填
    access: read                        # write 条目不注册为工具
    unit: m/s
    description: "当前车速"
```

OEM 扩展属性必须带 `id`（整数），与 AOSP vendor-extended 规则一致；换车企只改这份 yaml。

**引擎接线**：`AgentEngine` 从 `ToolSystem.exposedFor(skillSubset)` 取工具定义注入 prompt，解析到 tool_call → 查 `permission` → `ask` 无确认器即回 `Unavailable`/拒绝说明 → 执行带 30s 超时 → `tool_result` 事件 → 回灌模型。防护逻辑独立于引擎（`ToolLoopGuard`），便于单测。

**minSdk 31 的影响**（2026-09-26 决策，见父计划决策 12）：`AndroidVehicleReader` 不能假定 `android.car` 存在——存量含 AAOS 12/13，且国内多数量产座舱只是 Android 衍生系统。进入时先经 `Capabilities.hasCarApi` 探测，缺失即整体 `no_vhal` 降级；同一份探测结果供 UI 与 Step 6 的 MCP 来源共用，不许各写各的 `SDK_INT` 判断。

**性能**：yaml 与 mock 解析在 IO 协程一次性完成，不进主线程；工具层不得让常驻内存突破父计划 <200MB 基线。

## 4. 分步验收条件

- **4.1 骨架（纯 JVM）**：`ToolSystem` 注册/查找/执行/超时单测通过；`./gradlew :app:testDebugUnitTest` 绿；未注册工具名被调用返回结构化错误而不是抛异常。
- **4.2 权限语义**：白名单内 shell 免确认执行；名单外且无 `ToolConfirmer` 时**拒绝**并给出原因；测试注入确认器后放行与拒绝两条路径都可验证；黑名单始终 forbid。
- **4.3 车辆只读**：模拟器上 `vehicle_basic` 返回结构化车速/电量/续航；删掉 yaml 一行 → 对应字段变 `Unsupported` 且 **Kotlin 代码零改动**；yaml 里加一条 `access: write` → 不出现在工具定义中。
- **4.4 真机降级**：未 `pm grant` 时 `permission_denied`（不崩溃、不返回散文）；`pm grant` 后同字段可读。此项若手上无真车/真机，标记为「未验证」而不是跳过不写。
- **4.5 端到端无头**：「现在车速多少、电量还剩多少」→ 事件序列出现两组 `tool_call`/`tool_result` 并整合进最终 `end`；同工具同参数连续重复调用被 `ToolLoopGuard` 中止；畸形工具 JSON 被修复后正常执行。
- **4.6 收口**：回写父计划 Step 3 勾选与本子计划进度；按第 1 节的回写规则更新机制文档与索引。

## 5. 本步不做

- `androidx.appfunctions` / KSP / `AppFunctionManager` 任何依赖与代码位。
- 车辆属性写路径、控车工具、`AreaIdConfig` 分区访问、动态 min/max、`BYTES`/`MIXED` 类型（CarToolForge 自认也未支持）。
- 本地小模型与 function-calling 微调（CarTool-Instruct 只在 Batch 3 评估）。
- 终端 UI / PTY、语音；MCP 工具来源属父计划 Step 6，本步不碰。

## 进度

- [ ] 1. 前置权限实测核对表 — 未开始（阻塞 4.4 与 allowlist 初版）
- [ ] 4.1 ToolSystem 骨架
- [ ] 4.2 权限语义
- [ ] 4.3 车辆只读 + allowlist
- [ ] 4.4 真机降级
- [ ] 4.5 端到端无头
- [ ] 4.6 回写

> 创建：2026-09-21（按同日外部调研调整 Step 3 时生成）
