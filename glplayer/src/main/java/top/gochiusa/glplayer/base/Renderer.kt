package top.gochiusa.glplayer.base

import top.gochiusa.glplayer.entity.Format

/**
 * 渲染适配器
 */
interface Renderer: Receiver {

    /**
     * 返回[Renderer]处理的轨道的类型
     */
    fun getTrackType(): Int

    /**
     * 返回与[Renderer]有关的时钟
     */
    fun getMediaClock(): MediaClock

    /**
     * 根据当前播放位置[positionUs]，进行渲染操作
     * @param positionUs 当前播放位置
     * @param elapsedRealtimeMs 渲染开始的基准时间，单位为ms
     */
    fun render(positionUs: Long, elapsedRealtimeMs: Long)

    /**
     * 状态为[STATE_DISABLE]时可调用，将渲染器与[sender]进行关联
     * 调用成功后状态转为[STATE_ENABLE]
     * @param positionUs player当前的播放位置
     */
    fun enable(
        format: List<Format>,
        sender: Sender,
        positionUs: Long,
    )

    fun replaceSender(format: List<Format>, sender: Sender?, startPositionUs: Long)

    /**
     * 发生SeekTo事件后回调
     */
    fun onSeekTo(startPositionUs: Long)

    /**
     * 发生Pause事件后回调
     */
    fun onPause()

    /**
     * 状态为[STATE_ENABLE]时可调用，取消与[Sender]的关联
     * 调用成功后状态转为[STATE_DISABLE]
     */
    fun disable()

    /**
     * 不再需要[Renderer]时调用，释放相应资源
     */
    fun release()

    /**
     * [Renderer]目前的状态
     */
    val state: Int

    companion object {
        const val STATE_DISABLE = 1
        const val STATE_ENABLE = 2
    }
}
