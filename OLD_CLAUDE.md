@RTK.md

#Confidence gate
#- Do not make changes until you have ≥95% confidence. Ask follow‑up questions until you reach that.
- Do not make any changes until you have 95% confidence in what you need to build. Ask me follow-up questions until you reach that confidence level.

#Token economy
#Minimize token use without sacrificing correctness:

- Keep responses short; no preambles or final summaries.
- Prefer rg/grep before reading files; only Read with offset/limit when necessary.
- Skip unnecessary cat/ls/pwd when paths are known.
- Use Edit for small changes; Write for new files or full rewrites.
- Do not re-read files already read unless they changed.
- Do not echo file contents, diffs, or command output back.
- Spawn subagents only to parallelize real work or protect context.
- Batch independent tool calls in parallel.

# Modo de Trabalho (Regras do Agente)

## Objetivo
Realizar alterações no repositório com a maior precisão possível, evitando leitura/escrita desnecessária e minimizando texto enviado ao modelo.

## Princípios
1) **Edição por patch, nunca reescrita total**
- Não reescreva ficheiros inteiros.
- Alterar apenas as linhas/trechos estritamente necessários.
- Responder com um patch/diff ou instruções de edição local que cubram só as regiões alvo.

2) **Leitura mínima antes de editar**
- Antes de alterar, localizar exatamente o(s) trecho(s) via busca.
- Ler apenas um contexto curto ao redor do match (regra prática: ~30–80 linhas, ou o menor bloco que contenha assinatura/uso relevante).
- Não inspecionar “arredores” amplos nem ficheiros adicionais sem necessidade direta.

3) **Um passo por rodada**
- Fazer **uma** mudança coerente por iteração (ou o menor conjunto que seja logicamente inseparável).
- Depois de aplicar, confirmar o efeito esperado e indicar o próximo passo.

4) **Não repetir trabalho (estado curto)**
- Consultar `AGENT_STATE.md` (ou `ai-state.md`, se existir) e evitar repetir mudanças já marcadas como concluídas.
- Atualizar o estado somente quando uma mudança estiver aplicada e validada.

5) **Escopo estrito**
- Restringir alterações ao(s) ficheiro(s) e símbolos identificados no contexto atual.
- Se for necessário tocar em mais de X ficheiros, parar e pedir confirmação com uma lista curta do impacto.

## Formato de resposta (obrigatório)
Sempre que houver alteração:
- Listar: Arquivo(s) afetado(s) + motivo em 1 frase
- Indicar: Exatamente quais trechos serão alterados (por função/class/chave e proximidade)
- Entregar: patch/diff para aplicar (ou instruções de edição local equivalentes)
- Final: checklist curto de validação (ex.: “verificar compilação/testes” se aplicável)

## Limites (para reduzir tokens)
- Preferir trechos curtos ao invés de ficheiros inteiros.
- Se o plano exigir mais contexto do que o razoável, reduzir o escopo ou dividir em rodadas.

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Native Android client for VueFileManager — a self-hosted Laravel/Sanctum file-manager backend.
The server URL is **not** hardcoded: the user configures it at runtime (DataStore), so the app
talks to whatever backend they point it at. Package: `co.golink.tester`. UI strings and code
comments are mixed Portuguese/English — match the surrounding file.

## Build / run

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build + install to connected device/emulator
./gradlew lint                   # Android lint
./gradlew :app:compileDebugKotlin   # fast type-check without packaging
```

- JDK 17, `compileSdk`/`targetSdk` 35, `minSdk` 26. `local.properties` holds `sdk.dir`.
- Debug builds use `applicationIdSuffix = ".debug"` (debug + release can coexist on one device).
- **No test suite exists** (`androidTest`/`test` are empty) despite the configured `AndroidJUnitRunner`.
- Single Gradle module (`:app`); dependencies are managed through the `gradle/libs.versions.toml`
  version catalog — add libs there, reference as `libs.*`.

## Stack

Kotlin · Jetpack Compose (Material 3) · Hilt (DI) · Retrofit + OkHttp + kotlinx.serialization ·
DataStore (preferences) · WorkManager (Hilt-integrated) · Coil (incl. SVG) · Media3 (viewer) ·
ZXing (QR) · androidx.security-crypto (encrypted token) · WebKit (Socialite OAuth).

## Architecture

Layered, single-activity Compose app: `ui` → `data` (repositories/managers) → `network` (Retrofit
APIs) → backend. `domain/<feature>/` holds `@Serializable` DTOs. Packages are organized by feature
(`auth`, `browse`, `files`, `share`, `trash`, `settings`, `backup`, `notifications`, etc.), each
typically present across `network/`, `data/`, `domain/`, and `ui/screens/`.

### Networking (the non-obvious part — read before touching network code)

Because the backend host is dynamic, requests are built against the placeholder base URL
`http://localhost/` and rewritten at request time:

- `HostRewriteInterceptor` swaps the placeholder host/scheme/port for the configured backend
  (read live from `BackendUrlHolder`, which mirrors the DataStore-backed `BackendConfigRepository`).
- `AuthInterceptor` attaches `Authorization: Bearer <token>` from `TokenStore`, plus
  `Accept`/`X-Requested-With` headers — **only** when the request targets the backend host.
- Both interceptors deliberately leave non-backend hosts untouched, so pre-signed S3/CDN URLs
  (downloads, thumbnails) pass through unauthenticated. Preserve this when editing interceptors.

DI lives in `di/`:
- `NetworkModule` provides Json, a `@Named("base")` client, a `@Named("authed")` client (adds the
  interceptors), and a `@Named("longRunning")` variant with 5–10 min timeouts. There are two
  Retrofit instances — the default and `@Named("longRunningRetrofit")`.
- `ApiModule` creates the Retrofit API interfaces. `FilesApi` and `TrashApi` use the long-running
  Retrofit (large uploads / bulk deletes); everything else uses the default.
- OkHttp body logging is intentionally `HEADERS`, not `BODY`, to avoid OOM on large uploads.

### Auth & navigation gate

`SessionManager` derives an `AuthState` (Loading / Unauthenticated / OtpRequired / Authenticated /
BootstrapFailed) from `TokenStore` (encrypted) + `UserRepository`. `AppNavHost` maps that to
`TopState` and renders the matching screen tree; `AppLockManager` adds a biometric/PIN lock layer
on top. Auth flow: configure server → `POST /api/login` (Sanctum token) → mandatory OTP
(`send-otp-code` / `validate-otp-code`) → bootstrap `GET /api/user`. Socialite OAuth runs in a
WebView, syncs cookies, then adopts the pending token.

### Background work

`UploadManager` and the `backup/` package (`AutoBackupWorker`, `AutoBackupManager`, `MediaScanner`)
use WorkManager. `App` implements `Configuration.Provider` with `HiltWorkerFactory`, so workers are
Hilt-injected — annotate new workers with `@HiltWorker` and inject via `@AssistedInject`.

## Backend API conventions

REST under `/api/*`, JSON, Sanctum bearer tokens. Json is configured with `ignoreUnknownKeys`,
`coerceInputValues`, `explicitNulls = false` — new DTOs only need fields the app actually reads.
The README documents the implemented endpoints per feature phase.
