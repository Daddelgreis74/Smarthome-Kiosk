package com.example.smarthomekiosk

import android.content.Context
import android.content.SharedPreferences

class KioskSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("kiosk_settings", Context.MODE_PRIVATE)

    companion object {
        const val KEY_DASHBOARD_URL = "dashboard_url"
        const val KEY_SETTINGS_PASSWORD = "settings_password"
        const val KEY_KIOSK_ENABLED = "kiosk_enabled"
        const val KEY_SCREEN_OFF_METHOD = "screen_off_method"
        const val KEY_SCREEN_TIMEOUT_MINUTES = "screen_timeout_minutes"
        const val KEY_MOTION_DETECTION_ENABLED = "motion_detection_enabled"
        const val KEY_MOTION_DETECTION_SENSITIVITY = "motion_detection_sensitivity"
        const val KEY_MOTION_DETECTION_DEBUG = "motion_detection_debug"
        const val KEY_HTTP_PORT = "http_port"
        const val KEY_HTTP_PASSWORD = "http_password"
        const val KEY_MDNS_ENABLED = "mdns_enabled"
        const val KEY_IGNORE_SSL_ERRORS = "ignore_ssl_errors"
        const val KEY_PIN_PROTECTION_ENABLED = "pin_protection_enabled"
    }

    var dashboardUrl: String
        get() = prefs.getString(KEY_DASHBOARD_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DASHBOARD_URL, value).apply()

    var settingsPassword: String
        get() = prefs.getString(KEY_SETTINGS_PASSWORD, "1234") ?: "1234"
        set(value) = prefs.edit().putString(KEY_SETTINGS_PASSWORD, value).apply()

    var kioskEnabled: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()

    var screenOffMethod: String
        get() = prefs.getString(KEY_SCREEN_OFF_METHOD, "fake") ?: "fake"
        set(value) = prefs.edit().putString(KEY_SCREEN_OFF_METHOD, value).apply()

    var screenTimeoutMinutes: Int
        get() = prefs.getInt(KEY_SCREEN_TIMEOUT_MINUTES, 0)
        set(value) = prefs.edit().putInt(KEY_SCREEN_TIMEOUT_MINUTES, value).apply()

    var motionDetectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION_DETECTION_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_DETECTION_ENABLED, value).apply()

    var motionDetectionSensitivity: Int
        get() = prefs.getInt(KEY_MOTION_DETECTION_SENSITIVITY, 50)
        set(value) = prefs.edit().putInt(KEY_MOTION_DETECTION_SENSITIVITY, value).apply()

    var motionDetectionDebug: Boolean
        get() = prefs.getBoolean(KEY_MOTION_DETECTION_DEBUG, false)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_DETECTION_DEBUG, value).apply()

    var httpPort: Int
        get() = prefs.getInt(KEY_HTTP_PORT, 8080)
        set(value) = prefs.edit().putInt(KEY_HTTP_PORT, value).apply()

    var httpPassword: String
        get() = prefs.getString(KEY_HTTP_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HTTP_PASSWORD, value).apply()

    var mdnsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MDNS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MDNS_ENABLED, value).apply()

    var ignoreSslErrors: Boolean
        get() = prefs.getBoolean(KEY_IGNORE_SSL_ERRORS, true)
        set(value) = prefs.edit().putBoolean(KEY_IGNORE_SSL_ERRORS, value).apply()

    var pinProtectionEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIN_PROTECTION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PIN_PROTECTION_ENABLED, value).apply()
}
