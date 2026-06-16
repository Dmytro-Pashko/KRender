attribute vec3 a_position;
attribute vec2 a_texCoord{{UV_CHANNEL}};
uniform mat4 u_projViewTrans;
uniform mat4 u_worldTrans;
varying vec2 v_uv;

void main() {
    v_uv = a_texCoord{{UV_CHANNEL}};
    gl_Position = u_projViewTrans * u_worldTrans * vec4(a_position, 1.0);
}
