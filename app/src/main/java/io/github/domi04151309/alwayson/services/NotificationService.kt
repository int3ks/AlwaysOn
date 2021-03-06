package io.github.domi04151309.alwayson.services

import android.app.Notification.*
import android.content.*
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Handler
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import io.github.domi04151309.alwayson.alwayson.AlwaysOn
import io.github.domi04151309.alwayson.objects.Global
import io.github.domi04151309.alwayson.objects.JSON
import org.json.JSONArray

class NotificationService : NotificationListenerService() {

    private var cache: Int = -1
    private var localManager: LocalBroadcastManager? = null
    private var prefs: SharedPreferences? = null
    private var sentRecently: Boolean = false

    val categorys = arrayOf(CATEGORY_MESSAGE, CATEGORY_EMAIL, CATEGORY_CALL, CATEGORY_ALARM, CATEGORY_SOCIAL, CATEGORY_REMINDER)

    private val actionReceiver = object : BroadcastReceiver() {

        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Global.REQUEST_DETAILED_NOTIFICATIONS -> {
                    localManager!!.sendBroadcast(Intent(Global.DETAILED_NOTIFICATIONS).putExtra("notifications", activeNotifications))
                }
                Global.REQUEST_NOTIFICATIONS -> {
                    sendCount(true)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        localManager = LocalBroadcastManager.getInstance(this)
        prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val filter = IntentFilter(Global.REQUEST_DETAILED_NOTIFICATIONS)
        filter.addAction(Global.REQUEST_NOTIFICATIONS)
        localManager!!.registerReceiver(actionReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        localManager!!.unregisterReceiver(actionReceiver)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        sendCount()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        sendCount()
    }

    private fun sendCount(force: Boolean = false) {
        if (!sentRecently) {
            sentRecently = true
            var count = 0
            val apps: ArrayList<String> = arrayListOf()
            val icons: ArrayList<Icon> = arrayListOf()
            try {
                val notifications = activeNotifications
                for (notification in notifications) {

                    AlwaysOn?.mediaIcons?.let {

                        it.put(notification.packageName, notification.notification.smallIcon)

                    }
                    if (
                            (categorys.contains(notification?.notification?.category
                                    ?: "") || notification?.notification?.sound != null)
                            && !notification.isOngoing && !JSON.contains(JSONArray(prefs!!.getString("blocked_notifications", "[]")), notification.packageName)) {
                        count++
                        if (!apps.contains(notification.packageName)) {
                            apps += notification.packageName
                            if (notification.notification.color != 0) {
                                var brighter = Color.rgb(Math.min(255, Color.red(notification.notification.color) + 70), Math.min(255, Color.green(notification.notification.color) + 70), Math.min(255, Color.blue(notification.notification.color) + 70))
                                icons += notification.notification.smallIcon.setTint(brighter)
                            } else {
                                icons += notification.notification.smallIcon
                            }
                        }
                    }

                    if (notification.packageName.toLowerCase().contains("torque")) {
                        LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(Global.REQUEST_STOP))
                    }
                }
            } catch (e: Exception) {
                Log.e(Global.LOG_TAG, e.toString())
            }
            if (cache != count || force) {
                cache = count
                localManager!!.sendBroadcast(Intent(Global.NOTIFICATIONS).putExtra("count", count).putExtra("icons", icons))
            }
            Handler().postDelayed({ sentRecently = false }, 100)
        }
    }
}