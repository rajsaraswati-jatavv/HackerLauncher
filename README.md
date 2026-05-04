<div align="center">

# 🖥️ HackerLauncher

**A hacker-themed Android launcher with built-in security tools, live wallpapers, and AI chat**

[![Platform](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-orange)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-34%20(Android%2014)-blue)](https://developer.android.com)
[![Version](https://img.shields.io/badge/Version-7.0.0-red)](https://github.com/T3RMUXK1NG/HackerLauncher/releases)
[![YouTube](https://img.shields.io/badge/YouTube-T3rmuxk1ng-red?logo=youtube)](https://youtube.com/@T3rmuxk1ng)
[![License](https://img.shields.io/badge/License-Educational-yellow)](./LICENSE)

*Built with 💚 by [T3rmuxk1ng](https://youtube.com/@T3rmuxk1ng)*

</div>

---

## 🎬 Demo & Tutorials

📺 **Watch on YouTube**: [https://youtube.com/@T3rmuxk1ng](https://youtube.com/@T3rmuxk1ng)

Subscribe for hacking tool tutorials, Android security walkthroughs, and exclusive tool demos!

---

## ✨ Features

### 🎨 Live Wallpaper Engine
- **Matrix Rain** — Classic falling Katakana/digit characters
- **Glitch Effect** — Digital corruption and screen tearing
- **CRT Scanlines** — Retro monitor effect with vignette
- **Particle Network** — Connected nodes with physics simulation
- Customizable: speed (24-60fps), density (low/medium/high), color (green/red/blue/amber/purple)

### 🏠 Launcher Core
- Custom home screen replacing default launcher
- Dark/AMOLED theme with neon green hacker accents
- Swipe gestures (up/down/left/right) to open modules
- Terminal widget on home screen with live log
- App drawer with grid/list view
- Dock bar for favorite apps
- Gesture manager with custom actions

### 🔧 84+ Tool Modules

| Category | Modules |
|----------|---------|
| **Terminal** | Full terminal emulator, Termux bridge, persistent sessions |
| **Network** | WiFi scanner, ARP table, DNS lookup, ping, port scanner, netstat, subnet scanner |
| **OSINT** | Username search (15 platforms), email breach check, phone lookup, domain recon, IP geolocation |
| **Crypto** | MD5/SHA-1/SHA-256/SHA-512 hash, Base64, XOR, Caesar cipher, password strength, random password |
| **Web Testing** | HTTP GET/POST, header inspection, URL encode/decode, SQLi/XSS demo payloads, directory brute-force |
| **Anonymity** | VPN status, DNS info/change, public IP check, proxy info, Tor check |
| **File Management** | File explorer, text viewer, AES-256 encryption/decryption, secure delete (3-pass overwrite) |
| **Automation** | Macro recorder/playback, task scheduler (AlarmManager), auto-recon, notification logger |
| **AI Chat** | Google/GitHub OAuth (Firebase Auth), OpenAI/Gemini API support, offline mock mode, Room chat history |
| **System Tools** | RAM cleaner, junk cleaner, cache cleaner, storage analyzer, battery optimizer |
| **Security** | AdBlocker, privacy guard, secure vault, anti-theft, panic button, intruder selfie |
| **Scanners** | Network scanner, port scanner, SIP scanner, deep link scanner, honeypot detector, Bluetooth scanner |
| **Utilities** | Calculator, compass, flashlight, QR scanner, screen recorder, audio recorder, sensor box |

### ⚙️ Persistence & Background Services
- Foreground sticky service (`START_STICKY`)
- Auto-start on boot
- Overlay permission support
- Wake lock management
- WorkManager periodic tasks (15 min intervals)
- Battery optimization bypass request

### 🔐 Authentication
- Firebase Auth (Google + GitHub OAuth with graceful fallback)
- Biometric lock (fingerprint/face unlock)
- PIN lock for app protection

---

## 🛠️ Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 34 (Android 14) |
| Architecture | MVVM with ViewModels |
| Database | Room |
| Auth | Firebase UI (Google + GitHub) |
| Network | Retrofit + OkHttp |
| Background | WorkManager + AlarmManager |
| UI | Material Design Components, TabLayout, RecyclerView |
| Image Loading | Glide |
| Animations | Lottie |
| Camera | CameraX + ML Kit Barcode Scanning |

---

## 📁 Project Structure

```
HackerLauncher/
├── .github/workflows/build-and-release.yml
├── app/
│   ├── src/main/
│   │   ├── java/com/hackerlauncher/
│   │   │   ├── HackerApp.kt           # Application class
│   │   │   ├── MainActivity.kt        # Entry point
│   │   │   ├── livewallpaper/         # 5 files - wallpaper engine
│   │   │   ├── services/              # 14 files - background services
│   │   │   ├── modules/               # 50+ files - tool fragments
│   │   │   ├── launcher/              # 16 files - launcher core
│   │   │   ├── auth/                  # 2 files - Firebase auth
│   │   │   ├── chat/                  # 4 files - AI chat
│   │   │   ├── utils/                 # 5 files - utilities
│   │   │   └── widgets/               # 2 files - home screen widgets
│   │   ├── res/                       # layouts, drawables, values, xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── SETUP.md
└── README.md
```

---

## 🚀 Installation

### From Release APK
1. Download the latest APK from [Releases](https://github.com/T3RMUXK1NG/HackerLauncher/releases)
2. Enable **Install from Unknown Sources** on your Android device
3. Open the APK file and install
4. Set HackerLauncher as your default home app

### Build from Source
```bash
git clone https://github.com/T3RMUXK1NG/HackerLauncher.git
cd HackerLauncher
./gradlew assembleDebug
```
The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

> 📖 See [SETUP.md](./SETUP.md) for detailed setup with Firebase, CI/CD, and development environment.

---

## 📖 Usage

1. **Set as Default Launcher** — Press Home and select HackerLauncher
2. **Grant Permissions** — Allow all requested permissions for full functionality
3. **Explore Modules** — Swipe to access tools from the home screen
4. **Terminal** — Tap the terminal widget or install Termux for full terminal
5. **AI Chat** — Open Chat tab (offline mock mode works without API key)
6. **Live Wallpaper** — Long-press home screen > Wallpapers > HackerLauncher

---

## 🤝 Contributing

Contributions are welcome! Please read our [Contributing Guidelines](./.github/README.md) for details.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## ⚠️ Disclaimer

**This application is for EDUCATIONAL and AUTHORIZED TESTING purposes only.**

- Only use these tools on systems you own or have explicit permission to test
- Unauthorized access to computer systems is illegal in most jurisdictions
- The developers are not liable for any misuse or damage caused
- A disclaimer is shown on first launch and must be accepted before use

---

## 📺 YouTube

📺 **T3rmuxk1ng** — [https://youtube.com/@T3rmuxk1ng](https://youtube.com/@T3rmuxk1ng)

Subscribe for:
- Hacking tool tutorials & demos
- Android security walkthroughs
- Cybersecurity tips & tricks
- Exclusive tool releases

---

<div align="center">

**Built with 💚 by [T3rmuxk1ng](https://youtube.com/@T3rmuxk1ng)**

⭐ If you like this project, give it a star!

</div>
