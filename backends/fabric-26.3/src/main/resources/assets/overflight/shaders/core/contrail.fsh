#version 330
#extension GL_ARB_separate_shader_objects : require

// The 26.3 form of common/resources/.../contrail.fsh; see contrail.vsh.

#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 2) in vec4 vertexColor;
layout(location = 4) in vec4 lightMapColor;
layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = texture(Sampler0, texCoord0) * vertexColor * lightMapColor * ColorModulator;
}
