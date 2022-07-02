package top.gochiusa.glplayer.opengl.data

import android.opengl.GLES20.*
import top.gochiusa.glplayer.util.Constants
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * 对顶点数据的简单封装类
 */
class VertexArray(
    vertexData: FloatArray
) {
    private val floatBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * Constants.BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertexData)

    /**
     * 将[floatBuffer]内的数据作为属性数据传输到OpenGL中
     * @param dataOffset 第一个该属性数据的位置
     * @param attributeLocation 属性的位置id
     * @param componentCount 属性包含多少分量
     * @param stride 读取属性时需要的步长
     */
    fun setVertexAttribPointer(dataOffset: Int, attributeLocation: Int,
                               componentCount: Int, stride: Int) {
        floatBuffer.position(dataOffset)
        glVertexAttribPointer(attributeLocation, componentCount, GL_FLOAT,
            false, stride, floatBuffer)
        glEnableVertexAttribArray(attributeLocation)
        // 将偏移量复原
        floatBuffer.position(0)
    }
}