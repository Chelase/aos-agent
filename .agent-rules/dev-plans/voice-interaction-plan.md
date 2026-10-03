# 语音交互计划（唤醒 / 识别 / 连续对话 / 语音指令）

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。语音交互是 aos-agent 车机端本地 UX 层：音频不出车机（KWS 唤醒词本地跑、识别文本才进 Agent 引擎），不改变 AOC 单一人格出口，不自研跨设备协议。
>
> 配套机制文档：`../mechanisms/voice-interaction.md`（Phase A 已于 2026-10-02 落地并生成）
>
> 设计依据：`../docs/原型/design.md` §6.5 驾驶模式遮罩、§9 驾驶模式规范（语音优先、行驶中禁键盘输入、触控 ≥72dp）。

## 当前阶段 / 方案结论

**现状：语音能力零实现。** 全仓库无 `SpeechRecognizer` / `AudioRecord` / `TextToSpeech` / `RECORD_AUDIO`，导航里也没有任何语音入口；`CarUxRestrictions` 驾驶模式同样未接入。

用户需求拆解（含一个待确认的术语假设）：

| 能力 | 含义 | 分期 |
|---|---|---|
| 语音唤醒 | 免手操说出唤醒词（如"你好副驾"）进入聆听 | Phase B |
| 语音识别（ASR） | 说话转文字，流式上屏，作为 Agent 输入 | Phase A |
| 单轮对话 | 按一下说一句 → 一个回答（**假设"单词对话"指单轮对话**；若指"单词唤醒"请指出，方案不变仅换叫法） | Phase A |
| 连续对话 | 回答播报完自动回到聆听，多轮不碰屏；支持打断（barge-in） | Phase B |
| 语音指令 | 本地解析固定指令：新开会话、使用技能 X、使用 MCP、打开/关闭设置、返回首页 | Phase A（第一批）/ Phase B 扩充 |
| 驾驶场景优先 | 行驶中语音是唯一安全输入（键盘被 CarUxRestrictions 禁止）；大按钮、少层级、状态灯可见 | 贯穿 |

**关键技术决策（本计划的骨架）：**

1. **ASR 用系统 `SpeechRecognizer`**（Google 语音服务，zh-CN，支持流式部分结果）。不可用时（部分 AAOS 车机无语音服务）按能力缺失规则渲染禁用态并说明原因，不伪造。
2. **TTS 用系统 `TextToSpeech`**，回答播报可开关；播报时请求 `USAGE_ASSISTANT` 音频焦点，播完自动释放并（连续对话模式下）回到聆听。
3. **唤醒词用 Vosk 离线 KWS**（Apache-2.0，中文小模型，模型随首启下载不打进 APK）。
   交付后按用户要求改为**唤醒词 = Agent 名字**（`你好X` / `Hi X` / 裸名可多选，默认带前缀），
   语法运行时注入所以改词不用重下模型；英文名受限于普通话声学模型，命中率待真机调。不用 Porcupine（商用授权）与 `VoiceInteractionService` 热词（需系统签名，留待厂商合作路径 Phase C）。
4. **语音指令本地解析**：`core/voice/VoiceCommandParser.kt` 纯函数（文本 → 指令或 None），可单测；指令不打 LLM，省时省 token。指令词表走双语资源。
5. **音频隐私**：录音仅进识别管道，不落盘不缓存；唤醒 KWS 在本地推理；隐私说明进设置页文案。
6. **音频焦点是硬约束**：导航播报/媒体播放时语音识别主动让位（focus loss → 暂停聆听），避免跟车机系统抢麦。

## 改动范围

### 新增模块

| 文件 | 职责 |
|---|---|
| `core/voice/VoiceCommandParser.kt` | 纯函数：识别文本 → `VoiceCommand`（新开会话/使用技能/使用MCP/开关设置/返回首页）或 null；词表由资源注入 |
| `core/voice/VoiceCommand.kt` | 指令密封类 |
| `ui/voice/VoiceController.kt` | 状态机：Idle → Listening(ASR) → Recognizing → Thinking(engine) → Speaking(TTS) → (连续模式)Listening；持有音频焦点生命周期 |
| `ui/voice/VoiceState.kt` | 状态 + 部分识别文本 + 错误码（Compose 状态源） |
| `ui/voice/VoiceButton.kt` | 状态灯化的大麦克风按钮（≥56dp，驾驶态 ≥72dp），复用灯语规则 |
| `ui/voice/VoiceOverlay.kt` | Phase B 连续对话的轻量覆盖层（状态灯 + 波形/部分文本 + 打断按钮） |

### 修改点

| 文件 | 改什么 |
|---|---|
| `AndroidManifest.xml` | 加 `RECORD_AUDIO` |
| `ui/chat/ChatScreen.kt` | 输入行左侧加 VoiceButton；识别部分结果直接进输入框（所见即所发）；语音指令就地执行不发送 |
| `MainActivity.kt` / `ChatConsole` | 接 VoiceController；指令"新开会话"清 entries + ContextManager；"打开设置"导航 |
| `runtime/AgentRuntime.kt` | 暴露 `newSession()`（清 ContextManager 历史）；TTS 状态回调查询 |
| `res/values*/strings.xml` | 指令词表、权限说明、错误文案（中英成对） |
| `res/xml/` | `SpeechRecognizer` 可用性探测无 config 需求；Vosk 模型下载白名单 URL 常量 |

### 驾驶模式（与语音绑定的安全壳）

| 文件 | 改什么 |
|---|---|
| `system/CarUxRestrictionsReader.kt` | 读 `CarUxRestrictionsManager`，暴露 `isRestricted` StateFlow（能力不可用降级为 false） |
| `ui/home/HomeScreen.kt` | 行驶中：磁贴/行点击目标放大、隐藏终端与工程师入口（design.md §9.2），突出语音按钮 |

## 步骤

### Phase A — 单轮语音 + 语音指令 + TTS（本期）

1. **权限与探测**：`RECORD_AUDIO` 运行时申请；`SpeechRecognizer.isRecognitionAvailable` 探测，不可用→按钮禁用态+原因文案（能力缺失规则）。
   验收：无语音服务环境（可 `pm disable` Google 语音模拟）下界面为禁用态而非假按钮。
2. **VoiceController 状态机**：ASR 流式部分结果 → 输入框；最终结果 → `VoiceCommandParser`：命中指令就地执行；否则按现有 `runtime.send` 走引擎。
   验收：`VoiceCommandParser` 单测覆盖中英词表、误触发（"使用技能"无参数）、大小写；`assembleDebug` 通过。
3. **TTS 播报**：`AgentEvent.Completed` 文本 → TTS（开关存 DataStore，默认开）；音频焦点申请/释放。
   验收：模拟器（宿主麦克风）说"你好"→ 识别上屏 → 发送 → 回复播报可听；开关关闭后不播报。
4. **指令第一批**：新开会话（清 entries+历史）、打开/关闭设置、返回首页、使用技能 X（命中现有 `SkillRegistry.match` 路径）、使用 MCP（列出并引导选择来源）。
   验收：模拟器逐条说指令，行为与点按一致；语音指令执行有可见回执（灯点+文案）。
5. **驾驶可用性**：聊天页麦克风按钮 ≥56dp；`CarUxRestrictionsReader` 就绪后（本 Phase 只接读取，不改布局）记录 restricted 状态到日志/状态行。
   验收：AAOS 模拟器 `adb shell dumpsys car_ux_restrictions` 模拟 RESTRICTED，状态流转正确。

### Phase B — 连续对话 + 离线唤醒

> 已拆解为子计划，实施按子计划推进（父计划不直接开工）：
> [voice/step-b1-continuous-loop-plan.md](./voice/step-b1-continuous-loop-plan.md)、
> [voice/step-b2-vosk-wakeword-plan.md](./voice/step-b2-vosk-wakeword-plan.md)、
> [voice/step-b3-driving-mask-plan.md](./voice/step-b3-driving-mask-plan.md)。
> 实施顺序 B1 → B3 → B2（先可测的，把原生依赖与 42MB 模型下载放最后）。

1. **连续对话回路**：Speaking 结束 → 自动 Listening（带 1s 防自听静默）；说话打断 TTS（barge-in：检测到人声即 `TextToSpeech.stop()`）；超时 8s 无语音回 Idle。
   验收：模拟器连续三轮问答不碰屏；播放媒体（音频焦点被夺）时回路自动挂起、焦点回来自动恢复。
2. **Vosk 离线唤醒**：首启引导页下载中文小模型（约 50MB，Wi-Fi 提示）；KWS 服务常驻前台服务（复用 `AgentForegroundService` 进程）唤醒词"你好副驾"；唤醒 → 打开聊天页并进入聆听。
   验收：熄屏（车机屏常亮场景按屏幕状态）喊唤醒词 3/5 次内响应；误唤醒 <1 次/10 分钟（模拟器上以音频文件灌入测试）。
3. **驾驶遮罩**：`CarUxRestrictions=RESTRICTED` 时全屏语音态（design.md §6.5）：状态灯 + 大麦克风（≥72dp）+ 返回首页，隐藏一切文本操作入口。
   验收：模拟 RESTRICTED 全 UI 走查；触控目标全部 ≥72dp。

### Phase C — 车机深度（厂商合作路径，不在本期承诺）

- `VoiceInteractionService` 系统级热词（需平台签名，五菱/厂商路径）
- 多说话人区分（主驾/乘客）、车内多音区
- AOC 侧语音路由（远程会话时音频不出车、文本出车的隐私边界）

## 验收清单（Phase A 出口）

- [~] 模拟器全链路：说 → 识别上屏 → 发送 → 回复 → TTS 播报，全程不碰键盘
      **部分验证**：点麦克风确实起了系统识别器并回到界面回执；模拟器无麦克风音频
      （emulator 日志 `Voice is not capturing`），"说→出字"这一段只能真机验。
- [x] `VoiceCommandParser` 单测 ≥10 用例全绿（10 例）；另加状态机 16 例，共 26 例；`:app:testDebugUnitTest` 全绿
- [x] 无语音服务/未授权环境渲染禁用态 + 原因（实测：未授权时按钮灰、状态行显示"未授予麦克风权限，语音不可用"）
- [~] 音频焦点被导航/媒体抢占时识别暂停、恢复后继续
      **仅代码接通**（播报侧成对申请/放弃焦点），未用真实媒体流验证。
- [x] 新增文案中英成对；`assembleDebug` 通过
- [x] 行驶模拟（RESTRICTED）下麦克风按钮 ≥72dp、无键盘依赖 —— Phase B.3 交付（2026-10-03，实测 bounds 72px@160dpi）

## 进度

- [x] 现状盘点（语音零实现、CarUxRestrictions 未接入）
- [x] 方案决策记录（ASR/TTS/KWS/指令解析/音频焦点）
- [x] Phase A 实施 — 2026-10-02（用户确认排期后开工）
- [x] Phase B 子计划拆解 — 2026-10-03（`dev-plans/voice/` 三份）
- [x] Phase B 实施 — 2026-10-03（B1 连续对话回路 / B3 行驶遮罩 / B2 离线唤醒，见 `voice/` 三份归档）
- [ ] Phase C

## 归档

**Phase A 完成日期：** 2026-10-02

**交付：** `core/voice`（指令模型 + 纯函数解析 + 通道接口）、`system/voice`（SpeechRecognizer / TextToSpeech
封装 + 焦点）、`ui/voice`（状态机 + 灯语麦克风控件 + 状态行 + 词表装配）、`data/store/VoiceSettingsStore`、
`system/CarUxRestrictionsReader`（只读）；对话页输入行接麦克风，设置页加「语音」两张开关。

**实测记录（AAOS API 36 x86_64 模拟器）：**
- 未授权：按钮灰 + 状态行"未授予麦克风权限，语音不可用"（禁用态带原因，未伪造能力）。
- 授权后：按钮点亮（蓝色话筒 + 强调描边），点按起了系统识别器，回落到"麦克风被占用，稍后再试"回执，
  状态回 IDLE 不卡死。
- 设置页「语音播报回答」「连续对话」开关渲染正常，拨动后离开再进仍保留（DataStore 往返）。
- **坑**：AAOS 是双用户（user 0 / user 10），应用跑在 user 10，`adb shell pm grant` 默认只授 user 0，
  症状是"授了权界面还说没权限"，要 `pm grant --user 10`。

**遗留问题：**
- 「说→出字」与播报可听需真机或带麦克风环境验证；本地指令 5 条动作已接通但未逐条语音实测。
- 打断（barge-in）目前是"点按打断"，未做检测到人声自动停播（需要一路常听，与隐私边界冲突，另案）。
- 唤醒词（Vosk 离线 KWS + 首启模型下载）与行驶全屏遮罩属 Phase B，本期未动。
- 权限弹窗在本 AAOS 镜像不弹出（车机通常由厂商授权），授权路径未在界面侧验证，仅验证了已授权/未授权两态。

**回写机制文档：** 新增 `../mechanisms/voice-interaction.md`，并同步 `mechanisms/README.md` 与
`.agent-rules/README.md` §6 双索引。

---

**Phase B 完成日期：** 2026-10-03

**交付：** 连续对话回路（1s 防自听静默、8s 无语音收工、人声打断默认关、焦点 LOSS/GAIN 挂起恢复）；
音频焦点从播报侧上移为**会话级** `VoiceFocusHandle`；行驶受限全屏语音遮罩（≥72dp，实测 bounds）；
Vosk 离线唤醒「你好副驾」（42MB 模型首启下载 + 前台服务常驻 KWS + 唤醒分发跳对话页）；
驾驶态从布尔改三态并接进首页与遮罩。子计划归档见 `voice/step-b1-continuous-loop-plan.md`、
`voice/step-b2-vosk-wakeword-plan.md`、`voice/step-b3-driving-mask-plan.md`。

**实测账（AAOS API 35 x86_64 模拟器，160dpi）：**
- 单测 173 例全绿（状态机 28、指令解析 10、唤醒判定 13）；`assembleDebug` 通过。
- 模型下载链路跑通（模拟器 guest 无路由，靠 `adb reverse` 借宿主代理出网）：进度 100% → "唤醒模型已就绪"。
- 开唤醒后通知写明在听什么；关唤醒 PSS 146MB / 开唤醒 215MB（基线 <200MB，**开启态超约 15MB**，
  取舍为默认关 + 关即释放）。
- 遮罩走查用启动参数 `--ez com.aos.agent.extra.PREVIEW_DRIVE_RESTRICTED true` 驱动同一套布局。

**仍未验证（必须真机或有麦环境）：** 说→出字、播报可听、唤醒命中率 3/5 与误唤醒率、人声打断、
真实 `CarUxRestrictions` 翻 RESTRICTED（user 版镜像屏蔽 `cmd car_service`，VHAL 写不进去）。

**回写机制文档：** `../mechanisms/voice-interaction.md` 重写为 Phase A+B 全链路（含焦点只有一份、
遮罩必须吃手势、常驻麦要显式开、内存实测四条硬约束）。

## 不在本期做的事

- 不做唤醒词（Phase B）、不做多说话人/音区（Phase C）、不做系统签名热词（Phase C）
- 不上传任何音频；识别走系统 Google 服务（隐私边界在 Phase A 文案中说明）
- 不做 TTS 音色定制、不做打断手势之外的旋钮交互
- 不改 AOC 协议
