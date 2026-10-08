package xyz.fieldatlas.ui.presentation

import java.net.URI

/** Show the selected endpoint, never URL paths, signed queries or credentials. No I/O. */
fun downloadSourceDisclosure(address: String): String {
    val endpoint = runCatching {
        val uri = URI(address)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
            uri.fragment == null && (uri.port == -1 || uri.port in 1..65535))
        uri.host + if (uri.port == -1 || uri.port == 443) "" else ":${uri.port}"
    }.getOrDefault("selected HTTPS server")
    return "Source: $endpoint\nDownload servers can see your IP address, not your questions."
}
