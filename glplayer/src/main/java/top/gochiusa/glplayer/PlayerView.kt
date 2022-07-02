package top.gochiusa.glplayer

import android.content.Context
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import top.gochiusa.glplayer.base.Player
import top.gochiusa.glplayer.opengl.VideoGLSurfaceView
import top.gochiusa.glplayer.util.Assert

/**
 * 播放器渲染媒体的顶层视图
 */
class PlayerView
@JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), DefaultLifecycleObserver {

    private var internalPlayer: Player? = null

    val bindPlayer: Player?
        get() = internalPlayer

    private val surfaceView: SurfaceView

    private var lifecycle: Lifecycle? = null

    init {
        surfaceView = VideoGLSurfaceView(context)
        addView(
            surfaceView, 0,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    fun setPlayer(player: Player?) {
        Assert.verifyMainThread("PlayerView:setPlayer is accessed on the wrong thread")

        if (internalPlayer != player) {
            val oldPlayer = internalPlayer
            oldPlayer?.clearVideoSurfaceView(surfaceView)
            internalPlayer = player
            player?.setVideoSurfaceView(surfaceView)
        }
    }


    /**
     * 当用户不再能够看见播放平面时应当调用的方法，
     * 应用进入后台时，如果仍保持对媒体流的渲染，则必须调用该方法，否则，视频流渲染将异常终止。
     * 此方法与[onResume]相对应
     */
    fun onPause() {
        if (surfaceView is GLSurfaceView) {
            surfaceView.onPause()
        }
    }

    /**
     * 当用户可以看见播放平面时应当调用的方法。
     * 此方法与[onPause]相对应，如果曾调用过[onPause]暂停渲染，则渲染将在调用[onResume]后才会恢复
     */
    fun onResume() {
        if (surfaceView is GLSurfaceView) {
            surfaceView.onResume()
        }
    }

    /**
     * 绑定[Lifecycle]，这将允许[PlayerView]在合适的时机自动回调[onResume]和[onPause]
     * 与[Lifecycle]的绑定将在[onDetachedFromWindow]中自动解除
     */
    fun bindLifecycle(lifecycle: Lifecycle) {
        lifecycle.addObserver(this)
        this.lifecycle = lifecycle
    }

    /**
     * 解除与[Lifecycle]的绑定
     */
    fun unbindLifecycle() {
        lifecycle?.removeObserver(this)
        lifecycle = null
    }

    /**
     * 修改[surfaceView]的背景颜色
     */
    fun setSurfaceBackground(color: Color) {
        if (surfaceView is VideoGLSurfaceView) {
            surfaceView.setClearColor(color)
        } else {
            surfaceView.setBackgroundColor(color.toArgb())
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lifecycle?.addObserver(this)
        rebindPlayerUncheck()
    }

    override fun onDetachedFromWindow() {
        internalPlayer?.clearVideoSurfaceView(surfaceView)
        lifecycle?.removeObserver(this)
        super.onDetachedFromWindow()
    }

    override fun onResume(owner: LifecycleOwner) {
        onResume()
    }

    override fun onPause(owner: LifecycleOwner) {
        onPause()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unbindLifecycle()
    }

    override fun onScreenStateChanged(screenState: Int) {
        super.onScreenStateChanged(screenState)
        // 当锁屏状态发生变化时，自动暂停/恢复渲染
        when (screenState) {
            SCREEN_STATE_ON -> {
                onResume()
            }
            SCREEN_STATE_OFF -> {
                onPause()
            }
        }
    }

    private fun rebindPlayerUncheck() {
        internalPlayer?.setVideoSurfaceView(surfaceView)
    }
}