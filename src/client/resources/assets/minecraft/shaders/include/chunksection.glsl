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

// GL_ARB_shader_draw_parameters adds gl_DrawID to GLSL
#define ModelViewMat sections[gl_DrawID].ModelViewMat
#define ChunkVisibility sections[gl_DrawID].ChunkVisibility
#define TextureSize sections[gl_DrawID].TextureSize
#define ChunkPosition sections[gl_DrawID].ChunkPosition
