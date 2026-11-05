package io.github.orth4x0r.venty.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
    @Test fun parsesTagsAndNames() {
        assertEquals(listOf(0, 3, 1), UpdateVersion.parse("v0.3.1"))
        assertEquals(listOf(0, 3, 1), UpdateVersion.parse("0.3.1"))
        assertEquals(listOf(1, 0), UpdateVersion.parse("V1.0-beta"))
        assertNull(UpdateVersion.parse("nightly"))
        assertNull(UpdateVersion.parse(""))
    }

    @Test fun comparesNumericallyNotAlphabetically() {
        assertTrue(UpdateVersion.isNewer("v0.10.0", "0.9.9"))
        assertTrue(UpdateVersion.isNewer("v0.3.2", "0.3.1"))
        assertTrue(UpdateVersion.isNewer("v0.3.1.1", "0.3.1"))
        assertFalse(UpdateVersion.isNewer("v0.3.1", "0.3.1"))
        assertFalse(UpdateVersion.isNewer("v0.3.0", "0.3.1"))
        assertFalse(UpdateVersion.isNewer("v0.3", "0.3.0"))
    }

    @Test fun garbageIsNeverAnUpdate() {
        assertFalse(UpdateVersion.isNewer("latest", "0.3.1"))
    }

    @Test fun picksThePhoneApkNotTheWatchOne() {
        assertEquals(1, UpdateVersion.pickPhoneApk(listOf("venty-wear-v0.3.0.apk", "venty-v0.3.0.apk")))
        assertEquals(0, UpdateVersion.pickPhoneApk(listOf("app-release.apk", "notes.txt")))
        assertEquals(-1, UpdateVersion.pickPhoneApk(listOf("venty-wear-v0.3.0.apk", "source.zip")))
    }

    @Test fun flattensMarkdownNotes() {
        val md = "## What's new\r\n\r\n- **Ramp** mode\r\n* See [the docs](https://x)\r\n\r\n\r\n\r\n`code`"
        assertEquals("What's new\n\n• Ramp mode\n• See the docs\n\ncode", UpdateVersion.plainNotes(md))
    }
}
