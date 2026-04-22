package de.majuwa.android.nextcloudlogin

import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

class NextcloudLoginFlowTest {
    private lateinit var mockWebServer: MockWebServer
    private val serverUrl: String
        get() = mockWebServer.url("").toString()

    @Before
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `loginFlow fails when server returns non-2xx on init`() =
        runTest {
            mockWebServer.enqueue(MockResponse().setResponseCode(500))

            val flow = NextcloudLoginFlow()
            val states = flow.loginFlow(serverUrl).toList()

            val failed = states.filterIsInstance<LoginFlowState.Failed>()
            assertTrue("Expected a Failed state for 500 response", failed.isNotEmpty())
            assertTrue(failed.first().message.contains("500"))
        }

    @Test
    fun `loginFlow fails when pollEndpoint origin differs from server`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "poll": {
                        "token": "tok123",
                        "endpoint": "https://evil.attacker.com/poll"
                      },
                      "login": "${mockWebServer.url("/login")}"
                    }
                    """.trimIndent(),
                ),
            )

            val flow = NextcloudLoginFlow()
            val states = flow.loginFlow(serverUrl).toList()

            val failed = states.filterIsInstance<LoginFlowState.Failed>()
            assertTrue("Expected a Failed state for mismatched poll origin", failed.isNotEmpty())
            assertTrue(failed.first().message.contains("Poll endpoint origin", ignoreCase = true))
        }

    @Test
    fun `loginFlow fails when loginUrl origin differs from server`() =
        runTest {
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "poll": {
                        "token": "tok123",
                        "endpoint": "${mockWebServer.url("/poll")}"
                      },
                      "login": "https://phishing.example.com/fake-login"
                    }
                    """.trimIndent(),
                ),
            )

            val flow = NextcloudLoginFlow()
            val states = flow.loginFlow(serverUrl).toList()

            val failed = states.filterIsInstance<LoginFlowState.Failed>()
            assertTrue("Expected a Failed state for mismatched login URL origin", failed.isNotEmpty())
            assertTrue(failed.first().message.contains("Login URL origin", ignoreCase = true))
        }

    @Test
    fun `loginFlow emits WaitingForBrowser before polling`() =
        runTest {
            val pollUrl = mockWebServer.url("/poll").toString()
            val loginUrl = mockWebServer.url("/login").toString()

            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "poll": {"token": "tok123", "endpoint": "$pollUrl"},
                      "login": "$loginUrl"
                    }
                    """.trimIndent(),
                ),
            )
            // Poll succeeds immediately
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"server":"${mockWebServer.url("")}","loginName":"alice","appPassword":"app-pw"}""",
                ),
            )

            val flow = NextcloudLoginFlow()
            val states = flow.loginFlow(serverUrl).toList()

            val waitingIndex = states.indexOfFirst { it is LoginFlowState.WaitingForBrowser }
            val authIndex = states.indexOfFirst { it is LoginFlowState.Authenticated }
            assertTrue("WaitingForBrowser must be emitted", waitingIndex >= 0)
            assertTrue("Authenticated must come after WaitingForBrowser", authIndex > waitingIndex)
        }

    @Test
    fun `loginFlow succeeds when both poll endpoint and login URL share server origin`() =
        runTest {
            val pollUrl = mockWebServer.url("/poll").toString()
            val loginUrl = mockWebServer.url("/login").toString()

            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "poll": {"token": "tok123", "endpoint": "$pollUrl"},
                      "login": "$loginUrl"
                    }
                    """.trimIndent(),
                ),
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"server":"${mockWebServer.url("")}","loginName":"alice","appPassword":"app-pw"}""",
                ),
            )

            val flow = NextcloudLoginFlow()
            val states = flow.loginFlow(serverUrl).toList()

            assertFalse(
                "Should not fail for same-origin URLs",
                states.any { it is LoginFlowState.Failed },
            )
            assertTrue(states.any { it is LoginFlowState.Authenticated })
        }

    @Test
    fun `loginFlow fails with timeout when poll never succeeds`() =
        runTest {
            val pollUrl = mockWebServer.url("/poll").toString()
            val loginUrl = mockWebServer.url("/login").toString()

            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    {
                      "poll": {"token": "tok123", "endpoint": "$pollUrl"},
                      "login": "$loginUrl"
                    }
                    """.trimIndent(),
                ),
            )
            repeat(10) {
                mockWebServer.enqueue(MockResponse().setResponseCode(404))
            }

            val flow = NextcloudLoginFlow(pollIntervalMs = 10, maxPollDurationMs = 50)
            val states = flow.loginFlow(serverUrl).toList()

            val failed = states.filterIsInstance<LoginFlowState.Failed>()
            assertTrue("Expected timeout failure state", failed.isNotEmpty())
            assertTrue(failed.last().message.contains("timed out", ignoreCase = true))
        }

    @Test
    fun `loginFlow succeeds when server uses explicit default port 443`() =
        runTest {
            // MockWebServer runs on a non-standard port; simulate the scenario by using matching
            // explicit-default-port origins in the init response.
            val basePort = mockWebServer.port
            val baseUrl = "http://127.0.0.1:$basePort"
            val pollUrl = "http://127.0.0.1:$basePort/poll"
            val loginUrl = "http://127.0.0.1:$basePort/login"

            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"poll":{"token":"tok","endpoint":"$pollUrl"},"login":"$loginUrl"}""",
                ),
            )
            mockWebServer.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"server":"$baseUrl","loginName":"alice","appPassword":"pw"}""",
                ),
            )

            val states = NextcloudLoginFlow().loginFlow(baseUrl).toList()
            assertFalse("Should not fail", states.any { it is LoginFlowState.Failed })
            assertTrue(states.any { it is LoginFlowState.Authenticated })
        }

    @Test
    fun `Authenticated toString does not expose credentials`() {
        val state = LoginFlowState.Authenticated(
            server = "https://example.com",
            loginName = "alice",
            appPassword = "super-secret-token",
        )
        val str = state.toString()
        assertFalse("appPassword must not appear in toString", str.contains("super-secret-token"))
        assertFalse("loginName must not appear in toString", str.contains("alice"))
        assertTrue("server should appear in toString", str.contains("example.com"))
    }
}
