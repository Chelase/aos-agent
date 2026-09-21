# MVP 专项子计划目录

父计划：[../mvp-core-plan.md](../mvp-core-plan.md)。该计划属复杂功能，**禁止按全文一次性实现**；每个 Step 开工前必须先在父计划「步骤」中选定 Step，再在本目录写出子计划（改动文件清单 + 关键实现方案 + 分步验收条件），子计划就绪才开始编码。

## 命名

`step<N>-<topic>-plan.md`，N 对应父计划 Step 编号。一个 Step 一个文件；同一 Step 拆多份时用 `step<N>a>-<topic>-plan.md` 并在本索引标注。

## 文档清单

| 文件 | 对应父计划 Step | 状态 |
|---|---|---|
| [step3-toolsystem-plan.md](./step3-toolsystem-plan.md) | Step 3 ToolSystem 与首批工具 | 已就绪，未开工（2026-09-21 按外部调研调整） |

Step 1 / 2 / 4 / 5 的子计划在各自开工前创建。Step 1 与 Step 2 相互独立可并行。

## 依赖顺序

```
Step 1 (AOC Entry) ─┐
Step 2 (引擎无头循环) ─┬─→ Step 3 (ToolSystem) ─→ Step 4 (Skill) ─→ Step 5 (AOC 收口)
```

## 约定

- 子计划顶部必须有「统一愿景对齐」段，逐条对照 `../../docs/unified-ecosystem-vision.md` 四原则。
- 子计划只写"本期改成什么样"；协议契约、权限边界、风险条款回写 `../../mechanisms/`，此处引用不复制。
- 完成后勾进度、写完成日期，并把父计划对应 Step 与 `../roadmap.md` 一并回写。
