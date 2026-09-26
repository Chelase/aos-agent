# Agent 能力架构

## 结论

AOS-Agent 是一个车载 AI Agent，具备工具调用、插件扩展、多步推理、上下文管理等核心能力。架构参考 OpenClaw、Hermes、Claude Code、OpenCode、Pi 等开源 AI Agent 系统的设计模式；架构层调整的技能路由见 `../rules/skill-routing.md` §3。

## 涉及对象

| 层 | 文件/模块 | 角色 | 参考来源 |
|---|---|---|---|
| Core | AgentEngine.kt | Agent 推理引擎核心 | Claude Code / OpenCode |
| Core | ToolSystem.kt | 工具注册、调度、执行 | OpenClaw Tool Executor |
| Core | PluginManager.kt | 插件热加载与管理 | OpenClaw Plugin System |
| Core | ContextManager.kt | 对话上下文管理 | Claude Code Context |
| Core | TaskPlanner.kt | 多步任务规划与执行 | OpenClaw / Hermes |
| Core | SkillRegistry.kt | 技能注册中心 | OpenClaw Skills |
| Core | LLM Router | 模型路由（云端/本地） | General |
| Tools | CarApiTool.kt | 车机 API 调用工具 | Tool Pattern |
| Tools | ShellTool.kt | 终端命令执行工具 | Claude Code Shell |
| Tools | FileSystemTool.kt | 文件读写操作工具 | OpenCode FS |
| Tools | AppManagerTool.kt | 应用安装管理工具 | Custom |
| Tools | WebSearchTool.kt | 网络搜索工具 | Hermes Web |
| Memory | ShortTermMemory.kt | 短期对话记忆 | Context Window |
| Memory | LongTermMemory.kt | 长期事实记忆（Room DB） | Claude Code Memory |
| UI | AgentChat.kt | Agent 对话 UI | Claude Code / OpenCode |

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

## 维护方式

- **新增工具**：实现 Tool 接口，在 ToolSystem 注册，定义 tool_describe。
- **新增插件**：编写 plugin.yaml + 实现代码，放入插件目录。
- **验证**：与 Agent 对话 -> 工具调用正确 -> 回复合理。

> 更新时间：2026-09-26（补 Pi 参考系与技能路由指针）
