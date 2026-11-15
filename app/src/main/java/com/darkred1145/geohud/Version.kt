package com.darkred1145.geohud

/** Semantic-ish version comparison that tolerates tags such as "v0.6.2-beta". */
internal object Version {

    fun isRemoteNewer(remote: String, local: String): Boolean {
        val remoteParts = parse(remote)
        val localParts = parse(local)
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val remotePart = remoteParts.getOrElse(i) { 0 }
            val localPart = localParts.getOrElse(i) { 0 }
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }

    private fun parse(version: String): List<Int> {
        val trimmed = version.trim()
        val withoutPrefix = if (trimmed.startsWith("v", ignoreCase = true)) trimmed.substring(1) else trimmed
        return withoutPrefix
            .split('.', '-', '+', '_')
            .map { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
    }
}
