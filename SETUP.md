# HackerLauncher - Setup Guide

This guide walks you through setting up the development environment, configuring Firebase, and building the APK using GitHub Actions.

## Prerequisites

- Android Studio (latest stable version recommended)
- JDK 17
- Android SDK with API level 34
- A Firebase project (for Auth features)
- GitHub account (for CI/CD builds)

## Step 1: Firebase Project Setup

### 1.1 Create Firebase Project
1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Create a project"
3. Name it `HackerLauncher` (or any name you prefer)
4. Enable Google Analytics (optional)
5. Click "Create project"

### 1.2 Add Android App
1. In Firebase Console, click the Android icon to add a new app
2. Package name: `com.hackerlauncher`
3. App nickname: `HackerLauncher`
4. SHA-1: Generate using:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
5. Click "Register app"

### 1.3 Download google-services.json
1. Download the `google-services.json` file from Firebase
2. Place it in: `app/google-services.json`

### 1.4 Enable Authentication Providers
1. In Firebase Console, go to **Authentication > Sign-in method**
2. Enable **Google** provider
   - Add your support email
   - Add the SHA-1 from step 1.2
3. Enable **GitHub** provider
   - Create a GitHub OAuth App at https://github.com/settings/developers
   - Set Authorization callback URL from Firebase Console
   - Copy Client ID and Client Secret to Firebase

## Step 2: GitHub Actions Setup

### 2.1 Enable GitHub Actions
1. Push the project to a GitHub repository
2. Go to the repository's **Actions** tab
3. GitHub will detect the workflow file at `.github/workflows/build-and-release.yml`

### 2.2 Add Firebase Secret (Optional)
1. Go to **Settings > Secrets and variables > Actions**
2. Add a new repository secret:
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: Contents of your `google-services.json` file

### 2.3 Build-Only (Push to Main)
Every push to the `main` branch will:
- Set up JDK 17 and Android SDK
- Build the debug APK
- Upload the APK as a GitHub Actions artifact

### 2.4 Create a Release
1. Go to **Actions > Build & Release**
2. Click "Run workflow"
3. Enter a version number (e.g., `1.0.0`)
4. Click "Run workflow"
5. When complete, find the release under **Releases** with the APK attached

## Step 3: Download and Install APK

### From Artifacts (Debug Builds)
1. Go to **Actions > [your workflow run]**
2. Scroll down to "Artifacts"
3. Download `HackerLauncher-debug`
4. Extract the ZIP to get the APK file

### From Releases
1. Go to **Releases** in your repository
2. Find the latest release
3. Download the APK from the Assets section

### Install on Android
1. Transfer the APK to your Android device
2. Enable **Install from Unknown Sources**:
   - Settings > Security > Unknown Sources (or per-app setting)
3. Open the APK file and install
4. Open the app and grant all requested permissions

## Step 4: Post-Install Configuration

### 4.1 Set as Default Launcher
1. Press the Home button
2. Select "HackerLauncher" as the default launcher
3. Choose "Always" to make it permanent

### 4.2 Grant Permissions
The app will request permissions on first launch. Grant all of them for full functionality:
- Location (for WiFi scanning)
- Storage (for file management)
- Overlay (for floating widget)
- Battery optimization ignore (for background service)

### 4.3 Enable Accessibility Service (Optional)
1. Go to Settings > Accessibility
2. Find "HackerLauncher"
3. Enable it for notification logging and automation features

### 4.4 Set Up Live Wallpaper
1. Long-press the home screen
2. Select "Wallpapers"
3. Choose "HackerLauncher Wallpaper"
4. Customize in Settings > Wallpaper Settings

### 4.5 Configure AI Chat
1. Open the Chat tab
2. For offline mode: Just start chatting (mock responses)
3. For full AI: Go to Settings and enter your API key
   - OpenAI: Get from https://platform.openai.com/api-keys
   - Google Gemini: Get from https://makersuite.google.com/app/apikey

### 4.6 Install Termux (Recommended)
1. Open the Terminal tab
2. Tap "TMUX" button
3. Install Termux from F-Droid (recommended) or Play Store
4. This enables full terminal functionality

## Step 5: Local Development

### Clone and Build
```bash
git clone https://github.com/YOUR_USERNAME/HackerLauncher.git
cd HackerLauncher
```

### Open in Android Studio
1. File > Open > Select the HackerLauncher directory
2. Wait for Gradle sync
3. Place `google-services.json` in `app/` directory
4. Build > Make Project

### Run on Device/Emulator
1. Connect an Android device (with USB debugging) or start an emulator
2. Run > Run 'app'
3. Select your device

### Build APK Manually
```bash
# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

## Troubleshooting

### Build Errors
- **google-services.json missing**: Place it in the `app/` directory or set the `GOOGLE_SERVICES_JSON` GitHub secret
- **SDK not found**: Install Android SDK 34 via Android Studio SDK Manager
- **JDK version**: Ensure JDK 17 is installed and selected in Android Studio

### Runtime Issues
- **Permissions denied**: Grant all permissions manually in Settings > Apps > HackerLauncher > Permissions
- **Launcher not appearing**: Set HackerLauncher as default home app
- **Live wallpaper not showing**: Set it manually from Android wallpaper picker
- **Terminal not working**: Install Termux from F-Droid
- **Chat offline mode**: This is normal without an API key. Configure in Settings.

### Firebase Issues
- **Google Sign-In fails**: Ensure SHA-1 is added to Firebase project settings
- **GitHub Sign-In fails**: Verify OAuth App callback URL matches Firebase
- **Auth UI crash**: Check that `google-services.json` is correctly placed

## Security Notes

- **Never commit** `google-services.json` to a public repository (use GitHub Secrets)
- **API keys** are stored locally in SharedPreferences and never transmitted except to the intended API
- **Encrypted vault** files use AES-256-CBC with a separate key file
- **Secure delete** performs 3-pass random overwrite before deletion
- All network tools are designed for **authorized testing only**
