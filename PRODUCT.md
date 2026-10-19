# Product

<!-- impeccable:product-schema 1 -->

> 本文件是供 Impeccable 读取的上下文快照；产品真来源为 `.agent-rules/mechanisms/requirements-analysis.md` 和 `.agent-rules/docs/unified-ecosystem-vision.md`。运行现状以对应机制文档为准。

## Platform

android

## Users

- **车主/驾驶员（首要）**：在车内使用，停车时用触控操作全部功能，行驶中只用受限的语音遮罩。诉求是"上车就能用、开车不分心"。
- **调试/适配工程师（次要）**：用工程师模式在真车上做能力探测、唤醒命中率调优、发版前走查。
- 使用场景证据：2025 款五菱星光真车实测（Android 13 / API 33 / arm64 / 1920×1080@160dpi / 单用户 user 0），App 以普通第三方 AAOS 应用身份安装运行。

## Product Purpose

AOS-Agent 是跑在 Android Automotive 车机上的常驻智能体：车辆通电即自启并在后台存活，提供 AI 对话（含工具调用与 Skill）、真实 shell 终端、系统/车辆状态感知、连续语音对话与唤醒，以及工程师诊断面板。成功意味着车主在车内有一个"一直都在、能力缺失就明说原因"的助手，而不是又一个要掏手机的 App。

## Positioning

与手机语音助手/车机自带语音的区别机制：**能力运行期探测降级**——同一份 App 在模拟器、五菱真车、未来车型上行为不同但都诚实：读不到就报"未知+原因"，缺 ASR/TTS 就渲染禁用态而不是死按钮；诊断链路（唤醒命中率观测、驾驶态诊断、自检）是产品内建能力，不是外挂工具。

## Operating Context

- 车辆通电 → `BOOT_COMPLETED` → 前台服务常驻；EV 电源循环下保存/恢复状态。
- 驾驶态三态（`CarUxRestrictions`）：UNKNOWN / UNRESTRICTED / RESTRICTED；受限时只允许语音遮罩。
- 网络不可靠：真车出现过 WiFi 断开、4G 未注册、以太网 captive portal；更新检查与 42MB 唤醒模型下载必须处理无网，离线预置是刚需。
- 真车无系统 ASR/TTS（无 RecognitionService、无 TTS 引擎、零 Google 包）：语音整条链路在该车型上是"能力缺失禁用态"。
- 电量不走 `BatteryManager`（恒 0），走 VHAL `EV_BATTERY_LEVEL`（Wh 口径，需容量换算）。
- 屏幕 1920×1080@160dpi，可视内容区 1920×992；带 `FLAG_SECURE`，取证靠 `uiautomator dump`。
- 中英双语（`values/` + `values-en/`），日夜双主题（用户手动选择并保留偏好）。

## Capabilities and Constraints

- 已落地：开机自启+前台服务、系统面板、工程师模式最小版+驾驶态诊断卡、真实 shell 终端、无头 Agent 主循环+工具系统+Skill 注册+MCP 工具源接入、Vosk 唤醒底座、版本更新检查。
- 约束：minSdk 31；触控目标 ≥72dp；行驶中禁键盘输入与复杂交互；release 包体基线 20.8MB 级；车机实测文档（含 test-keys / Permissive / root adb 信息）永不进公开仓库。
- 本次重设计：**触控优先**（停车全功能触控、行驶切语音遮罩）、**全部 6 个目的地重做**、**保留日夜双主题**。当前实现由用户手动切换并保存偏好，见 UI 机制文档。
- 未决（P0-2）：五菱车型语音路线三选一（自带离线 ASR+TTS / 接云端 / 明确不提供语音），本次重设计不替它做决定，只保证"缺失即明示"的禁用态规范延续。

## Brand Commitments

- 应用名 AOS-Agent，包名 `com.aos.agent`；当前图标为用户确认的蓝底白色环形灯组放大加粗版，原生资源和矢量母版见 `artwork/README.md`。
- 文案约束：能力缺失必须说明原因、不伪造数据——这是产品级承诺，沿用。

## Evidence on Hand

- 真车实测：`.agent-rules/docs/wuling-starlight-2025-device.md`、`wuling-starlight-2025-app-test.md`（本地留存，不入库公开仓）。
- 参考调研：`docs/参考文档/zhuanlan.zhihu.com/car-intelligence-agent/`。
- UI 取证截图：`.impeccable/review/`（本地留存，当前 v6 证据与历史对照按评审记录区分；验收状态见 phase6 计划）。
- 代码现状：`app/src/main/java/com/aos/agent/` 下 6 个目的地已实现（Home/Chat/Terminal/SystemPanel/Settings/Engineer），`ui/theme/` 有 token 基建（Color/Type/Dimens/Theme），`DesignTokensTest` 为一致性守护。

## Product Principles

1. 开车不分心：行驶中只做最少、最确定的事，其余一律延后或明示不可用。
2. 缺失就明说：任何能力不可用都渲染原因，绝不演数据、绝不留死控件。
3. 常驻可信：通电即在、掉线不崩、无网有退路，状态变化可解释。
4. 真车为准：模拟器只验逻辑，一切"能不能"以真车实测为准，诊断能力内建。

## Accessibility & Inclusion

- 驾驶场景视线安全：行驶受限态下大触控目标（≥72dp）与语音优先；TalkBack 下被遮罩盖住的页面从无障碍树摘除。
- 中英双语用户；暂无其他已确认的无障碍专项要求。
