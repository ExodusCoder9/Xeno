struct SectionData {
    mat4 ModelViewMat;
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    SectionData sections[1024];
};

// In vertex shader, use gl_InstanceID directly
// In fragment shader, use the flat instanceId varying passed from vertex shader
#ifdef VERTEX_SHADER
#define INSTANCE_ID gl_InstanceID
#else
#define INSTANCE_ID instanceId
#endif

#define ModelViewMat sections[INSTANCE_ID].ModelViewMat
#define ChunkVisibility sections[INSTANCE_ID].ChunkVisibility
#define TextureSize sections[INSTANCE_ID].TextureSize
#define ChunkPosition sections[INSTANCE_ID].ChunkPosition
