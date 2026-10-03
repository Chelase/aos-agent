# 语音交互机制（识别 / 播报 / 连续对话 / 语音指令 / 行驶遮罩）

> 离线唤醒词另见 [`wake-word-kws.md`](./wake-word-kws.md)：那是常驻服务里的另一条链路，
> 只有"命中之后"与本机制相接。

## 结论

对话页用系统语音服务做识别与播报，识别文本先过一层本地指令解析：命中固定指令就地执行、不进模型，
其余文本与键盘输入走同一条发送通路进 Agent 引擎；连续对话自己走（播报→静默 1s→再听，8s 不说话收工），
音频焦点由**整条会话**持有并在被导航/媒体抢走时挂起回路，行驶受限时非首页一律盖全屏语音遮罩。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| UI | `ui/chat/ChatScreen.kt` | 输入行左侧麦克风按钮 + 状态行；部分识别结果直接落到输入框 |
| UI | `ui/voice/VoiceControls.kt` | 灯语化麦克风控件（尺寸档位由调用方给）与状态/回执行；话筒字形自绘，不引图标库 |
| UI | `ui/voice/DriveVoiceMask.kt` | 行驶受限全屏遮罩：状态灯 + ≥72dp 大麦克风 + 返回首页，吃掉一切手势 |
| UI | `ui/voice/VoiceController.kt` | 状态机：`IDLE → LISTENING → (指令 \| THINKING) → SPEAKING → WAITING → LISTENING …`，含超时/打断/焦点挂起 |
| UI | `ui/voice/VoiceState.kt` | `VoicePhase`（含 `WAITING`）+ `VoiceUiState`（Compose 唯一真相源，文案一律资源 id） |
| UI | `ui/voice/VoiceVocabulary.kt` | 从资源装配指令词表 |
| Core | `core/voice/VoiceCommand.kt` / `VoiceCommandParser.kt` | 指令模型 + 纯函数解析（无 Android 依赖，可单测） |
| Core | `core/voice/SpeechChannels.kt` | `SpeechTranscriber` / `SpeechSynthesizer` / `VoiceFocusHandle` 接口 + `VoiceError` |
| System | `system/voice/AndroidSpeechTranscriber.kt` | 系统 `SpeechRecognizer` 封装（流式部分结果） |
| System | `system/voice/AndroidSpeechSynthesizer.kt` | 系统 `TextToSpeech` 封装；**不碰焦点**（焦点归会话） |
| System | `system/voice/AndroidVoiceFocus.kt` | 会话级 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，被抢时回调 |
| System | `system/CarUxRestrictionsReader.kt` | 驾驶限制三态只读（`UNKNOWN/UNRESTRICTED/RESTRICTED`） |
| Data | `data/store/VoiceSettingsStore.kt` | 播报 / 连续 / 打断 / 唤醒四个开关（DataStore 唯一真相） |
| 入口 | `MainActivity.kt` | 创建释放通道与状态机；指令执行；权限申请；遮罩接线；唤醒落地点 |

## 运行链路

```
[点麦克风] ChatScreen.onVoiceToggle / DriveVoiceMask.onMicClick
   ├─ state.needsPermission → Activity 结果启动器申请 RECORD_AUDIO → refreshAvailability()
   └─ 否则 VoiceController.toggle()
          ├─ IDLE      → startSession() → ensureFocus() → startListening()
          ├─ WAITING   → goIdle()（用户不想继续就立刻收，静默计时作废）
          ├─ LISTENING → transcriber.stop()          // 让识别器结算，交给 onFinal
          ├─ THINKING  → 只给回执文案，不重复发问
          └─ SPEAKING  → interruptPlayback() → synthesizer.stop() → startListening()

[聆听] startListening() 一次做三件事
   ├─ ensureFocus()：会话第一次开麦才 claim 焦点（重复 claim 会把自己踢成 LOSS）
   ├─ 起 8s 无语音计时：到点仍 LISTENING 且 partial 为空 → cancel() → goIdle(超时文案)
   └─ transcriber.start(onPartial/onFinal/onError)
          onPartial 非空 → 取消 8s 计时（人在说就不催）+ 上屏
          onFinal → 阶段仍是 LISTENING 才认（迟到的最终结果一律丢弃）
          onError → goIdle(对应原因)，不卡状态

[一轮回答] AgentEvent.Completed → VoiceController.onAnswer(text)
   ├─ awaitingFocus → 不抢播：suspendLoop() 等 GAIN，文字回答本来就在页面上
   ├─ 播报开 → ensureFocus() → SPEAKING → TextToSpeech.speak
   │      └─ onDone/onStop → afterSpeaking()（阶段已被打断带走就不重复起聆听）
   │             ├─ 连续模式 → WAITING + 1s 防自听静默 → startListening()
   │             └─ 否则 → goIdle()（交还焦点）
   ├─ 播报关 → 直接 finishTurn()
   └─ bargeInEnabled && continuous → probeForBargeIn()：播报期间另起一路只听 partial 的探针
          首个非空 partial → interruptPlayback()（探针的 onFinal/onError 一律不提交结果）

[指令] onFinal → VoiceCommandParser.parse(text, vocab)
   ├─ 命中 → goIdle + 回执 → onCommand → 清对话 / 跳设置 / 回首页 / 按技能名发送
   └─ 未命中 → THINKING → onQuery → AgentRuntime.send(query)

[焦点] AndroidVoiceFocus 是会话唯一持有者
   被导航/媒体抢走 → onAudioFocusLost() → suspendLoop()
      cancel 识别 + stop 播报 + 作废计时 + suspendedByFocus=true，灯回 IDLE 但**不交还焦点**
      （交了就拿不到 GAIN，回路永远醒不过来）
   系统给回 GAIN → onAudioFocusGained() → 仍可用则 startListening()，否则 goIdle(原因)
   回 IDLE / release() → abandonAudioFocusRequest（复用同一个 request 对象）

[行驶受限] CarUxRestrictionsReader.restriction
   ├─ RESTRICTED && destination != Home → DriveVoiceMask 盖满
   │      被盖页面继续组合（条目与会话不丢）但 invisibleToUser()，根节点吃掉全部手势
   ├─ Home → 终端/工程师行禁用 + 原因；「驾驶模式」行按三态如实显示
   └─ 真值注入不了时：--ez com.aos.agent.extra.PREVIEW_DRIVE_RESTRICTED true 走查同一套布局

[唤醒接入点] WakeWordGate.wakes / MainActivity.EXTRA_VOICE_WAKE
   → destination = Chat → VoiceController.startFromWake()（与点麦克风同一条通路）
```

## 使用点

- 对话页输入行：与键盘输入共用 `ChatConsole.submit()`，语音只是另一种输入方式。
- 行驶遮罩：盖在对话/设置/终端/系统面板/工程师任一页面上，返回首页即解除。
- `MainActivity.onCreate` 创建通道与状态机（`scope = lifecycleScope`），`onDestroy` 统一 `release()`。
- 设置页「语音」卡：播报 / 连续对话 / 说话打断 / 语音唤醒四个开关。
- 引擎侧无感知：语音不新增事件、不改 `AgentEvent` 契约。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 加/改本地指令 | `res/values/strings.xml` 的 `voice_cmd_*` 数组 + `VoiceCommand` | 词表只加资源；新增动作要在 `VoiceCommandParser` 与入口 `runCommand` 各补一支 |
| 改匹配严格度 | `core/voice/VoiceCommandParser.kt` | 现为整句全等 + 技能前缀；放宽前先想清误判代价 |
| 换识别/播报实现 | 新增 `SpeechTranscriber`/`SpeechSynthesizer` 实现 | 状态机只认接口，替换实现不动 `VoiceController`；离线 ASR/自建 TTS 走这条路 |
| 改静默/超时/打断节奏 | `ui/voice/VoiceController` 的 `SILENCE_AFTER_SPEECH_MS` / `NO_SPEECH_TIMEOUT_MS` | 计时一律走注入的 `scope`，单测用虚拟时间推进验证，不许改成 `Handler.postDelayed` |
| 改焦点策略 | `system/voice/AndroidVoiceFocus.kt` | 只有会话这一处申请焦点；播报侧再加申请就是把回路自己挂起 |
| 行驶中再裁布局 | `MainActivity` 的遮罩条件 + `HomeScreen.driveRestricted` | 尺寸只走 `AOSSizing.driveTarget`，不在界面写 72dp 字面量 |
| 加语音新文案 | `res/values/strings.xml` + `values-en/strings.xml` | 成对新增；`voice_cmd_*` 词表例外（有意只留一份中英混收） |

## 关键约束

1. **音频不出车机**：录音只进系统识别管道，应用不落盘、不缓存、不上传，交给模型的只有识别文本。
   任何改动不得把原始音频写进文件或网络请求。
2. **焦点只有一份，归会话**：`USAGE_ASSISTANT` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，申请与放弃复用
   同一个 request 对象（每次新建会让 abandon 失效）；挂起时**不能**交还焦点，回 IDLE/失败/超时必须交还；
   播报侧再申请焦点就是把回路自己踢挂。
3. **能力缺失必须渲染禁用态并说明原因**：无语音服务 / 未授权都不可点但可见，原因紧邻其下；
   只差授权时点击应直接发起授权请求（`needsPermission`）。可用性在 `VoiceController` 构造时定死——
   遮罩可能盖在任何页面上，没人替它刷新。
4. **状态机只在主线程驱动，计时走注入的 `scope`**：识别器与 TTS 绑创建线程；识别器每次聆听用新实例
   （复用实例在部分设备不再回调）；延迟/超时不许换成 `Handler`，否则单测跑不了。
5. **一次阶段迁移只能有一个驱动者**：播报回调 `afterSpeaking()` 只在阶段仍是 SPEAKING 时接管，
   打断探针的 `onFinal` 一律丢弃，迟到的最终结果不认——否则一次打断会起两轮聆听并把整句重复提交。
6. **行驶遮罩必须吃掉手势，被盖页面要继续组合**：Compose 会把事件继续发给被盖住的页面，
   实测点底层"返回"能把遮罩跳没；遮罩根节点 `pointerInput` 消费全部手势，被盖子树 `invisibleToUser()`
   但**不卸载**（否则对话条目与终端会话随遮罩上来就丢）。本地指令宁可漏判不误判；
   语音不改变引擎契约，新事件一律加在 UI/系统层，不进 `core/engine`。

## 维护方式

- **新增**：先加 `VoiceCommand` 分支与资源词表 → 补 `VoiceCommandParserTest` → 在入口 `runCommand` 落动作 → 双语补文案。
- **排查**：按钮灰着看状态行原因（无服务 / 未授权）；点了没反应查 `checkSelfPermission` 是否授到了**运行用户**
  （AAOS 双用户，`pm grant` 默认只给 user 0，应用常跑在 user 10，要 `pm grant --user 10`）；
  播报不停查焦点是否成对放弃；识别不回调查是否复用了同一个 `SpeechRecognizer` 实例。
- **验证**：`./gradlew :app:testDebugUnitTest`（解析器 10 例 + 状态机 28 例，全走假通道不碰 Android）。
  模拟器：`adb install -r` → 对话页点麦克风 → 无麦克风音频时预期回落到原因文案而不是卡住；
  行驶遮罩用 `am start -n com.aos.agent/.MainActivity --ez com.aos.agent.extra.PREVIEW_DRIVE_RESTRICTED true`
  走查（真值注入需要 VHAL 写权限，user 版镜像把 `cmd car_service` 全量屏蔽）；
  触控尺寸按 `uiautomator dump` 的 bounds 量（160dpi 下 1px=1dp），别用眼睛估；
  界面文案点击一律先 dump 再按可点击祖先的 bounds 点，按文字节点中心点会打偏。
- **不可验证项（模拟器）**：说→出字、播报可听、人声打断（需硬件回声消除）。
  这些只能真机或有麦环境补，交付时按"仅单测覆盖"标注，不得写成已验收。

> 更新时间：2026-10-03（拆出唤醒机制，本篇收敛为交互链路）
