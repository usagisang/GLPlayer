package top.gochiusa.glplayer.data

import top.gochiusa.glplayer.base.Sender
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaItem

interface MediaSource: Sender {

    fun setDataSource(mediaItem: MediaItem, requestHeaders: Map<String, String>?)

    fun release()

    fun seekTo(positionUs: Long, seekMode: SeekMode): Long

    /**
     * 是否已经缓存到媒体流的末尾，如果是，返回true
     */
    fun hasCacheReachedEndOfStream(): Boolean

    /**
     * 媒体流总时长，如果不存在相关信息，返回-1
     */
    val durationUs: Long

    /**
     * 缓存总时长，如果没有更多数据，返回-1
     */
    val cacheDurationUs: Long
    val format: List<Format>
}