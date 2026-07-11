# Xeno (26.2 Alpha)

Xeno is a rendering optimization mod for Minecraft 26.2

## Architecture


* **Asynchronous Occlusion :** A dedicated background thread isolates macro-occlusion graphs, bounding box evaluations, and frustum culling visibility checks, ensuring the render thread never stalls on geometry evaluation.
* **Custom Chunk Meshing :** Overtakes Vanilla chunks meshing.

## Optimizations

* **Translucency Resort Allocation Reductions:** Replaced vanilla translucent section resort loops with a pre-allocated coordinate cache and array index iterations, removing Iterator and BlockPos allocations.
* **Non-allocating Entity Culling:** Overwrote entity visibility culling checks to map bounding boxes directly to sections without allocating BlockPos objects.
* **Persistent GPU Staging Buffers:** Configured staging buffers to force persistently mapped memory transfers on compatible hardware, reducing CPU copy times.
* **Dynamic Uniform UBO Optimization:** Reduced rendering thread garbage by reusing camera ModelView Matrix4f instances and introducing a list-based upload path that avoids list-to-array conversions.
* **Resource Reload Safety:** Integrated automatic cache invalidation for model block and fluid renderers upon resource reload and mipmap changes to prevent texture bleeding and sampling artifacts.

## Repository Branch Layout

This repository maintains three operational tracks:

* `dev`: The default branch. Contains current experimental ports, active alpha revisions, and daily optimization passes.
* `26.2 stable`: Production branch reserved for thoroughly verified, stable releases on the 26.2 modding ecosystem.
* `26.1.2 stable`: Production branch tracking mature optimization sets for the 26.1.2 game version.

## Environment Requirements

* **Minecraft Version:** 26.2
* **Java Environment:** Java 25
* **Environment:** Client-side only