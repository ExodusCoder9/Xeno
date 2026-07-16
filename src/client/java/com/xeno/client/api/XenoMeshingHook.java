package com.xeno.client.api;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.block.BlockQuadOutput;

/**
 * Hook interface called during the async chunk compilation/meshing phase.
 * Other mods can register this to inject custom block geometry, modify vertex attributes,
 * or dynamically override block rendering behavior based on biome or surrounding blocks.
 */
@FunctionalInterface
public interface XenoMeshingHook {

    /**
     * Invoked when a block is about to be meshed in a chunk section.
     * @param pos The global position of the block.
     * @param state The current block state.
     * @param region The local chunk section region (used for biome or neighbor lookups).
     * @param quadOutput The block quad output writer. Add custom quads here.
     * @return True if the block's default rendering should be cancelled/bypassed, false to allow Xeno to mesh it normally.
     */
    boolean onBlockMesh(BlockPos pos, BlockState state, RenderSectionRegion region, BlockQuadOutput quadOutput);
}
