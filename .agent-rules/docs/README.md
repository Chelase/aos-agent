# 参考资料目录（docs/）

本目录存放参考资料、外部规范、技术学习笔记等辅助文档，供 Agent 在执行任务时查阅。

## 判据

写外部参考资料、技术规范摘要、学习笔记、工具使用指南等辅助性文档，放本目录。
若是"必须/不要"的执行约束，放 `../rules/`；若是"系统现在如何工作"，放 `../mechanisms/`；若是"本期实施计划"，放 `../dev-plans/`。

## 约定

- 文件名用语义化短横线命名，例如 `react-patterns.md`、`api-design-guide.md`。
- 每份参考资料注明来源（URL、书籍、文档版本等）和采集日期。
- 参考资料是辅助性文档，不作为规则或机制的权威来源——规则以 `rules/` 为准，机制以 `mechanisms/` 为准。
- 文档过多时按主题拆分子目录，每个子目录保留自己的 `README.md` 索引。

## 当前参考资料

- `unified-ecosystem-vision.md` — **统一生态愿景（开发约束）**：aos-agent、AOC、aoc-watch-agent 三大项目的定位与协作关系。**所有开发计划必须与其对齐，不得偏离。**
- [android-agent-projects-survey.md](./android-agent-projects-survey.md) — 手机端开源 Agent 项目调研（OpenMinis/Operit/RikkaHub：License 边界、可借鉴设计点与 MVP 修订映射）
- [car-agent-ecosystem-survey.md](./car-agent-ecosystem-survey.md) — 车载 Agent / 星环 OS 调研（orangefplus/car-agent、wwsa666/caragent、知乎座舱形态、HaloOS：分层映射与 MVP 加速点）
- [car-agent-market-survey-2026-09.md](./car-agent-market-survey-2026-09.md) — 车机 Agent 成熟度调研（Gemini/高通 Claw/国内 OEM 量产能力/华为技能平台/CarToolForge 等开源实测：谁已成熟、第三方可不可接入、缺口在哪）
