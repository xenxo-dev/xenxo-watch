package io.xenxo.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicModelsTest {

    @Test
    fun testTrackFormatting() {
        val t1 = Track(
            id = "t1",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            durationSec = 355,
            filePath = "/data/music/track1.mp3",
            addedAt = 1000L,
            fileSizeBytes = 5 * 1024 * 1024L
        )

        assertEquals("5:55", t1.formattedDuration)
        assertEquals("5.0 MB", t1.formattedSize)

        val t2 = Track(
            id = "t2",
            title = "Short Intro",
            artist = "Artist",
            durationSec = 45,
            filePath = "/data/music/track2.mp3",
            addedAt = 2000L,
            fileSizeBytes = 512 * 1024L
        )

        assertEquals("0:45", t2.formattedDuration)
        assertEquals("0.5 MB", t2.formattedSize)
    }

    @Test
    fun testZeroDuration() {
        val t = Track(
            id = "t0",
            title = "Unknown",
            artist = "Unknown",
            durationSec = 0,
            filePath = "/data/music/unknown.mp3",
            addedAt = 1000L
        )
        assertEquals("--:--", t.formattedDuration)
    }
}
