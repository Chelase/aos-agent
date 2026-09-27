# Step 2 子计划：本地 Agent 主循环（无头引擎）

> 父计划：[../mvp-core-plan.md](../mvp-core-plan.md) Step 2
> 配套机制文档：[../../mechanisms/agent-capabilities.md](../../mechanisms/agent-capabilities.md)、[../../mechanisms/architecture-overview.md](../../mechanisms/architecture-overview.md)
> **统一愿景对齐**：①引擎形态参考 OpenClaw/Hermes/Claude Code 的"循环 + 事件流"，不自研新架构；③本地主脑保证 AOC Hub 不可用时照常工作（愿景原则三、五）；④本步不碰车辆专属能力（Step 3）。无偏离。

## 0. 本步要解决什么

一条**没有界面也能跑通**的推理循环：喂入自然语言 → 流式吐结构化事件 → 多轮上下文生效。它是 Step 3 工具循环与 Step 5 AOC 远程触达的共同地基，所以判据只有一条：**能不能被代码驱动、能不能被测试证明**，与 UI 无关。

## 1. 开工前必须解决的文档冲突

`mechanisms/aoc-integration.md` 结论与关键约束 6 写着"LLM 推理全部发生在 AOC 中心 Brain，车机端不自建推理路由"，与父计划决策 1（本地 `AgentEngine` 是常驻主脑、AOC Brain 是增量能力）直接矛盾。本步按**愿景原则三 + 父计划决策 1** 为准：车机端有自己的 LLM 连接。约束 6 的原意收窄为"**AOC 协议层**不自建推理路由（不把推理请求塞进 DM 协议）"，随本步一并改写该文档，避免后续 Agent 被过期结论带偏。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `core/llm/LlmContract.kt` | 新增 | `LlmMessage` / `LlmRequest`（含 Step 3 工具定义位）/ `LlmStreamChunk` |
| `core/llm/LlmProvider.kt` | 新增 | `suspend fun stream(request): Flow<LlmStreamChunk>` 接口，纯 Kotlin |
| `core/llm/OpenAiCompatibleProvider.kt` | 新增 | okhttp SSE 实现；baseURL/headers 归一化；`parseSseEvent(String)` 保持纯函数便于单测 |
| `core/llm/LlmConfig.kt` | 新增 | `LlmConfig`（baseUrl/model/apiKey/extraHeaders）+ `LlmConfigSource` 接口 |
| `core/engine/AgentEvent.kt` | 新增 | sealed：`Started` / `Token` / `Completed` / `Failed`（`ToolCall`/`ToolResult` 留 Step 3） |
| `core/engine/ContextManager.kt` | 新增 | 系统提示 + 会话历史 + 预算截断（`TokenBudget` 接口，MVP 用字符估算，真 tokenizer 后换） |
| `core/engine/AgentEngine.kt` | 新增 | 单轮循环：入历史 → 流式消费 → 出事件 → 回写历史；不 import 任何 `android.*` |
| `data/store/LlmConfigStore.kt` | 新增 | DataStore Preferences 实现 `LlmConfigSource`（Android 侧唯一触点） |
| `gradle/libs.versions.toml`、`app/build.gradle.kts` | 改 | 新增 okhttp、kotlinx-serialization-json、datastore-preferences；测试侧 kotlinx-coroutines-test |
| `app/src/test/.../core/engine/AgentEngineTest.kt` | 新增 | 用 `FakeLlmProvider` 断言事件序列与多轮上下文 |
| `app/src/test/.../core/llm/OpenAiSseParserTest.kt` | 新增 | SSE 行解析：正常增量、`[DONE]`、畸形 JSON、空 data |
| `app/src/test/.../core/engine/ContextManagerTest.kt` | 新增 | 超预算时保留系统提示 + 最近轮次 |

## 3. 关键实现方案

- **可测性是硬约束**：`core/llm` 与 `core/engine` 全部纯 Kotlin（无 `Context`、无 `android.*`）。"引擎可在无 Activity 情况下驱动"这条验收，由 JVM 单测直接证明，不需要仪器测试。
- **事件流形状固定**：`Started → Token(text)* → Completed(fullText)`，任何异常收敛成 `Failed(cause, retryable)`，绝不抛到调用方协程外。Step 3 只在这条流上插 `ToolCall`/`ToolResult`，不改既有事件。
- **SSE 解析与 IO 分离**：`parseSseEvent(line): LlmStreamChunk?` 是无网络纯函数，畸形 chunk 返回 null 并计数，不中断整条流。
- **baseURL 归一化**：允许填 `https://host/v1` 或带 `/chat/completions` 的全路径，统一成一个；自定义 headers 透传（兼容各类中转）。
- **凭证纪律**：apiKey 只存应用私有 DataStore（与 AOC secret 同策略，禁 SharedPreferences）；日志与异常信息只允许出现键名与 base URL，**不得打印 key**；`toString()` 对 apiKey 做掩码。
- **不引 Retrofit**：okhttp 直用，符合轻量预算（release 整包基线 20.8MB）。
- **上下文预算**：`TokenBudget` 接口 + 字符估算实现（`chars / 4` 粗算，中文偏保守），超限先丢最旧的非系统轮次，系统提示永不丢弃。

## 4. 分步验收条件

- **4.1 纯逻辑**：`ContextManagerTest` + `OpenAiSseParserTest` 绿（`./gradlew :app:testDebugUnitTest`）。
- **4.2 引擎行为**：`FakeLlmProvider` 三段输出 → 事件依次为 `Started, Token, Token, Token, Completed`，`Completed.fullText` 等于三段拼接；provider 抛异常 → 只出一个 `Failed` 且流正常结束；第二轮请求的消息里含第一轮内容（多轮上下文生效）。
- **4.3 无头证明**：以上全部在 JVM 跑通，即父计划"引擎可在无 Activity 情况下被直接驱动"成立；不新增任何 Activity/Composable。
- **4.4 真 endpoint 冒烟（需你点头）**：用你现有 OpenAI 兼容 endpoint 跑一次真实流式请求，记录首包延迟与内存。这一步会产生 API 费用，且要临时读取本机其他工具的凭据，**默认不做**，等你明确同意。
- **4.5 回写**：修 `aoc-integration.md` 冲突结论；`agent-capabilities.md` 的涉及对象与运行链路补已实现类；父计划 Step 2 勾选。

## 5. 本步不做

- 工具调用分支、`ToolSystem`、循环防护（Step 3）。
- 对话界面、气泡、流式上屏（非门闩）。
- 本地 LLM / llama.cpp、AOC Brain 委派路由（Batch 3 / 二期）。
- 真实 tokenizer 与精确计费截断。

## 进度

- [x] 1. 文档冲突修正（§1）— 2026-09-27，`mechanisms/aoc-integration.md` 结论与约束 6 已收窄为"AOC 协议层不自建推理路由"
- [x] 4.1 纯逻辑单测 — SSE 7 例 + 上下文 5 例
- [x] 4.2 引擎行为单测 — 4 例（事件序列、失败收敛为事件、多轮上下文、失败轮不入历史）
- [x] 4.3 无头证明 — 上述 16 例全在 JVM 跑通，`core/llm` 与 `core/engine` 零 `android.*` 引用；未新增任何 Activity/Composable
- [x] 4.5 回写 — `agent-capabilities.md` 涉及对象补实现状态、新增事件流契约约束；父计划 Step 2 勾选
- [ ] 4.4 真 endpoint 冒烟 — **未做，等你授权**（要用你现有 OpenAI 兼容 endpoint，会产生 API 费用）

## 实测记录

- `:app:testDebugUnitTest` 38 例全绿（新增 16 例）。
- `:app:connectedDebugAndroidTest` 在 Automotive 模拟器（1408x792 / API 35）15 例全绿，含新增 `LlmConfigStoreTest` 4 例（DataStore 往返、空 headers、apiKey 掩码）。
- 一次 `MainActivityTest.engineerMode_canOpenAndReturn` 偶发失败：单独跑与全量重跑均通过，失败信息为空 XML 节点，**根因未查明**；如再复现需查仪器测试与前台服务/广播测试的相互干扰。

> 创建：2026-09-27
