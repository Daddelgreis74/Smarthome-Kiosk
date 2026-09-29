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
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import kotlin.math.roundToInt

data class PenBatteryInfo(
    val level: Int,
    val name: String,
    val isConnected: Boolean,
    val source: String = "unknown"
)

/**
 * Erkennt und überwacht den Akkustand von Stylus / Pens (insb. Lenovo Tab Pen AP400U / AP401U).
 * Unterstützt InputManager (Android 12+), MotionEvent Live-Capture, System-Settings, Sysfs & BLE.
 */
class PenBatteryManager(private val context: Context) {

    companion object {
        private const val TAG = "PenBatteryManager"
        val BATTERY_SERVICE_UUID: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL_CHAR_UUID: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

        @Volatile
        var instance: PenBatteryManager? = null

        @Volatile
        var lastStylusEvent: JSONObject? = null
        @Volatile
        var stylusEventCount: Int = 0

        fun recordStylusMotionEvent(ev: MotionEvent) {
            try {
                for (i in 0 until ev.pointerCount) {
                    val toolType = ev.getToolType(i)
                    if (toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER) {
                        stylusEventCount++
                        val obj = JSONObject()
                        obj.put("count", stylusEventCount)
                        obj.put("action", MotionEvent.actionToString(ev.actionMasked))
                        obj.put("toolType", toolType)
                        obj.put("pressure", ev.getPressure(i))
                        val dev = ev.device
                        if (dev != null) {
                            obj.put("deviceId", dev.id)
                            obj.put("deviceName", dev.name)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                try {
                                    val bs = dev.batteryState
                                    obj.put("batteryPresent", bs.isPresent)
                                    val cap = bs.capacity
                                    if (cap.isNaN()) {
                                        obj.put("batteryCapacity", "NaN")
                                    } else {
                                        obj.put("batteryCapacity", cap)
                                        if (cap in 0f..1f) {
                                            val pct = (cap * 100f).roundToInt().coerceIn(0, 100)
                                            instance?.updateBatteryLevel(pct, dev.name ?: "Lenovo Tab Pen", "stylus_touch_event")
                                        }
                                    }
                                    obj.put("batteryStatus", bs.status)
                                } catch (e: Exception) {
                                    obj.put("batteryErr", e.message)
                                }
                            }
                        }
                        lastStylusEvent = obj
                        break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error recording stylus motion event", e)
            }
        }
    }

    private var lastLevel: Int = -1
    private var lastName: String = "Lenovo Tab Pen"
    private var lastSource: String = "none"
    private var isMonitoring: Boolean = false
    private var activeGatt: BluetoothGatt? = null

    private val recentBroadcasts = mutableListOf<JSONObject>()
    private val mainHandler = Handler(Looper.getMainLooper())
    var onBatteryUpdated: ((level: Int, name: String) -> Unit)? = null

    init {
        instance = this
    }

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

    private val stylusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return

            try {
                val bObj = JSONObject()
                bObj.put("action", action)
                val extrasObj = JSONObject()
                val bundle = intent.extras
                if (bundle != null) {
                    for (key in bundle.keySet()) {
                        @Suppress("DEPRECATION")
                        val v = bundle.get(key)
                        extrasObj.put(key, v?.toString() ?: "null")
                        val lower = key.lowercase()
                        if (v is Number && (lower.contains("battery") || lower.contains("level") || lower.contains("capacity") || lower.contains("pct"))) {
                            val num = v.toInt()
                            if (num in 0..100 && !action.contains("android.intent.action.BATTERY_CHANGED")) {
                                updateBatteryLevel(num, "Lenovo Tab Pen", "broadcast:$action:$key")
                            }
                        }
                    }
                }
                bObj.put("extras", extrasObj)
                synchronized(recentBroadcasts) {
                    if (recentBroadcasts.size > 20) {
                        recentBroadcasts.removeAt(0)
                    }
                    recentBroadcasts.add(bObj)
                }
            } catch (_: Exception) {}

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
                    updateBatteryLevel(level, devName, "broadcast_bt")
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
                addAction("android.hardware.input.action.STYLUS_BATTERY")
                addAction("android.hardware.input.action.STYLUS_BATTERY_CHANGED")
                addAction("com.lenovo.stylus.BATTERY_CHANGED")
                addAction("com.lenovo.pen.BATTERY_CHANGED")
                addAction("lenovo.intent.action.PEN_BATTERY")
                addAction("lenovo.intent.action.STYLUS_BATTERY")
                addAction("com.zui.pen.BATTERY_CHANGED")
                addAction("com.zui.stylus.BATTERY_CHANGED")
                addAction("com.motorola.stylus.BATTERY_CHANGED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(stylusReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(stylusReceiver, filter)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Registrieren des StylusReceivers", e)
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
            context.unregisterReceiver(stylusReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Fehler beim Abmelden des StylusReceivers", e)
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

    fun updateBatteryLevel(level: Int, name: String, source: String) {
        if (level in 0..100) {
            lastLevel = level
            lastName = name
            lastSource = source
            notifyUpdate(level, name)
        }
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
                        if (batteryState.isPresent && !cap.isNaN() && cap >= 0f) {
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

        // 3. Priorität: Sysfs Battery Scan
        val sysfsBattery = checkSysfsBattery()
        if (sysfsBattery != null && sysfsBattery >= 0) {
            lastLevel = sysfsBattery
            lastSource = "sysfs"
            return PenBatteryInfo(sysfsBattery, lastName, true, lastSource)
        }

        // 4. Priorität: Gekoppelte Bluetooth-Geräte
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

                            // Reflected getBatteryLevel()
                            val refLevel = getBluetoothDeviceBattery(bDev)
                            if (refLevel != null && refLevel >= 0) {
                                lastLevel = refLevel
                                lastSource = "bt_reflection"
                                return PenBatteryInfo(refLevel, cleanName, true, lastSource)
                            }

                            // BLE GATT Battery Service auslesen
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
            try {
                val level = Settings.Secure.getInt(context.contentResolver, key, -1)
                if (level in 0..100) return level
            } catch (_: Exception) {}
        }
        return null
    }

    private fun checkSysfsBattery(): Int? {
        val candidatePaths = listOf(
            "/sys/class/input/input5/device/battery/capacity",
            "/sys/class/input/input5/capacity",
            "/sys/class/power_supply/stylus/capacity",
            "/sys/class/power_supply/pen/capacity",
            "/sys/class/power_supply/wacom_battery/capacity"
        )
        for (path in candidatePaths) {
            try {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val text = f.readText().trim()
                    val value = text.toIntOrNull()
                    if (value != null && value in 0..100) {
                        return value
                    }
                }
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
                            updateBatteryLevel(level, gatt.device.name ?: "Lenovo Tab Pen", "ble_gatt")
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

    private fun scanSystemProps(): JSONObject {
        val obj = JSONObject()
        try {
            val process = Runtime.getRuntime().exec("/system/bin/getprop")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line ?: continue
                val lower = l.lowercase()
                if (lower.contains("pen") || lower.contains("stylus") || lower.contains("wacom") ||
                    lower.contains("himax") || lower.contains("ap40") || lower.contains("lenovo") ||
                    lower.contains("touch")) {
                    val parts = l.split("]: [")
                    if (parts.size == 2) {
                        val key = parts[0].trim().removePrefix("[").removeSuffix("]")
                        val value = parts[1].trim().removePrefix("[").removeSuffix("]")
                        obj.put(key, value)
                    }
                }
            }
            reader.close()
            process.waitFor()
        } catch (e: Exception) {
            obj.put("scanError", e.message)
        }
        return obj
    }

    private fun scanMatchedPackages(): JSONArray {
        val arr = JSONArray()
        try {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val pName = pkg.packageName.lowercase()
                if (pName.contains("pen") || pName.contains("stylus") || pName.contains("wacom") ||
                    (pName.contains("lenovo") && (pName.contains("service") || pName.contains("device") || pName.contains("touch") || pName.contains("smart")))) {
                    val pObj = JSONObject()
                    pObj.put("package", pkg.packageName)
                    pObj.put("version", pkg.versionName ?: "")
                    arr.put(pObj)
                }
            }
        } catch (_: Exception) {}
        return arr
    }

    private fun scanSysfs(): JSONArray {
        val results = JSONArray()
        val paths = listOf(
            "/sys/class/power_supply",
            "/sys/class/input/input5",
            "/sys/class/input/input5/device",
            "/sys/class/input/input4",
            "/sys/devices/virtual/input/input5"
        )
        for (p in paths) {
            val dir = File(p)
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    try {
                        val fObj = JSONObject()
                        fObj.put("path", f.absolutePath)
                        fObj.put("name", f.name)
                        fObj.put("isDir", f.isDirectory)
                        if (f.isFile && f.canRead() && f.length() < 2048) {
                            val lower = f.name.lowercase()
                            if (lower.contains("capacity") || lower.contains("battery") || lower.contains("status") ||
                                lower.contains("pen") || lower.contains("stylus") || lower.contains("name") ||
                                lower.contains("type") || lower.contains("health") || lower.contains("mode") ||
                                lower.contains("state")) {
                                fObj.put("content", f.readText().trim())
                            }
                        }
                        results.put(fObj)
                    } catch (_: Exception) {}
                }
            }
        }
        return results
    }

    private fun scanAllSettings(): JSONObject {
        val root = JSONObject()

        val uris = listOf(
            "system" to Uri.parse("content://settings/system"),
            "secure" to Uri.parse("content://settings/secure"),
            "global" to Uri.parse("content://settings/global")
        )
        for ((name, uri) in uris) {
            val matches = JSONObject()
            try {
                val cursor = context.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)
                if (cursor != null) {
                    val nameIdx = cursor.getColumnIndex("name")
                    val valIdx = cursor.getColumnIndex("value")
                    while (cursor.moveToNext()) {
                        val k = cursor.getString(nameIdx) ?: continue
                        val lower = k.lowercase()
                        if (lower.contains("pen") || lower.contains("stylus") || lower.contains("wacom")) {
                            val v = cursor.getString(valIdx)
                            matches.put(k, v)
                        }
                    }
                    cursor.close()
                }
            } catch (_: Exception) {}
            if (matches.length() > 0) {
                root.put("cursor_$name", matches)
            }
        }

        val fallbackKeys = listOf(
            "lenovo_pen_battery_level", "pen_battery_level", "stylus_battery_level",
            "lenovo_stylus_battery", "lenovo_pen_battery", "pen_battery", "stylus_battery",
            "wacom_pen_battery", "lenovo_pen_capacity", "stylus_connected", "pen_connected",
            "lenovo_pen_state", "lenovo_pen_sn", "pen_mode", "stylus_mode", "lenovo_stylus_mode",
            "lenovo_pen_type", "lenovo_stylus_type", "lenovo_pen_mac", "pen_low_battery",
            "stylus_low_battery", "pen_status", "stylus_status", "stylus_charging_status",
            "lenovo_pen_charge_state", "lenovo_pen_connect_status"
        )
        val fallbackObj = JSONObject()
        for (k in fallbackKeys) {
            val s = try { Settings.System.getString(context.contentResolver, k) } catch (_: Exception) { null }
            val sec = try { Settings.Secure.getString(context.contentResolver, k) } catch (_: Exception) { null }
            val g = try { Settings.Global.getString(context.contentResolver, k) } catch (_: Exception) { null }
            if (s != null || sec != null || g != null) {
                fallbackObj.put(k, "system=$s, secure=$sec, global=$g")
            }
        }
        root.put("probedKeys", fallbackObj)
        return root
    }

    fun getDebugJson(): String {
        val root = JSONObject()
        root.put("lastLevel", lastLevel)
        root.put("lastName", lastName)
        root.put("lastSource", lastSource)

        val hasBtPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        root.put("hasBtConnectPermission", hasBtPerm)

        // Live Stylus Motion Event
        root.put("stylusEventCount", stylusEventCount)
        root.put("lastStylusEvent", lastStylusEvent ?: JSONObject.NULL)

        // Input Devices (with safe NaN handling)
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
                        val cap = bs.capacity
                        if (cap.isNaN()) {
                            devObj.put("batteryCapacity", "NaN")
                        } else {
                            devObj.put("batteryCapacity", cap)
                        }
                        devObj.put("batteryStatus", bs.status)
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
        root.put("settingsScan", scanAllSettings())

        // System Properties Scan
        root.put("systemProps", scanSystemProps())

        // Matched Packages Scan
        root.put("matchedPackages", scanMatchedPackages())

        // Sysfs Scan
        root.put("sysfsScan", scanSysfs())

        // Recent Broadcasts
        val bArr = JSONArray()
        synchronized(recentBroadcasts) {
            for (b in recentBroadcasts) {
                bArr.put(b)
            }
        }
        root.put("recentBroadcasts", bArr)

        return root.toString(2)
    }
}
