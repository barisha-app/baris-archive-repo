package com.example

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var name = "Internet Archive"
    override var mainUrl = "https://archive.org"
    override val hasMainPage = false
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.Documentary)

    private val mapper = jacksonObjectMapper()

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val encoded = URLEncoder.encode(query, "UTF-8")
        val url =
            "$mainUrl/advancedsearch.php?q=$encoded+AND+mediatype:(movies)" +
                    "&fl[]=identifier&fl[]=title&fl[]=mediatype&fl[]=description" +
                    "&rows=20&page=1&output=json"

        val text = app.get(url).text
        val root = mapper.readTree(text)
        val docs = root.path("response").path("docs")

        val results = mutableListOf<SearchResponse>()

        for (item in docs) {
            val identifier = item.path("identifier").asText("")
            val title = item.path("title").asText(identifier)

            if (identifier.isNotBlank()) {
                val detailsUrl = "$mainUrl/details/$identifier"
                val poster = "$mainUrl/services/img/$identifier"

                results.add(
                    newMovieSearchResponse(
                        title.ifBlank { "Internet Archive" },
                        detailsUrl,
                        TvType.Movie
                    ) {
                        this.posterUrl = poster
                    }
                )
            }
        }

        return results
    }

    override suspend fun load(url: String): LoadResponse? {
        val identifier = url.substringAfter("/details/").substringBefore("?").trim()
        if (identifier.isBlank()) return null

        val metadataUrl = "$mainUrl/metadata/$identifier"
        val text = app.get(metadataUrl).text
        val root = mapper.readTree(text)

        val metadata = root.path("metadata")
        val title = metadata.path("title").asText(identifier)
        val plot = metadata.path("description").asText("")
        val poster = "$mainUrl/services/img/$identifier"

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val identifier = data.substringAfter("/details/").substringBefore("?").trim()
        if (identifier.isBlank()) return false

        val metadataUrl = "$mainUrl/metadata/$identifier"
        val text = app.get(metadataUrl).text
        val root = mapper.readTree(text)
        val files = root.path("files")

        var found = false

        for (file in files) {
            val name = file.path("name").asText("")
            val format = file.path("format").asText("").lowercase()

            if (name.isBlank()) continue

            val fileUrl = "$mainUrl/download/$identifier/$name"

            val isVideo =
                name.endsWith(".mp4", true) ||
                name.endsWith(".webm", true) ||
                name.endsWith(".ogv", true) ||
                format.contains("mpeg4") ||
                format.contains("h.264") ||
                format.contains("quicktime") ||
                format.contains("webm")

            val isSubtitle =
                name.endsWith(".srt", true) ||
                name.endsWith(".vtt", true)

            if (isVideo) {
                callback(
                    newExtractorLink(
                        source = this.name,
                        name = this.name,
                        url = fileUrl,
                        type = INFER_TYPE
                    )
                )
                found = true
            }

            if (isSubtitle) {
                subtitleCallback(
                    SubtitleFile(
                        lang = "Unknown",
                        url = fileUrl
                    )
                )
            }
        }

        return found
    }
}
