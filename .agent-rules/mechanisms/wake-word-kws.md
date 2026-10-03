# 离线唤醒词（Vosk KWS）机制

## 结论

前台服务里常驻一个本地小模型，只听"是不是在叫我"，命中就把对话页拉到前台并直接进聆听；
音频不出车机、不落盘、不上传，唤醒词由用户给 Agent 起的名字实时生成。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Core | `core/voice/WakeWord.kt` | `WakeWordEngine` 接口 + `WakeWordPhrases`（名字→说法→受限语法）+ `WakeWordMatcher`（命中判定）+ `WakeWordGate`（让路闸门与唤醒事件） |
| Core | `core/voice/WakeDebug.kt` | 观测环形缓冲（最近 20 条 + 命中/未命中计数），只在面板开着时收集 |
| System | `system/voice/VoskWakeWordEngine.kt` | `AudioRecord`(16kHz mono) + `Model`/`Recognizer` 循环，命中带 2s 冷却 |
| System | `system/voice/WakeModelInstaller.kt` | 42MB 模型下载→解压→校验→改名，状态流驱动设置页 |
| Service | `service/AgentForegroundService.kt` | 唤醒生命周期宿主：三条件齐才听，说法变了重建识别器 |
| Data | `data/store/VoiceSettingsStore.kt` | `agentName` + `wakeVariants` + `wakeWordEnabled`（DataStore 唯一真相） |
| UI | `ui/settings/SettingsScreen.kt` | 名字输入、说法多选、唤醒开关、模型下载进度与失败重试 |
| UI | `ui/engineer/EngineerModeScreen.kt` | 唤醒调试卡（真车调命中率的观测面） |
| 入口 | `MainActivity.kt` | `EXTRA_VOICE_WAKE` 与进程内事件两条分发路径，落到 `VoiceController.startFromWake()` |
| 依赖 | `com.alphacephei:vosk-android:0.3.47`（传递带 JNA 5.13.0） | 原生 KWS，aar 含 arm64-v8a 与 x86_64 |

## 运行链路

```
[配置] 设置页
   agentName + wakeVariants ──▶ WakeWordPhrases.build(name, variants)
        HELLO→"你好X"  HI→"Hi X"  BARE→"X"（默认只开 HELLO）
        └─ 名字为空 → 没有任何说法（不生成"你好"这种半截词）
   wakeWordEnabled ─▶ AgentWakeWatcher.requestRefresh() 把服务拉起来

[常驻] AgentForegroundService.watchWakeWord()
   combine(settings.wakeWordEnabled, installer.state, WakeWordGate.paused)
     ├─ 任一不成立 → stopWakeWord()：release 引擎（Model.close() 跟着释放，约 -69MB）
     └─ 三条件齐   → 说法变了？先 stop 再 start（语法在 Recognizer 构造时定死）
                     VoskWakeWordEngine.start(phrases)

[监听] VoskWakeWordEngine.listenLoop()（IO 线程）
   Model(modelPath) → Recognizer(model, 16000, grammarOf(phrases))   // ["你好X","[unk]"]
   while running: AudioRecord.read(ShortArray 3200)
     acceptWaveForm → true 时取 result
       text = WakeWordMatcher.textOf(json)；recognizer.reset()
       matched = isWake(text, phrases)                    // 去空白与标点、忽略大小写后包含判定
       WakeDebug.record(text, matched, elapsedRealtime)   // [unk] 也记：那才是"没听清"的那一半
       matched && 过了 2s 冷却 → 主线程回调 onWake

[分发] onWakeWordHeard()
   ├─ WakeWordGate.emitWake()          → 界面活着就直接进聆听（无重放，晚到的收集者不该被历史唤醒拉起）
   └─ startActivity(EXTRA_VOICE_WAKE)  → Activity 不在栈上时补一次；后台启动被系统拒时只记日志不崩
        → MainActivity.onCreate/onNewIntent 或事件流 → destination=Chat → voice.startFromWake()

[观测] 工程师模式 WakeDebugCard
   进入 DisposableEffect → WakeDebug.open()；离开 → close()（清空计数与缓冲）
   显示：当前说法 / 命中 / 未命中 / 最近 20 条原文 / 清零

[让路] 对话页或遮罩在用麦 → VoiceController 阶段 ≠ IDLE
   → LaunchedEffect 置 WakeWordGate.paused=true → 上面的 combine 立刻 stop
```

## 使用点

- 设置页「语音」卡：唤醒开关 + 名字 + 说法多选 + 模型下载；开关在模型未就绪时禁用并写明原因。
- 前台服务通知：监听中显示"正在听唤醒词「…」"，起停都刷新（常驻麦必须看得见）。
- 工程师模式：唤醒调试卡（唯一能看见模型原始吐字的入口）。
- 对话页/行驶遮罩：唤醒后与点麦克风走同一条 `VoiceController` 通路，不另开状态机。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 改唤醒词 | 设置页「Agent 名字」+ 说法勾选 | 存 `VoiceSettings`，语法运行时注入，不改代码也不重下模型 |
| 加一种说法模板 | `core/voice/WakeVariant` + `WakeWordPhrases.build` | 新枚举值 + 一条生成规则 + 双语标签；判定侧按列表包含匹配，不用改 |
| 换唤醒模型 | `WakeModelInstaller` 的 URL 与目录名常量 | 就绪判据是 `am/final.mdl` + `conf/model.conf`，换模型一起改；官方包带一层同名顶层目录 |
| 调误唤醒 | 说法组合（关掉裸名）→ 再考虑换大模型 | 受限语法已经是最有效的一档；冷却窗只防连发不防误判 |
| 改隐私边界 | `VoskWakeWordEngine.listenLoop` | 任何"把音频留下来/发出去"的改动都先违反关键约束第 1 条，需先改机制文档 |

## 关键约束

1. **音频不出车机**：只在内存缓冲里做判定，不落盘、不缓存、不上传；命中只交一个事件，
   唤醒词那半句随缓冲丢弃。观测面板显示的原文之所以可以安全展示，是因为受限语法保证
   取值只可能是选定的说法或 `[unk]`——**放宽语法等于同时放宽隐私**。
2. **常驻麦要显式开、要看得见**：`wakeWordEnabled` 默认 false；开启期间通知必须写明在听什么词。
3. **要念出口的词不是界面文案**：前缀"你好"/"Hi"用常量原样显示，不随界面语言翻译；
   名字是用户输入，进语法前必须剥掉引号与反斜杠，否则整条受限语法 JSON 打烂、识别器构造失败。
4. **一路麦克风**：界面在用麦时 KWS 必须让路（`WakeWordGate.paused`），否则对话页报"麦克风被占用"；
   让路即释放模型，回 IDLE 才重新加载。
5. **内存基线**：常驻 <200MB。实测关唤醒 146MB、开唤醒 215MB——开启态超基线约 15MB 是用户显式换能力，
   默认态必须留在基线内，所以"关即 `Model.close()`"不能省。
6. **能力缺失要禁用并说明原因**：模型没下载、没录音权限、原生库缺失（ABI 不匹配）三种都不得留死开关，
   分别给"先下载模型 / 未授予麦克风权限 / 本机不支持离线唤醒"的可见原因。

## 维护方式

- **排查**：`adb logcat -s AOSWakeWord AOSAgent.Service AOSWakeModel`——起停、循环异常、模型安装失败都有日志；
  `dumpsys notification --noredact | grep android.text` 看通知是否变成"正在听唤醒词"。
- **验证（模拟器可验）**：设置页改名 → 说法标签与开关标签、通知文案跟随；勾选说法 → 日志出现成对 stopped/started；
  工程师页进出 → 计数清零；模型下载进度到 100% 后显示"已就绪"。
- **验证（必须真机）**：喊唤醒词的命中率与误唤醒率——模拟器没有麦克风音频（`pcm_read failed`），
  这块只能靠工程师模式的唤醒调试卡在车上测：清零 → 喊 5 次 → 看命中数；静置 10 分钟看未命中里是否冒出命中。
- **英文名的天花板**：中文小模型是普通话声学模型，英文名命中率不可预测，要可靠只能中英双模型并行
  （约再 +70MB，已超基线），当前不做。

> 更新时间：2026-10-03
