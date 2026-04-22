package de.majuwa.android.nextcloudlogin

/**
 * Represents the states emitted by [NextcloudLoginFlow.loginFlow].
 */
public sealed class LoginFlowState {
    /** The user must open [loginUrl] in a browser to approve the login. */
    public data class WaitingForBrowser(val loginUrl: String) : LoginFlowState()

    /** Authentication succeeded. Credentials are ready to use. */
    public data class Authenticated(
        val server: String,
        val loginName: String,
        val appPassword: String,
    ) : LoginFlowState() {
        /** Excludes sensitive fields from automatic string representation to prevent credential leaks in logs. */
        override fun toString(): String = "Authenticated(server=$server, loginName=***)"
    }

    /** Authentication failed. [message] contains a human-readable reason. */
    public data class Failed(val message: String) : LoginFlowState()
}
