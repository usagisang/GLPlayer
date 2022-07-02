package top.gochiusa.glplayer.base

import android.view.Surface
import top.gochiusa.glplayer.listener.VideoSurfaceListener

interface SurfaceProvider {
    val surface: Surface?
    fun setOnVideoSurfaceListener(listener: VideoSurfaceListener?)
}