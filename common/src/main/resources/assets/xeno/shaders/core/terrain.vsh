#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in uvec2 Position;
in vec4 Color;
in vec2 UV0;
in uvec4 UV2;

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

const float VERTEX_SCALE = 32.0 / 1048576.0;
const float VERTEX_OFFSET = -8.0;

uvec3 deinterleave_pos(uvec2 data) {
    uvec3 hi = (uvec3(data.x) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    uvec3 lo = (uvec3(data.y) >> uvec3(0u, 10u, 20u)) & 0x3FFu;
    return (hi << 10u) | lo;
}

void main() {
    vec3 localPos = (vec3(deinterleave_pos(Position)) * VERTEX_SCALE) + VERTEX_OFFSET;
    vec3 pos = localPos + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);
    vertexColor = Color * sample_lightmap(Sampler2, ivec2(UV2.xy));
    texCoord0 = UV0;
}
