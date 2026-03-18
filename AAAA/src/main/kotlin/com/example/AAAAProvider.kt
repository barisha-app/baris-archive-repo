package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class AAAAProvider : MainAPI() {
    override var name = "AAAA Example"
    override var mainUrl = "https://archive.org"
    override val hasMainPage = true
    override var lang = "tr"

    override val supportedTypes = setOf(
        TvType.Movie
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        return HomePageResponse(
            listOf(
                HomePageList(
                    "Örnek Filmler",
                    listOf(
                        newMovieSearchResponse(
                            "Örnek Film",
                            "https://archive.org/details/movies"
                        ) {
                            this.posterUrl = "https://archive.org/services/img/movies"
                        }
                    )
                )
            )
        )
    }

    override suspend fun load(url: String): LoadResponse {
        return newMovieLoadResponse(
            "Örnek Film",
            url,
            TvType.Movie,
            url
        ) {
            this.posterUrl = "https://archive.org/services/img/movies"
            this.plot = "Bu bir örnek filmdir."
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        callback.invoke(
            newExtractorLink(
                source = "Archive",
                name = "Test Video",
                url = "https://archive.org/download/ElephantsDream/ed_1024_512kb.mp4",
                referer = "",
                quality = Qualities.P720.value
            )
        )

        return true
    }
}
