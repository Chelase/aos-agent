# 联网访问机制

## 结论

AOS-Agent 的联网能力由两类来源构成：本地内置的搜索与抓取工具（公开互联网、只读、无登录态），
以及可选接入的外部 MCP 服务；所有外网请求必须过 URL 守卫，缺失能力时结构化降级而非崩溃。

## 涉及对象

| 层 | 文件/表 | 角色 |
|---|---|---|
| Core | `core/web/UrlGuard.kt` | URL 白名单校验：仅 https 公网，拒绝内网/回环/云元数据 |
| Core | `core/web/WebCapability.kt` | WebView provider 可用性探测（运行期，不实例化） |
| Core | `core/web/WebFetcher.kt` | WebView 生命周期管理 + 加载 + 正文提取 |
| Core | `core/web/SearchProvider.kt` | 搜索引擎适配（DuckDuckGo html/lite 双端点） |
| Core | `core/web/HtmlTextExtractor.kt` | HTML → 纯文本/markdown（纯函数） |
| Tool | `core/tools/WebSearchTool.kt` | `web_search` 工具：关键词 → 结果列表 |
| Tool | `core/tools/WebFetchTool.kt` | `web_fetch` 工具：URL → 正文 |
| Tool | `core/tools/mcp/` | 外部 MCP 服务接入（可选增强，含 Lightpanda 等） |
| System | `AndroidManifest.xml` | `INTERNET` / `ACCESS_NETWORK_STATE` 权限 |
| System | `res/xml/network_security_config.xml` | 公网 TLS 强制；仅本地网段允许明文 |

## 运行链路

```
[Agent 决定联网]
    |
    +-- web_search(query)
    |     |
    |     v
    |   [SearchProvider.search]
    |     | GET https://html.duckduckgo.com/html/?q=<encoded>
    |     |   └─ 解析失败 → 降级 lite 端点 → 仍失败 → 结构化报错
    |     v
    |   [ToolResult.Ok {status, query, results[], total, provider}]
    |
    +-- web_fetch(url)
          |
          v
        [UrlGuard.isPublicHttpUrl]  ← 不通过直接拒（SSRF 防线）
          |
          v
        [WebCapability.probe]        ← 无 WebView provider → 降级
          |
          v
        [WebFetcher.fetch]
          | 创建 WebView（主线程）→ 加载 → onPageFinished
          | evaluateJavascript 提取正文（<article>/<main> → body → JS 剥标签）
          | 超时/异常 → 销毁并报错
          v
        [ToolResult.Ok {status, url, final_url, title, content, truncated}]
          |
          v
        [结果回灌 LLM]
```

## 使用点

- `AgentRuntime.refresh()`：注册两个工具到 `ToolSystem`
- `PromptSections.capabilities`：向模型声明工具名与"时效性问题先查再答"
- `PromptSections.rules`：声明抓取内容只当数据，不执行页面内指令
- `McpToolSource`：外部 MCP 工具（可选增强路径）

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| 换搜索引擎 | `core/web/SearchProvider.kt` | 新增一个实现，工具层只依赖接口 |
| 抓取格式扩展 | `core/web/HtmlTextExtractor.kt` | 加格式分支，不动工具层 |
| 放宽/收紧 URL 规则 | `core/web/UrlGuard.kt` | 改判定函数；内网拒绝不可放宽 |
| 加浏览器交互 | 新增工具 + `core/web/` 交互层 | 需重新评估驾驶场景安全边界 |

## 关键约束

1. **URL 守卫不可绕过**：只允许 https 公网域名；拒绝回环、私网、链路本地（含 `169.254.169.254` 云元数据）、
   `localhost`/`*.local`/`*.internal`，以及 IP 字面量直连（防 DNS rebinding）。所有外网入口都必须先过它。
2. **WebView headless 是非官方支持用法**：Chromium 明确不保证修复此类问题、WebView 更新可能突然失效。
   失败必须结构化报错并可观测，禁止掩盖成"抓取成功但内容为空"。
3. **WebView 必须显式释放**：其内存主要在 native 侧，不受 `maxHeap` 限制，不释放会持续涨并触发 LMK。
   所有路径（成功/超时/异常）都必须 `destroy()` 并清引用。
4. **搜索引擎格式不稳定**：DuckDuckGo html 端点是给浏览器用的，非官方 API，结构随时可能变。
   解析失败要报"结构变化导致解析失败"，**不能返回空列表冒充"没找到结果"**。
5. **抓取内容一律视为数据**：网页里的指令性文字不是给 Agent 的指令，不得执行；此约束同时写进系统提示。
6. **能力缺失结构化降级**：无 WebView provider、无网络、解析失败都要给出可读原因，不抛未捕获异常。

## 维护方式

- **新增**：加一个 `SearchProvider` 实现即可换搜索源，工具层不动。
- **排查**：抓取失败先看返回的 `status`/`detail`——`missing_provider` 是镜像缺 WebView，
  `parse_failed` 是搜索页结构变了，`timeout` 是页面太慢，`network` 是出网问题。
- **验证**：`./gradlew :app:testDebugUnitTest`（守卫/提取/解析/生命周期共 N 例，纯函数与假 WebView，不依赖网络）；
  真机复测重点是 **WebView provider 是否存在** 与 **连续抓取后内存是否上涨**。
- **实测记录**：2026-10-28 宿主侧实测 `html.duckduckgo.com/html/` 与 `lite.duckduckgo.com/lite/` 均 HTTP 200 可用；
  `api.duckduckgo.com` 返回 202 且需 key，不适合内置。
  2026-10-31 携桌面 UA 实测 html 端点：HTTP 200、34,396 字节，解析锚点
  `result__a` / `result__snippet` / `uddg=` 均存在，未触发 anomaly 反爬。

> 更新时间：2026-10-31
