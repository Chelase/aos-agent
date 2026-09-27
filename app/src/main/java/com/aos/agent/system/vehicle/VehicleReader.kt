package com.aos.agent.system.vehicle

import com.aos.agent.core.tools.DegradeStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject

sealed interface VehicleReading {
    data class Number(val value: Double) : VehicleReading
    data class Text(val value: String) : VehicleReading

    /** 读不到。结构化原因会原样进工具结果，避免模型把"没权限"读成"值为 0"。 */
    data class Missing(val status: DegradeStatus, val detail: String? = null) : VehicleReading
}

interface VehicleReader {
    suspend fun read(spec: VehiclePropertySpec): VehicleReading
}

/**
 * 生产兜底实现。
 *
 * 真车 Car API 的编译期接入方式（平台 stub jar 还是反射网关）尚未定，
 * 见 `step3-toolsystem-plan.md` §6；在它定下来之前，如实报"未接入"，
 * 不用异常、也不返回编造的值。
 */
class UnavailableVehicleReader(
    private val status: DegradeStatus = DegradeStatus.NoVhal,
    private val detail: String = "车辆属性读取未接入",
) : VehicleReader {
    override suspend fun read(spec: VehiclePropertySpec): VehicleReading =
        VehicleReading.Missing(status, detail)
}

/** 模拟器与单测夹具。 */
class FakeVehicleReader(
    private val values: Map<String, VehicleReading>,
) : VehicleReader {
    override suspend fun read(spec: VehiclePropertySpec): VehicleReading =
        values[spec.name] ?: VehicleReading.Missing(DegradeStatus.Unsupported, "夹具未提供 ${spec.name}")

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }

        /**
         * 夹具形态：`{"SPEED": 12.5, "TYRE_PRESSURE": {"status": "permission_denied"}}`。
         * 数组值取第一个元素（VHAL 大量属性是数组，如四轮胎压）。
         */
        fun fromJson(raw: String): FakeVehicleReader {
            val root = runCatching { JSON.parseToJsonElement(raw).jsonObject }.getOrNull()
                ?: return FakeVehicleReader(emptyMap())
            val values = root.mapNotNull { (name, element) ->
                name to when (element) {
                    is JsonNull -> null
                    is JsonPrimitive -> (element.doubleOrNull?.let { VehicleReading.Number(it) }
                        ?: VehicleReading.Text(element.content))
                    is JsonArray -> (element.firstOrNull() as? JsonPrimitive)?.let {
                        VehicleReading.Number(it.doubleOrNull ?: 0.0)
                    }
                    is JsonObject -> {
                        val status = (element["status"] as? JsonPrimitive)?.content
                            ?.uppercase()
                            ?.let { runCatching { DegradeStatus.valueOf(it) }.getOrNull() }
                            ?: DegradeStatus.Unsupported
                        VehicleReading.Missing(
                            status,
                            (element["detail"] as? JsonPrimitive)?.content,
                        )
                    }
                }
            }.filterIsInstance<Pair<String, VehicleReading>>()
            return FakeVehicleReader(values.toMap())
        }
    }
}
