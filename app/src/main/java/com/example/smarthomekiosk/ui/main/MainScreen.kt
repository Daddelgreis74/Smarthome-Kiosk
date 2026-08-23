package com.example.smarthomekiosk.ui.main

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.http.SslError
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.smarthomekiosk.AppUpdater
import com.example.smarthomekiosk.KioskDeviceAdminReceiver
import com.example.smarthomekiosk.KioskService
import com.example.smarthomekiosk.KioskSettings
import com.example.smarthomekiosk.MainActivity
import com.example.smarthomekiosk.i18n.AppLanguage
import com.example.smarthomekiosk.i18n.Strings
import com.example.smarthomekiosk.ui.setup.SetupWizardDialog

@Composable
fun MainScreenContent(
    settings: KioskSettings,
    isDimmed: Boolean,
    onWakeUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effectiveLang = Strings.getEffectiveLanguage(settings.appLanguage)

    var currentUrl by remember { mutableStateOf(settings.dashboardUrl) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeAccumulatedDistance by remember { mutableStateOf(0f) }

    var showUpdateDialog by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(settings.httpPassword.isEmpty() || settings.dashboardUrl.isEmpty()) }
    var currentAppVersion by remember {
        mutableStateOf(
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.5"
            } catch (e: Exception) {
                "2.5"
            }
        )
    }

    // Silent check for updates on app start
    LaunchedEffect(Unit) {
        val updateInfo = AppUpdater.checkForUpdates(context)
        if (updateInfo.isUpdateAvailable && !updateInfo.apkDownloadUrl.isNullOrEmpty()) {
            showUpdateDialog = updateInfo
        }
    }

    // Re-load WebView if settings URL changes
    LaunchedEffect(settings.dashboardUrl) {
        if (settings.dashboardUrl.isNotEmpty() && currentUrl != settings.dashboardUrl) {
            currentUrl = settings.dashboardUrl
            webViewRef?.loadUrl(currentUrl)
        }
    }

    // Register receiver to reload WebView via API command
    DisposableEffect(webViewRef) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == KioskService.ACTION_RELOAD_WEBVIEW) {
                    val newUrl = settings.dashboardUrl
                    if (newUrl.isNotEmpty()) {
                        currentUrl = newUrl
                        webViewRef?.loadUrl(newUrl)
                    } else {
                        webViewRef?.reload()
                    }
                }
            }
        }
        val filter = IntentFilter(KioskService.ACTION_RELOAD_WEBVIEW)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (isDimmed) {
                            onWakeUp()
                        }
                    }
                )
            }
    ) {
        // Fullscreen WebView
        if (currentUrl.isNotEmpty()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setupWebView(this, ctx, settings)
                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Neo Kiosk",
                    color = Color.White,
                    fontSize = 24.sp
                )
            }
        }

        // Left edge swipe detector to open settings
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            swipeAccumulatedDistance = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 0) {
                                swipeAccumulatedDistance += dragAmount
                                if (swipeAccumulatedDistance > 100f) {
                                    swipeAccumulatedDistance = 0f
                                    if (settings.pinProtectionEnabled) {
                                        showPasswordPrompt = true
                                    } else {
                                        showSettings = true
                                    }
                                }
                            }
                        }
                    )
                }
        )

        // Dimmed / Fake Blackscreen Overlay
        if (isDimmed && settings.screenOffMethod == "fake") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            onWakeUp()
                        }
                    }
            )
        }

        // Password Prompt Dialog
        if (showPasswordPrompt) {
            var passwordInput by remember { mutableStateOf("") }
            var isError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showPasswordPrompt = false },
                title = { Text(Strings.pinPromptTitle(effectiveLang)) },
                text = {
                    Column {
                        Text(Strings.pinPromptDesc(effectiveLang))
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = {
                                passwordInput = it
                                isError = false
                            },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = {
                                if (passwordInput == settings.settingsPassword) {
                                    showPasswordPrompt = false
                                    showSettings = true
                                } else {
                                    isError = true
                                }
                            }),
                            isError = isError,
                            placeholder = { Text(Strings.pinPlaceholder(effectiveLang)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isError) {
                            Text(Strings.pinWrong(effectiveLang), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        if (passwordInput == settings.settingsPassword) {
                            showPasswordPrompt = false
                            showSettings = true
                        } else {
                            isError = true
                        }
                    }) {
                        Text(Strings.confirm(effectiveLang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordPrompt = false }) {
                        Text(Strings.cancel(effectiveLang))
                    }
                }
            )
        }

        // Settings Dialog
        if (showSettings) {
            SettingsDialog(
                settings = settings,
                onDismiss = { showSettings = false },
                onSave = {
                    showSettings = false
                    currentUrl = settings.dashboardUrl
                    webViewRef?.loadUrl(currentUrl)
                    (context as? MainActivity)?.restartKioskService()
                },
                onReload = {
                    showSettings = false
                    webViewRef?.reload()
                },
                currentVersion = currentAppVersion,
                isCheckingForUpdates = isCheckingForUpdates,
                onCheckForUpdates = {
                    scope.launch {
                        isCheckingForUpdates = true
                        val updateInfo = AppUpdater.checkForUpdates(context)
                        isCheckingForUpdates = false
                        if (updateInfo.isUpdateAvailable && !updateInfo.apkDownloadUrl.isNullOrEmpty()) {
                            showUpdateDialog = updateInfo
                        } else {
                            Toast.makeText(context, Strings.upToDateToast(effectiveLang, currentAppVersion), Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onStartSetupWizard = {
                    showSettings = false
                    showSetupDialog = true
                }
            )
        }

        // 1. Update Available Dialog (Changelog)
        showUpdateDialog?.let { info ->
            AlertDialog(
                onDismissRequest = { showUpdateDialog = null },
                title = { Text(Strings.updateAvailableTitle(effectiveLang, info.latestVersion)) },
                text = {
                    Column {
                        Text(Strings.updateAvailableDesc(effectiveLang), fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Aktuell / Installed: $currentAppVersion")
                        Text("Neu / Latest: ${info.latestVersion}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(Strings.updateChangelog(effectiveLang), fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
                                .padding(8.dp)
                        ) {
                            Text(info.changelog.ifEmpty { "Keine Release-Notes vorhanden." }, fontSize = 13.sp)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val downloadUrl = info.apkDownloadUrl
                            showUpdateDialog = null
                            scope.launch {
                                downloadProgress = 0f
                                val file = AppUpdater.downloadApk(context, downloadUrl) { progress ->
                                    downloadProgress = progress
                                }
                                downloadProgress = null
                                if (file != null) {
                                    AppUpdater.startInstallation(context, file)
                                } else {
                                    Toast.makeText(context, "Fehler beim Herunterladen des Updates!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) {
                        Text(Strings.updateInstallBtn(effectiveLang))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = null }) {
                        Text(Strings.cancel(effectiveLang))
                    }
                }
            )
        }

        // 2. Download Progress Overlay
        downloadProgress?.let { progress ->
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false
                )
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = Strings.updateDownloading(effectiveLang, (progress * 100).toInt()),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 3. Setup Wizard Dialog (First launch / on-demand setup)
        if (showSetupDialog) {
            SetupWizardDialog(
                settings = settings,
                onComplete = {
                    showSetupDialog = false
                    currentUrl = settings.dashboardUrl
                    webViewRef?.loadUrl(currentUrl)
                    (context as? MainActivity)?.restartKioskService()
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: KioskSettings,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    currentVersion: String,
    isCheckingForUpdates: Boolean,
    onCheckForUpdates: () -> Unit,
    onStartSetupWizard: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Temporary settings state
    var appLanguage by remember { mutableStateOf(settings.appLanguage) }
    val effectiveLang = Strings.getEffectiveLanguage(appLanguage)

    var url by remember { mutableStateOf(settings.dashboardUrl) }
    var password by remember { mutableStateOf(settings.settingsPassword) }
    var pinProtectionEnabled by remember { mutableStateOf(settings.pinProtectionEnabled) }
    var kioskEnabled by remember { mutableStateOf(settings.kioskEnabled) }
    var screenOffMethod by remember { mutableStateOf(settings.screenOffMethod) }
    var timeoutMinutes by remember { mutableStateOf(settings.screenTimeoutMinutes.toString()) }
    var motionEnabled by remember { mutableStateOf(settings.motionDetectionEnabled) }
    var sensitivity by remember { mutableStateOf(settings.motionDetectionSensitivity.toFloat()) }
    var motionDebug by remember { mutableStateOf(settings.motionDetectionDebug) }
    var apiPort by remember { mutableStateOf(settings.httpPort.toString()) }
    var apiPassword by remember { mutableStateOf(settings.httpPassword) }
    var mdnsEnabled by remember { mutableStateOf(settings.mdnsEnabled) }
    var ignoreSslErrors by remember { mutableStateOf(settings.ignoreSslErrors) }

    // Device Admin status check
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val adminComponent = ComponentName(context, KioskDeviceAdminReceiver::class.java)
    var isAdminActive by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    // Overlay permission status check
    var hasOverlayPermission by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) AndroidSettings.canDrawOverlays(context) else true)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Text(
                    text = Strings.settingsTitle(effectiveLang),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Settings list scrollable
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // 0. Language Configuration
                    Text(Strings.sectionGeneral(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    Text(Strings.appLanguageLabel(effectiveLang), fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            AppLanguage.AUTO to "Auto",
                            AppLanguage.DE to "Deutsch 🇩🇪",
                            AppLanguage.EN to "English 🇬🇧"
                        ).forEach { (al, label) ->
                            val isSelected = appLanguage == al.code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { appLanguage = al.code }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 1. Dashboard URL Configuration
                    Text(Strings.dashboardUrlLabel(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(Strings.dashboardUrlLabel(effectiveLang)) },
                        placeholder = { Text("http://192.168.178.101:3000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Strings.reloadPageBtn(effectiveLang))
                        }
                        Button(onClick = onReload) {
                            Text(Strings.reloadPageBtn(effectiveLang))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(Strings.settingsPinLabel(effectiveLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Strings.pinProtectionToggle(effectiveLang))
                            Text(Strings.pinProtectionToggleDesc(effectiveLang), fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = pinProtectionEnabled, onCheckedChange = { pinProtectionEnabled = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // App-Update
                    Text("App-Update", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Version: $currentVersion")
                        }
                        Button(
                            onClick = onCheckForUpdates,
                            enabled = !isCheckingForUpdates
                        ) {
                            if (isCheckingForUpdates) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text(Strings.checkForUpdatesBtn(effectiveLang))
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 2. Kiosk and Standby Mode
                    Text(Strings.sectionDisplayKiosk(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Strings.lockTaskToggle(effectiveLang))
                            Text(Strings.lockTaskDesc(effectiveLang), fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = kioskEnabled, onCheckedChange = { kioskEnabled = it })
                    }

                    OutlinedTextField(
                        value = timeoutMinutes,
                        onValueChange = { timeoutMinutes = it },
                        label = { Text(Strings.screenTimeoutLabel(effectiveLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(Strings.screenOffMethodLabel(effectiveLang), fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = screenOffMethod == "fake",
                            onClick = { screenOffMethod = "fake" }
                        )
                        Text(Strings.methodFakeTitle(effectiveLang))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = screenOffMethod == "admin",
                            onClick = { screenOffMethod = "admin" }
                        )
                        Text(Strings.methodNativeTitle(effectiveLang))
                    }

                    if (screenOffMethod == "admin" && !isAdminActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(Strings.deviceAdminSection(effectiveLang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text(Strings.deviceAdminDesc(effectiveLang), fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Erforderlich zum automatischen Ausschalten des Displays.")
                                        }
                                        context.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(Strings.deviceAdminGrantBtn(effectiveLang))
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 3. Motion Detection
                    Text(Strings.sectionMotion(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Strings.motionDetectionToggle(effectiveLang))
                        Switch(checked = motionEnabled, onCheckedChange = { motionEnabled = it })
                    }

                    if (motionEnabled) {
                        Text(Strings.motionSensitivity(effectiveLang, sensitivity.toInt()), fontSize = 14.sp)
                        Slider(
                            value = sensitivity,
                            onValueChange = { sensitivity = it },
                            valueRange = 10f..95f,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(Strings.motionDebugToggle(effectiveLang))
                                Text(Strings.motionDebugDesc(effectiveLang), fontSize = 12.sp, color = Color.Gray)
                            }
                            Switch(checked = motionDebug, onCheckedChange = { motionDebug = it })
                        }

                        if (motionDebug && !hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(Strings.overlaySection(effectiveLang), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                val intent = Intent(
                                                    AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            }
                                        }
                                    ) {
                                        Text(Strings.overlayGrantBtn(effectiveLang))
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 4. REST API & Network
                    Text(Strings.sectionRestApi(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    OutlinedTextField(
                        value = apiPort,
                        onValueChange = { apiPort = it },
                        label = { Text(Strings.httpPortLabel(effectiveLang)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiPassword,
                        onValueChange = { apiPassword = it },
                        label = { Text(Strings.apiPasswordLabel(effectiveLang)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Strings.mdnsToggle(effectiveLang))
                            Text(Strings.mdnsDesc(effectiveLang), fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = mdnsEnabled, onCheckedChange = { mdnsEnabled = it })
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(Strings.ignoreSslToggle(effectiveLang))
                            Text(Strings.ignoreSslToggleDesc(effectiveLang), fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = ignoreSslErrors, onCheckedChange = { ignoreSslErrors = it })
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 5. Setup Wizard
                    Text(Strings.wizardTitle(effectiveLang), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton(
                        onClick = onStartSetupWizard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(Strings.rerunWizardBtn(effectiveLang))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close App Button (left-aligned)
                    Button(
                        onClick = {
                            (context as? Activity)?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(Strings.closeApp(effectiveLang))
                    }
                    
                    // Cancel & Save Buttons (right-aligned)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(Strings.cancel(effectiveLang))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                // Save state to settings
                                settings.appLanguage = appLanguage
                                settings.dashboardUrl = url
                                settings.settingsPassword = password
                                settings.pinProtectionEnabled = pinProtectionEnabled
                                settings.kioskEnabled = kioskEnabled
                                settings.screenOffMethod = screenOffMethod
                                settings.screenTimeoutMinutes = timeoutMinutes.toIntOrNull() ?: 0
                                settings.motionDetectionEnabled = motionEnabled
                                settings.motionDetectionSensitivity = sensitivity.toInt()
                                settings.motionDetectionDebug = motionDebug
                                settings.httpPort = apiPort.toIntOrNull() ?: 8080
                                settings.httpPassword = apiPassword
                                settings.mdnsEnabled = mdnsEnabled
                                settings.ignoreSslErrors = ignoreSslErrors

                                onSave()
                            }
                        ) {
                            Text(Strings.saveAndClose(effectiveLang))
                        }
                    }
                }
            }
        }
    }
}

private fun setupWebView(webView: WebView, context: Context, settings: KioskSettings) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        allowContentAccess = true
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true
        displayZoomControls = false
        builtInZoomControls = false
        setSupportZoom(false)
        cacheMode = WebSettings.LOAD_DEFAULT
    }

    webView.webViewClient = object : WebViewClient() {
        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
            if (settings.ignoreSslErrors) {
                handler?.proceed()
            } else {
                super.onReceivedSslError(view, handler, error)
            }
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            injectSpeechPolyfill(view)
            injectAudioPolyfill(view)
        }
    }

    webView.webChromeClient = object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
            request?.let {
                val resources = it.resources
                if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) ||
                    resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                ) {
                    it.grant(resources)
                } else {
                    it.grant(resources)
                }
            }
        }
    }

    webView.addJavascriptInterface(
        AndroidSpeechInterface(context) { webView },
        "AndroidSpeech"
    )

    webView.addJavascriptInterface(
        AndroidAudioPlayerInterface(context) { webView },
        "AndroidAudioPlayer"
    )
}

private fun injectSpeechPolyfill(view: WebView?) {
    val script = """
        (function() {
            if (!window.SpeechRecognition && !window.webkitSpeechRecognition) {
                class WebviewSpeechRecognition {
                    constructor() {
                        this.continuous = false;
                        this.interimResults = false;
                        this.lang = 'de-DE';
                        this.onstart = null;
                        this.onend = null;
                        this.onerror = null;
                        this.onresult = null;
                        this._isListening = false;
                    }
                    start() {
                        if (this._isListening) return;
                        this._isListening = true;
                        if (window.AndroidSpeech) {
                            window.AndroidSpeech.startListening(this.lang);
                        }
                    }
                    stop() {
                        this._isListening = false;
                        if (window.AndroidSpeech) {
                            window.AndroidSpeech.stopListening();
                        }
                    }
                    abort() {
                        this._isListening = false;
                        if (window.AndroidSpeech) {
                            window.AndroidSpeech.stopListening();
                        }
                    }
                }

                window.SpeechRecognition = WebviewSpeechRecognition;
                window.webkitSpeechRecognition = WebviewSpeechRecognition;
                window._currentSpeechRecognition = null;

                window._onNativeSpeechStart = function() {
                    if (window._activeRecognition && window._activeRecognition.onstart) {
                        window._activeRecognition.onstart();
                    }
                };

                window._onNativeSpeechResult = function(transcript, isFinal) {
                    if (window._activeRecognition && window._activeRecognition.onresult) {
                        const event = {
                            resultIndex: 0,
                            results: [[{ transcript: transcript, confidence: 1.0 }]]
                        };
                        event.results[0].isFinal = isFinal;
                        window._activeRecognition.onresult(event);
                    }
                };

                window._onNativeSpeechError = function(errorMsg) {
                    if (window._activeRecognition && window._activeRecognition.onerror) {
                        window._activeRecognition.onerror({ error: errorMsg });
                    }
                };

                window._onNativeSpeechEnd = function() {
                    if (window._activeRecognition) {
                        window._activeRecognition._isListening = false;
                        if (window._activeRecognition.onend) {
                            window._activeRecognition.onend();
                        }
                    }
                };

                const origStart = WebviewSpeechRecognition.prototype.start;
                WebviewSpeechRecognition.prototype.start = function() {
                    window._activeRecognition = this;
                    origStart.call(this);
                };
            }

            if (!window.speechSynthesis) {
                window.speechSynthesis = {
                    speaking: false,
                    paused: false,
                    pending: false,
                    speak: function(utterance) {
                        if (window.AndroidSpeech && utterance) {
                            this.speaking = true;
                            window._activeUtterance = utterance;
                            window.AndroidSpeech.speak(utterance.text, utterance.lang || 'de-DE', utterance.rate || 1.0, utterance.pitch || 1.0);
                        }
                    },
                    cancel: function() {
                        this.speaking = false;
                        if (window.AndroidSpeech) {
                            window.AndroidSpeech.stopSpeaking();
                        }
                    },
                    pause: function() {},
                    resume: function() {},
                    getVoices: function() {
                        return [
                            { name: 'Android German', lang: 'de-DE', default: true },
                            { name: 'Android English', lang: 'en-US', default: false }
                        ];
                    }
                };

                window.SpeechSynthesisUtterance = function(text) {
                    this.text = text || '';
                    this.lang = 'de-DE';
                    this.rate = 1.0;
                    this.pitch = 1.0;
                    this.volume = 1.0;
                    this.onstart = null;
                    this.onend = null;
                    this.onerror = null;
                };

                window._onNativeTtsStart = function() {
                    window.speechSynthesis.speaking = true;
                    if (window._activeUtterance && window._activeUtterance.onstart) {
                        window._activeUtterance.onstart();
                    }
                };

                window._onNativeTtsEnd = function() {
                    window.speechSynthesis.speaking = false;
                    if (window._activeUtterance && window._activeUtterance.onend) {
                        window._activeUtterance.onend();
                    }
                };

                window._onNativeTtsError = function(err) {
                    window.speechSynthesis.speaking = false;
                    if (window._activeUtterance && window._activeUtterance.onerror) {
                        window._activeUtterance.onerror({ error: err });
                    }
                };
            }
        })();
    """.trimIndent()
    view?.evaluateJavascript(script, null)
}

private fun injectAudioPolyfill(view: WebView?) {
    val script = """
        (function() {
            if (!window._nativeAudioPatched && window.AndroidAudioPlayer) {
                window._nativeAudioPatched = true;
                const OriginalAudio = window.Audio;
                
                class WebviewAudioShim {
                    constructor(src) {
                        this._id = 'audio_' + Math.random().toString(36).substr(2, 9);
                        this._src = src || '';
                        this._base64 = null;
                        this.onended = null;
                        this.onerror = null;
                        
                        if (this._src) {
                            this.src = this._src;
                        }
                    }
                    get src() {
                        return this._src;
                    }
                    set src(val) {
                        this._src = val;
                        if (!val) return;
                        const shim = this;
                        if (!window._activeAudioShims) window._activeAudioShims = {};
                        window._activeAudioShims[this._id] = this;
                        
                        if (val.startsWith('blob:')) {
                            fetch(val)
                                .then(r => r.blob())
                                .then(blob => {
                                    const reader = new FileReader();
                                    reader.onloadend = () => {
                                        const base64data = reader.result.split(',')[1];
                                        shim._base64 = base64data;
                                    };
                                    reader.readAsDataURL(blob);
                                })
                                .catch(e => {
                                    console.error("Error reading blob audio:", e);
                                    if (shim.onerror) shim.onerror(e);
                                });
                        }
                    }
                    play() {
                        const shim = this;
                        if (this._src.startsWith('blob:')) {
                            const playNative = () => {
                                if (shim._base64) {
                                    window.AndroidAudioPlayer.playBase64(shim._id, shim._base64);
                                } else {
                                    setTimeout(playNative, 50);
                                }
                            };
                            playNative();
                            return Promise.resolve();
                        } else {
                            this._nativeAudio = new OriginalAudio(this._src);
                            this._nativeAudio.onended = () => {
                                if (shim.onended) shim.onended();
                            };
                            this._nativeAudio.onerror = (e) => {
                                if (shim.onerror) shim.onerror(e);
                            };
                            return this._nativeAudio.play();
                        }
                    }
                    pause() {
                        if (this._src.startsWith('blob:')) {
                            window.AndroidAudioPlayer.pause(this._id);
                        } else if (this._nativeAudio) {
                            this._nativeAudio.pause();
                        }
                    }
                    get currentTime() {
                        return 0;
                    }
                    set currentTime(val) {}
                }
                
                window.Audio = WebviewAudioShim;
                
                window._onAudioEnded = function(id) {
                    const shim = window._activeAudioShims ? window._activeAudioShims[id] : null;
                    if (shim && shim.onended) {
                        try { shim.onended(); } catch(e) { console.error(e); }
                    }
                    if (window._activeAudioShims) delete window._activeAudioShims[id];
                };
                
                window._onAudioError = function(id) {
                    const shim = window._activeAudioShims ? window._activeAudioShims[id] : null;
                    if (shim && shim.onerror) {
                        try { shim.onerror(); } catch(e) { console.error(e); }
                    }
                    if (window._activeAudioShims) delete window._activeAudioShims[id];
                };
            }
        })();
    """.trimIndent()
    view?.evaluateJavascript(script, null)
}

class AndroidAudioPlayerInterface(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private var mediaPlayer: MediaPlayer? = null
    private var activeId: String? = null

    @JavascriptInterface
    fun playBase64(id: String, base64Data: String) {
        (context as? Activity)?.runOnUiThread {
            try {
                stopPlaying()
                activeId = id
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val tempFile = File.createTempFile("tts_audio_", ".mp3", context.cacheDir)
                tempFile.deleteOnExit()
                FileOutputStream(tempFile).use { it.write(decodedBytes) }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        notifyEnded(id)
                        tempFile.delete()
                    }
                    setOnErrorListener { _, _, _ ->
                        notifyError(id, "MediaPlayer error")
                        tempFile.delete()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("AndroidAudioPlayer", "Error playing base64 audio", e)
                notifyError(id, e.message ?: "Playback error")
            }
        }
    }

    @JavascriptInterface
    fun pause(id: String) {
        (context as? Activity)?.runOnUiThread {
            try {
                if (activeId == id && mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.pause()
                }
            } catch (e: Exception) {
                Log.e("AndroidAudioPlayer", "Error pausing audio", e)
            }
        }
    }

    private fun stopPlaying() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {}
    }

    private fun notifyEnded(id: String) {
        webViewProvider()?.evaluateJavascript("if (window._onAudioEnded) window._onAudioEnded('$id');", null)
    }

    private fun notifyError(id: String, error: String) {
        webViewProvider()?.evaluateJavascript("if (window._onAudioError) window._onAudioError('$id');", null)
    }
}

class AndroidSpeechInterface(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false

    init {
        (context as? Activity)?.runOnUiThread {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    textToSpeech?.language = Locale.GERMAN
                    isTtsReady = true
                    textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            (context as? Activity)?.runOnUiThread {
                                webViewProvider()?.evaluateJavascript("if(window._onNativeTtsStart) window._onNativeTtsStart();", null)
                            }
                        }
                        override fun onDone(utteranceId: String?) {
                            (context as? Activity)?.runOnUiThread {
                                webViewProvider()?.evaluateJavascript("if(window._onNativeTtsEnd) window._onNativeTtsEnd();", null)
                            }
                        }
                        override fun onError(utteranceId: String?) {
                            (context as? Activity)?.runOnUiThread {
                                webViewProvider()?.evaluateJavascript("if(window._onNativeTtsError) window._onNativeTtsError('TTS error');", null)
                            }
                        }
                    })
                }
            }
        }
    }

    @JavascriptInterface
    fun startListening(lang: String) {
        (context as? Activity)?.runOnUiThread {
            try {
                if (speechRecognizer == null) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        webViewProvider()?.evaluateJavascript("if(window._onNativeSpeechStart) window._onNativeSpeechStart();", null)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_AUDIO -> "audio"
                            SpeechRecognizer.ERROR_CLIENT -> "client"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                            SpeechRecognizer.ERROR_NETWORK -> "network"
                            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
                            SpeechRecognizer.ERROR_NO_MATCH -> "no-speech"
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                            SpeechRecognizer.ERROR_SERVER -> "network"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "no-speech"
                            else -> "aborted"
                        }
                        webViewProvider()?.evaluateJavascript("if(window._onNativeSpeechError) window._onNativeSpeechError('$errorMsg');", null)
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].replace("'", "\\'")
                            webViewProvider()?.evaluateJavascript("if(window._onNativeSpeechResult) window._onNativeSpeechResult('$text', true);", null)
                        }
                        webViewProvider()?.evaluateJavascript("if(window._onNativeSpeechEnd) window._onNativeSpeechEnd();", null)
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        if (!matches.isNullOrEmpty()) {
                            val text = matches[0].replace("'", "\\'")
                            webViewProvider()?.evaluateJavascript("if(window._onNativeSpeechResult) window._onNativeSpeechResult('$text', false);", null)
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang.isNotEmpty()) lang else "de-DE")
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("AndroidSpeechInterface", "Error starting speech recognition", e)
            }
        }
    }

    @JavascriptInterface
    fun stopListening() {
        (context as? Activity)?.runOnUiThread {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("AndroidSpeechInterface", "Error stopping speech recognition", e)
            }
        }
    }

    @JavascriptInterface
    fun speak(text: String, lang: String, rate: Float, pitch: Float) {
        (context as? Activity)?.runOnUiThread {
            if (!isTtsReady || textToSpeech == null) return@runOnUiThread
            try {
                val locale = if (lang.startsWith("en", ignoreCase = true)) Locale.US else Locale.GERMAN
                textToSpeech?.language = locale
                textToSpeech?.setSpeechRate(rate)
                textToSpeech?.setPitch(pitch)
                val utteranceId = "utt_" + System.currentTimeMillis()
                textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } catch (e: Exception) {
                Log.e("AndroidSpeechInterface", "Error speaking text", e)
            }
        }
    }

    @JavascriptInterface
    fun stopSpeaking() {
        (context as? Activity)?.runOnUiThread {
            try {
                textToSpeech?.stop()
            } catch (e: Exception) {
                Log.e("AndroidSpeechInterface", "Error stopping TTS", e)
            }
        }
    }
}
