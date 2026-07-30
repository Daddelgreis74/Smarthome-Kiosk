// State
let apiPassword = "";
let pollingTimer = null;

// DOM Elements
const loginScreen = document.getElementById("login-screen");
const dashboardPanel = document.getElementById("dashboard-panel");
const passwordInput = document.getElementById("api-password");
const loginBtn = document.getElementById("login-btn");
const loginError = document.getElementById("login-error");
const logoutBtn = document.getElementById("logout-btn");

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
const saveSettingsBtn = document.getElementById("save-settings-btn");

// Initialize
document.addEventListener("DOMContentLoaded", () => {
    // Check if password is saved in localStorage
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
    volumeVal.textContent = `${e.target.value}%`;
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
    if (!text) {
        showToast("Bitte gib einen Text ein, der vorgelesen werden soll.", "error");
        return;
    }
    sendCommand("/api/tts", { text: text }).then(success => {
        if (success) ttsText.value = "";
    });
});

// Preset tags click
document.querySelectorAll(".preset-tag").forEach(tag => {
    tag.addEventListener("click", () => {
        const phrase = tag.getAttribute("data-phrase");
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
    sendCommand("/api/settings", { dashboardUrl: url, ignoreSslErrors: ignoreSsl })
        .then(success => {
            if (success) {
                showToast("Einstellungen erfolgreich gespeichert und Kiosk neu geladen.", "success");
            }
        });
});

// Functions
function showLogin() {
    loginScreen.classList.remove("hidden");
    dashboardPanel.classList.add("hidden");
    passwordInput.focus();
}

function showDashboard() {
    loginScreen.classList.add("hidden");
    dashboardPanel.classList.remove("hidden");
    startPolling();
}

function performLogin() {
    const enteredPassword = passwordInput.value.trim();
    if (!enteredPassword) {
        loginError.textContent = "Bitte gib ein Passwort ein.";
        return;
    }
    loginError.textContent = "Verbinde...";
    testConnection(enteredPassword).catch(() => {
        loginError.textContent = "Falsches Passwort oder Server nicht erreichbar.";
    });
}

function performLogout() {
    localStorage.removeItem("kiosk_api_password");
    apiPassword = "";
    stopPolling();
    showLogin();
    showToast("Erfolgreich abgemeldet.", "success");
}

async function testConnection(passwordToTest) {
    try {
        const response = await fetch(`/api/device/info?password=${encodeURIComponent(passwordToTest)}`);
        if (response.status === 200) {
            apiPassword = passwordToTest;
            localStorage.setItem("kiosk_api_password", apiPassword);
            showDashboard();
            showToast("Verbindung hergestellt!", "success");
            return true;
        } else if (response.status === 401) {
            throw new Error("401");
        } else {
            throw new Error("Server error");
        }
    } catch (e) {
        localStorage.removeItem("kiosk_api_password");
        showLogin();
        loginError.textContent = e.message === "401" ? "Ungültiges Passwort!" : "Verbindung zum Tablet fehlgeschlagen.";
        throw e;
    }
}

function startPolling() {
    stopPolling();
    fetchStats();
    pollingTimer = setInterval(fetchStats, 3000);
}

function stopPolling() {
    if (pollingTimer) {
        clearInterval(pollingTimer);
        pollingTimer = null;
    }
}

async function fetchStats() {
    try {
        const response = await fetch(`/api/device/info?password=${encodeURIComponent(apiPassword)}`);
        if (response.status === 200) {
            const data = await response.json();
            updateUI(data);
        } else if (response.status === 401) {
            showToast("Sitzung abgelaufen. Bitte erneut anmelden.", "error");
            performLogout();
        }
    } catch (e) {
        console.error("Error polling stats", e);
        // Visual indicator that connection is lost
        document.querySelector(".status-text").textContent = "Verbindungsfehler";
        document.querySelector(".status-indicator .dot").classList.remove("active");
    }
}

function updateUI(data) {
    // Connection indicator
    document.querySelector(".status-text").textContent = "Verbunden";
    document.querySelector(".status-indicator .dot").classList.add("active");

    // Display Status
    if (data.screenOff) {
        statScreen.textContent = "Aus";
        statScreen.className = "badge badge-off";
    } else {
        statScreen.textContent = "An";
        statScreen.className = "badge badge-on";
    }

    // Battery
    const level = data.batteryLevel;
    statBattery.textContent = `${level}%`;
    batteryLevelBar.style.width = `${level}%`;
    
    // Change battery bar color based on charge level
    if (level > 40) {
        batteryLevelBar.style.background = "var(--success)";
    } else if (level > 15) {
        batteryLevelBar.style.background = "var(--warning)";
    } else {
        batteryLevelBar.style.background = "var(--danger)";
    }

    // Charging icon
    if (data.isCharging) {
        chargingIcon.classList.remove("hidden");
    } else {
        chargingIcon.classList.add("hidden");
    }

    // Memory (RAM)
    statRam.textContent = `${data.freeMemoryMb} MB frei`;

    // Model and OS
    statModel.textContent = `${data.model} (Android ${data.androidVersion})`;

    // Populate Settings URL and Checkbox if not active/focused
    if (document.activeElement !== kioskUrlInput) {
        kioskUrlInput.value = data.dashboardUrl || "";
    }
    if (document.activeElement !== kioskSslCheckbox) {
        kioskSslCheckbox.checked = !!data.ignoreSslErrors;
    }
}

async function sendCommand(endpoint, body = {}) {
    try {
        const response = await fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-Kiosk-Password": apiPassword
            },
            body: JSON.stringify(body)
        });

        if (response.status === 200) {
            showToast("Befehl erfolgreich ausgeführt.", "success");
            // Immediately fetch stats to show changed values
            setTimeout(fetchStats, 500);
            return true;
        } else {
            showToast(`Fehler beim Ausführen: Status ${response.status}`, "error");
            return false;
        }
    } catch (e) {
        showToast("Fehler bei der Netzwerkverbindung.", "error");
        return false;
    }
}

function showToast(message, type = "success") {
    toast.textContent = message;
    toast.className = `toast toast-${type}`;
    toast.classList.remove("hidden");

    setTimeout(() => {
        toast.classList.add("hidden");
    }, 3000);
}
