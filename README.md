# Xeno

A Vulkan rendering backend for Minecraft, forked from [VulkanMod](https://github.com/xCollateral/VulkanMod) by XCollateral.

## Features

- Full Vulkan rendering pipeline replacing OpenGL
- Indirect draw support (single draw call per render pass)
- Double-buffered vertex/index/uniform buffers
- VMA-based GPU memory allocation with deferred freeing
- Multi-threaded chunk mesh building with priority queues
- Back-face culling on CPU
- Frustum culling with 2-level octree
- Entity culling using section visibility tracking
- Configurable frame queue size
- Custom video settings screen with search
- Animated texture support
- Smooth lighting (flat and smooth AO pipelines)
- Biome tinting with per-section tint cache
- Conditional sub-block ambient occlusion
- Unified opaque render pass option
- Vulkan pipeline cache with per-state deduplication
- Descriptor set pooling with automatic growth
- Host-mapped staging buffer (128MB)
- Texture sampler caching with parameter hashing
- Compute shader-based shader includes (SPIR-V via shaderc)

## In Progress

- Greedy mesh optimization (merges coplanar block faces, WIP)
- GPU-driven renderer (compute shader culling and indirect command generation, WIP)
- Model part normal packing optimization (cache packed normals for static poses)
- Staging buffer flush batching (reduce render pass breaks during chunk loading)

## Requirements

- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Java 25
- Vulkan 1.2+ compatible GPU with up-to-date drivers

## License

LGPL 3.0 — same as the original VulkanMod codebase.
VulkanMod's Original License has also been included in My Module Root.
