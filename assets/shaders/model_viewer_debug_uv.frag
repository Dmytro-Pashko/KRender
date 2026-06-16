#ifdef GL_ES
precision mediump float;
#endif

varying vec2 v_uv;
uniform int u_DebugMode;
uniform int u_DebugComponent;
uniform sampler2D u_DebugTexture;
uniform float u_UvCheckerScale;

void main() {
    vec2 uv = v_uv;
    if (u_DebugMode == {{MODE_UV_CHECKER}}) {
        uv = v_uv * u_UvCheckerScale;
    }
    vec4 texel = texture2D(u_DebugTexture, uv);
    if (u_DebugComponent == {{COMPONENT_R}}) {
        gl_FragColor = vec4(vec3(texel.r), 1.0);
    } else if (u_DebugComponent == {{COMPONENT_G}}) {
        gl_FragColor = vec4(vec3(texel.g), 1.0);
    } else if (u_DebugComponent == {{COMPONENT_B}}) {
        gl_FragColor = vec4(vec3(texel.b), 1.0);
    } else if (u_DebugComponent == {{COMPONENT_A}}) {
        gl_FragColor = vec4(vec3(texel.a), 1.0);
    } else if (u_DebugComponent == {{COMPONENT_RGBA}}) {
        gl_FragColor = texel;
    } else {
        gl_FragColor = vec4(texel.rgb, 1.0);
    }
}
