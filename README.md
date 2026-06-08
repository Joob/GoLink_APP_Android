<<<<<<< HEAD

# Android (Native)

Stack: Kotlin · Jetpack Compose · Hilt · Retrofit · DataStore · Material 3.

## Pré-requisitos

- Android Studio Ladybug ou superior
- JDK 17
- Android SDK com `compileSdk = 35` e `targetSdk = 35`

## Como abrir

1. **Android Studio → Open → selecciona esta pasta.** O Android Studio irá provisionar o Gradle Wrapper (`gradle-wrapper.jar`) automaticamente.
2. Aguarda o "Gradle sync".
3. Run ▶ no emulador (API 26+) ou device físico.

## Estado por fase

- [x] **Fase 0** — Scaffold, tema da marca (#00BC7E), DataStore de configuração, ecrã para configurar o URL do servidor com botão "Testar ligação" (faz `GET /api/ping`).
- [x] **Fase 1** — Auth completa: email/password com OTP por email, registo, recuperar password, Socialite (Google/GitHub/Microsoft) via WebView com sync de cookies, logout, token guardado encriptado, bootstrap via `GET /api/user`, gate de navegação Loading/NeedsBackend/NeedsAuth/NeedsOtp/Authenticated/BootstrapFailed.
- [x] **Fase 2** — Browse de pastas/ficheiros com `GET /api/browse/folders/{id}`, breadcrumbs com navegação recursiva, drawer com Os Meus Ficheiros / Recentes / Partilhado Comigo, search Spotlight com debounce, bottom sheet de acções por ficheiro, download via `DownloadManager` com Authorization Bearer.
- [x] **Fase 3** — Upload simples (`POST /api/upload`) + chunked 5 MB (`POST /api/upload/chunks`) com progresso via banner, criar pasta (`POST /api/create-folder`), renomear (`PATCH /api/rename/{id}`), mover (`POST /api/move`) com folder picker baseado no `GET /api/browse/navigation`, eliminar/lixo (`POST /api/remove`). FAB com menu (Carregar/Nova pasta) e long-press / tap-em-ficheiro abre actions sheet (Descarregar/Renomear/Mover/Eliminar).
- [x] **Fase 4** — Partilha (`POST /api/share`, `PATCH /api/share/{token}`, `DELETE /api/share`) com password opcional, permissão para pastas (can-view/can-edit), expiração em dias; envio por email (`POST /api/share/{token}/email`); QR code via `GET /api/share/{token}/qr` (SVG); revogar; favoritos para pastas (`POST/DELETE /api/favourites`) sincronizados via `/api/user` relationships; novo modo "Favoritos" no drawer.
- [x] **Fase 5** — Lixo: `GET /api/browse/trash/{id}` para listar, `POST /api/trash/restore` para repor item, `DELETE /api/trash/dump` para esvaziar; novo modo "Lixo" no drawer, top bar com botão **Esvaziar lixo** (com confirmação destrutiva), actions sheet adaptado quando estamos no lixo (Restaurar / Eliminar permanentemente — esconde Partilhar/Mover/Renomear/etc.), confirmação para `force_delete: true`.
- [x] **Fase 6** — Notificações in-app com polling 60s (`GET /api/notifications`, `POST /api/notifications/read`, `POST /api/notifications/{id}/read`, `POST /api/notifications/{id}/delete`, `DELETE /api/notifications`) + bell badge na top bar com contagem de não lidas; ecrã de definições com perfil, storage usage (`GET /api/user/storage`), lista de sessões activas (`GET /api/user/sessions`) com revogação individual e em massa, alterar password (`POST /api/user/password`). _Billing e WebSocket Pusher ficam para próxima iteração._
- [x] **Fase 7** — Polish UI: ImageLoader Coil partilhado com OkHttp autenticado + decoder SVG; HostRewrite/AuthInterceptor agora só actuam para o host do backend (URLs assinadas S3/CDN passam transparentes); emojis e cor das pastas renderizados no `BrowseItemRow`; thumbnails reais para imagens; avatar redondo no drawer header (com fallback para inicial); QR code SVG renderizado a 220 dp; recursos `values/strings.xml` + `values-en/strings.xml` (PT e EN) prontos para swap dos hardcoded strings em iteração futura.

## Estrutura

```
app/src/main/kotlin/co/golink/tester/
├── App.kt / MainActivity.kt
├── data/
│   ├── auth/                     # TokenStore (encrypted), AuthRepository, SessionManager, ApiErrorParser
│   ├── config/                   # BackendConfigRepository, BackendUrlHolder, ConfigRepository
│   └── user/                     # UserRepository
├── domain/                       # auth, user, config DTOs + AuthError
├── di/                           # NetworkModule, ApiModule
├── network/
│   ├── ApiService(Factory)       # Ping (factory para pré-config)
│   ├── AuthApi, UserApi, ConfigApi
│   └── interceptors/             # HostRewrite + Auth bearer
└── ui/
    ├── common/AuthScaffold       # Layout partilhado dos ecrãs de auth
    ├── theme/                    # Color, Typography, Theme (#00BC7E)
    ├── navigation/               # NavHost com gate
    └── screens/
        ├── config/               # BackendConfigScreen
        ├── signin/ register/ forgot/ otp/ socialite/
        └── home/                 # Placeholder pós-login
```

## Fluxo de autenticação implementado

1. **Configurar servidor** → guarda URL no DataStore.
2. **Login** (`POST /api/login`) → recebe token Sanctum → guardado encriptado.
3. **OTP** (`POST /api/user/send-otp-code` + `POST /api/user/validate-otp-code`) → obrigatório após login (espelha o comportamento da web).
4. **Bootstrap** (`GET /api/user/`) → utilizador persistido em memória.
5. **Socialite** → `GET /api/socialite/{provider}/redirect` → WebView → callback no `/sign-in` → cookies copiados para chamar `GET /api/socialite/pending-token` → token adoptado → segue OTP.
6. **Logout** → `POST /api/logout` + limpa TokenStore.
>>>>>>> 91f381b (project added)
