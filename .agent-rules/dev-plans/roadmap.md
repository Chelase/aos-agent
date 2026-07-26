# 开发路线图

> 配套机制文档：`.agent-rules/mechanisms/architecture-overview.md`、`.agent-rules/mechanisms/requirements-analysis.md`、`.agent-rules/mechanisms/engineer-mode-architecture.md`、`.agent-rules/mechanisms/agent-capabilities.md`

## 当前阶段

当前路线不再采用“一个大计划覆盖全部实现”的推进方式，而是改为：

1. **先搭框架**
2. **再做基础功能**
3. **再做核心功能**
4. **最后做扩展 / 复杂功能**

每个批次对应一份独立开发计划文档，只推进当前批次，不提前实现后续批次内容。

> 术语说明：路线图中的 `Batch 0~3` 表示**实现批次优先级**；机制文档中的 `P0 / P1` 表示**产品需求优先级**。两者相关，但不等价。

---

## 批次划分总览

| 批次 | 优先级 | 目标 | 对应计划 |
|---|---|---|---|
| Batch 0 | P0 | 跑通 AAOS 基础骨架与工程结构 | [framework-foundation-plan.md](./framework-foundation-plan.md) |
| Batch 1 | P0 | 建立最小可用基础功能闭环 | [basic-capabilities-plan.md](./basic-capabilities-plan.md) |
| Batch 2 | P1 | 形成真正可用的车载 Agent 核心体验 | [core-agent-plan.md](./core-agent-plan.md) |
| Batch 3 | P2 | 完成扩展能力、复杂能力与系统级深化 | [extended-advanced-plan.md](./extended-advanced-plan.md) |

---

## Batch 0: 框架搭建

**目标：** 先把项目从 Android Studio 空壳改造成可运行、可验证、可继续迭代的 AAOS 工程骨架。

**范围：**
- Compose 基础依赖与主题系统
- `MainActivity` 与基础导航壳
- `BootReceiver` / `AgentForegroundService` 骨架
- 基础系统信息读取
- 工程目录与模块边界落地
- Automotive 模拟器验证

**不在本批次做：**
- CLI 终端
- 语音对话
- 桌面宠物
- Agent 推理引擎
- Car API 全量扫描
- 第三方 App 管理

---

## Batch 1: 基础功能

**目标：** 在框架稳定后，形成“能持续运行、能感知系统、能进入工程师模式”的最小闭环。

**范围：**
- 开机自启与前台服务稳定化
- 系统感知面板（Android 版本 / 厂商 / 网络 / 电源等）
- 工程师模式入口与只读诊断面板
- 基础电源状态处理
- 真机自检报告最小版

**不在本批次做：**
- 真实 shell 终端
- 语音交互
- 多步 Agent 工具调用
- 第三方应用市场

---

## Batch 2: 核心功能

**目标：** 建立 AOS-Agent 的核心产品价值，而不是只停留在系统壳。

**范围：**
- CLI 终端（真实 shell）
- Agent 对话主循环
- 工具系统 / 上下文管理 / 任务规划
- 连续语音对话
- 工程师模式增强（Car API 扫描、自检报告增强）
- 多厂商探测 -> 适配 -> 降级策略初版

**不在本批次做：**
- 本地 LLM
- Live2D
- 社区插件市场
- 系统级预装

---

## Batch 3: 扩展 / 复杂功能

**目标：** 补齐高复杂度、重集成、重性能或系统权限相关能力。

**范围：**
- Live2D / 高质量宠物表现
- 本地 LLM 集成
- 第三方应用上传 / 搜索 / 安装管理
- Agent 插件系统 / MCP / 扩展工具链
- 深度 Car API 集成（导航 / 空调 / 车窗等）
- 系统级 App 化 / 平台签名 / AOSP 集成
- 长稳压测与安全审查

---

## 推进规则

1. **只实现当前批次。** 后续批次能力只写计划，不提前编码。
2. **每个批次先有计划文档，再开始实现。**
3. **每完成一个批次，回写机制文档与路线图。**
4. **性能优化贯穿所有批次，不作为单独延期理由。**

---

## 进度

- Batch 0（框架搭建）: ✅ 已完成（2026-07-26）
- Batch 1（基础功能）: 🚧 进行中 — 共用前置「UI 设计系统与多语言」已完成，Step 1~5 未开始
- Batch 2（核心功能）: ⏳ 待开始
- Batch 3（扩展 / 复杂功能）: ⏳ 待开始

> 批次外交付：[ui-design-system-plan.md](./ui-design-system-plan.md) 是 Batch 1 各 Step 的共用前置
> （设计 token、共享组件库、中英双语基建），不占用 Batch 1 的 Step 编号。



