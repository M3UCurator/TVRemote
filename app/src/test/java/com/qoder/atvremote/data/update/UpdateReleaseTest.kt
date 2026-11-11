package com.qoder.atvremote.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateReleaseTest {

    @Test
    fun `patch release is newer`() {
        assertTrue(ReleaseVersion.isNewer("1.0.1", "1.0.0"))
    }

    @Test
    fun `same version is not newer`() {
        assertFalse(ReleaseVersion.isNewer("1.0.0", "1.0.0"))
    }

    @Test
    fun `older version is not newer`() {
        assertFalse(ReleaseVersion.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `tag prefix and missing components are tolerated`() {
        assertTrue(ReleaseVersion.isNewer("v2.0", "1.9.9"))
        assertFalse(ReleaseVersion.isNewer("v1.0", "1.0.0"))
    }

    @Test
    fun `suffixes are ignored`() {
        assertTrue(ReleaseVersion.isNewer("1.1.0-debug", "1.0.0"))
    }

    @Test
    fun `unparseable versions never report an update`() {
        assertFalse(ReleaseVersion.isNewer("nightly", "1.0.0"))
        assertFalse(ReleaseVersion.isNewer("1.0.1", "unknown"))
    }

    @Test
    fun `latest release json is parsed with its apk asset`() {
        val release = ReleaseParser.parse(RELEASE_JSON)

        assertEquals("1.0.1", release?.version)
        assertEquals("v1.0.1", release?.tag)
        assertEquals("https://github.com/M3UCurator/TVRemote/releases/tag/v1.0.1", release?.releaseUrl)
        assertEquals("https://github.com/M3UCurator/TVRemote/releases/download/v1.0.1/TVRemote-1.0.1.apk", release?.apkUrl)
        assertEquals(4_755_753L, release?.sizeBytes)
    }

    @Test
    fun `release without apk asset is ignored`() {
        assertNull(ReleaseParser.parse(RELEASE_JSON.replace("TVRemote-1.0.1.apk", "TVRemote-1.0.1.aab")))
    }

    @Test
    fun `prerelease is ignored`() {
        assertNull(ReleaseParser.parse(RELEASE_JSON.replace("\"prerelease\": false", "\"prerelease\": true")))
    }

    @Test
    fun `malformed json is ignored`() {
        assertNull(ReleaseParser.parse("<html>not json</html>"))
    }

    private companion object {
        val RELEASE_JSON = """
            {
              "tag_name": "v1.0.1",
              "html_url": "https://github.com/M3UCurator/TVRemote/releases/tag/v1.0.1",
              "body": "Fixes and improvements.",
              "draft": false,
              "prerelease": false,
              "assets": [
                {
                  "name": "checksums.txt",
                  "size": 128,
                  "browser_download_url": "https://github.com/M3UCurator/TVRemote/releases/download/v1.0.1/checksums.txt"
                },
                {
                  "name": "TVRemote-1.0.1.apk",
                  "size": 4755753,
                  "browser_download_url": "https://github.com/M3UCurator/TVRemote/releases/download/v1.0.1/TVRemote-1.0.1.apk"
                }
              ]
            }
        """.trimIndent()
    }
}
