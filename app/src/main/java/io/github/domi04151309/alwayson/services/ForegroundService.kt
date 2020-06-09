package io.github.domi04151309.alwayson.services

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.os.Build
import android.os.IBinder

import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.R
import io.github.domi04151309.alwayson.alwayson.AlwaysOn
import io.github.domi04151309.alwayson.alwayson.AlwaysOnQS
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver

class ForegroundService : Service(), SensorEventListener {

    private val combinedServiceReceiver = CombinedServiceReceiver()

    //Proximity
    private var mSensorManager: SensorManager? = null
    private var mProximity: Sensor? = null

    companion object {
        const val CHANNEL_ID = "service_channel"
        private const val SENSOR_DELAY_SLOW: Int = 1000000
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        registerReceiver(combinedServiceReceiver, filter)
        TileService.requestListeningState(this, ComponentName(this, AlwaysOnQS::class.java))
        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mProximity = mSensorManager!!.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        mSensorManager!!.registerListener(this, mProximity, SENSOR_DELAY_SLOW, SENSOR_DELAY_SLOW)

    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    //Proximity
    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0!!.sensor.type == Sensor.TYPE_PROXIMITY) {
            var locked = (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked();
            if (p0.values[0] == p0.sensor.maximumRange && !AlwaysOn.servicesRunning) {
                val prefs = PreferenceManager.getDefaultSharedPreferences(this)
                val rules = Rules(this, prefs)
                if (locked && rules.isAlwaysOnDisplayEnabled() && rules.matchesBatteryPercentage() && rules.isInTimePeriod()) {
                    this.startActivity(Intent(this, AlwaysOn::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            } else if (p0.values[0] < p0.sensor.maximumRange && AlwaysOn.servicesRunning) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Global.REQUEST_STOP_AND_OFF))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(combinedServiceReceiver)
        mSensorManager!!.unregisterListener(this)
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentText(resources.getString(R.string.service_text))
                .setSmallIcon(R.drawable.ic_always_on_white)
                .setShowWhen(false)
                .build()

        startForeground(1, notification)
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                    CHANNEL_ID,
                    resources.getString(R.string.service_channel),
                    NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(serviceChannel)
        }
    }
}