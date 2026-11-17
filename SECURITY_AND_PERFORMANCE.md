# SAM: Security & Performance Architecture Specification
### Device: realme P4 5G (MediaTek Dimensity 7400 Ultra, Android 15 / realme UI 6.0)

This document specifies the technical implementations of the security guarantees and hardware optimizations implemented within the SAM Assistant application.

---

## 1. Lock-State Security Guard

The Android platform security model requires that sensitive personal information and background microphone operations remain strictly governed. SAM enforces a multi-layered lock-state boundary:

### 1.1 State Detection Matrix

| System Event | API Mechanism | Action Triggered |
|---|---|---|
| Screen Turned Off | `Intent.ACTION_SCREEN_OFF` | Immediate pause of `WakeWordDetector`. Release of `AudioRecord` microphone handle. |
| Keyguard Displayed | `KeyguardManager.isKeyguardLocked` | `LockStateManager.assertUnlocked()` fails with `SecurityException`. |
| Screen Turned On (Locked) | `Intent.ACTION_SCREEN_ON` | Standby remains disabled until credentials verified. |
| User Authenticated | `Intent.ACTION_USER_PRESENT` | Standby eligibility restored. SAM returns to passive wake-word detection. |

### 1.2 Defensive Enforcements While Locked
1. **Microphone Access:** Completely revoked. No raw audio sampling or buffer allocation occurs.
2. **Pending Actions:** Any queued operations (e.g., placing a call, composing an SMS) are purged from memory immediately.
3. **Notification Sanitization:** Reminder notifications triggered via `AlarmManager` while locked suppress all private payload text, displaying only generic titles (e.g. *"SAM Reminder - Unlock device to view details"*).
4. **Data Repositories:** Room database reads and Keystore decryption operations reject execution.

---

## 2. Cryptographic Storage Architecture

All persistent personal memories are protected using hardware-backed cryptography:

```
[Plaintext Memory Payload]
          │
          ▼
[KeystoreManager] ───> Queries AndroidKeyStore Provider ("AndroidKeyStore")
          │
          ▼
[Master Key Alias: "SAM_MASTER_MEMORY_KEY"]
  - Algorithm: AES-256
  - Block Mode: GCM (Galois/Counter Mode)
  - Padding: NoPadding
  - IV: 12-byte cryptographically secure random vector
  - Tag Length: 128-bit authentication tag
          │
          ▼
[Encrypted Blob (IV + Ciphertext + Tag)]
          │
          ▼
[Base64 Encoded & Persisted into Room Database (sam_memory.db)]
```

- When the application is backed up or transferred via system mechanisms, `data_extraction_rules.xml` and `backup_rules.xml` strictly exclude `sam_memory.db` and secure preferences from cloud transfer, guaranteeing zero remote leakage.

---

## 3. Command Security & Allowlist Enforcement

To prevent prompt injection, hallucinated executions, or privilege escalation, SAM decouples natural language understanding from execution:

```
Natural Language Input ("Call Sarah", "Turn off flashlight")
                      │
                      ▼
             Intent Classification
                      │
                      ▼
         Structured ActionRequest Object
   (ActionType, Target, Payload, ExtraParams)
                      │
                      ▼
          CommandSecurityValidator
   ├── 1. Lock State Check (Must be unlocked)
   ├── 2. Allowlist Check (Must match authorized enum)
   ├── 3. Android Runtime Permission Check
   └── 4. Sensitive Action Confirmation Check
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
    [Validation Passed]    [Confirmation Required]
          │                       │
          ▼                       ▼
   ActionExecutor           Voice & UI Prompt
 (App, Call, SMS, Torch)  ("Are you sure you want to call Sarah?")
          │                       │
          ▼                       ▼
   Audit Log Saved         User Confirmation ("Yes" / "No")
```

Sensitive operations requiring mandatory explicit confirmation:
- `MAKE_CALL`: Initiating phone calls to contacts or dialed numbers.
- `SEND_SMS`: Sending cellular messages.
- `SEND_WHATSAPP`: Composing and sending instant messages.
- `DELETE_MEMORY`: Erasing personal memories.

---

## 4. Hardware Optimization for realme P4 5G

The MediaTek Dimensity 7400 Ultra integrates an octa-core CPU layout (Arm Cortex performance and efficiency clusters) paired with an on-device NPU.

### 4.1 Memory & Execution Budgeting

| Component | RAM Footprint | CPU Usage | Execution Latency |
|---|---|---|---|
| **Standby Wake-Word Detector** | ~14 MB | ~1.5 - 3% (Throttled) | 80 - 140 ms |
| **Path A: Fast Command Engine** | ~4 MB | < 1% (Instant regex) | 2 - 8 ms |
| **Path B: SmolLM2 360M GGUF** | ~450 MB | Multi-threaded CPU/NPU | 280 - 650 ms |
| **Path B: Qwen 2.5 0.5B GGUF** | ~650 MB | Multi-threaded CPU/NPU | 420 - 900 ms |
| **Jetpack Compose UI** | ~45 MB | Hardware accelerated | 60 - 120 FPS |

### 4.2 Dynamic Resource Management
- **Conditional Model Loading:** The local LLM is loaded on-demand. If the device available RAM falls below the required safety threshold (e.g. system memory pressure), the AI engine automatically unloads the neural weights and falls back to deterministic command execution to prevent Android Out-Of-Memory (OOM) killer events.
- **Audio Stream Recycling:** The `AudioRecord` buffer size is dynamically calculated based on hardware sample rates (`AudioRecord.getMinBufferSize()`), preventing audio underruns or buffer over-allocation.

