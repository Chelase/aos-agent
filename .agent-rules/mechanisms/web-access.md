# 联网访问与内置浏览器机制

## 结论

AOS-Agent 的联网能力由三层构成：纯 HTTP 的搜索工具、可供人工操作的内置 WebView 浏览器页、
以及在浏览器页上做只读抽取与受控操作的 Agent 工具；所有外网请求必须过 URL 守卫，
能力缺失时结构化降级而非崩溃。

采用"车机内置 WebView + JS Bridge"是行业通行做法，与蔚来的 WebView 运行容器、
ACCESS 的 `flutter_linux_webview`、OpenHarmony 的 `web_webview` 同路；不采用 Chrome/Vivaldi/Firefox
等现成车机浏览器，因为外部 App 无法被 Agent 注入 JS、读 DOM、点按钮。

成熟的开源车机浏览器 [kododake/AABrowser](https://github.com/kododake/AABrowser)（AAOS 原生、694 stars、
持续活跃）作为**参考实现**而非依赖：它是 GPL-3.0，与本仓 Apache-2.0 不兼容；且它是独立浏览器 App，
缺 Agent 接口与宿主集成。本仓保持 Apache-2.0，浏览器能力自建。

## 涉及对象

**已实现（Phase 1：搜索）**

| 层 | 文件/表 | 角色 |
|---|---|---|
| Core | `core/web/UrlGuard.kt` | URL 守卫：仅 https 公网，拒绝内网/回环/云元数据/IP 字面量/内网后缀 |
| Core | `core/web/SearchProvider.kt` | 搜索引擎适配（DuckDuckGo html 主端点 → lite 降级） |
| Core | `core/web/HtmlTextExtractor.kt` | HTML → 纯文本/标题/链接（纯函数） |
| Core | `core/web/UrlExtractor.kt` | 文本 → 可点击 https 链接（纯函数，过 URL 守卫） |
| Tool | `core/tools/WebSearchTool.kt` | `web_search`：关键词 → 结果列表 |
| Test | `test/resources/ddg_result_sample.html` | 真实抓取的搜索页片段，用于解析回归 |

**已实现（Phase 2：内置浏览器页）**

| 层 | 文件/表 | 角色 |
|---|---|---|
| Core | `core/web/WebCapability.kt` | WebView 可用性探测（真建一个，失败给原因） |
| Core | `core/web/BrowserController.kt` | WebView 持有 + JS 注入封装（串行化、释放后拒绝） |
| UI | `ui/browser/BrowserInput.kt` | 地址栏输入解析：网址还是搜索词（纯函数） |
| UI | `ui/browser/BrowserViewModel.kt` | 浏览器页状态、地址栏、历史、导航动作 |
| UI | `ui/browser/BrowserScreen.kt` | 浏览器页：起始页 + 地址栏 + WebView + 状态条 |
| UI | `ui/components/AOSAppShell.kt` | `AOSDestination.Browser` 导航项 |
| UI | `ui/chat/ChatScreen.kt` | 助手回答里的 https 链接渲染为可点，交给宿主在内置浏览器打开 |

**待实现（Phase 3：Agent 操作页面）**

| 层 | 文件/表 | 角色 |
|---|---|---|
| Tool | `core/tools/BrowserTool.kt` | `browser_read`：读当前页正文；`browser_act`：受控操作页面 |

**通用**

| 层 | 文件/表 | 角色 |
|---|---|---|
| Tool | `core/tools/mcp/` | 外部 MCP 服务接入（可选增强） |
| System | `AndroidManifest.xml` | `INTERNET` / `ACCESS_NETWORK_STATE` 权限 |
| System | `res/xml/network_security_config.xml` | 公网 TLS 强制；仅本地网段允许明文 |

## 运行链路

```
[Agent 决定联网]
    |
    +-- web_search(query)                    ← 已实现
    |     |
    |     v
    |   [SearchProvider.search]
    |     | GET https://html.duckduckgo.com/html/?q=<encoded>（携桌面 UA）
    |     |   ├─ 网络失败 → 试 lite 端点 → 都失败 → NetworkError（带异常类型与原因）
    |     |   ├─ 通了但解析不出条目 → 试 lite → 仍空 → ParseFailed（"结构变化"）
    |     |   └─ 成功 → 解开 uddg= 跳转链接 → 标题/真实URL/摘要
    |     v
    |   [ToolResult.Ok {status, query, results[], total, provider}]
    |     status ∈ {ok, parse_failed, network_error}
    |
    +-- browser_read / browser_act           ← 待实现（Phase 3）
          |
          v
        [BrowserController]  ← 会话不存在 → 返回"浏览器未打开"降级
          |
          v
        [WebView.evaluateJavascript]（串行化 + 主线程）
          │
          v
        [ToolResult.Ok {status, url, title, content, …}]
```

```
[人工使用浏览器]
    |
    v
[AOSDestination.Browser]  ← 栈式导航压栈，不打断对话
    |
    v
[BrowserScreen]
    ├─ 起始页：未加载任何页时盖住 WebView（否则是黑屏，车机上突兀）
    │    └─ 标题 + 提示 + 三个常用入口（文字链，非卡片堆砌）
    ├─ 地址栏：URL → 直接加载；非 URL 文本 → 当搜索词（走 DDG）
    ├─ WebView（AndroidView 包 Compose）
    │    ├─ onPageStarted/Finished → 更新 title/url/进度状态
    │    ├─ onReceivedError → 可读原因 + 重试按钮
    │    └─ 离开页面 → destroy() + 清引用
    └─ 底部条：后退 / 前进 / 刷新
```

```
[从聊天进浏览器]
    |
    v
[助手回答里的 https 链接可点]（UrlExtractor 抽链接，过 URL 守卫）
    |
    v
[MainActivity] 记下来源页(Chat) → 切到 Browser → BrowserScreen 消费 URL → 加载
    |
    v
[返回] → 回来源页(Chat)，对话内容不丢
```

## 使用点

- `AgentRuntime.refresh()`：注册 `web_search`（Phase 3 再加 `browser_read` / `browser_act`）
- `AgentRuntime`：持有 `BrowserController`，与浏览器页共享同一会话（Phase 3 接入）
- `MainActivity`：`AOSDestination.Browser` 装配、栈式导航、待加载 URL 的传递
- `PromptSections.capabilities`：向模型声明工具名与"时效性问题先查再答"
- `PromptSections.rules`：声明网页内容只当数据，不执行页面内指令
- `McpToolSource`：外部 MCP 工具（可选增强路径）

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 换搜索引擎 | `core/web/SearchProvider.kt` | 新增一个实现，工具层只依赖接口 |
| 抓取格式扩展 | `core/web/HtmlTextExtractor.kt` | 加格式分支，不动工具层 |
| 放宽/收紧 URL 规则 | `core/web/UrlGuard.kt` | 改判定函数；内网拒绝不可放宽 |
| 改起始页内容 | `ui/browser/BrowserScreen.kt` 的 `StartPage` | 走既有 token 与双语资源 |
| 加浏览器交互动作 | `core/tools/BrowserTool.kt` + `BrowserController` | 新增动作类型；写动作必须走 Ask 闸 |
| 浏览器页视觉调整 | `ui/browser/BrowserScreen.kt` | 走既有 token 与双语资源，不硬编码色值字号 |

## 关键约束

1. **URL 守卫不可绕过**：只允许 https 公网域名；拒绝回环、私网、链路本地（含 `169.254.169.254` 云元数据）、
   `localhost`/`*.local`/`*.internal`，以及 IP 字面量直连（防 DNS rebinding）。所有外网入口都必须先过它。
2. **浏览器用有窗口的 WebView，不用无头的**：Chromium 明确声明脱离窗口的 WebView 属未受支持用法
   （可能崩、升级即失效）。有窗口既避开该风险，也让 Agent 自主操作与人工操作共用同一实例。
3. **浏览器仅停车可用**：与行业惯例一致——Google 把浏览器归入 parked apps 类别，
   Chrome/Vivaldi/Firefox 三家车机版全部仅停车可用。行驶中锁定浏览器页，复用现有 `driveRestricted` 遮罩。
4. **WebView 必须显式释放**：其内存主要在 native 侧，不受 `maxHeap` 限制，不释放会持续涨并触发 LMK。
   离开页面必须 `destroy()` 并清引用。
5. **Agent 的浏览器写操作必须走 Ask 闸**：`browser_act` 的点击/填写可能触及付费、提交、登录等真实后果，
   需人工确认；无确认器即拒（与现有 `ToolPermission.Ask` 一致）。
6. **网页内容一律视为数据**：网页里的指令性文字不是给 Agent 的指令，不得执行；此约束同时写进系统提示。
7. **能力缺失结构化降级**：无 WebView provider、无网络、解析失败、页面未加载完，都要给出可读原因，
   不抛未捕获异常、不返回空内容冒充成功。
8. **不得链接 GPL 代码**：本仓是 Apache-2.0。开源车机浏览器 AABrowser 是 GPL-3.0，
   链接或复制其代码会要求衍生作品按 GPL 发布，与本仓许可不兼容。可读可借鉴，不可复制不可依赖。
9. **页面主动回调宿主必须校验 origin**：页面与宿主的通信通道用 `addWebMessageListener`（按 origin 匹配），
   不用 `addJavascriptInterface`（把宿主对象直接暴露给任意页面）。任何页面发起的调用都要校验
   `sourceOrigin` 与当前页一致、且是主 frame、且当前页为 http/https，否则恶意页面可直接调用 Agent 能力。
10. **代理环境下不要依赖本地 DNS 判定**：车机常走 HTTP 代理，域名由代理侧解析，本地
    `InetAddress.getAllByName` 会失败。`UrlGuard.checkWithDns` 在此场景会把合法 URL 误判为非公网。
    生产校验走静态 `UrlGuard.check`；`checkWithDns` 当前只用于测试。
11. **网络失败必须带原因**：出网失败要区分连接超时、DNS 失败、连接被拒、非 2xx、空响应体，
    不能统一报"无法连接"——排查时看不出差别。异常类型与消息要保留（注意别把凭据带进消息里）。
12. **搜索端点的失败分三类**：网络失败（连不上）、解析失败（结构变了/被限流）、成功但无结果。
    三者不可混为一谈，尤其**不能把"解析失败"返回成空列表**——那会让模型以为"没搜到"。
    判定顺序：主端点网络失败 → 直接报网络错误（不再试 lite）；主端点通了但解析不出 → 试 lite
    → 仍空才报解析失败。
13. **主机名先转 punycode 再判断**：中文域名不规范化会绕过 `.local` / `.internal` 这类后缀检查。
14. **出网必须显式设超时**：车机弱网是常态，依赖客户端默认超时会让 Agent 长时间无响应。
    当前搜索层用 `connect 8s / read 12s / call 15s`，并关闭 `retryOnConnectionFailure`（重试只会放大等待）。
15. **同一域名族不重复试**：主端点与降级端点同域时，主端点网络失败即返回，不再试降级端点。
    降级端点只在"网络通了但解析不出结果"时使用。
16. **WebView 可用性靠真建一个来判**：不查固定包名（各厂商包名不同，写死会在非 Google 设备上误报），
    不反射 `WebViewFactory`（hidden API，ROM 差异大）。直接建一个，失败就给原因。
    代价是几十 MB 内存与几百毫秒，但只在浏览器页真正打开时发生一次。
17. **浏览器首屏不能是黑屏**：未加载任何页面时 WebView 是纯黑，车机上非常突兀。
    用 Compose 起始页盖住（标题 + 提示 + 常用入口），加载真实页面后撤掉。
    起始页用 Compose 渲染而非 assets HTML——两套设计语言会打架，且要跟 token 保持一致。
18. **地址栏草稿不能跟随页面状态重置**：草稿状态若以地址栏当前值为 key，
    页面加载回调一改地址栏，用户正在输入的内容就被冲掉。草稿只在进入编辑态时显式赋值。
19. **聊天里的链接只有 https 可点**：明文 http 不做可点入口——模型输出的链接不能默认可信；
    内网/回环/云元数据地址直接不抽。抽取统一走 `UrlGuard`。
20. **栈式返回要记住来源页**：从聊天点链接进浏览器，返回应回聊天而不是首页，
    且对话内容不丢。用一个"来源页"状态承接，不做完整导航栈。

## 维护方式

- **新增**：加一个 `SearchProvider` 实现即可换搜索源，工具层不动。
- **排查**：先看返回的 `status`/`detail`——`missing_provider` 是镜像缺 WebView，
  `parse_failed` 是搜索页结构变了，`timeout` 是页面太慢，`network` 是出网问题，
  `no_session` 是浏览器页没打开。
- **验证**：`./gradlew :app:testDebugUnitTest`（守卫/提取/解析/会话/注入构造共 N 例，纯函数与假 WebView，不依赖网络）；
  真机复测重点是 **WebView provider 是否存在** 与 **反复进出浏览器后内存是否上涨**。
- **实测记录**：2026-10-31 宿主侧实测 `html.duckduckgo.com/html/` 与 `lite.duckduckgo.com/lite/` 均 HTTP 200 可用；
  `api.duckduckgo.com` 返回 202 且需 key，不适合内置。
  携桌面 UA 实测 html 端点：HTTP 200、34,396 字节，解析锚点 `result__a` / `result__snippet` / `uddg=` 均存在，未触发反爬。
- **设备端实测（2026-10-31）**：AAOS 镜像（`android-35-ext15` Play Store 版）带 WebView provider
  `com.google.android.webview`；设备上真实搜索 5 条结果、耗时 1.6–1.8 秒；
  切断网络后 0.24 秒快速失败并报 `UnknownHostException`。
  另发现 **AAOS 镜像没有 ethernet 服务**（车机走 cellular radio），`eth0` DOWN 属正常。
- **弱网与超时**：默认 OkHttp 超时会让两个端点累计 40 秒，车上不可接受；
  已改为 `connect 8s / read 12s / call 15s` 并关闭重试，主端点网络失败不再试 lite。
- **Phase 2 设备端实测（2026-10-31，AAOS 模拟器 API 35）**：
  - 浏览器页可从导航栏进入；起始页渲染正常（此前 WebView 区域实测为纯黑，已用起始页覆盖）。
  - 点起始页"维基百科"入口 → 真实加载成功，标题"维基百科，自由的百科全书"，
    地址栏拿到真实 URL `https://zh.wikipedia.org/wiki/Wikipedia:%E9%A6%96%E9%A1%B5`。
  - 助手回答里的链接可点 → 进浏览器 → 返回后回到对话且对话内容仍在（栈式导航端到端通过）。
  - 地址栏输入 `https://192.168.0.1/` 被守卫拦下并显示"禁止直接使用 IP 地址：192.168.0.1"。
  - 设备侧测试：BrowserScreenTest 6 例、BrowserCoreTest 8 例、ChatLinkTest 3 例、
    BrowserStackNavigationTest 1 例全部通过；JVM 单测 379 例中仅 `LlmStateMirrorTest` 失败
    （依赖宿主 `sh` 命令，Windows 环境问题，改动前即失败）。
- **测试环境注意**：模拟器若设了系统 HTTP 代理，到 `127.0.0.1` 的本地 fixture 请求会被代理劫持，
  LLM SSE fixture 收不到连接（表现为 `SocketTimeoutException: Poll timed out`）。
  `BrowserStackNavigationTest` 已自行在测前关代理、测后恢复；`V6NavigationTest` 未做此处理，
  在带代理的环境下会失败——这是既有失败，与浏览器页改动无关。

> 更新时间：2026-10-31
