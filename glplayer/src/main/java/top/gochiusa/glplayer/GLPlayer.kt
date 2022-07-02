package top.gochiusa.glplayer

import android.content.Context
import android.os.*
import android.view.Surface
import android.view.SurfaceView
import top.gochiusa.glplayer.base.MediaClock
import top.gochiusa.glplayer.base.Player
import top.gochiusa.glplayer.base.Renderer
import top.gochiusa.glplayer.base.SurfaceProvider
import top.gochiusa.glplayer.data.MediaSource
import top.gochiusa.glplayer.data.SeekMode
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.entity.MediaItem
import top.gochiusa.glplayer.listener.EventListener
import top.gochiusa.glplayer.listener.VideoFrameListener
import top.gochiusa.glplayer.listener.VideoMetadataListener
import top.gochiusa.glplayer.listener.VideoSurfaceListener
import top.gochiusa.glplayer.util.*
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 播放器实现类
 *
 * <b>快速使用:</b>
 *
 * ```
 * val player: Player = GLPlayerBuilder(context).setPlayAfterLoading(true).build()
 *
 * findViewById<PlayerView>(R.id.player_view).setPlayer(player)
 *
 * player.setMediaItem(MediaItem.fromUrl(videoUrl))
 *
 * player.prepare()
 *
 * ```
 * 如果应用退出到后台，则必须调用[Player.pause]或[PlayerView.onPause]两个函数之一，否则视频流渲染
 * 将会因为 EGL Display 被释放而异常终止
 *
 * [PlayerView]提供了绑定到[androidx.lifecycle.Lifecycle]的函数[PlayerView.bindLifecycle]，
 * 能够免去手动调用[PlayerView.onPause]的繁琐
 */
class GLPlayer
internal constructor(builder: GLPlayerBuilder) : Player, Handler.Callback {

    override val durationMs: Long
        get() = if (mediaSource.durationUs > 0) mediaSource.durationUs / 1000 else -1L
    override val currentPositionMs: Long
        get() = _currentPositionUs / 1000
    override val cacheDurationMs: Long
        get() = if (mediaSource.cacheDurationUs > 0) mediaSource.cacheDurationUs / 1000 else -1L

    /**
     * 播放器展示给外部检阅的当前状态
     * 规定只能在主线程更改播放器状态并通知相应的监听器
     */
    override var playerState: Int = Player.STATE_INIT
        private set

    @Volatile
    override var playAfterLoading: Boolean = builder.playAfterLoading

    private var _currentPositionUs: Long = 0L
    private var startRenderTimeMs: Long = -1L

    private val mayRenderFirstFrame: Boolean = builder.renderFirstFrame
    private val infiniteLoop: Boolean = builder.infiniteLoop
    private val mediaSource: MediaSource
    private val renderers: Array<Renderer>
    private val requestHeaders: Map<String, String>?

    /**
     * 工作线程
     */
    private val playbackThread: HandlerThread = HandlerThread("GLPlayer",
        Process.THREAD_PRIORITY_AUDIO)

    /**
     * 绑定工作线程的Handler
     */
    private val eventHandler: Handler

    private val applicationContext: Context = builder.context.applicationContext
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
    private var videoOutput: SurfaceView? = null
    private val componentListener: ComponentListener

    private var eventListenerSet: CopyOnWriteArraySet<EventListener> = CopyOnWriteArraySet()

    private var mediaItem: MediaItem? = null

    private var syncClock: MediaClock? = null

    /**
     * 播放器内部状态
     */
    private var innerState: Int = Player.STATE_INIT

    /**
     * 播放器的上一个状态，目前只用于seekTo的状态保存
     */
    private var lastState: Int = Player.STATE_INIT

    init {
        PlayerLog.show = builder.context.isDebugVersion()
        playbackThread.start()
        eventHandler = Handler(playbackThread.looper, this)
        componentListener = ComponentListener()

        mediaSource = builder.sourceFactory.createMediaSource(applicationContext)
        componentListener.let {
            renderers = builder.rendererFactory.createRenders(eventHandler, it, it)
        }
        requestHeaders = builder.requestHeader

        syncClock = renderers.firstOrNull {
            it.getTrackType() == Constants.TRACK_TYPE_AUDIO
        } ?.getMediaClock()
    }

    override fun play(delayTimeMs: Long) {
        Assert.verifyMainThread()

        when(playerState)  {
             Player.STATE_STOP -> {
                 eventHandler.sendMessage(
                     Message.obtain().apply {
                         what = MSG_SEEK_TO
                         obj = 0L
                     }
                 )
            }
            Player.STATE_BUFFERING, Player.STATE_PAUSE, Player.STATE_READY -> {
                if (delayTimeMs > 0) {
                    eventHandler.sendMessageDelayed(
                        Message.obtain().apply {
                            what = MSG_PLAY
                            obj = PENDING_PLAY_TOKEN
                        }, delayTimeMs)
                } else {
                    eventHandler.sendEmptyMessage(MSG_PLAY)
                }
            }
            else -> {}
        }
    }

    override fun canPause(state: Int): Boolean =
        state == Player.STATE_PLAYING || state == Player.STATE_BUFFERING ||
                state == Player.STATE_LOADING

    override fun canSeekTo(state: Int): Boolean =
        state == Player.STATE_PLAYING || state == Player.STATE_PAUSE
                || state == Player.STATE_STOP || state == Player.STATE_BUFFERING


    override fun pause() {
        Assert.verifyMainThread()
        if (canPause(playerState) || playerState == Player.STATE_READY) {
            eventHandler.sendEmptyMessage(MSG_PAUSE)
        }
    }

    override fun seekTo(positionMs: Long) {
        Assert.verifyMainThread()

        if (positionMs > durationMs || positionMs == currentPositionMs) {
            return
        }
        if (canSeekTo(playerState)) {
            eventHandler.sendMessage(Message.obtain().apply {
                what = MSG_SEEK_TO
                obj = positionMs
            })
        }
    }

    override fun release() {
        Assert.verifyMainThread()

        if (playerState != Player.STATE_RELEASE) {
            playerState = Player.STATE_RELEASE
            eventHandler.removeCallbacksAndMessages(null)
            playbackThread.quit()
            renderers.forEach {
                it.disable()
                it.release()
            }
            mediaSource.release()
            videoOutput = null
            eventListenerSet.forEach {
                it.onPlaybackStateChanged(playerState)
            }
            eventListenerSet.clear()
        }
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        this.mediaItem = mediaItem
    }

    override fun prepare() {
        Assert.verifyMainThread("Player:prepare is accessed on the wrong thread")
        if (playerState != Player.STATE_RELEASE) {
            mediaItem?.let {
                eventHandler.sendMessage(Message.obtain().apply {
                    what = MSG_PREPARE
                    obj = it
                })
            }
        }
    }

    override fun setVideoSurfaceView(surfaceView: SurfaceView) {
        Assert.verifyMainThread()

        if (playerState == Player.STATE_RELEASE) {
            return
        }

        this.videoOutput?.let {
            clearVideoSurfaceView(it)
        }

        this.videoOutput = surfaceView
        if (surfaceView is VideoMetadataListener) {
            componentListener.internalVideoMetadataListener = surfaceView
        }
        if (surfaceView is SurfaceProvider) {
            surfaceView.setOnVideoSurfaceListener(componentListener)
            setVideoOutputInternal(surfaceView.surface)
        } else {
            setVideoOutputInternal(surfaceView.holder.surface)
        }
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView) {
        if (this.videoOutput == surfaceView) {
            setVideoOutputInternal(null)
            this.videoOutput = null
        }

        if (surfaceView == componentListener.internalVideoMetadataListener) {
            componentListener.internalVideoMetadataListener = null
        }
        if (surfaceView is SurfaceProvider) {
            surfaceView.setOnVideoSurfaceListener(null)
        }
    }

    override fun addEventListener(eventListener: EventListener) {
        eventListenerSet.add(eventListener)
        eventListener.onPlaybackStateChanged(playerState)
    }

    override fun removeEventListener(eventListener: EventListener) {
        eventListenerSet.remove(eventListener)
    }

    override fun isPlaying(): Boolean = playerState == Player.STATE_PLAYING

    override fun handleMessage(msg: Message): Boolean {
        return when (msg.what) {
            MSG_PREPARE -> {
                prepareInternal(msg.obj as MediaItem)
                true
            }
            MSG_RENDER -> {
                renderInternal(true)
                true
            }
            MSG_PLAY -> {
                playInternal()
                true
            }
            MSG_PAUSE -> {
                pauseInternal()
                true
            }
            MSG_SEEK_TO -> {
                seekToInternal(msg.obj as Long)
                true
            }
            MSG_WAIT_FOR_CACHE -> {
                waitForCacheInternal(msg.obj?.let { it as Long }
                    ?: WAIT_TIME_AS_START_MS)
                true
            }
            MSG_RENDER_FRAME_ONCE -> {
                renderFirstFrameInternal(msg.arg1, msg.obj as Long)
                true
            }
            else -> {
                false
            }
        }
    }

    private fun setVideoOutputInternal(videoOutput: Surface?) {
        if (videoOutput == null) {
            eventListenerSet.forEach { listener ->
                listener.onVideoSurfaceDetach()
            }
        } else {
            eventListenerSet.forEach { listener ->
                listener.onVideoSurfaceAttach()
            }
        }
        eventHandler.post {
            renderers.forEach {
                if (it is VideoSurfaceListener) {
                    if (videoOutput != null) {
                        it.onVideoSurfaceCreated(videoOutput)
                    } else {
                        it.onVideoSurfaceDestroyed(null)
                    }
                }
            }
        }
    }

    private fun prepareInternal(mediaItem: MediaItem) {
        if (eventHandler.hasMessages(MSG_PREPARE)) {
            return
        }
        // 移除除了本身和pause之外的其他message
        eventHandler.removeMessages(MSG_RENDER)
        eventHandler.removeMessages(MSG_PLAY)
        eventHandler.removeMessages(MSG_WAIT_FOR_CACHE)
        eventHandler.removeMessages(MSG_SEEK_TO)
        // 取消首帧渲染
        eventHandler.removeMessages(MSG_RENDER_FRAME_ONCE)

        innerState = Player.STATE_LOADING
        mainHandler.post {
            changeStateUncheck(Player.STATE_LOADING)
        }

        renderers.forEach {
            if (it.state == Renderer.STATE_ENABLE) {
                it.disable()
            }
        }
        // 重置播放位置
        _currentPositionUs = 0L

        runCatching {
            mediaSource.setDataSource(mediaItem, requestHeaders)
        }.onFailure {
            // 播放源初始化失败，转入INIT状态
            innerState = Player.STATE_INIT
            mainHandler.post {
                notifyError(if (it is NetworkUnreachableException) NETWORK_UNREACHABLE_ERROR else
                    SOURCE_ERROR)
                changeStateUncheck(Player.STATE_INIT)
            }
        }.onSuccess {
            // check again
            if (eventHandler.hasMessages(MSG_PREPARE)) {
                return
            }
            val formats = mediaSource.format
            renderers.forEach { renderer ->
                renderer.enable(formats, mediaSource, 0L)
            }

            loopSendData(block = { !eventHandler.hasMessages(MSG_PREPARE) })

            // check again
            if (eventHandler.hasMessages(MSG_PREPARE)) {
                return
            }

            if (playAfterLoading) {
                eventHandler.sendMessageDelayed(
                    Message.obtain().apply {
                        what = MSG_PLAY
                        obj = PENDING_PLAY_TOKEN
                    }, DELAY_FOR_DECODE_MS)
            } else if (mayRenderFirstFrame) {
                eventHandler.sendMessageDelayed(
                    eventHandler.obtainMessage().apply {
                        what = MSG_RENDER_FRAME_ONCE
                        arg1 = Player.STATE_READY
                        obj = 0L
                    }, DELAY_FOR_DECODE_MS
                )
            } else {
                innerState = Player.STATE_READY
                mainHandler.post {
                    changeStateUncheck(Player.STATE_READY)
                }
            }
        }
    }

    private fun renderInternal(loop: Boolean) {
        val loopStart = SystemClock.elapsedRealtime()
        loopSendData()

        val startTimeMs: Long = SystemClock.elapsedRealtime()
        renderers.forEach {
            try {
                it.render(_currentPositionUs, startTimeMs)
            } catch (e: IOException) {
                mainHandler.post {
                    notifyError(IO_ERROR)
                }
                PlayerLog.e(message = e)
            } catch (e: Exception) {
                PlayerLog.e(message = e)
            }
        }

        // 如果已经到达末尾
        if (currentPositionMs >= durationMs) {
            componentListener.run {
                PlayerLog.i(message = "FrameCount: ${totalLose + totalRelease}, FrameLose: " +
                        "$totalLose, ratio: ${totalLose.toFloat() / (totalLose + totalRelease)}")
                resetFrameCount()
            }
            if (infiniteLoop) {
                eventHandler.sendMessage(Message.obtain().apply {
                    what = MSG_SEEK_TO
                    obj = 0L
                })
            } else {
                innerState = Player.STATE_STOP
                mainHandler.post {
                    changeStateUncheck(Player.STATE_STOP)
                }
            }
            return
        }

        val delayTimeMs = (10L - SystemClock.elapsedRealtime() + loopStart).coerceAtLeast(1)
        /*_currentPositionUs = ((SystemClock.elapsedRealtime() - startRenderTimeMs) * 1000
                + delayTimeMs).coerceAtMost(mediaSource.durationUs)*/
        _currentPositionUs = getCurrentPosition(delayTimeMs)

        // 如果数据源缓存不足
        if (shouldWaitForCache()) {
            eventHandler.sendMessage(Message.obtain().apply {
                what = MSG_WAIT_FOR_CACHE
                obj = WAIT_TIME_AS_START_MS
            })
            innerState = Player.STATE_BUFFERING
            mainHandler.post {
                changeStateUncheck(Player.STATE_BUFFERING)
            }
            return
        }

        if (loop) {
            eventHandler.sendEmptyMessageDelayed(MSG_RENDER, delayTimeMs)
        }
    }

    private fun seekToInternal(positionMs: Long) {
        // 如果存在较新的seekTo操作，取消本次操作
        if (eventHandler.hasMessages(MSG_SEEK_TO)) {
            return
        }
        // 取消缓存等待
        eventHandler.removeMessages(MSG_WAIT_FOR_CACHE)
        eventHandler.removeMessages(MSG_RENDER)
        // 取消首帧渲染
        eventHandler.removeMessages(MSG_RENDER_FRAME_ONCE)
        val positionUs = positionMs * 1000

        // 缓存Buffing状态之前的状态，结束seekTo操作后需要自动恢复该状态
        var oldState = innerState
        if (oldState != Player.STATE_BUFFERING) {
            innerState = Player.STATE_BUFFERING
            mainHandler.post {
                changeStateUncheck(Player.STATE_BUFFERING)
            }
        }

        renderers.forEach {
            it.onSeekTo(positionUs)
        }
        val syncTime = mediaSource.seekTo(positionUs, SeekMode.CLOSEST_SYNC)
        //_currentPositionUs = if (syncTime < 0) positionUs else syncTime

        loopSendData()
        // 更新播放位置
        _currentPositionUs = positionUs

        // 存在多个seekTo操作，延迟状态的自动恢复
        if (eventHandler.hasMessages(MSG_SEEK_TO)) {
            // Buffing状态无需缓存
            if (oldState != Player.STATE_BUFFERING) {
                lastState = oldState
                // 延迟处理oldState
                return
            }
        } else {
            if (oldState == Player.STATE_BUFFERING) {
                oldState = lastState
            }
        }

        when(oldState) {
            Player.STATE_PLAYING, Player.STATE_STOP -> {
                // 缓存告急，不应当退出Buffing状态，而应当继续等待
                if (shouldWaitForCache()) {
                    eventHandler.sendMessage(Message.obtain().apply {
                        what = MSG_WAIT_FOR_CACHE
                        obj = WAIT_TIME_AS_START_MS
                    })
                } else {
                    eventHandler.sendMessageDelayed(
                        Message.obtain().apply {
                            what = MSG_PLAY
                            obj = PENDING_PLAY_TOKEN
                        }, DELAY_FOR_DECODE_MS)
                }
            }
            Player.STATE_PAUSE -> {
                eventHandler.sendMessageDelayed(
                    eventHandler.obtainMessage().apply {
                        what = MSG_RENDER_FRAME_ONCE
                        arg1 = Player.STATE_PAUSE
                        obj = syncTime
                    }, DELAY_FOR_DECODE_MS)
            }
        }
    }

    private fun playInternal() {
        eventHandler.removeMessages(MSG_RENDER)
        // 避免重复进行Play操作
        eventHandler.removeMessages(MSG_PLAY)
        // 取消缓存等待
        eventHandler.removeMessages(MSG_WAIT_FOR_CACHE)
        // 取消渲染以避免状态转换至PAUSE(seekTo中)
        eventHandler.removeMessages(MSG_RENDER_FRAME_ONCE)
        // 更新起始时间
        startRenderTimeMs = SystemClock.elapsedRealtime() - _currentPositionUs / 1000
        eventHandler.sendEmptyMessage(MSG_RENDER)
        innerState = Player.STATE_PLAYING
        mainHandler.post {
            changeStateUncheck(Player.STATE_PLAYING)
        }
    }

    private fun pauseInternal() {
        eventHandler.removeMessages(MSG_RENDER)
        // 移除seekTo和prepare发送的pending play
        eventHandler.removeCallbacksAndMessages(PENDING_PLAY_TOKEN)
        // 在Loading或Buffing期间调用的Pause，取消对第一帧的渲染
        eventHandler.removeMessages(MSG_RENDER_FRAME_ONCE)
        eventHandler.removeMessages(MSG_WAIT_FOR_CACHE)

        // Ready状态不应当转入Pause状态
        if (innerState != Player.STATE_READY) {
            innerState = Player.STATE_PAUSE
            renderers.forEach {
                it.onPause()
            }
        }
        mainHandler.post {
            if (playerState != Player.STATE_READY) {
                changeStateUncheck(Player.STATE_PAUSE)
            }
        }
    }

    private fun waitForCacheInternal(delayTimeMs: Long) {
        eventHandler.removeMessages(MSG_RENDER)
        eventHandler.removeMessages(MSG_WAIT_FOR_CACHE)

        if (shouldStopWait()) {
            eventHandler.sendEmptyMessage(MSG_PLAY)
        } else {
            eventHandler.sendMessageDelayed(
                Message.obtain().apply {
                    what = MSG_WAIT_FOR_CACHE
                    obj = (delayTimeMs * 2).coerceAtMost(MAX_WAIT_TIME_MS)
                }, delayTimeMs
            )
        }
    }

    private fun renderFirstFrameInternal(nextState: Int, renderTime: Long) {
        renderVideoFrame(renderTime)
        innerState = nextState
        mainHandler.post {
            changeStateUncheck(nextState)
        }
    }

    private fun renderVideoFrame(positionUs: Long) {
        val startRenderTime = SystemClock.elapsedRealtime()
        renderers.forEach {
            if (it.getTrackType() == Constants.TRACK_TYPE_VIDEO) {
                runCatching {
                    it.render(positionUs, startRenderTime)
                }.onFailure { throwable ->
                    PlayerLog.d(message = throwable)
                }
            }
        }
    }

    // call in main thread
    private fun changeStateUncheck(newValue: Int) {
        if (playerState != Player.STATE_RELEASE && playerState != newValue) {
            playerState = newValue
            eventListenerSet.forEach {
                it.onPlaybackStateChanged(newValue)
            }
        }
    }

    // call in main thread
    private fun notifyError(errorCode: Int) {
        if (playerState != Player.STATE_RELEASE) {
            eventListenerSet.forEach {
                it.onPlayerError(errorCode)
            }
        }
    }

    /**
     * 循环调用[MediaSource.sendData]，直到发送失败或[block]返回false
     */
    private fun loopSendData(block: () -> Boolean = { true }) {
        try {
            var sendData = true
            while (sendData && block()) {
                sendData = mediaSource.sendData()
            }
        } catch (e: IOException) {
            mainHandler.post {
                notifyError(IO_ERROR)
            }
            PlayerLog.e(message = e)
        } catch (e: Exception) {
            PlayerLog.e(message = e)
        }
    }

    /**
     * 是否应该进行缓存等待
     */
    private fun shouldWaitForCache(): Boolean = !mediaSource.hasCacheReachedEndOfStream() &&
            mediaSource.cacheDurationUs in 0L until MIN_CACHE_DURATION_US

    /**
     * 是否应该停止缓存等待
     */
    private fun shouldStopWait(): Boolean = mediaSource.hasCacheReachedEndOfStream() ||
            mediaSource.cacheDurationUs >= MAX_CACHE_DURATION_US

    /**
     * 计算当前播放位置
     */
    private fun getCurrentPosition(delayTimeMs: Long): Long {
        val durationUs = mediaSource.durationUs
        val realTime = ((SystemClock.elapsedRealtime() - startRenderTimeMs) * 1000
                + delayTimeMs).coerceAtMost(durationUs)
        val clock = syncClock ?: return realTime
        val time: Long = clock.getPositionUs(durationUs)

        return if (time == MediaClock.END_OF_RENDER || time < 0) {
            realTime
        } else {
            time.coerceAtMost(realTime)
        }
    }

    private inner class ComponentListener
        : VideoSurfaceListener, VideoMetadataListener, VideoFrameListener {
        var internalVideoMetadataListener: VideoMetadataListener? = null
            set(value) {
                field = value
                if (::cacheFormat.isInitialized) {
                    value?.onVideoMetadataChanged(cacheFormat)
                }
            }
        lateinit var cacheFormat: Format

        var totalLose: Int = 0
        var totalRelease: Int = 0

        override fun onVideoSurfaceCreated(surface: Surface) {
            setVideoOutputInternal(surface)
        }

        override fun onVideoSurfaceDestroyed(surface: Surface?) {
            setVideoOutputInternal(null)
        }

        override fun onVideoMetadataChanged(format: Format) {
            internalVideoMetadataListener?.onVideoMetadataChanged(format)

            cacheFormat = format
        }

        override fun onFrameRelease() {
            totalRelease ++
        }

        override fun onFrameLose() {
            totalLose ++
        }

        fun resetFrameCount() {
            totalRelease = 0
            totalLose = 0
        }
    }

    companion object {
        private const val MSG_PREPARE = 1
        private const val MSG_RENDER = 2
        private const val MSG_SEEK_TO = 3
        private const val MSG_PLAY = 4
        private const val MSG_PAUSE = 5
        private const val MSG_WAIT_FOR_CACHE = 6
        private const val MSG_RENDER_FRAME_ONCE = 7

        private const val DELAY_FOR_DECODE_MS = 100L

        /**
         * 延迟进行播放的Message(MSG_PLAY)所携带的Token
         */
        private val PENDING_PLAY_TOKEN = Any()

        /**
         * 如果缓存时长低于此值，则应进入等待状态
         */
        private const val MIN_CACHE_DURATION_US = 1000000L

        /**
         * 如果缓存时长高于此值，则应离开等待状态
         */
        private const val MAX_CACHE_DURATION_US = 3500000L

        /**
         * Cache轮询初始时间
         */
        private const val WAIT_TIME_AS_START_MS = 100L

        /**
         * Cache轮询的最大时间
         */
        private const val MAX_WAIT_TIME_MS = 800L

        /**
         * 提供的媒体数据无法成功加载数据
         */
        const val SOURCE_ERROR = 11

        const val IO_ERROR = 12

        /**
         * 无网络连接而引发的异常
         */
        const val NETWORK_UNREACHABLE_ERROR = 13
    }
}