package com.example.smarthomekiosk.i18n

import java.util.Locale

enum class AppLanguage(val code: String, val label: String) {
    AUTO("auto", "Automatisch / System"),
    DE("de", "Deutsch 🇩🇪"),
    EN("en", "English 🇬🇧");

    companion object {
        fun fromCode(code: String): AppLanguage = values().find { it.code == code } ?: AUTO
    }
}

object Strings {
    fun getEffectiveLanguage(configuredCode: String): String {
        return when (configuredCode) {
            "de" -> "de"
            "en" -> "en"
            else -> {
                val sys = Locale.getDefault().language.lowercase()
                if (sys == "de") "de" else "en"
            }
        }
    }

    // Common
    fun appTitle(lang: String) = "Neo Kiosk"
    fun cancel(lang: String) = if (lang == "de") "Abbrechen" else "Cancel"
    fun save(lang: String) = if (lang == "de") "Speichern" else "Save"
    fun saveAndClose(lang: String) = if (lang == "de") "Speichern & Schließen" else "Save & Close"
    fun back(lang: String) = if (lang == "de") "Zurück" else "Back"
    fun next(lang: String) = if (lang == "de") "Weiter" else "Next"
    fun finishAndStart(lang: String) = if (lang == "de") "Fertigstellen & Starten" else "Finish & Start"
    fun granted(lang: String) = if (lang == "de") "Erteilt" else "Granted"
    fun allow(lang: String) = if (lang == "de") "Erlauben" else "Allow"
    fun closeApp(lang: String) = if (lang == "de") "App schließen" else "Close App"

    // Setup Wizard
    fun wizardTitle(lang: String) = if (lang == "de") "Einrichtungs-Assistent" else "Setup Wizard"
    
    // Step 1: Welcome & Language & Permissions
    fun step1Title(lang: String) = if (lang == "de") "Willkommen bei Neo Kiosk" else "Welcome to Neo Kiosk"
    fun step1Desc(lang: String) = if (lang == "de") 
        "Dieser Assistent richtet dein Tablet optimal als Smart Home Kiosk-Terminal ein. Alle Designs und Steuerelemente sind auf das futuristische Neo Aurora Design abgestimmt."
        else "This wizard sets up your tablet as a Smart Home kiosk terminal. All controls and designs are styled in the futuristic Neo Aurora theme."
    fun step1LangSelect(lang: String) = if (lang == "de") "Sprache / Language" else "Language / Sprache"
    fun step1PermissionsTitle(lang: String) = if (lang == "de") "Erforderliche Berechtigungen" else "Required Permissions"
    fun permCameraTitle(lang: String) = if (lang == "de") "Kamera-Berechtigung" else "Camera Permission"
    fun permCameraDesc(lang: String) = if (lang == "de") 
        "Wird für die optische Bewegungserkennung genutzt, um das Display bei Annäherung einzuschalten."
        else "Used for optical motion detection to wake up the screen when motion is detected."
    fun permMicTitle(lang: String) = if (lang == "de") "Mikrofon-Berechtigung" else "Microphone Permission"
    fun permMicDesc(lang: String) = if (lang == "de") 
        "Ermöglicht Spracheingaben für den integrierten Sprachassistenten J.A.R.V.I.S."
        else "Enables voice input for the built-in J.A.R.V.I.S. voice assistant."

    // Step 2: Security
    fun step2Title(lang: String) = if (lang == "de") "Sicherheit & Zugriffsschutz" else "Security & Access Control"
    fun step2Desc(lang: String) = if (lang == "de") 
        "Vergib Passwörter, um die Fernsteuerungs-API und die lokalen Tablet-Einstellungen vor unbefugtem Zugriff zu schützen."
        else "Set passwords to protect the remote REST API and local tablet settings from unauthorized access."
    fun remotePassTitle(lang: String) = if (lang == "de") "1. Remote-Admin Passwort (API & Web-Steuerung)" else "1. Remote Admin Password (API & Web Control)"
    fun remotePassDesc(lang: String) = if (lang == "de") 
        "Wird benötigt, wenn du das Tablet über die Weboberfläche oder per HTTP-API fernsteuerst."
        else "Required when remotely controlling the tablet via web interface or HTTP API."
    fun remotePassLabel(lang: String) = if (lang == "de") "Remote-Passwort (mind. 4 Zeichen)" else "Remote Password (min. 4 chars)"
    fun settingsPinTitle(lang: String) = if (lang == "de") "2. Einstellungs-PIN (Tablet-Menü)" else "2. Settings PIN (Tablet Menu)"
    fun settingsPinDesc(lang: String) = if (lang == "de") 
        "Wird abgefragt, wenn du auf dem Tablet vom linken Rand wischt, um die Kiosk-Einstellungen zu öffnen."
        else "Prompted when swiping from the left screen edge to open tablet settings."
    fun settingsPinLabel(lang: String) = if (lang == "de") "Einstellungen-PIN (Standard: 1234)" else "Settings PIN (Default: 1234)"
    fun pinProtectionToggle(lang: String) = if (lang == "de") "PIN-Schutz für Einstellungen aktivieren" else "Enable PIN protection for settings"
    fun pinProtectionToggleDesc(lang: String) = if (lang == "de") 
        "Wenn deaktiviert, öffnet sich das Menü sofort ohne Abfrage."
        else "When disabled, the settings menu opens immediately without a prompt."

    // Step 3: Dashboard Connection
    fun step3Title(lang: String) = if (lang == "de") "Dashboard-Verbindung" else "Dashboard Connection"
    fun step3Desc(lang: String) = if (lang == "de") 
        "Gib die Web-Adresse deines SmartHome-Dashboards (z. B. auf deinem TrueNAS Server oder Raspberry Pi) ein."
        else "Enter the web address of your SmartHome dashboard (e.g. on your TrueNAS server or Raspberry Pi)."
    fun dashboardUrlLabel(lang: String) = if (lang == "de") "Dashboard Server URL" else "Dashboard Server URL"
    fun ignoreSslToggle(lang: String) = if (lang == "de") "SSL-Zertifikatsfehler ignorieren" else "Ignore SSL certificate errors"
    fun ignoreSslToggleDesc(lang: String) = if (lang == "de") 
        "Empfohlen für lokale IP-Adressen und selbstsignierte HTTPS-Zertifikate."
        else "Recommended for local IP addresses and self-signed HTTPS certificates."
    fun testConnectionBtn(lang: String) = if (lang == "de") "Verbindung testen" else "Test Connection"
    fun testingConnection(lang: String) = if (lang == "de") "Teste..." else "Testing..."

    // Step 4: Kiosk Behavior
    fun step4Title(lang: String) = if (lang == "de") "Kiosk & Display-Verhalten" else "Kiosk & Display Behavior"
    fun step4Desc(lang: String) = if (lang == "de") 
        "Lege fest, wie und wann das Display gedimmt bzw. über die Kamera-Bewegungserkennung wieder aufgeweckt wird."
        else "Configure when the display dims and how it wakes up via camera motion detection."
    fun screenTimeoutLabel(lang: String) = if (lang == "de") "Display-Timeout (Inaktivität)" else "Screen Timeout (Inactivity)"
    fun timeoutNever(lang: String) = if (lang == "de") "Nie" else "Never"
    fun timeoutMin(lang: String, min: Int) = if (lang == "de") "$min Min" else "$min min"
    fun screenOffMethodLabel(lang: String) = if (lang == "de") "Ausschalt-Methode" else "Screen Off Method"
    fun methodFakeTitle(lang: String) = if (lang == "de") "Fake / Schwarzbild (Empfohlen)" else "Fake / Black Screen (Recommended)"
    fun methodFakeDesc(lang: String) = if (lang == "de") "Sofortiges Aufwachen bei Berührung ohne System-Lock." else "Instant wake-up on touch without system lock."
    fun methodNativeTitle(lang: String) = if (lang == "de") "Native Bildschirmsperre" else "Native Screen Lock"
    fun methodNativeDesc(lang: String) = if (lang == "de") "Schaltet das Display via Device-Admin komplett aus." else "Turns the display off completely via Device-Admin."
    fun motionDetectionToggle(lang: String) = if (lang == "de") "Kamera-Bewegungserkennung" else "Camera Motion Detection"
    fun motionDetectionDesc(lang: String) = if (lang == "de") 
        "Weckt das Display automatisch bei Bewegung vor dem Tablet auf."
        else "Automatically wakes up the screen upon detecting motion in front of the tablet."
    fun motionSensitivity(lang: String, percent: Int) = if (lang == "de") "Empfindlichkeit: $percent%" else "Sensitivity: $percent%"

    // Step 5: Summary
    fun step5Title(lang: String) = if (lang == "de") "Zusammenfassung & Start" else "Summary & Start"
    fun step5Desc(lang: String) = if (lang == "de") 
        "Deine Kiosk-Konfiguration ist bereit. Bitte überprüfe die Werte vor dem Start."
        else "Your kiosk configuration is ready. Please review the settings before starting."
    fun summaryTheme(lang: String) = if (lang == "de") "Neo Aurora (Glas & Glow)" else "Neo Aurora (Glass & Glow)"
    fun summarySslIgnored(lang: String) = if (lang == "de") "Fehler ignorieren (Aktiv)" else "Ignore Errors (Active)"
    fun summarySslStrict(lang: String) = if (lang == "de") "Strikte Prüfung" else "Strict Verification"
    fun summaryActive(lang: String) = if (lang == "de") "Aktiv" else "Active"
    fun summaryDisabled(lang: String) = if (lang == "de") "Deaktiviert" else "Disabled"
    fun summaryAlwaysOn(lang: String) = if (lang == "de") "Immer an" else "Always On"
    fun summaryMinutes(lang: String, mins: Int, method: String) = if (lang == "de") "$mins Minuten ($method)" else "$mins minutes ($method)"
    fun summaryMotionEnabled(lang: String) = if (lang == "de") "Aktiviert (Frontkamera)" else "Enabled (Front Camera)"

    // Validation Errors
    fun errRemotePassLength(lang: String) = if (lang == "de") "Das Remote-Admin-Passwort muss mindestens 4 Zeichen lang sein." else "The remote admin password must be at least 4 characters long."
    fun errPinEmpty(lang: String) = if (lang == "de") "Die Einstellungen-PIN darf nicht leer sein." else "The settings PIN cannot be empty."
    fun errUrlProtocol(lang: String) = if (lang == "de") "Die Dashboard-URL muss mit http:// oder https:// beginnen." else "The dashboard URL must begin with http:// or https://"
    fun errUrlEmpty(lang: String) = if (lang == "de") "Bitte gib eine gültige URL ein." else "Please enter a valid URL."
    fun connSuccess(lang: String, code: Int) = if (lang == "de") "Verbindung erfolgreich! (HTTP $code)" else "Connection successful! (HTTP $code)"
    fun connHttpError(lang: String, code: Int) = if (lang == "de") "Server antwortete mit Status $code." else "Server responded with status $code."
    fun connFailed(lang: String, msg: String) = if (lang == "de") "Nicht erreichbar: $msg" else "Unreachable: $msg"
    fun setupCompletedToast(lang: String) = if (lang == "de") "Kiosk-Einrichtung erfolgreich abgeschlossen!" else "Kiosk setup successfully completed!"

    // Settings Dialog
    fun settingsTitle(lang: String) = if (lang == "de") "Kiosk Einstellungen" else "Kiosk Settings"
    fun sectionGeneral(lang: String) = if (lang == "de") "Allgemein & Sprache" else "General & Language"
    fun appLanguageLabel(lang: String) = if (lang == "de") "App-Sprache" else "App Language"
    fun sectionDisplayKiosk(lang: String) = if (lang == "de") "Display & Kiosk-Modus" else "Display & Kiosk Mode"
    fun lockTaskToggle(lang: String) = if (lang == "de") "Kiosk-Modus (Lock Task)" else "Kiosk Mode (Lock Task)"
    fun lockTaskDesc(lang: String) = if (lang == "de") "Verhindert das Verlassen der App" else "Prevents leaving the application"
    fun sectionMotion(lang: String) = if (lang == "de") "Kamera-Bewegungserkennung" else "Camera Motion Detection"
    fun motionDebugToggle(lang: String) = if (lang == "de") "Kamera-Vorschau anzeigen (Debug)" else "Show Camera Preview (Debug)"
    fun motionDebugDesc(lang: String) = if (lang == "de") "Zeigt ein kleines Vorschaubild der Frontkamera zur Kalibrierung" else "Shows a small front camera preview for calibration"
    fun deviceAdminSection(lang: String) = if (lang == "de") "Geräte-Administrator (Native Display-Sperre)" else "Device Administrator (Native Screen Lock)"
    fun deviceAdminGranted(lang: String) = if (lang == "de") "Geräte-Administrator ist aktiv" else "Device Administrator is active"
    fun deviceAdminDesc(lang: String) = if (lang == "de") "Erlaubt das vollständige Ausschalten des Displays" else "Allows completely turning off the display"
    fun deviceAdminGrantBtn(lang: String) = if (lang == "de") "Geräte-Admin aktivieren" else "Activate Device Admin"
    fun overlaySection(lang: String) = if (lang == "de") "System-Overlay (Fake Blackscreen)" else "System Overlay (Fake Black Screen)"
    fun overlayGranted(lang: String) = if (lang == "de") "Overlay-Berechtigung ist erteilt" else "Overlay permission is granted"
    fun overlayGrantBtn(lang: String) = if (lang == "de") "Overlay-Rechte gewähren" else "Grant Overlay Permission"
    fun sectionRestApi(lang: String) = if (lang == "de") "Lokale REST API & Netzwerk" else "Local REST API & Network"
    fun httpPortLabel(lang: String) = if (lang == "de") "HTTP REST Port" else "HTTP REST Port"
    fun apiPasswordLabel(lang: String) = if (lang == "de") "API Authentifizierungs-Passwort" else "API Authentication Password"
    fun mdnsToggle(lang: String) = if (lang == "de") "mDNS Service Discovery" else "mDNS Service Discovery"
    fun mdnsDesc(lang: String) = if (lang == "de") "Macht das Tablet im Netzwerk automatisch findbar" else "Makes the tablet automatically discoverable in the network"
    fun rerunWizardBtn(lang: String) = if (lang == "de") "Einrichtungs-Assistent erneut ausführen" else "Rerun Setup Wizard"
    fun checkForUpdatesBtn(lang: String) = if (lang == "de") "Nach Updates suchen" else "Check for Updates"
    fun checkingForUpdates(lang: String) = if (lang == "de") "Prüfe..." else "Checking..."
    fun reloadPageBtn(lang: String) = if (lang == "de") "Seite neu laden" else "Reload Page"
    fun upToDateToast(lang: String, ver: String) = if (lang == "de") "Kiosk ist auf dem neuesten Stand (v$ver)!" else "Kiosk is up to date (v$ver)!"

    // PIN Prompt
    fun pinPromptTitle(lang: String) = if (lang == "de") "PIN erforderlich" else "PIN Required"
    fun pinPromptDesc(lang: String) = if (lang == "de") "Bitte gib die 4-stellige PIN ein, um die Kiosk-Einstellungen zu öffnen." else "Please enter the 4-digit PIN to open kiosk settings."
    fun pinPlaceholder(lang: String) = if (lang == "de") "PIN eingeben" else "Enter PIN"
    fun pinWrong(lang: String) = if (lang == "de") "Falsche PIN!" else "Incorrect PIN!"
    fun confirm(lang: String) = if (lang == "de") "Bestätigen" else "Confirm"

    // Updates
    fun updateAvailableTitle(lang: String, ver: String) = if (lang == "de") "Update verfügbar (v$ver)" else "Update Available (v$ver)"
    fun updateAvailableDesc(lang: String) = if (lang == "de") "Eine neue Version von Neo Kiosk steht bereit." else "A new version of Neo Kiosk is available."
    fun updateChangelog(lang: String) = if (lang == "de") "Changelog / Neuerungen:" else "Changelog / What's New:"
    fun updateInstallBtn(lang: String) = if (lang == "de") "Herunterladen & Installieren" else "Download & Install"
    fun updateDownloading(lang: String, percent: Int) = if (lang == "de") "Update wird heruntergeladen... $percent%" else "Downloading update... $percent%"
}
