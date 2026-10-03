# 语音 B2：Vosk 离线唤醒词「你好副驾」

> **统一愿景对齐**：与 `../../docs/unified-ecosystem-vision.md` 一致。唤醒判定**全程本地推理，音频不出车机**；
> 唤醒只负责"进入聆听"，不改变 AOC 单一人格出口，不自研跨设备协议。
>
> 父计划：[../voice-interaction-plan.md](../voice-interaction-plan.md) Phase B.2
> 配套机制文档：`../../mechanisms/voice-interaction.md`、`../../mechanisms/boot-and-foreground-service.md`

## 已核实的外部事实（2026-10-03 本机探测）

| 项 | 结论 |
|---|---|
| `com.alphacephei:vosk-android:0.3.47` | Maven Central 有货（2023-03 发布，aar 12,297,565 B），**含 x86_64 与 arm64-v8a 的 `libvosk.so`**，模拟器可加载 |
| 传递依赖 | `org.vosk.*` 继承 `com.sun.jna.PointerType` → **必须另加 JNA**（`net.java.dev.jna:jna:@aar`），否则运行时 `NoClassDefFoundError` |
| 中文小模型 | `vosk-model-small-cn-0.22.zip` = 43,898,754 B（约 42MB），`alphacephei.com` 直连 200 |
| 可用 API | `Model(path)` / `Recognizer(model, sampleRate, grammar)` / `acceptWaveForm(byte[],Int):Boolean` / `getResult()` / `getFinalResult()` / `setGrammar()` / `reset()` / `close()`；`LibVosk.setLogLevel(...)` |

未核实：模型加载后的常驻内存、唤醒命中率（见"不可验证项"）。

## 本 Step 范围与四条决策

| # | 决策 | 理由 |
|---|---|---|
| 1 | 模型**打进 APK 之外**：首启引导页下载到 `filesDir/vosk/<model>`，显示进度、可重试、失败可跳过 | 42MB 不能进 APK；父计划已定；下载失败不能挡主流程 |
| 2 | 唤醒用**受限语法** `["你好副驾","[unk]"]` | 词表外一律 `[unk]`，误唤醒显著低于开放识别；命中只判 `text != "[unk]"` 且包含唤醒词 |
| 3 | KWS 常驻在 `AgentForegroundService` 进程内，**开关默认关**，且要求"模型就绪 + 有录音权限"才起 | 麦克风常开是隐私红线，必须用户显式开启；界面按能力缺失规则说明缺哪一条 |
| 4 | 音频只进内存环形缓冲做判定，**不落盘、不缓存、不进引擎**；唤醒后不保留唤醒词音频 | 隐私边界要能自证，而不只是文案承诺 |

唤醒分发：`WakeWordEngine` 命中 → 前台服务发 `Intent(MainActivity)` 带 `EXTRA_VOICE_WAKE` → 打开对话页并 `voice.startFromWake()` 直接进聆听。

## 改动文件

| 文件 | 动作 |
|---|---|
| `gradle/libs.versions.toml`、`app/build.gradle.kts` | 加 `vosk-android` + `jna@aar` |
| `core/voice/WakeWordEngine.kt` | 新增 — 接口（`available` / `start(onWake,onError)` / `stop` / `release`）+ 纯函数 `WakeWordResult.parse(json, keyword)`（可单测） |
| `system/voice/VoskWakeWordEngine.kt` | 新增 — `AudioRecord`(16kHz mono) + `Model`/`Recognizer` 循环，IO 协程；原生库缺失时 `available=false` |
| `system/voice/VoskModelInstaller.kt` | 新增 — 下载 → 校验大小 → 解压 → `READY` 状态 StateFlow（进度/失败原因可见） |
| `data/store/VoiceSettingsStore.kt` | 加 `wakeWordEnabled`；模型 URL/版本常量化 |
| `service/AgentForegroundService.kt` | 持有 KWS 生命周期：开关变化 / 权限变化 / 模型就绪三条件都满足才起，任一变即停并 `release()` |
| `MainActivity.kt` | 处理 `EXTRA_VOICE_WAKE`：跳对话页 → 进聆听；唤醒回执走状态行 |
| `ui/voice/`（唤醒引导/状态卡） | 新增首启引导：模型说明 + Wi‑Fi 提示 + 下载进度 + 失败重试 + 隐私说明 |
| `ui/settings/SettingsScreen.kt` | 唤醒开关三态（未下载 / 可开启 / 已开启）+ 原因文案 |
| `AndroidManifest.xml` | `MODIFY_AUDIO_SETTINGS`（AudioRecord 需要）；前台服务类型 `specialUse` 说明补唤醒职责 |
| `res/values/strings.xml`、`values-en/strings.xml` | 引导/进度/失败/隐私/唤醒文案，中英成对 |
| `app/src/test/.../core/voice/WakeWordResultTest.kt` | 新增 — 命中、`[unk]`、空文本、多候选、大小写/标点的纯函数单测 |

## 步骤

### B2.1 依赖与模型安装器
下载走 `okhttp`（工程已有网络栈）+ 解压；进度以 `Flow<ModelInstallState>` 暴露（`DOWNLOADING(percent)` / `READY` / `FAILED(reasonRes)`）。
**验收：** 模拟器上下完 42MB 后 `Model` 构造成功；断网/半途中断给可重试原因，不留半成品目录（先解压到临时目录再改名）。

### B2.2 KWS 引擎与内存实测
`AudioRecord` 读 4000 帧/次喂 `acceptWaveForm`；命中 → `reset()` + 回调，并做**冷却窗**（命中后 2s 内不再判定）防连发。
**验收：** 起服务后无崩溃、无 ANR；`dumpsys meminfo com.aos.agent` 实测 RSS，与常驻内存 <200MB 基线对账，超了如实写偏离与缓解（关开关即 `Model.close()` 释放）。

### B2.3 唤醒分发与引导入口
前台服务唤醒 → 对话页自动聆听；设置页/首页在"开关开但模型未下载"时给引导入口而不是静默失败。
**验收：** `adb` 手动注入唤醒事件可看到"跳页 + 进 LISTENING + 状态行回执"，全程不碰键盘。

## 验收清单

- [ ] 依赖可解析、`assembleDebug` BUILD SUCCESSFUL，APK 内 `libvosk.so`/JNA 按 ABI 就位
- [ ] 模型下载/解压/加载在模拟器实测通过（含失败重试）
- [ ] 唤醒开关关闭时零麦克风占用；开启后前台服务通知文案说明在听什么
- [ ] 常驻内存实测数值记录（基线 <200MB）
- [ ] `WakeWordResult` 单测 ≥6 例全绿；`:app:testDebugUnitTest` 全绿
- [ ] 中英成对文案

## 进度

- [ ] B2.1 依赖接入与模型安装器
- [ ] B2.2 KWS 引擎与内存实测
- [ ] B2.3 唤醒分发与引导入口

## 不可验证项（开工前就讲清）

| 项 | 为什么 | 交付口径 |
|---|---|---|
| 唤醒命中率 3/5、误唤醒 <1 次/10 分钟 | 模拟器无麦克风音频输入（Phase A 已确认 `Voice is not capturing`），无法喂真实语音 | 标"仅本地实现"，真机验收另开 |
| 模型加载后的真实内存曲线 | 可测 RSS 单点，但不是整车长时间运行数据 | 报实测数字，不外推 |
| 首启 42MB 下载的车机网络环境 | 模拟器网络与真车 TSP 出口不同 | 报模拟器结果 |

## 回滚点

唤醒是**加法式**能力：`wakeWordEnabled` 默认 false，KWS 不起则模型/引擎/服务三块对现有链路零影响。
若 B2 阻塞主流程，可只保留 `core/voice/WakeWordEngine` 接口 + 引导页，撤掉依赖与服务接线。
