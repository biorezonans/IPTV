package com.tamay.iptv.data

enum class ContentType { LIVE, MOVIE, SERIES, UNKNOWN }

data class IptvChannel(
    val name: String,
    val streamUrl: String,
    val groupTitle: String = "Diğer",
    val logoUrl: String? = null,
    val tvgId: String? = null,
    val country: String = "Bilinmeyen ülke",
    val contentType: ContentType = ContentType.LIVE
)
