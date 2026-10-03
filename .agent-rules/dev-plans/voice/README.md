# 语音 Phase B 子计划

父计划：[../voice-interaction-plan.md](../voice-interaction-plan.md) 的 Phase B（连续对话 + 离线唤醒 + 驾驶遮罩）。
Phase A（单轮语音 + 语音指令 + TTS）已于 2026-10-02 交付，本目录只做 Phase B 的三步拆解。

| 子计划 | 父计划步骤 | 可验证上限 |
|---|---|---|
| [step-b1-continuous-loop-plan.md](./step-b1-continuous-loop-plan.md) | Phase B.1 连续对话回路 | 单测全覆盖；模拟器无麦克风音频，人声相关只能真机 |
| [step-b2-vosk-wakeword-plan.md](./step-b2-vosk-wakeword-plan.md) | Phase B.2 离线唤醒词 | 模型下载/加载/KWS 循环/内存实测可验；唤醒命中率不可验 |
| [step-b3-driving-mask-plan.md](./step-b3-driving-mask-plan.md) | Phase B.3 驾驶遮罩 | 模拟器可走查布局与尺寸；真实 RESTRICTED 注入不了 |
| [step-b4-wake-debug-plan.md](./step-b4-wake-debug-plan.md) | B 交付后新增：唤醒调试面板 | 面板与采集开关可验；命中数据必须真机 |

实施顺序 B1 → B3 → B2 → B4：先做纯状态机与纯 UI（可测、可回归），把引入原生依赖与 42MB 模型下载的 B2 放最后；
B4 是 B2 交付后追加的观测工具，因为真机调命中率没有它就只能盲调。
