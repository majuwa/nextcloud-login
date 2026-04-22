# Requirements

## Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| FR-01 | Implement Nextcloud Login Flow v2 (POST `/index.php/login/v2`, poll endpoint) | Implemented |
| FR-02 | Emit `WaitingForBrowser` state containing the login URL | Implemented |
| FR-03 | Poll the server token endpoint at a configurable interval | Implemented |
| FR-04 | Emit `Authenticated` state with `server`, `loginName`, `appPassword` on success | Implemented |
| FR-05 | Emit `Failed` state on non-2xx init response | Implemented |
| FR-06 | Emit `Failed` state on poll timeout | Implemented |
| FR-07 | Emit `Failed` state on JSON parse error or network exception | Implemented |
| FR-08 | Normalize server URL (prepend `https://` when no scheme present, strip trailing slash) | Implemented |
| FR-09 | Strip trailing slash from `server` field in credential response | Implemented |

## Non-Functional Requirements

| ID | Requirement | Status |
|---|---|---|
| NFR-01 | No Android SDK imports (`android.*`) anywhere in library source | Policy |
| NFR-02 | Same-origin validation: `poll.endpoint` and `login` URL must match `scheme://host[:port]` of server | Implemented |
| NFR-03 | Configurable `User-Agent` header (constructor parameter, default `"login-flow-nextcloud/1.0"`) | Implemented |
| NFR-04 | Configurable poll interval (default 2 000 ms) | Implemented |
| NFR-05 | Configurable max poll duration (default 120 000 ms) | Implemented |
| NFR-06 | All HTTP response bodies closed via `.use {}` to prevent resource leaks | Implemented |
| NFR-07 | Flow runs on `Dispatchers.IO` | Implemented |
| NFR-08 | Library published to GitHub Packages (`de.majuwa.android:login-flow-nextcloud`) | Implemented |
| NFR-09 | CI runs tests on every push and pull request | Implemented |
| NFR-10 | Secrets not committed to source control | Policy |
| NFR-11 | Unit tests cover origin validation, timeout, happy path, non-2xx init, WaitingForBrowser ordering | Implemented |
