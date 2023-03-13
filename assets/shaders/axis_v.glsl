#version 330 core

in vec3 a_position;
in vec4 a_color;

uniform mat4 cameraCombinedMatrix;

out vec4 v_color;

void main()
{
    gl_Position = cameraCombinedMatrix * vec4(a_position, 1.0);
    v_color = a_color;
}