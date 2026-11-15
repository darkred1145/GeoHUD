package com.darkred1145.geohud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun newerPatchIsNewer() {
        assertTrue(Version.isRemoteNewer("0.6.2", "0.6.1"))
    }

    @Test
    fun sameVersionIsNotNewer() {
        assertFalse(Version.isRemoteNewer("0.6.1", "0.6.1"))
        assertFalse(Version.isRemoteNewer("v0.6.1", "0.6.1"))
    }

    @Test
    fun olderVersionIsNotNewer() {
        assertFalse(Version.isRemoteNewer("0.5.9", "0.6.1"))
    }

    @Test
    fun prereleaseSuffixDoesNotHideNewerBaseVersion() {
        assertTrue(Version.isRemoteNewer("v0.7.0-beta", "0.6.9"))
    }

    @Test
    fun missingSegmentsCountAsZero() {
        assertTrue(Version.isRemoteNewer("1.0", "0.9.9"))
        assertFalse(Version.isRemoteNewer("0.6.1", "0.6.1.1"))
    }

    @Test
    fun buildNumberMakesVersionNewer() {
        assertTrue(Version.isRemoteNewer("0.6.1.1", "0.6.1"))
    }
}
