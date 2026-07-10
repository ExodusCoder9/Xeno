#version 330

#if defined(VULKAN) || defined(SPIRV)
    // Vulkan compat
#else
    #extension GL_ARB_shader_draw_parameters : enable
#endif

struct ChunkSectionData {
    mat4 ModelViewMat;
    float ChunkVisibility;
    ivec2 TextureSize;
    ivec3 ChunkPosition;
};

layout(std140) uniform ChunkSection {
    ChunkSectionData sections[512];
};
