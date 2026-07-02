# Xeno ARM

A high-performance Vulkan rendering mod for Minecraft, providing enhanced frame rates and reduced CPU overhead. **Now optimized for Android devices via Zalith Launcher!**

## 🚀 Android & ARM Support

This version of Xeno is specifically built to run on **Android devices** using the **Zalith Launcher**. Experience smooth Vulkan rendering on mobile platforms with ARM architecture support.

### Zalith Launcher Integration
- ✅ Full Vulkan support on Android ARM devices
- ✅ Optimized for mobile performance
- ✅ Compatible with Zalith Launcher ecosystem
- ✅ Enhanced frame rates on ARM processors
- ✅ Reduced CPU overhead for mobile devices

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
- Further ARM optimization and mobile-specific enhancements

## Requirements

### Desktop
- Minecraft 26.1.2
- Fabric Loader 0.19.3+
- Java 25
- Vulkan 1.2+ compatible GPU with up-to-date drivers

### Android (via Zalith Launcher)
- Zalith Launcher installed
- Android device with ARM processor
- Vulkan 1.2+ capable GPU
- Minimum 4GB RAM recommended
- Updated GPU drivers

## Installation

### Desktop
Standard Fabric mod installation - place the mod JAR in your mods folder.

### Android (Zalith Launcher)
1. Install Zalith Launcher from the official source
2. Add this mod to your Zalith Launcher instance
3. Launch and enjoy enhanced rendering on your ARM device

## License

LGPL 3.0 — same as the original VulkanMod codebase.
VulkanMod's Original License has also been included in this module.

## Credits

A Vulkan rendering backend for Minecraft, forked from [VulkanMod](https://github.com/xCollateral/VulkanMod) by XCollateral.
