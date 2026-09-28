# Agent 能力架构

## 结论

AOS-Agent 是一个车载 AI Agent，具备工具调用、插件扩展、多步推理、上下文管理等核心能力。架构参考 OpenClaw、Hermes、Claude Code、OpenCode、Pi 等开源 AI Agent 系统的设计模式；架构层调整的技能路由见 `../rules/skill-routing.md` §3。

## 涉及对象

| 层 | 文件/模块 | 角色 | 状态 |
|---|---|---|---|
| Core | `core/engine/AgentEngine.kt` | 无头主循环 + 工具调用回灌，产出 `Flow<AgentEvent>` | 已实现（Step 2，Step 3 扩展工具循环） |
| Core | `core/engine/ContextManager.kt` | 系统提示常驻 + 历史按预算从最旧丢弃 | 已实现（MVP Step 2） |
| Core | `core/engine/AgentEvent.kt` | `Started`/`Token`/`Completed`/`Failed` 事件契约 | 已实现（MVP Step 2） |
| Core | `core/llm/LlmProvider.kt` + `OpenAiCompatibleProvider.kt` | 流式模型接入；SSE 解析为纯函数 | 已实现（MVP Step 2） |
| Core | `core/llm/LlmConfig.kt` + `data/store/LlmConfigStore.kt` | 配置与私有 DataStore 持久化 | 已实现（MVP Step 2） |
| Core | `core/tools/ToolSystem.kt` | 工具注册、子集暴露、三级权限、30s 超时、异常包装 | 已实现（MVP Step 3） |
| Core | `core/tools/Tool.kt` / `ToolJsonRepair.kt` / `ToolLoopGuard.kt` | 工具契约（name/version/category）、参数畸形保守修复、步数与重复调用防护 | 已实现（MVP Step 3） |
| System | `system/vehicle/VehiclePropertyAllowlist.kt` + `assets/vehicle/vehicle_properties.json` | 车辆属性声明式 allowlist，写与特权条目挡下并留原因 | 已实现（MVP Step 3） |
| System | `system/vehicle/AndroidVehicleReader.kt` | 真车 Car API 读取（`useLibrary` 编译期 + 运行期探测降级） | 已实现（MVP Step 3，模拟器实测） |
| System | `system/Capabilities.kt` | 运行环境能力探测（Car API / per-app locale / AppFunctions），工具与 UI 共用一份 | 已实现（MVP Step 3） |
| Core | `core/skills/SkillRegistry.kt` + `SkillDefinition.kt` + `AssetSkillLoader.kt` | 技能定义解析、触发词命中、越权过滤；`assets/skills/<id>/skill.json` 为唯一配置源 | 已实现（MVP Step 4） |
| Core | `core/tools/mcp/`（MCP client 工具来源） | `StreamableHttpTransport` → `McpClient` → `McpToolAdapter` → `McpToolSource`，远端工具变成本地 `Tool` | 已实现（MVP Step 6；真实 server 互通待验证） |
| Core | PluginManager.kt | 插件热加载与管理 | 待规划（Batch 3），参考 OpenClaw Plugin System |
| Core | TaskPlanner.kt | 多步任务规划与执行 | 待规划，参考 OpenClaw / Hermes |
| Tools | `core/tools/VehicleBasicTool.kt` | 唯一车辆只读工具，字段由 allowlist 决定 | 已实现（MVP Step 3） |
| Tools | `core/tools/ShellTool.kt` | argv 直接执行，不经 shell；白名单 auto / 名单外 ask / 黑名单 forbid | 已实现（MVP Step 3） |
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
[ContextManager]
    | 组装对话历史 + 系统提示 + 工具定义
    v
[LLM Router]
    | 路由到 Gemini API 或本地 llama.cpp
    v
[AgentEngine]
    | 解析 LLM 响应
    | 如果是工具调用 -> ToolSystem
    | 如果是文本回复 -> 直接输出
    v
[ToolSystem]
    | 查找已注册的工具
    | 执行工具（带超时和错误处理）
    | 工具结果返回给 LLM 继续推理
    v
[AgentEngine]
    | LLM 根据工具结果生成最终回复
    v
[输出（文本/TTS）]
```

### 工具调用模式（参考 Claude Code / OpenCode）

```
工具定义格式（Function Calling）：

tool_describe(name="car_get_speed", description="获取当前车速")
tool_describe(name="shell_exec", description="执行 shell 命令")
tool_describe(name="app_install", description="安装 APK 应用")
tool_describe(name="web_search", description="搜索网络信息")

LLM 输出工具调用：
{
  "tool": "car_get_speed",
  "params": {}
}

ToolSystem 执行 -> 结果返回 LLM
LLM 整合结果 -> 自然语言回复
```

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
| Agent 核心循环 | core/engine/AgentEngine.kt | LLM 调用 + 工具调度 + 多步推理 |
| 工具系统 | core/engine/ToolSystem.kt | 工具注册/查找/执行/超时 |
| 插件系统 | core/engine/PluginManager.kt | 插件加载/卸载/依赖管理 |
| 上下文管理 | core/engine/ContextManager.kt | 对话历史、Token 限制、摘要 |
| 任务规划 | core/engine/TaskPlanner.kt | 任务分解、依赖排序、并行执行 |

## 关键约束

1. Agent 所有工具调用必须有超时（默认 30s），防止卡死。
2. 危险操作（shell 命令、系统修改）必须用户确认。
3. 对话上下文有 Token 上限，超限后自动摘要历史。
4. 插件系统必须有安全沙箱，不能访问 Agent 核心数据。
5. 驾驶模式下 Agent 优先使用语音交互。
6. 引擎事件流契约固定为 `Started → Token* → Completed | Failed`：失败以事件形式产出、不向调用方抛异常；Step 3/5/6 只能在流上增加事件类型，不得改动既有语义。`core/llm` 与 `core/engine` 禁止 import `android.*`，否则"无头可驱动"这条验收失效。

## 维护方式

- **新增工具**：实现 Tool 接口，在 ToolSystem 注册，定义 tool_describe。
- **新增远端工具来源**：往 `McpServerStore` 加一条 `McpServerConfig`（id/url/token/默认权限），
  由 `McpToolSource.load()` 产出 `Tool`；远端名会被改写成 `mcp_<server>_<tool>` 供模型选择，
  调用时仍用原始名。加来源不改引擎与 Skill 层。
- **新增插件**：编写 plugin.yaml + 实现代码，放入插件目录。
- **验证**：与 Agent 对话 -> 工具调用正确 -> 回复合理。

> 更新时间：2026-09-27（MVP Step 2 无头引擎落地，涉及对象补实现状态）
