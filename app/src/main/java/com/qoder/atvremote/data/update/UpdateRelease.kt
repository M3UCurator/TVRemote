package com.qoder.atvremote.data.update

import org.json.JSONArray
import org.json.JSONObject

data class UpdateRelease(
    val version: String,
    val tag: String,
    val releaseUrl: String,
    val apkUrl: String,
    val sizeBytes: Long,
    val notes: String,
)

object ReleaseVersion {

    /** True when [candidate] is a strictly newer version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val newer = numbers(candidate) ?: return false
        val installed = numbers(current) ?: return false
        for (index in 0 until maxOf(newer.size, installed.size)) {
            val a = newer.getOrElse(index) { 0 }
            val b = installed.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun numbers(value: String): List<Int>? {
        val core = value.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (core.isEmpty()) return null
        return core.split('.').map { it.toIntOrNull() ?: return null }
    }
}

object ReleaseParser {

    fun parse(json: String): UpdateRelease? = try {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) {
            null
        } else {
            val tag = root.optString("tag_name").takeIf { it.isNotBlank() }
            val asset = apkAsset(root.optJSONArray("assets") ?: JSONArray())
            if (tag == null || asset == null) {
                null
            } else {
                UpdateRelease(
                    version = tag.removePrefix("v").removePrefix("V"),
                    tag = tag,
                    releaseUrl = root.optString("html_url"),
                    apkUrl = asset.url,
                    sizeBytes = asset.size,
                    notes = root.optString("body"),
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun apkAsset(assets: JSONArray): ApkAsset? {
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue
            val url = asset.optString("browser_download_url")
            if (url.isNotBlank()) {
                return ApkAsset(url = url, size = asset.optLong("size", 0L))
            }
        }
        return null
    }

    private data class ApkAsset(val url: String, val size: Long)
}
