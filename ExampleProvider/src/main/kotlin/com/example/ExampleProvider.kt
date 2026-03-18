package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Document
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var name = "Internet Archive"
    override var mainUrl = "https://archive.org"
    override val hasMainPage = true
    override var lang = "tr"
    override val supportedTypes = setOf(TvType.Movie, TvType.Documentary)

    override val mainPage = mainPageOf(
        "$mainUrl/details/movies" to "Filmler",
        "$mainUrl/details/feature_films" to "Uzun Metraj",
        "$mainUrl/details/documentaryandfieldrecordings" to "Belgeseller"
    )

    private fun parseCard(document: Document): List<SearchResponse> {
        return document.select("div.item-ia, div.results_item, div.item-box").mapNotNull { item ->
            val link = item.selectFirst("a[href*=/details/]") ?: return@mapNotNull null
            val title =
                item.selectFirst(".C234")?.text()?.trim()
                    ?: item.selectFirst("img")?.attr("alt")?.trim()
                    ?: link.attr("title").trim()
                    ?: link.text().trim()

            val href = link.absUrl("href").ifBlank { mainUrl + link.attr("href") }
            val poster =
                item.selectFirst("img")?.attr("data-src")
                    ?.takeIf { it.isNotBlank() }
                    ?: item.selectFirst("img")?.absUrl("src")

            newMovieSearchResponse(
                title.ifBlank { "Internet Archive" },
                href,
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pagedUrl = if (page == 1) request.data else "${request.data}?page=$page"
        val document = app.get(pagedUrl).document
        val items = parseCard(document)

        return newHomePageResponse(
            request.name,
            items
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search?query=$encoded+AND+mediatype%3A%28movies%29"
        val document = app.get(url).document
        return parseCard(document)
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title =
            document.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
                ?: document.selectFirst("h1")?.text()?.trim()
                ?: return null

        val poster =
            document.selectFirst("meta[property=og:image]")?.attr("content")
                ?: document.selectFirst(".item-image img")?.absUrl("src")

        val plot =
            document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                ?: document.selectFirst(".js-description")?.text()?.trim()
                ?: document.selectFirst(".description")?.text()?.trim()

        val tags = document.select("span.badge, a[href*=subject]").map { it.text() }.filter { it.isNotBlank() }

        return newMovieLoadResponse(
            title,
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = poster
            this.plot = plot
            this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        var found = false

        document.select("source[src], a[href]").forEach { el ->
            val raw = el.attr("src").ifBlank { el.attr("href") }
            if (raw.isBlank()) return@forEach

            val full = when {
                raw.startsWith("http") -> raw
                raw.startsWith("/") -> "$mainUrl$raw"
                else -> "$mainUrl/$raw"
            }

            if (full.contains(".mp4", true) || full.contains("/download/", true)) {
                callback(
                    newExtractorLink(
                        source = name,
                        name = name,
                        url = full,
                        type = INFER_TYPE
                    )
                )
                found = true
            }

            if (full.endsWith(".vtt", true) || full.endsWith(".srt", true)) {
                subtitleCallback(
                    SubtitleFile(
                        lang = "Türkçe",
                        url = full
                    )
                )
            }
        }

        return found
    }
}
