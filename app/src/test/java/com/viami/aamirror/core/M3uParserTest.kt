package com.onurguner.ogauto.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    @Test
    fun parsesExtinfWithAttributesAndHeaders() {
        val text = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Haber, TV" group-title="Haber",Haber TV
            #EXTVLCOPT:http-user-agent=MyAgent/1.0
            #EXTVLCOPT:http-referrer=https://ref.example/
            http://host/live/1.m3u8
            #EXTINF:-1,Spor
            https://host/live/2.ts
        """.trimIndent()
        val channels = M3uParser.parse(text)
        assertEquals(2, channels.size)
        assertEquals("Haber TV", channels[0].title)
        assertEquals("Haber", channels[0].group)
        assertEquals("MyAgent/1.0", channels[0].headers["User-Agent"])
        assertEquals("https://ref.example/", channels[0].headers["Referer"])
        assertEquals("Spor", channels[1].title)
        assertTrue(channels[1].headers.isEmpty())
    }

    @Test
    fun bareUrlsBecomeChannelsAndJunkIsSkipped() {
        val channels = M3uParser.parse("﻿#EXTM3U\nnot a url\nhttp://a/b/c.mp4\n")
        assertEquals(1, channels.size)
        assertEquals("c.mp4", channels[0].title)
    }

    @Test
    fun recognisesPlaylistUrls() {
        assertTrue(M3uParser.isPlaylistUrl("http://h/list.m3u?x=1"))
        assertTrue(M3uParser.isPlaylistUrl("http://h/get.php?username=a&type=m3u_plus"))
        assertFalse(M3uParser.isPlaylistUrl("http://h/live/1.m3u8"))
    }
}
