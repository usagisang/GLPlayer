package top.gochiusa.glplayer.listener

import android.view.Surface
import top.gochiusa.glplayer.opengl.VideoGLSurfaceView

/**
 * 可以监听Surface相关事件的Listener
 */
interface VideoSurfaceListener {
    /**
     * 当[surface]被创建并与[VideoGLSurfaceView]相关联时，该函数被回调
     */
    fun onVideoSurfaceCreated(surface: Surface)

    /**
     * 当[surface]与[VideoGLSurfaceView]不再关联并被释放前，该函数被回调
     */
    fun onVideoSurfaceDestroyed(surface: Surface?)
}