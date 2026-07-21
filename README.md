# Xeno (26.2 Alpha 6)

Xeno is a low-overhead graphics optimization mod for Minecraft 26.2. It replaces vanilla rendering loops with a streamlined, API-agnostic pipeline built for Vulkan and OpenGL backends.

---

## Architecture Highlights

* **Multi-Draw Indirect (MDI) Engine:** Single-call hardware indirect drawing (`GL43C` / Vulkan `vkCmdDrawIndexedIndirect`). Packs 20-byte indirect commands with hardware `gl_BaseInstance` matrix indexing in off-heap FFM memory.
* **Generational Arena Allocator:** Multi-buffer generational memory (`XGenerationMultiBufferAllocator`) combining lock-free `AtomicLong` Young/Survivor bump arenas with $O(1)$ TLSF coalescing for Old generation chunk memory. Uses bit-packed 64-bit `XenoHandle` primitive handles.
* **Flat 1D Section Compiler (`XenoSectionCompiler`):** Replaces vanilla's 4,096 `BlockPos` loop with a flat index bit-wise traversal, eliminating heap allocations during section meshing.
* **Asynchronous Occlusion & Culling:** Background thread occlusion graphs, bounding box checks, and frustum culling to prevent main-thread geometry stalls.
* **Unified GPU Memory Pools:** Consolidates chunk allocations into massive GPU memory pools (128MB VBO / 32MB IBO) to reduce driver state switches and allocation stutter.

---

## Core Systems & Features

* **Tabbed Video Settings GUI:** Modern video settings interface split into *General*, *Quality*, *Performance*, and *Advanced* tabs with pending change notifications.
* **Xeno Rendering & Shader API (XRA):** Extensible API supporting custom shaders, compute shaders (OpenGL/Vulkan with fallback checks), custom materials, and pipeline pass injections.
* **Threaded Translucent Quad Sorting:** Background translucent quad sorting on `Util.backgroundExecutor()`, uploading directly to GPU buffer slices.
* **3-Frame Deferred Free Queue:** Prevents GPU read-after-free corruption during chunk re-meshing.
* **Off-Heap FFM Transfers:** Java 25 Foreign Function & Memory (`MemorySegment`) zero-copy data transfer to mapped VRAM.

---

## Requirements

* **Minecraft:** 26.2
* **Fabric Loader:** >= 0.19.3
* **Java:** Java 25 or higher
* **Environment:** Client-side