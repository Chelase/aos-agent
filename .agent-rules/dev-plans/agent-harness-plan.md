# Agent Harness 层计划（H1–H4）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。本计划补齐 Agent 运行时的
> "harness" 骨架（提示组装 / 工具输出后处理 / 审批 / 会话轨迹），属"离线独立运行"地基，
> 不引入任何远端依赖，不改变模型接入方式。
>
> 配套机制文档：[`../mechanisms/agent-capabilities.md`](../mechanisms/agent-capabilities.md)
> （落地后回写其中「Agent 核心循环」与「工具调用模式」两节）

## 1. 问题来源（实车证据，不含推断）

| 观测 | 证据 |
|---|---|
| Agent 不知道应用自带 `aos-llm` CLI | 实车对话截图：用户提 CLI，回答"我没有你指 CLI 的配置资料" |
| Agent 说"不能绕过 AOSAgent 去访问未开放的底层接口"，但它自己持有 `shell_exec` | 同上 |
| 被要求"深入"时输出 BMS/VCU/CAN 通用知识，未调用任何工具 | 实车对话截图 |
| `shell_exec` 禁止管道，输出硬截 8000 字符 | `core/tools/ShellTool.kt:122`、`:37` |
| `ToolSystem()` 空参构造，`Ask` 级工具恒被拒 | `runtime/AgentRuntime.kt:102`、`core/tools/ToolSystem.kt:55` |
| `settings`/`wm` 实际不可用；`pm` 同时出现在 ask 与 blocked | `runtime/AgentRuntime.kt:97-98` |
| 工具轨迹不跨轮保留，`recordExchange` 只存最终 Q/A | `core/engine/ContextManager.kt:78` |
| 系统提示是单个常量 + skill 名拼接，无环境自述 | `AgentRuntime.kt:193`、`SkillRegistry.capabilitySummary()` |

参考实现对照（[DeepSeek Harness 架构文档](https://github.com/deepseek-ai/deepseek-harness)）：
`core/session`（append-only 会话日志）、`core/system-prompt`（`PromptSection` 分节 + 动态上下文）、
`core/tools`（guarded pipeline）、`dsh-base`（sandbox and approval policy）。

### 本地 Claude Code CLI 逆向对照（`@anthropic-ai/claude-code@2.1.302`）

证据来源（全部本机只读）：

1. 发行物 `C:\nvm4w\nodejs\node_modules\@anthropic-ai\claude-code\bin\claude.exe`，
   sha256 `3ab075f7…3d2a0ae`，290 MB，Bun 单文件编译产物（PE / x64，节表含 `.bun`）。
2. REA（`rea-agents@6.7.0`）`inspect-artifact` 确认格式与摘要；Ghidra 12.1.4 已配置
   （`GHIDRA_INSTALL_DIR=E:\tools\ghidra_12.1.4_PUBLIC`，doctor 9 项检查全绿），Hopper/IDA/Kuna 未配置；
   JS bundle 抽取路线已用原生 provider 替代验证。
3. PE 节解析定位 `.bun`（rawptr `119417856`，size `170783232`）；该节 offset
   `120.5 MB – 165.7 MB` 为 100% 可打印 minified JS，含 2341 个 `B:/~BUN/root/chunk-*.js` 模块。
4. 静态关键字定位抽取证据（`behavior:"ask"` / `canUseTool` / `decisionReason` /
   `persistedOutputPath` / `microcompact` / `compact_boundary` / `systemPromptSnapshot` 等）。
5. 交叉验证：[arxiv:2604.14228](https://arxiv.org/html/2604.14228v2) 架构综述。

Claude Code harness 心智模型（本地证据佐证）：**模型只出 `tool_use`；执行 / 权限 / 截断 / 落盘全在 harness**。
权限判定是纯数据驱动的 `behavior` 三态（`allow` / `ask` / `deny`）+ `decisionReason` 结构化理由 +
`classifierApprovable` 标记；落盘走 `persistedOutputPath` + `persistedOutputSize` + 预览回填。

#### 逆向出的 6 条可迁移结论

| # | Claude Code 本地证据 | 可迁移做法 | 对本仓影响 |
|---|---|---|---|
| C1 | 权限决策返回结构化对象：`{behavior, message, decisionReason:{type,reason,classifierApprovable,circuitBreaker}}` | 拒绝/追问要带**原因分类**，不要只给一句话 | 本仓 `ToolResult.Rejected` 只有裸字符串；建议加 reason 分类枚举 |
| C2 | 落盘是**超大才落**，且回填预览 + 路径 + 原始字节数：`persistedOutputPath` / `persistedOutputSize` / `Output too large (N). Full output saved to: <path>` + `Preview (first M):` | 我们的 `full_output` / `total_lines` / `truncated` 已同构，但**正文里没有给模型"去读这个文件"的指引** | `ShellTool` 截断文案需带路径与"可二次读取"指引 |
| C3 | 内联上限量化为常量：`or=204800`（200 KB 内联）、`rr=1048576`（1 MB 图片）、`cr=4`（安全检查上限） | 阈值写成常量而非散落 magic number | 我们已有 `DEFAULT_KEEP_EACH_SIDE`，方向一致 |
| C4 | 压缩是**五层流水线**：budget reduction → snip → microcompact → context collapse → auto-compact；`compact_boundary` 是显式消息类型，`system`/`microcompact_boundary` 子类型区分 | 上下文超限要**分级降级**，不要一次性丢历史 | 我们 `ContextManager.trim()` 只做「从最旧整条丢」——最简档，明显落后 |
| C5 | microcompact 只清 **tool_result**，保留最近 N 条（`keepRecent`），清理内容先落盘再替换（`persist` → 占位符），并记账 `tokensSaved` | 清工具结果 = 落盘 + 占位 + 保留最近 N 条 | 我们 H3 只留 `ToolTrace` 摘要，不留原文，方向偏保守但可接受 |
| C6 | 系统提示**快照化**：`systemPromptSnapshot` 默认 true——首帧渲染后逐字复用，直到 compact 边界才重算；`appendSystemPrompt` 单独追加 | 环境每轮重算 vs 提示快照，二选一 | 我们 H1 是**每轮全量重算**；车机环境确实每轮可能变，但可参照做成「稳定节快照 + 动态节重算」 |

#### Harness 能力对照表

| Harness 能力 | Claude Code（本地证据） | 本仓现状 | 差距 |
|---|---|---|---|
| 系统提示 / 上下文 | 分节 + `appendSystemPrompt` + 快照复用 + CLAUDE.md 分层 | `PromptAssembler` 五节每轮求值 | 结构同源；缺快照与分层加载 |
| 工具结果落盘 | `persistedOutputPath` + `persistedOutputSize` + 预览 + 指引文案 | `full_output` + `total_lines` + `truncated` | 字段同构；缺回填指引 |
| 输出过滤 | 独立 `Grep`/`Glob`/`Read(offset,limit)`；Bash 有真 sandbox | `shell_exec` 的 `grep`/`head` 参数 | 车机无 sandbox，参数过滤是正确折中 |
| 审批 | `behavior` 三态 + `decisionReason` + 多 mode + 弹窗可改参数 | `ToolPermission` 三态 + `UiToolConfirmer`；驾驶态硬拒 | 三态同构；缺结构化理由与 mode 谱系 |
| Ask 人话确认 | `AskUserQuestion`（1–4 题、选项、预览） | 仅允许/拒绝 | 另立；车机触控场景可能反而更需要结构化问句 |
| 会话轨迹 | append-only JSONL；可 resume/fork/rewind | 内存 `ContextManager` + 最近一轮 `ToolTrace` | 缺持久化 |
| 上下文压缩 | 五层流水线 + `compact_boundary` 消息 | 单一「丢最旧整条」 | **最大差距**，见 C4 |
| Sandbox | macOS Seatbelt / Linux bubblewrap；Win 原生无沙箱 | app uid + 命令白名单 | 同结论；计划已排除 |
| 子代理 / Task | `Agent` + `Task*` 工具族 | 无 | 计划已排除，依赖 H3 后再评 |

对本期实现的校验结论：H1–H4 选的四刀与 Claude Code harness **同层**（提示装配 / 权限闸 / 结果后处理 / 短程记忆），
没有走偏到「再包一层编排框架」。

#### 逆向后的对齐优先序（替代上一版猜测）

| 优先 | 动作 | 对标 | 理由 |
|---|---|---|---|
| P0 | `ShellTool` 截断文案带路径 + 「可二次读取」指引 | C2 | 成本最低，直接解决"截断了就查不到" |
| P0 | `ToolResult.Rejected` 带结构化 reason 分类 | C1 | 让模型能据原因换策略（换参数 / 换工具 / 放弃） |
| P1 | `ContextManager` 引入分级降级（先清旧 tool_result，再丢整轮） | C4 | 当前一次丢历史是最易伤；车载场景上下文更小 |
| P1 | 系统提示拆「稳定节 / 动态节」，稳定节快照化 | C6 | 减少每轮重算，也让提示语义更可控 |
| P2 | 会话 JSONL 持久化 | 会话轨迹 | 与 H3 一起做才有意义 |
| P2 | 结构化 `AskUserQuestion` | Ask 人话确认 | 驾驶外体验；需独立设计 |
| P3 | 独立只读 `grep`/`read` 工具 | 输出过滤 | 减轻 `shell_exec` 负担 |

## 2. 范围

只做四件事，让 Agent **会查、敢查、查得到、记得住**：

| 编号 | 能力 | 解决的观测 |
|---|---|---|
| H1 | 系统提示分节（环境 / 能力 / CLI / 规则） | 不知道自己有什么、把"查底层"误解为"绕过宿主" |
| H2 | 审批通道（`ToolConfirmer` 注入 + 驾驶态策略） | `Ask` 级工具全废 |
| H3 | 会话工具轨迹跨轮保留 | 同一问题追问三轮 |
| H4 | shell 输出过滤与头尾截断 | dumpsys 只能看开头 8000 字、无法 grep |

执行顺序按 **H1 → H4 → H2 → H3**：H1 让 Agent 愿意调工具，H4 让工具真的查得到，
H2 解锁受限命令，H3 让多轮不重复劳动。

## 3. 改动范围

| 文件 | 动作 |
|---|---|
| `core/engine/PromptSection.kt` | 新增 — 分节契约 + 组装器 |
| `core/engine/PromptContext.kt` | 新增 — 每轮求值的环境快照 |
| `core/engine/ContextManager.kt` | 改 — 系统提示改为组装结果；支持上轮轨迹 |
| `core/engine/AgentEngine.kt` | 改 — 回合结束时回传工具轨迹 |
| `runtime/AgentRuntime.kt` | 改 — 注册分节、注入 confirmer、清理 `pm` 死配置 |
| `runtime/RuntimeEnvironment.kt` | 新增 — 采集环境快照（包名/版本/工具名/CLI 路径） |
| `core/tools/ShellTool.kt` | 改 — `grep`/`head` 参数、头尾截断、完整输出落盘 |
| `core/tools/ToolSystem.kt` | 改 — 驾驶态策略接入 |
| `ui/chat/ToolConfirmDialog.kt` | 新增 — 确认弹窗 |
| `ui/chat/ChatScreen.kt`、`MainActivity.kt`（`ChatConsole`） | 改 — 挂确认弹窗 |
| `app/src/test/.../core/engine/PromptAssemblerTest.kt` | 新增 |
| `app/src/test/.../core/engine/ContextManagerTest.kt` | 扩展 |
| `app/src/test/.../core/tools/ShellToolTest.kt` | 扩展 |

---

## H1 · 系统提示分节

**问题**：`BASE_SYSTEM_PROMPT` 只有一句话，Agent 不知道自己在什么设备上、有哪些工具、自带什么 CLI。

**契约**：

```kotlin
// core/engine/PromptSection.kt
interface PromptSection {
    val name: String
    val order: Int
    fun render(context: PromptContext): String
}

class PromptAssembler(private val sections: List<PromptSection>) {
    init { require(sections.map { it.name }.toSet().size == sections.size) { "section 重名" } }

    fun assemble(context: PromptContext): String =
        sections.sortedWith(compareBy({ it.order }, { it.name }))
            .map { it.render(context).trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
}
```

**分节与顺序**：

| order | name | 内容 |
|---|---|---|
| 10 | persona | 现有 `BASE_SYSTEM_PROMPT` 第一句 |
| 20 | environment | 应用包名、版本、Android API、是否 Automotive、当前时间、屏幕尺寸 |
| 30 | capabilities | 已注册工具的「名字 + 一句话用途」，从 `Tool.description` 取首句 |
| 40 | cli | `aos-llm` 绝对路径与子命令清单（`show/set/use/list/status`） |
| 50 | rules | 现有规则 + 新增两条：① 用户给出的真实数值与工具读数矛盾时，以用户为准并写明"工具读数与实际不符"；② 需要底层数据时优先用 `shell_exec`，不要以"没有提供给我"作答 |

**关键点**：`render` 每轮求值，不在构造期烘焙。时间、可用工具、CLI 是否存在都会变。

**验收**：

- 单测 `PromptAssemblerTest`：顺序正确；重名抛错；同一 assembler 在不同 `PromptContext` 下产出不同文本
- 单测：`capabilities` 段落包含全部已注册工具名
- 真机复测图 1 场景：问"你能不能把思考强度设成 high" → 应答"不能设，但可以用 `aos-llm show` 看当前配置"，且**实际调用了 `shell_exec`**

---

## H2 · 审批通道

**问题**：`ToolSystem()` 空参构造，`Ask` 级工具一律返回"当前没有注入确认器"。`settings`/`wm` 等于不存在。

**契约**：

```kotlin
// ui/chat/UiToolConfirmer.kt
class UiToolConfirmer(private val scope: CoroutineScope) : ToolConfirmer {
    private val _pending = MutableStateFlow<PendingConfirm?>(null)
    val pending: StateFlow<PendingConfirm?> = _pending

    var driveRestricted: Boolean = false
    private var deferred: CompletableDeferred<Boolean>? = null

    override suspend fun confirm(toolName: String, args: JsonObject): Boolean {
        // 驾驶中不弹框：直接拒绝，由模型向用户说明原因
        if (driveRestricted) return false
        val answer = CompletableDeferred<Boolean>().also { deferred = it }
        _pending.value = PendingConfirm(toolName, args.toString().take(200))
        val result = withTimeoutOrNull(CONFIRM_TIMEOUT_MS) { answer.await() } ?: false
        _pending.value = null
        return result
    }

    fun respond(allow: Boolean) { deferred?.complete(allow) }
    private companion object { const val CONFIRM_TIMEOUT_MS = 15_000L }
}
```

**接线**：

- `AgentRuntime.refresh()` 接受并保存 confirmer，构造 `ToolSystem(confirmer)`
- `ChatConsole` 观察 `pending`，非空时弹 `ToolConfirmDialog`（工具名 + 参数摘要 + 允许/拒绝）
- 驾驶态拒绝时，`ToolResult.Rejected` 的 reason 写"驾驶中不允许人工确认，请停车后重试"，让模型能如实转述
- 删除 `askCommands` 里的 `pm`（与 `blockedCommands` 冲突，当前恒 Forbid）；`pm` 维持禁用，子命令级放行留到后续

**验收**：

- `ToolSystemTest`：Ask + confirmer=true → 执行；confirmer=false → `Rejected`；confirmer=null → 维持现有拒绝文案
- 新增：驾驶态 → 拒绝且 reason 含"驾驶中"
- 真机：让 Agent 跑 `settings get global device_name` → 弹窗 → 允许 → 返回实际值

---

## H3 · 会话工具轨迹

**问题**：`recordExchange` 只存 Q/A 文本，工具调用与结果不入历史。下一轮 Agent 不知道上一轮查过什么。

**契约**：

```kotlin
// core/engine/ContextManager.kt
data class ToolTrace(val name: String, val status: String, val summary: String)

fun recordTurn(userText: String, assistantText: String, traces: List<ToolTrace> = emptyList())
```

- `AgentEngine` 在 `Completed` 前把本轮 `ToolOutcome` 汇总成 `List<ToolTrace>`（每次调用一条，`summary` 取结果前 120 字符）
- `ContextManager` 保存 traces，**下一轮**把它们渲染进系统提示的 `trace` 段落（order 45）：
  `上一轮工具轨迹：shell_exec → ok，128 行；vehicle_basic → ok`
- 只保留最近一轮，超出即丢弃；traces 总长超过 400 字符时截断

**验收**：

- `ContextManagerTest`：第二轮 `beginTurn` 的系统提示包含上一轮 trace
- 单测：traces 超长被截断且不影响正常历史
- 单测：`reset()` 后 trace 清空

---

## H4 · shell 输出过滤与头尾截断

**问题**：禁止管道 + `take(8000)` 硬截，`dumpsys car_service` 几十 KB 只能看到开头。

**契约**（不拼 shell，全部在 Kotlin 侧处理）：

```kotlin
// ShellTool inputSchema 增加两个可选参数
"grep":  { "type": "string", "description": "按子串过滤 stdout 行，区分大小写" }
"head":  { "type": "integer", "description": "过滤后只保留前 N 行" }
```

- 仍拒绝 `; | & $ < > \``，管道继续不支持——过滤走参数，不走 shell
- `ProcessCommandRunner` 截断改头尾：保留前 `4000` + 后 `4000`，中间写 `…省略 N 字符…`
- 完整输出落盘 `files/tool-output/<uuid>.txt`，结果 JSON 追加
  `"full_output": "<绝对路径>", "total_lines": N, "truncated": true`
- `ShellTool.description` 同步说明这三个字段，让模型知道可以二次读取

**验收**：

- `ShellToolTest`：`grep` 只留匹配行；`head` 生效；含元字符仍被 `Rejected`
- `ShellToolTest`：超长输出 → `truncated=true` 且 `full_output` 路径存在
- 真机：`dumpsys car_service` + `grep="Supported Properties"` → Agent 能拿到属性表并引用

**风险**：`shell_exec` 以 app uid（`u0_a48`）运行，`dumpsys car_service` 的可见范围
可能与 adb root 不同。此项**须实测**，结果并入
[`real-car-readonly-probe-plan.md`](./real-car-readonly-probe-plan.md) M5x。

---

## 4. 验收清单

- [x] H1 单测全绿（真机图 1 场景复测待装车）
- [x] H2 单测全绿（真机确认弹窗待装车）
- [x] H3 单测全绿（真机连续两轮不重复调同一工具待装车）
- [x] H4 单测全绿（真机 `dumpsys` 过滤待装车）
- [x] 现有单测无回归（262 项中仅 `LlmStateMirrorTest` Windows `sh` 缺失为已知环境问题）
- [ ] 提示词增长 < 800 token（`CharEstimateBudget` 实测，待真机/联调补测）

## 5. 不在本期做的事

- 会话持久化（append-only 日志落库）——属 `core/session` 对等能力，另起计划
- 子代理 / 计划（Todo）状态 —— 依赖 H3 落地后再评估
- sandbox（Landlock / seccomp 级隔离）——Android 上不可移植，本期只用权限分级
- 网页搜索、文件读写、应用管理工具 —— 见 `agent-capabilities.md` 待规划项
- 图 4 的 300% 误差 —— **不是 harness 问题**，属工具实现（`VehicleBatteryReader` 把
  fallback 分母算出的结果标成 `ok`），单独立项

## 6. 进度

| 步骤 | 状态 |
|---|---|
| H1 系统提示分节 | 完成 2026-10-28（单测绿；真机复测待装车） |
| H4 shell 输出过滤 | 完成 2026-10-28（单测绿；真机 dumpsys 待装车） |
| H2 审批通道 | 完成 2026-10-28（单测绿；真机弹窗待装车） |
| H3 会话工具轨迹 | 完成 2026-10-28（单测绿；真机多轮待装车） |
| P0-a `ShellTool` 截断指引（C2） | 完成 2026-10-28（`truncation_notice` 带落盘路径 + 原始字符数 + 「再次读取」；单测绿；真机待装车） |
| P0-b `Rejected` 结构化 reason（C1） | 完成 2026-10-28（`RejectionReason` 五分类 + 引擎回灌 `reason`/`message`；单测绿；真机待装车） |
| P1-a 上下文分级降级（C4） | 完成 2026-10-28（一级压旧工具结果 + 二级按回合组丢；单测绿；真机长会话待装车） |
| P1-b 稳定节快照（C6） | 完成 2026-10-28（`PromptSection.stable` + 首帧快照复用；单测绿；真机待装车） |
| P2 会话 JSONL 持久化 | 完成 2026-10-28（`SessionLog` append-only + 冷启动恢复最近一轮；单测绿；真机进程被杀恢复待装车） |
| P2 结构化 `ask_user` 提问 | 完成 2026-10-28（`AskUserTool` + 弹窗/驾驶态语音回答；单测绿；真机待装车） |

### P0 对齐补充说明（2026-10-28）

- **C2 截断指引**：`truncated=true` 时新增 `truncation_notice`，内容含原始字符数与
  `full_output` 绝对路径，并明确写「可再次读取该文件取回全部内容」；未配置 `outputDir`
  时如实说明拿不到全文并建议改用 `grep`/`head`，不编造路径。
- **C1 结构化 reason**：`ToolResult.Rejected(reason: RejectionReason, message: String)`。
  分类为稳定契约，措辞与语义分离；引擎回灌 payload 改为
  `{status:"rejected", reason:<wire>, message:<措辞>}`。分类与触发点见
  [`../mechanisms/agent-capabilities.md`](../mechanisms/agent-capabilities.md)「拒绝原因分类」。

### P1 对齐补充说明（2026-10-28）

- **C4 分级降级**：`core/engine/ContextDegradation.kt`，一级压缩旧工具结果里的长字符串字段
  （JSON 保留结构，加 `compacted`/`original_chars` 记账；`full_output` 路径字段得以保留），
  最近 2 条工具结果保持原样；二级按**回合组**丢弃最旧历史——逐条丢会拆散
  `assistant(tool_calls)` 与 `tool` 的配对，OpenAI 兼容服务端会拒整个请求。
- **C4 前提**：`AgentEngine` 把整个工具交换序列原样存入 `ContextManager`
  （`recordToolTurn`），三段分离 `user → assistant(tool_calls) → tool → assistant(最终答案)`，
  最终答案单独成一条 assistant 消息，合并会被服务端拒。
- **C6 稳定节快照**：`PromptSection.stable` 首帧渲染后逐字复用，动态节每轮重算；
  `persona`/`rules` 为稳定节，`environment`/`capabilities`/`cli` 为动态节。
  稳定节只能用于纯常量文本，标错会固化过期环境。
- 回归：284 项单测中仅 `LlmStateMirrorTest.valuesWithSingleQuotesStayShellSafe` 失败，
  原因是 Windows 环境缺 `sh`（已知环境问题，与本次改动无关）。
