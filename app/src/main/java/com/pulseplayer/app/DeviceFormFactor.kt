package com.pulseplayer.app

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.pm.ActivityInfo

/**
 * Detects Android TV/Google TV without confusing a landscape phone or tablet for a TV.
 * Device classification is independent of display resolution.
 */
object DeviceFormFactor {
    fun orientationForTelevision(isTelevision: Boolean): Int =
        if (isTelevision) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_USER

    fun preferredOrientation(context: Context): Int = orientationForTelevision(isTelevision(context))

    fun isTelevision(context: Context): Boolean {
        val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature("android.software.leanback")
    }
}
