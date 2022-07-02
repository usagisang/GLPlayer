package top.gochiusa.glplayer.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLUtils
import androidx.annotation.DrawableRes

object ShaderHelper {
    private const val TAG = "ShaderHelper"

    /**
     * 编译顶点着色器代码
     */
    fun compileVertexShader(shaderCode: String) = compileShader(GL_VERTEX_SHADER, shaderCode)

    /**
     * 编译片元着色器代码
     */
    fun compileFragmentShader(shaderCode: String) = compileShader(GL_FRAGMENT_SHADER, shaderCode)


    /**
     * 编译着色器代码
     * @param type 表示着色器类型的常量，可以是[GL_FRAGMENT_SHADER]或[GL_VERTEX_SHADER]
     */
    fun compileShader(type: Int, shaderCode: String): Int {
        // 创建着色器
        val shaderObjectId = glCreateShader(type)
        if (type == 0) {
            PlayerLog.d(tag = TAG, message = "Could not create new shader")
            return 0
        }
        // 传输并编译代码
        glShaderSource(shaderObjectId, shaderCode)
        glCompileShader(shaderObjectId)

        // 获得代码编译结果
        val compileStatus = IntArray(1)
        glGetShaderiv(shaderObjectId, GL_COMPILE_STATUS, compileStatus, 0)
        PlayerLog.v(tag = TAG, message ="Results of compiling source:" +
                "\n$shaderCode\n:${glGetShaderInfoLog(shaderObjectId)}")

        if (compileStatus[0] == 0) {
            // 编译失败，删除着色器
            glDeleteShader(shaderObjectId)
            PlayerLog.w(TAG, "Compilation of shader failed.")
            return 0
        }
        return shaderObjectId
    }

    /**
     * 将顶点着色器与片元着色器链接在一起构成完整的 OpenGL ES 程序
     */
    fun linkProgram(vertexShaderId: Int, fragmentShaderId: Int): Int {
        val programObjectId = glCreateProgram()
        if (programObjectId == 0) {
            PlayerLog.w(TAG, "Could not create new program")
            return 0
        }
        // 让程序连接对应的着色器
        glAttachShader(programObjectId, vertexShaderId)
        glAttachShader(programObjectId, fragmentShaderId)

        glLinkProgram(programObjectId)
        val linkStatus = IntArray(1)
        glGetProgramiv(programObjectId, GL_LINK_STATUS, linkStatus, 0)
        PlayerLog.v(TAG, "Results of linking program:\n${glGetProgramInfoLog(programObjectId)}")

        if (linkStatus[0] == 0) {
            glDeleteProgram(programObjectId)
            PlayerLog.w(TAG, "Linking of program failed.")
            return 0
        }
        return programObjectId
    }

    /**
     * 验证程序是否有效，并输出日志
     */
    fun validateProgram(programObjectId: Int): Boolean {
        glValidateProgram(programObjectId)
        val validateStatus = IntArray(1)
        glGetProgramiv(programObjectId, GL_VALIDATE_STATUS, validateStatus, 0)
        PlayerLog.v(TAG, "Results of validating program: " + validateStatus[0]
                + "\nLog:" + glGetProgramInfoLog(programObjectId))
        return validateStatus[0] != 0
    }

    /**
     * 编译着色器代码，并创建程序
     */
    fun buildProgram(vertexShaderSource: String, fragmentShaderSource: String): Int {
        val vertexShaderId = compileVertexShader(vertexShaderSource)
        val fragmentShaderId = compileFragmentShader(fragmentShaderSource)
        val program = linkProgram(vertexShaderId, fragmentShaderId)
        validateProgram(program)
        return program
    }

    /**
     * 申请一个OES纹理id
     */
    fun createOESTextureId(): Int {
        val textureObjectIds = IntArray(1)
        glGenTextures(1, textureObjectIds, 0)
        if (textureObjectIds[0] == 0) {
            PlayerLog.w(message = "Could not generate a new OpenGL texture object.")
            return 0
        }
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureObjectIds[0])
        // 设置纹理参数
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        // 设置超出范围的纹理处理方式
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

        // 将纹理ID绑定到纹理单元的纹理目标上，这里选择的纹理目标是GL_TEXTURE_EXTERNAL_OES，可以自动完成 YUV 格式到 RGB 的自动转换
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)

        return textureObjectIds[0]
    }

    /**
     * 从资源文件中加载纹理
     */
    fun Context.loadTexture(@DrawableRes resourceId: Int): Int {
        val textureObjectIds = IntArray(1)
        glGenTextures(1, textureObjectIds, 0)
        if (textureObjectIds[0] == 0) {
            PlayerLog.w(message = "Could not generate a new OpenGL texture object.")
            return 0
        }
        val option = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bitmap: Bitmap? = BitmapFactory.decodeResource(resources, resourceId, option)
        if (bitmap == null) {
            PlayerLog.w(message = "Resource ID $resourceId could not be decoded.")
            glDeleteTextures(1, textureObjectIds, 0)
            return 0
        }
        glBindTexture(GL_TEXTURE_2D, textureObjectIds[0])
        // 设置默认过滤参数
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

        // 将位图数据加载到OpenGL
        GLUtils.texImage2D(GL_TEXTURE_2D, 0, bitmap, 0)
        // 回收位图
        bitmap.recycle()
        // 生成MIP映射需要的纹理
        glGenerateMipmap(GL_TEXTURE_2D)
        // 解除纹理绑定（传入的纹理id为0）
        glBindTexture(GL_TEXTURE_2D, 0)
        return textureObjectIds[0]
    }
}