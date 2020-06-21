package io.github.domi04151309.alwayson

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.objects.Root
import io.github.domi04151309.alwayson.objects.Theme
import io.github.domi04151309.alwayson.preferences.Preferences
import io.github.domi04151309.alwayson.services.ForegroundService
import io.github.domi04151309.alwayson.services.MyAccessibility
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*


class MainActivity : Activity() {

    private var clockTxt: TextView? = null
    private var dateTxt: TextView? = null
    private var batteryTxt: TextView? = null

    private val mBatInfoReceiver = object : BroadcastReceiver() {

        override fun onReceive(ctxt: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            batteryTxt!!.text = resources.getString(R.string.percent, level)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            if (isCharging) batteryTxt!!.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_charging, 0, 0, 0)
            else batteryTxt!!.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }

    private val isNotificationServiceEnabled: Boolean
        get() {
            val pkgName = packageName
            val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null) {
                        if (TextUtils.equals(pkgName, cn.packageName)) {
                            return true
                        }
                    }
                }
            }
            return false
        }

    private val isDeviceAdmin: Boolean
        get() {
            return MyAccessibility.isAccessibilitySettingsOn(this)

        }

    private val isDeviceRoot: Boolean
        get() {
            if (Root.request()) {
                PreferenceManager.getDefaultSharedPreferences(this).edit().putBoolean("root_mode", true).apply()
            }

            return PreferenceManager.getDefaultSharedPreferences(this).getBoolean("root_mode", false)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        Theme.set(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)




        try {
            ContextCompat.startForegroundService(this, Intent(this, ForegroundService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, R.string.err_service_failed, Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.pref).setOnClickListener { startActivity(Intent(this@MainActivity, Preferences::class.java)) }

        //Battery
        batteryTxt = findViewById(R.id.batteryTxt)
        registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        //Date and time updates
        val clock = prefs.getBoolean("hour", false)
        val amPm = prefs.getBoolean("am_pm", false)
        val dateFormat = if (clock) {
            if (amPm) "h:mm a"
            else "h:mm"
        } else "H:mm"

        clockTxt = findViewById(R.id.clockTxt)
        dateTxt = findViewById(R.id.dateTxt)

        clockTxt!!.text = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Calendar.getInstance())
        dateTxt!!.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Calendar.getInstance())
        object : Thread() {
            override fun run() {
                try {
                    while (!isInterrupted) {
                        sleep(1000)
                        runOnUiThread {
                            dateTxt!!.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Calendar.getInstance())
                            clockTxt!!.text = SimpleDateFormat(dateFormat, Locale.getDefault()).format(Calendar.getInstance())
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Global.LOG_TAG, e.toString())
                }
            }
        }.start()

        if (applicationContext.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.READ_PHONE_STATE),
                    0)
        }

        if (!isDeviceAdmin) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

        if (!Settings.System.canWrite(this)) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_SETTINGS), 101);
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 42)
        }

        try {
            var userRefresh = Settings.Global.getInt(this.contentResolver, "oneplus_screen_refresh_rate", 1)
            Settings.Global.putInt(this.contentResolver, "oneplus_screen_refresh_rate", userRefresh)
            permission.visibility = View.GONE
        } catch (e: java.lang.Exception) {
            Root.WriteSupportBatch(this)
            permission.text = "You have to grant permission in adbshell -> \nadb shell pm grant " + this.packageName + "\nandroid.permission.WRITE_SECURE_SETTINGS\n\nyou find a batch on sdcard in \\Android\\data\\" + this.packageName + "\\files\\"
            permission.visibility = View.VISIBLE
        }


        isDeviceRoot

        if (!isNotificationServiceEnabled) startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        if (!Settings.canDrawOverlays(this)) startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 1)


        val intent = Intent()
        val packageName = packageName
        val pm: PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

    }

    override fun onStart() {
        super.onStart()




    }

    /* private fun buildDialog(case: Int) {
         val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.DialogTheme))

         when (case) {
             1 -> {
                 builder.setTitle(R.string.device_admin)
                 builder.setMessage(R.string.device_admin_summary)
                 builder.setPositiveButton(resources.getString(android.R.string.ok)) { dialog, _ ->
                     //startActivity(Intent(this@MainActivity, PermissionPreferences::class.java))
                     startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                     dialog.cancel()
                     finish()
                 }
             }
             2 -> {
                 builder.setTitle(R.string.notification_listener_service)
                 builder.setMessage(R.string.notification_listener_service_summary)
                 builder.setPositiveButton(resources.getString(android.R.string.ok)) { dialog, _ ->
                     startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                     dialog.cancel()
                     finish()
                 }
             }
             3 -> {
                 builder.setTitle(R.string.setup_draw_over_other_apps)
                 builder.setMessage(R.string.setup_draw_over_other_apps_summary)
                 builder.setPositiveButton(resources.getString(android.R.string.ok)) { dialog, _ ->
                     startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 1)
                     dialog.cancel()
                     finish()
                 }
             }
             else -> return
         }

         builder.setNegativeButton(resources.getString(android.R.string.cancel)) { dialog, _ -> dialog.cancel() }

         builder.show()
     }*/

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    public override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mBatInfoReceiver)
    }

}

