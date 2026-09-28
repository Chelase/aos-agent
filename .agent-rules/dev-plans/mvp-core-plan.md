# 最小 MVP 核心能力计划（Agent 执行 + Skill + AOC 接入）

> **统一愿景对齐**：本计划与 [../docs/unified-ecosystem-vision.md](../docs/unified-ecosystem-vision.md) 对齐——
> ① Agent 核心参考 OpenClaw/Hermes 模式（契约见 `../mechanisms/agent-capabilities.md`，不自研架构）；
> ② AOC 接入复用对端 Entry 协议与身份机制，不自建协议（契约见 `../mechanisms/aoc-integration.md`）——**本期 MVP 不含 AOC 接入**（2026-09-27 决策），契约文档保留待二期；
> ③ 本地引擎保证 AOC Hub 不可用时独立运行（愿景原则三）；
> ④ 工具优先覆盖车辆差异化能力（vehicle 工具先行）；
> ⑤ 轻内核 + 能力外挂：MCP 作为第二类工具来源接进 ToolSystem，内核不感知（愿景原则五）；
> ⑥ "任意车机可运行"当验收项处理：minSdk 降到 31，系统特性一律运行期探测降级。无偏离。

> ⚠️ **复杂功能 — 禁止直接实现**
>
> 本计划属于复杂功能，Agent 读取本计划文档后，不得直接按全文一次性实现。
>
> **必须按以下流程执行：**
> 1. 从「步骤」中选择一个 Step
> 2. 将该 Step 拆解为独立子计划，写入 `dev-plans/mvp/step<N>-<topic>-plan.md`（首个子计划落地时创建该子目录及其 README）
> 3. 子计划需包含：改动文件清单、关键实现方案、分步验收条件
> 4. 子计划就绪后再开始编码实现
> 5. 完成后回到本计划，选择下一个 Step
>
> **禁止：** 一个 Agent 调用直接实现多个 Step。

> 配套机制文档：`../mechanisms/architecture-overview.md`、`../mechanisms/agent-capabilities.md`、`../mechanisms/aoc-integration.md`、`../mechanisms/boot-and-foreground-service.md`
> 借鉴来源：
> - [../docs/android-agent-projects-survey.md](../docs/android-agent-projects-survey.md) — 手机端 Agent（OpenMinis/Operit/RikkaHub）
> - [../docs/car-agent-ecosystem-survey.md](../docs/car-agent-ecosystem-survey.md) — 车载演示仓库 / 座舱形态文章 / HaloOS（2026-09-14 吸收）
> - [../docs/car-agent-market-survey-2026-09.md](../docs/car-agent-market-survey-2026-09.md) — 车机 Agent 成熟度（Gemini / 高通 Claw / 国内 OEM 量产 / 华为技能平台 / CarToolForge，2026-09-21 吸收）
>
> 「抄思路不抄代码」；两个 GitHub 车载仓库无 License、HaloOS 不在座舱层，一律不复制代码。映射见下方「借鉴来源映射」。

## 为什么做这个 MVP（2026-09-06 决策；2026-09-14 强化）

用户决策：**不是 UI 优先。** 面板、对话气泡、驾驶舱大屏都不构成 MVP 门闩。优先打通一条纵向能力切片：

> **车机上的 Agent 能被代码驱动、能调工具干活、能按 Skill 组织能力、能经 MCP 接入外部工具来源。**
>
> 2026-09-27 用户决策：**AOC 接入不在本期 MVP**（原 Step 1 / Step 5 移出）。契约与协议调研全部保留，二期直接接上，不重做设计。

「能对话」指引擎能吃自然语言、跑循环、吐出结构化事件与最终回复；调试用的最小输入面可以后补，不能反过来卡住 Step 2/3/4/6。

MVP 一句话验收（2026-09-27 改版）：同一套**无头**引擎，被单元测试驱动时能按 Skill 组织并连续调用本地工具与远端 MCP 工具干活（含车辆查询，模拟器无权限时走 mock），产出带工具轨迹的结构化事件流；全程不需要 AOC Hub、不需要界面。

## 与既有路线图的关系

| 原批次内容 | 去向 |
|---|---|
| Batch 1 Step 2 系统感知面板、Step 3 工程师模式、Step 4 自检报告、Step 5 电源监听 | **冻结延后**（SystemInfoProvider 已有产出直接复用为 `system_info` 工具数据源） |
| Batch 2 Step 2 Agent 主循环、Step 3 工具系统 | **提前并入本 MVP**（Step 2 / Step 3） |
| Batch 2 Step 1 终端、Step 4 语音、Step 5/6 工程师增强与 OEM 降级 | 留在原批次，MVP 完成后回归（OEM 降级思想在 MVP 中仅体现为 vehicle 工具不可用静默降级） |

MVP 全部 Step 完成后回写 `roadmap.md`。

## 关键方案决策

1. **双脑模式**：本地 `AgentEngine` 是常驻主脑（离线独立，愿景原则三）；AOC Hub 的 Brain 是增量能力（经 DM 通道触达），不是生存依赖。
2. **（二期）AOC 接入走 Entry 模式**：纯 HTTP JSON（register / send_event / poll），对端参考实现 `src/aoc/entry/network_client.py` 已验证可行；**不做** gRPC 执行节点与完整 task_delegation（复杂度高一档，延后）。
3. **（二期）跨设备接力的 MVP 变体**：Hub 通过 DM 下发结构化 skill 请求 → 车机本地执行 → DM 回传结果。完整 `task.notification / complete_task` 链路延后。AOC 即座舱形态文章里的跨端控制面（注册 / 能力声明 / DM ≈ Topic 的 MVP 子集）；**不引入 WTT 或第二套协议**。
4. **Skill 形态**：定义文件 + `SkillRegistry`，参考 OpenClaw Skills；MVP 内置 2 个只读 skill。字段向 AOC capability（id/name/description/tags/input_modes/output_modes）对齐，并补 `permission` / `fallback` / `result_hint`（行为可先用 Tool 三级权限兜底）。
5. **shell 是工具不是终端**：`shell_exec` 用 `Runtime.exec` + 白名单，不做 PTY/终端 UI；终端体验留在 Batch 2 Step 1。
6. **无头优先，UI 可选**：引擎对外只发结构化事件；AOC `skill_request` 与单元测试走同一无头入口。对话界面若做，只消费事件，不作为任何 Step 的完成条件。
7. **控车分级（本期只做查询）**：信息查询 = auto；舒适性控制 / 运动控制 / 高风险 = 本期不上（对应 Tool 的 ask/forbid，留给后续批次）。2026-09-21 修正：写控车受阻是**结构性**的（AOSP `CONTROL_*` 全为 signature|privileged），不是排期取舍，详见 Step 3 调整点 4。
8. **模拟器可测车况**：`VehicleReader` 接口 + `FakeVehicleReader`（JSON mock）；真车走 Car API，不可用则结构化降级，不返回散文、不弹窗。
9. **借鉴策略**：手机端三仓库传染性协议 + 两个车载仓库无 License + HaloOS 层位不同，一律不复制代码，只吸收接口与工程模式；具体落点见下表。
10. **工具 schema 对齐 Android 16 AppFunctions 语义，但不引入其依赖**：`Tool` 定义用 `name` + `version` + `category` 三元组（对齐 `@AppFunctionSchemaDefinition`），二期可零重设计地把同一批工具经 `AppFunctionManager` 暴露给系统级 Agent。`androidx.appfunctions` 仍在 alpha，不进 MVP。
11. **车辆属性走声明式 allowlist**：`assets/vehicle/vehicle_properties.json` 是唯一事实源，OEM 差异与字段增减改配置不改 Kotlin；`access: write` 条目一律不注册为工具。
12. **"任意车机"落到 minSdk 与能力探测上**：minSdk 由 34 降到 **31**（Android 12，覆盖 AAOS 12/13 存量车型）。凡依赖 API 版本或系统特性者（Car API、AppFunctions(36)、框架 per-app locale(33)、specialUse 前台服务(34)）一律运行期探测 + 结构化降级，已落地样例见 `i18n/AppLocale.kt:localeStoreFor`。风险清单见 `../mechanisms/risk-assessment.md`。
13. **MCP 是工具来源，不是新编排层**：只实现 **MCP client**（streamable-HTTP / SSE），远端工具经适配层注册成 `Tool`；引擎、SkillRegistry、AOC 三层都不感知 MCP。跨设备身份与接力仍只走 AOC——MCP 不替代 AOC（愿景约束 5）。stdio 子进程型 server 在量产车机上大概率不可用，**未核实前不设计**。

## 借鉴来源映射（抄思路不抄代码）

| 借鉴点 | 落点 | 来源 |
|---|---|---|
| Provider 接口形态：Flow 流式分块、工具定义参数位预留、baseURL/headers 归一化 | Step 2 LlmRouter | OpenMinis `LLMProvider` / RikkaHub `ai` 模块 |
| 无头驱动 Agent（不开 UI 执行完整循环） | Step 2 引擎事件流 + Step 5 skill_request | OpenMinis `HeadlessChatRunner`；car-agent 事件类型（只借形状） |
| 引擎事件：`start` / `token` / `tool_call` / `tool_result` / `end` / `error` | Step 2 `AgentEvent`；Step 5 `skill_result` 回带工具轨迹 | car-agent `events.ts` |
| 工具三级权限模型 auto / ask / forbid | Step 3 ToolSystem；`ask` 走 `ToolConfirmer` 回调，无确认器默认拒绝 | Operit |
| 循环防护：步数上限、重复调用检测、畸形工具 JSON 修复 | Step 3 AgentEngine | OpenMinis `ToolLoopDetector` / `ToolJsonRepair` |
| 工具描述写清必填/可选/示例；返回结构化字段 | Step 3 各 Tool schema | car-agent MCP tool docstring；HaloOS「服务化」 |
| Car API 不可用时的 JSON mock Reader | Step 3 `FakeVehicleReader` | car-agent `vehicle_state.json`；HaloOS SIM 模拟器思想 |
| 车辆只读字段清单（车速/电量/续航先落地，其余只预留名） | Step 3 `vehicle_basic` | car-agent `vehicle_server` |
| SKILL.md 风格 skill 定义；按类别取工具子集；未命中回退通用对话 | Step 4 SkillRegistry | OpenMinis；caragent `ToolRegistry` / `IntentRouter` |
| Skill 带权限、降级、结果摘要字段 | Step 4 `skill.json` | 知乎座舱形态文 SKILL 定义 |
| 语音 VAD+STT+TTS、本地模型、聊天 UI 视觉 | 延后：Batch 2 Step 4 / Batch 3 / 可选调试屏 | Operit / RikkaHub |
| 车辆属性**声明式 allowlist**（`name`/`id`/`category`/`description`，vendor 扩展必须带 `id`） | Step 3 调整点 2：`vehicle_properties.json` | [autoharness/CarToolForge](https://github.com/autoharness/CarToolForge) `config/`（MIT） |
| 按数据类型泛型展开的 get/set 工具族（15+ 个 `getXProperty`） | **拒绝**：会把选工具的负担压给模型；改用语义化少工具 | CarToolForge `CarPropertyApi.kt` |
| 平台级工具声明语义 `@AppFunctionSchemaDefinition(name, version, category)` | Step 3 调整点 1：`Tool` schema 三元组对齐，不引依赖 | Android 16 AppFunctions（[官方样例](https://github.com/android/appfunctions)，Apache-2.0；`androidx.appfunctions` 1.0.0-alpha10） |
| 「泛型工具需要 >20B 模型才有好效果」+ 小模型要靠微调数据集救 | Step 3 调整点 3：MVP 用远程大模型 + 只读少工具绕开；本地小模型留 Batch 3 再评估 | CarToolForge README Limitations；[CarTool-Instruct](https://huggingface.co/datasets/autoharness/CarTool-Instruct) |
| 车控属性全需平台签名、读属性按 normal/dangerous 分级 | Step 3 调整点 4：写控车结构性阻断，本期不预留写路径 | CarToolForge `AndroidManifest.xml` 权限注释（与 AOSP `car-lib` 一致，**本项目未实测**） |

## 改动范围

### 模块

- 新增：`system/aoc/`、`system/vehicle/`、`core/llm/`、`core/tools/`（含 `core/tools/mcp/` 适配层）、`core/skills/`、`core/engine/`
- 扩展：`data/store/`、`service/`
- **不作为本期必达**：`ui/chat/`（若拆 Step 子计划时需要一个调试输入框，单独标明「可选、不挡验收」）
- 依赖：`app/build.gradle.kts` 新增 okhttp、kotlinx-serialization-json；`android.car` 以 compileOnly 引入（运行环境由车机/模拟器提供）。MCP client 优先用 okhttp + serialization 薄实现，不为此引入重依赖（体积基线见 `../mechanisms/architecture-overview.md` 关键约束 5）；若改用官方 SDK 需先确认其 minSdk 与传输层满足"任意车机"前提。

### 重点文件（新增）

- `system/aoc/AocEntryClient.kt` / `AocConnectionManager.kt`
- `data/store/AocIdentityStore.kt`、`data/store/LlmConfigStore.kt`
- `core/llm/LlmRouter.kt`
- `core/engine/AgentEngine.kt` / `ContextManager.kt` / `AgentEvent.kt`
- `core/tools/Tool.kt` / `ToolSystem.kt` / `ToolConfirmer.kt` / `SystemInfoTool.kt` / `ShellTool.kt` / `VehicleBasicTool.kt`
- `system/vehicle/VehicleReader.kt` / `AndroidVehicleReader.kt` / `FakeVehicleReader.kt` / `VehiclePropertyAllowlist.kt` + `assets/vehicle/vehicle_properties.json`
- `core/skills/SkillRegistry.kt` + `assets/skills/<id>/skill.json`
- `core/tools/mcp/McpClient.kt` / `McpToolSource.kt`（Step 6：远端工具 → `Tool` 适配）
- `system/Capabilities.kt`（运行期能力探测：Car API / AppFunctions / per-app locale / server 可达性，供工具与 UI 共用）

## 步骤

### Step 1. AOC Entry 接入链路 —— 移出本期（2026-09-27 决策，二期直接按本节实施）

**内容：** Entry HTTP 客户端（register/sendEvent/poll/health，契约见 `../mechanisms/aoc-integration.md`）；身份经 DataStore 持久化、重启复用；`AocConnectionManager` 在前台服务协程中跑注册 → 长轮询 → 指数退避重连；收到的消息先落日志（Step 5 再接引擎）。Hub 地址 BuildConfig 默认值（模拟器 `http://10.0.2.2:8700`）；凭证由 AOC 侧开通专用 agent_group 后注入，Debug 配置不进 git。

**验收：**
- 模拟器启动后注册成功获得 secret；杀进程重启复用同一 agent_id
- 停止/重启 AOC Hub，客户端自动重连不崩溃
- AOC 侧发 DM，车机端 poll 到并打印；`/api/health` 可见 aos-agent

### Step 2. 本地 Agent 主循环（无头优先）

**内容：** `LlmRouter`——接口形态参照 OpenMinis `LLMProvider` / RikkaHub `ai` 模块：`streamMessage(): Flow<LlmStreamChunk>` 流式分块、工具定义参数位为 Step 3 预留、自定义 baseURL/headers 归一化（兼容 OpenAI 协议中转）；MVP 只实现 OpenAI 兼容一种 provider，配置走 DataStore，AOC 不可达时独立工作。`ContextManager`（系统提示 + 会话历史 + Token 截断预留接口）。`AgentEngine` 单轮推理骨架（工具调用分支为 Step 3 预留）——**只暴露可编程入口**，产出 `Flow<AgentEvent>`（至少 `start` / `token` / `end` / `error`；Step 3 再补 `tool_call` / `tool_result`）。无 UI 也能驱动完整循环，这是 Step 5 `skill_request` 的前置。对话界面不在本 Step 范围。

**验收：**
- 无 AOC / 不通 hub：测试代码喂入 query → 收到流式 `token` 事件 → 多轮上下文生效
- 引擎可在无 Activity 情况下被直接驱动（仪器测试或 JVM 测试冒烟）
- 首包延迟与内存记录基线，不违反架构性能基线
- **不验收** 消息列表、气泡、Home 入口、流式上屏

### Step 3. ToolSystem 与首批工具

**内容：** `Tool` 接口 + `ToolSystem`（注册/查找/执行/默认 30s 超时/错误包装），Tool 定义带**三级权限标记**（auto / ask / forbid）。`ask` 经 `ToolConfirmer` 回调；未注入确认器时**默认拒绝**（fail-closed），不要把系统弹窗写成主路径。AgentEngine 扩展 function calling 循环，并补齐 `tool_call` / `tool_result` 事件。循环防护参照 OpenMinis：步数上限 8、重复调用检测、畸形工具 JSON 修复后重试。工具 description 写清必填/可选/调用示例。返回值用结构化字段（数字/枚举），由引擎再生成自然语言。

**2026-09-21 调整（依据见「借鉴来源映射」新增行）**：

1. **`Tool` schema 用 `name` + `version` + `category` 三元组**，对齐 Android 16 `@AppFunctionSchemaDefinition` 语义；description 必带**单位与取值枚举**（CarToolForge 的 yaml 每条都写清 `0/1/256` 含义，这是降低畸形 function-call 概率的实证做法）。不引入 `androidx.appfunctions` 依赖（alpha10 + 需 API 36），只借形状，为二期把工具暴露给系统 Agent 留零改造路径。
2. **车辆属性改成声明式 allowlist**：`assets/vehicle/vehicle_properties.json` 为唯一事实源，字段 `name` / `id`(vendor 扩展必填) / `category` / `permission` / `access` / `unit` / `values` / `description`；分类沿用 AOSP 六类 `VEHICLE_INFO` / `ENERGY_MANAGEMENT` / `HVAC_SYSTEM` / `BODY_CONTROL` / `CHASSIS_AND_DYNAMICS` / `LIGHTING_SYSTEM`。`VehicleReader` 按 allowlist 产出，OEM 差异与字段增减只改配置不改 Kotlin（对齐已有的「探测 → 适配 → 降级」）。
3. **工具保持「少而语义化」**：MVP 仍是一个 `vehicle_basic`（返回结构化 bundle，可选 `fields` 入参），**不做** CarToolForge 那种按数据类型泛型展开的 15+ 工具族。依据是他们自己的实测结论：泛型工具族需要 >20B 模型才有可用效果，为此另做 CarTool-Instruct 微调数据集。第二个独立数据点来自国内供应商侧——诚迈×智达诚远的萤火Claw 端侧车控方案用的是 Qwen3-30B-A3B。车控 function-calling 的实用下限看起来在 20~30B 级，不是 3B/7B。MVP 用远程大模型 + 只读少工具绕开该问题；本地小模型属 Batch 3，届时再评估该数据集。
4. **写控车是结构性阻断**：AOSP 侧 `CONTROL_CAR_CLIMATE` / `WINDOWS` / `SEATS` / `DOORS` / `MIRRORS` / `ENERGY` / `GLOVE_BOX` 及 `CAR_TIRES` 均为 **signature|privileged**，普通可安装 APK 拿不到；读侧 `CAR_SPEED`、`CAR_ENERGY` 为 **dangerous**（可运行时授予），`CAR_INFO`、`CAR_POWERTRAIN`、`CAR_EXTERIOR_ENVIRONMENT`、`CAR_ENERGY_PORTS` 为 **normal**。→ 本期 allowlist 中 `access: write` 条目**不注册为可调用工具**，也不预留写路径代码位；任何控车写能力的前置条件是 Batch 3 系统级预装 + OEM 授权，而非排期。分级确认本身是行业通行做法（智己 IM Ultra Agent 对高风险指令走对话式二次确认），与我们 `ask` + `ToolConfirmer` fail-closed 同形，将来拿到签名也照此办理。
   ✅ 上述等级已于 2026-09-27 在 Automotive 模拟器（API 35 / user 版）用 `dumpsys package permission` 逐条实测确认，11 条结果见 [mvp/step3-toolsystem-plan.md](./mvp/step3-toolsystem-plan.md) §1.1。**属性级**可读性仍未测到：user build 上 `cmd car_service get-prop` 被拒，只能从应用内部实测，而这卡在 `android.car` 的编译期接入方式（子计划 §6）。
5. **降级返回结构化而不是散文**：取不到时返回 `{field, status: permission_denied | no_vhal | unsupported, detail}`。`rules/project-onboarding.md` §7 的 `"Unavailable"` 字符串约定只适用于 SystemInfoReader → 面板路径；工具返回值要进模型上下文，散文会堵死循环。

首批 3 工具，全部可无头验收：
- `system_info`（复用 SystemInfoProvider，auto）
- `shell_exec`（`Runtime.exec`；白名单内 auto、白名单外 ask、黑名单 forbid）
- `vehicle_basic`（`VehicleReader`，auto，**只读**）。本期字段候选：车速、电量、续航、车外温度、车型/里程（后两项 normal 权限，模拟器最容易拿到）；**胎压剔除**（privileged）。真车走 `AndroidVehicleReader`；模拟器无 VHAL / 权限未授予走 `FakeVehicleReader`（JSON 夹具）。不上空调/车窗/座椅/驾驶模式等控制工具。

**验收：**
- 无头：「现在车速多少、电量还剩多少」→ 依序出现 `tool_call`/`tool_result` 并整合进最终 `end`
- 模拟器无 VHAL 时 mock 仍返回结构化车速/电量/续航，而不是 `"Unavailable"` 散文堵死循环
- 白名单内 shell 免确认执行；白名单外无 `ToolConfirmer` 时拒绝；测试注入确认器可放行或拒绝
- 任一工具挂起 30s 被超时终止，Agent 能说明失败
- 同工具同参数连续调用被循环防护中止；畸形工具 JSON 被修复后正常执行
- **新增**：从 `vehicle_properties.json` 删一条即该字段结构化降级，不改任何 Kotlin；`access: write` 条目存在也不注册
- **新增**：`Tool` 的 schema 输出含 `name`/`version`/`category`，且 description 带单位与枚举，测试断言字段齐全
- [x] **设备核对表**（模拟器实测）：车速/电量/电池容量/车外温度/车型/厂商可读；`PERF_ODOMETER` 返回 `permission_denied`（所需权限不是 CAR_POWERTRAIN，真名待查）。真车仍待测；`pm revoke` 在这台模拟器上不生效，未授予路径由 SecurityException 分支间接验证

### Step 4. Skill 机制

**内容：** `assets/skills/<id>/skill.json`：id/name/description/触发场景/tools/提示词片段，外加 `permission`（查询|舒适|运动|高风险，MVP 仅用查询）、`fallback`（工具不可用时的说明）、`result_hint`（回传/摘要提示）。`SkillRegistry` 启动扫描、向引擎注入 skill 列表、命中后只加载该 skill 的工具子集与提示词。内置 2 个只读 skill：`vehicle_status`、`system_diagnostics`。字段对齐 AOC capability，预留上报映射。未命中 skill 回退通用对话，不报错。不拆多 Agent。工具来源不在此层扩展——MCP 在 Step 6 作为工具来源接进 `ToolSystem`，Skill 层只按名字引用。

**验收：**
- 无头：「车辆状态怎么样」命中 `vehicle_status` 并只暴露其工具集
- 新增一个 skill 只需加定义文件，不改引擎代码
- 未命中 skill 时退回通用对话不报错
- 两个内置 skill 的 `permission` 均为查询；定义里出现控制类工具名也不注册执行

### Step 5. AOC 双向协作收口 —— 移出本期（2026-09-27 决策，依赖 Step 1）

**内容：** poll 收到的 DM 分流——文本回复写日志（可选通知）；结构化 `{type:"skill_request", skill, params}` → Step 2 无头入口走 SkillRegistry + ToolSystem → DM 回传 `{type:"skill_result", ...}`，payload 含工具轨迹摘要（工具名、成功/失败、关键结构化字段），便于 Hub 侧观测，不上 Topic 追踪平台。本地 skill 列表按 AOC skill 字段随注册 metadata 上报（HTTP 通道能力范围内；完整 `set_capabilities` 若需 gRPC 则记录 gap 延后）。本地引擎 → hub 委派只做接口与日志预留，不实现完整 route_task。本 Step 不依赖对话 UI。

**验收：**
- AOC 侧向 aos-agent 发 skill_request → 收到正确 skill_result（含工具轨迹）
- AOC discovery 能看到 aos-agent 及其 skill 列表
- hub 全程不可达时本地无头循环 / 工具 / skill 不受影响

### Step 6. MCP 工具来源接入（2026-09-26 新增）

**内容：** 在 `ToolSystem` 之上加一层 **MCP client 适配**：连接已存在的 MCP server（streamable-HTTP / SSE），把远端 `tools/list` 映射成 `Tool` 注册进 `ToolSystem`，`tools/call` 映射到 `Tool.execute`，返回仍走 `ToolResult` 结构化路径。**引擎、SkillRegistry、AOC 三层都不感知 MCP**——它们看到的还是 `Tool`，这正是愿景原则五"轻内核 + 能力外挂"的判据：加一类能力来源不改内核。

配置走 DataStore（一个 server 一条目：地址 + 可选 token）。连接失败或 server 不可达 → 该来源整体不注册 + 结构化状态，不把异常抛进模型上下文。权限：远端工具默认 `ask`，无 `ToolConfirmer` 即拒（fail-closed）；可按 server 粒度白名单提升为 `auto`。**不做 MCP server 侧，不做 stdio 子进程型 server**（量产车机无 root 时 exec 受限，transport 可行性未核实，见 `../docs/` 待补的 MCP 调研）。

**验收：**
- 无头：连上本地一个假 MCP server（HTTP/SSE），其工具出现在引擎可用工具里，调用产生的 `tool_call`/`tool_result` 与本地工具**同形**
- 拔掉该 server：本地工具、skill、AOC 链路照常，只有该来源消失
- 未注入 `ToolConfirmer` 时远端工具不被执行
- 远端工具数量增长不改引擎代码；Skill 的 `tools` 能按 `server/工具名` 引用远端工具
- 包体积与冷启动不超过 `../mechanisms/architecture-overview.md` 关键约束 5 的基线，超出需在子计划说明

## 验收清单

主路径（必须，全部无头，不依赖 Compose 界面，也不依赖 AOC）：

- [x] 无 AOC 参与下本地无头多轮对话可用
- [x] 模拟器无 VHAL 时 `vehicle_basic` 仍能返回结构化 mock
- [x] Agent 多步调用 ≥ 2 个工具并整合结果
- [x] 2 个内置 skill 可命中（远程触发随 AOC 一并二期）
- [x] 新增 skill 零引擎改动
- [x] 无 `ToolConfirmer` 时白名单外 shell 默认不执行；注入确认器后 ask 可放行或拒绝
- [x] 循环防护与畸形 JSON 修复生效
- [x] 车辆属性来自 `vehicle_properties.json`；`access: write` 条目不注册为工具
- [ ] 性能基线不回退（前台服务就绪 < 2s，常驻内存 < 200MB）
- [ ] 远端 MCP 工具可作为第二类工具来源被引擎调用，且拔链不影响本地能力
- [ ] 低版本车机（API 31/32）：能力探测生效，per-app locale 等缺失项降级为"跟随系统 + 说明原因"，不崩溃

非门闩（允许缺席）：

- [ ] 调试用对话界面 / Home 入口 / 流式气泡

## 不在本期做的事

- 对话 UI 打磨、工具时间线、Agent 拓扑可视化、RikkaHub 风格聊天页
- 空调 / 车窗 / 天窗 / 座椅 / 氛围灯 / 驾驶模式等控制类工具
- WTT / `wtt.sh` / OpenClaw `@cecwxf/wtt`；任何第二套跨设备 Topic 网络
- RAG 知识库、LangGraph 式多 Agent 编排
- **MCP server 侧**（把本车工具反向暴露成 MCP）与 stdio 子进程型 MCP server；本期只做 streamable-HTTP / SSE 的 client（Step 6）
- 跨端长期记忆库、家庭/办公 Agent、VIN 作为跨设备身份
- 集成 HaloOS / VCOS / ADOS 源码
- 终端 UI / PTY JNI（Batch 2 Step 1）
- 语音（唤醒/ASR/TTS，Batch 2 Step 4）
- 系统感知面板、工程师模式增强、自检报告、电源监听（Batch 1 冻结部分）
- 本地 LLM、Live2D、插件市场、第三方应用管理、系统级预装（Batch 3）
- **AOC 接入全部（原 Step 1 Entry 链路与 Step 5 双向协作）**：2026-09-27 用户决策移出本期 MVP，契约见 `../mechanisms/aoc-integration.md`，二期按原文实施
- AOC gRPC 执行节点与完整 task_delegation（AOC 接入二期）
- `androidx.appfunctions` 依赖与 `AppFunctionManager` 对外暴露（二期；本期只对齐 schema 语义）
- 车辆属性**写路径**与控车工具（受 signature|privileged 阻断，前置为 Batch 3 系统级预装 + OEM 授权）

## 外部依赖与风险

- **AOC 侧凭证**：对端当前为单组 admin password_hash 认证，需与 AOC 维护者（对端仓库 `D:\code\project\AgentOpenConnect`）协调为本项目开通专用 agent_group / 入口凭证；admin 凭证不得下发到车机。契约细节见 `../mechanisms/aoc-integration.md` 关键约束。
- **网络边界**：模拟器经 10.0.2.2 访问宿主机 hub；真车跨公网需 AOC gRPC mTLS 或内网/VPN，MVP 阶段限制在可信网络。
- **shell 能力上限**：app 沙箱 + SELinux 限制 shell_exec 能力范围，MVP 不绕过——这正是后续终端 PTY / 系统级化的动机。
- **平台方已占位（2026-09 调研）**：Gemini for Android Automotive 自 2026-06-26 起在量产车 rollout（Volvo EX30 首批，可直接调空调/座椅加热/雨刮，能力取决于各车企集成深度）；高通 2026-06-05 启动「车端人工智能 Claw 生态计划」，提供通用智能体框架 + 开放 SKILL HUB，运行于骁龙数字底盘，首批伙伴含中科创达 / 德赛西威 / 斑马智能 / 镁佳 / 诚迈 / 车联天下。→ aos-agent 的价值不能落在"能聊天 / 能控车"，只能是**跨设备 Agent 网络节点 + 离线独立 + 用户可自装**；工具层的能力声明形状因此要与外部编排层可对齐（见决策 10）。
- **第三方没有车厂分发通道**（2026-09-21 国内面调研补实）：面向车主的车机 Agent 已成熟且**全部封闭**——理想同学 + OTA 8.2（2026-01-23，多步分解 + 原生控车 + 记忆 + 主动建议）、小鹏天玑 AIOS 6.0（2026-02-04 起 OTA）、极氪超级Eva（2026-03 首搭）、智己 IM Ultra Agent（2026-03-26）都不对外；唯一有第三方技能平台的是华为小艺 / HarmonySpace 6（自然语言开发 Skill、一键发布、车机跨端同步），但检索不到线控/ADAS/安全子系统的公开 API。诚迈×智达诚远的萤火Claw（端侧 Qwen3-30B-A3B + 可独立调度能力单元 + SDK）仍是 B2B 授权且无具名量产车。→ 结论：**缺口不在"能不能做车机 Agent"，在"AAOS 上第三方拿不到的那一层"**；MVP 验收一律锚定模拟器与用户自有设备 sideload，**不得**把真车控车写成验收条件。详见 [../docs/car-agent-market-survey-2026-09.md](../docs/car-agent-market-survey-2026-09.md)。

## 进度

- [~] Step 1. AOC Entry 接入链路 — 立项 2026-09-06；**2026-09-27 移出本期 MVP**（二期）
- [x] Step 2. 本地 Agent 主循环（无头优先） — 2026-09-14 从「+ 最小对话界面」改为无头门闩；**2026-09-27 无头循环落地**（JVM 16 例 + 仪器 15 例全绿），子计划 [mvp/step2-agent-engine-plan.md](./mvp/step2-agent-engine-plan.md)；真 endpoint 冒烟待授权
- [x] Step 3. ToolSystem 与首批工具 — 2026-09-14 补 FakeVehicleReader / ToolConfirmer / 只读控车分级；2026-09-21 按外部调研调整；2026-09-27 工具层与引擎工具循环落地；**2026-09-28 补 `AndroidVehicleReader` + `Capabilities`**，`android.car` 走官方 `useLibrary` 路径（子计划 §6），属性级实测顺带纠正了三个不存在的属性名（子计划 §1.2）。JVM 91 例 + 仪器 17 例全绿。子计划 [mvp/step3-toolsystem-plan.md](./mvp/step3-toolsystem-plan.md)
- [x] Step 4. Skill 机制 — 2026-09-14 补 permission / fallback / result_hint；**2026-09-27 落地**：声明式触发词命中、按 skill 收窄工具子集、非 query 档位不注册、未命中安静回退（JVM 91 例 + 仪器 16 例全绿）。子计划 [mvp/step4-skill-registry-plan.md](./mvp/step4-skill-registry-plan.md)
- [~] Step 5. AOC 双向协作收口 — 2026-09-14 明确不依赖 UI；**2026-09-27 移出本期 MVP**（二期，依赖 Step 1）
- [ ] Step 6. MCP 工具来源接入 — **2026-09-26 新增**（用户决策：进 MVP，只做 client over HTTP/SSE）

> Step 1 与 Step 2 相互独立可并行开工；Step 3 依赖 Step 2，Step 5 依赖 Step 1 + Step 4，Step 6 依赖 Step 3（需要 `Tool` 抽象已就位）。
> 2026-09-14：吸收 `../docs/car-agent-ecosystem-survey.md`；仍禁止按全文一次性实现，须先拆 `dev-plans/mvp/step<N>-*.md`。
