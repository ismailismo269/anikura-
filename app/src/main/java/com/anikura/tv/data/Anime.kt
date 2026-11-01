package com.anikura.tv.data

data class Anime(
    val id: Int,
    val title: String,
    val romajiTitle: String,
    val poster: String,
    val banner: String,
    val description: String,
    val score: Int?,
    val episodes: Int?,
    val format: String,
    val status: String,
    val season: String,
    val year: Int?,
    val genres: List<String>,
    val nextEpisode: Int?,
    val nextAiringAt: Long?
)
