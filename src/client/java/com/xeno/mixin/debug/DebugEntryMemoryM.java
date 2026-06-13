/*
 * Original Codebase: Copyright XCollateral (VulkanMod)
 * Refactored Codebase: Copyright ExodusCoder9 (Xeno)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Refactored, Renamed and Optimized by ExodusCoder9.
 */
package com.xeno.mixin.debug;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.components.debug.DebugEntryMemory;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import com.xeno.vulkan.memory.MemoryManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DebugEntryMemory.class)
public abstract class DebugEntryMemoryM {
   @Shadow
   @Final
   private static Identifier GROUP;

   @Shadow
   private static long bytesToMebibytes(long l) {
      return 0L;
   }

   @Overwrite
   public void display(DebugScreenDisplayer debugScreenDisplayer, @Nullable Level level, @Nullable LevelChunk levelChunk, @Nullable LevelChunk levelChunk2) {
      long l = Runtime.getRuntime().maxMemory();
      long m = Runtime.getRuntime().totalMemory();
      long n = Runtime.getRuntime().freeMemory();
      long o = m - n;
      debugScreenDisplayer.addToGroup(
         GROUP,
         List.of(
            String.format(Locale.ROOT, "Mem: %2d%% %03d/%03dMB", o * 100L / l, bytesToMebibytes(o), bytesToMebibytes(l)),
            String.format(Locale.ROOT, "Allocated: %2d%% %03dMB", m * 100L / l, bytesToMebibytes(m)),
            String.format("Off-heap: " + this.getOffHeapMemory() + "MB"),
            "NativeMemory: %dMB".formatted(MemoryManager.getInstance().getNativeMemoryMB()),
            "DeviceMemory: %dMB".formatted(MemoryManager.getInstance().getAllocatedDeviceMemoryMB())
         )
      );
   }

   private long getOffHeapMemory() {
      return bytesToMebibytes(ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());
   }
}
