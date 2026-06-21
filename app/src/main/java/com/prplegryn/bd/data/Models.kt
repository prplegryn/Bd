package com.prplegryn.bd.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class SourceType {
    VIDEO,
    BANGUMI,
    COURSE,
    FAVOURITE,
    WATCH_LATER,
    SPACE,
    COLLECTION,
    SERIES,
}

enum class TaskStatus {
    QUEUED,
    PREPARING,
    DOWNLOADING,
    MERGING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

enum class DanmakuFormat { ASS, XML }

data class Episode(
    val index: Int,
    val title: String,
    val aid: Long,
    val bvid: String,
    val cid: Long,
    val epId: Long? = null,
    val coverUrl: String = "",
    val durationSeconds: Int = 0,
) {
    companion object
}

data class SourceInfo(
    val sourceUrl: String,
    val type: SourceType,
    val title: String,
    val owner: String = "",
    val coverUrl: String = "",
    val description: String = "",
    val episodes: List<Episode>,
)

data class VideoStream(
    val id: Int,
    val codec: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long,
    val url: String,
    val backups: List<String>,
)

data class AudioStream(
    val id: Int,
    val codec: String,
    val bandwidth: Long,
    val url: String,
    val backups: List<String>,
)

data class ContentOptions(
    val video: Boolean = true,
    val audio: Boolean = true,
    val subtitles: Boolean = true,
    val danmaku: Boolean = true,
    val cover: Boolean = true,
    val saveCover: Boolean = false,
    val metadata: Boolean = false,
    val chapterInfo: Boolean = true,
    val danmakuFormat: DanmakuFormat = DanmakuFormat.ASS,
) {
    companion object
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val sourceUrl: String,
    val sourceTitle: String,
    val episode: Episode,
    val options: ContentOptions,
    val preferredVideoQuality: Int,
    val preferredAudioQuality: Int,
    val preferredCodec: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val speedBytesPerSecond: Long = 0,
    val outputName: String = "",
    val outputUri: String = "",
    val error: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("sourceUrl", sourceUrl)
        put("sourceTitle", sourceTitle)
        put("episode", episode.toJson())
        put("options", options.toJson())
        put("preferredVideoQuality", preferredVideoQuality)
        put("preferredAudioQuality", preferredAudioQuality)
        put("preferredCodec", preferredCodec)
        put("status", status.name)
        put("progress", progress)
        put("downloadedBytes", downloadedBytes)
        put("totalBytes", totalBytes)
        put("speedBytesPerSecond", speedBytesPerSecond)
        put("outputName", outputName)
        put("outputUri", outputUri)
        put("error", error)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(json: JSONObject) = DownloadTask(
            id = json.getString("id"),
            sourceUrl = json.optString("sourceUrl"),
            sourceTitle = json.optString("sourceTitle"),
            episode = Episode.fromJson(json.getJSONObject("episode")),
            options = ContentOptions.fromJson(json.optJSONObject("options") ?: JSONObject()),
            preferredVideoQuality = json.optInt("preferredVideoQuality", 127),
            preferredAudioQuality = json.optInt("preferredAudioQuality", 30280),
            preferredCodec = json.optString("preferredCodec", "avc"),
            status = runCatching { TaskStatus.valueOf(json.optString("status")) }
                .getOrDefault(TaskStatus.FAILED),
            progress = json.optInt("progress"),
            downloadedBytes = json.optLong("downloadedBytes"),
            totalBytes = json.optLong("totalBytes"),
            speedBytesPerSecond = json.optLong("speedBytesPerSecond"),
            outputName = json.optString("outputName"),
            outputUri = json.optString("outputUri"),
            error = json.optString("error"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        )
    }
}

fun Episode.toJson() = JSONObject().apply {
    put("index", index)
    put("title", title)
    put("aid", aid)
    put("bvid", bvid)
    put("cid", cid)
    put("epId", epId)
    put("coverUrl", coverUrl)
    put("durationSeconds", durationSeconds)
}

fun Episode.Companion.fromJson(json: JSONObject) = Episode(
    index = json.optInt("index", 1),
    title = json.optString("title", "未命名"),
    aid = json.optLong("aid"),
    bvid = json.optString("bvid"),
    cid = json.optLong("cid"),
    epId = if (json.has("epId") && !json.isNull("epId")) json.optLong("epId") else null,
    coverUrl = json.optString("coverUrl"),
    durationSeconds = json.optInt("durationSeconds"),
)

fun ContentOptions.toJson() = JSONObject().apply {
    put("video", video)
    put("audio", audio)
    put("subtitles", subtitles)
    put("danmaku", danmaku)
    put("cover", cover)
    put("saveCover", saveCover)
    put("metadata", metadata)
    put("chapterInfo", chapterInfo)
    put("danmakuFormat", danmakuFormat.name)
}

fun ContentOptions.Companion.fromJson(json: JSONObject) = ContentOptions(
    video = json.optBoolean("video", true),
    audio = json.optBoolean("audio", true),
    subtitles = json.optBoolean("subtitles", true),
    danmaku = json.optBoolean("danmaku", true),
    cover = json.optBoolean("cover", true),
    saveCover = json.optBoolean("saveCover", false),
    metadata = json.optBoolean("metadata", false),
    chapterInfo = json.optBoolean("chapterInfo", true),
    danmakuFormat = runCatching {
        DanmakuFormat.valueOf(json.optString("danmakuFormat", DanmakuFormat.ASS.name))
    }.getOrDefault(DanmakuFormat.ASS),
)

fun List<DownloadTask>.toJsonString(): String {
    val array = JSONArray()
    forEach { array.put(it.toJson()) }
    return array.toString()
}

fun tasksFromJson(value: String): List<DownloadTask> = runCatching {
    val array = JSONArray(value)
    buildList {
        repeat(array.length()) {
            add(DownloadTask.fromJson(array.getJSONObject(it)))
        }
    }
}.getOrDefault(emptyList())
