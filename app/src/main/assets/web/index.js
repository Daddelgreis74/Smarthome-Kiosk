// State
let apiPassword = "";
let pollingTimer = null;
let currentLang = localStorage.getItem("kiosk_web_lang") || "de";

// Dictionary
const translations = {
    de: {
        login_title: "Kiosk Login",
        login_subtitle: "Bitte gib das Kiosk-API-Passwort ein, um fortzufahren.",
        login_btn: "Anmelden",
        status_connected: "Verbunden",
        logout_btn: "Abmelden",
        card_status_title: "Systemstatus",
        stat_screen: "Display:",
        stat_battery: "Batterie:",
        stat_ram: "Freier Speicher:",
        stat_model: "Tablet-Modell:",
        card_display_title: "Display Steuerung",
        card_display_desc: "Schalte den Bildschirm des Tablets ein oder aus.",
        btn_screen_on: "Bildschirm Ein",
        btn_screen_off: "Bildschirm Aus",
        card_volume_title: "Lautstärke",
        card_volume_desc: "Passe die Musik- und Medienlautstärke an.",
        btn_mute: "Stummschalten",
        card_tts_title: "Sprachausgabe (TTS)",
        card_tts_desc: "Lass das Tablet eine Nachricht vorlesen.",
        tts_placeholder: "Gib hier einen Text ein...",
        btn_speak: "Nachricht vorlesen",
        card_ops_title: "System Aktionen",
        card_ops_desc: "Steuere das Kiosk-System und die Web-Anzeige.",
        btn_reload_webview: "WebView neu laden",
        card_settings_title: "Kiosk Einstellungen",
        card_settings_desc: "Konfiguriere die Dashboard-URL und die SSL-Sicherheit des Tablets.",
        label_dash_url: "Dashboard URL:",
        label_ignore_ssl: "SSL-Fehler ignorieren",
        label_pin_protection: "PIN-Schutz für Einstellungen aktivieren",
        btn_save_settings: "Einstellungen speichern",
        toast_tts_empty: "Bitte gib einen Text ein, der vorgelesen werden soll.",
        toast_settings_saved: "Einstellungen erfolgreich gespeichert!",
        toast_cmd_success: "Befehl erfolgreich ausgeführt.",
        toast_cmd_error: "Fehler bei der Ausführung des Befehls.",
        toast_login_error: "Ungültiges API-Passwort oder Verbindung fehlgeschlagen."
    },
    en: {
        login_title: "Kiosk Login",
        login_subtitle: "Please enter the kiosk API password to continue.",
        login_btn: "Sign In",
        status_connected: "Connected",
        logout_btn: "Sign Out",
        card_status_title: "System Status",
        stat_screen: "Screen:",
        stat_battery: "Battery:",
        stat_ram: "Free Memory:",
        stat_model: "Tablet Model:",
        card_display_title: "Display Control",
        card_display_desc: "Turn the tablet screen on or off.",
        btn_screen_on: "Screen On",
        btn_screen_off: "Screen Off",
        card_volume_title: "Volume",
        card_volume_desc: "Adjust music and media playback volume.",
        btn_mute: "Mute",
        card_tts_title: "Voice Output (TTS)",
        card_tts_desc: "Let the tablet read a voice message.",
        tts_placeholder: "Enter a message to speak...",
        btn_speak: "Speak Message",
        card_ops_title: "System Actions",
        card_ops_desc: "Control the kiosk system and web view.",
        btn_reload_webview: "Reload WebView",
        card_settings_title: "Kiosk Settings",
        card_settings_desc: "Configure the dashboard URL and SSL security on the tablet.",
        label_dash_url: "Dashboard URL:",
        label_ignore_ssl: "Ignore SSL Errors",
        label_pin_protection: "Enable PIN Protection for Settings",
        btn_save_settings: "Save Settings",
        toast_tts_empty: "Please enter a message to read aloud.",
        toast_settings_saved: "Settings successfully saved!",
        toast_cmd_success: "Command executed successfully.",
        toast_cmd_error: "Error executing command.",
        toast_login_error: "Invalid API password or connection failed."
    }
};

function applyLanguage(lang) {
    currentLang = lang;
    localStorage.setItem("kiosk_web_lang", lang);
    const dict = translations[lang] || translations.de;

    document.querySelectorAll("[data-i18n]").forEach(el => {
        const key = el.getAttribute("data-i18n");
        if (dict[key]) el.textContent = dict[key];
    });

    const ttsText = document.getElementById("tts-text");
    if (ttsText) ttsText.placeholder = dict.tts_placeholder;

    const loginLangBtn = document.getElementById("login-lang-btn");
    if (loginLangBtn) loginLangBtn.textContent = lang === "de" ? "🇬🇧 English" : "🇩🇪 Deutsch";

    const dashLangBtn = document.getElementById("dash-lang-btn");
    if (dashLangBtn) dashLangBtn.textContent = lang === "de" ? "🇬🇧 EN" : "🇩🇪 DE";
}

function toggleLanguage() {
    applyLanguage(currentLang === "de" ? "en" : "de");
}

// DOM Elements
const loginScreen = document.getElementById("login-screen");
const dashboardPanel = document.getElementById("dashboard-panel");
const passwordInput = document.getElementById("api-password");
const loginBtn = document.getElementById("login-btn");
const loginError = document.getElementById("login-error");
const logoutBtn = document.getElementById("logout-btn");
const loginLangBtn = document.getElementById("login-lang-btn");
const dashLangBtn = document.getElementById("dash-lang-btn");

// Status Elements
const statScreen = document.getElementById("stat-screen");
const statBattery = document.getElementById("stat-battery");
const batteryLevelBar = document.getElementById("battery-level-bar");
const statRam = document.getElementById("stat-ram");
const statModel = document.getElementById("stat-model");
const chargingIcon = document.getElementById("charging-icon");

// Controls Elements
const screenOnBtn = document.getElementById("screen-on-btn");
const screenOffBtn = document.getElementById("screen-off-btn");
const volumeSlider = document.getElementById("volume-slider");
const volumeVal = document.getElementById("volume-val");
const volumeMuteBtn = document.getElementById("volume-mute-btn");
const ttsText = document.getElementById("tts-text");
const ttsSendBtn = document.getElementById("tts-send-btn");
const reloadWebviewBtn = document.getElementById("reload-webview-btn");
const toast = document.getElementById("toast");

// Settings elements
const kioskUrlInput = document.getElementById("kiosk-url-input");
const kioskSslCheckbox = document.getElementById("kiosk-ssl-checkbox");
const kioskPinCheckbox = document.getElementById("kiosk-pin-checkbox");
const saveSettingsBtn = document.getElementById("save-settings-btn");

// Initialize
document.addEventListener("DOMContentLoaded", () => {
    applyLanguage(currentLang);

    if (loginLangBtn) loginLangBtn.addEventListener("click", toggleLanguage);
    if (dashLangBtn) dashLangBtn.addEventListener("click", toggleLanguage);

    const savedPassword = localStorage.getItem("kiosk_api_password");
    if (savedPassword) {
        apiPassword = savedPassword;
        testConnection(savedPassword);
    } else {
        showLogin();
    }
});

// Event Listeners
loginBtn.addEventListener("click", performLogin);
passwordInput.addEventListener("keypress", (e) => {
    if (e.key === "Enter") performLogin();
});
logoutBtn.addEventListener("click", performLogout);

screenOnBtn.addEventListener("click", () => sendCommand("/api/screen/on"));
screenOffBtn.addEventListener("click", () => sendCommand("/api/screen/off"));

volumeSlider.addEventListener("input", (e) => {
    volumeVal.textContent = e.target.value + "%";
});
volumeSlider.addEventListener("change", (e) => {
    sendCommand("/api/volume", { volume: parseInt(e.target.value) });
});
volumeMuteBtn.addEventListener("click", () => {
    volumeSlider.value = 0;
    volumeVal.textContent = "0%";
    sendCommand("/api/volume", { volume: 0 });
});

ttsSendBtn.addEventListener("click", () => {
    const text = ttsText.value.trim();
    const dict = translations[currentLang] || translations.de;
    if (!text) {
        showToast(dict.toast_tts_empty, "error");
        return;
    }
    sendCommand("/api/tts", { text: text }).then(success => {
        if (success) ttsText.value = "";
    });
});

// Preset tags click
document.querySelectorAll(".preset-tag").forEach(tag => {
    tag.addEventListener("click", () => {
        const phrase = currentLang === "en" ? 
            (tag.getAttribute("data-phrase-en") || tag.getAttribute("data-phrase-de")) : 
            tag.getAttribute("data-phrase-de");
        ttsText.value = phrase;
        sendCommand("/api/tts", { text: phrase }).then(success => {
            if (success) ttsText.value = "";
        });
    });
});

reloadWebviewBtn.addEventListener("click", () => sendCommand("/api/webview/reload"));

saveSettingsBtn.addEventListener("click", () => {
    const url = kioskUrlInput.value.trim();
    const ignoreSsl = kioskSslCheckbox.checked;
    const pinProtection = kioskPinCheckbox.checked;
    const dict = translations[currentLang] || translations.de;
    sendCommand("/api/settings", { dashboardUrl: url, ignoreSslErrors: ignoreSsl, pinProtectionEnabled: pinProtection })
        .then(success => {
            if (success) {
                showToast(dict.toast_settings_saved, "success");
            }
        });
});

// Auth & API
async function performLogin() {
    const pwd = passwordInput.value.trim();
    const dict = translations[currentLang] || translations.de;
    if (!pwd) {
        loginError.textContent = dict.toast_login_error;
        return;
    }

    loginBtn.disabled = true;
    loginBtn.textContent = "...";
    loginError.textContent = "";

    try {
        const response = await fetch("/api/device/info", {
            headers: { "X-Kiosk-Password": pwd }
        });

        if (response.ok) {
            apiPassword = pwd;
            localStorage.setItem("kiosk_api_password", pwd);
            showDashboard();
            loadSettings();
            startPolling();
        } else {
            loginError.textContent = dict.toast_login_error;
        }
    } catch (e) {
        loginError.textContent = dict.toast_login_error;
    } finally {
        loginBtn.disabled = false;
        loginBtn.textContent = dict.login_btn;
    }
}

async function testConnection(pwd) {
    try {
        const response = await fetch("/api/device/info", {
            headers: { "X-Kiosk-Password": pwd }
        });
        if (response.ok) {
            showDashboard();
            loadSettings();
            startPolling();
        } else {
            showLogin();
        }
    } catch (e) {
        showLogin();
    }
}

function performLogout() {
    apiPassword = "";
    localStorage.removeItem("kiosk_api_password");
    stopPolling();
    passwordInput.value = "";
    showLogin();
}

function showLogin() {
    loginScreen.classList.remove("hidden");
    dashboardPanel.classList.add("hidden");
    passwordInput.focus();
}

function showDashboard() {
    loginScreen.classList.add("hidden");
    dashboardPanel.classList.remove("hidden");
}

// API Commands
async function sendCommand(endpoint, data = null) {
    const dict = translations[currentLang] || translations.de;
    try {
        const options = {
            method: "POST",
            headers: {
                "X-Kiosk-Password": apiPassword,
                "Content-Type": "application/json"
            }
        };
        if (data) {
            options.body = JSON.stringify(data);
        }

        const response = await fetch(endpoint, options);
        if (response.ok) {
            showToast(dict.toast_cmd_success, "success");
            fetchStatus();
            return true;
        } else if (response.status === 401) {
            performLogout();
            showToast(dict.toast_login_error, "error");
            return false;
        } else {
            showToast(dict.toast_cmd_error, "error");
            return false;
        }
    } catch (e) {
        showToast(dict.toast_cmd_error + ": " + e.message, "error");
        return false;
    }
}

async function loadSettings() {
    try {
        const response = await fetch("/api/device/info", {
            headers: { "X-Kiosk-Password": apiPassword }
        });
        if (response.ok) {
            const data = await response.json();
            if (data.dashboardUrl) kioskUrlInput.value = data.dashboardUrl;
            if (typeof data.ignoreSslErrors !== "undefined") kioskSslCheckbox.checked = data.ignoreSslErrors;
            if (typeof data.pinProtectionEnabled !== "undefined") kioskPinCheckbox.checked = data.pinProtectionEnabled;
        }
    } catch (e) {
        console.error("Failed to load settings", e);
    }
}

async function fetchStatus() {
    try {
        const response = await fetch("/api/device/info", {
            headers: { "X-Kiosk-Password": apiPassword }
        });

        if (response.ok) {
            const data = await response.json();
            updateUI(data);
        } else if (response.status === 401) {
            performLogout();
        }
    } catch (e) {
        console.warn("Polling error:", e);
    }
}

function updateUI(data) {
    // Screen state
    if (data.isScreenOn) {
        statScreen.textContent = currentLang === "de" ? "AN" : "ON";
        statScreen.className = "badge badge-success";
    } else {
        statScreen.textContent = currentLang === "de" ? "AUS" : "OFF";
        statScreen.className = "badge badge-danger";
    }

    // Battery
    if (typeof data.batteryLevel !== "undefined") {
        statBattery.textContent = data.batteryLevel + "%";
        batteryLevelBar.style.width = data.batteryLevel + "%";
        if (data.batteryLevel <= 20) {
            batteryLevelBar.style.background = "var(--danger)";
        } else if (data.batteryLevel <= 50) {
            batteryLevelBar.style.background = "var(--warning)";
        } else {
            batteryLevelBar.style.background = "var(--success)";
        }
    }

    if (data.isCharging) {
        chargingIcon.classList.remove("hidden");
    } else {
        chargingIcon.classList.add("hidden");
    }

    // RAM
    if (typeof data.freeMemoryMb !== "undefined") {
        statRam.textContent = data.freeMemoryMb + " MB";
    }

    // Device Model
    if (data.model) {
        statModel.textContent = data.model;
    }

    // Volume Slider
    if (typeof data.volumePercent !== "undefined") {
        if (document.activeElement !== volumeSlider) {
            volumeSlider.value = data.volumePercent;
            volumeVal.textContent = data.volumePercent + "%";
        }
    }
}

function startPolling() {
    fetchStatus();
    pollingTimer = setInterval(fetchStatus, 3000);
}

function stopPolling() {
    if (pollingTimer) {
        clearInterval(pollingTimer);
        pollingTimer = null;
    }
}

function showToast(msg, type = "info") {
    toast.textContent = msg;
    toast.className = "toast " + type;
    toast.classList.remove("hidden");

    setTimeout(() => {
        toast.classList.add("hidden");
    }, 3000);
}
