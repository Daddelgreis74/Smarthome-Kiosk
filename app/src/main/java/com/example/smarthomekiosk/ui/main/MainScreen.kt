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
import android.provider.Settings
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
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

@Composable
fun MainScreenContent(
    settings: KioskSettings,
    isDimmed: Boolean,
    onWakeUp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentUrl by remember { mutableStateOf(settings.dashboardUrl) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var swipeAccumulatedDistance by remember { mutableStateOf(0f) }

    var showUpdateDialog by remember { mutableStateOf<AppUpdater.UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var isCheckingForUpdates by remember { mutableStateOf(false) }
    var currentAppVersion by remember {
        mutableStateOf(
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "2.1"
            } catch (e: Exception) {
                "2.1"
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
                // Intercept any touch on screen to reset idle timer in service
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        context.sendBroadcast(Intent(KioskService.ACTION_RESET_IDLE))
                    }
                }
            }
    ) {
        if (currentUrl.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Smarthome Kiosk", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Bitte konfiguriere die Dashboard-URL in den Einstellungen.")
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = {
                        if (settings.pinProtectionEnabled) {
                            showPasswordPrompt = true
                        } else {
                            showSettings = true
                        }
                    }) {
                        Text("Einstellungen öffnen")
                    }
                }
            }
        } else {
            // Fullscreen WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webViewClient = object : WebViewClient() {
                            @Deprecated("Deprecated in Java")
                            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                if (url != null) {
                                    view?.loadUrl(url)
                                }
                                return true
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                if (settings.ignoreSslErrors) {
                                    handler?.proceed()
                                } else {
                                    handler?.cancel()
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                injectKioskPolyfills(view)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                injectKioskPolyfills(view)
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }
                        
                        // Register Speech Recognition Interface
                        addJavascriptInterface(
                            AndroidSpeechRecognitionInterface(ctx) { webViewRef },
                            "AndroidSpeechRecognition"
                        )

                        // Register Speech Synthesis Interface
                        addJavascriptInterface(
                            AndroidSpeechSynthesisInterface(ctx) { webViewRef },
                            "AndroidSpeechSynthesis"
                        )

                        // Register Audio Player Interface (Blob Audio Fix for ElevenLabs)
                        addJavascriptInterface(
                            AndroidAudioPlayerInterface(ctx) { webViewRef },
                            "AndroidAudioPlayer"
                        )

                        this.settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            
                            // Enable mixed content (http on https dashboard if needed)
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        webViewRef = this
                        loadUrl(currentUrl)
                    }
                },
                update = {
                    // Update settings if needed
                }
            )
        }

        // Invisible swipe-from-left-edge hotspot to open settings
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(35.dp)
                .background(Color.Transparent)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            swipeAccumulatedDistance = 0f
                        },
                        onDragEnd = {
                            if (swipeAccumulatedDistance > 250f) { // Swipe of ~2.5 cm to the right
                                if (settings.pinProtectionEnabled) {
                                    showPasswordPrompt = true
                                } else {
                                    showSettings = true
                                }
                            }
                            swipeAccumulatedDistance = 0f
                        },
                        onDragCancel = {
                            swipeAccumulatedDistance = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeAccumulatedDistance += dragAmount
                        }
                    )
                }
        )

        // Dimmed Screen (Fake Standby Overlay)
        if (isDimmed) {
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
                title = { Text("Einstellungen sperren") },
                text = {
                    Column {
                        Text("Bitte gib das Passwort ein:")
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
                            placeholder = { Text("Passwort") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isError) {
                            Text("Falsches Passwort!", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
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
                        Text("Bestätigen")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordPrompt = false }) {
                        Text("Abbrechen")
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
                    // Force reload/load of current URL with new settings
                    currentUrl = settings.dashboardUrl
                    webViewRef?.loadUrl(currentUrl)
                    // Restart Kiosk Service background engines
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
                            Toast.makeText(context, "Kiosk ist auf dem neuesten Stand (v$currentAppVersion)!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // 1. Update Available Dialog (Changelog)
        showUpdateDialog?.let { info ->
            AlertDialog(
                onDismissRequest = { showUpdateDialog = null },
                title = { Text("Update verfügbar (v${info.latestVersion})") },
                text = {
                    Column {
                        Text("Eine neue Version von Neo Kiosk steht bereit.", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Deine Version: $currentAppVersion")
                        Text("Neueste Version: ${info.latestVersion}")
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Changelog / Versionshinweise:", fontWeight = FontWeight.SemiBold)
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
                        Text("Herunterladen & Installieren")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = null }) {
                        Text("Später")
                    }
                }
            )
        }

        // 2. Download Progress Dialog
        downloadProgress?.let { progress ->
            Dialog(
                onDismissRequest = {},
                properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.width(280.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(progress = progress)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Update wird heruntergeladen... ${(progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
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
    onCheckForUpdates: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Temporary settings state
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
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true)
    }

    // Polling permission checks when returning from settings
    LaunchedEffect(showDialogKey) {
        isAdminActive = dpm.isAdminActive(adminComponent)
        hasOverlayPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
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
                    text = "Kiosk-Einstellungen",
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
                    // 1. Dashboard URL Configuration
                    Text("Dashboard-Konfiguration", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Dashboard URL") },
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
                            Text("Seite neu laden")
                            Text("Aktualisiert die geladene Webseite im Kiosk sofort", fontSize = 12.sp, color = Color.Gray)
                        }
                        Button(onClick = onReload) {
                            Text("Neu laden")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Admin-Einstellungen Passwort") },
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
                            Text("PIN-Schutz für Einstellungen")
                            Text("Fordert das Passwort an, wenn das Einstellungsmenü geöffnet wird", fontSize = 12.sp, color = Color.Gray)
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
                            Text("Suche auf GitHub nach neuen Versionen", fontSize = 12.sp, color = Color.Gray)
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
                                Text("Prüfen")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 2. Kiosk and Standby Mode
                    Text("Kiosk & Standby", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Kiosk-Modus aktivieren")
                            Text("Systemleisten ausblenden und Sperrungen einschalten", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = kioskEnabled, onCheckedChange = { kioskEnabled = it })
                    }

                    OutlinedTextField(
                        value = timeoutMinutes,
                        onValueChange = { timeoutMinutes = it },
                        label = { Text("Bildschirm-Timeout (Minuten, 0 = deaktiviert)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Ausschalt-Methode", fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = screenOffMethod == "fake",
                            onClick = { screenOffMethod = "fake" }
                        )
                        Text("Fake-Standby (Schwarzer Screen + Helligkeit 0)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = screenOffMethod == "admin",
                            onClick = { screenOffMethod = "admin" }
                        )
                        Text("Echtes Ausschalten (Benötigt Geräte-Admin)")
                    }

                    if (screenOffMethod == "admin" && !isAdminActive) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Geräte-Admin nicht aktiv!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                                Text("Die App benötigt Admin-Rechte, um das Display hart auszuschalten.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onErrorContainer)
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
                                    Text("Rechte aktivieren")
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 3. Motion Detection
                    Text("Bewegungserkennung (Frontkamera)", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Kamera-Bewegungserkennung aktivieren")
                        Switch(checked = motionEnabled, onCheckedChange = { motionEnabled = it })
                    }

                    if (motionEnabled) {
                        Text("Empfindlichkeit: ${sensitivity.toInt()}%", fontSize = 14.sp)
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
                                Text("Kamera-Debug-Vorschau anzeigen")
                                Text("Zeigt ein kleines Vorschaubild zur Kalibrierung", fontSize = 12.sp, color = Color.Gray)
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
                                    Text("Overlay-Berechtigung fehlt!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text("Für das Debug-Kamerabild muss die Berechtigung 'Über anderen Apps einblenden' erteilt werden.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                val intent = Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            }
                                        }
                                    ) {
                                        Text("Overlay-Rechte gewähren")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    // 4. REST API & Network
                    Text("Lokale REST API & Netzwerk", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
                    
                    OutlinedTextField(
                        value = apiPort,
                        onValueChange = { apiPort = it },
                        label = { Text("HTTP REST Port") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiPassword,
                        onValueChange = { apiPassword = it },
                        label = { Text("API Authentifizierungs-Passwort (Header)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("mDNS Service Discovery")
                            Text("Macht das Tablet im Netzwerk automatisch findbar", fontSize = 12.sp, color = Color.Gray)
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
                            Text("Selbstsignierte SSL-Zertifikate ignorieren")
                            Text("Erlaubt das Laden von lokalen HTTPS-Seiten ohne gültiges Zertifikat", fontSize = 12.sp, color = Color.Gray)
                        }
                        Switch(checked = ignoreSslErrors, onCheckedChange = { ignoreSslErrors = it })
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
                        Text("App schließen")
                    }
                    
                    // Cancel & Save Buttons (right-aligned)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Abbrechen")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                // Save state to settings
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
                            Text("Speichern & Schließen")
                        }
                    }
                }
            }
        }
    }
}

// Key for refreshing dialog state on resume
private val showDialogKey = Any()

class AndroidSpeechRecognitionInterface(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var activeId: String? = null

    @JavascriptInterface
    fun startListening(id: String, lang: String) {
        (context as? Activity)?.runOnUiThread {
            try {
                if (speechRecognizer != null) {
                    speechRecognizer?.destroy()
                }
                activeId = id
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {}
                        override fun onBeginningOfSpeech() {
                            sendToJs("window._onSpeechRecognitionStart('$id')")
                        }
                        override fun onRmsChanged(rmsdB: Float) {}
                        override fun onBufferReceived(buffer: ByteArray?) {}
                        override fun onEndOfSpeech() {}
                        
                        override fun onError(error: Int) {
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_AUDIO -> "audio"
                                SpeechRecognizer.ERROR_CLIENT -> "client"
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                                SpeechRecognizer.ERROR_NETWORK -> "network"
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network-timeout"
                                SpeechRecognizer.ERROR_NO_MATCH -> "no-match"
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "busy"
                                SpeechRecognizer.ERROR_SERVER -> "server"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech-timeout"
                                else -> "unknown"
                            }
                            sendToJs("window._onSpeechRecognitionError('$id', '$errorMsg')")
                            sendToJs("window._onSpeechRecognitionEnd('$id')")
                        }
                        
                        override fun onResults(results: Bundle?) {
                            val speechResults = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            if (speechResults != null && speechResults.isNotEmpty()) {
                                val text = speechResults[0].replace("'", "\\'")
                                sendToJs("window._onSpeechRecognitionResult('$id', '$text')")
                            }
                            sendToJs("window._onSpeechRecognitionEnd('$id')")
                        }
                        
                        override fun onPartialResults(partialResults: Bundle?) {}
                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }
                
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e("SpeechInterface", "Error starting speech recognition", e)
                sendToJs("window._onSpeechRecognitionError('$id', 'unknown')")
                sendToJs("window._onSpeechRecognitionEnd('$id')")
            }
        }
    }

    @JavascriptInterface
    fun stopListening(id: String) {
        (context as? Activity)?.runOnUiThread {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("SpeechInterface", "Error stopping speech recognition", e)
            }
        }
    }

    private fun sendToJs(script: String) {
        val webView = webViewProvider()
        webView?.post {
            webView.evaluateJavascript(script, null)
        }
    }
}

class AndroidSpeechSynthesisInterface(
    private val context: Context,
    private val webViewProvider: () -> WebView?
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                tts?.language = Locale.GERMAN
            }
        }
    }

    @JavascriptInterface
    fun speak(id: String, text: String, lang: String) {
        (context as? Activity)?.runOnUiThread {
            if (!isInitialized || tts == null) {
                sendToJs("window._onSpeechError('$id')")
                return@runOnUiThread
            }
            
            tts?.language = Locale.forLanguageTag(lang)
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    sendToJs("window._onSpeechStart('$id')")
                }

                override fun onDone(utteranceId: String?) {
                    sendToJs("window._onSpeechEnd('$id')")
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    sendToJs("window._onSpeechError('$id')")
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id)
            }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
        }
    }

    @JavascriptInterface
    fun cancel() {
        (context as? Activity)?.runOnUiThread {
            if (isInitialized) {
                tts?.stop()
            }
        }
    }

    private fun sendToJs(script: String) {
        val webView = webViewProvider()
        webView?.post {
            webView.evaluateJavascript(script, null)
        }
    }
}

fun injectKioskPolyfills(view: WebView?) {
    val script = """
        (function() {
            // Speech Recognition Polyfill
            if (!('webkitSpeechRecognition' in window) && !('SpeechRecognition' in window)) {
                class SpeechRecognitionShim {
                    constructor() {
                        this.continuous = false;
                        this.interimResults = false;
                        this.lang = 'en-US';
                        this.onstart = null;
                        this.onend = null;
                        this.onresult = null;
                        this.onerror = null;
                        this._id = Math.random().toString(36).substring(2);
                        window._activeSpeechRecognitions = window._activeSpeechRecognitions || {};
                        window._activeSpeechRecognitions[this._id] = this;
                    }
                    start() {
                        if (window.AndroidSpeechRecognition) {
                            window.AndroidSpeechRecognition.startListening(this._id, this.lang);
                        } else {
                            console.error("AndroidSpeechRecognition native interface not found.");
                            if (this.onerror) this.onerror({ error: 'service-not-allowed' });
                        }
                    }
                    stop() {
                        if (window.AndroidSpeechRecognition) {
                            window.AndroidSpeechRecognition.stopListening(this._id);
                        }
                    }
                    abort() {
                        this.stop();
                    }
                }
                window.SpeechRecognition = SpeechRecognitionShim;
                window.webkitSpeechRecognition = SpeechRecognitionShim;
                window._onSpeechRecognitionStart = function(id) {
                    const instance = window._activeSpeechRecognitions[id];
                    if (instance && instance.onstart) {
                        try { instance.onstart(); } catch(e) { console.error(e); }
                    }
                };
                window._onSpeechRecognitionEnd = function(id) {
                    const instance = window._activeSpeechRecognitions[id];
                    if (instance && instance.onend) {
                        try { instance.onend(); } catch(e) { console.error(e); }
                    }
                };
                window._onSpeechRecognitionResult = function(id, text) {
                    const instance = window._activeSpeechRecognitions[id];
                    if (instance && instance.onresult) {
                        const event = {
                            results: [
                                [
                                    { transcript: text }
                                ]
                            ]
                        };
                        try { instance.onresult(event); } catch(e) { console.error(e); }
                    }
                };
                window._onSpeechRecognitionError = function(id, errorMsg) {
                    const instance = window._activeSpeechRecognitions[id];
                    if (instance && instance.onerror) {
                        try { instance.onerror({ error: errorMsg }); } catch(e) { console.error(e); }
                    }
                };
            }

            // Speech Synthesis Polyfill/Override
            if (window.AndroidSpeechSynthesis) {
                const nativeSpeak = function(utterance) {
                    const id = Math.random().toString(36).substring(2);
                    window._activeUtterances = window._activeUtterances || {};
                    window._activeUtterances[id] = utterance;
                    
                    const text = utterance.text;
                    const lang = utterance.lang || 'de-DE';
                    
                    window.AndroidSpeechSynthesis.speak(id, text, lang);
                };
                
                const nativeCancel = function() {
                    window.AndroidSpeechSynthesis.cancel();
                };
                
                if (window.speechSynthesis) {
                    window.speechSynthesis.speak = nativeSpeak;
                    window.speechSynthesis.cancel = nativeCancel;
                } else {
                    window.speechSynthesis = {
                        speak: nativeSpeak,
                        cancel: nativeCancel,
                        getVoices: function() {
                            return [
                                { name: 'System Deutsch', lang: 'de-DE', default: true, localService: true },
                                { name: 'System English', lang: 'en-US', default: false, localService: true }
                            ];
                        }
                    };
                }
                
                window._onSpeechStart = function(id) {
                    const u = window._activeUtterances ? window._activeUtterances[id] : null;
                    if (u && u.onstart) {
                        try { u.onstart(); } catch(e) { console.error(e); }
                    }
                };
                
                window._onSpeechEnd = function(id) {
                    const u = window._activeUtterances ? window._activeUtterances[id] : null;
                    if (u && u.onend) {
                        try { u.onend(); } catch(e) { console.error(e); }
                    }
                    if (window._activeUtterances) delete window._activeUtterances[id];
                };
                
                window._onSpeechError = function(id) {
                    const u = window._activeUtterances ? window._activeUtterances[id] : null;
                    if (u && u.onerror) {
                        try { u.onerror(); } catch(e) { console.error(e); }
                    }
                    if (window._activeUtterances) delete window._activeUtterances[id];
                };
            }

            // HTML5 Audio Blob Override (Fix for ElevenLabs playbacks in Android WebView)
            if (window.AndroidAudioPlayer) {
                const OriginalAudio = window.Audio;
                class WebviewAudioShim {
                    constructor(src) {
                        this._src = src || '';
                        this.onended = null;
                        this.onerror = null;
                        this._id = Math.random().toString(36).substring(2);
                        
                        window._activeAudioShims = window._activeAudioShims || {};
                        window._activeAudioShims[this._id] = this;
                        
                        if (src) {
                            this.src = src;
                        }
                    }
                    get src() {
                        return this._src;
                    }
                    set src(val) {
                        this._src = val;
                        if (val.startsWith('blob:')) {
                            const shim = this;
                            fetch(val)
                                .then(r => r.blob())
                                .then(blob => {
                                    const reader = new FileReader();
                                    reader.onloadend = function() {
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
                val audioBytes = Base64.decode(base64Data, Base64.DEFAULT)
                
                // Save to a temporary file
                val tempFile = File.createTempFile("kiosk_audio_", ".mp3", context.cacheDir)
                tempFile.deleteOnExit()
                
                FileOutputStream(tempFile).use { fos ->
                    fos.write(audioBytes)
                }
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnCompletionListener {
                        sendToJs("window._onAudioEnded('$id')")
                        tempFile.delete()
                        stopPlaying()
                    }
                    setOnErrorListener { _, _, _ ->
                        sendToJs("window._onAudioError('$id')")
                        tempFile.delete()
                        stopPlaying()
                        true
                    }
                    prepare()
                    start()
                }
            } catch (e: Exception) {
                Log.e("AudioInterface", "Error playing base64 audio", e)
                sendToJs("window._onAudioError('$id')")
            }
        }
    }

    @JavascriptInterface
    fun pause(id: String) {
        (context as? Activity)?.runOnUiThread {
            if (activeId == id) {
                stopPlaying()
            }
        }
    }

    private fun stopPlaying() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            // Ignore
        }
        mediaPlayer = null
        activeId = null
    }

    private fun sendToJs(script: String) {
        val webView = webViewProvider()
        webView?.post {
            webView.evaluateJavascript(script, null)
        }
    }
}

