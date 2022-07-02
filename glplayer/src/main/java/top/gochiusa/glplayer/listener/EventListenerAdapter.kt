package top.gochiusa.glplayer.listener


/**
 * 简单的事件监听适配，可以选择感兴趣的事件进行监听
 */
interface EventListenerAdapter: EventListener {

    override fun onPlayerError(errorCode: Int) {}

    override fun onPlaybackStateChanged(playbackState: Int) {}

    override fun onVideoSurfaceAttach() {}

    override fun onVideoSurfaceDetach() {}
}