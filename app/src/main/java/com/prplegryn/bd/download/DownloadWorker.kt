package com.prplegryn.bd.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.prplegryn.bd.BdApplication
import com.prplegryn.bd.data.AudioStream
import com.prplegryn.bd.data.DownloadTask
import com.prplegryn.bd.data.TaskStatus
import com.prplegryn.bd.data.VideoStream
import com.prplegryn.bd.network.encodeQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlin.coroutines.coroutineContext

class DownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val app = context.applicationContext as BdApplication
    private val repository = app.tasks
    private val settings get() = app.preferences.settings.value

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val task = repository.find(id) ?: return Result.failure()
        DownloadGate.acquire(id, settings.parallelTasks)
        setForeground(createForeground(task, 0, "正在准备"))
        repository.update(id) { it.copy(status = TaskStatus.PREPARING, error = "") }

        val tempDir = File(applicationContext.filesDir, "downloads/$id").apply { mkdirs() }
        var completedSuccessfully = false
        return try {
            val play = app.client.playInfo(task.episode)
            val video = if (task.options.video) selectVideo(play.videos, task) else null
            val audio = if (task.options.audio) selectAudio(play.audios, task) else null
            if (task.options.video && video == null) throw IOException("没有符合条件的视频流")
            if (task.options.audio && audio == null) throw IOException("没有符合条件的音频流")

            val videoFile = video?.let { File(tempDir, "video.m4s") }
            val audioFile = audio?.let { File(tempDir, "audio.m4s") }
            val expectedTotal = listOfNotNull(
                video?.let { probeLength(listOf(it.url) + it.backups) },
                audio?.let { probeLength(listOf(it.url) + it.backups) },
            ).filter { it > 0 }.sum()
            var completedBytes = 0L

            repository.update(id) {
                it.copy(status = TaskStatus.DOWNLOADING, totalBytes = expectedTotal)
            }

            if (video != null && videoFile != null) {
                completedBytes += download(
                    task = task,
                    urls = listOf(video.url) + video.backups,
                    output = videoFile,
                    completedBefore = completedBytes,
                    expectedTotal = expectedTotal,
                )
            }
            if (audio != null && audioFile != null) {
                completedBytes += download(
                    task = task,
                    urls = listOf(audio.url) + audio.backups,
                    output = audioFile,
                    completedBefore = completedBytes,
                    expectedTotal = expectedTotal,
                )
            }

            val safeName = sanitizeFileName(task.episode.title)
            val outputStore = OutputStore(applicationContext, settings)
            var outputUri = ""
            var outputName = ""
            if (videoFile != null || audioFile != null) {
                repository.update(id) { it.copy(status = TaskStatus.MERGING, progress = 96) }
                setForeground(createForeground(task, 96, "正在封装媒体"))
                val extension = if (videoFile == null) "m4a" else "mp4"
                outputName = "$safeName.$extension"
                val output = File(tempDir, outputName)
                if (videoFile != null && audioFile != null) {
                    MediaMerger.merge(videoFile, audioFile, output)
                } else {
                    (videoFile ?: audioFile)?.copyTo(output, overwrite = true)
                }
                outputUri = outputStore.write(
                    output,
                    outputName,
                    if (extension == "mp4") "video/mp4" else "audio/mp4",
                ).toString()
            }

            downloadAuxiliaryFiles(task, safeName, outputStore)
            repository.update(id) {
                it.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 100,
                    downloadedBytes = completedBytes,
                    speedBytesPerSecond = 0,
                    outputName = outputName,
                    outputUri = outputUri,
                )
            }
            completedSuccessfully = true
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            repository.update(id) {
                it.copy(
                    status = TaskStatus.FAILED,
                    speedBytesPerSecond = 0,
                    error = error.message ?: "下载失败",
                )
            }
            Result.failure()
        } finally {
            if (completedSuccessfully) tempDir.deleteRecursively()
            DownloadGate.release(id)
        }
    }

    private suspend fun download(
        task: DownloadTask,
        urls: List<String>,
        output: File,
        completedBefore: Long,
        expectedTotal: Long,
    ): Long = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        for (url in urls.filter(String::isNotBlank)) {
            try {
                val existing = output.length()
                val request = app.client.request(url).newBuilder().apply {
                    if (existing > 0) header("Range", "bytes=$existing-")
                }.build()
                app.client.client().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("媒体下载失败：HTTP ${response.code}")
                    val body = response.body ?: throw IOException("媒体流为空")
                    val contentLength = body.contentLength().coerceAtLeast(0)
                    val append = existing > 0 && response.code == 206
                    val initial = if (append) existing else 0L
                    val total = if (expectedTotal > 0) {
                        expectedTotal
                    } else {
                        completedBefore + initial + contentLength
                    }
                    var downloaded = initial
                    var lastBytes = initial
                    var lastTime = System.currentTimeMillis()
                    RandomAccessFile(output, "rw").use { sink ->
                        if (append) sink.seek(initial) else sink.setLength(0)
                        body.byteStream().use { source ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                coroutineContext.ensureActive()
                                val read = source.read(buffer)
                                if (read < 0) break
                                sink.write(buffer, 0, read)
                                downloaded += read
                                val now = System.currentTimeMillis()
                                if (now - lastTime >= 500) {
                                    val speed = (downloaded - lastBytes) * 1000 / (now - lastTime)
                                    val all = completedBefore + downloaded
                                    val progress = if (total > 0) {
                                        (all * 94 / total).toInt().coerceIn(1, 94)
                                    } else {
                                        1
                                    }
                                    repository.update(task.id) {
                                        it.copy(
                                            status = TaskStatus.DOWNLOADING,
                                            progress = progress,
                                            downloadedBytes = all,
                                            totalBytes = total,
                                            speedBytesPerSecond = speed,
                                        )
                                    }
                                    setForeground(createForeground(task, progress, "正在下载"))
                                    lastBytes = downloaded
                                    lastTime = now
                                }
                            }
                        }
                    }
                    return@withContext downloaded
                }
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IOException("没有可用的下载地址")
    }

    private suspend fun probeLength(urls: List<String>): Long = withContext(Dispatchers.IO) {
        for (url in urls.filter(String::isNotBlank)) {
            val request = app.client.request(url).newBuilder()
                .header("Range", "bytes=0-0")
                .build()
            runCatching {
                app.client.client().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use 0L
                    val contentRange = response.header("Content-Range")
                    contentRange?.substringAfterLast('/')?.toLongOrNull()
                        ?: response.body?.contentLength()
                        ?: 0L
                }
            }.getOrNull()?.takeIf { it > 1 }?.let { return@withContext it }
        }
        0L
    }

    private suspend fun downloadAuxiliaryFiles(
        task: DownloadTask,
        safeName: String,
        output: OutputStore,
    ) {
        val episode = task.episode
        val query = buildString {
            if (episode.bvid.isNotBlank()) append("bvid=${episode.bvid}") else append("aid=${episode.aid}")
            append("&cid=${episode.cid}")
        }
        if (task.options.subtitles) {
            runCatching {
                val data = app.client.getJson("https://api.bilibili.com/x/player/wbi/v2?$query")
                    .optJSONObject("data")
                val subtitles = data?.optJSONObject("subtitle")?.optJSONArray("subtitles")
                if (subtitles != null) repeat(subtitles.length()) { index ->
                    val item = subtitles.getJSONObject(index)
                    val subtitleUrl = item.optString("subtitle_url")
                    if (subtitleUrl.isNotBlank()) {
                        val json = app.client.getJson(
                            if (subtitleUrl.startsWith("//")) "https:$subtitleUrl" else subtitleUrl,
                        )
                        val language = sanitizeFileName(item.optString("lan_doc").ifBlank { "字幕" })
                        output.writeText(
                            AuxiliaryFiles.subtitleJsonToSrt(json),
                            "$safeName.$language.srt",
                            "application/x-subrip",
                        )
                    }
                }
            }
        }
        if (task.options.danmaku) {
            runCatching {
                val xml = app.client.getText(
                    "https://api.bilibili.com/x/v1/dm/list.so?oid=${episode.cid}",
                )
                if (task.options.danmakuFormat == com.prplegryn.bd.data.DanmakuFormat.XML) {
                    output.writeText(xml, "$safeName.xml", "application/xml")
                } else {
                    output.writeText(
                        AuxiliaryFiles.danmakuXmlToAss(xml, settings),
                        "$safeName.ass",
                        "text/x-ssa",
                    )
                }
            }
        }
        if (task.options.cover && episode.coverUrl.isNotBlank()) {
            runCatching {
                output.writeBytes(
                    app.client.getBytes(episode.coverUrl),
                    "$safeName.jpg",
                    "image/jpeg",
                )
            }
        }
        if (task.options.metadata) {
            output.writeText(
                AuxiliaryFiles.metadataNfo(task.sourceTitle, episode),
                "$safeName.nfo",
                "application/xml",
            )
        }
        if (task.options.chapterInfo) {
            runCatching {
                val data = app.client.getJson(
                    "https://api.bilibili.com/x/player/v2?$query",
                ).optJSONObject("data")
                val viewpoint = data?.optJSONArray("view_points")
                if (viewpoint != null && viewpoint.length() > 0) {
                    output.writeText(
                        JSONObject().put("chapters", viewpoint).toString(2),
                        "$safeName.chapters.json",
                        "application/json",
                    )
                }
            }
        }
    }

    private fun selectVideo(streams: List<VideoStream>, task: DownloadTask): VideoStream? {
        val qualityOrder = qualityPriority(
            VIDEO_QUALITIES,
            task.preferredVideoQuality,
        )
        val codecOrder = listOf(task.preferredCodec, "avc", "hevc", "av1").distinct()
        return qualityOrder.firstNotNullOfOrNull { quality ->
            codecOrder.firstNotNullOfOrNull { codec ->
                streams.firstOrNull { it.id == quality && it.codec == codec }
            }
        } ?: streams.maxByOrNull { it.height }
    }

    private fun selectAudio(streams: List<AudioStream>, task: DownloadTask): AudioStream? {
        val qualityOrder = qualityPriority(AUDIO_QUALITIES, task.preferredAudioQuality)
        return qualityOrder.firstNotNullOfOrNull { quality ->
            streams.firstOrNull { it.id == quality }
        } ?: streams.maxByOrNull { it.bandwidth }
    }

    private fun qualityPriority(values: List<Int>, selected: Int): List<Int> {
        val index = values.indexOf(selected).takeIf { it >= 0 } ?: 0
        return (index until values.size).map(values::get) +
            (index - 1 downTo 0).map(values::get)
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim()
        .trimEnd('.')
        .take(120)
        .ifBlank { "未命名" }

    private fun createForeground(task: DownloadTask, progress: Int, text: String): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "下载任务", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(task.episode.title)
            .setContentText(text)
            .setOngoing(progress < 100)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                task.id.hashCode(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(task.id.hashCode(), notification)
        }
    }

    companion object {
        const val KEY_TASK_ID = "task_id"
        private const val CHANNEL_ID = "downloads"
        private val VIDEO_QUALITIES = listOf(127, 126, 125, 120, 116, 112, 100, 80, 74, 64, 32, 16)
        private val AUDIO_QUALITIES = listOf(30251, 30255, 30250, 30280, 30232, 30216)
    }
}
