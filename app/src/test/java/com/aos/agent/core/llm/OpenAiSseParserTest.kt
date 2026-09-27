package com.aos.agent.core.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiSseParserTest {

    private fun delta(content: String) =
        """{"choices":[{"index":0,"delta":{"content":"$content"}}]}"""

    @Test
    fun parsesIncrementalContent() {
        assertEquals(SseEvent.Delta("你好"), parseSseData(delta("你好")))
    }

    @Test
    fun doneMarkerEndsStream() {
        assertEquals(SseEvent.Done, parseSseData("[DONE]"))
    }

    @Test
    fun emptyAndHeartbeatLinesAreIgnored() {
        assertEquals(SseEvent.Ignored, parseSseData(""))
        assertEquals(SseEvent.Ignored, parseSseData("   "))
        assertEquals(SseEvent.Ignored, parseSseData("""{"model":"gpt-x"}"""))
    }

    /** 角色切换块只有 role 没有 content，属正常流，不能当失败。 */
    @Test
    fun roleOnlyDeltaIsIgnoredNotFailed() {
        assertEquals(
            SseEvent.Ignored,
            parseSseData("""{"choices":[{"delta":{"role":"assistant"}}]}"""),
        )
    }

    /** 结束块常见形态：delta.content 为 null。 */
    @Test
    fun nullContentIsIgnored() {
        assertEquals(SseEvent.Ignored, parseSseData("""{"choices":[{"delta":{"content":null}}]}"""))
    }

    @Test
    fun malformedJsonIsIgnoredAndKeepsStreamAlive() {
        assertEquals(SseEvent.Ignored, parseSseData("{not json"))
        assertEquals(SseEvent.Ignored, parseSseData("""{"choices":"oops"}"""))
        assertEquals(SseEvent.Ignored, parseSseData("""{"choices":[]}"""))
    }

    /** 中转服务会在 chunk 里塞 usage/其它字段，未知键必须被容忍。 */
    @Test
    fun unknownFieldsAreTolerated() {
        assertEquals(
            SseEvent.Delta("ok"),
            parseSseData(
                """{"choices":[{"delta":{"content":"ok","tool_calls":null},"index":0,"finish_reason":null}],""" +
                    """"usage":{"prompt_tokens":1}}""",
            ),
        )
    }

    /** 流式 function calling：id/name 只在首块，arguments 分片到达。 */
    @Test
    fun parsesToolCallFragment() {
        val payload =
            """{"choices":[{"index":0,"delta":{"tool_calls":[{"index":0,"id":"call_1",""" +
                """"function":{"name":"vehicle_basic","arguments":"{\"fields\""}}]}}]}"""

        assertEquals(
            SseEvent.ToolCall(0, "call_1", "vehicle_basic", "{\"fields\""),
            parseSseData(payload),
        )
    }

    @Test
    fun parsesArgumentsOnlyFragment() {
        val payload = """{"choices":[{"delta":{"tool_calls":[{"index":1,"function":{"arguments":"xyz"}}]}}]}"""

        assertEquals(SseEvent.ToolCall(1, null, null, "xyz"), parseSseData(payload))
    }

    @Test
    fun emptyToolCallsArrayIsIgnored() {
        assertEquals(SseEvent.Ignored, parseSseData("""{"choices":[{"delta":{"tool_calls":[]}}]}"""))
    }
}
