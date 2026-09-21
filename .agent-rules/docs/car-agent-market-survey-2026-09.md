# 车机 Agent 成熟度调研（平台方 / 国内 OEM / 供应商 / 开源）

> 采集日期：2026-09-21。来源：GitHub API 实测数据、厂商公告与中文产业报道（链接见各条）。
> 性质：外部参考资料，不构成规则或机制。与 [car-agent-ecosystem-survey.md](./car-agent-ecosystem-survey.md) 互补——那篇看的是开源演示仓库与分层思想，本篇看的是**谁已经把车机 Agent 做到量产、以及第三方能否接入**。

## 一句话结论

**面向车主的车机 Agent 已经成熟，但全部是封闭的；面向 AAOS 第三方开发者的那一层是空的。**Google 与高通把平台位占了，华为把技能平台开放了却不开放安全域，国内 OEM 的成熟能力一律不外露，开源侧最接近我们的 CarToolForge 只有 4★。aos-agent 不是重复造产品，是重复造**架构**——所以差异化只能落在"跨设备节点 + 离线独立 + 用户可自装"，而不是"能控车能聊天"。

## 平台方（已占位）

| 项目 | 事实 | 对 aos-agent 的含义 |
|---|---|---|
| **Gemini for Android Automotive** | 2026-06-26 起向存量车 rollout，首批 Volvo EX30；可直接调空调温度、座椅加热、雨刮；实际能力取决于各车企集成深度 | "能控座舱"不再是卖点。检索到的报道未提任何面向第三方的 API/SDK |
| **高通 车端人工智能 Claw 生态计划** | 2026-06-05 发布，自称"行业首个"加速车端智能体规模化部署的生态计划；通用智能体框架 + **开放技能市场 SKILL HUB** + 即用中间件，跑在骁龙数字底盘（NPU/CPU/GPU 全栈）；首批伙伴：诚迈科技、车联天下、斑马智能、德赛西威、镁佳科技、中科创达 | 我们 Step 4 的 `skill.json` / SkillRegistry 用的正是同一套词汇；但它是 B2B 芯片厂生态，不是可 sideload 的 APK 通道 |

## 国内 OEM（成熟但封闭）

| 产品 | 量产状态（有日期证据） | 实际能力 | 第三方可接入 |
|---|---|---|---|
| 理想同学 / MindGPT + OTA 8.2 | OTA 2026-01-23 | 多步任务分解、原生控车窗/空调/灯光/媒体、记忆、主动充电与泊车建议；MindVLA 2B 端侧 | 否 |
| 小鹏 天玑 AIOS 6.0（AI 小P 管家） | OTA 2026-02-04 起（G7/P7/X9） | 自称"行业首个主动服务座舱"，全天候主动服务、自由对话、手机接续 | 否 |
| 超级Eva（吉利/极氪 × 阶跃星辰） | 极氪 8X 首搭 2026-03，2026 内约 10 款车型 | 感知-决策-执行中枢，座舱与转向/制动协同，动态记忆 | 否 |
| IM Ultra Agent（智己 × 千问） | LS8 2026-03-26 上市 | 高风险指令**对话式二次确认**、持久记忆、智能体总线、时效敏感请求本地路由 | 明确否 |
| 小艺 + HarmonySpace 6（华为） | 2026-04/06 发布；HarmonyOS NEXT 座舱原生应用 2026-09 才车内内测、12 月交付 | 系统能力"Skill 化"、兼容 MCP、A2A 协议、手机→平板→**车机**技能跨端同步、分级记忆 | **是**（自然语言开发 Skill、一键发布，伙伴 Skill 与 Agent 已过百）——但检索未发现线控/ADAS/安全子系统的公开 API，产业报道评价"所谓开放是宣传定位" |
| 腾讯 出行智能体开放平台 | 2026-04 发布 | Agent Studio + Skills，小程序转可调用模块 | 是，但经车企中转；演示合作方不含车队级落地 |

## 供应商 / 端侧方案

- **萤火Claw / Firefly AIOS（诚迈 × 智达诚远，骁龙 8797）**：与本项目设计最接近的一份——端侧 Qwen3-30B-A3B、"可独立调度的能力单元"、串联导航/日历/座舱/第三方应用、提供语音与多模态控制的 API/SDK。状态为**量产验证阶段，未见具名量产车型落地**，且只走 B2B 车企授权。
- Cerence、博世：机构买家专属，无开发者 SDK 与时间表。

## 开源侧（GitHub 实测，2026-09-21）

| 仓库 | 数据 | 判断 |
|---|---|---|
| `autoharness/CarToolForge` | 4★ / MIT / Kotlin / 最后推送 2026-07-19 | **唯一**在 AAOS 上把 Car API 属性经 allowlist 暴露给 LLM 的开源实现，走 Android 16 AppFunctions；必须 priv-app + aflags 手工开关；自述泛型工具需 >20B 模型 |
| `autoharness/CarToolPlayground` | 2★ / MIT | 它的对话演示端，依赖 Firebase AI Logic 调 Gemini |
| `android/appfunctions` | 194★ / Apache-2.0 | Google 官方 AppFunctions 样例 + 特权测试 Agent；`androidx.appfunctions` 仍 alpha10 |
| `ThinkOffApp/CarWatch` | 289★ / AGPL-3.0 | 愿景在**另一层硬件**上成立：Pi 5 端侧 35B + OBD + 手册 RAG + 免唤醒语音 + systemd 自启 + 作为成员加入聊天房间被远程触达。不是 AAOS，且 AGPL 传染性 |
| `rufolangus/AAOSP` | 24★ | AOSP 分叉把 LLM 做成 system service、manifest 声明 MCP 工具、平台级 HITL 确认与审计——与我们的 auto/ask/forbid 同思路，但层位是改 OS |
| 其余（car-agent / caragent / CockpitAgent 等） | 0–1★，多为 Python FastAPI 演示，多数无 License | 见 car-agent-ecosystem-survey.md，只借思路 |

## 确实还不存在的东西（本项目的缺口）

1. **第三方注册车辆控制工具**的公开通道——华为只开便利类 Skill，安全域不开；OEM 全自研。
2. **AAOS 上的可用 Agent**——除 CarToolForge 那个演示外，没有开源的智能体循环、技能注册表、记忆或任务委派。
3. **开放的车 ↔ 中枢跨设备委派**——华为 A2A 只在自己墙内；没有可互操作的第三方 hub。
4. **个人开发者能拿到并跑通的东西**——供应商方案一律 B2B 授权。
5. **端侧开源权重模型真正跑在量产车里**——唯一像样的宣称是萤火Claw，而它还没有具名量产车型。

## 未核实（不要当事实引用）

- 华为发布会口径的能力数与调用量级数字（厂商自述，无第三方审计）。
- 小艺开放平台是否接受**个人**开发者，还是仅对注册主体开放。
- 理想/蔚来/小鹏/BYD 是否存在座舱开发者计划——检索只命中隐私"第三方 SDK 列表"页，**无法证明其不存在**。
- NOMI 当前的工具调用深度、AVATR MoLA 的智能体能力、Cerence NEURA 的量产定点、联发科的座舱 SDK 授权条款、星环 OS 开源范围是否含 Agent SDK。
- CarToolForge 的权限保护级别（normal/dangerous/signature|privileged）取自其 manifest 注释，与 AOSP `car-lib` 一致，但 **developer.android.com 当时抓取失败，本项目也未实测**——已在 mvp 子计划中设为强制实测门槛。

> 更新时间：2026-09-21
