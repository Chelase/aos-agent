package com.aos.agent.system

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 驾驶限制（CarUxRestrictions）读取。
 *
 * 三态而不是布尔：拿不到驾驶态（没有 Car 服务、注册监听被厂商权限拒绝）必须能报"未知"，
 * 否则界面会把"读不到"演成"停着"，用户据此做错了事没人负责。
 *
 * 车机侧两条硬约束：编译期可选库把该类放在 `android.car.drivingstate`，
 * 而 Android 13 起框架把它搬到了 `android.car.uxrestriction`；注册监听还要厂商授权。
 * 所以整段包在 runCatching 里，拿不到就降级为 [DriveRestriction.UNKNOWN] 并留一行日志，
 * 绝不因为读不到驾驶态而让界面出错
 * （与 [com.aos.agent.system.vehicle.AndroidVehicleReader] 同款策略）。
 */
enum class DriveRestriction { UNKNOWN, UNRESTRICTED, RESTRICTED }

class CarUxRestrictionsReader(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val _restriction = MutableStateFlow(DriveRestriction.UNKNOWN)

    /** 当前驾驶限制状态；能力不可用时恒为 [DriveRestriction.UNKNOWN]。 */
    val restriction: StateFlow<DriveRestriction> = _restriction.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        runCatching {
            val instance = Car.createCar(appContext) ?: return@runCatching
            val ux = instance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            car = instance
            manager = ux ?: return@runCatching
            ux.registerListener { restrictions -> _restriction.value = classify(restrictions) }
            _restriction.value = classify(ux.currentCarUxRestrictions)
        }.onFailure { error ->
            Log.i(TAG, "CarUxRestrictions 不可用，按未知处理：${error.javaClass.simpleName}")
            _restriction.value = DriveRestriction.UNKNOWN
        }
    }

    /**
     * 基线（BASELINE）= 不限制；只要有任何一位限制生效就认为行驶受限。
     * 读不到值（null）是"未知"，不是"不限制"。
     */
    private fun classify(restrictions: CarUxRestrictions?): DriveRestriction = when {
        restrictions == null -> DriveRestriction.UNKNOWN
        restrictions.activeRestrictions == CarUxRestrictions.UX_RESTRICTIONS_BASELINE ->
            DriveRestriction.UNRESTRICTED

        else -> DriveRestriction.RESTRICTED
    }

    override fun close() {
        runCatching { manager?.unregisterListener() }
        runCatching { car?.disconnect() }
        manager = null
        car = null
    }

    private companion object {
        const val TAG = "AOSUxRestrictions"
    }
}
