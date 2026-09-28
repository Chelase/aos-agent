package com.aos.agent.system.vehicle

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import com.aos.agent.core.tools.DegradeStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 真车 / 真模拟器的车辆属性读取。
 *
 * 编译期走 `useLibrary("android.car")`（官方样例同款），运行期一律探测：
 * 没有 Car 服务、没有该权限、本机 VHAL 没这个属性、OEM 改了签名——
 * 全部返回结构化 `Missing`，不抛异常也不编造数值。
 */
class AndroidVehicleReader(
    context: Context,
) : VehicleReader, AutoCloseable {

    private val appContext = context.applicationContext
    private var car: Car? = null
    private var propertyManager: CarPropertyManager? = null
    private var connectionAttempted = false

    override suspend fun read(spec: VehiclePropertySpec): VehicleReading = withContext(Dispatchers.IO) {
        deniedByPermission(spec)?.let { return@withContext it }
        val propertyId = resolvePropertyId(spec)
            ?: return@withContext VehicleReading.Missing(
                DegradeStatus.Unsupported,
                "本机未定义车辆属性 ${spec.name}",
            )
        val manager = connectPropertyManager()
            ?: return@withContext VehicleReading.Missing(DegradeStatus.NoVhal, "Car 服务不可用")

        runCatching { manager.getProperty<Any>(propertyId, AREA_GLOBAL)?.value }
            .fold(
                onSuccess = { value -> describe(value, spec.name) },
                onFailure = { error ->
                    VehicleReading.Missing(statusFor(error), "${spec.name}: ${error.javaClass.simpleName}")
                },
            )
    }

    private fun describe(value: Any?, propertyName: String): VehicleReading = when (value) {
        null -> VehicleReading.Missing(DegradeStatus.NoVhal, "$propertyName 当前无值")
        is Number -> VehicleReading.Number(value.toDouble())
        is String -> VehicleReading.Text(value)
        is Boolean -> VehicleReading.Number(if (value) 1.0 else 0.0)
        else -> VehicleReading.Missing(
            DegradeStatus.Unsupported,
            "$propertyName 返回类型 ${value.javaClass.simpleName} 本期不解析",
        )
    }

    private fun deniedByPermission(spec: VehiclePropertySpec): VehicleReading? {
        val permission = spec.permission
        if (permission.isBlank()) return null
        val granted = appContext.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return if (granted) {
            null
        } else {
            VehicleReading.Missing(DegradeStatus.PermissionDenied, "未授予 $permission")
        }
    }

    /**
     * 属性 id 优先取定义文件里的 `id`（vendor 扩展属性必须显式给）；
     * 否则按名字反射 AOSP 常量表。用反射不是为了绕开 useLibrary，
     * 而是因为 OEM 会在自己的 VHAL 上增删属性——编译期常量表覆盖不了这种差异。
     */
    private fun resolvePropertyId(spec: VehiclePropertySpec): Int? {
        spec.id?.let { return it }
        return runCatching {
            VehiclePropertyIds::class.java.getField(spec.name).getInt(null)
        }.getOrNull()
    }

    private fun connectPropertyManager(): CarPropertyManager? {
        propertyManager?.let { return it }
        if (connectionAttempted) return null
        connectionAttempted = true
        val connected = runCatching {
            Car.createCar(appContext)?.also { instance ->
                propertyManager = instance.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            }
        }.getOrNull()
        car = connected
        return propertyManager
    }

    private fun statusFor(error: Throwable): DegradeStatus = when (error) {
        is SecurityException -> DegradeStatus.PermissionDenied
        is UnsupportedOperationException, is NoSuchFieldException, is IllegalArgumentException -> DegradeStatus.Unsupported
        is NoSuchMethodError, is NoClassDefFoundError -> DegradeStatus.Unsupported
        else -> DegradeStatus.NoVhal
    }

    override fun close() {
        runCatching { car?.disconnect() }
        car = null
        propertyManager = null
    }

    private companion object {
        const val AREA_GLOBAL = 0
    }
}
