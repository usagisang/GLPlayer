package top.gochiusa.glplayer

import android.content.Context
import top.gochiusa.glplayer.base.Player
import top.gochiusa.glplayer.base.Renderer
import top.gochiusa.glplayer.base.RendererFactory
import top.gochiusa.glplayer.data.DefaultMediaSourceFactory
import top.gochiusa.glplayer.data.MediaSource
import top.gochiusa.glplayer.data.MediaSourceFactory
import top.gochiusa.glplayer.mediacodec.CodecRendererFactory

class GLPlayerBuilder(internal val context: Context) {

    internal var playAfterLoading: Boolean = false
    internal var renderFirstFrame: Boolean = false
    internal var rendererFactory: RendererFactory = CodecRendererFactory()
    internal var requestHeader: Map<String, String>? = null
    internal var sourceFactory: MediaSourceFactory = DefaultMediaSourceFactory()
    internal var infiniteLoop: Boolean = false

    /**
     * 设置是否在媒体数据首次加载完成后自动播放，如果为true，则播放器将从[Player.STATE_LOADING]直接
     * 转入[Player.STATE_PLAYING]状态
     */
    fun setPlayAfterLoading(enable: Boolean): GLPlayerBuilder {
        playAfterLoading = enable
        return this
    }

    /**
     * 设置如何构造[Renderer]的实现类
     */
    fun setRendererFactory(factory: RendererFactory): GLPlayerBuilder {
        rendererFactory = factory
        return this
    }

    /**
     * 设置请求网络媒体资源的请求头（HTTP/HTTPS协议下）
     */
    fun setRequestHeader(headers: Map<String, String>): GLPlayerBuilder {
        requestHeader = headers
        return this
    }

    /**
     * 设置如何构造[MediaSource]的实现类
     */
    fun setMediaSourceFactory(mediaSourceFactory: MediaSourceFactory): GLPlayerBuilder {
        sourceFactory = mediaSourceFactory
        return this
    }

    /**
     * 设置是否渲染视频的首帧画面，会尽最大努力尝试渲染，但不能保证成功
     */
    fun setRenderFirstFrame(enable: Boolean): GLPlayerBuilder {
        renderFirstFrame = enable
        return this
    }

    /**
     * 是否允许播放器无限循环播放媒体
     */
    fun setInfiniteLoop(enable: Boolean): GLPlayerBuilder {
        infiniteLoop = enable
        return this
    }

    fun build(): Player {
        return GLPlayer(this)
    }

}