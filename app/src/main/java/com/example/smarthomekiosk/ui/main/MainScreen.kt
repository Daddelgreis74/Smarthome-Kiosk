package com.example.smarthomekiosk.ui.main

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.input.KeyboardActions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    var currentUrl by remember { mutableStateOf(settings.dashboardUrl) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Tap counting for hotspot
    var lastTapTime by remember { mutableStateOf(0L) }
    var tapCount by remember { mutableStateOf(0) }

    // Re-load WebView if settings URL changes
    LaunchedEffect(settings.dashboardUrl) {
        if (settings.dashboardUrl.isNotEmpty() && currentUrl != settings.dashboardUrl) {
            currentUrl = settings.dashboardUrl
            webViewRef?.loadUrl(currentUrl)
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
                    Button(onClick = { showPasswordPrompt = true }) {
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
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false
                            
                            // Enable mixed content (http on https dashboard if needed)
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
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

        // Invisible Top-Right Hotspot for Settings (5 Taps)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(90.dp)
                .background(Color.Transparent)
                .pointerInput(Unit) {
                    detectTapGestures {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 500) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now
                        if (tapCount >= 5) {
                            tapCount = 0
                            showPasswordPrompt = true
                        }
                    }
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
                    // Reload web view if URL changed
                    if (settings.dashboardUrl != currentUrl) {
                        currentUrl = settings.dashboardUrl
                        webViewRef?.loadUrl(currentUrl)
                    }
                    // Restart Kiosk Service background engines
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
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Temporary settings state
    var url by remember { mutableStateOf(settings.dashboardUrl) }
    var password by remember { mutableStateOf(settings.settingsPassword) }
    var kioskEnabled by remember { mutableStateOf(settings.kioskEnabled) }
    var screenOffMethod by remember { mutableStateOf(settings.screenOffMethod) }
    var timeoutMinutes by remember { mutableStateOf(settings.screenTimeoutMinutes.toString()) }
    var motionEnabled by remember { mutableStateOf(settings.motionDetectionEnabled) }
    var sensitivity by remember { mutableStateOf(settings.motionDetectionSensitivity.toFloat()) }
    var motionDebug by remember { mutableStateOf(settings.motionDetectionDebug) }
    var apiPort by remember { mutableStateOf(settings.httpPort.toString()) }
    var apiPassword by remember { mutableStateOf(settings.httpPassword) }
    var mdnsEnabled by remember { mutableStateOf(settings.mdnsEnabled) }

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
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Admin-Einstellungen Passwort") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )

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
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.warningContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Overlay-Berechtigung fehlt!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onWarningContainer)
                                    Text("Für das Debug-Kamerabild muss die Berechtigung 'Über anderen Apps einblenden' erteilt werden.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onWarningContainer)
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
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
                            settings.kioskEnabled = kioskEnabled
                            settings.screenOffMethod = screenOffMethod
                            settings.screenTimeoutMinutes = timeoutMinutes.toIntOrNull() ?: 0
                            settings.motionDetectionEnabled = motionEnabled
                            settings.motionDetectionSensitivity = sensitivity.toInt()
                            settings.motionDetectionDebug = motionDebug
                            settings.httpPort = apiPort.toIntOrNull() ?: 8080
                            settings.httpPassword = apiPassword
                            settings.mdnsEnabled = mdnsEnabled

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

// Key for refreshing dialog state on resume
private val showDialogKey = Any()

