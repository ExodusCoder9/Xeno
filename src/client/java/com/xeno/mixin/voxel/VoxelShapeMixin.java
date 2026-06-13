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
package com.xeno.mixin.voxel;

import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.xeno.interfaces.VoxelShapeExtended;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VoxelShape.class)
public class VoxelShapeMixin implements VoxelShapeExtended {
   @Shadow
   @Final
   protected DiscreteVoxelShape shape;
   int co;

   @Inject(method = "<init>", at = @At("RETURN"))
   private void initCornerOcclusion(DiscreteVoxelShape discreteVoxelShape, CallbackInfo ci) {
      DiscreteVoxelShape disShape = this.shape;
      VoxelShape shape = (VoxelShape)(Object)this;
      if (shape instanceof CubeVoxelShape && disShape != null) {
         int xSize = Math.max(disShape.getXSize(), 1);
         int ySize = Math.max(disShape.getYSize(), 1);
         int zSize = Math.max(disShape.getZSize(), 1);
         int co = 0;
         int s = 0;

         for (int y1 = 0; y1 <= 1; y1++) {
            for (int z1 = 0; z1 <= 1; z1++) {
               for (int x1 = 0; x1 <= 1; x1++) {
                  int x2 = x1 * (xSize - 1);
                  int y2 = y1 * (ySize - 1);
                  int z2 = z1 * (zSize - 1);
                  co |= (disShape.isFull(x2, y2, z2) ? 1 : 0) << s;
                  s++;
               }
            }
         }

         this.co = co;
      } else {
         this.co = 0;
      }
   }

   @Override
   public int getCornerOcclusion() {
      return this.co;
   }
}
