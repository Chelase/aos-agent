package com.aos.agent.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aos.agent.R
import com.aos.agent.system.BatterySnapshot
import com.aos.agent.system.NetworkTransport

/**
 * 状态语义 → 本地化文案映射（design.md：文案一律走资源）。
 * 工程师模式与系统面板共用，避免两页各写一套映射。
 */
object AOSLabels {
    @Composable
    fun networkLabel(transport: NetworkTransport): String = stringResource(
        when (transport) {
            NetworkTransport.WIFI -> R.string.network_wifi
            NetworkTransport.CELLULAR -> R.string.network_cellular
            NetworkTransport.ETHERNET -> R.string.network_ethernet
            NetworkTransport.OTHER -> R.string.network_other
            NetworkTransport.NONE -> R.string.network_none
            NetworkTransport.UNAVAILABLE -> R.string.network_unavailable
        },
    )

    @Composable
    fun batteryStateLabel(snapshot: BatterySnapshot): String = stringResource(
        when (snapshot.charging) {
            true -> R.string.battery_charging
            false -> R.string.battery_discharging
            null -> R.string.battery_unknown
        },
    )
}
