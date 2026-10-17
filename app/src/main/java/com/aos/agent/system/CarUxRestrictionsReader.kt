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
 * 车机侧硬约束：该类在编译期可选库里位于 `android.car.drivingstate`；注册监听还要厂商授权。
 * （曾怀疑 Android 14+ 把它挪到 `android.car.uxrestriction`——核对 AOSP car-lib `main` 分支后
 * 确认并无此包，`drivingstate` 一直是唯一位置，故不再做跨包名反射。）
 *
 * 与过去"整段 runCatching 吞掉"的区别：现在按阶段（建 Car / 取 Manager / 注册监听）
 * 分别记录失败原因到 [diagnostics]，供工程师页显式告警——把"静默 UNKNOWN"变成可观测。
 */
enum class DriveRestriction { UNKNOWN, UNRESTRICTED, RESTRICTED }

class CarUxRestrictionsReader(context: Context) : AutoCloseable {

    private val appContext = context.applicationContext
    private val _restriction = MutableStateFlow(DriveRestriction.UNKNOWN)
    private val _diagnostics = MutableStateFlow<DriveUxDiagnostics>(
        DriveUxDiagnostics.Unavailable(DriveUxDiagnostics.FailureStage.CREATE_CAR, "尚未启动"),
    )

    /** 当前驾驶限制状态；能力不可用时恒为 [DriveRestriction.UNKNOWN]。 */
    val restriction: StateFlow<DriveRestriction> = _restriction.asStateFlow()

    /** 读取链路诊断：真实绑定成功，或在哪一阶段因何失败。工程师页据此显式告警。 */
    val diagnostics: StateFlow<DriveUxDiagnostics> = _diagnostics.asStateFlow()

    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null

    fun start() {
        val instance = runCatching { Car.createCar(appContext) }
            .onFailure {
                report(DriveUxDiagnostics.FailureStage.CREATE_CAR, it)
                return
            }
            .getOrNull()
        if (instance == null) {
            report(DriveUxDiagnostics.FailureStage.CREATE_CAR, null)
            return
        }
        car = instance

        val ux = runCatching {
            instance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        }
            .onFailure {
                report(DriveUxDiagnostics.FailureStage.GET_MANAGER, it)
                return
            }
            .getOrNull()
        if (ux == null) {
            report(DriveUxDiagnostics.FailureStage.GET_MANAGER, null)
            return
        }
        manager = ux

        runCatching {
            ux.registerListener { restrictions -> applyCurrent(restrictions) }
            applyCurrent(ux.currentCarUxRestrictions)
        }.onFailure { report(DriveUxDiagnostics.FailureStage.REGISTER, it) }
    }

    private fun applyCurrent(restrictions: CarUxRestrictions?) {
        val active = restrictions?.activeRestrictions
        applyClassified(DriveRestrictionClassifier.classify(active))
    }

    private fun applyClassified(classified: DriveRestriction) {
        _restriction.value = classified
        _diagnostics.value = DriveUxDiagnostics.Bound(
            carService = Car.CAR_UX_RESTRICTION_SERVICE,
            listenerRegistered = manager != null,
            restriction = classified,
        )
    }

    private fun report(stage: DriveUxDiagnostics.FailureStage, error: Throwable?) {
        Log.i(TAG, "CarUxRestrictions 不可用（$stage）：${error?.javaClass?.simpleName ?: "返回 null"}")
        _restriction.value = DriveRestriction.UNKNOWN
        _diagnostics.value = DriveUxDiagnostics.fromFailure(stage, error)
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