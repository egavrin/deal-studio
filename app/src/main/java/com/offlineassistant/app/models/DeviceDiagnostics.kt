package com.offlineassistant.app.models

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import androidx.core.content.ContextCompat

data class DeviceDiagnostics(
    val microphonePermissionGranted: Boolean,
    val notificationPermissionGranted: Boolean,
    val freeStorageMb: Long,
    val memoryClassMb: Int,
    val lowRamDevice: Boolean,
    val noiseSuppressorAvailable: Boolean,
    val automaticGainControlAvailable: Boolean,
) {
    companion object {
        fun from(context: Context): DeviceDiagnostics {
            val appContext = context.applicationContext
            val activityManager = appContext.getSystemService(ActivityManager::class.java)
            return DeviceDiagnostics(
                microphonePermissionGranted = appContext.hasPermission(Manifest.permission.RECORD_AUDIO),
                notificationPermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    appContext.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                freeStorageMb = appContext.filesDir.usableSpace / (1_024L * 1_024L),
                memoryClassMb = activityManager.memoryClass,
                lowRamDevice = activityManager.isLowRamDevice,
                noiseSuppressorAvailable = NoiseSuppressor.isAvailable(),
                automaticGainControlAvailable = AutomaticGainControl.isAvailable(),
            )
        }

        private fun Context.hasPermission(permission: String): Boolean =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
