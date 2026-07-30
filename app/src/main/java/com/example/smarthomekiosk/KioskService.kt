package com.example.smarthomekiosk

import android.app.*
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.Locale

class KioskService : Service(), KioskHttpServer.KioskCommandListener {

    companion object {
        const val ACTION_WAKE_UP = "com.example.smarthomekiosk.ACTION_WAKE_UP"
        const val ACTION_SLEEP = "com.example.smarthomekiosk.ACTION_SLEEP"
        const val ACTION_RESET_IDLE = "com.example.smarthomekiosk.ACTION_RESET_IDLE"
        const val ACTION_RELOAD_WEBVIEW = "com.example.smarthomekiosk.ACTION_RELOAD_WEBVIEW"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "kiosk_service_channel"
    }

    private lateinit var settings: KioskSettings
    private var httpServer: KioskHttpServer? = null
    private var nsdHelper: KioskNsdHelper? = null
    private var motionDetector: MotionDetector? = null
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isScreenOffState = false

    // Idle timer runnable
    private val idleRunnable = Runnable {
        Log.i("KioskService", "Idle timeout reached, turning screen off")
        onScreenOff()
    }

    // Local broadcast receiver to let MainActivity reset the idle timer on touch
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_RESET_IDLE) {
                resetIdleTimer()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = KioskSettings(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        acquireCpuWakeLock()
        initTts()
        registerReceiver(serviceReceiver, IntentFilter(ACTION_RESET_IDLE))

        startNetworkServices()
        startMotionDetection()
        resetIdleTimer()
        
        Log.i("KioskService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle explicit commands if sent via intent
        val command = intent?.getStringExtra("command")
        if (command == "RESTART_SERVICES") {
            Log.i("KioskService", "Restarting background services with new settings")
            stopNetworkServices()
            stopMotionDetection()
            startNetworkServices()
            startMotionDetection()
            resetIdleTimer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(idleRunnable)
        unregisterReceiver(serviceReceiver)
        stopNetworkServices()
        stopMotionDetection()
        releaseCpuWakeLock()
        
        tts?.stop()
        tts?.shutdown()
        
        Log.i("KioskService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startNetworkServices() {
        if (settings.kioskEnabled) {
            httpServer = KioskHttpServer(this, settings.httpPort, settings.httpPassword, this).apply {
                start()
            }
            if (settings.mdnsEnabled) {
                nsdHelper = KioskNsdHelper(this).apply {
                    registerService(settings.httpPort)
                }
            }
        }
    }

    private fun stopNetworkServices() {
        httpServer?.stop()
        httpServer = null
        nsdHelper?.unregisterService()
        nsdHelper = null
    }

    private fun startMotionDetection() {
        if (settings.motionDetectionEnabled) {
            motionDetector = MotionDetector(this) {
                // On Motion callback
                handler.post {
                    if (isScreenOffState) {
                        onScreenOn()
                    }
                    resetIdleTimer()
                }
            }.apply {
                sensitivity = settings.motionDetectionSensitivity
                isDebugEnabled = settings.motionDetectionDebug
                start()
            }
        }
    }

    private fun stopMotionDetection() {
        motionDetector?.stop()
        motionDetector = null
    }

    private fun resetIdleTimer() {
        handler.removeCallbacks(idleRunnable)
        val timeoutMinutes = settings.screenTimeoutMinutes
        if (timeoutMinutes > 0 && !isScreenOffState) {
            val delayMs = timeoutMinutes * 60 * 1000L
            handler.postDelayed(idleRunnable, delayMs)
        }
    }

    private fun acquireCpuWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KioskApp::CpuWakeLock").apply {
            acquire()
        }
    }

    private fun releaseCpuWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            } else {
                Log.e("KioskService", "TTS Initialization failed")
            }
        }
    }

    // KioskCommandListener implementation
    override fun onScreenOn() {
        Log.i("KioskService", "Screen On triggered")
        isScreenOffState = false
        
        // 1. Send broadcast to wake up MainActivity (dismisses fake standby)
        sendBroadcast(Intent(ACTION_WAKE_UP))
        
        // 2. Hardware Wake: Acquire temporary screen wake lock to light up display (if locked by system)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val screenLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "KioskApp::ScreenWakeLock"
        )
        screenLock.acquire(1000)
        
        // 3. Make sure MainActivity is brought to front
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        
        resetIdleTimer()
    }

    override fun onScreenOff() {
        Log.i("KioskService", "Screen Off triggered")
        isScreenOffState = true
        handler.removeCallbacks(idleRunnable)

        val method = settings.screenOffMethod
        if (method == "admin") {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
            if (dpm.isAdminActive(adminComponent)) {
                try {
                    dpm.lockNow()
                } catch (e: Exception) {
                    Log.e("KioskService", "DeviceAdmin Lock failed, falling back to Fake Standby", e)
                    sendBroadcast(Intent(ACTION_SLEEP))
                }
            } else {
                Log.w("KioskService", "DeviceAdmin is not active, falling back to Fake Standby")
                sendBroadcast(Intent(ACTION_SLEEP))
            }
        } else {
            sendBroadcast(Intent(ACTION_SLEEP))
        }
    }

    override fun onSpeak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kiosk_tts_id")
    }

    override fun onSetVolume(volume: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (volume * maxVol) / 100
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
    }

    override fun getDeviceInfoJson(): String {
        // Battery status
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val chargeStatus = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING || chargeStatus == BatteryManager.BATTERY_STATUS_FULL

        // Memory status
        val mi = ActivityManager.MemoryInfo()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.getMemoryInfo(mi)
        val freeMemoryBytes = mi.availMem

        val json = JSONObject().apply {
            put("batteryLevel", batteryPct)
            put("isCharging", isCharging)
            put("screenOff", isScreenOffState)
            put("freeMemoryMb", freeMemoryBytes / (1024 * 1024))
            put("appVersion", "1.0")
            put("androidVersion", Build.VERSION.RELEASE)
            put("model", Build.MODEL)
            put("dashboardUrl", settings.dashboardUrl)
            put("ignoreSslErrors", settings.ignoreSslErrors)
        }
        return json.toString()
    }

    override fun onReloadWebView() {
        Log.i("KioskService", "WebView Reload triggered")
        sendBroadcast(Intent(ACTION_RELOAD_WEBVIEW))
    }

    override fun onUpdateSettings(dashboardUrl: String?, ignoreSslErrors: Boolean?) {
        Log.i("KioskService", "Updating settings remotely: url=$dashboardUrl, ignoreSsl=$ignoreSslErrors")
        if (dashboardUrl != null) {
            settings.dashboardUrl = dashboardUrl
        }
        if (ignoreSslErrors != null) {
            settings.ignoreSslErrors = ignoreSslErrors
        }
        sendBroadcast(Intent(ACTION_RELOAD_WEBVIEW))
    }

    // Notification Channel for Foreground Service
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Mode Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smarthome Kiosk")
            .setContentText("Kiosk background handler is active.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
