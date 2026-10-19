package com.onurguner.ogauto.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V3SplitPrefsTest {
    @Test fun allowsWebUrls() {
        assertTrue(V3SplitPrefs.isSafeWebUrl("https://www.google.com/maps"))
        assertTrue(V3SplitPrefs.isSafeWebUrl("http://localhost:8080/"))
    }

    @Test fun rejectsNonWebAndMalformedUrls() {
        assertFalse(V3SplitPrefs.isSafeWebUrl("javascript:alert(1)"))
        assertFalse(V3SplitPrefs.isSafeWebUrl("file:///sdcard/example"))
        assertFalse(V3SplitPrefs.isSafeWebUrl("https://"))
        assertFalse(V3SplitPrefs.isSafeWebUrl("https://user:pass@example.org"))
    }
}
