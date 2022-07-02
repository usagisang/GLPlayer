package top.gochiusa.glplayer.opengl

import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import top.gochiusa.glplayer.opengl.base.ProgramData
import top.gochiusa.glplayer.opengl.objects.EntireScreen
import top.gochiusa.glplayer.opengl.programs.VideoShaderProgram
import top.gochiusa.glplayer.util.ShaderHelper
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ProgramsRenderer(
    private val glSurfaceView: VideoGLSurfaceView
): GLSurfaceView.Renderer {

    var clearColor: Color? = null

    /**
     * OES纹理的id
     */
    private var textureId: Int = -1

    private lateinit var surfaceTexture: SurfaceTexture

    /**
     * 着色器代码实体
     */
    private lateinit var videoShaderProgram: VideoShaderProgram

    /**
     * 绘制数据实体
     */
    private lateinit var entireScreen: ProgramData<VideoShaderProgram>

    /**
     * 投影矩阵
     */
    private val projectionMatrix: FloatArray = FloatArray(16)

    /**
     * 模型矩阵
     */
    private val modelMatrix: FloatArray = FloatArray(16)

    private var videoWidth: Int = -1
    private var videoHeight: Int = -1
    private var videoRotation: Float = 0F

    private var surfaceWidth: Int = -1
    private var surfaceHeight: Int = -1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // 在create时尝试进行一次设置
        clearColor?.let {
            glClearColor(it.red(), it.green(), it.blue(), it.alpha())
        }
        glSurfaceView.onSurfaceTextureAvailable(init())

        Matrix.setIdentityM(projectionMatrix, 0)
        videoShaderProgram = VideoShaderProgram(glSurfaceView.context)
        entireScreen = EntireScreen()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        glViewport(0, 0, width, height)

        surfaceWidth = width
        surfaceHeight = height
        refreshMatrix()
    }

    override fun onDrawFrame(gl: GL10?) {
        clearColor?.let {
            glClearColor(it.red(), it.green(), it.blue(), it.alpha())
        }
        // 把窗口清除为glClearColor的颜色
        glClear(GL_COLOR_BUFFER_BIT)

        videoShaderProgram.useProgram()
        videoShaderProgram.setUniforms(projectionMatrix, textureId)

        entireScreen.bindData(videoShaderProgram)
        entireScreen.draw()
    }

    fun setVideoInfo(width: Int, height: Int, rotation: Int) {
        // rotation是顺时针旋转的信息，我们需要进行一次反转
        val antiClockwise = (360 - rotation).coerceAtLeast(0)
        if (width > 0 && height > 0) {
            if (antiClockwise % 180 == 0) {
                videoWidth = width
                videoHeight = height
            } else {
                // 随着视频的旋转，width和height的语义也发生了变化
                videoWidth = height
                videoHeight = width
            }
        } else {
            videoWidth = -1
            videoHeight = -1
        }
        videoRotation = antiClockwise.toFloat()
        refreshMatrix()
    }

    private fun init(): SurfaceTexture {
        textureId = ShaderHelper.createOESTextureId()
        surfaceTexture = SurfaceTexture(textureId)
        return surfaceTexture
    }

    private fun refreshMatrix() {
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setIdentityM(projectionMatrix, 0)
        val screenRatio: Float = (surfaceWidth.toFloat()) / (surfaceHeight.toFloat())

        val videoRatio: Float = if (videoWidth <= 0 || videoHeight <= 0) {
            screenRatio
        } else {
            (videoWidth.toFloat()) / (videoHeight.toFloat())
        }

        // 这种计算方式假定了虚拟坐标为1，如果虚拟坐标修改，则投影矩阵的计算方式也需要修改
        if (videoRatio > screenRatio) {
            val r = videoRatio / screenRatio
            Matrix.orthoM(projectionMatrix, 0, -1F, 1F, -r, r, -1F, 1F)
        } else {
            val r = screenRatio / videoRatio
            Matrix.orthoM(projectionMatrix, 0, -r, r, -1F, 1F, -1F, 1F)
        }
        // 调整模型矩阵
        Matrix.rotateM(modelMatrix, 0, videoRotation, 0f, 0f, 1f)

        val temp = FloatArray(16)
        Matrix.multiplyMM(temp, 0, projectionMatrix, 0, modelMatrix, 0)
        System.arraycopy(temp, 0, projectionMatrix, 0, temp.size)
    }
}