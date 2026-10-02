# 开发计划目录

本目录集中存放本项目当前在做或近期要做的开发计划文档。`.agent-rules/mechanisms/` 是项目稳定机制文档，本目录是流动的实施计划，两者职责分离。

## 1. 用途

- 记录"为了实现某个能力，要改哪些文件和模块、按什么顺序、改完算什么样"。
- 与 `.agent-rules/mechanisms/` 下的机制文档一一对应：机制文档描述"系统是什么样"，本目录文档描述"本期要把系统改成什么样"。
- 小任务优先使用 issue tracker；需要跨模块协调、机制沉淀或接力时写入本目录。
- 进行中、已完成的开发计划都先留在这里，归档另说（见 4. 归档）。

## 2. 文件命名

| 来源机制文档 | 配对开发计划 |
| --- | --- |
| `.agent-rules/mechanisms/<topic>.md` | `.agent-rules/dev-plans/<topic>-plan.md` |

一份机制文档可以对应多份开发计划（不同阶段、不同方案），命名追加阶段后缀，例如 `<topic>-plan-phase2.md`。

## 3. 文档结构约定

每份开发计划文档应包含至少：

1. **统一愿景对齐**（顶部标注）—— 必须说明本计划是否与 `../docs/unified-ecosystem-vision.md` 一致，如有偏离需说明理由。
2. **配套机制文档链接**（顶部一行）。
2. **当前阶段**：选定的方案 / 范围。
3. **改动范围**：模块 + 文件清单。
4. **步骤**：每一步给出改动文件、关键代码片段、验收方式。
5. **验收清单**：可勾选的最终冒烟清单。
6. **不在本期做的事**：与下一阶段的边界。
7. **进度**：步骤当前状态。

不要把机制约束、协议契约、风险条款写到这里——这些放在配套机制文档里，本文件只引用。

## 4. 归档

开发计划完成后：

- 把"进度"区所有步骤标为完成 + 日期。
- 在文档末尾追加 `## 归档` 小节：完成日期、最终冒烟结果、遗留问题、回写到机制文档的哪些更新。
- 文件本体保留在 `dev-plans/` 至少到下个里程碑结束；之后可按需移到 `dev-plans/archived/`，保留历史。

## 5. 当前在册

- [roadmap.md](./roadmap.md) — 三阶段开发路线图（Phase 0-3）
- [mvp-core-plan.md](./mvp-core-plan.md) — 最小 MVP：Agent 执行 + Skill + AOC 接入（当前最高优先级专项）
- [framework-foundation-plan.md](./framework-foundation-plan.md) — 批次 0：框架搭建计划
- [basic-capabilities-plan.md](./basic-capabilities-plan.md) — 批次 1：基础功能计划
- [core-agent-plan.md](./core-agent-plan.md) — 批次 2：核心功能计划
- [extended-advanced-plan.md](./extended-advanced-plan.md) — 批次 3：扩展 / 复杂功能计划
- [terminal-plan.md](./terminal-plan.md) — 终端模块专项计划（从属 Batch 2，不再作为当前优先批次单独提前执行）
- [ui-design-system-plan.md](./ui-design-system-plan.md) — UI 设计系统落地与多语言（Batch 1 共用前置）
- [ui-design-system-plan-phase2.md](./ui-design-system-plan-phase2.md) — UI 样式重构 v2.0 琥珀仪表（已被 v3.0/v4.0 接替，留档）
- [ui-design-system-plan-phase3.md](./ui-design-system-plan-phase3.md) — UI 样式从零重构 v3.0 竞速荧光（已被 v4.0 接替，留档）
- [ui-design-system-plan-phase4.md](./ui-design-system-plan-phase4.md) — UI 样式 v4.0 深空蓝白暗色单主题（已被 v5.0 接替，留档）
- [ui-design-system-plan-phase5.md](./ui-design-system-plan-phase5.md) — UI v5.0 双主题：日间蓝白浅色（默认）+ 夜间深色，可切换（当前生效）
- [voice-interaction-plan.md](./voice-interaction-plan.md) — 语音交互：ASR/TTS/语音指令（Phase A **已交付 2026-10-02**）+ 连续对话/离线唤醒（Phase B）+ 车机深度（Phase C），驾驶场景优先

### 子目录

- [batch1/](./batch1/README.md) — Batch 1 各 Step 拆解出的子计划（父计划为复杂功能，禁止直接实现）
- [mvp/](./mvp/README.md) — MVP 专项各 Step 拆解出的子计划（父计划为 [mvp-core-plan.md](./mvp-core-plan.md)）

## 6. 已归档

> 初始化时为空。归档后从"当前在册"移到此处。
