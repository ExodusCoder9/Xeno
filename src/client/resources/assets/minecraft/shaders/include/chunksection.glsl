struct SectionData {
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    mat4 ModelViewMat;
    SectionData sections[1024];
};

#extension GL_ARB_shader_draw_parameters : enable

// In vertex shader, use gl_DrawIDARB when available
// In fragment shader, use the flat instanceId varying passed from vertex shader
#ifdef VERTEX_SHADER
  #ifdef GL_ARB_shader_draw_parameters
    #define INSTANCE_ID gl_DrawIDARB
  #else
    #define INSTANCE_ID gl_InstanceID
  #endif
#else
  #define INSTANCE_ID instanceId
#endif

#define ChunkVisibility sections[INSTANCE_ID].ChunkVisibility
#define TextureSize sections[INSTANCE_ID].TextureSize
#define ChunkPosition sections[INSTANCE_ID].ChunkPosition
