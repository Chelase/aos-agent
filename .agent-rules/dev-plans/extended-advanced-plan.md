# 扩展与复杂功能计划

> 配套机制文档：`.agent-rules/mechanisms/architecture-overview.md`、`.agent-rules/mechanisms/agent-capabilities.md`、`.agent-rules/mechanisms/engineer-mode-architecture.md`、`.agent-rules/mechanisms/third-party-apps.md`

## 当前阶段

本计划只覆盖高复杂度、重性能、重系统权限或重生态能力，不应该在前面批次提前实施。

## 改动范围

### 模块

- `ui/pet/`
- `core/engine/`
- `core/plugins/`
- `system/carapi/`
- `app-management/`
- AOSP / 平台集成相关目录

## 步骤

### Step 1. 升级宠物表现能力

**内容：**
- 从简单 Compose Canvas 角色升级到更复杂表现
- 评估 Live2D 或轻量替代方案

### Step 2. 引入本地 LLM

**内容：**
- llama.cpp JNI / 模型加载 / 离线推理策略
- 云端/本地模型路由

### Step 3. 建立第三方应用管理

**内容：**
- APK 上传
- 本地应用安装管理
- 网络应用搜索与基础索引

### Step 4. 建立 Agent 插件 / MCP 扩展体系

**内容：**
- 插件注册 / 卸载 / 沙箱
- MCP 风格工具接入

### Step 5. 深化 Car API 集成

**内容：**
- 导航 / 空调 / 车窗 / 更多车辆控制能力
- 真车兼容性回归

### Step 6. 系统级 App 化与 AOSP 集成

**内容：**
- Android.bp
- 平台签名
- privapp allowlist
- 更早启动路径与更多系统权限

### Step 7. 长稳压测与安全审查

**内容：**
- 7 天连续运行稳定性
- 内存泄漏 / Crash / Watchdog 风险
- 权限最小化与数据安全复盘

## 验收清单

- [ ] 扩展宠物表现能力稳定
- [ ] 本地 LLM 可运行
- [ ] 第三方应用管理闭环可用
- [ ] 插件 / MCP 扩展能力可用
- [ ] 深度 Car API 集成可用
- [ ] 系统级 App 化路径清晰并验证
- [ ] 长稳和安全审查通过

## 不在本期做的事

- 超前重构前面批次已经稳定的基础框架
- 为了炫技增加没有优先级的复杂能力

## 进度

- [ ] Step 1. 升级宠物表现能力
- [ ] Step 2. 引入本地 LLM
- [ ] Step 3. 建立第三方应用管理
- [ ] Step 4. 建立 Agent 插件 / MCP 扩展体系
- [ ] Step 5. 深化 Car API 集成
- [ ] Step 6. 系统级 App 化与 AOSP 集成
- [ ] Step 7. 长稳压测与安全审查
