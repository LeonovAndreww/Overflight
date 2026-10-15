#version 330
#extension GL_ARB_separate_shader_objects : require

// The 26.3 form of common/resources/.../contrail.vsh: same shader, written the
// way 26.3 wants it. Includes are #include rather than #moj_import, and every
// input and output carries an explicit location, because the stages are now
// compiled separately and matched by location rather than by name. The
// locations are vanilla entity's, so the two read alike.

#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler2;

layout(location = 2) out vec4 vertexColor;
layout(location = 4) out vec4 lightMapColor;
layout(location = 6) out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
