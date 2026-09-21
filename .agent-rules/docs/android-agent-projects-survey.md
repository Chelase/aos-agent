# 手机端开源 Agent 项目调研（OpenMinis / Operit / RikkaHub）

> 采集日期：2026-09-13。来源：GitHub 仓库公开信息（README、API 元数据、文件树）。
> 性质：外部参考资料，不构成规则或机制；结论服务于 mvp-core-plan 的"复用 vs 参考"决策。

## 一句话结论

三个项目都是成熟的手机端 AI Agent/聊天客户端，**均不能直接复用代码**（两个强传染协议 + 执行底座全是手机专属），但各自有明确可借鉴的设计点，直接映射到 mvp-core-plan 的 Step 2/3/4 与 Batch 2/3 规划。

## 逐项评估

### OpenMinis/OpenMinis — GPL-3.0，只可做架构参考

- 定位：iOS + Android 双端通用 AI Agent 客户端（BYOK），4.4k★，活跃（2026-09 仍高频提交），基本单人 + AI 结对开发。
- 技术栈：Kotlin + Compose + Material3，minSdk 26，仅 arm64；OkHttp + SSE、kotlinx-serialization、Room + DataStore；无 DI 框架。
- 架构：单 `:app` 模块；Agent 循环埋在 12.4k 行 `ChatViewModel.kt`；工具经 PRoot Alpine 沙箱执行，guest 内 execve 被 fork 拦截 → abstract unix socket → 宿主 21 个 NativeOffloadHandler（无障碍/浏览器/日历/Shizuku 等）。
- License：GPL-3.0（因链接 iSH/PRoot 被迫传染），**禁止代码复制**。

借鉴点（思路层面）：
1. `LLMProvider` 统一接口：`streamMessage(): Flow<LLMStreamChunk>`、thinking level 按模型上限自动钳制、多协议各自实现（OpenAI/Anthropic/Gemini）、自定义 baseURL 归一化（OpenAI 兼容中转可用）。
2. Agent 循环健壮性工程：`ToolLoopDetector` 防死循环、`ToolJsonRepair` 修畸形工具 JSON、循环内带预算的自动上下文压缩、断点续跑、mid-flight 模型 fallback。
3. **offload 模式平移**：「Agent 沙箱 → unix socket → 宿主 handler」可平移为「Agent 引擎 → 车辆能力 handler（VHAL/CarService/导航/媒体）」——对本项目最有价值的架构参考。
4. `HeadlessChatRunner` 无头驱动 Agent：正是 AOC `skill_request` 远程触发本地执行所需的模式。
5. MCP：Claude Desktop 兼容 servers.json + MCP OAuth(PKCE) 移动端实现。
6. Skill 用 Claude 风格 SKILL.md 片段注入系统提示词——与本项目 SkillRegistry 定义文件设计同构。

不适用：PRoot/Alpine 沙箱（AAOS SELinux 大概率拒绝 + GPL）、Shizuku（车机无 adb-shell uid 通道）、无障碍操控、browser_use WebView 自动化、手机通知/Live Updates、GMS OAuth。

### AAswordman/Operit — LGPL-3.0，功能最全，底座手机绑定

- 定位：Android 上最完整的 AI Agent 平台（工具调用/工作流/UI 自动化/终端/语音/应用市场），7.8k★，极活跃（2026-09-12 仍有提交），中文社区。
- 技术栈：Kotlin，minSdk 26，仅 arm64；另有 Operit 2（Rust 运行时 + Flutter 客户端）并行开发。
- 能力全景：工具调用带三级权限模型（自动允许/询问/禁止）；UI 自动化走无障碍/Shizuku/ADB/root 四通道；PRoot Ubuntu 终端；浏览器 Agent；图谱记忆；角色卡；带触发器的工作流；STT/TTS/VAD 连续语音；ToolPkg/Skill/MCP 扩展市场；本地模型 MNN/llama.cpp GGUF + Ollama/LM Studio。
- License：**LGPL-3.0-only**——动态链接可用，但其可用部分与手机底座深度耦合，实际仍是参考价值为主。

借鉴点：
1. **工具三级权限模型**（auto/ask/forbid 按工具配置）——直接对应本项目「危险操作必须用户确认」约束，可落到 ToolSystem 的 Tool 定义里。
2. Skill/ToolPkg 扩展格式与市场机制——为未来 AOC capability 对齐、插件生态（Batch 3）提供格式参照。
3. 连续语音对话链路（VAD + STT + Agent + TTS）——Batch 2 Step 4 的最接近开源参照。
4. 本地模型集成（MNN/llama.cpp GGUF 选型）——Batch 3 Step 2 参照。
5. PRoot 终端可行性佐证——但 AAOS 上需重验，本项目终端仍按 terminal-architecture.md 走 Termux terminal-emulator + PTY JNI。

不适用：无障碍服务（车机受限/驾驶分心法规）、Shizuku（车机通常不可用）、四通道 UI 自动化整体、手机应用市场场景。

### rikkahub/rikkahub — AGPL-3.0，聊天客户端工程标杆

- 定位：原生 Android LLM 聊天客户端，多 provider 切换，7.6k★，极活跃（2026-09-13 当天有提交）。官网 rikka-ai.com。
- 技术栈：Kotlin + Compose + Material You + Navigation 3，Koin DI，DataStore + Room，OkHttp + kotlinx-serialization。**多模块化清晰**：`ai`（provider 层）、`search`（搜索聚合）、`speech`、`highlight`（代码高亮/Markdown/LaTeX/Mermaid）、`oauth`、`workspace`（PRoot Linux Agent 环境）、`material3` 等。
- 能力：OpenAI/Google/Anthropic 兼容 + 自定义 baseURL/请求头/请求体；MCP；联网搜索（Exa/Tavily/智谱/Brave/Perplexity 等）；ChatGPT 式记忆；消息分支；多模态输入；SillyTavern 角色卡。
- License：**AGPL-3.0**——最强传染，任何代码复制都会迫使 aos-agent 整体 AGPL，不可复用。

借鉴点：
1. `ai` 模块的 provider 抽象与自定义 baseURL/headers 设计——MVP Step 2 `LlmRouter` 的首选参照。
2. Compose 聊天 UI 组件化（消息列表/Markdown/高亮/输入框）——MVP Step 2 对话界面的视觉与交互参照（设计参照不涉及代码复制）。
3. `search` 模块的搜索 provider 聚合——未来 web_search 工具的 provider 选型参照。

不适用：AGPL 整体；Workspace PRoot 环境；面向手机的对话产品形态。

## 决策建议

1. **不 fork、不整体复用**：三个项目的执行底座（无障碍/Shizuku/PRoot/浏览器）在 AAOS 车机上均不可用或受限，且协议均不友好；aos-agent 的差异化价值（Car API、AOC 接入、驾驶场景）三者都不具备，fork 无收益。
2. **现有 aos-agent 架构不必推翻**：现仓库仅 ~17 个文件的薄壳，重写成本本就低，但四层架构与 MVP 计划不与上述任何项目冲突；正确姿势是「自有薄核心 + 按上文借鉴点抄设计」。
3. **MVP 计划的修订吸收**：
   - Step 2 `LlmRouter` 参照 rikkahub `ai` 模块 + OpenMinis `LLMProvider` 接口形态（流式 Flow、baseURL 归一化、thinking 钳制）。
   - Step 3 `ToolSystem` 吸收 Operit 三级权限模型 + OpenMinis `ToolLoopDetector`/`ToolJsonRepair`/步数上限。
   - Step 5 AOC skill_request 处理参照 OpenMinis `HeadlessChatRunner` 无头模式。
   - Skill 定义格式继续对齐 Claude SKILL.md 风格（OpenMinis 已验证该模式在 Android 端可行）。
4. **若未来 aos-agent 整体转 AGPL 开源**，rikkahub 的 `ai`/`search` 模块可重新评估复用；当前生态定位（对接 AOC、可能系统级化）下不建议。
