package xyz.fieldatlas.ui

import java.net.URI

internal fun sourceDisplayName(source: String): String =
    runCatching { URI(source).host?.removePrefix("www.") }.getOrNull()
        ?.takeIf(String::isNotBlank) ?: source
