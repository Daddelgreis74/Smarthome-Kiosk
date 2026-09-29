package com.example.smarthomekiosk

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

data class PenBatteryInfo(
    val level: Int,
    val name: String,
    val isConnected: Boolean,
    val source: String = "unknown"
)

/**
 * Erkennt und überwacht den Akkustand von Stylus / Bluetooth Pens (insb. Lenovo Tab Pen Plus / Lenovo Tap Pen).
 * Unterstützt BLE GATT (Battery Service 0x180F / 0x2A19), Android 12+ InputManager, System Settings & Broadcasts.
 */
class PenBatteryManager(private val context: Context) {

    companion object {
        private const val TAG = "PenBatteryManager"
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }

    private var lastLevel: Int = -1
    private var lastName: String = "Lenovo Tab Pen"
    private var lastSource: String = "none"
    private var isMonitoring: Boolean = false
    private var activeGatt: BluetoothGatt? = null

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
                    lastSource = "broadcast"
                    notifyUpdate(level, devName)
                    return
                }
            }

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

        refreshBattery()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
            activeGatt = null
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Schließen des GATT-Clients", e)
        }

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
            if (info.isConnected) {
                notifyUpdate(info.level, info.name)
            }
        }
    }

    private fun notifyUpdate(level: Int, name: String) {
        mainHandler.post {
            onBatteryUpdated?.invoke(level, name)
        }
    }

    fun queryCurrentPenInfo(): PenBatteryInfo {
        // 1. Priorität: Android InputManager (ab Android 12/API 31)
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
                        val cap = batteryState.capacity
                        if (!cap.isNaN() && cap >= 0f) {
                            val pct = (cap * 100f).roundToInt().coerceIn(0, 100)
                            val devName = device.name?.takeIf { it.isNotBlank() } ?: "Lenovo Tab Pen"
                            lastLevel = pct
                            lastName = devName
                            lastSource = "input_manager"
                            return PenBatteryInfo(pct, devName, true, lastSource)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler bei InputManager-Abfrage", e)
        }

        // 2. Priorität: Lenovo System-Settings (Settings.System / Settings.Global)
        val settingsBattery = checkSystemSettingsBattery()
        if (settingsBattery != null && settingsBattery >= 0) {
            lastLevel = settingsBattery
            lastSource = "system_settings"
            return PenBatteryInfo(settingsBattery, lastName, true, lastSource)
        }

        // 3. Priorität: Gekoppelte Bluetooth-Geräte
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
                            val cleanName = if (bName.isNotBlank()) bName else "Lenovo Tab Pen"
                            lastName = cleanName

                            // A: Reflected getBatteryLevel()
                            val refLevel = getBluetoothDeviceBattery(bDev)
                            if (refLevel != null && refLevel >= 0) {
                                lastLevel = refLevel
                                lastSource = "bt_reflection"
                                return PenBatteryInfo(refLevel, cleanName, true, lastSource)
                            }

                            // B: BLE GATT Battery Service auslesen (asynchron via connectGatt)
                            triggerBleGattBatteryRead(bDev)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler bei BluetoothDevice-Abfrage", e)
        }

        return PenBatteryInfo(lastLevel, lastName, lastLevel >= 0, lastSource)
    }

    private fun checkSystemSettingsBattery(): Int? {
        val keys = listOf(
            "lenovo_pen_battery_level", "pen_battery_level", "stylus_battery_level",
            "lenovo_stylus_battery", "lenovo_pen_battery", "pen_battery",
            "stylus_battery", "wacom_pen_battery", "lenovo_pen_capacity"
        )
        for (key in keys) {
            try {
                val level = Settings.System.getInt(context.contentResolver, key, -1)
                if (level in 0..100) return level
            } catch (_: Exception) {}
            try {
                val level = Settings.Global.getInt(context.contentResolver, key, -1)
                if (level in 0..100) return level
            } catch (_: Exception) {}
        }
        return null
    }

    private fun triggerBleGattBatteryRead(device: BluetoothDevice) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        try {
            activeGatt?.disconnect()
            activeGatt?.close()
            activeGatt = null

            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            gatt.discoverServices()
                        } catch (e: Exception) {
                            Log.w(TAG, "discoverServices failed", e)
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        try {
                            gatt.close()
                        } catch (_: Exception) {}
                        if (activeGatt == gatt) activeGatt = null
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt.getService(BATTERY_SERVICE_UUID)
                        val char = service?.getCharacteristic(BATTERY_LEVEL_CHAR_UUID)
                        if (char != null) {
                            try {
                                gatt.readCharacteristic(char)
                            } catch (e: Exception) {
                                Log.w(TAG, "readCharacteristic failed", e)
                            }
                        }
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onCharacteristicRead(
                    gatt: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    status: Int
                ) {
                    if (status == BluetoothGatt.GATT_SUCCESS && characteristic.uuid == BATTERY_LEVEL_CHAR_UUID) {
                        val value = characteristic.value
                        val level = if (value != null && value.isNotEmpty()) {
                            value[0].toInt() and 0xFF
                        } else -1

                        if (level in 0..100) {
                            Log.i(TAG, "BLE GATT Battery Level: $level% for ${gatt.device.name}")
                            lastLevel = level
                            lastName = gatt.device.name ?: "Lenovo Tab Pen"
                            lastSource = "ble_gatt"
                            notifyUpdate(level, lastName)
                        }
                    }
                }
            }

            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error connecting BLE GATT to ${device.name}", e)
        }
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
               lower.contains("lenovo precision") ||
               lower.contains("lenovo tab") ||
               lower.contains("lenovo tap")
    }

    fun getDebugJson(): String {
        val root = JSONObject()
        root.put("lastLevel", lastLevel)
        root.put("lastName", lastName)
        root.put("lastSource", lastSource)

        val hasBtPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        root.put("hasBtConnectPermission", hasBtPerm)

        // Input Devices
        val inputsArr = JSONArray()
        inputManager?.let { im ->
            for (id in im.inputDeviceIds) {
                val d = im.getInputDevice(id) ?: continue
                val devObj = JSONObject()
                devObj.put("id", id)
                devObj.put("name", d.name)
                devObj.put("sources", d.sources)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val bs = d.batteryState
                        devObj.put("batteryPresent", bs.isPresent)
                        devObj.put("batteryCapacity", bs.capacity)
                    } catch (e: Exception) {
                        devObj.put("batteryErr", e.message)
                    }
                }
                inputsArr.put(devObj)
            }
        }
        root.put("inputDevices", inputsArr)

        // Bluetooth Devices
        val btArr = JSONArray()
        try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter
            root.put("btAdapterEnabled", adapter?.isEnabled == true)
            if (adapter != null && hasBtPerm) {
                for (b in adapter.bondedDevices) {
                    val bObj = JSONObject()
                    bObj.put("name", b.name)
                    bObj.put("address", b.address)
                    bObj.put("type", b.type)
                    bObj.put("batteryLevel", getBluetoothDeviceBattery(b) ?: -1)
                    bObj.put("isPenMatch", isPenName(b.name))
                    btArr.put(bObj)
                }
            }
        } catch (e: Exception) {
            root.put("btErr", e.message)
        }
        root.put("bondedDevices", btArr)

        // System Settings
        val settingsObj = JSONObject()
        val checkKeys = listOf(
            "lenovo_pen_battery_level", "pen_battery_level", "stylus_battery_level",
            "lenovo_stylus_battery", "lenovo_pen_battery", "pen_battery", "stylus_battery",
            "lenovo_pen_capacity"
        )
        for (k in checkKeys) {
            val sVal = try { Settings.System.getString(context.contentResolver, k) } catch (_: Exception) { null }
            val gVal = try { Settings.Global.getString(context.contentResolver, k) } catch (_: Exception) { null }
            if (sVal != null || gVal != null) {
                settingsObj.put(k, "system=$sVal, global=$gVal")
            }
        }
        root.put("penSettings", settingsObj)

        return root.toString(2)
    }
}
