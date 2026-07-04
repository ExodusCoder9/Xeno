#version 330
#moj_import <minecraft:extensions.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:chunksection.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>

in ivec3 Position; // RGB16_SINT (x, y, z local positions in mm)
in vec4 Color;     // RGBA8_UNORM
in ivec2 UV0;      // RG16_SINT
in ivec2 UV2;      // RG8_SINT (x contains blockLight & normalId, y contains skyLight)

uniform sampler2D Sampler2;

out float sphericalVertexDistance;
out float cylindricalVertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    // 1. Decode local position from millimeters to meters
    vec3 localPos = vec3(Position) / 1000.0;

    // 2. Reconstruct world position using ChunkPosition (same as vanilla)
    vec3 pos = localPos + (ChunkPosition - CameraBlockPos) + CameraOffset;
    gl_Position = ProjMat * ModelViewMat * vec4(pos, 1.0);

    sphericalVertexDistance = fog_spherical_distance(pos);
    cylindricalVertexDistance = fog_cylindrical_distance(pos);

    // 3. Unpack packed lightmap coordinates (blockLight: x & 0x0F, skyLight: y & 0x0F)
    int blockLight = UV2.x & 0x0F;
    int skyLight = UV2.y & 0x0F;
    ivec2 lightmapCoords = ivec2(blockLight * 16 + 8, skyLight * 16 + 8);

    // Reconstruct lightmap color
    vertexColor = Color * sample_lightmap(Sampler2, lightmapCoords);

    // 4. Reconstruct texture coordinates
    texCoord0 = vec2(UV0) / 32767.0;
}
