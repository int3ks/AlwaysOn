package io.github.domi04151309.alwayson.helpers


import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.os.Debug
import android.os.PowerManager
import android.text.format.DateFormat
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.domi04151309.alwayson.BuildConfig
import io.github.domi04151309.alwayson.TurnOnScreen
import io.github.domi04151309.alwayson.alwayson.AlwaysOn
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.preferences.RulesActivity.PreferenceFragment.Companion.DEFAULT_END_TIME
import io.github.domi04151309.alwayson.preferences.RulesActivity.PreferenceFragment.Companion.DEFAULT_START_TIME
import java.io.File
import java.io.FileOutputStream
import java.util.*


class Rules(private val c: Context, private val prefs: SharedPreferences) {


    private var now = Calendar.getInstance()
    private var start = Calendar.getInstance()
    private var end = Calendar.getInstance()




    init {

    }

    fun isAlwaysOnDisplayEnabled(): Boolean {
        return prefs.getBoolean("always_on", false)
    }


    companion object {

        fun StopAlwaysOn(c: Context, reason: String) {
            LogInfo(c, "StopAlwayson ${reason}")
            LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP_AND_SCREENOFF))
            isAlwaysOnRunning = false
        }

        fun LogInfo(c: Context, txt: String) {
            if (BuildConfig.DEBUG) {
                Log.i("checkRunningState:", txt)
                try {
                    val newFolder = c.getExternalFilesDir(null)
                    if (!newFolder!!.exists()) {
                        newFolder.mkdir()
                    }
                    val date = java.util.Calendar.getInstance().time

                    val file = File(newFolder, "${DateFormat.format("yyyy-MM-dd", date)}_checkRunningState.txt")
                    file.createNewFile()
                    val fos: FileOutputStream
                    fos = FileOutputStream(file, true)


                    val sDate = "${DateFormat.format("hh:mm:ss ", date)}".trimIndent()
                    fos.write(sDate.toByteArray())
                    fos.flush()
                    val data = (txt + "\n").toByteArray()
                    fos.write(data)
                    fos.flush()
                    fos.close()
                } catch (e1: Exception) {
                    //*wenn das nich geht , dann nacht mattes ;) *//*
                }
            }
        }

        @JvmField
        var isCameraOn: Boolean = false
        var isScreenOn: Boolean = false
        var alwaysonSwitchScreenOn: Boolean = false
        var OngoingPhonecall: Boolean = false
        var batteryLevel: Int = 0
        var isPlugged: Boolean = false
        var isInPocket: Boolean = false
        var isAlwaysOnRunning: Boolean = false
    }

    fun LogInfo(txt: String) {
        LogInfo(c, txt)
    }

    fun checkAlwaysOnRuningState(because: String) {
        LogInfo("checkRunningState because:" + because)
        if (AlwaysOnShouldBeRunning()) {
            if (!isAlwaysOnRunning) {
                StartAlwaysOn()
            } else {
                LogInfo("should run BUT is allready running")
            }


        } else {
            if (isAlwaysOnRunning) {
                StopAlwaysOn(c, because)
            } else {
                LogInfo("should NOT run AND Isnt running")
            }
        }
    }

    private fun AlwaysOnShouldBeRunning(): Boolean {
        if(isCameraOn){
            LogInfo("should NOT run (Camera is On)")
            return false
        }

        if (isScreenOn) {
            LogInfo("should NOT run (screenIsOn)")
            return false
        }

        val isSysScreenOn = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        if (isSysScreenOn) {
            LogInfo("should NOT run (isSysScreenOn)")
            return false
        }

        if (isInPocket) {
            LogInfo("should NOT run (isInPocket)")
            return false
        }

        if (OngoingPhonecall) {
            LogInfo("should NOT run (ongoingPhonecall)")
            return false
        }

        if (!isAlwaysOnDisplayEnabled()) {
            LogInfo("should NOT run (AlwaysOnIsNotEnabled)")
            return false
        }

        if (!matchesBatteryPercentage()) {
            LogInfo("should NOT run (doesntMatchesBatteryPercentage)")
            return false
        }
        if (!isInTimePeriod()) {
            LogInfo("should NOT run (isNotInTimePeriod)")
            return false
        }

        if (!matchesChargingState()) {
            LogInfo("should NOT run (doesntMatchChargingState)")
            return false
        }
        LogInfo("should run")
        return true
    }

    fun OnScreenOff() {
        isScreenOn = false
        LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP))
        LogInfo("screen OFF event")
        if (isAlwaysOnDisplayEnabled()) {
            checkAlwaysOnRuningState("ScreenOff")
        }
    }

    fun OnScreenOn() {
        isScreenOn = true
        if (alwaysonSwitchScreenOn) {
            alwaysonSwitchScreenOn = false
            LogInfo("screen ON event (alwayson)")
        } else {
            LogInfo("screen ON event (user)")
        }
    }


    private fun StartAlwaysOn() {
        alwaysonSwitchScreenOn = true
        LogInfo("StartAlwayson")
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
            LogInfo("rule is always")
            return true
        }

        if (ruleChargingState == "charging" && isPlugged) {
            LogInfo("rule is charging and is plugged")
            return true
        }
        if (ruleChargingState == "discharging" && !isPlugged) {
            LogInfo("rule is discharging and NOT is plugged")
            return true
        }
        return false

    }

    fun matchesBatteryPercentage(): Boolean {
        return batteryLevel > prefs.getInt("rules_battery_level", 0)
    }








    fun isInTimePeriod(): Boolean {
        var clockFormat = SimpleDateFormat("H:mm", Locale.getDefault())
        val startString = prefs.getString("rules_time_start", DEFAULT_START_TIME) ?: DEFAULT_START_TIME
        val endString = prefs.getString("rules_time_end", DEFAULT_END_TIME) ?: DEFAULT_END_TIME
        start[Calendar.MILLISECOND] = 0
        start[Calendar.SECOND] = 0
        start[Calendar.MINUTE] = startString.substringAfter(":").toInt()
        start[Calendar.HOUR_OF_DAY] = startString.substringBefore(":").toInt()
        end[Calendar.MILLISECOND] = 0
        end[Calendar.SECOND] = 0
        end[Calendar.MINUTE] = endString.substringAfter(":").toInt()
        end[Calendar.HOUR_OF_DAY] = endString.substringBefore(":").toInt()
        if (start.after(end)) end.add(Calendar.DATE, 1)

        if (start.equals(end)) {
            return true
        }
        if( now.after(start) && now.before(end)){
            return true
        }
        var clk = "notInTime now:${clockFormat.format(now)} start:${clockFormat.format(start)} start:${clockFormat.format(end)}"
        LogInfo(clk)

        return false;
    }

   /* fun millisTillEnd(): Long {
        if (start.equals(end)) {
            return -1
        }
        return end.time.time - now.time.time
    }
*/

}