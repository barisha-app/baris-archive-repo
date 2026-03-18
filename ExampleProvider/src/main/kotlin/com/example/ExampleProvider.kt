package com.example

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.SubtitleFile
import org.json.JSONObject
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://archive.org"
    override var name = "Archive Movies"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url =
            "$mainUrl/advancedsearch.php?q=$encodedQuery+AND+mediatype:(movies)&fl[]=identifier&fl[]=title&output=json&rows=20&page=1"

        val response = app.get(url).text
        val json = JSONObject(response)
        val docs = json.getJSONObject("response").getJSONArray("docs")

        val results = mutableListOf<SearchResponse>()

        for (i in 0 until docs.length()) {
            val item = docs.getJSONObject(i)
            val identifier = item.optString("identifier")
            val title = item.optString("title", identifier)

            if (identifier.isNotBlank()) {
                results.add(
                    newMovieSearchResponse(
                        title,
                        "$mainUrl/details/$identifier",
                        TvType.Movie
                    )
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse {
        val identifier = url.substringAfter("/details/").substringBefore("?")
        val metadataUrl = "$mainUrl/metadata/$identifier"

        val response = app.get(metadataUrl).text
        val json = JSONObject(response)

        val metadata = json.getJSONObject("metadata")
        val title = metadata.optString("title", identifier)
        val description = metadata.optString("description", "")
        val files = json.getJSONArray("files")

        var videoUrl: String? = null

        for (i in 0 until files.length()) {
            val file = files.getJSONObject(i)
            val fileName = file.optString("name")
            val format = file.optString("format")

            val isMp4 =
                format.contains("mp4", ignoreCase = true) ||
                format.contains("h.264", ignoreCase = true) ||
                fileName.endsWith(".mp4", ignoreCase = true)

            if (isMp4) {
                videoUrl = "$mainUrl/download/$identifier/$fileName"
                break
            }
        }

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            videoUrl ?: ""
        ) {
            plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit
    ): Boolean {
        if (data.isBlank()) return false

        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = data,
                type = com.lagradost.cloudstream3.utils.INFER_TYPE
            )
        )

        return true
    }
}
