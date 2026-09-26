# 技能路由：界面 UI 与工程化架构

任务命中"界面/UI"或"工程化架构/重构"时，必须按下表调用指定技能，不得凭手感直接改。技能是用户级资源（本机 `.agents/skills/`），不在仓库内。

## 1. 界面与 UI：三个技能搭配使用

| 技能 | 负责什么 | 什么时候用 |
|---|---|---|
| `ui-ux-pro-max` | 选型与取数：风格、配色、字体配对、UX 反模式、图表建议 | 动手前第一步。`--design-system` 出方案，再按 `--domain` / `--stack` 补细节 |
| `frontend-design` | 美学方向与反模板化：定调、避免"AI 味"通用视觉 | 定方向时。给出可被评审的明确 POV，而不是"再美化一点" |
| `impeccable` | 工序与质量底线：`shape` 立项、`critique` / `audit` 评审、`polish` / `harden` 交付前、`adapt` 多尺寸 | 全流程。改动 UI 文件后的检测交给它的 hook |

推荐工序：`ui-ux-pro-max --design-system` 取候选 → `frontend-design` 定方向 → 编码 → `impeccable` 的 `critique` / `polish` 收尾。三者不是三选一；只调其中一个视为未遵守本规则，除非任务只涉及其中一个环节（例如纯文案澄清走 `impeccable clarify`）。

### 本项目实测参数

- 栈一律显式指定 `--stack jetpack-compose`。脚本默认值是 `html-tailwind`，忘了写就会拿到 Tailwind 建议。
- 脚本入口 `python .agents/skills/ui-ux-pro-max/scripts/search.py`（本机已验证 Python 3.14 可跑，`jetpack-compose` 库有数据）。
- 已有设计系统位置：`.agent-rules/docs/原型/design-system/aosagent/`（`MASTER.md` + `pages/`，`pages/` 为页面级覆盖，当前为空）。要延续既有风格先读它，不要另起炉灶。

## 2. 禁止让技能生成第二套真来源

这些技能会各自寻找/创建自己的上下文文件，本仓库根目录**没有** `PRODUCT.md` / `DESIGN.md` / `CONTEXT.md` / `docs/adr/`。需要时按下表映射读取，不要新建同义文件：

| 技能想要的 | 本仓库的真来源 |
|---|---|
| PRODUCT.md（产品与用户） | `../mechanisms/requirements-analysis.md`、`../docs/unified-ecosystem-vision.md` |
| DESIGN.md（视觉世界） | `.agent-rules/docs/原型/design-system/aosagent/MASTER.md`、`.agent-rules/docs/原型/design.md`、`../mechanisms/ui-design-system.md` |
| CONTEXT.md（领域词表） | `../mechanisms/` 各文档中的对象与字段命名 |
| ADR（架构决策记录） | 决策写进 `../dev-plans/`，稳定事实写进 `../mechanisms/` |

`impeccable init` / `ui-ux-pro-max --persist` 若确实要落盘，必须写到上表对应位置；写到仓库根视为违规。`improve-codebase-architecture` 的报告按要求输出到系统临时目录，不进仓库。

## 3. 工程化架构：`improve-codebase-architecture`

所有架构层面的调整（模块拆分、接口重划、可测性改造、跨模块链路变更）走这个技能，并按其流程：先探索找"浅模块"→ 出候选清单（含 before/after）→ 用户选定后才设计接口。不要在候选阶段直接给重构代码。

架构参考系固定为四个成熟 Agent 工具：**Hermes、OpenClaw、Claude Code、Pi**。要求：

1. 每个架构决策必须写明"参考谁、借什么点"，与 `../mechanisms/agent-capabilities.md` 的参考表对齐；无参考系支撑的自研设计要在计划文档里说明为什么非自研不可。
2. 借的是**接口形态与工程模式**，不是复制代码；外部仓库的 License 边界见 `../docs/` 下调研文档。
3. 术语用该技能自己的词表（module / interface / implementation / depth / seam / adapter / leverage / locality），不要漂移到"组件/服务/边界"。

## 4. 车机特有硬约束（优先于任何技能的美学建议）

1. 驾驶安全优先：`frontend-design` 鼓励"极端方向"，但座舱界面受 CarUxRestrictions 与分心约束，动效强度、信息密度、可点击尺寸一律让位于行车中可读可用。技能的"更大胆"与本项目冲突时，本项目赢。
2. 视觉值必须来自 `ui/theme/` 的 token（见 `../mechanisms/ui-design-system.md`），不接受技能输出的裸色值/硬编码字号。
3. 文案走 Android 资源、支持中英双语，技能生成的示例文案不得直接写进布局代码。
4. 验收要在真实车机形态下看：AAOS 模拟器尺寸 + `prefers-reduced-motion`，不接受只在桌面浏览器截图就交付。

## 5. 技能不可用时

宿主没有该技能或脚本跑不起来：先说明"技能不可用"，再按本文件的工序手工执行同等检查，不得跳过工序直接出图。

> 更新时间：2026-09-26
