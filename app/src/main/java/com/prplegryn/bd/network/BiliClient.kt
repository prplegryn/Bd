package com.prplegryn.bd.network

import android.net.Uri
import com.prplegryn.bd.data.AppPreferences
import com.prplegryn.bd.data.AudioStream
import com.prplegryn.bd.data.Episode
import com.prplegryn.bd.data.SourceInfo
import com.prplegryn.bd.data.SourceType
import com.prplegryn.bd.data.VideoStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class PlayInfo(
    val videos: List<VideoStream>,
    val audios: List<AudioStream>,
)

data class AccountInfo(
    val loggedIn: Boolean,
    val name: String,
    val vip: Boolean,
)

class BiliClient(private val preferences: AppPreferences) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun resolve(input: String): SourceInfo = withContext(Dispatchers.IO) {
        val normalized = normalize(input)
        val redirected = if (normalized.contains("b23.tv")) resolveRedirect(normalized) else normalized
        when {
            isCourse(redirected) -> resolveCourse(redirected)
            isBangumi(redirected) -> resolveBangumi(redirected)
            isWatchLater(redirected) -> resolveWatchLater(redirected)
            isFavourite(redirected) -> resolveFavourite(redirected)
            isSpaceVideos(redirected) -> resolveSpaceVideos(redirected)
            isCollection(redirected) -> resolveCollection(redirected)
            isSeries(redirected) -> resolveSeries(redirected)
            else -> resolveVideo(redirected)
        }
    }

    suspend fun playInfo(episode: Episode): PlayInfo = withContext(Dispatchers.IO) {
        val ugcUrl = buildString {
            append("https://api.bilibili.com/x/player/playurl?")
            append(if (episode.bvid.isNotBlank()) "bvid=${episode.bvid}" else "avid=${episode.aid}")
            append("&cid=${episode.cid}&qn=127&fnver=0&fnval=4048&fourk=1&otype=json")
        }
        val pgcUrl = buildString {
            append("https://api.bilibili.com/pgc/player/web/playurl?")
            append("avid=${episode.aid}&cid=${episode.cid}&qn=127&fnver=0&fnval=4048&fourk=1")
            episode.epId?.let { append("&ep_id=$it") }
        }
        val candidates = if (episode.epId != null) listOf(pgcUrl, ugcUrl) else listOf(ugcUrl)
        var lastError: Throwable? = null
        for (url in candidates) {
            try {
                val data = checkedData(getJson(url))
                val dash = data.optJSONObject("dash") ?: continue
                return@withContext parseDash(dash)
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IOException("没有可用的 DASH 媒体流")
    }

    suspend fun accountInfo(): AccountInfo = withContext(Dispatchers.IO) {
        val json = getJson("https://api.bilibili.com/x/web-interface/nav")
        val data = json.optJSONObject("data") ?: return@withContext AccountInfo(false, "", false)
        AccountInfo(
            loggedIn = data.optBoolean("isLogin"),
            name = data.optString("uname"),
            vip = data.optJSONObject("vipStatus")?.optInt("status") == 1 ||
                data.optInt("vipStatus") == 1,
        )
    }

    suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        JSONObject(getText(url))
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        execute(url).use { response ->
            if (!response.isSuccessful) throw IOException("网络请求失败：HTTP ${response.code}")
            response.body?.string() ?: throw IOException("服务器返回空内容")
        }
    }

    suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        execute(url).use { response ->
            if (!response.isSuccessful) throw IOException("网络请求失败：HTTP ${response.code}")
            response.body?.bytes() ?: throw IOException("服务器返回空内容")
        }
    }

    fun request(url: String): Request = Request.Builder()
        .url(url.replace("http://", "https://"))
        .header("User-Agent", USER_AGENT)
        .header("Referer", "https://www.bilibili.com/")
        .header("Origin", "https://www.bilibili.com")
        .apply {
            preferences.cookie.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
        }
        .build()

    fun client(): OkHttpClient = http

    private fun execute(url: String) = http.newCall(request(url)).execute()

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            Regex("(?i)^BV[0-9A-Za-z]+(?:\\?p=\\d+)?$").matches(trimmed) ->
                "https://www.bilibili.com/video/$trimmed"
            Regex("(?i)^av\\d+(?:\\?p=\\d+)?$").matches(trimmed) ->
                "https://www.bilibili.com/video/$trimmed"
            Regex("(?i)^ep\\d+$").matches(trimmed) ->
                "https://www.bilibili.com/bangumi/play/$trimmed"
            Regex("(?i)^ss\\d+$").matches(trimmed) ->
                "https://www.bilibili.com/bangumi/play/$trimmed"
            Regex("(?i)^md\\d+$").matches(trimmed) ->
                "https://www.bilibili.com/bangumi/media/$trimmed"
            else -> throw IllegalArgumentException("无法识别该链接或编号")
        }
    }

    private fun resolveRedirect(url: String): String {
        execute(url).use { response -> return response.request.url.toString() }
    }

    private fun resolveVideo(url: String): SourceInfo {
        val uri = Uri.parse(url)
        val id = Regex("(?i)/(BV[0-9A-Za-z]+|av\\d+)").find(uri.path.orEmpty())
            ?.groupValues?.get(1)
            ?: Regex("(?i)(BV[0-9A-Za-z]+|av\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("链接中没有视频编号")
        val query = if (id.startsWith("av", true)) {
            "aid=${id.drop(2)}"
        } else {
            "bvid=$id"
        }
        val data = checkedData(getJsonBlocking("https://api.bilibili.com/x/web-interface/view?$query"))
        val pages = data.optJSONArray("pages") ?: JSONArray()
        val selectedPage = uri.getQueryParameter("p")?.toIntOrNull()
        val episodes = buildList {
            repeat(pages.length()) { position ->
                val page = pages.getJSONObject(position)
                if (selectedPage == null || selectedPage == position + 1 || pages.length() == 1) {
                    add(
                        Episode(
                            index = position + 1,
                            title = page.optString("part").ifBlank { data.optString("title") },
                            aid = data.optLong("aid"),
                            bvid = data.optString("bvid"),
                            cid = page.optLong("cid"),
                            coverUrl = page.optString("first_frame").ifBlank { data.optString("pic") },
                            durationSeconds = page.optInt("duration"),
                        ),
                    )
                }
            }
        }
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.VIDEO,
            title = data.optString("title"),
            owner = data.optJSONObject("owner")?.optString("name").orEmpty(),
            coverUrl = data.optString("pic"),
            description = data.optString("desc"),
            episodes = episodes,
        )
    }

    private fun resolveBangumi(url: String): SourceInfo {
        val epId = Regex("(?i)ep(\\d+)").find(url)?.groupValues?.get(1)
        val seasonId = Regex("(?i)ss(\\d+)").find(url)?.groupValues?.get(1)
        val mediaId = Regex("(?i)md(\\d+)").find(url)?.groupValues?.get(1)
        val api = when {
            epId != null -> "https://api.bilibili.com/pgc/view/web/season?ep_id=$epId"
            seasonId != null -> "https://api.bilibili.com/pgc/view/web/season?season_id=$seasonId"
            mediaId != null -> "https://api.bilibili.com/pgc/review/user?media_id=$mediaId"
            else -> throw IllegalArgumentException("番剧链接缺少 EP、SS 或 MD 编号")
        }
        val firstData = checkedData(getJsonBlocking(api))
        val data = if (mediaId != null) {
            val ss = firstData.optJSONObject("media")?.optLong("season_id")
                ?: throw IOException("无法解析番剧季度")
            checkedData(getJsonBlocking("https://api.bilibili.com/pgc/view/web/season?season_id=$ss"))
        } else {
            firstData
        }
        val all = mutableListOf<JSONObject>()
        data.optJSONArray("episodes")?.let { array ->
            repeat(array.length()) { all += array.getJSONObject(it) }
        }
        data.optJSONArray("section")?.let { sections ->
            repeat(sections.length()) { sectionIndex ->
                sections.getJSONObject(sectionIndex).optJSONArray("episodes")?.let { array ->
                    repeat(array.length()) { all += array.getJSONObject(it) }
                }
            }
        }
        val episodes = all.mapIndexed { index, item ->
            Episode(
                index = index + 1,
                title = item.optString("show_title").ifBlank {
                    item.optString("long_title").ifBlank { item.optString("title") }
                },
                aid = item.optLong("aid"),
                bvid = item.optString("bvid"),
                cid = item.optLong("cid"),
                epId = item.optLong("id"),
                coverUrl = item.optString("cover"),
                durationSeconds = item.optInt("duration") / 1000,
            )
        }
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.BANGUMI,
            title = data.optString("season_title").ifBlank { data.optString("title") },
            coverUrl = data.optString("cover"),
            description = data.optString("evaluate"),
            episodes = episodes,
        )
    }

    private fun resolveCourse(url: String): SourceInfo {
        val epId = Regex("(?i)ep(\\d+)").find(url)?.groupValues?.get(1)
        val seasonId = Regex("(?i)ss(\\d+)").find(url)?.groupValues?.get(1)
        val query = if (epId != null) "ep_id=$epId" else "season_id=$seasonId"
        val data = checkedData(
            getJsonBlocking("https://api.bilibili.com/pugv/view/web/season?$query"),
        )
        val array = data.optJSONArray("episodes") ?: JSONArray()
        val episodes = buildList {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    Episode(
                        index = index + 1,
                        title = item.optString("title"),
                        aid = item.optLong("aid"),
                        bvid = item.optString("bvid"),
                        cid = item.optLong("cid"),
                        epId = item.optLong("id"),
                        coverUrl = item.optString("cover"),
                        durationSeconds = item.optInt("duration"),
                    ),
                )
            }
        }
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.COURSE,
            title = data.optString("title"),
            coverUrl = data.optString("cover"),
            description = data.optString("subtitle"),
            episodes = episodes,
        )
    }

    private fun resolveWatchLater(url: String): SourceInfo {
        val data = checkedData(getJsonBlocking("https://api.bilibili.com/x/v2/history/toview"))
        val list = data.optJSONArray("list") ?: JSONArray()
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.WATCH_LATER,
            title = "稍后再看",
            episodes = flattenArchives(list),
        )
    }

    private fun resolveFavourite(url: String): SourceInfo {
        val uri = Uri.parse(url)
        val fid = uri.getQueryParameter("fid")
        if (fid == null) return resolveAllFavourites(url)
        val info = checkedData(
            getJsonBlocking("https://api.bilibili.com/x/v3/fav/folder/info?media_id=$fid"),
        )
        val result = mutableListOf<Episode>()
        var page = 1
        var hasMore: Boolean
        do {
            val data = checkedData(
                getJsonBlocking(
                    "https://api.bilibili.com/x/v3/fav/resource/list?media_id=$fid&pn=$page&ps=20&platform=web",
                ),
            )
            val medias = data.optJSONArray("medias") ?: JSONArray()
            result += flattenArchives(medias, result.size)
            page++
            hasMore = data.optBoolean("has_more")
        } while (hasMore && page <= 100)
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.FAVOURITE,
            title = info.optString("title"),
            owner = info.optJSONObject("upper")?.optString("name").orEmpty(),
            coverUrl = info.optString("cover"),
            episodes = result,
        )
    }

    private fun resolveAllFavourites(url: String): SourceInfo {
        val mid = Regex("space\\.bilibili\\.com/(\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("收藏页链接缺少用户编号")
        val folders = checkedData(
            getJsonBlocking(
                "https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=$mid",
            ),
        ).optJSONArray("list") ?: JSONArray()
        val result = mutableListOf<Episode>()
        var owner = ""
        repeat(folders.length()) { folderIndex ->
            val folder = folders.getJSONObject(folderIndex)
            val folderId = folder.optLong("id")
            owner = folder.optJSONObject("upper")?.optString("name").orEmpty().ifBlank { owner }
            var page = 1
            var hasMore: Boolean
            do {
                val data = checkedData(
                    getJsonBlocking(
                        "https://api.bilibili.com/x/v3/fav/resource/list?" +
                            "media_id=$folderId&pn=$page&ps=20&platform=web",
                    ),
                )
                val medias = data.optJSONArray("medias") ?: JSONArray()
                result += flattenArchives(medias, result.size)
                page++
                hasMore = data.optBoolean("has_more")
            } while (hasMore && page <= 100)
        }
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.FAVOURITE,
            title = if (owner.isBlank()) "全部收藏夹" else "$owner 的全部收藏夹",
            owner = owner,
            episodes = result,
        )
    }

    private fun resolveSpaceVideos(url: String): SourceInfo {
        val mid = Regex("space\\.bilibili\\.com/(\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("空间链接缺少用户编号")
        val result = mutableListOf<Episode>()
        var page = 1
        var owner = ""
        var count: Int
        do {
            val json = getJsonBlocking(
                "https://api.bilibili.com/x/space/arc/search?mid=$mid&pn=$page&ps=30&order=pubdate",
            )
            val data = checkedData(json)
            val vlist = data.optJSONObject("list")?.optJSONArray("vlist") ?: JSONArray()
            owner = vlist.optJSONObject(0)?.optString("author").orEmpty().ifBlank { owner }
            result += flattenArchives(vlist, result.size)
            count = data.optJSONObject("page")?.optInt("count") ?: result.size
            page++
        } while (result.size < count && page <= 100)
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.SPACE,
            title = if (owner.isBlank()) "全部投稿" else "$owner 的全部投稿",
            owner = owner,
            episodes = result,
        )
    }

    private fun resolveCollection(url: String): SourceInfo {
        val uri = Uri.parse(url.replace("?sid=", "&sid="))
        val mid = Regex("space\\.bilibili\\.com/(\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("合集链接缺少用户编号")
        val sid = uri.getQueryParameter("sid")
            ?: Regex("sid=(\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("合集链接缺少 sid")
        val data = checkedData(
            getJsonBlocking(
                "https://api.bilibili.com/x/polymer/web-space/seasons_archives_list?mid=$mid&season_id=$sid&page_num=1&page_size=100",
            ),
        )
        val meta = data.optJSONObject("meta")
        val archives = data.optJSONArray("archives") ?: JSONArray()
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.COLLECTION,
            title = meta?.optString("name").orEmpty().ifBlank { "视频合集" },
            coverUrl = meta?.optString("cover").orEmpty(),
            episodes = flattenArchives(archives),
        )
    }

    private fun resolveSeries(url: String): SourceInfo {
        val uri = Uri.parse(url)
        val mid = Regex("(?:space\\.bilibili\\.com/|bilibili\\.com/list/)(\\d+)")
            .find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("列表链接缺少用户编号")
        val sid = uri.getQueryParameter("sid")
            ?: Regex("/lists/(\\d+)").find(url)?.groupValues?.get(1)
            ?: throw IllegalArgumentException("列表链接缺少 sid")
        val data = checkedData(
            getJsonBlocking(
                "https://api.bilibili.com/x/series/archives?mid=$mid&series_id=$sid&only_normal=true&sort=desc&pn=1&ps=100",
            ),
        )
        val archives = data.optJSONArray("archives") ?: JSONArray()
        return SourceInfo(
            sourceUrl = url,
            type = SourceType.SERIES,
            title = "视频列表",
            episodes = flattenArchives(archives),
        )
    }

    private fun flattenArchives(array: JSONArray, offset: Int = 0): List<Episode> = buildList {
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            val pages = item.optJSONArray("pages")
            if (pages != null && pages.length() > 0) {
                repeat(pages.length()) { pageIndex ->
                    val page = pages.getJSONObject(pageIndex)
                    add(
                        Episode(
                            index = offset + size + 1,
                            title = if (pages.length() == 1) {
                                item.optString("title")
                            } else {
                                "${item.optString("title")} · ${page.optString("part")}"
                            },
                            aid = item.optLong("id", item.optLong("aid")),
                            bvid = item.optString("bvid"),
                            cid = page.optLong("cid"),
                            coverUrl = item.optString("cover").ifBlank { item.optString("pic") },
                            durationSeconds = page.optInt("duration", item.optInt("duration")),
                        ),
                    )
                }
            } else {
                val aid = item.optLong("id", item.optLong("aid"))
                val bvid = item.optString("bvid")
                val detail = runCatching {
                    val query = if (bvid.isNotBlank()) "bvid=$bvid" else "aid=$aid"
                    checkedData(getJsonBlocking("https://api.bilibili.com/x/web-interface/view?$query"))
                }.getOrNull()
                val detailPages = detail?.optJSONArray("pages")
                if (detailPages != null && detailPages.length() > 0) {
                    repeat(detailPages.length()) { pageIndex ->
                        val page = detailPages.getJSONObject(pageIndex)
                        add(
                            Episode(
                                index = offset + size + 1,
                                title = if (detailPages.length() == 1) {
                                    item.optString("title")
                                } else {
                                    "${item.optString("title")} · ${page.optString("part")}"
                                },
                                aid = aid,
                                bvid = bvid,
                                cid = page.optLong("cid"),
                                coverUrl = item.optString("cover").ifBlank { item.optString("pic") },
                                durationSeconds = page.optInt("duration", item.optInt("duration")),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun parseDash(dash: JSONObject): PlayInfo {
        val videoArray = dash.optJSONArray("video") ?: JSONArray()
        val audioArray = dash.optJSONArray("audio") ?: JSONArray()
        val videos = buildList {
            repeat(videoArray.length()) {
                val item = videoArray.getJSONObject(it)
                add(
                    VideoStream(
                        id = item.optInt("id"),
                        codec = codecName(item.optInt("codecid"), item.optString("codecs")),
                        width = item.optInt("width"),
                        height = item.optInt("height"),
                        bandwidth = item.optLong("bandwidth"),
                        url = item.optString("baseUrl").ifBlank { item.optString("base_url") },
                        backups = stringList(
                            item.optJSONArray("backupUrl") ?: item.optJSONArray("backup_url"),
                        ),
                    ),
                )
            }
        }
        val audios = buildList {
            repeat(audioArray.length()) {
                val item = audioArray.getJSONObject(it)
                add(
                    AudioStream(
                        id = item.optInt("id"),
                        codec = item.optString("codecs").substringBefore(".").ifBlank { "mp4a" },
                        bandwidth = item.optLong("bandwidth"),
                        url = item.optString("baseUrl").ifBlank { item.optString("base_url") },
                        backups = stringList(
                            item.optJSONArray("backupUrl") ?: item.optJSONArray("backup_url"),
                        ),
                    ),
                )
            }
            dash.optJSONObject("dolby")?.optJSONArray("audio")?.let { array ->
                repeat(array.length()) {
                    val item = array.getJSONObject(it)
                    add(
                        AudioStream(
                            id = item.optInt("id", 30250),
                            codec = "eac3",
                            bandwidth = item.optLong("bandwidth"),
                            url = item.optString("baseUrl").ifBlank { item.optString("base_url") },
                            backups = stringList(
                                item.optJSONArray("backupUrl") ?: item.optJSONArray("backup_url"),
                            ),
                        ),
                    )
                }
            }
            dash.optJSONObject("flac")?.optJSONObject("audio")?.let { item ->
                add(
                    AudioStream(
                        id = item.optInt("id", 30251),
                        codec = "flac",
                        bandwidth = item.optLong("bandwidth"),
                        url = item.optString("baseUrl").ifBlank { item.optString("base_url") },
                        backups = stringList(
                            item.optJSONArray("backupUrl") ?: item.optJSONArray("backup_url"),
                        ),
                    ),
                )
            }
        }
        if (videos.isEmpty() && audios.isEmpty()) throw IOException("媒体流为空")
        return PlayInfo(videos, audios)
    }

    private fun getJsonBlocking(url: String): JSONObject {
        execute(url).use { response ->
            if (!response.isSuccessful) throw IOException("网络请求失败：HTTP ${response.code}")
            return JSONObject(response.body?.string() ?: throw IOException("服务器返回空内容"))
        }
    }

    private fun checkedData(json: JSONObject): JSONObject {
        if (json.optInt("code") != 0) {
            throw IOException(json.optString("message").ifBlank { "接口返回错误 ${json.optInt("code")}" })
        }
        return json.optJSONObject("data") ?: json.optJSONObject("result")
            ?: throw IOException("接口没有返回数据")
    }

    private fun stringList(array: JSONArray?): List<String> = buildList {
        if (array != null) repeat(array.length()) { add(array.optString(it)) }
    }.filter { it.isNotBlank() }

    private fun codecName(id: Int, fallback: String): String = when (id) {
        7 -> "avc"
        12 -> "hevc"
        13 -> "av1"
        else -> fallback.substringBefore(".").ifBlank { "unknown" }
    }

    private fun isBangumi(url: String) =
        url.contains("/bangumi/") || Regex("(?i)(ep|ss|md)\\d+").matches(url)

    private fun isCourse(url: String) = url.contains("/cheese/")
    private fun isWatchLater(url: String) = url.contains("watchlater")
    private fun isFavourite(url: String) = url.contains("/favlist")
    private fun isSpaceVideos(url: String) = url.contains("space.bilibili.com") && url.contains("/video")
    private fun isCollection(url: String) = url.contains("/lists") && url.contains("type=season")
    private fun isSeries(url: String) =
        (url.contains("/lists/") && url.contains("type=series")) ||
            (url.contains("bilibili.com/list/") && url.contains("sid="))

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Bd) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36"
    }
}

fun encodeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
