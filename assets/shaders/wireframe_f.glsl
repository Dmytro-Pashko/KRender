#ifdef GL_ES
precision mediump float;
#endif

uniform vec3 u_wireframeColor;
void main() {
    gl_FragColor = vec4(u_wireframeColor, 1.0);
}