# 需求文档更新计划

## TL;DR
根据用户反馈更新三种需求：
1. 开机自启 + 后台持续运行 — 提升为硬性要求
2. 性能优化 — 从独立任务改为贯穿所有任务的设计基线
3. 工程师模式 — 新增功能：车机 API/服务/系统版本调试诊断面板
4. 连续语音对话 — 驾驶场景免手动操作，语音唤醒 + 持续对话

## 修改文件清单

| 文件 | 改动类型 |
|------|---------|
| `.agent-rules/mechanisms/requirements-analysis.md` | 更新结论、涉及对象、运行链路、关键约束 |
| `.agent-rules/mechanisms/architecture-overview.md` | 新增工程师模式模块、性能基线约束 |
| `.agent-rules/dev-plans/roadmap.md` | 新增工程师模式任务、重构性能优化 |
| `.agent-rules/mechanisms/engineer-mode-architecture.md` | **新增** — 工程师模式机制文档 |

## 具体改动

### 1. requirements-analysis.md

- **§结论**：六大核心需求表（新增工程师模式），性能优化改为"贯穿所有任务"
- **§涉及对象**：新增 `EngineerMode`、`Performance Baseline`
- **§运行链路**：新增"后台持续运行"链路（Crash 自恢复、白名单保活）
- **§系统感知**：新增"全量 Car API 枚举"说明
- **§关键约束**：新增约束 5-8

### 2. architecture-overview.md

- **§涉及对象**：新增 `ui/engineer/` 模块
- **§模块结构**：在 `ui/` 下新增 `engineer/` 子目录
- **§关键约束**：新增性能基线约束（启动 <2s、内存 <200MB、帧率 >30fps）

### 3. roadmap.md

- **Phase 1**：新增 `1.9 工程师模式` 任务，移除 `1.8 性能优化`（改为跨阶段约束）
- **§性能优化**：新增独立说明段，规定每个任务的性能基线
- **验收清单**：新增工程师模式验收项

### 4. engineer-mode-architecture.md（新增）

七段结构：

| 段落 | 内容 |
|------|------|
| 结论 | 车机调试诊断面板，查看所有 Car API、系统服务、版本信息 |
| 涉及对象 | EngineerMode Activity / ViewModel / Car API Scanner / SystemService Inspector |
| 运行链路 | 隐藏入口(5次点击Logo) → 密码验证 → 诊断面板 → 实时数据扫描 |
| 数据分类 | Car API 列表/实时值、系统属性、传感器、日志 |
| 关键约束 | 仅调试模式可用，Release 编译需移除或混淆入口 |
| 实现阶段 | Phase 1 MVP |

### 5. engineer-mode-architecture.md 补充（语音模块）

在涉及对象中新增：

```
| UI/Audio | VoiceManager | 语音唤醒 + 连续对话管理 |
| Core | ASR (Speech-to-Text) | 语音识别引擎 |
| Core | TTS (Text-to-Speech) | 语音合成引擎 |
```

在运行链路中新增：

```
[语音唤醒 "Hey AOS"]
    │
    ▼
[VoiceManager 激活]
    │ 播放提示音 (可选)
    │
    ├──► [ASR] ── 实时语音识别 → 文本
    │        │
    │        ▼
    ├──► [AgentEngine] ── 处理语义 → 生成回复
    │        │
    │        ▼
    ├──► [TTS] ── 合成语音 → 播放
    │        │
    │        ▼
    └──► [持续监听] ── 等待下一句（无需再次唤醒）
           │ 超时 10 秒无语音 → 关闭对话
           │ 用户说"退下"/"结束" → 关闭
```

### 6. requirements-analysis.md 补充

在"涉及对象"表新增：

```
| Audio | VoiceManager | 语音唤醒 + 连续对话 |
| Core | ASR/TTS Engine | 语音识别与合成 |
```

在"关键约束"新增：

```
8. 语音对话必须在驾驶场景优先：唤醒词延迟 < 1 秒，TTS 响应 < 2 秒。
9. 连续对话超时时间可配置（默认 10 秒）。
10. 语音对话与 UI 操作必须同步——用户在屏幕上打字时闭麦。
```

### 7. roadmap.md 补充

Phase 1 任务表新增：

```
| **1.9 语音对话** | 唤醒词 + ASR + TTS + 连续对话，驾驶场景免手动操作 |
```

验收清单新增：

```
- [ ] 说"Hey AOS"可唤醒 Agent 并开始对话
- [ ] 连续对话无需重复唤醒
- [ ] TTS 播报响应自然无卡顿
```
