package io.github.domi04151309.alwayson.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.R
import io.github.domi04151309.alwayson.alwayson.AlwaysOnQS
import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver
import java.lang.Exception

class ForegroundService : Service(), SensorEventListener {



    private lateinit var rules: Rules
    private lateinit var prefs: SharedPreferences
    private val combinedServiceReceiver = CombinedServiceReceiver()

    //Proximity
    private var mSensorManager: SensorManager? = null
    private var mProximity: Sensor? = null

    companion object {
        private var lastIsInPocketState: Boolean=false
        const val CHANNEL_ID = "service_channel"
        private const val SENSOR_DELAY_SLOW: Int = 1000000
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_SCREEN_ON)
        filter.addAction(Intent.ACTION_BATTERY_CHANGED)
        filter.addAction(Intent.ACTION_POWER_CONNECTED)
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED)

        registerReceiver(combinedServiceReceiver, filter)
        TileService.requestListeningState(this, ComponentName(this, AlwaysOnQS::class.java))

        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        rules = Rules(this, prefs)

        mSensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        mProximity = mSensorManager!!.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        mSensorManager!!.registerListener(this, mProximity, SENSOR_DELAY_SLOW, SENSOR_DELAY_SLOW)

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}


    //Proximity

    //var timeStartInPocket :Long = 0
    private var inPocketThread: Thread? = null
    override fun onSensorChanged(p0: SensorEvent?) {
        if (p0!!.sensor.type == Sensor.TYPE_PROXIMITY) {
            var currentInPocketState = p0.values[0] != p0.sensor.maximumRange

            if(currentInPocketState){
                 inPocketThread = object : Thread() {
                    override fun run() {
                        try {
                            sleep(5 * 1000)
                            if(!isInterrupted) {
                                Rules.isInPocket = true
                                if(Rules.isAlwaysOnRunning) {
                                    Rules.StopAlwaysOn(this@ForegroundService, "isInPocket")
                                }
                                //rules.checkAlwaysOnRuningState("SensorChange in pocket")
                            }
                        }catch (e:Exception){}
                    }
                }
                inPocketThread?.start()
            }

            if(!currentInPocketState){
                inPocketThread?.interrupt()
                if(Rules.isInPocket ) {
                    Rules.isInPocket = false
                    rules.checkAlwaysOnRuningState("SensorChange not in pocket")
                }
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