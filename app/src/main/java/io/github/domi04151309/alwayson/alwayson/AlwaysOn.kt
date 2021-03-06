package io.github.domi04151309.alwayson.alwayson

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.*
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
import android.widget.TextView
import androidx.annotation.RequiresApi

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.domi04151309.alwayson.OffActivity
import io.github.domi04151309.alwayson.R
import io.github.domi04151309.alwayson.adapters.NotificationGridAdapter

import io.github.domi04151309.alwayson.helpers.Rules
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.objects.Root
import io.github.domi04151309.alwayson.services.MyAccessibility
import io.github.domi04151309.alwayson.services.NotificationService
import kotlinx.android.synthetic.main.activity_ao_always.*
import kotlinx.android.synthetic.main.activity_ao_always.batteryIcn
import kotlinx.android.synthetic.main.activity_ao_always.batteryTxt
import kotlinx.android.synthetic.main.activity_ao_always.batteryinfo
import kotlinx.android.synthetic.main.activity_ao_always.clockFrame
import kotlinx.android.synthetic.main.activity_ao_always.dateTxt
import kotlinx.android.synthetic.main.activity_ao_always.fullscreen_content
import kotlinx.android.synthetic.main.activity_ao_always.notifications_grid
import kotlinx.android.synthetic.main.activity_ao_samsung_2.*

import kotlinx.android.synthetic.main.fingersensor.*
import kotlinx.android.synthetic.main.mediainfo.*
import java.lang.Thread.sleep
import java.util.*
import java.util.concurrent.ThreadLocalRandom
import kotlin.collections.HashMap


class AlwaysOn : OffActivity(), MediaSessionManager.OnActiveSessionsChangedListener {

    companion object {
        var mediatext: String = ""
        var mediaIcons: HashMap<String, Icon> = HashMap()
        var mediaInfoManualVisible: Boolean = false
    }

    private var servicesRunning: Boolean = false
    private var userTheme: String? = ""
    private var userRefresh: Int = 1
    private var userDisplayTimeout: Int = -1
    private var aoDoubleTapDisabled: Boolean = false
    private var ao_vibration: Long = 0
    private var comp: ComponentName? = null
    private var msm: MediaSessionManager? = null
    private var localManager: LocalBroadcastManager? = null

    //private var content: View? = null
    //private var fingersensor: ImageView? = null
    private var frame: View? = null
    private var rootMode: Boolean = false
    private var mediaCtl: MediaController? = null

    //Threads
    private var animationThread: Thread = Thread()

    //Settings

    private var aoBatteryIcn: Boolean = true
    private var aoBattery: Boolean = true
    private var aoMediaInfoTxt: Boolean = true
    private var aoNotificationIcons: Boolean = false
    private var animateIcons: Boolean = true
    private var aoPocketMode: Boolean = false
    private var aoHeadsUp: Boolean = false

    private var clockFormat: SimpleDateFormat = SimpleDateFormat("", Locale.getDefault())

    //private var dateTxt: TextView? = null
    private var dateFormat: SimpleDateFormat = SimpleDateFormat("", Locale.getDefault())

    //Battery
    //private var batteryIcn: ImageView? = null
    //private var batteryTxt: TextView? = null

    //Battery saver
    private var powerSaving: Boolean = false
    private var powerSaving60: Boolean = false


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
            mediaInfo.visibility = View.GONE
            return
        }
        mediaCtl?.metadata?.let { meta ->
            meta?.getString(MediaMetadata.METADATA_KEY_TITLE)?.let {
                mediatext = it
                meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.let { mediatext += (" - " + it) }
            }
        }

        if (mediatext == null || mediatext.isEmpty()) {
            mediaInfo.visibility = View.GONE
            return
        }

        mediaInfoTxt?.text = mediatext
        mediaIcons?.get(mediaCtl?.packageName)?.let {
            musicicon?.setImageIcon(it)
        }
        if (mediaCtl?.playbackState?.state?.equals(PlaybackState.STATE_PLAYING)!! || mediaInfoManualVisible) {
            mediaInfo.visibility = View.VISIBLE
        } else {
            mediaInfo.visibility = View.GONE
        }
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

    //clockAndDateRelated
    private var mDateChangedReceiver: BroadcastReceiver? = null
    private fun checkIfDateReceiverNeededAndRegisterIt() {

        val dateTimeFilter = IntentFilter()
        dateTimeFilter.addAction(Intent.ACTION_TIME_TICK)
        dateTimeFilter.addAction(Intent.ACTION_DATE_CHANGED)
        dateTimeFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)

        mDateChangedReceiver = object : BroadcastReceiver() {
            init {
                setClockAndDate()
            }

            override fun onReceive(c: Context, intent: Intent) {
                setClockAndDate()
                val prefs = PreferenceManager.getDefaultSharedPreferences(c)
                val rule = Rules(c, prefs)
                if (!rule.isInTimePeriod()) {
                    LocalBroadcastManager.getInstance(c).sendBroadcast(Intent(Global.REQUEST_STOP_AND_SCREENOFF))
                }
            }

            private fun setClockAndDate() {

                if (minuteTxt != null) {
                    minuteTxt.text = getClockText().split(":")[1]
                    hourTxt.text = getClockText().split(":")[0]
                }
                if (clockTxt != null) {
                    clockTxt.text = getClockText()
                }

                dateTxt!!.text = dateFormat.format(Calendar.getInstance())
            }

            private fun getClockText(): CharSequence {
                return if (userTheme == "oneplus") {
                    Html.fromHtml(clockFormat.format(Calendar.getInstance()).replaceFirst("1", "<font color='#aa0000'>1</font>").replace("\n", "<br>"))
                } else {
                    clockFormat.format(Calendar.getInstance())
                }
            }


        }

        registerReceiver(mDateChangedReceiver, dateTimeFilter)
        //mDateChangedReceiver.setClockAndDate()
    }


    private var mBattLevelReceiver: BroadcastReceiver? = null
    private fun checkBatteryReceiverIfNeeded(prefs: SharedPreferences) {

        aoBatteryIcn = prefs.getBoolean("ao_batteryIcn", false)
        aoBattery = prefs.getBoolean("ao_battery", true)
        if (!aoBatteryIcn) batteryIcn.visibility = View.GONE
        if (!aoBattery) batteryTxt.visibility = View.GONE
        if (aoBattery || aoBatteryIcn) {

            mBattLevelReceiver = object : BroadcastReceiver() {
                init {
                    setBattLevelIconAndText()
                }

                override fun onReceive(c: Context, intent: Intent) {
                    setBattLevelIconAndText()
                }

                private fun setBattLevelIconAndText() {
                    if (aoBattery) batteryTxt!!.text = resources.getString(R.string.percent, Rules.batteryLevel)
                    if (aoBatteryIcn) {
                        if (Rules.isPlugged) {
                            when {
                                Rules.batteryLevel >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100_charging)
                                Rules.batteryLevel >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90_charging)
                                Rules.batteryLevel >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80_charging)
                                Rules.batteryLevel >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60_charging)
                                Rules.batteryLevel >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50_charging)
                                Rules.batteryLevel >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30_charging)
                                Rules.batteryLevel >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_charging)
                                Rules.batteryLevel >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0_charging)
                                else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown_charging)
                            }
                        } else {
                            when {
                                Rules.batteryLevel >= 100 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_100)
                                Rules.batteryLevel >= 90 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_90)
                                Rules.batteryLevel >= 80 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_80)
                                Rules.batteryLevel >= 60 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_60)
                                Rules.batteryLevel >= 50 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_50)
                                Rules.batteryLevel >= 30 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_30)
                                Rules.batteryLevel >= 20 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20)
                                Rules.batteryLevel >= 10 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_20_orange)
                                Rules.batteryLevel >= 0 -> batteryIcn!!.setImageResource(R.drawable.ic_battery_0)
                                else -> batteryIcn!!.setImageResource(R.drawable.ic_battery_unknown)
                            }
                        }
                    }
                }
            }
            localManager!!.registerReceiver(mBattLevelReceiver!!, IntentFilter(Global.BATTERYLEVEL_CHANGED))
        }
    }


    fun animateNotificationGrid() {
        val minAlpha = 0.5f
        val maxAlpha = 1f

        val fadeInDuration = 500L
        val fadeOutDuration = 1000L
        val fadePauseAfterFadeOut = 500L
        val fadePauseAfterFadeIn = 3000L

        notifications_grid!!.animate().cancel()
        if (animateIcons && notifications_grid.adapter?.itemCount ?: 0 > 0) {
            var newAlpha = if (notifications_grid.alpha > minAlpha) minAlpha else maxAlpha
            var newPause = if (notifications_grid.alpha > minAlpha) fadePauseAfterFadeOut else fadePauseAfterFadeIn
            var newDuration = if (newAlpha == maxAlpha) fadeInDuration else fadeOutDuration

            notifications_grid.animate().alpha(newAlpha).setListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator?) {
                    animateNotificationGrid()
                    sleep(newPause)
                }

                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            }).duration = newDuration
        }
    }


    private val mNotificationReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onReceive(c: Context, intent: Intent) {
            val count = intent.getIntExtra("count", 0)
            if (aoNotificationIcons) {
                val itemArray: ArrayList<Icon> = intent.getParcelableArrayListExtra("icons")
                        ?: arrayListOf()
                itemArray.removeIf { it.resPackage.equals(mediaCtl?.packageName) }
                notifications_grid.adapter = NotificationGridAdapter(itemArray)
                animateNotificationGrid()
            }
        }
    }


    //Stop
    private val mStopReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Global.REQUEST_STOP -> {
                    finish()
                    Rules.isAlwaysOnRunning = false
                }
                Global.REQUEST_STOP_AND_SCREENOFF -> {
                    finish()
                    MyAccessibility.instance?.lockScreen()
                    Rules.isAlwaysOnRunning = false
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//Check prefs
        var prefs = PreferenceManager.getDefaultSharedPreferences(this)
        localManager = LocalBroadcastManager.getInstance(this)


        rootMode = prefs.getBoolean("root_mode", false)
        powerSaving = prefs.getBoolean("ao_power_saving", true)
        powerSaving60 = prefs.getBoolean("ao_power_saving_60", true)

        userTheme = prefs.getString("ao_style", "oneplus")


        aoMediaInfoTxt = prefs.getBoolean("ao_mediainformation", true)
        aoNotificationIcons = prefs.getBoolean("ao_notification_icons", true)
        animateIcons = prefs.getBoolean("ao_animate_icons", true)
        aoPocketMode = prefs.getBoolean("ao_pocket_mode", true)
        //aoDND = prefs.getBoolean("ao_dnd", false)
        aoHeadsUp = prefs.getBoolean("heads_up", false)
        val clock = prefs.getBoolean("hour", false)
        val amPm = prefs.getBoolean("am_pm", false)
        val aoForceBrightness = prefs.getBoolean("ao_force_brightness", false)
        ao_vibration = prefs.getInt("ao_vibration", 10).toLong()
        aoDoubleTapDisabled = prefs.getBoolean("ao_double_tap_disabled", true)

        setTheme(R.style.CutoutIgnore)


        when (userTheme) {
            "google" -> setContentView(R.layout.activity_ao_google)
            "samsung" -> setContentView(R.layout.activity_ao_samsung)
            "samsung2" -> setContentView(R.layout.activity_ao_samsung_2)
            "oneplus" -> setContentView(R.layout.activity_ao_oneplus)
            "alwaysonstyle" -> setContentView(R.layout.activity_ao_always)
        }

        checkBatteryReceiverIfNeeded(prefs)

        if (!aoNotificationIcons) notifications_grid.visibility = View.GONE

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

        frame = findViewById<View>(R.id.frame)

        var alpha = (PreferenceManager.getDefaultSharedPreferences(this).getInt("ao_fingerprint_visibility", 3)) / 10f
        fingersensor_ico.alpha = alpha

        var britness = (PreferenceManager.getDefaultSharedPreferences(this).getInt("ao_brightness", 7)) / 10f
        batteryinfo?.alpha = britness
        dateTxt?.alpha = britness
        clockTxt?.alpha = britness
        hourTxt?.alpha = britness
        minuteTxt?.alpha = britness

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
        checkIfDateReceiverNeededAndRegisterIt()

//Notifications
        if (aoNotificationIcons) {
            val layoutManager = LinearLayoutManager(this)
            layoutManager.orientation = LinearLayoutManager.HORIZONTAL
            notifications_grid.layoutManager = layoutManager
        }

//Animation
        var animationDuration = 5000L
        val animationDelay = (prefs!!.getInt("ao_animation_delay", 120) * 1000).toLong()
        if (animationDelay < 6000) animationDuration = 100

        animationThread = object : Thread() {
            override fun run() {
                try {
                    while (fullscreen_content.height == 0) {
                        sleep(10)
                    }

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


        mediaInfo.visibility = View.GONE
        if (aoMediaInfoTxt) {
            msm = this.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            comp = ComponentName(this.applicationContext, NotificationService::class.java.name)
            try {
                msm?.addOnActiveSessionsChangedListener(this, comp)
                onActiveSessionsChanged(msm?.getActiveSessions(comp))
            } catch (e: Exception) {
                Log.e("mediaControlRouter contructor", e.localizedMessage)
            }
        }


        localManager!!.registerReceiver(mStopReceiver, IntentFilter(Global.REQUEST_STOP_AND_SCREENOFF))
        localManager!!.registerReceiver(mStopReceiver, IntentFilter(Global.REQUEST_STOP))


        val rulesTimeout = prefs.getInt("rules_timeout", 0)
        if (rulesTimeout > 0) {
            Handler().postDelayed({
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Global.REQUEST_STOP_AND_SCREENOFF))
            }, rulesTimeout * 60000L)
        }
    }


    private fun setTouchlistener(enabled: Boolean) {
        if (!enabled) {
            mediaInfoTxt!!.setOnTouchListener(null)
            frame!!.setOnTouchListener(null)
            fingersensor_ico!!.setOnTouchListener(null)
            clockFrame.setOnTouchListener(null)
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
                        LocalBroadcastManager.getInstance(this@AlwaysOn).sendBroadcast(Intent(Global.REQUEST_STOP))
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

        fun restartMediaInfoAnimation() {
            mediaInfo.animate().cancel()
            mediaInfo.alpha = 1f
            mediaInfo.animate().alpha(0.5f).setListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator?) {
                    if (!mediaCtl?.playbackState?.state?.equals(PlaybackState.STATE_PLAYING)!! && mediaInfo.alpha <= 0.5f) {
                        mediaInfoManualVisible = false
                        checkMediaInfoVisibility()
                    }
                }

                override fun onAnimationRepeat(animation: Animator?) {}
                override fun onAnimationCancel(animation: Animator?) {}
                override fun onAnimationStart(animation: Animator?) {}
            }).duration = 8000
        }

        clockFrame.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    mediaInfoManualVisible = !mediaInfoManualVisible
                    checkMediaInfoVisibility()
                    restartMediaInfoAnimation()
                    return super.onDoubleTap(e)
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                gestureDetector.onTouchEvent(event)
                v.performClick()
                return true
            }
        })


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
                        restartMediaInfoAnimation()
                        return true
                    }
                    return false
                }
            })

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                if (mediaInfo.alpha > 0.5f) {
                    restartMediaInfoAnimation()
                }
                gestureDetector.onTouchEvent(event)
                v.performClick()
                return true
            }
        })

        fingersensor_ico.setOnTouchListener(object : View.OnTouchListener {
            private val gestureDetector = GestureDetector(this@AlwaysOn, object : GestureDetector.SimpleOnGestureListener() {
                @RequiresApi(Build.VERSION_CODES.O)
                override fun onLongPress(e: MotionEvent?) {
                    if (ao_vibration > 0) {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(ao_vibration, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    LocalBroadcastManager.getInstance(this@AlwaysOn).sendBroadcast(Intent(Global.REQUEST_STOP))
                    super.onLongPress(e)
                }

                @RequiresApi(Build.VERSION_CODES.O)
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (ao_vibration > 0) {
                        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                        vibrator.vibrate(VibrationEffect.createOneShot(ao_vibration, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    LocalBroadcastManager.getInstance(this@AlwaysOn).sendBroadcast(Intent(Global.REQUEST_STOP))
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

    fun animateContent(animationDuration: Long) {
        Log.i("always", "fingersensortop:${fingersensorTopPositionRange.toInt()} fingersensorleft:${fingersensorLeftPositionRange.toInt()} contenttop:${contentTopPositionRange.toInt()}")
        fingersensor_ico.animate().y(fingersensorTopPositionRange).x(fingersensorLeftPositionRange).duration = animationDuration
        fullscreen_content.animate().y(contentTopPositionRange).duration = animationDuration
    }

    val screenHeight: Int get() = windowManager.defaultDisplay.mode.physicalHeight
    val screenWidth: Int get() = windowManager.defaultDisplay.mode.physicalWidth


    val contentTopPositionRange: Float get() = ThreadLocalRandom.current().nextInt(100, screenHeight / 4).toFloat()
    val fingersensorLeftPositionRange: Float get() = (screenWidth * (0.50f)) - (fingersensor_ico.width / 2) + ((ThreadLocalRandom.current().nextFloat() * 20) - 10)
    val fingersensorTopPositionRange: Float get() = (screenHeight * (0.803f)) - (fingersensor_ico.height / 2) + ((ThreadLocalRandom.current().nextFloat() * 40) - 20)


    override fun onStart() {
        super.onStart()
        hideUI()
        Rules.isAlwaysOnRunning = true
        if (!servicesRunning) {
            servicesRunning = true

            setTouchlistener(true)

// Notification Listener
            if (aoMediaInfoTxt || aoNotificationIcons || animateIcons) {
                localManager!!.registerReceiver(mNotificationReceiver, IntentFilter(Global.NOTIFICATIONS))
                localManager!!.sendBroadcast(Intent(Global.REQUEST_NOTIFICATIONS))
            }
        }

        if (aoHeadsUp) {
            try {
                Settings.Global.putInt(this.contentResolver, "heads_up_notifications_enabled", 0)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    Root.shell("settings put global heads_up_notifications_enabled 0")
                }
            }
        }
        // Power saving mode
        if (powerSaving) {
            try {
                userDisplayTimeout = Settings.System.getInt(this.contentResolver, "screen_off_timeout", -1)
                Settings.Global.putInt(this.contentResolver, "low_power", 1)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    Root.shell("settings put global low_power 1")
                }
            }
            if (rootMode) {
                Root.shell("dumpsys deviceidle force-idle")
            }
        }
        if (powerSaving60) {
            try {
                userRefresh = Settings.Global.getInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
                Settings.Global.putInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    userRefresh = Settings.Global.getInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
                    Root.shell("settings put global oneplus_screen_refresh_rate 1")
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Rules.isAlwaysOnRunning = false

        if (servicesRunning) {
            servicesRunning = false

            setTouchlistener(false)

// Notification Listener
            if (aoMediaInfoTxt || aoNotificationIcons || animateIcons) localManager!!.unregisterReceiver(mNotificationReceiver)
        }

        if (powerSaving) {
            try {
                Settings.Global.putInt(this.contentResolver, "low_power", 0)
                if (userDisplayTimeout > 0) Settings.System.putInt(this.contentResolver, "screen_off_timeout", userDisplayTimeout)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    Root.shell("settings put global low_power 0")
                    if (userDisplayTimeout > 0) Root.shell("settings put system screen_off_timeout " + userDisplayTimeout)
                }
            }
        }
        if (powerSaving60) {
            try {
                Settings.Global.putInt(this.contentResolver, "oneplus_screen_refresh_rate", userRefresh)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    Root.shell("settings put global oneplus_screen_refresh_rate " + userRefresh)
                }
            }
        }

        if (aoHeadsUp) {
            try {
                Settings.Global.putInt(this.contentResolver, "heads_up_notifications_enabled", 1)
            } catch (e: java.lang.Exception) {
                if (rootMode) {
                    Root.shell("settings put global heads_up_notifications_enabled 1")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        Rules.isAlwaysOnRunning = false


        animationThread.interrupt()

        // Date Receiver
        mDateChangedReceiver?.let { unregisterReceiver(it) }

        localManager!!.unregisterReceiver(mStopReceiver)
        mBattLevelReceiver?.let { localManager!!.unregisterReceiver(it) }
    }

    private fun hideUI() {
        fullscreen_content.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)

        fingersensor_ico.systemUiVisibility = (View.SYSTEM_UI_FLAG_LOW_PROFILE
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }


}
