package com.aos.agent.ui.state

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.aos.agent.core.tools.DegradeStatus
import com.aos.agent.system.BatterySnapshot
import com.aos.agent.system.SystemInfo
import com.aos.agent.system.SystemInfoProvider
import com.aos.agent.system.vehicle.AndroidVehicleReader
import com.aos.agent.system.vehicle.VehicleBatteryReader
import com.aos.agent.system.vehicle.VehiclePropertyAllowlist
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 首页与面板使用同一个采集口径；连接和定时任务跟随调用页面释放。 */
class SystemSnapshot internal constructor(initial: SystemInfo) {
    var info by mutableStateOf(initial.copy(battery = BatterySnapshot(null, null)))
        internal set
    var batteryStatus by mutableStateOf<DegradeStatus?>(null)
        internal set
    var updatedAt by mutableStateOf("")
        internal set
    internal var refreshAction: () -> Unit = {}
    fun refresh() = refreshAction()
}

@Composable
fun rememberSystemSnapshot(provider: SystemInfoProvider): SystemSnapshot {
    val context = LocalContext.current
    val snapshot = remember(provider) { SystemSnapshot(provider.collect()) }
    val vehicle = remember(context) { AndroidVehicleReader(context) }
    val reader = remember(vehicle) { VehicleBatteryReader(vehicle, VehiclePropertyAllowlist.parseAsset(context)) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        val collected = provider.collect()
        val battery = reader.read()
        val merged = collected.battery.withVehicleLevel(battery.levelPercent)
        snapshot.info = collected.copy(battery = merged)
        snapshot.batteryStatus = if (merged.levelPercent == null) battery.unavailableStatus else null
        snapshot.updatedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    SideEffect { snapshot.refreshAction = { scope.launch { reload() } } }
    DisposableEffect(vehicle) { onDispose { vehicle.close() } }
    LaunchedEffect(provider, reader) {
        while (true) { reload(); delay(5_000) }
    }
    return snapshot
}
