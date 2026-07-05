struct SectionData {
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    mat4 ModelViewMat;
    SectionData sections[1024];
};

#ifdef VERTEX_SHADER
  #define INSTANCE_ID gl_DrawID
#else
  #define INSTANCE_ID instanceId
#endif
