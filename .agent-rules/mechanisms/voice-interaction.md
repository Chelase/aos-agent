# 语音交互机制

## 结论

对话页用系统语音服务做识别与播报，识别文本先过一层本地指令解析：命中固定指令就地执行、不进模型，
其余文本与键盘输入走同一条发送通路进 Agent 引擎。Phase B 起再加三条：连续对话自己走
（播报→静默 1s→再听，8s 不说话收工），音频焦点由**整条会话**持有并在被导航/媒体抢走时挂起回路，
行驶受限时非首页一律盖全屏语音遮罩，唤醒词用 Vosk 在车机本地常驻判定（默认关）。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| UI | `ui/chat/ChatScreen.kt` | 输入行左侧麦克风按钮 + 按钮上方状态行；部分识别结果直接落到输入框 |
| UI | `ui/voice/VoiceControls.kt` | 灯语化的麦克风控件（尺寸档位由调用方给）与状态/回执行；话筒字形自绘，不引图标库 |
| UI | `ui/voice/DriveVoiceMask.kt` | 行驶受限全屏遮罩：状态灯 + ≥72dp 大麦克风 + 返回首页，吃掉一切手势 |
| UI | `ui/voice/VoiceController.kt` | 状态机：`IDLE → LISTENING → (指令 \| THINKING) → SPEAKING → WAITING → LISTENING …`，含超时/打断/焦点挂起 |
| UI | `ui/voice/VoiceState.kt` | `VoicePhase`（含 `WAITING`）+ `VoiceUiState`（Compose 唯一真相源，文案一律资源 id） |
| UI | `ui/voice/VoiceVocabulary.kt` | 从资源装配指令词表 |
| Core | `core/voice/VoiceCommand.kt` / `VoiceCommandParser.kt` | 指令模型 + 纯函数解析（无 Android 依赖，可单测） |
| Core | `core/voice/SpeechChannels.kt` | `SpeechTranscriber` / `SpeechSynthesizer` / `VoiceFocusHandle` 接口 + `VoiceError` |
| Core | `core/voice/WakeWord.kt` | `WakeWordEngine` 接口 + `WakeWordPhrases`（名字→说法→受限语法）+ `WakeWordMatcher` 判定 + `WakeWordGate`（让路闸门与唤醒事件） |
| System | `system/voice/AndroidSpeechTranscriber.kt` | 系统 `SpeechRecognizer` 封装（流式部分结果） |
| System | `system/voice/AndroidSpeechSynthesizer.kt` | 系统 `TextToSpeech` 封装；**不碰焦点**（焦点归会话） |
| System | `system/voice/AndroidVoiceFocus.kt` | 会话级 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，被抢时回调 |
| System | `system/voice/VoskWakeWordEngine.kt` | `AudioRecord`(16k mono) + Vosk 受限语法循环，命中判本地 |
| System | `system/voice/WakeModelInstaller.kt` | 42MB 模型下载→解压→校验→改名；状态流驱动设置页 |
| System | `system/CarUxRestrictionsReader.kt` | 驾驶限制三态只读（`UNKNOWN/UNRESTRICTED/RESTRICTED`） |
| Service | `service/AgentForegroundService.kt` | 常驻 KWS 宿主：开关+模型+权限三条件齐才听，命中分发唤醒 |
| Data | `data/store/VoiceSettingsStore.kt` | 播报/连续/打断/唤醒四个开关（DataStore 唯一真相） |
| 入口 | `MainActivity.kt` | 创建/释放通道与状态机；指令执行；权限申请；遮罩接线；唤醒分发 |

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

[焦点被抢] AndroidVoiceFocus 回调 → VoiceController.onAudioFocusLost() → suspendLoop()
   cancel 识别 + stop 播报 + 作废计时 + suspendedByFocus=true，灯回 IDLE 但**不交还焦点**
   （交了就拿不到 GAIN，回路永远醒不过来）
[焦点回来] onAudioFocusGained() → 重新可用则 startListening()，否则 goIdle(原因)

[唤醒] AgentForegroundService
   说法 = WakeWordPhrases.build(agentName, wakeVariants)   // 你好X / Hi X / 裸X，可多选
   combine(voiceSettings.wakeWordEnabled, wakeModel.state, WakeWordGate.paused)
      三条件齐 → VoskWakeWordEngine.start(受限语法 = 说法 + [unk])
      说法变了 → 先 stop 再 start（语法在 Recognizer 构造时定死，改名字必须重建）
      任一不成立 / 界面正在用麦 → stop() 并 release()（模型跟着释放）
   命中（冷却 2s 内不重复）→ WakeWordGate.emitWake() + startActivity(EXTRA_VOICE_WAKE)
      → MainActivity.onNewIntent/onCreate 或进程内事件 → 跳对话页 → voice.startFromWake()
   监听起停同时刷新常驻通知文案，写明在听哪个词

[行驶受限] CarUxRestrictionsReader.restriction
   ├─ RESTRICTED && destination != Home → DriveVoiceMask 盖满（被盖页面继续组合但 invisibleToUser）
   └─ Home → 终端/工程师行禁用 + 原因；「驾驶模式」行按三态如实显示
```

## 使用点

- 对话页输入行：与键盘输入共用 `ChatConsole.submit()`，语音只是另一种输入方式。
- 行驶遮罩：盖在对话/设置/终端/系统面板/工程师任一页面上，返回首页即解除。
- `MainActivity.onCreate` 创建通道与状态机（`scope = lifecycleScope`），`onDestroy` 统一 `release()`。
- 设置页「语音」卡：播报 / 连续对话 / 说话打断 / 语音唤醒四个开关，外加模型下载进度与失败重试；
  唤醒开关在模型未就绪时禁用并写明原因。
- 引擎侧无感知：语音不新增事件、不改 `AgentEvent` 契约。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 加/改本地指令 | `res/values/strings.xml` 的 `voice_cmd_*` 数组 + `VoiceCommand` | 词表只加资源；新增动作要在 `VoiceCommandParser` 与入口 `runCommand` 各补一支 |
| 改匹配严格度 | `core/voice/VoiceCommandParser.kt` | 现为整句全等 + 技能前缀；放宽前先想清误判代价 |
| 换识别/播报实现 | 新增 `SpeechTranscriber`/`SpeechSynthesizer` 实现 | 状态机只认接口，替换实现不动 `VoiceController`；离线 ASR/自建 TTS 走这条路 |
| 改静默/超时/打断节奏 | `ui/voice/VoiceController` 的 `SILENCE_AFTER_SPEECH_MS` / `NO_SPEECH_TIMEOUT_MS` | 计时一律走注入的 `scope`，单测用虚拟时间推进验证，不许改成 `Handler.postDelayed` |
| 改焦点策略 | `system/voice/AndroidVoiceFocus.kt` | 只有会话这一处申请焦点；播报侧再加申请就是把回路自己挂起 |
| 改唤醒词 | 设置页「Agent 名字」+ 唤醒说法勾选 | 名字与说法存 `VoiceSettings`，语法运行时注入，改词不用重下模型；不加代码分支 |
| 加一种唤醒说法 | `core/voice/WakeVariant` + `WakeWordPhrases.build` | 新枚举值 + 一条生成规则 + 双语标签；判定侧不用改（按说法列表包含匹配） |
| 换唤醒模型 | `WakeModelInstaller` 的 URL/目录名常量 | 就绪判据是 `am/final.mdl` + `conf/model.conf`，换模型要一起改 |
| 行驶中再裁布局 | `MainActivity` 的遮罩条件 + `HomeScreen.driveRestricted` | 尺寸只走 `AOSSizing.driveTarget`，不在界面写 72dp 字面量 |
| 加语音新文案 | `res/values/strings.xml` + `values-en/strings.xml` | 成对新增；`voice_cmd_*` 词表例外（有意只留一份中英混收） |

## 关键约束

1. **音频不出车机**：录音只进系统识别管道，应用不落盘、不缓存、不上传；交给模型的只有识别文本。
   唤醒判定全在本地，命中只交一个事件，唤醒词那半句音频随缓冲丢弃。任何改动不得把原始音频写进文件或网络请求。
2. **要念出口的词不是界面文案**：唤醒前缀"你好"/"Hi"与生成的说法一律用常量原样显示，
   **不随界面语言翻译**（英文界面上用户照样喊"你好Chelsea"）；只有"名字为空""裸名易误唤醒"这类说明文字走双语资源。
   名字是用户输入，进语法前要剥掉引号与反斜杠，否则整条受限语法会打烂、识别器构造失败。
3. **常驻麦克风必须显式开启**：`wakeWordEnabled` 默认 false；开启后通知必须写明在听什么。
   界面在用麦时 KWS 必须让路（`WakeWordGate.paused`），否则对话页会报"麦克风被占用"。
4. **焦点只有一份，归会话**：`USAGE_ASSISTANT` + `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`，
   申请与放弃复用同一个 request 对象（每次新建会让 abandon 失效）；挂起时**不能**交还焦点；
   回 IDLE/失败/超时必须交还。播报侧再申请焦点就是把回路自己踢挂。
5. **能力缺失必须渲染禁用态并说明原因**：无语音服务 / 未授权 / 模型没下载，三种都不可点但可见，
   原因文案紧邻其下；只差授权时点击应直接发起授权请求（`needsPermission`）。
   可用性在 `VoiceController` 构造时就定死——遮罩可能盖在任何页面上，没人替它刷新。
6. **状态机只在主线程驱动**：`SpeechRecognizer` 与 `TextToSpeech` 都绑定创建线程；
   识别器每次聆听用新实例（复用实例在部分设备不再回调）。
7. **行驶遮罩必须吃掉手势**：Compose 会把事件继续传给被盖住的页面，实测点底层"返回"能把遮罩跳没；
   遮罩根节点 `pointerInput` 消费全部手势，被盖子树 `invisibleToUser()`（但**继续组合**，否则对话条目与终端会话会丢）。
8. **本地指令宁可漏判不误判**；语音不改变引擎契约，新事件一律加在 UI/系统层，不进 `core/engine`。
9. **内存基线**：常驻 <200MB。实测模拟器：唤醒关 **146MB**、唤醒开（模型已加载）**215MB** —
   开唤醒这条 opt-in 路径超基线约 15MB，靠"关即 `Model.close()` 释放"把默认状态留在基线内。

## 维护方式

- **新增**：先加 `VoiceCommand` 分支与资源词表 → 补 `VoiceCommandParserTest` → 在入口 `runCommand` 落动作 → 双语补文案。
- **排查**：按钮灰着看状态行原因（无服务 / 未授权）；点了没反应查 `checkSelfPermission` 是否授到了**运行用户**
  （AAOS 双用户，`pm grant` 默认只给 user 0，应用常跑在 user 10，要 `pm grant --user 10`）；
  播报不停查焦点是否成对放弃；识别不回调查是否复用了同一个 `SpeechRecognizer` 实例；
  唤醒不起查 `dumpsys notification` 里通知文案是否变成"正在听唤醒词"，以及 logcat `AOSAgent.Service` 的 started/stopped。
- **验证**：`./gradlew :app:testDebugUnitTest`（解析器 10 例 + 状态机 28 例 + 唤醒判定 13 例，全走假通道不碰 Android）。
  模拟器：`adb install -r` → 对话页点麦克风 → 无麦克风音频时预期回落到原因文案而不是卡住；
  行驶遮罩用 `am start -n com.aos.agent/.MainActivity --ez com.aos.agent.extra.PREVIEW_DRIVE_RESTRICTED true`
  走查（真值注入需要 VHAL 写权限，user 版镜像把 `cmd car_service` 全量屏蔽）；
  触控尺寸按 `uiautomator dump` 的 bounds 量（160dpi 下 1px=1dp），别用眼睛估；
  界面文案点击一律先 dump 再按 bounds 点，按文字节点中心点会打偏。
  模型下载在无路由的模拟器上要 `adb reverse tcp:7897 tcp:7897` + `settings put global http_proxy 127.0.0.1:7897`。
- **不可验证项（模拟器）**：说→出字、播报可听、唤醒命中率 3/5 与误唤醒率、人声打断（需硬件回声消除）。
  这些只能真机或有麦环境补，交付时按"仅本地实现/仅单测覆盖"标注，不得写成已验收。

> 更新时间：2026-10-03（Phase B + 唤醒词改为按 Agent 名字生成）
