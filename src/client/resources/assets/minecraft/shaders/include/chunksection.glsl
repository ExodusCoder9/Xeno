struct ChunkSectionData {
    mat4 ModelViewMat;
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    ChunkSectionData sections[512];
};
