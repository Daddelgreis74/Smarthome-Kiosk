// State
let apiPassword = "";
let pollingTimer = null;
let currentLang = localStorage.getItem("kiosk_web_lang") || "de";

// Dictionary (DE / EN)
const translations = {
    de: {
        login_title: "Kiosk Login",
        login_subtitle: "Bitte gib das Kiosk-API-Passwort ein, um fortzufahren.",
        login_btn: "Anmelden",
        status_connected: "Verbunden",
        logout_btn: "Abmelden",
        
        // Status Hero
        stat_screen: "Display Status",
        stat_battery: "Batterie",
        stat_ram: "Freier RAM",
        stat_model: "Tablet Modell",
        
        // Card 1: Control
        card_display_title: "Display & Steuerung",
        card_display_desc: "Direkte Steuerung von Bildschirm und Browseranzeige.",
        label_screen_power: "Bildschirm Ein / Aus:",
        btn_screen_on_text: "Display Ein",
        btn_screen_off_text: "Display Aus",
        label_webview_action: "Kiosk WebView:",
        btn_reload_text: "WebView neu laden",
        
        // Card 2: Audio
        card_volume_title: "Lautstärke",
        card_volume_desc: "Passe die Medienlautstärke des Tablets an.",
        btn_mute: "Stumm",
        
        // Card 3: Settings
        card_settings_title: "Kiosk Einstellungen",
        card_settings_desc: "Konfiguriere URL und Sicherheit des Tablets.",
        label_dash_url: "Dashboard Server URL:",
        helper_dash_url: "Die Webadresse deines Dashboards im lokalen Netzwerk.",
        label_ignore_ssl: "Selbstsignierte SSL-Fehler ignorieren",
        desc_ignore_ssl: "Erlaubt HTTPS mit lokalen oder selbstausgestellten Zertifikaten.",
        label_pin_protection: "PIN-Schutz für Einstellungen",
        desc_pin_protection: "Sichert das Einstellungsmenü auf dem Tablet mit PIN ab.",
        btn_save_settings: "💾 Einstellungen speichern",
        
        // Feedback Toasts
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
        
        // Status Hero
        stat_screen: "Display Status",
        stat_battery: "Battery",
        stat_ram: "Free RAM",
        stat_model: "Tablet Model",
        
        // Card 1: Control
        card_display_title: "Display & Controls",
        card_display_desc: "Direct controls for screen power and browser view.",
        label_screen_power: "Display Power:",
        btn_screen_on_text: "Screen On",
        btn_screen_off_text: "Screen Off",
        label_webview_action: "Kiosk WebView:",
        btn_reload_text: "Reload WebView",
        
        // Card 2: Audio
        card_volume_title: "Volume",
        card_volume_desc: "Adjust media playback volume of the tablet.",
        btn_mute: "Mute",
        
        // Card 3: Settings
        card_settings_title: "Kiosk Settings",
        card_settings_desc: "Configure dashboard URL and security options.",
        label_dash_url: "Dashboard Server URL:",
        helper_dash_url: "The web address of your dashboard on the local network.",
        label_ignore_ssl: "Ignore Self-Signed SSL Errors",
        desc_ignore_ssl: "Allows HTTPS with local or self-signed certificates.",
        label_pin_protection: "PIN Protection for Settings",
        desc_pin_protection: "Locks the settings menu on the tablet with a PIN.",
        btn_save_settings: "💾 Save Settings",
        
        // Feedback Toasts
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
const headerModel = document.getElementById("header-model");
const chargingIcon = document.getElementById("charging-icon");

// Controls Elements
const screenOnBtn = document.getElementById("screen-on-btn");
const screenOffBtn = document.getElementById("screen-off-btn");
const volumeSlider = document.getElementById("volume-slider");
const volumeVal = document.getElementById("volume-val");
const volumeMuteBtn = document.getElementById("volume-mute-btn");
const vol25Btn = document.getElementById("vol-25-btn");
const vol50Btn = document.getElementById("vol-50-btn");
const vol100Btn = document.getElementById("vol-100-btn");
const reloadWebviewBtn = document.getElementById("reload-webview-btn");
const toast = document.getElementById("toast");

// Settings Elements
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

if (vol25Btn) {
    vol25Btn.addEventListener("click", () => {
        volumeSlider.value = 25;
        volumeVal.textContent = "25%";
        sendCommand("/api/volume", { volume: 25 });
    });
}

if (vol50Btn) {
    vol50Btn.addEventListener("click", () => {
        volumeSlider.value = 50;
        volumeVal.textContent = "50%";
        sendCommand("/api/volume", { volume: 50 });
    });
}

if (vol100Btn) {
    vol100Btn.addEventListener("click", () => {
        volumeSlider.value = 100;
        volumeVal.textContent = "100%";
        sendCommand("/api/volume", { volume: 100 });
    });
}

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
        statScreen.textContent = currentLang === "de" ? "STANDBY" : "STANDBY";
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
        if (headerModel) headerModel.textContent = data.model;
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
