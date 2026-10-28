# Agent 能力架构

## 结论

AOS-Agent 是一个车载 AI Agent，具备工具调用、插件扩展、多步推理、上下文管理等核心能力。架构参考 OpenClaw、Hermes、Claude Code、OpenCode、Pi 等开源 AI Agent 系统的设计模式；架构层调整的技能路由见 `../rules/skill-routing.md` §3。

## 涉及对象

| 层 | 文件/模块 | 角色 | 状态 |
|---|---|---|---|
| Core | `core/engine/AgentEngine.kt` | 无头主循环 + 工具调用回灌，产出 `Flow<AgentEvent>`；回合结束回写 `ToolTrace` | 已实现（Harness H3） |
| Core | `core/engine/ContextManager.kt` | 动态系统提示 + 历史按预算丢弃 + 上一轮工具轨迹 | 已实现（Harness H1/H3） |
| Core | `core/engine/PromptAssembler.kt` + `PromptSections.kt` | 系统提示分节（persona/environment/capabilities/cli/rules），每轮求值 | 已实现（Harness H1） |
| Core | `core/engine/AgentEvent.kt` | `Started`/`Token`/`Completed`/`Failed` 事件契约 | 已实现（MVP Step 2） |
| Runtime | `runtime/RuntimeEnvironment.kt` | 环境快照（包名/版本/API/CLI 路径/工具清单） | 已实现（Harness H1） |
| UI | `ui/chat/UiToolConfirmer.kt` + `ToolConfirmDialog.kt` | Ask 级工具确认弹窗；驾驶态由 ToolSystem 先拒 | 已实现（Harness H2） |
| Core | `core/llm/LlmProvider.kt` + `OpenAiCompatibleProvider.kt` | 流式模型接入；SSE 解析为纯函数 | 已实现（MVP Step 2） |
| Core | `core/llm/LlmConfig.kt` + `data/store/LlmConfigStore.kt` | 配置与私有 DataStore 持久化 | 已实现（MVP Step 2） |
| Core | `core/tools/ToolSystem.kt` | 工具注册、子集暴露、三级权限、确认器注入、驾驶态拒绝、30s 超时 | 已实现（Harness H2） |
| Core | `core/tools/Tool.kt` / `ToolJsonRepair.kt` / `ToolLoopGuard.kt` | 工具契约（name/version/category）、参数畸形保守修复、步数与重复调用防护；`Rejected` 带 `RejectionReason` 分类 | 已实现（MVP Step 3；分类 2026-10-28） |
| System | `system/vehicle/VehiclePropertyAllowlist.kt` + `assets/vehicle/vehicle_properties.json` | 车辆属性声明式 allowlist，写与特权条目挡下并留原因 | 已实现（MVP Step 3） |
| System | `system/vehicle/AndroidVehicleReader.kt` | 真车 Car API 读取（`useLibrary` 编译期 + 运行期探测降级） | 已实现（MVP Step 3，模拟器实测） |
| System | `system/vehicle/VehicleBattery.kt` | 动力电池 SOC：`EV_BATTERY_LEVEL` 换算成百分比，读不到给结构化原因 | 已实现（2026-10-15，模拟器与单测；真车口径待确认） |
| System | `system/Capabilities.kt` | 运行环境能力探测（Car API / per-app locale / AppFunctions），工具与 UI 共用一份 | 已实现（MVP Step 3） |
| Core | `core/skills/SkillRegistry.kt` + `SkillDefinition.kt` + `AssetSkillLoader.kt` | 技能定义解析、触发词命中、越权过滤；`assets/skills/<id>/skill.json` 为唯一配置源 | 已实现（MVP Step 4） |
| Core | `core/tools/mcp/`（MCP client 工具来源） | `StreamableHttpTransport` → `McpClient` → `McpToolAdapter` → `McpToolSource`，远端工具变成本地 `Tool` | 已实现（MVP Step 6；真实 server 互通待验证） |
| Core | PluginManager.kt | 插件热加载与管理 | 待规划（Batch 3），参考 OpenClaw Plugin System |
| Core | TaskPlanner.kt | 多步任务规划与执行 | 待规划，参考 OpenClaw / Hermes |
| Tools | `core/tools/VehicleBasicTool.kt` | 唯一车辆只读工具，字段由 allowlist 决定 | 已实现（MVP Step 3） |
| Tools | `core/tools/ShellTool.kt` | argv 直执；`grep`/`head` 参数过滤；头尾截断 + `full_output` 落盘 + `truncation_notice` 二次读取指引 | 已实现（Harness H4；截断指引 2026-10-28） |
| Tools | `core/tools/SystemInfoTool.kt` | 复用 SystemInfoReader | 已实现（MVP Step 3） |
| Tools | FileSystemTool.kt | 文件读写操作工具 | 待规划 |
| Tools | AppManagerTool.kt | 应用安装管理工具 | 待规划 |
| Tools | WebSearchTool.kt | 网络搜索工具 | 待规划 |
| Memory | ShortTermMemory.kt | 短期对话记忆 | 由 ContextManager 承担（Step 2 已落地最小形态） |
| Memory | LongTermMemory.kt | 长期事实记忆（Room DB） | 待规划 |
| UI | AgentChat.kt | Agent 对话 UI | 非门闩，可缺席 |

## 运行链路

### Agent 核心循环

```
[用户输入（文本/语音）]
    |
    v
[AgentRuntime.send]
    | PromptAssembler 按 PromptContext 组装分节系统提示
    | 复用会话级 ContextManager（历史 + 上一轮 ToolTrace）
    v
[AgentEngine]
    | beginTurn -> LLM 流式推理
    | 工具调用 -> ToolSystem（Ask 可弹窗 / 驾驶中拒绝）
    | 文本回复 -> recordTurn(Q/A + traces) -> Completed
    v
[输出（文本/TTS）]
```

### 工具调用模式

```
LLM function-call -> ToolSystem.dispatch
  Forbid -> Rejected(forbidden)
  Ask    -> 驾驶中直接拒 drive_restricted / 无 confirmer 拒 approval_unavailable
            / 用户拒 user_denied / 放行后执行
  Auto   -> 执行（shell_exec 可带 grep/head；超长头尾截断，完整落盘 + 截断指引）

结果回灌 LLM；回合结束把 ToolTrace 写入 ContextManager，供下一轮系统提示引用。
```

### 拒绝原因分类（2026-10-28）

`ToolResult.Rejected` = `reason: RejectionReason` + `message: String`。分类是稳定契约，
`message` 只负责措辞；引擎回灌给模型的 payload 为
`{status: "rejected", reason: <wire>, message: <措辞>}`。

| `RejectionReason` | wire | 触发点 | 模型该做什么 |
|---|---|---|---|
| `Forbidden` | `forbidden` | 工具 `permission = Forbid`（rm/reboot/pm 等） | 换工具或如实说明该操作不可用，不要重试 |
| `ApprovalUnavailable` | `approval_unavailable` | Ask 级但没注入确认器 | 换免确认工具或说明需要人工授权 |
| `UserDenied` | `user_denied` | 人工确认被拒 | 停止该操作，如实转述被拒 |
| `DriveRestricted` | `drive_restricted` | 驾驶态不允许人工确认 | 提示停车后重试 |
| `InvalidInput` | `invalid_input` | 参数非法（如 shell 元字符） | 改参数后重试 |

### Shell 输出截断指引（2026-10-28）

`shell_exec` 输出超过 `DEFAULT_KEEP_EACH_SIDE`（4000 字符）时头尾截断，并在结果里给：

- `truncated: true`
- `full_output`: 完整内容落盘绝对路径（`files/tool-output/<uuid>.txt`）
- `truncation_notice`: 人话指引，含原始字符数与落盘路径，明确写「可再次读取该文件取回全部内容」

未配置 `outputDir` 时 `truncation_notice` 如实说明拿不到全文，并建议改用 `grep`/`head` 缩小范围，
不编造路径。

### 上下文分级降级（2026-10-28）

对标 Claude Code 的 C4 结论：上下文超限要**分级降级**，不要一次性丢历史。

| 级别 | 动作 | 代价 |
|---|---|---|
| 一级 | 压缩旧工具结果里的长字符串字段（JSON 结构保留） | 问答脉络全留 |
| 二级 | 按**回合组**丢弃最旧历史 | 丢掉最老的问答 |

- 一级只压工具返回的原文：JSON 保留结构，`full_output` 这类路径字段因此得以保留，
  同时追加 `compacted: true` 与 `original_chars` 让模型知道原文被压过。
- 最近 `DEFAULT_KEEP_RECENT`（2）条工具结果保持原样——模型多半正要基于它继续追问。
- 二级**按回合组**丢，不逐条丢：一组 = 一条 user 消息 + 其后连续的 assistant / tool 消息。
  逐条丢会拆散 `assistant(tool_calls)` 与 `tool` 的配对，OpenAI 兼容服务端会拒整个请求。
- 当前提问永远留在窗口内。

实现：`core/engine/ContextDegradation.kt`，由 `ContextManager.trim()` 两级串联调用。

### 系统提示稳定节快照（2026-10-28）

对标 Claude Code 的 C6 结论：`systemPromptSnapshot` 默认开启——首帧渲染后逐字复用。

`PromptSection.stable = true` 的节首帧渲染后快照复用，动态节每轮重算：

| 节 | stable | 理由 |
|---|---|---|
| `persona` | 是 | 纯常量文本 |
| `rules` | 是 | 纯常量文本 |
| `environment` | 否 | 时间、版本、屏幕会变 |
| `capabilities` | 否 | 工具清单随 `refresh()` 变 |
| `cli` | 否 | CLI 是否安装会变 |

**约束**：稳定节只能用于与会话上下文无关的常量文本。标错会把过期的环境信息固化下来。
`PromptAssembler.resetSnapshots()` 用于外部条件变化时显式失效。

### 工具回合入历史（2026-10-28）

C4 的前提：历史里必须有工具结果，否则"先清旧 tool_result"无从谈起。

`AgentEngine` 把整个工具交换序列原样存入 `ContextManager`，协议要求三段分离：

```
user → assistant(tool_calls, 空 content) → tool → assistant(最终答案)
```

最终答案单独成一条 assistant 消息，与 `tool_calls` 合并会被服务端拒。

### 插件系统（参考 OpenClaw Plugin）

```
[PluginManager]
    |
    +-- 内置插件（编译时集成）
    |     - Car API 插件
    |     - Shell 插件
    |     - 文件系统插件
    |
    +-- 用户插件（运行时加载）
    |     - 通过 Agent 对话安装
    |     - 描述文件 (plugin.yaml)
    |     - 工具定义 + 实现代码
    |
    +-- 社区插件（后续）
          - 插件市场
          - 版本管理
```

### 多步任务规划（参考 OpenClaw / Hermes）

```
用户: "帮我检查车辆状态并推荐充电站"

[TaskPlanner]
    | 分解任务：
    | 1. car_get_battery() -> 获取电量
    | 2. car_get_range() -> 获取续航
    | 3. web_search("附近充电站") -> 搜索充电站
    | 4. car_get_location() -> 获取位置
    |
    v
顺序/并行执行计划
    | 各工具结果返回
    v
[AgentEngine 整合]
    | "当前电量 65%，续航 320km，附近有 3 个充电站..."
```

## 参考开源项目模式

| 项目 | 核心模式 | 借鉴点 |
|---|---|---|
| Claude Code | Tool calling + 安全执行 + 上下文管理 | 工具定义、执行沙箱、对话上下文 |
| OpenCode | Agent loop + 文件操作 + shell 执行 | Agent 主循环、文件系统工具 |
| OpenClaw | 多 Agent 编排 + 技能系统 + 插件热加载 | 插件架构、技能注册 |
| Hermes | 工具调用 + 多步推理 + web 搜索 | 多步规划、网络搜索工具 |
| Pi | 尚未逐项梳理 | 按 `../rules/skill-routing.md` §3 补齐后再引用，禁止凭印象对齐 |

## 使用点

- 所有用户交互通过 Agent 进行（文字/语音）
- 车辆控制、信息查询、应用管理、问题解答
- 复杂任务自动分解执行

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 改系统提示分节 | `PromptSections.kt` / `RuntimeEnvironment.kt` | 增删 section 或快照字段；勿在构造期烘焙 |
| Ask 确认 / 驾驶策略 | `ToolSystem.kt` + `UiToolConfirmer.kt` | 确认器由 ChatConsole 注入；驾驶拒绝文案在 ToolSystem |
| shell 过滤/截断 | `ShellTool.kt` | 参数侧 grep/head；展示截断与 `full_output` 落盘 |
| 拒绝原因分类 | `Tool.kt` 的 `RejectionReason` | 新增分类要同步改 `ToolSystem`/`ShellTool` 触发点与引擎回灌 payload |
| 跨轮轨迹 | `ContextManager.recordTurn` / `AgentEngine` | 只保留最近一轮，总长 ≤400 字符 |

## 关键约束

1. Agent 所有工具调用必须有超时（默认 30s），防止卡死。
2. 危险操作（shell 命令、系统修改）必须用户确认。
3. 对话上下文有 Token 上限，超限后自动摘要历史。
4. 插件系统必须有安全沙箱，不能访问 Agent 核心数据。
5. 驾驶模式下 Agent 优先使用语音交互。
6. 引擎事件流契约固定为 `Started → Token* → Completed | Failed`：失败以事件形式产出、不向调用方抛异常；Step 3/5/6 只能在流上增加事件类型，不得改动既有语义。`core/llm` 与 `core/engine` 禁止 import `android.*`，否则"无头可驱动"这条验收失效。
7. **动力电池电量不能直接当百分比用**。AOSP 的 `EV_BATTERY_LEVEL` 单位是 Wh（模拟器实测 150000.0），
   SOC 必须 `剩余能量 / 容量` 自己算；分母优先 `EV_CURRENT_BATTERY_CAPACITY`（实测可读），
   `INFO_EV_BATTERY_CAPACITY` 兜底——AOSP 里**没有** `EV_BATTERY_CAPACITY` 这个常量名（那是 VHAL 侧属性名）。
   部分厂商直接把百分比塞进 `EV_BATTERY_LEVEL`，所以口径是 `vehicle_properties.json` 的
   `battery_level_unit` 键（`wh` / `percent`），不是代码常量；换车型改数据即可。
   算不出来一律返回 null 并带 `DegradeStatus`，界面显示"不可用（原因）"——
   车机 `dumpsys battery` 恒 `level 0`，把 `BatteryManager` 的 0 当真值显示属于界面说谎（五菱星光实测 bug）。
   `EV_BATTERY_DISPLAY_UNITS` 的取值语义未在官方文档核实（本机无外网），因此不据它自动判口径。

## 维护方式

- **新增工具**：实现 Tool 接口，在 ToolSystem 注册，定义 tool_describe。
- **新增远端工具来源**：往 `McpServerStore` 加一条 `McpServerConfig`（id/url/token/默认权限），
  由 `McpToolSource.load()` 产出 `Tool`；远端名会被改写成 `mcp_<server>_<tool>` 供模型选择，
  调用时仍用原始名。加来源不改引擎与 Skill 层。
- **新增插件**：编写 plugin.yaml + 实现代码，放入插件目录。
- **验证**：与 Agent 对话 -> 工具调用正确 -> 回复合理。

> 更新时间：2026-10-28（Harness H1–H4：提示分节 / 审批 / 轨迹 / shell 后处理；P0 对齐：拒绝原因分类 + 截断二次读取指引）
