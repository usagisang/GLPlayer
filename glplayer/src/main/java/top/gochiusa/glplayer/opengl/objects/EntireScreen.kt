package top.gochiusa.glplayer.opengl.objects

import android.opengl.GLES20.GL_TRIANGLE_FAN
import android.opengl.GLES20.glDrawArrays
import top.gochiusa.glplayer.opengl.base.ProgramData
import top.gochiusa.glplayer.opengl.data.VertexArray
import top.gochiusa.glplayer.opengl.programs.VideoShaderProgram
import top.gochiusa.glplayer.util.Constants


internal class EntireScreen: ProgramData<VideoShaderProgram> {

    private val vertexArray: VertexArray = VertexArray(vertexData)

    override fun bindData(program: VideoShaderProgram) {
        vertexArray.setVertexAttribPointer(
            dataOffset = 0,
            attributeLocation = program.aPositionLocation,
            componentCount = POSITION_COMPONENT_COUNT,
            stride = STRIDE
        )
        vertexArray.setVertexAttribPointer(
            dataOffset = POSITION_COMPONENT_COUNT,
            attributeLocation = program.aTextureCoordinatesLocation,
            componentCount = TEXTURE_COORDINATES_COMPONENT_COUNT,
            stride = STRIDE
        )
    }

    override fun draw() {
        glDrawArrays(GL_TRIANGLE_FAN, 0, 6)
    }


    companion object {
        private const val POSITION_COMPONENT_COUNT = 2
        private const val TEXTURE_COORDINATES_COMPONENT_COUNT = 2
        private const val STRIDE: Int = (POSITION_COMPONENT_COUNT
                + TEXTURE_COORDINATES_COMPONENT_COUNT) * Constants.BYTES_PER_FLOAT

        private val vertexData: FloatArray = floatArrayOf(
            // 数据顺序: X, Y, S, T
            // 三角形扇形
            0f, 0f, 0.5f, 0.5f,
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            1f, 1f, 1f, 0f,
            -1f, 1f, 0f, 0f,
            -1f, -1f, 0f, 1f
        )
    }
}