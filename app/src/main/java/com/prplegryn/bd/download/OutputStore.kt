package com.prplegryn.bd.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.prplegryn.bd.data.UserSettings
import java.io.File
import java.io.IOException

class OutputStore(
    private val context: Context,
    private val settings: UserSettings,
) {
    fun write(source: File, requestedName: String, mime: String): Uri {
        return if (settings.storageTreeUri.isNotBlank()) {
            writeToTree(source, requestedName, mime)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToDownloads(source, requestedName, mime)
        } else {
            writeToLegacyDownloads(source, requestedName)
        }
    }

    fun writeText(text: String, requestedName: String, mime: String): Uri {
        val temp = File.createTempFile("bd-aux-", ".tmp", context.cacheDir)
        return try {
            temp.writeText(text)
            write(temp, requestedName, mime)
        } finally {
            temp.delete()
        }
    }

    fun writeBytes(bytes: ByteArray, requestedName: String, mime: String): Uri {
        val temp = File.createTempFile("bd-aux-", ".tmp", context.cacheDir)
        return try {
            temp.writeBytes(bytes)
            write(temp, requestedName, mime)
        } finally {
            temp.delete()
        }
    }

    private fun writeToTree(source: File, requestedName: String, mime: String): Uri {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(settings.storageTreeUri))
            ?: throw IOException("保存目录不可用")
        val folder = root.findFile("Bd") ?: root.createDirectory("Bd")
            ?: throw IOException("无法创建 Bd 目录")
        val name = resolveDocumentName(folder, requestedName)
        val document = folder.createFile(mime, name) ?: throw IOException("无法创建输出文件")
        context.contentResolver.openOutputStream(document.uri, "w")?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: throw IOException("无法写入输出文件")
        return document.uri
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToDownloads(source: File, requestedName: String, mime: String): Uri {
        val name = resolveMediaName(requestedName)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOWNLOADS}/Bd",
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建系统下载文件")
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                source.inputStream().use { it.copyTo(output) }
            } ?: throw IOException("无法写入系统下载文件")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun writeToLegacyDownloads(source: File, requestedName: String): Uri {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val folder = File(base, "Bd").apply { mkdirs() }
        val target = resolveLegacyName(folder, requestedName)
        source.copyTo(target, overwrite = settings.overwrite)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.files",
            target,
        )
    }

    private fun resolveDocumentName(folder: DocumentFile, requested: String): String {
        if (settings.overwrite) {
            folder.findFile(requested)?.delete()
            return requested
        }
        if (folder.findFile(requested) == null) return requested
        val base = requested.substringBeforeLast('.', requested)
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 2
        while (folder.findFile("$base ($index)$extension") != null) index++
        return "$base ($index)$extension"
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun resolveMediaName(requested: String): String {
        if (settings.overwrite) {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.DISPLAY_NAME}=?",
                arrayOf(requested),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = Uri.withAppendedPath(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        cursor.getLong(0).toString(),
                    )
                    context.contentResolver.delete(uri, null, null)
                }
            }
        }
        return requested
    }

    private fun resolveLegacyName(folder: File, requested: String): File {
        val direct = File(folder, requested)
        if (settings.overwrite || !direct.exists()) return direct
        val base = requested.substringBeforeLast('.', requested)
        val extension = requested.substringAfterLast('.', "").let { if (it.isBlank()) "" else ".$it" }
        var index = 2
        var candidate: File
        do {
            candidate = File(folder, "$base ($index)$extension")
            index++
        } while (candidate.exists())
        return candidate
    }
}
