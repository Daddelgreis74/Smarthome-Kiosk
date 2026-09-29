package com.example.smarthomekiosk

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

data class PenBatteryInfo(
    val level: Int,
    val name: String,
    val isConnected: Boolean
)

/**
 * Überwacht und erkennt den Akkustand des Bluetooth-Stifts (z. B. Lenovo Tab Pen Plus AP400U/AP401U).
 * Unterstützt sowohl die moderne Android 12+ InputManager-API als auch die Bluetooth-Batterie-Schnittstelle.
 */
class PenBatteryManager(private val context: Context) {

    companion object {
        private const val TAG = "PenBatteryManager"
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    private var lastLevel: Int = -1
    private var lastName: String = "Lenovo Tab Pen Plus"
    private var isMonitoring: Boolean = false

    private val mainHandler = Handler(Looper.getMainLooper())
    var onBatteryUpdated: ((level: Int, name: String) -> Unit)? = null

    private val inputManager: InputManager? by lazy {
        context.getSystemService(Context.INPUT_SERVICE) as? InputManager
    }

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            refreshBattery()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            refreshBattery()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            refreshBattery()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return

            if (action == ACTION_BATTERY_LEVEL_CHANGED) {
                val level = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                @Suppress("DEPRECATION")
                val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                } else {
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                val devName = device?.name ?: lastName

                if (isPenName(devName) && level >= 0) {
                    lastLevel = level
                    lastName = devName
                    notifyUpdate(level, devName)
                    return
                }
            }

            // Bei Verbindung / Trennung den Status neu berechnen
            refreshBattery()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        try {
            inputManager?.registerInputDeviceListener(inputDeviceListener, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Registrieren des InputDeviceListeners", e)
        }

        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_BATTERY_LEVEL_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(bluetoothReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Registrieren des BluetoothReceivers", e)
        }

        // Initiale Abfrage
        refreshBattery()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        try {
            inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Abmelden des InputDeviceListeners", e)
        }

        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Abmelden des BluetoothReceivers", e)
        }
    }

    fun getBatteryLevel(): Int {
        val info = queryCurrentPenInfo()
        return if (info.isConnected) info.level else lastLevel
    }

    fun getPenName(): String {
        return lastName
    }

    fun isConnected(): Boolean {
        return getBatteryLevel() >= 0
    }

    fun refreshBattery() {
        mainHandler.post {
            val info = queryCurrentPenInfo()
            notifyUpdate(info.level, info.name)
        }
    }

    private fun notifyUpdate(level: Int, name: String) {
        mainHandler.post {
            onBatteryUpdated?.invoke(level, name)
        }
    }

    fun queryCurrentPenInfo(): PenBatteryInfo {
        // 1. Priorität: Android InputManager (ab Android 12/API 31 liefert device.batteryState den echten Pen-Akkustand)
        try {
            inputManager?.let { im ->
                for (id in im.inputDeviceIds) {
                    val device = im.getInputDevice(id) ?: continue
                    val sources = device.sources
                    val isStylus = (sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
                                   (sources and InputDevice.SOURCE_BLUETOOTH_STYLUS) == InputDevice.SOURCE_BLUETOOTH_STYLUS ||
                                   isPenName(device.name)

                    if (isStylus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val batteryState = device.batteryState
                        if (batteryState.isPresent) {
                            val cap = batteryState.capacity
                            if (!cap.isNaN() && cap >= 0f) {
                                val pct = (cap * 100f).roundToInt().coerceIn(0, 100)
                                val devName = device.name?.takeIf { it.isNotBlank() } ?: "Lenovo Tab Pen Plus"
                                lastLevel = pct
                                lastName = devName
                                return PenBatteryInfo(pct, devName, true)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler bei InputManager-Abfrage", e)
        }

        // 2. Priorität: BluetoothAdapter gekoppelte Geräte & getBatteryLevel Reflection
        try {
            val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter != null && adapter.isEnabled) {
                val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    val bonded = adapter.bondedDevices
                    for (bDev in bonded) {
                        val bName = bDev.name ?: ""
                        if (isPenName(bName)) {
                            val level = getBluetoothDeviceBattery(bDev)
                            if (level != null && level >= 0) {
                                val cleanName = if (bName.isNotBlank()) bName else "Lenovo Tab Pen Plus"
                                lastLevel = level
                                lastName = cleanName
                                return PenBatteryInfo(level, cleanName, true)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler bei BluetoothDevice-Abfrage", e)
        }

        return PenBatteryInfo(-1, lastName, false)
    }

    private fun getBluetoothDeviceBattery(device: BluetoothDevice): Int? {
        return try {
            val method = device.javaClass.getMethod("getBatteryLevel")
            val res = method.invoke(device) as? Int
            if (res != null && res >= 0) res else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isPenName(name: String?): Boolean {
        if (name == null) return false
        val lower = name.lowercase()
        return lower.contains("pen") ||
               lower.contains("stylus") ||
               lower.contains("ap400") ||
               lower.contains("ap401") ||
               lower.contains("lenovo precision")
    }
}
