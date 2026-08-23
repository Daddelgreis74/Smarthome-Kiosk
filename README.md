# Neo Kiosk 📱
> A highly optimized, futuristic Android kiosk app for the perfect integration of your smart home dashboard, featuring full J.A.R.V.I.S. voice assistant support.

![Neo Kiosk Preview](preview.jpg)

**Neo Kiosk** transforms any Android tablet into a dedicated control unit for your smart home. It was specifically designed to bypass the typical restrictions and security blocks found in standard Android WebViews (such as Google Chrome or Fully Kiosk) when using local dashboards.

---

## 🌟 Why Neo Kiosk? (The Benefits)

Standard browsers and WebViews block many features essential for a sophisticated smart home dashboard. **Neo Kiosk solves these issues natively:**

*   **🎙️ J.A.R.V.I.S. Voice Recognition Bridge:** By default, Android WebViews lack support for browser-based voice recognition (`webkitSpeechRecognition`). Neo Kiosk automatically injects a JavaScript interface that intercepts these calls and tunnels them to the **Android tablet's native voice recognition service**.
*   **🔊 ElevenLabs & Audio Blob Fix:** WebViews block the playback of audio blobs (`blob:http://...`) via HTML5 audio elements because the native system player cannot access the browser's memory. Neo Kiosk intercepts these audio objects, converts them to Base64, and plays them smoothly using the native Android `MediaPlayer`.
*   **🗣️ Local Speech Output (TTS Bridge):** Web-based speech synthesis (`window.speechSynthesis`) is often silent or disabled in WebViews. Our app tunnels all read-aloud commands directly to Android's native text-to-speech engine. *   **🔒 Handling Self-Signed Certificates:** If the dashboard runs via HTTPS using a self-signed certificate (`AUTO_SSL=true`), WebView silently aborts the loading process. Neo Kiosk offers an optional setting to ignore SSL errors.
*   **🎵 Mixed Content Audio Streaming:** Allows playback of unencrypted HTTP streams (e.g., Fritzbox internet radio or WDR 2) on an encrypted HTTPS dashboard.
*   **📷 Native Motion Detection:** The app uses the front-facing camera to analyze movement in front of the tablet and automatically wake the display from standby (without requiring external motion sensors).

---

## 🛠️ Feature Overview

### 1. Tablet User Interface
*   **Full-Screen WebView:** No distracting system bars or status bars.
*   **Swipe Gesture for Settings:** Swiping from the left edge of the screen toward the center opens the configuration menu.
*   **PIN Protection:** The settings menu can optionally be locked with a PIN (default: `1234`) to prevent unauthorized access.
*   **Close App:** A button in the settings menu allows you to exit kiosk mode and cleanly close the app.
*   **Direct Reload:** Refresh the dashboard directly via the menu.
*   **In-App Update:** Automatically checks for newer versions in the background (or via a button click in the settings) using the GitHub Releases API, downloads the APK with a progress bar, and initiates the native installation.

### 2. Remote Administration (Web Interface)
The app launches a local web server (port `8080`) that allows for convenient tablet management from a PC:
*   **Live Status:** Displays battery level, charging status, available RAM, and display status.
*   **Display Control:** Remotely turn the screen on or off. *   **Volume Control:** Control the tablet's system volume.
*   **Text-to-Speech Console:** Send text for the tablet to read aloud.
*   **Configuration:** Change the dashboard URL or SSL mode remotely.

---

## 🚀 Installation & Getting Started

1.  Download the latest **`neo-kiosk.apk`** from the [GitHub Releases](https://github.com/Daddelgreis74/smarthome-kiosk/releases) page.
2.  Install the app on your Android tablet (enable "Install from unknown sources" if necessary).
3.  Upon first launch, grant permissions for the **Camera** (for motion detection) and **Microphone** (for J.A.R.V.I.S.).
4.  **Using Remote Management:**
*   Open a browser on your PC and navigate to: `http://<tablet-ip>:8080/`
*   Log in using the default password **`admin`**. 
*   Enter your dashboard URL and click "Save Settings." The tablet will immediately load your dashboard automatically!

---

## 🛡️ Permissions & Security
The app requires the following Android permissions:
*   `RECORD_AUDIO`: For J.A.R.V.I.S. voice recognition.
*   `CAMERA`: For local motion detection (images are analyzed only in RAM and **never** saved or transmitted).
*   `SYSTEM_ALERT_WINDOW` (Display over other apps): For the debug camera preview.
*   `WRITE_SETTINGS` / `DEVICE_POLICY_MANAGER`: To put the tablet display to sleep at the hardware level.
*   `REQUEST_INSTALL_PACKAGES`: To allow the app to pass the downloaded APK update to the system installer.
