package top.gochiusa.glplayer.opengl

import android.content.Context
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import top.gochiusa.glplayer.base.SurfaceProvider
import top.gochiusa.glplayer.entity.Format
import top.gochiusa.glplayer.listener.VideoMetadataListener
import top.gochiusa.glplayer.listener.VideoSurfaceListener
import top.gochiusa.glplayer.util.PlayerLog

class VideoGLSurfaceView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private var onVideoSurfaceListener: VideoSurfaceListener? = null
): GLSurfaceView(context, attrs), VideoMetadataListener, SurfaceProvider,
    SurfaceTexture.OnFrameAvailableListener {

    private val mainHandler: Handler = Handler(Looper.myLooper()!!)

    private var surfaceTexture: SurfaceTexture? = null

    override var surface: Surface? = null
        private set

    private var started = true

    private val renderer: ProgramsRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = ProgramsRenderer(this)
        setRenderer(renderer)
        // 设置为DIRTY时才渲染
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun setClearColor(color: Color) {
        queueEvent {
            renderer.clearColor = color
        }
    }

    override fun setOnVideoSurfaceListener(listener: VideoSurfaceListener?) {
        onVideoSurfaceListener = listener
    }

    override fun onPause() {
        super.onPause()
        started = false
    }

    override fun onResume() {
        super.onResume()
        started = true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mainHandler.post {
            val oldSurface = surface
            oldSurface?.let {
                onVideoSurfaceListener?.onVideoSurfaceDestroyed(it)
            }
            releaseSurface(surfaceTexture, oldSurface)
            surfaceTexture = null
            surface = null
        }
    }

    internal fun onSurfaceTextureAvailable(newSurfaceTexture: SurfaceTexture) {
        mainHandler.post {
            val oldSurfaceTexture = surfaceTexture
            val oldSurface = surface

            val newSurface = Surface(newSurfaceTexture)

            surface = newSurface
            surfaceTexture = newSurfaceTexture

            newSurfaceTexture.setOnFrameAvailableListener(this)

            onVideoSurfaceListener?.onVideoSurfaceCreated(newSurface)

            releaseSurface(oldSurfaceTexture, oldSurface)
        }
    }

    private fun releaseSurface(surfaceTexture: SurfaceTexture?, surface: Surface?) {
        surfaceTexture?.let {
            it.setOnFrameAvailableListener(null)
            it.release()
        }
        surface?.release()
    }

    override fun onVideoMetadataChanged(format: Format) {
        renderer.setVideoInfo(format.width, format.height, format.rotation)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        if (started) {
            queueEvent {
                surfaceTexture?.apply {
                    runCatching {
                        updateTexImage()
                    }.onFailure {
                        PlayerLog.e(message = it)
                    }
                    requestRender()
                }
            }
        }
    }
}