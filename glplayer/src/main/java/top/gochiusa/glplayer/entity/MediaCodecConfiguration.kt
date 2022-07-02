package top.gochiusa.glplayer.entity

import android.media.MediaCrypto
import android.media.MediaFormat
import android.view.Surface

data class MediaCodecConfiguration(
    val type: String,
    val format: MediaFormat?,
    val surface: Surface?,
    val crypto: MediaCrypto?,
    val flags: Int,
)