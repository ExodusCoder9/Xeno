# Xeno (26.2 Alpha 2)

Xeno is a modern, high-performance rendering optimization mod for Minecraft 26.2. It neutralizes vanilla's slow rendering loops and replaces them with an optimized, API-agnostic graphics pipeline compatible with both Vulkan and OpenGL backends.

---

## Core Architecture

* **Asynchronous Occlusion & Culling:** A dedicated background thread isolates macro-occlusion graphs, bounding box evaluations, and frustum culling visibility checks, ensuring the render thread never stalls on geometry evaluation. Uncompiled meshes are traversed transparently to prevent chunk loading chicken-and-egg timeouts.
* **Consolidated Buffer Pools:** Consolidates thousands of individual chunk allocations into single, massive unified GPU memory pools (128MB VBO / 32MB IBO), eliminating VRAM allocation stutters and driver state overhead.
* **Single-Binding Batch Draw:** Intercepts batch rendering at the `RenderPass` level. It binds the massive consolidated VBO/IBO pools exactly once per render pass, looping to execute draws with only UBO matrix offset updates, removing 95%+ of CPU-to-GPU binding changes.

---

## Key Optimizations & Features (New in Alpha 2)

* **Unified GPU Memory Allocation:** Memory is dynamically allocated in sub-segments of a pre-allocated GPU pool using a custom thread-safe, coalescing free-list allocator.
* **Deferred Allocation Freeing:** Discarded memory allocations are placed in a 3-frame deferred free queue. This prevents the GPU from reading overwritten memory, completely resolving visual chunk flashing and water flickering.
* **Zero-Allocation Memory Copies:** Leverages Java's Foreign Function & Memory (FFM) API (`MemorySegment` copies) to copy compiled chunk data directly to persistently mapped GPU memory slices without JVM heap allocations.
* **Leak-Safe Async Compiler:** Wraps background chunk compile tasks in try-catch-finally blocks to guarantee that builder packs are returned to the pool even if compilation fails due to world reload/invalidation.
* **Silent Builder Resets:** Integrates `discardAll()` resets on compiler builders to silently clear unbuilt vertex batches, completely eliminating the `Clearing BufferBuilder with unused batches` console spam and the associated Render Thread freezes.
* **Resource Reload Safety:** Preserves memory pools across resource reloads and mipmap configuration changes, preventing cached rendering paths from attempting to draw from closed buffers.

---

## Environment Requirements

* **Minecraft Version:** 26.2
* **Fabric Loader:** >= 0.19.3
* **Java Environment:** Java 25 or higher
* **Environment:** Client-side only