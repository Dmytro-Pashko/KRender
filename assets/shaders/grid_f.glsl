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

uniform vec4 lineColor;

out vec4 fragColor;

void main()
{
    fragColor = lineColor;
}