package top.gochiusa.glplayer.opengl.programs

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import top.gochiusa.glplayer.R
import top.gochiusa.glplayer.opengl.base.ShaderProgram

internal class VideoShaderProgram(
    context: Context
): ShaderProgram(context, R.raw.video_vertex_shader, R.raw.video_fragment_shader) {

    private val uTextureLocation: Int = getUniformLocation(U_TEXTURE)
    private val uMatrixLocation: Int = getUniformLocation(U_MATRIX)

    val aPositionLocation: Int = getAttribLocation(A_POSITION)

    val aTextureCoordinatesLocation = getAttribLocation(A_TEXTURE_COORDINATES)

    fun setUniforms(matrix: FloatArray, textureId: Int) {
        glUniformMatrix4fv(uMatrixLocation, 1, false, matrix, 0)
        // 设置活动的纹理单元
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        glUniform1i(uTextureLocation, 0)
    }

    companion object {
        private const val A_POSITION = "a_Position"
        private const val A_TEXTURE_COORDINATES = "a_TextureCoordinates"
        private const val U_TEXTURE = "u_Texture"
        private const val U_MATRIX = "u_Matrix"
    }
}