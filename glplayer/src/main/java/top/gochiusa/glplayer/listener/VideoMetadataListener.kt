package top.gochiusa.glplayer.listener

import top.gochiusa.glplayer.entity.Format

/**
 * 关于Video Track的元数据监听
 */
fun interface VideoMetadataListener {

    /**
     * 当Video Track的元数据发生改变时回调
     */
    fun onVideoMetadataChanged(
        format: Format
    )
}