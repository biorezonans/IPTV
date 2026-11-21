package com.tamay.iptv.data

/** Tolerant M3U parser with best-effort content and country classification. */
object M3uParser {
    private val attributeRegex = Regex("([\\w-]+)=\"([^\"]*)\"")
    private val movieHints = listOf("movie", "movies", "film", "films", "vod", "cinema", "sinema")
    private val seriesHints = listOf("series", "serie", "dizi", "tv show", "tvshows", "season", "sezon")
    private val countries = listOf(
        "Türkiye", "Turkey", "Turkiye", "Germany", "Deutschland", "Almanya", "United Kingdom", "UK", "England", "İngiltere",
        "United States", "USA", "US", "Amerika", "France", "Fransa", "Italy", "İtalya", "Spain", "İspanya",
        "Netherlands", "Hollanda", "Belgium", "Belçika", "Austria", "Avusturya", "Switzerland", "İsviçre",
        "Russia", "Rusya", "Azerbaijan", "Azerbaycan", "Northern Cyprus", "Kıbrıs", "Greece", "Yunanistan",
        "Canada", "Brazil", "Portugal", "Poland", "Sweden", "Norway", "Denmark", "Arabic", "Arab", "India", "Pakistan"
    )

    fun parse(content: String): List<IptvChannel> {
        val result = mutableListOf<IptvChannel>()
        var name: String? = null
        var group = "Diğer"
        var logo: String? = null
        var tvgId: String? = null
        var countryAttr: String? = null

        content.lineSequence().forEach { raw ->
            val line = raw.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach
            when {
                line.startsWith("#EXTINF", true) -> {
                    val comma = line.indexOf(',')
                    val metadata = if (comma >= 0) line.substring(0, comma) else line
                    val attrs = attributeRegex.findAll(metadata).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                    name = (if (comma >= 0) line.substring(comma + 1).trim() else "")
                        .ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { "İsimsiz içerik" }
                    group = attrs["group-title"].orEmpty().ifBlank { "Diğer" }
                    logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    tvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    countryAttr = attrs["tvg-country"] ?: attrs["country"]
                }
                line.startsWith("#EXTGRP:", true) -> group = line.substringAfter(':').trim().ifBlank { "Diğer" }
                line.startsWith("#") -> Unit
                name != null -> {
                    if (line.startsWith("http://", true) || line.startsWith("https://", true) || line.startsWith("rtsp://", true)) {
                        val type = classify(group, name!!, line)
                        result += IptvChannel(name!!, line, group, logo, tvgId, detectCountry(countryAttr, group, name!!), type)
                    }
                    name = null; group = "Diğer"; logo = null; tvgId = null; countryAttr = null
                }
            }
        }
        return result
    }

    private fun classify(group: String, name: String, url: String): ContentType {
        val text = "$group $name".lowercase()
        if (seriesHints.any { text.contains(it) }) return ContentType.SERIES
        if (movieHints.any { text.contains(it) }) return ContentType.MOVIE
        val path = url.substringBefore('?').lowercase()
        if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".mov")) return ContentType.MOVIE
        return ContentType.LIVE
    }

    private fun detectCountry(explicit: String?, group: String, name: String): String {
        if (!explicit.isNullOrBlank()) return normalizeCountry(explicit)
        val haystack = "$group $name"
        return countries.firstOrNull { Regex("(^|[^\\p{L}])${Regex.escape(it)}([^\\p{L}]|$)", RegexOption.IGNORE_CASE).containsMatchIn(haystack) }
            ?.let(::normalizeCountry) ?: "Bilinmeyen ülke"
    }

    private fun normalizeCountry(value: String): String = when (value.trim().lowercase()) {
        "turkey", "turkiye" -> "Türkiye"
        "deutschland" -> "Almanya"
        "uk", "england" -> "İngiltere"
        "usa", "us" -> "ABD"
        "france" -> "Fransa"
        "italy" -> "İtalya"
        "spain" -> "İspanya"
        "netherlands" -> "Hollanda"
        "belgium" -> "Belçika"
        "austria" -> "Avusturya"
        "switzerland" -> "İsviçre"
        "russia" -> "Rusya"
        "azerbaijan" -> "Azerbaycan"
        else -> value.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
