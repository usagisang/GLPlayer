package top.gochiusa.glplayer.base

import android.os.Handler
import top.gochiusa.glplayer.listener.VideoFrameListener
import top.gochiusa.glplayer.listener.VideoMetadataListener

fun interface RendererFactory {

    /**
     * 创建渲染器
     * @param eventHandler 渲染线程的Handler
     * @param metadataListener 视频的元数据监听器
     */
    fun createRenders(
        eventHandler: Handler,
        metadataListener: VideoMetadataListener,
        videoFrameListener: VideoFrameListener
    ): Array<Renderer>
}