# 核心功能计划

> ⚠️ **复杂功能 — 禁止直接实现**
>
> 本批次属于复杂功能，Agent 读取本计划文档后，不得直接按全文一次性实现。
>
> **必须按以下流程执行：**
> 1. 从「步骤」中选择一个 Step
> 2. 将该 Step 进一步拆解为独立的子计划文档，写入 `dev-plans/archived/` 或新子目录
> 3. 子计划文档需包含：改动文件清单、关键实现方案、分步验收条件
> 4. 子计划就绪后再开始编码实现
> 5. 完成后回到本计划，选择下一个 Step，重复上述流程
>
> **禁止：** 一个 Agent 调用直接实现多个 Step。

> 配套机制文档：`.agent-rules/mechanisms/architecture-overview.md`、`.agent-rules/mechanisms/agent-capabilities.md`、`.agent-rules/mechanisms/terminal-architecture.md`

## 当前阶段

本计划定义 AOS-Agent 真正的核心价值功能。前提是框架搭建和基础功能批次已经完成。

本批次开始引入终端、Agent、语音和增强工程师模式，但仍不进入系统级预装和本地 LLM。

本批次只实现**内置核心能力**：终端、Agent 主循环、内置工具系统、多步规划、连续语音。

插件生态、MCP 风格运行时扩展、第三方应用市场等“可扩展生态能力”统一延后到扩展 / 复杂功能批次。

## 改动范围

### 模块

- `terminal/`
- `core/engine/`
- `core/tools/`
- `ui/chat/`
- `audio/` 或 `core/voice/`
- `ui/engineer/`
- `system/carapi/`

## 步骤

### Step 1. 接入真实 CLI 终端

**内容：**
- 基于 `terminal-plan.md` 推进真实 shell 终端
- 完成终端会话、渲染、输入桥接

**验收：**
- 可执行 shell 命令并回显结果

### Step 2. 建立 Agent 对话主循环

**内容：**
- 最小 LLM Router
- ContextManager
- AgentEngine 单轮推理

**验收：**
- 文本输入 -> Agent 回复链路打通

### Step 3. 建立工具系统与多步任务规划

**内容：**
- ToolSystem
- Tool 注册 / 调度 / 超时 / 错误处理
- 基础多步规划骨架

**验收：**
- Agent 可调用至少 2 个工具并整合结果

### Step 4. 建立连续语音对话能力

**内容：**
- 唤醒 -> ASR -> Agent -> TTS 主链路
- 连续对话状态管理

**验收：**
- 驾驶场景下可连续对话，无需每轮重新进入文本模式

### Step 5. 增强工程师模式

**内容：**
- Car API 扫描器
- 系统服务检查器
- 增强自检报告

**验收：**
- 可生成更完整的兼容性诊断报告

### Step 6. 建立多厂商探测 -> 适配 -> 降级初版

**内容：**
- OEM Detector 初版
- 差异能力探测
- 对不可用 API 做静默降级

**验收：**
- 在未知车型/模拟器上不崩溃

## 验收清单

- [x] 真实 shell 终端可用
- [ ] Agent 文本对话可用
- [ ] 至少两个工具可被 Agent 调用
- [ ] 连续语音对话可用
- [ ] 工程师模式支持增强诊断
- [ ] 多厂商差异有基础降级路径

## 不在本期做的事

- 本地 LLM
- Live2D
- 社区插件市场
- 系统级预装 / 平台签名

## 进度

- [x] Step 1. 接入真实 CLI 终端 — 2026-10-02（Phase 1，见 [terminal-plan.md](./terminal-plan.md) 归档；Step 2/3 已提前并入 MVP）
- [ ] Step 2. 建立 Agent 对话主循环
- [ ] Step 3. 建立工具系统与多步任务规划
- [ ] Step 4. 建立连续语音对话能力
- [ ] Step 5. 增强工程师模式
- [ ] Step 6. 建立多厂商探测 -> 适配 -> 降级初版
