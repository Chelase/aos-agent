package com.aos.agent.core.web

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * 浏览器页面操作脚本的构造与结果解析。
 *
 * 全部是纯函数，不碰 WebView——所以能在 JVM 单测里逐条验证。
 *
 * **注入安全**：selector、文本这些参数来自模型输出，会被拼进 JS。
 * 一律经 [jsString] 转义成 JS 字符串字面量，绝不裸拼。
 * 这是页面 → Agent 这条不可信通道上最容易被绕过的一环。
 */
object BrowserScripts {

    /** 正文最大字符数。超了截断并标 truncated——不然一个长页面能撑爆模型上下文。 */
    const val MAX_CONTENT_CHARS = 8_000

    /** 单次操作结果的统一 JSON 键。 */
    object Keys {
        const val STATUS = "status"
        const val URL = "url"
        const val TITLE = "title"
        const val CONTENT = "content"
        const val TRUNCATED = "truncated"
        const val DETAIL = "detail"
        const val FOUND = "found"
        const val TAG = "tag"
        const val LABEL = "label"
    }

    /** 状态取值。 */
    object Status {
        const val OK = "ok"
        const val NOT_FOUND = "not_found"
        const val NO_CONTENT = "no_content"
        const val LOADING = "loading"
        const val ERROR = "error"
    }

    // ---------------------------------------------------------------- 读正文

    /**
     * 抽当前页正文。
     *
     * 取正文优先 `<article>` → `<main>` → `<body>`，并剥掉 script/style 等
     * 非内容节点——它们的 innerText 会混进噪声，script 的文本更是执行残留。
     */
    fun readPage(): String = """
(function(){
  try {
    /* WebView 加载失败时显示的是内部错误页（chrome-error://...），
       它的正文是"网页无法打开"这类提示，不是用户要看的内容。
       当成正常正文返回会让 Agent 以为读到了页面。 */
    var href = location.href || '';
    if (href.indexOf('chrome-error://') === 0 || href.indexOf('chrome://') === 0) {
      return {status: 'error', detail: '页面加载失败，当前是浏览器的错误页', url: href};
    }
    function clean(el) {
      var c = el.cloneNode(true);
      var junk = c.querySelectorAll('script,style,noscript,iframe,svg,template');
      for (var i = 0; i < junk.length; i++) { junk[i].parentNode.removeChild(junk[i]); }
      return (c.innerText || c.textContent || '').replace(/\s+\n/g, '\n').trim();
    }
    var root = document.querySelector('article') || document.querySelector('main') || document.body;
    if (!root) {
      /* body 还没建出来 = 页面还在渲染，不是"页面是空的" */
      return {status: 'loading', detail: '页面尚未渲染出内容'};
    }
    var text = clean(root);
    if (!text) { return {status: 'no_content', url: href, title: document.title || ''}; }
    return {
      status: 'ok',
      url: href,
      title: document.title || '',
      content: text
    };
  } catch (e) {
    return {status: 'error', detail: String(e && e.message || e)};
  }
})()
""".trimIndent()

    // ------------------------------------------------------------------ 点击

    /**
     * 点元素。
     *
     * 优先 CSS 选择器；没有选择器时按可见文本匹配可交互元素。
     * 匹配用"文本包含"而不是"完全相等"——网页上的按钮常带前后缀
     * （"登录（推荐）"、"下一步 >"），完全相等会一个都点不到。
     */
    fun click(selector: String?, text: String?): String {
        val sel = selector?.takeIf { it.isNotBlank() }?.let { jsString(it) } ?: "null"
        val txt = text?.takeIf { it.isNotBlank() }?.let { jsString(it) } ?: "null"
        return """
(function(){
  try {
    var el = null;
    var sel = $sel;
    var txt = $txt;
    if (sel) { el = document.querySelector(sel); }
    if (!el && txt) {
      var nodes = document.querySelectorAll('a,button,[role="button"],[role="link"],input[type="submit"],input[type="button"],summary');
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        if (n.disabled) continue;
        var label = (n.innerText || n.value || n.getAttribute('aria-label') || n.title || '').trim();
        if (label === txt || label.indexOf(txt) >= 0) { el = n; break; }
      }
    }
    if (!el) { return {status: 'not_found'}; }
    el.scrollIntoView({block: 'center', inline: 'nearest'});
    var label = (el.innerText || el.value || el.getAttribute('aria-label') || '').trim().slice(0, 120);
    el.click();
    return {status: 'ok', tag: el.tagName, label: label};
  } catch (e) {
    return {status: 'error', detail: String(e && e.message || e)};
  }
})()
""".trimIndent()
    }

    // ------------------------------------------------------------------ 填写

    /**
     * 填表单。
     *
     * **必须用原生 setter 再派发 input/change**：直接 `el.value = x` 改的是
     * DOM 自己的属性，React/Vue 的受控组件监听的是事件，属性被覆盖后
     * 框架状态不更新——表单看着填了，提交上去是空的。
     */
    fun fill(selector: String?, text: String, label: String?): String {
        val sel = selector?.takeIf { it.isNotBlank() }?.let { jsString(it) } ?: "null"
        val value = jsString(text)
        val lbl = label?.takeIf { it.isNotBlank() }?.let { jsString(it) } ?: "null"
        return """
(function(){
  try {
    var sel = $sel;
    var txt = $value;
    var lbl = $lbl;
    var el = null;
    if (sel) { el = document.querySelector(sel); }
    if (!el && lbl) {
      var nodes = document.querySelectorAll('input,textarea,select');
      for (var i = 0; i < nodes.length; i++) {
        var n = nodes[i];
        var name = (n.getAttribute('name') || n.getAttribute('aria-label') || n.getAttribute('placeholder') || '').toLowerCase();
        if (name.indexOf(lbl.toLowerCase()) >= 0) { el = n; break; }
      }
    }
    if (!el) { return {status: 'not_found'}; }
    if (el.disabled || el.readOnly) { return {status: 'error', detail: '目标不可编辑'}; }
    var proto = (el.tagName === 'TEXTAREA') ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) { desc.set.call(el, txt); } else { el.value = txt; }
    el.dispatchEvent(new Event('input', {bubbles: true}));
    el.dispatchEvent(new Event('change', {bubbles: true}));
    el.focus();
    return {status: 'ok', tag: el.tagName};
  } catch (e) {
    return {status: 'error', detail: String(e && e.message || e)};
  }
})()
""".trimIndent()
    }

    // ------------------------------------------------------------------ 滚动

    /**
     * 滚动。
     *
     * 有选择器就滚到那个元素（`scrollIntoView`），否则滚到绝对位置。
     *
     * **用即时滚动而不是平滑滚动**：平滑滚动是异步动画，调用返回时页面
     * 还没滚到位，紧接着读正文会读到旧位置。Agent 不看动画，要的是
     * "滚完之后的位置"，确定性比观感重要。
     */
    fun scroll(selector: String?, toY: Int?): String {
        val sel = selector?.takeIf { it.isNotBlank() }?.let { jsString(it) } ?: "null"
        val y = toY ?: 0
        return """
(function(){
  try {
    var sel = $sel;
    if (sel) {
      var el = document.querySelector(sel);
      if (!el) { return {status: 'not_found'}; }
      el.scrollIntoView({block: 'center', inline: 'nearest'});
      return {status: 'ok', y: Math.round(window.scrollY)};
    }
    window.scrollTo({top: $y, behavior: 'auto'});
    return {status: 'ok', y: Math.round(window.scrollY)};
  } catch (e) {
    return {status: 'error', detail: String(e && e.message || e)};
  }
})()
""".trimIndent()
    }

    // ------------------------------------------------------------------ 等待

    /**
     * 探测选择器是否命中。
     *
     * **轮询放在宿主做**，不在脚本里 await：注入的脚本不能长时间阻塞，
     * 否则 WebView 事件循环被卡住，页面自身也动不了。宿主反复求值直到命中或超时。
     */
    fun waitFor(selector: String): String {
        val sel = jsString(selector)
        return """
(function(){
  try {
    var el = document.querySelector($sel);
    return {status: el ? 'ok' : 'not_found', found: !!el};
  } catch (e) {
    return {status: 'error', detail: String(e && e.message || e)};
  }
})()
""".trimIndent()
    }

    // -------------------------------------------------------------- 结果解析

    /** 解析 `browser_read` 的返回。 */
    fun parseRead(raw: String): ReadResult {
        val text = raw.trim()
        // WebView 刚建好、还没加载任何页面时 evaluateJavascript 会返回 null。
        // 这是"页面还没准备好"，不是"页面是空的"——报 loading 让调用方重试。
        if (text.isEmpty() || text == "null") {
            return ReadResult.Loading("页面尚未就绪，稍后重试")
        }
        val obj = parseObject(text) ?: return ReadResult.Failed("页面返回的不是可解析的结果")
        val status = obj.string(Keys.STATUS) ?: return ReadResult.Failed("页面返回缺少 status")
        when (status) {
            Status.OK -> Unit
            // 页面还在渲染：这是"等一会儿再来"，不是"读不到内容"
            Status.LOADING -> return ReadResult.Loading(obj.string(Keys.DETAIL) ?: "页面尚未渲染出内容")
            else -> return ReadResult.Failed(obj.string(Keys.DETAIL) ?: "页面未返回正文（status=$status）")
        }
        val full = obj.string(Keys.CONTENT).orEmpty()
        val truncated = full.length > MAX_CONTENT_CHARS
        val content = if (truncated) full.substring(0, MAX_CONTENT_CHARS) else full
        return ReadResult.Ok(
            url = obj.string(Keys.URL).orEmpty(),
            title = obj.string(Keys.TITLE).orEmpty(),
            content = content,
            truncated = truncated,
        )
    }

    /** 解析 `browser_act` 的返回。 */
    fun parseAct(raw: String): ActResult {
        val text = raw.trim()
        if (text.isEmpty() || text == "null") {
            return ActResult.Failed("页面尚未就绪，脚本没有执行")
        }
        val obj = parseObject(text) ?: return ActResult.Failed("页面返回的不是可解析的结果")
        val status = obj.string(Keys.STATUS) ?: return ActResult.Failed("页面返回缺少 status")
        return when (status) {
            Status.OK -> ActResult.Ok(obj.string(Keys.TAG), obj.string(Keys.LABEL))
            Status.NOT_FOUND -> ActResult.NotFound("页面里没有匹配的元素")
            else -> ActResult.Failed(obj.string(Keys.DETAIL) ?: "操作失败（status=$status）")
        }
    }

    /** 解析 `waitFor` 的返回——只看命中与否。 */
    fun parseWait(raw: String): Boolean {
        val obj = parseObject(raw) ?: return false
        if (obj.string(Keys.STATUS) != Status.OK) return false
        return obj.bool(Keys.FOUND) ?: (obj.string(Keys.STATUS) == Status.OK)
    }

    private fun parseObject(raw: String): JsonObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        return runCatching {
            Json.parseToJsonElement(text) as? JsonObject
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.contentOrNull

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.booleanOrNull

    // -------------------------------------------------------------- 字符串转义

    /**
     * 转成 JS 字符串字面量（含引号）。
     *
     * 参数来自模型输出，直接拼进 JS 等于给了它一个注入口：
     * `"; alert(1); "` 就能跳出字符串。所以这里按 JS 规则转义
     * 反斜杠、引号、换行等，并把控制字符用 `\uXXXX` 表示。
     */
    fun jsString(value: String): String {
        val sb = StringBuilder(value.length + 2)
        sb.append('"')
        value.forEach { ch ->
            when {
                ch == '\\' -> sb.append("\\\\")
                ch == '"' -> sb.append("\\\"")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                // U+2028/U+2029 是行/段分隔符，在 JS 字符串字面量里会截断字符串
                ch.code == 0x2028 -> sb.append("\\u2028")
                ch.code == 0x2029 -> sb.append("\\u2029")
                ch.code < 0x20 -> sb.append("\\u%04x".format(ch.code))
                else -> sb.append(ch)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /** 读正文的结果。 */
    sealed interface ReadResult {
        data class Ok(
            val url: String,
            val title: String,
            val content: String,
            val truncated: Boolean,
        ) : ReadResult

        /** 页面还在渲染，稍后重试。 */
        data class Loading(val reason: String) : ReadResult

        data class Failed(val reason: String) : ReadResult
    }

    /** 操作结果。 */
    sealed interface ActResult {
        data class Ok(val tag: String?, val label: String?) : ActResult
        data class NotFound(val reason: String) : ActResult
        data class Failed(val reason: String) : ActResult
    }
}
