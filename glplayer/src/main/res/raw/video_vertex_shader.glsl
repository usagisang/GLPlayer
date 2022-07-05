uniform mat4 u_Matrix;
// 与纹理相关的TransformMatrix
uniform mat4 v_TextureMatrix;
// 顶点坐标
attribute vec4 a_Position;
// 纹理坐标
attribute vec4 a_TextureCoordinates;
// 线性插值坐标
varying vec2 v_TextureCoordinates;

void main() {
    v_TextureCoordinates = (v_TextureMatrix * a_TextureCoordinates).xy;
    gl_Position = u_Matrix * a_Position;
}
