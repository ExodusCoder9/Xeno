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
  #if defined(VULKAN) || defined(SPIRV) || defined(GL_SPIRV)
    #define INSTANCE_ID gl_InstanceIndex
  #else
    #define INSTANCE_ID gl_DrawID
  #endif
#else
  #define INSTANCE_ID instanceId
#endif
