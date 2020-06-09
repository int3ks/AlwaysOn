package io.github.domi04151309.alwayson.helpers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.icu.util.Calendar
import android.os.BatteryManager
import android.os.Debug

class Rules(private val c: Context, private val prefs: SharedPreferences) {

    private var now = Calendar.getInstance()
    private var start = Calendar.getInstance()
    private var end = Calendar.getInstance()
    private val batteryStatus: Intent = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter -> c.registerReceiver(null, filter)!! }

    init {
        val startString = prefs.getString("rules_time_start", "0:00") ?: "0:00"
        val endString = prefs.getString("rules_time_end", "0:00") ?: "0:00"
        start[Calendar.MILLISECOND] = 0
        start[Calendar.SECOND] = 0
        start[Calendar.MINUTE] = startString.substringAfter(":").toInt()
        start[Calendar.HOUR_OF_DAY] = startString.substringBefore(":").toInt()
        end[Calendar.MILLISECOND] = 0
        end[Calendar.SECOND] = 0
        end[Calendar.MINUTE] = endString.substringAfter(":").toInt()
        end[Calendar.HOUR_OF_DAY] = endString.substringBefore(":").toInt()
        if (start.after(end)) end.add(Calendar.DATE, 1)
    }

    fun isAlwaysOnDisplayEnabled(): Boolean {
        return prefs.getBoolean("always_on", false)
    }

    fun matchesChargingState(): Boolean {
        if(Debug.isDebuggerConnected()){
            return true
        }

        val chargingState: Int = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val ruleChargingState = prefs.getString("rules_charging_state", "always")
        return (ruleChargingState == "charging" && chargingState > 0) || (ruleChargingState == "discharging" && chargingState == 0) || (ruleChargingState == "always")
    }

    fun matchesBatteryPercentage(): Boolean {
        return batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) > prefs.getInt("rules_battery_level", 0)
    }

    fun isInTimePeriod(): Boolean {
        if(Debug.isDebuggerConnected()){
            return true
        }

        if(start.equals(end)){
            return true
        }
        return now.after(start) && now.before(end)
    }

    fun millisTillEnd(): Long {
        if(start.equals(end)){
            return -1
        }
        return end.time.time - now.time.time
    }
}