#version 300 es

#ifdef GL_ES
#extension GL_OES_standard_derivatives : enable
precision highp float;
precision highp int;
#else
#define highp
#define mediump
#define lowp
#endif

in vec4 v_color;

out vec4 FragColor;

void main()
{
    FragColor = v_color;
}