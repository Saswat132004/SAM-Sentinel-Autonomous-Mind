# SAM: Secure Autonomous Mobile Assistant
### Production-Grade On-Device Voice Assistant for Android

**Target Device:** realme P4 5G (MediaTek Dimensity 7400 Ultra, Android 15 / realme UI 6.0)

SAM (Secure Autonomous Mobile Assistant) is a voice-controlled personal smartphone assistant engineered for privacy, minimal resource consumption, and offline-first autonomy.

---

## 1. Architectural Highlights

- **Voice-First Hands-Free Activation:**
  - Standby detection for the wake phrase **"HEY SAM"**.
  - Immediate acoustic acknowledge: *"Yes, I'm listening."*
  - Dual-engine speech processing: Offline ASR pipeline + Text-to-Speech feedback.

- **Mandatory Lock-State Security:**
  - Immediate termination of microphone hardware handles when device is locked.
  - Zero private notifications or background access in locked state.
  - Automated resumption when unlocked via PIN or biometrics.

- **Dual-Path Local AI Engine:**
  - **Path A (Fast Command Engine):** Deterministic pattern matcher executing common tasks (App launches, calls, flashlight, alarms, battery, reminders, volume) in `< 10ms` without loading neural networks.
  - **Path B (Local AI Engine):** Compact GGUF quantized models (SmolLM2-360M, Qwen2.5-0.5B, Gemma-2-2B) for multi-step reasoning, contextual chat, and memory retrieval.

- **Transparent Personal Memory Center:**
  - Local SQLite database managed via Room.
  - Sensitive information encrypted with **Android KeyStore AES-256-GCM**.
  - Full user inspection, search, editing, JSON export, and nuclear wipe capability.

- **Native System Integration:**
  - Full Android `VoiceInteractionService` and `RoleManager.ROLE_ASSISTANT` support.
  - System setting deep-links and optional accessibility gestures.

---

## 2. Technology Stack

| Layer | Implementation |
|---|---|
| **Language** | Kotlin 2.0 |
| **UI System** | Jetpack Compose (Material 3 Dark Theme) |
| **Architecture** | Clean Architecture + MVVM |
| **Local Database** | Room Database (SQLite) |
| **Encryption** | Android KeyStore (AES-256-GCM / EncryptedSharedPreferences) |
| **Wake-Word** | Energy-Acoustic Cadence Detector ("Hey SAM") |
| **Speech Recognition**| Native Android Offline `SpeechRecognizer` (`RecognizerIntent.EXTRA_PREFER_OFFLINE`) |
| **Text-to-Speech** | Android Local `TextToSpeech` Engine |
| **Inference Format**| GGUF 4-bit Quantization (llama.cpp compliant) |
| **Min / Target SDK**| Min SDK 26 (Android 8.0) / Target SDK 35 (Android 15) |

---

## 3. Directory Layout

```
SAM/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/sam/assistant/
│   │   │   ├── SamApplication.kt
│   │   │   ├── core/
│   │   │   │   ├── common/             # Resource, Result types
│   │   │   │   ├── security/           # KeystoreManager, LockStateManager, LockStateReceiver
│   │   │   │   ├── permissions/        # PermissionManager, RoleManager intents
│   │   │   │   └── diagnostics/        # Hardware detection, latency gauges
│   │   │   ├── voice/
│   │   │   │   ├── wakeword/           # WakeWordDetector ("Hey SAM")
│   │   │   │   ├── recognition/        # OfflineSpeechRecognizer, SamRecognitionService
│   │   │   │   ├── tts/                # TextToSpeechEngine
│   │   │   │   └── interaction/        # SamAssistantService, VoiceInteractionService
│   │   │   ├── ai/
│   │   │   │   ├── intent/             # FastCommandEngine (Path A)
│   │   │   │   ├── models/             # ModelConfig, ModelManager
│   │   │   │   ├── inference/          # LocalAiEngine (Path B)
│   │   │   │   └── planner/            # TaskPlanner (Multi-step execution)
│   │   │   ├── automation/
│   │   │   │   ├── apps/               # AppLauncher
│   │   │   │   ├── calls/              # CallManager (with user confirmation)
│   │   │   │   ├── messages/           # MessageManager (SMS & WhatsApp)
│   │   │   │   ├── device/             # Flashlight, Battery, Volume, Settings, Accessibility
│   │   │   │   ├── reminders/          # ReminderScheduler, AlarmManager receivers
│   │   │   │   └── executor/           # ActionExecutor, CommandSecurityValidator
│   │   │   ├── memory/
│   │   │   │   ├── model/              # MemoryEntity, ConversationEntity, CommandLogEntity
│   │   │   │   ├── database/           # SamDatabase, Room DAOs
│   │   │   │   ├── repository/         # MemoryRepository (Keystore-backed)
│   │   │   │   └── manager/            # PersonalMemoryManager (Natural language parser)
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt
│   │   │       ├── theme/              # Dark Theme, Colors (#090D18, #111827, #6366F1, #8B5CF6)
│   │   │       ├── components/         # VoiceOrb, MetricCard, GlassCard, ConfirmationDialog
│   │   │       ├── screens/            # 12 Screens (Dashboard, Privacy Center, Diagnostics, etc.)
│   │   │       └── navigation/         # SamNavHost
│   │   └── res/                        # Vector drawables, colors, assistant XML metadata
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml              # Version catalog
│   └── wrapper/
└── settings.gradle.kts
```

---

## 4. Activation Flow

```
[Phone Unlocked (PIN/Biometric)]
              │
              ▼
   [SAM Enters Standby] ◄─── (Mic monitored locally)
              │
              ▼ User says: "Hey SAM"
  [Acoustic Cadence Matched]
              │
              ▼ SAM Speaks: "Yes, I'm listening."
    [Offline SpeechRecognizer]
              │
              ▼ User gives command
    [Task Planning & Parsing]
              │
     ┌────────┴────────┐
     ▼                 ▼
[Path A: Fast Path]  [Path B: Local LLM]
(< 10ms Regex/Map)   (SmolLM2 / Qwen GGUF)
     │                 │
     └────────┬────────┘
              ▼
  [Command Security Validator]
  (Allowlist + Confirmation Gate)
              │
              ▼
   [Android Action Executor]
   (App, Call, Torch, Reminder, etc.)
              │
              ▼
   [Text-to-Speech Response]
              │
              ▼
   [Return to Standby Mode]
```

---

## 5. Privacy & Memory Center

The dedicated **SAM PRIVACY & MEMORY CENTER** provides complete transparency:
- **Personal Memory:** View, search, edit, and delete permanent facts remembered by SAM.
- **Conversation History:** Full chat log with individual deletion and clear-all controls.
- **Command History:** Audit trail of all automation operations with timestamps and status codes.
- **Voice Data Policy:** Raw audio is never saved. Audio buffers are discarded immediately.
- **Data Export:** Export all local data to a formatted JSON document.
- **Nuclear Data Wipe:** One-tap cryptographic erasure of all stored user records.

---

## 6. How to Build & Install

```bash
# 1. Clone repository or open in Android Studio Hedgehog / Iguana / Koala / Ladybug
cd SAM

# 2. Build debug APK using Gradle Wrapper
./gradlew assembleDebug

# 3. Output APK will be located at:
# app/build/outputs/apk/debug/app-debug.apk

# 4. Install onto realme P4 5G via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

