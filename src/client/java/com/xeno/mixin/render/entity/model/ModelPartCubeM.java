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
package com.xeno.mixin.render.entity.model;

import java.util.Set;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import com.xeno.interfaces.ModelPartCubeMixed;
import com.xeno.render.model.CubeModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelPart.Cube.class)
public class ModelPartCubeM implements ModelPartCubeMixed {
   @Unique
   CubeModel cube;

   @Inject(
      method = "<init>",
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/client/model/geom/ModelPart$Cube;polygons:[Lnet/minecraft/client/model/geom/ModelPart$Polygon;",
         ordinal = 0,
         shift = Shift.AFTER
      )
   )
   private void getVertices(
      int xTexOffs,
      int yTexOffs,
      float minX,
      float minY,
      float minZ,
      float width,
      float height,
      float depth,
      float growX,
      float growY,
      float growZ,
      boolean mirror,
      float xTexSize,
      float yTexSize,
      Set<Direction> visibleFaces,
      CallbackInfo ci
   ) {
      CubeModel cube = new CubeModel();
      cube.setVertices(xTexOffs, yTexOffs, minX, minY, minZ, width, height, depth, growX, growY, growZ, mirror, xTexSize, yTexSize, visibleFaces);
      this.cube = cube;
   }

   @Override
   public CubeModel getCubeModel() {
      return this.cube;
   }
}
