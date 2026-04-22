package de.majuwa.android.nextcloudlogin.internal

/**
 * Returns `scheme://host[:port]` for [url], or an empty string when the URL is malformed.
 *
 * Default ports (80 for HTTP, 443 for HTTPS) are normalised away so that
 * `https://example.com` and `https://example.com:443` produce the same origin string,
 * as required by RFC 6454.
 */
internal fun buildOrigin(url: String): String =
    try {
        val u = java.net.URL(url)
        val port = if (u.port == -1 || u.port == u.defaultPort) -1 else u.port
        buildString {
            append(u.protocol).append("://").append(u.host)
            if (port != -1) append(":").append(port)
        }
    } catch (_: Exception) {
        ""
    }

/**
 * Returns an error message when [pollEndpoint] or [loginUrl] do not share the same origin
 * as [serverBaseUrl], or `null` when all three match.
 *
 * Prevents token theft and phishing via a compromised server response.
 */
internal fun validateSameOrigin(serverBaseUrl: String, pollEndpoint: String, loginUrl: String): String? {
    val serverOrigin = buildOrigin(serverBaseUrl)
    if (serverOrigin.isBlank() || buildOrigin(pollEndpoint) != serverOrigin) {
        return "Poll endpoint origin does not match server"
    }
    if (buildOrigin(loginUrl) != serverOrigin) {
        return "Login URL origin does not match server"
    }
    return null
}
