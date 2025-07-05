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
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontStyle
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
    @OptIn(ExperimentalMaterial3Api::class)
    private fun content() {
        setContent {
            val hasApps = vm.list.find { it.delay > 0 } != null
            val showSheet by vm.showSheet.collectAsState()
            var switch by remember { mutableStateOf(hasApps) }
            var showInfo by remember { mutableStateOf(false) }
            var showTable by remember { mutableStateOf(false) }
            AppTheme {
                Scaffold(
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showInfo = true  }) {
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
                                enabled = hasApps,
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
                            val pref = getPreferences(this@MainActivity)
                            val index = vm.list.indexOfFirst { it.id == showSheet?.id }
                            if (index != -1) {
                                vm.list[index] = vm.list[index].copy(
                                    delay = delay, timestamp = System.currentTimeMillis()
                                )
                            }
                            /*if (delay > 0) {
                                if (!isAccessibilityServiceEnabled(this@MainActivity, AppService::class.java)) {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    startActivity(intent)
                                }
                            }

                             */
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
                        if (showInfo)
                            BasicAlertDialog(
                                onDismissRequest = { showInfo = false }
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .wrapContentWidth(),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("App Starter", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("This app is a lets users restart any app on device with a specified period.", fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = {
                                                showInfo = false
                                            }
                                        ) {
                                            Text("OK")
                                        }
                                    }
                                }
                            }
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
            myService?.list = vm.list.filter { it.delay > 0 }
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
    val ctx = LocalContext.current
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
        if (item.delay != 0)
            Button(
                onClick = {
                    if (isAccessibilityServiceEnabled(ctx, AppService::class.java) || item.delay > 0) {
                        val pref = getPreferences(ctx)
                        pref.edit(commit = true) { putInt(item.id, item.delay * -1) }
                        val index = vm.list.indexOfFirst { it.id == item.id }
                        if (index != -1)
                            vm.list[index] = vm.list[index].copy(
                                delay = item.delay * -1, timestamp = System.currentTimeMillis()
                            )
                    }
                },
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
    val ctx = LocalContext.current
    if (item != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        var hasNotificationPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    ctx,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            )
        }
        val permissionRequest =
            rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { result ->
                hasNotificationPermission = result
            }
        LaunchedEffect(Unit) { permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS) }
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
                    Text(formatMinutesToHHMMSS((24 * 3600 * currentSliderValue.pow(3)).toInt()))
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
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { callback((currentSliderValue.pow(3) * 3600 * 24).toInt().unaryMinus()) },
                        colors = ButtonColors(
                            containerColor = Color.White.copy(alpha = .3f),
                            contentColor = Color.Black,
                            disabledContainerColor = ButtonDefaults.buttonColors().disabledContainerColor,
                            disabledContentColor = ButtonDefaults.buttonColors().disabledContentColor
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    ) {
                        Text("OK")
                    }
                    Button(
                        onClick = {
                            if (!isAccessibilityServiceEnabled(ctx, AppService::class.java)) { showDialog.value = true }
                            else callback((currentSliderValue.pow(3) * 3600 * 24).toInt())
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                    ) {
                        Text("Apply")
                    }
                }
                if (showDialog.value)
                    BasicAlertDialog(
                        onDismissRequest = { showDialog.value = false }
                    ) {
                        Surface(
                            modifier = Modifier
                                .wrapContentWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("App Starter", fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("This feature requires Accessibility permission", fontSize = 14.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        showDialog.value = false
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        ctx.startActivity(intent)
                                    }
                                ) {
                                    Text("OK")
                                }
                            }
                        }
                    }
            }
        }
    }
}

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

fun formatMinutesToHHMMSS(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val timeParts = mutableListOf<String>()
    if (hours > 0) timeParts.add(hours.toString())
    timeParts.add(minutes.let { if (it < 10) "0$it" else it.toString() })
    timeParts.add(seconds.let { if (it < 10) "0$it" else it.toString() })
    return timeParts.joinToString(":")
}

data class TableItem(
    val icon: Drawable?,
    val id: String,
    val cls: String,
    val title: String,
    var delay: Int,
    var timestamp: Long,
) : Serializable


class VM: ViewModel() {
    val list = mutableStateListOf<TableItem>()
    var showSheet = MutableStateFlow<TableItem?>(null)
}
val vm = VM()
