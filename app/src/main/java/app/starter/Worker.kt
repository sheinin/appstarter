package app.starter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*


class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        qqq("RCV "+intent.action)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            qqq("BootReceiver Device booted. Scheduling app start...")
            val workRequest = OneTimeWorkRequestBuilder<AppStartWorker>()
              //  .setInitialDelay(10, TimeUnit.SECONDS) // Optional: Delay before starting
                .build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}


class AppStartWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {
        qqq("AppStartWorker Starting app...")
        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            applicationContext.startActivity(launchIntent)
            Result.success()
        } catch (e: Exception) {
            qqq("AppStartWorker Error starting app"+ e)
            Result.failure()
        }
    }
}