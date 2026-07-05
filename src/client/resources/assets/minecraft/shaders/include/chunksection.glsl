struct SectionData {
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    mat4 ModelViewMat;
    SectionData sections[1024];
};

// In vertex shader, use gl_InstanceID directly
// In fragment shader, use the flat instanceId varying passed from vertex shader
#ifdef VERTEX_SHADER
#define INSTANCE_ID gl_InstanceID
#else
#define INSTANCE_ID instanceId
#endif

#define ChunkVisibility sections[INSTANCE_ID].ChunkVisibility
#define TextureSize sections[INSTANCE_ID].TextureSize
#define ChunkPosition sections[INSTANCE_ID].ChunkPosition
