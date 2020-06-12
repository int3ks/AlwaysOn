package io.github.domi04151309.alwayson.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager

import io.github.domi04151309.alwayson.charging.Circle
import io.github.domi04151309.alwayson.charging.Flash
import io.github.domi04151309.alwayson.charging.IOS
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.objects.Global

class CombinedServiceReceiver : BroadcastReceiver() {




    override fun onReceive(c: Context, intent: Intent) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(c)
        val alwaysOnIsEnabled = prefs.getBoolean("always_on", false)

        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val chargePlug: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                Rules.isPlugged = chargePlug == BatteryManager.BATTERY_PLUGGED_AC || chargePlug == BatteryManager.BATTERY_PLUGGED_USB || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;
                val newLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)

                if(newLevel != Rules.batteryLevel){
                    Rules.batteryLevel = newLevel
                    LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.BATTERYLEVEL_CHANGED))
                    Rules(c, prefs).checkAlwaysOnRuningState("BatteryLevelChanged")
                }
            }

            Intent.ACTION_POWER_CONNECTED -> {
                val chargePlug: Int = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                Rules.isPlugged = chargePlug == BatteryManager.BATTERY_PLUGGED_AC || chargePlug == BatteryManager.BATTERY_PLUGGED_USB || chargePlug == BatteryManager.BATTERY_PLUGGED_WIRELESS;




                if (prefs.getBoolean("charging_animation", false)) {
                    if (!Rules.isScreenOn(c) || Rules.isAlwaysOnRunning) {
                        if (Rules.isAlwaysOnRunning) LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP))
                        val i: Intent = when (prefs.getString("charging_style", "circle")) {
                            "ios" -> Intent(c, IOS::class.java)
                            "circle" -> Intent(c, Circle::class.java)
                            else -> Intent(c, Flash::class.java)
                        }
                        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        c.startActivity(i)
                    }
                } else {
                    Rules(c, prefs).checkAlwaysOnRuningState("PowerConnected")
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Rules(c, prefs).checkAlwaysOnRuningState("PowerDisconnected")
            }
            Intent.ACTION_SCREEN_OFF -> {
                Rules(c,prefs).OnScreenOff()
            }

            Intent.ACTION_SCREEN_ON -> {
                Rules(c,prefs).OnScreenOn()
            }
        }
    }
}
