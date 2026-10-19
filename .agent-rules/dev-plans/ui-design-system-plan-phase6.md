# v6 七页原型落地

> 与 `../docs/unified-ecosystem-vision.md` 对齐：只改变原生 UI 表达，Agent 内核及生态协议保持现状。
> 配套机制：`../mechanisms/ui-design-system.md`。视觉依据：`.impeccable/mocks/v6/` 七页 HTML。

## 当前阶段

用户已指定七页原型并要求工程化复用。采用原型的 Material 导航轨、蓝灰双主题、指标卡、对话气泡、双栏设置、诊断卡及全屏驾驶遮罩。原型中的车辆与会话数字只表示布局，产品仍展示真实数据和缺失原因。

## 改动范围

- `ui/theme/`：v6 语义色、字号和尺寸。
- `ui/components/`：共享导航外壳、标题、承载面与控件。
- 六个目的地及 `DriveVoiceMask`：接入共享视觉 Module。
- `res/drawable/`、`mipmap-anydpi/`：首版环形灯组的蓝白改色自适应图标及单色版本。
- `res/values*/strings.xml`：新增文案成对维护。

## 步骤

1. 以 v6 原型覆盖旧 token；保留 Material 角色和扩展状态语义。
2. 在现有导航 seam 装配共享外壳 Module，页面不复制导航 implementation；驾驶遮罩位于外壳之上。
3. 深化已有承载面 Module，统一卡片、指标、标题和数据行；页面保留采集、终端及诊断的既有生命周期。
4. 接入原生矢量图标；构建、现有单测与 AAOS 模拟器检查。

工程参考：Pi 的 UI 与无头内核分离模式、Claude Code 的单点装配模式（见 `agent-capabilities.md`）；本次只借工程模式。导航轨与触控遵循 Android Material，属平台实现，四个 Agent 项目不承担原生布局参考。无需新增业务 Interface。

## 验收清单

- [x] Debug APK 与单测通过（223/223）
- [x] 六目的地及驾驶遮罩原生截图
- [x] 双主题、英文、大字体和关闭动画检查（1920×1080 / 1408×792，160dpi）
- [x] 系统 Back、受限禁用与图标接入检查
- [ ] 独立 reviewer 最终终审（额度限制；本地替代复核已完成）

## 不在本期做的事

不实现原型中的车辆写入示例、模型 token 用量估算和未存在的工具能力；不重划 Agent Core 或设备协议。

## 进度

- 2026-10-18：已完成 v6 token、共享外壳/承载面、六目的地、行驶遮罩和同源原生图标接入；中英资源同步。
- 首轮原生截图覆盖 1920 日/夜七页及 1408 英文/字体 1.3 六页；合成对话 fixture 单独标注。首轮两项修正已落地：气泡按内容扩张、跨导航保留会话/草稿。
- ChatConsole 保持组合；SaveableStateHolder 保存页面 UI 状态和未发送输入；SettingsDraft 仅在根组合内存保留，API Key 不进 saved bundle，不承诺对话跨 Activity 重建恢复。
- 机制、映射设计规范、设计面板 sidecar、surface brief 与图标来源说明已同步；223 单测及 13 个不同 UI 用例通过；第二轮21张截图本地复核完成。独立 reviewer 最终轮受额度限制中断，未记为独立终审通过。

## 交付验证

2026-10-18：Debug APK 已生成。UI 用例覆盖保存/MCP、导航和 Back、驾驶禁用/遮罩、消息发送/麦克风授权，以及本地 HTTP/SSE 在途请求和两类草稿跨 rail 保留。测试配置已恢复，真车未验证。最终截图及复核记录在 `.impeccable/review/` 本地留存。App 图标母版和预览在 `artwork/`，原生 launcher/monochrome 已接入。

2026-10-18 图标最终修订：用户选定第一版图标的蓝底白色造型，并要求白色图案稍微加粗、调大。保留灯组与感知弧线布局，整体放大 7.5%，弧线基础宽度 1.8→2.16dp、顶灯半径 3.8→4.1dp、其余灯点半径 3→3.25dp；蓝色背景与白色图案保持不变。同步 SVG/PNG、launcher、monochrome 和应用内品牌标记。当前版本另存 `artwork/concepts/2026-10-18-blue-white/07*`；前述页面截图对应本次调整前图标。

图标调整验证：SVG 与三份 Android vector 路径、缩放和弧线宽度一致，主体保留在中心直径 66dp 安全区；`aapt2 compile --dir app/src/main/res` 和 `git diff --check` 通过。

2026-10-19 提交前验证：通过本机代理解决 Gradle 插件解析超时，完成 `:app:assembleDebug :app:testDebugUnitTest`；223 个单元测试全部通过。最终蓝底白色放大加粗图标已打入新 APK，安装至可见的 `aosagent-api35` AAOS 模拟器（user 10），首页正常打开，未见 AndroidRuntime 崩溃。模拟器保留运行供用户查看；本次没有重复此前 13 个 UI 用例，也没有将独立终审记为通过。
