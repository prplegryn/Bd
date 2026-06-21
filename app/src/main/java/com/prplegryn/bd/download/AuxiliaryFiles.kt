package com.prplegryn.bd.download

import com.prplegryn.bd.data.Episode
import com.prplegryn.bd.data.UserSettings
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.util.Locale
import kotlin.math.max

object AuxiliaryFiles {
    fun subtitleJsonToSrt(json: JSONObject): String {
        val body = json.optJSONArray("body") ?: return ""
        return buildString {
            repeat(body.length()) { index ->
                val item = body.getJSONObject(index)
                appendLine(index + 1)
                append(formatTime(item.optDouble("from")))
                append(" --> ")
                appendLine(formatTime(item.optDouble("to")))
                appendLine(item.optString("content"))
                appendLine()
            }
        }
    }

    fun danmakuXmlToAss(xml: String, settings: UserSettings): String {
        val comments = parseDanmaku(xml, settings)
        val fontSize = if (settings.danmakuFontSize > 0) settings.danmakuFontSize else 36
        val alpha = ((1f - settings.danmakuOpacity.coerceIn(0f, 1f)) * 255)
            .toInt().coerceIn(0, 255)
        val alphaHex = "%02X".format(Locale.US, alpha)
        return buildString {
            appendLine("[Script Info]")
            appendLine("ScriptType: v4.00+")
            appendLine("PlayResX: 1920")
            appendLine("PlayResY: 1080")
            appendLine("WrapStyle: 2")
            appendLine("ScaledBorderAndShadow: yes")
            appendLine()
            appendLine("[V4+ Styles]")
            appendLine(
                "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, " +
                    "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, " +
                    "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, " +
                    "Alignment, MarginL, MarginR, MarginV, Encoding",
            )
            appendLine(
                "Style: Default,${settings.danmakuFont},$fontSize,&H${alphaHex}FFFFFF," +
                    "&H${alphaHex}FFFFFF,&H80000000,&H80000000,0,0,0,0,100,100,0,0,1,2,0,7,0,0,0,1",
            )
            appendLine()
            appendLine("[Events]")
            appendLine("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text")
            comments.forEachIndexed { index, comment ->
                val duration = max(4.0, 12.0 / settings.danmakuSpeed.coerceAtLeast(0.25f))
                val end = comment.time + duration
                val lane = index % max(1, (15 * settings.danmakuDisplayRegion).toInt())
                val y = 42 + lane * (fontSize + 8)
                val color = assColor(comment.color)
                val position = when (comment.mode) {
                    5 -> "{\\an8\\pos(960,$y)}"
                    4 -> "{\\an2\\pos(960,${1030 - y})}"
                    else -> "{\\move(1980,$y,-${comment.text.length * fontSize},$y)}"
                }
                append("Dialogue: 0,")
                append(formatAssTime(comment.time))
                append(',')
                append(formatAssTime(end))
                append(",Default,,0,0,0,,")
                append(position)
                if (comment.color != 0xFFFFFF) append("{\\c$color}")
                appendLine(escapeAss(comment.text))
            }
        }
    }

    fun metadataNfo(sourceTitle: String, episode: Episode): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        appendLine("<episodedetails>")
        appendLine("  <title>${escapeXml(episode.title)}</title>")
        appendLine("  <showtitle>${escapeXml(sourceTitle)}</showtitle>")
        appendLine("  <episode>${episode.index}</episode>")
        appendLine("  <uniqueid type=\"bvid\">${escapeXml(episode.bvid)}</uniqueid>")
        appendLine("</episodedetails>")
    }

    private fun parseDanmaku(xml: String, settings: UserSettings): List<Comment> {
        val blocked = settings.blockedKeywords.lines().map(String::trim).filter(String::isNotBlank)
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml))
        val result = mutableListOf<Comment>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "d") {
                val params = parser.getAttributeValue(null, "p").orEmpty().split(',')
                val text = parser.nextText()
                val mode = params.getOrNull(1)?.toIntOrNull() ?: 1
                val color = params.getOrNull(3)?.toIntOrNull() ?: 0xFFFFFF
                val blockedByMode =
                    (settings.blockTop && mode == 5) ||
                        (settings.blockBottom && mode == 4) ||
                        (settings.blockScroll && mode in 1..3) ||
                        (settings.blockColorful && color != 0xFFFFFF)
                val blockedByWord = blocked.any { keyword ->
                    runCatching { Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(text) }
                        .getOrElse { text.contains(keyword, ignoreCase = true) }
                }
                if (!blockedByMode && !blockedByWord) {
                    result += Comment(
                        time = params.firstOrNull()?.toDoubleOrNull() ?: 0.0,
                        mode = mode,
                        color = color,
                        text = text,
                    )
                }
            } else {
                parser.next()
            }
        }
        return result
    }

    private fun formatTime(seconds: Double): String {
        val millis = (seconds * 1000).toLong().coerceAtLeast(0)
        val hours = millis / 3_600_000
        val minutes = millis / 60_000 % 60
        val secs = millis / 1000 % 60
        val ms = millis % 1000
        return "%02d:%02d:%02d,%03d".format(Locale.US, hours, minutes, secs, ms)
    }

    private fun formatAssTime(seconds: Double): String {
        val centis = (seconds * 100).toLong().coerceAtLeast(0)
        return "%d:%02d:%02d.%02d".format(
            Locale.US,
            centis / 360_000,
            centis / 6_000 % 60,
            centis / 100 % 60,
            centis % 100,
        )
    }

    private fun assColor(rgb: Int): String {
        val r = rgb shr 16 and 0xFF
        val g = rgb shr 8 and 0xFF
        val b = rgb and 0xFF
        return "&H%02X%02X%02X&".format(Locale.US, b, g, r)
    }

    private fun escapeAss(value: String) = value
        .replace("\\", "\\\\")
        .replace("{", "\\{")
        .replace("}", "\\}")
        .replace("\n", "\\N")

    private fun escapeXml(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private data class Comment(
        val time: Double,
        val mode: Int,
        val color: Int,
        val text: String,
    )
}

