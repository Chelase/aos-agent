# AOC 生态接入契约（Entry 模式）

## 结论

aos-agent 以 AOC Entry 节点身份通过纯 HTTP JSON 协议接入 AOC 生态（注册身份、DM 双向通道、skill 请求/回传），LLM 推理全部发生在 AOC 中心 Brain，车机端不自建推理路由。对端协议事实已调研确认；aos-agent 侧接入链路为规划态（mvp-core-plan Step 1/5 落地后更新本文档为已实现）。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| 对端 | `AgentOpenConnect/src/aoc/entry/network_client.py` | AOC 官方 Entry 客户端参考实现（仅 aiohttp，无 SDK 依赖） |
| 对端 | `AgentOpenConnect/config/network.yaml` | transport 配置：HTTP :8700（manifest）/ gRPC :8600（mTLS）；admin password_hash |
| 对端 | `AgentOpenConnect/src/aoc/mods/identity/adapter.py` | 中心侧 root_id 生成与持久化 |
| 对端 | `AgentOpenConnect/src/aoc/memory/schemas.py` | HandoffPackage / ResultPackage 契约（二期 task_delegation 用） |
| 对端 | `AgentOpenConnect/src/aoc/brain.py` | 中心 LLM Brain（OpenAI 兼容口），DM 由其 DECIDE→route_task→CONVERGE |
| 本端(规划) | `system/aoc/AocEntryClient.kt` | register/sendEvent/poll/health HTTP 客户端 |
| 本端(规划) | `system/aoc/AocConnectionManager.kt` | 注册、长轮询循环、重连退避、DM 分流 |
| 本端(规划) | `data/store/AocIdentityStore.kt` | agent_id/secret 的 DataStore 持久化 |
| 本端(规划) | `service/AgentForegroundService.kt` | 连接生命周期宿主 |

## 运行链路

```
[AOC Hub（对端，已存在，生产 MVP）]
  POST /api/register   {agent_id, agent_group, metadata, password_hash}
                       → {success, secret, assigned_group, network_name}
  POST /api/send_event {event_name:"thread.direct_message.send",
                        source_id, target_agent_id, payload, secret, event_id}
  GET  /api/poll?agent_id=&secret=  （长轮询）
                       → {success, messages[]}，下行 DM 事件为
                         thread.direct_message.notification，内容在 payload.content
  GET  /api/health     → {agent_count, agents}
  中心侧：assistant on_direct → Brain DECIDE → route_task → executor → CONVERGE → 回复 DM

[aos-agent（规划态）]
  AgentForegroundService
    └─ AocConnectionManager（Service 协程）
        ├─ 首启：AocEntryClient.register → secret → AocIdentityStore 落盘
        ├─ 重启：读 DataStore 复用 agent_id+secret（180s 重连限制见关键约束）
        ├─ 长轮询循环 → 收 DM → 分流：
        │    ├─ 文本回复 → 对话界面 / 通知
        │    └─ {type:"skill_request", skill, params}
        │         → SkillRegistry + ToolSystem 本地执行
        │         → send_event 回传 {type:"skill_result", ...}
        └─ 指数退避重连（hub 不可达时不影响本地引擎）
```

capability 声明：本地 skill 列表按 AOC skill 字段（id/name/description/tags/input_modes/output_modes）随注册 metadata 上报；完整 `set_capabilities`（全量替换语义）与 `task.notification.assigned / complete_task` 链路属二期（gRPC 执行节点）。

## 使用点

- 当前无代码调用（规划态）。落地后：`AgentForegroundService` 启动时拉起连接；`AgentChatScreen` 经其收发 DM；`SkillRegistry` 经其上报 skill 列表。

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 接入 AOC Entry | `system/aoc/` | 按 mvp-core-plan Step 1 新建 Client + ConnectionManager |
| 持久化身份/凭证 | `data/store/AocIdentityStore.kt` | DataStore 存 agent_id/secret/hub 地址 |
| 调整 DM 分流规则 | `system/aoc/AocConnectionManager.kt` | 扩展 skill_request payload 契约字段 |
| 二期升级执行节点 | `system/aoc/` + 对端 gRPC transport | 对齐 HandoffPackage/ResultPackage 与 task.notification 扁平 payload |

## 关键约束

1. 认证为 agent_groups.admin.password_hash（SHA256，配置在 network.yaml）；**不得把 admin 凭证下发到车机**，接入前需与 AOC 侧协商专用 agent_group / 入口凭证。
2. `task.notification.*` payload 是扁平结构；DM 内容在 `payload.content`——两类事件解包方式不同，不得混用。
3. `set_capabilities` 为全量替换语义，不是增量；上报前先取全量再合并。
4. 同名 agent_id 180 秒内重连需处理 force_reconnect，否则注册失败。
5. MVP 走 HTTP 明文，仅限可信网络（模拟器 10.0.2.2 / 局域网）；跨公网必须切 AOC gRPC mTLS。secret 存应用私有 DataStore，禁用 SharedPreferences。
6. LLM 推理全部在中心 Brain；aos-agent 通过 DM 只发自然语言与结构化 skill 请求，协议层不自建推理路由。

## 维护方式

- **排查**：先 `GET /api/health` 确认 hub 与节点可见性；再看 AOC 面板（:8050 REST）任务与 agent 列表；车机端看 `AocConnectionManager` 日志。
- **验证**：从 AOC 侧发 DM → 车机 poll 收到；车机回传 skill_result → AOC 侧收到。
- **对端参考**：仓库 `D:\code\project\AgentOpenConnect`（2026-09-06 调研时为活跃生产 MVP），接入实现以其 `src/aoc/entry/network_client.py` 为准；对端协议变更时同步更新本文档。

> 更新时间：2026-09-06
