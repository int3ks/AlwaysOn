package io.github.domi04151309.alwayson.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager

import io.github.domi04151309.alwayson.TurnOnScreen

import io.github.domi04151309.alwayson.charging.Circle
import io.github.domi04151309.alwayson.charging.Flash
import io.github.domi04151309.alwayson.charging.IOS
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.objects.Global

class CombinedServiceReceiver : BroadcastReceiver() {

    companion object {
        var isInPocket: Boolean = false
        var isScreenOn: Boolean = true
        var isAlwaysOnRunning: Boolean = false
        var hasRequestedStop: Boolean = false
    }


    override fun onReceive(c: Context, intent: Intent) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(c)
        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                if (prefs.getBoolean("charging_animation", false)) {
                    if (!isScreenOn || isAlwaysOnRunning) {
                        if (isAlwaysOnRunning) LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP))
                        val i: Intent = when (prefs.getString("charging_style", "circle")) {
                            "ios" -> Intent(c, IOS::class.java)
                            "circle" -> Intent(c, Circle::class.java)
                            else -> Intent(c, Flash::class.java)
                        }
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        c.startActivity(i)
                    }
                } else{
                    Rules(c, prefs).checkAlwaysOnRuningState("PowerConnected")
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Rules(c, prefs).checkAlwaysOnRuningState("PowerDisconnected")
            }
            Intent.ACTION_SCREEN_OFF -> {
                isScreenOn = false
                val alwaysOn = prefs.getBoolean("always_on", false)
                if (alwaysOn && !hasRequestedStop) {
                    if (isAlwaysOnRunning) {
                        c.startActivity(Intent(c, TurnOnScreen::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        isAlwaysOnRunning = false
                    }else{
                        Rules(c, prefs).checkAlwaysOnRuningState("ScreenOff")
                    }
                } else if (alwaysOn && hasRequestedStop) {
                    hasRequestedStop = false
                    isAlwaysOnRunning = false
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                isScreenOn = true
            }
        }
    }
}
