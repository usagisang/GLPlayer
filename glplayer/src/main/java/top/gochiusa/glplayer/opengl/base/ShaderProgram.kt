package top.gochiusa.glplayer.opengl.base

import android.content.Context
import android.opengl.GLES20.*
import androidx.annotation.RawRes
import top.gochiusa.glplayer.util.ShaderHelper
import top.gochiusa.glplayer.util.readStringFromRaw

/**
 * 代表着色器程序的类，简单从raw资源文件夹中读取着色器代码
 */
abstract class ShaderProgram(
    context: Context,
    @RawRes vertexShaderResId: Int,
    @RawRes fragmentShaderResId: Int,
) {
    protected val programId: Int

    init {
        with(context) {
            programId = ShaderHelper.buildProgram(
                readStringFromRaw(vertexShaderResId),
                readStringFromRaw(fragmentShaderResId)
            )
        }
    }

    fun getAttribLocation(name: String): Int = glGetAttribLocation(programId, name)

    fun getUniformLocation(name: String): Int = glGetUniformLocation(programId, name)

    fun useProgram() {
        glUseProgram(programId)
    }
}