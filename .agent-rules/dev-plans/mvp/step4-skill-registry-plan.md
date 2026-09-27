# Step 4 子计划：Skill 机制

> 父计划：[../mvp-core-plan.md](../mvp-core-plan.md) Step 4
> 配套机制文档：[../../mechanisms/agent-capabilities.md](../../mechanisms/agent-capabilities.md)、[../../mechanisms/aoc-integration.md](../../mechanisms/aoc-integration.md)（capability 字段对齐）
> **统一愿景对齐**：①skill 形态参考 OpenClaw Skills（定义文件 + 注册表），不自研新框架；④两个内置 skill 都是车辆/车机专属只读能力；⑤轻内核——加一个 skill = 加一份定义文件，引擎零改动。AOC 上报映射只留字段不做接入（AOC 已移出本期）。无偏离。

## 0. 本步要解决什么

让引擎**按任务组织能力**：一句话进来，判断该用哪套提示词 + 哪一组工具，判断不出来就正常聊天。产出物是 `SkillRegistry` + 两份内置 skill 定义。

## 1. 命中方式：先确定性，不靠模型猜

MVP 用**声明式触发词匹配**（`triggers` 数组），不用"再问一次模型该用哪个 skill"。理由：

- 可测：命中结果是纯函数输出，JVM 单测能穷举。
- 可解释：Step 5（二期）AOC 远程下发 `skill_request` 时按 `id` 直接命中，不依赖模糊匹配；本地命中与远程命中共用同一套注册表。
- 省一次往返：座舱场景对首包延迟敏感。

代价是触发词覆盖不全就漏判——所以**未命中必须安静回退通用对话**（父计划硬要求），且 skill 的 `description` 会随可用能力注入系统提示，让模型在没命中时仍知道有这些能力。多 Agent 路由、意图分类器一律不做。

## 2. 改动文件清单

| 文件 | 动作 | 作用 |
|---|---|---|
| `core/skills/SkillDefinition.kt` | 新增 | 定义数据类 + `SkillPermission`(Query/Comfort/Motion/HighRisk) + `effectiveToolNames()` 过滤 |
| `core/skills/SkillRegistry.kt` | 新增 | 解析定义、按触发词命中、按 id 取；`parseAll` 为纯函数 |
| `core/skills/SkillSourceLoader.kt` | 新增 | Android 侧从 `assets/skills/<id>/skill.json` 枚举加载（唯一碰 `Context` 的一层） |
| `core/engine/ContextManager.kt` | 改 | `beginTurn`/`buildRequest` 接受本轮附加指令，系统提示 = 基础提示 + skill 片段 |
| `core/engine/AgentEngine.kt` | 改 | `run(query, instruction, toolNames)`：按 skill 收窄工具子集 |
| `assets/skills/vehicle_status/skill.json` | 新增 | 车况查询 |
| `assets/skills/system_diagnostics/skill.json` | 新增 | 系统诊断 |
| `app/src/test/.../core/skills/*` | 新增 | 解析、命中、越权工具过滤、未命中回退 |
| `app/src/androidTest/.../SkillAssetTest.kt` | 新增 | 从 APK 真资产加载，证明两份 skill 可被解析并命中 |

## 3. `skill.json` 契约

```json
{
  "id": "vehicle_status",
  "name": "车辆状态查询",
  "description": "查询车速、电量、续航、车外温度、里程等只读状态",
  "triggers": ["车辆状态", "车况", "电量", "续航", "车速"],
  "tools": ["vehicle_basic", "system_info"],
  "prompt": "回答时给出数值与单位；字段缺失要说明是权限还是硬件原因，不要编造。",
  "permission": "query",
  "fallback": "车辆属性不可用时说明当前车机未暴露该属性，改用 system_info 给出系统层信息",
  "result_hint": "输出 {字段: {status, value, unit}} 的要点摘要",
  "tags": ["vehicle", "readonly"],
  "inputModes": ["text", "voice"],
  "outputModes": ["text", "voice"]
}
```

- `permission` 四档对齐父计划决策 7；**MVP 只允许 `query`**，其余档位视为未命中（回退通用对话），不报错也不执行。
- `tags` / `inputModes` / `outputModes` 是为 AOC capability 上报预留的映射位（二期用），本期只解析不使用。
- `tools` 里出现引擎里不存在或越权的工具名时**直接过滤掉**：skill 写错不该让工具调用失败，更不该把控制类能力带进来。

## 4. 关键实现方案

- **引擎不感知 SkillRegistry**：`AgentEngine.run` 只接 `instruction: String?` 与 `toolNames: Set<String>?`。谁做路由（本地命中、AOC 远程指定、测试）由调用方决定，避免引擎反向依赖 skill 层。
- **工具子集复用 Step 3 已有的 `ToolSystem.exposedFor(names)`**，未知名字忽略。
- **命中打分**：触发词按"是否出现在 query 中"计数，取最高分；同分按 `id` 字典序，保证结果稳定可测。空 query 不命中。
- **降级不静默**：`SkillRegistry` 保留 `skipped`（档位不支持、字段缺失、JSON 坏了），与车辆 allowlist 同一套可观测思路。

## 5. 分步验收条件

- **5.1 解析**：两份内置 skill 解析成功；缺字段/坏 JSON 进 `skipped` 且不抛异常。
- **5.2 命中**：「车辆状态怎么样」命中 `vehicle_status`，且该轮请求里的工具定义只含它声明的子集（不是全部工具）。
- **5.3 回退**：「今天心情不好」不命中任何 skill，引擎按通用对话执行且不报错（工具集为全量）。
- **5.4 越权过滤**：给一个声明 `tools: ["control_window"]` 或 `permission: "motion"` 的 skill，前者被过滤掉、后者整体按未命中处理；断言引擎拿到的工具集里没有任何控制类工具。
- **5.5 零引擎改动**：新增第三份 skill 只加 `assets/skills/<id>/skill.json`，测试断言它能被加载并命中（不改 Kotlin）。
- **5.6 真资产**：仪器测试从 APK 里读 `assets/skills/`，两份 skill 可加载命中。
- **5.7 回写**：父计划 Step 4 勾选、`agent-capabilities.md` 补 SkillRegistry 实现状态、索引同步。

## 6. 本步不做

- LLM 意图路由、多 Agent 分解、skill 优先级/冲突消解。
- skill 热加载与下载（Batch 3 插件市场）。
- AOC capability 上报（随 AOC 接入一并二期）。
- `comfort` / `motion` / `high_risk` 三档的任何执行路径。

## 进度

- [x] 5.1 解析 — 坏 JSON / 缺字段 / 非法档位进 `skipped`，注册表不崩
- [x] 5.2 命中 — 「现在电量还剩多少」→ `vehicle_status`；该轮工具定义只剩它声明的子集
- [x] 5.3 回退 — 「今天心情不好」不命中，引擎按全量工具继续对话，不报错
- [x] 5.4 越权过滤 — skill 里的 `control_window` 被过滤；`permission: comfort` 整体不注册（等同未命中）
- [x] 5.5 零引擎改动 — 合成新 skill 的 JSON 即可被解析并命中（`highestScoreWins...` 用例），未改任何 Kotlin
- [x] 5.6 真资产 — `SkillAssetTest` 从 APK `assets/skills/` 枚举加载，两份 skill 命中真实问法
- [x] 5.7 回写 — 本节、父计划、`agent-capabilities.md`、`mvp/README.md` 索引

实测：JVM 91 例、Automotive 模拟器仪器测试 16 例，全部通过（2026-09-27）。

## 一处设计取舍的补充说明

`胎压` 被写进 `vehicle_status` 的触发词，但它在 allowlist 里是 `signature|privileged` 已被挡下——
所以真实效果是：命中 skill、调用 `vehicle_basic`、该字段返回 `unsupported` 并带原因。
保留这个组合是有意的：它让"用户问了但车机给不了"这条路径长期有一条测试覆盖，
而不是等真车上才发现要么编数、要么静默。

> 创建：2026-09-27
