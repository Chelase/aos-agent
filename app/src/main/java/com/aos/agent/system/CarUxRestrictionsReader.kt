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
 * 本期只**读取并暴露状态**，不改任何布局（行驶全屏遮罩属语音 Phase B）。
 * 车机侧两条硬约束：编译期可选库把该类放在 `android.car.drivingstate`，
 * 而 Android 13 起框架把它搬到了 `android.car.uxrestriction`；注册监听还要厂商授权。
 * 所以整段包在 runCatching 里，拿不到就降级为"不限制"并留一行日志，
 * 绝不因为读不到驾驶态而让界面出错
 * （与 [com.aos.agent.system.vehicle.AndroidVehicleReader] 同款策略）。
 */
class CarUxRestrictionsReader(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val _restricted = MutableStateFlow(false)

    /** 行驶中是否受 UX 限制；能力不可用时恒为 false。 */
    val restricted: StateFlow<Boolean> = _restricted.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        runCatching {
            val instance = Car.createCar(appContext) ?: return@runCatching
            val ux = instance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
            car = instance
            manager = ux ?: return@runCatching
            ux.registerListener { restrictions ->
                _restricted.value = isRestricted(restrictions)
            }
            _restricted.value = isRestricted(ux.currentCarUxRestrictions)
        }.onFailure { error ->
            Log.i(TAG, "CarUxRestrictions 不可用，按不限制处理：${error.javaClass.simpleName}")
            _restricted.value = false
        }
    }

    /** 基线（BASELINE）= 不限制；只要有任何一位限制生效就认为行驶受限。 */
    private fun isRestricted(restrictions: CarUxRestrictions?): Boolean =
        restrictions != null &&
            restrictions.activeRestrictions != CarUxRestrictions.UX_RESTRICTIONS_BASELINE

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
