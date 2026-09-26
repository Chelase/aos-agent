# 统一生态愿景

> 本文档定义 aos-agent、AOC (AgentOpenConnect)、aoc-watch-agent 三大项目的生态定位与协作关系。
>
> **后续所有开发计划与实现决策必须与本愿景对齐，不得偏离。**

---

## 一句话定位

**aos-agent = OpenClaw / Hermes 的车机移植版**，是一个跑在 Android Automotive OS 上的原生车载 Agent，同时参与 AOC 多设备 Agent 生态。

---

## 生态全景

```
                    ┌──────────────┐
                    │   AOC Hub    │
                    │ (中心编排层)  │
                    │  LLM 大脑     │
                    │  单一人格出口  │
                    └──────┬───────┘
                           │ AOC 协议 (HTTP/gRPC)
                           │ root_id 统一身份
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   ┌────────────┐  ┌──────────────┐  ┌──────────────┐
   │ 手机 Agent  │  │ aos-agent    │  │ watch-agent   │
   │ (规划中)    │  │ (车机)       │  │ (手表)        │
   │            │  │ 车载 Agent    │  │ 穿戴 Agent    │
   └────────────┘  └──────────────┘  └──────────────┘
```

### 三个项目的角色分工

| 项目 | 角色 | 定位 |
|------|------|------|
| **AOC (AgentOpenConnect)** | 中枢编排 + AI 大脑 | 桌面/服务器端的多 Agent 委派执行框架，提供 LLM 推理路由、单一人格收敛、跨设备状态同步；活动中心是**可迁移角色**，不绑定物理机器，故障后候选设备按确定性顺位接管（2026-09-06 对齐 AOC `vision-rule.md`） |
| **aos-agent** | 车载 Agent 节点 | OpenClaw/Hermes 车机版，跑在 AAOS 上的原生 Agent，提供车辆感知、CLI 终端、驾驶安全交互 |
| **aoc-watch-agent** | 穿戴 Agent 节点 | 手表端的轻量 Agent 节点，提供健康感知、抬手交互、通知触达 |

### 核心设计原则

#### 原则一：OpenClaw/Hermes 车机版

aos-agent 的 Agent 核心（Agent 循环、工具系统、Skill 机制、上下文管理）参考 OpenClaw/Hermes 的成熟模式，**不是从零设计**。aos-agent 的差异化不在 Agent 架构本身，而在：

- 车辆专属能力：Car API、OEM 探测适配、EV 电源周期管理
- 驾驶场景交互：语音优先、CarUxRestrictions 安全约束
- 系统集成：开机自启、前台服务、系统级 App

#### 原则二：参与 AOC 生态，而非自建

aos-agent 的跨设备能力通过对接 AOC 实现，不自研生态协议：

- **身份**：复用 AOC Identity Mod 的 root_id 机制
- **能力声明**：通过 AOC capability 注册机制声明车载技能
- **跨设备通信**：通过 AOC 协议（HTTP/gRPC）进行状态同步与任务接力
- **记忆/上下文**：与 AOC MemoryStore 模式对齐

#### 原则三：每个节点独立可运行

每个设备 Agent 都是**独立完整的 Agent**，不依赖中心节点也能完成自身核心功能：

- aos-agent 离线时也能执行车辆诊断、CLI 终端、本地交互
- AOC Hub 提供的是增强能力（复杂编排、跨设备接力），不是生存依赖
- 每个节点有自己独立的 Agent 循环、工具执行、本地存储

#### 原则四：能力互补，不做重复

| 能力域 | 归属 |
|--------|------|
| 车辆 API（速度/续航/空调/导航） | aos-agent 独占 |
| 驾驶安全语音交互 | aos-agent 独占 |
| EV 电源周期管理 | aos-agent 独占 |
| OEM 探测 → 适配 → 降级 | aos-agent 独占 |
| 24/7 健康传感（心率/血氧/运动） | watch-agent 独占 |
| 抬手即时交互 / 振动触觉 | watch-agent 独占 |
| LLM 推理路由 / 复杂任务编排 | AOC 核心能力 |
| 多设备状态同步 / 跨设备接力 | AOC 核心能力 |
| 桌面 Studio / IDE 集成 | AOC 核心能力 |

---

## 生态协作流程

### 跨设备任务接力（示例：驾驶场景）

```
1. 手表检测到用户上车 → 通过 AOC 协议通知车机
2. aos-agent 接管交互 → 切换到驾驶模式（语音优先、简化 UI）
3. 用户通过 aos-agent 语音设定导航目的地
4. aos-agent 调用 Car API 获取续航 → 判断是否需要中途充电
5. 如需充电 → AOC 接力到手机 Agent 搜索沿途充电站
6. 结果回传 aos-agent → 语音播报建议 → 导航确认
```

### 身份统一性

```
每次交互用户面对的都是同一人格，不论在哪个设备上：

车机："剩余续航 120 公里"
手表：（振动提醒）"你常去的充电站在前方 3 公里"
桌面：同一身份登录 AOC Studio，可查看车辆历史状态报告
```

---

## 对 aos-agent 开发的具体约束

所有 Batch 1~3 的开发计划必须遵循以下约束：

1. **Agent 核心参考 OpenClaw/Hermes 设计**，不自研基础 Agent 架构
2. **跨设备协议对接 AOC**，不自研 identity / capability / 通信协议
3. **保证离线独立运行能力**，AOC Hub 不可用时核心功能不受影响
4. **车载差异化是 aos-agent 的唯一价值**，车辆专属能力投入优先级 > Agent 通用能力打磨

---

> 更新时间：2026-07-14（2026-09-06 对齐修订：补注 AOC 活动中心为可迁移角色、故障可按顺位接管，对齐 AgentOpenConnect `vision-rule.md` 2026-08-09 版）
>
> 相关项目：
> - AOC (AgentOpenConnect)：`D:\code\project\AgentOpenConnect`
> - aoc-watch-agent：`E:\code\watch\aoc-watch-agent`
