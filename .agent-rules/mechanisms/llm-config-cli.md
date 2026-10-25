# 模型服务配置 CLI（aos-llm）

## 结论

应用内终端里用 `aos-llm <命令>` 快速配置/切换 LLM 供应商与模型：写操作经非 exported 显式广播写入应用私有 DataStore（配置真源不变），读操作读应用生成的 shell 快照文件，装车时免去触屏长输入。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| core | `core/llm/LlmProfiles.kt` | 档案模型、JSON 编解码（手工，无序列化插件）、`LlmCliParser`（广播解析）、`LlmProfiles.apply`（纯状态转移） |
| data | `data/store/LlmProfileStore.kt` | 档案地址簿 DataStore（`llm_profiles`）+「当前档案名」提示位 |
| data | `data/store/LlmConfigStore.kt` | 「当前生效」配置真源（本机制只写它，不改结构） |
| system | `system/llm/LlmCliInstaller.kt` | assets 模板 → `filesDir/bin/aos-llm` 幂等安装 |
| system | `system/llm/LlmStateMirror.kt` | 生成 `files/llm/state.sh`（可 source 快照，Key 掩码） |
| receiver | `receiver/LlmConfigReceiver.kt` | 非 exported 显式广播接收器，goAsync 协程写存储 |
| terminal | `terminal/TerminalViewModel.kt` | 会话环境 `PATH` 前插 `filesDir/bin`；建会话前调安装器 |
| service | `service/AgentForegroundService.kt` | onCreate 幂等安装脚本 + 刷镜像 |
| runtime | `runtime/AgentRuntime.kt` | `saveLlmConfig`（界面保存路径）后清档案提示位 + 刷镜像 |
| assets | `assets/bin/aos-llm.sh` | CLI 脚本模板（`@PKG@` / `@BIN_DIR@` 占位符） |

## 运行链路

写（以 `aos-llm use work` 为例）：

```
终端 shell（应用 uid fork）
  └─ aos-llm（filesDir/bin，PATH 已前插）
      └─ am broadcast -n com.aos.agent/.receiver.LlmConfigReceiver -a com.aos.agent.llm.USE_PROFILE --es name work
          └─ LlmConfigReceiver.onReceive → goAsync
              ├─ LlmCliParser.parse → LlmCommand.UseProfile
              ├─ LlmProfileStore.profiles + LlmConfigStore.current → LlmCliState
              ├─ LlmProfiles.apply → LlmCliOutcome.Ok（新档案列表 + 新 active + 提示位）
              ├─ 写 LlmConfigStore（真源）+ LlmProfileStore（地址簿/提示位）
              ├─ LlmStateMirror.refresh() 重建 state.sh（先写 tmp 再 rename）
              └─ pendingResult.setResultData(结果消息) + finish() → am 才返回
                  └─ 脚本收到 "Broadcast completed" → 自动跟 show_status（读 state.sh，已是新状态）
```

读（`aos-llm status` / `list`）：脚本 `source files/llm/state.sh` 输出；镜像在每次配置变更（CLI、设置页、服务启动）后重建，脚本不做网络/存储直读。

Agent 引擎侧零改动：`AgentRuntime.send()` 每次发送前重读 `LlmConfigStore.current()`，切换配置下一条对话即生效。

命令面：`status`（默认）/ `list` / `set --url --model [--key|--keep] [--name]` / `add <name> --url --model [--key]` / `use <name>` / `rm <name>` / `sync` / `help`。`--keep` = 未显式给出的字段（URL/模型/Key）沿用当前配置（只换模型的场景免重输 URL）。别名 `aosllm` 与 `aos-llm` 同内容：车载拼音键盘常把半角 `-` 转成全角 `－` 导致命令名打不出来。

脚本要点：`send()` 按应用自身 uid 计算所属用户（`id -u` / 100000）**显式传 `--user`**——应用 uid 下 `am` 的 USER_CURRENT 解析会被 INTERACT_ACROSS_USERS 拒绝（API 36 车机镜像实测）；接收器把执行结果（成功或错误原因）放在回执 data 里，脚本提取回显后自动跟 `status`（广播按顺序派发、接收器 `finish()` 后 `am` 才返回，读到的是已落地状态）。

## 使用点

- 应用内终端（主入口）：用户敲 `aos-llm`（或别名 `aosllm`），车机上免去触屏长输入
- `AgentForegroundService.onCreate` / `TerminalViewModel.start`：幂等安装脚本
- `AgentRuntime.saveLlmConfig`：设置页保存后同步镜像与提示位
- adb 直发接收器：**API 36 模拟器实测不可用**——shell（uid 2000）发非 exported 显式广播返回
  `Broadcast completed: result=0` 但接收器不执行（静默丢弃、无 SecurityException、无回执 data）；
  电脑侧调试用 `adb shell run-as com.aos.agent --user 10 sh files/bin/aos-llm <命令>`（debug 包）等效驱动
  （实测全链路可用）；release 包车机只能走应用内终端，真车行为待装车复验

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 加 CLI 子命令 | `assets/bin/aos-llm.sh` | 新 case 分支拼广播参数；接收器侧同步加 action 与解析 |
| 加写操作 | `LlmCliParser` + `LlmProfiles.apply` | 先扩纯逻辑与单测，再接接收器；脚本只拼 extras |
| 改镜像字段 | `LlmStateMirror.write` | 保持单引号转义与 Key 掩码；变量名前缀 `AOS_LLM_` |
| 改脚本安装路径 | `LlmCliInstaller` | 同步改 `TerminalViewModel` 的 PATH 前插 |

## 关键约束

1. **真源唯一**：「当前生效」配置只有 `LlmConfigStore`；`use` 是复制不是引用。任何新写入路径都必须在完成后调 `LlmStateMirror.refresh()`，否则终端读到的状态漂移。
2. **接收器保持 `exported="false"`、无 intent-filter**；**脚本必须显式 `--user`**（按 `id -u`/100000 计算）：应用 uid 下 `am` 的 USER_CURRENT 解析实测被拒，而 shell 直发则被静默丢弃——两条路都只有显式指定应用自身 user 才通。
3. **API Key 全链路掩码**：state.sh、脚本输出、广播 resultData 只允许 `set(len=N)`/`empty` 形式；明文只允许存在于 DataStore 与请求头。
4. **档案名 `[a-z0-9][a-z0-9_-]{0,31}`**：它是 state.sh 变量名片段，放宽即注入面。
5. **手工 JSON 编解码**：本工程不引 kotlinx-serialization 插件（见 `core/update/UpdateDecision.kt` 同款约定），新数据结构沿用 buildJsonObject 手拼。
6. **profile 不携带 extraHeaders**：`use` 生成 active 时 headers 置空；带 headers 的场景仍走设置页（当前 UI 也不暴露 headers）。车载触屏键盘（拼音 IME）会把 `-.` 等转成全角，URL 现场键入易错——长值优先走设置页或外接键盘，CLI 侧不为此做兼容。

## 维护方式

- **新增**：改动后验证顺序 = `:app:testDebugUnitTest` 全绿 → 模拟器开 App（服务起，装脚本）→ 终端/run-as 驱动 `aos-llm set/use/rm` 全链路 → 对话页确认新配置生效。
- **排查**：脚本报"广播未送达"= 应用进程没在运行或接收器没注册（查 Manifest）；`status` 显示旧值 = 有人写配置没刷镜像（查约束 1）；`aos-llm: not found` = 安装失败（查 `LlmCliInstaller` 日志 tag `AOSAgent.LlmCli`）；命令有回执但没生效 = 看回执里的错误原因（接收器消息会如实回显）。
- **模拟器/调试驱动**：`adb shell run-as com.aos.agent --user 10 sh files/bin/aos-llm <命令>`（debug 包，与终端同 uid 等效）；注意模拟器是 AAOS 双用户，包跑在 user 10。触屏拼音 IME 会把半角转全角，`adb shell input text` 无法可靠键入命令，用 run-as 替代。

> 更新时间：2026-10-25（模拟器全链路实测；adb 直发结论、--user 显式、别名与 keep 语义回写）
