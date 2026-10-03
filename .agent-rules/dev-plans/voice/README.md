# 语音 Phase B 子计划

父计划：[../voice-interaction-plan.md](../voice-interaction-plan.md) 的 Phase B（连续对话 + 离线唤醒 + 驾驶遮罩）。
Phase A（单轮语音 + 语音指令 + TTS）已于 2026-10-02 交付，本目录只做 Phase B 的三步拆解。

| 子计划 | 父计划步骤 | 可验证上限 |
|---|---|---|
| [step-b1-continuous-loop-plan.md](./step-b1-continuous-loop-plan.md) | Phase B.1 连续对话回路 | 单测全覆盖；模拟器无麦克风音频，人声相关只能真机 |
| [step-b2-vosk-wakeword-plan.md](./step-b2-vosk-wakeword-plan.md) | Phase B.2 离线唤醒词 | 模型下载/加载/KWS 循环/内存实测可验；唤醒命中率不可验 |
| [step-b3-driving-mask-plan.md](./step-b3-driving-mask-plan.md) | Phase B.3 驾驶遮罩 | 模拟器 `dumpsys car_service` 模拟行驶态可走查 |

实施顺序 B1 → B3 → B2：先做纯状态机与纯 UI（可测、可回归），把引入原生依赖与 42MB 模型下载的 B2 放最后，
避免底座未稳时叠加外部风险。
