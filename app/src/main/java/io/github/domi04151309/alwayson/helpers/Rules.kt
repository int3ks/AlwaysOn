package io.github.domi04151309.alwayson.helpers

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.util.Calendar
import android.os.Debug
import android.os.PowerManager
import android.util.Log


import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.domi04151309.alwayson.TurnOnScreen
import io.github.domi04151309.alwayson.alwayson.AlwaysOn
import io.github.domi04151309.alwayson.objects.Global


class Rules(private val c: Context, private val prefs: SharedPreferences) {

    private var now = Calendar.getInstance()
    private var start = Calendar.getInstance()
    private var end = Calendar.getInstance()

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


    companion object {
        fun isScreenOn(c: Context): Boolean {
            val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager?
            val isScreenOn = pm!!.isScreenOn
            return isScreenOn
        }
        fun StopAlwaysOn(c: Context) {
            LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP_AND_SCREENOFF))
            isAlwaysOnRunning = false
        }

        var OngoingPhonecall: Boolean = false
        var batteryLevel: Int = 0
        var isPlugged: Boolean = false
        var AlwaysOnRequestScreenOff: Boolean = false
        var isInPocket: Boolean = false
        //var isScreenOn: Boolean = true
        var isAlwaysOnRunning: Boolean = false
    }

    fun checkAlwaysOnRuningState(because: String) {
        //Log.i("checkRunningState:", "checkRunningState:" + because)
        if (AlwaysOnShouldBeRunning()) {
            if (!isAlwaysOnRunning) {
                //Log.i("checkRunningState:", "StartAlwayson")
                StartAlwaysOn()
            }
        } else {
            if (isAlwaysOnRunning) {
                //Log.i("checkRunningState:", "StopAlwayson")
                StopAlwaysOn(c)
            }
        }
    }

    private fun AlwaysOnShouldBeRunning(): Boolean {

        if (isScreenOn(c)) {
            //Log.i("checkRunningState:", "should NOT run (screenon)")
            return false
        }
        if (isInPocket) {
            //Log.i("checkRunningState:", "should NOT run (inPocket)")
            return false
        }

        if(OngoingPhonecall){
            //Log.i("checkRunningState:", "should NOT run (phonecall)")
            return false
        }

        if (!isAlwaysOnDisplayEnabled()) {
            //Log.i("checkRunningState:", "should NOT run (isAlwaysOnDisplayEnabled)")
            return false
        }

        if (!matchesBatteryPercentage()) {
            //Log.i("checkRunningState:", "should NOT run (matchesBatteryPercentage)")
            return false
        }
        if (!isInTimePeriod()) {
            //Log.i("checkRunningState:", "should NOT run (isInTimePeriod)")
            return false
        }

        if (!matchesChargingState()) {
            //Log.i("checkRunningState:", "should NOT run (isInTimePeriod)")
            return false
        }
        //Log.i("checkRunningState:", "should run")
        return true
    }

    fun OnScreenOff() {
        if (isAlwaysOnDisplayEnabled()) {
            if (!AlwaysOnRequestScreenOff) {
                checkAlwaysOnRuningState("ScreenOff")
            }
        }
    }

    fun OnScreenOn() {
        //Log.i("checkRunningState:", "screen on event")
        AlwaysOnRequestScreenOff = false
    }



    private fun StartAlwaysOn() {
        c.startActivity(Intent(c, AlwaysOn::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        isAlwaysOnRunning = true
    }

    fun turnScreenOn() {
        LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP))
        c.startActivity(Intent(c, TurnOnScreen::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        isAlwaysOnRunning = false
    }

    fun matchesChargingState(): Boolean {

        if (Debug.isDebuggerConnected()) {
            return true
        }

        val ruleChargingState = prefs.getString("rules_charging_state", "always")
        if (ruleChargingState == "always") {
            //Log.i("checkRunningState:", "rule is always")
            return true
        }

        if (ruleChargingState == "charging" && isPlugged) {
            //Log.i("checkRunningState:", "rule is charging and is plugged")
            return true
        }
        if (ruleChargingState == "discharging" && !isPlugged) {
            //Log.i("checkRunningState:", "rule is discharging and NOT is plugged")
            return true
        }
        return false

    }

    fun matchesBatteryPercentage(): Boolean {
        return batteryLevel > prefs.getInt("rules_battery_level", 0)
    }

    fun isInTimePeriod(): Boolean {

        if (start.equals(end)) {
            return true
        }
        return now.after(start) && now.before(end)
    }

    fun millisTillEnd(): Long {
        if (start.equals(end)) {
            return -1
        }
        return end.time.time - now.time.time
    }


}