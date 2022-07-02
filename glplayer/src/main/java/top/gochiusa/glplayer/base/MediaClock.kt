package top.gochiusa.glplayer.base

/**
 * 与[Renderer]渲染相关的时钟类
 */
interface MediaClock {

    /**
     * 返回上一次渲染成功的时间戳，单位为微秒，不保证有序
     */
    fun getPositionUs(durationUs: Long): Long

    /**
     * 返回[Renderer]渲染的轨道的时长
     */
    fun getDurationUs(): Long

    companion object {
        const val END_OF_RENDER = Long.MIN_VALUE
    }
}