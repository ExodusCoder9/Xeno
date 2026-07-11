package com.xeno.client.mixin;

import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(LeavesBlock.class)
public class LeavesBlockMixin {
    /**
     * @author ExodusCoder9
     * @reason Cull adjacent leaf faces to optimize rendering in forest biomes.
     */
    @Overwrite
    public boolean skipRendering(BlockState state, BlockState neighborState, Direction direction) {
        return neighborState.getBlock() instanceof LeavesBlock;
    }
}
