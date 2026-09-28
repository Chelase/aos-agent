# Step 6 子计划：MCP 工具来源接入

> 父计划：[../mvp-core-plan.md](../mvp-core-plan.md) Step 6
> 配套机制文档：[../../mechanisms/agent-capabilities.md](../../mechanisms/agent-capabilities.md)、[../../mechanisms/architecture-overview.md](../../mechanisms/architecture-overview.md)
> **统一愿景对齐**：⑤轻内核 + 能力外挂——MCP 只是第二类工具来源，引擎/Skill/AOC 三层都不感知它；工具协议用公开标准不自研（愿景约束 5）；跨设备身份仍只走 AOC，MCP 不承担设备间编排。无偏离。

## 0. 本步要解决什么

让车机 Agent 能把**外部 MCP server 的工具**当本地工具用：连上 → 列工具 → 注册进 `ToolSystem` → 引擎照常调用。判据是"加一类能力来源不改内核"。

## 1. 范围与协议事实

只做 **MCP client**，传输只支持 **Streamable HTTP**（POST 一个 JSON-RPC 请求，响应可能是 `application/json` 也可能是 `text/event-stream` 单帧）。协议要点（2026-09-28 核对官方 transports 规范）：

- 请求头必须 `Accept: application/json, text/event-stream`；`Content-Type: application/json`。
- 握手：`initialize`（带 `protocolVersion` / `capabilities` / `clientInfo`）→ 响应里回 `protocolVersion` + `serverInfo`；会话 id 在 initialize 响应的 **`Mcp-Session-Id`** 头，后续请求要带；还要带 **`MCP-Protocol-Version`** 头。
- 握手完必须补一条 `notifications/initialized` 通知（无 id）。
- `tools/list` → `result.tools[]`，每项 `name` / `description` / `inputSchema`。
- `tools/call` → `result.content[]`（`{type:"text",text:…}` 等）+ `isError` + 可选 `structuredContent`。

**明确不做**：stdio 子进程 server（量产车机 exec 受限）、SSE 长连接 resume（`Last-Event-ID`）、resources / prompts / sampling / roots、把本车工具反向暴露成 MCP server。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `core/tools/mcp/McpTransport.kt` | 新增 | `McpTransport` 接口 + `StreamableHttpTransport`（okhttp）；`parseSseJsonRpcFrame()` 为纯函数 |
| `core/tools/mcp/McpClient.kt` | 新增 | JSON-RPC 2.0：`initialize` / `toolsList` / `toolsCall`；错误与协议版本不匹配收敛成结果类型 |
| `core/tools/mcp/McpToolAdapter.kt` | 新增 | 把远端工具实现成 `Tool`，`content[]` 映射成结构化 `ToolResult` |
| `core/tools/mcp/McpToolSource.kt` | 新增 | 一个 server 一条来源：连接→列工具→产出适配器；失败只让该来源消失 |
| `core/tools/mcp/McpServerConfig.kt` | 新增 | server 配置（id/url/token/默认权限）+ 配置读取口 |
| `data/store/McpServerStore.kt` | 新增 | DataStore 持久化配置列表（token 与 apiKey 同级：只进私有存储，日志掩码） |
| `app/build.gradle.kts` | 不改 | 复用 okhttp + kotlinx-serialization，**不新增依赖** |
| `app/src/test/.../core/tools/mcp/*` | 新增 | 报文解析、结果映射、失败隔离、命名与越权 |

## 3. 关键实现方案

- **不引 MCP SDK**：`androidx.appfunctions` 之外，MCP 官方 JVM SDK 仍在演进且要拉进 Netty/Reactor 一类传输栈，与"轻量 + 任意车机"冲突。我们只需要 `initialize` + `tools/list` + `tools/call` 三个方法，手写 JSON-RPC 反而小且可测。
- **传输与协议解耦**：`McpTransport` 只有一个 `suspend fun request(method, params): JsonElement`，单测用假传输喂官方报文样例，不需要起服务。
- **命名**：OpenAI function name 只允许 `[A-Za-z0-9_-]`，所以远端 `tools/list` 里的名字要改写成 `mcp_<serverId>_<toolName>`（截断到 64、非法字符换下划线），适配器内部保留**原始远端名**用于 `tools/call`。Skill 里按改写后的名字引用。
- **权限**：远端工具默认 `Ask`，无 `ToolConfirmer` 即拒（与本地工具同一套 fail-closed）；可按 server 粒度配成 `Auto`。
- **失败隔离**：连接失败、握手版本不匹配、`tools/list` 报错 → 该来源贡献 0 个工具并记录 `McpSourceStatus`，**不影响本地工具、Skill 与对话**。这是父计划 Step 6 的核心验收。
- **结果映射**：`content[]` 里 `type:"text"` 收进 `content` 数组；有 `structuredContent` 直接摊平成字段；`isError:true` → `ToolResult.Error`；非文本内容（image/resource）本期只记录类型不解析。
- **超时**：沿用 `ToolSystem` 的 30s；连接与握手各自另设较短超时（10s），避免一个坏 server 拖慢整轮。

## 4. 分步验收条件

- **4.1 报文**：`initialize` 请求体含 `protocolVersion`/`clientInfo`；响应带 `Mcp-Session-Id` 时后续请求必须带上；`notifications/initialized` 被发出。
- **4.2 双形态响应**：`application/json` 与 `text/event-stream`（单帧 `data:`）两种响应都能解析；SSE 里出现多帧/心跳/非 JSON 帧时不崩。
- **4.3 工具映射**：远端 3 个工具 → `ToolSystem` 里出现 3 个合法命名的 `Tool`；`tools/call` 用的是**原始名**；`isError` 与非文本 content 各自映射正确。
- **4.4 失败隔离**：假传输抛连接异常 → 该来源 0 工具 + 状态可见；本地工具与引擎循环照常跑完一轮。
- **4.5 权限**：远端工具在无确认器时不被执行；配成 auto 的 server 才免确认。
- **4.6 内核未改**：`git diff` 证明 `core/engine/*` 与 `core/skills/*` 在本步零改动（这是"能力外挂"的硬证据）。
- **4.7 回写**：机制文档新增 MCP 工具来源契约；父计划 Step 6 勾选。

## 5. 已知未验证项（不许含糊）

- **与真实 MCP server 的互通没测过**。4.1–4.5 用的是按规范手写样例，能证明"我们按规范实现"，不能证明"和某个具体 server 兼容"。补测需要在本机起一个 Streamable HTTP server（模拟器经 `10.0.2.2` 访问），列为本步收尾项。
- 协议版本：按 `2025-06-18` 实现；server 若回更高版本，本期只记录不协商降级。
- token 走明文 HTTP 时等同裸奔，与 AOC 一样限制在可信网络/回环地址。

## 进度

- [x] 4.1 握手报文 — `initialize` 带 `protocolVersion`/`capabilities`/`clientInfo`，随后必发 `notifications/initialized`；`Mcp-Session-Id` 与 `MCP-Protocol-Version` 头已实现
- [x] 4.2 双形态响应 — JSON 与 SSE 两条分支都有测试；SSE 只认以 `{` 开头的 `data:` 帧（`parseToJsonElement` 对裸词是宽松的，光靠它能骗过 3 个用例，已改）
- [x] 4.3 工具映射 — 3 个远端工具 → 3 个合法名 `Tool`（`get.time` 与 `get_time` 撞名时后者加 `_2`），调用打回**原始远端名**；`isError` → `ToolResult.Error`，非文本 content 记类型不解析
- [x] 4.4 失败隔离 — 假传输抛连接异常 → 该来源 0 工具 + `McpSourceStatus(ok=false, detail=IOException)`，其余照常
- [x] 4.5 权限 — 远端 `Ask` 工具在无确认器时被 `ToolSystem` 拒（与本地工具同一个 fail-closed 闸，不是另开一条路）；配成 `Auto` 才免确认
- [x] 4.6 内核零改动 — `git diff` 证实本步**只新增文件**：`core/engine`、`core/skills`、`core/llm` 一行未动。这就是"能力外挂"的硬证据
- [x] 4.7 回写 — 机制文档新增 MCP 工具来源契约，父计划 Step 6 标注
- [ ] 真实 server 互通（§5）

实测：JVM 108 例、Automotive 模拟器仪器测试 17 例全绿（2026-09-28）。

> 创建：2026-09-28
