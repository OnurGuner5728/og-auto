package com.onurguner.ogauto.core

/** One playable entry of an IPTV playlist or a single shared stream. */
data class Channel(
    val title: String,
    val url: String,
    val group: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Minimal extended-M3U reader for IPTV playlists:
 *   #EXTINF:-1 tvg-name="X" group-title="News",Channel name
 *   #EXTVLCOPT:http-user-agent=...   (optional, also http-referrer)
 *   http://host/stream.m3u8
 * Anything it does not understand is skipped rather than rejected.
 */
object M3uParser {

    private val attr = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var title: String? = null
        var group: String? = null
        val headers = linkedMapOf<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val attrs = attr.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    val comma = lastCommaOutsideQuotes(line)
                    val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
                    title = name.ifEmpty { attrs["tvg-name"].orEmpty() }.ifEmpty { null }
                    group = attrs["group-title"]?.ifEmpty { null }
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    group = line.substringAfter(':').trim().ifEmpty { null }
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                    val opt = line.substringAfter(':')
                    val key = opt.substringBefore('=').trim().lowercase()
                    val value = opt.substringAfter('=', "").trim()
                    when (key) {
                        "http-user-agent" -> headers["User-Agent"] = value
                        "http-referrer", "http-referer" -> headers["Referer"] = value
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    if (looksLikeUrl(line)) {
                        out += Channel(
                            title = title ?: line.substringAfterLast('/').ifEmpty { line },
                            url = line,
                            group = group,
                            headers = headers.toMap(),
                        )
                    }
                    title = null
                    group = null
                    headers.clear()
                }
            }
        }
        return out
    }

    fun looksLikeUrl(s: String): Boolean {
        val lower = s.lowercase()
        return lower.startsWith("http://") || lower.startsWith("https://") ||
            lower.startsWith("rtsp://") || lower.startsWith("content://") ||
            lower.startsWith("file://")
    }

    /** True when [url] points at a playlist to expand rather than a stream to play. */
    fun isPlaylistUrl(url: String): Boolean {
        val path = url.substringBefore('?').lowercase()
        return path.endsWith(".m3u") || path.contains("get.php") && url.contains("type=m3u")
    }

    private fun lastCommaOutsideQuotes(line: String): Int {
        var inQuotes = false
        var last = -1
        for ((i, c) in line.withIndex()) {
            if (c == '"') inQuotes = !inQuotes
            else if (c == ',' && !inQuotes) last = i
        }
        return last
    }
}
