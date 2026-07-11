package com.aklzo.videoplayer.data.source

import kotlinx.serialization.Serializable

/** 対象フォルダ(複数選択可)。設定に JSON で永続化する */
@Serializable
data class VideoFolder(
    val id: String,
    val name: String,
)
