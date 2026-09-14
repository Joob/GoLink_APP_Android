# GoLink Android

Native Android app for **GoLink** — private cloud storage with end-to-end encryption. Official client for the GoLink backend (Laravel), featuring full file management, sharing, automatic phone backup, and bank-grade security.

![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![targetSdk](https://img.shields.io/badge/targetSdk-36-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-MIT-green)

---

## ✨ Features

### Files
- 📂 Folder browsing with breadcrumbs, instant search, favourites and "Shared with me"
- ⬆️ Simple and chunked uploads (5 MB) with live progress; create folder, rename, move, delete
- ⬇️ Authenticated downloads via `DownloadManager`; built-in viewer for images, video and documents
- 🗑️ Full trash: restore, permanently delete, empty
- 🖼️ Real thumbnails, folder emojis and colours, user avatar

### End-to-end encryption (E2E)
- 🔐 Files encrypted on-device before upload (X25519 + secretbox) — the server never sees the content
- 🔑 Private key protected by a passphrase (PBKDF2), unlocked locally, with a recovery option
- 🤝 E2E sharing with per-recipient re-encryption of the data key

### Account security
- 🔒 Email OTP login (2-minute code), social login (Google/GitHub/Microsoft)
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
| Storage | DataStore + encrypted SharedPreferences |
| Background | WorkManager (automatic backup) |
| Crypto | libsodium-style X25519/XSalsa20-Poly1305, PBKDF2 |

### Project structure

```
app/src/main/kotlin/co/golink/tester/
├── data/          # Repositories, auth, encryption, upload/download, backup
├── domain/        # DTOs and models (kotlinx.serialization)
├── network/       # Retrofit interfaces + interceptors (auth, session, host)
├── di/            # Hilt modules
└── ui/
    ├── screens/   # Compose screens (browse, settings, share, viewer, …)
    ├── components/# Reusable dialogs and components
    ├── i18n/      # Runtime translations (PT/EN/FR/ES)
    └── theme/     # Brand theme
```

---

## 🚀 Getting started

### Requirements
- Android Studio Ladybug (or newer)
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
