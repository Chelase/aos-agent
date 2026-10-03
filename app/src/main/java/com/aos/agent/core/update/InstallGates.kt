package com.aos.agent.core.update

/** 能不能拉起安装器的判定结果（与设备能力无关，纯逻辑，可单测）。 */
enum class InstallGate { READY, PERMISSION_NEEDED, NO_INSTALLER }

/**
 * 安装前的两道门。
 *
 * 顺序有意义：先问"系统让不让装未知来源"，再问"这台机器上有没有安装器可以拉"。
 * 车机镜像常常两个都不满足，界面必须把差别讲清楚——"去授权"和"这台车装不了"是两回事。
 */
object InstallGates {

    fun decide(canRequestInstalls: Boolean, installerPresent: Boolean): InstallGate = when {
        !canRequestInstalls -> InstallGate.PERMISSION_NEEDED
        !installerPresent -> InstallGate.NO_INSTALLER
        else -> InstallGate.READY
    }

    /** 下载地址不在白名单里就不该走到下载这一步。 */
    fun refuseUntrusted(url: String): Boolean = !UpdateDecision.isTrustedUrl(url)
}
