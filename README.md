# GoLink Android

Native Android app for **GoLink** — private cloud storage with end-to-end encryption. Official client for the GoLink backend (Laravel), featuring full file management, sharing, automatic phone backup, a password manager, notes, calendar, receipts and bank-grade security.

![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)

---

## ✨ Features

### Files
- 📂 Folder browsing with breadcrumbs, instant search, favourites and "Shared with me"
- ⬆️ Simple and chunked uploads (5 MB) with live progress; camera upload; create folder, rename, move, delete
- ⬇️ Authenticated downloads via `DownloadManager`; built-in viewer for images, video and documents
- 📝 Office editor for documents and spreadsheets (docx, odt, xlsx, ods, csv)
- 🗑️ Full trash: restore, permanently delete, empty
- 🖼️ Real thumbnails, folder emojis and colours, user avatar

### End-to-end encryption (E2E)
- 🔐 Files encrypted on-device before upload (X25519 + secretbox) — the server never sees the content
- 🔑 Private key protected by a passphrase (PBKDF2 / Argon2), unlocked locally, with a recovery option
- 🤝 E2E sharing with per-recipient re-encryption of the data key

### Productivity
- 🔑 Password manager: encrypted vault, TOTP codes, password generator, health check and system autofill
- 🗒️ Encrypted notes with notebooks, tags and multi-select
- 📅 Calendar with month, week and agenda views, events and Drive attachments
- 🧾 Receipts: camera scan with OCR, spending charts, budgets, tax (IRS) summary and PDF export
- 🕊️ Digital legacy: trusted people, check-ins and encrypted secrets released if you stop checking in

### Account security
- 🔒 Email OTP login (2-minute code), social login (Google/GitHub/Microsoft)
- 🗝️ Passkeys and two-factor authentication
- 📱 App lock with biometrics or PIN
- 🕵️ Active session list with individual and bulk revocation; security activity log
- 🚪 Immediate detection of suspended accounts and revoked sessions (forced logout)
- ❌ Self-service account deletion: email confirmation + 6-digit code, with real-time progress as 100% of the data is erased

### Sharing
- 🔗 Share links with optional password, permissions (view/edit) and expiry
- 📧 Email delivery and QR code
- ⭐ Synced favourites

### Extras
- ☁️ Automatic gallery backup (WorkManager, network-aware)
- 🔔 In-app notifications with unread badge
- 💳 Billing and plans (Stripe / crypto)
- 🎫 Support tickets with attachments
- 🌍 Interface in Portuguese, English, French and Spanish
- 🛠️ Built-in admin panel (dashboard, users, invites, news)

---

## 🧱 Tech stack

| Layer | Technology |
|---|---|
| UI | Jetpack Compose + Material 3 |
| DI | Hilt |
| Networking | Retrofit + OkHttp + kotlinx.serialization |
| Images | Coil (authenticated OkHttp, SVG support) |
| Media | Media3 (video playback), CameraX (camera upload and receipt scan) |
| OCR / QR | Tesseract4Android, ZXing |
| Auth | AndroidX Credentials (passkeys), AndroidX Biometric |
| Storage | DataStore + encrypted SharedPreferences |
| Background | WorkManager (automatic backup) |
| Crypto | Lazysodium (libsodium): X25519, XSalsa20-Poly1305, Argon2; PBKDF2 |

### Project structure

```
app/src/main/kotlin/co/golink/tester/
├── data/          # Repositories, auth, encryption, upload/download, backup
├── domain/        # DTOs and models (kotlinx.serialization)
├── network/       # Retrofit interfaces + interceptors (auth, session, host)
├── di/            # Hilt modules
└── ui/
    ├── screens/   # Compose screens (browse, pass, notes, calendar, receipts, settings, …)
    ├── components/# Reusable dialogs and components
    ├── common/    # Shared UI helpers
    ├── navigation/# Navigation host, routes and deep links
    ├── i18n/      # Runtime translations (PT/EN/FR/ES)
    └── theme/     # Brand theme
```

---

## 🚀 Getting started

### Requirements
- Android Studio Narwhal 3 Feature Drop (2025.1.3) or newer (AGP 8.13)
- JDK 17
- Android SDK 36
- A reachable GoLink server (the app asks for the URL on first launch and verifies it with `GET /api/ping`)

### Build

```bash
git clone git@github.com:Joob/GoLink_APP_Android.git
cd GoLink_APP_Android
./gradlew :app:assembleDebug
```

Or open the folder in Android Studio → Gradle sync → Run ▶ (emulator or device on API 26+).

### Release

Release builds require a `keystore.properties` file in the project root (not versioned):

```properties
storeFile=../golink-release.jks
storePassword=•••
keyAlias=•••
keyPassword=•••
```

```bash
./gradlew :app:assembleRelease
```

Without `keystore.properties` (e.g. on CI) the release build produces an unsigned APK.

---

## 🔌 Backend

This app talks to the GoLink REST API (Laravel + Sanctum). Main endpoints cover authentication/OTP, browsing, chunked uploads, sharing, trash, notifications, billing, E2E encryption and account deletion. The server URL is configurable in-app — it works with any self-hosted GoLink instance.

---

## 🔒 Security notes

- The session token is stored encrypted on the device
- Interceptors ensure the `Authorization` header is only sent to the backend host (signed S3/CDN URLs pass through untouched)
- E2E content is encrypted and decrypted exclusively on the device
- Logs are sanitised — tokens, codes and file contents are never recorded

---

## 📄 License

Released under the [MIT](LICENSE) license.
