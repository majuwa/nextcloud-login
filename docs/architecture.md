# Architecture

## Overview

`nextcloud-login` is a **pure Kotlin JVM library** — it has no dependency on the Android SDK. This means it can run and be tested on any standard JVM (including CI runners without Android toolchains), and can be consumed by any JVM-based project, not only Android apps.

## Package Structure

```
lib/src/main/java/de/majuwa/android/nextcloudlogin/
├── LoginFlowState.kt          # Sealed class — public API
├── NextcloudLoginFlow.kt      # Main class — public API
└── internal/
    └── OriginValidator.kt     # buildOrigin() + validateSameOrigin() — internal only
```

## Public API

### `NextcloudLoginFlow`

The single entry point. Constructed with:

| Parameter | Type | Default |
|---|---|---|
| `userAgent` | `String` | `"login-flow-nextcloud/1.0"` |
| `pollIntervalMs` | `Long` | `2000` |
| `maxPollDurationMs` | `Long` | `120000` |

**Method:**
```kotlin
fun loginFlow(serverUrl: String): Flow<LoginFlowState>
```

Creates and manages an OkHttp client internally. The flow runs on `Dispatchers.IO`.

### `LoginFlowState`

A sealed class with three subtypes:

| Subtype | Fields | Meaning |
|---|---|---|
| `WaitingForBrowser` | `loginUrl: String` | Open URL in browser |
| `Authenticated` | `server`, `loginName`, `appPassword` | Credentials ready |
| `Failed` | `message: String` | Unrecoverable error |

## Internal

### `OriginValidator` (`internal`)

`buildOrigin(url: String): String`
: Parses a URL and returns `scheme://host[:port]`. Returns `""` on parse failure.

`validateSameOrigin(serverBaseUrl, pollEndpoint, loginUrl): String?`
: Compares origins. Returns an error message if any mismatch is found, `null` if all match.

## Flow of States

```
loginFlow(serverUrl)
       │
       ▼
  [HTTP POST /index.php/login/v2]
       │
       ├─ non-2xx ──────────────────► Failed("Server responded N")
       │
       ▼
  Parse JSON: pollToken, pollEndpoint, loginUrl
       │
       ├─ origin mismatch ──────────► Failed("Poll/Login URL origin does not match server")
       │
       ▼
  emit WaitingForBrowser(loginUrl)
       │
       ▼
  Poll loop (every pollIntervalMs)
       │
       ├─ 200 OK ──────────────────► Authenticated(server, loginName, appPassword)
       │
       ├─ non-200 ─────────────────► (continue polling)
       │
       └─ timeout ─────────────────► Failed("Login timed out. Please try again.")
```

## Security Decisions

### Same-Origin Check

After the init response is parsed, `validateSameOrigin()` is called before emitting any state. If the `poll.endpoint` or `login` URL in the server's JSON response differs in scheme, host, or port from the original `serverUrl`, the flow terminates with `Failed`.

**Why**: A compromised or malicious server could return a `poll.endpoint` on an attacker-controlled host. Without this check, the poll token (which is effectively a one-time session credential) would be sent to the attacker.

### Timeout

The poll loop is bounded by `maxPollDurationMs`. Without a timeout, a non-responsive server would block the flow indefinitely.

### `.use {}` for Response Bodies

All OkHttp responses are consumed with `.use {}` to guarantee that the response body is closed promptly, preventing connection pool exhaustion.

## Why Kotlin JVM Over Android Library?

| Concern | Kotlin JVM library | Android library (AAR) |
|---|---|---|
| Testability | Plain JVM — runs in any CI | Requires Android instrumentation or Robolectric |
| Portability | Any JVM consumer | Android-only |
| Build toolchain | Standard `kotlin.jvm` plugin | Requires Android Gradle Plugin |
| Size | Minimal | Adds Android SDK overhead |

The Nextcloud Login Flow v2 uses only standard HTTP and JSON — no Android-specific APIs are needed.
