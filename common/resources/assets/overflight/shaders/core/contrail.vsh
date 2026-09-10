#version 330

// A copy of core/entity with three things taken out, and nothing put in.
//
//  - no fog: entity.fsh applies it unconditionally, and a trail beyond the fog
//    end arrives painted the colour of the sky it is meant to stand against
//  - no ALPHA_CUTOUT: it discards fragments where the texture is faint, which
//    is most of what a contrail is
//  - no cardinal lighting: minecraft_mix_light shades by normal and floors a
//    white trail at 0.4 grey
//
// The lightmap stays, because the render setup declares it and every vanilla
// type that draws does the same. Trails are handed full brightness, so it
// multiplies by white.

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;

out vec4 vertexColor;
out vec4 lightMapColor;
out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    lightMapColor = sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;
}
