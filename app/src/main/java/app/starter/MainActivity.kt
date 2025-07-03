package app.starter

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.ACCESSIBILITY_SERVICE
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
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import app.starter.MainActivity
import app.starter.ui.theme.AppTheme
import com.google.accompanist.drawablepainter.DrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.concurrent.TimeUnit
import kotlin.contracts.contract
import kotlin.math.absoluteValue
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt


var myConnection: ServiceConnection? = null
var myService: StartService? = null

class MainActivity : ComponentActivity() {
    private var isBound = false
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
        }
        if (myConnection == null && vm.list.isEmpty() == true) requestPermissions()
        bind()
        if (vm.list.isEmpty() == true) loading()
        else content()
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
                myService?.list = vm.list.filter { it.delay > 0 }
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
            var showTable by remember { mutableStateOf(false) }
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
                        fun callback(delay: Int) {
                            qqq("cb "+delay)
                            val pref = getPreferences(this@MainActivity)
                            val index = vm.list.indexOfFirst { it.id == showSheet?.id }
                            if (index != -1) {
                                vm.list[index] = vm.list[index].copy(
                                    delay = delay, timestamp = System.currentTimeMillis()
                                )
                            }
                            if (delay > 0) {
                                fun isAccessibilityServiceEnabled(
                                    context: Context,
                                    service: Class<out AccessibilityService?>
                                ): Boolean {
                                    val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
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
                                if (!isAccessibilityServiceEnabled(this@MainActivity, AppService::class.java)) {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    startActivity(intent)
                                }
                            }
                            if (delay == 0) pref.edit(commit = true) { remove(showSheet?.id) }
                            else pref.edit(commit = true) { putInt(showSheet?.id, delay) }
                            myService?.list = vm.list.filter { it.delay > 0 }
                            vm.showSheet.value = null
                        }
                        LaunchedEffect(Unit) {
                            delay(1)
                            showTable = true
                        }
                        if (showTable) SimpleTable()
                        BottomSheetWithSliderAndClose(
                            item = showSheet,
                            onDismissRequest = { vm.showSheet.value = null },
                            callback = ::callback
                        )
                    }
                }
            }
        }
    }
    @SuppressLint("QueryPermissionsNeeded")
    private fun loading() {
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
            if (vm.list.isEmpty() == true) {
                val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
                for (packageInfo in packages) {
                    if (packageInfo.packageName != packageName) {
                        val cls = packageManager.getLaunchIntentForPackage(packageInfo.packageName)?.component?.className
                        if (cls != null) {
                            var icon: Drawable? = null
                            try {
                                icon = packageManager.getApplicationIcon(packageInfo.processName)
                            } catch (_: Exception) { }
                            vm.list.add(
                                TableItem(
                                    delay = getPreferences(this).getInt(packageInfo.processName, 0),
                                    icon = icon,
                                    id = packageInfo.processName,
                                    cls = cls,
                                    title = packageInfo.loadLabel(packageManager).toString(),
                                    timestamp = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }
            myService?.list = vm.list.filter { it.delay != 0 }
            content()
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
fun SimpleTable() {
    var list = remember(vm.list) { vm.list }
    LazyColumn {
        items(list.sortedBy { it.title }, key = { it.id }) { rowData ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                TableRow(rowData)
            }
        }
    }
}

@Composable
fun TableRow(item: TableItem) {
    @SuppressLint("DefaultLocale")
    fun formatMinutesToHHMMSS(totalSeconds: Int): String {
        if (totalSeconds == Int.MAX_VALUE) return "--"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val timeParts = mutableListOf<String>()
        if (hours > 0) timeParts.add(hours.toString())
        timeParts.add(minutes.toString())
        timeParts.add(seconds.let { if (it < 10) "0$it" else it.toString() })
        return timeParts.joinToString(":")
    }
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .clickable { vm.showSheet.value = item }
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
        qqq("DEL DISP "+item.delay)
        if (item.delay != 0)
            Button(
                onClick = { expanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (item.delay <= 0) Color.Gray else Color.Green,
                ),
                content = {
                    Text(formatMinutesToHHMMSS(item.delay.absoluteValue))
                }
            )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetWithSliderAndClose(
    item: TableItem?,
    onDismissRequest: () -> Unit,
    callback: (delay: Int) -> Unit
) {
    @SuppressLint("DefaultLocale")
    fun formatSecondsToHHMMSS(s: Float): String {
        val seconds = s.toLong()
        val hours = TimeUnit.SECONDS.toHours(seconds)
        val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
        val remainingSeconds = seconds % 60
        return when {
            hours > 0 -> {
                if (minutes > 0) String.format("%dhr %dmin", hours, minutes)
                else String.format("%dhr %dmin", hours, minutes)
            }
            minutes > 0 -> String.format("%dmin %2dsec", minutes, remainingSeconds)
            else -> String.format("%2dsec", remainingSeconds)
        }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val showDialog = remember { mutableStateOf(false) }
    if (item != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (item.icon != null)
                        Image(
                            bitmap = item.icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .width(56.dp)
                                .height(56.dp)
                        )
                    else
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            modifier = Modifier
                                .width(56.dp)
                                .height(56.dp)
                        )
                    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(item.title, fontSize = 16.sp)
                        Text(item.id, fontSize = 14.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                var currentSliderValue by remember { mutableFloatStateOf(cbrt(item.delay.toFloat().absoluteValue / 24 / 3600)) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Settings, contentDescription = null, modifier = Modifier.size(12.dp))
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(36.dp).rotate(270f))
                    }
                    Text(
                        formatSecondsToHHMMSS(24 * 3600 * currentSliderValue.pow(3)))
                }
                Slider(
                    value = currentSliderValue,
                    onValueChange = { newValue ->
                        currentSliderValue = newValue
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                LaunchedEffect(Unit) {}

                var ctx = LocalContext.current
                if (showDialog.value) Dialog(onDismissRequest = {}) { }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { callback((currentSliderValue.pow(3) * 3600 * 24).toInt().unaryMinus()) },
                        colors = ButtonColors(
                            containerColor = Color.White.copy(alpha = .5f),
                            contentColor = ButtonDefaults.buttonColors().contentColor,
                            disabledContainerColor = ButtonDefaults.buttonColors().disabledContainerColor,
                            disabledContentColor = ButtonDefaults.buttonColors().disabledContentColor
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    ) {
                        Text("OK")
                    }
                    Button(
                        onClick = {
                            fun isAccessibilityServiceEnabled(
                                context: Context,
                                service: Class<out AccessibilityService?>
                            ): Boolean {
                                val am = context.getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
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
                            if (!isAccessibilityServiceEnabled(ctx, AppService::class.java)) { showDialog.value = true }
                            if (!isAccessibilityServiceEnabled(ctx, AppService::class.java)) {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                ctx.startActivity(intent)
                            }
                            callback((currentSliderValue.pow(3) * 3600 * 24).toInt()) },
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}


data class TableItem(
    val icon: Drawable?,
    val id: String,
    val cls: String,
    val title: String,
    var delay: Int,
    var timestamp: Long,
) : Serializable

fun qqq(text: String) {
    Log.d("qqq", text)
}

class VM: ViewModel() {
    val list = mutableStateListOf<TableItem>()
    var showSheet = MutableStateFlow<TableItem?>(null)
}
val vm = VM()
