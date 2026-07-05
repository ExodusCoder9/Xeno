struct SectionData {
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    mat4 ModelViewMat;
    SectionData sections[1024];
};

// In vertex shader, use gl_DrawID when available
// In fragment shader, use the flat instanceId varying passed from vertex shader
#ifdef VERTEX_SHADER
  #if defined(GL_EXT_shader_draw_parameters)
    #define INSTANCE_ID gl_DrawID
  #elif defined(GL_ARB_shader_draw_parameters)
    #define INSTANCE_ID gl_DrawIDARB
  #else
    #define INSTANCE_ID gl_InstanceID
  #endif
#else
  #define INSTANCE_ID instanceId
#endif
