package com.aos.agent.system

/**
 * 驾驶限制（CarUxRestrictions）读取失败的可观测诊断。
 *
 * 设计初衷：真车实测（五菱星光 Android 13）与上游源码核对后发现，
 * `CarUxRestrictions` 在 AOSP car-lib 里始终位于 `android.car.drivingstate` 包
 * （`main` 分支亦然，并无 `android.car.uxrestriction`）。也就是说"Android 14+ 换包名导致静默失效"
 * 这个推测本身不成立——真正会静默失效的是**注册监听被厂商拒、Car 服务连不上、读回 null**，
 * 过去这些全被 `runCatching` 吞成 [DriveRestriction.UNKNOWN]，界面看不出为什么。
 *
 * 本模型把"为什么是未知"拆成可读的几段，工程师页据此显式告警，
 * 而不是让驾驶遮罩永不出面却没有任何解释（这正是 P1-2 要去掉的坑）。
 */
sealed interface DriveUxDiagnostics {

    /** Car 服务、UX 管理器、监听都就位，[restriction] 是真实值。 */
    data class Bound(
        val carService: String,
        val listenerRegistered: Boolean,
        val restriction: DriveRestriction,
    ) : DriveUxDiagnostics

    /** 读取在 [stage] 处失败，[reason] 是异常类型或空返回的说明。 */
    data class Unavailable(
        val stage: FailureStage,
        val reason: String,
    ) : DriveUxDiagnostics

    enum class FailureStage {
        /** `Car.createCar` 返回 null 或抛异常（非 Automotive / 无 Car 服务）。 */
        CREATE_CAR,

        /** 拿不到 `CarUxRestrictionsManager`（服务未启用或类型不符）。 */
        GET_MANAGER,

        /** 管理器拿到但注册监听被拒（厂商权限）或读取当前值抛异常。 */
        REGISTER,
    }

    companion object {
        /** 把某阶段抛出的异常归一成 [Unavailable]，保留异常简单类名便于真车 logcat 定位。 */
        fun fromFailure(stage: FailureStage, error: Throwable?): Unavailable =
            Unavailable(
                stage = stage,
                reason = error?.javaClass?.simpleName ?: "返回 null",
            )
    }
}

/**
 * 驾驶态分类的纯逻辑（不触碰 Car API，便于单元测试）。
 *
 * 基线（BASELINE）= 不限制；任何一位限制生效即视为受限；读回 null 是"未知"不是"不限制"。
 */
internal object DriveRestrictionClassifier {

    /** 对应 `CarUxRestrictions.UX_RESTRICTIONS_BASELINE = 0`。 */
    const val UX_RESTRICTIONS_BASELINE = 0

    fun classify(activeRestrictions: Int?): DriveRestriction = when {
        activeRestrictions == null -> DriveRestriction.UNKNOWN
        activeRestrictions == UX_RESTRICTIONS_BASELINE -> DriveRestriction.UNRESTRICTED
        else -> DriveRestriction.RESTRICTED
    }
}