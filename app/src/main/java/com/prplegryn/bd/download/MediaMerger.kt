package com.prplegryn.bd.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

object MediaMerger {
    fun merge(video: File?, audio: File?, output: File) {
        require(video != null || audio != null)
        val sources = listOfNotNull(video, audio)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val tracks = mutableListOf<TrackSource>()
        try {
            sources.forEach { file ->
                val extractor = MediaExtractor()
                extractor.setDataSource(file.absolutePath)
                repeat(extractor.trackCount) { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    val wanted = if (file == video) mime.startsWith("video/") else mime.startsWith("audio/")
                    if (wanted) {
                        tracks += TrackSource(
                            extractor = extractor,
                            sourceTrack = index,
                            targetTrack = muxer.addTrack(format),
                            bufferSize = format.integerOrNull(MediaFormat.KEY_MAX_INPUT_SIZE)
                                ?.coerceAtLeast(DEFAULT_BUFFER_SIZE)
                                ?: DEFAULT_BUFFER_SIZE,
                        )
                        return@forEach
                    }
                }
                extractor.release()
            }
            check(tracks.isNotEmpty()) { "媒体文件中没有可合并的轨道" }
            muxer.start()
            tracks.forEach { copyTrack(it, muxer) }
        } finally {
            tracks.map { it.extractor }.distinct().forEach { runCatching { it.release() } }
            runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun copyTrack(track: TrackSource, muxer: MediaMuxer) {
        val extractor = track.extractor
        extractor.selectTrack(track.sourceTrack)
        val buffer = ByteBuffer.allocateDirect(track.bufferSize)
        val info = MediaCodec.BufferInfo()
        while (true) {
            buffer.clear()
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime.coerceAtLeast(0)
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(track.targetTrack, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(track.sourceTrack)
    }

    private fun MediaFormat.integerOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null

    private data class TrackSource(
        val extractor: MediaExtractor,
        val sourceTrack: Int,
        val targetTrack: Int,
        val bufferSize: Int,
    )

    private const val DEFAULT_BUFFER_SIZE = 2 * 1024 * 1024
}

