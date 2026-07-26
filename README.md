# Xeno (26.2 Alpha 7)

**Xeno** is a low-overhead, high-performance graphics optimization mod for Minecraft 26.2. It replaces vanilla rendering loops with a streamlined, API-agnostic pipeline built for Vulkan and OpenGL backends using an **Inject & Delegate** architecture.

---

## 🚀 Architecture Highlights

* **Multi-Draw Indirect (MDI) Engine:** Single-call hardware indirect drawing (`GL43C` / Vulkan `vkCmdDrawIndexedIndirect`). Packs 20-byte indirect commands with hardware `gl_BaseInstance` matrix indexing in off-heap FFM memory.
* **Cloned 3D World Snapshots (`XenoLevelSlice`):** Captures $3 \times 3 \times 3$ section neighborhoods into flat 1D primitive arrays, enabling $100\%$ thread-safe background chunk meshing with zero main-thread lock contention.
* **Spatial Chunk Grid & Instant VRAM Cleanup (`XenoSectionStorage`):** Replaces `ViewArea` modulo coordinate wrapping with a 1D spatial hash grid. Automatically releases GPU VRAM allocations in `XGenerationalMultiBufferAllocator` on chunk unload (`ClientChunkCache.drop`) without waiting for Java Garbage Collection.
* **Generational Arena Allocator:** Multi-buffer generational memory (`XGenerationalMultiBufferAllocator`) combining lock-free `AtomicLong` Young/Survivor bump arenas with $O(1)$ TLSF coalescing for Old generation chunk memory. Uses bit-packed 64-bit `XenoHandle` primitive handles.
* **Flat 1D Section Compiler (`XenoSectionCompiler`):** Replaces vanilla's 4,096 `BlockPos` loop with flat index bit-wise traversal, eliminating heap allocations during section meshing.
* **Asynchronous Occlusion & Culling:** Background thread occlusion graphs, bounding box checks, and frustum culling to prevent main-thread geometry stalls.
* **Unified GPU Memory Pools:** Consolidates chunk allocations into massive GPU memory pools (128MB VBO / 32MB IBO) to reduce driver state switches and allocation stutter.

---

## ⚡ Core Systems & Features

* **Dual Lighting Pipelines:**
  * **`XenoFlatLightPipeline`:** Fast single-sample directional face shading ($1.0$ UP, $0.5$ DOWN, $0.8$ N/S, $0.6$ E/W) when AO is disabled, skipping AO sampling for maximum FPS.
  * **`XenoSmoothLightPipeline`:** 4-corner vertex AO weight calculation and smooth lighting interpolation without `BlockPos` allocations.
* **Fast Biome Color Sampler (`XenoBiomeBlender`):** Flat primitive array sampling for grass, water, and foliage colors, bypassing `BiomeColors` sampler allocations.
* **Fast Volumetric Cloud Engine (`XenoCloudRenderer`):** Single-pass 3D cloud mesh builder allocating persistent GPU buffers directly in off-heap FFM memory.
* **High-Performance Quad Particle System (`XenoParticleRenderer`):** Quad particle batching hooked via `QuadParticleFeatureRendererMixin` (`executeGroup`), accelerating particle rendering while preserving vanilla particle physics and lifetimes.
* **Fabric Rendering API (FRAPI) Interop (`XenoFrapiMesh`):** Direct quad streaming support for complex modded block models (e.g. Create, TechReborn, AE2).
* **Localized Video Settings GUI:** Tabbed video settings interface (*General*, *Quality*, *Performance*, *Advanced*) with full multi-language translation support (`en_us.json`) for options, tooltips, buttons, and performance impact badges.
* **Xeno Rendering & Shader API (XRA):** Extensible API supporting custom shaders, compute shaders (OpenGL/Vulkan with fallback checks), custom materials, and pipeline pass injections.
* **Threaded Translucent Quad Sorting:** Background translucent quad sorting on `Util.backgroundExecutor()`, uploading directly to GPU buffer slices.
* **3-Frame Deferred Free Queue:** Prevents GPU read-after-free corruption during chunk re-meshing.
* **Off-Heap FFM Transfers:** Java 25 Foreign Function & Memory (`MemorySegment`) zero-copy data transfer to mapped VRAM.

---

## 📋 Requirements

* **Minecraft:** 26.2
* **Fabric Loader:** >= 0.19.3
* **Java:** Java 25 or higher versions
* **Environment:** Client-side