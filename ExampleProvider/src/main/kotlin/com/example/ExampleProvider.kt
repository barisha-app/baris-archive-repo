package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import java.net.URLEncoder

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://archive.org"
    override var name = "Archive Movies"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = false

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$mainUrl/search?query=$encoded+AND+mediatype%3A%28movies%29"

        val document = app.get(url).document

        return document.select("div.item-ia").mapNotNull { item ->
            val linkEl = item.selectFirst("a[href*=/details/]") ?: return@mapNotNull null
            val title =
                item.selectFirst(".C234")?.text()?.trim()
                    ?: linkEl.attr("title").trim()
                    ?: linkEl.text().trim()

            val href = linkEl.absUrl("href").ifBlank { mainUrl + linkEl.attr("href") }
            val poster =
                item.selectFirst("img")?.attr("data-src")
                    ?: item.selectFirst("img")?.attr("src")

            newMovieSearchResponse(
                title.ifBlank { "Archive Item" },
                href,
                TvType.Movie
            ) {
                this.posterUrl = poster
            }
        }
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
                ?: document.selectFirst(".description")?.text()?.trim()

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
        val document = app.get(data).document
        var found = false

        document.select("source[src], a[href]").forEach { el ->
            val raw = el.attr("src").ifBlank { el.attr("href") }
            val full = if (raw.startsWith("http")) raw else mainUrl + raw

            if (full.contains(".mp4", ignoreCase = true) || full.contains("/download/")) {
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
        }

        return found
    }
}
