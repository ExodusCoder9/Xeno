# Xeno (26.2 Alpha 4)

Xeno is a rendering optimization mod for Minecraft 26.2. It replaces the slow vanilla rendering loops with a streamlined, API-agnostic graphics pipeline designed to play nice with Vulkan and OpenGL backends.

---

## Core Architecture

* **Asynchronous Occlusion & Culling:** A dedicated background thread runs macro-occlusion graphs, bounding box checks, and frustum culling, keeping the main render thread from stalling on geometry checks. Uncompiled meshes are handled transparently to prevent chunk loading locks.
* **Consolidated Buffer Pools:** Merges thousands of individual chunk allocations into single, massive GPU memory pools (128MB VBO / 32MB IBO) to eliminate VRAM allocation stutters and driver state overhead.
* **Single-Binding Batch Draw:** Intercepts batch rendering at the render pass level. Binds the unified VBO/IBO pools once per pass, then loops to execute draws using simple UBO matrix offset updates. This cuts CPU-to-GPU binding changes by over 95%.

---

## Key Features & Changes (Up to Alpha 4)

* **Tabbed Video Settings Screen:** A fully custom video settings GUI built from scratch. It splits options into *General*, *Quality*, *Performance*, and *Advanced* tabs. Features smooth scrolling, performance impact tags (Low/Medium/High), and a pending changes system that handles reloads and notifies you if a restart is needed for backend changes.
* **Xeno Rendering & Shader API (XRA):** An extensible API allowing other mods to register custom shaders, OpenGL+Vulkan compute shaders (with macOS/compatibility fallback checks), materials (custom blend modes, depth testing, backface culling, and uniform bindings), custom render passes (injected at stages like `BEFORE_WORLD` or `AFTER_WORLD`), and async meshing hooks.
* **Threaded Translucent Sorting:** Sorts translucent quad indices in the background via `Util.backgroundExecutor()`. Once sorted relative to the camera, it uploads the results directly to GPU buffer slices on the main thread, keeping the render thread smooth.
* **Unified GPU Memory Allocation:** Dynamic sub-allocations in the pre-allocated GPU pool using a custom thread-safe, coalescing free-list allocator.
* **Deferred Allocation Freeing:** Puts discarded memory allocations in a 3-frame deferred queue to prevent the GPU from reading overwritten memory, fixing chunk flashing and water flickering.
* **Zero-Allocation Memory Copies:** Uses Java's Foreign Function & Memory (FFM) API (`MemorySegment` copies) to copy compiled chunk data directly to persistently mapped GPU memory slices, bypassing JVM heap allocations.
* **Leak-Safe Async Compiler:** Wraps background compilation in try-catch-finally blocks to guarantee builder packs are returned to the pool even if compilation fails during world reloads.
* **Silent Builder Resets:** Clears unbuilt vertex batches silently with `discardAll()`, stopping console spam and associated render thread freezes.
* **Resource Reload Safety:** Keeps memory pools intact across resource reloads and mipmap changes, avoiding crashes from closed buffers.

---

## Environment Requirements

* **Minecraft Version:** 26.2
* **Fabric Loader:** >= 0.19.3
* **Java Environment:** Java 25 or higher
* **Environment:** Client-side only