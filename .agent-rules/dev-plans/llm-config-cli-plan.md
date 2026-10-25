# LLM 配置 CLI（aos-llm）：装车现场快速配置/切换模型服务

> **统一愿景对齐**：与 `../docs/unified-ecosystem-vision.md` 一致。
> - 原则五「轻内核，能力外挂」：CLI 是终端与配置存储之间加的一个来源适配，`AgentEngine` / `OpenAiCompatibleProvider` 零改动；换配置只是写入既有 `LlmConfigStore`。
> - 「任意车机可运行」：只依赖 `/system/bin/am`（Android 自带）与应用自身 uid，不依赖 root、特权权限或 AOC。
> - 「缺失就明说」：API Key 在终端与镜像文件里只出现掩码；未配置时如实提示原因，不伪造状态。
>
> 配套机制文档：`../mechanisms/llm-config-cli.md`（随实现同步生成，复杂跨模块功能强制规则）。
>
> 参考来源（skill-routing 规则 3）：CLI 进终端 PATH 的做法参考 **Termux**（app 私有 bin 目录挂 PATH + 脚本从 assets 装到 filesDir）；跨进程配置指令走显式广播（非 Termux RUN_COMMAND 的 exported + 权限模式，理由见机制文档约束）。不引入第三方 CLI 框架。

## 背景与目标

装车调试时触屏输入长 URL / API Key 不现实。目标：在车内可用两条路配置/切换模型服务——

1. **应用内真实终端**（必然可用，release 同样可用）：`aos-llm <命令>`，经应用自身 uid 的显式广播写配置。
2. **车外 adb**（可用性取决于系统策略，作为便利项）：`adb shell am broadcast -n com.aos.agent/.receiver.LlmConfigReceiver ...`，接收器保持 `exported="false"`，shell 能否送达**未核实**，实车验证后回写文档；不给任何第三方 App 开口子。

## 方案要点

- **Profile（配置档案）= 地址簿，不引入第二真源**：新增 `llm_profiles` DataStore 存命名档案（name/baseUrl/model/apiKey）；「当前生效」仍然只有 `LlmConfigStore` 一份（UI、AgentRuntime、设置页全部不动）。`use <name>` = 把档案复制进当前配置。
- **读写通道分离**：写 = 显式广播 → `LlmConfigReceiver`（goAsync + 协程写 DataStore）；读 = 应用在每次配置变更后生成 `files/llm/state.sh`（可 source 的 shell 快照，Key 只写掩码），脚本 `status/list` 直接读它，不绕回广播。
- **脚本安装**：assets 模板（占位符替换）装到 `filesDir/bin/aos-llm`（chmod 755，内容一致则跳过）；终端会话的 `PATH` 前插 `filesDir/bin`。安装触发点：前台服务启动 + 终端建会话前（双保险，均幂等）。

## 改动范围

| 层 | 文件 | 改动 |
|---|---|---|
| core | `core/llm/LlmProfiles.kt`（新增） | `LlmProfile` 数据类、编解码（kotlinx JSON）、名称校验、`LlmProfileOps` 纯函数命令执行（add/use/set/rm → 新状态 + 结果消息） |
| data | `data/store/LlmProfileStore.kt`（新增） | `llm_profiles` DataStore：档案 JSON + 「当前档案名」提示位 |
| system | `system/llm/LlmCliInstaller.kt`（新增） | assets 模板 → `filesDir/bin/aos-llm`，幂等安装 |
| system | `system/llm/LlmStateMirror.kt`（新增） | 生成 `files/llm/state.sh`（单引号转义，Key 掩码） |
| receiver | `receiver/LlmConfigReceiver.kt`（新增） | 非 exported 显式广播接收器，解析 extras → 纯命令 → 写存储 → 刷镜像 |
| terminal | `terminal/TerminalViewModel.kt` | `PATH` 前插 `filesDir/bin`；建会话前调安装器 |
| runtime | `runtime/AgentRuntime.kt` | `saveLlmConfig` 后刷镜像（UI 保存路径也归一） |
| service | `service/AgentForegroundService.kt` | onCreate 里幂等安装脚本 + 刷镜像 |
| manifest | `AndroidManifest.xml` | 注册非 exported 接收器（无 intent-filter） |
| assets | `assets/bin/aos-llm.sh`（新增） | CLI 脚本模板（POSIX sh，toybox 兼容） |

## 命令面（v1）

```text
aos-llm                       # = status：当前生效配置 + 提示（别名 aosllm，IME 友好）
aos-llm status
aos-llm list                  # 已存档案（Key 恒为掩码）
aos-llm set  --url U --model M [--key K] [--name P] [--keep]
                              # 直接设当前；带 --name 同时存为档案；
                              # --keep = 未给出的字段（URL/模型/Key）沿用当前配置
aos-llm add  <name> --url U --model M [--key K]       # 新增/覆盖档案（不动当前配置）
aos-llm use  <name>          # 档案 → 当前生效配置（下一条消息即生效）
aos-llm rm   <name>
aos-llm sync                 # 强制重建 state.sh（自愈用）
```

写操作完成后脚本自动跟一条 `status`（广播按顺序派发、接收器 `finish()` 后 `am` 才返回，读到的是已落地状态）；接收器的成功/错误消息经回执 data 如实回显。

## 步骤

1. **纯逻辑层** `LlmProfiles.kt` + JVM 单测：名称规则 `[a-z0-9][a-z0-9_-]{0,31}`、upsert/rm/use/set 的状态转移、非法输入返回错误消息不抛出。
2. **存储与镜像** `LlmProfileStore` / `LlmStateMirror` + 镜像转义单测（值含单引号）。
3. **通道与装配**：接收器 + manifest + Runtime/Service/TerminalViewModel 接线 + assets 脚本。
4. **验证**：`:app:testDebugUnitTest` 全绿（含存量 223 例）；AAOS 模拟器走查——终端里 `aos-llm set/use/status/rm` 全链路 + 对话页确认新配置生效 + adb 广播路径实测（可/不可都如实回写）。

## 验收清单

- [x] 终端通道：`aos-llm set`（含 `--name`）→ 配置与档案落库、`status` 立即可见 —— 模拟器实测
      （以 `run-as --user 10` 驱动，与应用内终端同 uid 等效；真实触屏打字受拼音 IME 全角转换限制，
      见机制文档约束 6）
- [x] `add`（别名 aosllm）/`list`/`use` 来回切换/`rm`/`sync` 全链路 —— 模拟器实测
- [x] `set --keep` 只换模型时 URL/Key 沿用、`--name` 缺省时档案提示如实消失 —— 模拟器实测
- [x] API Key 全链路掩码：脚本输出、state.sh、广播回执均无明文（state.sh 终态 cat 核查）
- [x] 命名/参数非法给原因不崩：`use nope`/`add` 缺名/`set` 缺参实测；档案名校验单测钉住
- [x] 单测全绿：246 例（含存量 223 与本专项 23 例）
- [x] adb 直发结论回写机制文档：API 36 模拟器实测**不可用**（静默丢弃），run-as 等效路径可用；
      真车复验待装车
- [ ] 设置页改动后 `aos-llm status` 同步（UI 驱动未做，代码路径已接线并 review；随下次界面走查覆盖）

## 不在本期做的事

- 连通性测试命令（`aos-llm test` 打一次真实 chat/completions）——需要网络层参与，单独立项。
- Profile 携带 extraHeaders / 多模型并存——现 UI 也不暴露 headers，跟随现状。
- MCP 服务源、语音设置的 CLI 化——同一通道未来可复用，本期只做 LLM 三元组。
- exported 接收器 / 自定义权限——不给第三方 App 配置入口；shell 直达与否以实测为准。

## 进度

- [x] Step 1 纯逻辑层 — 2026-10-25（`LlmProfiles` + `LlmCliParser`，JVM 单测）
- [x] Step 2 存储与镜像 — 2026-10-25（`LlmProfileStore` / `LlmStateMirror`，含单引号转义 source 验证）
- [x] Step 3 通道与装配 — 2026-10-25（接收器 + Manifest + Terminal PATH + Runtime/Service 接线 + assets 脚本）
- [x] Step 4 验证与文档回写 — 2026-10-25：246 例单测全绿（含存量 223）；AAOS API 36 模拟器实测全链路
  （run-as 等效驱动 + 真实终端页打开验证 PATH/安装）。实测发现并当场修复三处：
  ① 应用 uid 下 `am` 的 USER_CURRENT 解析被拒 → 脚本按 `id -u`/100000 显式 `--user`；
  ② `add` 位置参数被当未知 flag → 解析前先收档案名；
  ③ 接收器回执（含错误原因）被脚本吞掉 → 提取 `data="..."` 回显。
  另发现 adb shell 直发非 exported 接收器被静默丢弃（结论回写机制文档）；拼音 IME 全角转换
  促使加装无连字符别名 `aosllm` 并把 `--keep` 升级为「未给出字段全部沿用」。
  **真车待验**：应用内终端在实车 IME/键盘下的可用性、对话页用 CLI 写入的配置实际发消息。

## 归档

（完成后回填：完成日期、最终冒烟结果、遗留问题、回写的机制文档更新）
