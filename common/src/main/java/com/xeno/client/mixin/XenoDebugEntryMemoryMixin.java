/*
 * Copyright (C) 2026 ExodusCoder9
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://gnu.org>.
 */

package com.xeno.client.mixin;

import net.minecraft.client.gui.components.debug.DebugEntryMemory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;

@Mixin(DebugEntryMemory.class)
public abstract class XenoDebugEntryMemoryMixin {
    @Shadow
    @Final
    private static Identifier GROUP;

    @Unique
    private static final List<BufferPoolMXBean> XENO_BUFFER_POOLS = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);

    @Inject(method = "display", at = @At("TAIL"))
    private void xeno$addOffHeapMemory(
            DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk,
            CallbackInfo ci
    ) {
        long offHeapBytes = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
        for (int i = 0; i < XENO_BUFFER_POOLS.size(); i++) {
            long used = XENO_BUFFER_POOLS.get(i).getMemoryUsed();
            if (used > 0L) {
                offHeapBytes += used;
            }
        }
        long offHeapMiB = offHeapBytes / (1024L * 1024L);
        displayer.addToGroup(GROUP, "Off-Heap: +" + offHeapMiB + "MB");
    }
}
