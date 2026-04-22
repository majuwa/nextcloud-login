# Login Flow for Nextcloud

A pure Kotlin JVM library implementing the [Nextcloud Login Flow v2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html) protocol.

## Features

- **Login Flow v2**: Full implementation of the Nextcloud token-based login protocol
- **Same-origin validation**: Guards against phishing and token theft by verifying that the poll endpoint and login URL share the server's origin
- **Configurable timeout**: Polling stops after a configurable maximum duration (default 120 s) and emits a `Failed` state
- **Configurable `User-Agent`**: Pass your app name via the constructor — no hard-coded strings
- **Pure Kotlin JVM**: No Android SDK dependency; works on any JVM target

## Installation

### 1. Add the GitHub Packages repository

**`settings.gradle.kts`** (inside `dependencyResolutionManagement.repositories`):

```kotlin
maven {
    url = uri("https://maven.pkg.github.com/majuwa/login-flow-nextcloud")
    credentials {
        username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
        password = providers.gradleProperty("gpr.token").orNull ?: System.getenv("GITHUB_TOKEN")
    }
}
```

Store your GitHub credentials locally (never commit these):

```properties
# gpr.properties  — add to .gitignore
gpr.user=your-github-username
gpr.token=ghp_your_personal_access_token
```

### 2. Add the dependency

**Kotlin DSL:**
```kotlin
implementation("de.majuwa.android:login-flow-nextcloud:0.0.1")
```

**Groovy DSL:**
```groovy
implementation 'de.majuwa.android:login-flow-nextcloud:0.0.1'
```

## Usage

```kotlin
import de.majuwa.android.nextcloudlogin.LoginFlowState
import de.majuwa.android.nextcloudlogin.NextcloudLoginFlow

val loginFlow = NextcloudLoginFlow(userAgent = "MyApp/1.0 (Android)")

loginFlow.loginFlow("https://nextcloud.example.com")
    .collect { state ->
        when (state) {
            is LoginFlowState.WaitingForBrowser -> {
                // Open state.loginUrl in the system browser
                openBrowser(state.loginUrl)
            }
            is LoginFlowState.Authenticated -> {
                // Save state.server, state.loginName, state.appPassword
                saveCredentials(state)
            }
            is LoginFlowState.Failed -> {
                // Show state.message to the user
                showError(state.message)
            }
        }
    }
```

The flow runs on `Dispatchers.IO` and is cold — a new network session starts each time you `collect`.

## Configuration

| Parameter | Default | Description |
|---|---|---|
| `userAgent` | `"login-flow-nextcloud/1.0"` | `User-Agent` header for all HTTP requests |
| `pollIntervalMs` | `2000` | Milliseconds between poll attempts |
| `maxPollDurationMs` | `120000` | Maximum total polling time before timeout |

```kotlin
val loginFlow = NextcloudLoginFlow(
    userAgent = "MyApp/2.0",
    pollIntervalMs = 3_000,
    maxPollDurationMs = 60_000,
)
```

## Security Design

- **Same-origin check**: After initiating the flow, the library validates that the `poll.endpoint` and `login` URL in the server's JSON response share the same `scheme://host[:port]` as the original server URL. A mismatch immediately emits `LoginFlowState.Failed` and stops the flow, preventing a compromised server from redirecting credentials to an attacker.
- **Timeout**: Polling is bounded by `maxPollDurationMs` to prevent indefinite blocking.
- **`.use {}` for responses**: OkHttp response bodies are always closed via `.use {}` to prevent resource leaks.
- **No secrets in source**: Credentials are passed via environment variables or `gpr.properties` (which is git-ignored).

## Publishing a New Version

1. Bump `version` in `lib/build.gradle.kts`
2. Commit: `git commit -am "chore: release vX.Y.Z"`
3. Tag: `git tag vX.Y.Z && git push origin vX.Y.Z`

The `publish.yml` GitHub Actions workflow publishes to GitHub Packages automatically on tag push.

## License

LGPL License — see [LICENSE](LICENSE) for details.
