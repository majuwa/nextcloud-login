# CLAUDE.md — login-flow-nextcloud

AI assistant instructions for the `login-flow-nextcloud` library.

## Project Vision

`login-flow-nextcloud` is a **shared Kotlin JVM library** that implements the Nextcloud Login Flow v2 protocol. It is consumed by Android apps (KrhnlesImageManagement, android-paper-reader) and any future JVM/Android project that needs to authenticate against a Nextcloud server.

The library must remain **free of Android SDK imports** so it can be tested on the JVM without an Android device or emulator.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin (JVM target 17) |
| HTTP | OkHttp 5.x |
| Async | Kotlin Coroutines (`Flow`, `Dispatchers.IO`) |
| Build | Gradle (Kotlin DSL), version catalogs |
| Testing | JUnit 4, OkHttp MockWebServer, `kotlinx-coroutines-test`, Turbine |
| Distribution | GitHub Packages (Maven) |

## Architecture Overview

The library is a single Gradle module (`:lib`) with two layers:

- **Public API** (`de.majuwa.android.nextcloudlogin`): `NextcloudLoginFlow`, `LoginFlowState`
- **Internal utilities** (`de.majuwa.android.nextcloudlogin.internal`): `OriginValidator`

Consumers depend only on the public API. Internal classes carry the `internal` visibility modifier.

## Package Structure

```
lib/src/main/java/de/majuwa/android/nextcloudlogin/
├── LoginFlowState.kt          # Sealed class with flow states
├── NextcloudLoginFlow.kt      # Main entry point
└── internal/
    └── OriginValidator.kt     # buildOrigin() + validateSameOrigin()
```

## Coding Standards

1. **No `android.*` imports** anywhere in `lib/`. The build will succeed without the Android SDK.
2. Use `public` visibility modifiers explicitly on public API classes and functions (Kotlin explicit API mode may be enabled in the future).
3. OkHttp 5.x: `response.body` is non-nullable — use `.body.string()`, not `?.string()`.
4. Always close response bodies with `.use {}`.
5. Use `Dispatchers.IO` for all network operations via `.flowOn(Dispatchers.IO)`.
6. Keep `internal` classes in the `internal` sub-package.

## Security Conventions

- **Same-origin validation** must be performed on every `loginFlow()` call, before emitting `WaitingForBrowser`. This prevents a rogue server from redirecting credentials to an attacker.
- **Timeout**: The poll loop is bounded by `maxPollDurationMs`. Never poll indefinitely.
- **No secrets in source**: Credentials (GitHub token) are provided via environment variables or `gpr.properties` (git-ignored).

## After Every Code Change

- [ ] Run `./gradlew :lib:test` — all tests must pass
- [ ] Update `README.md` if the public API or configuration changes
- [ ] Update `docs/architecture.md` if the architecture changes
- [ ] Update `docs/requirements.md` if requirements change

## Versioning & Release

1. Bump `version` in `lib/build.gradle.kts`
2. Commit: `git commit -am "chore: release vX.Y.Z"`
3. Tag & push: `git tag vX.Y.Z && git push origin vX.Y.Z`
4. GitHub Actions `publish.yml` publishes to GitHub Packages automatically
