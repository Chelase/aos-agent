# Batch 1 Step 级子计划

本目录存放 [../basic-capabilities-plan.md](../basic-capabilities-plan.md) 各 Step 拆解出的子计划。

## 为什么有这一层

`basic-capabilities-plan.md` 标记为复杂功能，禁止按全文一次性实现。每个 Step 必须先拆成
独立子计划（含改动文件清单、实现方案、分步验收），子计划就绪后才开始编码。本目录就是这些
子计划的落地位置。

## 读取顺序

1. 先读 `../basic-capabilities-plan.md` 确认 Step 边界与批次「不在本期做的事」
2. 再读 `../../mechanisms/` 下对应机制文档
3. 最后读本目录下目标 Step 的子计划

## 当前子计划

- [step1-foreground-service-plan.md](./step1-foreground-service-plan.md) — Step 1：稳定开机自启与前台服务
- [step2-system-panel-plan.md](./step2-system-panel-plan.md) — Step 2：建立基础系统感知面板

## 命名约定

`step<N>-<topic>-plan.md`，N 与 `basic-capabilities-plan.md` 的 Step 编号一致。
