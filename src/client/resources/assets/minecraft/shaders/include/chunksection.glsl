#version 330
#extension GL_ARB_shader_draw_parameters : enable

struct SectionData {
    mat4 ModelViewMat;
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    SectionData sections[1024];
};

#if defined(GL_ARB_shader_draw_parameters)
#define XENO_DRAW_ID gl_DrawIDARB
#else
#define XENO_DRAW_ID gl_DrawID
#endif

#define ModelViewMat sections[XENO_DRAW_ID].ModelViewMat
#define ChunkVisibility sections[XENO_DRAW_ID].ChunkVisibility
#define TextureSize sections[XENO_DRAW_ID].TextureSize
#define ChunkPosition sections[XENO_DRAW_ID].ChunkPosition
