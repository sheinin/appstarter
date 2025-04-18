package app.starter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent


class AppService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString()
        if (packageName != null) {
            val intent = Intent(getPackageName() + "APP_ID")
            intent.putExtra("app_id", packageName)
            sendBroadcast(intent)
        }
    }
    override fun onInterrupt() {}
}

