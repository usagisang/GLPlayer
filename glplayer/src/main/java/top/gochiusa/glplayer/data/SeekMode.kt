package top.gochiusa.glplayer.data

import android.media.MediaExtractor

enum class SeekMode(
    val mode: Int
) {
    PREVIOUS_SYNC(MediaExtractor.SEEK_TO_PREVIOUS_SYNC),
    NEXT_SYNC(MediaExtractor.SEEK_TO_NEXT_SYNC),
    CLOSEST_SYNC(MediaExtractor.SEEK_TO_CLOSEST_SYNC)
}