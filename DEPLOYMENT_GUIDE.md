# SAM: Deployment, Testing & Verification Guide
### Target: realme P4 5G (Android 15 / realme UI 6.0)

This guide provides instructions to compile, test, verify, and run SAM on an Android device or emulator.

---

## 1. Prerequisites

- **Host Machine:** Windows 10/11, macOS, or Linux.
- **JDK:** Java Development Kit 17 (Temurin 17+).
- **Android SDK:**
  - Android SDK Platform 35 (Android 15)
  - Android Build-Tools 35.0.0+
- **Physical Device:** realme P4 5G with USB Debugging enabled, or Android 15 Emulator.

---

## 2. Compiling the Project

Ensure `JAVA_HOME` is pointed to JDK 17:

```powershell
# Set JAVA_HOME (Windows example)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot"

# Verify Gradle Wrapper
.\gradlew.bat --version

# Build Debug APK
.\gradlew.bat assembleDebug
```

The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 3. Installation onto realme P4 5G

Connect your device via USB:

```bash
# Verify ADB connection
adb devices

# Install APK onto the device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 4. Initial Assistant Setup on Device

1. **Launch SAM:** Open the "SAM Assistant" app from your launcher.
2. **Complete Onboarding:** Review the 100% offline, privacy-first principles.
3. **Grant Required Permissions:**
   - **Microphone:** Required for local wake-word and speech recognition.
   - **Contacts & Phone (Optional):** Required for voice-controlled calling.
   - **SMS (Optional):** Required for composing and sending SMS.
   - **Camera (Optional):** Required for torch / flashlight toggle.
   - **Notifications:** Required for reminder alarms on Android 13+.
4. **Set as Default Assistant:**
   - Go to device **Settings > Apps > Default Apps > Digital Assistant App**.
   - Select **SAM Voice Assistant**.
   - SAM is now linked to Android's native `VoiceInteractionService` and can be invoked with system assist gestures (e.g. corner swipe or long-press power).

---

## 5. End-to-End Voice Testing Matrix

| Test Case | Spoken Command | Expected SAM Reaction |
|---|---|---|
| **Wake Word** | *"Hey SAM"* | SAM responds: *"Yes, I'm listening."* Voice Orb pulses cyan. |
| **App Launch** | *"Open YouTube"* | SAM speaks: *"Opening YouTube."* and launches YouTube. |
| **App Launch** | *"Open WhatsApp"* | SAM speaks: *"Opening WhatsApp."* and launches WhatsApp. |
| **Flashlight Toggle** | *"Turn on flashlight"* | SAM turns on camera torch and speaks: *"Flashlight turned on."* |
| **Battery Check** | *"Check battery level"* | SAM reads real-time battery percentage and charging state. |
| **Time & Date** | *"What time is it?"* | SAM reads current local time and date. |
| **Call (Confirmation)** | *"Call John"* | SAM speaks confirmation prompt: *"Are you sure you want to call John?"* |
| **Alarm** | *"Set alarm for 7:30 am"* | SAM sets exact alarm via system clock. |
| **Reminder** | *"Remind me to buy groceries in 10 minutes"* | SAM schedules local alarm and saves to database. |
| **Personal Memory** | *"Remember that my project name is ARES"* | SAM encrypts and stores memory: *"I've remembered that project name is ARES."* |
| **Memory Retrieval** | *"What do you remember about project?"* | SAM retrieves and speaks: *"project name: ARES"*. |
| **Lock-State Security** | *Turn screen off / Lock phone* | Microphone hardware is closed immediately. Say *"Hey SAM"* — no reaction occurs. |
| **Unlock Resumption** | *Unlock with PIN / Biometrics* | Standby returns. Saying *"Hey SAM"* responds normally. |

---

## 6. Privacy & Memory Center Verification

1. Navigate to the **SAM PRIVACY & MEMORY CENTER** in the app.
2. View **Personal Memory**: Confirm that your saved memories are displayed with the "Keystore Encrypted" badge.
3. Tap **Export All Data as JSON**: Verify the generated JSON contains all memories, conversation entries, and command logs.
4. Test **Nuclear Data Wipe**: Confirm that all data is permanently wiped from the Room database upon user confirmation.

