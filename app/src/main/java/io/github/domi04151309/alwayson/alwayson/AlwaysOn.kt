package io.github.domi04151309.alwayson.alwayson

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.*
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.drawable.Icon

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.*
import android.provider.Settings
import android.text.Html
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.domi04151309.alwayson.OffActivity
import io.github.domi04151309.alwayson.R
import io.github.domi04151309.alwayson.adapters.NotificationGridAdapter

import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.objects.Root
import io.github.domi04151309.alwayson.receivers.CombinedServiceReceiver
import io.github.domi04151309.alwayson.services.NotificationService
import kotlinx.android.synthetic.main.mediainfo.*
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.HashMap


class AlwaysOn : OffActivity(), MediaSessionManager.OnActiveSessionsChangedListener {

    companion object {
        private const val CLOCK_DELAY: Long = 60000
        private const val SENSOR_DELAY_SLOW: Int = 1000000
        var mediatext: String = ""
        public var mediaIcons: HashMap<String, Icon> = HashMap()
        var servicesRunning: Boolean = false
    }


    private var userTheme: String? = ""
    private var userRefresh: Int = 1
    private var aoDoubleTapDisabled: Boolean = false
    private var ao_vibration: Long = 0
    private lateinit var comp: ComponentName
    private lateinit var msm: MediaSessionManager
    private var localManager: LocalBroadcastManager? = null
    private var content: View? = null
    private var fingersensor: ImageView? = null
    private var frame: View? = null
    private var rootMode: Boolean = false

    private var screenSize: Float = 0F
    private var mediaCtl: MediaController? = null

    //Media
    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        mediaCtl = controllers?.firstOrNull()
        mediaCtl?.let {
            it.registerCallback(sessionCallback)
        }
        checkMediaInfoVisibility()
    }

    fun checkMediaInfoVisibility() {
        if (mediaCtl == null) {
            mediaInfo!!.visibility = View.GONE
            return
        }
        mediaCtl?.metadata?.let { meta ->
            meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.let {
                mediatext = it
                meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.let { mediatext += (" - " + it) }
            }
        }

        if (mediatext == null || mediatext.isEmpty()) {
            mediaInfo!!.visibility = View.GONE
            return
        }

        mediaInfoTxt?.text = mediatext
        mediaIcons?.get(mediaCtl?.packageName)?.let {
            musicicon?.setImageIcon(it)
        }

        mediaInfo!!.visibility = View.VISIBLE

    }

    var sessionCallback: MediaController.Callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            checkMediaInfoVisibility()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            checkMediaInfoVisibility()
        }
    }

    //Threads
    private var aoAnimateIconsThread: Thread? = null
    private var animationThread: Thread = Thread()

    //Settings
    private var aoClock: Boolean = true
    private var aoDate: Boolean = true
    private var aoBatteryIcn: Boolean = true
    private var aoBattery: Boolean = true
    private var aoMediaInfoTxt: Boolean = true
    private var aoNotificationIcons: Boolean = false
    private var animateIcons: Boolean = true
    private var aoPocketMode: Boolean = false
    private var aoDND: Boolean = false
    private var aoHeadsUp: Boolean = false

    //Time
    private var clockTxt: TextView? = null
    private var clockFormat: SimpleDateFormat = SimpleDateFormat("", Locale.getDefault())
    private val clockHandler = Handler()
    private val clockRunnable = object : Runnable {
        override fun run() {
            clockTxt!!.text = getClockText()
            clockHandler.postDelayed(this, CLOCK_DELAY)
        }
    }

    //Date
    private var dateTxt: TextView? = null
    private var dateFormat: SimpleDateFormat = SimpleDateFormat("", Locale.getDefault())
    private val mDateChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            dateTxt!!.text = dateFormat.format(Calendar.getInstance())
        }
    }
    private val dateFilter = IntentFilter()

    //Battery
    private var batteryIcn: ImageView? = null
    private var batteryTxt: TextView? = null
    private var batteryFilter = IntentFilter()
    private val mBatInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    if (level <= rulesBattery) {
                        stopAndOff()
                        return
                    }
                    if (aoBattery) batteryTxt!!.text = resources.getString(R.string.percent, level)
                    if (status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL) {
                        if (aoBatteryIcn) when {
                            level >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100_charging)
                            level >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90_charging)
                            level >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80_charging)
                            level >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60_charging)
                            level >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50_charging)
                            level >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30_charging)
                            level >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_charging)
                            level >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0_charging)
                            else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown_charging)
                        }
                    } else {
                        if (aoBatteryIcn) when {
                            level >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100)
                            level >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90)
                            level >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80)
                            level >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60)
                            level >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50)
                            level >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30)
                            level >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20)
                            level >= 10 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_orange)
                            level >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0)
                            else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown)
                        }
                    }
                }
                Intent.ACTION_POWER_CONNECTED -> {
                    if (rulesChargingState == "discharging") stopAndOff()
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    if (rulesChargingState == "charging") stopAndOff()
                }
            }
        }
    }

    //Notifications

    private var notificationGrid: RecyclerView? = null
    private val mNotificationReceiver = object : BroadcastReceiver() {

        override fun onReceive(c: Context, intent: Intent) {
            val count = intent.getIntExtra("count", 0)
            if (aoNotificationIcons) {
                val itemArray: ArrayList<Icon> = intent.getParcelableArrayListExtra("icons")
                        ?: arrayListOf()
                itemArray.removeIf { it.resPackage.equals(mediaCtl?.packageName) }
                notificationGrid!!.adapter = NotificationGridAdapter(itemArray)

                if (animateIcons && notificationGrid?.adapter?.itemCount ?: 0 > 0) {
                    if (aoAnimateIconsThread == null) {
                        aoAnimateIconsThread = object : Thread() {
                            override fun run() {
                                try {
                                    while (!isInterrupted) {
                                        if (notificationGrid!!.alpha > 0f) {
                                            notificationGrid!!.animate().alpha(0f).duration = 1000
                                        } else {
                                            notificationGrid!!.animate().alpha(1f).duration = 300
                                        }
                                        sleep(1500)
                                    }
                                } catch (e: Exception) {
                                    Log.e(Global.LOG_TAG, e.toString())
                                }
                            }
                        }
                        aoAnimateIconsThread?.start()
                    }
                } else {
                    aoAnimateIconsThread?.interrupt()
                    aoAnimateIconsThread = null
                }
            }
        }
    }

    //Battery saver
    private var powerSaving: Boolean = false

    //DND
    private var mNotificationManager: NotificationManager? = null
    private var notificationAccess: Boolean = false
    private var userDND: Int = 0

    //Stop
    private val mStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Global.REQUEST_STOP ->
                    finish()
                Global.REQUEST_STOP_AND_OFF ->
                    stopAndOff()
            }
        }
    }

    //Rules
    private var rulesChargingState: String = ""
    private var rulesBattery: Int = 0

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


//Check prefs
        var prefs = PreferenceManager.getDefaultSharedPreferences(this)
        rootMode = prefs.getBoolean("root_mode", false)
        powerSaving = prefs.getBoolean("ao_power_saving", false)
        userTheme = prefs.getString("ao_style", "google")
        aoClock = prefs.getBoolean("ao_clock", true)
        aoDate = prefs.getBoolean("ao_date", true)
        aoBatteryIcn = prefs.getBoolean("ao_batteryIcn", false)
        aoBattery = prefs.getBoolean("ao_battery", true)
        aoMediaInfoTxt = prefs.getBoolean("ao_mediainformation", true)
        aoNotificationIcons = prefs.getBoolean("ao_notification_icons", true)
        animateIcons = prefs.getBoolean("ao_animate_icons", true)
        aoPocketMode = prefs.getBoolean("ao_pocket_mode", true)
        aoDND = prefs.getBoolean("ao_dnd", false)
        aoHeadsUp = prefs.getBoolean("heads_up", false)
        val clock = prefs.getBoolean("hour", false)
        val amPm = prefs.getBoolean("am_pm", false)
        val aoForceBrightness = prefs.getBoolean("ao_force_brightness", false)
        ao_vibration = prefs.getInt("ao_vibration", 64).toLong()
        aoDoubleTapDisabled = prefs.getBoolean("ao_double_tap_disabled", true)

        setTheme(R.style.CutoutIgnore)


        when (userTheme) {
            "google" -> setContentView(R.layout.activity_ao_google)
            "samsung" -> setContentView(R.layout.activity_ao_samsung)
            "samsung2" -> setContentView(R.layout.activity_ao_samsung_2)
            "oneplus" -> setContentView(R.layout.activity_ao_oneplus)
        }

//Watch face
        clockTxt = findViewById(R.id.clockTxt)
        dateTxt = findViewById(R.id.dateTxt)
        batteryIcn = findViewById(R.id.batteryIcn)
        batteryTxt = findViewById(R.id.batteryTxt)
        notificationGrid = findViewById(R.id.notifications_grid)

        if (!aoClock) clockTxt!!.visibility = View.GONE
        if (!aoDate) dateTxt!!.visibility = View.GONE
        if (!aoBatteryIcn) batteryIcn!!.visibility = View.GONE
        if (!aoBattery) batteryTxt!!.visibility = View.GONE
//
        if (!aoNotificationIcons) notificationGrid!!.visibility = View.GONE

        clockFormat = SimpleDateFormat(
                if (userTheme == "samsung" || userTheme == "oneplus") {
                    if (clock) {
                        if (amPm) "hh\nmm\na"
                        else "hh\nmm"
                    } else "HH\nmm"
                } else {
                    if (clock) {
                        if (amPm) "h:mm a"
                        else "h:mm"
                    } else "H:mm"
                }, Locale.getDefault()
        )
        dateFormat = SimpleDateFormat("EEE d MMMM", Locale.getDefault()
        )

//Brightness
        if (aoForceBrightness) {
            val brightness: Float = prefs.getInt("ao_force_brightness_value", 50) / 255.toFloat()
            val lp = window.attributes
            lp.screenBrightness = brightness
            window.attributes = lp
        }

//Variables
        localManager = LocalBroadcastManager.getInstance(this)
        frame = findViewById<View>(R.id.frame)
        content = findViewById(R.id.fullscreen_content)
        fingersensor = findViewById(R.id.fingersensor)
        var alpha = (PreferenceManager.getDefaultSharedPreferences(this).getInt("ao_fingerprint_visibility", 3)) / 10f
        fingersensor!!.alpha = alpha

//Show on lock screen
        Handler().postDelayed({
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }, 300L)

//Hide UI
        hideUI()
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0)
                hideUI()
        }

//Time
        if (aoClock) clockTxt!!.text = getClockText()

//Date
        if (aoDate) {
            dateTxt!!.text = dateFormat.format(Calendar.getInstance())
            dateFilter.addAction(Intent.ACTION_DATE_CHANGED)
            dateFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }

//Battery
        batteryFilter.addAction(Intent.ACTION_BATTERY_CHANGED)
        batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED)

//Notifications
        if (aoNotificationIcons) {
            val layoutManager = LinearLayoutManager(this)
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL
            notificationGrid!!.layoutManager = layoutManager


        }


//DND
        if (aoDND) {
            mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationAccess = mNotificationManager!!.isNotificationPolicyAccessGranted
            if (notificationAccess) userDND = mNotificationManager!!.currentInterruptionFilter
        }

//Edge Glow
        if (animateIcons) {

        }


//Animation
        var animationDuration = 3000L
        //  val animationScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
        val animationDelay = (prefs!!.getInt("ao_animation_delay", 120) * 1000).toLong()
        if (animationDelay < 5000) animationDuration = 100

/* val animationDuration = 10L
val animationScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f)
val animationDelay = (prefs!!.getInt("ao_animation_delay", 2) * 6 + animationDuration * animationScale + 1000).toLong()
*/
        animationThread = object : Thread() {
            override fun run() {
                try {
                    while (content!!.height == 0) sleep(10)

                    animateContent(0)
                    while (!isInterrupted) {
                        sleep(animationDelay)
                        try {
                            animateContent(animationDuration)
                        } catch (e: java.lang.Exception) {
                            Log.e(Global.LOG_TAG, e.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Global.LOG_TAG, e.toString())
                }
            }
        }
        animationThread.start()

        if (!aoMediaInfoTxt) {
            mediaInfo!!.visibility = View.GONE
        } else {
            msm = this.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            comp = ComponentName(this.applicationContext, NotificationService::class.java.name)
            try {
                msm.addOnActiveSessionsChangedListener(this, comp)
                onActiveSessionsChanged(msm.getActiveSessions(comp))
            } catch (e: Exception) {
                Log.e("mediaControlRouter contructor", e.localizedMessage)
            }
        }


//Stop
        localManager!!.registerReceiver(mStopReceiver, IntentFilter(Global.REQUEST_STOP))
        localManager!!.registerReceiver(mStopReceiver, IntentFilter(Global.REQUEST_STOP_AND_OFF))

//Rules
        rulesChargingState = prefs.getString("rules_charging_state", "always") ?: "always"
        rulesBattery = prefs.getInt("rules_battery_level", 0)

        if (Rules(this, prefs).millisTillEnd() >= 0) {
            Handler().postDelayed({
                stopAndOff()
            }, Rules(this, prefs).millisTillEnd())
        }

        val rulesTimeout = prefs.getInt("rules_timeout", 0)
        if (rulesTimeout != 0) {
            Handler().postDelayed({
                stopAndOff()
            }, rulesTimeout * 60000L)
        }
    }

    private fun getClockText(): CharSequence? {

        return if (userTheme == "oneplus") {
            Html.fromHtml(clockFormat.format(Calendar.getInstance()).replaceFirst("1", "<font color='#aa0000'>1</font>").replace("\n", "<br>"))
        } else {
            clockFormat.format(Calendar.getInstance())
        }
    }

    private fun setTouchlistener(enabled: Boolean) {

        if (!enabled) {
            mediaInfoTxt!!.setOnTouchListener(null)
            frame!!.setOnTouchListener(null)
            fingersensor!!.setOnTouchListener(null)
            return
        }

        //DoubleTap
        if (!aoDoubleTapDisabled) {
            frame!!.setOnTouchListener(object : View.OnTouchListener {
                private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        if (ao_vibration > 0) {
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            vibrator.vibrate(ao_vibration)
                        }
                        finish()
                        return super.onDoubleTap(e)
                    }
                })

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    gestureDetector.onTouchEvent(event)
                    v.performClick()
                    return true
                }
            })
        }

        mediaInfoTxt!!.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (mediaInfo.alpha > 0.5f) {
                        try {
                            val diffY = e2.y - e1.y
                            val diffX = e2.x - e1.x
                            if (Math.abs(diffX) > Math.abs(diffY)) {
                                if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                                    if (diffX > 0) {
                                        mediaCtl?.let { it.transportControls.skipToPrevious() }
                                    } else {
                                        mediaCtl?.let { it.transportControls.skipToNext() }
                                    }
                                    return true
                                }
                            }
                        } catch (exception: java.lang.Exception) {
                            exception.printStackTrace()
                        }
                    }
                    return false
                }

                override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                    if (mediaInfo.alpha > 0.5f) {
                        mediaCtl?.let {
                            if (it.playbackState?.state == PlaybackState.STATE_PLAYING) {
                                it.transportControls.pause()
                            } else {
                                it.transportControls.play()
                            }
                        }
                        return true
                    }
                    return false
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (mediaInfo.alpha < 1f) {
                        restartAnimation()
                        return true
                    }
                    return false
                }
            })

            fun restartAnimation() {
                mediaInfo.animate().cancel()
                mediaInfo.alpha = 1f
                mediaInfo.animate().alpha(0.5f).duration = 5000
            }

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (mediaInfo.alpha > 0.5f) {
                    restartAnimation()
                }
                gestureDetector.onTouchEvent(event)
                v.performClick()
                return true
            }
        })

        fingersensor!!.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onLongPress(e: MotionEvent?) {
                    if (ao_vibration > 0) {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(ao_vibration, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    finish();
                    super.onLongPress(e)
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (ao_vibration > 0) {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(ao_vibration, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    finish()
                    return super.onDoubleTap(e)
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                v.performClick()
                // finish();
                return true
            }
        })
    }

    fun animateContent(animationDuration: Long) {
        Log.i("always", "fingersensortop:" + fingersensorTopPositionRange + "fingersensorleft:" + fingersensorLeftPositionRange + "/contenttop:" + contentTopPositionRange)
        fingersensor!!.animate().translationY(fingersensorTopPositionRange).translationX(fingersensorLeftPositionRange).duration = animationDuration
        //fingersensor!!.animate().translationX(fingersensorLeftPositionRange).duration = animationDuration
        content!!.animate().translationY(contentTopPositionRange).duration = animationDuration
    }

    val contentTopPositionRange: Float
        get() {
            return getScreenSize() / ((ThreadLocalRandom.current().nextFloat() * 3) + 3)
        }

    val fingersensorLeftPositionRange: Float
        get() {
            val display2 = windowManager.defaultDisplay
            val size = Point()
            display2.getSize(size)
            var screenWidth = size.x

            return (screenWidth * (0.50f)) - (fingersensor!!.width / 2) + ((ThreadLocalRandom.current().nextFloat() * 30) - 15)
        }

    val fingersensorTopPositionRange: Float
        get() {
            val display2 = windowManager.defaultDisplay
            val size = Point()
            display2.getSize(size)
            var screenHeight = size.y

//            return (display2.mode.physicalHeight * (0.767f)) +  ((ThreadLocalRandom.current().nextFloat() * 40) - 20)
            return (display2.mode.physicalHeight * (0.803f)) - (fingersensor!!.height / 2) + ((ThreadLocalRandom.current().nextFloat() * 40) - 20)

            //return screenHeight * (0.82f + ThreadLocalRandom.current().nextFloat() / 50)
        }


    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenSize = getScreenSize()
    }

    override fun onStart() {
        super.onStart()
        hideUI()
        CombinedServiceReceiver.isAlwaysOnRunning = true
//Global.lastPowerKeyPressed=null
        startServices()
        if (aoDND && notificationAccess) mNotificationManager!!.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
        if (aoHeadsUp) {
            if (rootMode) {
                Root.shell("settings put global heads_up_notifications_enabled 0")
            } else {
                try {
                    Settings.Global.putInt(this.contentResolver, "heads_up_notifications_enabled", 0)
                } catch (e: java.lang.Exception) {
                }
            }
        }
        // Power saving mode
        if (powerSaving) {
            if (rootMode) {
                userRefresh = Settings.Global.getInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
                Root.shell("settings put global oneplus_screen_refresh_rate 1")
                Root.shell("settings put global low_power 1")
                Root.shell("dumpsys deviceidle force-idle")
            } else {
                try {
                    userRefresh = Settings.Global.getInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
                    Settings.Global.putInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
                    Settings.Global.putInt(this.contentResolver, "low_power", 1)
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("permission_warning", false).apply()
                } catch (e: java.lang.Exception) {
                    PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("permission_warning", true).apply()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopServices()
        if (aoDND && notificationAccess) mNotificationManager!!.setInterruptionFilter(userDND)
        if (powerSaving) { // && !userPowerSaving) {
            if (rootMode) {
                Root.shell("settings put global oneplus_screen_refresh_rate " + userRefresh)
                Root.shell("settings put global low_power 0")
            } else {
                try {
                    Settings.Global.putInt(this.contentResolver, "low_power", 0)
                    Settings.Global.putInt(this.contentResolver, "oneplus_screen_refresh_rate", userRefresh)
                } catch (e: java.lang.Exception) {
                }
            }
        }
        if (aoHeadsUp) {

            if (rootMode) {
                Root.shell("settings put global heads_up_notifications_enabled 1")
            } else {
                try {
                    Settings.Global.putInt(this.contentResolver, "heads_up_notifications_enabled", 1)
                } catch (e: java.lang.Exception) {
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        CombinedServiceReceiver.isAlwaysOnRunning = false

        if (animateIcons) aoAnimateIconsThread?.interrupt()
        animationThread.interrupt()
        localManager!!.unregisterReceiver(mStopReceiver)
    }

    private fun hideUI() {
        content!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        fingersensor!!.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    private fun getScreenSize(): Float {
        val size = Point()
        windowManager.defaultDisplay.getSize(size)
        return (size.y - content!!.height).toFloat()
    }

    private fun stopAndOff() {
        CombinedServiceReceiver.hasRequestedStop = true
        Global.close(this)
    }

    private fun startServices() {
        if (!servicesRunning) {
            servicesRunning = true

            setTouchlistener(true)

// Clock Handler
            if (aoClock) clockHandler.postDelayed(clockRunnable, CLOCK_DELAY)

// Date Receiver
            if (aoDate) registerReceiver(mDateChangedReceiver, dateFilter)

// Battery Receiver
            registerReceiver(mBatInfoReceiver, batteryFilter)

// Notification Listener
            if (aoMediaInfoTxt || aoNotificationIcons || animateIcons) {
                localManager!!.registerReceiver(mNotificationReceiver, IntentFilter(Global.NOTIFICATIONS))
                localManager!!.sendBroadcast(Intent(Global.REQUEST_NOTIFICATIONS))
            }
        }
    }

    private fun stopServices() {
        if (servicesRunning) {
            servicesRunning = false

            setTouchlistener(false)

// Clock Handler
            if (aoClock) clockHandler.removeCallbacksAndMessages(null)

// Date Receiver
            if (aoDate) unregisterReceiver(mDateChangedReceiver)

// Battery Receiver
            unregisterReceiver(mBatInfoReceiver)

// Notification Listener
            if (aoMediaInfoTxt || aoNotificationIcons || animateIcons) localManager!!.unregisterReceiver(mNotificationReceiver)
        }
    }


}
