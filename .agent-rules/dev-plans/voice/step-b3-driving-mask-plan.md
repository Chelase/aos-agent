# 语音 B3：驾驶全屏语音遮罩

> **统一愿景对齐**：与 `../../docs/unified-ecosystem-vision.md` 一致。遮罩是车机本地 UX 层的安全壳，
> 不新增跨设备能力；行驶中把交互收窄到"语音 + 一个返回首页"，与 AOC 单一人格出口不冲突。
>
> 父计划：[../voice-interaction-plan.md](../voice-interaction-plan.md) Phase B.3
> 设计依据：`../../docs/design-agent-handoff.md` §驾驶模式约束（隐藏终端入口、触控 > 72dp、限制信息量）
> 配套机制文档：`../../mechanisms/voice-interaction.md`、`../../mechanisms/ui-design-system.md`

## 当前状态

`system/CarUxRestrictionsReader.kt` 已读 `activeRestrictions != UX_RESTRICTIONS_BASELINE` 并暴露
`restricted: StateFlow<Boolean>`，但**没有任何布局消费它**（Phase A 明确只接读取）。
`AOSSizing.touchTarget = 56dp` 是全应用下限，没有驾驶态档位。

## 本 Step 范围

行驶受限（RESTRICTED）时，非首页一律盖一层全屏语音态；首页在受限态下裁掉文字操作入口。

| 决策 | 理由 |
|---|---|
| 遮罩只在"已离开首页"时盖满，"返回首页"即解除 | 父计划要求"隐藏一切文本操作入口"，但首页本身没有文本输入；把首页也糊掉会让用户以为应用坏了 |
| 遮罩只做三件事：状态灯 + 大号麦克风（≥72dp）+ 返回首页 | design-agent-handoff 的"限制信息量/限制复杂手势"；不做可滚动内容 |
| 首页受限态：隐藏终端与工程师模式入口 | handoff 明确"行驶中禁止 shell 操作" |
| 新增 `AOSSizing.driveTarget = 72.dp` token，不在 UI 里写 72dp 字面量 | 项目规则：尺寸只走 token |
| 语音能力缺失时遮罩必须显示禁用态 + 原因，且保留返回首页 | 能力缺失规则；行驶中不能出现"既不能用又没说明"的死界面 |

## 改动文件

| 文件 | 动作 |
|---|---|
| `ui/theme/AOSSizing.kt`（或 token 所在文件） | 新增 `driveTarget = 72.dp` |
| `ui/voice/VoiceControls.kt` | `VoiceMicButton` 增 `targetSize` 参数（默认 `touchTarget`），遮罩传 `driveTarget`；状态行字级放大走既有 typographic token |
| `ui/voice/DriveVoiceMask.kt` | 新增 — `DriveVoiceMask(state, onMicClick, onBackHome)` 全屏层 |
| `MainActivity.kt` | 消费 `uxRestrictions.restricted`；`destination != Home && restricted` → 渲染遮罩；返回首页清遮罩 |
| `ui/home/HomeScreen.kt` | 受限态隐藏终端/工程师入口并给出原因文案（不是静默消失） |
| `res/values/strings.xml`、`values-en/strings.xml` | 遮罩标题/返回首页/入口隐藏原因，中英成对 |

## 步骤

### B3.1 token 与按钮尺寸档位
`driveTarget` 落 token；`VoiceMicButton` 的 `heightIn(min=)` 改为参数，默认值不变（Phase A 界面无回归）。
**验收：** 单测/走查默认档仍 56dp；遮罩档 ≥72dp。

### B3.2 遮罩层与接线
`DriveVoiceMask`：背景取主题 surface（日间浅遮罩、夜间深遮罩，design.md §6.5），
仅状态灯 + 大麦克风 + 返回首页；点击麦克风走 `voice.toggle()`，识别文本只回状态行不铺开。
`MainActivity` 用 `collectAsStateWithLifecycle()` 读 restricted 并叠在 `when(destination)` 之上。
**验收：** 模拟器 `dumpsys car_service` 置行驶受限 → 非首页立刻出遮罩；解除限制 → 遮罩消失，原页面不丢状态。

### B3.3 首页裁剪
受限态首页：终端/工程师磁贴改为禁用并显示"行驶中不可用"，或直接隐藏（取禁用+原因，符合能力反馈规则）。
**验收：** 受限/非受限两态截图对比；触控目标尺寸实测。

## 验收清单

- [ ] 模拟 RESTRICTED 全 UI 走查：非首页只见遮罩三要素，无文本输入入口
- [ ] 遮罩内全部触控目标 ≥72dp（实测，不是"看着像"）
- [ ] 解除限制后回原页面状态不丢（对话条目、终端会话）
- [ ] 语音不可用（无服务/无权限）时遮罩仍给出原因 + 返回首页可用
- [ ] 中英成对；两主题配色只走 token；`assembleDebug` + `testDebugUnitTest` 绿

## 进度

- [ ] B3.1 token 与按钮尺寸档位
- [ ] B3.2 遮罩层与接线
- [ ] B3.3 首页裁剪

## 不可验证项（先说清楚）

- 模拟器 AAOS 镜像能否注册 `CarUxRestrictionsManager` 监听要实测：`registerListener` 需要厂商授权，
  拿不到时会降级为"不限制"。若如此，本 Step 的走查改用工程师模式内的"驾驶态预览"开关驱动同一套布局，
  并在文档里标明真实触发链路未在模拟器验证。
