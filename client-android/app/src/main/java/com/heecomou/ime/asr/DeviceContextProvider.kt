package com.heecomou.ime.asr

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager

class DeviceContextProvider(private val context: Context) {

    data class DeviceContext(
        val isOnline: Boolean,
        val networkType: String,
        val signalStrength: Float,
        val batteryLevel: Float,
        val isCharging: Boolean
    )

    fun getCurrentContext(): DeviceContext {
        val connectivity = getConnectivityInfo()
        val battery = getBatteryInfo()

        return DeviceContext(
            isOnline = connectivity.isOnline,
            networkType = connectivity.networkType,
            signalStrength = connectivity.signalStrength,
            batteryLevel = battery.level,
            isCharging = battery.isCharging
        )
    }

    private fun getConnectivityInfo(): ConnectivityInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return ConnectivityInfo(false, "unknown", 0f)

        val network = cm.activeNetwork ?: return ConnectivityInfo(false, "none", 0f)
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectivityInfo(false, "none", 0f)

        val isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        val networkType = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }

        val signalStrength = 1.0f

        return ConnectivityInfo(isOnline, networkType, signalStrength)
    }

    private fun getBatteryInfo(): BatteryInfo {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val intent = context.registerReceiver(null, intentFilter)
            ?: return BatteryInfo(1.0f, false)

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

        val batteryPct = if (scale > 0) level.toFloat() / scale.toFloat() else 1.0f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return BatteryInfo(batteryPct, isCharging)
    }

    private data class ConnectivityInfo(
        val isOnline: Boolean,
        val networkType: String,
        val signalStrength: Float
    )

    private data class BatteryInfo(
        val level: Float,
        val isCharging: Boolean
    )
}
