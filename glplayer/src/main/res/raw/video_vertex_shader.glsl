uniform mat4 u_Matrix;
// 顶点坐标
attribute vec4 a_Position;
// 纹理坐标
attribute vec2 a_TextureCoordinates;
// 线性插值坐标
varying vec2 v_TextureCoordinates;

void main() {
    v_TextureCoordinates = a_TextureCoordinates;
    gl_Position = u_Matrix * a_Position;
}
