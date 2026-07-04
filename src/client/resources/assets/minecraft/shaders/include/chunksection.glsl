struct SectionData {
    mat4 ModelViewMat;
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    SectionData sections[1024];
};

// Use gl_InstanceID instead of gl_DrawID (no extension needed)
#define ModelViewMat sections[gl_InstanceID].ModelViewMat
#define ChunkVisibility sections[gl_InstanceID].ChunkVisibility
#define TextureSize sections[gl_InstanceID].TextureSize
#define ChunkPosition sections[gl_InstanceID].ChunkPosition
