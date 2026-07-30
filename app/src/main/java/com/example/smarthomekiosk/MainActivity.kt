package com.example.smarthomekiosk

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.smarthomekiosk.theme.SmarthomeKioskTheme
import com.example.smarthomekiosk.ui.main.MainScreenContent

class MainActivity : ComponentActivity() {

    lateinit var settings: KioskSettings
    val isDimmedState = mutableStateOf(false)

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                KioskService.ACTION_WAKE_UP -> {
                    setDimmed(false)
                }
                KioskService.ACTION_SLEEP -> {
                    setDimmed(true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = KioskSettings(this)

        enableEdgeToEdge()
        setupKioskFlags()

        // Register receiver for screen state broadcasts
        val filter = IntentFilter().apply {
            addAction(KioskService.ACTION_WAKE_UP)
            addAction(KioskService.ACTION_SLEEP)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }

        // Start Kiosk Background Service
        startKioskService()

        // Check & request camera permission if motion detection is enabled
        checkPermissions()

        setContent {
            SmarthomeKioskTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreenContent(
                        settings = settings,
                        isDimmed = isDimmedState.value,
                        onWakeUp = {
                            sendBroadcast(Intent(KioskService.ACTION_RESET_IDLE))
                            sendBroadcast(Intent(KioskService.ACTION_WAKE_UP))
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        sendBroadcast(Intent(KioskService.ACTION_RESET_IDLE))
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
    }

    private fun startKioskService() {
        val serviceIntent = Intent(this, KioskService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    fun restartKioskService() {
        val serviceIntent = Intent(this, KioskService::class.java).apply {
            putExtra("command", "RESTART_SERVICES")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun setupKioskFlags() {
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Prevent screen locks & show on lock screen (for older/various APIs)
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
    }

    private fun applyImmersiveMode() {
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun setDimmed(dimmed: Boolean) {
        isDimmedState.value = dimmed
        val layoutParams = window.attributes
        // Setting brightness to 0.01f dims the screen completely
        layoutParams.screenBrightness = if (dimmed) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = layoutParams
    }

    private fun checkPermissions() {
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }
        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), 100)
        }
    }

    // Back button lockdown
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (settings.kioskEnabled) {
            // Do nothing, block back button in kiosk mode
        } else {
            super.onBackPressed()
        }
    }
}
