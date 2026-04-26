# HackerLauncher

A hacker-themed Android launcher with built-in security tools, live wallpapers, and AI chat.

## Features

### Live Wallpaper
- **Matrix Rain** - Classic falling Katakana/digit characters
- **Glitch Effect** - Digital corruption and screen tearing
- **CRT Scanlines** - Retro monitor effect with vignette
- **Particle Network** - Connected nodes with physics
- **Hex Fall** - Falling hex codes
- Customizable: speed (24-60fps), density (low/medium/high), color (green/red/blue/amber/purple)

### Launcher Core
- Custom home screen replacing default launcher
- Dark/AMOLED theme with neon green hacker accents
- Swipe gestures (up/down/left/right) to open modules
- Terminal widget on home screen with live log

### Tool Modules

| Module | Features |
|--------|----------|
| **Terminal** | Full terminal emulator, Termux bridge, persistent sessions |
| **Network** | WiFi scanner, ARP table, DNS lookup, ping, port scanner, netstat |
| **OSINT** | Username search (15 platforms), email breach check, phone lookup, domain recon, IP geolocation |
| **Crypto** | MD5/SHA-1/SHA-256/SHA-512 hash, Base64, XOR, Caesar cipher, password strength, random password |
| **Web Testing** | HTTP GET/POST, header inspection, URL encode/decode, SQLi/XSS demo payloads, directory brute-force |
| **Anonymity** | VPN status, DNS info/change, public IP check, proxy info, Tor check |
| **File Management** | File explorer, text viewer, AES-256 encryption/decryption, secure delete (3-pass overwrite) |
| **Automation** | Macro recorder/playback, task scheduler (AlarmManager), auto-recon, notification logger |
| **AI Chat** | Google/GitHub OAuth (Firebase Auth), OpenAI/Gemini API support, offline mock mode, Room chat history |

### Persistence & Background
- Foreground sticky service (`START_STICKY`)
- Auto-start on boot
- Overlay permission support
- Wake lock management
- WorkManager periodic tasks (15 min intervals)
- Battery optimization bypass request

### Permissions
All permissions declared and requested at runtime:
- Network (INTERNET, WiFi state, change WiFi)
- Location (fine, coarse, background)
- Bluetooth (classic, scan, connect)
- Storage (read, write, manage external)
- System (foreground service, overlay, boot, wake lock, wallpaper, biometric, notifications)

## Tech Stack
- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM with ViewModels
- **Database**: Room
- **Auth**: Firebase UI (Google + GitHub)
- **Network**: Retrofit + OkHttp
- **Background**: WorkManager + AlarmManager
- **UI**: Material Design Components, TabLayout, RecyclerView

## Project Structure
```
HackerLauncher/
├── .github/workflows/build-and-release.yml
├── app/
│   ├── src/main/
│   │   ├── java/com/hackerlauncher/
│   │   │   ├── HackerApp.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── livewallpaper/    (5 files)
│   │   │   ├── services/         (5 files)
│   │   │   ├── modules/          (9 files)
│   │   │   ├── auth/             (2 files)
│   │   │   ├── chat/             (4 files)
│   │   │   ├── utils/            (5 files)
│   │   │   └── widgets/          (1 file)
│   │   ├── res/                  (layouts, drawables, values, xml)
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── SETUP.md
└── README.md
```

## Disclaimer

**This application is for EDUCATIONAL and AUTHORIZED TESTING purposes only.**

- Only use these tools on systems you own or have explicit permission to test
- Unauthorized access to computer systems is illegal in most jurisdictions
- The developers are not liable for any misuse or damage caused
- A disclaimer is shown on first launch and must be accepted before use

## License

This project is provided as-is for educational purposes.
