package top.gochiusa.glplayer.listener

import android.view.Surface
import top.gochiusa.glplayer.base.Player

/**
 * 播放器事件监听
 */
interface EventListener {

    /**
     * 当[Player]内部发生错误时回调
     */
    fun onPlayerError(errorCode: Int)

    /**
     * 当[Player]的状态发生修改时或者首次注册该监听器时回调，状态发生修改时，保证此回调函数运行在主线程
     */
    fun onPlaybackStateChanged(playbackState: Int)

    /**
     * 当[Player]成功与[Surface]建立关联时回调
     */
    fun onVideoSurfaceAttach()

    /**
     * 当[Player]与[Surface]失去关联时回调，此回调可能会无意义地被调用多次
     */
    fun onVideoSurfaceDetach()
}