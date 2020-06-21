package io.github.domi04151309.alwayson.objects

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import android.view.View
import android.view.WindowManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.alwayson.AlwaysOnQS
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver

import io.github.domi04151309.alwayson.services.MyAccessibility

object Global {

    const val BATTERYLEVEL_CHANGED = "io.github.domi04151309.alwayson.BATTERYLEVEL_CHANGED"
    const val LOG_TAG = "AlwaysOn"

    const val REQUEST_DETAILED_NOTIFICATIONS = "io.github.domi04151309.alwayson.REQUEST_DETAILED_NOTIFICATIONS"
    const val DETAILED_NOTIFICATIONS = "io.github.domi04151309.alwayson.DETAILED_NOTIFICATIONS"
    const val REQUEST_NOTIFICATIONS = "io.github.domi04151309.alwayson.REQUEST_NOTIFICATIONS"
    const val NOTIFICATIONS = "io.github.domi04151309.alwayson.NOTIFICATIONS"

    const val REQUEST_STOP = "io.github.domi04151309.alwayson.REQUEST_STOP"
    const val REQUEST_STOP_AND_SCREENOFF = "io.github.domi04151309.alwayson.REQUEST_STOP_AND_SCREENOFF"

    const val ALWAYS_ON_STATE_CHANGED = "io.github.domi04151309.alwayson.ALWAYS_ON_STATE_CHANGED"

    fun currentAlwaysOnState(context: Context): Boolean{
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean("always_on", false)
    }

    fun changeAlwaysOnState(context: Context): Boolean {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val value = !prefs.getBoolean("always_on", false)
        prefs.edit().putBoolean("always_on", value).apply()
        TileService.requestListeningState(context, ComponentName(context ,AlwaysOnQS::class.java))
        LocalBroadcastManager.getInstance(context).sendBroadcast(Intent().setAction(ALWAYS_ON_STATE_CHANGED))
        return value
    }

    fun close_old(context: Context) {
        MyAccessibility.instance?.lockScreen()
        (context as Activity).finish()
    }

    fun fullscreen(context: Context, rootLayout: View) {
        val activity = context as Activity
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)

        rootLayout.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }
}