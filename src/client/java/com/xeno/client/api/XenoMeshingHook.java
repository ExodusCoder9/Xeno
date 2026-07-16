package com.xeno.client.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.block.BlockQuadOutput;

/**
 * Functional callback interface executed during the asynchronous chunk meshing/compilation phase.
 * <p>
 * Registered meshing hooks are invoked for every block in a chunk section as the compiler thread iterates over them.
 * Modders can use this to inject custom block models, append custom vertex attributes, or dynamically
 * override rendering behavior based on neighboring states (e.g. connected textures) or biomes.
 * </p>
 *
 * <h2>⚠️ CRITICAL THREAD SAFETY WARNING:</h2>
 * <p>
 * This callback is executed inside **background chunk compilation threads**, not the main client rendering thread.
 * <strong>Do NOT perform any OpenGL/Blaze3D calls</strong> (e.g. binding shaders, creating textures, or calling GL11)
 * inside this callback, as there is no active GL context on worker threads, which will cause JVM crashes.
 * Only read data from {@code RenderSectionRegion} and write vertex data to {@code BlockQuadOutput}.
 * </p>
 *
 * @see XenoRenderAPI#registerMeshingHook(XenoMeshingHook)
 */
@FunctionalInterface
public interface XenoMeshingHook {

    /**
     * Invoked when a block state is about to be meshed in a chunk section.
     * <p>
     * Add custom quads directly to the {@code quadOutput} writer. You can return {@code true}
     * to cancel/bypass default vanilla model rendering (e.g. if your hook handles drawing a completely custom model).
     * </p>
     *
     * @param pos        the global world position of the block being compiled
     * @param state      the block state at the position
     * @param region     the local chunk section region (safe for thread-safe biome and neighboring blockstate lookups)
     * @param quadOutput the quad output stream writer. Emit custom baked quads here.
     * @return {@code true} if default model rendering should be bypassed; {@code false} to let Xeno mesh the block normally
     */
    boolean onBlockMesh(BlockPos pos, BlockState state, RenderSectionRegion region, BlockQuadOutput quadOutput);
}
