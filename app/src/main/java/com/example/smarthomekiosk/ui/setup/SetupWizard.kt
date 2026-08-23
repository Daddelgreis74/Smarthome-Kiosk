package com.example.smarthomekiosk.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.smarthomekiosk.KioskSettings
import com.example.smarthomekiosk.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.*

@Composable
fun SetupWizardDialog(
    settings: KioskSettings,
    onComplete: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        var currentStep by remember { mutableStateOf(1) }
        val totalSteps = 5

        // State holder across steps
        var remotePassword by remember { mutableStateOf(settings.httpPassword.ifEmpty { "" }) }
        var settingsPin by remember { mutableStateOf(settings.settingsPassword.ifEmpty { "1234" }) }
        var pinProtection by remember { mutableStateOf(settings.pinProtectionEnabled) }
        var dashboardUrl by remember { mutableStateOf(settings.dashboardUrl.ifEmpty { "http://192.168.178.100:8443" }) }
        var ignoreSsl by remember { mutableStateOf(settings.ignoreSslErrors) }
        var screenTimeout by remember { mutableStateOf(settings.screenTimeoutMinutes) }
        var screenOffMethod by remember { mutableStateOf(settings.screenOffMethod) }
        var motionDetection by remember { mutableStateOf(settings.motionDetectionEnabled) }
        var motionSensitivity by remember { mutableStateOf(settings.motionDetectionSensitivity.toFloat()) }

        var validationError by remember { mutableStateOf<String?>(null) }
        var isTestingConnection by remember { mutableStateOf(false) }
        var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

        // Permission states
        var hasCameraPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        var hasMicPermission by remember {
            mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        }

        val cameraPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasCameraPermission = isGranted
        }

        val micPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            hasMicPermission = isGranted
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AuroraDarkBg)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .heightIn(max = 760.dp)
                    .border(1.dp, AuroraCardBorder, RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = AuroraCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Header: Logo & Stepper
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.linearGradient(listOf(AuroraCyan, AuroraPurple))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Neo Kiosk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
                                Text("Einrichtungs-Assistent", fontSize = 12.sp, color = AuroraCyan)
                            }
                        }

                        // Step Indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (step in 1..totalSteps) {
                                val isActive = step == currentStep
                                val isDone = step < currentStep
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isActive -> AuroraCyan
                                                isDone -> AuroraPurple
                                                else -> Color(0xFF1E293B)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            if (isActive || isDone) Color.Transparent else AuroraCardBorder,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isDone) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    } else {
                                        Text(
                                            text = "$step",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isActive) Color.Black else AuroraTextMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = currentStep.toFloat() / totalSteps.toFloat(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AuroraCyan,
                        trackColor = Color(0xFF1E293B)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Step Body
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        AnimatedContent(
                            targetState = currentStep,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "step_content"
                        ) { step ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                when (step) {
                                    1 -> StepWelcome(
                                        hasCamera = hasCameraPermission,
                                        hasMic = hasMicPermission,
                                        onRequestCamera = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                        onRequestMic = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                                    )
                                    2 -> StepSecurity(
                                        remotePassword = remotePassword,
                                        onRemotePasswordChange = { remotePassword = it; validationError = null },
                                        pin = settingsPin,
                                        onPinChange = { settingsPin = it; validationError = null },
                                        pinProtection = pinProtection,
                                        onPinProtectionChange = { pinProtection = it }
                                    )
                                    3 -> StepDashboardConnection(
                                        url = dashboardUrl,
                                        onUrlChange = { dashboardUrl = it; connectionTestResult = null; validationError = null },
                                        ignoreSsl = ignoreSsl,
                                        onIgnoreSslChange = { ignoreSsl = it; connectionTestResult = null },
                                        isTesting = isTestingConnection,
                                        testResult = connectionTestResult,
                                        onTestConnection = {
                                            if (dashboardUrl.isBlank()) {
                                                validationError = "Bitte gib eine gültige URL ein."
                                                return@StepDashboardConnection
                                            }
                                            isTestingConnection = true
                                            connectionTestResult = null
                                            scope.launch {
                                                val result = testUrlConnection(dashboardUrl, ignoreSsl)
                                                isTestingConnection = false
                                                connectionTestResult = result
                                            }
                                        }
                                    )
                                    4 -> StepKioskBehavior(
                                        timeout = screenTimeout,
                                        onTimeoutChange = { screenTimeout = it },
                                        offMethod = screenOffMethod,
                                        onOffMethodChange = { screenOffMethod = it },
                                        motionEnabled = motionDetection,
                                        onMotionEnabledChange = { motionDetection = it },
                                        motionSensitivity = motionSensitivity,
                                        onSensitivityChange = { motionSensitivity = it }
                                    )
                                    5 -> StepSummary(
                                        remotePassword = remotePassword,
                                        pin = settingsPin,
                                        pinProtection = pinProtection,
                                        dashboardUrl = dashboardUrl,
                                        ignoreSsl = ignoreSsl,
                                        screenTimeout = screenTimeout,
                                        screenOffMethod = screenOffMethod,
                                        motionEnabled = motionDetection
                                    )
                                }
                            }
                        }
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            color = AuroraError,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Footer Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentStep > 1) {
                            OutlinedButton(
                                onClick = {
                                    validationError = null
                                    currentStep--
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AuroraCardBorder, AuroraCardBorder))),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AuroraTextPrimary)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Zurück")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                validationError = null
                                when (currentStep) {
                                    2 -> {
                                        if (remotePassword.trim().length < 4) {
                                            validationError = "Das Remote-Admin-Passwort muss mindestens 4 Zeichen lang sein."
                                            return@Button
                                        }
                                        if (settingsPin.trim().isEmpty()) {
                                            validationError = "Die Einstellungen-PIN darf nicht leer sein."
                                            return@Button
                                        }
                                    }
                                    3 -> {
                                        val trimmedUrl = dashboardUrl.trim()
                                        if (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://")) {
                                            validationError = "Die Dashboard-URL muss mit http:// oder https:// beginnen."
                                            return@Button
                                        }
                                    }
                                }

                                if (currentStep < totalSteps) {
                                    currentStep++
                                } else {
                                    // Save all settings and finish
                                    settings.httpPassword = remotePassword.trim()
                                    settings.settingsPassword = settingsPin.trim()
                                    settings.pinProtectionEnabled = pinProtection
                                    settings.dashboardUrl = dashboardUrl.trim()
                                    settings.ignoreSslErrors = ignoreSsl
                                    settings.screenTimeoutMinutes = screenTimeout
                                    settings.screenOffMethod = screenOffMethod
                                    settings.motionDetectionEnabled = motionDetection
                                    settings.motionDetectionSensitivity = motionSensitivity.toInt()

                                    Toast.makeText(context, "Kiosk-Einrichtung erfolgreich abgeschlossen!", Toast.LENGTH_SHORT).show()
                                    onComplete()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AuroraCyan,
                                contentColor = Color.Black
                            )
                        ) {
                            Text(
                                text = if (currentStep == totalSteps) "Fertigstellen & Starten" else "Weiter",
                                fontWeight = FontWeight.Bold
                            )
                            if (currentStep < totalSteps) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Step 1: Welcome & Permissions
// ----------------------------------------------------------------------------
@Composable
private fun StepWelcome(
    hasCamera: Boolean,
    hasMic: Boolean,
    onRequestCamera: () -> Unit,
    onRequestMic: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Willkommen bei Neo Kiosk", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Dieser Assistent richtet dein Tablet optimal als Smart Home Kiosk-Terminal ein. Alle Designs und Steuerelemente sind auf das futuristische Neo Aurora Design abgestimmt.",
            fontSize = 14.sp,
            color = AuroraTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))
        Text("Erforderliche Berechtigungen", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AuroraCyan)
        Spacer(modifier = Modifier.height(10.dp))

        PermissionCard(
            title = "Kamera-Berechtigung",
            desc = "Wird für die optische Bewegungserkennung genutzt, um das Display bei Annäherung einzuschalten.",
            icon = Icons.Default.CameraAlt,
            isGranted = hasCamera,
            onRequest = onRequestCamera
        )

        Spacer(modifier = Modifier.height(12.dp))

        PermissionCard(
            title = "Mikrofon-Berechtigung",
            desc = "Ermöglicht Spracheingaben für den integrierten Sprachassistenten J.A.R.V.I.S.",
            icon = Icons.Default.Mic,
            isGranted = hasMic,
            onRequest = onRequestMic
        )
    }
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    icon: ImageVector,
    isGranted: Boolean,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isGranted) AuroraEmerald.copy(alpha = 0.5f) else AuroraCardBorder, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isGranted) AuroraEmerald.copy(alpha = 0.15f) else AuroraCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (isGranted) AuroraEmerald else AuroraCyan, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AuroraTextPrimary)
                Text(desc, fontSize = 12.sp, color = AuroraTextMuted)
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (isGranted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AuroraEmerald, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Erteilt", fontSize = 12.sp, color = AuroraEmerald, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onRequest,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraCyan, contentColor = Color.Black)
                ) {
                    Text("Erlauben", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Step 2: Security & PIN
// ----------------------------------------------------------------------------
@Composable
private fun StepSecurity(
    remotePassword: String,
    onRemotePasswordChange: (String) -> Unit,
    pin: String,
    onPinChange: (String) -> Unit,
    pinProtection: Boolean,
    onPinProtectionChange: (Boolean) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Sicherheit & Zugriffsschutz", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Vergib Passwörter, um die Fernsteuerungs-API und die lokalen Tablet-Einstellungen vor unbefugtem Zugriff zu schützen.",
            fontSize = 14.sp,
            color = AuroraTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Remote Password
        Text("1. Remote-Admin Passwort (API & Web-Steuerung)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AuroraCyan)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Wird benötigt, wenn du das Tablet über die Weboberfläche oder per HTTP-API fernsteuerst.", fontSize = 12.sp, color = AuroraTextMuted)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = remotePassword,
            onValueChange = onRemotePasswordChange,
            label = { Text("Remote-Passwort (mind. 4 Zeichen)") },
            placeholder = { Text("z.B. MeinGeheimesKioskPasswort") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = AuroraTextMuted
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Local Settings PIN
        Text("2. Einstellungs-PIN (Tablet-Menü)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AuroraCyan)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Wird abgefragt, wenn du auf dem Tablet vom linken Rand wischt, um die Kiosk-Einstellungen zu öffnen.", fontSize = 12.sp, color = AuroraTextMuted)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.all { c -> c.isDigit() } && it.length <= 8) {
                    onPinChange(it)
                }
            },
            label = { Text("Einstellungen-PIN (Standard: 1234)") },
            placeholder = { Text("1234") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = pinProtection,
                onCheckedChange = onPinProtectionChange,
                colors = CheckboxDefaults.colors(checkedColor = AuroraCyan, checkmarkColor = Color.Black)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("PIN-Schutz für Einstellungen aktivieren", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuroraTextPrimary)
                Text("Wenn deaktiviert, öffnet sich das Menü sofort ohne Abfrage.", fontSize = 11.sp, color = AuroraTextMuted)
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Step 3: Dashboard Connection
// ----------------------------------------------------------------------------
@Composable
private fun StepDashboardConnection(
    url: String,
    onUrlChange: (String) -> Unit,
    ignoreSsl: Boolean,
    onIgnoreSslChange: (Boolean) -> Unit,
    isTesting: Boolean,
    testResult: Pair<Boolean, String>?,
    onTestConnection: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Dashboard-Verbindung", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Gib die Web-Adresse deines SmartHome-Dashboards (z. B. auf deinem TrueNAS Server oder Raspberry Pi) ein.",
            fontSize = 14.sp,
            color = AuroraTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text("Dashboard Server URL") },
            placeholder = { Text("http://192.168.178.100:8443") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = ignoreSsl,
                onCheckedChange = onIgnoreSslChange,
                colors = CheckboxDefaults.colors(checkedColor = AuroraCyan, checkmarkColor = Color.Black)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("SSL-Zertifikatsfehler ignorieren", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = AuroraTextPrimary)
                Text("Empfohlen für lokale IP-Adressen und selbstsignierte HTTPS-Zertifikate.", fontSize = 11.sp, color = AuroraTextMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTestConnection,
                enabled = !isTesting,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AuroraPurple, contentColor = Color.White)
            ) {
                if (isTesting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Teste...")
                } else {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verbindung testen")
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            testResult?.let { (success, message) ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (success) AuroraEmerald.copy(alpha = 0.15f) else AuroraError.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (success) AuroraEmerald else AuroraError,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = if (success) AuroraEmerald else AuroraError,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// Step 4: Kiosk Behavior & Display
// ----------------------------------------------------------------------------
@Composable
private fun StepKioskBehavior(
    timeout: Int,
    onTimeoutChange: (Int) -> Unit,
    offMethod: String,
    onOffMethodChange: (String) -> Unit,
    motionEnabled: Boolean,
    onMotionEnabledChange: (Boolean) -> Unit,
    motionSensitivity: Float,
    onSensitivityChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Kiosk & Display-Verhalten", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Lege fest, wie und wann das Display gedimmt bzw. über die Kamera-Bewegungserkennung wieder aufgeweckt wird.",
            fontSize = 14.sp,
            color = AuroraTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Screen Timeout
        Text("Display-Timeout (Inaktivität)", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AuroraCyan)
        Spacer(modifier = Modifier.height(8.dp))

        val timeoutOptions = listOf(0 to "Nie", 1 to "1 Min", 2 to "2 Min", 5 to "5 Min", 10 to "10 Min")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            timeoutOptions.forEach { (mins, label) ->
                val isSelected = timeout == mins
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) AuroraCyan else Color(0xFF0F172A))
                        .border(1.dp, if (isSelected) Color.Transparent else AuroraCardBorder, RoundedCornerShape(10.dp))
                        .clickable { onTimeoutChange(mins) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else AuroraTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Screen Off Method
        Text("Ausschalt-Methode", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AuroraCyan)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val isFake = offMethod == "fake"
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, if (isFake) AuroraCyan else AuroraCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onOffMethodChange("fake") },
                colors = CardDefaults.cardColors(containerColor = if (isFake) AuroraCyan.copy(alpha = 0.1f) else Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Fake / Schwarzbild (Empfohlen)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AuroraTextPrimary)
                    Text("Sofortiges Aufwachen bei Berührung ohne System-Lock.", fontSize = 11.sp, color = AuroraTextMuted)
                }
            }

            val isNative = offMethod == "native"
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, if (isNative) AuroraCyan else AuroraCardBorder, RoundedCornerShape(12.dp))
                    .clickable { onOffMethodChange("native") },
                colors = CardDefaults.cardColors(containerColor = if (isNative) AuroraCyan.copy(alpha = 0.1f) else Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Native Bildschirmsperre", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AuroraTextPrimary)
                    Text("Schaltet das Display via Device-Admin komplett aus.", fontSize = 11.sp, color = AuroraTextMuted)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Motion Detection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Switch(
                checked = motionEnabled,
                onCheckedChange = onMotionEnabledChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = AuroraCyan)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("Kamera-Bewegungserkennung", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AuroraTextPrimary)
                Text("Weckt das Display automatisch bei Bewegung vor dem Tablet auf.", fontSize = 11.sp, color = AuroraTextMuted)
            }
        }

        if (motionEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Empfindlichkeit: ${motionSensitivity.toInt()}%", fontSize = 13.sp, color = AuroraTextMuted)
            Slider(
                value = motionSensitivity,
                onValueChange = onSensitivityChange,
                valueRange = 10f..100f,
                colors = SliderDefaults.colors(thumbColor = AuroraCyan, activeTrackColor = AuroraCyan)
            )
        }
    }
}

// ----------------------------------------------------------------------------
// Step 5: Summary
// ----------------------------------------------------------------------------
@Composable
private fun StepSummary(
    remotePassword: String,
    pin: String,
    pinProtection: Boolean,
    dashboardUrl: String,
    ignoreSsl: Boolean,
    screenTimeout: Int,
    screenOffMethod: String,
    motionEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Zusammenfassung & Start", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AuroraTextPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Deine Kiosk-Konfiguration ist bereit. Bitte überprüfe die Werte vor dem Start.",
            fontSize = 14.sp,
            color = AuroraTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, AuroraCardBorder, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryRow(label = "Dashboard URL", value = dashboardUrl, icon = Icons.Default.Language)
                SummaryRow(label = "Design-Theme", value = "Neo Aurora (Glas & Glow)", icon = Icons.Default.Palette)
                SummaryRow(label = "SSL-Modus", value = if (ignoreSsl) "Fehler ignorieren (Aktiv)" else "Strikte Prüfung", icon = Icons.Default.Security)
                SummaryRow(label = "Remote API Passwort", value = "••••••••", icon = Icons.Default.Lock)
                SummaryRow(label = "Einstellungen-PIN", value = if (pinProtection) "$pin (Aktiv)" else "Deaktiviert", icon = Icons.Default.Pin)
                SummaryRow(
                    label = "Display-Timeout",
                    value = if (screenTimeout > 0) "$screenTimeout Minuten ($screenOffMethod)" else "Immer an",
                    icon = Icons.Default.ScreenLockPortrait
                )
                SummaryRow(
                    label = "Bewegungserkennung",
                    value = if (motionEnabled) "Aktiviert (Frontkamera)" else "Deaktiviert",
                    icon = Icons.Default.MotionPhotosOn
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = AuroraCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, color = AuroraTextMuted)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = AuroraTextPrimary)
    }
}

// ----------------------------------------------------------------------------
// Network Test Helper
// ----------------------------------------------------------------------------
private suspend fun testUrlConnection(urlString: String, ignoreSsl: Boolean): Pair<Boolean, String> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.requestMethod = "HEAD"

            if (ignoreSsl && connection is HttpsURLConnection) {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                        override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
                    }
                )
                val sc = SSLContext.getInstance("SSL")
                sc.init(null, trustAllCerts, SecureRandom())
                connection.sslSocketFactory = sc.socketFactory
                connection.hostnameVerifier = HostnameVerifier { _, _ -> true }
            }

            connection.connect()
            val code = connection.responseCode
            if (code in 200..399) {
                Pair(true, "Verbindung erfolgreich! (HTTP $code)")
            } else {
                Pair(false, "Server antwortete mit Status $code.")
            }
        } catch (e: Exception) {
            Pair(false, "Nicht erreichbar: ${e.localizedMessage ?: "Verbindungsfehler"}")
        }
    }
}
