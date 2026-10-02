# 语音交互机制

## 结论

对话页用系统语音服务做识别与播报，识别文本先过一层本地指令解析：命中固定指令就地执行、不进模型，
其余文本与键盘输入走同一条发送通路进 Agent 引擎。唤醒词不在本机制内（属语音 Phase B）。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| UI | `ui/chat/ChatScreen.kt` | 输入行左侧麦克风按钮 + 按钮上方状态行；部分识别结果直接落到输入框 |
| UI | `ui/voice/VoiceMicButton` / `VoiceStatusLine`（`VoiceControls.kt`） | 灯语化的大麦克风控件（≥56dp）与状态/回执行；话筒字形自绘，不引图标库 |
| UI | `ui/voice/VoiceController.kt` | 状态机：`IDLE → LISTENING → (指令就地执行 \| THINKING) → SPEAKING → 连续模式回 LISTENING` |
| UI | `ui/voice/VoiceState.kt` | `VoicePhase` + `VoiceUiState`（Compose 唯一真相源，文案一律资源 id） |
| UI | `ui/voice/VoiceVocabulary.kt` | 从资源装配指令词表 |
| Core | `core/voice/VoiceCommand.kt` / `VoiceCommandParser.kt` | 指令模型 + 纯函数解析（无 Android 依赖，可单测） |
| Core | `core/voice/SpeechChannels.kt` | `SpeechTranscriber` / `SpeechSynthesizer` 接口 + `VoiceError` |
| System | `system/voice/AndroidSpeechTranscriber.kt` | 系统 `SpeechRecognizer` 封装（流式部分结果） |
| System | `system/voice/AndroidSpeechSynthesizer.kt` | 系统 `TextToSpeech` + `USAGE_ASSISTANT` 音频焦点成对申请/释放 |
| System | `system/CarUxRestrictionsReader.kt` | 驾驶限制只读状态（本期只读不改布局） |
| Data | `data/store/VoiceSettingsStore.kt` | 播报开关与连续对话开关（DataStore 唯一真相） |
| 入口 | `MainActivity.kt` | 创建/释放语音通道；指令执行（清对话、跳设置、回首页、按技能名发送）；权限申请 |

## 运行链路

```
[点麦克风] ChatScreen.onVoiceToggle
   ├─ state.needsPermission → Activity 结果启动器申请 RECORD_AUDIO → 回调 refreshAvailability()
   └─ 否则 VoiceController.toggle()
          ├─ IDLE      → startListening() → [System] SpeechTranscriber.start(回调三件套)
          ├─ LISTENING → transcriber.stop()          // 让识别器结算，交给 onFinal
          ├─ THINKING  → 只给回执文案，不重复发问
          └─ SPEAKING  → synthesizer.stop() → startListening()   // barge-in 打断

[识别回调]（全部经主线程）
   onPartial → VoiceUiState.partial → 输入框实时上屏
   onFinal   → VoiceCommandParser.parse(text, vocab)
                 ├─ 命中 → VoiceUiState=IDLE + 回执 → onCommand → [入口] 清对话 / 跳设置 / 回首页 / 按技能名发送
                 └─ 未命中 → VoiceUiState=THINKING → onQuery → AgentRuntime.send(query)
   onError(VoiceError) → IDLE + 对应原因文案（不卡状态）

[一轮回答] AgentEvent.Completed
   → VoiceController.onAnswer(text)
        ├─ 播报开且引擎可用 → SPEAKING → [System] TextToSpeech.speak(USAGE_ASSISTANT, 焦点申请)
        │      └─ 进度回调 onDone/onStop → abandonFocus() → afterSpeaking()
        │             ├─ 连续模式 → startListening()（回聆听）
        │             └─ 否则 → IDLE
        └─ 播报关 → 直接 afterSpeaking()
```

## 使用点

- 对话页输入行：与键盘输入共用 `ChatConsole.submit()`，语音只是另一种输入方式。
- `MainActivity.onCreate` 创建通道与状态机，`onDestroy` 统一 `release()`（识别器与 TTS 都绑 Activity）。
- 设置页「语音」卡：播报开关与连续对话开关，写 DataStore 后状态机即时读到（`StateFlow` 共享）。
- 引擎侧无感知：语音不新增事件、不改 `AgentEvent` 契约。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 加/改本地指令 | `res/values/strings.xml` 的 `voice_cmd_*` 数组 + `VoiceCommand` | 词表只加资源；新增动作要在 `VoiceCommandParser` 与入口 `runCommand` 各补一支 |
| 改匹配严格度 | `core/voice/VoiceCommandParser.kt` | 现为整句全等 + 技能前缀；放宽前先想清误判代价 |
| 换识别/播报实现 | 新增 `SpeechTranscriber`/`SpeechSynthesizer` 实现 | 状态机只认接口，替换实现不动 `VoiceController`；离线 ASR/自建 TTS 走这条路 |
| 加打断/超时策略 | `ui/voice/VoiceController.kt` | 阶段迁移集中在此；超时依赖识别器回调，不另起计时器 |
| 行驶中改布局 | `HomeScreen` / 新遮罩页，消费 `CarUxRestrictionsReader.restricted` | 本期只读不改布局；做遮罩时按 design.md §6.5/§9 放大触控到 ≥72dp |
| 加语音新文案 | `res/values/strings.xml` + `values-en/strings.xml` | 成对新增；`voice_cmd_*` 词表例外（有意只留一份中英混收） |

## 关键约束

1. **音频不出车机**：录音只进系统识别管道，应用不落盘、不缓存、不上传；交给模型的只有识别文本。
   任何改动不得把原始音频写进文件或网络请求。
2. **能力缺失必须渲染禁用态并说明原因**：无语音服务 / 未授权时按钮不可点但可见，
   原因文案紧邻其下；只差授权时点击应直接发起授权请求（`needsPermission`），而不是只提示。
3. **状态机只在主线程驱动**：`SpeechRecognizer` 与 `TextToSpeech` 都绑定创建线程，
   通道随 Activity 创建、`onDestroy` 释放；识别器每次聆听用新实例（复用实例在部分设备不再回调）。
4. **音频焦点成对**：`USAGE_ASSISTANT` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，申请与放弃必须复用同一个
   request 对象（每次新建会让 abandon 失效）；播完立即放弃，不长期持有焦点。
5. **本地指令宁可漏判不误判**：固定指令整句全等才算命令，技能指令必须带技能名；
   普通提问一律走引擎，避免把用户问题当命令吞掉。
6. **语音不改变引擎契约**：`AgentEvent` 语义不变，语音只是事件流的一个消费者；
   新增语音相关事件一律加在 UI/系统层，不进 `core/engine`。

## 维护方式

- **新增**：先加 `VoiceCommand` 分支与资源词表 → 补 `VoiceCommandParserTest` 用例 → 在入口 `runCommand` 落动作 → 双语补文案。
- **排查**：按钮灰着看状态行原因（无服务 / 未授权）；点了没反应查 `checkSelfPermission` 是否授到了**运行用户**
  （AAOS 双用户，`pm grant` 默认只给 user 0，应用常跑在 user 10，要 `pm grant --user 10`）；
  播报不停查焦点是否成对放弃；识别不回调查是否复用了同一个 `SpeechRecognizer` 实例。
- **验证**：`./gradlew :app:testDebugUnitTest`（解析器 10 例 + 状态机 16 例，全走假通道不碰 Android）；
  模拟器 `adb install -r` → 对话页点麦克风 → 无麦克风音频时预期回落到"麦克风被占用/没听清"回执而不是卡住；
  设置页两个开关拨动后离开再进应保留。真机需补：说"新开会话"应清对话流，问一句应有播报。

> 更新时间：2026-10-02（Phase A：单轮语音 + 语音指令 + 播报 + 连续对话）
