package top.gochiusa.glplayer.opengl.base

/**
 * 与着色器程序相关联的数据类接口
 */
interface ProgramData<T: ShaderProgram> {

    /**
     * 将数据类内的数据绑定到着色器程序[program]中
     */
    fun bindData(program: T)

    /**
     * 指示如何绘制该类
     */
    fun draw()
}