package com.tamay.iptv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesExtendedInfoAndStreamUrl() {
        val playlist = """#EXTM3U
#EXTINF:-1 tvg-id="news.tr" tvg-logo="https://example.test/logo.png" group-title="Haber",Örnek Haber
https://example.test/live/news.m3u8
""".trimIndent()

        val channels = M3uParser.parse(playlist)
        assertEquals(1, channels.size)
        assertEquals("Örnek Haber", channels[0].name)
        assertEquals("Haber", channels[0].groupTitle)
        assertEquals("https://example.test/logo.png", channels[0].logoUrl)
        assertEquals("news.tr", channels[0].tvgId)
        assertEquals("https://example.test/live/news.m3u8", channels[0].streamUrl)
    }

    @Test
    fun ignoresCommentsAndInvalidFollowingLines() {
        val playlist = """#EXTM3U
#EXTINF:-1,Test Kanal
#EXTVLCOPT:http-user-agent=Test
not-a-url
""".trimIndent()
        assertEquals(0, M3uParser.parse(playlist).size)
    }
}
