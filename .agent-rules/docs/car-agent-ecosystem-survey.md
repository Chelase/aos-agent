# 车载 Agent / 星环 OS 调研（car-agent / caragent / 座舱形态 / HaloOS）

> 采集日期：2026-09-14。来源：GitHub 公开仓库、知乎专栏原文、Gitee HaloOS 文档。
> 性质：外部参考资料，不构成规则或机制；结论服务于 mvp-core-plan 的「抄思路不抄代码」决策。
> 原文存档：`docs/参考文档/zhuanlan.zhihu.com/car-intelligence-agent/`（baoyu-url-to-markdown 拉取）。

## 一句话结论

四个来源里，**真正能加速本仓库 MVP 的不是代码，而是分层与工程套路**：知乎文把「SOA / 工具 / Skill / 跨端网络」划清了，正好对应本项目的 Car API、`ToolSystem`、`SkillRegistry`、AOC；两个 GitHub 仓库是 Python 云端演示（模拟车况、无 AAOS），只借工具清单、mock 状态机和流式事件；HaloOS 是 MCU/ADAS 整车 OS，与座舱 Agent 不在同一层，只借「软硬解耦 + 模拟器 + 控车隔离」思想。**不 fork、不接 WTT、不在 MVP 做 RAG 或多 Agent 编排。**

## 逐项评估

### orangefplus/car-agent — 无 License，Python 演示，只可做场景参考

- 定位：RAG + 多 Agent 的智能汽车服务助手（车况 / 车控 / 导航 / 诊断 / 预约 / 售后）。1★，Python 3.12 + FastAPI WebSocket + LangChain/LangGraph + React 前端 + ChromaDB + MCP。
- 架构：Orchestrator 意图路由 → 6 个子 Agent；车况/车控通过 FastMCP 暴露；`vehicle_state.json` 文件模拟整车状态（跨 MCP 子进程同步）。
- 工具切面（`SUB_AGENT_TOOL_MAP`）把「查」和「控」拆开：`get_battery_status` / `get_fault_codes` vs `control_air_conditioner` / `set_driving_mode`。
- License：**未声明**。禁止复制代码。

可借鉴（思路）：

1. **车辆工具最小集合**：电量/SOH/续航、里程与保养、胎压、故障码、位置、空调、车窗、天窗、座椅加热通风、氛围灯、驾驶模式——MVP `vehicle_basic` 先做只读子集（车速/电量/续航），字段命名可按此清单预留。
2. **Car API 不可用时的 mock**：`VehicleState` + JSON 落盘。AAOS 模拟器经常没有完整 VHAL，这是让 Step 3 能在模拟器上跑通的最快路径。
3. **流式执行事件**：`start` / `thinking` / `route` / `tool_call` / `tool_result` / `token` / `end` / `error` / `vehicle_status_update`。本地对话 UI 与 AOC `skill_result` 都需要这类结构化进度，不必等前端拓扑图。
4. **工具 docstring 写清必填/可选/示例**：降低畸形 function-call JSON 概率，与已有 `ToolJsonRepair` 互补。
5. **关键词预路由 + LLM 兜底**：本项目用 Skill 命中代替子 Agent 路由即可，不要再拆 6 个 Agent。

不适用：LangGraph 多 Agent、Chroma RAG、高德 MCP、预约售后、React 驾驶舱大屏、把控车做成无确认 MCP。

### wwsa666/caragent — 无 License，售后 RAG 管家，工程点更碎

- 定位：新能源车服 AI 管家（售前/售后问答 + SOH/天气工具 + 报告工作流）。0★，Python + FastAPI 流式 + LangChain ReAct + Chroma + 可选 Mongo 画像。
- 有价值的工程件：`ToolRegistry` 按类别注册、子 Agent 按需拉取工具子集；`IntentRouter` 只输出 `chat|report|diagnose`，非法标签回退 `chat`；对话结束后异步抽画像，失败不影响主流程。
- 反面教材：仓库提交了 `.env`；身份用 VIN 而不是统一人格 ID。

可借鉴（思路）：

1. **按类别注册工具、Skill 只加载子集**——直接对应 mvp-core-plan Step 4：`skill.json` 声明 `tools`，`SkillRegistry` 从 `ToolSystem` 取子集注入引擎。
2. **意图分类失败回退通用对话**——对应 Step 4「未命中 skill 时退回通用对话不报错」。
3. **旁路记忆提取失败不阻断主循环**——MVP 不做长期记忆；若以后做，必须异步且可失败。

不适用：Mongo 画像、CSV 车队沙盘、报告 Graph、RAG 手册、VIN 作为跨设备身份（本项目身份在 AOC `root_id` / `agent_id`）。

### 知乎《智能座舱Agent的真正形态》——与本仓库愿景同构，是这次调研的主收获

原文主张：2026 座舱 Agent 多数仍是「语音遥控器」（控车 + 闲聊）；真正形态是车作为个人 Agent 网络的高频入口。分层是：

| 文中概念 | 本仓库对应 | MVP 是否落地 |
|---|---|---|
| SOA（车辆服务标准化） | `system/` Car API / `VehicleBasicTool` | Step 3 只读 3 字段 |
| MCP（Agent 调工具） | `ToolSystem`（本地工具协议，不接 MCP SDK） | Step 3 |
| SKILL（任务封装） | `SkillRegistry` + `assets/skills/` | Step 4 |
| WTT / Topic 网络 | **AOC Hub**（注册、DM、skill_request/result、能力声明） | Step 1/5；**不自研 WTT** |
| 车端 Orchestrator | `AgentEngine` | Step 2 |
| Fallback + 记忆同步 | 本地引擎常驻 + Hub 增强；任务状态走 AOC 回传 | 双脑已写进计划；完整记忆同步延后 |

文中与愿景已对齐、应直接吸收的原则：

1. **不要做成语音遥控器**。MVP 验收已是「能调工具干活 + 能被 AOC 远程触达」，不是聊天面板。
2. **车端是入口和编排，不是唯一大脑**。与「aos-agent 独立可运行、AOC 是增强」一致。
3. **Skill 不只是提示词**：应带输入参数、权限、工具子集、执行位置偏好、Fallback、结果摘要模板、驾驶安全限制。当前 `skill.json` 规划（id/name/description/触发场景/tools/提示词）缺 **权限与安全级、降级策略、结果模板**——Step 4 子计划应补这三项，实现可以先留字段、行为用 Tool 三级权限兜底。
4. **控车分级**：信息查询 / 舒适性控制 / 运动控制 / 高风险。MVP 只做信息查询（auto）；舒适性以后 ask；运动与高风险 forbid 或显式确认。不要把 GitHub demo 里的 `control_window` 无确认搬进来。
5. **本地优先、云端增强、长任务异步化**：语音/紧急取消/读车况本地化；跨设备任务走 AOC DM，用进度事件而不是阻塞等待。
6. **可观测**：任务由谁发起、调了哪些工具、为何降级。MVP 用 `tool_call`/`tool_result` 日志 + `skill_result` 结构化回传即可，不上 Topic 追踪平台。

明确不要做：接入 `wtt.sh` / OpenClaw `@cecwxf/wtt` 插件。跨设备控制面已经是 AOC，再接一套 Topic 网络违反「不自研生态协议」。

### 理想星环 OS（HaloOS，gitee.com/haloos）——整车 OS，不是座舱 Agent

- 定位：理想自研整车 OS。开源的是 **VCOS（智能车控，NuttX/MCU）**、**ADOS（辅助驾驶内核）**、**VBS（车载通信中间件）**。Apache-2.0。座舱/AAOS 不在已开源范围内。
- 架构口号：辅助驾驶=大脑、车控=小脑、通信=神经、信息安全=免疫。解决算力/成本、软硬迭代周期、实时性、安全四对矛盾。
- 对 aos-agent 的层位：HaloOS 在 MCU/ADAS/中间件；本项目在 **AAOS 座舱应用层**。不能把 VCOS 链进 APK，也不能用其 POSIX/车控栈代替 Android Car API。

可借鉴（思想，映射到座舱侧已有或应坚持的做法）：

1. **软硬解耦 / vendor 适配层** → 已有 OEM 探测→适配→降级；`VehicleBasicTool` 必须走 Reader 接口 + fallback，不要把某家 VHAL 写死。
2. **无硬件也能开发**：HaloOS 自带 SIM 模拟器 → MVP 必须有 `FakeVehicleReader`（JSON mock），否则 Step 3 卡在真车上。
3. **轻量隔离**：控车与信息查询不要同一权限面；对应 Tool 的 auto/ask/forbid。
4. **服务化**：车况是服务不是 prompt 幻觉；工具返回结构化字段（电量数字而不是散文），Agent 再生成语音/文本摘要。

不适用：硬实时内核、确定性以太网、TrustZone/HSM、跨 ECU 调度。那些属于车控 OS，不是本仓库范围。

## 对 MVP 的具体吸收（按 Step）

已于 2026-09-14 写入 [../dev-plans/mvp-core-plan.md](../dev-plans/mvp-core-plan.md)（无头优先，未开工）。下表是计划内增量对照：

| Step | 吸收 | 来源 | 明确不做 |
|---|---|---|---|
| 1 AOC Entry | 把 AOC 当作文中的 WTT：注册=入网，metadata=能力声明，DM=Topic 的 MVP 子集 | 知乎 / 已有 aoc-integration | 不接 WTT 插件 |
| 2 AgentEngine（无头） | 引擎发射 `tool_call`/`token`/`end`/`error`；测试与 AOC 只消费事件 | car-agent events.ts | 不做对话页、拓扑图、RAG |
| 3 ToolSystem | 只读 `vehicle_basic` + JSON mock Reader；工具描述写清 schema；查询 vs 控制分权限 | car-agent vehicle_server + HaloOS 隔离 | 不实现空调/车窗等控制工具 |
| 4 Skill | 按类别取工具子集；未命中回退 chat；skill.json 增加 `permission`/`fallback`/`result_hint` 字段（可先声明后实现） | caragent ToolRegistry + 知乎 SKILL | 不拆多 Agent、不上 MCP SDK |
| 5 AOC 收口 | `skill_result` 带回 tool 轨迹摘要；Hub 不可达时本地循环不变 | 知乎 Fallback + 已有双脑 | 不做跨端记忆库、不做家庭/办公 Agent |

## 决策建议

1. **不 fork 两个 GitHub 仓库**：无 License、Python/LangChain、模拟车而非 AAOS，fork 零收益。
2. **不集成 HaloOS 源码**：层位错误；Apache-2.0 也改变不了「MCU 内核 vs 座舱 App」。
3. **不引入 WTT**：AOC 已占据该生态位；再引入会分裂身份与协议。
4. **加速 MVP 的三件最小实事**（已写入 mvp-core-plan，尚未开工）：① `FakeVehicleReader`；② 工具/Skill 事件流（无头 + AOC 回传共用）；③ skill.json 补权限与降级字段，控制类工具本期不上。对话 UI 不是这三件之一。

> 更新时间：2026-09-14
