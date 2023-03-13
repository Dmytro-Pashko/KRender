#version 330 core

in vec3 a_position;

uniform mat4 cameraCombinedMatrix;

void main()
{
    gl_Position = cameraCombinedMatrix * vec4(a_position, 1.0);
}