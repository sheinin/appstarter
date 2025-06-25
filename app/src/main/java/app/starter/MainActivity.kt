package app.starter

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.MutableLiveData
import app.starter.ui.theme.AppTheme
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.concurrent.TimeUnit
import kotlin.math.pow


val list = mutableListOf<TableItem>()
var myConnection: ServiceConnection? = null
var myService: StartService? = null

class MainActivity : ComponentActivity() {
    private var isBound = false
    @SuppressLint("QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fun requestPermissions() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPermission) {
                    val requestPermissionLauncher = registerForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) { isGranted: Boolean -> }
                  //  requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            fun isAccessibilityServiceEnabled(
                context: Context,
                service: Class<out AccessibilityService?>
            ): Boolean {
                val am = context.getSystemService(ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
                val enabledServices: List<AccessibilityServiceInfo> =
                    am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                for (enabledService in enabledServices) {
                    val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
                    if (enabledServiceInfo.packageName == context.packageName &&
                        enabledServiceInfo.name == service.name
                    ) {
                        return true
                    }
                }
                return false
            }
            //if (!isAccessibilityServiceEnabled(this, AppService::class.java)) {
            //    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            //    startActivity(intent)
           // }
        }
        if (myConnection == null && list.isEmpty()) requestPermissions()
        bind()
        if (list.isEmpty()) {
            setContent {
                AppTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Column(
                            Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                painter = DrawablePainter(packageManager.getApplicationIcon(applicationInfo)),
                                contentDescription = null
                            )
                            Text("Loading...", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
                        }
                    }
                }
            }
            sleep(1000) {
                if (list.isEmpty()) {
                    val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                    for (packageInfo in packages) {
                        if (packageInfo.packageName != packageName) {
                            val cls = packageManager.getLaunchIntentForPackage(packageInfo.packageName)?.component?.className
                            if (cls != null) {
                                var icon: Drawable? = null
                                try {
                                    icon = packageManager.getApplicationIcon(packageInfo.processName)
                                } catch (_: Exception) {
                                }
                                val pref = getPreferences(this)
                                list.add(
                                    TableItem(
                                        delay = pref.getInt(packageInfo.processName, Int.MAX_VALUE),
                                        icon = icon,
                                        id = packageInfo.processName,
                                        cls = cls,
                                        title = packageInfo.loadLabel(packageManager).toString(),
                                        ts = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
                myService?.list = list.filter { it.delay != Int.MAX_VALUE }
                content()
            }
        } else content()
    }
    private fun bind() {
        if (myConnection != null) return
        myConnection = object : ServiceConnection {
            override fun onBindingDied(name: ComponentName?) {
                super.onBindingDied(name)
                isBound = false
            }
            override fun onServiceConnected(
                className: ComponentName,
                service: IBinder
            ) {
                val binder = service as StartService.MyLocalBinder
                myService = binder.getService()
                myService?.list = list.filter { it.delay != Int.MAX_VALUE }
                isBound = true
            }
            override fun onServiceDisconnected(name: ComponentName) {
                isBound = false
            }
        }
        applicationContext.bindService(Intent(this, StartService::class.java), myConnection!!, BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val a = intent.extras!!.getString("app_id")!!
                    myService?.lastAppId = a
                }
            }, IntentFilter(packageName + "APP_ID"), RECEIVER_EXPORTED)
        else
            registerReceiver(object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent) {
                    val a = intent.extras!!.getString("app_id")!!
                    myService?.lastAppId = a
                }
            }, IntentFilter(packageName + "APP_ID"))
        actionOnService(Actions.START)
    }
    private fun content() {
        setContent {
            val showSheet by vm.showSheet.collectAsState()
            var switch by remember { mutableStateOf(true) } // For preview, initially show { mutableStateOf(vm.showSheet.value) } // For preview, initially show
            var sliderValue by remember { mutableFloatStateOf(0.5f) }
            AppTheme {
                Scaffold(
                    floatingActionButton = {
                        val coroutineScope = rememberCoroutineScope()
                        FloatingActionButton(onClick = { coroutineScope.launch {  } }) {
                            Icon(Icons.Default.Info, contentDescription = "Show Bottom Sheet")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Column(Modifier.padding(innerPadding)) {
                        Row {
                            Text(
                                text = "App Starter",
                                style = typography.headlineMedium,
                                modifier = Modifier
                                    .padding(16.dp, 8.dp)
                                    .weight(1f)
                            )
                            Switch(
                                checked = switch,
                                onCheckedChange = {
                                    if (myService?.job?.isActive == true) myService?.job?.cancel()
                                    else myService?.job()
                                    switch = myService?.job?.isActive == true
                                },
                                modifier = Modifier.padding(16.dp, 8.dp)
                            )
                        }
                        fun callback(delay: Int, item: TableItem) {
                            val pref = getPreferences(this@MainActivity)
                            item.delay = delay
                            item.ts = System.currentTimeMillis()
                            if (delay == Int.MAX_VALUE) pref.edit(commit = true) { remove(item.id) }
                            else pref.edit(commit = true) { putInt(item.id, delay) }
                            myService?.list = list.filter { it.delay != Int.MAX_VALUE }
                        }
                        MaterialTable(list, ::callback)
                        BottomSheetWithSliderAndClose(
                            showSheet = showSheet,
                            onDismissRequest = { vm.showSheet.value = null },
                        )
                    }
                }
            }
        }
    }
    fun sleep(delay: Long, callback: (() -> Unit)) { Handler(Looper.getMainLooper()).postDelayed({ callback.invoke() }, delay) }

    private fun actionOnService(@Suppress("SameParameterValue") action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, StartService::class.java).also {
            it.action = action.name
            startForegroundService(it)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        if (myConnection != null && isBound) {
            applicationContext.unbindService(myConnection!!)
        }
    }
}

@Composable
fun MaterialTable(
    items: List<TableItem>,
    callback: (delay: Int, item: TableItem) -> Unit
    ) {
    @Composable
    fun Table(items: List<TableItem>, callback: (delay: Int, item: TableItem) -> Unit) {
        @Composable
        fun TableRow(item: TableItem, callback: (delay: Int, item: TableItem) -> Unit) {
            @SuppressLint("DefaultLocale")
            fun formatMinutesToHHMMSS(totalSeconds: Int): String {
                if (totalSeconds == Int.MAX_VALUE) return "--"
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val seconds = totalSeconds % 60
                val timeParts = mutableListOf<String>()
                timeParts.add(minutes.toString())
                if (hours > 0) timeParts.add(hours.toString())
                timeParts.add(seconds.let { if (it < 10) "0$it" else it.toString() })
                return timeParts.joinToString(":")
            }
            var expanded by remember { mutableStateOf(false) }
            var delay by remember { mutableIntStateOf(item.delay) }
            Row(
                modifier = Modifier
                    .clickable {
                        qqq("click! "+item)
                        vm.showSheet.value = item }
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.icon != null)
                    Image(
                        bitmap = item.icon.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .width(42.dp)
                            .height(42.dp)
                    )
                else
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier
                            .width(42.dp)
                            .height(42.dp)
                    )
                Spacer(modifier = Modifier.width(8.dp))
                Text(item.title, modifier = Modifier.weight(1f), overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.width(8.dp))
                Box {
                    Button(
                        onClick = { expanded = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (delay == Int.MAX_VALUE) Color.Gray else Color.Green,
                        ),
                        content = {
                            Text(formatMinutesToHHMMSS(delay))
                        }
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf(
                            Int.MAX_VALUE to "--",
                            5 to "5 sec",
                            10 to "10 sec",
                            30 to "30 sec",
                            60 to "1 min",
                            300 to "5 min",
                            600 to "10 min"
                        ).map {
                            DropdownMenuItem(
                                onClick = {
                                    expanded = false
                                    delay = it.first
                                    callback(delay, item)
                                },
                                text = { Text(it.second) }
                            )
                        }
                    }
                }
            }
        }
        Column {
            items.sortedBy { it.title }.forEach { item ->
                TableRow(item, callback)
            }
        }
    }
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(8.dp)
        .verticalScroll(rememberScrollState())) {
        Table(items, callback)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetWithSliderAndClose(
    showSheet: TableItem?,
    onDismissRequest: () -> Unit
) {

    fun formatSecondsToHHMMSS(s: Float): String {
        val seconds = s.toLong()
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val remainingSeconds = seconds % 60
        qqq("formatSecondsToHHMMSS"+s+ " "+seconds)
        return when {
            hours > 0 -> String.format("%dhr %dmin", hours, minutes)
            minutes > 0 -> String.format("%dmin %2dsec", minutes, remainingSeconds)
            else -> String.format("%2dsec", remainingSeconds)
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true // You can set this to false if you want a partially expanded state
    )
    val scope = rememberCoroutineScope()

    if (showSheet != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close Bottom Sheet")
                }
                Spacer(modifier = Modifier.height(16.dp))
                var currentSliderValue by remember { mutableFloatStateOf(0f) }
                Text("Adjust Value: "+

                    //if (currentSliderValue < .33f)
                        formatSecondsToHHMMSS(24 * 3600 * currentSliderValue.pow(3))
                    //else if (currentSliderValue < .66f)
                     //   formatSecondsToHHMMSS(24 * 3600 * currentSliderValue / 2)
                    //else
                      //  formatSecondsToHHMMSS(24 * 3600 * currentSliderValue)

                )
                Slider(
                    value = currentSliderValue,
                    onValueChange = { newValue ->
                        currentSliderValue = newValue
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onDismissRequest()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Apply")
                }
            }
        }
    }
}


data class TableItem(
    var delay: Int,
    var ts: Long,
    val icon: Drawable?,
    val id: String,
    val cls: String,
    val title: String
) : Serializable

fun qqq(text: String) {
    Log.d("qqq", text)
}

class VM: androidx.lifecycle.ViewModel() {
    var showSheet = MutableStateFlow<TableItem?>(null)
}
val vm = VM()
