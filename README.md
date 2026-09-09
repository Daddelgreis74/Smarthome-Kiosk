# Neo Kiosk 📱
> A highly optimized, futuristic Android kiosk app for the seamless integration of your smart home dashboard (Neo Deck) with J.A.R.V.I.S. voice bridge and local remote management.

![Neo Kiosk Preview](preview.jpg)

**Neo Kiosk** transforms any Android tablet into a dedicated, futuristic wall-mounted control terminal for your smart home. It is engineered to bypass standard Android WebView limitations (such as audio blob restrictions, missing speech recognition APIs, and self-signed SSL errors) while offering a sleek **Neo Aurora** glassmorphic interface and a responsive local web remote control.

---

## 🌟 What's New & Changelog

### 🚀 Highlights in Version 2.6:
* **🔢 High-Visibility PIN Entry & Touch Keypad:**
  * Redesigned high-contrast PIN dialog with 4 distinct illuminated digit slots (`[●][●][ ][ ]`).
  * Built-in tactile on-screen keypad (`1–9, C, 0, ⌫`) for effortless PIN entry directly on wall tablets without relying on the Android soft keyboard.
  * Instant auto-unlock upon typing the correct PIN and neon-red warning on wrong attempts.
* **📱 Executive Web Remote Control (Port 8080):**
  * **Top Hero Status Bar:** Instant glance at live display state (ON/STANDBY), battery gauge with charging indicator ⚡, free RAM (MB), and tablet hardware model.
  * **Balanced 2-Column Layout:** Separates hardware controls (Screen Power, WebView Reload, Volume slider with presets `Mute`, `25%`, `50%`, `100%`) from Kiosk settings.
  * **Modern Toggle Switches:** Replaced legacy checkboxes with sleek glassmorphic switches for SSL and PIN protection.
* **🔇 Removed Legacy TTS:** Stripped out unused Text-To-Speech background engine and web console for maximum performance and a streamlined UI.

### 🌐 Added in Version 2.5:
* **Full Bilingual Support (DE / EN):** Complete localization for both the tablet UI (Compose) and the browser-based Web Remote Control Panel.
* **5-Step Onboarding Setup Wizard:** First-launch wizard for language selection, camera/microphone permissions, server connectivity testing, and security setup.
* **In-App Updater:** Automatic update checking via GitHub Releases API, background APK download with progress bar, and native package installer handoff.

---

## 💎 Key Features & Benefits

Standard mobile browsers and standard WebViews block many features essential for a sophisticated smart home dashboard. **Neo Kiosk solves these issues natively:**

* **🎙️ J.A.R.V.I.S. Voice Recognition Bridge:** Android WebViews lack native support for browser-based speech recognition (`webkitSpeechRecognition`). Neo Kiosk injects a JavaScript bridge that tunnels web speech requests directly to the tablet's native Android speech recognizer.
* **🔊 ElevenLabs & Audio Blob Fix:** WebViews block the playback of memory-based audio blobs (`blob:http://...`) through HTML5 audio tags. Neo Kiosk intercepts these audio objects, encodes them, and plays them smoothly via the native Android `MediaPlayer`.
* **🎵 Mixed Content Audio Streaming:** Play unencrypted HTTP audio streams (such as Fritz!Box radio, WDR 2, internet radio) on encrypted HTTPS dashboards without security blocks.
* **🔒 Self-Signed Certificate Bypass:** When running local dashboards with self-signed SSL certificates, standard WebViews abort silently. Neo Kiosk offers an optional setting to seamlessly accept local HTTPS certificates.
* **📷 Native Front-Camera Motion Detection:** Uses the tablet's front-facing camera to detect optical motion in front of the device and automatically wakes the display from standby—no external PIR motion sensors required.
* **🔐 PIN-Protected Settings Menu:** Swipe from the left screen edge to access configuration, secured with a custom PIN and high-contrast numeric keypad.

---

## 🛠️ Components Overview

### 1. Tablet User Interface (Jetpack Compose)
* **Kiosk Lockdown:** Fullscreen mode hiding Android navigation and status bars.
* **Edge Swipe Gesture:** Swiping from the left screen edge opens the settings menu.
* **Integrated Touch Keypad:** Direct PIN entry on the screen with tactile buttons and automatic validation.
* **In-App Auto Update:** One-click update check against GitHub Releases; downloads and installs updates directly on the tablet.
* **Reload & Close:** Dedicated quick buttons to refresh the dashboard or cleanly exit kiosk mode.

### 2. Web Remote Control Panel (`http://<tablet-ip>:8080/`)
Access the tablet remotely from any PC, smartphone, or laptop on the local network:
* **Live Hardware Monitor:** Real-time display status, battery percentage & charging indicator, free memory, and device model.
* **Display Power Control:** Remotely turn the screen ON (☀️) or OFF (🌙).
* **Browser Management:** Trigger a remote reload of the kiosk WebView.
* **Volume Slider & Presets:** Adjust media volume with dedicated buttons (`Mute`, `25%`, `50%`, `100%`).
* **Kiosk Settings Management:** Remotely change the dashboard URL, toggle SSL error bypassing, or adjust PIN protection.
* **Bilingual Switcher:** Instant toggle between German (🇩🇪) and English (🇬🇧).

---

## 🚀 Installation & Quick Start

1. **Download APK:** Download the latest **`neo-kiosk.apk`** from the [GitHub Releases](https://github.com/Daddelgreis74/smarthome-kiosk/releases) page.
2. **Install on Tablet:** Sideload the APK onto your Android tablet (enable *"Install unknown apps"* in Android settings if prompted).
3. **Setup Wizard:** Follow the 5-step setup wizard on first launch:
   - Select language (German / English).
   - Grant **Camera** (for optical motion detection) and **Microphone** (for voice assistant) permissions.
   - Enter your dashboard server URL (e.g. `http://192.168.178.100:8443`).
   - Set up API remote access password and optional PIN lock.
4. **Remote Access:** Open a browser on your PC or smartphone and navigate to:
   ```text
   http://<tablet-ip>:8080/
   ```
   Log in with your configured API password (default: `admin`).

---

## 🛡️ Required Permissions

| Permission | Purpose |
| :--- | :--- |
| `RECORD_AUDIO` | J.A.R.V.I.S. voice assistant speech recognition bridge |
| `CAMERA` | Optical motion detection (frames are processed strictly in RAM and never stored) |
| `SYSTEM_ALERT_WINDOW` | Overlay preview and wake-up display control |
| `WRITE_SETTINGS` / `DEVICE_ADMIN` | Putting display into standby at the hardware level |
| `REQUEST_INSTALL_PACKAGES` | Handing downloaded APK updates to the native system installer |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — free and open source.
