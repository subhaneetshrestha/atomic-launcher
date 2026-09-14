package io.github.subhaneetshrestha.atomic.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The fields of a GitHub release this app reads. Everything else in the real answer is ignored. */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("published_at") val publishedAt: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
)
