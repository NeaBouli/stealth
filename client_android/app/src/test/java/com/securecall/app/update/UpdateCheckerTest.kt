package com.securecall.app.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateCheckerTest {

    private val TEST_FLAVOR = "free"
    private val TEST_VERSION_CODE = 50

    private fun buildRelease(
        tagName: String = "v1.0.29",
        name: String = "SecureCall v1.0.29",
        body: String = "",
        assets: List<Triple<String, String, Long>> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("tag_name", tagName)
        root.put("name", name)
        root.put("html_url", "https://github.com/NeaBouli/stealth/releases/tag/$tagName")
        root.put("body", body)
        val arr = JSONArray()
        for ((assetName, url, size) in assets) {
            val a = JSONObject()
            a.put("name", assetName)
            a.put("browser_download_url", url)
            a.put("size", size)
            arr.put(a)
        }
        root.put("assets", arr)
        return root.toString()
    }

    private fun parse(json: String) =
        UpdateChecker.parseRelease(json, TEST_FLAVOR, TEST_VERSION_CODE)

    @Test
    fun `asset with vC pattern is parsed correctly`() {
        val json = buildRelease(
            body = "Release notes",
            assets = listOf(
                Triple("securecall-free-v1.0.29-vC99.apk", "https://example.com/free.apk", 80_000_000L)
            )
        )
        val result = parse(json)
        assertNotNull(result)
        assertEquals(99, result!!.versionCode)
        assertEquals("https://example.com/free.apk", result.apkUrl)
    }

    @Test
    fun `asset without vC falls back to body versionCode`() {
        val json = buildRelease(
            body = "Security fixes. versionCode: 51",
            assets = listOf(
                Triple("app-free-arm64-v8a-release.apk", "https://example.com/free.apk", 80_000_000L)
            )
        )
        val result = parse(json)
        assertNotNull(result)
        assertEquals(51, result!!.versionCode)
        assertEquals("https://example.com/free.apk", result.apkUrl)
    }

    @Test
    fun `asset without vC and body with vC shorthand`() {
        val json = buildRelease(
            body = "Changelog here. vC55.",
            assets = listOf(
                Triple("app-free-release.apk", "https://example.com/free.apk", 70_000_000L)
            )
        )
        val result = parse(json)
        assertNotNull(result)
        assertEquals(55, result!!.versionCode)
    }

    @Test
    fun `asset without vC and no body versionCode returns null`() {
        val json = buildRelease(
            body = "Just some notes without version info",
            assets = listOf(
                Triple("app-free-release.apk", "https://example.com/free.apk", 70_000_000L)
            )
        )
        val result = parse(json)
        assertNull(result)
    }

    @Test
    fun `non-matching flavor is skipped`() {
        val json = buildRelease(
            body = "vC99",
            assets = listOf(
                Triple("app-premium-v1.0.29-vC99.apk", "https://example.com/premium.apk", 80_000_000L)
            )
        )
        val result = parse(json)
        assertNull(result)
    }

    @Test
    fun `multiple APKs picks highest vC`() {
        val json = buildRelease(
            assets = listOf(
                Triple("securecall-free-v1.0.28-vC51.apk", "https://example.com/old.apk", 70_000_000L),
                Triple("securecall-free-v1.0.29-vC55.apk", "https://example.com/new.apk", 80_000_000L)
            )
        )
        val result = parse(json)
        assertNotNull(result)
        assertEquals(55, result!!.versionCode)
        assertEquals("https://example.com/new.apk", result.apkUrl)
    }

    @Test
    fun `empty assets returns null`() {
        val json = buildRelease(assets = emptyList())
        val result = parse(json)
        assertNull(result)
    }

    @Test
    fun `aab files are ignored`() {
        val json = buildRelease(
            body = "vC55",
            assets = listOf(
                Triple("app-free-release.aab", "https://example.com/free.aab", 90_000_000L)
            )
        )
        val result = parse(json)
        assertNull(result)
    }

    @Test
    fun `current version code is not treated as update`() {
        val json = buildRelease(
            body = "vC50",
            assets = listOf(
                Triple("app-free-release.apk", "https://example.com/free.apk", 70_000_000L)
            )
        )
        val result = parse(json)
        assertNull(result)
    }
}
