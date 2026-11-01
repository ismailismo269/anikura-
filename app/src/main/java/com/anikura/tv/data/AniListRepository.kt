package com.anikura.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class AniListRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun browse(
        search: String = "",
        genre: String? = null,
        year: Int? = null,
        format: String? = null,
        status: String? = null,
        sort: String = "TRENDING_DESC",
        ids: Set<Int> = emptySet(),
        page: Int = 1
    ): List<Anime> = withContext(Dispatchers.IO) {
        val allowedSorts = setOf(
            "TRENDING_DESC", "POPULARITY_DESC", "SCORE_DESC",
            "START_DATE_DESC", "START_DATE"
        )
        val allowedFormats = setOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL")
        val allowedStatuses = setOf("RELEASING", "FINISHED", "NOT_YET_RELEASED", "CANCELLED")
        val args = mutableListOf(
            "type: ANIME",
            "isAdult: false"
        )

        if (search.isNotBlank()) args += "search: ${JSONObject.quote(search.trim())}"
        if (!genre.isNullOrBlank()) args += "genre_in: [${JSONObject.quote(genre)}]"
        if (year != null && year in 1940..2100) args += "seasonYear: $year"
        if (format in allowedFormats) args += "format: $format"
        if (status in allowedStatuses) args += "status: $status"
        if (ids.isNotEmpty()) args += "id_in: [${ids.take(50).joinToString(",")}]"
        args += "sort: [${if (sort in allowedSorts) sort else "TRENDING_DESC"}]"

        val query = """
            query {
              Page(page: $page, perPage: 24) {
                media(${args.joinToString(", ")}) {
                  id
                  title { english romaji }
                  coverImage { extraLarge large }
                  bannerImage
                  description(asHtml: false)
                  averageScore
                  episodes
                  format
                  status
                  season
                  seasonYear
                  genres
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
        """.trimIndent()

        val body = JSONObject().put("query", query)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", "AnikuraTV/0.1 AndroidTV")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("AniList returned HTTP ${response.code}")
            }

            val root = JSONObject(response.body?.string().orEmpty())
            val errors = root.optJSONArray("errors")
            if (errors != null && errors.length() > 0) {
                throw IllegalStateException(errors.optJSONObject(0)?.optString("message") ?: "AniList query failed")
            }
            val media = root.optJSONObject("data")
                ?.optJSONObject("Page")
                ?.optJSONArray("media")
                ?: JSONArray()

            buildList {
                for (index in 0 until media.length()) {
                    val item = media.optJSONObject(index) ?: continue
                    val title = item.optJSONObject("title")
                    val cover = item.optJSONObject("coverImage")
                    val airing = item.optJSONObject("nextAiringEpisode")
                    val genresJson = item.optJSONArray("genres") ?: JSONArray()
                    val genres = (0 until genresJson.length())
                        .mapNotNull { genresJson.optString(it).takeIf(String::isNotBlank) }
                    val description = item.optString("description")
                        .replace(Regex("<[^>]*>"), "")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&#039;", "'")
                        .trim()

                    val english = title?.optString("english").orEmpty()
                    val romaji = title?.optString("romaji").orEmpty()
                    val scoreValue = item.optInt("averageScore", 0).takeIf { it > 0 }

                    add(
                        Anime(
                            id = item.optInt("id"),
                            title = english.ifBlank { romaji }.ifBlank { "Untitled anime" },
                            romajiTitle = romaji,
                            poster = cover?.optString("extraLarge").orEmpty()
                                .ifBlank { cover?.optString("large").orEmpty() },
                            banner = item.optString("bannerImage").takeUnless { it == "null" }.orEmpty(),
                            description = description,
                            score = scoreValue,
                            episodes = item.optInt("episodes", 0).takeIf { it > 0 },
                            format = item.optString("format").takeUnless { it == "null" }.orEmpty(),
                            status = item.optString("status").takeUnless { it == "null" }.orEmpty(),
                            season = item.optString("season").takeUnless { it == "null" }.orEmpty(),
                            year = item.optInt("seasonYear", 0).takeIf { it > 0 },
                            genres = genres,
                            nextEpisode = airing?.optInt("episode", 0)?.takeIf { it > 0 },
                            nextAiringAt = airing?.optLong("airingAt", 0L)?.takeIf { it > 0L }
                        )
                    )
                }
            }
        }
    }
}
