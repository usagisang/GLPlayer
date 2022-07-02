package top.gochiusa.glplayer.base

import android.view.SurfaceView
import top.gochiusa.glplayer.entity.MediaItem
import top.gochiusa.glplayer.listener.EventListener

/**
 * 播放器接口。接口描述的大多数的状态转换需要满足条件
 */
interface Player {

    /**
     * 延迟[delayTimeMs]毫秒后，继续/开始媒体播放，播放器状态转为[Player.STATE_PLAYING]
     *
     * [delayTimeMs]小于等于0时无延迟效果
     *
     * 延时对[Player.STATE_STOP]状态不生效
     */
    fun play(delayTimeMs: Long = 0L)

    /**
     * 播放器状态转为[Player.STATE_PAUSE]，并暂停媒体播放
     *
     * 此调用保证对[Player.STATE_LOADING]和[Player.STATE_BUFFERING]生效，状态将在稍后转为[Player.STATE_PAUSE]
     *
     * 此调用保证移除任何延时的[play]
     */
    fun pause()

    /**
     * 将播放进度设置为指定位置，播放器状态将转为[Player.STATE_BUFFERING]
     *
     * 直到seekTo动作完成，播放器将自动回到原状态
     */
    fun seekTo(positionMs: Long)

    /**
     * 在主线程中的任意时刻可安全调用，释放播放器资源，播放器状态将转为[Player.STATE_RELEASE]
     *
     * 此播放器后续不能再次用于视频播放
     */
    fun release()

    /**
     * 设置需要加载的媒体
     */
    fun setMediaItem(mediaItem: MediaItem)

    /**
     * 开始加载媒体数据，播放器状态转为[Player.STATE_LOADING]
     *
     * 如果[playAfterLoading]属性为true，播放器的状态将在准备完毕之后转为[Player.STATE_PLAYING]，开始媒体播放；
     *
     * 否则，播放器的状态将在准备完毕之后转为[Player.STATE_READY]，等待[play]调用后开始媒体播放
     */
    fun prepare()

    /**
     * 将播放器与具有渲染能力的View相关联。目前View用于提供视频渲染能力，音频渲染不受影响
     */
    fun setVideoSurfaceView(surfaceView: SurfaceView)

    /**
     * 清除播放器与具有渲染能力的View的关联
     */
    fun clearVideoSurfaceView(surfaceView: SurfaceView)

    /**
     * 添加与播放器相关的事件监听器
     */
    fun addEventListener(eventListener: EventListener)

    /**
     * 移除相关的事件监听器
     */
    fun removeEventListener(eventListener: EventListener)

    /**
     * 播放器处于[Player.STATE_PLAYING]状态时为true，其他状态均为false
     */
    fun isPlaying(): Boolean

    /**
     * 检查状态[state]是否允许转入状态[Player.STATE_BUFFERING]
     *
     * [state]的默认值为当前状态
     */
    fun canSeekTo(state: Int = playerState): Boolean

    /**
     * 检查状态[state]是否允许转入状态[Player.STATE_PAUSE]
     *
     * [state]的默认值为当前状态
     */
    fun canPause(state: Int = playerState): Boolean

    /**
     * 媒体的总时长，单位为毫秒，如果尚未加载媒体信息，返回负值
     */
    val durationMs: Long

    /**
     * 当前播放进度，单位毫秒，尚未开始播放时，返回0
     */
    val currentPositionMs: Long

    /**
     * 已缓存的媒体长度，单位毫秒，如果尚未加载媒体信息，返回负值
     */
    val cacheDurationMs: Long

    /**
     * 如果为true，允许媒体数据初次加载完成后自动播放
     *
     * 此标记位仅在[Player.STATE_LOADING]期间检查，如果为true，则转入至[Player.STATE_PLAYING]，
     * 否则转入[Player.STATE_READY]
     *
     * 修改该标志位不保证立即生效
     */
    var playAfterLoading: Boolean

    /**
     * 播放器的当前状态
     */
    val playerState: Int

    companion object {
        /**
         * 播放器的初始状态，如果准备媒体资源失败(in [Player.STATE_LOADING])，则会回退到该状态
         */
        const val STATE_INIT = 0

        /**
         * 初次加载媒体资源，解析MetaData并缓冲媒体流，如果设置了[playAfterLoading]，则准备完毕后
         * 转入[Player.STATE_PLAYING]，否则，转入[Player.STATE_READY]
         *
         * 此状态可以收到调用[pause]的事件，在准备完毕后状态转入[Player.STATE_PAUSE]，此时[playAfterLoading]
         * 和[top.gochiusa.glplayer.GLPlayerBuilder.setRenderFirstFrame]失效
         */
        const val STATE_LOADING = 1

        /**
         * 媒体资源准备完毕而等待播放指令的状态，可以调用[play]转入[Player.STATE_PLAYING]
         */
        const val STATE_READY = 2

        /**
         * 播放器处于播放状态，此状态下会持续渲染媒体流
         *
         * 在播放结束后如果未设置[top.gochiusa.glplayer.GLPlayerBuilder.setInfiniteLoop]，
         * 则会转入[Player.STATE_STOP]
         */
        const val STATE_PLAYING = 3

        /**
         * 媒体渲染处于暂停状态
         *
         * 可以通过[play]来恢复播放状态
         *
         * [seekTo]在此状态下可以生效，但不会自动解除暂停状态
         */
        const val STATE_PAUSE = 4

        /**
         * 媒体源缓冲区数据不足，需要进行等待
         */
        const val STATE_BUFFERING = 5

        /**
         * 媒体播放结束，此状态目前只能由[Player.STATE_PLAYING]自然转入。可以调用[play]或[seekTo]重新进入播放状态
         */
        const val STATE_STOP = 6

        /**
         * 播放器终态，此状态下释放所有资源，并不能再用于媒体渲染
         */
        const val STATE_RELEASE = 7
    }
}