# 语音 B1：连续对话回路（静默 / 超时 / 打断 / 焦点挂起恢复）

> **统一愿景对齐**：与 `../../docs/unified-ecosystem-vision.md` 一致。音频只在车机本地进识别管道，
> 只有识别出的文本进 Agent 引擎；本 Step 不新增任何上行音频通路，不改变 AOC 单一人格出口。
>
> 父计划：[../voice-interaction-plan.md](../voice-interaction-plan.md) Phase B.1
> 配套机制文档：`../../mechanisms/voice-interaction.md`
> 前序：Phase A 已交付单轮 ASR + TTS + 本地语音指令（`ui/voice/VoiceController.kt` 状态机）

## 当前状态

Phase A 的回路是"一轮一问"：`SPEAKING` 播完若开了连续对话就直接 `startListening()`，中间没有任何静默；
聆听没有超时，识别器一直挂着；打断只有"点按钮打断"；焦点只在播报侧成对申请/放弃，
媒体或导航抢焦点时回路不会挂起。父计划验收要求这四点都要反过来。

## 本 Step 范围与四条决策

| # | 决策 | 理由 |
|---|---|---|
| 1 | 定时能力从外部注入：`VoiceController` 增 `scope: CoroutineScope` 参数 | 状态机要延迟/超时又不碰 Android；测试传 `TestScope.backgroundScope` 用虚拟时间推进，不靠 sleep |
| 2 | **焦点所有权上移到会话级** `VoiceFocusHandle`，`AndroidSpeechSynthesizer` 不再自己申请焦点 | 同一 App 两个焦点请求会互相踢（播报时把聆听挤成 LOSS），且拿不到回调就谈不上"挂起/恢复"。会话期持有一份 `GAIN_TRANSIENT_MAY_DUCK`，被更高优先级抢走时由该 listener 收到 LOSS → 回路挂起 |
| 3 | 人声打断（barge-in）默认**关**，设置页给开关并写明原因 | 无硬件回声消除的设备上，TTS 自己的声音会被识别成人声，越打断越糟。模拟器没有麦克风，这条无法本地验，必须留开关而不是硬开 |
| 4 | 新增可见阶段 `WAITING`（播报完的 1s 静默期） | 静默期若沿用 SPEAKING 灯，界面在说谎；沿用 IDLE 灯又像断线 |

## 改动文件

| 文件 | 动作 |
|---|---|
| `core/voice/SpeechChannels.kt` | 新增 `VoiceFocusHandle` 接口（`claim(onLost,onGained)` / `release()`） |
| `system/voice/AndroidVoiceFocus.kt` | 新增 — `AudioManager` 焦点封装，单一 `AudioFocusRequest` 复用 |
| `system/voice/AndroidSpeechSynthesizer.kt` | 删除内部焦点申请/放弃（改由会话持有） |
| `ui/voice/VoiceController.kt` | 静默 1s → 聆听；聆听 8s 无语音 → Idle；SPEAKING 探针打断；焦点挂起/恢复；释放时交还焦点 |
| `ui/voice/VoiceState.kt` | 新增 `VoicePhase.WAITING` + `suspendedByFocus` 字段 |
| `ui/voice/VoiceControls.kt` | `WAITING` 的灯色与文案 |
| `data/store/VoiceSettingsStore.kt` | 新增 `bargeInEnabled` 开关（默认 false） |
| `ui/settings/SettingsScreen.kt` | 语音卡加第三张开关（说明需要设备回声消除） |
| `MainActivity.kt` / `ChatConsole` | 注入 `lifecycleScope` 与 `AndroidVoiceFocus` |
| `res/values/strings.xml`、`values-en/strings.xml` | 静默/挂起/超时/打断开关文案成对新增 |
| `app/src/test/.../ui/voice/VoiceControllerTest.kt` | 扩展：静默、超时、打断、焦点挂起恢复 |

## 步骤

### B1.1 定时注入与静默期

`afterSpeaking()` → `continuous` 时置 `WAITING` 并 `scope.launch { delay(1_000); if (仍 WAITING) startListening() }`。
超时 8s：进入 `LISTENING` 起 `delay(8_000)`，到点仍是 LISTENING 且 `partial` 为空 → `cancel()` 识别器 +
回 `IDLE` + `voice_notice_timeout`；收到非空 partial 即取消该计时（人在说就不催）。

**验收：** `runTest` 里 `advanceTimeBy(1_000)` 才起第二轮聆听；`advanceTimeBy(8_000)` 后回 IDLE 带提示；
中途灌 partial 则不超时。

### B1.2 焦点挂起 / 恢复

会话第一次开麦 `focus.claim(onLost = { suspendForFocus() }, onGained = { resumeFromFocus() })`，
回 `IDLE`（含超时、失败、手动停止）即 `release()`。挂起：`cancel()` 识别器 + `stop()` 播报 + `suspendedByFocus=true`
并记住"这是连续回路的一环"；恢复：`WAITING → LISTENING` 继续，非回路则直接 `IDLE`。
挂起期间界面必须给状态行文案，不能静默消失。

**验收：** 单测里直接调 `onFocusLost()/onFocusGained()`（或假 handle 回调）断言阶段与 `suspendedByFocus`；
恢复后 `transcriber.startCount` 递增。

### B1.3 人声打断

`continuous && bargeInEnabled` 时，进入 `SPEAKING` 同时起一路探针聆听（只认 partial，`onFinal`/`onError` 丢弃）。
首个非空 partial → `synthesizer.stop()` + 探针 `cancel()` + `startListening()` 重新起一轮干净聆听。
打断后不追回被吞掉的半句（识别器实例已作废），界面提示"已打断"。

**验收：** 假通道灌 partial → `stopCount` 与 `startCount` 各 +1，阶段落 `LISTENING`；开关关时灌 partial 无反应。

## 验收清单

- [ ] `VoiceControllerTest` 全绿（新增 ≥8 例，覆盖静默/超时/打断/挂起恢复/焦点释放）
- [ ] `:app:testDebugUnitTest` + `:app:assembleDebug` BUILD SUCCESSFUL
- [ ] 模拟器：开连续对话点麦 → 状态灯依次 WAITING→LISTENING，8s 不说话自动回 IDLE 并显示超时文案（可验，识别器自身超时也会走同一路径）
- [ ] 媒体抢占焦点自动挂起、回来自动恢复 —— **需真实音频焦点竞争环境**，模拟器只能验到"申请/放弃成对、无泄漏"
- [ ] 人声打断 —— **模拟器不可验**（无麦克风音频），交付为"开关 + 已接通代码"
- [ ] 中英成对文案；禁用态与挂起态都有原因说明

## 进度

- [ ] B1.1 定时注入与静默期 + 超时
- [ ] B1.2 焦点挂起 / 恢复
- [ ] B1.3 人声打断（默认关）

## 不在本 Step 做的事

| 项目 | 归属 |
|---|---|
| 唤醒词常听（24/7 麦克风） | B2，且必须离线 KWS，不走系统识别器 |
| 行驶中全屏遮罩 | B3 |
| 多说话人 / 音区 | Phase C |
