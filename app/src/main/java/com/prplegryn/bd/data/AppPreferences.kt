package com.prplegryn.bd.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserSettings(
    val videoQuality: Int = 127,
    val audioQuality: Int = 30280,
    val videoCodec: String = "avc",
    val parallelTasks: Int = 2,
    val overwrite: Boolean = false,
    val downloadIntervalSeconds: Int = 0,
    val storageTreeUri: String = "",
    val content: ContentOptions = ContentOptions(),
    val danmakuFont: String = "sans-serif",
    val danmakuFontSize: Int = 0,
    val danmakuOpacity: Float = 0.8f,
    val danmakuDisplayRegion: Float = 1f,
    val danmakuSpeed: Float = 1f,
    val blockTop: Boolean = false,
    val blockBottom: Boolean = false,
    val blockScroll: Boolean = false,
    val blockColorful: Boolean = false,
    val blockedKeywords: String = "",
)

class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val authPrefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(readSettings())

    val settings = _settings.asStateFlow()

    var cookie: String
        get() = authPrefs.getString("cookie", "") ?: ""
        set(value) {
            authPrefs.edit().putString("cookie", value).apply()
        }

    var accountName: String
        get() = authPrefs.getString("account_name", "") ?: ""
        set(value) {
            authPrefs.edit().putString("account_name", value).apply()
        }

    fun update(transform: (UserSettings) -> UserSettings) {
        val value = transform(_settings.value)
        prefs.edit()
            .putInt("videoQuality", value.videoQuality)
            .putInt("audioQuality", value.audioQuality)
            .putString("videoCodec", value.videoCodec)
            .putInt("parallelTasks", value.parallelTasks)
            .putBoolean("overwrite", value.overwrite)
            .putInt("downloadIntervalSeconds", value.downloadIntervalSeconds)
            .putString("storageTreeUri", value.storageTreeUri)
            .putString("content", value.content.toJson().toString())
            .putString("danmakuFont", value.danmakuFont)
            .putInt("danmakuFontSize", value.danmakuFontSize)
            .putFloat("danmakuOpacity", value.danmakuOpacity)
            .putFloat("danmakuDisplayRegion", value.danmakuDisplayRegion)
            .putFloat("danmakuSpeed", value.danmakuSpeed)
            .putBoolean("blockTop", value.blockTop)
            .putBoolean("blockBottom", value.blockBottom)
            .putBoolean("blockScroll", value.blockScroll)
            .putBoolean("blockColorful", value.blockColorful)
            .putString("blockedKeywords", value.blockedKeywords)
            .apply()
        _settings.value = value
    }

    fun setStorageTree(uri: Uri?) = update { it.copy(storageTreeUri = uri?.toString().orEmpty()) }

    fun clearAuth() {
        authPrefs.edit().clear().apply()
    }

    private fun readSettings(): UserSettings {
        val defaults = UserSettings()
        return UserSettings(
            videoQuality = prefs.getInt("videoQuality", defaults.videoQuality),
            audioQuality = prefs.getInt("audioQuality", defaults.audioQuality),
            videoCodec = prefs.getString("videoCodec", defaults.videoCodec) ?: defaults.videoCodec,
            parallelTasks = prefs.getInt("parallelTasks", defaults.parallelTasks),
            overwrite = prefs.getBoolean("overwrite", defaults.overwrite),
            downloadIntervalSeconds = prefs.getInt(
                "downloadIntervalSeconds",
                defaults.downloadIntervalSeconds,
            ),
            storageTreeUri = prefs.getString("storageTreeUri", "") ?: "",
            content = ContentOptions.fromJson(
                runCatching {
                    org.json.JSONObject(prefs.getString("content", "{}") ?: "{}")
                }.getOrDefault(org.json.JSONObject()),
            ),
            danmakuFont = prefs.getString("danmakuFont", defaults.danmakuFont)
                ?: defaults.danmakuFont,
            danmakuFontSize = prefs.getInt("danmakuFontSize", defaults.danmakuFontSize),
            danmakuOpacity = prefs.getFloat("danmakuOpacity", defaults.danmakuOpacity),
            danmakuDisplayRegion = prefs.getFloat(
                "danmakuDisplayRegion",
                defaults.danmakuDisplayRegion,
            ),
            danmakuSpeed = prefs.getFloat("danmakuSpeed", defaults.danmakuSpeed),
            blockTop = prefs.getBoolean("blockTop", defaults.blockTop),
            blockBottom = prefs.getBoolean("blockBottom", defaults.blockBottom),
            blockScroll = prefs.getBoolean("blockScroll", defaults.blockScroll),
            blockColorful = prefs.getBoolean("blockColorful", defaults.blockColorful),
            blockedKeywords = prefs.getString("blockedKeywords", "") ?: "",
        )
    }
}

