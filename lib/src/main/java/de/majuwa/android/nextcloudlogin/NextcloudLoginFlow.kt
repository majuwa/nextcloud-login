package de.majuwa.android.nextcloudlogin

import de.majuwa.android.nextcloudlogin.internal.validateSameOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val DEFAULT_POLL_INTERVAL_MS = 2_000L
private const val DEFAULT_MAX_POLL_DURATION_MS = 120_000L
private const val CONNECT_TIMEOUT_SECONDS = 30L
private const val READ_TIMEOUT_SECONDS = 30L

/**
 * Implements the [Nextcloud Login Flow v2](https://docs.nextcloud.com/server/latest/developer_manual/client_apis/LoginFlow/index.html).
 *
 * Usage:
 * ```kotlin
 * val loginFlow = NextcloudLoginFlow(userAgent = "MyApp/1.0")
 * loginFlow.loginFlow("https://nextcloud.example.com")
 *     .collect { state -> /* handle LoginFlowState */ }
 * ```
 *
 * @param userAgent The `User-Agent` header sent with every HTTP request. Defaults to `"login-flow-nextcloud/1.0"`.
 * @param pollIntervalMs Milliseconds between poll attempts. Defaults to 2 000 ms.
 * @param maxPollDurationMs Maximum total polling time before a timeout failure is emitted. Defaults to 120 000 ms.
 */
public class NextcloudLoginFlow(
    private val userAgent: String = "login-flow-nextcloud/1.0",
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    private val maxPollDurationMs: Long = DEFAULT_MAX_POLL_DURATION_MS,
) {
    private val httpClient: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", userAgent)
                        .build(),
                )
            }
            .build()

    /**
     * Returns a cold [Flow] that drives the Nextcloud Login Flow v2 for [serverUrl].
     *
     * Emitted states:
     * 1. [LoginFlowState.WaitingForBrowser] — open the URL in a browser.
     * 2. [LoginFlowState.Authenticated] — credentials are ready.
     * 3. [LoginFlowState.Failed] — unrecoverable error; collection stops.
     *
     * The flow runs on [Dispatchers.IO].
     */
    public fun loginFlow(serverUrl: String): Flow<LoginFlowState> =
        flow {
            val normalized = serverUrl.trim()
            val baseUrl = (if ("://" !in normalized) "https://$normalized" else normalized).trimEnd('/')

            val initBody =
                httpClient.newCall(buildInitRequest(baseUrl)).execute().use { response ->
                    if (!response.isSuccessful) {
                        emit(LoginFlowState.Failed("Server responded ${response.code}"))
                        return@flow
                    }
                    response.body.string()
                }

            val json = JSONObject(initBody)
            val pollToken = json.getJSONObject("poll").getString("token")
            val pollEndpoint = json.getJSONObject("poll").getString("endpoint")
            val loginUrl = json.getString("login")

            validateSameOrigin(baseUrl, pollEndpoint, loginUrl)?.let { error ->
                emit(LoginFlowState.Failed(error))
                return@flow
            }

            emit(LoginFlowState.WaitingForBrowser(loginUrl))

            val startedAt = System.currentTimeMillis()
            while (System.currentTimeMillis() - startedAt < maxPollDurationMs) {
                delay(pollIntervalMs)
                httpClient.newCall(buildPollRequest(pollEndpoint, pollToken)).execute().use { response ->
                    if (response.isSuccessful) {
                        val creds = JSONObject(response.body.string())
                        emit(
                            LoginFlowState.Authenticated(
                                server = creds.getString("server").trimEnd('/'),
                                loginName = creds.getString("loginName"),
                                appPassword = creds.getString("appPassword"),
                            ),
                        )
                        return@flow
                    }
                }
            }
            emit(LoginFlowState.Failed("Login timed out. Please try again."))
        }
            .catch { e -> emit(LoginFlowState.Failed(e.message ?: "Unexpected error")) }
            .flowOn(Dispatchers.IO)

    private fun buildInitRequest(baseUrl: String): Request =
        Request.Builder()
            .url("$baseUrl/index.php/login/v2")
            .post(FormBody.Builder().build())
            .header("OCS-APIREQUEST", "true")
            .build()

    private fun buildPollRequest(pollEndpoint: String, pollToken: String): Request =
        Request.Builder()
            .url(pollEndpoint)
            .post(FormBody.Builder().add("token", pollToken).build())
            .build()
}
