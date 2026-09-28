# 对话控制台（Agent 调试面）计划

> 父计划：[../mvp-core-plan.md](../mvp-core-plan.md)（对话界面在父计划里是"非门闩"，本次按用户要求提前放出来当测试入口）
> 配套机制文档：[../../mechanisms/agent-capabilities.md](../../mechanisms/agent-capabilities.md)、[../../mechanisms/ui-design-system.md](../../mechanisms/ui-design-system.md)
> **统一愿景对齐**：只做调试与验证面，不做聊天产品；界面只消费引擎事件流，不持有任何推理逻辑（愿景原则五）。无偏离。

## 0. 目标

一句话：**让人在车机上打字，亲眼看到 Agent 命中 skill、调本地工具与远端 MCP 工具、把结果整合成回答**。这是验证 Step 2/3/4/6 的唯一人机入口。

## 1. 设计（Operate 模式，沿用既有设计系统）

按 `rules/skill-routing.md`：本轮是对既有界面的**扩展**而非重设计，视觉世界沿用 `design-system/aosagent/MASTER.md` 与 `ui/theme/*` token，不引入新色值/新字号。

- 三栏 → 沿用 `EngineerModeScreen` 的顶栏样式（标题 + 返回按钮）。
- 左：对话流。用户问句、助手回答、**工具轨迹行**（等宽 `AOSDataText`，`→ 工具名 参数` / `← ok|rejected|timeout 摘要`）。工具轨迹是这一页存在的理由，必须常驻可见而不是折叠在调试开关里。
- 右：运行状态卡。模型配置（baseURL / model / key 是否已设）、已加载 skill 列表、MCP 来源逐个状态（成功几个工具 / 失败原因）、当前可调用工具清单。
- 底：输入行 + 发送按钮，触控目标 56dp；配置缺失时**按钮禁用并说明原因**（不留死按钮）。
- 配置区就在状态卡下方：baseURL / model / apiKey / MCP server 地址，保存即写入私有 DataStore；apiKey 输入框 `Password` 视觉 + 不回显。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `runtime/AgentRuntime.kt` | 新增 | 组装根：配置 → provider → ToolSystem（3 本地工具）→ SkillRegistry → MCP 来源 → AgentEngine；暴露 `status()` / `send(query)` |
| `ui/chat/ChatScreen.kt` | 新增 | 对话流 + 工具轨迹 + 输入行 |
| `ui/chat/ChatState.kt` | 新增 | 纯 Kotlin 状态与事件折叠（可 JVM 测） |
| `ui/components/AOSControls.kt` | 改 | 新增 `AOSToolTraceLine`（等宽轨迹行） |
| `MainActivity.kt` | 改 | 布尔导航改 `Destination` 枚举（Home / Engineer / Chat） |
| `ui/home/HomeScreen.kt` | 改 | 对话磁贴从"待开放"改"可用"并接跳转 |
| `res/values/strings.xml` + `values-en/` | 改 | 成对新增文案 |

## 3. 关键实现约束

- **引擎不反向依赖 UI**：`AgentRuntime.send()` 返回 `Flow<AgentEvent>`，页面只做折叠；AOC 二期复用同一入口。
- **skill 路由在运行时层**：`SkillRegistry.match(query)` → `engine.run(query, skill.prompt, skill.effectiveToolNames(available))`；未命中传 `null` 走通用对话。
- **MCP 来源异步加载**：进页面即拉一次，失败只让该来源显示"0 工具 + 原因"，不阻塞输入框。
- **apiKey 只进私有 DataStore**，UI 与日志都不回显；`RuntimeStatus` 里只带掩码描述。
- 不新增依赖、不加 ViewModel 框架（当前工程没有），状态用 Compose `remember` + 一个纯函数折叠器。

## 4. 验收

- 4.1 编译 + 现有测试全绿（JVM/仪器）。
- 4.2 装到 Automotive 模拟器：对话磁贴可点、进得去页面、返回能出来。
- 4.3 状态卡显示真实数据：2 个内置 skill、MCP fixture 的 3 个远端工具、车辆工具可读字段清单。
- 4.4 未配模型时发送按钮禁用并给出原因；配好后能发。
- 4.5 端到端跑一条"车速多少、电量还剩多少"，看得到两次工具调用与整合回答（需用户填模型 key）。

## 5. 不做

气泡美化、Markdown 渲染、语音输入、历史持久化、多会话、流式打字机特效之外的任何聊天产品功能。

## 进度

- [x] 2 全部文件（`AOSToolTraceLine` 未单独建组件，轨迹行直接用 `AOSDataText` 渲染，一处使用不值得抽象）
- [x] 4.1 编译 + 现有测试全绿（JVM 111 例、仪器 18 例）
- [x] 4.2 对话磁贴可点、进得去、返回能出来（Automotive 模拟器实跑）
- [x] 4.3 状态卡显示真实数据：2 个内置 skill、6 个可调用工具（3 本地 + 3 个来自官方 SDK fixture server）、车辆可读字段 7 项
- [x] 4.4 未配模型时发送按钮禁用并在上方给出原因；MCP 连不上时状态卡显示具体失败原因（`transport failed: ConnectException: Failed to connect to /10.0.2.2:9101`），应用不崩
- [ ] 4.5 端到端跑一条真实问题 —— **待用户填模型 key**

实测中修掉的三处：配置卡原本在滚动区下方进页面看不到（改到状态卡上方）；MCP 工具被重复列两遍（状态按 `category` 前缀区分本地/远端）；默认 MCP 地址填了被防火墙挡的 `10.0.2.2`（改成 `adb reverse` 的 `localhost`）。`shell_exec` 之前漏接进运行时，已补（只读白名单 getprop/uptime/date/dumpsys）。

> 创建：2026-09-28
