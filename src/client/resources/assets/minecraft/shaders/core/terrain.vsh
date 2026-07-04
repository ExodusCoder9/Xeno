#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in ivec4 Position; // RGBA16_SINT (x, y, z are positions in mm, w is normal ID)
in vec4 Color;     // RGBA8_UNORM
in ivec2 UV0;      // RG16_SINT
in ivec2 UV2;      // RG16_SINT

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // 1. Decode local position from millimeters to meters
    vec3 localPos = vec3(Position.xyz) / 1000.0;

    // 2. Reconstruct world position using ChunkPosition (same as vanilla)
    vec3 pos = localPos + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    // 3. Reconstruct lightmap coordinates
    vec2 lightmapUV = vec2(UV2) / 16.0;
    vertexColor = Color * sample_lightmap(Sampler2, lightmapUV);

    // 4. Reconstruct texture coordinates
    texCoord0 = vec2(UV0) / 32767.0;
}
