package app.starter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class StartService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private lateinit var notification: Notification
    var list = listOf<TableItem>()
    var lock = true
    var job: Job? = null
    lateinit var lastAppId: String
    private val myBinder = MyLocalBinder()
    override fun onBind(intent: Intent): IBinder {
        return myBinder
    }
    inner class MyLocalBinder : Binder() {
        fun getService() : StartService {
            return this@StartService
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        qqq("onStartCommand executed with startId: $startId "+lock)
        if (intent != null) {
            val action = intent.action
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                else -> qqq("This should never happen. No action in the received intent")
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        lastAppId = packageName
        qqq("The service has been created")
        notification = createNotification()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        qqq("The service has been destroyed")
        //startService()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun startService() {
        if (isServiceStarted) return
        qqq("Starting the foreground service task")
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)
        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire(10*60*1000L /*10 minutes*/)
                }
            }
        job()
    }
    fun job() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                //qqq("JOB!"+lastAppId+" " + list.size)
                delay(5000)
                var lock = false
                list.mapIndexed looper@ { ix, it ->
                    if (
                        !lock &&
                        lastAppId != it.id &&
                        (System.currentTimeMillis() - it.timestamp) / 1000 > it.delay
                    ) {
                       // qqq("RUN TASK" + lastAppId + "::" + it.id + " " + it.cls+ " "+(System.currentTimeMillis() - it.ts) / 1000)
                        if (lastAppId != packageName && lastAppId != "com.android.systemui") notification.contentIntent.send()
                        lock = true
                        val intent = Intent(Intent.ACTION_MAIN, null)
                        intent.addCategory(Intent.CATEGORY_LAUNCHER)
                        lastAppId = it.id
                        list[ix].timestamp = System.currentTimeMillis()
                        intent.component = ComponentName(
                            it.id,
                            it.cls
                        )
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        try { startActivity(intent) }
                        catch (e: Exception) { qqq("ERROR RUN TASK"+e) }
                        return@looper
                    }
                }
            }
        }
    }
    private fun stopService() {
        qqq("Stopping the foreground service")
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            qqq("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            notificationChannelId,
            "App Starter notifications channel",
            NotificationManager.IMPORTANCE_HIGH
        ).let {
            it.description = "App Starter channel"
            it.enableLights(false)
            it.lightColor = Color.RED
            it.enableVibration(false)
            it
        }
        notificationManager.createNotificationChannel(channel)


        val pendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        val builder =
            Notification.Builder(
                this,
                notificationChannelId
            )

        return builder
            .setContentTitle("App Starter")
            .setContentText("App Starter service is running")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Ticker text")
            //.setPriority(Notification.PRIORITY_HIGH) // for under android 26 compatibility
            .build()
    }
}



enum class ServiceState {
    STARTED,
    STOPPED,
}

private const val name = "SPYSERVICE_KEY"
private const val key = "SPYSERVICE_STATE"

fun setServiceState(context: Context, state: ServiceState) {
    val sharedPrefs = getPreferences(context)
    sharedPrefs.edit().let {
        it.putString(key, state.name)
        it.apply()
    }
}

fun getServiceState(context: Context): ServiceState {
    val sharedPrefs = getPreferences(context)
    val value = sharedPrefs.getString(key, ServiceState.STOPPED.name)
    return ServiceState.valueOf(value.toString())
}
internal fun getPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(name, 0)
}

enum class Actions {
    START,
    STOP
}


